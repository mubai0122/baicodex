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

package b.ai.core.cfg.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** 界面语言：空串跟随系统，其它按 BCP-47 建 Configuration，改完由调用方 recreate 生效 */
object Lang {
    //可选语言：代码 to 自称（自称固定不翻译，任何语言下都能认出自己那一项）
    val options = listOf(
        "" to "跟随系统",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "fr" to "Français"
    )

    /** 支持的 BCP-47 代码（不含「跟随系统」） */
    val codes = options.map { it.first }.filter { it.isNotEmpty() }

    /** 按当前设置包一层 Context：Activity 的 resources 跟着换语言 */
    fun wrap(base: Context): Context {
        val tag = Cfg.lang
        if (tag.isBlank()) return base
        val loc = runCatching { Locale.forLanguageTag(tag) }.getOrNull() ?: return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return base.createConfigurationContext(cfg)
    } //换语言的 Context 包装

    /** 系统语言落在支持列表里的哪一个；不支持就回 zh-CN */
    fun systemTag(): String {
        val l = Locale.getDefault()
        val tag = l.language + if (l.country.isBlank()) "" else "-" + l.country
        return when {
            tag.startsWith("zh") && (tag.contains("TW") || tag.contains("HK") || tag.contains("MO")) -> "zh-TW"
            tag.startsWith("zh") -> "zh-CN"
            tag.startsWith("en") -> "en"
            tag.startsWith("ja") -> "ja"
            tag.startsWith("fr") -> "fr"
            else -> "zh-CN"
        }
    } //系统语言映射
} //界面语言