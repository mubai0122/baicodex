/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 默认工具集装配
 */

package b.ai.desktop.core.tool

import b.ai.desktop.core.tool.tools.DeletePathTool
import b.ai.desktop.core.tool.tools.EditFileTool
import b.ai.desktop.core.tool.tools.MakeDirTool
import b.ai.desktop.core.tool.tools.MovePathTool
import b.ai.desktop.core.tool.tools.StatPathTool
import b.ai.desktop.core.tool.tools.ListDirTool
import b.ai.desktop.core.tool.tools.ReadFileTool
import b.ai.desktop.core.tool.tools.RunCommandTool
import b.ai.desktop.core.tool.tools.SearchTextTool
import b.ai.desktop.core.tool.tools.WriteFileTool
import b.ai.desktop.core.sandbox.SandboxMode
import java.io.File

/** 桌面版默认工具：列目录 / 读 / 写 / 局部改 / 建目录 / 移动 / 删除 / 属性 / 搜索 / 执行命令 */
fun defaultToolRegistry(
    workspace: File,
    /** 只启用这些工具名；空列表 = 全部启用 */
    enabled: List<String> = emptyList(),
    /** 命令工具默认超时（秒） */
    commandTimeoutSec: Int = 30,
    /** 沙箱档位：决定路径校验的严格程度 */
    sandboxMode: SandboxMode = SandboxMode.STANDARD,
    /** 额外工具链目录（分号分隔），前置进 PATH；编译工具链设置用 */
    extraPath: String = "",
): ToolRegistry {
    val ws = Workspace(workspace, sandboxMode)
    val all = listOf(
        ListDirTool(ws),
        ReadFileTool(ws),
        WriteFileTool(ws),
        EditFileTool(ws),
        MakeDirTool(ws),
        MovePathTool(ws),
        DeletePathTool(ws),
        StatPathTool(ws),
        SearchTextTool(ws),
        RunCommandTool(
            ws = ws,
            defaultTimeoutSec = commandTimeoutSec,
            env = {
                b.ai.desktop.core.auth.GitHubAuth.env() +
                    listOf(
                        // 工具链目录前置：用户配的 JDK/gradle/SDK 优先于系统 PATH
                        extraPath.split(';', ',').map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(";")
                            .takeIf { it.isNotEmpty() }
                            ?.let { dirs -> "PATH" to "$dirs;${System.getenv("PATH") ?: ""}" },
                    ).filterNotNull().toMap()
            },
        ),
    )
    val picked = if (enabled.isEmpty()) all else all.filter { it.spec.name in enabled }
    return ToolRegistry(picked)
}

/** 工具清单（设置页用来列出可开关的项） */
fun toolCatalog(): List<Pair<String, String>> = listOf(
    "list_dir" to "列目录",
    "read_file" to "读文件",
    "write_file" to "写文件",
    "edit_file" to "局部改文件",
    "make_dir" to "建目录",
    "move_path" to "移动/重命名",
    "delete_path" to "删除路径",
    "stat_path" to "查属性",
    "search_text" to "搜索文本",
    "run_command" to "执行命令",
)
