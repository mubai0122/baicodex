package b.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** 页面背景图：选图缩到长边 1600 落私有目录，模糊用纯 Kotlin 栈式模糊算，不依赖 API 31 的 RenderEffect */
object Wallpaper {

    /** 落盘上限：再大只是白占内存与磁盘，铺满屏幕用不上 */
    private const val LONG_SIDE = 1600

    /** 模糊前先缩到这个宽度，速度差一个量级，观感几乎没差 */
    private const val WORK_W = 720

    private fun dir(ctx: Context): File = File(ctx.filesDir, "wall").apply { runCatching { mkdirs() } }

    private var cacheKey = ""
    private var cacheImg: ImageBitmap? = null

    /** 相册选一张落盘，返回绝对路径；解码失败返回 null */
    fun import(ctx: Context, uri: Uri): String? {
        val src = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return null
        val long = max(src.width, src.height)
        val scaled = if (long > LONG_SIDE) {
            val k = LONG_SIDE.toFloat() / long
            Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true)
        } else src
        val f = File(dir(ctx), "bg.jpg")
        runCatching { FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        if (scaled !== src) scaled.recycle()
        src.recycle()
        cacheKey = ""
        cacheImg = null
        return f.absolutePath
    } //选图落盘

    /** 按「路径 + 模糊值」取图：同一组参数只解码与模糊一次 */
    fun get(path: String, blur: Int): ImageBitmap? {
        if (path.isBlank()) return null
        val b = blur.coerceIn(0, 100)
        val key = "$path#$b"
        if (key == cacheKey) return cacheImg
        val src = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val w = minOf(src.width, WORK_W)
        val h = (src.height * (w.toFloat() / src.width)).toInt().coerceAtLeast(1)
        val small = if (w < src.width) Bitmap.createScaledBitmap(src, w, h, true) else src
        val out = if (b == 0) small else blurWrap(small, radius(small.width, b))
        cacheKey = key
        cacheImg = out.asImageBitmap()
        return cacheImg
    } //按参数取背景图

    /** 模糊半径：按工作宽度等比缩放，100 档大约 40px */
    private fun radius(w: Int, blur: Int): Int =
        (blur / 100f * 40f * (w / WORK_W.toFloat())).toInt().coerceAtLeast(1)

    /** 两趟盒式模糊近似高斯，够用且实现短 */
    private fun blurWrap(src: Bitmap, r: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 2 || h < 2 || r <= 0) return src
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        box(pix, w, h, r)
        box(pix, w, h, r)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        if (src !== out) src.recycle()
        return out
    } //模糊整张图

    /** 原地做一次横向 + 纵向盒式模糊 */
    private fun box(pix: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        val tmp = IntArray(pix.size)
        //横向：滑窗求和，边界像素用夹取
        for (y in 0 until h) {
            val row = y * w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = pix[row + i.coerceIn(0, w - 1)]
                sa += c ushr 24 and 0xFF
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (sa / div shl 24) or (sr / div shl 16) or (sg / div shl 8) or (sb / div)
                val add = pix[row + (x + r + 1).coerceAtMost(w - 1)]
                val sub = pix[row + (x - r).coerceAtLeast(0)]
                sa += (add ushr 24 and 0xFF) - (sub ushr 24 and 0xFF)
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        //纵向：同一套滑窗换列扫
        for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                sa += c ushr 24 and 0xFF
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (y in 0 until h) {
                pix[y * w + x] = (sa / div shl 24) or (sr / div shl 16) or (sg / div shl 8) or (sb / div)
                val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
                sa += (add ushr 24 and 0xFF) - (sub ushr 24 and 0xFF)
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    } //一次盒式模糊
} //页面背景图