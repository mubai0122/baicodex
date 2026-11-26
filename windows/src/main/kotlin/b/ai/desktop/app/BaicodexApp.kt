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
 * Baicodex Windows - 应用根节点
 */

package b.ai.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import b.ai.desktop.theme.BaicodexTheme
import b.ai.desktop.theme.UiMetrics

@Composable
fun BaicodexApp(state: AppState? = null) {
    val app = state ?: rememberAppState()
    // 全局缩进：把整个界面的 dp/sp 一起放大缩小（80%–150%）
    val base = LocalDensity.current
    val scaled = Density(base.density * UiMetrics.uiScale, base.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        BaicodexTheme(app.themeMode, app.accent, UiMetrics.fontFamily) {
            AppContent(app)
        }
    }
}
