package b.ai.net

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tavily 搜索：只用一个 POST /search，结果按"标题/链接/摘要"整理成纯文本给模型 */
object Tavily {
    private const val ENDPOINT = "https://api.tavily.com/search"

    fun search(
        key: String,
        query: String,
        maxResults: Int = 5,
        depth: String = "basic",
        topic: String = "general",
        days: Int? = null
    ): String {
        if (key.isBlank()) return "未配置 Tavily API Key：请到 设置 → 功能专区 里填写后再试。"
        if (query.isBlank()) return "搜索词为空。"
        val body = JSONObject()
            .put("api_key", key)
            .put("query", query)
            .put("search_depth", if (depth == "advanced") "advanced" else "basic")
            .put("topic", if (topic == "news") "news" else "general")
            .put("max_results", maxResults.coerceIn(1, 10))
            .put("include_answer", true)
        if (topic == "news" && days != null) body.put("days", days.coerceIn(1, 30))
        val raw = runCatching { post(key, body.toString()) }.getOrElse { return "搜索失败：${it.message}" }
        return render(raw, query)
    } //执行一次搜索

    private fun post(key: String, body: String): String {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 45_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("Authorization", "Bearer $key")
        return try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            text
        } finally {
            c.disconnect()
        }
    } //POST 并统一错误文本

    private fun render(raw: String, query: String): String {
        val o = runCatching { JSONObject(raw) }.getOrElse { return "响应不是 JSON：${raw.take(300)}" }
        val sb = StringBuilder()
        val answer = o.optString("answer", "")
        if (answer.isNotBlank()) sb.append("摘要回答：").append(answer).append("\n\n")
        val arr = o.optJSONArray("results")
        if (arr == null || arr.length() == 0) {
            if (sb.isEmpty()) return "没搜到结果：$query"
            return sb.toString().trim()
        }
        sb.append("结果（$query）：\n")
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            sb.append("\n[${i + 1}] ").append(r.optString("title").ifBlank { "(无标题)" }).append("\n")
            sb.append(r.optString("url")).append("\n")
            val content = r.optString("content").replace("\n", " ").trim()
            if (content.isNotBlank()) sb.append(content.take(600)).append("\n")
        }
        return sb.toString().trim()
    } //整理成可读文本
} //Tavily 搜索客户端