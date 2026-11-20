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

import b.ai.core.cfg.app.Cfg
import b.ai.core.model.chat.FnModel
import b.ai.core.plugin.skill.Providers
import b.ai.net.api.llm.LlmClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 记忆整理：把「零散、重复、越写越长」的长期记忆合并成一份更干净的清单。
 * 只出建议不直接落库——用户看过再点应用，避免模型把一条重要偏好改没了。
 */
object MemOrganizer {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    /** 整理后的候选清单：非空就说明有一份待确认的结果 */
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    fun clear() {
        suggestions = emptyList()
        error = ""
    }

    /** 跑一次整理：空表直接返回；模型不可用时给明确原因 */
    suspend fun run() {
        if (busy) return
        val rows = Memory.rows
        error = ""
        suggestions = emptyList()
        if (rows.isEmpty()) {
            error = "还没有记忆条目，没什么可整理的"
            return
        }
        val m = FnModel.conn(FnModel.MEM)
        if (m[0].isBlank() || m[3].isBlank()) {
            error = "还没配可用的模型：设置 → 模型 → 记忆 → 整理用模型"
            return
        }
        busy = true
        try {
            val list = rows.joinToString("\n") { "- ${it.text}" }
            val pay = JSONArray()
                .put(
                    JSONObject().put("role", "system").put(
                        "content",
                        "你是记忆整理器。把用户给的一串长期记忆合并、去重、改写成更准确的一行一条。" +
                            "硬性要求：1) 只保留事实与偏好，不脑补、不推断、不添加原文没有的信息；" +
                            "2) 冲突的条目保留时间更新的一条，并在同一条里写清与旧说法的关系；" +
                            "3) 每条不超过 60 字，总量不超过 40 条；" +
                            "4) 只输出 JSON 字符串数组，如 [\"…\",\"…\"]，不要解释、不要代码块。"
                    )
                )
                .put(JSONObject().put("role", "user").put("content", list))
            val txt = withContext(Dispatchers.IO) {
                LlmClient(m[0], m[1], Providers.byId(m[2]).auth)
                    .chat(m[3], pay, temperature = 0.2, tools = JSONArray())
                    .optString("content")
            }
            val arr = runCatching {
                val s = txt.indexOf('[')
                val e = txt.lastIndexOf(']')
                if (s >= 0 && e > s) JSONArray(txt.substring(s, e + 1)) else JSONArray()
            }.getOrNull()
            val out = ArrayList<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.optString(i).trim().replace("\n", " ")
                    if (t.isNotEmpty() && t.length <= 300) out.add(t)
                }
            }
            if (out.isEmpty()) {
                error = "模型没给出可用的条目，换个整理模型再试"
            } else {
                suggestions = out.distinct().take(40)
            }
        } catch (e: Exception) {
            error = "整理失败：" + (e.message?.take(120) ?: e.javaClass.simpleName)
        } finally {
            busy = false
        }
    } //跑整理

    /** 应用建议：先全清再逐条写入，任一失败保留现场并回报条数 */
    fun apply(): String {
        val list = suggestions
        if (list.isEmpty()) return "没有待应用的整理结果"
        Memory.clear()
        var n = 0
        list.forEach { if (Memory.add(it, "organize")) n++ }
        clear()
        return "已用 $n 条整理结果替换原有记忆"
    } //应用建议
}
