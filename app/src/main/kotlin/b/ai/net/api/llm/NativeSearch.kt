// 原生 search 注入层：勾选「模型支持原生 search」后，按厂商在请求体里声明各家自己的联网工具。
// 这些工具都由服务端执行并把结果注入上下文，客户端不跑任何东西，只负责把返回的引用解析出来。
package b.ai.net.api.llm

import org.json.JSONArray
import org.json.JSONObject

object NativeSearch {
    /** 各厂商自带联网工具的声明方式，直接拼进 Chat Completions 请求体 */
    enum class Kind { OPENAI, DEEPSEEK, MOONSHOT, QWEN, ZHIPU, NONE }

    /** 按 providerId / base_url 判定走哪套声明方式 */
    fun kindOf(providerId: String, baseUrl: String): Kind {
        val p = providerId.lowercase()
        val b = baseUrl.lowercase()
        return when {
            p.contains("deepseek") || b.contains("deepseek") -> Kind.DEEPSEEK
            p.contains("moonshot") || b.contains("moonshot") || b.contains("kimi") -> Kind.MOONSHOT
            p.contains("dashscope") || b.contains("dashscope") || b.contains("aliyuncs") -> Kind.QWEN
            p.contains("zhipu") || b.contains("bigmodel") -> Kind.ZHIPU
            p.contains("openai") || b.contains("openai.com") -> Kind.OPENAI
            else -> Kind.NONE
        }
    } //厂商判定

    /** 该厂商是否支持原生 search（未知厂商一律 false，宁可不下发也不要打 400） */
    fun supported(providerId: String, baseUrl: String): Boolean = kindOf(providerId, baseUrl) != Kind.NONE

    /**
     * 把原生搜索声明塞进请求体。
     * 注意顺序：必须在普通 tools 已经 put 完之后调用，这里只做「追加/合并」，不覆盖已有 tools。
     */
    fun inject(body: JSONObject, providerId: String, baseUrl: String) {
        if (!b.ai.core.cfg.app.Cfg.modelNativeSearch) return
        when (kindOf(providerId, baseUrl)) {
            //OpenAI：Chat Completions 用顶层 web_search_options，size 取默认
            Kind.OPENAI -> body.put(
                "web_search_options",
                JSONObject().put("search_context_size", "medium")
            )
            //DeepSeek：两个服务端工具，搜索 + 读链接；模型据此自助决定搜不搜
            Kind.DEEPSEEK -> {
                addTool(body, JSONObject().put("type", "deepseek_web_search"))
                addTool(body, JSONObject().put("type", "deepseek_read_url"))
            }
            //Kimi / Moonshot：内置工具函数，名字固定 $web_search，不需要参数说明
            Kind.MOONSHOT -> addTool(
                body,
                JSONObject()
                    .put("type", "builtin_function")
                    .put("function", JSONObject().put("name", "\$web_search"))
            )
            //通义千问（百炼兼容模式）：一个布尔开关，服务端自己检索
            Kind.QWEN -> body.put("enable_search", true)
            //智谱：web_search 工具对象，enable 打开，结果回填给模型
            Kind.ZHIPU -> addTool(
                body,
                JSONObject()
                    .put("type", "web_search")
                    .put(
                        "web_search",
                        JSONObject()
                            .put("enable", true)
                            .put("search_result", true)
                    )
            )
            Kind.NONE -> Unit
        }
    } //注入原生搜索声明

    /** 往 tools 数组里追加一条；没有 tools 就先建一个 */
    private fun addTool(body: JSONObject, tool: JSONObject) {
        val arr = body.optJSONArray("tools") ?: JSONArray().also { body.put("tools", it) }
        //去重：同一轮里工具声明不能重复，有的厂商会直接 400
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val same = o.optString("type") == tool.optString("type") &&
                o.optJSONObject("function")?.optString("name").orEmpty() ==
                tool.optJSONObject("function")?.optString("name").orEmpty()
            if (same) return
        }
        arr.put(tool)
    } //追加工具

    /**
     * 从响应 message 里解析原生搜索带回来的引用，拼成一段 Markdown 追加到正文末尾。
     * 各家字段不统一：OpenAI 走 annotations，DeepSeek / 智谱可能给 sources / citations。
     * 解析不到就原样返回，绝不影响正文。
     */
    fun withSources(content: String, msg: JSONObject): String {
        val lines = ArrayList<String>()
        collect(msg.optJSONArray("annotations"), lines)
        collect(msg.optJSONArray("sources"), lines)
        collect(msg.optJSONArray("citations"), lines)
        if (lines.isEmpty()) return content
        val uniq = lines.distinct().take(8)
        val sb = StringBuilder(content.trimEnd())
        sb.append("\n\n**参考来源**\n")
        uniq.forEach { sb.append("- ").append(it).append('\n') }
        return sb.toString()
    } //附上引用

    /** 从数组里抠出 url + title，统一成 markdown 链接行 */
    private fun collect(arr: JSONArray?, out: MutableList<String>) {
        if (arr == null || arr.length() == 0) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            //不同厂商的嵌套层：annotation.url_citation.url / source.url / 直接 url
            val cite = o.optJSONObject("url_citation") ?: o.optJSONObject("source") ?: o
            val url = cite.optString("url")
            if (url.isBlank()) continue
            val title = cite.optString("title").ifBlank { cite.optString("site_name") }.ifBlank { url }
            out.add("[$title]($url)")
        }
    } //解析单组引用
} //原生 search 注入层