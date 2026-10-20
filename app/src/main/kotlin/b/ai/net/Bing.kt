package b.ai.net

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 必应网页版 RSS：format=rss 返回标准 RSS 2.0，不需要 Key（微软官方 Search API 2025-08 已退役） */
object Bing {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun search(query: String, maxResults: Int = 5): String {
        if (query.isBlank()) return "搜索词为空。"
        val n = maxResults.coerceIn(1, 20)
        val url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") +
            "&format=rss&count=$n"
        val raw = runCatching { get(url) }.getOrElse { return "搜索失败：${it.message}" }
        return render(raw, query)
    } //执行一次搜索

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.requestMethod = "GET"
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "text/xml,application/xml,text/html;q=0.9,*/*;q=0.8")
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
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
        val items = runCatching { parse(raw) }.getOrElse { return "响应不是 RSS：${raw.take(300)}" }
        if (items.isEmpty()) return "没搜到结果：$query（必应 RSS 可能被限流，稍后再试）"
        val sb = StringBuilder("结果（$query）：\n")
        items.forEachIndexed { i, it ->
            sb.append("\n[${i + 1}] ").append(it.title.ifBlank { "(无标题)" }).append("\n")
            if (it.link.isNotBlank()) sb.append(it.link).append("\n")
            if (it.desc.isNotBlank()) sb.append(it.desc.take(600)).append("\n")
        }
        return sb.toString().trim()
    } //整理成可读文本

    private data class Item(val title: String, val link: String, val desc: String)

    private fun parse(raw: String): List<Item> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(StringReader(raw))
        val out = ArrayList<Item>()
        var title = ""
        var link = ""
        var desc = ""
        var inItem = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name.lowercase()) {
                    "item" -> {
                        inItem = true
                        title = ""
                        link = ""
                        desc = ""
                    }
                    "title" -> if (inItem) title = p.nextText()
                    "link" -> if (inItem) link = p.nextText()
                    "description" -> if (inItem) desc = clean(p.nextText())
                }
            } else if (event == XmlPullParser.END_TAG && p.name.equals("item", true)) {
                inItem = false
                if (title.isNotBlank() || link.isNotBlank()) out.add(Item(title.trim(), link.trim(), desc.trim()))
            }
            event = p.next()
        }
        return out
    } //解析 RSS item 列表

    /** RSS 摘要里带 HTML 标签与实体，去掉后再给模型 */
    private fun clean(s: String): String {
        val noTag = s.replace(Regex("<[^>]+>"), " ")
        val sb = StringBuilder(noTag.length)
        var i = 0
        while (i < noTag.length) {
            val c = noTag[i]
            if (c == '&') {
                val end = noTag.indexOf(';', i)
                if (end in (i + 1)..(i + 12)) {
                    val ent = noTag.substring(i + 1, end)
                    sb.append(
                        when (ent.lowercase()) {
                            "amp" -> '&'
                            "lt" -> '<'
                            "gt" -> '>'
                            "quot" -> '"'
                            "apos" -> '\''
                            "nbsp" -> ' '
                            else -> if (ent.startsWith("#x", true)) {
                                ent.drop(2).toIntOrNull(16)?.toChar() ?: ' '
                            } else {
                                ent.drop(1).takeIf { ent.startsWith("#") }?.toIntOrNull()?.toChar() ?: ' '
                            }
                        }
                    )
                    i = end + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    } //去标签与实体
} //必应 RSS 搜索
