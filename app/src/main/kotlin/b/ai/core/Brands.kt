package b.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** 厂商图标：按 base_url 的域名自动抓官网 favicon，抓不到就返回 null 让界面退化成首字母徽章 */
object Brands {

    /** 图标只显示十几 dp，存 96 够清晰又不占内存 */
    private const val SIDE = 96

    /** 内存缓存：失败也记（value 为 null），免得每次重组都重连一次已挂的域名 */
    private val mem = HashMap<String, ImageBitmap?>()

    private fun dir(ctx: Context): File = File(ctx.filesDir, "brands").apply { runCatching { mkdirs() } }

    /** 常见接口子域：去掉后剩下的才是官网主域（api.deepseek.com → deepseek.com） */
    private val SUBS = setOf("api", "www", "open", "chat", "portal", "console", "llm", "gateway", "server", "v1")

    fun domain(baseUrl: String): String? {
        val h = runCatching { URI(baseUrl.trim()).host }.getOrNull() ?: return null
        val p = h.lowercase().split('.').filter { it.isNotBlank() }
        if (p.size < 2) return null
        var i = 0
        while (i < p.size - 2 && p[i] in SUBS) i++
        return p.drop(i).joinToString(".")
    } //从 base_url 推官网域名

    /** 已经拿到的（含失败）直接给，没有就返回 null 由调用方决定要不要联网 */
    fun cached(id: String): ImageBitmap? = mem[id]

    /** 取图标：内存 → 磁盘 → 联网抓一次；失败结果也缓存 */
    fun ensure(ctx: Context, id: String, baseUrl: String): ImageBitmap? {
        if (mem.containsKey(id)) return mem[id]
        val f = File(dir(ctx), "$id.png")
        if (f.exists()) {
            val b = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            if (b != null) {
                val img = b.asImageBitmap()
                mem[id] = img
                return img
            }
            runCatching { f.delete() }
        }
        val img = fetch(baseUrl)?.let { bmp ->
            runCatching {
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bmp.asImageBitmap()
        }
        mem[id] = img
        return img
    } //取厂商图标

    /** 依序试几个公共 favicon 服务，返回第一张能解码的方图 */
    private fun fetch(baseUrl: String): Bitmap? {
        val d = domain(baseUrl) ?: return null
        val list = listOf(
            "https://api.iowen.cn/favicon/$d.png",
            "https://www.google.com/s2/favicons?sz=128&domain=$d",
            "https://$d/favicon.ico"
        )
        for (u in list) {
            val bytes = get(u) ?: continue
            val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: continue
            return square(bmp)
        }
        return null
    } //抓 favicon

    /** 居中裁方再缩到 SIDE，避免宽图被拉变形 */
    private fun square(src: Bitmap): Bitmap {
        val s = minOf(src.width, src.height)
        if (s <= 0) return src
        val x = (src.width - s) / 2
        val y = (src.height - s) / 2
        val crop = runCatching { Bitmap.createBitmap(src, x, y, s, s) }.getOrDefault(src)
        val out = if (s > SIDE) Bitmap.createScaledBitmap(crop, SIDE, SIDE, true) else crop
        if (crop !== src && crop !== out) crop.recycle()
        if (src !== crop && src !== out) src.recycle()
        return out
    } //裁成方图

    /** 小 GET：只为了几十 KB 的图标，超时就放弃，别拖住界面 */
    private fun get(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 4000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BaiAgent")
        try {
            if (c.responseCode !in 200..299) return null
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull() //下载图标字节
} //厂商图标
