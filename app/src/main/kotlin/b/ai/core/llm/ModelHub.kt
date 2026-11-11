package b.ai.core.llm

import android.content.Context
import android.os.StatFs
import b.ai.core.cfg.io.Paths
import b.ai.core.cfg.app.Cfg
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
    /** 模型目录名，落在公共根 baicode 下：/sdcard/baicode/models，用户能直接用文件管理器换模型 */
    const val SUB = "models"

    val recs = listOf(
        RecModel(
            "Qwen2.5-0.5B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "约 400MB，内存占用最小，老机首选"
        ),
        RecModel(
            "Qwen2.5-1.5B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "约 1.1GB，效果与速度平衡，推荐"
        ),
        RecModel(
            "Qwen2.5-3B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-3B-Instruct-GGUF",
            "qwen2.5-3b-instruct-q4_k_m.gguf",
            "约 1.9GB，8GB 内存机型上限，质量明显更好"
        ),
        RecModel(
            "Qwen3-4B · Q4_K_M",
            "Qwen/Qwen3-4B-GGUF",
            "Qwen3-4B-Q4_K_M.gguf",
            "约 2.5GB，带推理模式，8GB 以上机型可试，速度偏慢"
        ),
        RecModel(
            "Qwen2.5-Coder-7B-Instruct · Q4_K_M",
            "Qwen/Qwen2.5-Coder-7B-Instruct-GGUF",
            "qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            "约 4.4GB，写代码最强，12GB 内存机型才建议，纯 CPU 很慢"
        )
    )

    fun isPublic(): Boolean = Paths.externalOk() && Paths.ensure() == Paths.base() //真落在 /sdcard/baicode 才算公共路径

    fun dir(): File = Paths.dir(SUB) //模型目录：公共路径优先，外部存储不可用时 Paths 会自动退回私有目录

    /** 旧版本位置：App 私有 filesDir/models，用于一次性迁移已有模型 */
    fun legacyDir(ctx: Context): File = File(ctx.filesDir, SUB)

    /** 把旧私有目录里的 .gguf 搬到公共目录，同名已存在就跳过；返回搬成功的文件名 */
    fun migrate(ctx: Context): List<String> {
        val from = legacyDir(ctx)
        if (!from.isDirectory) return emptyList()
        val to = dir()
        if (from.absolutePath == to.absolutePath) return emptyList()
        val moved = mutableListOf<String>()
        from.listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }?.forEach { src ->
            val dst = File(to, src.name)
            if (dst.exists()) return@forEach
            if (src.renameTo(dst) || (src.copyTo(dst, overwrite = false).length() == src.length() && src.delete())) {
                moved += src.name
            }
        }
        //旧路径被写进过配置，搬完要把选中的路径一起改过来
        val sel = Cfg.llmModel
        if (sel.startsWith(from.absolutePath + "/")) {
            val name = File(sel).name
            val dst = File(to, name)
            if (dst.isFile) Cfg.llmModel = dst.absolutePath
        }
        return moved
    } //旧私有模型迁到公共路径

    fun freeBytes(): Long = runCatching {
        StatFs(dir().absolutePath).availableBytes
    }.getOrDefault(0L) //可用空间：导入和下载前先看一眼

    fun list(): List<File> = dir().listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }
        ?.sortedBy { it.name.lowercase() } ?: emptyList() //目录里所有 GGUF

    /** 已存在同名模型的完整文件即视为已下载 */
    fun installed(m: RecModel): Boolean = File(dir(), m.file).let { it.isFile && it.length() > 0 }

    /** 从下载链接里猜文件名：去掉查询串，取最后一段，非 .gguf 就兜底成 model.gguf */
    fun nameOf(url: String): String {
        val p = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val raw = p.substringAfterLast('/').ifBlank { "model.gguf" }
        return if (raw.endsWith(".gguf", true)) raw else "$raw.gguf"
    } //自定义链接的文件名

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
} //本地模型目录与下载管理
