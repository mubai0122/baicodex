package b.ai.core.misc.apk
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import b.ai.device.shell.root.Root
import b.ai.device.shell.root.Shiz
import java.io.File
import rikka.shizuku.Shizuku
/** 装更新包：有 root / Shizuku 就静默装，都没有才拉系统安装器 */
object ApkInstall {
    private const val TAG = "bai.install"
    /** pm install 的执行者读不到应用私有目录，先把包搬到它能读的地方 */
    private const val TMP = "/data/local/tmp/bai-update.apk"
    /** 安装结果：ok = 已静默装完或已拉起安装器；msg = 给用户看的一句话 */
    data class Done(val ok: Boolean, val msg: String, val silent: Boolean = false)

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

    /** 静默装：成功返回 null，失败返回原因（调用方据此回退系统安装器） */
    private fun silent(ctx: Context, apk: File): String? {
        //root：能直接读 /data/user/0，拷到 /data/local/tmp 再装
        if (Root.suPath() != null && Root.run("id", 6_000).out.contains("uid=0")) {
            val r = Root.run(
                "cp '${apk.absolutePath}' $TMP && pm install -r -d $TMP; rc=\$?; rm -f $TMP; exit \$rc",
                300_000
            )
            if (r.ok || r.out.contains("Success")) return null
            return "root 静默安装失败：${r.out.take(120)}"
        }
        //Shizuku：shell 读不了应用私有目录，先把包放到应用外部目录（shell 有存储权限）再拷
        val granted = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) return "既没有 root，也没有可用的 Shizuku"
        val mid = File(ctx.getExternalFilesDir(null) ?: return "拿不到外部目录", "update")
        runCatching { mid.mkdirs() }
        val shared = File(mid, apk.name)
        val copy = runCatching { apk.copyTo(shared, overwrite = true) }
        if (copy.isFailure) return "安装包搬不出来：${copy.exceptionOrNull()?.message?.take(80)}"
        val r = Shiz.run(
            "cp '$shared' $TMP && pm install -r -d $TMP; rc=\$?; rm -f $TMP; exit \$rc",
            null,
            300_000
        )
        runCatching { shared.delete() }
        if (r.ok || r.out.contains("Success")) return null
        return "Shizuku 静默安装失败：${r.out.take(120)}"
    } //静默安装

    /** 装包入口：先静默，不行再拉系统安装器 */
    fun install(ctx: Context, apk: File): Done {
        if (!apk.isFile || apk.length() <= 0) return Done(false, "安装包不存在或为空")
        //静默优先：有 root / Shizuku 就省掉「未知来源」那一趟系统页
        val why = silent(ctx, apk)
        if (why == null) return Done(true, "已静默安装，重开应用生效", silent = true)
        Log.i(TAG, "静默安装没走通：$why")
        if (!allowed(ctx)) {
            requestPerm(ctx)
            return Done(false, "先去打开「安装未知应用」权限，回来再点更新（包已下好，不用重下）")
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Done(true, "已交给系统安装器")
        }.getOrElse { Done(false, "拉起安装器失败：${it.message?.take(60)}") }
    } //装包
} //装更新包