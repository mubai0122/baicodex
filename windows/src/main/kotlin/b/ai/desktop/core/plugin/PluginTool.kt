/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 插件工具：把 plugin.json 里的 command 声明变成模型可调用的工具。
 * 参数走环境变量 PLUGIN_INPUT（避免把用户输入拼进命令行导致引号/注入问题），
 * 工作目录固定为工作区；已登录 GitHub 时额外注入 GITHUB_TOKEN。
 */

package b.ai.desktop.core.plugin

import b.ai.desktop.core.auth.GitHubAuth
import b.ai.desktop.core.tool.Tool
import b.ai.desktop.core.tool.ToolResult
import b.ai.desktop.core.tool.ToolSpec
import b.ai.desktop.core.tool.Workspace
import b.ai.desktop.core.tool.stringArg
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PluginTool(
    private val info: PluginInfo,
    private val command: PluginCommand,
    private val workspace: Workspace,
    private val timeoutSec: Int = 60,
) : Tool {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    override val spec = ToolSpec(
        name = command.name,
        description = "[插件 ${info.title}] ${command.description.ifBlank { command.name }}",
        parameters = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put(
                        "input",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "传给插件命令的输入，命令里用环境变量 PLUGIN_INPUT 取")
                        },
                    )
                },
            )
            put("required", kotlinx.serialization.json.buildJsonArray { add("input") })
        },
    )

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val input = args.stringArg("input")
        val builder = if (isWindows) {
            ProcessBuilder("cmd.exe", "/c", "chcp 65001>nul & ${command.command}")
        } else {
            ProcessBuilder("sh", "-c", command.command)
        }
        builder.directory(workspace.root)
        builder.redirectErrorStream(true)
        // 用户输入不拼命令行：环境变量原样传，中文与引号都不会被转义搞坏
        builder.environment()["PLUGIN_INPUT"] = input
        builder.environment().putAll(GitHubAuth.env())

        val process = runCatching { builder.start() }
            .getOrElse { return@withContext ToolResult(false, "插件命令启动失败：${it.message}") }

        var raw = ByteArray(0)
        val reader = Thread {
            raw = runCatching { b.ai.desktop.core.util.ConsoleOutput.read(process.inputStream, 40_000) }
                .getOrDefault(ByteArray(0))
        }
        reader.isDaemon = true
        reader.start()

        val finished = runCatching { process.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS) }
            .getOrDefault(false)
        if (!finished) {
            process.destroyForcibly()
            reader.join(1200)
            return@withContext ToolResult(
                false,
                "插件命令超时（${timeoutSec}s）已终止\n" + b.ai.desktop.core.util.ConsoleOutput.decode(raw).take(4000),
            )
        }
        reader.join(1500)
        val exit = process.exitValue()
        val body = b.ai.desktop.core.util.ConsoleOutput.decode(raw).ifBlank { "（无输出）" }.take(12_000)
        ToolResult(exit == 0, "exit=$exit\n$body")
    }
}
