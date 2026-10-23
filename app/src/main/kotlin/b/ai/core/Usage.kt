package b.ai.core

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/** 设备使用时长：只读系统 UsageStats，今天 0 点到现在 */
object Usage {
    data class App(val name: String, val minutes: Int)

    /** 使用情况访问权：用 appops 里的 get_usage_stats 判定，比查统计有没有数据可靠 */
    fun allow(c: Context): Boolean = runCatching {
        val om = c.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            om.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            om.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun openPerm(c: Context) {
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    } //去使用情况访问页

    /** 今日 0 点到现在：总前台的分钟数 + 用得最多的三个应用 */
    fun today(c: Context): Pair<Int, List<App>> {
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0 to emptyList()
        val list = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, System.currentTimeMillis())
        }.getOrNull() ?: return 0 to emptyList()
        val pm = c.packageManager
        val agg = HashMap<String, Long>()
        list.forEach { s ->
            if (s.totalTimeInForeground <= 0L) return@forEach
            agg[s.packageName] = (agg[s.packageName] ?: 0L) + s.totalTimeInForeground
        }
        val total = (agg.values.sum() / 60_000L).toInt()
        val apps = agg.entries.sortedByDescending { it.value }.take(3).map { e ->
            val name = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(e.key, 0)).toString()
            }.getOrDefault(e.key)
            App(name, (e.value / 60_000L).toInt())
        }
        return total to apps
    } //今日使用时长

    /** 分钟转人话：不到 1 小时说分钟，超过说「x 小时 y 分」 */
    fun hm(m: Int): String = if (m < 60) "${m}分钟" else "${m / 60}小时${m % 60}分"
} //使用时长