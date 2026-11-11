package b.ai.core.data.log

import b.ai.core.cfg.io.Paths
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import android.util.Base64

/**
 * 日志仓：外部存储 baicode/logs 下按天一个文件，内容用自己定的一套流密码加密。
 * 目的是「拷到电脑上也读不出明文」，不是拿来存机密的；七天前的文件自动清掉。
 * 现在还没有埋点接入，只是把底座备好：以后 debug 时直接调 d / i / w / e，读回明文用 read(day)。
 *
 * 格式：一条日志一行，行内容 = base64(随机盐 8 字节 + 密文)。
 * 密文 = 明文第 n 字节 ⊕ SHA-256(种子 ‖ 盐 ‖ 块号)[n % 32]，再做一次 rol3 位轮转。
 */
object Logs {
    /** 保留天数：写盘时顺带删掉更早的文件 */
    const val KEEP = 7
    /** 日志子目录名，落在 Paths 根下 → /sdcard/baicode/logs */
    const val SUBDIR = "logs"
    /** 密钥种子：与加密库、备份密钥都不共用，日志丢了不影响用户数据 */
    private const val SEED = "bai.log.vault.2026"
    /** 单条上限：一条巨大堆栈不能把文件撑爆 */
    private const val MAX_LINE = 8 * 1024
    /** 盐长度 */
    private const val SALT = 8
    /** 当前有多少条排队上盘：积压时直接丢新日志，绝不反压业务线程 */
    private const val CAP = 256

    private val rnd = SecureRandom()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 单线程上盘：日志永远不在调用线程做 IO，也就不可能卡住界面或网络回调 */
    private val io = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(CAP),
        { r -> Thread(r, "bai-log").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY } },
        ThreadPoolExecutor.DiscardPolicy()
    )
    /** 上一次清扫的日期：天天都扫目录没必要，换天才清一次 */
    @Volatile
    private var purgedOn = ""

    /** 目录：外部存储没挂上时 Paths 会退回私有目录，这里跟着走同一套兜底 */
    fun root(): File = Paths.dir(SUBDIR)

    /** 启动自检：建目录 + 清过期；放在 boot-init 线程里跑，不占首帧 */
    fun start() {
        runCatching { io.execute { purge() } }
    } //启动自检

    fun d(tag: String, msg: String) = put("D", tag, msg)
    fun i(tag: String, msg: String) = put("I", tag, msg)
    fun w(tag: String, msg: String) = put("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        put("E", tag, if (t == null) msg else msg + "\n" + t.stackTraceToString())

    /** 入队：调用线程只做一次字符串拼接，超过上限的行先截断 */
    private fun put(level: String, tag: String, msg: String) {
        val t = System.currentTimeMillis()
        val body = if (msg.length > MAX_LINE) msg.take(MAX_LINE) + "…(截断)" else msg
        val line = "${fmt.format(Date(t))} $level/$tag: $body"
        io.execute { append(t, line) }
    } //入队

    /** 落盘：按当天日期追加，换天时顺带清扫 */
    private fun append(t: Long, line: String) {
        val newDay = dayFmt.format(Date(t))
        if (newDay != purgedOn) {
            purgedOn = newDay
            purge()
        }
        runCatching {
            File(root(), "$newDay.log").appendText(encode(line) + "\n")
        }
    } //落盘

    /** 删掉超过保留天数的文件：日期在文件名里，字符串比较就够 */
    fun purge() {
        val edge = dayFmt.format(Date(System.currentTimeMillis() - (KEEP - 1) * 86_400_000L))
        root().listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".log")) return@forEach
            val d = f.name.removeSuffix(".log")
            if (d.length == 10 && d < edge) runCatching { f.delete() }
        }
    } //清理过期

    /** 现有日志的日期，旧到新 */
    fun days(): List<String> = root().listFiles()
        ?.filter { it.isFile && it.name.endsWith(".log") }
        ?.map { it.name.removeSuffix(".log") }
        ?.sorted()
        ?: emptyList()

    /** 读回某天的明文（调试用）；文件读不了就给空串，不抛 */
    fun read(day: String): String {
        val f = File(root(), "$day.log")
        if (!f.isFile) return ""
        val sb = StringBuilder()
        runCatching { f.forEachLine { l -> decode(l)?.let { sb.append(it).append('\n') } } }
        return sb.toString()
    } //读明文

    /** 清空全部日志（界面上要清缓存时用） */
    fun wipe(): Int {
        var n = 0
        root().listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".log") && runCatching { f.delete() }.getOrDefault(false)) n++
        }
        purgedOn = ""
        return n
    } //清空全部

    /** 一条日志 → 一行 base64：盐是每条随机的，同样的内容密文也不一样 */
    private fun encode(line: String): String {
        val raw = line.toByteArray(Charsets.UTF_8)
        val salt = ByteArray(SALT).also { rnd.nextBytes(it) }
        val out = ByteArray(SALT + raw.size)
        System.arraycopy(salt, 0, out, 0, SALT)
        var i = 0
        var blk = 0
        while (i < raw.size) {
            val ks = keyStream(salt, blk++)
            var j = 0
            while (j < 32 && i < raw.size) {
                out[SALT + i] = rol3((raw[i].toInt() and 0xFF) xor (ks[j].toInt() and 0xFF)).toByte()
                i++
                j++
            }
        }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    } //加密一行

    /** 一行 base64 → 明文：格式不对就返回 null，不让一条坏行进不去也就别人读不了别的行 */
    private fun decode(s: String): String? {
        if (s.isBlank()) return null
        val all = runCatching { Base64.decode(s, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (all.size <= SALT) return null
        val salt = all.copyOfRange(0, SALT)
        val n = all.size - SALT
        val out = ByteArray(n)
        var i = 0
        var blk = 0
        while (i < n) {
            val ks = keyStream(salt, blk++)
            var j = 0
            while (j < 32 && i < n) {
                val p = ror3(all[SALT + i].toInt() and 0xFF)
                out[i] = (p xor (ks[j].toInt() and 0xFF)).toByte()
                i++
                j++
            }
        }
        return runCatching { String(out, Charsets.UTF_8) }.getOrNull()
    } //解密一行

    /** 8 位内左旋 3 位：不能直接用 Integer.rotateLeft，它按 32 位轮转，跨过 toByte 的截断就解不回来 */
    private fun rol3(v: Int): Int = ((v shl 3) or (v ushr 5)) and 0xFF

    /** 8 位内右旋 3 位 */
    private fun ror3(v: Int): Int = ((v ushr 3) or (v shl 5)) and 0xFF

    /** 密钥流：SHA-256(种子 ‖ 盐 ‖ 块号)，32 字节一块 */
    private fun keyStream(salt: ByteArray, index: Int): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(SEED.toByteArray(Charsets.UTF_8))
        md.update(salt)
        md.update(
            byteArrayOf(
                (index ushr 24).toByte(),
                (index ushr 16).toByte(),
                (index ushr 8).toByte(),
                index.toByte()
            )
        )
        return md.digest()
    } //密钥流
} //日志仓