package b.ai.native

import org.json.JSONObject

/**
 * 内核事件。
 *
 * 与 Rust 侧 `AppEvent` 一一对应（字段名见 `core/feature/app/event.rs` 的 serde 定义）。
 *
 * ## 为什么事件要类型化
 *
 * 界面里 `when (e)` 分支必须穷尽。直接拿 JSON 判断字符串 key，改一个字段名
 * 编译器不会提醒，运行时静默不显示——这类 bug 在真机上极难发现。
 *
 * ## 为什么不用 sealed class 的 data object
 *
 * 事件量大（每个 token 一条 Delta），用 object 常量比每次 new 一个实例省。
 * 有字段的那几种才建实例。
 */
sealed class AppEvent {

    /** 状态栏一句话。 */
    data class Status(val text: String) : AppEvent()

    /** 流式增量：正文与思考分开推。 */
    data class Delta(val content: String, val reason: String) : AppEvent()

    /** 用户消息已落库（界面照它画，别自己先画一条）。 */
    data class UserMessage(val id: Long, val text: String, val images: List<String>) : AppEvent()

    /** 一条消息已落库。 */
    data class Message(val message: JSONObject) : AppEvent()

    /** 工具开始 / 结束。 */
    data class ToolStart(val seq: Long, val name: String, val args: String, val fromSub: Boolean) : AppEvent()
    data class ToolEnd(val seq: Long) : AppEvent()

    /** 忙闲变化。 */
    data class Busy(val busy: Boolean) : AppEvent()

    /** 会话列表变了。 */
    data class Sessions(val list: org.json.JSONArray) : AppEvent()

    /** 一次性提示。 */
    data class Notice(val text: String) : AppEvent()

    /** 需要用户确认工具调用 / 回答问题 / 裁决计划。 */
    data object ConfirmRequest : AppEvent()
    data object AskRequest : AppEvent()
    data object PlanRequest : AppEvent()

    /** 用量变化。 */
    data class Usage(val prompt: Long, val completion: Long) : AppEvent()

    /** 出错。message 已经是给人看的字，直接显示。 */
    data class Error(val code: String, val message: String) : AppEvent()

    /** 退出。 */
    data object Quit : AppEvent()

    companion object {
        /**
         * 解析一行事件 JSON。不认识的事件返回 null（内核升级后旧界面不至于崩）。
         */
        fun parse(json: String): AppEvent? = runCatching {
            val o = JSONObject(json)
            when (o.optString("type")) {
                "status" -> Status(o.optString("text"))
                "delta" -> Delta(o.optString("content"), o.optString("reason"))
                "user_message" -> UserMessage(
                    o.optLong("id"),
                    o.optString("text"),
                    o.optJSONArray("images").toStringList()
                )
                "message" -> Message(o.optJSONObject("message") ?: JSONObject())
                "tool_start" -> ToolStart(
                    o.optLong("seq"),
                    o.optString("name"),
                    o.optString("args"),
                    o.optBoolean("from_sub")
                )
                "tool_end" -> ToolEnd(o.optLong("seq"))
                "busy" -> Busy(o.optBoolean("busy"))
                "sessions" -> Sessions(o.optJSONArray("list") ?: org.json.JSONArray())
                "notice" -> Notice(o.optString("text"))
                "confirm" -> ConfirmRequest
                "ask" -> AskRequest
                "plan" -> PlanRequest
                "usage" -> Usage(o.optLong("prompt"), o.optLong("completion"))
                "error" -> Error(o.optString("code"), o.optString("message"))
                "quit" -> Quit
                else -> null
            }
        }.getOrNull()
    }
}

internal fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val s = optString(i, "")
        if (s.isNotEmpty()) out.add(s)
    }
    return out
}

/** 内核抛回来的错误。`code` 是稳定枚举，界面可据此分支（比如 auth 跳设置页）。 */
class KernelException(
    val code: String,
    override val message: String,
    val retryable: Boolean = false
) : Exception(message) {

    /** 给用户看的短句。 */
    fun userText(): String = when (code) {
        "auth" -> "鉴权失败：请检查 API Key"
        "network" -> "网络不通：$message"
        "timeout" -> "请求超时：$message"
        "not_found" -> "没找到：$message"
        "conflict" -> "状态冲突：$message"
        "invalid" -> "参数不对：$message"
        "crypto" -> "解密失败：$message"
        "db" -> "数据库出错：$message"
        "ffi" -> "内核未就绪：$message"
        else -> message
    }

    companion object {
        /** 从 `{"ok":false,"error":{...}}` 构造。 */
        fun of(error: JSONObject): KernelException = KernelException(
            code = error.optString("code", "internal"),
            message = error.optString("message", "未知错误"),
            retryable = error.optBoolean("retryable", false)
        )
    }
}
