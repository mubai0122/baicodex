/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:08:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 复制部分内容
 * 手机端 MessageTextSheet 的桌面版：把整条消息原样摊开交给 SelectionContainer，
 * 鼠标拖选任意片段 Ctrl+C；顶部另有「复制全部」兜底。
 */

package b.ai.desktop.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.util.copyToClipboard

@Composable
fun MessageTextOverlay(
    msg: ChatMessage,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var copied by remember(msg.text) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.26f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            shadowElevation = 18.dp,
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 720.dp)
                .fillMaxHeight(0.78f)
                // 卡片内部点击不冒泡到遮罩，否则一选字就把面板关了
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "复制部分内容",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (copied) "已复制" else "复制全部",
                        fontSize = 12.sp,
                        color = colors.primary,
                        modifier = Modifier
                            .clickable {
                                if (copyToClipboard(msg.text)) copied = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "关闭",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onClose)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    "拖动选中任意片段，Ctrl+C 复制",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        msg.text,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}
