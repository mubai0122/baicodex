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
 */

package b.ai.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import b.ai.desktop.app.BaicodexApp
import b.ai.desktop.app.rememberAppState
import b.ai.desktop.dev.runThinkingSelftest
import b.ai.desktop.dev.runDeviceSelftest
import b.ai.desktop.dev.runOnboardingShot
import b.ai.desktop.dev.runMcpSelftest
import b.ai.desktop.dev.runAdbSelftest
import b.ai.desktop.dev.runSettingsShot
import b.ai.desktop.dev.runSandboxSelftest
import b.ai.desktop.dev.runToolSelftest
import b.ai.desktop.dev.runVaultSelftest
import kotlinx.coroutines.runBlocking

/**
 * 程序入口：只负责创建窗口。
 *
 * 代码分层：
 *   app/      应用外壳（AppContent）与跨界面共享状态（AppState）
 *   core/     内核：配置/会话持久化、模型客户端、工具集、Agent 循环
 *   feature/  功能模块：chat（聊天区 + 输入区）、session、topbar、settings
 *   ui/       通用组件（BarChip / SheetCard / InlinePanel）与内联面板
 *   dev/      开发自检（--selftest）
 *   theme/    配色主题（白 + 淡灰，跟随系统）
 */
fun main(args: Array<String>) {
    // gradle run --args="--selftest"：不联网，验证工具链与越界保护
    if (args.contains("--selftest-device")) {
        runDeviceSelftest()
        return
    }

    // gradle run --args="--selftest-sandbox"：不联网，验证沙箱边界与命令守卫
    // gradle run --args="--selftest-adb"：验证 adb 定位/版本/设备解析（无需真机）
    if (args.contains("--selftest-adb")) {
        runAdbSelftest()
        return
    }

    // gradle run --args="--selftest-mcp"：验证 MCP 客户端握手与调用
    if (args.contains("--selftest-mcp")) {
        runMcpSelftest()
        return
    }

    // gradle run --args="--selftest-vault"：离线验证凭据加密回环与绑定开关迁移
    if (args.contains("--selftest-vault")) {
        runVaultSelftest()
        return
    }

    // gradle run --args="--shot-settings=build/shots"：渲染设置页核对版式
    args.firstOrNull { it.startsWith("--shot-settings=") }?.let { arg ->
        runSettingsShot(arg.substringAfter('='))
        return
    }

    // gradle run --args="--shot-onboarding=build/shots"：把引导页渲染成 PNG 核对版式
    args.firstOrNull { it.startsWith("--shot-onboarding=") }?.let { arg ->
        runOnboardingShot(arg.substringAfter('='))
        return
    }

    if (args.contains("--selftest-sandbox")) {
        runSandboxSelftest()
        return
    }

    if (args.contains("--selftest")) {
        runToolSelftest()
        return
    }
    // gradle run --args="--selftest-thinking"：联网验证思考档位与思考内容回传
    if (args.contains("--selftest-thinking")) {
        runBlocking { runThinkingSelftest() }
        return
    }
    launchApp()
}

private fun launchApp() = application {
    val state = rememberAppState()
    val windowState = rememberWindowState(width = 1200.dp, height = 780.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Baicodex",
        state = windowState,
    ) {
        BaicodexApp(state)
    }

    // 桌宠：独立小窗，透明置顶；关掉只影响它自己
    if (state.config.petOn) {
        b.ai.desktop.ui.pet.PetWindow(state)
    }
}
