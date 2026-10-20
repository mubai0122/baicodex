package b.ai.net

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Google Custom Search JSON API：要 API Key + 搜索引擎 ID(cx)，结果在 items[] 里 */
object GoogleCse {
    private const val ENDPOINT = "https://www.googleapis.com/customsearch/v1"

    fun search(
        key: String,
        cx: String,
        query: String,
        maxResults: Int = 5,
        start: Int = 1
    ): String {
        if (key.isBlank()) return "未配置 Google API Key：请到 设置 → 扩展 → 搜索平台 里填写后再试。"
        if (cx.isBlank()) return "未配置 Google 搜索引擎 ID(cx)：请到 设置 → 扩展 → 搜索平台 里填写后再试。"
        if (query.isBlank()) return "搜索词为空。"
        val n = maxResults.coerceIn(1, 10)
        val s = start.coerceIn(1, 91)
        val url = "$ENDPOINT?key=" + URLEncoder.encode(key, "UTF-8") +
            "&cx=" + URLEncoder.encode(cx, "UTF-8") +
            "&q=" + URLEncoder.encode(query, "UTF-8") +
            "&num=$n&start=$s"
        val raw = runCatching { get(url) }.getOrElse { return "搜索失败：${it.message}" }
        return render(raw, query)
    } //执行一次搜索

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = "GET"
        c.setRequestProperty("Accept", "application/json")
        return try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            text
        } finally {
            c.disconnect()
        }
    } //GET 并统一错误文本

    private fun render(raw: String, query: String): String {
        val o = runCatching { JSONObject(raw) }.getOrElse { return "响应不是 JSON：${raw.take(300)}" }
        val err = o.optJSONObject("error")?.optString("message", "") ?: ""
        if (err.isNotBlank()) {
            return "搜索失败：$err（常见原因：Key 无效、cx 不对、该 API 未对账号开放）"
        }
        val arr = o.optJSONArray("items")
        if (arr == null || arr.length() == 0) return "没搜到结果：$query"
        val sb = StringBuilder("结果（$query）：\n")
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            sb.append("\n[${i + 1}] ").append(r.optString("title").ifBlank { "(无标题)" }).append("\n")
            sb.append(r.optString("link")).append("\n")
            val snippet = r.optString("snippet").replace("\n", " ").trim()
            if (snippet.isNotBlank()) sb.append(snippet.take(600)).append("\n")
        }
        return sb.toString().trim()
    } //整理成可读文本
} //Google 自定义搜索客户端
