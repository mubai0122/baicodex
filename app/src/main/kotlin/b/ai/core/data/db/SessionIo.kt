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

import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.io.Paths
import b.ai.core.model.chat.ChatMsg
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话导出 / 导入：一个文件装多个会话（标题 + 正文）。
 * 只带正文与思考过程，不带分支树与工具结构——导入后会按顺序重建一条线性会话，够读够搜。
 * 导出支持五种格式：Markdown / 纯文本 / JSON（可再导回本应用）/ HTML / CSV，统一落在 baicode/export。
 */
object SessionIo {
    private const val V = 1

    /** 导出格式：label 给界面选，ext 决定落盘后缀 */
    enum class Fmt(val label: String, val ext: String) {
        MD("Markdown", "md"),
        TXT("纯文本", "txt"),
        JSON("JSON（可再导入）", "json"),
        HTML("网页", "html"),
        CSV("表格", "csv")
    } //导出格式

    fun dir(): File = Paths.dir("export") //导出目录：baicode/export

    /** 老入口：默认 JSON，导入功能只认这个格式 */
    fun export(ids: List<String>): Pair<File?, String> = exportAs(ids, Fmt.JSON)

    /** 导出选中的会话到指定格式，返回 文件 + 一句结果 */
    fun exportAs(ids: List<String>, fmt: Fmt): Pair<File?, String> {
        if (ids.isEmpty()) return null to "先选要导出的会话"
        //一次把数据读完，后面各格式只做渲染：避免每种格式重复查库
        val data = ids.mapNotNull { sid ->
            val s = Repo.session(sid) ?: return@mapNotNull null
            //系统摘要与工具回包不进导出：前者是内部标记，后者离开本机会话没有上下文
            val msgs = Repo.messages(sid).filter { it.role != "tool" && it.role != "summary" }
            if (msgs.isEmpty()) return@mapNotNull null
            Triple(s.title.ifBlank { "未命名会话" }, s.category.ifBlank { "默认" }, msgs)
        }
        if (data.isEmpty()) return null to "选中的会话里没有内容"
        val body = when (fmt) {
            Fmt.MD -> md(data)
            Fmt.TXT -> txt(data)
            Fmt.JSON -> json(data)
            Fmt.HTML -> html(data)
            Fmt.CSV -> csv(data)
        }
        val name = "sessions-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + "." + fmt.ext
        val f = File(dir(), name)
        val err = runCatching { f.writeText(body) }.exceptionOrNull()
        if (err != null) return null to "导出失败：" + (err.message ?: "写不进 " + dir().absolutePath)
        return f to "已导出 ${data.size} 个会话：${f.absolutePath}"
    } //按格式导出

    // ------------------------------------------------------------ 各格式渲染

    private fun md(data: List<Triple<String, String, List<ChatMsg>>>): String {
        val sb = StringBuilder()
        data.forEach { (title, _, msgs) ->
            sb.append("# ").append(title).append("\n\n")
            msgs.forEach { m ->
                if (m.role == "user") {
                    sb.append("**用户**\n\n").append(m.content).append("\n\n")
                } else {
                    if (m.reasoning.isNotBlank()) {
                        sb.append("<details><summary>思考过程</summary>\n\n")
                            .append(m.reasoning).append("\n\n</details>\n\n")
                    }
                    if (m.content.isNotBlank()) sb.append("**助手**\n\n").append(m.content).append("\n\n")
                }
            }
            sb.append("\n---\n\n")
        }
        return sb.toString()
    } //Markdown

    private fun txt(data: List<Triple<String, String, List<ChatMsg>>>): String {
        val sb = StringBuilder()
        data.forEach { (title, _, msgs) ->
            sb.append("【").append(title).append("】\n\n")
            msgs.forEach { m ->
                if (m.role == "user") sb.append("用户：").append(m.content).append("\n\n")
                else sb.append("助手：").append(m.content).append("\n\n")
            }
            sb.append("\n================================\n\n")
        }
        return sb.toString()
    } //纯文本

    private fun json(data: List<Triple<String, String, List<ChatMsg>>>): String {
        val arr = JSONArray()
        data.forEach { (title, category, msgs) ->
            val ma = JSONArray()
            msgs.forEach { m ->
                ma.put(
                    JSONObject().put("role", m.role).put("content", m.content).apply {
                        if (m.reasoning.isNotBlank()) put("reasoning", m.reasoning)
                    }
                )
            }
            arr.put(
                JSONObject()
                    .put("title", title)
                    .put("category", category)
                    .put("messages", ma)
            )
        }
        return JSONObject().put("v", V).put("sessions", arr).toString(2)
    } //JSON（原有格式，可再导入）

    private fun html(data: List<Triple<String, String, List<ChatMsg>>>): String {
        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<title>会话导出</title><style>")
        sb.append("body{font:15px/1.7 -apple-system,system-ui,sans-serif;margin:0;padding:16px;background:#fff;color:#222}")
        sb.append("h1{font-size:18px;border-bottom:1px solid #ddd;padding-bottom:8px}")
        sb.append(".m{margin:12px 0}.u{color:#0a6}.a{color:#333}")
        sb.append(".who{font-size:12px;color:#888;margin-bottom:2px}")
        sb.append("pre{white-space:pre-wrap;word-break:break-word;margin:0;font:inherit}")
        sb.append("details{margin:6px 0;color:#666}summary{cursor:pointer;font-size:12px}")
        sb.append("</style></head><body>")
        data.forEach { (title, _, msgs) ->
            sb.append("<h1>").append(esc(title)).append("</h1>")
            msgs.forEach { m ->
                val who = if (m.role == "user") "用户" else "助手"
                val cls = if (m.role == "user") "u" else "a"
                sb.append("<div class=\"m ").append(cls).append("\">")
                sb.append("<div class=\"who\">").append(who).append("</div>")
                if (m.reasoning.isNotBlank()) {
                    sb.append("<details><summary>思考过程</summary><pre>")
                        .append(esc(m.reasoning)).append("</pre></details>")
                }
                sb.append("<pre>").append(esc(m.content)).append("</pre></div>")
            }
        }
        sb.append("</body></html>")
        return sb.toString()
    } //HTML

    private fun csv(data: List<Triple<String, String, List<ChatMsg>>>): String {
        //带 BOM，Excel 打开中文不乱码
        val sb = StringBuilder("\ufeff")
        sb.append("会话,角色,内容\r\n")
        data.forEach { (title, _, msgs) ->
            msgs.forEach { m ->
                val who = if (m.role == "user") "用户" else "助手"
                sb.append(q(title)).append(',')
                    .append(q(who)).append(',')
                    .append(q(m.content)).append("\r\n")
            }
        }
        return sb.toString()
    } //CSV

    /** HTML 转义：引号字符用 34.toChar() 取，避免源码里出现裸引号字面量 */
    private fun esc(s: String): String {
        val q = 34.toChar().toString()
        return s.replace("&", "&" + "amp;")
            .replace("<", "&" + "lt;")
            .replace(">", "&" + "gt;")
            .replace(q, "&" + "quot;")
    } //HTML 转义

    /** CSV 字段转义：含逗号 / 引号 / 换行就包引号并把引号翻倍 */
    private fun q(s: String): String {
        val need = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (need) "\"" + s.replace("\"", "\"\"") + "\"" else s
    } //CSV 字段转义

    // ---------------------------------------------------------------- 导入

    /** 导入：每个会话新建一条，按顺序逐条追加，标题加「（导入）」后缀便于区分 */
    fun import(file: File): String {
        val root = runCatching { JSONObject(file.readText()) }.getOrElse { return "这不是本应用导出的会话文件" }
        val arr = root.optJSONArray("sessions") ?: return "文件里没有 sessions 字段"
        var sessions = 0
        var msgs = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").ifBlank { "导入的会话" }
            //后缀受设置控制：关掉就按原标题导入，来源混在一起由用户自己承担
            val name = if (Cfg.importSuffix) title + "（导入）" else title
            val row = Repo.newSession(name, o.optString("category").ifBlank { "默认" })
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