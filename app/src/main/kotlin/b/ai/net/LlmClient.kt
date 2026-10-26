package b.ai.net

import b.ai.core.AuthStyle
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
class LlmException(message: String) : Exception(message)

/**Android 的 org.json 里 optString 遇到 JSON null 会返回字面量 ""（JSONObject.NULL.toString 就是 ""）， 正文恒为 null 就是踩这个坑；统一走这个取值 */
internal fun JSONObject.text(name: String, def: String = ""): String =
    if (isNull(name)) def else optString(name, def)


/** 极简 OpenAI 兼容客户端：只依赖 HttpURLConnection */
class LlmClient(baseUrl: String, private val apiKey: String, private val auth: AuthStyle) {
    private val base = normalize(baseUrl)

    private fun normalize(u: String): String {
        var b = u.trim().trimEnd('/')
        if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions").trimEnd('/')
        return b
    } //base_url 容错

    private fun conn(path: String): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 300_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        when (auth) {
            AuthStyle.BEARER -> c.setRequestProperty("Authorization", "Bearer $apiKey")
            AuthStyle.X_API_KEY -> c.setRequestProperty("api-key", apiKey)
            AuthStyle.NONE -> Unit
        }
        return c
    } //建立连接并按鉴权方式带头

    private fun exec(c: HttpURLConnection, body: String?): String {
        return try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw LlmException("HTTP $code: ${text.take(600)}")
            text
        } catch (e: LlmException) {
            throw e
        } catch (e: IOException) {
            throw LlmException("网络错误: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            c.disconnect()
        }
    } //收发并统一错误文本

    /** 组装请求体（tools + 采样参数 + 厂商私有字段） */
    private fun bodyOf(
        model: String,
        messages: JSONArray,
        temperature: Double?,
        think: Boolean,
        thinkEffort: String,
        extraParams: String?,
        tools: JSONArray? = null
    ): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
        //模型不支持工具调用就别发 tools，发了有的中转站直接 400
        if (b.ai.core.Cfg.modelToolCall) {
            //传了就按传的来（分智能体只下发只读工具），传空数组等于这轮不给工具
            val defs = tools ?: b.ai.agent.Tools.definitions()
            if (defs.length() > 0) body.put("tools", defs).put("tool_choice", "auto")
        }
        temperature?.let { body.put("temperature", it) }
        //单轮上限：最大模式不下发（交给服务端默认最大），否则按配置的 max_tokens
        val maxTok = if (b.ai.core.Cfg.maxMode) 0 else b.ai.core.Cfg.maxTokens
        if (maxTok > 0) body.put("max_tokens", maxTok)
        //思考强度：只看思考开关，最大模式不再强开思考（最大模式只管 max_tokens）
        if (think && b.ai.core.Cfg.modelReasoning) {
            body.put("reasoning_effort", thinkEffort)
        }
        //DeepSeek 官方格式的思考开关默认是「开」：不发 thinking 就等于思考，必须显式 disabled 才真关
        val ds = b.ai.core.Cfg.providerId.contains("deepseek", true) ||
            b.ai.core.Cfg.baseUrl.contains("deepseek", true)
        if (ds && !think) {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        //附加参数浅合并，用来补厂商私有字段（如 thinking / enable_thinking）
        if (!extraParams.isNullOrBlank()) {
            runCatching { JSONObject(extraParams) }.getOrNull()?.let { extra ->
                extra.keys().forEach { k -> body.put(k, extra.get(k)) }
            }
        }
        return body
    } //请求体

    private fun messageOf(raw: String): JSONObject {
        val obj = runCatching { JSONObject(raw) }
            .getOrElse { throw LlmException("响应不是 JSON: ${raw.take(300)}") }
        val choices = obj.optJSONArray("choices")
            ?: throw LlmException("响应缺失 choices: ${raw.take(300)}")
        val msg = choices.getJSONObject(0).optJSONObject("message")
            ?: throw LlmException("响应缺失 message: ${raw.take(300)}")
        //usage 挂在响应顶层，顺手带进 message 里，调用方用来算 token 与速度
        obj.optJSONObject("usage")?.let { msg.put("usage", it) }
        return msg
    } //取出 choices[0].message

    /** 返回 choices[0].message 对象 */
    fun chat(
        model: String,
        messages: JSONArray,
        temperature: Double? = null,
        think: Boolean = false,
        thinkEffort: String = "medium",
        extraParams: String? = null,
        tools: JSONArray? = null
    ): JSONObject {
        if (base.isBlank()) throw LlmException("base_url 未配置")
        val body = bodyOf(model, messages, temperature, think, thinkEffort, extraParams, tools)
        return messageOf(exec(conn("/chat/completions"), body.toString()))
    } //对话：带 tools 定义

    /**
     * 流式对话：逐块回调增量（content / reasoning），返回拼好的 message 对象（tool_calls 也按 index 合并）。
     * 服务端忽略 stream=true（返回普通 JSON）时自动按整包解析，不抛错。
     */
    fun chatStream(
        model: String,
        messages: JSONArray,
        temperature: Double? = null,
        think: Boolean = false,
        thinkEffort: String = "medium",
        extraParams: String? = null,
        tools: JSONArray? = null,
        shouldStop: () -> Boolean = { false },
        onDelta: (String, String) -> Unit
    ): JSONObject {
        if (base.isBlank()) throw LlmException("base_url 未配置")
        val c = conn("/chat/completions").apply {
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
        }
        val body = bodyOf(model, messages, temperature, think, thinkEffort, extraParams, tools)
            .put("stream", true)
        return try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw LlmException("HTTP $code: ${err.take(600)}")
            }
            val ctype = c.contentType ?: ""
            if (!ctype.contains("event-stream", true)) {
                //端点没走 SSE，退回整包
                return messageOf(c.inputStream.bufferedReader().use { it.readText() })
            }
            val content = StringBuilder()
            val reason = StringBuilder()
            val calls = LinkedHashMap<Int, JSONObject>()
            var usage: JSONObject? = null
            c.inputStream.bufferedReader().use { r ->
                while (true) {
                    //用户点「停止」时靠这里跳出读流，HttpURLConnection 的 read 不会自己响应取消
                    if (shouldStop()) throw CancellationException("已停止")
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    //用量可能挂在最后一个 chunk 上（choices 为空），先收下再解析增量
                    o.optJSONObject("usage")?.let { usage = it }
                    val delta = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        ?: continue
                    val t = delta.text("content")
                    val rz = delta.text("reasoning_content")
                        .ifBlank { delta.text("reasoning") }
                    if (t.isNotEmpty()) {
                        content.append(t)
                        onDelta(t, "")
                    }
                    if (rz.isNotEmpty()) {
                        reason.append(rz)
                        onDelta("", rz)
                    }
                    val tc = delta.optJSONArray("tool_calls") ?: continue
                    for (i in 0 until tc.length()) {
                        val part = tc.optJSONObject(i) ?: continue
                        val idx = part.optInt("index", i)
                        val cur = calls.getOrPut(idx) {
                            JSONObject().put("id", "").put("name", "").put("arguments", "")
                        }
                        val pid = part.text("id")
                        if (pid.isNotEmpty()) cur.put("id", pid)
                        val fn = part.optJSONObject("function") ?: continue
                        val fnName = fn.text("name")
                        if (fnName.isNotEmpty()) cur.put("name", fnName)
                        val frag = fn.text("arguments")
                        if (frag.isNotEmpty()) {
                            cur.put("arguments", cur.optString("arguments", "") + frag)
                        }
                    }
                }
            }
            val arr = JSONArray()
            calls.entries.sortedBy { it.key }.forEach { (_, v) ->
                val args = v.optString("arguments", "").ifBlank { "{}" }
                arr.put(
                    JSONObject()
                        .put("id", v.optString("id"))
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject().put("name", v.optString("name")).put("arguments", args)
                        )
                )
            }
            JSONObject()
                .put("content", content.toString())
                .put("reasoning_content", reason.toString())
                .put("tool_calls", arr)
                .also { out -> usage?.let { out.put("usage", it) } }
        } catch (e: LlmException) {
            throw e
        } catch (e: IOException) {
            throw LlmException("网络错误: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            c.disconnect()
        }
    } //流式对话

    /** 拉取 /models，兼容 data[].id / models[].slug 等格式 */
    fun listModels(): List<String> {
        if (base.isBlank()) throw LlmException("base_url 未配置")
        val raw = exec(conn("/models").apply { requestMethod = "GET"; doOutput = false }, null)
        val obj = runCatching { JSONObject(raw) }.getOrElse {
            return raw.split('\n').map { it.trim().trim('"', ',') }.filter { it.contains("/") || it.length > 2 }
        }
        val arr = obj.optJSONArray("data") ?: obj.optJSONArray("models") ?: JSONArray()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id").ifBlank {
                item.optString("slug").ifBlank { item.optString("name") }
            }
            if (id.isNotBlank()) out.add(id)
        }
        return out.distinct()
    } //动态模型列表
}