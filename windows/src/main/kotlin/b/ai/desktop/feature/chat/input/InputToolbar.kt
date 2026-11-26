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
 * Baicodex Windows - 输入区上方胶囊工具栏
 * 运行模式五档 / 深度思考 / 最大模式 / 语音 / 全屏输入
 */

package b.ai.desktop.feature.chat.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.feature.chat.AgentMode
import b.ai.desktop.feature.chat.ChatState
import b.ai.desktop.ui.component.BarChip

@Composable
fun InputToolbar(
    chat: ChatState,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 运行模式：五档
        Box {
            OutlinedButton(
                onClick = { modeMenuExpanded = !modeMenuExpanded },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    chat.agentMode.label,
                    fontSize = 11.sp,
                    fontWeight = if (chat.agentMode != AgentMode.STANDARD) FontWeight.Bold else FontWeight.Medium,
                    color = if (chat.agentMode != AgentMode.STANDARD) colors.primary else colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
                Text("▾", fontSize = 9.sp, color = colors.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false },
            ) {
                AgentMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, fontSize = 12.sp) },
                        onClick = {
                            chat.agentMode = mode
                            modeMenuExpanded = false
                        },
                        leadingIcon = {
                            if (mode == chat.agentMode) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else null
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))

        // 深度思考（与配置面板同一份状态）
        BarChip(active = chat.thinkingEnabled, onClick = { chat.thinkingEnabled = !chat.thinkingEnabled }) {
            Text(
                if (chat.thinkingEnabled) "深度思考·中" else "深度思考",
                fontSize = 11.sp,
                fontWeight = if (chat.thinkingEnabled) FontWeight.Bold else FontWeight.Medium,
                color = if (chat.thinkingEnabled) colors.primary else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(6.dp))

        // 最大模式
        BarChip(active = chat.maxMode, onClick = { chat.maxMode = !chat.maxMode }) {
            Text(
                if (chat.maxMode) "最大模式·开" else "最大模式",
                fontSize = 11.sp,
                fontWeight = if (chat.maxMode) FontWeight.Bold else FontWeight.Medium,
                color = if (chat.maxMode) colors.primary else colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        IconToggleButton(
            checked = chat.voiceEnabled,
            onCheckedChange = { chat.voiceEnabled = it },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "语音",
                tint = if (chat.voiceEnabled) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onOpenFullscreen, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "全屏输入",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
