/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:03
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.agent.memory
import b.ai.core.data.db.Repo
import b.ai.net.api.llm.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 一条永久记忆 */
data class MemRow(val id: Long, val text: String, val at: Long)

/** 永久记忆：模型觉得重要的事实 / 偏好写进来，用户不删就每轮都随系统提示下发 */
object Memory {
    /** 界面直接读这份表，增删都先落库再刷新 */
    var rows by mutableStateOf<List<MemRow>>(emptyList())
        private set

    fun init() {
        rows = runCatching { Repo.memoryList() }.getOrDefault(emptyList())
    } //开机载入

    fun reload() {
        runCatching { Repo.memoryList() }.getOrNull()?.let { rows = it }
    } //重新读

    /** 同一条不重复记；返回 true 才是新写入 */
    fun add(text: String, sessionId: String): Boolean {
        val t = text.trim().replace("\n", " ")
        if (t.isEmpty()) return false
        if (rows.any { it.text == t }) return false
        val ok = runCatching { Repo.memoryAdd(t.take(500), sessionId) }.getOrDefault(false)
        if (ok) reload()
        return ok
    } //记一条

    fun delete(id: Long) {
        runCatching { Repo.memoryDelete(id) }
        reload()
    } //删一条

    fun clear() {
        runCatching { Repo.memoryClear() }
        rows = emptyList()
    } //全清

    /** 注入系统提示：一条一行；空表就什么都不加。limit <= 0 = 不限条数，超出时末尾标出还有多少没带 */
    fun prompt(limit: Int = 0): String {
        if (rows.isEmpty()) return ""
        val take = if (limit <= 0) rows.size else limit.coerceAtMost(rows.size)
        val sb = StringBuilder("长期记忆（用户没删就一直算数，与本轮对话同等可信）：\n")
        rows.take(take).forEach { sb.append("- ").append(it.text).append('\n') }
        //被截断时明确告诉模型还有存量，免得它以为记忆就这么多
        if (take < rows.size) sb.append("- （另有 ").append(rows.size - take).append(" 条未注入，需要时可用记忆检索工具查）\n")
        return sb.toString()
    } //拼提示

    /** 切词元：中文按字 bigram（不停在分词上，够召回），拉丁数字按词、长度 ≥2 才收 */
    private fun tokens(s: String): Set<String> {
        val out = LinkedHashSet<String>()
        Regex("[a-z0-9_]{2,}").findAll(s.lowercase()).forEach { out.add(it.value) }
        Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff]+").findAll(s).forEach { m ->
            val run = m.value
            if (run.length == 1) out.add(run)
            else for (i in 0 until run.length - 1) out.add(run.substring(i, i + 2))
        }
        return out
    } //切查询词元

    /** 关键词召回：IDF × 覆盖率 × 长度惩罚 × 时间衰减；记忆条数少，直接在内存里全文扫一遍 */
    fun search(q: String, limit: Int = 20): List<MemRow> {
        val toks = tokens(q)
        if (toks.isEmpty() || rows.isEmpty()) return emptyList()
        val docs = rows.map { it to tokens(it.text) }
        val df = HashMap<String, Int>()
        docs.forEach { (_, set) -> set.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = docs.size.toDouble()
        val now = System.currentTimeMillis()
        val hit = ArrayList<Pair<MemRow, Double>>()
        docs.forEach doc@{ (row, set) ->
            var s = 0.0
            var cover = 0
            toks.forEach { t ->
                if (set.contains(t)) {
                    //越稀有的词越值钱：只在少数记忆里出现的词命中就是强信号
                    s += Math.log(1.0 + n / (1.0 + (df[t] ?: 0)))
                    cover++
                }
            }
            if (s <= 0.0) return@doc
            s *= 1.0 + cover.toDouble() / toks.size
            s /= 1.0 + row.text.length / 200.0
            val days = (now - row.at) / 86400000.0
            s *= 1.0 + 0.25 * Math.exp(-days / 90.0)
            hit.add(row to s)
        }
        return hit.sortedByDescending { it.second }.take(limit.coerceIn(1, 50)).map { it.first }
    } //按关键词搜记忆
} //永久记忆