/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.model.voice

import b.ai.App
import b.ai.native.archive.Bz2tar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Sherpa 模型下载器。
 *
 * 三种源都支持，只看地址后缀分流：
 * 1. `.tar.bz2` / `.tbz2` —— 官方发行包的标准形式，交给 native（bzip2 + 自写 tar 解析）只挑必需的七个文件；
 * 2. `.zip` —— 整包下载后用 ZipInputStream 解；
 * 3. 其余当「目录前缀」，七个裸文件逐个 GET（自己拿 GitHub 存的源走这条）。
 */
object ModelFetch {

    /** 默认源：本仓库 models 目录，逐个裸文件直拉，不需要解压 */
    const val DEFAULT_SRC = "https://raw.githubusercontent.com/mubai0122/Baicodex/main/models/sherpa-zh-14M/"

    private const val UA = "Baicodex/1.19 (+https://agent.ai.baiccb.cn/)"
    private const val CONNECT_MS = 20_000
    private const val READ_MS = 30_000

    /** 进度：percent 0..100，note 给界面直接显示 */
    data class Progress(val percent: Int, val note: String)

    /** 下载失败带人话原因，界面直接显示 message */
    class FetchError(message: String) : IOException(message)

    /** 模型根目录：files/sherpa-models，每个模型一个子目录 */
    fun root(): File = File(App.self.filesDir, "sherpa-models").apply { mkdirs() }

    /** 已经下全的模型目录（七个文件齐），给设置页做「用这个」下拉 */
    fun installed(): List<File> = root().listFiles()
        ?.filter { it.isDirectory && SherpaAsr.REQUIRED.all { n -> File(it, n).isFile } }
        ?.sortedBy { it.name }
        .orEmpty()

    /**
     * 下载到 files/sherpa-models/&lt;名字&gt; 并校验，成功返回目录。
     * 地址以 .zip 结尾走解压分支，其余当「目录前缀」逐文件拉。
     */
    suspend fun fetch(src: String, onProgress: (Progress) -> Unit): File = withContext(Dispatchers.IO) {
        val url = src.trim()
        if (url.isEmpty()) throw FetchError("下载地址是空的")
        val low = url.lowercase()
        val dir = when {
            low.endsWith(".tar.bz2") || low.endsWith(".tbz2") || low.endsWith(".tbz") -> fetchTarBz2(url, onProgress)
            low.endsWith(".zip") -> fetchZip(url, onProgress)
            else -> fetchDir(url, onProgress)
        }
        //最后兜一道校验：缺文件就别让用户以为下好了
        val miss = SherpaAsr.REQUIRED.filterNot { File(dir, it).isFile }
        if (miss.isNotEmpty()) throw FetchError("下载完成但缺文件：" + miss.joinToString("、"))
        dir
    } //下载入口

    /** 目录前缀模式：七个文件依次 GET */
    private fun fetchDir(url: String, onProgress: (Progress) -> Unit): File {
        val base = if (url.endsWith("/")) url else "$url/"
        val name = base.trimEnd('/').substringAfterLast('/').ifBlank { "sherpa" }
        val dir = File(root(), name).apply { mkdirs() }
        val files = SherpaAsr.REQUIRED
        files.forEachIndexed { i, f ->
            onProgress(Progress(i * 100 / files.size, "下载 $f（${i + 1}/${files.size}）"))
            get(base + f, File(dir, f))
        }
        onProgress(Progress(100, "已下载到 ${dir.absolutePath}"))
        return dir
    } //逐文件下载

    /** zip 模式：整包落地再解开，顶层目录忽略，只取需要的文件 */
    private fun fetchZip(url: String, onProgress: (Progress) -> Unit): File {
        val zipName = url.substringAfterLast('/').substringBefore('?').ifBlank { "model.zip" }
        val name = zipName.removeSuffix(".zip").ifBlank { "sherpa" }
        val dir = File(root(), name).apply { mkdirs() }
        val tmp = File(App.self.cacheDir, "sherpa-$zipName")
        runCatching { tmp.delete() }
        onProgress(Progress(0, "下载 $zipName"))
        get(url, tmp)
        onProgress(Progress(60, "解压 $zipName"))
        var n = 0
        ZipInputStream(tmp.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                //只要七个必需文件里的名字，其余（示例音频、文档）不解，省空间也避免路径穿越
                val short = e.name.substringAfterLast('/')
                if (short !in SherpaAsr.REQUIRED) continue
                File(dir, short).also { out -> FileOutputStream(out).use { z.copyTo(it) } }
                n++
                onProgress(Progress(60 + n * 40 / SherpaAsr.REQUIRED.size, "解压 $short"))
            }
        }
        runCatching { tmp.delete() }
        return dir
    } //解压 zip

    /** tar.bz2 模式：整包落到 cache，交给 native 只挑必需的七个文件解出来（tar 里的目录结构不落地） */
    private fun fetchTarBz2(url: String, onProgress: (Progress) -> Unit): File {
        val pak = url.substringAfterLast('/').substringBefore('?').ifBlank { "model.tar.bz2" }
        val name = pak.removeSuffix(".tar.bz2").removeSuffix(".tbz2").removeSuffix(".tbz").ifBlank { "sherpa" }
        val dir = File(root(), name).apply { mkdirs() }
        val tmp = File(App.self.cacheDir, "sherpa-$pak")
        runCatching { tmp.delete() }
        onProgress(Progress(0, "下载 $pak"))
        var lastPct = -1
        get(url, tmp) { done, total ->
            //下载占总进度 70%，每过一个百分点才回调一次，避免刷爆 Compose 重组
            val pct = if (total > 0) (done * 70 / total).toInt().coerceIn(0, 70) else 0
            if (pct != lastPct) {
                lastPct = pct
                onProgress(Progress(pct, if (total > 0) "下载 $pak $pct%" else "下载 $pak ${done / 1048576} MB"))
            }
        }
        onProgress(Progress(72, "解压 $pak"))
        val n = Bz2tar.extract(tmp, dir, SherpaAsr.REQUIRED)
        runCatching { tmp.delete() }
        if (n < 0) throw FetchError("解压失败：" + Bz2tar.describe(n))
        onProgress(Progress(96, "解出 $n 个文件"))
        return dir
    } //解压 tar.bz2

    /** 单文件 GET：先写 .part 再改名，中途断网不会留下半个「看起来正常」的模型 */
    private fun get(url: String, out: File, onBytes: (done: Long, total: Long) -> Unit = { _, _ -> }) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw FetchError("HTTP $code：$url")
            val tmp = File(out.parentFile, out.name + ".part")
            val total = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
            conn.inputStream.use { inp ->
                FileOutputStream(tmp).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val r = inp.read(buf)
                        if (r <= 0) break
                        fos.write(buf, 0, r)
                        done += r
                        onBytes(done, total)   //大文件（几十 MB）靠这个刷下载百分比
                    }
                }
            }
            if (tmp.length() <= 0L) {
                tmp.delete()
                throw FetchError("服务器返回空内容：$url")
            }
            if (!tmp.renameTo(out)) {
                out.delete()
                if (!tmp.renameTo(out)) {
                    tmp.delete()
                    throw FetchError("文件写不进去：${out.absolutePath}")
                }
            }
        } finally {
            conn.disconnect()
        }
    } //单文件下载
} //Sherpa 模型下载器