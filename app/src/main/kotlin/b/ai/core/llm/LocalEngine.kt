package b.ai.core.llm

import b.ai.core.cfg.app.Cfg
import b.ai.native.llm.Llama
import b.ai.net.api.llm.LlmException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本地推理的调用链入口：把 OpenAI 格式的 messages 转成 llama.cpp 的 prompt 跑出来。
 * 句柄按需加载并缓存，换模型自动卸旧的；一次只允许一个生成（native 侧上下文不是线程安全的）。
 */
object LocalEngine {
    private const val MAX_TOK = 512 //单轮生成上限：再高手机上纯 CPU 要等很久

    private var handle = 0L
    private var sess = 0L
    private var path = ""

    /** 是否该走本地：开关打开 + 模型文件在 + native 库可用，三者缺一就回云端 */
    fun route(): Boolean =
        Cfg.llmLocal && Llama.available() && Cfg.llmModel.isNotBlank() && File(Cfg.llmModel).isFile

    /** 给设置页看的一句话状态 */
    fun status(): String = when {
        !Llama.available() -> "推理库不可用（当前 ABI 没编进来）"
        Cfg.llmModel.isBlank() -> "未选择模型"
        !File(Cfg.llmModel).isFile -> "模型文件不存在"
        sess != 0L -> "已加载 " + File(path).name
        else -> "未加载（首次对话会加载）"
    } //后端状态

    fun release() {
        if (sess != 0L) Llama.ctxFree(sess)
        if (handle != 0L) Llama.free(handle)
        sess = 0L
        handle = 0L
        path = ""
    } //先收上下文再收模型：顺序反了会用到已释放的内存

    /** 同步加载，返回空串表示成功；换模型时先卸旧的 */
    private fun ensure(): String {
        val want = Cfg.llmModel
        if (handle != 0L && sess != 0L && path == want) return ""
        release()
        val h = Llama.load(want, Cfg.llmThreads, Cfg.llmGpu)
        if (h == 0L) return "本地模型加载失败：" + Llama.lastErr().ifBlank { "未知原因" }
        val c = Llama.ctxNew(h, Cfg.llmCtx)
        if (c == 0L) {
            Llama.free(h)
            return "本地模型建上下文失败：" + Llama.lastErr().ifBlank { "内存不足或上下文过大" }
        }
        Llama.setSampling(c, Cfg.llmTemp, Cfg.llmTopP, 40, Cfg.llmRep, -1)
        handle = h
        sess = c
        path = want
        return ""
    } //按需加载

    /** 整包：内部走流式拼完再返回，保证与云端 chat 的返回结构一致 */
    fun chat(messages: JSONArray, temperature: Double?): JSONObject =
        stream(messages, temperature, { false }) { _, _ -> }

    /** 流式：onDelta(正文, 推理) 与云端同语义，local 没有 reasoning 只回正文 */
    @Synchronized
    fun stream(
        messages: JSONArray,
        temperature: Double?,
        shouldStop: () -> Boolean,
        onDelta: (String, String) -> Unit
    ): JSONObject {
        val why = ensure()
        if (why.isNotEmpty()) throw LlmException(why)
        val c = sess
        val msgs = buildPrompt(messages)
        val prompt = Llama.chat(handle, msgs, true)
            ?: msgs.joinToString("\n") { "${it.first}: ${it.second}" } + "\nassistant:"
        if (temperature != null) {
            Llama.setSampling(c, temperature.toFloat(), Cfg.llmTopP, 40, Cfg.llmRep, -1)
        }
        val sb = StringBuilder()
        val t0 = System.currentTimeMillis()
        val rc = Llama.run(c, prompt, MAX_TOK) { t ->
            sb.append(t)
            onDelta(t, "")
            //用户点停止时下一 token 前跳出，不在这里做重活
            if (shouldStop()) Llama.cancel(c)
        }
        val ms = System.currentTimeMillis() - t0
        if (rc < 0) {
            val reason = when (rc) {
                -2 -> "提示超出上下文（调小上下文长度或减少历史）"
                -3 -> throw LlmException("已停止")
                else -> Llama.lastErr().ifBlank { "推理失败($rc)" }
            }
            throw LlmException(reason)
        }
        val pTok = (prompt.length / 1.8).toInt()
        return JSONObject()
            .put("role", "assistant")
            .put("content", sb.toString())
            .put(
                "usage",
                JSONObject()
                    .put("prompt_tokens", pTok)
                    .put("completion_tokens", rc)
                    .put("total_tokens", pTok + rc)
                    .put("local_ms", ms)
            )
    } //一轮生成

    /** OpenAI messages → llama.cpp 对话；按上下文预算裁剪，system 只留前三分之一 */
    private fun buildPrompt(messages: JSONArray): List<Pair<String, String>> {
        val budget = (Cfg.llmCtx * 2).coerceAtLeast(512) //字符预算，中文约 1.5～2 字/token
        val rows = ArrayList<Pair<String, String>>()
        for (i in 0 until messages.length()) {
            val o = messages.optJSONObject(i) ?: continue
            val role = o.optString("role", "user")
            if (role == "tool") continue //本地不出 tool_calls，孤儿工具结果直接丢
            val body = textOf(o.opt("content"))
            if (body.isBlank()) continue
            val r = when (role) {
                "system" -> "system"
                "assistant" -> "assistant"
                else -> "user"
            }
            rows.add(r to body)
        }
        val sys = rows.firstOrNull { it.first == "system" }?.second?.take(budget / 3).orEmpty()
        val rest = rows.filter { it.first != "system" }
        val picked = ArrayList<Pair<String, String>>()
        var used = sys.length
        for (r in rest.asReversed()) {
            if (used + r.second.length > budget) break
            picked.add(0, r)
            used += r.second.length
        }
        if (picked.isEmpty()) picked.add(rest.lastOrNull() ?: ("user" to "你好"))
        val out = ArrayList<Pair<String, String>>(picked.size + 1)
        if (sys.isNotBlank()) out.add("system" to sys)
        out.addAll(picked)
        return out
    } //上下文裁剪

    /** content 可能是字符串，也可能是多模态数组；本地只取文本段 */
    private fun textOf(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is String -> v
        is JSONArray -> (0 until v.length()).mapNotNull { i ->
            val p = v.optJSONObject(i) ?: return@mapNotNull null
            if (p.optString("type") == "text") p.optString("text") else null
        }.joinToString(" ")
        else -> v.toString()
    } //取文本部分
} //本地模型调用链