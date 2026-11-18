/**
 * LinuxMirror.kt — Ubuntu rootfs 镜像源管理：清单拉取 / 并行测速 / 择优下载
 *
 * 背景：rootfs.tgz 不再随 APK 内置，改为运行时从服务端下发的节点清单里挑一个下载。
 * 服务端接口：GET /api/linux/nodes.php —— 返回文件元信息与节点列表（含自建直连与第三方加速）。
 * 兜底：服务端不可达时用本文件的 FALLBACK_NODES，保证老版本服务端也能装上环境。
 *
 * 选择口径（"200"）：TTFB ≤ 200ms 的节点算达标，达标者里取下载速度最快的；
 * 一个都不达标时退化为「谁快选谁」，全失败的节点不参与。
 */
package b.ai.device.shell.local

import b.ai.core.cfg.app.Bai
import b.ai.core.cfg.app.Cfg
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LinuxMirror {

    /** 达标延迟阈值（毫秒）：TTFB 在这个数以内算「快线」，优先被选中 */
    const val FAST_TTFB_MS = 200L

    /** 达标阈值（毫秒）：默认 200，服务端清单可用 threshold.ttfbMs 覆盖，便于随时调口径 */
    @Volatile
    var fastTtfbMs: Long = FAST_TTFB_MS

    /** 测速实际抓取的字节数：太小测不准，太大拖慢安装 */
    private const val PROBE_BYTES = 256 * 1024

    /** 单个节点测速的总超时 */
    private const val PROBE_TIMEOUT_MS = 6_000

    /** 下载阶段：连接超时与读超时（读超时按「多久没数据」算，不是总时长） */
    private const val DL_CONNECT_TIMEOUT_MS = 10_000
    private const val DL_READ_TIMEOUT_MS = 30_000

    /** 最多尝试几个节点下载：最优的挂了再退下一个，不会把全清单刷一遍 */
    private const val MAX_TRY = 3

    /** UA：个别加速节点按 UA 限流，带一个明确的客户端标识 */
    private const val UA = "BaiAgent-Android"

    /** 一个镜像节点：id 稳定用于日志与手动选择，name 给用户看 */
    data class Node(val id: String, val name: String, val url: String)

    /** 一份镜像文件的元信息：大小与 sha256 都是服务端权威值，客户端只做校验 */
    data class Meta(val name: String, val size: Long, val sha256: String)

    /** 清单：文件元信息 + 节点列表 */
    data class Manifest(val meta: Meta, val nodes: List<Node>)

    /** 单节点测速结果：ttfb 是首字节耗时，speed 是每字节下载速度（B/s） */
    data class Probe(
        val node: Node,
        val ok: Boolean,
        val ttfbMs: Long = -1,
        val speedBps: Long = 0,
        val err: String = ""
    ) {
        /** 速度给人看的字符串 */
        fun speedText(): String = when {
            !ok -> "不可用"
            speedBps <= 0 -> "0 KB/s"
            speedBps < 1024 * 1024 -> "${speedBps / 1024} KB/s"
            else -> String.format("%.1f MB/s", speedBps / 1024.0 / 1024.0)
        } //速度展示

        /** 是否达标：连得上且 TTFB 在阈值内 */
        fun fast(): Boolean = ok && ttfbMs in 0..fastTtfbMs
    } //单节点结果

    /**
     * 极简兜底：镜像地址全部由服务端 nodes.php 下发，本地不维护镜像源清单。
     * 这里只在「清单接口完全拉不到」时留两条 GitHub 直连，保证新装机器还有一次机会。
     * 注意：GitHub raw 对大小写敏感，路径必须与仓库里的目录名逐字符一致（assets/Linux）。
     */
    val FALLBACK_NODES: List<Node> = listOf(
        Node("gh", "GitHub 源", "https://raw.githubusercontent.com/mubai0122/baicodex/main/app/src/main/assets/Linux/rootfs.tgz"),
        Node("ghfast", "ghfast.top", "https://ghfast.top/https://raw.githubusercontent.com/mubai0122/baicodex/main/app/src/main/assets/Linux/rootfs.tgz")
    )

    /** 兜底文件元信息：服务端拿不到时不做 sha256 校验（size/sha 未知就跳过校验） */
    private val FALLBACK_META = Meta("rootfs.tgz", 0L, "")

    /**
     * 拉服务端清单。失败返回 null，调用方退回 FALLBACK。
     * 接口契约见 /api/linux/nodes.php：{ file:{name,size,sha256}, nodes:[{id,name,url}] }
     */
    /** 最近一次拉清单失败的原因：开发者工具里直接显示，省得只看到一句「拉取失败」 */
    @Volatile
    var lastError: String = ""
        private set

    fun fetchManifest(timeoutMs: Int = 6_000): Manifest? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(Bai.api("/api/linux/nodes.php")).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                //明确要求不压缩：Java 的 HttpURLConnection 不会替我们解 gzip，开代理时容易读到压缩字节
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
                if (m == null && lastError.isBlank()) lastError = "返回不是合法 JSON：${raw.take(120)}"
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
    /** 解析清单 JSON：字段缺失的节点直接跳过，避免下载阶段才炸 */
    internal fun parseManifest(raw: String): Manifest? {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val f = o.optJSONObject("file")
        val meta = Meta(
            name = f?.optString("name").orEmpty().ifBlank { "rootfs.tgz" },
            size = f?.optLong("size", 0L) ?: 0L,
            sha256 = f?.optString("sha256").orEmpty().lowercase()
        )
        val thMs = o.optJSONObject("threshold")?.optLong("ttfbMs", FAST_TTFB_MS)
        if (thMs != null && thMs > 0) fastTtfbMs = thMs
        val arr = o.optJSONArray("nodes") ?: return null
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
     * 并行测所有节点：每个节点一个线程，各抓 256KB 计时。
     * onEach 每出一个结果就回调一次（UI 边测边显示），最后返回全部结果。
     */
    fun probeAll(
        nodes: List<Node>,
        timeoutMs: Int = PROBE_TIMEOUT_MS,
        onEach: (Probe) -> Unit = {}
    ): List<Probe> {
        if (nodes.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(nodes.size.coerceAtMost(8))
        val latch = CountDownLatch(nodes.size)
        val results = arrayOfNulls<Probe>(nodes.size)
        nodes.forEachIndexed { i, node ->
            pool.execute {
                val p = probe(node, timeoutMs)
                results[i] = p
                runCatching { onEach(p) }
                latch.countDown()
            }
        }
        //等所有节点出结果；总上限比单节点超时多一点，防止线程池异常时永久挂住
        latch.await(timeoutMs + 4_000L, TimeUnit.MILLISECONDS)
        pool.shutdownNow()
        return results.mapIndexed { i, p -> p ?: Probe(nodes[i], false, err = "超时") }
    } //并行测速

    /** 单节点测速：Range 只取头部若干字节，用「首字节耗时」和「总吞吐」两个数说明质量 */
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
            if (code !in 200..299) return Probe(node, false, err = "HTTP $code")
            val input: InputStream = conn.inputStream
            val buf = ByteArray(16 * 1024)
            var total = 0L
            var ttfb = -1L
            input.use { ins ->
                while (total < PROBE_BYTES) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    if (ttfb < 0) ttfb = (System.nanoTime() - t0) / 1_000_000
                    total += n
                }
            }
            val costMs = ((System.nanoTime() - t0) / 1_000_000).coerceAtLeast(1)
            //速度只按首字节之后的时间算，把握手/排队时间剥离出去，避免把慢节点误判成「小带宽」
            val bodyMs = (costMs - ttfb.coerceAtLeast(0)).coerceAtLeast(1)
            val speed = if (ttfb < 0) 0L else total * 1000 / bodyMs
            Probe(node, ok = ttfb >= 0, ttfbMs = ttfb.coerceAtLeast(0), speedBps = speed)
        } catch (e: Exception) {
            Probe(node, false, err = e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    } //单节点测速

    /**
     * 择优：先取达标（TTFB ≤ 200ms）里最快的；没有达标的就取整体最快的。
     * 全失败返回 null。
     */
    fun pick(probes: List<Probe>): Probe? {
        val ok = probes.filter { it.ok }
        if (ok.isEmpty()) return null
        val fast = ok.filter { it.fast() }
        val pool = if (fast.isNotEmpty()) fast else ok
        return pool.maxByOrNull { it.speedBps } ?: pool.first()
    } //择优

    /** 排序：达标优先，其次速度高优先；仅用于 UI 展示与下载兜底顺序 */
    fun order(probes: List<Probe>): List<Probe> =
        probes.sortedWith(compareByDescending<Probe> { it.ok }.thenByDescending { it.fast() }.thenByDescending { it.speedBps })

    /**
     * 完整流程：拉清单 → 测速 → 按「手动指定 / 自动择优」挑节点 → 下载校验。
     * 纯阻塞实现（内部线程池并行测速），给在后台线程里跑的 install() 直接用。
     *
     * @param dst 目标文件（不存在或校验不过时才会写入）
     * @param manualId 手动选中的节点 id，空串表示自动
     * @param onLog 日志
     * @param onProgress 下载进度回调 (已下载, 总字节，-1 表示未知)
     */
    fun fetch(
        dst: File,
        manualId: String = "",
        onLog: (String) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean {
        dst.parentFile?.mkdirs()

        val manifest = fetchManifest()
        val nodes = manifest?.nodes ?: FALLBACK_NODES.also {
            onLog("镜像清单拉取失败（${lastError.ifBlank { "未知原因" }}），改用内置节点清单（${it.size} 个）")
        }
        val meta = manifest?.meta ?: FALLBACK_META
        //本地已有镜像：必须跟清单体积对得上才算数，对不上就是上次中断留下的半成品，删掉重下
        if (dst.isFile && dst.length() > 0) {
            if (meta.size <= 0 || dst.length() == meta.size) {
                onLog("镜像已在本地：${dst.absolutePath}（${dst.length() / 1024 / 1024} MB）")
                return true
            }
            onLog("本地镜像不完整（${dst.length()} / ${meta.size} 字节），删除后重下")
            runCatching { dst.delete() }
        }
        onLog("镜像 ${meta.name}，体积 ${if (meta.size > 0) "${meta.size / 1024 / 1024} MB" else "未知"}")

        onLog("开始测速（并行 ${nodes.size} 个节点）…")
        val probes = probeAll(nodes) { p ->
            onLog("· ${p.node.name}：${if (p.ok) "TTFB ${p.ttfbMs}ms / ${p.speedText()}" else "不可用（${p.err}）"}")
        }
        val best = pick(probes)
        if (best == null) {
            onLog("所有镜像节点都不可用：检查网络后重试，或把镜像放到 ${EXT_HINT} 手动导入")
            return false
        }

        //下载顺序：手动指定优先，其次自动择优，再次按速度排其余节点兜底
        val ordered = order(probes).map { it.node }
        val chosen = ordered.firstOrNull { it.id == manualId.trim() }
        val queue = ArrayList<Node>(MAX_TRY)
        (chosen ?: best.node).let { queue += it }
        ordered.forEach { n -> if (queue.size < MAX_TRY && n.id != queue.first().id) queue += n }
        onLog("选用节点：${queue.first().name}${if (chosen != null) "（手动）" else "（自动择优）"}")

        for ((idx, node) in queue.withIndex()) {
            onLog("下载镜像 [${idx + 1}/${queue.size}] ${node.name}")
            val ok = downloadWithRetry(node, dst, meta.size, onLog, onProgress)
            if (!ok) {
                onLog("节点 ${node.name} 下载失败，换下一个…")
                continue
            }
            //有权威 sha256 才校验；没有就只认大小（兜底清单的场景）
            if (meta.sha256.isNotBlank()) {
                val sum = sha256(dst)
                if (!sum.equals(meta.sha256, ignoreCase = true)) {
                    onLog("校验不通过：期望 ${meta.sha256.take(12)}…，实际 ${sum.take(12)}…，已删除重试")
                    runCatching { dst.delete() }
                    continue
                }
                onLog("sha256 校验通过")
            }
            onLog("镜像就绪：${dst.length() / 1024 / 1024} MB")
            return true
        }
        runCatching { dst.delete() }
        onLog("镜像下载全部失败")
        return false
    } //完整流程

    /** 外置导入提示路径，日志里会用到 */
    private const val EXT_HINT = "/sdcard/BaiAgent/linux/ubuntu.tar.gz"

    /**
     * 单节点下载：支持断点续传（目标已存在则带 Range），失败一次重试一次。
     * 目标写 .part 临时文件，完成后再改名，避免半成品被当成完整镜像。
     */
    private fun downloadWithRetry(
        node: Node,
        dst: File,
        expectSize: Long,
        onLog: (String) -> Unit,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        val part = File(dst.absolutePath + ".part")
        repeat(2) { attempt ->
            var conn: HttpURLConnection? = null
            try {
                val already = if (part.isFile) part.length() else 0L
                conn = (URL(node.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DL_CONNECT_TIMEOUT_MS
                    readTimeout = DL_READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                    if (already > 0) setRequestProperty("Range", "bytes=$already-")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    onLog("HTTP $code")
                    return false
                }
                val append = code == 206 && already > 0
                val total = when {
                    expectSize > 0 -> expectSize
                    conn.contentLengthLong > 0 -> conn.contentLengthLong + if (append) already else 0L
                    else -> -1L
                }
                onLog("开始下载${if (append) "（续传，已有 ${already / 1024} KB）" else ""}")
                var got = if (append) already else 0L
                conn.inputStream.use { ins ->
                    java.io.FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            got += n
                            onProgress(got, total)
                        }
                        out.flush()
                    } //写盘
                } //读网络
                if (expectSize > 0 && got < expectSize) {
                    onLog("下载不完整（$got / $expectSize），重试")
                    return@repeat
                }
                //收尾：把 .part 落到正式路径。rename 在少数机型/目录上会失败，
                //失败时退回复制；复制前先确认 .part 还在，否则只可能是「其实已经改名成功」
                if (part.isFile && !part.renameTo(dst)) {
                    runCatching { dst.delete() }
                    if (part.isFile) {
                        val copied = runCatching { part.copyTo(dst, overwrite = true).isFile }.getOrDefault(false)
                        if (!copied) {
                            onLog("写入目标文件失败")
                            return false
                        }
                    }
                    runCatching { part.delete() }
                }
                //最终以磁盘文件为准：大小不够就当这次没成，交给重试或下一个节点
                if (expectSize > 0 && (!dst.isFile || dst.length() < expectSize)) {
                    onLog("落盘校验不通过（${if (dst.isFile) dst.length() else 0} / $expectSize），重试")
                    runCatching { part.delete() }
                    return@repeat
                }
                return true
            } catch (e: Exception) {
                onLog("下载出错（第 ${attempt + 1} 次）：${e.javaClass.simpleName}: ${e.message ?: "-"}")
                if (e !is java.io.IOException) onLog(e.stackTrace.take(3).joinToString(" <- ") { it.toString() })
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
        return false
    } //单节点下载

    /** 流式计算 sha256，避免把 30MB 全读进内存 */
    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    } //sha256

    /** 当前是否处于「手动指定节点」状态：Cfg 里存节点 id，空串表示自动 */
    fun manualId(): String = Cfg.linuxMirrorNode.trim()
}//镜像源管理