package b.ai.net

import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 用户反馈提交：分类 + 内容 + 邮箱（必填），落服务器 feedback 表；失败把服务端原话抛给界面 */
object Feedback {
    private const val ENDPOINT = "https://agent.ai.baiccb.cn/api/get/feedback.php"

    /** 分类键，与服务器 feedback.php 校验的三个值一一对应 */
    const val BUG = "bug"
    const val IDEA = "idea"
    const val OTHER = "other"

    /** 同步提交，调用方自己切 IO 线程；成功返回记录 id，失败抛带服务端 msg 的异常 */
    fun submit(category: String, content: String, contact: String): Int {
        val body = JSONObject()
            .put("deviceCode", Usage.deviceCode())
            .put("category", category)
            .put("content", content.trim())
            .put("appVersion", Update.clientVersion())
            .put("android", Build.VERSION.RELEASE ?: "")
            .put("model", Build.MODEL ?: "")
        body.put("contact", contact.trim())
        val text = post(body.toString())
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: error("返回格式异常")
        if (json.optInt("code") != 200) error(json.optString("msg", "提交失败"))
        return json.optJSONObject("data")?.optInt("id", 0) ?: 0
    } //提交反馈

    private fun post(payload: String): String {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (text.isBlank()) error("HTTP $http")
            text
        } finally {
            c.disconnect()
        }
    } //POST feedback.php
} //用户反馈提交