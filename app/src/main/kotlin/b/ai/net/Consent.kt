package b.ai.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import b.ai.core.Cfg
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 协议同意上报：设备码 + 协议版本 + 同意时间，落服务器 consent_log；失败静默，不挡用户进门 */
object Consent {
    private const val ENDPOINT = "https://agent.ai.baiccb.cn/api/get/consent.php"
    private const val TAG = "bai.consent"

    /** 上报一次同意：同一版本已经上报过就跳过，不重复打点 */
    fun reportIfNeeded() {
        val v = Cfg.tosAgreed
        if (v <= 0 || Cfg.tosSent >= v) return
        Thread {
            runCatching { post(v) }
                .onSuccess {
                    //Cfg 的状态镜像要在主线程写，后台线程只负责网络
                    Handler(Looper.getMainLooper()).post {
                        Cfg.tosSent = v
                        Log.i(TAG, "同意已上报 版本=$v")
                    }
                }
                .onFailure { Log.w(TAG, "同意上报失败 ${it.message}") }
        }.apply { isDaemon = true }.start()
    } //上报同意

    private fun post(v: Int): String {
        val body = JSONObject()
            .put("deviceCode", Usage.deviceCode())
            .put("agreed", true)
            .put("tosVersion", "1.$v")
            .toString()
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (http !in 200..299) error("HTTP $http: ${text.take(200)}")
            text
        } finally {
            c.disconnect()
        }
    } //POST consent.php
} //协议同意上报