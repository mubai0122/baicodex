/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:31:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 工具同意：模型每次要动磁盘或跑命令，都先经过这里。
 * 手机端是「权限弹窗 + 记住本次选择」，桌面端做成一张确认卡片，语义一致。
 */

package b.ai.desktop.core.approval

import b.ai.desktop.core.sandbox.Risk
import b.ai.desktop.core.tool.ToolCall
import kotlinx.serialization.json.JsonPrimitive

/** 什么时候问用户 */
enum class ApprovalMode(val id: String, val label: String, val hint: String) {
    SMART("smart", "智能", "只读直接过，写入与危险操作先问一句"),
    ALWAYS("always", "每次都问", "每个工具调用都要你点确认"),
    TRUST("trust", "全自动", "不再询问；沙箱仍会挡系统级破坏性操作");

    companion object {
        fun of(id: String?): ApprovalMode =
            entries.firstOrNull { it.id == (id ?: "").trim() } ?: SMART
    }
}

/** 内核执行一个工具前的判断结果 */
sealed interface ToolGate {
    /** 直接执行 */
    data object Pass : ToolGate

    /** 需要用户点头 */
    data class Ask(val risk: Risk, val reason: String) : ToolGate

    /** 直接拒绝（原因回灌给模型） */
    data class Reject(val reason: String) : ToolGate
}

/** 用户看到的确认请求 */
data class ApprovalRequest(
    val toolName: String,
    /** 中文标题，例如「写文件」 */
    val title: String,
    /** 一行摘要：改哪个文件 / 跑哪条命令 */
    val summary: String,
    /** 参数明细（可能截断） */
    val detail: String,
    val risk: Risk,
    /** 为什么问：风险说明或命令守卫给的理由 */
    val reason: String,
    /** 该工具是否已在「永久放行」名单里（界面上给个提示） */
    val permanent: Boolean = false,
)

/** 用户的选择 */
sealed interface ApprovalDecision {
    /** 就这一次 */
    data object AllowOnce : ApprovalDecision

    /** 以后这个工具不再问 */
    data object AllowAlways : ApprovalDecision

    /** 拒绝，理由会告诉模型 */
    data class Deny(val reason: String) : ApprovalDecision
}

/** 工具 → 中文名 + 摘要，给确认卡片和工具结果用 */
object ToolLabels {
    private val labels = mapOf(
        "list_dir" to "列目录",
        "read_file" to "读文件",
        "write_file" to "写文件",
        "edit_file" to "局部改文件",
        "make_dir" to "建目录",
        "move_path" to "移动 / 重命名",
        "delete_path" to "删除路径",
        "stat_path" to "查属性",
        "search_text" to "搜索文本",
        "run_command" to "执行命令",
    )

    fun titleOf(tool: String): String = labels[tool] ?: tool

    /** 从参数里挑一句最能说明「要动什么」的话 */
    fun summaryOf(call: ToolCall): String {
        fun arg(key: String): String = (call.args[key] as? JsonPrimitive)?.content.orEmpty()
        return when (call.name) {
            "list_dir", "read_file", "stat_path" -> arg("path").ifBlank { "工作区根目录" }
            "write_file" -> "${arg("path")}（${arg("content").length} 字符）"
            "edit_file" -> arg("path")
            "make_dir" -> arg("path")
            "move_path" -> "${arg("from")} → ${arg("to")}"
            "delete_path" -> arg("path")
            "search_text" -> "「${arg("query")}」in ${arg("path").ifBlank { "." }}"
            "run_command" -> arg("command")
            else -> call.args.toString().take(160)
        }
    }

    /** 参数明细：命令直接全文，其余走 JSON 的截断版 */
    fun detailOf(call: ToolCall): String {
        val raw = if (call.name == "run_command") {
            (call.args["command"] as? JsonPrimitive)?.content.orEmpty()
        } else {
            call.args.toString()
        }
        return if (raw.length <= 900) raw else raw.take(900) + "\n…（已截断 ${raw.length - 900} 字符）"
    }
}
