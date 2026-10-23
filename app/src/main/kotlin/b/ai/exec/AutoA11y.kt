package b.ai.exec

import android.content.Context
import b.ai.core.Cfg
import java.util.concurrent.atomic.AtomicBoolean

/** Shizuku 自动补无障碍：shell 持有 WRITE_SECURE_SETTINGS，直接写 secure 设置把服务挂回去 */
object AutoA11y {
    private const val COOL_MS = 30_000L
    private const val WAIT_MS = 3_000L

    @Volatile
    private var lastTry = 0L

    private val busy = AtomicBoolean(false)

    /** 要不要跑一次：开关开着、服务没连上、Shizuku 可用、30 秒内没试过 */
    fun need(ctx: Context): Boolean {
        if (!Cfg.autoA11y) return false
        if (A11y.ready()) return false
        if (System.currentTimeMillis() - lastTry < COOL_MS) return false
        return Shiz.probe(ctx).ok
    } //判断是否值得尝试

    /** 写 settings 把本服务加进已启用列表并打开总开关，然后等系统真把它 bind 上 */
    fun run(ctx: Context): String {
        if (!busy.compareAndSet(false, true)) return "上一次还在跑"
        lastTry = System.currentTimeMillis()
        try {
            if (A11y.ready()) return "无障碍已经连着"
            val p = Shiz.probe(ctx)
            if (!p.ok) return "Shizuku 不可用：${p.desc}"
            val comp = ctx.packageName + "/" + A11yService::class.java.name
            val cur = Shiz.run("settings get secure enabled_accessibility_services", timeoutMs = 15_000)
            if (cur.code != 0) return "读当前设置失败：${cur.out.trim()}"
            val old = cur.out.trim().trim('\'').trim()
            val next = when {
                old.isEmpty() || old == "null" -> comp
                old.split(':').contains(comp) -> old
                else -> "$old:$comp"
            }
            if (next != old) {
                val w = Shiz.run("settings put secure enabled_accessibility_services '$next'", timeoutMs = 15_000)
                if (w.code != 0) return "写入失败：${w.out.trim()}"
            }
            //总开关单独写一次：部分 ROM 会把 accessibility_enabled 顺手清零
            Shiz.run("settings put secure accessibility_enabled 1", timeoutMs = 15_000)
            val end = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < end) {
                if (A11y.ready()) return "已自动授予无障碍"
                Thread.sleep(250L)
            }
            return "设置写成功了，但系统没把它挂上（去设置里手动开一次，或这台 ROM 拦了 shell 写入）"
        } catch (e: Exception) {
            return "自动授予失败：${e.message ?: e.javaClass.simpleName}"
        } finally {
            busy.set(false)
        }
    } //自动授予无障碍
} //Shizuku 自动补无障碍