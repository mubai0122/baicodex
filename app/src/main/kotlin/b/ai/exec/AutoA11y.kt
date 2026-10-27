package b.ai.exec

import android.content.Context
import b.ai.core.Cfg
import b.ai.core.WorkMode
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

    /** 通道顺序：用户选的执行通道优先，另一条留作兜底（Root 用户先用 root，不被 Shizuku 抢） */
    private fun order(): List<String> =
        if (Cfg.workMode == WorkMode.ROOT) listOf("root", "shizuku") else listOf("shizuku", "root")

    /** 便宜探测：只判断「有没有可能」，shizuku 看 binder，root 只看 su 二进制，不弹授权框 */
    fun quickChannel(ctx: Context): String? {
        val shiz = Shiz.probe(ctx).ok
        val root = Root.suPath() != null
        return order().firstOrNull { (it == "shizuku" && shiz) || (it == "root" && root) }
    } //快速探测高权限通道

    /** 真探测：root 会弹一次 su 授权框，所以只在真的要动手时调；按优先级给出全部可用通道 */
    fun channels(ctx: Context): List<String> {
        val shiz = Shiz.probe(ctx).ok
        val root = Root.probe(SU_PROBE_MS).ok
        return order().filter { (it == "shizuku" && shiz) || (it == "root" && root) }
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
            val list = channels(ctx)
            if (list.isEmpty()) {
                onStep?.invoke(3, "没有 Shizuku / Root 权限")
                return false
            }
            val comp = ctx.packageName + "/" + A11yService::class.java.name
            val errs = ArrayList<String>()
            for (ch in list) {
                onStep?.invoke(1, if (ch == "root") "正在用 Root 写入无障碍设置" else "正在用 Shizuku 写入无障碍设置")
                val bad = activate(ch, comp)
                if (bad == null) {
                    onStep?.invoke(2, "")
                    return true
                }
                errs.add((if (ch == "root") "Root" else "Shizuku") + "：" + bad)
            }
            onStep?.invoke(3, errs.joinToString("；"))
            return false
        } catch (e: Exception) {
            onStep?.invoke(3, e.message ?: e.javaClass.simpleName)
            return false
        } finally {
            busy.set(false)
        }
    } //一键激活无障碍

    /**
     * 单通道尝试：读现值 → 强制制造一次变更（先摘掉再挂回）→ 打开总开关 → 等系统 bind。
     * 返回 null 表示成功，否则是这条通道的失败原因，会拼进最终提示。
     */
    private fun activate(ch: String, comp: String): String? {
        val cur = exec(ch, "settings get secure enabled_accessibility_services")
        if (cur.code != 0) return cur.out.trim().ifBlank { "读取当前设置失败（退出码 ${cur.code}）" }
        val items = cur.out.trim().trim('\'').split(':')
            .map { it.trim() }.filter { it.isNotEmpty() && it != "null" }
        val rest = items.filter { it != comp }
        //值没变化系统收不到设置变更通知，AccessibilityManagerService 就不会重新 bind，所以先摘掉
        if (rest.size != items.size) {
            val off = exec(ch, putSvc(rest.joinToString(":")))
            if (off.code != 0) return off.out.trim().ifBlank { "摘掉旧值时写入失败（退出码 ${off.code}）" }
            Thread.sleep(300L)
        }
        val on = exec(ch, putSvc((rest + comp).joinToString(":")))
        if (on.code != 0) return on.out.trim().ifBlank { "写入服务列表失败（退出码 ${on.code}）" }
        //总开关同样先关再开：部分 ROM 写服务列表会把总开关清零，且值不变同样不触发通知
        exec(ch, "settings put secure accessibility_enabled 0")
        Thread.sleep(150L)
        val sw = exec(ch, "settings put secure accessibility_enabled 1")
        if (sw.code != 0) return sw.out.trim().ifBlank { "打开无障碍总开关失败（退出码 ${sw.code}）" }
        val end = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < end) {
            if (A11y.ready()) return null
            Thread.sleep(200L)
        }
        //写进去了却没挂上：把真实值读回来，能区分是被 ROM 拦了还是值没落地
        val now = exec(ch, "settings get secure enabled_accessibility_services").out.trim().trim('\'').trim()
        val flag = exec(ch, "settings get secure accessibility_enabled").out.trim()
        val v = if (now.isEmpty() || now == "null") "空" else now
        return "设置已写入但系统没挂上（服务列表=$v，总开关=$flag）"
    } //单通道激活

    private fun putSvc(v: String): String =
        if (v.isEmpty()) "settings put secure enabled_accessibility_services ''"
        else "settings put secure enabled_accessibility_services '$v'" //写服务列表

    /** 要不要自动补一次：开关开着、服务没连上、有通道、30 秒内没试过 */
    fun need(ctx: Context): Boolean {
        if (!Cfg.autoA11y) return false
        if (A11y.ready()) return false
        if (System.currentTimeMillis() - lastTry < COOL_MS) return false
        return quickChannel(ctx) != null
    } //判断是否值得自动补
} //一键补无障碍