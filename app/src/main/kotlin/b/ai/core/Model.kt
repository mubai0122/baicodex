package b.ai.core

import b.ai.R

import org.json.JSONArray
import org.json.JSONObject

/** 工作方式：三档权限档位 */
enum class WorkMode(
    val id: String,
    val label: String,
    val desc: String,
    val labelRes: Int,
    val descRes: Int
) {
    NORMAL("normal", "普通用户", "App 沙箱权限，可选零宽路径拓展", R.string.wm_normal, R.string.wm_normal_d),
    SHIZUKU("shizuku", "Shizuku", "Binder 桥接，得到 shell(UID 2000) 能力", R.string.wm_shizuku, R.string.wm_shizuku_d),
    ROOT("root", "Root", "su -c 直接执行，无限制", R.string.wm_root, R.string.wm_root_d);

    companion object {
        fun of(id: String?): WorkMode = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** 鉴权方式 */
enum class AuthStyle(val id: String, val label: String) {
    BEARER("bearer", "Authorization: Bearer"),
    X_API_KEY("x-api-key", "api-key 头"),
    NONE("none", "无鉴权");

    companion object {
        fun of(id: String?): AuthStyle = entries.firstOrNull { it.id == id } ?: BEARER
    }
}

/** 厂商条目：只存名称 + base_url + 鉴权方式 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val auth: AuthStyle = AuthStyle.BEARER,
    val custom: Boolean = false
)

/** 权限路径 */
enum class Route(val label: String, val labelRes: Int) {
    SANDBOX("沙箱", R.string.rt_sandbox),
    ALL_FILES("全文件", R.string.rt_all),
    ZERO_WIDTH("零宽", R.string.rt_zero),
    SHIZUKU("Shizuku", R.string.rt_shizuku),
    ROOT("root", R.string.rt_root)
}

/** 会话 */
data class SessionRow(
    val id: String,
    val title: String,
    val category: String,
    val updatedAt: Long = 0L,
    val preview: String = "",
    val count: Int = 0,
    val descr: String = "",
    val pinned: Boolean = false,
    /** 会话自己的智能体角色 id，空 = 默认助手 */
    val persona: String = "",
    /** 会话自己的工作区目录，空 = 用全局兜底 */
    val workspace: String = ""
)

/** 工具调用 */
data class ToolCall(val id: String, val name: String, val arguments: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("arguments", arguments)
}

/** 一轮请求的统计：token / 速度 / 耗时 / 供应商与模型，展示在气泡下面 */
data class MsgMeta(
    val at: Long = 0L,
    val ms: Long = 0L,
    val firstMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val provider: String = "",
    val model: String = ""
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    /** 生成速度：首字到达之后才算，首字前的时间属于排队，不该摊到 tok/s 里 */
    val speed: Double
        get() {
            if (completionTokens <= 0) return 0.0
            val gen = (ms - firstMs).coerceAtLeast(1L) / 1000.0
            return completionTokens / gen
        }

    fun toJson(): JSONObject = JSONObject()
        .put("at", at)
        .put("ms", ms)
        .put("firstMs", firstMs)
        .put("promptTokens", promptTokens)
        .put("completionTokens", completionTokens)
        .put("cachedTokens", cachedTokens)
        .put("provider", provider)
        .put("model", model)

    companion object {
        fun of(o: JSONObject?): MsgMeta? {
            if (o == null) return null
            return MsgMeta(
                at = o.optLong("at", 0L),
                ms = o.optLong("ms", 0L),
                firstMs = o.optLong("firstMs", 0L),
                promptTokens = o.optInt("promptTokens", 0),
                completionTokens = o.optInt("completionTokens", 0),
                cachedTokens = o.optInt("cachedTokens", 0),
                provider = o.optString("provider", ""),
                model = o.optString("model", "")
            )
        } //从 JSON 还原
    }
} //单轮请求统计

/** 一条会话记录（消息 / 工具调用 / 工具结果） */
data class ChatMsg(
    val id: Long = 0L,
    val role: String,
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val isError: Boolean = false,
    /** 模型思考过程（reasoning_content），仅展示，不回传给模型 */
    val reasoning: String = "",
    /** 附带的图片（本应用私有目录里的绝对路径），仅用户消息使用 */
    val images: List<String> = emptyList(),
    /** 附带的文件（本地绝对路径），仅用户消息使用；模型拿 read_file 自己去读 */
    val files: List<String> = emptyList(),
    /** 本轮统计，只有 assistant 消息带 */
    val meta: MsgMeta? = null,
    /** 已收藏：长按菜单里钉住，收藏夹里能翻回来 */
    val starred: Boolean = false,
    /** 父消息 id：0 表示会话根；重发 / 重新生成靠它长出新分支 */
    val parentId: Long = 0L,
    /** 同父兄弟里的序号（从 1 起）与总数，只有总数 > 1 时界面才显示 ‹ n/m › */
    val bIdx: Int = 0,
    val bTotal: Int = 0
) {
    fun toApiJson(): JSONObject {
        val o = JSONObject().put("role", role)
        //附件文件不走进图片那种多模态结构：路径直接写进正文，模型用 read_file 自己读
        val body = if (files.isEmpty()) content else buildString {
            if (content.isNotBlank()) append(content).append("\n")
            append("（用户附了 ").append(files.size).append(" 个文件，本地绝对路径如下，需要时用 read_file 读）\n")
            files.forEach { append(it).append("\n") }
        }
        if (role == "assistant" && toolCalls.isNotEmpty()) {
            o.put("content", content)
            val arr = JSONArray()
            toolCalls.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("type", "function")
                        .put("function", JSONObject().put("name", c.name).put("arguments", c.arguments))
                )
            }
            o.put("tool_calls", arr)
        } else if (role == "tool") {
            o.put("tool_call_id", toolCallId ?: "")
            if (!toolName.isNullOrBlank()) o.put("name", toolName)
            o.put("content", content.ifBlank { "[空]" })
        } else if (role == "user" && images.isNotEmpty()) {
            //模型不支持视觉：不塞图片，降级成一句话，省得上游直接 400
            if (!Cfg.modelVision) {
                val note = "（用户附了 ${images.size} 张图片，当前模型不支持图片输入，看不到图片内容）"
                o.put("content", body.ifBlank { note } + "\n" + note)
                return o
            }
            //多模态：内容走数组，文本在前图片在后，OpenAI 兼容接口都认这个结构
            val parts = JSONArray()
            parts.put(JSONObject().put("type", "text").put("text", body.ifBlank { "看这些图片" }))
            images.forEach { p ->
                val url = Images.dataUrl(p) ?: return@forEach
                parts.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", url))
                )
            }
            o.put("content", parts)
        } else {
            o.put("content", body.ifBlank { "[空]" })
        }
        return o
    } //转成 OpenAI Chat Completions 的 message 结构
} //一条会话记录

/** 收藏夹一条：消息本体 + 所属会话（会话被删掉时 title 为空，仍可读原文） */
data class StarHit(
    val msg: ChatMsg,
    val at: Long,
    val sessionId: String,
    val title: String
) //收藏夹条目