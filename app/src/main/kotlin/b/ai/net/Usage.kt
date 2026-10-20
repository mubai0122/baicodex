package b.ai.net

import android.media.MediaDrm
import android.os.Build
import android.util.Log
import b.ai.App
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**启动上报：每次进程起来往服务端累加一次登录次数，deviceCode 用设备指纹哈希，不上传任何原始标识 */
object Usage {
    private const val ENDPOINT = "https://agent.ai.baiccb.cn/api/use/put.php"
    private const val TAG = "bai.usage"

    /** Widevine UUID：mediaDrmId 的来源 */
    private val WIDEVINE = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    /** 最终ID = SHA256(mediaDrmId + ":" + Build.MODEL + ":" + packageName) */
    fun deviceCode(): String {
        val raw = "${drmId()}:${Build.MODEL}:${App.self.packageName}"
        val d = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    } //设备码

    /** Widevine 的 deviceUniqueId：取不到就给空串（哈希结果照样稳定），任何异常都不外抛 */
    private fun drmId(): String = runCatching {
        val drm = MediaDrm(WIDEVINE)
        try {
            drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        } finally {
            runCatching { drm.release() }
        }
    }.getOrDefault("") //读 Widevine 设备ID

    /** 起一个后台线程上报一次：接口是累加语义，所以只发一次不重试，失败静默不打扰用户 */
    fun reportOnce() {
        Thread {
            runCatching { post(deviceCode(), 1) }
                .onSuccess { Log.i(TAG, "上报成功 $it") }
                .onFailure {
                    //409 = nonce 已被用过，说明上一次请求其实已经记上账了，按成功处理
                    if (it is Duplicate) Log.i(TAG, "上报已记过账，不重试：${it.message}")
                    else Log.w(TAG, "上报失败 ${it.message}")
                }
        }.apply { isDaemon = true }.start()
    } //上报一次

    /** 服务端判定重复请求：同一个 nonce 第二次出现，返回 409 */
    private class Duplicate(msg: String) : Exception(msg)

    /** 一次性随机串：16 字节随机数转 hex，32 位，落在服务端要求的 8-64 位 [A-Za-z0-9_-] 区间 */
    private fun nonce(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    } //一次性随机串

    private fun post(code: String, count: Int): String {
        //防重放三件套：timestamp 用秒（服务端 ±300 秒内有效），nonce 每次请求重新生成
        val body = JSONObject()
            .put("deviceCode", code)
            .put("count", count)
            .put("timestamp", System.currentTimeMillis() / 1000)
            .put("nonce", nonce())
            .toString()
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (http == 409) throw Duplicate(text.take(200))
            if (http !in 200..299) error("HTTP $http: ${text.take(200)}")
            text
        } finally {
            c.disconnect()
        }
    } //POST put.php
} //启动上报