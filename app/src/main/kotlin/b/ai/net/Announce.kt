package b.ai.net

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
/** 公告：服务端 Announce.json 下发一张卡片，字段固定为标题 / 副标题 / 正文 / 按钮 / 日期 / 是否每次展示 */
object Announce {
    private const val BASE = "https://agent.ai.baiccb.cn/api/announce"
    private const val TAG = "bai.announce"
    /** 一个按钮：只允许跳浏览器，别的动作一概不支持 */
    data class Btn(val text: String, val url: String)
    data class Item(
        val id: String,
        val title: String,
        val subtitle: String,
        val body: String,
        val buttons: List<Btn>,
        val date: String,
        val always: Boolean
    )
    fun check(): Item? {
        val conn = (URL("$BASE/get.php").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (root.optInt("code") != 200) return null
            val d = root.optJSONObject("data") ?: return null
            val id = d.optString("id").trim()
            val title = d.optString("title").trim()
            if (id.isEmpty() || title.isEmpty()) return null
            //按钮只认 http/https，其它 scheme 直接丢掉，避免被当成外部动作
            val btns = ArrayList<Btn>()
            val arr = d.optJSONArray("buttons")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val t = o.optString("text").trim()
                    val u = o.optString("url").trim()
                    if (t.isNotEmpty() && (u.startsWith("http://") || u.startsWith("https://"))) btns.add(Btn(t, u))
                }
            }
            return Item(
                id = id,
                title = title,
                subtitle = d.optString("subtitle").trim(),
                body = d.optString("body"),
                buttons = btns,
                date = d.optString("date").trim(),
                always = d.optBoolean("always", false)
            )
        } catch (e: Exception) {
            Log.w(TAG, "公告拉取失败: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    } //拉一次公告，失败一声不响
}