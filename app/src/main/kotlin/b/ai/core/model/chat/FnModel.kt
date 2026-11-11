package b.ai.core.model.chat

import b.ai.core.cfg.app.Cfg
import b.ai.core.data.profile.Profiles
import b.ai.core.plugin.skill.Providers
import b.ai.core.model.chat.AuthStyle

/**
 * 功能模型：标题生成、上下文总结可以各用一条独立的模型配置（省 token、也可用小模型提速）。
 * 没配就用当前聊天选中的那条，连接参数与主对话完全一致。
 */
object FnModel {
    const val TITLE = "title"
    const val SUMMARY = "summary"

    fun profileId(kind: String): String =
        if (kind == TITLE) Cfg.titleProfileId else Cfg.summaryProfileId

    /** 返回 baseUrl / apiKey / providerId / model 四元组 */
    fun conn(kind: String): Array<String> {
        val pid = profileId(kind)
        if (pid.isNotBlank()) {
            val p = Profiles.list.firstOrNull { it.id == pid && it.model.isNotBlank() }
            if (p != null) return arrayOf(p.baseUrl, p.apiKey, p.providerId, p.model)
        }
        return arrayOf(Cfg.baseUrl, Cfg.apiKey, Cfg.providerId, Cfg.model)
    } //取连接参数

    fun auth(kind: String): AuthStyle = Providers.byId(conn(kind)[2]).auth //鉴权方式

    fun label(kind: String): String = conn(kind)[3].ifBlank { "未配置" } //给界面看模型名
} //功能模型连接参数