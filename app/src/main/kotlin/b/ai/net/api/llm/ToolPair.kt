/**
 * 本文件作用：请求体最后的「工具调用配对硬校验」，把 messages / input 修成上游一定接受的结构。
 *
 * 背景（线上偶现 400）：
 *   An assistant message with 'tool_calls' must be followed by tool messages
 *   responding to each 'tool_call_id'. (insufficient tool messages following tool_calls message)
 *
 * 这条错误只说明一件事：assistant 里的 N 个 tool_call，后面跟的 tool 消息不足 N 条。
 * 能造成它的路径很多，而且会随运行环境变化（不影响业务逻辑，纯协议层）：
 *   · 并行工具里有几条结果没落库（取消 / 崩溃 / superseded）；
 *   · 历史被编辑、重生成、切分支，兄弟 tool 消息没进当前路径；
 *   · 上下文压缩的切点落在 assistant 与它的 tool 消息之间；
 *   · 模型返回的 tool_call.id 为空或重复，配对键对不上；
 *   · 中断后继续对话，上一轮的 tool 结果永远等不到。
 *
 * 与其逐条堵路径，不如在**唯一出口**（LlmClient 组请求体时）做一次幂等修补：
 * 缺的补桩、孤儿丢弃、空 id 规范化。修完再发，上游永远拿不到不自洽的对话。
 * 修补只作用于「这一次的请求体」，库里的历史一个字节都不动。
 */
package b.ai.net.api.llm

import org.json.JSONArray
import org.json.JSONObject

object ToolPair {

    /** 补桩正文：明确告诉模型「没拿到结果」，别让它臆测 */
    private fun stubText(name: String): String {
        val who = if (name.isBlank()) "该工具" else "工具 $name"
        return "工具结果缺失：$who 未返回执行结果（可能是中途中断、执行异常或结果未落库）。" +
            "这不是用户取消，也不要臆测它的返回值；如需该结果请重新调用。"
    } //补桩正文

    /**
     * Chat Completions 的配对修补（原地改 [messages]，返回是否改动过）。
     * 规则：
     *  1. assistant 的 tool_calls 里 id 为空或重复 → 生成稳定的 `call_auto_<序号>` 并写回；
     *  2. assistant 之后必须紧跟它每一个 id 对应的 tool 消息，缺的立刻补桩；
     *  3. 没有对应 tool_calls 的孤儿 tool 消息一律丢弃（上游同样会 400）；
     *  4. 顺序不动：已配对的 tool 消息保持原有先后。
     */
    fun fixChat(messages: JSONArray): Boolean {
        var changed = false
        val out = JSONArray()
        var i = 0
        while (i < messages.length()) {
            val m = messages.optJSONObject(i)
            if (m == null) {
                i++
                continue
            } //非对象跳过
            val role = m.optString("role")
            if (role == "assistant" && !m.isNull("tool_calls")) {
                val calls = m.optJSONArray("tool_calls")
                if (calls == null || calls.length() == 0) {
                    //tool_calls 存在但是空数组：一些上游也会判非法，直接摘掉
                    m.remove("tool_calls")
                    out.put(m)
                    changed = true
                    i++
                    continue
                } //空的话直接去掉
                //① 规范化 id：空值 / 同一条内重复都换掉，键必须能对上
                val ids = LinkedHashMap<String, String>() //id → 工具名
                var auto = 0
                for (k in 0 until calls.length()) {
                    val c = calls.optJSONObject(k) ?: continue
                    val fn = c.optJSONObject("function")
                    val name = fn?.optString("name").orEmpty()
                    var id = c.optString("id").trim()
                    if (id.isEmpty() || ids.containsKey(id)) {
                        id = "call_auto_${System.nanoTime() % 100000}_$auto"
                        c.put("id", id)
                        changed = true
                    } //补 id
                    auto++
                    ids[id] = name
                } //规范化 id
                out.put(m)
                //② 收集紧随其后的 tool 段：只留匹配且不重复的，其余丢弃
                var j = i + 1
                val kept = ArrayList<JSONObject>()
                while (j < messages.length()) {
                    val t = messages.optJSONObject(j) ?: break
                    if (t.optString("role") != "tool") break
                    val tid = t.optString("tool_call_id").trim()
                    val dup = kept.any { it.optString("tool_call_id").trim() == tid }
                    if (ids.containsKey(tid) && !dup) {
                        kept.add(t)
                    } else {
                        changed = true //孤儿 / 重复结果，丢弃
                    }
                    j++
                } //收集 tool 段
                kept.forEach { out.put(it) }
                //③ 没等到的调用就地补桩，位置紧跟已到的结果
                ids.forEach { (id, name) ->
                    if (kept.none { it.optString("tool_call_id").trim() == id }) {
                        out.put(
                            JSONObject().put("role", "tool").put("tool_call_id", id)
                                .put("content", stubText(name))
                        )
                        changed = true
                    } //补桩
                }
                i = j
                continue
            } //assistant + tool_calls
            if (role == "tool") {
                //走到这里说明前面没有等待中的 tool_calls，属于孤儿结果
                changed = true
                i++
                continue
            } //孤儿 tool
            out.put(m)
            i++
        } //逐条重排
        if (changed) {
            while (messages.length() > 0) messages.remove(0)
            for (k in 0 until out.length()) messages.put(out.opt(k))
        } //改动才回写，避免无谓重建
        return changed
    } //Chat 配对修补

    /**
     * Responses 的配对修补：function_call 与 function_call_output 是平级 item，
     * 每个 call_id 都要有 output，缺的在 call 后面直接补一条。
     */
    fun fixResponses(input: JSONArray): Boolean {
        var changed = false
        val out = JSONArray()
        var i = 0
        while (i < input.length()) {
            val o = input.optJSONObject(i)
            if (o == null) {
                i++
                continue
            } //非对象跳过
            if (o.optString("type") != "function_call") {
                out.put(o)
                i++
                continue
            } //非调用
            out.put(o)
            val id = o.optString("call_id").ifBlank { o.optString("id") }
            val name = o.optString("name")
            //紧随其后的 output 段：匹配的保留，其余（若有）也不丢，交给后续逻辑判断
            var j = i + 1
            var matched = false
            while (j < input.length()) {
                val t = input.optJSONObject(j) ?: break
                if (t.optString("type") != "function_call_output") break
                if (t.optString("call_id") == id) matched = true
                out.put(t)
                j++
            } //output 段
            if (!matched) {
                out.put(
                    JSONObject().put("type", "function_call_output").put("call_id", id)
                        .put("output", stubText(name))
                )
                changed = true
            } //补 output
            i = j
        } //逐条重排
        if (changed) {
            while (input.length() > 0) input.remove(0)
            for (k in 0 until out.length()) input.put(out.opt(k))
        } //改动才回写
        return changed
    } //Responses 配对修补

    /** 是否为「工具配对不完整」类错误：命中时上层可以给出可操作的提示 */
    fun isPairError(text: String): Boolean {
        val s = text.lowercase()
        return s.contains("tool_call_id") || s.contains("tool_calls") ||
            s.contains("function_call_output") || s.contains("tool messages")
    } //配对错误判定
} //工具调用配对修补