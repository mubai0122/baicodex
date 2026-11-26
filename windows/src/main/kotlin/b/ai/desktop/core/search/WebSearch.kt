/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 06:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 搜索平台注册表（对齐手机端 net/search/Search）：tavily / bing / google。
 * 平台、且该填的配置都填了，web_search 工具才挂进工具表。
 * 加平台在这里加一行，再在 dispatch 里加一个分支。
 */

package b.ai.desktop.core.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 一个平台需要的配置项；bing 这种免 Key 的平台就是空表 */
data class SearchField(val id: String, val label: String, val hint: String)

/** 一个搜索平台；id 与 [dispatch] 里的实现一一对应 */
data class SearchProvider(
    val id: String,
    val label: String,
    val hint: String,
    val fields: List<SearchField>,
)

object WebSearch {

    /** 平台清单：加平台在这里加一行，再在 dispatch 里加一个分支 */
    val providers: List<SearchProvider> = listOf(
        SearchProvider(
            id = "tavily",
            label = "Tavily",
            hint = "官方 API，每月 1000 次免费额度，返回带摘要的结果。",
            fields = listOf(SearchField("key", "API Key", "tvly-…（tavily.com 免费注册）")),
        ),
        SearchProvider(
            id = "bing",
            label = "Bing",
            hint = "免 Key：走必应网页版 RSS 接口。微软官方 Search API 已于 2025-08 退役，" +
                "这里按 RSS 聚合读取，可能被限流，适合个人低频使用。",
            fields = emptyList(),
        ),
        SearchProvider(
            id = "google",
            label = "Google",
            hint = "Custom Search JSON API：要 API Key + 搜索引擎 ID(cx)。" +
                "注意 Google 已对该 API 关闭新客户注册，存量 Key 可用到 2027-01-01。",
            fields = listOf(
                SearchField("key", "API Key", "AIza…"),
                SearchField("cx", "搜索引擎 ID (cx)", "012345678901234567890:abcdefghij"),
            ),
        ),
    )

    fun byId(id: String): SearchProvider? = providers.firstOrNull { it.id == id }

    /** 第一个没填的配置项，用来拼提示；null = 配置齐了 */
    fun missing(provider: SearchProvider, cfg: (String) -> String): SearchField? =
        provider.fields.firstOrNull { cfg(it.id).isBlank() }

    /**
     * 派发一次搜索。cfg(fieldId) 取平台配置（Tavily 的 key / Google 的 key、cx）。
     * topic / days / depth 只有 Tavily 认，其他平台忽略。
     */
    fun search(
        provider: SearchProvider,
        cfg: (String) -> String,
        query: String,
        maxResults: Int = 5,
        topic: String = "general",
        days: Int? = null,
        depth: String = "basic",
    ): String {
        if (query.isBlank()) return "搜索词为空。"
        return when (provider.id) {
            "bing" -> bing(query, maxResults)
            "google" -> googleCse(cfg("key"), cfg("cx"), query, maxResults)
            else -> tavily(cfg("key"), query, maxResults, topic, days, depth)
        }
    }

    // ---------------------------------------------------------------- Tavily

    private fun tavily(key: String, query: String, maxResults: Int, topic: String, days: Int?, depth: String): String {
        if (key.isBlank()) return "未配置 Tavily Key：设置 → 扩展 → 联网搜索 里填写后再试。"
        val body = buildString {
            append("""{"api_key":"${jsonEscape(key)}","query":"${jsonEscape(query)}"""")
            append(""","topic":"${if (topic == "news") "news" else "general"}","max_results":${maxResults.coerceIn(1, 20)},"include_answer":true""")
            if (depth == "advanced") append(""","search_depth":"advanced"""")
            if (topic == "news" && days != null && days > 0) append(""","days":$days""")
            append("}")
        }
        val raw = postJson("https://api.tavily.com/search", body)
            .getOrElse { return "搜索失败：${it.message}" }
        val o = runCatching { Json.parseToJsonElement(raw).jsonObject }
            .getOrElse { return "响应不是 JSON：${raw.take(300)}" }
        val answer = o["answer"]?.jsonPrimitive?.content.orEmpty()
        val results = o["results"]?.jsonArray.orEmpty().joinToString("\n\n") { item ->
            val r = item.jsonObject
            "- ${r["title"]?.jsonPrimitive?.content}\n  ${r["url"]?.jsonPrimitive?.content}\n  ${r["content"]?.jsonPrimitive?.content?.take(300)}"
        }
        return buildString {
            if (answer.isNotBlank()) appendLine("摘要：$answer\n")
            if (results.isNotBlank()) append("结果：\n$results")
        }.ifBlank { "没搜到结果：$query" }
    }

    // ---------------------------------------------------------------- Bing（RSS，免 Key）

    private const val BING_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun bing(query: String, maxResults: Int): String {
        val n = maxResults.coerceIn(1, 20)
        val url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&format=rss&count=$n"
        val raw = get(url, mapOf("User-Agent" to BING_UA, "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"))
            .getOrElse { return "搜索失败：${it.message}" }
        val items = runCatching { parseRss(raw) }.getOrElse { return "响应不是 RSS：${raw.take(300)}" }
        if (items.isEmpty()) return "没搜到结果：$query（必应 RSS 可能被限流，稍后再试）"
        return buildString {
            appendLine("结果（$query）：")
            items.forEachIndexed { i, (title, link, desc) ->
                append("\n[${i + 1}] ").appendLine(title.ifBlank { "(无标题)" })
                if (link.isNotBlank()) appendLine(link)
                if (desc.isNotBlank()) appendLine(desc.take(600))
            }
        }.trim()
    }

    /** 极简 RSS 解析：抽 <item> 里的 title / link / description，够用不引 XML 库 */
    private fun parseRss(raw: String): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        val itemRe = Regex("<item[\\s\\S]*?</item>", RegexOption.IGNORE_CASE)
        val tagRe = Regex("<(title|link|description)[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
        itemRe.findAll(raw).take(20).forEach { m ->
            val fields = tagRe.findAll(m.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val title = xmlClean(fields["title"].orEmpty())
            val link = xmlClean(fields["link"].orEmpty())
            val desc = xmlClean(fields["description"].orEmpty())
            if (title.isNotBlank() || link.isNotBlank()) out.add(Triple(title, link, desc))
        }
        return out
    }

    /** RSS 摘要里带 HTML 标签与实体，去掉后再给模型 */
    private fun xmlClean(s: String): String {
        val noCdata = s.replace(Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>")) { it.groupValues[1] }
        val noTag = noCdata.replace(Regex("<[^>]+>"), " ")
        val decoded = noTag
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&nbsp;", " ")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: " " }
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " " }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }

    // ---------------------------------------------------------------- Google CSE

    private fun googleCse(key: String, cx: String, query: String, maxResults: Int): String {
        if (key.isBlank()) return "未配置 Google API Key：设置 → 扩展 → 联网搜索 里填写后再试。"
        if (cx.isBlank()) return "未配置 Google 搜索引擎 ID(cx)：设置 → 扩展 → 联网搜索 里填写后再试。"
        val n = maxResults.coerceIn(1, 10)
        val url = "https://www.googleapis.com/customsearch/v1?key=" + URLEncoder.encode(key, "UTF-8") +
            "&cx=" + URLEncoder.encode(cx, "UTF-8") +
            "&q=" + URLEncoder.encode(query, "UTF-8") + "&num=$n"
        val raw = get(url, mapOf("Accept" to "application/json")).getOrElse { return "搜索失败：${it.message}" }
        val o = runCatching { Json.parseToJsonElement(raw).jsonObject }
            .getOrElse { return "响应不是 JSON：${raw.take(300)}" }
        val err = o["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content.orEmpty()
        if (err.isNotBlank()) return "搜索失败：$err（常见原因：Key 无效、cx 不对、该 API 未对账号开放）"
        val items = o["items"]?.jsonArray.orEmpty()
        if (items.isEmpty()) return "没搜到结果：$query"
        return buildString {
            appendLine("结果（$query）：")
            items.forEachIndexed { i, item ->
                val r = item.jsonObject
                append("\n[${i + 1}] ").appendLine(r["title"]?.jsonPrimitive?.content?.ifBlank { "(无标题)" } ?: "(无标题)")
                appendLine(r["link"]?.jsonPrimitive?.content.orEmpty())
                val snippet = r["snippet"]?.jsonPrimitive?.content?.replace("\n", " ")?.trim().orEmpty()
                if (snippet.isNotBlank()) appendLine(snippet.take(600))
            }
        }.trim()
    }

    // ---------------------------------------------------------------- HTTP 与小工具

    private fun get(url: String, headers: Map<String, String>): Result<String> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, body: String): Result<String> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(300)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun jsonEscape(s: String): String = s
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
