package b.ai.core.llm

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** 推荐模型：主源 ModelScope（国内快），备源 hf-mirror；两个都支持 Range 断点续传 */
data class RecModel(val label: String, val repo: String, val file: String, val note: String) {
    val url: String get() = "https://modelscope.cn/models/$repo/resolve/master/$file"
    val mirror: String get() = "https://hf-mirror.com/$repo/resolve/main/$file"
} //一条推荐模型

object ModelHub {
    val recs = listOf(
        RecModel(
            "Qwen2.5-0.5B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "约 468MB，内存占用最小，老机首选"
        ),
        RecModel(
            "Qwen2.5-1.5B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "约 1.1GB，效果与速度平衡"
        ),
        RecModel(
            "Qwen2.5-3B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-3B-Instruct-GGUF",
            "qwen2.5-3b-instruct-q4_k_m.gguf",
            "约 1.9GB，8GB 内存机型推荐"
        )
    )

    fun dir(ctx: Context): File = File(ctx.filesDir, "models").apply { mkdirs() } //模型目录

    fun freeBytes(ctx: Context): Long = runCatching {
        StatFs(ctx.filesDir.absolutePath).availableBytes
    }.getOrDefault(0L) //可用空间：导入和下载前先看一眼

    /** 已存在同名模型的完整文件即视为已下载 */
    fun installed(ctx: Context, m: RecModel): Boolean = File(dir(ctx), m.file).let { it.isFile && it.length() > 0 }

    /** 断点续传下载：写成 .part，完整拿到才改名，中途失败下次接着下 */
    fun download(
        url: String,
        dst: File,
        onProg: (have: Long, total: Long) -> Unit,
        cancelled: () -> Boolean
    ): Boolean {
        val part = File(dst.parentFile, dst.name + ".part")
        var have = if (part.exists()) part.length() else 0L
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "BaiAgent/1.17 (Android)")
            if (have > 0L) conn.setRequestProperty("Range", "bytes=$have-")
            val code = conn.responseCode
            if (code != 200 && code != 206) {
                conn.disconnect()
                return@runCatching false
            }
            if (code == 200) have = 0L //服务端不认 Range，只能从头来
            val len = conn.contentLengthLong
            val total = when {
                len <= 0L -> -1L
                code == 206 -> have + len
                else -> len
            }
            val raf = RandomAccessFile(part, "rw")
            if (have == 0L) raf.setLength(0L)
            raf.seek(have)
            var last = 0L
            conn.inputStream.use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    if (cancelled()) {
                        raf.close()
                        conn.disconnect()
                        return@runCatching false
                    }
                    val n = input.read(buf)
                    if (n <= 0) break
                    raf.write(buf, 0, n)
                    have += n
                    val now = System.currentTimeMillis()
                    if (now - last > 300L) {
                        last = now
                        onProg(have, total)
                    }
                }
            }
            raf.close()
            conn.disconnect()
            onProg(have, if (total > 0L) total else have)
            if (total > 0L && have < total) return@runCatching false //没下完，留着 .part 下次续
            if (dst.exists()) dst.delete()
            part.renameTo(dst)
        }.getOrDefault(false)
    } //下载到 .part 再改名
} //本地模型下载与目录管理