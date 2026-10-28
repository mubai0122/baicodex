package b.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 页面背景图：选图缩到长边 1600 落私有目录。
 * 模糊结果与一张几十 KB 的缩略图都落盘缓存，冷启动首帧直接读缩略图上屏，
 * 真图解码完再替换，所以不会再「先白一下再出背景」。
 */
object Wallpaper {

    /** 落盘上限：再大只是白占内存与磁盘，铺满屏幕用不上 */
    private const val LONG_SIDE = 1600

    /** 模糊前先缩到这个宽度，速度差一个量级，观感几乎没差 */
    private const val WORK_W = 720

    /** 缩略图宽度：raw 存盘，128 宽约 110KB，主线程读一次不到 2ms，够铺满首帧 */
    private const val PV_W = 128

    /** 落盘缓存版本号：改了模糊算法或取样宽度就抬一位，旧缓存文件自然作废 */
    private const val CACHE_VER = 2

    /** 只在 warm 时拿一次 applicationContext：compute 要能被无 context 的后台任务调用 */
    @Volatile
    private var app: Context? = null

    private fun dir(ctx: Context): File = File(ctx.filesDir, "wall").apply { runCatching { mkdirs() } }

    /** 缓存戳的记忆表：draw 里会反复问同一条路径，别每帧都去 stat 文件 */
    private val stamps = HashMap<String, Int>()

    /** 缓存戳：路径 + 文件指纹 + 版本号，换了图或换了算法都落到不同文件上 */
    private fun stamp(path: String): Int = synchronized(stamps) {
        stamps.getOrPut(path) {
            val f = File(path)
            val fp = runCatching { "${f.length()}@${f.lastModified()}" }.getOrDefault("0@0")
            val h = "$path#$fp#$CACHE_VER".hashCode()
            if (h < 0) -h else h
        }
    } //缓存戳

    private fun bgFile(ctx: Context, path: String, blur: Int): File = File(dir(ctx), "bg_${stamp(path)}_$blur.jpg")

    private fun pvFile(ctx: Context, path: String): File = File(dir(ctx), "pv_${stamp(path)}.raw")

    private var cacheKey = ""
    private var cacheImg: ImageBitmap? = null

    /** 已算好的背景图：key 是「路径#模糊」。draw 里只读它，没算完先画缩略图 */
    private val ready = mutableStateMapOf<String, ImageBitmap>()
    private val working = java.util.Collections.synchronizedSet(HashSet<String>())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 缩略图内存表：命中就是一次 HashMap 查，不走盘 */
    private val pvCache = HashMap<String, ImageBitmap?>()

    /**
     * 进程起来的第一时间调一次：缩略图同步读进内存（几十 KB，主线程读不心疼），
     * 同时后台把真图解码或补齐盘缓存。这样首帧画出来的背景颜色就是对的。
     */
    fun warm(ctx: Context, path: String, blur: Int) {
        app = ctx.applicationContext
        if (path.isBlank()) return
        preview(path)
        cached(path, blur)
    } //冷启动预热

    /**
     * 非阻塞取图：命中直接返回；没命中先返回 null，后台算完写进 ready 触发重绘。
     * 盘上已有算好的就直接解码，不再走模糊。
     */
    fun cached(path: String, blur: Int): ImageBitmap? {
        if (path.isBlank()) return null
        val b = blur.coerceIn(0, 100)
        val key = "$path#$b"
        ready[key]?.let { return it }
        if (!working.add(key)) return null
        val ctx = app
        io.launch {
            val img = runCatching { if (ctx == null) compute(path, b) else load(ctx, path, b) }.getOrNull()
            if (img != null) ready[key] = img
            working.remove(key)
        }
        return null
    } //非阻塞取图

    /** 同步取缩略图：只读一个小文件，结果常驻内存；没有缓存返回 null，下一次进应用前会补上 */
    fun preview(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        val ctx = app ?: return null
        val f = pvFile(ctx, path)
        synchronized(pvCache) { if (pvCache.containsKey(f.name)) return pvCache[f.name] }
        val img = runCatching { readPv(f) }.getOrNull()
        synchronized(pvCache) { pvCache[f.name] = img }
        return img
    } //取缩略图

    /** 相册选一张落盘并顺手存缩略图，返回绝对路径；解码失败返回 null */
    fun import(ctx: Context, uri: Uri): String? {
        app = ctx.applicationContext
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
        //缓存先归零，再把缩略图跟着原图一起存：下次冷启动首帧就有对色的底，不用等模糊
        cacheKey = ""
        cacheImg = null
        ready.clear()
        synchronized(pvCache) { pvCache.clear() }
        synchronized(stamps) { stamps.clear() }
        runCatching { savePv(ctx, f.absolutePath, scaled) }
        if (scaled !== src) scaled.recycle()
        src.recycle()
        purge(ctx, setOf(pvFile(ctx, f.absolutePath).name))
        return f.absolutePath
    } //选图落盘

    /** 清掉不属于当前图片、当前版本的缓存文件（bg.jpg 本体不动） */
    private fun purge(ctx: Context, keep: Set<String>) {
        runCatching {
            dir(ctx).listFiles()?.forEach { f ->
                if (f.isFile && f.name != "bg.jpg" && f.name !in keep) f.delete()
            }
        }
    } //清旧缓存

    /** 优先读盘缓存，没有再算并写盘（只在后台线程被调用） */
    private fun load(ctx: Context, path: String, blur: Int): ImageBitmap? {
        val key = "$path#$blur"
        synchronized(this) { if (key == cacheKey) return cacheImg }
        val f = bgFile(ctx, path, blur)
        if (f.length() > 0L) {
            val hit = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            if (hit != null) {
                val img = hit.asImageBitmap()
                synchronized(this) { cacheKey = key; cacheImg = img }
                return img
            }
            runCatching { f.delete() }
        }
        val bmp = computeBmp(path, blur) ?: return null
        runCatching { saveBg(ctx, path, blur, bmp) }
        val img = bmp.asImageBitmap()
        synchronized(this) { cacheKey = key; cacheImg = img }
        return img
    } //读盘优先

    /** 解码 + 缩到工作宽度 + 模糊，返回 Android 位图（后台线程，结果会被缓存复用） */
    private fun computeBmp(path: String, blur: Int): Bitmap? {
        val src = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val w = minOf(src.width, WORK_W)
        val h = (src.height * (w.toFloat() / src.width)).toInt().coerceAtLeast(1)
        val small = if (w < src.width) Bitmap.createScaledBitmap(src, w, h, true) else src
        if (small !== src) src.recycle()
        if (blur <= 0) return small
        val pix = IntArray(small.width * small.height)
        small.getPixels(pix, 0, small.width, 0, 0, small.width, small.height)
        Blur.box2(pix, small.width, small.height, radius(small.width, blur))
        val out = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, small.width, 0, 0, small.width, small.height)
        small.recycle()
        return out
    } //解码并模糊

    /** 计算版：不带盘缓存，供没有 context 的调用方兜底 */
    private fun compute(path: String, blur: Int): ImageBitmap? {
        val key = "$path#$blur"
        synchronized(this) { if (key == cacheKey) return cacheImg }
        val bmp = computeBmp(path, blur) ?: return null
        val img = bmp.asImageBitmap()
        synchronized(this) { cacheKey = key; cacheImg = img }
        return img
    } //纯计算

    /** 模糊半径：按工作宽度等比缩放，100 档大约 40px */
    private fun radius(w: Int, blur: Int): Int =
        (blur / 100f * 40f * (w / WORK_W.toFloat())).toInt().coerceAtLeast(1)

    /** 写模糊结果（JPEG）与缩略图（raw），失败就静默，下次重算 */
    private fun saveBg(ctx: Context, path: String, blur: Int, bmp: Bitmap) {
        runCatching {
            FileOutputStream(bgFile(ctx, path, blur)).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
        }
        savePv(ctx, path, bmp)
    } //写盘缓存

    /** 缩略图存 raw（宽高各 4 字节 + ARGB 像素）：读回来不用解码，比解一张 JPEG 快一个量级 */
    private fun savePv(ctx: Context, path: String, bmp: Bitmap) {
        val w = PV_W.coerceAtMost(bmp.width).coerceAtLeast(8)
        val h = (bmp.height * (w.toFloat() / bmp.width)).toInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val pix = IntArray(w * h)
        small.getPixels(pix, 0, w, 0, 0, w, h)
        if (small !== bmp) small.recycle()
        val f = pvFile(ctx, path)
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(f), 64 * 1024)).use { o ->
                o.writeInt(w)
                o.writeInt(h)
                for (p in pix) o.writeInt(p)
            }
        }
        val keep = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        keep.setPixels(pix, 0, w, 0, 0, w, h)
        synchronized(pvCache) { pvCache[f.name] = keep.asImageBitmap() }
    } //写缩略图

    /** 读缩略图：文件不在或损坏都返回 null。整块读进 ByteBuffer 再转 int 数组，比逐个 readInt 快一个量级 */
    private fun readPv(f: File): ImageBitmap? {
        if (f.length() <= 0L) return null
        runCatching {
            val all = f.readBytes()
            if (all.size < 8) return null
            val buf = java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.BIG_ENDIAN)
            val w = buf.int
            val h = buf.int
            if (w !in 1..4096 || h !in 1..4096) return null
            val need = w * h * 4
            if (all.size - 8 < need) return null
            val pix = IntArray(w * h)
            buf.asIntBuffer().get(pix)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pix, 0, w, 0, 0, w, h)
            return bmp.asImageBitmap()
        }
        return null
    } //读缩略图
} //页面背景图