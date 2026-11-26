/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:55:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 桌宠悬浮窗：无边框透明小窗，置顶、可拖、双击图标收起主窗口再点开。
 * 主进程的第二个 Compose Window；关掉它只是关桌面伙伴，不影响主程序。
 */

package b.ai.desktop.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import b.ai.desktop.app.AppState
import b.ai.desktop.ui.component.AppIcon
import java.time.LocalTime
import org.jetbrains.skia.Image

@Composable
fun PetWindow(state: AppState) {
    val size = state.config.petSize.coerceIn(72, 220)
    val windowState = WindowState(size = DpSize(size.dp, (size + 26).dp))

    Window(
        onCloseRequest = { state.editConfig { it.copy(petOn = false) } },
        title = "Baicodex 桌宠",
        state = windowState,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
    ) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(size.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            // 跟着拖动整体移动窗口（px 当 dp 用，高分屏略有偏差，可接受）
                            windowState.position = androidx.compose.ui.window.WindowPosition(
                                windowState.position.x + drag.x.dp,
                                windowState.position.y + drag.y.dp,
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // 双击切换主窗口：关掉桌宠就是最轻的收起方式
                                state.editConfig { it.copy(petOn = false) }
                                state.flash("桌宠已收起；想再唤出：设置 → 外观 → 个性化")
                            },
                            onLongPress = { state.flash("现在是 ${LocalTime.now().withSecond(0)}") },
                        )
                    }
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(size = (size * 0.62f).dp)
            }
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface.copy(alpha = 0.8f))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    "拖动移动 · 双击收起",
                    fontSize = 8.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
