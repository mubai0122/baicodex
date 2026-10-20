package b.ai.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/** 装更新包：应用私有目录里的 apk 交给系统安装器，走 FileProvider 授权读，不碰外部存储 */
object ApkInstall {
    private const val TAG = "bai.install"

    /** 有没有「安装未知应用」权限；API 26 以下系统不限制 */
    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls() //能否装未知来源

    /** 跳系统的「安装未知应用」开关页 */
    fun requestPerm(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "打不开安装权限页：${it.message}") }
    } //跳安装权限页

    /** 拉起系统安装器；没权限时先跳设置页，返回给用户看的提示文案（null = 已拉起） */
    fun install(ctx: Context, apk: File): String? {
        if (!apk.isFile || apk.length() <= 0) return "安装包不存在或为空"
        if (!allowed(ctx)) {
            requestPerm(ctx)
            return "先去打开「安装未知应用」权限，回来再点更新"
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        }.onFailure {
            Log.w(TAG, "拉起安装器失败：${it.message}")
        }.getOrElse { "拉起安装器失败：${it.message?.take(60)}" }
    } //拉起安装器
} //安装更新包