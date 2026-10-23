package b.ai.net

import java.net.HttpURLConnection
import java.net.URL

/** C 层 HTTPS 客户端：图标这类小请求直接走 native，不再走 JVM 的 HttpURLConnection */
object Net {
    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    private external fun nativeGet(url: String, timeoutMs: Int): ByteArray?

    private external fun nativeErr(): String

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
}