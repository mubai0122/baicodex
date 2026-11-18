/**
 * UpdateMirror.kt — 更新包镜像：清单拉取 / 并行测速 / 择优下载
 *
 * 背景：APK 更新包不再只认一个直链，改为从服务端清单里挑节点下载。
 * 服务端接口：GET /api/update/nodes.php
 *   { "ok":true, "version":"20261118.1",
 *     "file":{ "name":"BaicodexV1.18.1.apk", "size":0, "sha256":"..." },
 *     "threshold":{ "ttfbMs":200 },
 *     "nodes":[ {"id":"self","name":"官方源","url":"..."} ] }
 *
 * 原则：客户端不硬编码任何镜像地址。节点失效时只改服务端 php，客户端不发版。
 * 兜底：清单接口完全拉不到时，才退回 get.php 里下发的那条 downloadDirectUrl（由调用方传入）。
 *
 * 选择口径与 LinuxMirror 一致：TTFB ≤ 200ms 算达标，达标者里选速度最快的；
 * 全都不达标就「谁快选谁」；全失败返回 null。
 */
package b.ai.net.api.update

import android.util.Log
import b.ai.core.cfg.app.Bai
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object UpdateMirror {

    private const val TAG = "bai.upmirror"

    /** 默认达标阈值（毫秒）：服务端可用 threshold.ttfbMs 覆盖 */
    const val FAST_TTFB_MS = 200L

    @Volatile
    var fastTtfbMs: Long = FAST_TTFB_MS

    /** 最近一次拉清单失败的原因：开发者工具里直接显示 */
    @Volatile
    var lastError: String = ""
        private set

    /** 测速抓取的字节数：更新包节点都是静态文件，128KB 足够区分快慢 */
    private const val PROBE_BYTES = 128 * 1024

    /** 单节点测速总超时 */
    private const val PROBE_TIMEOUT_MS = 6_000

    /** 下载阶段：连接超时 / 无数据读超时 */
    private const val DL_CONNECT_TIMEOUT_MS = 15_000
    private const val DL_READ_TIMEOUT_MS = 30_000

    private const val UA = "BaiAgent-Update"

    /** 一个镜像节点：id 稳定用于日志，name 给用户看 */
    data class Node(val id: String, val name: String, val url: String)

    /** 更新包元信息：大小与 sha256 以服务端为准，客户端只校验 */
    data class Meta(val name: String, val size: Long, val sha256: String)

    /** 清单：包元信息 + 节点列表 */
    data class Manifest(val meta: Meta, val nodes: List<Node>)

    /** 单节点测速结果 */
    data class Probe(
        val node: Node,
        val ok: Boolean,
        val ttfbMs: Long = -1,
        val speedBps: Long = 0,
        val err: String = ""
    ) {
        /** 速度展示 */
        fun speedText(): String = when {
            !ok -> "不可用"
            speedBps <= 0 -> "0 KB/s"
            speedBps < 1024 * 1024 -> "${speedBps / 1024} KB/s"
            else -> String.format("%.1f MB/s", speedBps / 1024.0 / 1024.0)
        } //速度展示

        /** 是否达标：连得上且 TTFB 在阈值内 */
        fun fast(): Boolean = ok && ttfbMs in 0..fastTtfbMs
    } //单节点结果

    /** 清单接口地址：集中在这里，换路径只改一处 */
    fun endpoint(): String = Bai.api("/api/update/nodes.php")

    /**
     * 拉服务端镜像清单。失败返回 null，调用方退回 get.php 的直链。
     */
    fun fetchManifest(timeoutMs: Int = 6_000): Manifest? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                //明确不压缩：HttpURLConnection 不替我们解 gzip，走代理时容易读到压缩字节
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                lastError = "HTTP $code：${raw.take(120)}"
                null
            } else {
                val m = runCatching { parseManifest(raw) }.getOrElse { e ->
                    lastError = "解析异常：${e.message ?: e.javaClass.simpleName}；原文 ${raw.take(120)}"
                    null
                }
                if (m == null && lastError.isBlank()) lastError = "返回不是可用清单：${raw.take(120)}"
                if (m != null) lastError = ""
                m
            }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: ""}".trim()
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    } //拉清单

    /** 解析清单 JSON：没 url 的节点直接跳过，避免下载阶段才炸 */
    internal fun parseManifest(raw: String): Manifest? {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        //服务端可能包一层 {ok, ...} 或 {code, data:{...}}，两种都认
        val root = o.optJSONObject("data") ?: o
        val f = root.optJSONObject("file")
        val meta = Meta(
            name = f?.optString("name").orEmpty().ifBlank { "update.apk" },
            size = f?.optLong("size", 0L) ?: 0L,
            sha256 = f?.optString("sha256").orEmpty().lowercase()
        )
        val thMs = root.optJSONObject("threshold")?.optLong("ttfbMs", FAST_TTFB_MS) ?: FAST_TTFB_MS
        if (thMs > 0) fastTtfbMs = thMs
        val arr = root.optJSONArray("nodes") ?: return null
        val nodes = ArrayList<Node>(arr.length())
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            val url = n.optString("url").trim()
            if (url.isBlank()) continue
            nodes += Node(
                id = n.optString("id").ifBlank { "n$i" },
                name = n.optString("name").ifBlank { "节点 $i" },
                url = url
            )
        }
        return if (nodes.isEmpty()) null else Manifest(meta, nodes)
    } //解析清单

    /**
     * 并行测所有节点：每个节点一个线程，各抓 128KB 计时，每出一结果回调一次。
     * onEach 用于 UI 边测边涨进度（第一个参数已完成数，第二个总数）。
     */
    fun probeAll(
        nodes: List<Node>,
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        onEach: (Probe, done: Int, total: Int) -> Unit = { _, _, _ -> }
    ): List<Probe> {
        if (nodes.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(nodes.size.coerceAtMost(8))
        val latch = CountDownLatch(nodes.size)
        val results = arrayOfNulls<Probe>(nodes.size)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        nodes.forEachIndexed { i, node ->
            pool.execute {
                val p = probe(node, timeoutMs)
                results[i] = p
                runCatching { onEach(p, done.incrementAndGet(), nodes.size) }
                latch.countDown()
            }
        }
        //总上限比单节点超时多一点，防止线程池异常时永久挂住
        latch.await(timeoutMs + 4_000L, TimeUnit.MILLISECONDS)
        pool.shutdownNow()
        return results.mapIndexed { i, p -> p ?: Probe(nodes[i], false, err = "超时") }
    } //并行测速

    /** 单节点测速：Range 只取头部，用「首字节耗时 + 吞吐」两个数说明质量 */
    fun probe(node: Node, timeoutMs: Int = PROBE_TIMEOUT_MS): Probe {
        var conn: HttpURLConnection? = null
        return try {
            val t0 = System.nanoTime()
            conn = (URL(node.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
            }
            val code = conn.responseCode
            //部分 CDN 不支持 Range 会回 200+全量，也算可用
            if (code !in 200..299) return Probe(node, false, err = "HTTP $code")
            val buf = ByteArray(16 * 1024)
            var total = 0L
            var ttfb = -1L
            conn.inputStream.use { ins ->
                while (total < PROBE_BYTES) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    if (ttfb < 0) ttfb = (System.nanoTime() - t0) / 1_000_000
                    total += n
                }
            }
            val costMs = ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1)
            //速度只算首字节之后的耗时，握手的排队时间不该算进带宽
            val bodyMs = (costMs - ttfb.coerceAtLeast(0)).coerceAtLeast(1)
            val speed = if (ttfb < 0) 0L else total * 1000 / bodyMs
            Probe(node, ok = ttfb >= 0, ttfbMs = ttfb.coerceAtLeast(0), speedBps = speed)
        } catch (e: Exception) {
            Probe(node, false, err = e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    } //单节点测速

    /** 择优：达标里最快；都不达标就整体最快；全失败 null */
    fun pick(probes: List<Probe>): Probe? {
        val ok = probes.filter { it.ok }
        if (ok.isEmpty()) return null
        val fast = ok.filter { it.fast() }
        val pool = if (fast.isNotEmpty()) fast else ok
        return pool.maxByOrNull { it.speedBps } ?: pool.first()
    } //择优

    /** 排序：可用优先、达标优先、速度高优先（下载兜底顺序同此） */
    fun order(probes: List<Probe>): List<Probe> =
        probes.sortedWith(
            compareByDescending<Probe> { it.ok }
                .thenByDescending { it.fast() }
                .thenByDescending { it.speedBps }
        )

    /**
     * 从指定节点下载到 dst（覆盖写）。
     * 自带 http→https 这类跨协议跳转跟随（GitHub Release 会 302 到 objects.githubusercontent.com）。
     *
     * @param onProgress (已下载字节, 总字节；总字节 -1 表示服务端没给 Content-Length)
     */
    fun download(url: String, dst: File, onProgress: (Long, Long) -> Unit): File {
        var current = url
        repeat(5) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DL_CONNECT_TIMEOUT_MS
                    readTimeout = DL_READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: error("HTTP $code 无跳转地址")
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) error("HTTP $code")
                val total = runCatching { conn.contentLengthLong }.getOrDefault(-1L)
                dst.parentFile?.mkdirs()
                var done = 0L
                val buf = ByteArray(64 * 1024)
                conn.inputStream.use { ins ->
                    dst.outputStream().use { outs ->
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            outs.write(buf, 0, n)
                            done += n
                            runCatching { onProgress(done, total) }
                        }
                    }
                }
                if (dst.length() <= 0) error("下载到的文件是空的")
                Log.i(TAG, "下载完成 ${dst.absolutePath} ${dst.length()} 字节")
                return dst
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
        error("跳转次数过多")
    } //从节点下载
} //更新包镜像
