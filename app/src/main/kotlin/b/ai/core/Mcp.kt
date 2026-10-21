package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** 一个 MCP 服务：kind=mcp 走 JSON-RPC（端点一般是 /mcp），kind=rest 走 termux REST 桥（端点 /tools、/run） */
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val token: String = "",
    val kind: String = KIND_MCP,
    val on: Boolean = true
) {
    companion object {
        const val KIND_MCP = "mcp"
        const val KIND_REST = "rest"
    }
}

/**
 * 服务暴露的一个能力：schema 存 OpenAI function 的 parameters 原文，直接转给模型用。
 * name 保持服务端的原文（tools/call 要用它），full 是发给模型的名字（清洗过非法字符 + 加服务前缀）
 */
data class McpTool(
    val serverId: String,
    val server: String,
    val name: String,
    val desc: String,
    val schema: String
) {
    /** 服务名可能带中文/空格，压成合法工具名片段；全被洗掉就用服务 id 兜底 */
    private val prefix: String
        get() = server.replace(Regex("[^A-Za-z0-9_]"), "_").trim('_').ifBlank { serverId }

    private val safeName: String get() = name.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** 带服务前缀的工具名，避免多个服务撞名 */
    val full: String get() = "mcp_" + prefix + "_" + safeName
}

/** MCP 客户端：JSON-RPC 2.0 over HTTP，兼容 application/json 与 text/event-stream 两种响应 */
object Mcp {
    private const val KEY = "mcp_servers"
    private const val TKEY = "mcp_tools"

    private val cache = mutableStateOf<List<McpServer>>(emptyList())
    /** 已拉到的能力清单（落盘，重启不用重拉）；只有启用的服务才会下发给模型 */
    private val toolsCache = mutableStateOf<List<McpTool>>(emptyList())
    private var sp: SharedPreferences? = null
    /** 服务 id → Mcp-Session-Id，只在内存里，闪退重连会重新 initialize */
    private val sessions = HashMap<String, String>()
    private var seq = 1
    @Volatile var lastError: String = ""

    val list: List<McpServer> get() = cache.value

    /** 所有启用服务缓存下来的能力 */
    val tools: List<McpTool>
        get() {
            val onIds = cache.value.filter { it.on }.map { it.id }.toSet()
            return toolsCache.value.filter { it.serverId in onIds }
        }

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        cache.value = runCatching { parse(p.getString(KEY, null)) }.getOrDefault(emptyList())
        toolsCache.value = runCatching { parseTools(p.getString(TKEY, null)) }.getOrDefault(emptyList())
    } //读服务表与能力缓存

    fun add(name: String, url: String, token: String, kind: String): McpServer {
        val s = McpServer(UUID.randomUUID().toString().take(8), name.trim(), url.trim(), token.trim(), kind, true)
        upsert(s)
        return s
    } //加一条服务

    fun upsert(s: McpServer) {
        val exists = cache.value.any { it.id == s.id }
        cache.value = if (exists) cache.value.map { if (it.id == s.id) s else it } else cache.value + s
        save()
    } //写回一条

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        toolsCache.value = toolsCache.value.filterNot { it.serverId == id }
        sessions.remove(id)
        save()
        saveTools()
    } //删一条

    fun toggle(id: String, on: Boolean) {
        cache.value = cache.value.map { if (it.id == id) it.copy(on = on) else it }
        save()
    } //开关

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("url", s.url)
                    .put("token", s.token)
                    .put("kind", s.kind)
                    .put("on", s.on)
            )
        }
        sp?.edit()?.putString(KEY, arr.toString())?.apply()
    } //整表落盘

    private fun parse(raw: String?): List<McpServer> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            McpServer(
                o.optString("id"),
                o.optString("name"),
                o.optString("url"),
                o.optString("token"),
                o.optString("kind").ifBlank { McpServer.KIND_MCP },
                o.optBoolean("on", true)
            )
        }
    } //从 JSON 还原

    private fun saveTools() {
        val arr = JSONArray()
        toolsCache.value.forEach { t ->
            arr.put(
                JSONObject()
                    .put("sid", t.serverId)
                    .put("srv", t.server)
                    .put("name", t.name)
                    .put("desc", t.desc)
                    .put("schema", t.schema)
            )
        }
        sp?.edit()?.putString(TKEY, arr.toString())?.apply()
    } //能力缓存落盘

    private fun parseTools(raw: String?): List<McpTool> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            McpTool(
                o.optString("sid"),
                o.optString("srv"),
                o.optString("name"),
                o.optString("desc"),
                o.optString("schema").ifBlank { "{\"type\":\"object\",\"properties\":{}}" }
            )
        }
    } //从 JSON 还原能力缓存

    /** 拉一个服务的全部能力：mcp 走 tools/list，rest 走 GET /tools */
    fun listTools(s: McpServer): List<McpTool> {
        lastError = ""
        return runCatching {
            if (s.kind == McpServer.KIND_REST) restTools(s) else mcpTools(s)
        }.onFailure { lastError = it.message.orEmpty() }.getOrDefault(emptyList())
    } //能力清单

    /** 调一个能力，返回文本结果；mcp 走 tools/call，rest 只支持 run（把参数当命令） */
    fun callTool(s: McpServer, tool: String, args: JSONObject): String {
        lastError = ""
        return runCatching {
            if (s.kind == McpServer.KIND_REST) restCall(s, tool, args) else mcpCall(s, tool, args)
        }.onFailure { lastError = it.message.orEmpty() }.getOrDefault("")
    } //调用能力

    /** 探活：能取到能力清单就算连上 */
    fun ping(s: McpServer): Boolean = listTools(s).isNotEmpty()

    /** 拉一遍某个服务的能力并覆盖缓存，返回拉到的条数；失败原因留在 lastError */
    fun refresh(s: McpServer): Int {
        val got = listTools(s)
        toolsCache.value = toolsCache.value.filterNot { it.serverId == s.id } + got
        saveTools()
        return got.size
    } //刷新单个服务

    /** 拉所有启用服务，返回总条数 */
    fun refreshAll(): Int = cache.value.filter { it.on }.sumOf { refresh(it) }

    fun findTool(full: String): McpTool? = toolsCache.value.firstOrNull { it.full == full }

    fun serverOf(t: McpTool): McpServer? = cache.value.firstOrNull { it.id == t.serverId }

    //MCP 协议
    private fun mcpTools(s: McpServer): List<McpTool> {
        ensureSession(s)
        val r = rpc(s, "tools/list", JSONObject())
        val arr = r.optJSONObject("result")?.optJSONArray("tools") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            McpTool(
                s.id,
                s.name,
                o.optString("name"),
                o.optString("description"),
                o.optJSONObject("inputSchema")?.toString() ?: "{\"type\":\"object\",\"properties\":{}}"
            )
        }
    } //列 MCP 工具

    private fun mcpCall(s: McpServer, tool: String, args: JSONObject): String {
        ensureSession(s)
        val p = JSONObject().put("name", tool).put("arguments", args)
        val r = rpc(s, "tools/call", p)
        r.optJSONObject("error")?.let { return "调用失败：" + it.optString("message") }
        val res = r.optJSONObject("result") ?: return ""
        val content = res.optJSONArray("content")
        if (content != null) {
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val c = content.optJSONObject(i) ?: continue
                if (c.optString("type") == "text") sb.append(c.optString("text")).append('\n')
            }
            if (sb.isNotBlank()) return sb.toString().trimEnd()
        }
        return res.toString()
    } //调 MCP 工具

    /** initialize + notifications/initialized，拿到并记住 Mcp-Session-Id */
    private fun ensureSession(s: McpServer) {
        if (sessions.containsKey(s.id)) return
        val p = JSONObject()
            .put("protocolVersion", "2025-06-18")
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "BaiAgent").put("version", "1.1.0"))
        rpc(s, "initialize", p)
        val sid = sessions[s.id]
        val n = JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized")
        post(s, n.toString(), sid.orEmpty(), null)
    } //握手

    private fun rpc(s: McpServer, method: String, params: JSONObject): JSONObject {
        val id = seq++
        val body = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        val text = post(s, body.toString(), sessions[s.id].orEmpty(), id)
        return pickReply(text, id)
    } //发一帧 JSON-RPC

    /** 响应可能是单个 JSON，也可能是 SSE（每行 data: {...}），取 id 对得上的那帧 */
    private fun pickReply(text: String, id: Int): JSONObject {
        if (text.isBlank()) throw IllegalStateException("服务没有返回内容")
        val direct = runCatching { JSONObject(text) }.getOrNull()
        if (direct != null) return direct
        text.lineSequence().forEach { line ->
            val t = line.trim()
            if (!t.startsWith("data:")) return@forEach
            val o = runCatching { JSONObject(t.removePrefix("data:").trim()) }.getOrNull() ?: return@forEach
            if (o.optInt("id", -1) == id) return o
        }
        throw IllegalStateException("响应里没有 id=$id 的结果")
    } //取结果帧

    private fun post(s: McpServer, body: String, sid: String, expectId: Int?): String {
        val conn = (URL(s.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/event-stream")
            if (sid.isNotEmpty()) setRequestProperty("Mcp-Session-Id", sid)
            if (s.token.isNotEmpty()) setRequestProperty("Authorization", "Bearer " + s.token)
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val newSid = conn.headerFields.entries.firstOrNull { it.key.equals("Mcp-Session-Id", true) }
            ?.value?.firstOrNull().orEmpty()
        if (newSid.isNotEmpty()) sessions[s.id] = newSid
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.let { readAll(it) }.orEmpty()
            throw IllegalStateException("HTTP $code " + err.take(200))
        }
        //通知类请求（expectId 为空）不读响应体：有的服务会挂一个空的 SSE 流，读完就卡到超时
        if (expectId == null) return ""
        //SSE 响应要边读边判 id，读到目标帧立刻断开；否则服务器不关流就会一直等
        if (conn.contentType.orEmpty().contains("text/event-stream", true)) {
            return readSse(conn.inputStream, expectId)
        }
        return readAll(conn.inputStream)
    } //发 HTTP

    /** 读 SSE 到 id 对得上的那一帧为止，返回拼起来的原文给 pickReply 用 */
    private fun readSse(input: java.io.InputStream, id: Int): String {
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(input, "UTF-8")).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                sb.append(line).append('\n')
                val t = line.trim()
                if (!t.startsWith("data:")) continue
                val o = runCatching { JSONObject(t.removePrefix("data:").trim()) }.getOrNull() ?: continue
                if (o.optInt("id", -1) == id) break
            }
        }
        return sb.toString()
    } //读到目标帧

    private fun readAll(input: java.io.InputStream): String =
        BufferedReader(InputStreamReader(input, "UTF-8")).use { r -> r.readText() } //读完

    //REST 桥（termux-mcp 的 8080）
    private fun base(s: McpServer) = s.url.trimEnd('/')

    private fun restTools(s: McpServer): List<McpTool> {
        val text = get(s, base(s) + "/tools")
        val arr = JSONObject(text).optJSONArray("tools") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val f = arr.optJSONObject(i)?.optJSONObject("function") ?: return@mapNotNull null
            McpTool(
                s.id,
                s.name,
                f.optString("name"),
                f.optString("description"),
                f.optJSONObject("parameters")?.toString() ?: "{\"type\":\"object\",\"properties\":{}}"
            )
        }
    } //REST 桥的工具清单

    private fun restCall(s: McpServer, tool: String, args: JSONObject): String {
        if (tool != "run") return "该服务只有 run 能直接调用，" + tool + " 请当作技能清单使用"
        val cmd = args.optString("cmd").ifBlank { args.optString("command") }
        if (cmd.isBlank()) return "缺少 cmd 参数"
        val body = JSONObject().put("cmd", cmd).put("timeout", args.optInt("timeout", 120))
        return postPlain(s, base(s) + "/run", body.toString())
    } //REST 桥只支持 run

    private fun get(s: McpServer, url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 30_000
            if (s.token.isNotEmpty()) setRequestProperty("Authorization", "Bearer " + s.token)
        }
        val code = conn.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return readAll(conn.inputStream)
    } //GET

    private fun postPlain(s: McpServer, url: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (s.token.isNotEmpty()) setRequestProperty("Authorization", "Bearer " + s.token)
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return readAll(conn.inputStream)
    } //POST 普通 JSON
} //MCP 客户端