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
 * 工具自检：`gradle run --args="--selftest"` 时执行，验证工具链与越界保护。
 * 不联网、不需要模型。
 */

package b.ai.desktop.dev

import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.plugin.PluginStore
import b.ai.desktop.core.tool.ToolCall
import b.ai.desktop.core.tool.Workspace
import b.ai.desktop.core.tool.defaultToolRegistry
import b.ai.desktop.core.util.CtxStat
import b.ai.desktop.core.util.Tokens
import b.ai.desktop.core.model.ChatMessage
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun runToolSelftest() = runBlocking {
    val root = File(ConfigStore.appDir(), "selftest").apply { mkdirs() }
    println("工作区：${root.absolutePath}")
    val registry = defaultToolRegistry(root)
    println("工具：${registry.names().joinToString(", ")}")
    println("---")

    suspend fun call(name: String, args: JsonObject) {
        val result = registry.run(ToolCall(id = "selftest", name = name, args = args))
        val body = result.output.lineSequence().take(8).joinToString("\n")
        println("[${if (result.ok) "OK " else "ERR"}] $name → $body")
    }

    call(
        "write_file",
        buildJsonObject {
            put("path", "hello.txt")
            put("content", "hello baicodex\nsecond line")
        },
    )
    call("list_dir", buildJsonObject { put("path", "") })
    call("read_file", buildJsonObject { put("path", "hello.txt") })
    call(
        "edit_file",
        buildJsonObject {
            put("path", "hello.txt")
            put("old_string", "second line")
            put("new_string", "second line (edited)")
        },
    )
    call("read_file", buildJsonObject { put("path", "hello.txt") })
    call(
        "write_file",
        buildJsonObject {
            put("path", "dup.txt")
            put("content", "dup dup")
        },
    )
    // 多处命中且未允许全部替换 → 必须报错
    call(
        "edit_file",
        buildJsonObject {
            put("path", "dup.txt")
            put("old_string", "dup")
            put("new_string", "DUP")
        },
    )
    call(
        "edit_file",
        buildJsonObject {
            put("path", "dup.txt")
            put("old_string", "dup")
            put("new_string", "DUP")
            put("replace_all", true)
        },
    )
    call("make_dir", buildJsonObject { put("path", "sub/dir") })
    call("stat_path", buildJsonObject { put("path", "hello.txt") })
    call(
        "move_path",
        buildJsonObject {
            put("from", "hello.txt")
            put("to", "sub/dir/hello.txt")
        },
    )
    call("list_dir", buildJsonObject { put("path", "sub/dir") })
    call("search_text", buildJsonObject { put("query", "DUP") })
    call("delete_path", buildJsonObject { put("path", "sub") })
    call("delete_path", buildJsonObject { put("path", "dup.txt") })
    call("search_text", buildJsonObject { put("query", "edited") })
    call(
        "run_command",
        buildJsonObject {
            put("command", "echo selftest-ok")
            put("timeout_sec", 10)
        },
    )
    call("read_file", buildJsonObject { put("path", "../config.json") })
    println("---")
    runPluginSelftest(root)
    println("---")
    println("自检结束")
}

/**
 * 插件自检：创建骨架 → 读回 → 跑插件工具 → 卸载，全程用真实目录。
 * 同时验一下 token 估算与上下文统计，预算卡的数字就来自这里。
 */
private suspend fun runPluginSelftest(root: File) {
    val id = "selftest-plugin"
    runCatching { PluginStore.uninstall(id) }   // 上次中断留下的先清掉

    PluginStore.create(id, "自检插件", "只用来验证插件链路", "这是插件追加的提示词")
        .onFailure { println("[ERR] 创建插件：${it.message}") }
        .onSuccess { println("[OK ] 创建插件：${it.dir.absolutePath}") }

    val info = PluginStore.list().firstOrNull { it.id == id }
    if (info == null) {
        println("[ERR] 插件列表里没找到 $id")
        return
    }
    println("[OK ] 读回插件：${info.title} v${info.version}，工具 ${info.manifest.tools.size} 个，提示词 ${info.manifest.prompt.length} 字")

    val workspace = Workspace(root)
    val tools = PluginStore.tools(info, workspace, 30)
    val registry = b.ai.desktop.core.tool.ToolRegistry(tools)
    val tool = tools.firstOrNull()
    if (tool == null) {
        println("[ERR] 插件里没有可用工具")
    } else {
        val result = registry.run(
            ToolCall(
                id = "selftest",
                name = tool.spec.name,
                args = buildJsonObject { put("input", "插件中文输入 · ok") },
            ),
        )
        val ok = result.ok && result.output.contains("插件中文输入 · ok")
        println("[${if (ok) "OK " else "ERR"}] 插件工具 ${tool.spec.name} → ${result.output.trim().take(80)}")
    }

    // 目录安装：临时目录 → plugins/<id>，再卸载
    val src = File.createTempFile("plugin-src-", "")
    src.delete()
    src.mkdirs()
    File(src, "plugin.json").writeText(
        """{"id":"selftest-dir","name":"目录安装","version":"0.0.2","description":"验证目录安装","prompt":""}""",
    )
    PluginStore.installFromDir(src).fold(
        onSuccess = { println("[OK ] 目录安装：${it.id} v${it.version}") },
        onFailure = { println("[ERR] 目录安装：${it.message}") },
    )
    runCatching { PluginStore.uninstall("selftest-dir") }
    src.deleteRecursively()

    PluginStore.uninstall(id).fold(
        onSuccess = { println("[OK ] 卸载插件：$id") },
        onFailure = { println("[ERR] 卸载插件：${it.message}") },
    )

    val sample = listOf(
        ChatMessage(role = "user", text = "用表格对比 REST 和 GraphQL"),
        ChatMessage(role = "assistant", text = "REST 是按资源建模，GraphQL 是一次请求取多块数据。", reasoning = "先想清楚差异"),
    )
    val stat = CtxStat.of(sample, 32000)
    println("[${if (stat.used > 0 && stat.count == 2) "OK " else "ERR"}] 用量估算：${stat.used} tokens / ${stat.count} 条（${Tokens.estimate("中文 abc")} tokens 抽样）")
}
