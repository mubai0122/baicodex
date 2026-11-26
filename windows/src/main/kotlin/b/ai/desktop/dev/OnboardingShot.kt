/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:12:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 出图自检：把首启引导渲染成 PNG，用来核对版式（不打开窗口）。
 * gradle run --args="--shot-onboarding=build/shots"
 */

package b.ai.desktop.dev

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import b.ai.desktop.app.AppState
import b.ai.desktop.feature.onboarding.OnboardingScreen
import b.ai.desktop.theme.BaicodexTheme
import b.ai.desktop.theme.ThemeMode
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

fun runOnboardingShot(outDir: String) {
    val dir = File(outDir).apply { mkdirs() }
    val state = AppState()

    listOf(1200 to 800, 980 to 720).forEachIndexed { index, (width, height) ->
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
        ) {
            ShotRoot(state)
        }
        try {
            val image = scene.render()
            val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("渲染结果无法编码成 PNG")
            dir.resolve("onboarding-${width}x$height.png").writeBytes(bytes)
            println("saved onboarding-${width}x$height.png · ${bytes.size} bytes")
        } finally {
            scene.close()
        }
    }
}

@Composable
private fun ShotRoot(state: AppState) {
    BaicodexTheme(ThemeMode.LIGHT) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            OnboardingScreen(
                state = state,
                onFinish = {},
                onOpenSettings = {},
            )
        }
    }
}
