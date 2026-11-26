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
 * Baicodex Windows - 全屏输入
 * 覆盖聊天区的整页编辑模式：大输入区 + 字数 + 发送/关闭，内容与输入条共享同一份 draft。
 */

package b.ai.desktop.feature.chat.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.data.Attachment

@Composable
fun FullscreenInput(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<Attachment>,
    onSend: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(
        color = colors.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(colors.surface)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("全屏输入", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Spacer(Modifier.width(10.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = colors.surfaceVariant) {
                    Text(
                        if (attachments.isEmpty()) "无附件" else "${attachments.size} 个附件",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("${draft.length} 字", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "退出全屏",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant)

            // 正文
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.surface)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focus)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when {
                                event.key == Key.Escape -> {
                                    onClose(); true
                                }
                                event.key == Key.Enter && event.isCtrlPressed -> {
                                    onSend(); true
                                }
                                else -> false
                            }
                        },
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp, lineHeight = 22.sp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                "在这里写长内容，Ctrl+Enter 发送，Esc 返回",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        inner()
                    },
                )
            }

            HorizontalDivider(color = colors.outlineVariant)

            // 底部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (attachments.isNotEmpty()) {
                    Text(
                        attachments.joinToString("  ") { it.badge },
                        fontSize = 10.sp,
                        color = colors.primary,
                    )
                } else {
                    Text("Enter 换行 · Ctrl+Enter 发送 · Esc 返回", fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("发送", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
