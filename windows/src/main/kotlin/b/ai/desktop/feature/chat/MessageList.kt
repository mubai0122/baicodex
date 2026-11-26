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
 * Baicodex Windows - 消息列表（含空态与运行中的状态行）
 */

package b.ai.desktop.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    busy: Boolean,
    status: String?,
    style: BubbleViewStyle,
    onMessageMenu: (index: Int, msg: ChatMessage, position: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只隐藏「空文本也没有思考」的助手消息：它是工具调用协议的一环，没有展示价值；
    // 但要把原始下标带上，右键删除时才能改对那条消息
    val visible = messages.mapIndexedNotNull { index, msg ->
        val empty = msg.text.isBlank() && msg.reasoning.isBlank()
        if (msg.role == MsgRole.ASSISTANT && empty) null else index to msg
    }
    val colors = MaterialTheme.colorScheme

    if (visible.isEmpty() && !busy) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "开始对话吧。\n工具会在需要时自动调用：列目录、读文件、写文件、搜索、执行命令。",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (style.compact) 7.dp else 12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(visible) { _, entry ->
            val (index, msg) = entry
            MessageBubble(
                msg = msg,
                style = style,
                onContextMenu = { position -> onMessageMenu(index, msg, position) },
            )
        }
        if (busy) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        status ?: "模型思考中…",
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
