/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-21 17:02:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.net.api.llm
import b.ai.agent.tool.Tools
import b.ai.core.cfg.app.Cfg
import b.ai.core.llm.LocalEngine
import b.ai.core.model.chat.AuthStyle
import b.ai.native.llm.Llama
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

/**
 * 极简 OpenAI 兼容客户端：只依赖 HttpURLConnection。
 *
 * 支持两种接口协议（由每条模型配置独立决定）：
 *  - [PROTO_CHAT]      Chat Completions，POST {base}/chat/completions，body 用 messages[]
 *  - [PROTO_RESPONSES] Responses API，   POST {base}/responses，body 用 input[] + instructions
 * 两者对外暴露同一套 chat / chatStream 签名与同一个返回结构（content / reasoning_content / tool_calls / usage），
 * 上层（Agent、辅助任务）不需要判断协议。
 */
class LlmClient(
    baseUrl: String,
    private val apiKey: String,
    private val auth: AuthStyle,
    /** 连通性测试用：开着本地模型开关时也要把请求真发到云端，不能被本地引擎接管 */
    private val forceRemote: Boolean = false,
    /**
     * 接口协议；空串 = 跟随当前选中配置（[Cfg.modelProtocol]）。
     * 构造时定死，避免一次请求跑到一半用户切了配置，body 与解析口径对不上。
     */
    protocol: String = ""
) {
    companion object {
        /** 最大模式下至少下发的单轮上限：比自己设的小值抬上来，比这个还大就按用户设的走 */
        private const val MAX_CAP = 8192

        /** Chat Completions（老默认，兼容绝大多数中转） */
        const val PROTO_CHAT = "chat"

        /** OpenAI Responses API（新版，部分服务商已停用 Chat Completions） */
        const val PROTO_RESPONSES = "responses"

        /** 协议归一化：认 "responses" / "response" / "resp*"，其余一律当 chat */
        fun protocolOf(v: String): String {
            val s = v.trim().lowercase()
            return if (s.startsWith("resp")) PROTO_RESPONSES else PROTO_CHAT
        }//协议归一化
    }

    private val base = normalize(baseUrl)

    /** 实际生效的协议（已归一化） */
    private val proto = protocolOf(if (protocol.isBlank()) Cfg.modelProtocol else protocol)

    private val isResp: Boolean get() = proto == PROTO_RESPONSES

    private fun normalize(u: String): String {
        var b = u.trim().trimEnd('/')
        //用户常常把整个接口地址粘进来：这两种后缀都要剥掉，否则会拼成 /v1/responses/responses
        if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions").trimEnd('/')
        if (b.endsWith("/responses")) b = b.removeSuffix("/responses").trimEnd('/')
        return b
    }//base_url 容错

    /** 没配 base_url 时分两种原因：本地后端开着但推理库不可用，别让人白去填 Key */
    private fun noBase(): Nothing {
        if (Cfg.llmLocal && !Llama.available()) {
            throw LlmException("本地推理库不可用：这个安装包没带当前 CPU 架构的 so，请换官方 arm64 包")
        }
        throw LlmException("base_url 未配置")
    }//未配置后端时给准确原因

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
    }//建立连接并按鉴权方式带头

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
    }//收发并统一错误文本

    // ───────────────────────── Chat Completions ─────────────────────────

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
        if (b.ai.core.cfg.app.Cfg.modelToolCall) {
            //传了就按传的来（分智能体只下发只读工具），传空数组等于这轮不给工具
            val defs = tools ?: b.ai.agent.tool.Tools.definitions()
            if (defs.length() > 0) body.put("tools", defs).put("tool_choice", "auto")
        }
        //原生 search：勾了才声明各家自己的联网工具，服务端检索完把结果注入上下文
        NativeSearch.inject(body, b.ai.core.cfg.app.Cfg.providerId, b.ai.core.cfg.app.Cfg.baseUrl)
        temperature?.let { body.put("temperature", it) }
        //单轮上限：最大模式显式抬到下限 8192（各家服务端默认值普遍偏小，DeepSeek 是 4096），否则按配置的 max_tokens
        val maxTok = if (b.ai.core.cfg.app.Cfg.maxMode) maxOf(b.ai.core.cfg.app.Cfg.maxTokens, MAX_CAP) else b.ai.core.cfg.app.Cfg.maxTokens
        if (maxTok > 0) body.put("max_tokens", maxTok)
        //思考与原生联网搜索互斥：本轮实际带上了服务端联网工具时，思考一律不发（工具与思考同时下发多数厂商会报错）
        val searchOn = NativeSearch.on(
            b.ai.core.cfg.app.Cfg.providerId,
            b.ai.core.cfg.app.Cfg.baseUrl
        )
        //思考强度：只看思考开关，最大模式不再强开思考（最大模式只管 max_tokens）
        //先让规则表说话：命中规则的模型按规则写（各家字段不同，用户可在设置里改）
        val ruleDone = if (!searchOn) {
            ThinkRules.apply(body, model, think, b.ai.core.cfg.app.Cfg.thinkLevel)
        } else false
        //思考下发只看设置里的能力开关（模型能力 →「推理 / 思考参数」），不再拿模型名单当门控：
        //中转站/自建模型的名单永远列不全，名单一窄就等于替用户把明确打开的思考悄悄吞掉
        if (think && b.ai.core.cfg.app.Cfg.modelReasoning && !searchOn && !ruleDone) {
            body.put("reasoning_effort", thinkEffort)
        }
        //DeepSeek 官方格式的思考开关默认是「开」：不发 thinking 就等于思考，必须显式 disabled 才真关
        val ds = b.ai.core.cfg.app.Cfg.providerId.contains("deepseek", true) ||
            b.ai.core.cfg.app.Cfg.baseUrl.contains("deepseek", true)
        //DeepSeek 硬约束（实测 400）：client tools 只能和 deepseek_web_search 一起、且必须在思考模式下使用。
        //所以带了原生联网搜索时不能再显式关思考，否则服务端直接拒绝：
        //  tools: client tools can only be used together with deepseek_web_search in thinking mode
        //（这是唯一一处「互斥」失效的厂商，属于服务端强制要求，不改就会被 400 挡死）
        if (ds && !think && !searchOn && !ruleDone) {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        //创造性 / 重复控制参数：负数或 0 表示不下发，交给服务端默认值
        if (b.ai.core.cfg.app.Cfg.topP >= 0f) body.put("top_p", b.ai.core.cfg.app.Cfg.topP.toDouble())
        if (b.ai.core.cfg.app.Cfg.topK > 0) body.put("top_k", b.ai.core.cfg.app.Cfg.topK)
        if (b.ai.core.cfg.app.Cfg.presencePenalty > -9f) {
            body.put("presence_penalty", b.ai.core.cfg.app.Cfg.presencePenalty.toDouble())
        }
        if (b.ai.core.cfg.app.Cfg.frequencyPenalty > -9f) {
            body.put("frequency_penalty", b.ai.core.cfg.app.Cfg.frequencyPenalty.toDouble())
        }
        if (b.ai.core.cfg.app.Cfg.repeatPenalty > 0f) {
            body.put("repeat_penalty", b.ai.core.cfg.app.Cfg.repeatPenalty.toDouble())
        }
        //附加参数浅合并，用来补厂商私有字段（如 thinking / enable_thinking）
        if (!extraParams.isNullOrBlank()) {
            runCatching { JSONObject(extraParams) }.getOrNull()?.let { extra ->
                extra.keys().forEach { k -> body.put(k, extra.get(k)) }
            }
        }
        return body
    }//请求体

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
    }//取出 choices[0].message

    // ───────────────────────── Responses API ─────────────────────────

    /** 把 Chat 格式的 messages 转成 Responses 的 input 数组，同时把 system 抬成 instructions */
    private fun responsesInput(messages: JSONArray): Pair<JSONArray, String> {
        val input = JSONArray()
        val sysText = StringBuilder()
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            when (m.text("role")) {
                //Responses 里 system 有两种写法，这里统一抬到顶层 instructions（各家实现最认这个）
                "system", "developer" -> {
                    val t = plainText(m.opt("content"))
                    if (t.isNotBlank()) {
                        if (sysText.isNotEmpty()) sysText.append("\n\n")
                        sysText.append(t)
                    }
                }//系统提示
                "assistant" -> {
                    val txt = plainText(m.opt("content"))
                    if (txt.isNotBlank()) {
                        input.put(
                            JSONObject().put("type", "message").put("role", "assistant")
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject().put("type", "output_text").put("text", txt)
                                    )
                                )
                        )
                    }
                    //工具调用在 Responses 里是顶层 item，不挂在 message 下面
                    val calls = m.optJSONArray("tool_calls")
                    for (j in 0 until (calls?.length() ?: 0)) {
                        val c = calls!!.optJSONObject(j) ?: continue
                        val fn = c.optJSONObject("function") ?: continue
                        input.put(
                            JSONObject().put("type", "function_call")
                                .put("call_id", c.text("id"))
                                .put("name", fn.text("name"))
                                .put("arguments", fn.text("arguments").ifBlank { "{}" })
                        )
                    }//历史里的工具调用
                }
                //工具结果同样是顶层 item：call_id 必须能对上前面的 function_call，否则上游 400
                "tool" -> input.put(
                    JSONObject().put("type", "function_call_output")
                        .put("call_id", m.text("tool_call_id"))
                        .put("output", plainText(m.opt("content")))
                )
                //其余（user）走多模态 parts，图片结构与 Chat 不同（input_image 的 url 是字符串，不是对象）
                else -> input.put(
                    JSONObject().put("type", "message").put("role", "user")
                        .put("content", responsesParts(m.opt("content")))
                )
            }
        }//逐条转换
        return input to sysText.toString()
    }//messages → input + instructions

    /** 取纯文本：字符串直接用，数组则拼各 part 的 text（assistant 历史可能带多模态结构） */
    private fun plainText(v: Any?): String {
        return when (v) {
            null, JSONObject.NULL -> ""
            is String -> v
            is JSONArray -> buildString {
                for (i in 0 until v.length()) {
                    val p = v.optJSONObject(i) ?: continue
                    append(p.text("text"))
                }
            }//多模态数组
            else -> v.toString()
        }
    }//取文本

    /** Chat 的 content（字符串或 parts 数组）→ Responses 的 parts 数组 */
    private fun responsesParts(v: Any?): JSONArray {
        val parts = JSONArray()
        when (v) {
            is JSONArray -> {
                for (i in 0 until v.length()) {
                    val p = v.optJSONObject(i) ?: continue
                    when (p.text("type")) {
                        "text" -> parts.put(
                            JSONObject().put("type", "input_text").put("text", p.text("text"))
                        )
                        "image_url" -> {
                            val iu = p.opt("image_url")
                            val url = if (iu is JSONObject) iu.text("url") else iu?.toString().orEmpty()
                            if (url.isNotBlank()) {
                                parts.put(
                                    JSONObject().put("type", "input_image").put("image_url", url)
                                )
                            }
                        }//图片（data URL 直接给）
                    }
                }
            }
            else -> {
                val t = plainText(v)
                parts.put(JSONObject().put("type", "input_text").put("text", t.ifBlank { "[空]" }))
            }
        }
        //一个 part 都没有时补一句，避免 content: [] 被上游判成非法
        if (parts.length() == 0) {
            parts.put(JSONObject().put("type", "input_text").put("text", "[空]"))
        }
        return parts
    }//content → parts

    /** Chat 的 tools（{type:function, function:{name,...}}）→ Responses 的扁平结构（{type:function, name,...}） */
    private fun flatTools(defs: JSONArray?): JSONArray {
        val out = JSONArray()
        val src = defs ?: return out
        for (i in 0 until src.length()) {
            val o = src.optJSONObject(i) ?: continue
            val fn = o.optJSONObject("function") ?: o //兼容已经扁平化的调用方
            val name = fn.text("name")
            if (name.isBlank()) continue
            out.put(
                JSONObject().put("type", "function")
                    .put("name", name)
                    .put("description", fn.text("description"))
                    .put("parameters", fn.optJSONObject("parameters") ?: JSONObject().put("type", "object"))
            )
        }//逐条扁平化
        return out
    }//tools → 扁平

    /** Responses 的 usage（input/output_tokens）→ 内部统一的 prompt/completion_tokens */
    private fun mapUsage(u: JSONObject?): JSONObject? {
        if (u == null) return null
        val inTok = u.optInt("input_tokens", u.optInt("prompt_tokens", 0))
        val outTok = u.optInt("output_tokens", u.optInt("completion_tokens", 0))
        val o = JSONObject()
            .put("prompt_tokens", inTok)
            .put("completion_tokens", outTok)
            .put("total_tokens", u.optInt("total_tokens", inTok + outTok))
        //缓存命中数：OpenAI 放在 input_tokens_details.cached_tokens，界面按 prompt_tokens_details 读
        val cached = u.optJSONObject("input_tokens_details")?.optInt("cached_tokens", 0) ?: 0
        if (cached > 0) {
            o.put("prompt_tokens_details", JSONObject().put("cached_tokens", cached))
        }
        return o
    }//usage 字段映射

    /** 组装 Responses 请求体 */
    private fun responsesBody(
        model: String,
        messages: JSONArray,
        temperature: Double?,
        think: Boolean,
        thinkEffort: String,
        extraParams: String?,
        tools: JSONArray?,
        stream: Boolean
    ): JSONObject {
        val (input, instructions) = responsesInput(messages)
        val body = JSONObject().put("model", model).put("input", input)
        if (instructions.isNotBlank()) body.put("instructions", instructions)
        //工具：Responses 的 schema 是扁平的，必须转换，直接塞 Chat 结构会被判非法
        if (b.ai.core.cfg.app.Cfg.modelToolCall) {
            val defs = flatTools(tools ?: Tools.definitions())
            if (defs.length() > 0) body.put("tools", defs).put("tool_choice", "auto")
        }
        temperature?.let { body.put("temperature", it) }
        val maxTok = if (b.ai.core.cfg.app.Cfg.maxMode) maxOf(b.ai.core.cfg.app.Cfg.maxTokens, MAX_CAP) else b.ai.core.cfg.app.Cfg.maxTokens
        if (maxTok > 0) body.put("max_output_tokens", maxTok)
        //思考：Responses 只认 reasoning.effort（low / medium / high）；是否下发同样只认能力开关
        if (think && b.ai.core.cfg.app.Cfg.modelReasoning) {
            body.put("reasoning", JSONObject().put("effort", thinkEffort))
        }
        if (b.ai.core.cfg.app.Cfg.topP >= 0f) body.put("top_p", b.ai.core.cfg.app.Cfg.topP.toDouble())
        //原生 search：Responses 各家中转支持度差异太大（web_search 工具名各不同），这里不注入，
        //避免勾了联网就被 400 挡住；需要联网请走 tavily / 内置搜索工具
        if (stream) body.put("stream", true)
        //附加参数浅合并，兜住厂商私有字段
        if (!extraParams.isNullOrBlank()) {
            runCatching { JSONObject(extraParams) }.getOrNull()?.let { extra ->
                extra.keys().forEach { k -> body.put(k, extra.get(k)) }
            }
        }
        return body
    }//Responses 请求体

    /** Responses 的整包响应 → 内部统一 message（content / reasoning_content / tool_calls / usage） */
    private fun respMessage(raw: String): JSONObject {
        val obj = runCatching { JSONObject(raw) }
            .getOrElse { throw LlmException("响应不是 JSON: ${raw.take(300)}") }
        //有些中转把错误包在 200 里，必须先看 error 字段
        obj.optJSONObject("error")?.let { e ->
            throw LlmException("接口报错: " + e.text("message").ifBlank { e.toString().take(300) })
        }
        val content = StringBuilder()
        val reason = StringBuilder()
        val calls = JSONArray()
        val out = obj.optJSONArray("output") ?: JSONArray()
        for (i in 0 until out.length()) {
            val item = out.optJSONObject(i) ?: continue
            when (item.text("type")) {
                "message" -> {
                    val arr = item.optJSONArray("content") ?: continue
                    for (j in 0 until arr.length()) {
                        val part = arr.optJSONObject(j) ?: continue
                        when (part.text("type")) {
                            "output_text", "text" -> content.append(part.text("text"))
                            "refusal" -> content.append(part.text("refusal"))
                        }//正文 / 拒答
                    }
                }
                //思考摘要：o 系列只回 summary，不回原始 reasoning，拼起来给界面展示
                "reasoning" -> {
                    val arr = item.optJSONArray("summary") ?: item.optJSONArray("content")
                    for (j in 0 until (arr?.length() ?: 0)) {
                        reason.append(arr!!.optJSONObject(j)?.text("text").orEmpty())
                    }
                }
                "function_call" -> calls.put(
                    JSONObject().put("id", item.text("call_id").ifBlank { item.text("id") })
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject().put("name", item.text("name"))
                                .put("arguments", item.text("arguments").ifBlank { "{}" })
                        )
                )
            }
        }//遍历 output
        val text = content.toString()
        //一个字都没有也没有工具调用：把 status 与截断原因抛出去，别让界面出空气泡
        if (text.isBlank() && calls.length() == 0) {
            val st = obj.text("status")
            val why = obj.optJSONObject("incomplete_details")?.text("reason").orEmpty()
            throw LlmException(
                "响应为空：status=${st.ifBlank { "?" }}" + if (why.isNotBlank()) " reason=$why" else ""
            )
        }
        val msg = JSONObject().put("role", "assistant").put("content", text)
            .put("reasoning_content", reason.toString())
        if (calls.length() > 0) msg.put("tool_calls", calls)
        mapUsage(obj.optJSONObject("usage"))?.let { msg.put("usage", it) }
        return msg
    }//Responses 整包解析

    // ───────────────────────── 对外：整包 ─────────────────────────

    /** 返回 choices[0].message 结构（Responses 协议下由 respMessage 归一化成同一结构） */
    fun chat(
        model: String,
        messages: JSONArray,
        temperature: Double? = null,
        think: Boolean = false,
        thinkEffort: String = "medium",
        extraParams: String? = null,
        tools: JSONArray? = null
    ): JSONObject {
        //本地模式：整条链路切到 llama.cpp，base_url 与 apiKey 都不参与
        if (!forceRemote && LocalEngine.route()) return LocalEngine.chat(messages, temperature)
        if (base.isBlank()) noBase()
        return if (isResp) {
            val body = responsesBody(model, messages, temperature, think, thinkEffort, extraParams, tools, false)
            respMessage(exec(conn("/responses"), body.toString()))
        } else {
            val body = bodyOf(model, messages, temperature, think, thinkEffort, extraParams, tools)
            messageOf(exec(conn("/chat/completions"), body.toString()))
        }
    }//对话：带 tools 定义

    // ───────────────────────── 对外：流式 ─────────────────────────

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
        //本地模式：token 回调语义保持一致，取消走 Llama.cancel
        if (!forceRemote && LocalEngine.route()) return LocalEngine.stream(messages, temperature, shouldStop, onDelta)
        if (base.isBlank()) noBase()
        val path = if (isResp) "/responses" else "/chat/completions"
        val c = conn(path).apply {
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
        }
        val body = if (isResp) {
            responsesBody(model, messages, temperature, think, thinkEffort, extraParams, tools, true)
        } else {
            bodyOf(model, messages, temperature, think, thinkEffort, extraParams, tools).put("stream", true)
        }
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
                val raw = c.inputStream.bufferedReader().use { it.readText() }
                if (isResp) respMessage(raw) else messageOf(raw)
            } else if (isResp) {
                respStream(c, shouldStop, onDelta)
            } else {
                chatStreamOf(c, shouldStop, onDelta)
            }
        } catch (e: LlmException) {
            throw e
        } catch (e: IOException) {
            throw LlmException("网络错误: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            c.disconnect()
        }
    }//流式对话

    /** Chat Completions 的 SSE 解析：delta.content / reasoning_content / tool_calls[index] */
    private fun chatStreamOf(
        c: HttpURLConnection,
        shouldStop: () -> Boolean,
        onDelta: (String, String) -> Unit
    ): JSONObject {
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
        }//读流
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
        return JSONObject()
            .put("content", content.toString())
            .put("reasoning_content", reason.toString())
            .put("tool_calls", arr)
            .also { out -> usage?.let { out.put("usage", it) } }
    }//Chat SSE

    /**
     * Responses 的 SSE 解析。事件类型与 Chat 完全不同：
     *  response.output_text.delta（正文增量）/ response.reasoning_summary_text.delta（思考增量）/
     *  response.output_item.added（function_call 起始）/ response.function_call_arguments.delta（参数增量）/
     *  response.completed（含 usage 与最终 output）
     * 中转实现有的只发 data 里的 type、有的另外发 event: 行，两种都认。
     */
    private fun respStream(
        c: HttpURLConnection,
        shouldStop: () -> Boolean,
        onDelta: (String, String) -> Unit
    ): JSONObject {
        val content = StringBuilder()
        val reason = StringBuilder()
        //item_id → {call_id, name, arguments}：参数是按增量片段拼起来的
        val calls = LinkedHashMap<String, JSONObject>()
        var usage: JSONObject? = null
        var finalObj: JSONObject? = null
        var evName = ""
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                if (shouldStop()) throw CancellationException("已停止")
                val line = r.readLine() ?: break
                if (line.startsWith("event:")) {
                    evName = line.removePrefix("event:").trim()
                    continue
                }
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break
                val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                //有的中转把事件名只放在 data.type 里
                val type = o.text("type").ifBlank { evName }
                when (type) {
                    "response.output_text.delta", "response.refusal.delta" -> {
                        val d = o.text("delta")
                        if (d.isNotEmpty()) {
                            content.append(d)
                            onDelta(d, "")
                        }
                    }
                    "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                        val d = o.text("delta")
                        if (d.isNotEmpty()) {
                            reason.append(d)
                            onDelta("", d)
                        }
                    }
                    //function_call 起始：id / name 先记下来，参数等 delta 拼
                    "response.output_item.added" -> {
                        val item = o.optJSONObject("item") ?: continue
                        if (item.text("type") == "function_call") {
                            val key = item.text("id").ifBlank { "c" + calls.size }
                            calls[key] = JSONObject()
                                .put("call_id", item.text("call_id"))
                                .put("name", item.text("name"))
                                .put("arguments", "")
                        }
                    }
                    "response.function_call_arguments.delta" -> {
                        val cur = calls[o.text("item_id")] ?: continue
                        cur.put("arguments", cur.optString("arguments") + o.text("delta"))
                    }
                    //参数收完 / 整个 item 收完：都以完整值覆盖，避免增量丢了片段
                    "response.function_call_arguments.done", "response.output_item.done" -> {
                        val item = o.optJSONObject("item")
                        val key = o.text("item_id").ifBlank { item?.text("id").orEmpty() }
                        val cur = calls[key] ?: continue
                        val full = o.text("arguments").ifBlank { item?.text("arguments").orEmpty() }
                        if (full.isNotEmpty()) cur.put("arguments", full)
                        if (item != null) {
                            if (item.text("name").isNotEmpty()) cur.put("name", item.text("name"))
                            if (item.text("call_id").isNotEmpty()) cur.put("call_id", item.text("call_id"))
                        }
                    }
                    "response.completed", "response.incomplete" -> {
                        val resp = o.optJSONObject("response")
                        usage = resp?.optJSONObject("usage") ?: usage
                        finalObj = resp
                    }
                    "response.failed" -> {
                        val e2 = o.optJSONObject("response")?.optJSONObject("error")
                        throw LlmException("生成失败: " + (e2?.text("message") ?: data.take(300)))
                    }
                    "error" -> throw LlmException(
                        "流式错误: " + o.text("message").ifBlank { data.take(300) }
                    )
                }
            }//读流
        }
        //服务端给了最终结果就直接按它解析（output 最完整），否则用累加的增量拼
        val fin = finalObj
        if (fin != null && (fin.optJSONArray("output")?.length() ?: 0) > 0) {
            return respMessage(fin.toString())
        }
        val arr = JSONArray()
        calls.values.forEach { v ->
            arr.put(
                JSONObject().put("id", v.text("call_id"))
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject().put("name", v.text("name"))
                            .put("arguments", v.text("arguments").ifBlank { "{}" })
                    )
            )
        }
        return JSONObject()
            .put("content", content.toString())
            .put("reasoning_content", reason.toString())
            .put("tool_calls", arr)
            .also { out -> mapUsage(usage)?.let { out.put("usage", it) } }
    }//Responses SSE

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
    }//动态模型列表
}//OpenAI 兼容客户端（Chat Completions + Responses API）