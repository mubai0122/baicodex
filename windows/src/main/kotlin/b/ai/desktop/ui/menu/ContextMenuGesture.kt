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
 * Baicodex Windows - 右键手势
 * 桌面端取右键位置：监听 Press 且 buttons.isSecondaryPressed，转换成窗口坐标交给宿主，
 * 主键点击仍走 clickable，两者互不影响。
 */

package b.ai.desktop.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 给一块区域挂右键菜单。
 *
 * @param enabled false 时完全不响应（比如没有选中会话）
 * @param onClick 主键点击（可选；为空时只处理右键）
 * @param onContextMenu 右键回调，参数是窗口坐标
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.contextMenuTarget(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onContextMenu: (Offset) -> Unit,
): Modifier {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestClick by rememberUpdatedState(onClick)
    val latestContext by rememberUpdatedState(onContextMenu)

    val interactive = this
        .onGloballyPositioned { coords = it }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    // Initial 阶段（从外到内）就把右键吃掉，内层 clickable 便不会把它当成一次点击
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press) continue
                    if (!event.buttons.isSecondaryPressed) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val at = change.position
                    event.changes.forEach { it.consume() }
                    latestContext(coords?.localToWindow(at) ?: at)
                }
            }
        }

    return if (latestClick == null) interactive
    else interactive.then(Modifier.clickable(onClick = { latestClick?.invoke() }))
}
