/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MCP 自检：起一个假的 MCP 服务器（node），走握手 → tools/list → tools/call。
 * gradle run --args="--selftest-mcp"
 */

package b.ai.desktop.dev

import b.ai.desktop.core.mcp.McpManager
import b.ai.desktop.core.mcp.McpServerCfg
import b.ai.desktop.core.mcp.McpTool
import kotlinx.coroutines.runBlocking
import java.io.File

fun runMcpSelftest() {
    val serverScript = File("build/mcp-selftest-server.js")
    if (!serverScript.exists()) {
        println("[FAIL] 找不到 ${serverScript.absolutePath}，请先在 build/ 下生成")
        return
    }
    val cfg = McpServerCfg(label = "自检服务", command = "node", args = serverScript.absolutePath)
    runBlocking {
        try {
            val session = McpManager.session(cfg)
            val tools = session.listTools()
            check(tools.size == 1 && tools[0].name == "echo") { "工具列表不对：$tools" }
            println("[OK ] 握手 + tools/list：echo · ${tools[0].description}")

            val tool = McpTool(cfg.displayName, tools[0], cfg)
            val result = tool.run(kotlinx.serialization.json.buildJsonObject {
                put("text", kotlinx.serialization.json.JsonPrimitive("你好 MCP"))
            })
            check(result.ok && result.output.contains("echo:")) { "调用结果不对：${result.output}" }
            println("[OK ] tools/call：${result.output.take(60)}")
            println("--- MCP 自检全部通过 ---")
        } catch (t: Throwable) {
            println("[FAIL] ${t.message}")
        } finally {
            McpManager.shutdown()
        }
    }
}
