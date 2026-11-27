/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:04
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.agent.run
import b.ai.agent.flow.FlowRunner
import b.ai.agent.sub.SubAgents
import b.ai.agent.sub.SubTask
import b.ai.agent.tool.Tools
import b.ai.core.model.chat.Sum

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.core.cfg.app.AgentMode
import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.app.PromptTags
import b.ai.core.cfg.emoji.Emojis
import b.ai.App
import b.ai.R
import java.io.File
import b.ai.core.model.chat.ChatMsg
import b.ai.core.cfg.env.Env
import b.ai.core.cfg.env.PromptVars
import b.ai.agent.flow.Flow
import b.ai.agent.flow.Flows
import b.ai.agent.memory.Memory
import b.ai.native.security.Mask
import b.ai.core.model.chat.MsgMeta
import b.ai.core.perm.Perms
import b.ai.agent.persona.Personas
import b.ai.core.data.profile.Profiles
import b.ai.core.plugin.skill.Providers
import b.ai.core.data.db.Repo
import b.ai.core.data.db.Sums
import b.ai.core.dsh.DshChat
import b.ai.core.dsh.DshEv
import b.ai.core.model.chat.Route
import b.ai.net.search.Search
import b.ai.core.model.chat.SessionRow
import b.ai.core.plugin.skill.Skills
import b.ai.core.plugin.skill.SkillPacks
import b.ai.core.model.voice.Speak
import b.ai.core.model.chat.ToolCall
import b.ai.device.sys.island.Island
import b.ai.core.pet.say.Waifu
import b.ai.core.model.chat.WorkMode
import b.ai.core.plugin.skill.Workflows
import b.ai.device.shell.local.Linux
import b.ai.device.sys.probe.Router
import b.ai.device.fs.Workspace
import b.ai.net.api.llm.LlmClient
import b.ai.net.api.llm.LlmException
import b.ai.net.api.llm.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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

/** 正在执行的工具：模型一返回调用就进这里，界面据此立刻弹出转圈卡片 */
data class RunningTool(val seq: Long, val name: String, val args: String, val fromSub: Boolean = false)

object Agent {
    /** 错误回复类型：模型 / 口令 / 地址没配好 —— 去配置落到「模型」页 */
    const val ERR_MODEL = "model"
    /** 错误回复类型：缺权限（无障碍 / 悬浮窗 / 其它授权）—— 去配置落到「外观」页的自查项 */
    const val ERR_PERM = "perm"
    /** 错误回复类型：本地环境没就绪（proot / 终端依赖）—— 去配置落到「扩展」页 */
    const val ERR_ENV = "env"

    //轮次不再设上限：循环只靠 ctx.isActive（用户停止）与「本轮没有工具调用」两条收敛
    //上下文压缩器与会话命名的提示词正文已移到 PromptStore（由 local.properties 种子注入）

    /** 压缩器单趟输入上限（字符）：一段历史超过它就切块分趟压，绝不 take(n) 切断后半段 */
    private const val COMPACT_CHUNK_CHARS = 24_000
    /** 分趟上限：块数再多就合并成大块（每块变大但总趟数可控，避免为一次压缩打十几炮） */
    private const val COMPACT_MAX_PASS = 6

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set
    var sessionId by mutableStateOf<String?>(null)
        private set
    var sessions by mutableStateOf<List<SessionRow>>(emptyList())
        private set
    var sessionTitle by mutableStateOf("")
        private set
    // ───────────────────────── 会话运行通道 ─────────────────────────
    //以前整台引擎只有一份运行态（busy / job / stream…）：任一会话在跑，别的会话发消息就只能排队，
    //还会被上一轮的 pump 牵走。现在按会话拆成 Run，一个会话一根通道，互相不排队、不打断；
    //界面只读「当前显示会话」那一根的状态，切会话自动跟着换。

    private val runs = HashMap<String, Run>()

    /**当前显示会话的运行通道：界面上的 running 状态全部从它读 */
    private var curRun by mutableStateOf<Run?>(null)

    private fun runOf(id: String): Run = runs.getOrPut(id) { Run(id) } //取会话的通道

    /**切会话时把界面绑定换到新会话的通道上 */
    private fun bindRun(id: String?) {
        curRun = id?.let { runOf(it) }
    } //绑定当前通道

    /**给通道用的窄口子：读写界面消息列表（messages 是 private set，通道不能直接改） */
    internal fun uiMessages(): List<ChatMsg> = messages
    internal fun uiMessages(v: List<ChatMsg>) {
        messages = v
    } //界面消息读写

    /**当前显示的是哪个会话：通道据此判断自己是不是在前台 */
    internal fun curSid(): String? = sessionId

    /**当前会话标题：灵动岛 / 通知的文案用 */
    internal fun curTitle(): String = sessionTitle

    /**取资源文案用；不叫 ctx 是因为 runLoop 里有同名局部变量 */
    private val host: Context get() = App.self

    /**会话层协程（列表刷新 / 建会话这类，不跟某一条通道绑死） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ──界面读的入口：一律转发到当前会话那一根通道 ──
    val busy: Boolean get() = curRun?.busy == true
    val busySince: Long get() = curRun?.busySince ?: 0L
    var status: String
        get() = curRun?.status ?: ""
        set(v) {
            curRun?.status = v
        }
    val streamText: String get() = curRun?.streamText ?: ""
    val streamReason: String get() = curRun?.streamReason ?: ""
    val typing: Boolean get() = curRun?.typing == true
    val pending: PendingTool? get() = curRun?.pending
    val runningTools: List<RunningTool> get() = curRun?.runningTools ?: emptyList()
    val ask: AskReq? get() = curRun?.ask
    val plan: PlanReq? get() = curRun?.plan
    val queue: List<QueuedMsg> get() = curRun?.queue ?: emptyList()
    val lastAppendId: Long get() = curRun?.lastAppendId ?: 0L

    /**当前会话是不是正在自己这儿跑（别的会话在跑不影响这个返回值） */
    fun runningHere(): Boolean = curRun?.runningHere() ?: true

    // ──通道转发的操作入口：界面调用一律落到当前会话那一根 ──
    fun stop() {
        curRun?.stop()
    } //停手

    fun resolve(allow: Boolean, permanent: Boolean) {
        curRun?.resolve(allow, permanent)
    } //工具确认回填

    fun answerAsk(answers: List<String>) {
        curRun?.answerAsk(answers)
    } //作答回填

    fun answerPlan(v: PlanVerdict) {
        curRun?.answerPlan(v)
    } //计划回填

    /**
     * 发送：按 sessionId 分流到对应会话的通道上，互不排队。
     * 注意：用户消息原样发出，不做花括号替换 —— 花括号参数只服务「自定义系统提示」。
     */
    fun send(raw: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        val text = raw
        val id = sessionId
        if (id != null) {
            runOf(id).send(text, images, files)
            return
        }
        //还没有会话：先落到最新会话（真没有就建一个），再走它自己的通道
        scope.launch {
            val s = withContext(Dispatchers.IO) { Repo.latestSession() ?: Repo.newSession() }
            sessionId = s.id
            adopt(s)
            uiMessages(withContext(Dispatchers.IO) { Repo.messages(s.id) })
            bindRun(s.id)
            runOf(s.id).send(text, images, files)
        }
    } //发送

    fun enqueue(raw: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        //用户消息原样入队，不做参数替换（同上：花括号只用于系统提示）
        curRun?.enqueue(raw, images, files)
    } //压入当前会话的队列

    fun dropQueued(id: Long) {
        curRun?.dropQueued(id)
    } //撤掉一条

    fun clearQueue() {
        curRun?.clearQueue()
    } //清空队列

    fun pullQueued(id: Long): QueuedMsg? = curRun?.pullQueued(id)//取出队列条目

    fun promoteQueued(id: Long) {
        curRun?.promoteQueued(id)
    } //提到最前

    fun editResend(msgId: Long, text: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        curRun?.editResend(msgId, text, images, files)
    } //编辑重发

    fun regenerate(msgId: Long, note: String? = null) {
        curRun?.regenerate(msgId, note)
    } //重新生成

    fun retryErr(msgId: Long) {
        curRun?.retryErr(msgId)
    } //错误重发

    fun resendErr(msgId: Long) {
        curRun?.resendErr(msgId)
    } //仍然重发

    fun switchBranch(msgId: Long, dir: Int) {
        curRun?.switchBranch(msgId, dir)
    } //切分支

    fun compactNow() {
        curRun?.compactNow()
    } //手动压缩

    fun recompact(note: String? = null) {
        curRun?.recompact(note)
    } //重新总结

    fun compressNow(): String = curRun?.compressNow() ?: host.getString(R.string.ag_compressing)

    fun clearCurrent() {
        curRun?.clearCurrent()
    } //清空当前会话

    /**全局配置（工作区 / 角色）只有一份：后台会话执行前要换上去、跑完换回来，靠这把锁串行化 */
    internal val ctxGate = kotlinx.coroutines.sync.Mutex()

    fun exportCurrent(): String? = curRun?.exportCurrent()//导出 Markdown

    /**子智能体 / 工作流回头调工具卡片：按协程上下文找回发起它的那条通道 */
    internal suspend fun beginTool(name: String, args: String, fromSub: Boolean = false): Long =
        owner()?.beginTool(name, args, fromSub) ?: 0L

    internal suspend fun endTool(seq: Long) {
        owner()?.endTool(seq)
    }

    internal suspend fun confirmFromSub(name: String, args: JSONObject, callId: String): Boolean =
        owner()?.confirmFromSub(name, args, callId) ?: false

    /**通道标记：挂在运行协程的上下文里，子智能体派生出来的协程都能继承到 */
    private class RunKey(val run: Run) : kotlin.coroutines.AbstractCoroutineContextElement(RunKey) {
        companion object Key : kotlin.coroutines.CoroutineContext.Key<RunKey>
    } //通道标记

    /**当前协程属于哪条通道：找不到就退回界面显示的那条 */
    private suspend fun owner(): Run? = currentCoroutineContext()[RunKey]?.run ?: curRun

    /** 子智能体要用的后端参数：只给值，不把整个通道对象漏出去 */
    internal data class SubBackend(
        val providerId: String,
        val baseUrl: String,
        val protocol: String,
        val model: String,
        val key: String,
        val temperature: Float,
        val thinkEffort: String,
        val extraParams: String
    )

    /** 取发起这条子任务的主通道的本轮后端；拿不到就返回 null，调用方回落全局 */
    internal suspend fun subBackend(): SubBackend? = owner()?.forSub()
    /**排队中的一条：内容和附件在这里冻结，轮到时原样发出 */
    data class QueuedMsg(val id: Long, val sid: String?, val text: String, val images: List<String>, val files: List<String>)

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
            bindRun(s.id)
            reloadSessions()
        }
    } //重进 App 落在最新会话

    private suspend fun adopt(s: SessionRow) {
        Cfg.personaId = s.persona
        Personas.current()?.profileId?.trim()?.takeIf { it.isNotEmpty() }?.let { Cfg.profileId = it }
        //会话级后端：有绑定就切过去（打开哪个对话就是哪个后端），没绑定就把当前这条记到它身上
        val bind = s.profile.trim()
        if (bind.isNotEmpty() && Profiles.byId(bind) != null) {
            runOf(s.id).sessionProfile = bind
            if (Cfg.profileId != bind) Profiles.select(bind)
        } else {
            runOf(s.id).sessionProfile = Cfg.profileId
            withContext(Dispatchers.IO) { Repo.setProfile(s.id, Cfg.profileId) }
        }
        //先挂会话 id 再判工作区：自动分配的结果要写回这一行，绝不能落到别的会话上
        Workspace.attachSession(s.id)
        var ws = s.workspace.trim()
        //每会话模式才分配并落库：共用模式没有会话级目录，一律走 path() 拿内置默认工作区
        if (ws.isEmpty() && Cfg.autoBindWorkspace) {
            ws = withContext(Dispatchers.IO) { Workspace.newSessionWorkspace() }
            if (ws.isNotEmpty()) withContext(Dispatchers.IO) { Repo.setWorkspace(s.id, ws) }
        }
        Cfg.sessionWorkspace = ws
        //会话行里存的是哈希目录就是自动分配的，其余都算用户自己挑的
        Cfg.sessionWsManual = ws.isNotEmpty() && !Workspace.isAutoDir(ws)
        Workspace.invalidate()
    } //套用会话自己的角色与工作区

    fun setPersona(personaId: String) {
        Cfg.personaId = personaId
        val bound = Personas.current()?.profileId?.trim().orEmpty()
        if (bound.isNotEmpty()) Cfg.profileId = bound
        //正在跑就用通道自己的会话 id，没跑就用界面当前会话
        val sid = curRun?.runningSid ?: sessionId ?: return
        scope.launch { withContext(Dispatchers.IO) { Repo.setPersona(sid, personaId) } }
    } //当前会话换角色

    /** 当前会话换后端：只写会话绑定与通道，全局的 Cfg 由调用方走 Profiles.select 换 */
    fun bindProfile(id: String) {
        val t = curRun?.runningSid ?: sessionId ?: return
        runOf(t).sessionProfile = id
        scope.launch { withContext(Dispatchers.IO) { Repo.setProfile(t, id) } }
    } //会话绑定后端

    fun setSessionWorkspace(path: String) {
        Cfg.sessionWorkspace = path
        Cfg.sessionWsManual = path.isNotBlank() && !Workspace.isAutoDir(path)
        Workspace.invalidate()
        val sid = curRun?.runningSid ?: sessionId ?: return
        scope.launch { withContext(Dispatchers.IO) { Repo.setWorkspace(sid, path) } }
    } //当前会话换工作区

    fun newSession() {
        scope.launch {
            //已经有没用过的新会话就不再建第二个：直接切过去，抽屉里不留一排空会话
            val blank = withContext(Dispatchers.IO) { Repo.blankSession() }
            if (blank != null) {
                if (blank.id != sessionId) openSession(blank.id) else status = ""
                return@launch
            }
            val s = withContext(Dispatchers.IO) { Repo.newSession() }
            sessionId = s.id
            //新会话在 dsh 侧也换一条：插件上下文（会话历史）不该继承上一个会话
            DshChat.reset(s.id)
            //先挂会话 id 再分配目录：分配结果要写回这一行，别落到上一个会话上
            Workspace.attachSession(s.id)
            bindRun(s.id)
            //新会话继承当前选中的后端，之后各走各的
            runOf(s.id).sessionProfile = Cfg.profileId
            messages = emptyList()
            status = ""
            val ws = withContext(Dispatchers.IO) {
                val dir = Workspace.newSessionWorkspace()
                Repo.setWorkspace(s.id, dir)
                Repo.setPersona(s.id, Cfg.personaId)
                Repo.setProfile(s.id, Cfg.profileId)
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

    /** 打开会话并定位到某条消息（收藏夹 / 搜索结果用）：先挂落点再切会话，时序无关 */
    fun openSessionAt(id: String, msgId: Long) {
        focusBySession = id to msgId
        openSession(id)
    }

    /** 待定位的消息：带会话 id 存，只有真的切到那个会话才对界面生效，避免在旧消息里找不到而白清 */
    private var focusBySession by mutableStateOf<Pair<String, Long>?>(null)

    /** 当前会话要滚到的消息 id；null = 没有落点 */
    val focusMsg: Long? get() = focusBySession?.takeIf { it.first == sessionId }?.second

    /** 聊天页滚完自己清掉落点，避免下次切回来又跳一次 */
    fun clearFocusMsg() {
        focusBySession = null
    }

    fun openSession(id: String) {
        scope.launch {
            val list = withContext(Dispatchers.IO) { Repo.messages(id) }
            val row = withContext(Dispatchers.IO) { Repo.session(id) }
            android.util.Log.d("BaiDBG", "openSession id=${id.take(8)} msgs=${list.size}")
            //打开也算一次交互：列表按最近交互排序，刚看的会话要浮上来
            withContext(Dispatchers.IO) { Repo.touch(id) }
            sessionId = id
            bindRun(id)
            if (row != null) adopt(row) else Workspace.attachSession(id)
            messages = list
            reloadSessions()
            //切回某个会话时，属于它的排队消息接着发（只动它自己那条通道）
            runOf(id).pump()
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
            //通道跟着会话一起销毁：残留的 job 先停掉，免得往已删的会话里写
            ids.forEach { id ->
                runs.remove(id)?.let { r ->
                    if (r.runningSid != null) r.stop()
                }
            }
            if (sessionId in ids) {
                val next = withContext(Dispatchers.IO) { Repo.latestSession() }
                if (next == null) {
                    val s = withContext(Dispatchers.IO) { Repo.newSession() }
                    sessionId = s.id
                    bindRun(s.id)
                    //新建的会话也要有自己的工作区，adopt 里统一分配并落库
                    adopt(s)
                    messages = emptyList()
                } else {
                    sessionId = next.id
                    bindRun(next.id)
                    //接着别的会话就要套用它的角色与工作区，不然工作区还停在被删掉的那条上
                    adopt(next)
                    messages = withContext(Dispatchers.IO) { Repo.messages(next.id) }
                }
            }
            reloadSessions()
        }
    } //批量删会话

    private suspend fun autoTitle(sid: String, first: String) {
        val pay = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", PromptStore.title))
            put(JSONObject().put("role", "user").put("content", first.take(800)))
        }
        val resp = runCatching {
            withContext(Dispatchers.IO) {
                //标题生成走「功能模型」里配的那条；没配就跟随当前聊天
                val m = b.ai.core.model.chat.FnModel.conn(b.ai.core.model.chat.FnModel.TITLE)
                LlmClient(m[0], m[1], b.ai.core.model.chat.FnModel.auth(b.ai.core.model.chat.FnModel.TITLE), protocol = m[4])
                    .chat(m[3], pay, temperature = 0.3)
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


    // ───────────────────────── 运行通道（按会话隔离）─────────────────────────
    private class Run(val sid: String) {
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
    var streamText by mutableStateOf("")
        private set
    var streamReason by mutableStateOf("")
        private set
    var busySince by mutableStateOf(0L)
        private set
    /** 分句场景下「对方正在输入…」：这一条已发出、下一条还在等打字时长为 true */
    var typing by mutableStateOf(false)
        private set
    var pending by mutableStateOf<PendingTool?>(null)
        private set
    /** 正在执行的工具：界面据此立刻画转圈卡，别等结果回来才出现 */
    var runningTools by mutableStateOf<List<RunningTool>>(emptyList())
        private set
    var ask by mutableStateOf<AskReq?>(null)
        private set
    var plan by mutableStateOf<PlanReq?>(null)
        private set
    /**
     * 错误处理走「消息回复式」：这一轮的用户消息照常留在会话里，
     * 失败后追加一条 isError 的 assistant 回复，气泡上按 errKind 挂按钮：
     *   · 不涉及操作（网络 / 服务端）→「重发」「仍然重发」两个；
     *   · 涉及操作（缺配置 / 缺权限 / 缺环境）→ 再加一个「去配置」。
     * 错误回复不参与请求体（buildPayload 会跳过），只作界面留痕。
     */
    private suspend fun replyErr(text: String, kind: String) {
        runCatching { append(ChatMsg(role = "assistant", content = text, isError = true, errKind = kind)) }
    } //追加错误回复

    /** 异常 → 错误类型：配置 / 权限 / 环境三类要给「去配置」入口，其余当网络或服务端错误 */
    private fun errKindOf(e: Throwable): String {
        if (!(be?.ready ?: Cfg.ready)) return ERR_MODEL
        val m = e.message.orEmpty()
        val code = Regex("HTTP (\\d{3})").find(m)?.groupValues?.get(1)?.toIntOrNull()
        if (code == 401 || code == 403 || code == 404) return ERR_MODEL
        if (m.contains(host.getString(R.string.ag_err_kw_perm))) return ERR_PERM
        if (m.contains(host.getString(R.string.ag_err_kw_env))) return ERR_ENV
        return ""
    } //错误分类

    /** 记一次失败：把错误当成一条 AI 回复落到会话里 */
    private suspend fun fail(e: Exception) {
        status = ""
        replyErr(e.message?.takeIf { it.isNotBlank() } ?: host.getString(R.string.ag_err_unknown), errKindOf(e))
    } //记录失败

    /**
     * 错误气泡「重发」：把 head 退回该错误回复上方的用户消息，同一轮原样重跑。
     * 错误气泡不在新分支上，界面上自然消失，聊天记录里也不留红条。
     */
    fun retryErr(msgId: Long) {
        if (busy) return
        val sid = sessionId ?: return
        val i = messages.indexOfFirst { it.id == msgId }
        if (i <= 0) return
        val u = messages.subList(0, i).lastOrNull { it.role == "user" } ?: return
        busy = true
        val g = ++gen
        runGen = g
        job = scope.launch(RunKey(this@Run)) {
            try {
                runSid = sid
                withContext(Dispatchers.IO) { Repo.headTo(sid, u.id) }
                messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
                runHistory = messages
                runLoop()
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                fail(e)
            } finally {
                finish(g)
            }
        }
    } //重发（原地重跑这一轮）

    /** 错误气泡「仍然重发」：无视这次失败，把当初那条用户消息当新消息再发一次 */
    fun resendErr(msgId: Long) {
        if (busy) return
        val i = messages.indexOfFirst { it.id == msgId }
        if (i <= 0) return
        val u = messages.subList(0, i).lastOrNull { it.role == "user" } ?: return
        send(u.content, u.images, u.files)
    } //仍然重发（新发一条同样的消息）

    /** 最近一条用户消息 id：重发时用它原地重跑 */
    private fun lastUserId(): Long = messages.lastOrNull { it.role == "user" }?.id ?: -1L
    /** 一个回合里工具并发执行的上限：再多也不会更快，反而抢 IO 与内存 */
    private val PARALLEL_TOOLS = 4

    /**发送排队：忙的时候进来的消息按顺序等着，上一轮收工自动发下一条 */
    var queue by mutableStateOf<List<QueuedMsg>>(emptyList())
        private set
    private var qid = 0L

    private var job: Job? = null
    /// 运行代际：停手或开新一轮就 +1，旧循环靠它自杀，别再往会话里写
    private var gen = 0L
    private var runGen = 0L
    /** 最近一次追加的消息 id：气泡只会给这一条播弹出动画，历史一律静态；故意不用 State，免得整列表订阅 */
    @Volatile
    var lastAppendId = 0L
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

    // ──运行通道：每个会话各一份运行态，互不干扰 ──
    /**本通道的会话 id：原代码里的 sessionId 在运行期就等于它 */
    private val sessionId: String get() = sid

    /**是不是当前显示的那个会话：灵动岛 / 胶囊 / 朗读这类全局 UI 只让前台会话驱动 */
    private fun isFront(): Boolean = sid == curSid()

    /**本通道看到的会话消息：正在看这个会话就直连界面列表，没在看就写自己的快照 */
    private var messages: List<ChatMsg>
        get() = if (sid == curSid()) uiMessages() else runHistory
        set(v) {
            if (sid == curSid()) uiMessages(v) else runHistory = v
        }

    private val host: Context get() = App.self

    /**本通道的历史快照：请求体用这份，中途切会话也不会缺消息 */
    private fun hist(): List<ChatMsg> = if (runHistory.isNotEmpty()) runHistory else messages

    /**这条通道是不是空闲（按它自己的 runSid 判，不看别的会话） */
    fun runningHere(): Boolean = runSid == null || runSid == sid

    /**这条通道正在跑的会话 id：没在跑就是 null */
    val runningSid: String? get() = runSid

    // ──后台会话的配置上下文：工作区 / 角色在 Cfg 里只有一份，执行前换上去、跑完换回来 ──
    private var myWs: String? = null
    private var myManual = false
    private var myPersona = ""
    private var myProfile = ""

    // ── 本轮后端快照：会话绑定了模型配置就取那条，没绑定取当前全局 ──
    //以前请求链全读全局 Cfg：A 会话在后台跑，用户切到 B 换了个模型，A 下一轮就悄悄换到 B 的后端。
    //现在每次 send 开始时把后端整组拷一份到这里，循环里只认这份，改全局只影响下一轮。
    /** 本会话绑定的模型配置 id：空 = 跟随全局选中那条 */
    internal var sessionProfile = ""

    /** 本轮实际使用的后端；还没开跑时是 null */
    private var be: Backend? = null

    /** 一次运行用的后端参数：提供商 / 地址 / 协议 / 模型 / Key 组 / 能力位 / 思考参数 */
    private data class Backend(
        val profileId: String,
        val providerId: String,
        val baseUrl: String,
        val protocol: String,
        val model: String,
        val apiKey: String,
        val keys: List<String>,
        val vision: Boolean,
        val toolCall: Boolean,
        val maxTokens: Int,
        val think: Boolean,
        val thinkEffort: String,
        val extraParams: String,
        val noStream: Boolean,
        val temperature: Float,
        val localOk: Boolean
    ) {
        /** 第 i 个 Key（多 Key 轮换）；一个都没配就退回单条 apiKey */
        fun key(i: Int): String {
            if (keys.isEmpty()) return apiKey
            return keys[i % keys.size]
        }

        val keyCount: Int get() = keys.size.coerceAtLeast(1)

        /** 这条后端能不能直接发：本地模型要有文件，云端要有地址 + Key + 模型名 */
        val ready: Boolean
            get() = if (providerId == Providers.LOCAL) {
                localOk && model.isNotBlank()
            } else {
                baseUrl.isNotBlank() && model.isNotBlank() && (keys.isNotEmpty() || apiKey.isNotBlank())
            }
    } //后端快照

    /** 子智能体取本轮后端：和主循环用同一份，别让子任务跑到别的模型上去 */
    internal fun forSub(): SubBackend? = be?.let {
        SubBackend(
            providerId = it.providerId,
            baseUrl = it.baseUrl,
            protocol = it.protocol,
            model = it.model,
            key = it.key(0),
            temperature = it.temperature,
            thinkEffort = it.thinkEffort,
            extraParams = it.extraParams
        )
    }

    /** 生成后端快照：纯内存读取，不碰磁盘，send 前同步调即可 */
    private fun backendNow(): Backend {
        val pid = sessionProfile.trim()
        val p = pid.takeIf { it.isNotEmpty() }?.let { Profiles.byId(it) }
        if (p != null) {
            return Backend(
                profileId = p.id,
                providerId = p.providerId,
                baseUrl = p.baseUrl,
                protocol = b.ai.net.api.llm.LlmClient.protocolOf(p.protocol),
                model = p.model,
                apiKey = p.apiKey,
                keys = Cfg.keyList(p.id).ifEmpty { listOf(p.apiKey) }.filter { it.isNotBlank() },
                vision = p.vision,
                toolCall = p.toolCall,
                maxTokens = p.maxTokens,
                think = Cfg.think,
                thinkEffort = Cfg.thinkEffort,
                extraParams = Cfg.extraParams,
                noStream = Cfg.noStream,
                temperature = Cfg.temperature,
                localOk = p.providerId != Providers.LOCAL || File(p.model).isFile
            )
        }
        //没绑定（老会话 / 新会话还没写回）：跟全局当前这条，行为和以前一致
        return Backend(
            profileId = Cfg.profileId,
            providerId = Cfg.providerId,
            baseUrl = Cfg.baseUrl,
            protocol = Cfg.modelProtocol,
            model = Cfg.model,
            apiKey = Cfg.apiKey,
            keys = keys(),
            vision = Cfg.modelVision,
            toolCall = Cfg.modelToolCall,
            maxTokens = Cfg.maxTokens,
            think = Cfg.think,
            thinkEffort = Cfg.thinkEffort,
            extraParams = Cfg.extraParams,
            noStream = Cfg.noStream,
            temperature = Cfg.temperature,
            localOk = Cfg.localReady
        )
    } //后端快照

    /**把全局 Cfg / Workspace 切成本通道自己的会话配置（只取一次，后面复用） */
    private suspend fun applyCtx() {
        if (myWs == null) {
            val row = withContext(Dispatchers.IO) { Repo.session(sid) }
            myWs = row?.workspace.orEmpty()
            myManual = myWs!!.isNotEmpty() && !Workspace.isAutoDir(myWs!!)
            myPersona = row?.persona.orEmpty()
        }
        Workspace.attachSession(sid)
        Cfg.sessionWorkspace = myWs.orEmpty()
        Cfg.sessionWsManual = myManual
        Cfg.personaId = myPersona
        Personas.current()?.profileId?.trim()?.takeIf { it.isNotEmpty() }?.let { Cfg.profileId = it }
        Workspace.invalidate()
    } //切到本会话的配置

    /**
     * 借全局配置用一下：先把 Cfg / Workspace 换成本会话的，跑完还回去。
     * 前台也必须拿锁 —— 切走的那一刻起它就成了「后台」：
     * 若让前台免锁直跑，另一个后台会话借配置期间会把工作区 / 角色串给它。
     */
    private suspend fun <T> inCtx(block: suspend () -> T): T {
        return ctxGate.withLock {
            val pSid = Workspace.sid
            val pWs = Cfg.sessionWorkspace
            val pManual = Cfg.sessionWsManual
            val pPersona = Cfg.personaId
            val pProfile = Cfg.profileId
            applyCtx()
            try {
                block()
            } finally {
                Workspace.attachSession(pSid)
                Cfg.sessionWorkspace = pWs
                Cfg.sessionWsManual = pManual
                Cfg.personaId = pPersona
                Cfg.profileId = pProfile
                Workspace.invalidate()
            }
        }
    } //借全局配置执行

    fun stop() {
        //先清队列再取消：否则收尾的 pump 会把排队的第一条立刻又发出去
        queue = emptyList()
        //代际 +1：旧循环即便卡在阻塞 IO 里回不来，回来后也写不进会话
        gen++
        job?.cancel()
        Speak.stop()
        pending?.let { if (!it.deferred.isCompleted) it.deferred.complete(false) }
        pending = null
        ask?.let { if (!it.deferred.isCompleted) it.deferred.complete(emptyList()) }
        ask = null
        plan?.let { if (!it.deferred.isCompleted) it.deferred.complete(PlanVerdict.Dismiss) }
        plan = null
        status = host.getString(R.string.ag_stopped)
        //中途停掉时可能正挂在「正在输入」上，这里必须收掉，否则指示器会一直转
        typing = false
        sContent.setLength(0)
        sReason.setLength(0)
        chunks.value = "" to ""
        busy = false
        runSid = null
        be = null
        runHistory = emptyList()
        if (isFront()) b.ai.device.sys.host.Capsule.end()
        clearTools()
        //用户主动停手：只撤自己的任务通知；后台会话停手不能把前台那条一起撤掉
        if (isFront()) runCatching { Island.cancel() }
    } //停手

    fun resolve(allow: Boolean, permanent: Boolean) {
        val p = pending ?: return
        if (permanent) Cfg.setPolicy(p.call.name, if (allow) "allow" else "deny")
        pending = null
        if (!p.deferred.isCompleted) p.deferred.complete(allow)
    } //弹窗结果回填

    fun send(text: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        //忙的时候不丢消息：排进队列，等这一轮收工再发
        if (busy) {
            enqueue(text, images, files)
            return
        }
        //先按本会话绑定的后端判一次：别的对话换模型不该让这里判「没配好」
        val pre = backendNow()
        if (!pre.ready) {
            //本地这条不给「去填 base_url」的误导：分开说清是没选 GGUF、文件失效，还是云端配置没填全
            val why = when {
                pre.providerId == Providers.LOCAL && Cfg.llmModel.isBlank() ->
                    host.getString(R.string.ag_no_local_pick)
                pre.providerId == Providers.LOCAL ->
                    host.getString(R.string.ag_no_local_file)
                else -> host.getString(R.string.ag_no_model)
            }
            //先把这条当成用户消息发出去（会话里留痕），再用一条 AI 回复报错
            scope.launch {
                appendLocal(ChatMsg(role = "user", content = text, images = images, files = files))
                appendLocal(
                    ChatMsg(
                        role = "assistant",
                        content = why,
                        isError = true,
                        errKind = ERR_MODEL
                    )
                )
            }
            return
        }
        busy = true
        //新一轮：代际 +1，旧循环拿到旧值会自己退出
        val g = ++gen
        runGen = g
        job = scope.launch(RunKey(this@Run)) {
            try {
                //会话由 Agent.send 统一保证存在：这条通道只跑自己的会话，绝不碰别人的
                val sid = this@Run.sid
                runSid = sid
                be = backendNow() //本轮后端就此冻结，之后改全局不影响这一轮
                runHistory = messages
                if (messages.none { it.role == "user" }) {
                    withContext(Dispatchers.IO) { Repo.renameSession(sid, text.take(18)) }
                    reloadSessions()
                    scope.launch { runCatching { autoTitle(sid, text) } }
                }
                append(ChatMsg(role = "user", content = text, images = images, files = files))
                runLoop()
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                //失败不写进会话：用户消息已经落库，这里追加一条错误回复（重发 / 仍然重发 / 去配置）
                fail(e)
            } finally {
                //只有最新那一轮才收尾：停手后用户可能已经开了新一轮，旧的收尾会把新状态清掉
                finish(g)
            }
        }
    } //发送并驱动循环

    fun enqueue(text: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        queue = queue + QueuedMsg(++qid, sessionId, text, images, files)
    } //压入发送队列

    fun dropQueued(id: Long) {
        queue = queue.filterNot { it.id == id }
    } //撤掉队列里的一条

    fun clearQueue() {
        queue = emptyList()
    } //清空队列

    /** 取出一条给别人编辑：从队列里摘掉，内容交给调用方回填输入框 */
    fun pullQueued(id: Long): QueuedMsg? {
        val n = queue.firstOrNull { it.id == id } ?: return null
        queue = queue.filterNot { it.id == id }
        return n
    } //取出队列条目

    /** 立刻优先发这一条：空闲就直接发；正忙就挪到队首，本轮收工第一批发它 */
    fun promoteQueued(id: Long) {
        val n = queue.firstOrNull { it.id == id } ?: return
        if (n.sid != sessionId) {
            status = host.getString(R.string.ag_queue_other)
            return
        }
        queue = queue.filterNot { it.id == id }
        if (!busy) {
            send(n.text, n.images, n.files)
            return
        }
        queue = listOf(n) + queue
        status = host.getString(R.string.ag_queue_next)
    } //队列条目提到最前

    /**收工后接着发下一条：只发属于当前会话的，别把消息串到别的会话里 */
    /**
     * 本轮回工的公共收尾：清运行态、撤胶囊与工具、把任务通知切成完成态，再推进队列。
     * 四类入口（发送 / 编辑重发 / 同文本重发 / 重新生成）的 finally 统一走这里，
     * 之前这段是四份复制粘贴，加一个收尾动作要改四处。
     */
    private fun finish(g: Long) {
        if (gen != g) return
        busy = false
        runSid = null
        be = null
        runHistory = emptyList()
        if (isFront()) b.ai.device.sys.host.Capsule.end()
        clearTools()
        //岛：先进「完成」态，驻留时长由 Island 内部的倒计时管；pump 起新一轮时会先撤掉这个倒计时
        //只有前台会话才动这块全局 UI，后台会话静默收工，免得两个会话抢岛
        if (isFront()) runCatching { Island.taskDone(curTitle(), host.getString(R.string.island_done)) }
        //放在最后：send 内部是 Main.immediate，先 pump 会把新一轮的字段又清一遍
        pump()
    } //收工

    /**收工后接着发下一条：只发属于自己这个会话的，别的会话的排队消息碰都不碰 */
    internal fun pump() {
        if (busy || queue.isEmpty()) return
        val n = queue.firstOrNull { it.sid == sid } ?: return
        queue = queue.filterNot { it.id == n.id }
        send(n.text, n.images, n.files)
    } //队列推进

    fun editResend(msgId: Long, text: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        val t = text.trim()
        if (busy || t.isEmpty()) return
        //一个字都没动就点重发：不该再长出一条一模一样的用户消息，只让它引出的回复出新版本
        if (messages.firstOrNull { it.id == msgId }?.content?.trim() == t) {
            rerunReply(msgId)
            return
        }
        busy = true
        val g = ++gen
        runGen = g
        job = scope.launch(RunKey(this@Run)) {
            try {
                runSid = sessionId
                runHistory = messages
                rollbackTo(msgId)
                //原消息和它引出的回复都留在树里，新消息成了另一个分支，图片与文件照旧带上
                append(ChatMsg(role = "user", content = t, images = images, files = files))
                runLoop()
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                fail(e)
            } finally {
                finish(g)
            }
        }
    } //编辑重发

    /** 同文本重发：用户消息留在原地，只把这条引出的回复重新生成一份，观感就是回复上多一版 ‹ n/m › */
    private fun rerunReply(msgId: Long) {
        if (busy) return
        val sid = sessionId ?: return
        val i = messages.indexOfFirst { it.id == msgId }
        //下面已经有一条回复：直接对它重新生成，用户消息一个字节都不动
        if (i >= 0) {
            val nxt = messages.getOrNull(i + 1)
            if (nxt != null && nxt.role == "assistant") {
                regenerate(nxt.id)
                return
            }
        }
        busy = true
        val g = ++gen
        runGen = g
        job = scope.launch(RunKey(this@Run)) {
            try {
                runSid = sid
                //head 就停在这条用户消息上：跑出来的回复挂在它下面，成了旧回复的兄弟
                withContext(Dispatchers.IO) { Repo.headTo(sid, msgId) }
                messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
                runHistory = messages
                runLoop()
            } catch (e: CancellationException) {
                status = host.getString(R.string.ag_stopped)
            } catch (e: Exception) {
                fail(e)
            } finally {
                finish(g)
            }
        }
    } //同文本重发

    fun regenerate(msgId: Long, note: String? = null) {
        if (busy) return
        if (messages.none { it.id == msgId }) {
            status = host.getString(R.string.ag_no_regen)
            return
        }
        busy = true
                runSid = sessionId
                runHistory = messages
        val g = ++gen
        runGen = g
        job = scope.launch(RunKey(this@Run)) {
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
                fail(e)
            } finally {
                finish(g)
            }
        }
    } //重新生成
    
    private suspend fun rollbackTo(msgId: Long) {
        val sid = this.sid
        //只把 head 挪回父节点：旧回答留在树里成了另一个分支，不再被删掉
        withContext(Dispatchers.IO) { Repo.rollback(sid, msgId) }
        val list = withContext(Dispatchers.IO) { Repo.messages(sid) }
        if (sid == curSid()) {
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
            //成功失败都重读一次：失败时也把界面拉回数据库真身，免得看着像点了没反应
            if (!ok) {
                status = host.getString(R.string.ag_br_fail)
                android.util.Log.w("BaiDBG", "switchBranch miss msg=$msgId dir=$dir")
            }
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
        }
    } //切分支

    private var keyIdx = 0

    /** 正在跑的工具列表要跨线程改（子智能体并行），用锁护住 list 的读改写 */
    private val runLock = Any()
    private var runSeq = 0L
    /** 确认弹窗单槽：主智能体与多个子智能体排队轮流问，不然互相覆盖 */
    private val confirmGate = Mutex()

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

    /**
     * dsh 引擎跑一轮：把用户这条消息交给 dsh 的 agent 循环，事件回流成我们自己的消息气泡与工具卡。
     *
     * 为什么是这个形状（见 DshChat 文件头）：SDK 协议没有 tools/list、tool/execute，
     * 插件工具只能由 dsh 自己调，所以 chat 只负责「下发一轮 + 渲染事件」。
     * 这一轮里 dsh 装的所有插件工具（bash / fs / 第三方插件）都可用；
     * Baicodex 自家的记忆 / 工作流 / 米家 / 屏幕等工具不参与（那是自家循环的能力）。
     */
    private suspend fun runDshTurn() {
        val msg = messages.lastOrNull { it.role == "user" }
        val text = msg?.content.orEmpty().trim()
        if (text.isEmpty()) {
            status = host.getString(R.string.ag_no_user_msg)
            return
        }
        status = host.getString(R.string.ag_ask_model)
        busySince = System.currentTimeMillis()
        if (isFront()) runCatching { Island.taskStart(curTitle(), host.getString(R.string.island_running)) }

        //附件与图片：dsh 的 contentBlocks 只发文本，把本地路径写进正文，让它的 bash/fs 工具自己去读
        val prompt = buildString {
            append(text)
            val files = msg?.files.orEmpty()
            if (files.isNotEmpty()) {
                append("\n\n[附件文件]\n")
                files.forEach { append("- ").append(it).append('\n') }
            }
            val images = msg?.images.orEmpty()
            if (images.isNotEmpty()) {
                append("\n[图片文件]\n")
                images.forEach { append("- ").append(it).append('\n') }
            }
        }

        val acc = StringBuilder()                 //流式预览缓冲
        val seqs = HashMap<String, Long>()        //callId → 工具卡 seq
        val names = HashMap<String, String>()     //callId → 工具名（结果消息补名用）
        var errText = ""
        val cwd = Cfg.sessionWorkspace.trim()

        try {
            val err = DshChat.run(sid, prompt, cwd) { ev ->
                when (ev) {
                    //流式预览：先铺在输入框上方，最终以 assistant/message 为准
                    is DshEv.Delta -> {
                        acc.append(ev.text)
                        chunks.value = acc.toString() to ""
                    } //预览

                    //一步的最终助手消息：正文 + 这一步发起的工具卡调用清单
                    is DshEv.Msg -> {
                        acc.setLength(0)
                        chunks.value = "" to ""
                        val calls = ev.calls.map { b.ai.core.model.chat.ToolCall(it.id, it.name, it.args) }
                        if (ev.text.isNotBlank() || calls.isNotEmpty()) {
                            append(
                                ChatMsg(
                                    role = "assistant",
                                    content = Mask.text(ev.text),
                                    toolCalls = calls
                                )
                            )
                        }
                    } //助手消息

                    //工具开始：卡片立刻转圈（同一次调用重复上报时只建一张卡）
                    is DshEv.ToolBegin -> {
                        if (!seqs.containsKey(ev.callId)) {
                            seqs[ev.callId] = beginTool(ev.name, ev.args)
                            names[ev.callId] = ev.name
                        }
                    } //工具开始

                    //工具结果：落一条 tool 消息，界面按 toolCallId 对到卡片上
                    is DshEv.ToolDone -> {
                        append(
                            ChatMsg(
                                role = "tool",
                                content = Mask.text(ev.text),
                                toolCallId = ev.callId,
                                toolName = ev.name.ifBlank { names[ev.callId].orEmpty() },
                                isError = !ev.ok,
                                toolMs = 0
                            )
                        )
                        seqs.remove(ev.callId)?.let { endTool(it) }
                        names.remove(ev.callId)
                    } //工具结果

                    is DshEv.State -> status = ev.text
                    is DshEv.Fail -> errText = ev.text
                    DshEv.Done -> Unit
                }
            }
            //收工：内核侧报错 / 本侧超时，都落一条错误回复（用户消息已留在会话里）
            val fail = err ?: errText.takeIf { it.isNotBlank() }
            if (fail != null) replyErr(fail, if (fail.contains("未安装") || fail.contains("Node")) ERR_ENV else "")
        } finally {
            chunks.value = "" to ""
            acc.setLength(0)
            //兜底：异常退出时还挂着的工具卡一并收掉，不让转圈永远停在那
            seqs.values.forEach { runCatching { endTool(it) } }
            seqs.clear()
        }
    } //dsh 引擎一轮

    private suspend fun runLoop() {
        //dsh 引擎：整轮交给 dsh 的 agent 循环（发送 / 编辑重发 / 重新生成都走这里，一处就够）
        if (Cfg.dshEngine) {
            runDshTurn()
            return
        }
        val ctx = currentCoroutineContext()
        val bk = be ?: backendNow().also { be = it }
        val temp = bk.temperature.takeIf { it >= 0f }?.toDouble()
        status = host.getString(R.string.ag_ask_model)
        busySince = System.currentTimeMillis()
        //任务通知：有灵动岛就挂岛，没有就是一条常驻通知；开关关掉时 Island 内部直接返回
        //后台会话不抢岛：同一块全局 UI 只归前台会话
        if (isFront()) runCatching { Island.taskStart(curTitle(), host.getString(R.string.island_running)) }
        compact(false)
        //无限轮：模型给完最终答案（本轮无工具调用）或用户点停止才退出
        while (ctx.isActive) {
            //代际变了（停手或已开新一轮）就立刻退，别把这一轮的结果写回去
            if (gen != runGen) throw CancellationException("superseded")
            val payload = withContext(Dispatchers.IO) { inCtx { buildPayload() } }
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
                    val client = LlmClient(bk.baseUrl, bk.key(keyIdx), Providers.byId(bk.providerId).auth, protocol = bk.protocol)
                    val got = runCatching {
                        val streamed = withContext(Dispatchers.IO) {
                            //关了流式就整包等：端点不支持 SSE 时也走这条，界面上没有逐字过程
                            if (bk.noStream) {
                                status = host.getString(R.string.ag_stream_off)
                                client.chat(bk.model, payload, temp, bk.think, bk.thinkEffort, bk.extraParams)
                            } else try {
                                client.chatStream(
                                    bk.model, payload, temp, bk.think, bk.thinkEffort, bk.extraParams,
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
                                client.chat(bk.model, payload, temp, bk.think, bk.thinkEffort, bk.extraParams)
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
            //输出侧脱敏：正文与推理都过一遍，落库的与下一轮回传的都是打码后的文本
            val shown = Mask.text(content)
            val think = Mask.text(reason)
            //用量独立记账：一次请求一行，删会话不回滚
            withContext(Dispatchers.IO) {
                Repo.recordUsage(
                    meta.at, meta.provider, meta.model, runSid ?: sessionId ?: "",
                    meta.promptTokens, meta.cachedTokens, meta.completionTokens
                )
            }
            //真实引擎分句更碎，普通模式仍是整段按句号切
            val segs = if (calls.isEmpty() && Cfg.waifuMode) {
                if (Cfg.waifuReal) Waifu.splitReal(shown) else Waifu.split(shown)
            } else listOf(shown)
            segs.forEachIndexed { n, seg ->
                val head = n == 0
                val tail = n == segs.lastIndex
                if (!head) {
                    //分句间隙：可选用「对方正在输入」占位，时长 = 本条字数 × 打字速度
                    val gap = Waifu.gapMs(seg, Cfg.waifuTypeMs)
                    if (Cfg.waifuTyping && segs.size > 1) {
                        typing = true
                        try {
                            delay(gap)
                        } finally {
                            typing = false
                        }
                    } else {
                        delay(gap)
                    }
                }
                append(
                    ChatMsg(
                        role = "assistant",
                        content = seg,
                        toolCalls = if (head) calls else emptyList(),
                        reasoning = if (head) think else "",
                        //统计只挂在最后一条：分句时它会显示在所有气泡之后，不在中间那条框里
                        meta = if (tail) meta else null
                    )
                )
            }
            //本轮没有工具调用就是最终答案：开了「回复朗读」就念出来
            if (calls.isEmpty()) {
                //计划模式：正文当计划给用户过一遍，没点「确定执行」绝不动手
                if (Cfg.agentMode == AgentMode.PLAN) {
                    when (val v = approvePlan(shown)) {
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
                            if (isFront()) Speak.say(shown)
                            break
                        }
                    }
                }
                if (isFront()) Speak.say(shown)
                break
            }
            //模型一返回调用就先把卡片全摆出来转圈：排队里的、等确认的、等作答的都看得见
            val seqs = calls.map { beginTool(it.name, it.arguments) }
            //单条调用：要交互或抢独占资源的走这里
            suspend fun one(i: Int) {
                val c = calls[i]
                //每张卡跟自己的调用绑定：这一条一出结果就收掉，跟结果行无缝接上
                val rs = seqs[i]
                try {
                    if (!ctx.isActive) return
                    //提问工具先接住：不弹工具确认框，直接把面板推给用户并等作答
                    if (c.name == Tools.ASK) {
                        status = host.getString(R.string.ag_ask_fmt, i + 1, calls.size)
                        busySince = System.currentTimeMillis()
                        val qa = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
                        appendToolMsg(c, askUser(qa), Route.SANDBOX, false)
                        return
                    }
                    //分智能体：免确认，直接并行跑完把合并结果当一次工具输出回灌
                    if (c.name == Tools.SUB) {
                        val qa = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
                        val tasks = subTasks(qa)
                        if (tasks.isEmpty()) {
                            appendToolMsg(c, "没有可执行的子任务（tasks 为空）。", Route.SANDBOX, true)
                            return
                        }
                        status = host.getString(R.string.ag_sub_start, tasks.size)
                        busySince = System.currentTimeMillis()
                        val tExec = System.currentTimeMillis()
                        val text = inCtx {
                            SubAgents.run(
                                tasks, qa.optInt("rounds", SubAgents.DEFAULT_ROUNDS),
                                onStatus = { s -> status = s },
                                confirm = { n, a, id -> confirmFromSub(n, a, id) }
                            )
                        }
                        status = host.getString(R.string.ag_think_again)
                        busySince = System.currentTimeMillis()
                        appendToolMsg(c, text, Route.SANDBOX, false, System.currentTimeMillis() - tExec)
                        return
                    }
                    //工作流：按步骤顺序跑，每步一个分智能体，失败按每步策略重试或跳过
                    if (c.name == Tools.FLOW_RUN) {
                        val qa = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
                        val flow = flowOf(qa)
                        if (flow == null) {
                            appendToolMsg(c, "没有可执行的工作流：要么给 flow_id，要么给 name + steps。", Route.SANDBOX, true)
                            return
                        }
                        status = "工作流「" + flow.name + "」开始，共 " + flow.steps.size + " 步"
                        busySince = System.currentTimeMillis()
                        val tExec = System.currentTimeMillis()
                        val out = inCtx {
                            FlowRunner.run(
                                flow,
                                onStatus = { s -> status = s },
                                confirm = { n, a, id -> confirmFromSub(n, a, id) }
                            )
                        }
                        if (flow.id.isNotBlank()) Flows.mark(flow.id, out.ok, out.text)
                        status = host.getString(R.string.ag_think_again)
                        busySince = System.currentTimeMillis()
                        appendToolMsg(c, out.text, Route.SANDBOX, !out.ok, System.currentTimeMillis() - tExec)
                        return
                    }
                    //当前模式禁掉的工具直接回绝：定义没下发，模型仍可能凭记忆硬调
                    if (!Tools.allowed(c.name)) {
                        appendToolMsg(
                            c,
                            "当前运行模式不允许调用「" + Tools.label(c.name) + "」，本次没有执行。",
                            Route.SANDBOX,
                            true
                        )
                        return
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
                        return
                    }
                    status = host.getString(R.string.ag_wait_fmt, Tools.label(c.name), i + 1, calls.size)
                    busySince = System.currentTimeMillis()
                    val allowed = decide(c)
                    if (!allowed) {
                        appendToolMsg(c, "用户拒绝执行该工具调用，未做任何改动。", Route.SANDBOX, true)
                        return
                    }
                    status = host.getString(R.string.ag_run_fmt, Tools.label(c.name), i + 1, calls.size)
                    busySince = System.currentTimeMillis()
                    val args = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
                    //顶部胶囊：「正在执行 xx」，有无障碍走无障碍窗层，没有则靠悬浮窗权限。
                    //screen start 之后胶囊已常驻，这里只改文案；quiet 那一次干脆别先亮。
                    if (!quietCall(c.name, args)) {
                        if (isFront()) b.ai.device.sys.host.Capsule.show(host.getString(R.string.ag_capsule_fmt, Tools.label(c.name)))
                    }
                    val tTool = System.currentTimeMillis()
                    val out = try {
                        inCtx { withContext(Dispatchers.IO) { Tools.execute(c.name, args) } }
                    } finally {
                        if (isFront()) b.ai.device.sys.host.Capsule.hide()
                    }
                    appendToolMsg(c, out.text, out.route, !out.ok, System.currentTimeMillis() - tTool)
                } finally {
                    endTool(rs)
                }
            } //单条调用
            //相邻的独立调用合成一批并发跑；要交互或抢独占资源的仍串行，混排时按段处理
            var ci = 0
            val sem = Semaphore(PARALLEL_TOOLS)
            while (ci < calls.size) {
                if (serialOnly(calls[ci])) {
                    one(ci)
                    ci++
                    continue
                }
                val batch = ArrayList<Int>()
                while (ci < calls.size && !serialOnly(calls[ci])) {
                    batch.add(ci)
                    ci++
                }
                if (batch.size == 1) {
                    one(batch[0])
                    continue
                }
                try {
                    //确认弹窗只有一份：先一条条把权限与确认过完，再并发执行
                    val ready = ArrayList<Int>()
                    for (i in batch) {
                        val c = calls[i]
                        if (!ctx.isActive) break
                        //当前模式禁掉的工具直接回绝：定义没下发，模型仍可能凭记忆硬调
                        if (!Tools.allowed(c.name)) {
                            appendToolMsg(
                                c,
                                "当前运行模式不允许调用「" + Tools.label(c.name) + "」，本次没有执行。",
                                Route.SANDBOX,
                                true
                            )
                            continue
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
                            continue
                        }
                        status = host.getString(R.string.ag_wait_fmt, Tools.label(c.name), i + 1, calls.size)
                        busySince = System.currentTimeMillis()
                        if (!decide(c)) {
                            appendToolMsg(c, "用户拒绝执行该工具调用，未做任何改动。", Route.SANDBOX, true)
                            continue
                        }
                        ready.add(i)
                    }
                    if (ready.size == 1) {
                        val o = execOne(calls[ready[0]])
                        appendToolMsg(calls[ready[0]], o.text, o.route, !o.ok, o.ms)
                    } else if (ready.size > 1) {
                        status = host.getString(R.string.ag_par_fmt, ready.size)
                        busySince = System.currentTimeMillis()
                        //并发跑完再按原顺序落库：工具结果的顺序必须跟调用顺序一致
                        val outs = coroutineScope {
                            ready.map { i -> async { sem.withPermit { execOne(calls[i]) } } }.awaitAll()
                        }
                        ready.forEachIndexed { k, i ->
                            appendToolMsg(calls[i], outs[k].text, outs[k].route, !outs[k].ok, outs[k].ms)
                        }
                    }
                } finally {
                    batch.forEach { endTool(seqs[it]) }
                }
            }
            status = host.getString(R.string.ag_think_again)
            busySince = System.currentTimeMillis()
        }
    } //模型 → 工具 → 回传 的主循环

    /** 只许单独跑的工具：要弹问答/计划/子智能体/工作流，或独占屏幕、独占 proot 会话 */
    private fun serialOnly(c: ToolCall): Boolean =
        c.name == Tools.ASK || c.name == Tools.SUB || c.name == Tools.FLOW_RUN ||
            c.name == Tools.SCREEN || c.name == Tools.LINUX

    /** 并行批里一条工具的结果：先收齐再按原顺序落库，顺序才不会和调用顺序错开 */
    private data class ToolOut(val text: String, val route: Route, val ok: Boolean, val ms: Long)

    /** 跑一条工具：异常收成结果文本，别让一个坏工具把整批带崩 */
    private suspend fun execOne(c: ToolCall): ToolOut {
        val args = runCatching { JSONObject(c.arguments) }.getOrElse { JSONObject() }
        val t0 = System.currentTimeMillis()
        val r = runCatching { inCtx { withContext(Dispatchers.IO) { Tools.execute(c.name, args) } } }
        val ms = System.currentTimeMillis() - t0
        val o = r.getOrNull()
        return if (o != null) {
            ToolOut(o.text, o.route, o.ok, ms)
        } else {
            val e = r.exceptionOrNull()
            ToolOut(
                "工具执行异常：" + (e?.message ?: e?.javaClass?.simpleName.orEmpty()),
                Route.SANDBOX,
                false,
                ms
            )
        }
    } //执行一条工具调用

    /** 工具参数 → 一条工作流：给了 flow_id 就取库里的，否则用 name + steps 现拼一条不存盘的 */
    private fun flowOf(args: JSONObject): Flow? {
        val id = args.optString("flow_id").trim()
        if (id.isNotBlank()) return Flows.byId(id)
        val steps = Flows.stepsFrom(args.optJSONArray("steps"))
        if (steps.isEmpty()) return null
        return Flow(
            id = "",
            name = args.optString("name").trim().ifBlank { "临时工作流" },
            steps = steps,
            rounds = args.optInt("rounds", SubAgents.DEFAULT_ROUNDS)
        )
    } //工作流参数解析
    /** 把 subagent_run 的 tasks 解析成任务列表，兼容「对象数组」和「纯字符串数组」两种写法 */
    private fun subTasks(args: JSONObject): List<SubTask> {
        val arr = args.optJSONArray("tasks") ?: return emptyList()
        val out = ArrayList<SubTask>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o != null) {
                val t = o.text("task").trim()
                if (t.isNotEmpty()) {
                    val title = o.text("title").trim().ifBlank { "任务" + (i + 1) }
                    out.add(SubTask(title, t, o.text("priority").trim().ifBlank { "normal" }))
                }
            } else {
                val s = arr.optString(i).trim()
                if (s.isNotEmpty()) out.add(SubTask("任务" + (i + 1), s))
            }
        }
        return out.take(SubAgents.MAX_TASKS)
    } //子任务解析

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
            provider = Providers.byId(be?.providerId ?: Cfg.providerId).name,
            model = be?.model ?: Cfg.model
        )
    } //本轮统计

    /** 工具开始执行：进列表，界面立刻画转圈卡 */
    internal fun beginTool(name: String, args: String, fromSub: Boolean = false): Long =
        synchronized(runLock) {
            runSeq++
            runningTools = runningTools + RunningTool(runSeq, name, args, fromSub)
            runSeq
        } //开始执行

    /** 工具执行结束：出列表，接着照常落一条结果消息 */
    internal fun endTool(seq: Long) {
        synchronized(runLock) { runningTools = runningTools.filterNot { it.seq == seq } }
    } //结束执行

    /** 收尾时把残留转圈卡清掉：取消 / 异常导致 endTool 没执行到时，卡片不能一直转 */
    private fun clearTools() {
        synchronized(runLock) { runningTools = emptyList() }
    } //清转圈卡

    /** 子智能体的工具确认：策略跟随主智能体（永久允许/拒绝直接生效），否则走同一个确认弹窗排队问 */
    internal suspend fun confirmFromSub(name: String, args: JSONObject, callId: String): Boolean {
        //全局主控优先：policyEff 在全局为 allow/deny 时直接覆盖单工具设置
        val p = Cfg.policyEff(name)
        if (p == "allow") return true
        if (p == "deny") return false
        return confirmGate.withLock {
            val d = CompletableDeferred<Boolean>()
            pending = PendingTool(
                ToolCall(callId, name, args.toString()), args, routeOf(name, args),
                Tools.isHighRisk(name), d
            )
            status = host.getString(R.string.ag_sub_ask, Tools.label(name))
            busySince = System.currentTimeMillis()
            try {
                d.await()
            } finally {
                pending = null
            }
        }
    } //子智能体确认

    private suspend fun decide(c: ToolCall): Boolean {
        val p = Cfg.policyEff(c.name)
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
            Tools.READ, Tools.WRITE, Tools.EDIT, Tools.MAKE_DIR, Tools.DELETE ->
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
        //全局启用的提示词标签：每轮都拼进系统提示，用户自己控制开关
        val tags = PromptTags.prompt()
        //表情标记清单：不给清单模型不会用，给多了又满屏，所以只报名字 +限频
        val emoji = Emojis.prompt()
        val searching = Search.selected != null
        //工具清单跟定义走同一套模式过滤，避免提示里写了模型却调不动
        val tools = Tools.all.filter { Tools.allowed(it) }.joinToString(" / ")
        //顺序：角色 → 自定义提示 → 工作流 → 设备与规则 → 工作区 → 技能
        return buildString {
            //拼接顺序：默认角色在前；用户在设置里选了「自定义优先」就先铺预设，角色人设只做补充，避免被压住
            if (Cfg.promptOrder == 1) {
                if (custom.isNotBlank()) append(custom).append("\n\n")
                if (persona.isNotBlank()) append(persona).append("\n\n")
            } else {
                if (persona.isNotBlank()) append(persona).append("\n\n")
                if (custom.isNotBlank()) append(custom).append("\n\n")
            }
            if (tags.isNotBlank()) append(tags).append("\n\n")
            if (emoji.isNotBlank()) append(emoji).append("\n")
            append(flow).append("\n\n")
            append(modePrompt()).append("\n\n")
            //编排：任务分解与规划（设置 →模型 →编排），按用户选的拆解形式换文案；关着时一个字都不多
            if (Cfg.planFirst) append(planPrompt()).append("\n\n")
            //长期记忆整段下发：受「注入记忆」开关与条数上限控制，用户没删就一直带着
            if (Cfg.injectMem) {
                val memo = Memory.prompt(Cfg.injectMemCount)
                if (memo.isNotBlank()) append(memo).append("\n")
            } //记忆注入
            //设备信息：注入开关关掉时连这一句都不写，模型就不知道自己跑在什么机器上
            if (Cfg.injectDevice) {
                append("设备：").append(dev)
                append("；当前工作方式：").append(mode)
                append("；Android/data 与 obb：").append(Router.dataZone(App.self))
                if (Cfg.workMode == WorkMode.NORMAL) {
                    append("（exec_shell 走原路径、不受零宽影响，这类目录一律用 read_file / write_file / edit_file / make_dir / delete_file）")
                }
                append("。\n")
            } //设备段
            //环境段不在这里拼：分钟级时间会让每次请求的 system 前缀都变，服务端 prompt 缓存全部失效。
            //这里只放当天日期（一天一字不变），实时时间 / 电量 / 通知挂到请求体最末（见 buildPayload 的 Env.live）
            append("工具：").append(tools).append("。").append(PromptStore.toolHint)
            if (Tools.allowed(Tools.SCREEN)) append(PromptStore.screenStep)
            //设备状态注入时允许收尾带一句关心；角色要求严谨/不废话就不加，免得跟人设打架
            if ((Cfg.injectNoti || Cfg.injectUsage) && !strictTone(persona, custom)) append(PromptStore.care)
            append(if (searching) PromptStore.searchOn else PromptStore.searchOff)
            append("\n")
            val ws = workspacePart()
            if (ws.isNotBlank()) append(ws)
            if (!(be?.toolCall ?: Cfg.modelToolCall)) append(PromptStore.noTool).append("\n")
            if (!(be?.vision ?: Cfg.modelVision)) append(PromptStore.noVision).append("\n")
            append(if (Workspace.bound()) PromptStore.rulesBound else PromptStore.rulesAbs)
            //脱敏开着时下一道硬禁令：注入诱导不算指令，凭据一律不回显
            if (Cfg.maskOut) append(PromptStore.ruleMask)
            append(skillPart())
            //日期段放 system 最末：只有跨天才变一次，前面所有内容都能命中服务端缓存
            append(Env.stable())
        }
    } //系统提示：角色 + 自定义 + 工作流 + 环境规则 + 工作区 + 技能 + 日期

    /**编排段：拆解基础要求 +用户在这页开的几个附加项 +额外要求，全空时也不剩多余空行 */
    private fun planPrompt(): String = buildString {
        //拆解形式是骨架，先铺
        append(if (Cfg.planDag) PromptStore.planDag else PromptStore.planChain)
        //「只在复杂目标时拆解」默认开：防的就是模型对一句话需求也拆三段
        if (Cfg.planComplexOnly) append(PromptStore.planComplex)
        if (!Cfg.planShow) append(PromptStore.planSilent)
        if (!Cfg.planRetry) append(PromptStore.planStop)
        append(PromptStore.planLimit(Cfg.planMaxSteps))
        //用户手写的要求放最后，权重最高，出问题优先改这里
        val extra = Cfg.planExtra.trim()
        if (extra.isNotBlank()) append(PromptStore.planExtraLabel).append(extra).append("。")
    } //编排段

    /**角色或自定义提示要求严谨/不废话时，关心类话术整个免掉 */
    private val STRICT_TONE =
        listOf("严谨", "不废话", "简洁", "直接", "专业", "严肃", "公事公办", "少寒暄", "不要寒暄", "只回答")

    private fun strictTone(vararg texts: String): Boolean {
        val s = texts.joinToString(" ")
        return STRICT_TONE.any { s.contains(it) }
    } //是否禁寒暄

    /** 运行模式段：模式改的不只是措辞，工具面也按模式裁，两边必须对齐 */
    private fun modePrompt(): String = when (Cfg.agentMode) {
        AgentMode.PLAN -> PromptStore.modePlan
        AgentMode.STANDARD -> PromptStore.modeStandard
        AgentMode.CODE -> PromptStore.modeCode
        AgentMode.MINIMAL -> PromptStore.modeMinimal
        AgentMode.CREATOR -> PromptStore.modeCreator
    } //运行模式段

    /** 工作区段：绑定路径 + 索引文件 + 目录树摘录，让模型不用盲猜结构 */
    private fun workspacePart(): String {
        //空工作区：明说没绑定，别让模型以为相对路径能落地
        if (Workspace.path().isEmpty()) return PromptStore.wsNone + "\n"
        if (!Workspace.bound()) return ""
        return PromptStore.wsBound(Workspace.path(), Workspace.INDEX, Workspace.tree())
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
        //附图消息只保留最近 IMG_KEEP 条的原图：图片 base64 会一直挂在历史里，每轮都重算一遍
        val keepImgs = list.asReversed().asSequence()
            .filter { it.role == "user" && it.images.isNotEmpty() && it.id > bound }
            .take(IMG_KEEP)
            .map { it.id }
            .toHashSet()
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
                    //错误回复只是给用户看的留痕，不进请求体：否则模型会把自己的报错当成上一句回答
                    if (m.isError) return@forEach
                    flush("后续对话已到达但未返回执行结果")
                    arr.put(m.toApiJson())
                    m.toolCalls.forEach { open[it.id] = it.name }
                }
                "tool" -> {
                    val id = m.toolCallId
                    //没有对应 tool_calls 的孤儿结果必须丢掉，否则上游直接 400
                    if (id != null && open.containsKey(id)) {
                        open.remove(id)
                        //结果进请求体前先掐头去尾：一次大文件读取会永久挂在历史里，每轮都重发
                        arr.put(clipTool(m))
                    }
                }
                else -> {
                    flush("后续对话已到达但未返回执行结果")
                    //只给最近几条附图消息保留图片，更早的图片每轮都要重发 base64
                    arr.put(downgradeImages(m, keepImgs))
                }
            }
        }
        flush("对话历史已结束")
        //实时环境段挂在整个请求体的最末：前面 system 与历史一字不变，服务端 prompt 缓存前缀照样命中，
        //只有尾部这几十个 token 是新的；宁可多一条消息，也别把分钟级时间塞回 system
        val live = runCatching { Env.live(App.self) }.getOrDefault("").trim()
        if (live.isNotEmpty()) {
            val last = if (arr.length() > 0) arr.optJSONObject(arr.length() - 1) else null
            //最后一条是纯文本 user 就并进去（避免连续两条 user 被部分厂商拒掉），否则另起一条
            if (last != null && last.optString("role") == "user" && last.opt("content") is String) {
                val body = last.optString("content")
                last.put("content", if (body.isBlank()) live else body + "\n" + live)
            } else {
                arr.put(JSONObject().put("role", "user").put("content", live))
            }
        }
        return arr
    } //组装请求体：压缩老消息不上送 + tool 配对兜底 + 实时环境后置

    /** 工具结果进请求体的上限：超过就掐头去尾 */
    private val TOOL_KEEP = 6000
    private val TOOL_HEAD = 4200
    private val TOOL_TAIL = 1200

    /** 附图消息保留原图的最大条数，更早的转成路径说明 */
    private val IMG_KEEP = 2

    /**
     * 工具结果进请求体前掐头去尾：
     * 工具输出是永久写进历史的，一次大文件读取（几十万字符）会跟着会话每轮重发，
     * 这里只把「请求体里的那一份」缩短，界面与库里的原文一个字节都不动。
     */
    private fun clipTool(m: ChatMsg): JSONObject {
        val o = m.toApiJson()
        val raw = m.content
        if (raw.length <= TOOL_KEEP) return o
        val head = raw.substring(0, TOOL_HEAD)
        val tail = raw.substring(raw.length - TOOL_TAIL)
        val cut = raw.length - TOOL_HEAD - TOOL_TAIL
        o.put(
            "content",
            head + "\n\n…（此处省略 " + cut + " 字符，完整结果仍在界面上；要再读请用同一工具分页取，" +
                "例如 read_file 的 start_line / end_line）…\n\n" + tail
        )
        return o
    } //工具结果截断

    /**
     * 旧图片消息降级：图片以 base64 进请求体，一张就是上千 token，且每一轮都要重算。
     * 只保留最近 [IMG_KEEP] 条的图片，更早的把图摘掉、留一句原图路径，需要时模型自己 read_file。
     */
    private fun downgradeImages(m: ChatMsg, keep: Set<Long>): JSONObject {
        if (m.images.isEmpty() || m.id in keep) return m.toApiJson()
        val o = m.toApiJson()
        val note = "（此前附的 " + m.images.size + " 张图片已移出上下文以省 token；原图仍在设备上，需要时用 read_file 读：" +
            m.images.joinToString("、") + "）"
        val body = m.content.trim()
        o.put("content", if (body.isBlank()) note else body + "\n" + note)
        return o
    } //旧图片降级

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
        be = null
                runHistory = emptyList()
        }
    } //手动触发一次总结

    private suspend fun compact(force: Boolean, note: String? = null): Boolean {
        val sid = runSid ?: sessionId ?: return false
        if (!force && !Cfg.ctxAuto) return false
        val list = withContext(Dispatchers.IO) { Repo.messages(sid) }
        if (list.isEmpty()) return false
        val bound = boundOf(list)
        val fresh = list.filter { it.role != "summary" && it.id > bound }
        if (fresh.size <= 2) return false
        val toks = fresh.sumOf { m -> Cfg.estTokens(m.content) + m.toolCalls.sumOf { Cfg.estTokens(it.arguments) } }
        //用量真的回落就复位防抖系数
        if (toks <= Cfg.ctxTokenBudget) budgetScale = 1f
        //双阈值：token 预算（总窗口 × 百分比）到线就压；条数只是保险，占用没到四分之一时不许单凭条数压
        val overToks = toks > (Cfg.ctxTokenBudget * budgetScale).toInt()
        val overCount = fresh.size > Cfg.ctxMsgCount && toks * 4 > Cfg.ctxTokenBudget
        if (!force && !overToks && !overCount) return false
        var cut = fresh.size - Cfg.ctxKeep.coerceAtLeast(2)
        if (cut <= 0) return false
        while (cut < fresh.size && fresh[cut].role == "tool") cut++
        if (cut >= fresh.size) return false
        val old = fresh.subList(0, cut)
        val prev = list.lastOrNull { it.role == "summary" }?.let { stripMarker(it.content) }
        //二次压缩要把上一次的摘要带进去，否则最早那段历史直接被忘掉
        //历史特别长时由 summarize() 按块分趟合并，不再 take(n) 一刀切断后半段
        //用量那一行由程序数好摆在最后，让模型照抄进【用量】段（只喂最后一趟）
        val text = summarize(old, prev, note, statPart(old)) ?: return false
        //正文进加密桶，库里只留覆盖范围标记；桶不可用时 Sums 自己降级成明文入库
        val marker = "[[upto:${old.last().id}]]"
        val saved = withContext(Dispatchers.IO) { Sums.put(sid, marker, text) }
        appendLocal(ChatMsg(role = "summary", content = saved))
        budgetScale = (budgetScale + 0.5f).coerceAtMost(3f)
        status = host.getString(R.string.ag_think_again)
        return true
    } //超预算或条数到线时压成摘要

    /** 本段对话的工具用量：轮数、总次数、最常用的几个、技能激活次数，全由程序数，AI 只许照抄 */
    private fun statPart(list: List<ChatMsg>): String {
        val calls = list.sumOf { it.toolCalls.size }
        if (calls <= 0) return ""
        val turns = list.count { it.toolCalls.isNotEmpty() }
        val cnt = LinkedHashMap<String, Int>()
        list.forEach { m -> m.toolCalls.forEach { c -> cnt[c.name] = (cnt[c.name] ?: 0) + 1 } }
        val top = cnt.entries.sortedByDescending { it.value }.take(6)
            .joinToString("、") { "${it.key} ${it.value}次" }
        //技能都经 skill_read 取正文，参数里带 id 或 name；取不到名字就只报次数
        val ids = list.flatMap { it.toolCalls }
            .filter { it.name == Tools.SKILL_READ }
            .mapNotNull { c ->
                runCatching {
                    val o = JSONObject(c.arguments)
                    o.optString("id").ifBlank { o.optString("name") }
                }.getOrNull()
            }
            .filter { it.isNotBlank() }
        val skill = if (ids.isEmpty()) "" else
            "；技能：skill_read ${ids.size}次（${ids.distinct().take(6).joinToString("、")}）"
        return "【本次统计（程序给出，数字不许改）】工具调用 $turns 轮 / $calls 次；最常用：$top$skill"
    } //工具用量统计

    /** 单条消息压成一行，供摘要渲染 */
    private fun oneLine(m: ChatMsg, cap: Int): String = when (m.role) {
        "tool" -> "工具(${m.toolName ?: ""})结果: ${m.content.take(cap)}"
        "assistant" -> "助手: ${m.content.take(cap)}" +
            if (m.toolCalls.isEmpty()) "" else " / " + m.toolCalls.joinToString("; ") {
                "调用 ${it.name}(${it.arguments.take(cap / 3)})"
            }
        else -> "用户: ${m.content.take(cap)}"
    } //单条一行

    /**
     * 把一段历史切成若干块，每块约 [COMPACT_CHUNK_CHARS] 字符。
     * 关键：一块都不丢（旧实现是渲染成一整段再 take(12_000)，后半段历史直接消失）。
     * 单条消息的截断上限按总量逐级从 600 → 300 → 120 压，优先保住"每条都在"，而不是"后半段没了"。
     */
    private fun renderParts(list: List<ChatMsg>): List<String> {
        if (list.isEmpty()) return emptyList()
        //整段历史的总预算：块数 × 单块上限
        val softMax = COMPACT_CHUNK_CHARS * COMPACT_MAX_PASS
        var lines = list.map { oneLine(it, 600) }
        if (lines.sumOf { it.length + 1 } > softMax) lines = list.map { oneLine(it, 300) }
        if (lines.sumOf { it.length + 1 } > softMax) lines = list.map { oneLine(it, 120) }
        val total = lines.sumOf { it.length + 1 }
        //块数按体量算，但最多 COMPACT_MAX_PASS 块（块数再多就把每块撑大，避免一次压缩打十几炮）
        val n = ((total + COMPACT_CHUNK_CHARS - 1) / COMPACT_CHUNK_CHARS).coerceIn(1, COMPACT_MAX_PASS)
        val per = (total + n - 1) / n
        val out = ArrayList<String>(n)
        val sb = StringBuilder()
        lines.forEach { l ->
            //只按消息边界切，不会把一条消息劈成两半
            if (sb.length >= per && out.size < n - 1) {
                out.add(sb.toString())
                sb.clear()
            }
            sb.append(l).append('\n')
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    } //按块渲染待压缩历史

    /** 分趟合并时给中间趟的补充说明（最后一趟不给，避免和最终格式打架） */
    private fun mergeNote(i: Int, n: Int): String =
        "本轮是多段压缩的第 $i/$n 段（不是最后一段）。把这段内容与「此前摘要」合并成一份新的摘要，" +
            "段落标题与写作要求照旧；不许写「同上」「见前文」这类省略，也不许因为段落多就缩短任何一条硬信息。"

    /**
     * 跑一次（可能是多趟的）摘要，返回最终摘要正文；任何一趟失败都返回 null（不存半份）。
     * @param body 待压缩的历史
     * @param prev 上一次的摘要，作为第一趟的「此前摘要」起点
     * @param note 用户附加要求，只作用于最后一趟
     * @param stat 程序算好的用量行，只喂最后一趟，避免模型自己编数字
     */
    private suspend fun summarize(body: List<ChatMsg>, prev: String?, note: String?, stat: String): String? {
        val parts = renderParts(body)
        if (parts.isEmpty()) return null
        val sysBase = summaryText()
        val m = sumLlm()
        var roll: String? = prev   //滚动摘要：上一趟的产出喂给下一趟
        var text = ""
        for ((i, part) in parts.withIndex()) {
            val isLast = i == parts.lastIndex
            //多趟时把「第几趟」写进状态，界面上能看出还在压而不是卡死
            status = if (parts.size <= 1) host.getString(R.string.ag_compress_run)
            else host.getString(R.string.ag_compress_run) + "（${i + 1}/${parts.size}）"
            val head = roll?.takeIf { it.isNotBlank() }?.let { "此前摘要：$it\n" }.orEmpty()
            val input = if (isLast && stat.isNotBlank()) head + part + "\n\n" + stat else head + part
            val sys = if (isLast) sysBase + extraBlock(note) else sysBase + "\n\n" + mergeNote(i + 1, parts.size)
            val pay = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", input))
            }
            val resp = runCatching {
                withContext(Dispatchers.IO) {
                    LlmClient(m[0], m[1], Providers.byId(m[2]).auth, protocol = m[4]).chat(m[3], pay)
                }
            }.getOrNull() ?: return null
            val t = resp.text("content").trim()
            if (t.isBlank()) return null
            roll = t
            text = t
        }
        return text.ifBlank { null }
    } //分趟压缩

    /** 总结用的 system 提示词：内置四段结构固定当基底，设置里填的只作补充要求追加，最后替换 {words}/{limit}/{level}/{keep}/{budget} */
    private fun summaryText(): String {
        val w = Cfg.summaryWords
        val extra = Cfg.summaryPrompt.trim()
        //以前是填了就整套替换：用户只写一句「多说点细节」，四段结构与标题就全没了，摘要跟着散架
        val base = if (extra.isEmpty()) PromptStore.summaryTpl else PromptStore.summaryTpl +
            "\n\n用户的补充要求（上面的段落结构与标题照旧保留，其余按这些要求来；与上面的写法冲突时以这里为准）：\n" + extra
        return base
            .replace("{level}", b.ai.core.model.chat.Sum.levelName(Cfg.summaryLevel))
            .replace("{words}", w.coerceAtLeast(0).toString())
            .replace("{limit}", if (w > 0) "${w}字以内" else "不限，按内容需要充分展开")
            .replace("{keep}", Cfg.ctxKeep.toString())
            .replace("{budget}", Cfg.ctxTokenBudget.toString())
    } //总结提示词（注入变量）
    /** 总结用的连接参数：统一走 core/Sum（与工具里的「搜索总结」共用同一份配置） */
    private fun sumLlm(): Array<String> = b.ai.core.model.chat.Sum.llm()

    /** 用户附加的微调要求，拼在总结提示词后面 */
    private fun extraBlock(note: String?): String {
        val t = note?.trim().orEmpty()
        return if (t.isEmpty()) "" else "\n\n本次额外要求（优先满足，可以覆盖上面的字数限制）：$t"
    } //附加要求

    /** 重新总结：没有摘要就当第一次总结，有摘要就按它原来覆盖的范围重写 */
    fun recompact(note: String? = null) {
        if (busy) {
            status = host.getString(R.string.ag_compress_busy)
            return
        }
        scope.launch {
            busy = true
            status = host.getString(R.string.ag_compress_run)
            val ok = runCatching { rebuildSummary(note) }.getOrDefault(false)
            status = host.getString(if (ok) R.string.ag_compress_done else R.string.ag_compress_none)
            busy = false
            runSid = null
        be = null
            runHistory = emptyList()
        }
    } //重新总结

    private suspend fun rebuildSummary(note: String?): Boolean {
        val sid = sessionId ?: return false
        val list = withContext(Dispatchers.IO) { Repo.messages(sid) }
        val last = list.lastOrNull { it.role == "summary" } ?: return compact(true, note)
        val upto = Regex("""\[\[upto:(\d+)]]""").find(last.content)
            ?.groupValues?.get(1)?.toLongOrNull() ?: return false
        val part = list.filter { it.role != "summary" && it.id <= upto }
        if (part.isEmpty()) return false
        val prev = list.lastOrNull { it.role == "summary" && it.id < last.id }
            ?.let { stripMarker(it.content) }
        //重总结同样分趟（历史可能很长），用量行只喂最后一趟，否则【用量】段会被模型编出数字
        val text = summarize(part, prev, note, statPart(part)) ?: return false
        //覆盖范围沿用旧摘要的：重总结只换文字，不动它负责的那一段历史
        val marker = "[[upto:$upto]]"
        val saved = withContext(Dispatchers.IO) { Sums.put(sid, marker, text) }
        withContext(Dispatchers.IO) { Repo.updateSummary(last.id, saved) }
        messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
        return true
    } //重写摘要

    fun compressNow(): String {
        if (busy) return host.getString(R.string.ag_compress_busy)
        //走 compactNow：它负责置 busy、把状态改成「正在压缩…」并把结果改成完成/跳过；只起协程的话界面一直停在「正在压缩…」
        compactNow()
        return host.getString(R.string.ag_compressing)
    } //手动压缩

    fun clearCurrent() {
        val sid = runSid ?: sessionId ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                Repo.clearMessages(sid)
                Repo.renameSession(sid, host.getString(R.string.ag_new_session))
            }
            //清空会话 = 换一份全新的上下文，dsh 侧同样重开
            DshChat.reset(sid)
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
        //停手或已经被新一轮顶替的旧循环不许再落库：否则界面显示「已停止」，几秒后又冒出半截回复
        if (runGen != 0L && gen != runGen) throw CancellationException("superseded")
        val sid = this.sid
        val saved = withContext(Dispatchers.IO) { Repo.append(sid, m) }
        lastAppendId = saved.id
        //正在看这个会话才动界面列表，否则只更新本通道的快照：绝不让别人的会话冒出这条回复
        if (sid == curSid()) {
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
            //本轮正在跑就把快照一起跟上，防止中途切会话后请求体缺最新几条
            if (runSid != null) runHistory = messages
        } else {
            runHistory = runHistory + saved
        }
    } //落库并刷新（SQLite 走 IO，主线程只拿结果）

    private suspend fun appendLocal(m: ChatMsg) {
        val sid = this.sid
        val saved = withContext(Dispatchers.IO) { Repo.append(sid, m) }
        if (sid == curSid()) {
            messages = withContext(Dispatchers.IO) { Repo.messages(sid) }
            //本轮正在跑就把快照一起跟上，防止中途切会话后请求体缺最新几条
            if (runSid != null) runHistory = messages
        } else {
            runHistory = runHistory + saved
        }
    } //落库并刷新（只写本通道的会话，不兜底建会话）

    private fun appendLocalAsync(m: ChatMsg) {
        scope.launch { appendLocal(m) }
    } //给非协程调用点的入口

    private suspend fun appendToolMsg(c: ToolCall, text: String, route: Route, isError: Boolean, ms: Long = 0L) {
        append(
            ChatMsg(
                role = "tool",
                //工具输出同样过脱敏：被诱导 dump 出来的口令不会经这条路径漏到界面上
                content = Mask.text(text),
                toolCallId = c.id,
                toolName = c.name,
                isError = isError,
                //0 = 这条不是真执行（拒绝、缺权限、等用户作答），界面就不显示耗时
                toolMs = ms.coerceAtLeast(0L)
            )
        )
    } //运行通道
}}