/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:19
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.net.search
import b.ai.net.api.llm.text

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tavily 搜索：只用一个 POST /search，结果按"标题/链接/摘要"整理成纯文本给模型 */
object Tavily {
    private const val ENDPOINT = "https://api.tavily.com/search"

    /** 只抓正文：给一组 URL，返回每页纯文本 */
    private const val EXTRACT = "https://api.tavily.com/extract"

    /** 按站爬：从起始 URL 顺着链接抓 N 页 */
    private const val CRAWL = "https://api.tavily.com/crawl"

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
        val raw = runCatching { post(ENDPOINT, key, body.toString()) }.getOrElse { return "搜索失败：${it.message}" }
        return render(raw, query)
    } //执行一次搜索

    /**
     * 提取正文：一批 URL → 每页纯文本（raw_content 按 maxChars 截断）。
     * 只读一篇文章/文档用它，比 crawl 快；要顺着链接抓整站才用 crawl。
     */
    fun extract(
        key: String, urls: List<String>, depth: String = "basic", maxChars: Int = 4000
    ): String {
        if (key.isBlank()) return "未配置 Tavily API Key：请到 设置 → 功能专区 里填写后再试。"
        if (urls.isEmpty()) return "提取失败：没有给 urls。"
        val body = JSONObject()
            .put("api_key", key)
            .put("urls", org.json.JSONArray(urls.take(20)))
            .put("extract_depth", if (depth == "advanced") "advanced" else "basic")
        val raw = runCatching { post(EXTRACT, key, body.toString()) }.getOrElse { return "提取失败：${it.message}" }
        return renderPages(raw, maxChars, "提取")
    } //抓一批网页正文

    /**
     * 爬站：从 url 开始按 maxDepth 顺链接抓，最多 limit 页。
     * 站点地图式收集资料用；只要一页就别用它，extract 更省额度。
     */
    fun crawl(
        key: String, url: String, maxDepth: Int = 1, limit: Int = 10,
        depth: String = "basic", maxChars: Int = 3000
    ): String {
        if (key.isBlank()) return "未配置 Tavily API Key：请到 设置 → 功能专区 里填写后再试。"
        if (url.isBlank()) return "爬取失败：没有给 url。"
        val body = JSONObject()
            .put("api_key", key)
            .put("url", url)
            .put("max_depth", maxDepth.coerceIn(1, 5))
            .put("limit", limit.coerceIn(1, 50))
            .put("extract_depth", if (depth == "advanced") "advanced" else "basic")
        val raw = runCatching { post(CRAWL, key, body.toString()) }.getOrElse { return "爬取失败：${it.message}" }
        return renderPages(raw, maxChars, "爬取")
    } //按站爬取

    /** 提取/爬取共用的渲染：每页标题行 + 截断后的正文，失败条目单独列出来 */
    private fun renderPages(raw: String, maxChars: Int, tag: String): String {
        val o = runCatching { JSONObject(raw) }
            .getOrElse { return "$tag 失败：响应不是 JSON：${raw.take(300)}" }
        val cap = maxChars.coerceIn(200, 20_000)
        val sb = StringBuilder()
        val arr = o.optJSONArray("results")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val c = r.optString("raw_content").ifBlank { r.optString("content") }
                sb.append("\n[${i + 1}] ").append(r.optString("url")).append('\n')
                sb.append(if (c.isBlank()) "（没有正文）" else c.trim().take(cap)).append('\n')
            }
        }
        val bad = o.optJSONArray("failed_results")
        if (bad != null && bad.length() > 0) {
            sb.append("\n失败 ").append(bad.length()).append(" 条：")
            for (i in 0 until bad.length()) {
                val b = bad.optJSONObject(i) ?: continue
                sb.append(b.optString("url")).append("（")
                    .append(b.optString("error").ifBlank { "未知原因" }).append("） ")
            }
            sb.append('\n')
        }
        if (sb.isBlank()) return "$tag 失败：没拿到正文（可能被站点拦截，或该页需要登录）。"
        return "$tag 结果（${arr?.length() ?: 0} 页）：\n" + sb.toString().trim()
    } //提取/爬取渲染

    private fun post(endpoint: String, key: String, body: String): String {
        val c = URL(endpoint).openConnection() as HttpURLConnection
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