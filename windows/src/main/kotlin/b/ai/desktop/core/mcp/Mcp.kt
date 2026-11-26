/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:35:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MCP 客户端（stdio 传输，无第三方 SDK）：
 *   起子进程 → initialize 握手 → notifications/initialized → tools/list → tools/call。
 *   消息是按行分隔的 JSON-RPC 2.0；服务器的通知/日志行直接跳过。
 *   会话按服务 id 缓存，进程活着就复用；坏了下次调用自动重连。
 */

package b.ai.desktop.core.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 一条 MCP 服务配置（存 config.json） */
@Serializable
data class McpServerCfg(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val label: String = "",
    /** 启动命令，例如 npx / node / python */
    val command: String = "",
    /** 空格分隔的参数；带空格的路径自己加引号，这里按 shell 惯例解析 */
    val args: String = "",
    val enabled: Boolean = true,
) {
    val displayName: String get() = label.ifBlank { command }
}

/** tools/list 返回的单个工具 */
data class McpToolInfo(
    val name: String,
    val description: String,
    val schema: JsonObject,
)

class McpException(message: String) : Exception(message)

/** 一条活跃的 MCP 会话：包着子进程与 stdin/stdout */
class McpSession internal constructor(
    val serverId: String,
    private val process: Process,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
) {
    private var nextId = 1
    private val lock = Object()

    /** 发请求等响应；通知行跳过；超时抛异常 */
    internal fun request(method: String, params: JsonObject?, timeoutSec: Long): JsonObject = synchronized(lock) {
        val id = nextId++
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }
        writer.write(message.toString())
        writer.newLine()
        writer.flush()

        val deadline = System.currentTimeMillis() + timeoutSec * 1000
        while (System.currentTimeMillis() < deadline) {
            val line = reader.readLine() ?: throw McpException("MCP 服务已断开（$method）")
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val obj = runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: continue
            // 有 method 的都是通知/请求（本客户端不支持服务端反调），跳过
            if (obj.containsKey("method") || !obj.containsKey("id")) continue
            val responseId = (obj["id"] as? JsonPrimitive)?.content?.toIntOrNull() ?: continue
            if (responseId != id) continue
            if (obj.containsKey("error")) {
                val err = obj["error"]?.jsonObject
                throw McpException("MCP 错误：${err?.get("message")?.jsonPrimitive?.content ?: "未知"}")
            }
            return obj["result"]?.jsonObject ?: JsonObject(emptyMap())
        }
        throw McpException("MCP 响应超时（$method，${timeoutSec}s）")
    }

    /** 单向通知（无 id）：initialized 这类握手尾包 */
    internal fun notify(method: String) = synchronized(lock) {
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        writer.write(message.toString())
        writer.newLine()
        writer.flush()
    }

    fun listTools(): List<McpToolInfo> {
        val result = request("tools/list", null, 20)
        val tools = result["tools"] as? JsonArray ?: return emptyList()
        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            McpToolInfo(
                name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                schema = obj["inputSchema"] as? JsonObject ?: buildJsonObject { put("type", "object") },
            )
        }
    }

    /** 执行工具；文本内容拼起来回灌 */
    fun callTool(name: String, arguments: JsonObject, timeoutSec: Long = 120): String {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val result = request("tools/call", params, timeoutSec)
        val content = result["content"] as? JsonArray ?: return result.toString()
        return content.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.content
            when (type) {
                "text" -> obj["text"]?.jsonPrimitive?.content
                else -> null
            }
        }.joinToString("\n").ifBlank { result.toString() }
    }

    fun close() {
        runCatching { process.destroy() }
        runCatching { process.waitFor(3, TimeUnit.SECONDS) }
        runCatching { process.destroyForcibly() }
    }

    val alive: Boolean get() = process.isAlive
}

/** 客户端：管理会话缓存与握手 */
object McpManager {
    private val sessions = ConcurrentHashMap<String, McpSession>()

    /** 取会话：缓存里的活着就直接用；否则连接（失败抛异常，调用方给用户看） */
    suspend fun session(cfg: McpServerCfg, forceReconnect: Boolean = false): McpSession {
        sessions[cfg.id]?.let { existing ->
            if (!forceReconnect && existing.alive) return existing
            existing.close()
            sessions.remove(cfg.id)
        }
        val session = connect(cfg)
        sessions[cfg.id] = session
        return session
    }

    private suspend fun connect(cfg: McpServerCfg): McpSession = withContext(Dispatchers.IO) {
        if (cfg.command.isBlank()) throw McpException("启动命令不能为空")
        val command = buildList {
            add(cfg.command)
            // 用 StringParsing 简单拆：带引号的一段是一个参数
            tokenize(cfg.args).forEach { add(it) }
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        val session = McpSession(cfg.id, process, reader, writer)

        try {
            // 握手
            val initParams = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", JsonObject(emptyMap()))
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", "Baicodex")
                        put("version", "0.1.0")
                    },
                )
            }
            session.initialize(initParams)
        } catch (t: Throwable) {
            session.close()
            throw McpException("MCP 握手失败（${cfg.displayName}）：${t.message}")
        }
        session
    }

    /** 断开全部（应用退出时调） */
    fun shutdown() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    fun cachedToolCount(cfg: McpServerCfg): Int = sessions[cfg.id]?.let {
        runCatching { it.listTools().size }.getOrDefault(-1)
    } ?: -1
}

/** MCP 握手：initialize 请求 + initialized 通知 */
internal fun McpSession.initialize(params: JsonObject) {
    request("initialize", params, 20)
    notify("notifications/initialized")
}

// tokenize：支持双引号与单引号
internal fun tokenize(input: String): List<String> {
    val tokens = ArrayList<String>()
    val sb = StringBuilder()
    var quote: Char? = null
    input.forEach { c ->
        when {
            quote != null && c == quote -> quote = null
            quote == null && (c == '"' || c == '\'') -> quote = c
            quote == null && c.isWhitespace() -> {
                if (sb.isNotEmpty()) tokens.add(sb.toString())
                sb.setLength(0)
            }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) tokens.add(sb.toString())
    return tokens
}

/** MCP 工具适配器：把 MCP 服务器的工具挂进本应用的工具表，名字加 mcp_ 前缀防撞 */
class McpTool(
    private val serverLabel: String,
    private val info: McpToolInfo,
    private val serverCfg: McpServerCfg,
) : b.ai.desktop.core.tool.Tool {
    private val toolName: String = "mcp_" + info.name.map { c ->
        if (c.isLetterOrDigit()) c.lowercaseChar() else '_'
    }.joinToString("").take(48)

    override val spec = b.ai.desktop.core.tool.ToolSpec(
        name = toolName,
        description = "[MCP:$serverLabel] ${info.description.ifBlank { info.name }}",
        parameters = info.schema,
    )

    override suspend fun run(args: JsonObject): b.ai.desktop.core.tool.ToolResult {
        return try {
            val session = McpManager.session(serverCfg)
            val output = session.callTool(info.name, args)
            b.ai.desktop.core.tool.ToolResult(true, output.ifBlank { "（无输出）" })
        } catch (t: Throwable) {
            // 进程挂了之类：重连一次再试
            val retried = runCatching {
                val session = McpManager.session(serverCfg, forceReconnect = true)
                session.callTool(info.name, args)
            }
            retried.map { b.ai.desktop.core.tool.ToolResult(true, it) }
                .getOrElse { b.ai.desktop.core.tool.ToolResult(false, "MCP 调用失败：${t.message}") }
        }
    }
}
