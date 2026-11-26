/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 00:52:12
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 会话导出
 * 会话 → Markdown（复制到剪贴板 / 另存为 .md 共用同一份文本）。
 */

package b.ai.desktop.core.export

import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.model.Session
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 会话全文：按消息顺序输出，工具输出包在代码块里 */
fun sessionMarkdown(session: Session): String = buildString {
    appendLine("# ${session.title}")
    appendLine()
    appendLine("> 导出时间 ${LocalDateTime.now().format(stampFormat)} · 共 ${session.messages.size} 条消息")
    appendLine()

    session.messages
        .filterNot { it.role == MsgRole.ASSISTANT && it.text.isBlank() }
        .forEach { msg -> appendMessage(msg) }
}

private fun StringBuilder.appendMessage(msg: ChatMessage) {
    appendLine("## ${heading(msg)}")
    appendLine()

    if (msg.attachments.isNotEmpty()) {
        appendLine(msg.attachments.joinToString(" ") { "`${it.kind}·${it.name}`" })
        appendLine()
    }
    if (msg.text.isBlank()) return

    if (msg.role == MsgRole.TOOL) {
        appendLine("```text")
        appendLine(msg.text)
        appendLine("```")
    } else {
        appendLine(msg.text)
    }
    appendLine()
}

private fun heading(msg: ChatMessage): String = when (msg.role) {
    MsgRole.USER -> "我"
    MsgRole.ASSISTANT -> "Baicodex"
    MsgRole.TOOL -> "工具 · ${msg.toolName ?: "tool"}${if (msg.toolOk) "" else "（失败）"}"
    else -> "系统"
}

/** 另存为 .md；用户取消返回 null */
fun exportSessionMarkdown(session: Session): File? = runCatching {
    val dialog = FileDialog(null as Frame?, "导出会话", FileDialog.SAVE)
    dialog.file = safeFileName(session.title) + ".md"
    dialog.isVisible = true

    val dir = dialog.directory ?: return@runCatching null
    val name = dialog.file ?: return@runCatching null
    val target = File(dir, if (name.endsWith(".md", ignoreCase = true)) name else "$name.md")
    target.writeText(sessionMarkdown(session))
    target
}.getOrNull()

/** Windows 文件名里不能出现的字符统一换掉 */
private fun safeFileName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        .trim()
        .ifBlank { "会话" }
        .take(40)

/** 把所有会话拼成一份文档导出；用户取消返回 null */
fun exportAllSessions(sessions: List<Session>): File? {
    if (sessions.isEmpty()) return null
    val dialog = FileDialog(null as Frame?, "导出全部会话", FileDialog.SAVE).apply {
        file = "Baicodex-会话备份-${LocalDate.now()}.md"
    }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val file = File(dir, if (name.endsWith(".md")) name else "$name.md")
    val body = buildString {
        appendLine("# Baicodex 会话备份")
        appendLine()
        appendLine("> 导出时间 ${LocalDateTime.now().format(stampFormat)} · 共 ${sessions.size} 个会话")
        appendLine()
        sessions.forEach { session ->
            appendLine("---")
            appendLine()
            append(sessionMarkdown(session))
            appendLine()
        }
    }
    return runCatching { file.writeText(body); file }.getOrNull()
}
