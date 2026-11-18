/**
 * 字体库：把用户丢进来的字体文件（ttf / otf / ttc）转成 Compose 的 FontFamily。
 * 目录放外部私有目录 fonts（免存储权限，卸载跟着清），设置页有「导入字体」按钮从系统文件选择器拷进来。
 * 转换结果按「绝对路径 + 最后修改时间」缓存：文件被换了名字不变也会自动重载。
 */
package b.ai.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import b.ai.App
import java.io.File

/** AI 回复 / Markdown 正文用的字体；空字体会退化成系统默认 */
val LocalAiFont = compositionLocalOf<FontFamily> { FontFamily.Default }

/** 我方（用户）气泡字体 */
val LocalUserFont = compositionLocalOf<FontFamily> { FontFamily.Default }

object FontStore {
    /** 只认这三种扩展名，其它文件一律忽略 */
    private val EXTS = setOf("ttf", "otf", "ttc")

    /** 路径 + mtime → FontFamily */
    private val cache = HashMap<String, Pair<Long, FontFamily>>()

    /** 字体目录：外部私有目录下的 fonts，不存在就建 */
    fun dir(): File {
        val base = App.self.getExternalFilesDir(null) ?: App.self.filesDir
        return File(base, "fonts").apply { if (!isDirectory) mkdirs() }
    } //字体目录

    /** 目录里的字体文件，按文件名排序；读不到目录就回空表 */
    fun files(): List<File> = runCatching {
        dir().listFiles { f -> f.isFile && f.extension.lowercase() in EXTS }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }.getOrDefault(emptyList())

    /** 把系统文件选择器给的 Uri 拷进字体目录，返回落地文件；失败回 null */
    fun import(ctx: Context, uri: Uri): File? = runCatching {
        val raw = name(ctx, uri) ?: "font_${System.currentTimeMillis()}.ttf"
        //文件名里的空白与中文一律换成下划线：proot / 备份脚本碰到怪名字容易出岔子
        val safe = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safe.substringAfterLast('.', "").lowercase() !in EXTS) return null
        val out = File(dir(), safe)
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            out.outputStream().use { o -> ins.copyTo(o) }
        } ?: return null
        out
    }.getOrNull()

    /** 读文件选择器里的显示名 */
    private fun name(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    /** 路径 → FontFamily；空路径 / 文件不在 / 解析失败都回 null，调用方自己退回默认字体 */
    @Synchronized
    fun family(path: String): FontFamily? {
        if (path.isBlank()) return null
        val f = File(path)
        if (!f.isFile) return null
        val hit = cache[f.absolutePath]
        if (hit != null && hit.first == f.lastModified()) return hit.second
        val fam = runCatching { FontFamily(Font(f)) }.getOrNull() ?: return null
        cache[f.absolutePath] = f.lastModified() to fam
        return fam
    } //取字体族

    /** 显示用短名：去掉目录与扩展名 */
    fun label(path: String): String {
        if (path.isBlank()) return "默认"
        return File(path).nameWithoutExtension
    } //字体短名

    /** 配置项里的候选列表：第一项恒为空串（默认字体） */
    fun options(): List<String> = listOf("") + files().map { it.absolutePath }
} //字体库