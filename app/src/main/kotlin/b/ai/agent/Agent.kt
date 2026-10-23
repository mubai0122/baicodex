package b.ai.agent

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.core.AgentMode
import b.ai.core.Cfg
import b.ai.App
import b.ai.R
import java.io.File
import b.ai.core.ChatMsg
import b.ai.core.Env
import b.ai.core.Memory
import b.ai.core.MsgMeta
import b.ai.core.Perms
import b.ai.core.Personas
import b.ai.core.Providers
import b.ai.core.Repo
import b.ai.core.Route
import b.ai.core.Search
import b.ai.core.SessionRow
import b.ai.core.Skills
import b.ai.core.SkillPacks
import b.ai.core.Speak
import b.ai.core.ToolCall
import b.ai.core.Waifu
import b.ai.core.WorkMode
import b.ai.core.Workflows
import b.ai.exec.Linux
import b.ai.exec.Router
import b.ai.exec.Workspace
import b.ai.net.LlmClient
import b.ai.net.LlmException
import b.ai.net.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AskQuestion(val title: String, val options: List<String>, val multi: Boolean, val other: Boolean)

/** 一次提问请求：deferred 由界面作答后回填，模型那一轮挂在这里等 */
data class AskReq(val questions: List<AskQuestion>, val deferred: CompletableDeferred<List<String>>)

/** 计划确认的三种结果：定下执行 / 继续改 / 不要了 */
sealed class PlanVerdict {
    data object Confirm : PlanVerdict()
    data class Revise(val note: String) : PlanVerdict()
    data object Dismiss : PlanVerdict()
}

/** 一次计划确认请求：正文是计划本身，deferred 由面板回填 */
data class PlanReq(val text: String, val deferred: CompletableDeferred<PlanVerdict>)

data class PendingTool(
    val call: ToolCall,
    val args: JSONObject,
    val route: Route,
    val highRisk: Boolean,
    val deferred: CompletableDeferred<Boolean>
)

object Agent {
    private const val MAX_ROUND = 24
    private const val SUMMARY_PROMPT =
        "你是上下文压缩器。把给你的对话记录压成要点摘要，保留：用户目标、已执行的动作与关键路径/命令、" +
            "已得结论、未完成事项。不要编造，不要客套，直接输出摘要，300 字以内。"
    private const val TITLE_PROMPT =
        "你是会话命名助手。看用户这一句需求，回一行：标题|描述。" +
            "标题 4-12 个字，名词性短语，不带标点和引号；描述一句话，不超过 24 个字，说这份对话在做什么。" +
            "只回这一行，不要解释、不要加序号。"

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var streamText by mutableStateOf("")
        private set
    var streamReason by mutableStateOf("")
        private set
    var busySince by mutableStateOf(0L)
        private set
    var pending by mutableStateOf<PendingTool?>(null)
        private set
    var ask by mutableStateOf<AskReq?>(null)
        private set
    var plan by mutableStateOf<PlanReq?>(null)
        private set
    var sessionId by mutableStateOf<String?>(null)
        private set
    var sessions by mutableStateOf<List<SessionRow>>(emptyList())
        private set
    var sessionTitle by mutableStateOf("")
        private set

    /**取资源文案用；不叫 ctx 是因为 runLoop 里有同名局部变量 */
    private val host: Context get() = App.self

    private var job: Job? = null
    private var runSid: String? = null
    private var runHistory: List<ChatMsg> = emptyList()
    private var budgetScale = 1f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chunks = MutableStateFlow("" to "")
    private val sContent = StringBuilder()
    private val sReason = StringBuilder()

    init {
        scope.launch {
            chunks.collect { (t, r) ->
                streamText = t
                streamReason = r
            }
        }
    } //增量回填主线程

    fun runningHere(): Boolean = runSid == null || runSid == sessionId

    private fun hist(): List<ChatMsg> = if (runSid != null && runSid != sessionId) runHistory else messages

    fun refreshSessions() {
        scope.launch { reloadSessions() }
    } //刷新会话列表与当前标题

    fun ensureSession() {
        if (sessionId != null) return
        scope.launch {
            if (sessionId != null) return@launch
            val s = withContext(Dispatchers.IO) { Repo.latestSession() ?: Repo.newSession() }
            sessionId = s.id
            adopt(s)
            messages = withContext(Dispatchers.IO) { Repo.messages(s.id) }
            reloadSessions()
        }
    } //重进 App 落在最新会话

    private fun adopt(s: SessionRow) {
        Cfg.personaId = s.persona
        Personas.current()?.profileId?.trim()?.takeIf { it.isNotEmpty() }?.let { Cfg.profileId = it }
        Cfg.sessionWorkspace = s.workspace.trim()
        Workspace.invalidate()
    } //套用会话自己的角色与工作区

    fun setPersona(personaId: String) {
        Cfg.personaId = personaId
        val bound = Personas.current()?.profileId?.trim().orEmpty()
        if (bound.isNotEmpty()) Cfg.profileId = bound
        val sid = runSid ?: sessionId ?: return
        scope.launch { withContext(Dispatchers.IO) { Repo.setPersona(sid, personaId) } }
    } //当前会话换角色

    fun setSessionWorkspace(path: String) {
        Cfg.sessionWorkspace = path
        Workspace.invalidate()
        val sid = runSid ?: sessionId ?: return
        scope.launch { withContext(Dispatchers.IO) { Repo.setWorkspace(sid, path) } }
    } //当前会话换工作区

    fun newSession() {
        if (messages.isEmpty() && sessionId != null) {
            status = ""
            return
        }
        scope.launch {
            val s = withContext(Dispatchers.IO) { Repo.newSession() }
            sessionId = s.id
            messages = emptyList()
            status = ""
            val ws = withContext(Dispatchers.IO) {
                val dir = Workspace.newSessionWorkspace()
                Repo.setWorkspace(s.id, dir)
                Repo.setPersona(s.id, Cfg.personaId)
                dir
            }
            Cfg.sessionWorkspace = ws
            val open = Personas.opener()
            if (open.isNotEmpty()) {
                val hello = withContext(Dispatchers.IO) { Repo.append(s.id, ChatMsg(role = "assistant", content = open)) }
                messages = listOf(hello)
            }
            reloadSessions()
        }
    }

    fun openSession(id: String) {
        scope.launch {
            val list = withContext(Dispatchers.IO) { Repo.messages(id) }
            val row = withContext(Dispatchers.IO) { Repo.session(id) }
            android.util.Log.d("BaiDBG", "openSession id=${id.take(8)} msgs=${list.size}")
            sessionId = id
            if (row != null) adopt(row)
            messages = list
            reloadSessions()
        }
    }

    fun renameSession(id: String, title: String) {
        scope.launch {
            withContext(Dispatchers.IO) { Repo.renameSession(id, title.trim().ifBlank { host.getString(R.string.ag_unnamed) }) }
            reloadSessions()
        }
    }

    fun pinSession(id: String, on: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { Repo.setPinned(id, on) }
            reloadSessions()
        }
    } //置顶

    /** 收藏开关：改标记后重读当前会话，气泡上的星标立刻跟着变 */
    fun toggleStar(m: ChatMsg, on: Boolean) {
        val sid = sessionId ?: return
        scope.launch {
            withContext(Dispatchers.IO) { Repo.setStarred(m.id, on) }
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
        }
    } //收藏开关

    /** 删单条消息：删完重读会话，会话列表的预览也跟着更新 */
    fun deleteMessage(m: ChatMsg) {
        val sid = sessionId ?: return
        scope.launch {
            withContext(Dispatchers.IO) { Repo.deleteMessage(sid, m.id) }
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
            reloadSessions()
        }
    } //删单条消息

    fun deleteSession(id: String) {
        deleteSessions(setOf(id))
    } //删会话
    
    fun deleteSessions(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) { ids.forEach { Repo.deleteSession(it) } }
            if (sessionId in ids) {
                val next = withContext(Dispatchers.IO) { Repo.latestSession() }
                if (next == null) {
                    val s = withContext(Dispatchers.IO) { Repo.newSession() }
                    sessionId = s.id
                    messages = emptyList()
                } else {
                    sessionId = next.id
                    messages = withContext(Dispatchers.IO) { Repo.messages(next.id) }
                }
            }
            reloadSessions()
        }
    } //批量删会话

    private suspend fun autoTitle(sid: String, first: String) {
        val pay = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", TITLE_PROMPT))
            put(JSONObject().put("role", "user").put("content", first.take(800)))
        }
        val resp = runCatching {
            withContext(Dispatchers.IO) {
                LlmClient(Cfg.baseUrl, Cfg.apiKey, Providers.byId(Cfg.providerId).auth)
                    .chat(Cfg.model, pay, temperature = 0.3)
            }
        }.getOrNull() ?: return
        val line = resp.text("content").lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isBlank()) return
        val parts = line.split('|', limit = 2)
        val title = tidy(parts.getOrNull(0).orEmpty()).take(16)
        if (title.isBlank()) return
        val descr = tidy(parts.getOrNull(1).orEmpty()).take(40)
        withContext(Dispatchers.IO) { Repo.setTitleDescr(sid, title, descr) }
        reloadSessions()
    } //开场让模型起名

    private fun tidy(s: String): String =
        s.trim().trim('"', '“', '”', '*', '`', '#', '。', '，', ',', '.', '：', ':').trim()

    private suspend fun reloadSessions() {
        val all = withContext(Dispatchers.IO) { Repo.sessions() }
        sessions = all
        val sid = sessionId
        sessionTitle = all.firstOrNull { it.id == sid }?.title.orEmpty()
    } //会话列表只在需要时刷新，避免每条消息都全表查一次

    fun stats(): Pair<Int, Int> {
        val sid = sessionId ?: return 0 to 0
        return Repo.stats(sid)
    }

    fun stop() {
        job?.cancel()
        Speak.stop()
        pending?.let { if (!it.deferred.isCompleted) it.deferred.complete(false) }
        pending = null
        ask?.let { if (!it.deferred.isCompleted) it.deferred.complete(emptyList()) }
        ask = null
        plan?.let { if (!it.deferred.isCompleted) it.deferred.complete(PlanVerdict.Dismiss) }
        plan = null
        status = host.getString(R.string.ag_stopped)
        sContent.setLength(0)
        sReason.setLength(0)
        chunks.value = "" to ""
        busy = false
                runSid = null
                runHistory = emptyList()
        b.ai.exec.Capsule.end()
    }

    fun resolve(allow: Boolean, permanent: Boolean) {
        val p = pending ?: return
        if (permanent) Cfg.setPolicy(p.call.name, if (allow) "allow" else "deny")
        pending = null
        if (!p.deferred.isCompleted) p.deferred.complete(allow)
    } //弹窗结果回填

    fun send(text: String, images: List<String> = emptyList()) {
        if (busy) return
        if (!Cfg.ready) {
            appendLocalAsync(ChatMsg(role = "assistant", content = host.getString(R.string.ag_no_model), isError = true))
            return
        }
        busy = true
        job = scope.launch {
            try {
                if (sessionId == null) {
                    val s = withContext(Dispatchers.IO) { Repo.latestSession() ?: Repo.newSession() }
                    sessionId = s.id
                    if (s.workspace.isNotBlank() || s.persona.isNotBlank()) {
                        adopt(s)
                    } else {
                        withContext(Dispatchers.IO) {
                            val dir = Workspace.newSessionWorkspace()
                            Repo.setWorkspace(s.id, dir)
                            Repo.setPersona(s.id, Cfg.personaId)
                            Cfg.sessionWorkspace = dir
                        }
                    }
                    messages = withContext(Dispatchers.IO) { Repo.messages(s.id) }
                }
                val sid = sessionId!!
                runSid = sid
                runHistory = messages
                if (messages.none { it.role == "user" }) {
                    withContext(Dispatchers.IO) { Repo.renameSession(sid, text.take(18)) }
                    reloadSessions()
                    scope.launch { runCatching { autoTitle(sid, text) } }
                }
                append(ChatMsg(role = "user", content = text, images = images))
                runLoop()
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                appendLocal(ChatMsg(role = "assistant", content = host.getString(R.string.ag_err_fmt, e.message.orEmpty()), isError = true))
                status = ""
            } finally {
                busy = false
                runSid = null
                runHistory = emptyList()
                b.ai.exec.Capsule.end()
            }
        }
    } //发送并驱动循环

    fun editResend(msgId: Long, text: String) {
        val t = text.trim()
        if (busy || t.isEmpty()) return
        busy = true
        job = scope.launch {
            try {
                runSid = sessionId
                runHistory = messages
                rollbackTo(msgId)
                append(ChatMsg(role = "user", content = t))
                runLoop()
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                appendLocal(ChatMsg(role = "assistant", content = host.getString(R.string.ag_err_fmt, e.message.orEmpty()), isError = true))
                status = ""
            } finally {
                busy = false
                runSid = null
                runHistory = emptyList()
                b.ai.exec.Capsule.end()
            }
        }
    } //编辑重发

    fun regenerate(msgId: Long, note: String? = null) {
        if (busy) return
        if (messages.none { it.id == msgId }) {
            status = host.getString(R.string.ag_no_regen)
            return
        }
        busy = true
                runSid = sessionId
                runHistory = messages
        job = scope.launch {
            try {
                rollbackTo(msgId)
                val tip = note?.trim()
                if (!tip.isNullOrEmpty()) append(ChatMsg(role = "user", content = tip))
                if (messages.none { it.role == "user" }) {
                    status = host.getString(R.string.ag_no_user_msg)
                } else {
                    runLoop()
                }
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                appendLocal(ChatMsg(role = "assistant", content = host.getString(R.string.ag_err_fmt, e.message.orEmpty()), isError = true))
                status = ""
            } finally {
                busy = false
                runSid = null
                runHistory = emptyList()
                b.ai.exec.Capsule.end()
            }
        }
    } //重新生成
    
    private suspend fun rollbackTo(msgId: Long) {
        val sid = runSid ?: sessionId ?: return
        //只把 head 挪回父节点：旧回答留在树里成了另一个分支，不再被删掉
        withContext(Dispatchers.IO) { Repo.rollback(sid, msgId) }
        val list = withContext(Dispatchers.IO) { Repo.messages(sid) }
        if (sid == sessionId) {
            messages = list
        } else {
            runHistory = list
        }
        budgetScale = 1f
    } //回滚到指定记录之前

    /** 在同父兄弟之间换一支：dir=+1 下一支，-1 上一支 */
    fun switchBranch(msgId: Long, dir: Int) {
        val sid = sessionId ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val list = Repo.siblings(sid, msgId)
                val i = list.indexOf(msgId)
                if (i < 0 || list.size < 2) return@withContext false
                val j = ((i + dir) % list.size + list.size) % list.size
                Repo.switchTo(sid, list[j])
                true
            }
            if (ok) messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
        }
    } //切分支

    private var keyIdx = 0

    private fun keys(): List<String> =
        Cfg.keyList(Cfg.profileId).ifEmpty { listOf(Cfg.apiKey) }.filter { it.isNotBlank() }
    private fun keyCount(): Int = keys().size.coerceAtLeast(1)
    private fun keyOf(i: Int): String {
        val list = keys()
        if (list.isEmpty()) return Cfg.apiKey
        return list[i % list.size]
    } //取第 i 个 Key

    private fun retryable(e: Throwable): Boolean {
        val msg = e.message ?: ""
        val code = Regex("HTTP (\\d{3})").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        if (code != null) return code == 429 || code == 401 || code == 403 || code >= 500
        return msg.startsWith("网络错误")
    } //是否值得换 Key

    private suspend fun runLoop() {
        val ctx = currentCoroutineContext()
        val temp = Cfg.temperature.takeIf { it >= 0f }?.toDouble()
        status = host.getString(R.string.ag_ask_model)
        busySince = System.currentTimeMillis()
        var round = 0
        compact(false)
        while (ctx.isActive && round < MAX_ROUND) {
            round++
            val payload = withContext(Dispatchers.IO) { buildPayload() }
            val t0 = System.currentTimeMillis()
            val firstAt = java.util.concurrent.atomic.AtomicLong(0L)
            sContent.setLength(0)
            sReason.setLength(0)
            chunks.value = "" to ""
            status = host.getString(R.string.ag_ask_model)
            busySince = System.currentTimeMillis()
            val emit: (String, String) -> Unit = { t, r ->
                if (t.isNotEmpty() || r.isNotEmpty()) {
                    if (firstAt.get() == 0L) firstAt.set(System.currentTimeMillis())
                }
                if (t.isNotEmpty()) sContent.append(t)
                if (r.isNotEmpty()) sReason.append(r)
                chunks.value = sContent.toString() to sReason.toString()
            }
            val total = keyCount()
            var attempt = 0
            val resp = run {
                while (true) {
                    val client = LlmClient(Cfg.baseUrl, keyOf(keyIdx), Providers.byId(Cfg.providerId).auth)
                    val got = runCatching {
                        val streamed = withContext(Dispatchers.IO) {
                            try {
                                client.chatStream(
                                    Cfg.model, payload, temp, Cfg.think, Cfg.thinkEffort, Cfg.extraParams,
                                    shouldStop = { !ctx.isActive },
                                    onDelta = emit
                                )
                            } catch (e: LlmException) {
                                if (sContent.isEmpty() && sReason.isEmpty()) null else throw e
                            }
                        }
                        streamed ?: run {
                            status = host.getString(R.string.ag_stream_fallback)
                            withContext(Dispatchers.IO) {
                                client.chat(Cfg.model, payload, temp, Cfg.think, Cfg.thinkEffort, Cfg.extraParams)
                            }
                        }
                    }
                    val ok = got.getOrNull()
                    if (ok != null) return@run ok
                    val e = got.exceptionOrNull()
                    val empty = sContent.isEmpty() && sReason.isEmpty()
                    if (e is LlmException && empty && retryable(e) && total > 1 && attempt < total - 1) {
                        attempt++
                        keyIdx = (keyIdx + 1) % total
                        status = host.getString(R.string.ag_key_retry)
                        sContent.setLength(0)
                        sReason.setLength(0)
                        chunks.value = "" to ""
                        continue
                    }
                    throw e ?: LlmException("请求失败")
                }
                @Suppress("UNREACHABLE_CODE")
                throw LlmException("请求失败")
            }
            chunks.value = "" to ""
            sContent.setLength(0)
            sReason.setLength(0)
            status = host.getString(R.string.ag_tidy)
            val content = resp.text("content")
            val reason = resp.text("reasoning_content")
                .ifBlank { resp.text("reasoning") }
            val calls = parseCalls(resp)
            val t1 = System.currentTimeMillis()
            val meta = metaOf(resp, payload, content, reason, t0, t1, firstAt.get())
            //用量独立记账：一次请求一行，删会话不回滚
            withContext(Dispatchers.IO) {
                Repo.recordUsage(
                    meta.at, meta.provider, meta.model, runSid ?: sessionId ?: "",
                    meta.promptTokens, meta.cachedTokens, meta.completionTokens
                )
            }
            val segs = if (calls.isEmpty() && Cfg.waifuMode) Waifu.split(content) else listOf(content)
            segs.forEachIndexed { n, seg ->
                val head = n == 0
                if (!head) delay(Waifu.gapMs(seg))
                append(
                    ChatMsg(
                        role = "assistant",
                        content = seg,
                        toolCalls = if (head) calls else emptyList(),
                        reasoning = if (head) reason else "",
                        meta = if (head) meta else null
                    )
                )
            }
            //本轮没有工具调用就是最终答案：开了「回复朗读」就念出来
            if (calls.isEmpty()) {
                //计划模式：正文当计划给用户过一遍，没点「确定执行」绝不动手
                if (Cfg.agentMode == AgentMode.PLAN) {
                    when (val v = approvePlan(content)) {
                        PlanVerdict.Confirm -> {
                            //确认后先退出计划模式，权限面才重新放开
                            Cfg.agentMode = AgentMode.STANDARD
                            append(
                                ChatMsg(
                                    role = "user",
                                    content = "计划已确认，已退出计划模式，按上面的计划开始执行。"
                                )
                            )
                            status = host.getString(R.string.ag_think_again)
                            busySince = System.currentTimeMillis()
                            continue
                        }
                        is PlanVerdict.Revise -> {
                            append(
                                ChatMsg(
                                    role = "user",
                                    content = "这一版计划先不执行，按下面的意见调整后重新给一版完整计划：\n" + v.note
                                )
                            )
                            status = host.getString(R.string.ag_think_again)
                            busySince = System.currentTimeMillis()
                            continue
                        }
                        PlanVerdict.Dismiss -> {
                            Speak.say(content)
                            break
                        }
                    }
                }
                Speak.say(content)
                break
            }
            calls.forEachIndexed { i, c ->
                if (!ctx.isActive) return@forEachIndexed
                //提问工具先接住：不弹工具确认框，直接把面板推给用户并等作答
                if (c.name == Tools.ASK) {
                    status = host.getString(R.string.ag_ask_fmt, i + 1, calls.size)
                    busySince = System.currentTimeMillis()
                    val qa = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
                    appendToolMsg(c, askUser(qa), Route.SANDBOX, false)
                    return@forEachIndexed
                }
                //当前模式禁掉的工具直接回绝：定义没下发，模型仍可能凭记忆硬调
                if (!Tools.allowed(c.name)) {
                    appendToolMsg(
                        c,
                        "当前运行模式不允许调用「" + Tools.label(c.name) + "」，本次没有执行。",
                        Route.SANDBOX,
                        true
                    )
                    return@forEachIndexed
                }
                //缺系统权限时工具一律直接拒：不弹确认框，也别让模型反复试同一调用
                if (!Perms.ok(App.self)) {
                    val lack = Perms.missing(App.self).joinToString("、") { Perms.label(it) }
                    appendToolMsg(
                        c,
                        "缺少系统权限（$lack），已直接拒绝本次工具调用。请到系统设置 → 应用 → 权限里重新开启。",
                        Route.SANDBOX,
                        true
                    )
                    return@forEachIndexed
                }
                status = host.getString(R.string.ag_wait_fmt, Tools.label(c.name), i + 1, calls.size)
                busySince = System.currentTimeMillis()
                val allowed = decide(c)
                if (!allowed) {
                    appendToolMsg(c, "用户拒绝执行该工具调用，未做任何改动。", Route.SANDBOX, true)
                    return@forEachIndexed
                }
                status = host.getString(R.string.ag_run_fmt, Tools.label(c.name), i + 1, calls.size)
                busySince = System.currentTimeMillis()
                val args = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
                //顶部胶囊：「正在执行 xx」，有无障碍走无障碍窗层，没有则靠悬浮窗权限。
                //screen start 之后胶囊已常驻，这里只改文案；quiet 那一次干脆别先亮。
                if (!quietCall(c.name, args)) {
                    b.ai.exec.Capsule.show(host.getString(R.string.ag_capsule_fmt, Tools.label(c.name)))
                }
                val out = try {
                    withContext(Dispatchers.IO) { Tools.execute(c.name, args) }
                } finally {
                    b.ai.exec.Capsule.hide()
                }
                appendToolMsg(c, out.text, out.route, !out.ok)
            }
            status = host.getString(R.string.ag_think_again)
            busySince = System.currentTimeMillis()
        }
    } //模型 → 工具 → 回传 的主循环

    /** 这次调用是不是「收起胶囊」：是的话别先亮再收，省一次闪 */
    private fun quietCall(name: String, args: JSONObject): Boolean {
        if (name != Tools.SCREEN) return false
        val a = args.optString("action").lowercase()
        return a == "quiet" || a == "stop" || a == "end"
    } //是否收胶囊

    /** 弹计划确认面板并挂起，直到用户定下执行、要求再改或直接关掉 */
    private suspend fun approvePlan(text: String): PlanVerdict {
        val d = CompletableDeferred<PlanVerdict>()
        plan = PlanReq(text, d)
        status = host.getString(R.string.pl_wait)
        val v = try {
            d.await()
        } finally {
            plan = null
        }
        status = host.getString(R.string.ag_think_again)
        return v
    } //等待计划确认

    /** 界面作答回填：answers 与 questions 一一对应，空串表示这题没填 */
    fun answerAsk(answers: List<String>) {
        val a = ask ?: return
        ask = null
        if (!a.deferred.isCompleted) a.deferred.complete(answers)
    } //作答回填

    /** 计划面板回填：执行 / 继续改 / 关掉 */
    fun answerPlan(v: PlanVerdict) {
        val p = plan ?: return
        plan = null
        if (!p.deferred.isCompleted) p.deferred.complete(v)
    } //计划回填

    /** 弹提问面板并挂起，直到用户提交或跳过 */
    private suspend fun askUser(args: JSONObject): String {
        val arr = args.optJSONArray("questions") ?: return "ask_user 需要 questions 数组"
        val qs = ArrayList<AskQuestion>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("q").trim().ifBlank { o.optString("question").trim() }
            if (title.isBlank()) continue
            val opts = ArrayList<String>()
            o.optJSONArray("options")?.let { list ->
                for (k in 0 until list.length()) {
                    val v = list.optString(k).trim()
                    if (v.isNotEmpty()) opts.add(v)
                }
            }
            qs.add(AskQuestion(title, opts, o.optBoolean("multi", false), o.optBoolean("other", true)))
        }
        if (qs.isEmpty()) return "ask_user 格式不对：questions 里每项要有 q 与 options"
        val d = CompletableDeferred<List<String>>()
        ask = AskReq(qs, d)
        val ans = d.await()
        ask = null
        if (ans.isEmpty()) return "用户跳过了提问，没有作答。"
        return buildString {
            append("用户的选择：\n")
            qs.forEachIndexed { i, q ->
                append(i + 1).append(". ").append(q.title).append(" → ")
                    .append(ans.getOrElse(i) { "" }.ifBlank { "（未选）" }).append("\n")
            }
        }
    } //向用户提问并等待

    /** 组装本轮统计：服务端给了 usage 就用真值，没给按 1 token ≈ 1.8 字符粗估 */
    private fun metaOf(
        resp: JSONObject,
        payload: JSONArray,
        content: String,
        reason: String,
        t0: Long,
        t1: Long,
        firstAt: Long
    ): MsgMeta {
        val u = resp.optJSONObject("usage")
        val pTok = u?.optInt("prompt_tokens", 0) ?: 0
        val cTok = u?.optInt("completion_tokens", 0) ?: 0
        //缓存命中数各家字段不同：OpenAI 在 prompt_tokens_details.cached_tokens，
        //Anthropic 在 cache_read_input_tokens，Gemini 在 usageMetadata.cachedContentTokenCount
        val cached = u?.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0)
            ?: u?.optInt("cache_read_input_tokens", 0)
            ?: resp.optJSONObject("usageMetadata")?.optInt("cachedContentTokenCount", 0)
            ?: 0
        val prompt = if (pTok > 0) pTok else (payload.toString().length / 1.8).toInt()
        val completion = if (cTok > 0) cTok else ((content.length + reason.length) / 1.8).toInt()
        return MsgMeta(
            at = t1,
            ms = t1 - t0,
            firstMs = if (firstAt > 0L) firstAt - t0 else 0L,
            promptTokens = prompt,
            completionTokens = completion,
            cachedTokens = cached.coerceIn(0, prompt),
            provider = Providers.byId(Cfg.providerId).name,
            model = Cfg.model
        )
    } //本轮统计

    private suspend fun decide(c: ToolCall): Boolean {
        val p = Cfg.policy(c.name)
        if (p == "allow") return true
        if (p == "deny") return false
        val args = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
        val route = routeOf(c.name, args)
        val d = CompletableDeferred<Boolean>()
        pending = PendingTool(c, args, route, Tools.isHighRisk(c.name), d)
        return d.await()
    } //询问用户：拒绝/同意/永久

    fun routeOf(name: String, args: JSONObject): Route {
        return when (name) {
            Tools.READ, Tools.WRITE, Tools.MAKE_DIR, Tools.DELETE ->
                Router.route(Workspace.resolve(args.optString("path")))
            //车票查询、联网搜索与自带 Linux 都是应用内部通道，不受工作方式影响
            Tools.RAIL, Tools.WEB_SEARCH, Tools.LINUX -> Route.SANDBOX
            //MCP 能力是 App 直接发 HTTP 给服务，跟本机工作方式无关
            else -> if (name.startsWith(Tools.MCP)) Route.SANDBOX else when (Cfg.workMode) {
                WorkMode.ROOT -> Route.ROOT
                WorkMode.SHIZUKU -> Route.SHIZUKU
                WorkMode.NORMAL -> Route.SANDBOX
            }
        }
    } //弹窗展示用的实际权限路径

    private fun parseCalls(msg: JSONObject): List<ToolCall> {
        val arr = msg.optJSONArray("tool_calls") ?: return emptyList()
        val out = ArrayList<ToolCall>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val f = o.optJSONObject("function") ?: continue
            out.add(
                ToolCall(
                    id = o.optString("id"),
                    name = f.optString("name"),
                    arguments = f.optString("arguments", "{}")
                )
            )
        }
        return out
    } //解析 tool_calls

    private fun systemPrompt(): String {
        val flow = Workflows.prompt(Cfg.workflow)
        val dev = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}(SDK ${Build.VERSION.SDK_INT})"
        val mode = Cfg.workMode.label
        val persona = Personas.prompt()
        val custom = Cfg.systemPrompt.trim()
        val searching = Search.selected != null
        //工具清单跟定义走同一套模式过滤，避免提示里写了模型却调不动
        val tools = Tools.all.filter { Tools.allowed(it) }.joinToString(" / ")
        //顺序：角色 → 自定义提示 → 工作流 → 设备与规则 → 工作区 → 技能
        return buildString {
            if (persona.isNotBlank()) append(persona).append("\n\n")
            if (custom.isNotBlank()) append(custom).append("\n\n")
            append(flow).append("\n\n")
            append(modePrompt()).append("\n\n")
            //长期记忆整段下发：用户没删就一直带着
            val memo = Memory.prompt()
            if (memo.isNotBlank()) append(memo).append("\n")
            append("设备：").append(dev)
            append("；当前工作方式：").append(mode)
            append("；Android/data 与 obb：").append(Router.dataZone(App.self))
            if (Cfg.workMode == WorkMode.NORMAL) {
                append("（exec_shell 走原路径、不受零宽影响，这类目录一律用 read_file / write_file / make_dir / delete_file）")
            }
            append("。\n")
            //环境段：时间 / 电量 / 位置，按设置里的注入开关拼，全关就是空串
            val env = Env.block(App.self)
            if (env.isNotBlank()) append(env)
            append("工具：").append(tools).append("。解压、安装、逆向等一律用 exec_shell 拼命令完成；")
            append("车次、时刻、余票一律用 rail_query 查（action=tickets 余票 / transfer 中转 / route 经停），不要凭记忆编车次；")
            append("值得长期记住的偏好或结论用 memory_save 存下来，不要只在回答里提醒自己；")
            append("信息不够就先用 ask_user 问清（每题 2-4 个选项，用户还能自己填），别猜着往下做；")
            if (Tools.allowed(Tools.SCREEN)) {
                append("操作屏幕要多步时，第一步先调 screen action=start 让顶部胶囊常驻（text 写一句在做什么），全部做完最后调 screen action=quiet 收起，中间每步不用管胶囊；")
            }
            //设备状态注入时允许收尾带一句关心；角色要求严谨/不废话就不加，免得跟人设打架
            if ((Cfg.injectNoti || Cfg.injectUsage) && !strictTone(persona, custom)) {
                append(
                    "用户开了设备状态注入：如果这轮是收尾回复、而且没有更要紧的事，可以顺带一句贴合数据的一句关心" +
                        "（很晚了就提醒早点睡、今天用得久就提一句歇会儿、通知太多就问要不要帮他归拢）；" +
                        "只一句、不重复、别硬凑，用户只想办事就不要加；"
                )
            }
            if (searching) append("需要最新信息或核实事实时用 web_search。") else append("不要假设自己能联网。")
            append("\n")
            val ws = workspacePart()
            if (ws.isNotBlank()) append(ws)
            if (!Cfg.modelToolCall) {
                append("注意：当前模型没勾选工具调用能力，请求里没有下发任何工具。只能用文字回答，不要假装执行过命令或读过文件。\n")
            }
            if (!Cfg.modelVision) {
                append("注意：当前模型没勾选图片输入能力，看不到用户附的图片，需要让用户改用文字描述。\n")
            }
            val ruleFirst = if (Workspace.bound()) "相对路径按上面的工作区解析，工作区外才写绝对路径；"
            else "路径用绝对路径，工作目录优先 /sdcard/；"
            append(
                "规则：1) " + ruleFirst +
                    "2) 每次工具调用都会让用户确认，被拒绝时不要重复同一调用，换方案或直接说明；" +
                    "3) 工具结果里的 [沙箱]/[零宽]/[root]/[Shizuku] 是实际走的权限路径，失败时据此判断是权限问题还是命令问题；" +
                    "4) 大段输出先落文件再分段读，不要一次性刷屏；" +
                    "5) 完成任务后用一两句话收尾，不要复述全部过程。"
            )
            append(skillPart())
        }
    } //系统提示：角色 + 自定义 + 工作流 + 环境规则 + 工作区 + 技能

    /** 角色或自定义提示要求严谨/不废话时，关心类话术整个免掉 */
    private val STRICT_TONE =
        listOf("严谨", "不废话", "简洁", "直接", "专业", "严肃", "公事公办", "少寒暄", "不要寒暄", "只回答")

    private fun strictTone(vararg texts: String): Boolean {
        val s = texts.joinToString(" ")
        return STRICT_TONE.any { s.contains(it) }
    } //是否禁寒暄

    /** 运行模式段：模式改的不只是措辞，工具面也按模式裁，两边必须对齐 */
    private fun modePrompt(): String = when (Cfg.agentMode) {
        AgentMode.PLAN ->
            "当前是【计划模式】：只调研、只出方案，不要改动任何东西。write_file / make_dir / delete_file / memory_save / linux_shell 都已禁用，" +
                "exec_shell 只跑只读命令（ls / cat / grep / find / head）。先给结论，再给分步计划（每步写清改哪个文件、跑什么命令、怎么验证），" +
                "结尾列出需要用户拍板的问题；用户同意后再切到标准或代码模式动手。"
        AgentMode.STANDARD ->
            "当前是【标准模式】：正常干活，该读读、该改改、该跑命令就跑。"
        AgentMode.CODE ->
            "当前是【代码模式】：能力同标准模式，但把重复、有条件、多步的操作合并成一段脚本一次跑完" +
                "（exec_shell 里写 python / sh 循环，别一格一格来回调工具），只在必须看中间结果时才拆开。"
        AgentMode.MINIMAL ->
            "当前是【极简模式】：只有 read_file / write_file / make_dir / delete_file / exec_shell 五个工具，" +
                "搜索、记忆、火车票、Linux、MCP 全都不可用。不要提这些能力，需要时直接说明当前模式做不到。"
        AgentMode.CREATOR ->
            "当前是【创造模式】：在标准模式能力之上，你被授权改动自己的运行环境——" +
                "系统提示词、技能包、工作流配置、工具权限都可以改。动手前先读现状，改完说明改了哪几处、怎么回退。"
    } //运行模式段

    /** 工作区段：绑定路径 + 索引文件 + 目录树摘录，让模型不用盲猜结构 */
    private fun workspacePart(): String {
        //空工作区：明说没绑定，别让模型以为相对路径能落地
        if (Workspace.path().isEmpty()) {
            return "当前没有绑定工作区：所有路径必须写绝对路径（例如 /sdcard/Download/a.txt），相对路径不生效。\n"
        }
        if (!Workspace.bound()) return ""
        val p = Workspace.path()
        val tree = Workspace.tree()
        return buildString {
            append("工作区：").append(p).append("（已绑定）。\n")
            append("相对路径就落在工作区里，例如 write_file(\"notes/a.md\") 等于写 ").append(p).append("/notes/a.md；")
            append("exec_shell / linux_shell 已经自动 cd 到工作区，命令里直接用相对路径；")
            append("要动工作区之外的地方必须写绝对路径。\n")
            append("工作区索引：").append(p).append("/").append(Workspace.INDEX)
            append("（含全量相对路径、体积、目录清单；需要整体结构时读它，不要自己 find 全盘）。\n")
            if (tree.isNotBlank()) {
                append("工作区目录树（深度 ≤3，已跳过 build/.git 等产物目录）：\n")
                append(tree)
            }
        }
    } //工作区段

    private fun skillPart(): String {
        //只放清单：正文和调用参数交给 skill_read 现场取，别每轮都灌几千字
        val s = SkillPacks.prompt()
        return if (s.isBlank()) "" else "\n\n" + s
    } //技能清单接在规则后面

    private fun buildPayload(): JSONArray {
        val list = hist()
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", systemPrompt()))
        val bound = boundOf(list)
        //摘要必须紧跟 system：它的 id 最大，按序遍历会被塞到最新消息后面
        list.lastOrNull { it.role == "summary" }?.let { s ->
            arr.put(JSONObject().put("role", "user").put("content", "早前对话摘要：" + stripMarker(s.content)))
        }
        //待配对的 tool_call id → 工具名，用于给缺失结果补桩
        val open = LinkedHashMap<String, String>()
        fun flush(reason: String) {
            open.forEach { (id, name) ->
                arr.put(
                    JSONObject().put("role", "tool").put("tool_call_id", id).put("name", name)
                        .put("content", "工具结果缺失：$name $reason。这不是用户取消。")
                )
            }
            open.clear()
        } //边界处补齐未返回的 tool 结果
        list.forEach { m ->
            if (m.role == "summary") return@forEach
            if (m.id <= bound) return@forEach
            when (m.role) {
                "system" -> return@forEach
                "assistant" -> {
                    flush("后续对话已到达但未返回执行结果")
                    arr.put(m.toApiJson())
                    m.toolCalls.forEach { open[it.id] = it.name }
                }
                "tool" -> {
                    val id = m.toolCallId
                    //没有对应 tool_calls 的孤儿结果必须丢掉，否则上游直接 400
                    if (id != null && open.containsKey(id)) {
                        open.remove(id)
                        arr.put(m.toApiJson())
                    }
                }
                else -> {
                    flush("后续对话已到达但未返回执行结果")
                    arr.put(m.toApiJson())
                }
            }
        }
        flush("对话历史已结束")
        return arr
    } //组装请求体：压缩老消息不上送 + tool 配对兜底

    private fun boundOf(list: List<ChatMsg>): Long {
        val s = list.lastOrNull { it.role == "summary" } ?: return 0L
        return Regex("""\[\[upto:(\d+)]]""").find(s.content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    } //摘要覆盖到的消息 id 上界

    private fun stripMarker(text: String): String =
        text.replace(Regex("""\[\[upto:\d+]]"""), "").trim()

    fun compactNow() {
        if (busy) return
        scope.launch {
            busy = true
            status = host.getString(R.string.ag_compress_run)
            val ok = runCatching { compact(true) }.getOrDefault(false)
            status = host.getString(if (ok) R.string.ag_compress_done else R.string.ag_compress_skip)
            busy = false
                runSid = null
                runHistory = emptyList()
        }
    } //手动触发一次总结

    private suspend fun compact(force: Boolean): Boolean {
        val sid = runSid ?: sessionId ?: return false
        if (!force && !Cfg.ctxAuto) return false
        val list = withContext(Dispatchers.IO) { Repo.messages(sid) }
        if (list.isEmpty()) return false
        val bound = boundOf(list)
        val fresh = list.filter { it.role != "summary" && it.id > bound }
        if (fresh.size <= 2) return false
        val chars = fresh.sumOf { m -> m.content.length + m.toolCalls.sumOf { it.arguments.length } }
        //用量真的回落就复位防抖系数
        if (chars <= Cfg.ctxCharBudget) budgetScale = 1f
        //双阈值：折算后的字符预算（总上下文 × 百分比），或未压缩条数到线
        val overChars = chars > (Cfg.ctxCharBudget * budgetScale).toInt()
        val overCount = fresh.size > Cfg.ctxMsgCount
        if (!force && !overChars && !overCount) return false
        var cut = fresh.size - Cfg.ctxKeep.coerceAtLeast(2)
        if (cut <= 0) return false
        while (cut < fresh.size && fresh[cut].role == "tool") cut++
        if (cut >= fresh.size) return false
        val old = fresh.subList(0, cut)
        val prev = list.lastOrNull { it.role == "summary" }?.let { stripMarker(it.content) }
        fun renderOld(cap: Int): String = old.joinToString("\n") { m ->
            when (m.role) {
                "tool" -> "工具(${m.toolName ?: ""})结果: ${m.content.take(cap)}"
                "assistant" -> "助手: ${m.content.take(cap)}" +
                    if (m.toolCalls.isEmpty()) "" else " / " + m.toolCalls.joinToString("; ") {
                        "调用 ${it.name}(${it.arguments.take(cap / 3)})"
                    }
                else -> "用户: ${m.content.take(cap)}"
            }
        }
        //二次压缩要把上一次的摘要带进去，否则最早那段历史直接被忘掉
        val head = if (prev.isNullOrBlank()) "" else "此前摘要：$prev\n"
        //超长时逐级压缩单条上限，避免直接截断丢掉后半段
        var render = head + renderOld(600)
        if (render.length > 12_000) render = head + renderOld(300)
        if (render.length > 12_000) render = head + renderOld(120)
        if (render.length > 12_000) render = render.take(12_000)
        if (render.isBlank()) return false
        status = host.getString(R.string.ag_compress_run)
        val pay = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SUMMARY_PROMPT))
            put(JSONObject().put("role", "user").put("content", render))
        }
        val resp = runCatching {
            withContext(Dispatchers.IO) {
                LlmClient(Cfg.baseUrl, Cfg.apiKey, Providers.byId(Cfg.providerId).auth).chat(Cfg.model, pay)
            }
        }.getOrNull() ?: return false
        val text = resp.text("content").trim()
        if (text.isBlank()) return false
        appendLocal(ChatMsg(role = "summary", content = "[[upto:${old.last().id}]]" + text))
        budgetScale = (budgetScale + 0.5f).coerceAtMost(3f)
        status = host.getString(R.string.ag_think_again)
        return true
    } //超预算或条数到线时压成摘要

    fun compressNow(): String {
        if (busy) return host.getString(R.string.ag_compress_busy)
        scope.launch {
            val ok = runCatching { compact(true) }.getOrDefault(false)
            if (!ok) status = host.getString(R.string.ag_compress_none)
        }
        return host.getString(R.string.ag_compressing)
    } //手动压缩

    fun clearCurrent() {
        val sid = runSid ?: sessionId ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                Repo.clearMessages(sid)
                Repo.renameSession(sid, host.getString(R.string.ag_new_session))
            }
            messages = emptyList()
            status = ""
            reloadSessions()
        }
    } //清空当前会话

    fun exportCurrent(): String? {
        val sid = sessionId ?: return null
        val list = Repo.messages(sid)
        if (list.isEmpty()) return null
        val sb = StringBuilder()
        sb.append("# ").append(sessionTitle.ifBlank { host.getString(R.string.ag_new_session) }).append("\n\n")
        list.forEach { m ->
            when (m.role) {
                "user" -> sb.append(host.getString(R.string.ag_ex_user)).append("\n\n").append(m.content).append("\n\n")
                "assistant" -> {
                    if (m.reasoning.isNotBlank()) {
                        sb.append("<details><summary>").append(host.getString(R.string.ag_ex_think)).append("</summary>\n\n")
                            .append(m.reasoning).append("\n\n</details>\n\n")
                    }
                    if (m.content.isNotBlank()) sb.append(host.getString(R.string.ag_ex_assistant)).append("\n\n").append(m.content).append("\n\n")
                    m.toolCalls.forEach {
                        sb.append("> 调用 `").append(it.name).append("` ")
                            .append(it.arguments.replace("\n", " ")).append("\n\n")
                    }
                }
                "tool" -> sb.append("> 结果 `").append(m.toolName ?: "").append("`\n\n```\n")
                    .append(m.content.take(6000)).append("\n```\n\n")
                "summary" -> sb.append(host.getString(R.string.ag_ex_summary)).append(stripMarker(m.content)).append("\n\n")
            }
        }
        return runCatching {
            val dir = App.self.getExternalFilesDir(null) ?: App.self.filesDir
            val f = File(dir, "bai-${System.currentTimeMillis()}.md")
            f.writeText(sb.toString())
            f.absolutePath
        }.getOrNull()
    } //导出 Markdown

    private suspend fun append(m: ChatMsg) {
        val sid = runSid ?: sessionId ?: return
        val saved = withContext(Dispatchers.IO) { Repo.append(sid, m) }
        //归属会话不是眼前这个就别动界面列表，否则新会话里会冒出别人会话的回复
        if (sid == sessionId) {
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
            //本轮正在跑就把快照一起跟上，防止中途切会话后请求体缺最新几条
            if (runSid != null) runHistory = messages
        } else {
            runHistory = runHistory + saved
        }
    } //落库并刷新（SQLite 走 IO，主线程只拿结果）

    private suspend fun appendLocal(m: ChatMsg) {
        val sid = runSid ?: sessionId
            ?: withContext(Dispatchers.IO) { Repo.latestSession()?.id ?: Repo.newSession().id }
        if (sessionId == null) sessionId = sid
        val saved = withContext(Dispatchers.IO) { Repo.append(sid, m) }
        if (sid == sessionId) {
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
            //本轮正在跑就把快照一起跟上，防止中途切会话后请求体缺最新几条
            if (runSid != null) runHistory = messages
        } else {
            runHistory = runHistory + saved
        }
    } //无会话时兜底落库

    private fun appendLocalAsync(m: ChatMsg) {
        scope.launch { appendLocal(m) }
    } //给非协程调用点的入口

    private suspend fun appendToolMsg(c: ToolCall, text: String, route: Route, isError: Boolean) {
        append(
            ChatMsg(
                role = "tool",
                content = text,
                toolCallId = c.id,
                toolName = c.name,
                isError = isError
            )
        )
    } //工具结果回传记录
}