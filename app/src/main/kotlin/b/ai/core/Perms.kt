package b.ai.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** 运行期权限：进应用一次性申请，缺权限时工具调用直接拒绝 */
object Perms {
    private fun storage(): List<String> = if (Build.VERSION.SDK_INT >= 33) listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
    ) else listOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

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

    /** 还没授予的权限；空表示都齐了 */
    fun missing(c: Context): List<String> = runtime().filterNot { granted(c, it) }

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
    }
} //权限申请与跳转
