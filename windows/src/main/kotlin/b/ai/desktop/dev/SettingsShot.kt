/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:30:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 出图自检：把设置页渲染成 PNG，核对三栏版式与新控件（不打开窗口）。
 * gradle run --args="--shot-settings=build/shots"
 */

package b.ai.desktop.dev

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import b.ai.desktop.app.AppState
import b.ai.desktop.core.i18n.I18n
import b.ai.desktop.feature.settings.SettingsScreen
import b.ai.desktop.theme.Accent
import b.ai.desktop.theme.BaicodexTheme
import b.ai.desktop.theme.ThemeMode
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

private data class Shot(val name: String, val group: String?, val page: String?, val accent: Accent = Accent.BLUE, val lang: String = "")

fun runSettingsShot(outDir: String) {
    val dir = File(outDir).apply { mkdirs() }
    val shots = listOf(
        Shot("default", null, null),
        Shot("model_cfg", "MODEL", "model_cfg"),
        Shot("ui_person", "LOOK", "ui_person", accent = Accent.ROSE),
        Shot("ext_agent", "EXT", "ext_agent"),
        Shot("ext_mcp", "EXT", "ext_mcp"),
        Shot("ext_privacy", "EXT", "ext_privacy"),
        Shot("chat_tags", "CHAT", "chat_tags"),
        Shot("ext_backup", "EXT", "ext_backup"),
        Shot("chat_ctx", "CHAT", "chat_ctx"),
        Shot("model_think", "MODEL", "model_think"),
        Shot("stat", "STAT", "stat"),
        Shot("ui_lang", "LOOK", "ui_lang", lang = "en"),
    )

    shots.forEach { shot ->
        val state = AppState()
        if (shot.group != null) {
            state.settingsGroup = shot.group
            state.settingsPage = shot.page
        }
        if (shot.lang.isNotBlank()) {
            state.editConfig { it.copy(lang = shot.lang) }
            I18n.switch(shot.lang)
        }
        val scene = ImageComposeScene(width = 1200, height = 760, density = Density(1f)) {
            ShotRoot(state, shot.accent)
            if (shot.lang.isNotBlank()) {
                LaunchedEffect(Unit) { /* lang 已在场景外切好 */ }
            }
        }
        try {
            val image = scene.render()
            val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("渲染结果无法编码成 PNG")
            dir.resolve("settings-${shot.name}.png").writeBytes(bytes)
            println("saved settings-${shot.name}.png · ${bytes.size} bytes")
        } finally {
            scene.close()
        }
        I18n.switch("")
    }
}

@Composable
private fun ShotRoot(state: AppState, accent: Accent) {
    BaicodexTheme(ThemeMode.LIGHT, accent) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            SettingsScreen(state, onClose = {})
        }
    }
}
