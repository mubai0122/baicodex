/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:05
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.data.db

import b.ai.core.cfg.io.Paths
import b.ai.core.model.chat.ChatMsg
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话导出 / 导入：一个 JSON 文件装多个会话（标题 + 正文）。
 * 只带正文与思考过程，不带分支树与工具结构——导入后会按顺序重建一条线性会话，够读够搜。
 */
object SessionIo {
    private const val V = 1

    fun dir(): File = Paths.dir("export") //导出目录：baicode/export

    /** 导出勾选的会话，返回 文件 + 一句结果 */
    fun export(ids: List<String>): Pair<File?, String> {
        if (ids.isEmpty()) return null to "先勾要导出的会话"
        val arr = JSONArray()
        ids.forEach outer@{ sid ->
            val s = Repo.session(sid) ?: return@outer
            val msgs = Repo.messages(sid)
            val ma = JSONArray()
            msgs.forEach inner@{ m ->
                //系统摘要与工具回包不进导出：前者是内部标记，后者离开本机会话没有上下文
                if (m.role == "tool" || m.role == "summary") return@inner
                ma.put(
                    JSONObject().put("role", m.role).put("content", m.content).apply {
                        if (m.reasoning.isNotBlank()) put("reasoning", m.reasoning)
                    }
                )
            }
            arr.put(
                JSONObject()
                    .put("title", s.title)
                    .put("category", s.category)
                    .put("descr", s.descr)
                    .put("messages", ma)
            )
        }
        if (arr.length() == 0) return null to "勾选的会话里没有内容"
        val name = "sessions-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json"
        val f = File(dir(), name)
        val err = runCatching {
            f.writeText(JSONObject().put("v", V).put("sessions", arr).toString(2))
        }.exceptionOrNull()
        if (err != null) return null to "导出失败：" + (err.message ?: "写不进 " + dir().absolutePath)
        return f to "已导出 ${arr.length()} 个会话：${f.absolutePath}"
    } //导出选中会话

    /** 导入：每个会话新建一条，按顺序逐条追加，标题加「（导入）」后缀便于区分 */
    fun import(file: File): String {
        val root = runCatching { JSONObject(file.readText()) }.getOrElse { return "这不是本应用导出的会话文件" }
        val arr = root.optJSONArray("sessions") ?: return "文件里没有 sessions 字段"
        var sessions = 0
        var msgs = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").ifBlank { "导入的会话" }
            val row = Repo.newSession(title + "（导入）", o.optString("category").ifBlank { "默认" })
            val ma = o.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until ma.length()) {
                val m = ma.optJSONObject(j) ?: continue
                val body = m.optString("content")
                if (body.isBlank()) continue
                val role = when (m.optString("role")) {
                    "user" -> "user"
                    "system" -> "system"
                    else -> "assistant"
                }
                Repo.append(row.id, ChatMsg(role = role, content = body, reasoning = m.optString("reasoning")))
                msgs++
            }
            sessions++
        }
        return "已导入 $sessions 个会话、$msgs 条消息"
    } //导入到本机
} //会话导入导出