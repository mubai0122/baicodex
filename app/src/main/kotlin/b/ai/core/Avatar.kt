package b.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**聊天头像：图片一律压成小方图落私有目录，读的时候带缓存，路径为空就退回默认图标 */
object Avatar {
    /**头像只要这么点，太大只是白占内存 */
    private const val SIDE = 256
    private const val QUALITY = 90

    private fun dir(ctx: Context): File = File(ctx.filesDir, "avatars").apply { mkdirs() }

    /** 从相册选一张落盘，返回本地绝对路径；解码失败返回 null */
    fun importFrom(ctx: Context, uri: Uri): String? {
        val bmp = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val scaled = cropSquare(bmp)
        val f = File(dir(ctx), "av-${System.currentTimeMillis()}-${(0..999).random()}.jpg")
        val ok = runCatching {
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.getOrDefault(false)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return if (ok) f.absolutePath else null
    } //相册图片压成头像入库

    /** 居中裁正方形再缩到 SIDE，避免头像被拉变形 */
    private fun cropSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cut = Bitmap.createBitmap(src, x, y, side, side)
        return if (side <= SIDE) cut else Bitmap.createScaledBitmap(cut, SIDE, SIDE, true)
    } //裁方缩图

    private val cache = ConcurrentHashMap<String, ImageBitmap?>()

    /** 路径转可绘制的位图，带缓存；文件没了返回 null 让界面退回默认图标 */
    fun bitmap(path: String): ImageBitmap? {
        val p = path.trim()
        if (p.isEmpty()) return null
        cache[p]?.let { return it }
        val bmp = runCatching { BitmapFactory.decodeFile(p) }.getOrNull()
        val out = bmp?.asImageBitmap()
        cache[p] = out
        return out
    } //读头像（带缓存）

    /** 丢掉某条缓存：换了图要立刻看到新图 */
    fun forget(path: String) {
        cache.remove(path.trim())
    }

    /** AI 头像最终用哪张图：按设置决定用角色卡的还是自定义的 */
    fun aiPath(): String = when (Cfg.avatarAiMode) {
        "custom" -> Cfg.avatarAi.trim()
        else -> Personas.current()?.avatar?.trim().orEmpty()
    } //AI 头像路径
} //聊天头像