package b.ai.native.net

import java.net.HttpURLConnection
import java.net.URL

/** C 层 HTTPS 客户端：图标这类小请求直接走 native，不再走 JVM 的 HttpURLConnection */
object Net {
    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    private external fun nativeGet(url: String, timeoutMs: Int): ByteArray?

    private external fun nativeErr(): String
    private external fun nativePing(host: String, port: Int, count: Int, timeoutMs: Int): String
    private external fun nativeCurl(
        method: String, url: String, headers: String, body: String, timeoutMs: Int, maxBody: Int
    ): String
    private external fun nativePage(url: String, timeoutMs: Int, maxChars: Int): String

    /** C 层 GET：非 2xx 或失败都返回 null，原因见 lastErr() */
    fun get(url: String, timeoutMs: Int = 6000): ByteArray? {
        if (!loaded) return null
        return runCatching { nativeGet(url, timeoutMs) }.getOrNull()
    } //native GET

    fun lastErr(): String =
        if (!loaded) "native not loaded" else runCatching { nativeErr() }.getOrNull().orEmpty() //上次失败原因

    /** JVM 兜底：C 层没编进去、或系统 CA 目录读不到时还能拿图 */
    fun jvmGet(url: String, timeoutMs: Int = 6000): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BaiAgent")
        c.setRequestProperty("Accept-Encoding", "identity")
        try {
            if (c.responseCode !in 200..299) return@runCatching null
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull() //JVM兜底

    /** 先 native 再 JVM：两条通道都不通才返回 null */
    fun fetch(url: String, timeoutMs: Int = 6000): ByteArray? =
        get(url, timeoutMs) ?: jvmGet(url, timeoutMs) //顺序取字节

    /** 测延迟：native TCP 建连，普通权限也能跑，不依赖 ping 命令 */
    fun ping(host: String, port: Int = 443, count: Int = 3, timeoutMs: Int = 3000): String =
        if (!loaded) "失败：native 没加载" else runCatching {
            nativePing(host, port, count, timeoutMs)
        }.getOrElse { "失败：" + it.message }

    /** 任意方法发一次请求：原样返回状态码、响应头与正文 */
    fun curl(
        method: String, url: String, headers: String = "", body: String = "",
        timeoutMs: Int = 15000, maxBody: Int = 1024 * 1024
    ): String =
        if (!loaded) "失败：native 没加载" else runCatching {
            nativeCurl(method, url, headers, body, timeoutMs, maxBody)
        }.getOrElse { "失败：" + it.message }

    /** 抓网页取正文：native 一次拿完 HTML 再去标签，只回纯文本 */
    fun page(url: String, timeoutMs: Int = 15000, maxChars: Int = 20000): String =
        if (!loaded) "失败：native 没加载" else runCatching {
            nativePage(url, timeoutMs, maxChars)
        }.getOrElse { "失败：" + it.message }
}