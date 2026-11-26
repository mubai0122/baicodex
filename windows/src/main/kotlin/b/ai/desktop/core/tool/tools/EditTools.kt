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
 * 编辑类工具：局部替换 / 建目录 / 移动 / 删除 / 查看属性
 */

package b.ai.desktop.core.tool.tools

import b.ai.desktop.core.tool.Tool
import b.ai.desktop.core.tool.ToolResult
import b.ai.desktop.core.tool.ToolSpec
import b.ai.desktop.core.tool.Workspace
import b.ai.desktop.core.tool.boolArg
import b.ai.desktop.core.tool.rawStringArg
import b.ai.desktop.core.tool.stringArg
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** 局部替换：优先用它改代码，避免整文件重写丢内容 */
class EditFileTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "edit_file",
        description = "把文件里的 old_string 替换成 new_string（先 read_file 看清楚原文）。默认要求 old_string 在文件里唯一，命中多处会报错，可用 replace_all 全部替换。",
        parameters = objectSchema(
            mapOf(
                "path" to ("string" to "相对工作区的文件路径"),
                "old_string" to ("string" to "要被替换的原文片段（尽量带上上下文，保证唯一）"),
                "new_string" to ("string" to "替换后的内容，允许为空串（等于删除该片段）"),
                "replace_all" to ("boolean" to "是否替换所有匹配，默认 false"),
            ),
            required = listOf("path", "old_string", "new_string"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.stringArg("path")
        if (path.isBlank()) return@withContext ToolResult(false, "缺少 path 参数")
        val old = args.rawStringArg("old_string")
            ?: return@withContext ToolResult(false, "缺少 old_string 参数")
        if (old.isEmpty()) return@withContext ToolResult(false, "old_string 不能为空")
        val new = args.rawStringArg("new_string")
            ?: return@withContext ToolResult(false, "缺少 new_string 参数")
        val replaceAll = args.boolArg("replace_all")

        val file = ws.resolveWritable(path)
        when {
            !file.exists() -> return@withContext ToolResult(false, "文件不存在：${ws.relative(file)}")
            file.isDirectory -> return@withContext ToolResult(false, "这是目录：${ws.relative(file)}")
        }
        val text = file.readText()
        val hits = countOccurrences(text, old)
        if (hits == 0) {
            return@withContext ToolResult(false, "没找到 old_string（注意空格与换行必须完全一致）")
        }
        if (hits > 1 && !replaceAll) {
            return@withContext ToolResult(false, "old_string 命中 $hits 处，请带上更多上下文，或设 replace_all=true")
        }
        val updated = if (replaceAll) text.replace(old, new) else text.replaceFirst(old, new)
        file.writeText(updated)
        ToolResult(true, "已更新 ${ws.relative(file)}（替换 $hits 处，${text.length} → ${updated.length} 字符）")
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }
}

/** 建目录（递归） */
class MakeDirTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "make_dir",
        description = "在工作区内创建目录（父目录不存在会一起创建）。",
        parameters = objectSchema(
            mapOf("path" to ("string" to "相对工作区的目录路径")),
            required = listOf("path"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.stringArg("path")
        if (path.isBlank()) return@withContext ToolResult(false, "缺少 path 参数")
        val dir = ws.resolveWritable(path)
        if (dir.exists()) {
            return@withContext ToolResult(true, "目录已存在：${ws.relative(dir)}")
        }
        val ok = dir.mkdirs()
        ToolResult(ok, if (ok) "已创建 ${ws.relative(dir)}" else "创建失败：${ws.relative(dir)}")
    }
}

/** 移动/重命名 */
class MovePathTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "move_path",
        description = "在工作区内移动或重命名文件/目录（目标存在会覆盖）。",
        parameters = objectSchema(
            mapOf(
                "from" to ("string" to "源路径（相对工作区）"),
                "to" to ("string" to "目标路径（相对工作区）"),
            ),
            required = listOf("from", "to"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val fromArg = args.stringArg("from")
        val toArg = args.stringArg("to")
        if (fromArg.isBlank() || toArg.isBlank()) {
            return@withContext ToolResult(false, "需要 from 与 to 两个参数")
        }
        val from = ws.resolveWritable(fromArg)
        val to = ws.resolveWritable(toArg)
        if (!from.exists()) return@withContext ToolResult(false, "源不存在：${ws.relative(from)}")
        if (from == ws.root) return@withContext ToolResult(false, "不能移动工作区根目录")
        to.parentFile?.mkdirs()
        runCatching {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse { return@withContext ToolResult(false, "移动失败：${it.message}") }
        ToolResult(true, "已移动：${ws.relative(from)} → ${ws.relative(to)}")
    }
}

/** 删除文件或目录（目录递归删除） */
class DeletePathTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "delete_path",
        description = "删除工作区内的文件或目录（目录会递归删除，不可恢复）。删前请确认路径。",
        parameters = objectSchema(
            mapOf("path" to ("string" to "相对工作区的路径")),
            required = listOf("path"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.stringArg("path")
        if (path.isBlank()) return@withContext ToolResult(false, "缺少 path 参数（不允许删除工作区根目录）")
        val target = ws.resolveWritable(path)
        if (target == ws.root) return@withContext ToolResult(false, "不允许删除工作区根目录")
        if (!target.exists()) return@withContext ToolResult(false, "路径不存在：${ws.relative(target)}")

        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        target.walkBottomUp().forEach { file ->
            val ok = runCatching { file.delete() }.getOrDefault(false)
            if (ok) deleted += ws.relative(file) else failed += ws.relative(file)
        }
        if (failed.isNotEmpty()) {
            ToolResult(false, "删除失败 ${failed.size} 项：${failed.take(5).joinToString(", ")}")
        } else {
            ToolResult(true, "已删除 ${ws.relative(target)}（共 ${deleted.size} 项）")
        }
    }
}

/** 路径属性 */
class StatPathTool(private val ws: Workspace) : Tool {
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override val spec = ToolSpec(
        name = "stat_path",
        description = "查看工作区内文件或目录的大小、修改时间、类型；文本文件附带行数。",
        parameters = objectSchema(
            mapOf("path" to ("string" to "相对工作区的路径，缺省为根目录")),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val target = ws.resolve(args.stringArg("path"))
        if (!target.exists()) return@withContext ToolResult(false, "路径不存在：${ws.relative(target)}")

        val modified = Instant.ofEpochMilli(target.lastModified())
            .atZone(ZoneId.systemDefault())
            .format(timeFormat)
        val builder = StringBuilder()
        builder.append("路径：").append(ws.relative(target)).append('\n')
        builder.append("类型：").append(if (target.isDirectory) "目录" else "文件").append('\n')
        builder.append("修改时间：").append(modified).append('\n')
        if (target.isFile) {
            builder.append("大小：").append(target.length()).append("B\n")
            if (target.length() <= 1_000_000) {
                runCatching { target.readText() }.getOrNull()?.let { text ->
                    if (!text.contains('\u0000')) {
                        builder.append("行数：").append(text.lines().size).append('\n')
                    }
                }
            }
        } else {
            builder.append("子项：").append(target.listFiles()?.size ?: 0).append(" 项\n")
        }
        ToolResult(true, builder.toString().trimEnd())
    }
}
