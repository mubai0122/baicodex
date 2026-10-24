package b.ai.net

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 停机开关：服务端 api/get/isuse.php 返回 false 时应用侧拦下并退出；读不到一律按 true 放行 */
object Isuse {
    private const val ENDPOINT = "https://agent.ai.baiccb.cn/api/get/isuse.php"
    private const val TAG = "bai.isuse"

    /** 读开关：连 3 秒 / 读 3 秒，任何异常都当 true——服务端故障不能把所有人挡在门外 */
    fun on(): Boolean = runCatching {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 3000
        c.requestMethod = "GET"
        c.setRequestProperty("User-Agent", "BaiAgent-Isuse")
        val text = try {
            val http = c.responseCode
            if (http !in 200..299) error("HTTP $http")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        val body = JSONObject(text)
        val v = body.optJSONObject("data")?.optBoolean("isuse", true) ?: true
        Log.i(TAG, "停机开关 isuse=$v")
        v
    }.getOrElse {
        Log.w(TAG, "停机开关读取失败，按开启放行: ${it.message}")
        true
    } //读开关
} //停机开关
