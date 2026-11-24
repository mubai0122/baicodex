/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：把 dsh 的 agent 循环当成 Baicodex chat 的引擎来驱动。
 *
 * 为什么必须这样接（源码取证，别改成"看起来更直接"的写法）：
 *  - dsh-sdk-protocol 的 ServerRequest 表只有 initialize / session.* / shutdown，
 *    没有 tools/list、也没有 tool/execute —— 插件注册的工具只能由 dsh 自己的 agent 循环触发。
 *  - 所以要让插件在 chat 里干活，唯一的路是：把用户这条消息交给 dsh（session/query），
 *    再把 dsh 推回来的 session.event 翻译成我们的消息 + 工具卡。
 *  - 会话身份：Baicodex 一个会话对应 dsh 一个会话（映射持久化在 SharedPreferences），
 *    这样 dsh 那边自己维护上下文与压缩，我们只管渲染。
 *
 * 事件映射（实测 dsh 0.2.2-rc.5）：
 *  - assistant/attempt      → 流式预览（有 chunk 就显示，最终以 assistant/message 为准）
 *  - assistant/message      → 一步的最终助手消息：text 块进正文，tool-call 块进工具卡
 *  - tool/call             → beginTool（卡片开始转圈）
 *  - tool/result           → 工具结果消息
 *  - turn/end、session.status(idle) → 本轮收工
 *  - 错误                  → data.reason.kind == error（assistant/attempt）或 agent 层 error
 */
package b.ai.core.dsh

import android.content.Context
import b.ai.App
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** dsh 一步里的一个工具调用（id 用 dsh 自己的 callId，工具卡按它对上结果） */
data class DshCall(val id: String, val name: String, val args: String)

/** dsh 运行时推给 chat 的事件：顺序由队列保证，渲染侧按顺序消费 */
sealed class DshEv {
    /** 流式预览文本（assistant/attempt 能解析出来才有） */
    data class Delta(val text: String) : DshEv()

    /** 一步的最终助手消息：正文 + 这一步发起的工具调用 */
    data class Msg(val text: String, val calls: List<DshCall>) : DshEv()

    /** 工具开始执行：卡片立刻转圈 */
    data class ToolBegin(val callId: String, val name: String, val args: String) : DshEv()

    /** 工具执行完成 */
    data class ToolDone(val callId: String, val name: String, val text: String, val ok: Boolean) : DshEv()

    /** 状态文案（分智能体启动、压缩中…） */
    data class State(val text: String) : DshEv()

    /** 本轮失败：文案给用户看 */
    data class Fail(val text: String) : DshEv()

    /** 本轮收工 */
    data object Done : DshEv()
} //dsh 事件

object DshChat {
    private const val PREF = "dsh_chat"
    private const val PREFIX = "sid_"

    /** 单轮总超时：dsh 自己也有工具超时，这里只兜住"永远不回 idle"的极端情况 */
    private const val TURN_TIMEOUT_MS = 20 * 60 * 1000L

    /** 本进程里已经 resume 过的 dsh 会话（协议规定重复 resume 会被拒，只试一次） */
    private val resumed = HashSet<String>()

    private fun prefs(): android.content.SharedPreferences =
        App.self.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /**
     * Baicodex 会话 → dsh 会话 id：一对一且持久化。
     * 新建会话（或清空会话）时调用 [reset] 换一个新的，dsh 那边就会重新起一份上下文。
     */
    fun sidOf(uid: String): String {
        val p = prefs()
        val key = PREFIX + uid
        val old = p.getString(key, null)
        if (!old.isNullOrBlank()) return old
        val v = DshSdk.newSessionId()
        p.edit().putString(key, v).apply()
        return v
    } //会话映射

    /** 丢掉映射：下一次 [sidOf] 会生成新的 dsh 会话（清空当前会话 / 删除会话时调） */
    fun reset(uid: String) {
        runCatching { prefs().edit().remove(PREFIX + uid).apply() }
    } //重开 dsh 会话

    /**
     * 跑一轮：把 prompt 交给 dsh，事件按顺序通过 [emit] 回调（在调用方的协程上下文里串行执行）。
     * @param uid Baicodex 会话 id（决定用哪个 dsh 会话）
     * @param prompt 用户消息纯文本（附件路径已拼进正文）
     * @param cwd 工作目录（宿主路径即可，proot 里 /sdcard 与 /storage 同路径；空则不切）
     * @param emit 事件消费者，必须是挂起函数以便按序落库
     * @return null 表示正常收工；否则是给用户看的错误文案
     */
    suspend fun run(
        uid: String,
        prompt: String,
        cwd: String = "",
        onLog: (String) -> Unit = {},
        emit: suspend (DshEv) -> Unit
    ): String? {
        //0. 内核就位：chat 引擎要求 dsh 常驻，没起来就现起一个
        if (!DshSdk.isRunning()) {
            emit(DshEv.State("启动 dsh 内核…"))
            if (!DshSdk.start(onLog = onLog)) {
                return "dsh 内核启动失败：" + DshSdk.error().ifBlank { "未知原因" }
            }
        }

        val dsid = sidOf(uid)
        //1. 会话恢复：只有第一次（本进程内）尝试，失败说明是全新会话，由 query 懒建
        if (resumed.add(dsid)) runCatching { DshSdk.resume(dsid) }
        if (cwd.isNotBlank()) runCatching { DshSdk.setWorkingDir(dsid, cwd) }

        val ch = Channel<DshEv>(Channel.UNLIMITED)
        //状态机：必须先看到 running，之后的 idle 才算收工（否则会把上一轮的 idle 当成本轮结束）
        var sawRunning = false

        //2. 事件桥：reader 协程里只做解析 + trySend，渲染顺序由 channel 保证
        DshSdk.onNotify = { method, params ->
            val sid = params.optString("sessionId")
            when (method) {
                "session.event" -> {
                    if (sid.isEmpty() || sid == dsid) parseEvent(params.optJSONObject("event"), ch)
                } //本会话事件
                "session.status" -> {
                    if (sid == dsid) {
                        when (params.optString("status")) {
                            "running" -> sawRunning = true
                            "idle" -> if (sawRunning) ch.trySend(DshEv.Done)
                        }
                    }
                } //本会话状态
                "subagent.started" -> if (sid == dsid) ch.trySend(DshEv.State("分智能体启动"))
                "subagent.finished" -> if (sid == dsid) ch.trySend(DshEv.State("分智能体收工"))
                else -> Unit
            }
        } //事件桥

        try {
            //3. 下发
            if (!DshSdk.queryIn(dsid, prompt, onLog)) {
                return "下发失败：" + DshSdk.error().ifBlank { "dsh 没有接收这条消息" }
            }
            //4. 消费到收工
            val deadline = System.currentTimeMillis() + TURN_TIMEOUT_MS
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return "dsh 超时：${TURN_TIMEOUT_MS / 60_000} 分钟内没有收工"
                val ev = withTimeoutOrNull(left) { ch.receive() } ?: return "dsh 超时：没有收到收工信号"
                emit(ev)
                if (ev is DshEv.Done || ev is DshEv.Fail) break
            }
        } finally {
            DshSdk.onNotify = null
            ch.close()
        }
        return null
    } //跑一轮

    //region 解析

    /** session.event → DshEv：认不出的类型直接忽略，绝不抛异常打断读循环 */
    private fun parseEvent(ev: JSONObject?, ch: Channel<DshEv>) {
        if (ev == null) return
        val type = ev.optString("type")
        val data = ev.optJSONObject("data") ?: JSONObject()
        when (type) {
            //流式预览：只有能拼出文本才发，防止和最终消息重复渲染
            "assistant/attempt" -> {
                val sb = StringBuilder()
                val stream = data.optJSONArray("stream") ?: JSONArray()
                for (i in 0 until stream.length()) {
                    val chunk = stream.optJSONObject(i)?.optJSONObject("chunk") ?: continue
                    when (chunk.optString("type")) {
                        "text" -> sb.append(chunk.optString("text"))
                        "finish" -> {
                            val kind = chunk.optJSONObject("reason")?.optString("kind").orEmpty()
                            if (kind == "error") ch.trySend(DshEv.Fail("模型请求失败（dsh 报 error）"))
                        }
                    }
                }
                if (sb.isNotEmpty()) ch.trySend(DshEv.Delta(sb.toString()))
            } //流式预览

            //一步的最终助手消息：正文与工具调用都在 content 块里
            "assistant/message" -> {
                val msg = data.optJSONObject("message") ?: data
                ch.trySend(parseMessage(msg))
            } //助手消息

            //工具开始：卡片转圈
            "tool/call" -> {
                val callId = data.optString("callId").ifBlank { data.optString("id") }
                val name = data.optString("name")
                if (name.isNotBlank()) {
                    ch.trySend(DshEv.ToolBegin(callId.ifBlank { name }, name, argText(data.opt("arguments"))))
                }
            } //工具开始

            //工具结果：content 块拼成文本
            "tool/result" -> {
                val msg = data.optJSONObject("message") ?: data
                val callId = msg.optString("toolCallId")
                    .ifBlank { msg.optJSONObject("source")?.optString("callId").orEmpty() }
                    .ifBlank { data.optString("callId") }
                val name = msg.optString("toolName").ifBlank { data.optString("name") }
                val ok = !msg.has("error") && !data.has("error")
                ch.trySend(DshEv.ToolDone(callId, name, blocksText(msg.optJSONArray("content")), ok))
            } //工具结果

            //一轮结束
            "turn/end" -> {
                val kind = data.optJSONObject("reason")?.optString("kind").orEmpty()
                if (kind == "error") ch.trySend(DshEv.Fail("dsh 本轮失败（turn/end reason=error）"))
                else ch.trySend(DshEv.Done)
            } //一轮结束

            //压缩开始：给个状态文案，长会话压缩要几秒
            "compaction/start" -> ch.trySend(DshEv.State("压缩上下文…"))
            else -> Unit
        }
    } //事件解析

    /** assistant/message → 正文 + 工具调用列表 */
    private fun parseMessage(msg: JSONObject): DshEv.Msg {
        val text = StringBuilder()
        val calls = ArrayList<DshCall>()
        val arr = msg.optJSONArray("content") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            when (b.optString("type")) {
                "text" -> text.append(b.optString("text"))
                "tool-call" -> {
                    val name = b.optString("name")
                    if (name.isBlank()) continue
                    calls.add(
                        DshCall(
                            id = b.optString("id").ifBlank { "$name-$i" },
                            name = name,
                            args = argText(b.opt("arguments"))
                        )
                    )
                } //工具调用块
                //thinking / image 等块不进正文：正文只放用户能读的文字
                else -> Unit
            }
        }
        return DshEv.Msg(text.toString(), calls)
    } //解析助手消息

    /** content 块数组 → 纯文本（tool/result 用的就是这套结构） */
    private fun blocksText(arr: JSONArray?): String {
        if (arr == null) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            when (b.optString("type")) {
                "text" -> sb.append(b.optString("text"))
                else -> {
                    //非文本块（图片等）：留个占位，别让用户以为结果为空
                    val t = b.optString("type")
                    if (t.isNotBlank()) sb.append("[$t]")
                }
            }
        }
        return sb.toString()
    } //块数组转文本

    /** arguments 在 dsh 里是 JSON 字符串，但也可能是对象：统一成字符串给 UI 展示 */
    private fun argText(v: Any?): String = when (v) {
        null -> "{}"
        is String -> v
        is JSONObject -> v.toString()
        is JSONArray -> v.toString()
        else -> v.toString()
    } //参数转字符串

    //endregion
} //dsh chat 引擎