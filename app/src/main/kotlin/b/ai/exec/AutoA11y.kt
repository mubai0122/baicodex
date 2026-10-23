package b.ai.exec

import android.content.Context
import b.ai.core.Cfg
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一键补无障碍：Shizuku / Root 都能写 secure 设置，
 * 于是「去系统设置手点开关」这条路可以让位给一次按钮点击。
 */
object AutoA11y {
    private const val WAIT_MS = 4_000L
    private const val SU_PROBE_MS = 20_000L
    private const val COOL_MS = 30_000L

    @Volatile
    private var lastTry = 0L

    private val busy = AtomicBoolean(false)

    /** 便宜探测：只判断「有没有可能」，shizuku 看 binder，root 只看 su 二进制，不弹授权框 */
    fun quickChannel(ctx: Context): String? {
        if (Shiz.probe(ctx).ok) return "shizuku"
        return if (Root.suPath() != null) "root" else null
    } //快速探测高权限通道

    /** 真探测：root 会弹一次 su 授权框，所以只在真的要动手时调 */
    fun channel(ctx: Context): String? {
        if (Shiz.probe(ctx).ok) return "shizuku"
        return if (Root.probe(SU_PROBE_MS).ok) "root" else null
    } //严格探测高权限通道

    private fun exec(ch: String, cmd: String): ShellResult =
        if (ch == "root") Root.run(cmd, 30_000L) else Shiz.run(cmd, timeoutMs = 30_000L)

    /**
     * 写系统设置把本服务挂上去，然后等系统真的 bind。
     * onStep：0 = 正在尝试激活，1 = 正在激活（等系统挂载），2 = 成功，3 = 失败（带原因）
     */
    fun run(ctx: Context, onStep: ((Int, String) -> Unit)? = null): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        lastTry = System.currentTimeMillis()
        try {
            if (A11y.ready()) {
                onStep?.invoke(2, "")
                return true
            }
            onStep?.invoke(0, "")
            val ch = channel(ctx)
            if (ch == null) {
                onStep?.invoke(3, "没有 Shizuku / Root 权限")
                return false
            }
            val comp = ctx.packageName + "/" + A11yService::class.java.name
            val cur = exec(ch, "settings get secure enabled_accessibility_services")
            if (cur.code != 0) {
                onStep?.invoke(3, cur.out.trim().ifBlank { "读取当前设置失败（退出码 ${cur.code}）" })
                return false
            }
            val old = cur.out.trim().trim('\'').trim()
            val next = when {
                old.isEmpty() || old == "null" -> comp
                old.split(':').contains(comp) -> old
                else -> "$old:$comp"
            }
            if (next != old) {
                val w = exec(ch, "settings put secure enabled_accessibility_services '$next'")
                if (w.code != 0) {
                    onStep?.invoke(3, w.out.trim().ifBlank { "写入失败（退出码 ${w.code}）" })
                    return false
                }
            }
            //部分 ROM 会顺手把总开关清零，单独再写一次
            exec(ch, "settings put secure accessibility_enabled 1")
            onStep?.invoke(1, "")
            val end = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < end) {
                if (A11y.ready()) {
                    onStep?.invoke(2, "")
                    return true
                }
                Thread.sleep(200L)
            }
            onStep?.invoke(3, "设置已经写进去了，但系统没把服务挂上（这台 ROM 可能拦了 shell 写入）")
            return false
        } catch (e: Exception) {
            onStep?.invoke(3, e.message ?: e.javaClass.simpleName)
            return false
        } finally {
            busy.set(false)
        }
    } //一键激活无障碍

    /** 要不要自动补一次：开关开着、服务没连上、有通道、30 秒内没试过 */
    fun need(ctx: Context): Boolean {
        if (!Cfg.autoA11y) return false
        if (A11y.ready()) return false
        if (System.currentTimeMillis() - lastTry < COOL_MS) return false
        return quickChannel(ctx) != null
    } //判断是否值得自动补
} //一键补无障碍
