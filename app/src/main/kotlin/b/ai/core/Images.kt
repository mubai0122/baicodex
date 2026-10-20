package b.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/** 图片附件：选中的图先压缩进私有目录，发请求时按需转 base64 */
object Images {
    private const val MAX_SIDE = 1280
    private const val QUALITY = 85

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "imgs").apply { mkdirs() }

    /** 压缩并落盘，返回本地绝对路径；解码失败返回 null */
    fun importFrom(ctx: Context, uri: Uri): String? {
        val bmp = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val scaled = scale(bmp, MAX_SIDE)
        val f = File(dir(ctx), "img-${System.currentTimeMillis()}-${(0..999).random()}.jpg")
        val ok = runCatching {
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.getOrDefault(false)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return if (ok) f.absolutePath else null
    } //相册图片压缩入库

    private fun scale(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val big = maxOf(w, h)
        if (big <= maxSide) return src
        val r = maxSide.toFloat() / big
        return Bitmap.createScaledBitmap(src, (w * r).toInt().coerceAtLeast(1), (h * r).toInt().coerceAtLeast(1), true)
    } //等比缩到最长边

    /** 转 data URL；文件没了返回 null（老会话里的图被清过也不会炸） */
    fun dataUrl(path: String): String? {
        val f = File(path)
        if (!f.isFile) return null
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } //给请求体用的内联图片

    /** 缩小保存，用于清空会话时回收空间 */
    fun delete(path: String) {
        runCatching { File(path).takeIf { it.isFile }?.delete() }
    }
} //图片附件管理