package b.ai.core

import java.util.Calendar

/** 一天的用量；cached 是输入里命中缓存的那部分（已含在 prompt 内） */
data class DayUse(val day: String, val prompt: Long, val completion: Long, val cached: Long = 0) {
    val total: Long get() = prompt + completion
}

/** 一个月的活跃与用量 */
data class MonthUse(val month: String, val activeDays: Int, val tokens: Long)

/** 统计快照：一次查库聚合完，页面只负责画 */
data class StatSnap(
    val totalPrompt: Long = 0,
    val totalCompletion: Long = 0,
    val totalCached: Long = 0,
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

    /** 全量扫 usage 账本聚合；库大也只是几千行，调用方放 IO 线程跑 */
    fun load(): StatSnap {
        val byDay = HashMap<String, LongArray>()
        val byModel = HashMap<String, Long>()
        var prompt = 0L
        var completion = 0L
        var cachedSum = 0L
        var msg = 0
        Repo.eachUsageAt { at, _, model, p, ca, cp ->
            if (p + cp > 0) {
                val c = ca.coerceIn(0L, p)
                prompt += p
                completion += cp
                cachedSum += c
                msg++
                val k = dayKey(if (at > 0) at else System.currentTimeMillis())
                val slot = byDay.getOrPut(k) { LongArray(3) }
                slot[0] += p
                slot[1] += cp
                slot[2] += c
                val name = model.ifBlank { "未知模型" }
                byModel[name] = (byModel[name] ?: 0L) + p + cp
            }
        }

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
            val v = byDay[dayKey(c.timeInMillis)]
            days += DayUse(dayKey(c.timeInMillis), v?.get(0) ?: 0L, v?.get(1) ?: 0L, v?.get(2) ?: 0L)
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
            byDay.forEach { (dk, v) ->
                if (dk.startsWith(k)) {
                    if (v[0] + v[1] > 0) active++
                    tk += v[0] + v[1]
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
            val v = byDay[k]
            monthDay += DayUse(k, v?.get(0) ?: 0L, v?.get(1) ?: 0L, v?.get(2) ?: 0L)
        }

        return StatSnap(
            totalPrompt = prompt,
            totalCompletion = completion,
            totalCached = cachedSum,
            models = byModel.entries.sortedByDescending { it.value }.map { it.key to it.value },
            days = days,
            months = months,
            monthDay = monthDay,
            monthLen = len,
            msgCount = msg
        )
    } //统计聚合
}