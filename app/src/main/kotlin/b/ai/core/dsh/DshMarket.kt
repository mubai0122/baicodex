/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：dsh 插件的两个「广场」数据源（只取元数据，不落盘、不安装）。
 *  ① npm registry 搜索（keywords:dsh-plugin）—— 社区插件生态，无需任何配置；
 *  ② 用户自填的 market index URL —— dsh-plugin-market 的 format 1 索引格式。
 *
 * 事实来源（源码取证）：
 *  - dsh-plugin-market/README.md 原文：No index source is configured by default,
 *    所以官方没有内置公共索引地址，只能由用户自己填；配置项 indexes 默认 []。
 *  - format 1 校验器在 @deepseek-ai/dsh-plugin-market-index：
 *    { format: 1, title: <localized>, plugins: [ { name, title?, description?,
 *      versions: [ { version, publishTimestamp, source: { type:'npm'|'git', ... } } ] } ] }
 *    其中 localized 可以是字符串，也可以是 { en: "...", zh: "..." }。
 *
 * 本文件不做任何安装动作，安装见 [DshInstall]。
 */
package b.ai.core.dsh

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 广场里的一条插件（只保留 UI 需要的字段） */
data class MarketItem(
    val name: String,              //npm 包名，安装时直接用它
    val version: String,           //最新可用版本（拿不到就空）
    val title: String,             //展示标题
    val desc: String,              //一句话说明
    val author: String,            //作者 / publisher
    val source: String,            //数据来源标记：npm / index:<host>
    val keywords: List<String> = emptyList()
) //广场条目

object DshMarket {

    /** 官方仅提供格式，不提供默认索引；这里预置一个「社区索引」的采集口径，留空表示只用 npm */
    const val PREF = "dsh_market"
    const val KEY_INDEXES = "indexes"

    /** npm 搜索接口（keywords:dsh-plugin 是社区约定，实测能搜到真实生态） */
    private const val NPM_SEARCH = "https://registry.npmjs.org/-/v1/search?text=%s&size=%d"

    /** 默认按社区约定的关键词搜索 */
    const val DEFAULT_QUERY = "keywords:dsh-plugin"

    /**
     * 搜 npm registry。
     * @param query 搜索词，空则用 keywords:dsh-plugin
     * @return 失败时带可读原因（网络/解析）
     */
    fun searchNpm(query: String = DEFAULT_QUERY, size: Int = 20): Result<List<MarketItem>> = runCatching {
        val q = query.trim().ifBlank { DEFAULT_QUERY }
        val url = NPM_SEARCH.format(URLEncoder.encode(q, "UTF-8").replace("%3A", ":"), size.coerceIn(1, 50))
        val text = getText(url)
        val root = JSONObject(text)
        val objs = root.optJSONArray("objects") ?: throw IllegalStateException("npm 返回里没有 objects")
        val out = ArrayList<MarketItem>(objs.length())
        for (i in 0 until objs.length()) {
            val pkg = objs.optJSONObject(i)?.optJSONObject("package") ?: continue
            val name = pkg.optString("name").trim()
            if (name.isBlank()) continue
            out.add(
                MarketItem(
                    name = name,
                    version = pkg.optString("version"),
                    title = name,
                    desc = pkg.optString("description").trim(),
                    author = pkg.optJSONObject("publisher")?.optString("username").orEmpty(),
                    source = "npm",
                    keywords = pkg.optJSONArray("keywords")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                    } ?: emptyList()
                )
            )
        }
        out
    } //npm 搜索

    /**
     * 读取一个用户自填的 format 1 索引。
     * 宽松解析：只认 name / title / description / versions，其余字段（icon、metadata、labels）
     * 一律忽略——索引里很多元数据是给 dsh 自家 UI 用的，我们不吃。
     */
    fun fetchIndex(indexUrl: String): Result<List<MarketItem>> = runCatching {
        val u = indexUrl.trim()
        if (u.isBlank()) throw IllegalArgumentException("索引地址为空")
        val root = JSONObject(getText(u))
        //格式校验只做最低限度：format 必须为 1（其它数字是未来格式，直接拒绝）
        val format = root.optInt("format", -1)
        if (format != 1) throw IllegalStateException("不是 format 1 索引（format=$format）")
        val plugins = root.optJSONArray("plugins") ?: throw IllegalStateException("索引里没有 plugins 数组")
        val host = runCatching { URL(u).host }.getOrDefault(u)
        val out = ArrayList<MarketItem>(plugins.length())
        for (i in 0 until plugins.length()) {
            val p = plugins.optJSONObject(i) ?: continue
            val name = p.optString("name").trim()
            if (name.isBlank()) continue
            //版本：取 publishTimestamp 最大的那条；没有时间戳就取第一条（validator 已按 SemVer 降序，服务端原始文件未必）
            val versions = p.optJSONArray("versions")
            var best: JSONObject? = null
            var bestTs = Long.MIN_VALUE
            if (versions != null) {
                for (j in 0 until versions.length()) {
                    val v = versions.optJSONObject(j) ?: continue
                    val ts = v.optLong("publishTimestamp", 0L)
                    if (best == null || ts > bestTs) {
                        best = v
                        bestTs = ts
                    }
                }
            }
            out.add(
                MarketItem(
                    name = name,
                    version = best?.optString("version").orEmpty(),
                    title = localized(p.opt("title")) ?: name,
                    desc = localized(p.opt("description")).orEmpty(),
                    author = p.optString("author").orEmpty(),
                    source = "index:$host"
                )
            )
        }
        out
    } //读索引

    /** dsh 的 localized 文本：字符串或 { en: .., zh: .. } */
    private fun localized(v: Any?): String? = when (v) {
        is String -> v
        is JSONObject -> v.optString("zh").ifBlank { v.optString("en") }.ifBlank { null }
        else -> null
    } //取本地化文本

    /** 最小 HTTP GET：不开压缩、不跟随奇怪跳转，超时给足（registry 偶发慢） */
    private fun getText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Baicodex-PluginMarket/1.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code：${text.take(160)}")
            return text
        } finally {
            conn.disconnect()
        }
    } //GET 文本
} //dsh 插件广场数据源