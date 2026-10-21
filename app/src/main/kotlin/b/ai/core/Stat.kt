package b.ai.core

import java.util.Calendar

/** 一天的用量；cached 是输入里命中缓存的那部分（已含在 prompt 内） */
data class DayUse(
    val day: String,
    val prompt: Long,
    val completion: Long,
    val cached: Long = 0,
    val cost: Double = 0.0
) {
    val total: Long get() = prompt + completion
}

/** 一个月的活跃与用量 */
data class MonthUse(val month: String, val activeDays: Int, val tokens: Long)

/** SQL 里折好的一天用量（Repo 直接吐这个，不经过对象分配） */
data class RawDay(val day: String, val prompt: Long, val cached: Long, val completion: Long)

/** 模型价格：元 / 百万 token；三项全 0 表示不计价，只统计 token */
data class Price(val inP: Float = 0f, val outP: Float = 0f, val cacheP: Float = 0f) {
    val on: Boolean get() = inP > 0f || outP > 0f || cacheP > 0f

    /** 一段用量折算成钱（元）；缓存命中那部分按缓存价算，不重复计输入价 */
    fun cost(prompt: Long, cached: Long, completion: Long): Double {
        if (!on) return 0.0
        val c = cached.coerceIn(0L, prompt)
        return ((prompt - c) * inP + c * cacheP + completion * outP) / 1_000_000.0
    } //折钱

    companion object {
        fun now() = Price(Cfg.priceIn, Cfg.priceOut, Cfg.priceCache)
    }
}

/** 统计快照：一次查库聚合完，页面只负责画 */
data class StatSnap(
    val totalPrompt: Long = 0,
    val totalCompletion: Long = 0,
    val totalCached: Long = 0,
    val totalCost: Double = 0.0,
    val price: Price = Price(),
    val models: List<Pair<String, Long>> = emptyList(),
    val days: List<DayUse> = emptyList(),
    val months: List<MonthUse> = emptyList(),
    val monthDay: List<DayUse> = emptyList(),
    val monthLen: Int = 31,
    val msgCount: Int = 0
) {
    val total: Long get() = totalPrompt + totalCompletion

    /** 近 180 天里有实际用量的天数 */
    val activeDays: Int get() = days.count { it.total > 0 }

    /** 一段日期区间的费用 */
    fun costOf(items: List<DayUse>): Double = items.sumOf { it.cost }
}

object Stat {
    /** 按本地时区把毫秒折成 yyyy-MM-dd */
    private fun dayKey(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        return "${c.get(Calendar.YEAR)}-${if (m < 10) "0$m" else m}-${if (d < 10) "0$d" else d}"
    } //yyyy-MM-dd

    /** 按本地时区折成 yyyy-MM */
    private fun monKey(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        val m = c.get(Calendar.MONTH) + 1
        return "${c.get(Calendar.YEAR)}-${if (m < 10) "0$m" else m}"
    } //yyyy-MM

    /** 本月天数：问系统日历，28 / 29 / 30 / 31 都对，别自己写判断 */
    fun monthLen(): Int = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)

    /** 聚合统计：日期折叠与求和都在 SQL 里做完，账本再大也只回几百行；调用方放 IO 线程 */
    fun load(): StatSnap {
        val price = Price.now()
        val t = Repo.usageTotals()
        val daily = Repo.usageDaily().associateBy { it.day }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        //近 180 天连续铺开，没用的天补 0，柱状图不会缺格子
        val days = ArrayList<DayUse>(180)
        for (i in 179 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_MONTH, -i)
            val k = dayKey(c.timeInMillis)
            days += toDayUse(k, daily[k], price)
        }

        //近 6 个月：活跃天数 + 用量
        val months = ArrayList<MonthUse>(6)
        for (i in 5 downTo 0) {
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, -i)
            val k = monKey(c.timeInMillis)
            var active = 0
            var tk = 0L
            daily.forEach { (dk, v) ->
                if (dk.startsWith(k)) {
                    if (v.prompt + v.completion > 0) active++
                    tk += v.prompt + v.completion
                }
            }
            months += MonthUse(k, active, tk)
        }

        //本月逐日：格子图按本月真实天数排，最后一行不满就留空
        val len = monthLen()
        val ck = monKey(System.currentTimeMillis())
        val monthDay = ArrayList<DayUse>(len)
        for (d in 1..len) {
            val k = "$ck-${if (d < 10) "0$d" else d}"
            monthDay += toDayUse(k, daily[k], price)
        }

        //cached 按行夹到 prompt 以内由 SQL 的 MIN(cached,prompt) 保证
        return StatSnap(
            totalPrompt = t[1],
            totalCompletion = t[3],
            totalCached = t[2],
            totalCost = price.cost(t[1], t[2], t[3]),
            price = price,
            models = Repo.usageModels().map { (m, v) -> m.ifBlank { "未知模型" } to v },
            days = days,
            months = months,
            monthDay = monthDay,
            monthLen = len,
            msgCount = t[0].toInt()
        )
    } //统计聚合

    private fun toDayUse(day: String, raw: RawDay?, price: Price): DayUse =
        if (raw == null) DayUse(day, 0, 0)
        else DayUse(day, raw.prompt, raw.completion, raw.cached, price.cost(raw.prompt, raw.cached, raw.completion)) //单日快照
}
