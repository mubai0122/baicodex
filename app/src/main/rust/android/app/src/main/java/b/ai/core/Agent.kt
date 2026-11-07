package b.ai.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.native.AppEvent
import b.ai.native.Kernel
import b.ai.native.KernelException
import b.ai.native.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 界面看到的"智能体"。
 *
 * ## 这个类现在只做三件事
 *
 * 1. **持状态**：Compose 要重组，所以状态必须是 `mutableStateOf`。状态的所有权
 *    在内核——这里是一份**镜像**，只由事件流写，同一份数据不会有第二个来源。
 * 2. **转事件**：把 [AppEvent] 落到上面那些字段。
 * 3. **转调用**：把界面动作翻译成 [Kernel] 调用，跑在 IO 线程。
 *
 * ## 为什么逻辑一行都不在这里
 *
 * 主循环、上下文压缩、工具治理、重试、分支——全在 Rust。Android 与终端用的是
 * 同一套。这里写一行 `if (重试)`，两个前端就开始不一致。
 */
object Agent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pumpJob: Job? = null

    // —— 界面读的状态 ——

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
    var runningTools by mutableStateOf<List<RunningTool>>(emptyList())
        private set
    var ask by mutableStateOf<AskReq?>(null)
        private set
    var plan by mutableStateOf<PlanReq?>(null)
        private set
    var sessionId by mutableStateOf<String?>(null)
        private set
    var sessionTitle by mutableStateOf("")
        private set
    var sessions by mutableStateOf<List<SessionRow>>(emptyList())
        private set

    /** 最近一次错误，界面显示红色提示；下一次成功自动清掉。 */
    var lastError by mutableStateOf<String?>(null)
        private set

    /**
     * 启动。
     *
     * @param base 数据根。调用方给 `context.filesDir/baicode`。
     * @param onFatal 内核加载失败时的回调（一般是弹一句"内核未就绪"）。
     */
    fun start(base: String, onFatal: (String) -> Unit = {}) {
        if (pumpJob != null) return
        pumpJob = scope.launch {
            NativeBridge.loadError()?.let {
                onFatal("内核未加载：$it")
                return@launch
            }
            // 事件必须在 boot 之前订阅：boot 会立刻推到"会话已就绪"之类的早期事件。
            launch { pump() }
            runCatching { Kernel.boot(base) }
                .onFailure { onFatal(it.message ?: "内核启动失败") }
            refreshSnapshot()
            refreshMessages()
        }
    }

    private suspend fun pump() {
        NativeBridge.events.collect { apply(it) }
    }

    private fun apply(e: AppEvent) {
        when (e) {
            is AppEvent.Status -> status = e.text
            is AppEvent.Delta -> {
                if (e.content.isNotEmpty()) {
                    // 流式正文只在内存里拼，落库由内核负责；结束后 refreshMessages 会拿到正本。
                    streamText += e.content
                }
                if (e.reason.isNotEmpty()) streamReason += e.reason
                busy = true
            }
            is AppEvent.UserMessage -> {
                // 内核已经落库，这里只把正文接上，避免"发出去半天没反应"。
                flushStream()
                refreshMessages()
            }
            is AppEvent.Message -> {
                flushStream()
                refreshMessages()
            }
            is AppEvent.ToolStart -> {
                runningTools = runningTools + RunningTool(e.seq, e.name, e.args, e.fromSub)
                status = "调用 ${e.name}…"
            }
            is AppEvent.ToolEnd -> runningTools = runningTools.filterNot { it.seq == e.seq }
            is AppEvent.Busy -> {
                busy = e.busy
                if (e.busy) {
                    busySince = System.currentTimeMillis()
                } else {
                    runningTools = emptyList()
                    flushStream()
                    refreshMessages()
                }
            }
            is AppEvent.Sessions -> {
                sessions = parseSessions(e.list)
                sessionId?.let { id -> sessions.firstOrNull { it.id == id }?.let { sessionTitle = it.title } }
            }
            is AppEvent.Notice -> status = e.text
            // 事件只说"有事等你"，具体内容要再问一次内核：事件体不带载荷，
            // 免得把工具参数、问题正文塞进每一份事件副本里广播。
            is AppEvent.ConfirmRequest, is AppEvent.AskRequest, is AppEvent.PlanRequest ->
                scope.launch { refreshPending() }
            is AppEvent.Usage -> Unit
            is AppEvent.Error -> {
                lastError = e.message
                status = e.message
                busy = false
            }
            AppEvent.Quit -> {
                busy = false
                runningTools = emptyList()
            }
        }
    }

    /** 把流式缓冲收进消息列表（消息本身由内核落了，这里只是清缓冲）。 */
    private fun flushStream() {
        streamText = ""
        streamReason = ""
    }

    // —— 会话 ——

    fun ensureSession() = scope.launch {
        runCatching { Kernel.snapshot() }
        refreshSnapshot()
        refreshMessages()
    }

    fun refreshSessions() = scope.launch {
        runCatching { sessions = parseSessions(Kernel.sessions()) }
    }

    fun newSession() = scope.launch {
        runCatching {
            Kernel.newSession()
            refreshSnapshot()
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun openSession(id: String) = scope.launch {
        runCatching {
            Kernel.openSession(id)
            refreshSnapshot()
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun renameSession(id: String, title: String) = scope.launch {
        runCatching {
            Kernel.renameSession(id, title)
            refreshSessions()
            if (id == sessionId) sessionTitle = title
        }.onFailure { fail(it) }
    }

    fun pinSession(id: String, on: Boolean) = scope.launch {
        runCatching { Kernel.pinSession(id, on); refreshSessions() }.onFailure { fail(it) }
    }

    fun deleteSession(id: String) = scope.launch {
        runCatching {
            Kernel.deleteSession(id)
            if (id == sessionId) {
                Kernel.ensureCurrent()
                refreshSnapshot()
                refreshMessages()
            }
            refreshSessions()
        }.onFailure { fail(it) }
    }

    fun deleteSessions(ids: Set<String>) = scope.launch {
        runCatching {
            ids.forEach { Kernel.deleteSession(it) }
            if (ids.contains(sessionId)) {
                Kernel.ensureCurrent()
                refreshSnapshot()
                refreshMessages()
            }
            refreshSessions()
        }.onFailure { fail(it) }
    }

    fun setPersona(personaId: String) = scope.launch {
        runCatching {
            val sid = sessionId ?: return@runCatching
            Kernel.setSessionPersona(sid, personaId)
            Kernel.patchConfig(JSONObject().put("agent", JSONObject().put("persona", personaId)))
        }.onFailure { fail(it) }
    }

    fun setSessionWorkspace(path: String) = scope.launch {
        runCatching {
            val sid = sessionId ?: return@runCatching
            Kernel.setSessionWorkspace(sid, path)
        }.onFailure { fail(it) }
    }

    // —— 消息 ——

    fun send(text: String, images: List<String> = emptyList(), files: List<String> = emptyList()) =
        scope.launch {
            lastError = null
            runCatching { Kernel.send(text, images, files) }
                .onFailure { fail(it) }
            flushStream()
            refreshMessages()
            refreshSessions()
        }

    fun regenerate() = scope.launch {
        lastError = null
        runCatching { Kernel.regenerate() }.onFailure { fail(it) }
        flushStream()
        refreshMessages()
    }

    /** 编辑后重发：删掉那条及其之后的全部，再用新文本跑一轮。 */
    fun editResend(
        msgId: Long,
        text: String,
        images: List<String> = emptyList(),
        files: List<String> = emptyList()
    ) = scope.launch {
        lastError = null
        runCatching {
            Kernel.editResend(msgId, text, images, files)
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun toggleStar(m: ChatMsg, on: Boolean) = scope.launch {
        runCatching {
            Kernel.starMessage(m.id, on)
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun deleteMessage(m: ChatMsg) = scope.launch {
        runCatching {
            Kernel.deleteMessage(m.id)
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun switchBranch(m: ChatMsg) = scope.launch {
        runCatching {
            Kernel.switchBranch(m.id)
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun stop() = scope.launch {
        runCatching { Kernel.stop() }.onFailure { fail(it) }
    }

    /** 压缩上下文。返回省下的字符数，界面给个提示。 */
    fun compact() = scope.launch {
        runCatching {
            val saved = Kernel.compact()
            status = "已压缩，省下约 $saved 字符"
            refreshMessages()
        }.onFailure { fail(it) }
    }

    fun recompact() = compact()
    fun compressNow() = compact()

    fun clearCurrent() = scope.launch {
        runCatching {
            Kernel.clearMessages()
            refreshMessages()
        }.onFailure { fail(it) }
    }

    /** 导出当前会话为 Markdown，交给系统分享。 */
    fun exportCurrent(): String = runCatching {
        NativeBridge.callBlocking("export_md").optString("data")
    }.getOrDefault("")

    /** 当前会话的 token 用量。 */
    fun sessionUsage(): Pair<Long, Long> = runCatching {
        val d = NativeBridge.callBlocking(
            "usage_session",
            JSONObject().put("id", sessionId ?: "")
        ).optJSONObject("data")
        (d?.optLong("prompt") ?: 0L) to (d?.optLong("completion") ?: 0L)
    }.getOrDefault(0L to 0L)

    fun stats(): Pair<Int, Int> {
        val (p, c) = sessionUsage()
        val sid = sessionId ?: return 0 to 0
        val n = runCatching {
            NativeBridge.callBlocking("session_stats", JSONObject().put("id", sid))
                .optJSONObject("data")?.optInt("messages") ?: 0
        }.getOrDefault(0)
        return n to (p + c).toInt()
    }

    // —— 挂起的交互 ——

    fun resolve(allow: Boolean, permanent: Boolean = false) = scope.launch {
        pending = null
        runCatching { Kernel.confirmTool(allow, permanent) }.onFailure { fail(it) }
    }

    fun answerAsk(answers: List<String>) = scope.launch {
        ask = null
        runCatching { Kernel.answerAsk(answers) }.onFailure { fail(it) }
    }

    fun answerPlan(confirm: Boolean, note: String = "") = scope.launch {
        plan = null
        runCatching {
            Kernel.answerPlan(if (confirm) "confirm" else "revise", note)
        }.onFailure { fail(it) }
    }

    /** 当前正在跑的会话是不是这个（多会话切换时界面用它决定要不要显示转圈）。 */
    fun runningHere(): Boolean = busy

    // —— 内部 ——

    fun refreshMessages() = scope.launch {
        runCatching {
            messages = parseMessages(Kernel.messages())
        }.onFailure { fail(it) }
    }

    /** 拉一次挂起交互的详情（确认 / 提问 / 计划三选一）。 */
    private suspend fun refreshPending() {
        val p = runCatching { Kernel.pending() }.getOrNull() ?: return
        val kind = p.optString("kind")
        pending = if (kind == "confirm") {
            p.optJSONObject("tool")?.let {
                PendingTool(
                    call = ToolCall("", it.optString("name"), it.optString("args")),
                    args = runCatching { JSONObject(it.optString("args")) }.getOrDefault(JSONObject()),
                    route = routeOf(it.optString("route")),
                    highRisk = it.optBoolean("high_risk")
                )
            }
        } else {
            null
        }
        ask = if (kind == "ask") {
            val qs = ArrayList<AskQuestion>()
            val arr = p.optJSONArray("questions")
            for (i in 0 until (arr?.length() ?: 0)) {
                val o = arr?.optJSONObject(i) ?: continue
                val opts = ArrayList<AskOption>()
                val oa = o.optJSONArray("options")
                for (j in 0 until (oa?.length() ?: 0)) {
                    val oo = oa?.optJSONObject(j) ?: continue
                    opts.add(AskOption(oo.optString("label"), oo.optString("desc")))
                }
                qs.add(
                    AskQuestion(
                        id = o.optString("id"),
                        question = o.optString("question"),
                        options = opts,
                        multi = o.optBoolean("multi")
                    )
                )
            }
            AskReq(qs)
        } else {
            null
        }
        plan = if (kind == "plan") PlanReq(p.optString("plan")) else null
    }

    fun refreshSnapshot() = scope.launch {
        runCatching {
            val s = Kernel.snapshot()
            sessionId = s.optString("session").takeIf { it.isNotEmpty() }
            sessionTitle = s.optString("title")
            busy = s.optBoolean("busy")
            status = s.optString("status")
            lastError = s.optString("error").takeIf { it.isNotEmpty() }
            if (s.optString("waiting").isNotEmpty()) refreshPending()
            refreshSessions()
        }
    }

    private fun fail(t: Throwable) {
        if (t is KernelException) {
            lastError = t.userText()
            status = t.userText()
        } else {
            lastError = t.message ?: "未知错误"
            status = lastError ?: ""
        }
    }

    private fun parseSessions(a: JSONArray): List<SessionRow> = buildList {
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            add(
                SessionRow(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    category = o.optString("category"),
                    updatedAt = o.optLong("updated_at"),
                    preview = o.optString("preview"),
                    count = o.optInt("count"),
                    descr = o.optString("descr"),
                    pinned = o.optBoolean("pinned"),
                    persona = o.optString("persona"),
                    workspace = o.optString("workspace")
                )
            )
        }
    }

    private fun parseMessages(a: JSONArray): List<ChatMsg> = buildList {
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            add(parseMessage(o))
        }
    }

    private fun parseMessage(o: JSONObject): ChatMsg {
        val calls = ArrayList<ToolCall>()
        o.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                calls.add(ToolCall(c.optString("id"), c.optString("name"), c.optString("arguments")))
            }
        }
        return ChatMsg(
            id = o.optLong("id"),
            role = o.optString("role"),
            content = o.optString("content"),
            toolCalls = calls,
            toolCallId = o.optString("tool_call_id").takeIf { it.isNotEmpty() },
            toolName = o.optString("tool_name").takeIf { it.isNotEmpty() },
            isError = o.optBoolean("is_error"),
            reasoning = o.optString("reasoning"),
            images = o.optJSONArray("images").toStringList(),
            files = o.optJSONArray("files").toStringList(),
            meta = MsgMeta.of(o.optJSONObject("meta")),
            starred = o.optBoolean("starred"),
            parentId = o.optLong("parent_id"),
            bIdx = o.optInt("b_idx"),
            bTotal = o.optInt("b_total")
        )
    }
}

/** 挂起的工具确认。 */
data class PendingTool(
    val call: ToolCall,
    val args: JSONObject,
    val route: Route,
    val highRisk: Boolean
)

/** 正在跑的工具。 */
data class RunningTool(val seq: Long, val name: String, val args: String, val fromSub: Boolean)

/** 待回答的问题。 */
data class AskReq(val questions: List<AskQuestion>)

data class AskQuestion(
    val id: String,
    val question: String,
    val options: List<AskOption> = emptyList(),
    val multi: Boolean = false
)

data class AskOption(val label: String, val desc: String = "")

/** 待裁决的计划。 */
data class PlanReq(val text: String)

/** 字符串 → 访问通道。认不出就按沙箱处理（最保守的那个）。 */
private fun routeOf(id: String): Route = when (id) {
    "all_files" -> Route.ALL_FILES
    "zero_width" -> Route.ZERO_WIDTH
    "shizuku" -> Route.SHIZUKU
    "root" -> Route.ROOT
    else -> Route.SANDBOX
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
}
