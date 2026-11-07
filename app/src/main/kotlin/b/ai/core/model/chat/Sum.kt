package b.ai.core.model.chat
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.profile.Profiles
import b.ai.core.plugin.skill.Providers
import b.ai.net.api.llm.text

import b.ai.net.api.llm.LlmClient
import org.json.JSONArray
import org.json.JSONObject

/** 一次性总结：走设置里的「总结模型」，没配就跟随当前聊天模型；同步阻塞，调用方必须在 IO 线程 */
object Sum {
    /** 喂给总结模型的输入上限：再长就在末尾截断，避免把整份搜索结果原样灌进去 */
    const val MAX_IN = 16_000

    /** 摘要档位名：0 简洁 / 1 详细 / 2 全面，设置页与压缩提示词共用一份 */
    fun levelName(level: Int): String = when (level.coerceIn(0, 2)) {
        0 -> "简洁"
        1 -> "详细"
        else -> "全面"
    } //档位名

    /** 档位的人话说明：字数上限由 Cfg.summaryWords 给，这里只讲取舍 */
    fun levelHint(level: Int): String = when (level.coerceIn(0, 2)) {
        0 -> "上限 1000 字，可能跟随轮次丢失关键"
        1 -> "上限 2000 字，推荐"
        else -> "不限字数，最推荐"
    } //档位说明

    /** 返回 baseUrl / apiKey / providerId / model：与聊天用的压缩器同一个来源 */
    fun llm(): Array<String> {
        val p = Profiles.list.firstOrNull { it.id == Cfg.summaryProfileId && it.model.isNotBlank() }
            ?: return arrayOf(Cfg.baseUrl, Cfg.apiKey, Cfg.providerId, Cfg.model)
        return arrayOf(p.baseUrl, p.apiKey, p.providerId, p.model)
    } //取总结模型连接参数

    /** 当前总结模型名，用于在结果里标明是谁归纳的 */
    fun label(): String = llm()[3].ifBlank { "未配置" }

    /** 跑一次总结：失败返回 null（调用方自己决定要不要提示），不带工具定义 */
    fun run(system: String, user: String, cap: Int = MAX_IN): String? {
        val m = llm()
        if (m[0].isBlank() || m[3].isBlank()) return null
        val text = if (user.length > cap) user.take(cap) + "\n…（已截断）" else user
        val pay = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            put(JSONObject().put("role", "user").put("content", text))
        }
        val msg = runCatching {
            LlmClient(m[0], m[1], Providers.byId(m[2]).auth)
                .chat(m[3], pay, temperature = 0.2, tools = JSONArray())
        }.getOrNull() ?: return null
        return msg.optString("content").trim().ifBlank { null }
    } //跑一次总结
} //总结模型