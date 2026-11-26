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
 * 工具层：工具定义、执行结果、注册表与工作区越界保护
 */

package b.ai.desktop.core.tool

import java.io.File
import b.ai.desktop.core.sandbox.SandboxMode
import b.ai.desktop.core.sandbox.SandboxPolicy
import b.ai.desktop.core.sandbox.SandboxViolation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull

/** 给模型看的工具声明 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/** 模型发起的一次工具调用 */
data class ToolCall(
    val id: String,
    val name: String,
    val args: JsonObject,
)

/** 工具执行结果；output 直接回灌给模型 */
data class ToolResult(
    val ok: Boolean,
    val output: String,
)

interface Tool {
    val spec: ToolSpec
    suspend fun run(args: JsonObject): ToolResult
}

/**
 * 工作区：所有文件类工具都被限制在这个根目录内，防止模型越界读写。
 * 传绝对路径时也必须是根目录下的路径。
 *
 * [mode] 是沙箱档位：严格档连「系统目录里的工作区」都不放行写入，
 * 标准档只做基础校验，宽松档跳过敏感目录检查（路径越界这一条永远不放开）。
 */
class Workspace(
    val root: File,
    val mode: SandboxMode = SandboxMode.STANDARD,
) {
    private val canonicalRoot: File = root.canonicalFile.apply { mkdirs() }

    fun resolve(path: String): File {
        val raw = path.trim()
        if (raw.isEmpty()) return canonicalRoot
        // 用未 trim 的原文查：结尾空格会被 Win32 悄悄截断，必须先于 trim 拦下来
        SandboxPolicy.checkRawPath(path)
        val target = if (File(raw).isAbsolute) File(raw) else File(canonicalRoot, raw)
        val canonical = target.canonicalFile
        val rootPath = canonicalRoot.path
        if (canonical != canonicalRoot && !canonical.path.startsWith(rootPath + File.separator)) {
            throw SandboxViolation("路径越界（只允许工作区内）：$raw")
        }
        return canonical
    }

    /** 要动磁盘的路径：在 resolve 之上再过一遍系统目录 / 盘根检查 */
    fun resolveWritable(path: String): File {
        val file = resolve(path)
        SandboxPolicy.checkWritableTarget(canonicalRoot, file, mode)
        return file
    }

    fun relative(file: File): String =
        runCatching { file.canonicalFile.relativeTo(canonicalRoot).path }
            .getOrElse { file.name }
            .ifBlank { "." }
}

class ToolRegistry(private val tools: List<Tool>) {
    /** 合并另一批工具（插件工具、Git 工具等后挂的能力） */
    operator fun plus(extra: List<Tool>): ToolRegistry = ToolRegistry(tools + extra)

    fun specs(): List<ToolSpec> = tools.map { it.spec }

    fun names(): List<String> = tools.map { it.spec.name }

    suspend fun run(call: ToolCall): ToolResult {
        val tool = tools.firstOrNull { it.spec.name == call.name }
            ?: return ToolResult(false, "未知工具：${call.name}（可用：${names().joinToString(", ")}）")
        return runCatching { tool.run(call.args) }
            .getOrElse { e -> ToolResult(false, "工具执行异常：${e.message ?: e::class.simpleName}") }
    }
}

/** 字符串参数取值（缺省空串）。用 content 而不是 toString，避免把 JSON 转义（\n 等）当内容 */
internal fun JsonObject.stringArg(name: String): String =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

/** 整数参数（缺省 null） */
internal fun JsonObject.intArg(name: String): Int? =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.toIntOrNull()

/** 布尔参数（缺省 false） */
internal fun JsonObject.boolArg(name: String): Boolean =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false

/** 原样取字符串参数（保留换行等真实内容） */
internal fun JsonObject.rawStringArg(name: String): String? =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content
