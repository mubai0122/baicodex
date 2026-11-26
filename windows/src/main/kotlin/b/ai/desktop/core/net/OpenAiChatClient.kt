/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 模型客户端：OpenAI 兼容 /chat/completions（含 tool_calls 与思考内容）。
 *
 * 思考相关的可选参数各家不统一（reasoning_effort / enable_thinking / thinking /
 * web_search_options），发错会被 400。这里的做法是：按模型挑一组最可能的发；
 * 如果服务端明确报某个字段不认识，就剔掉它重试，最多三次，保证对话不被打断。
 */

package b.ai.desktop.core.net

import b.ai.desktop.core.config.ModelProfile
import b.ai.desktop.core.model.RunOptions
import b.ai.desktop.core.tool.ToolCall
import b.ai.desktop.core.tool.ToolSpec
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 服务端回报的用量（有的网关不给，缺省全 0） */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

/** 一次模型回复 */
data class ChatReply(
    val text: String,
    /** 思考过程（reasoning_content / reasoning / thinking），没有就是空 */
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    /** 真实用量：预算卡的「累计消耗」用它，没有就是 null */
    val usage: TokenUsage? = null,
)

interface ChatClient {
    suspend fun complete(
        messages: List<JsonObject>,
        tools: List<ToolSpec>,
        options: RunOptions,
    ): ChatReply
}

/**
 * OpenAI 兼容实现。baseUrl 可带或不带 /v1，末尾斜杠自动处理。
 * 出错时抛出带原因的异常，界面直接显示，不再兜成「假的回复」。
 */
class OpenAiChatClient(
    private val profile: ModelProfile,
) : ChatClient {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override suspend fun complete(
        messages: List<JsonObject>,
        tools: List<ToolSpec>,
        options: RunOptions,
    ): ChatReply = withContext(Dispatchers.IO) {
        if (!profile.ready) throw IllegalStateException("模型配置不完整：需要 baseUrl 与模型名")
        if (profile.apiKey.isBlank()) throw IllegalStateException("还没填 API Key")

        val cacheKey = "${profile.baseUrl}|${profile.model}"
        // 已经确认不被支持的可选字段记下来，后续请求不再往枪口上撞
        var skipped: Set<String> = UnsupportedFields.get(cacheKey)
        // 档位先按原值发；端点不认再退到 high，仍不认就不发
        var effort: String? = options.requestEffort
        var lastError = ""

        repeat(MaxAttempts) {
            val response = send(payload(messages, tools, options, skipped, effort))
            val body = response.body().orEmpty()

            if (response.statusCode() in 200..299) return@withContext parse(body)

            lastError = "HTTP ${response.statusCode()}：${body.take(300)}"
            val field = rejectedField(body, skipped)
            if (response.statusCode() !in 400..422 || field == null) {
                throw IllegalStateException(lastError)
            }

            when (field) {
                // 档位不认识：先降一档再试，还不行就整个不发
                "reasoning_effort" -> {
                    if (options.thinking && effort != null && effort != FallbackEffort) {
                        effort = FallbackEffort
                    } else {
                        skipped = skipped + field
                        UnsupportedFields.add(cacheKey, field)
                        effort = null
                    }
                }

                else -> {
                    skipped = skipped + field
                    UnsupportedFields.add(cacheKey, field)
                }
            }
        }
        throw IllegalStateException(lastError.ifBlank { "请求被服务端拒绝" })
    }

    private fun send(payload: JsonObject): HttpResponse<String> {
        val request = HttpRequest.newBuilder(endpoint())
            .timeout(Duration.ofSeconds(profile.timeoutSec.coerceIn(30, 900).toLong()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${profile.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), Charsets.UTF_8))
            .build()
        return runCatching { http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)) }
            .getOrElse { e ->
                throw IllegalStateException("请求失败：${e.message ?: e::class.simpleName}", e)
            }
    }

    /**
     * 组装请求体。skipped 是被服务端明确拒绝过的可选字段：
     * 剔掉它们重发，能让「模型不支持思考参数」退化成普通对话，而不是整轮失败。
     */
    private fun payload(
        messages: List<JsonObject>,
        tools: List<ToolSpec>,
        options: RunOptions,
        skipped: Set<String>,
        effort: String?,
    ): JsonObject = buildJsonObject {
        put("model", profile.model)
        put("messages", JsonArray(messages))
        put("stream", false)

        if (profile.temperature >= 0 && "temperature" !in skipped) {
            put("temperature", profile.temperature)
        }
        if (profile.topP >= 0 && "top_p" !in skipped) {
            put("top_p", profile.topP)
        }

        val maxTokens = when {
            profile.maxTokens > 0 -> profile.maxTokens
            options.maxMode -> MaxOutputTokens
            else -> 0
        }
        if (maxTokens > 0 && "max_tokens" !in skipped) {
            put("max_tokens", maxTokens)
        }

        putThinking(options, skipped, effort)

        if (options.nativeSearch && "web_search_options" !in skipped) {
            put("web_search_options", buildJsonObject { })
        }

        if (tools.isNotEmpty() && profile.toolsEnabled && options.toolCalls) {
            put("tools", buildJsonArray {
                tools.forEach { spec ->
                    add(buildJsonObject {
                        put("type", "function")
                        put(
                            "function",
                            buildJsonObject {
                                put("name", spec.name)
                                put("description", spec.description)
                                put("parameters", spec.parameters)
                            },
                        )
                    })
                }
            })
            put("tool_choice", "auto")
            put("parallel_tool_calls", options.parallelToolCalls)
        }
    }

    private fun JsonObjectBuilder.putThinking(
        options: RunOptions,
        skipped: Set<String>,
        effort: String?,
    ) {
        // 关思考：推理模型不传参数也会想，必须显式 none（实测能把 reasoning 压到 0）
        if (!options.thinking) {
            if (RunOptions.looksLikeReasoningModel(profile.model) &&
                effort != null &&
                "reasoning_effort" !in skipped
            ) {
                put("reasoning_effort", "none")
            }
            return
        }

        // 国内几家的开关叫 enable_thinking；其余按 OpenAI 的 reasoning_effort 发
        if (prefersEnableThinking(profile.model) && "enable_thinking" !in skipped) {
            put("enable_thinking", true)
            return
        }
        if (effort != null && "reasoning_effort" !in skipped) {
            put("reasoning_effort", effort)
        }
    }

    private fun endpoint(): URI {
        var base = profile.baseUrl.trim().trimEnd('/')
        // 允许用户直接填到 /chat/completions
        if (base.endsWith("/chat/completions")) return URI.create(base)
        if (!base.endsWith("/v1") && !base.contains("/v1/") && !base.contains("/openai")) {
            base = "$base/v1"
        }
        return URI.create("$base/chat/completions")
    }

    private fun parse(body: String): ChatReply {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IllegalStateException("返回不是 JSON：${body.take(200)}") }

        root["error"]?.let { err ->
            val msg = runCatching { err.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
            throw IllegalStateException(msg ?: "服务端返回错误")
        }

        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?: throw IllegalStateException("返回缺少 choices[0].message")

        val text = (message["content"] as? JsonPrimitive)
            ?.takeIf { it !is JsonNull }
            ?.content
            .orEmpty()

        val calls = message["tool_calls"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val function = obj["function"]?.jsonObject ?: return@mapNotNull null
            val name = function["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val argsText = function["arguments"]?.jsonPrimitive?.content ?: "{}"
            val args = runCatching { Json.parseToJsonElement(argsText).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            ToolCall(
                id = obj["id"]?.jsonPrimitive?.content ?: name,
                name = name,
                args = args,
            )
        }.orEmpty()

        return ChatReply(
            text = text.trim(),
            reasoning = reasoningOf(message).trim(),
            toolCalls = calls,
            usage = usageOf(root["usage"] as? JsonObject),
        )
    }

    /** usage 字段各家都是 prompt/completion/total 三件套，缺失就整体当没有 */
    private fun usageOf(usage: JsonObject?): TokenUsage? {
        if (usage == null) return null
        fun pick(key: String): Int =
            (usage[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val total = pick("total_tokens")
        val prompt = pick("prompt_tokens")
        val completion = pick("completion_tokens")
        if (total == 0 && prompt == 0 && completion == 0) return null
        return TokenUsage(
            promptTokens = prompt,
            completionTokens = completion,
            totalTokens = if (total > 0) total else prompt + completion,
        )
    }

    /** 思考内容字段各家不同：DeepSeek/Qwen 用 reasoning_content，OpenRouter 用 reasoning(_details) */
    private fun reasoningOf(message: JsonObject): String {
        ReasoningKeys.forEach { key ->
            val value = elementText(message[key])
            if (value.isNotBlank()) return value
        }
        return (message["reasoning_details"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
            .orEmpty()
    }

    private fun elementText(element: JsonElement?): String = when (element) {
        null, is JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonObject -> element["content"]?.jsonPrimitive?.contentOrNull
            ?: element["text"]?.jsonPrimitive?.contentOrNull
            ?: ""

        is JsonArray -> element.mapNotNull {
            (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        }.joinToString("\n")
    }

    /** 只在错误信息里点名了某个可选字段时才剔除它 */
    private fun rejectedField(body: String, skipped: Set<String>): String? {
        val lower = body.lowercase()
        return OptionalFields.firstOrNull { it !in skipped && lower.contains(it) }
    }

    private fun prefersEnableThinking(model: String): Boolean {
        val m = model.lowercase()
        return m.contains("qwen") || m.contains("glm") || m.contains("kimi") ||
            m.contains("minimax") || m.contains("hunyuan") || m.contains("ernie")
    }

    /** 记录「这个端点不认这个字段」，避免每次请求都先撞一次 400 */
    private object UnsupportedFields {
        private val map = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

        fun get(key: String): Set<String> = map[key]?.toSet().orEmpty()

        fun add(key: String, field: String) {
            map.getOrPut(key) { java.util.Collections.synchronizedSet(mutableSetOf<String>()) }.add(field)
        }
    }

    private companion object {
        const val MaxAttempts = 4
        const val MaxOutputTokens = 8192

        /** 档位被拒时先退到这一档，通用性最好 */
        const val FallbackEffort = "high"

        /** 思考/搜索相关的可选字段：被拒就剔除重试 */
        val OptionalFields = listOf(
            "reasoning_effort",
            "enable_thinking",
            "thinking",
            "web_search_options",
            "max_tokens",
            "temperature",
            "top_p",
            "parallel_tool_calls",
        )

        val ReasoningKeys = listOf("reasoning_content", "reasoning", "thinking", "reasoning_text")
    }
}
