package b.ai.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 通知收录：按天落盘，只留应用名与标题，最多留 3 天；正文一律不存 */
object Noti {
    data class Row(val at: Long, val app: String, val title: String)

    /** 注入时的字符上限：条数不限时靠它兜底，防止一次请求被刷爆 */
    private const val CAP_CHARS = 8000

    private fun dir(c: Context): File = File(c.filesDir, "noti").apply { mkdirs() }

    private fun day(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    private fun file(c: Context, d: String = day()): File = File(dir(c), "n-$d.log")

    /** 通知使用权是否已给：系统把已启用的监听器包名记在 Secure 表里 */
    fun allow(c: Context): Boolean = runCatching {
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
            ?.contains(c.packageName) == true
    }.getOrDefault(false)

    fun openPerm(c: Context) {
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    } //去通知使用权页

    /** 记一条：自己发的、常驻的、没标题的都不收 */
    fun add(c: Context, pkg: String, app: String, title: String, ongoing: Boolean) {
        if (ongoing) return
        if (pkg == c.packageName) return
        val t = title.trim().take(60)
        if (t.isEmpty()) return
        val row = JSONObject().put("t", System.currentTimeMillis()).put("a", app).put("s", t)
        runCatching { file(c).appendText(row.toString() + "\n") }
        clean(c)
    } //落一条

    /** 只留今天和前两天，别把私有目录撑大 */
    private fun clean(c: Context) {
        val now = System.currentTimeMillis()
        val keep = listOf(day(now), day(now - 86_400_000L), day(now - 172_800_000L))
            .map { "n-$it.log" }
            .toSet()
        runCatching { dir(c).listFiles()?.forEach { f -> if (f.name !in keep) f.delete() } }
    } //清理老文件

    /** 今日通知，按时间正序；limit <= 0 表示不限 */
    fun today(c: Context, limit: Int): List<Row> {
        val f = file(c)
        if (!f.isFile) return emptyList()
        val all = runCatching {
            f.readLines().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val o = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
                Row(o.optLong("t"), o.optString("a"), o.optString("s"))
            }
        }.getOrDefault(emptyList())
        return if (limit > 0) all.takeLast(limit) else all
    } //今日通知

    fun count(c: Context): Int = runCatching {
        file(c).takeIf { it.isFile }?.readLines()?.count { it.isNotBlank() } ?: 0
    }.getOrDefault(0)

    /** 拼成给模型看的一行：条数 + 若干条「应用·标题」，超字符上限就截断并标注 */
    fun brief(c: Context, limit: Int): String {
        if (!allow(c)) return "通知 未授权（缺通知使用权）"
        val n = count(c)
        if (n <= 0) return "通知 今天还没有收到（或监听刚开启）"
        val rows = today(c, limit)
        if (rows.isEmpty()) return "通知 今日 $n 条"
        val sb = StringBuilder("通知 今日 ").append(n).append(" 条")
        sb.append(if (limit > 0) "，最近 ${rows.size} 条：" else "，全部：")
        var cut = false
        var used = 0
        val picked = ArrayList<String>()
        for (r in rows.asReversed()) {
            val one = r.app + "·" + r.title
            if (used + one.length > CAP_CHARS) {
                cut = true
                break
            }
            used += one.length + 1
            picked.add(one)
        }
        sb.append(picked.joinToString("｜"))
        if (cut) sb.append("（更早的已省略）")
        return sb.toString()
    } //拼注入口径
} //通知收录