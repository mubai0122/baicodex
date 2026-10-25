package b.ai.exec

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** 桌宠权限：无障碍活着就完全不要权限；否则 root → Shizuku → 手动三路自动降级 */
object PetGrant {
    enum class Route { A11Y, MANUAL, NONE }

    data class Res(val ok: Boolean, val route: String, val detail: String)

    private val main = Handler(Looper.getMainLooper())

    /** 现在靠什么显示：无障碍层 / 悬浮窗权限 / 什么都没有 */
    fun route(c: Context): Route = when {
        Pet.a11yActive() -> Route.A11Y
        runCatching { Settings.canDrawOverlays(c) }.getOrDefault(false) -> Route.MANUAL
        else -> Route.NONE
    } //现状

    /** 自动补权限：先 root，再 Shizuku，都不行就把结果交回去让 UI 引导手动授权 */
    fun grant(c: Context, cb: (Res) -> Unit) {
        val ctx = c.applicationContext
        Thread {
            val r = runCatching { Root.run("id", 8_000) }.getOrNull()
            if (r != null && r.out.contains("uid=0")) {
                val log = exec(ctx) { cmd -> Root.run(cmd, 15_000) }
                main.post { cb(done(ctx, "root", log)) }
                return@Thread
            }
            val s = runCatching { Shiz.run("id", timeoutMs = 8_000) }.getOrNull()
            if (s != null && s.code == 0 && s.out.contains("uid=")) {
                val log = exec(ctx) { cmd -> Shiz.run(cmd, timeoutMs = 15_000) }
                main.post { cb(done(ctx, "shizuku", log)) }
                return@Thread
            }
            val why = if (r == null) "root 不可用" else "root 与 Shizuku 都不可用"
            main.post { cb(Res(false, "none", why)) }
        }.apply { isDaemon = true }.start()
    } //自动授权

    private fun exec(ctx: Context, run: (String) -> ShellResult): String {
        val pkg = ctx.packageName
        val cmds = listOf(
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "pm grant $pkg android.permission.POST_NOTIFICATIONS"
        )
        val sb = StringBuilder()
        cmds.forEach { cmd ->
            val x = runCatching { run(cmd) }.getOrNull()
            sb.append(cmd).append(" → ").append(x?.code ?: -1)
            if (x != null && x.out.isNotBlank()) sb.append(' ').append(x.out.trim())
            sb.append('\n')
        }
        return sb.toString().trim()
    } //跑两条授权命令

    private fun done(ctx: Context, via: String, log: String): Res {
        //命令退出码不可全信：直接问系统现在到底有没有给
        val ok = Pet.a11yActive() || runCatching { Settings.canDrawOverlays(ctx) }.getOrDefault(false)
        return Res(ok, via, log)
    } //收尾复核

    fun openOverlaySetting(c: Context) {
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //跳悬浮窗设置页

    fun openNotiSetting(c: Context) {
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //跳通知设置页
} //桌宠权限