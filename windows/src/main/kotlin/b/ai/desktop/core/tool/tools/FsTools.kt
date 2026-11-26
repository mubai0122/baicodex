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
 * 文件类工具：列目录 / 读文件 / 写文件 / 文本搜索（全部限制在工作区内）
 */

package b.ai.desktop.core.tool.tools

import b.ai.desktop.core.tool.Tool
import b.ai.desktop.core.tool.ToolResult
import b.ai.desktop.core.tool.ToolSpec
import b.ai.desktop.core.tool.Workspace
import b.ai.desktop.core.sandbox.CommandGuard
import b.ai.desktop.core.sandbox.CommandVerdict
import b.ai.desktop.core.sandbox.SandboxPolicy
import b.ai.desktop.core.tool.rawStringArg
import b.ai.desktop.core.tool.stringArg
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 参数 schema 构造：key -> (类型, 说明) */
internal fun objectSchema(
    props: Map<String, Pair<String, String>>,
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", "object")
    put(
        "properties",
        buildJsonObject {
            props.forEach { (key, meta) ->
                put(
                    key,
                    buildJsonObject {
                        put("type", meta.first)
                        put("description", meta.second)
                    },
                )
            }
        },
    )
    put(
        "required",
        kotlinx.serialization.json.buildJsonArray {
            required.forEach { add(it) }
        },
    )
}

/** 列目录（只列一层） */
class ListDirTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "list_dir",
        description = "列出工作区内某个目录下的文件与子目录（只列一层）。想了解项目结构时先用它。",
        parameters = objectSchema(
            mapOf("path" to ("string" to "相对工作区的目录路径，缺省为工作区根目录")),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val dir = ws.resolve(args.stringArg("path"))
        when {
            !dir.exists() -> return@withContext ToolResult(false, "目录不存在：${ws.relative(dir)}")
            !dir.isDirectory -> return@withContext ToolResult(false, "不是目录：${ws.relative(dir)}")
        }
        val entries = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        if (entries.isEmpty()) return@withContext ToolResult(true, "目录 ${ws.relative(dir)} 是空的")

        val body = entries.take(300).joinToString("\n") { file ->
            if (file.isDirectory) "DIR  ${file.name}/"
            else "FILE ${file.name}  ${file.length()}B"
        }
        val more = if (entries.size > 300) "\n…共 ${entries.size} 项，只显示前 300 项" else ""
        ToolResult(true, "目录：${ws.relative(dir)}\n$body$more")
    }
}

/** 读文件 */
class ReadFileTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "read_file",
        description = "读取工作区内一个文本文件的内容。改文件之前先读它。",
        parameters = objectSchema(
            mapOf(
                "path" to ("string" to "相对工作区的文件路径"),
                "max_lines" to ("integer" to "最多读取行数，缺省 400"),
            ),
            required = listOf("path"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.stringArg("path")
        if (path.isBlank()) return@withContext ToolResult(false, "缺少 path 参数")
        val file = ws.resolve(path)
        when {
            !file.exists() -> return@withContext ToolResult(false, "文件不存在：${ws.relative(file)}")
            file.isDirectory -> return@withContext ToolResult(false, "这是目录，请用 list_dir：${ws.relative(file)}")
        }
        if (file.length() > 2_000_000) {
            return@withContext ToolResult(false, "文件过大（${file.length()}B），请用 run_command 配合 head/tail 分段查看")
        }
        val bytes = file.readBytes()
        if (bytes.take(4096).any { it.toInt() == 0 }) {
            return@withContext ToolResult(false, "看起来是二进制文件，未读取：${ws.relative(file)}")
        }
        val maxLines = args.stringArg("max_lines").toIntOrNull()?.coerceIn(1, 5000) ?: 400
        val lines = bytes.toString(Charsets.UTF_8).lines()
        val shown = lines.take(maxLines).mapIndexed { index, line -> "${index + 1}\t$line" }
        val more = if (lines.size > maxLines) "\n…共 ${lines.size} 行，只显示前 $maxLines 行" else ""
        ToolResult(true, "${ws.relative(file)}（${lines.size} 行）\n${shown.joinToString("\n")}$more")
    }
}

/** 写文件（覆盖或新建） */
class WriteFileTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "write_file",
        description = "把内容写入工作区内的文件（不存在则新建，存在则整体覆盖）。改前请先 read_file。",
        parameters = objectSchema(
            mapOf(
                "path" to ("string" to "相对工作区的文件路径"),
                "content" to ("string" to "要写入的完整文件内容"),
            ),
            required = listOf("path", "content"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.stringArg("path")
        if (path.isBlank()) return@withContext ToolResult(false, "缺少 path 参数")
        val content = args.rawStringArg("content")
            ?: return@withContext ToolResult(false, "缺少 content 参数")
        if (content.toByteArray().size > SandboxPolicy.MAX_WRITE_BYTES) {
            return@withContext ToolResult(
                false,
                "内容过大（${content.length} 字符），单次写入上限 ${SandboxPolicy.MAX_WRITE_BYTES / 1024 / 1024}MB，请拆成几次写",
            )
        }
        val file = ws.resolveWritable(path)
        if (file.isDirectory) return@withContext ToolResult(false, "目标是目录：${ws.relative(file)}")
        file.parentFile?.mkdirs()
        runCatching { file.writeText(content) }
            .getOrElse { return@withContext ToolResult(false, "写入失败：${it.message}") }
        ToolResult(true, "已写入 ${ws.relative(file)}（${content.length} 字符）")
    }
}

/** 文本搜索 */
class SearchTextTool(private val ws: Workspace) : Tool {
    override val spec = ToolSpec(
        name = "search_text",
        description = "在工作区内的文本文件里按关键字搜索（大小写不敏感），返回命中行。",
        parameters = objectSchema(
            mapOf(
                "query" to ("string" to "要搜索的关键字"),
                "path" to ("string" to "限定搜索的子目录，缺省为工作区根目录"),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val query = args.stringArg("query")
        if (query.isBlank()) return@withContext ToolResult(false, "缺少 query 参数")
        val base = ws.resolve(args.stringArg("path"))
        if (!base.exists()) return@withContext ToolResult(false, "路径不存在：${ws.relative(base)}")

        val hits = mutableListOf<String>()
        var scanned = 0
        var truncated = false
        val files = base.walkTopDown()
            .onEnter { it.name != ".git" && it.name != "node_modules" && it.name != "build" }
            .filter { it.isFile && it.length() <= 1_000_000 }

        for (file in files) {
            if (hits.size >= 60) {
                truncated = true
                break
            }
            scanned++
            if (scanned > 3000) {
                truncated = true
                break
            }
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (text.contains('\u0000')) continue
            text.lineSequence().forEachIndexed { index, line ->
                if (hits.size < 60 && line.contains(query, ignoreCase = true)) {
                    hits += "${ws.relative(file)}:${index + 1}: ${line.trim().take(200)}"
                }
            }
        }

        if (hits.isEmpty()) {
            ToolResult(true, "没有命中「$query」（扫描 $scanned 个文件）")
        } else {
            val more = if (truncated) "\n…结果较多，已截断" else ""
            ToolResult(true, "命中 ${hits.size} 处：\n${hits.joinToString("\n")}$more")
        }
    }
}

/** 供工具内部使用：跳过明显不该扫的目录 */
internal fun File.isScannable(): Boolean =
    name != ".git" && name != "node_modules" && name != "build" && name != ".gradle"
