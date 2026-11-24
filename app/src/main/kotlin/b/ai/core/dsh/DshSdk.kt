package b.ai.core.dsh

import b.ai.App
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.log.Logs
import b.ai.device.shell.local.Linux
import b.ai.device.shell.root.Root
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * DshSdk.kt — dsh SDK 直连客户端（无 UI 内核）
 *
 * 作用：
 * 把官方 dsh 以 `--profile sdk` 起成**没有界面**的 harness 运行时（stdout 只有换行分隔 JSON-RPC），
 * 由 Baicodex 自己的 chat 当客户端驱动它。插件（工具/模型适配/上下文压缩/沙箱/审批）全在 dsh 侧生效，
 * 界面全部由 Compose 画，不加载 dsh 的 Web UI。
 *
 * 协议（实测 dsh 0.2.2-rc.5）：
 * - 客户端 → 服务端：initialize / session/query / session/prompt / session/wait / session/resume
 *                    / session/working-directory{get,set} / shutdown
 * - 服务端 → 客户端：approval/request、user-question/ask（需要客户端应答）
 *                    session.event、session.status、session.query.completed、session.*（通知）
 * - 注意：协议里**没有** tools/list、tool/execute，工具只能由 dsh 的 agent 循环自己调
 *
 * 线程模型：
 * - 一个 reader 协程独占 stdout，按行解析，按 id 回填 pending，无 id 的当通知分发
 * - 写 stdin 加锁，保证不会被两行拼在一起
 */
object DshSdk {
    private const val TAG = "dsh-sdk"

    /** dsh 全局安装位置（由 DshCore 自举的 Node 24 提供） */
    private const val NODE_DIR = "/opt/node24"
    private const val DSH_JS = "/opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js"

    /** dsh 支持的 provider 路由名：SDK initialize 必须给一个已注册的路由 */
    const val PROVIDER = "deepseek-official"

    /** 单次请求超时：initialize 要挂载适配器，冷启动比后续方法慢 */
    private const val INIT_TIMEOUT_MS = 120_000L
    private const val CALL_TIMEOUT_MS = 60_000L

    @Volatile private var proc: Process? = null
    @Volatile private var sink: Writer? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var running = false
    @Volatile private var lastError = ""

    /** 最近一条非 JSON 的 stdout 噪音（dsh 的人类日志），仅诊断用，不算错误 */
    @Volatile private var lastNoise = ""

    /** 自动审批结论：P2 接 Compose 弹窗后由 UI 改写（allowed-once / rejected / cancelled） */
    @Volatile var autoApproveOutcome: String = "allowed-once"

    /** 当前会话 id（SDK 侧标识，首次 query 时生成） */
    @Volatile var sessionId: String = newSessionId()
        private set

    /** 通知回调：UI 侧接它渲染流式事件；P0 只往日志仓里写 */
    @Volatile var onNotify: ((method: String, params: JSONObject) -> Unit)? = null

    private val ids = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
    private val writeLock = Any()

    /** 生成会话 id：dsh 用未知 id 自动建会话，所以客户端自己造即可 */
    fun newSessionId(): String = java.util.UUID.randomUUID().toString()

    /** dsh 的 deepseek-official 路由内置 catalog 的模型 id（选第一个作兜底；另一个是 deepseek-v4-pro） */
    const val MODEL_FALLBACK = "deepseek-flash"

    /** 沙箱/审批模式：proot 里没有 workspace-write 后端，只能 full-access（见 cordis.patch.yml） */
    const val PERMISSION_MODE = "danger-full-access"

    /**
     * 启动 SDK 运行时并完成 initialize。
     * @param overrideModel 覆盖模型 id（null = 用 App 配置里的 model；配置项在官方端点不存在时请显式传 MODEL_FALLBACK）
     * @param onLog 阶段日志（开发者模式里实时回贴）
     */
    suspend fun start(overrideModel: String? = null, onLog: (String) -> Unit = {}): Boolean {
        fun log(s: String) {
            Logs.i(TAG, s)
            onLog(s)
        }
        if (running && proc?.isAlive == true) {
            log("已在运行，复用")
            return true
        }
        if (!Linux.installed()) {
            lastError = "Ubuntu 环境未安装：设置 → 扩展 → Linux 环境 里先装"
            log(lastError)
            return false
        }
        if (!Linux.existsIn("opt/node24/bin/node")) {
            lastError = "缺少 Node 24：先跑一次「dsh 自检」把 Node 与 dsh 装上"
            log(lastError)
            return false
        }

        //模型路由：dsh 的 key 优先级是 .credentials.yaml > 进程环境，所以这里只用环境兜底
        val key = Cfg.apiKey.trim()
        val base = baseUrlForDsh(Cfg.baseUrl)
        //模型 id：显式传入 > App 配置 > 官方兜底（配置里的 id 在官方端点不存在时会 404）
        val model = (overrideModel ?: Cfg.model).trim().ifBlank { MODEL_FALLBACK }
        log("provider=$PROVIDER model=$model base=${base.ifBlank { "(官方默认)" }} key=${if (key.isBlank()) "缺失" else "已注入 ${key.length} 位"}")

        //env -i 清宿主环境，PATH 前置 Node 24；DEEPSEEK_* 是我们的模型通道
        val inner = ArrayList(listOf(
            "/usr/bin/env", "-i", "HOME=/root",
            "PATH=$NODE_DIR/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8", "TERM=dumb"
        ))
        if (key.isNotBlank()) inner += "DEEPSEEK_API_KEY=$key"
        if (base.isNotBlank()) inner += "DEEPSEEK_BASE_URL=$base"
        //沙箱模式：proot 里没有 workspace-write 的后端（bwrap 不可用），bash 会被直接拒掉。
        //dsh-base 的 cordis.patch.yml 把这个环境变量同时喂给 sandbox-policy 与 approval 两行：
        //danger-full-access → 沙箱放行 + 审批策略 never（不必再走 approval/request）
        inner += "DSH_PERMISSION_MODE=$PERMISSION_MODE"
        inner += listOf(
            "$NODE_DIR/bin/node", DSH_JS, "--profile", "sdk"
        )

        val launch = Linux.launch(inner) ?: run {
            lastError = "缺少 proot：APK 里的 libproot.so 没解压出来"
            log(lastError)
            return false
        }
        val p = spawn(launch) ?: run {
            lastError = "proot 进程启动失败（检查 Root / Shizuku 通道）"
            log(lastError)
            return false
        }
        proc = p
        sink = OutputStreamWriter(p.outputStream, Charsets.UTF_8)
        running = true
        lastError = ""
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope?.launch { readLoop(p) }
        log("进程已起 pid=${runCatching { pidOf(p) }.getOrDefault(-1)}，开始 initialize…")

        //initialize：挂载适配器 + 解析模型路由，成功前 dsh 不接受任何 prompt
        val res = call("initialize", JSONObject().apply {
            put("cwd", "/root")
            put("provider", PROVIDER)
            put("model", model)
            put("approvalRequests", true) //审批走我们的弹窗，不让 dsh 自己放行
            put("capabilities", JSONObject().put("userQuestions", true))
        }, INIT_TIMEOUT_MS)

        if (res == null) {
            //失败原因通常是「没有 API key」或模型路由不可用，reader 已把错误写进 lastError
            log("initialize 失败：$lastError")
            stop()
            return false
        }
        log("initialize 成功：${res.optJSONObject("serverInfo")}")
        return true
    } //启动
    /** 发一条用户输入：走 session/query（支持原生命令 /compact /permission /plan /goal） */
    suspend fun query(text: String, onLog: (String) -> Unit = {}): Boolean = queryIn(sessionId, text, onLog)

    /**
     * 用指定会话 id 发一条：chat 引擎按 Baicodex 会话各用一个 dsh 会话，
     * 所以不能只认对象上那一个 sessionId。
     */
    suspend fun queryIn(session: String, text: String, onLog: (String) -> Unit = {}): Boolean {
        if (!running) {
            onLog("未启动")
            return false
        }
        val res = call("session/query", JSONObject().apply {
            put("sessionId", session)
            put("contentBlocks", org.json.JSONArray().put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            }))
        }, CALL_TIMEOUT_MS) ?: return false
        onLog("已入队：${res.optString("kind")} ${res.optString("messageId")}")
        return true
    } //发送（指定会话）

    /**
     * 恢复磁盘上的历史会话。协议规定「不存在 / 已归本客户端所有」都会被拒，
     * 所以每个进程里每个会话只尝试一次，失败就当它是全新的（query 会懒建）。
     */
    suspend fun resume(session: String): Boolean = call("session/resume", JSONObject().apply {
        put("sessionId", session)
    }, CALL_TIMEOUT_MS) != null

    /** 切会话的工作目录（proot 里 /sdcard 与 /storage 与宿主同路径，Android 路径可直接用） */
    suspend fun setWorkingDir(session: String, path: String): String? {
        val res = call("session/working-directory/set", JSONObject().apply {
            put("sessionId", session)
            put("path", path)
        }, CALL_TIMEOUT_MS) ?: return null
        return res.optString("cwd").ifBlank { null }
    } //切工作目录


    /** 回审批：outcome 只能是 allowed-once / rejected / cancelled */
    fun answerApproval(id: Long, outcome: String) {
        sendRaw(JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", JSONObject().put("outcome", outcome))
        })
    } //审批应答

    /** 停：先礼后兵（shutdown → 关 stdin → destroy） */
    fun stop() {
        runCatching {
            sendRaw(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", ids.getAndIncrement())
                put("method", "shutdown")
            })
        }
        runCatching { sink?.close() }
        sink = null
        runCatching { proc?.destroy() }
        proc = null
        running = false
        lastError = ""
        lastNoise = ""
        pending.values.forEach { it.cancel() }
        pending.clear()
        scope?.cancel()
        scope = null
        Logs.i(TAG, "已停止")
    } //停止

    fun isRunning(): Boolean = running && proc?.isAlive == true
    fun error(): String = lastError

    /** 最近一条 stdout 噪音（dsh 的人类日志），只用于诊断，不代表失败 */
    fun noise(): String = lastNoise

    //region 内部：进程 / 协议

    /** 起 proot 进程：Root 通道直接用 su（untrusted_app 域里 proot 常起不来） */
    private fun spawn(launch: Linux.Launch): Process? {
        if (Cfg.workMode == b.ai.core.model.chat.WorkMode.ROOT) {
            Root.suPath()?.let { su ->
                runCatching {
                    return ProcessBuilder(su, "-c", launch.cmdline).redirectErrorStream(true).start()
                }
            }
        }
        val local = runCatching {
            ProcessBuilder(launch.argv).redirectErrorStream(true).apply {
                environment().putAll(launch.env)
            }.start()
        }.getOrNull()
        if (local != null) {
            val dead = runCatching { local.waitFor(1500, TimeUnit.MILLISECONDS) }.getOrDefault(true)
            if (!dead) return local
            Logs.e(TAG, "App 身份起不来 proot，改走 Root 通道")
        }
        val su = Root.suPath() ?: return null
        return runCatching {
            ProcessBuilder(su, "-c", launch.cmdline).redirectErrorStream(true).start()
        }.getOrNull()
    } //起进程

    private fun pidOf(p: Process): Long = runCatching {
        Process::class.java.getMethod("pid").invoke(p) as Long
    }.getOrDefault(-1L)

    /** stdout 读循环：一行一个 JSON；有 id 回填 pending，无 id 当通知 */
    private fun readLoop(p: Process) {
        val r: BufferedReader = runCatching { p.inputStream.bufferedReader(Charsets.UTF_8) }.getOrElse {
            lastError = "读 stdout 失败：${it.message}"
            running = false
            return
        }
        r.use { br ->
            while (true) {
                val line = runCatching { br.readLine() }.getOrNull() ?: break //流断了
                if (line.isBlank()) continue
                val o = runCatching { JSONObject(line) }.getOrNull()
                if (o == null) {
                    //非 JSON 行：dsh 在 sdk profile 下仍会往 stdout 打人类可读日志
                    //（如 [llm-deepseek-api-key] model listing ... loaded 2 model(s)），
                    //这类行不是错误，只进日志；绝不能写 lastError，否则 error() 永远返回噪音
                    Logs.d(TAG, line.take(600))
                    lastNoise = line.take(300)
                    continue
                }
                val id = if (o.has("id") && !o.isNull("id")) o.optLong("id", -1L) else -1L
                val hasId = o.has("id") && !o.isNull("id")
                when {
                    //server→client 请求（approval/request、user-question/ask）：带 id 且带 method。
                    //必须回一个 result，否则服务器那一侧永远等不到答复，工具调用就卡死在这。
                    hasId && o.has("method") -> answerServerRequest(
                        o.opt("id"), o.optString("method"), o.optJSONObject("params") ?: JSONObject()
                    )
                    id >= 0 -> {
                        val d = pending.remove(id)
                        if (d != null) d.complete(o)
                    } //是应答
                    o.has("method") -> dispatch(o.optString("method"), o.optJSONObject("params") ?: JSONObject())
                    else -> Logs.d(TAG, "未识别帧：${line.take(300)}")
                }
            }
        }
        //读循环退出 = 进程没了；把错误回填给所有等待者，避免挂死
        if (running) {
            running = false
            if (lastError.isBlank()) lastError = "dsh 进程已退出"
            Logs.e(TAG, "readLoop 结束：$lastError")
        }
        pending.values.forEach { it.complete(JSONObject().put("[closed]", true)) }
    } //读循环

    /** 应答 server→client 请求：把原 id 原样回传（类型不动，数字/字符串都安全） */
    private fun answerServerRequest(id: Any?, method: String, params: JSONObject) {
        val result = when (method) {
            //审批：开发者阶段一律放行一次（P2 接 Compose 弹窗后改为等用户点击）
            "approval/request" -> {
                Logs.w(TAG, "审批请求 tool=${params.optString("toolName")} reason=${params.optString("reason")}")
                JSONObject().put("outcome", autoApproveOutcome)
            } //审批
            //结构化提问：暂不回答案，直接取消，让模型自己继续（P2 接选择器）
            "user-question/ask" -> {
                Logs.w(TAG, "提问请求 ${params.optJSONArray("questions")?.length() ?: 0} 条 → 取消")
                JSONObject().put("cancelled", true)
            } //提问
            else -> {
                Logs.w(TAG, "未知 server 请求 $method，回空 result 避免卡死")
                JSONObject()
            } //未知
        }
        sendRaw(JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        })
        runCatching { onNotify?.invoke(method, params) }
    } //应答服务端请求

    /** 通知分发：事件写日志（server→client 请求走 answerServerRequest） */
    private fun dispatch(method: String, params: JSONObject) {
        when (method) {
            "session.event" -> {
                val ev = params.optJSONObject("event") ?: JSONObject()
                val type = ev.optString("type")
                val brief = briefEvent(type, ev.optJSONObject("data") ?: JSONObject())
                if (brief.isNotBlank()) Logs.i(TAG, "event $type: $brief")
            }
            "session.status" -> Logs.i(TAG, "status=${params.optString("status")}")
            "session.query.completed" -> Logs.i(
                TAG, "query.completed reason=${params.optString("finishReason")} " +
                    "text=${params.optString("finalResponse").take(200)}"
            )
            "approval/request" -> Logs.w(
                TAG, "审批请求 tool=${params.optString("toolName")} reason=${params.optString("reason")}"
            )
            "user-question/ask" -> Logs.w(TAG, "提问请求 ${params.optJSONArray("questions")?.length() ?: 0} 条")
            else -> Logs.d(TAG, "notify $method")
        }
        runCatching { onNotify?.invoke(method, params) }
    } //通知分发

    /** 事件压缩成一行日志：流式 chunk 只留有效文本，避免日志爆炸 */
    private fun briefEvent(type: String, d: JSONObject): String = when (type) {
        "assistant/attempt" -> {
            val sb = StringBuilder()
            val stream = d.optJSONArray("stream") ?: org.json.JSONArray()
            for (i in 0 until stream.length()) {
                val ch = stream.optJSONObject(i)?.optJSONObject("chunk") ?: continue
                when (ch.optString("type")) {
                    "text" -> sb.append(ch.optString("text"))
                    "finish" -> sb.append("[finish ").append(ch.optJSONObject("reason")?.optString("kind")).append(']')
                    "thinking" -> sb.append("[think]")
                }
            }
            sb.toString().take(300)
        }
        "system/message", "user/message" -> d.optJSONArray("content")?.let { arr ->
            arr.optJSONObject(0)?.optString("text").orEmpty().take(120)
        } ?: ""
        else -> ""
    } //事件摘要

    /** 发请求并等应答；超时或进程死掉返回 null */
    private suspend fun call(method: String, params: JSONObject, timeoutMs: Long): JSONObject? {
        val id = ids.getAndIncrement()
        val d = CompletableDeferred<JSONObject>()
        pending[id] = d
        val ok = sendRaw(JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        })
        if (!ok) {
            pending.remove(id)
            return null
        }
        val res = withTimeoutOrNull(timeoutMs) { d.await() } ?: run {
            pending.remove(id)
            lastError = "$method 超时（${timeoutMs}ms）"
            return null
        }
        if (res.has("[closed]")) {
            lastError = "$method 失败：进程已退出"
            return null
        }
        res.optJSONObject("error")?.let {
            lastError = it.optString("message").ifBlank { it.toString() }
            Logs.e(TAG, "$method 错误：$lastError")
            return null
        }
        return res.optJSONObject("result") ?: JSONObject()
    } //发请求

    /** 写一行到 stdin（加锁，避免并发写把 JSON 切开） */
    private fun sendRaw(o: JSONObject): Boolean {
        val w = sink ?: return false
        return runCatching {
            synchronized(writeLock) {
                w.write(o.toString())
                w.write("\n")
                w.flush()
            }
            true
        }.getOrElse {
            lastError = "写 stdin 失败：${it.message}"
            false
        }
    } //写一行

    /** 把 Baicodex 的 baseUrl 转成 dsh 认的 endpoint。
     * 关键：dsh 的 deepseek-official 路由走 **Anthropic Messages 协议**，官方端点是
     * `https://api.deepseek.com/anthropic`（插件源码 PUBLIC_BASE_URL），不是 OpenAI 的 `/v1`。
     * 传错会被拼成 `https://api.deepseek.com/v1/messages` → 404 Not Found。
     */
    private fun baseUrlForDsh(url: String): String {
        var u = url.trim().trimEnd('/')
        //去掉 OpenAI 风格的尾巴
        listOf("/chat/completions", "/responses", "/v1").forEach { if (u.endsWith(it)) u = u.removeSuffix(it) }
        //官方 origin 补上 Anthropic 兼容前缀
        return when (u) {
            "https://api.deepseek.com", "http://api.deepseek.com" -> "$u/anthropic"
            else -> u
        }
    } //baseUrl 规范化
    //endregion
} //dsh SDK 客户端