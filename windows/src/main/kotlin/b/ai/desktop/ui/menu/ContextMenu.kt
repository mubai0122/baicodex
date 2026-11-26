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
 * Baicodex Windows - 右键菜单卡片
 * 与内联 Sheet 同一套视觉：实色卡 + 圆角 + 描边 + 阴影；行 hover 淡底，危险项走 error 色。
 */

package b.ai.desktop.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 菜单宽度与行高：宿主用它估算弹出高度，保证贴边时能往上翻 */
internal val ContextMenuWidth: Dp = 204.dp
internal val ContextMenuRowHeight: Dp = 30.dp
internal val ContextMenuDividerHeight: Dp = 7.dp
internal val ContextMenuPadding: Dp = 4.dp

sealed interface MenuEntry {
    data class Action(
        val label: String,
        val hint: String? = null,
        val danger: Boolean = false,
        val enabled: Boolean = true,
        /** true = 点完不关菜单，由回调自己把内容换成下一步（比如二次确认） */
        val keepOpen: Boolean = false,
        val onClick: () -> Unit,
    ) : MenuEntry

    data object Divider : MenuEntry
}

@Composable
fun ContextMenuCard(
    entries: List<MenuEntry>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = colors.surface,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.width(ContextMenuWidth),
    ) {
        Column(Modifier.padding(ContextMenuPadding)) {
            entries.forEach { entry ->
                when (entry) {
                    MenuEntry.Divider -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .height(1.dp)
                            .background(colors.outlineVariant),
                    )

                    is MenuEntry.Action -> MenuRow(entry) {
                        if (!entry.keepOpen) onClose()
                        entry.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuEntry.Action, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val tone = when {
        !item.enabled -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        item.danger -> colors.error
        else -> colors.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ContextMenuRowHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered && item.enabled) colors.surfaceVariant else Color.Transparent)
            .hoverable(interaction, enabled = item.enabled)
            .clickable(
                enabled = item.enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, color = tone, fontSize = 12.sp, maxLines = 1)
        if (item.hint != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                item.hint,
                color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}
