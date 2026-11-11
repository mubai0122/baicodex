package b.ai.core.dev

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.io.File

/**
 * 进程级性能采样：软件本体 / 终端会话 / 执行进程各算一份 CPU 与 PSS。
 * 插件（技能包、dex）跟界面跑在同一个进程里，进程级读不到分项，只能在文案里说明。
 * /proc/<pid> 读不到就说明不是本应用 uid 的进程，正好当过滤条件用。
 */
object PerfStat {
    /** 一行分项：cpu 为 null 表示第一次采样还没基线，内存单位 KB */
    data class Row(val name: String, val note: String, val cpu: Double?, val pssKb: Long, val procs: Int)
    data class Snap(val rows: List<Row>, val pssKb: Long, val procs: Int)

    /** clk_tck：Android 上固定 100，直接除就是秒 */
    private const val HZ = 100.0

    private var lastAt = 0L
    private val lastTicks = HashMap<Int, Long>()

    private class Proc(val pid: Int, val ppid: Int, val ticks: Long)

    /** /proc/<pid>/stat：comm 里可能带空格和括号，所以从最后一个 ) 往后切 */
    private fun statOf(pid: Int): Proc? {
        val raw = runCatching { File("/proc/$pid/stat").readText() }.getOrNull() ?: return null
        val cut = raw.lastIndexOf(')')
        if (cut < 0) return null
        val f = raw.substring(cut + 1).trim().split(' ').filter { it.isNotEmpty() }
        if (f.size < 13) return null
        //comm 之后：0=state 1=ppid … 11=utime 12=stime
        val ppid = f[1].toIntOrNull() ?: return null
        val u = f[11].toLongOrNull() ?: 0L
        val s = f[12].toLongOrNull() ?: 0L
        return Proc(pid, ppid, u + s)
    } //单进程 CPU 累计 ticks

    private fun scan(): Map<Int, Proc> {
        val m = HashMap<Int, Proc>()
        val names = File("/proc").list() ?: return m
        for (n in names) {
            val pid = n.toIntOrNull() ?: continue
            val p = statOf(pid) ?: continue
            m[pid] = p
        }
        return m
    } //一次全量快照

    /** 某进程的全部后代（不含自己）：终端会话的 sh / proot / apt 都在这里 */
    private fun subtree(root: Int, all: Map<Int, Proc>): List<Int> {
        val kids = HashMap<Int, MutableList<Int>>()
        all.values.forEach { kids.getOrPut(it.ppid) { ArrayList() }.add(it.pid) }
        val out = ArrayList<Int>()
        val q = ArrayDeque<Int>()
        q.addLast(root)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            kids[cur]?.forEach {
                out.add(it)
                q.addLast(it)
            }
        }
        return out
    } //后代链表

    private fun cmdOf(pid: Int): String = runCatching {
        File("/proc/$pid/cmdline").readText().substringBefore('\u0000').trim()
    }.getOrDefault("") //进程名

    private fun pssOf(ctx: Context, pids: List<Int>): Long {
        if (pids.isEmpty()) return 0L
        return runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getProcessMemoryInfo(pids.toIntArray()).sumOf { it.totalPss.toLong() }
        }.getOrDefault(0L)
    } //同 uid 才拿得到 PSS，其余返 0

    fun sample(ctx: Context): Snap {
        val me = Process.myPid()
        val all = scan()
        val now = System.currentTimeMillis()
        val dt = if (lastAt > 0L) (now - lastAt) / 1000.0 else 0.0
        val exec = all.keys.firstOrNull { cmdOf(it).startsWith(ctx.packageName + ":exec") }
        val term = subtree(me, all)
        val execAll = if (exec != null) listOf(exec) + subtree(exec, all) else emptyList<Int>()

        fun cpuOf(pids: Collection<Int>): Double? {
            if (dt <= 0.0 || pids.isEmpty()) return null
            var d = 0L
            pids.forEach { p ->
                val t = all[p]?.ticks ?: return@forEach
                val old = lastTicks[p] ?: return@forEach
                d += (t - old).coerceAtLeast(0L)
            }
            return d / HZ / dt * 100.0
        } //两次采样的 ticks 差折成单核百分比

        val rows = listOf(
            Row("软件本体", "界面、Agent 与插件（同进程，分不出插件单项）", cpuOf(listOf(me)), pssOf(ctx, listOf(me)), 1),
            Row("终端会话", if (term.isEmpty()) "没有开着的会话" else "sh / proot / apt 等子进程", cpuOf(term), pssOf(ctx, term), term.size),
            Row("执行进程", if (exec == null) "未启动（Shizuku 命令走这里）" else "Shizuku 命令（:exec）", cpuOf(execAll), pssOf(ctx, execAll), execAll.size)
        )
        val every = (listOf(me) + term + execAll).distinct()

        lastAt = now
        lastTicks.clear()
        all.forEach { (k, v) -> lastTicks[k] = v.ticks }
        return Snap(rows, pssOf(ctx, every), every.size)
    } //一次采样
} //性能采样
