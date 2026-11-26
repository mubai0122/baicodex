/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 00:52:12
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 右键菜单宿主
 * 全窗口只有一个宿主：任何位置右键 → 记录窗口坐标 → 在光标处弹出。
 * 贴右/下边时自动回退，不会出窗口；点击外部、Esc、选中任意项都会关闭。
 */

package b.ai.desktop.ui.menu

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

class ContextMenuHostState {
    var entries by mutableStateOf<List<MenuEntry>?>(null)
        private set

    /** 弹出点：窗口坐标（px） */
    var anchor by mutableStateOf(Offset.Zero)
        private set

    val isOpen: Boolean get() = entries != null

    fun open(windowPosition: Offset, items: List<MenuEntry>) {
        if (items.isEmpty()) return
        anchor = windowPosition
        entries = items
    }

    /** 就地换内容（二次确认），不重新定位也不播入场动画 */
    fun replace(items: List<MenuEntry>) {
        if (items.isEmpty()) {
            entries = null
            return
        }
        entries = items
    }

    fun dismiss() {
        entries = null
    }
}

/** 没装宿主时给个空壳，右键静默无效而不是崩溃 */
val LocalContextMenu = staticCompositionLocalOf { ContextMenuHostState() }

@Composable
fun rememberContextMenuHost(): ContextMenuHostState = remember { ContextMenuHostState() }

@Composable
fun ContextMenuHost(host: ContextMenuHostState, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val focus = remember { FocusRequester() }

    Box(modifier = modifier.onGloballyPositioned { origin = it.localToWindow(Offset.Zero) }) {
        val entries = host.entries ?: return@Box
        val anchor = host.anchor

        var appear by remember(anchor) { mutableStateOf(false) }
        LaunchedEffect(anchor) { appear = true }
        val progress by animateFloatAsState(
            targetValue = if (appear) 1f else 0f,
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
            label = "contextMenuIn",
        )

        val rows = entries.count { it is MenuEntry.Action }
        val dividers = entries.size - rows
        val height = ContextMenuPadding * 2f +
            ContextMenuRowHeight * rows.toFloat() +
            ContextMenuDividerHeight * dividers.toFloat()

        val marginPx = with(density) { 6.dp.toPx() }
        val widthPx = with(density) { ContextMenuWidth.toPx() }
        val heightPx = with(density) { height.toPx() }

        val localX = anchor.x - origin.x
        val localY = anchor.y - origin.y
        val maxX = (windowSize.width - widthPx - marginPx).coerceAtLeast(marginPx)
        val x = localX.coerceIn(marginPx, maxX)
        val y = if (localY + heightPx + marginPx > windowSize.height) {
            (localY - heightPx).coerceAtLeast(marginPx)
        } else {
            localY
        }

        Popup(
            offset = IntOffset(x.roundToInt(), y.roundToInt()),
            onDismissRequest = host::dismiss,
            properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
        ) {
            // popup 挂到窗口后再抢焦点，否则 Esc 收不到
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { focus.requestFocus() }
            }
            ContextMenuCard(
                entries = entries,
                onClose = host::dismiss,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = progress
                        val scale = 0.96f + 0.04f * progress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            host.dismiss()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}
