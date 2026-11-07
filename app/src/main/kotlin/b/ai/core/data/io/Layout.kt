package b.ai.core.data.io

import android.content.Context
import java.io.File

/**
 * 私有目录的数据布局：按「能不能丢」分三块，不再让十几个目录平铺在 files/ 根下。
 *
 * data/   不能丢：加密库、配置库、用户脚本与 Linux 根、导入导出的中间产物、统计
 * img/    全是图片：壁纸、头像、聊天图、账号头像、厂商图标、截图
 * cache/  随时可清：附件缓存、下载的安装包
 *
 * 别和 core/cfg/io/Store 混：那个是数据根里的单文件读写（read/write/exists），这个只管目录归位。
 * 老安装的一次性搬迁在 [migrate] 里做，启动时排在 Guard.boot / Cfg.init 之前。
 */
object Layout {
    /** 布局版本：加 1 就让所有设备再跑一次搬迁 */
    const val VER = 3
    private const val MARK = ".layout"

    /** 不能丢的数据根 */
    const val DATA = "data"
    /** 加密库：Vault 的每个桶都是这个目录下的一个密文文件 */
    const val CFG = "data/configs"
    /** 配置库：原来的 SharedPreferences 换成这里的一堆 json，一库一文件 */
    const val PREFS = "data/prefs"
    /** 用户自己的东西：js 脚本、Linux 根文件系统、通知记录 */
    const val USER = "data/user"
    /** 导入备份、打包这类中间产物 */
    const val COMPRESS = "data/compress"
    /** 统计导出：用量报表这类，库里查完落这里给用户拿走 */
    const val STAT = "data/statistics"

    /** 图片根 */
    const val IMG = "img"
    const val WALL = "img/wall"
    const val AVATAR = "img/avatars"
    const val CHAT = "img/imgs"
    const val FACE = "img/faces"
    const val BRAND = "img/brands"
    const val SHOT = "img/shots"

    /** 可清缓存根 */
    const val CACHE = "cache"
    const val ATTACH = "cache/attach"
    const val APK = "cache/update"

    /** 全部需要预先建出来的目录：少一层各写一次 mkdirs */
    private val DIRS = listOf(CFG, PREFS, USER, COMPRESS, STAT, WALL, AVATAR, CHAT, FACE, BRAND, SHOT, ATTACH, APK)

    private fun root(ctx: Context): File = ctx.filesDir

    /** 取一个布局目录，不存在就建 */
    fun dir(ctx: Context, name: String): File = File(root(ctx), name).also { runCatching { it.mkdirs() } }

    /** 取目录下的文件，目录顺手建好 */
    fun file(ctx: Context, name: String, sub: String): File = File(dir(ctx, name), sub)

    /** 启动时调一次：把老布局的十几个一级目录搬进新布局，搬完写标记，之后不再扫盘 */
    fun migrate(ctx: Context) {
        val mark = File(root(ctx), MARK)
        if (mark.isFile && mark.readText().trim() == VER.toString()) return
        //旧的 目录名 → 新的布局目录
        listOf(
            "configs" to CFG,
            "js" to "$USER/js",
            "linux" to "$USER/linux",
            "noti" to "$USER/noti",
            "attach" to ATTACH,
            "update" to APK,
            "restore" to "$COMPRESS/restore",
            "wall" to WALL,
            "avatars" to AVATAR,
            "imgs" to CHAT,
            "faces" to FACE,
            "brands" to BRAND,
            "shots" to SHOT
        ).forEach { (old, now) -> runCatching { move(ctx, old, now) } }
        DIRS.forEach { dir(ctx, it) }
        //标记最后写：中途被杀下次还会重跑，已搬过的会被 move 里的「目标已存在」挡住
        runCatching { mark.writeText(VER.toString()) }
    } //一次性搬迁

    /** 把一个旧目录里的东西搬进新目录：同分区 rename，跨分区退化成复制；同名已存在的一律不覆盖 */
    private fun move(ctx: Context, old: String, now: String) {
        val src = File(root(ctx), old)
        if (!src.isDirectory) return
        val dst = dir(ctx, now)
        src.listFiles()?.forEach { f ->
            val t = File(dst, f.name)
            if (t.exists()) return@forEach
            runCatching {
                if (!f.renameTo(t)) f.copyRecursively(t, overwrite = false)
            }
        }
        //搬空了才回收这个空壳：delete 对非空目录无效，动不到还没搬成的文件
        runCatching { if (src.listFiles()?.isEmpty() == true) src.delete() }
    } //搬到新位置
} //数据布局