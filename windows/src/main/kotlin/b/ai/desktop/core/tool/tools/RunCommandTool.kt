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
 * 命令执行工具：在工作区目录下跑一条命令，带超时与输出截断
 */

package b.ai.desktop.core.tool.tools

import b.ai.desktop.core.tool.Tool
import b.ai.desktop.core.tool.ToolResult
import b.ai.desktop.core.tool.ToolSpec
import b.ai.desktop.core.tool.Workspace
import b.ai.desktop.core.sandbox.CommandGuard
import b.ai.desktop.core.sandbox.CommandVerdict
import b.ai.desktop.core.sandbox.SandboxPolicy
import b.ai.desktop.core.tool.stringArg
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class RunCommandTool(
    private val ws: Workspace,
    /** 模型没给 timeout_sec 时用的默认值（设置里可调） */
    private val defaultTimeoutSec: Int = 30,
    private val maxTimeoutSec: Int = 120,
    /** 额外环境变量：登录 GitHub 后注入 token，私有仓库命令不用再交互输密码 */
    private val env: () -> Map<String, String> = { emptyMap() },
) : Tool {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    override val spec = ToolSpec(
        name = "run_command",
        description = "在工作区目录下执行一条命令，返回退出码与合并后的输出（stdout+stderr）。适合构建、测试、查看 git 状态等。",
        parameters = objectSchema(
            mapOf(
                "command" to ("string" to "要执行的命令，例如 gradle build 或 git status"),
                "timeout_sec" to ("integer" to "超时秒数，缺省 30，最大 120"),
            ),
            required = listOf("command"),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val command = args.stringArg("command")
        if (command.isBlank()) return@withContext ToolResult(false, "缺少 command 参数")
        // 兜底的第二道闸：系统级破坏性命令连确认都不给，直接拒
        when (val verdict = CommandGuard.check(command)) {
            is CommandVerdict.Blocked -> return@withContext ToolResult(false, verdict.reason)
            else -> Unit
        }
        val timeout = (args.stringArg("timeout_sec").toIntOrNull() ?: defaultTimeoutSec)
            .coerceIn(1, maxTimeoutSec)

        val builder = if (isWindows) {
            ProcessBuilder("cmd.exe", "/c", "chcp 65001>nul & $command")
        } else {
            ProcessBuilder("sh", "-c", command)
        }
        builder.directory(ws.root)
        builder.redirectErrorStream(true)
        runCatching { builder.environment().putAll(env()) }

        val process = runCatching { builder.start() }
            .getOrElse { return@withContext ToolResult(false, "无法启动进程：${it.message}") }

        // 命令输出按字节收，结束后再判断 UTF-8 / GBK，避免中文变成问号
        var raw = ByteArray(0)
        val reader = Thread {
            raw = runCatching { b.ai.desktop.core.util.ConsoleOutput.read(process.inputStream) }.getOrDefault(ByteArray(0))
        }
        reader.isDaemon = true
        reader.start()

        val finished = runCatching { process.waitFor(timeout.toLong(), TimeUnit.SECONDS) }
            .getOrDefault(false)

        if (!finished) {
            process.destroyForcibly()
            reader.join(1500)
            return@withContext ToolResult(
                false,
                "命令超时（${timeout}s）已终止\n" + b.ai.desktop.core.util.ConsoleOutput.decode(raw).take(6000),
            )
        }

        reader.join(2000)
        val exit = process.exitValue()
        val text = b.ai.desktop.core.util.ConsoleOutput.decode(raw)
        val body = text.take(12_000)
        val tail = if (text.length > 12_000) "\n…输出过长，已截断" else ""
        ToolResult(exit == 0, "exit=$exit\n$body$tail")
    }
}
