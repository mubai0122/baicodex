package b.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

/** 图片附件：选中的图先压缩进私有目录，发请求时按需转 base64 */
object Images {
    private const val MAX_SIDE = 1280
    private const val MAX_W = 1080
    private const val MAX_PX = 16_000_000L
    private const val MODEL_SIDE = 2048
    private const val DECODE_CAP = 4096
    private const val QUALITY = 85

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "imgs").apply { mkdirs() }

    /** 压缩并落盘，返回本地绝对路径；解码失败返回 null */
    fun importFrom(ctx: Context, uri: Uri): String? {
        val bmp = decodeSampled(ctx, uri) ?: return null
        val scaled = fit(bmp)
        val f = File(dir(ctx), "img-${System.currentTimeMillis()}-${(0..999).random()}.jpg")
        val ok = runCatching {
            FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.getOrDefault(false)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return if (ok) f.absolutePath else null
    } //相册图片压缩入库

    /** 解码前的尺寸探测 + 采样解码：长截屏整张解进来就是几十 MB，必须先砍采样率 */
    private fun decodeSampled(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val bound = runCatching {
            cr.openInputStream(uri)?.use { input ->
                BitmapFactory.Options().apply { inJustDecodeBounds = true }.also {
                    BitmapFactory.decodeStream(input, null, it)
                }
            }
        }.getOrNull() ?: return null
        val w = bound.outWidth
        val h = bound.outHeight
        if (w <= 0 || h <= 0) return null
        //采样基数：长图保宽度（字才看得清），普通图保最长边
        val key = if (isLong(w, h)) minOf(w, h) else maxOf(w, h)
        var s = 1
        while (key / (s * 2) >= DECODE_CAP) s *= 2
        val opt = BitmapFactory.Options().apply { inSampleSize = s }
        return runCatching {
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
        }.getOrNull()
    } //按采样率解码

    /** 长截屏判定：高超过宽的 3 倍 */
    private fun isLong(w: Int, h: Int): Boolean = w > 0 && h > w * 3

    /** 两套缩放规则：长图按宽度保真（不再压成细条），普通图按最长边 */
    private fun fit(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        var tw = w
        var th = h
        if (isLong(w, h)) {
            if (w > MAX_W) {
                tw = MAX_W
                th = (h.toLong() * tw / w).toInt().coerceAtLeast(1)
            }
            //总像素还是太大就等比再缩：宁可略糊，也不能一进来就 OOM
            val px = tw.toLong() * th
            if (px > MAX_PX) {
                val r = Math.sqrt(MAX_PX.toDouble() / px)
                tw = (tw * r).toInt().coerceAtLeast(1)
                th = (h.toLong() * tw / w).toInt().coerceAtLeast(1)
            }
        } else if (maxOf(w, h) > MAX_SIDE) {
            val r = MAX_SIDE.toFloat() / maxOf(w, h)
            tw = (w * r).toInt().coerceAtLeast(1)
            th = (h * r).toInt().coerceAtLeast(1)
        }
        if (tw == w && th == h) return src
        return Bitmap.createScaledBitmap(src, tw, th, true)
    } //按规则缩放

    /** 转 data URL；文件没了返回 null（老会话里的图被清过也不会炸） */
    fun dataUrl(path: String): String? {
        val f = File(path)
        if (!f.isFile) return null
        val src = modelCopy(f)
        val bytes = runCatching { src.readBytes() }.getOrNull() ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } //给请求体用的内联图片

    /** 给模型看的副本：最长边 2048 封顶。长截图原图直发能到 MB 级，多数接口直接拒 */
    private fun modelCopy(src: File): File {
        val dim = sizeOf(src.absolutePath) ?: return src
        val w = dim.first
        val h = dim.second
        if (maxOf(w, h) <= MODEL_SIDE) return src
        val out = File(src.parentFile, "model-" + src.name)
        if (out.isFile && out.length() > 0 && out.lastModified() >= src.lastModified()) return out
        val bmp = decodeFileSampled(src, maxOf(w, h)) ?: return src
        val r = MODEL_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val small = runCatching {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * r).toInt().coerceAtLeast(1),
                (bmp.height * r).toInt().coerceAtLeast(1),
                true
            )
        }.getOrNull() ?: bmp
        val ok = runCatching {
            FileOutputStream(out).use { small.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.getOrDefault(false)
        if (small !== bmp) small.recycle()
        bmp.recycle()
        return if (ok) out else src
    } //模型副本

    /** 按解码上限采样后整张解码：本地文件版，给模型副本与查看器兜底用 */
    private fun decodeFileSampled(f: File, big: Int): Bitmap? {
        var s = 1
        while (big / (s * 2) >= DECODE_CAP) s *= 2
        return runCatching {
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
        }.getOrNull()
    } //采样解码文件

    /** 缩小保存，用于清空会话时回收空间 */
    fun delete(path: String) {
        val f = File(path)
        runCatching { f.takeIf { it.isFile }?.delete() }
        //顺手清掉给模型用的副本，别在私有目录里越积越多
        runCatching { f.parentFile?.let { File(it, "model-" + f.name).takeIf { g -> g.isFile }?.delete() } }
    }
    //缩略图缓存：列表滚动时不重复解码原图，最多留 64 张
    private val thumbs = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 64
    } //LRU 容量 64

    /**本地图片缩略图：按目标边长算 inSampleSize，避免把 1280px 原图整张解进内存 */
    fun thumb(path: String, side: Int = 156): ImageBitmap? {
        val p = path.trim()
        if (p.isEmpty()) return null
        val key = p + "@" + side
        thumbs[key]?.let { return it }
        val out = runCatching {
            val bound = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(p, bound)
            if (bound.outWidth <= 0 || bound.outHeight <= 0) return null
            var s = 1
            while (minOf(bound.outWidth, bound.outHeight) / (s * 2) >= side) s *= 2
            BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }.getOrNull()
        if (out != null) thumbs[key] = out
        return out
    } //读迷你缩略图

    //原始宽高缓存：气泡里按真实比例排图，只量尺寸不解码像素
    private val sizes = object : LinkedHashMap<String, Pair<Int, Int>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>?): Boolean = size > 64
    } //LRU 容量 64

    /**读图片原始宽高，读不到返回 null（文件被清掉时用它兜底）*/
    fun sizeOf(path: String): Pair<Int, Int>? {
        val p = path.trim()
        if (p.isEmpty()) return null
        sizes[p]?.let { return it }
        val bound = runCatching {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(p, it) }
        }.getOrNull() ?: return null
        if (bound.outWidth <= 0 || bound.outHeight <= 0) return null
        val r = bound.outWidth to bound.outHeight
        sizes[p] = r
        return r
    } //量尺寸不占内存

    /**按目标显示边长解码：给气泡大图用，比 thumb 大一档 */
    fun big(path: String, side: Int): ImageBitmap? = thumb(path, side)

} //图片附件管理