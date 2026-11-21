/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * PeakHours.kt — 「现在是高峰还是错峰」的唯一判据（桌宠时段提示 / 桌宠面板共用）。
 *
 * 口径（用户 2026-11-22 定稿，取代旧的 DeepSeek 00:30–08:30 折扣口径）：
 *  - 高峰 = 工作日 && 非法定节假日 && (09:00–12:00 或 14:00–18:00)
 *  - 工作日 = 周一至周五；法定调休补班日（周末上班）也算工作日
 *  - 其余全部算错峰：午休（12:00–14:00）、18:00 之后、夜里、周末、法定节假日
 *
 * 节假日数据来源：先读本地缓存 `data/pet/holidays.json`，缺该年数据时后台静默联网拉一份
 * （timor.tech 免费接口，无 key）。拉不到就一直按「周一至周五」算，功能不受影响。
 */
package b.ai.core.pet.peak

import b.ai.core.cfg.io.Paths
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

object PeakHours {
    /** 上午高峰：[09:00, 12:00) —— 以分钟为单位，左闭右开 */
    private const val AM_FROM = 9 * 60
    private const val AM_TO = 12 * 60

    /** 下午高峰：[14:00, 18:00) */
    private const val PM_FROM = 14 * 60
    private const val PM_TO = 18 * 60

    private const val API = "https://timor.tech/api/holiday/year/%d"

    /** 已加载过哪一年的表；-1 表示需要重新加载 */
    @Volatile
    private var loadedYear = -1

    @Volatile
    private var holidays: Set<String> = emptySet() //法定节假日（YYYY-MM-DD）
    @Volatile
    private var workdays: Set<String> = emptySet() //调休补班日（周末但要上班）
    @Volatile
    private var refreshing = false

    private fun file(): File = File(Paths.dir("pet"), "holidays.json")

    /** 当前是否高峰；now 可注入，方便自检与调试 */
    fun peak(now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        if (!workday(c)) return false
        val mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return mins in AM_FROM until AM_TO || mins in PM_FROM until PM_TO
    } //高峰判定

    /** 当前是否错峰 = 非高峰（保留旧名，调用方不用改语义） */
    fun offPeak(now: Long = System.currentTimeMillis()): Boolean = !peak(now)

    /**
     * 某天算不算工作日：法定节假日一律排除，调休补班日优先认作工作日，
     * 两者都没命中再按周一至周五判断。
     */
    fun workday(now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        return workday(c)
    } //对外的工作日查询

    private fun workday(c: Calendar): Boolean {
        val key = key(c)
        ensure(c.get(Calendar.YEAR))
        if (holidays.contains(key)) return false //放假日：再像工作日也不算
        if (workdays.contains(key)) return true //补班日：周末也算工作日
        val dow = c.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
    } //工作日判定

    /** 当天类型：节假日 / 调休补班 / 工作日 / 周末（自检面板展示用） */
    fun dayKind(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        val key = key(c)
        ensure(c.get(Calendar.YEAR))
        if (holidays.contains(key)) return "法定节假日"
        if (workdays.contains(key)) return "调休补班"
        val dow = c.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY) return "周六"
        if (dow == Calendar.SUNDAY) return "周日"
        return "工作日"
    } //当天类型

    /** 自检：把判据、数据来源、表内条数一次打清楚 */
    fun describe(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        ensure(c.get(Calendar.YEAR))
        val sb = StringBuilder()
        sb.append(
            String.format(
                "现在：%04d-%02d-%02d %02d:%02d %s\n",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), dayKind(now)
            )
        )
        sb.append("判定：${if (peak(now)) "高峰" else "错峰"}\n")
        sb.append("高峰口径：工作日 09:00–12:00、14:00–18:00（含调休补班日，排除法定节假日）\n")
        sb.append("节假日表：${holidays.size} 条，补班表：${workdays.size} 条，年份=$loadedYear\n")
        sb.append("数据文件：${file().absolutePath}（${if (file().isFile) "已存在" else "未生成，当前按周一至周五兜底"}）\n")
        return sb.toString()
    } //自检输出

    /** 手动刷新指定年份的节假日表（DevPanel 用），返回结果文案 */
    fun refresh(year: Int): String = runCatching { fetchInto(year) }
        .fold(
            onSuccess = { "已更新 $year 年节假日表：${holidays.size} 个假日 / ${workdays.size} 个补班日" },
            onFailure = { "更新失败：${it.message ?: it.javaClass.simpleName}（继续按周一至周五兜底）" }
        )

    /** 确保当年数据已加载；缺数据就后台拉一次，本次调用不受影响 */
    private fun ensure(year: Int) {
        if (loadedYear == year) return
        load(year)
        if (loadedYear == year) return
        autoRefresh(year)
    } //懒加载 + 后台补数据

    private fun autoRefresh(year: Int) {
        if (refreshing) return
        refreshing = true
        Thread {
            runCatching { fetchInto(year) }
            refreshing = false
        }.apply { isDaemon = true }.start()
    } //后台拉表

    private fun load(year: Int) {
        val f = file()
        if (!f.isFile) return
        runCatching {
            val o = JSONObject(f.readText())
            if (o.optInt("year", -1) != year) return //年份对不上：等后台刷新覆盖
            holidays = arr(o.optJSONArray("holidays"))
            workdays = arr(o.optJSONArray("workdays"))
            loadedYear = year
        }
    } //读本地缓存

    private fun arr(a: org.json.JSONArray?): Set<String> {
        if (a == null) return emptySet()
        val set = HashSet<String>(a.length())
        for (i in 0 until a.length()) set.add(a.optString(i))
        return set
    } //JSON 数组转集合

    /**
     * 拉一年数据并写缓存。接口返回的 `holiday` 里，`holiday=true` 是放假，
     * `holiday=false` 是调休补班；key 是 MM-DD，date 是完整日期，这里统一按 date 存。
     */
    private fun fetchInto(year: Int) {
        val conn = URL(API.format(year)).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("code", -1) != 0) throw IllegalStateException("接口返回 code=${root.optInt("code")}")
            val hs = HashSet<String>()
            val ws = HashSet<String>()
            val obj = root.optJSONObject("holiday") ?: JSONObject()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val item = obj.optJSONObject(keys.next()) ?: continue
                val date = item.optString("date")
                if (date.length != 10 || !date.startsWith("$year-")) continue
                if (item.optBoolean("holiday")) hs.add(date) else ws.add(date)
            }
            //落盘：年份、拉取时间、两份清单
            val out = JSONObject()
                .put("year", year)
                .put("at", System.currentTimeMillis())
                .put("holidays", org.json.JSONArray(hs.toList().sorted()))
                .put("workdays", org.json.JSONArray(ws.toList().sorted()))
            file().writeText(out.toString())
            holidays = hs
            workdays = ws
            loadedYear = year
        } finally {
            conn.disconnect()
        }
    } //联网拉表并落盘

    private fun key(c: Calendar): String = String.format(
        "%04d-%02d-%02d",
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )
} //峰谷时段
