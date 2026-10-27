package b.ai.net

import b.ai.core.A2aNode
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 一次 A2A 调用的结果：ok 是协议层成败，text 是给人/模型看的正文，json 是原始回包 */
data class A2aRes(val ok: Boolean, val text: String, val json: JSONObject? = null)

/**
 * A2A 1.0 客户端。方法名、角色、任务状态都按 A2A 1.0 的 proto 枚举写法
 * （SendMessage / GetTask / ListTasks / CancelTask、ROLE_USER / ROLE_AGENT、TASK_STATE_*），
 * 与 Operit 那套实现互通。流式（SendStreamingMessage）不支持，一律用 SendMessage + 轮询 GetTask。
 */
object A2A {
    const val CARD_PATH = "/.well-known/agent-card.json"
    const val VERSION = "1.0"

    /** AgentCard 里没给 JSON-RPC 地址时的兜底路径 */
    private const val DEFAULT_RPC = "/a2a"

    const val ROLE_USER = "ROLE_USER"
    const val ROLE_AGENT = "ROLE_AGENT"

    const val ST_SUBMITTED = "TASK_STATE_SUBMITTED"
    const val ST_WORKING = "TASK_STATE_WORKING"
    const val ST_COMPLETED = "TASK_STATE_COMPLETED"
    const val ST_CANCELED = "TASK_STATE_CANCELED"
    const val ST_FAILED = "TASK_STATE_FAILED"
    const val ST_REJECTED = "TASK_STATE_REJECTED"

    /** 已解析出来的 JSON-RPC 地址：同一对端只拉一次 card */
    private val endpoints = HashMap<String, String>()

    /** 清掉 endpoint 缓存：改了对端地址或它换了 card 时用 */
    fun forget() {
        endpoints.clear()
    } //清缓存

    private fun isTerminal(state: String): Boolean =
        state == ST_COMPLETED || state == ST_CANCELED || state == ST_FAILED || state == ST_REJECTED //终态判定

    /** 拉 AgentCard：GET 基地址 + /.well-known/agent-card.json */
    fun card(node: A2aNode): A2aRes {
        val base = baseUrl(node.url)
        if (base.isBlank()) return A2aRes(false, "对端地址是空的")
        val r = call("GET", base + CARD_PATH, node.token, null, 15_000)
        if (!r.ok) return r
        val j = r.json ?: return A2aRes(false, "AgentCard 不是合法 JSON：" + r.text.take(200))
        val ep = rpcFromCard(j)
        if (ep.isNotBlank()) endpoints[base] = ep
        return A2aRes(true, describe(j, node), j)
    } //拉 AgentCard

    /** card 摘要：名字、说明、RPC 地址、能力、技能，一行一项，给模型看 */
    fun describe(j: JSONObject, node: A2aNode): String {
        val sb = StringBuilder()
        sb.append("对端：").append(j.optString("name").ifBlank { node.name }).append("（").append(node.url).append("）\n")
        sb.append("协议：A2A ").append(j.optString("protocolVersion").ifBlank { "未声明" })
        val caps = j.optJSONObject("capabilities")
        if (caps != null) {
            val on = ArrayList<String>()
            if (caps.optBoolean("streaming", false)) on.add("流式")
            if (caps.optBoolean("pushNotifications", false)) on.add("推送")
            sb.append("，能力：").append(if (on.isEmpty()) "普通请求-响应" else on.joinToString("、"))
        }
        sb.append('\n')
        val desc = j.optString("description")
        if (desc.isNotBlank()) sb.append("说明：").append(desc).append('\n')
        val ep = rpcFromCard(j)
        sb.append("JSON-RPC 地址：").append(ep.ifBlank { "未声明（回落到 " + baseUrl(node.url) + DEFAULT_RPC + "）" }).append('\n')
        val skills = j.optJSONArray("skills")
        if (skills != null && skills.length() > 0) {
            sb.append("技能：\n")
            for (i in 0 until skills.length()) {
                val s = skills.optJSONObject(i) ?: continue
                sb.append(" - ").append(s.optString("id")).append(" / ").append(s.optString("name"))
                val tags = s.optJSONArray("tags")
                if (tags != null && tags.length() > 0) {
                    sb.append(" [").append((0 until tags.length()).joinToString(",") { tags.optString(it) }).append("]")
                }
                val sd = s.optString("description")
                if (sd.isNotBlank()) sb.append("：").append(sd)
                sb.append('\n')
            }
        }
        return sb.toString().trim()
    } //card 摘要

    /** 发一条文本任务：非终态就轮询，直到出结果或超时 */
    fun send(node: A2aNode, text: String, waitMs: Long = 120_000L): A2aRes {
        if (text.isBlank()) return A2aRes(false, "任务内容是空的")
        val ep = endpoint(node)
        if (ep.isBlank()) return A2aRes(false, "拿不到 " + node.url + " 的 JSON-RPC 地址")
        val msg = JSONObject()
            .put("messageId", java.util.UUID.randomUUID().toString())
            .put("role", ROLE_USER)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))
        val params = JSONObject().put("message", msg)
        val r = rpc(ep, node.token, "SendMessage", params, 40_000)
        if (!r.ok) return r
        var task = r.json?.optJSONObject("result")?.optJSONObject("task")
            ?: r.json?.optJSONObject("result")
            ?: return A2aRes(false, "回包里没有 task：" + r.text.take(300))
        val id = task.optString("id")
        if (id.isBlank()) return A2aRes(false, "回包里的 task 没有 id：" + r.text.take(300))
        val deadline = System.currentTimeMillis() + waitMs
        //轮询期间每 700ms 问一次，别把对端打爆
        while (!isTerminal(task.optJSONObject("status")?.optString("state").orEmpty()) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(700)
            val g = getTask(node, id)
            if (!g.ok) return g
            val t = g.json?.optJSONObject("result")?.let { it.optJSONObject("task") ?: it } ?: g.json
            if (t != null) task = t
        }
        return A2aRes(true, render(task), task)
    } //发任务并等结果

    /** GetTask：按 id 取任务快照 */
    fun getTask(node: A2aNode, id: String): A2aRes {
        if (id.isBlank()) return A2aRes(false, "任务 id 是空的")
        val ep = endpoint(node)
        if (ep.isBlank()) return A2aRes(false, "拿不到 " + node.url + " 的 JSON-RPC 地址")
        val r = rpc(ep, node.token, "GetTask", JSONObject().put("id", id), 30_000)
        if (!r.ok) return r
        val task = r.json?.optJSONObject("result")?.let { it.optJSONObject("task") ?: it }
        return A2aRes(true, if (task == null) r.text.take(300) else render(task), task)
    } //取任务

    /** ListTasks：列对端最近的任务 */
    fun listTasks(node: A2aNode, contextId: String = ""): A2aRes {
        val ep = endpoint(node)
        if (ep.isBlank()) return A2aRes(false, "拿不到 " + node.url + " 的 JSON-RPC 地址")
        val p = JSONObject().put("pageSize", 20)
        if (contextId.isNotBlank()) p.put("contextId", contextId)
        val r = rpc(ep, node.token, "ListTasks", p, 30_000)
        if (!r.ok) return r
        val res = r.json?.optJSONObject("result")
        val arr = res?.optJSONArray("tasks") ?: res?.optJSONArray("items")
        if (arr == null || arr.length() == 0) return A2aRes(true, "对端没有任务记录。", res)
        val sb = StringBuilder("共 ").append(arr.length()).append(" 条：\n")
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            sb.append("- ").append(t.optString("id")).append("  ")
                .append(t.optJSONObject("status")?.optString("state").orEmpty()).append('\n')
        }
        return A2aRes(true, sb.toString().trim(), res)
    } //列任务

    /** CancelTask：让对端取消任务 */
    fun cancelTask(node: A2aNode, id: String): A2aRes {
        if (id.isBlank()) return A2aRes(false, "任务 id 是空的")
        val ep = endpoint(node)
        if (ep.isBlank()) return A2aRes(false, "拿不到 " + node.url + " 的 JSON-RPC 地址")
        val r = rpc(ep, node.token, "CancelTask", JSONObject().put("id", id), 30_000)
        if (!r.ok) return r
        val task = r.json?.optJSONObject("result")?.let { it.optJSONObject("task") ?: it }
        return A2aRes(true, if (task == null) r.text.take(300) else render(task), task)
    } //取消任务

    /** task 快照压成一段文本：状态 + 产物正文，失败时带上错误消息 */
    fun render(task: JSONObject): String {
        val sb = StringBuilder()
        val state = task.optJSONObject("status")?.optString("state").orEmpty()
        sb.append("任务 ").append(task.optString("id")).append("，状态 ").append(state.ifBlank { "未知" })
        val ctx = task.optString("contextId")
        if (ctx.isNotBlank()) sb.append("，上下文 ").append(ctx)
        sb.append('\n')
        val body = partsText(task.optJSONArray("artifacts"))
        if (body.isNotBlank()) sb.append(body)
        val st = task.optJSONObject("status")
        val msg = st?.optJSONObject("message")
        if (msg != null) {
            val m = partsText(JSONArray().put(msg))
            if (m.isNotBlank()) sb.append(if (body.isBlank()) "" else "\n\n").append("状态消息：").append(m)
        }
        return sb.toString().trim()
    } //任务渲染

    /** 从 artifacts / message 里把 text part 拼起来：只认 {text} 一种 part */
    fun partsText(arr: JSONArray?): String {
        if (arr == null) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val parts = o.optJSONArray("parts") ?: continue
            for (k in 0 until parts.length()) {
                val t = parts.optJSONObject(k)?.optString("text") ?: continue
                if (t.isBlank()) continue
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
            }
        }
        return sb.toString().trim()
    } //正文拼接

    /** 拿 JSON-RPC 地址：先读缓存，再拉 card，都不行就回落到基地址 + /a2a */
    private fun endpoint(node: A2aNode): String {
        val base = baseUrl(node.url)
        if (base.isBlank()) return ""
        endpoints[base]?.let { return it }
        val r = call("GET", base + CARD_PATH, node.token, null, 12_000)
        val ep = r.json?.let { rpcFromCard(it) }.orEmpty()
        val out = if (ep.isNotBlank()) ep else base + DEFAULT_RPC
        endpoints[base] = out
        return out
    } //解析 RPC 地址

    private fun rpcFromCard(card: JSONObject): String {
        val arr = card.optJSONArray("supportedInterfaces")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("url")
                if (u.isNotBlank()) return u
            }
        }
        //老写法：card 里直接给 url 或 endpoints
        val direct = card.optString("url")
        if (direct.isNotBlank()) return direct
        val eps = card.optJSONArray("endpoints")
        if (eps != null && eps.length() > 0) {
            return eps.optJSONObject(0)?.optString("url").orEmpty()
        }
        return ""
    } //card 取地址

    /** JSON-RPC 调用：统一处理 HTTP 状态、JSON-RPC error、回包解析 */
    private fun rpc(url: String, token: String, method: String, params: JSONObject, timeoutMs: Int): A2aRes {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", System.currentTimeMillis().toString())
            .put("method", method)
            .put("params", params)
            .toString()
        return call("POST", url, token, body, timeoutMs)
    } //JSON-RPC 调用

    private fun call(method: String, url: String, token: String, body: String?, timeoutMs: Int): A2aRes {
        val conn = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull()
            ?: return A2aRes(false, "地址不合法：$url")
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("A2A-Version", VERSION)
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer " + token)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            val json = if (raw.isBlank()) null else runCatching { JSONObject(raw) }.getOrNull()
            //JSON-RPC 层错误比 HTTP 状态更准确，先看它
            val err = json?.optJSONObject("error")
            if (err != null) {
                return A2aRes(
                    false,
                    "对端报错 " + err.optInt("code") + "：" + err.optString("message").ifBlank { "（无消息）" }
                )
            }
            when {
                code !in 200..299 -> A2aRes(false, "HTTP " + code + "：" + raw.take(300))
                else -> A2aRes(true, raw, json)
            }
        } catch (e: Throwable) {
            A2aRes(false, "请求失败：" + (e.message ?: e.javaClass.simpleName))
        } finally {
            runCatching { conn.disconnect() }
        }
    } //真正发请求

    private fun baseUrl(url: String): String = url.trim().trimEnd('/')
} //A2A 客户端