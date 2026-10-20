package b.ai.core

import android.content.Context
import b.ai.App

/** 一个车站：站名、telecode、所属城市名与城市码、拼音与首字母 */
data class Station(
    val name: String,
    val code: String,
    val pinyin: String,
    val initial: String,
    val city: String,
    val cityCode: String
)

/**
 * 车站表：内置 12306 官方 station_name.js，解析成「中文站名/城市名 → telecode」。
 * 让模型直接写「杭州」「上海虹桥」就行，不用先查站码。
 */
object Stations {
    private var all: List<Station> = emptyList()
    private var loaded = false

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val raw = runCatching {
            c.applicationContext.assets.open("station_name.js").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        all = parse(raw)
    } //读 assets 站表

    private fun parse(raw: String): List<Station> {
        val body = if (raw.contains("@")) {
            raw.substringAfter("'").substringBeforeLast("'")
        } else {
            raw
        }
        return body.split("@").mapNotNull { seg ->
            if (seg.isBlank()) return@mapNotNull null
            val f = seg.split("|")
            if (f.size < 8 || f[1].isBlank() || f[2].isBlank()) return@mapNotNull null
            Station(
                name = f[1].trim(),
                code = f[2].trim(),
                pinyin = f[3].trim(),
                initial = f[4].trim(),
                cityCode = f[6].trim(),
                city = f[7].trim()
            )
        }
    } //拆 @简拼|站名|telecode|拼音|首字母|序号|城市码|城市名

    /**
     * 解析用户输入：站名精确 → 城市名 → 「xx站」→ 拼音/首字母 → 站名包含。
     * 城市会返回该城市全部车站，调用方取第一个当下发码（12306 传一个站码就会回同城结果）。
     */
    fun resolve(input: String): List<Station> {
        //站表 17 万字符，首次用到才解析，不拖慢冷启动
        if (!loaded) runCatching { init(App.self) }
        val q = input.trim().removeSuffix("站").ifBlank { return emptyList() }
        if (all.isEmpty()) return emptyList()
        all.filter { it.name == q }.takeIf { it.isNotEmpty() }?.let { return it }
        all.filter { it.city == q }.takeIf { it.isNotEmpty() }?.let { return it }
        all.filter { it.name == q + "站" }.takeIf { it.isNotEmpty() }?.let { return it }
        all.filter { it.pinyin.equals(q, true) || it.initial.equals(q, true) }
            .takeIf { it.isNotEmpty() }?.let { return it }
        return all.filter { it.name.contains(q) || (q.contains(it.name) && it.name.length >= 2) }
    } //解析站名

    fun byCode(code: String): Station? = all.firstOrNull { it.code == code }

    fun size(): Int = all.size
} //车站表