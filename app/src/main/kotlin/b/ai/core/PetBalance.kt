package b.ai.core

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

/** DeepSeek 开放平台余额查询 + 峰谷时段判断 */
object PetBalance {
    private const val URL_BALANCE = "https://api.deepseek.com/user/balance"
    private const val TIMEOUT_MS = 8000

    private val main = Handler(Looper.getMainLooper())

    /**
     * 是不是错峰时段。官方口径：北京时间每天 00:30–08:30 为错峰，API 打折；
     * 其余时间为标准时段（高峰）。
     */
    fun offPeak(now: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        val mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return mins in 30 until (8 * 60 + 30)
    } //峰谷判断

    /** 后台查余额，结果回主线程；失败也给一句能看的文案 */
    fun fetchAsync(apiKey: String, cb: (String) -> Unit) {
        if (apiKey.isBlank()) {
            main.post { cb("") }
            return
        }
        Thread {
            val text = runCatching { fetch(apiKey) }.getOrDefault("")
            main.post { cb(text) }
        }.apply { isDaemon = true }.start()
    } //异步查

    private fun fetch(apiKey: String): String {
        val conn = URL(URL_BALANCE).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
            val code = conn.responseCode
            if (code != 200) return "HTTP $code"
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val info = JSONObject(body).optJSONArray("balance_infos")?.optJSONObject(0) ?: return "无数据"
            val raw = info.optString("total_balance")
            val cur = info.optString("currency", "CNY")
            val num = raw.toDoubleOrNull() ?: return raw
            val text = String.format(Locale.ROOT, "%.2f", num)
            val unit = if (cur.equals("CNY", true)) "元" else " $cur"
            return text + unit
        } finally {
            conn.disconnect()
        }
    } //同步查
} //余额与峰谷