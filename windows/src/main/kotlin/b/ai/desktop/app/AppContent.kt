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
 * Baicodex Windows - 应用外壳
 */

package b.ai.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import b.ai.desktop.feature.chat.ChatScreen
import b.ai.desktop.feature.onboarding.OnboardingScreen
import b.ai.desktop.feature.plugin.PluginPage
import b.ai.desktop.feature.workspace.WorkspacePage
import b.ai.desktop.feature.session.SessionList
import b.ai.desktop.feature.settings.SettingsScreen
import b.ai.desktop.feature.topbar.TopBar
import b.ai.desktop.theme.ChatType
import b.ai.desktop.theme.LocalChatType
import b.ai.desktop.ui.component.ToastBar
import b.ai.desktop.ui.menu.ContextMenuHost
import b.ai.desktop.ui.menu.LocalContextMenu
import b.ai.desktop.ui.menu.rememberContextMenuHost
import b.ai.desktop.ui.panel.ToolApprovalOverlay
import kotlinx.coroutines.delay

@Composable
fun AppContent(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val menu = rememberContextMenuHost()

    // 提示 1.8 秒后自己消失
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(1800)
            state.clearToast()
        }
    }

    CompositionLocalProvider(
        LocalContextMenu provides menu,
        LocalChatType provides ChatType(
            fontSize = state.config.fontSize,
            codeFontSize = state.config.codeFontSize,
            lineHeightPercent = state.config.lineHeight,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Column(Modifier.fillMaxSize()) {
                TopBar()
                HorizontalDivider(color = colors.outlineVariant)

                Row(Modifier.fillMaxSize().weight(1f)) {
                    SessionList(state = state)
                    VerticalDivider(color = colors.outlineVariant)
                    ChatScreen(
                        state = state,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.showPlugins) {
                PluginPage(state = state, onClose = { state.showPlugins = false })
            }

            if (state.showWorkspace) {
                WorkspacePage(state = state, onClose = { state.showWorkspace = false })
            }

            if (state.showSettings) {
                SettingsScreen(
                    state = state,
                    onClose = {
                        state.showSettings = false
                        // 关掉就忘掉上次停在哪个分类，下次打开二级还是空的
                        state.settingsGroup = null
                        state.settingsPage = null
                    },
                )
            }

            // 第一次打开：先走一遍引导，选完沙箱与确认策略再进主界面
            if (!state.config.onboardingDone) {
                OnboardingScreen(
                    state = state,
                    onFinish = { state.finishOnboarding() },
                    onOpenSettings = {
                        state.finishOnboarding()
                        state.settingsGroup = "MODEL"
                        state.settingsPage = "model_cfg"
                        state.showSettings = true
                    },
                )
            }

            // 工具确认盖在所有页面之上：模型在等，用户必须先答
            state.pendingApproval?.let { request ->
                ToolApprovalOverlay(
                    request = request,
                    onDecide = { state.decideApproval(it) },
                    onStopRound = { state.stopRound() },
                )
            }

            ContextMenuHost(menu, Modifier.align(Alignment.TopStart))

            ToastBar(
                text = state.toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            )
        }
    }
}
