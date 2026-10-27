package b.ai.exec

import b.ai.agent.SubAgents
import b.ai.agent.SubOut
import b.ai.agent.SubTask
import b.ai.core.Cfg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * A2A 1.0 服务端：一个极简 HTTP 监听 + JSON-RPC。
 * 开了之后外部 A2A 客户端把任务派进本机，任务本体交给一个分智能体跑完回传。
 * 只支持 SendMessage / GetTask / ListTasks / CancelTask，不提供流式（SendStreamingMessage 直接拒绝）。
 *
 * 安全默认：外部请求一律不弹确认框。工具只有在 `Cfg.policy(工具名) == "allow"` 时才放行，
 * 其余（包括默认的 ask）全部当拒绝——不能让陌生请求把确认框刷到用户脸上。
 */
object A2aSrv {
    const val CARD_PATH = "/.well-known/agent-card.json"
    const val RPC_PATH = "/a2a"
    const val VERSION = "1.0"

    private const val ST_SUBMITTED = "TASK_STATE_SUBMITTED"
    private const val ST_WORKING = "TASK_STATE_WORKING"
    private const val ST_COMPLETED = "TASK_STATE_COMPLETED"
    private const val ST_CANCELED = "TASK_STATE_CANCELED"
    private const val ST_FAILED = "TASK_STATE_FAILED"

    /** 任务表：只留最近 30 条，长时间开着不至于把内存吃光 */
    private const val MAX_TASKS = 30

    /** 单条任务最多等多久出结果：超过就先回转 working，让调用方自己再 GetTask */
    private const val WAIT_MS = 300_000L

    private val tasks = LinkedHashMap<String, SrvTask>()
    private var server: ServerSocket? = null
    private var loop: Job? = null
    private var port = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class SrvTask(val id: String, val ctx: String) {
        @Volatile var state = ST_SUBMITTED
        @Volatile var out = ""
        @Volatile var err = ""
        var job: Job? = null
    }

    val running: Boolean get() = server != null
    val listenPort: Int get() = port

    /** 局域网 IPv4：拿不到（没连网）就退回 127.0.0.1 */
    fun lanIp(): String {
        val all = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return "127.0.0.1"
        for (nif in all) {
            if (!nif.isUp || nif.isLoopback) continue
            for (a in nif.inetAddresses) {
                if (a is Inet4Address && !a.isLoopbackAddress) return a.hostAddress ?: "127.0.0.1"
            }
        }
        return "127.0.0.1"
    } //局域网地址

    /** 对外可访问的根地址：AgentCard 和面板上都用它 */
    fun baseUrl(): String = "http://" + lanIp() + ":" + port

    fun start(p: Int): String {
        stop()
        val s = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(p))
            }
        } catch (e: Throwable) {
            return "监听 $p 端口失败：" + (e.message ?: e.javaClass.simpleName)
        }
        server = s
        port = p
        loop = scope.launch {
            while (isActive) {
                val c = runCatching { s.accept() }.getOrNull() ?: continue
                scope.launch { serve(c) }
            }
        } //accept 循环
        return "已监听 " + baseUrl()
    } //开服务

    fun stop() {
        runCatching { loop?.cancel() }
        loop = null
        runCatching { server?.close() }
        server = null
        tasks.values.forEach { runCatching { it.job?.cancel() } }
        tasks.clear()
    } //关服务

    /** 一个连接一套处理：读头、读体、路由、回包，然后关掉 */
    private suspend fun serve(client: Socket) {
        try {
            client.soTimeout = 30_000
            val input = client.getInputStream()
            val head = readHead(input) ?: return
            val line = head.first
            val headers = head.second
            val body = readBody(input, headers["content-length"]?.toIntOrNull() ?: 0)
            val parts = line.split(' ')
            val method = parts.getOrNull(0).orEmpty().uppercase()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')
            when {
                method == "GET" && path == CARD_PATH -> {
                    write(client, 200, "application/json; charset=utf-8", card().toString(), "A2A-Version: $VERSION\r\n")
                }
                method == "GET" -> {
                    write(client, 404, "text/plain; charset=utf-8", "只有 " + CARD_PATH + " 和 " + RPC_PATH)
                }
                method != "POST" -> {
                    write(client, 405, "text/plain; charset=utf-8", "只支持 GET / POST")
                }
                !authOk(headers["authorization"].orEmpty()) -> {
                    write(
                        client, 401, "application/json; charset=utf-8",
                        errorJson(null, -32600, "Bearer token 不对").toString(),
                        "WWW-Authenticate: Bearer\r\n"
                    )
                }
                else -> {
                    val req = runCatching { JSONObject(body) }.getOrNull()
                    val res = if (req == null) errorJson(null, -32700, "请求体不是合法 JSON") else dispatch(req)
                    write(client, 200, "application/json; charset=utf-8", res.toString())
                }
            }
        } catch (e: Throwable) {
            runCatching { write(client, 500, "text/plain; charset=utf-8", "服务端异常：" + (e.message ?: "")) }
        } finally {
            runCatching { client.close() }
        }
    } //处理一个请求

    /** AgentCard：name / 接口地址 / 能力 / 技能 / 鉴权方式 */
    private fun card(): JSONObject = JSONObject()
        .put("protocolVersion", VERSION)
        .put("name", "BaiAgent")
        .put("description", "本机的 BaiAgent：文本任务交给一个分智能体执行，能读写文件、执行命令、跑工作流")
        .put(
            "supportedInterfaces",
            JSONArray().put(
                JSONObject()
                    .put("url", baseUrl() + RPC_PATH)
                    .put("protocolBinding", "JSONRPC")
                    .put("protocolVersion", VERSION)
            )
        )
        .put("capabilities", JSONObject().put("streaming", false).put("pushNotifications", false))
        .put("defaultInputModes", JSONArray().put("text/plain"))
        .put("defaultOutputModes", JSONArray().put("text/plain"))
        .put(
            "skills",
            JSONArray().put(
                JSONObject()
                    .put("id", "bai-agent")
                    .put("name", "BaiAgent")
                    .put("description", "给一条中文任务，本机的分智能体用工具做完后回一段结论")
                    .put("tags", JSONArray().put("chat").put("assistant").put("tools").put("android"))
            )
        )
        .put(
            "securitySchemes",
            JSONObject().put(
                "bearerAuth",
                JSONObject().put(
                    "httpAuthSecurityScheme",
                    JSONObject().put("scheme", "Bearer").put("bearerFormat", "BaiAgent token")
                )
            )
        )

    /** Bearer 校验：Cfg.a2aToken 为空表示不校验（只建议在可信局域网） */
    private fun authOk(header: String): Boolean {
        val need = Cfg.a2aToken.trim()
        if (need.isEmpty()) return true
        return header.equals("Bearer " + need, true)
    } //鉴权

    /** 方法分发：只认四个 A2A 方法，其余报 method not found */
    private suspend fun dispatch(req: JSONObject): JSONObject {
        val id = req.opt("id")
        val method = req.optString("method")
        val params = req.optJSONObject("params") ?: JSONObject()
        return when (method) {
            "SendMessage" -> sendMessage(id, params)
            "GetTask" -> {
                val t = lockTasks { tasks[params.optString("id")] }
                if (t == null) errorJson(id, -32602, "没有这个任务：" + params.optString("id"))
                else okJson(id, JSONObject().put("task", taskJson(t)))
            }
            "ListTasks" -> listTasks(id, params)
            "CancelTask" -> cancelTask(id, params)
            "SendStreamingMessage", "SubscribeToTask" ->
                errorJson(id, -32601, "本机不提供流式，请用 SendMessage + GetTask 轮询")
            else -> errorJson(id, -32601, "不支持的方法：$method")
        }
    } //方法分发

    private suspend fun sendMessage(id: Any?, p: JSONObject): JSONObject {
        val msg = p.optJSONObject("message") ?: return errorJson(id, -32602, "params.message 缺失")
        val role = msg.optString("role")
        if (role.isNotBlank() && role != "ROLE_USER") return errorJson(id, -32602, "只接受 ROLE_USER 的消息")
        val text = textOf(msg.optJSONArray("parts"))
        if (text.isBlank()) return errorJson(id, -32602, "只接受非空文本 parts")
        val t = SrvTask(
            UUID.randomUUID().toString(),
            msg.optString("contextId").ifBlank { UUID.randomUUID().toString() }
        )
        lockTasks { tasks[t.id] = t }
        trimTasks()
        run(t, text)
        //默认同步等结果：超过 WAIT_MS 就先回 working，让调用方自己轮询 GetTask
        val now = p.optJSONObject("configuration")?.optBoolean("returnImmediately", false) ?: false
        if (!now) withTimeoutOrNull(WAIT_MS) { t.job?.join() }
        return okJson(id, JSONObject().put("task", taskJson(t)))
    } //SendMessage

    private suspend fun listTasks(id: Any?, p: JSONObject): JSONObject {
        val ctx = p.optString("contextId")
        val size = p.optInt("pageSize", 20).coerceIn(1, 100)
        val all = lockTasks { tasks.values.toList() }
        val hit = all.filter { ctx.isBlank() || it.ctx == ctx }.takeLast(size)
        val arr = JSONArray()
        hit.forEach { arr.put(taskJson(it)) }
        return okJson(id, JSONObject().put("tasks", arr))
    } //ListTasks

    private suspend fun cancelTask(id: Any?, p: JSONObject): JSONObject {
        val t = lockTasks { tasks[p.optString("id")] } ?: return errorJson(id, -32602, "没有这个任务")
        if (t.state == ST_COMPLETED || t.state == ST_FAILED) {
            return okJson(id, JSONObject().put("task", taskJson(t)))
        }
        runCatching { t.job?.cancel() }
        t.state = ST_CANCELED
        t.err = "任务已被调用方取消"
        return okJson(id, JSONObject().put("task", taskJson(t)))
    } //CancelTask

    /** 交给分智能体跑：外部请求不弹确认，工具策略必须是 allow 才放行 */
    private fun run(t: SrvTask, text: String) {
        t.state = ST_WORKING
        t.job = scope.launch {
            val r = runCatching {
                SubAgents.runOne(SubTask("A2A", text), 6) { name, _, _ -> Cfg.policy(name) == "allow" }
            }.getOrElse { SubOut("A2A", false, "出错：" + (it.message ?: it.javaClass.simpleName), 0) }
            t.out = r.text
            t.err = if (r.ok) "" else r.text
            t.state = if (r.ok) ST_COMPLETED else ST_FAILED
        }
    } //执行任务

    private fun taskJson(t: SrvTask): JSONObject {
        val o = JSONObject().put("id", t.id).put("contextId", t.ctx)
        o.put("status", JSONObject().put("state", t.state))
        val body = if (t.err.isNotBlank()) t.err else t.out
        if (body.isNotBlank()) {
            o.put(
                "artifacts",
                JSONArray().put(
                    JSONObject().put("artifactId", t.id + "-result")
                        .put("parts", JSONArray().put(JSONObject().put("text", body)))
                )
            )
        }
        return o
    } //任务快照

    /** 只认 {text} 一种 part：A2A 里别的形态（文件、结构化）本机不收 */
    private fun textOf(arr: JSONArray?): String {
        if (arr == null) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i)?.optString("text") ?: continue
            if (t.isBlank()) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(t)
        }
        return sb.toString().trim()
    } //文本取用

    private fun <T> lockTasks(block: () -> T): T = synchronized(tasks) { block() }

    private fun trimTasks() = lockTasks {
        while (tasks.size > MAX_TASKS) {
            val first = tasks.keys.firstOrNull() ?: break
            tasks.remove(first)
        }
        Unit
    } //只留最近若干条

    /** 读到空行为止：返回请求行和头，头名统一小写 */
    private fun readHead(input: InputStream): Pair<String, Map<String, String>>? {
        val buf = ByteArrayOutputStream(1024)
        var tail = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            tail = (tail shl 8) or b
            if (tail == 0x0d0a0d0a) break
            if (buf.size() > 64 * 1024) return null
        }
        val lines = buf.toString("UTF-8").split("\r\n")
        val line = lines.firstOrNull().orEmpty()
        val headers = HashMap<String, String>()
        lines.drop(1).forEach { l ->
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        return line to headers
    } //读请求头

    private fun readBody(input: InputStream, len: Int): String {
        if (len <= 0) return ""
        if (len > 1 shl 20) return ""
        val body = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = runCatching { input.read(body, read, len - read) }.getOrDefault(-1)
            if (n <= 0) break
            read += n
        }
        return String(body, 0, read, Charsets.UTF_8)
    } //读请求体

    private fun write(client: Socket, code: Int, mime: String, body: String, extra: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 " + code + " " + if (code == 200) "OK" else "Error" +
            "\r\nContent-Type: " + mime +
            "\r\nContent-Length: " + bytes.size +
            "\r\nConnection: close\r\n" + extra + "\r\n"
        val os: OutputStream = client.getOutputStream()
        os.write(head.toByteArray(Charsets.UTF_8))
        os.write(bytes)
        os.flush()
    } //回包

    private fun okJson(id: Any?, result: JSONObject): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("result", result)

    private fun errorJson(id: Any?, code: Int, msg: String): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL)
            .put("error", JSONObject().put("code", code).put("message", msg))
} //A2A 服务端
