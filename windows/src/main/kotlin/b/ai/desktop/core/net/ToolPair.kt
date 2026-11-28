/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-28 18:05:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 本文件作用：请求体最后的「工具调用配对硬校验」（桌面端与 Android 端 ToolPair.kt 同语义）。
 *
 * 线上偶现 400：
 *   An assistant message with 'tool_calls' must be followed by tool messages
 *   responding to each 'tool_call_id'. (insufficient tool messages following tool_calls message)
 *
 * 含义只有一个：assistant 里的 N 个 tool_call，后面跟的 tool 消息不足 N 条。
 * 造成原因多且随机（并行工具结果没落库、中断、历史分支切走、id 为空/重复），
 * 所以不逐条堵路径，而是在组请求体时统一修一次：缺的补桩、孤儿丢弃、空 id 规范化。
 * 只改这一次要发出去的 messages，不动磁盘上的会话记录。
 */

package b.ai.desktop.core.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ToolPair {

    /** 取字符串字段：JsonNull / 缺失 / 非字符串一律当空串，避免到处写类型判断 */
    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty() //字符串字段
    //取串

    /** 补桩正文：明确告诉模型「没拿到结果」，别让它臆测 */
    private fun stubText(name: String): String {
        val who = if (name.isBlank()) "该工具" else "工具 $name"
        return "工具结果缺失：$who 未返回执行结果（可能是中途中断、执行异常或结果未落库）。" +
            "这不是用户取消，也不要臆测它的返回值；如需该结果请重新调用。"
    } //补桩正文

    /**
     * Chat Completions 的配对修补：返回一份新的 messages，输入不改。
     *  1. tool_call 的 id 为空或重复 → 生成 `call_auto_<n>` 并写回；
     *  2. assistant 之后必须紧跟它每个 id 对应的 tool 消息，缺的立刻补桩；
     *  3. 没有对应 tool_calls 的孤儿 tool 消息丢弃；
     *  4. 已配对的结果保持原有先后顺序。
     */
    fun fixChat(messages: List<JsonObject>): List<JsonObject> {
        val out = ArrayList<JsonObject>(messages.size + 4)
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            val role = m.str("role")
            val calls = m["tool_calls"] as? JsonArray
            if (role == "assistant" && calls != null) {
                if (calls.isEmpty()) {
                    //空数组同样会被部分上游判非法，直接摘掉
                    out.add(JsonObject(m - "tool_calls"))
                    i++
                    continue
                } //空 tool_calls
                //① 规范化 id 并重建 tool_calls
                val ids = LinkedHashMap<String, String>() //id → 工具名
                val rebuilt = JsonArray(
                    calls.mapIndexed { k, el ->
                        val c = el as? JsonObject ?: return@mapIndexed el
                        val name = (c["function"] as? JsonObject)?.str("name").orEmpty()
                        var id = c.str("id").trim()
                        val c2 = if (id.isEmpty() || ids.containsKey(id)) {
                            id = "call_auto_${System.nanoTime() % 100000}_$k"
                            JsonObject(c + ("id" to JsonPrimitive(id)))
                        } else {
                            c
                        } //补 id
                        ids[id] = name
                        c2
                    }
                )
                out.add(JsonObject(m + ("tool_calls" to rebuilt)))
                //② 紧随其后的 tool 段：只留匹配且不重复的
                var j = i + 1
                val kept = ArrayList<JsonObject>()
                while (j < messages.size) {
                    val t = messages[j]
                    if (t.str("role") != "tool") break
                    val tid = t.str("tool_call_id").trim()
                    val dup = kept.any { it.str("tool_call_id").trim() == tid }
                    if (ids.containsKey(tid) && !dup) kept.add(t) //孤儿/重复直接丢弃
                    j++
                } //收集 tool 段
                out.addAll(kept)
                //③ 没等到的调用就地补桩
                ids.forEach { (id, name) ->
                    if (kept.none { it.str("tool_call_id").trim() == id }) {
                        out.add(
                            JsonObject(
                                mapOf(
                                    "role" to JsonPrimitive("tool"),
                                    "tool_call_id" to JsonPrimitive(id),
                                    "content" to JsonPrimitive(stubText(name))
                                )
                            )
                        )
                    } //补桩
                }
                i = j
                continue
            } //assistant + tool_calls
            if (role == "tool") {
                //走到这里说明前面没有等待中的 tool_calls，属孤儿
                i++
                continue
            } //孤儿 tool
            out.add(m)
            i++
        } //逐条重排
        return out
    } //Chat 配对修补
} //工具调用配对修补