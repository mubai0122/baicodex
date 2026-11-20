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

package b.ai.core.cfg.env
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.usage.Usage
import b.ai.core.place.geo.Place
import b.ai.core.task.hook.Noti
import b.ai.net.api.llm.text

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 系统提示里的环境注入：时间 / 电量 / 位置，缺权限时如实写缺失，不编数据 */
object Env {
    /** 拼环境段；三个开关全关时返回空串，请求里就一个字都不多 */
    fun block(c: Context): String {
        val any = Cfg.injectTime || Cfg.injectBattery || Cfg.injectPlace || Cfg.injectNoti || Cfg.injectUsage
        if (!any) return ""
        val parts = ArrayList<String>(6)
        if (Cfg.injectTime) {
            parts.add("时间 " + SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINA).format(Date()))
        }
        if (Cfg.injectBattery) parts.add(battery(c))
        if (Cfg.injectPlace) parts.add(place(c))
        if (Cfg.injectUsage) parts.add(used(c))
        if (Cfg.injectNoti) parts.add(Noti.brief(c, Cfg.notiLimit))
        //模板走种子（正文在 local.properties），{parts} 由上面各项拼好；缺种子时用代码内兜底
        val tpl = b.ai.BuildConfig.SEED_PROMPT_ENV_BLOCK.trim().ifBlank {
            "环境：{parts}（本机实时数据，够用就直接用，别反问用户）"
        }
        return tpl.replace("{parts}", parts.joinToString("；")) + "\n"
    } //环境段

    /** 今日使用时长：没授权就如实写缺失，不编数字 */
    private fun used(c: Context): String {
        if (!Usage.allow(c)) return "使用时长 未授权（缺使用情况访问权限）"
        val (min, apps) = Usage.today(c)
        if (min <= 0) return "使用时长 今天还没记录"
        val top = if (apps.isEmpty()) "" else apps.joinToString("、", prefix = "（", postfix = "）") {
            it.name + " " + Usage.hm(it.minutes)
        }
        return "使用时长 今日 " + Usage.hm(min) + top
    } //今日使用时长

    fun hasPlacePerm(c: Context): Boolean = Place.hasCoarse(c)

    fun hasFinePerm(c: Context): Boolean = Place.hasFine(c)

    private fun battery(c: Context): String {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return "电量 读不到"
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        return "电量 $pct%" + if (bm.isCharging) "（充电中）" else "（未充电）"
    } //电量与充电状态

    private fun place(c: Context): String = Place.describe(c) //位置
} //环境注入