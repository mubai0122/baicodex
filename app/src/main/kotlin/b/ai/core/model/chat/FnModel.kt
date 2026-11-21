/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:08
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
    /** 提示词优化：用一条独立配置，跟聊天主模型分开，省 token 也能换小模型 */
    const val PROMPT_OPT = "prompt_opt"
    /** 记忆整理：把零散记忆合并、去重、改写成更省 token 的条目 */
    const val MEM = "mem"

    fun profileId(kind: String): String = when (kind) {
        TITLE -> Cfg.titleProfileId
        PROMPT_OPT -> Cfg.promptOptProfileId
        MEM -> Cfg.memProfileId
        else -> Cfg.summaryProfileId
    }

    /** 返回 baseUrl / apiKey / providerId / model / protocol 五元组 */
    fun conn(kind: String): Array<String> {
        val pid = profileId(kind)
        if (pid.isNotBlank()) {
            val p = Profiles.list.firstOrNull { it.id == pid && it.model.isNotBlank() }
            if (p != null) return arrayOf(p.baseUrl, p.apiKey, p.providerId, p.model, p.protocol)
        }
        //跟随当前聊天配置时，协议也跟当前这条走
        return arrayOf(Cfg.baseUrl, Cfg.apiKey, Cfg.providerId, Cfg.model, Cfg.modelProtocol)
    } //取连接参数（第 5 项是接口协议）

    fun auth(kind: String): AuthStyle = Providers.byId(conn(kind)[2]).auth //鉴权方式

    fun label(kind: String): String = conn(kind)[3].ifBlank { "未配置" } //给界面看模型名
} //功能模型连接参数