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

// 花括号参数：用户在输入框里写 {time} / {battery} 这类占位符，发送前一次性替换成真实值。
// 只替换白名单内的名字，未知的 {...} 原样保留，避免误伤代码片段里的花括号。
package b.ai.core.cfg.env

import android.content.Context
import android.os.BatteryManager
import b.ai.App
import b.ai.core.cfg.app.Cfg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptVars {
    /** 参数清单：token 用于插入输入框，提示文字用于面板上展示 */
    data class Var(val token: String, val label: String, val note: String)

    /** 面板上列的参数，顺序就是展示顺序 */
    val ALL = listOf(
        Var("{time}", "时间", "14:05"),
        Var("{date}", "日期", "2026-11-20"),
        Var("{weekday}", "星期", "星期五"),
        Var("{datetime}", "日期时间", "2026-11-20 14:05"),
        Var("{battery}", "电量", "82%（未充电）"),
        Var("{model}", "模型名", "当前模型"),
        Var("{mode}", "运行模式", "标准模式"),
        Var("{app}", "应用版本", "1.19.0")
    ) //参数清单

    /** 占位符正则：只认 ALL 里的名字，其它花括号不动 */
    private val KEYS = ALL.map { it.token.removeSurrounding("{", "}") }

    /**
     * 替换文本里的参数占位符。没有花括号直接原样返回，不做任何扫描。
     * c 传 null 时退回 App.self，取不到就留原样。
     */
    fun fill(text: String, c: Context? = null): String {
        if (!text.contains('{')) return text
        val ctx = c ?: App.self
        var out = text
        KEYS.forEach { k ->
            val token = "{$k}"
            if (!out.contains(token)) return@forEach
            out = out.replace(token, value(k, ctx))
        }
        return out
    } //替换参数

    /** 单个参数的值：读不到就返回空串，宁可少一段也不要瞎编 */
    private fun value(key: String, c: Context): String {
        val now = Date()
        return when (key) {
            "time" -> fmt("HH:mm", now)
            "date" -> fmt("yyyy-MM-dd", now)
            "weekday" -> fmt("EEEE", now)
            "datetime" -> fmt("yyyy-MM-dd HH:mm", now)
            "battery" -> battery(c)
            "model" -> Cfg.model.ifBlank { "未选模型" }
            "mode" -> Cfg.agentMode.id
            "app" -> b.ai.net.api.update.Update.clientVersion()
            else -> ""
        }
    } //单个参数取值

    private fun fmt(p: String, d: Date): String = SimpleDateFormat(p, Locale.CHINA).format(d)

    /** 电量文案：拿不到电池服务就写未知，不猜数字 */
    private fun battery(c: Context): String {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return "电量未知"
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        return "$pct%" + if (bm.isCharging) "（充电中）" else "（未充电）"
    } //电量
} //花括号参数