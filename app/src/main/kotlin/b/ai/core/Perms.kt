package b.ai.core

import b.ai.R

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings

/** 运行期权限：进应用一次性申请，缺权限时工具调用直接拒绝 */
object Perms {
    /** 存储类权限：全盘访问开了以后这两个都不用再要 */
    private val STORAGE = setOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    //WRITE_EXTERNAL_STORAGE 在清单里限到 29：targetSdk≥30 时系统根本不会授予它，申请也永远是 DENIED
    private fun storage(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        Build.VERSION.SDK_INT >= 30 -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /** 需要动态申请的权限清单（按系统版本裁剪，未定义的高版本权限不请求） */
    fun runtime(): List<String> {
        val l = ArrayList<String>(storage())
        l.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 34) l.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return l
    }

    fun granted(c: Context, p: String): Boolean =
        c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** 还没授予的权限；空表示都齐了。Android 11+ 开了全盘访问就不再纠结存储类权限 */
    fun missing(c: Context): List<String> {
        //只在 30+ 才认这个豁免：Android 10 及以下 allFiles() 恒为 true，那会儿 WRITE 是真的要
        val manager = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
        return runtime().filterNot { p ->
            granted(c, p) || (manager && p in STORAGE)
        }
    }

    fun ok(c: Context): Boolean = missing(c).isEmpty()

    /** 全盘访问是特殊权限，只能跳系统页面手动开（Android 11+） */
    fun allFiles(): Boolean =
        Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun openAppSettings(c: Context) {
        runCatching {
            c.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", c.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //跳到本应用的系统权限页

    fun openAllFiles(c: Context) {
        runCatching {
            val i = if (Build.VERSION.SDK_INT >= 30) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${c.packageName}"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
            }
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } //跳到「所有文件访问」开关

    fun label(p: String): String = when (p) {
        Manifest.permission.RECORD_AUDIO -> "麦克风"
        Manifest.permission.POST_NOTIFICATIONS -> "通知"
        Manifest.permission.READ_MEDIA_IMAGES -> "图片"
        Manifest.permission.READ_MEDIA_VIDEO -> "视频"
        Manifest.permission.READ_MEDIA_AUDIO -> "音频"
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> "部分图片"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "读取存储"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入存储"
        else -> p.substringAfterLast('.')
    } //中文名（拼进系统提示用）

    /**权限名的资源 id：未知权限返回 0 */
    fun labelRes(p: String): Int = when (p) {
        Manifest.permission.RECORD_AUDIO -> R.string.pr_mic
        Manifest.permission.POST_NOTIFICATIONS -> R.string.pr_notify
        Manifest.permission.READ_MEDIA_IMAGES -> R.string.pr_images
        Manifest.permission.READ_MEDIA_VIDEO -> R.string.pr_videos
        Manifest.permission.READ_MEDIA_AUDIO -> R.string.pr_audio
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> R.string.pr_audio_sel
        Manifest.permission.READ_EXTERNAL_STORAGE -> R.string.pr_read_store
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> R.string.pr_write_store
        else -> 0
    } //界面用资源

    /**界面用：按当前语言取权限名，未知权限退回类名 */
    fun label(c: Context, p: String): String {
        val r = labelRes(p)
        return if (r == 0) p.substringAfterLast('.') else c.getString(r)
    } //本地化权限名
    /** 无障碍服务是否已开启：系统设置里勾上了才算，光装服务没用 */
    fun a11yOn(c: Context): Boolean {
        if (b.ai.exec.A11y.ready()) return true
        val want = c.packageName + "/" + b.ai.exec.A11yService::class.java.name
        val cur = runCatching {
            Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        return cur.split(':').any { it.equals(want, true) }
    } //无障碍开关状态

    /** 跳系统无障碍页：Android 13+ 侧载应用可能要先在应用信息里点「允许受限设置」 */
    fun openA11y(c: Context) {
        runCatching {
            c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } //去开无障碍

    const val SP_A11Y = "a11y"
    const val SP_BATTERY = "battery"
    const val SP_OVERLAY = "overlay"

    /** 特殊权限分档：普通用户要无障碍 + 保活 + 悬浮窗；Root / Shizuku 有 shell，只补悬浮窗 */
    fun specials(mode: WorkMode): List<String> = when (mode) {
        WorkMode.NORMAL -> listOf(SP_A11Y, SP_BATTERY, SP_OVERLAY)
        else -> listOf(SP_OVERLAY)
    } //按模式给档位

    fun specialName(k: String): String = when (k) {
        SP_A11Y -> "屏幕操作"
        SP_BATTERY -> "后台保活"
        else -> "悬浮窗"
    } //档位名

    fun specialDesc(k: String): String = when (k) {
        SP_A11Y -> "让 AI 读屏、点屏。不开也能用，只是屏幕类工具会跑不通。"
        SP_BATTERY -> "加进电池优化白名单，锁屏后任务不被系统掐掉。"
        else -> "让「正在进行 xx」的胶囊浮在其他应用上层。"
    } //档位说明

    /** 电池优化白名单：进了名单系统就不在后台随便杀 */
    fun batteryFree(c: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 23) return@runCatching true
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(c.packageName)
    }.getOrDefault(true) //保活状态

    fun specialOn(c: Context, k: String): Boolean = when (k) {
        SP_A11Y -> a11yOn(c)
        SP_BATTERY -> batteryFree(c)
        else -> runCatching { Settings.canDrawOverlays(c) }.getOrDefault(false)
    } //档位是否已开

    fun openSpecial(c: Context, k: String) {
        when (k) {
            SP_A11Y -> openA11y(c)
            SP_BATTERY -> openBattery(c)
            else -> openOverlay(c)
        }
    } //去开某一档

    private fun go(c: Context, i: Intent): Boolean {
        val ok = c.packageManager.resolveActivity(i, 0) != null
        if (ok) runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return ok
    } //能跳就跳，返回是否成功

    /** 保活：先试系统弹窗，机型不给就退回电池优化列表页 */
    fun openBattery(c: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${c.packageName}")
        )
        if (go(c, direct)) return
        go(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } //去开保活

    fun openOverlay(c: Context) {
        go(
            c, Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${c.packageName}")
            )
        )
    } //去开悬浮窗
} //权限申请与跳转