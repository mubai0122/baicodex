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
 * Baicodex Windows - 左侧侧边栏
 * 搜索 + 全部/收藏过滤 + 会话列表 + 底部新对话/设置；右键出会话操作。
 */

package b.ai.desktop.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.model.Session
import b.ai.desktop.ui.component.BarChip
import b.ai.desktop.ui.menu.LocalContextMenu
import b.ai.desktop.ui.menu.contextMenuTarget
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SessionList(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val menu = LocalContextMenu.current

    var query by remember { mutableStateOf("") }
    var onlyStarred by remember { mutableStateOf(false) }

    val key = query.trim()

    /** 命中次数 + 第一条命中的上下文片段（搜正文时列表里能直接看到为什么命中） */
    fun hitOf(session: Session): Pair<Int, String?> {
        if (key.isEmpty()) return 0 to null
        var count = 0
        if (session.title.contains(key, ignoreCase = true)) count++
        var snippet: String? = null
        session.messages.forEach { msg ->
            val text = msg.text
            if (text.isBlank()) return@forEach
            var from = 0
            while (true) {
                val at = text.indexOf(key, from, ignoreCase = true)
                if (at < 0) break
                count++
                if (snippet == null) {
                    val start = (at - 16).coerceAtLeast(0)
                    val end = (at + key.length + 24).coerceAtMost(text.length)
                    snippet = (if (start > 0) "…" else "") +
                        text.substring(start, end).replace('\n', ' ') +
                        (if (end < text.length) "…" else "")
                }
                from = at + key.length
            }
        }
        return count to snippet
    }

    val shown = remember(state.sessions, query, onlyStarred) {
        state.sessions.filter { session ->
            val starred = session.messages.any { it.starred }
            val hit = key.isEmpty() || hitOf(session).first > 0
            (!onlyStarred || starred) && hit
        }
    }

    Column(
        modifier = modifier
            .width(state.config.sidebarWidth.dp)
            .fillMaxHeight()
            .background(colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("会话", color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("${state.sessions.size}", color = colors.onSurfaceVariant, fontSize = 10.sp)
        }

        SearchBox(query = query, onChange = { query = it })

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BarChip(active = !onlyStarred, onClick = { onlyStarred = false }) {
                Text(
                    "全部",
                    fontSize = 11.sp,
                    color = if (!onlyStarred) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (!onlyStarred) FontWeight.Bold else FontWeight.Medium,
                )
            }
            BarChip(active = onlyStarred, onClick = { onlyStarred = true }) {
                Text(
                    "收藏",
                    fontSize = 11.sp,
                    color = if (onlyStarred) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (onlyStarred) FontWeight.Bold else FontWeight.Medium,
                )
            }
            if (key.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Text(
                    "${shown.size} 个会话",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        HorizontalDivider(color = colors.outlineVariant)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.sessions.isEmpty() -> CenterHint("还没有会话")
                shown.isEmpty() -> CenterHint(if (onlyStarred) "还没有收藏的消息" else "没有匹配的会话")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(shown, key = { it.id }) { session ->
                        val (hitCount, snippet) = hitOf(session)
                        SessionItem(
                            session = session,
                            isSelected = session.id == state.currentId,
                            renaming = state.renamingId == session.id,
                            showCount = state.config.showMessageCount,
                            query = key,
                            hitCount = hitCount,
                            snippet = snippet,
                            onClick = { state.selectConversation(session.id) },
                            onDelete = { state.deleteConversation(session.id) },
                            onRenameCommit = { state.renameConversation(session.id, it) },
                            onRenameCancel = { state.cancelRename() },
                            onContextMenu = { position ->
                                menu.open(
                                    position,
                                    sessionMenuEntries(
                                        session = session,
                                        onRename = { state.beginRename(session.id) },
                                        onTogglePin = { state.togglePinned(session.id) },
                                        onCopy = { state.copySession(session) },
                                        onExport = { state.exportSession(session) },
                                        onDelete = {
                                            if (state.config.confirmDelete) {
                                                menu.replace(
                                                    sessionDeleteConfirm(
                                                        session = session,
                                                        onConfirm = { state.deleteConversation(session.id) },
                                                        onCancel = { menu.dismiss() },
                                                    ),
                                                )
                                            } else {
                                                state.deleteConversation(session.id)
                                            }
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.primary.copy(alpha = 0.10f),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { state.newConversation() },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("＋", fontSize = 13.sp, color = colors.primary)
                    Spacer(Modifier.width(5.dp))
                    Text("新对话", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { state.showPlugins = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("插件", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    if (state.activePlugins.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text("${state.activePlugins.size}", fontSize = 9.sp, color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { state.showSettings = true }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = colors.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", fontSize = 13.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索会话", fontSize = 11.sp, color = colors.onSurfaceVariant.copy(alpha = 0.8f))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                onChange("")
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "✕",
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { onChange("") }.padding(2.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: Session,
    isSelected: Boolean,
    renaming: Boolean,
    showCount: Boolean,
    query: String,
    hitCount: Int,
    snippet: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRenameCommit: (String) -> Unit,
    onRenameCancel: () -> Unit,
    onContextMenu: (Offset) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val starred = session.messages.any { it.starred }
    val count = session.messages.count { it.role != MsgRole.TOOL }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colors.surfaceVariant else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .contextMenuTarget(onClick = onClick, onContextMenu = onContextMenu),
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (renaming) {
                    RenameField(
                        initial = session.title,
                        onCommit = onRenameCommit,
                        onCancel = onRenameCancel,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    if (session.pinned) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    if (starred) {
                        Text("★", fontSize = 9.sp, color = colors.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        session.title,
                        color = colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(shortTime(session.updatedAt), color = colors.onSurfaceVariant, fontSize = 10.sp)
                    if (isSelected) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "删除会话",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (query.isNotEmpty() && snippet != null) snippet else session.preview,
                    color = if (query.isNotEmpty() && snippet != null) colors.primary else colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty() && hitCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("命中 $hitCount", color = colors.primary, fontSize = 9.sp)
                } else if (showCount && count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("$count 条", color = colors.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 9.sp)
                }
            }
        }
    }
}

/** 就地重命名：Enter 提交、Esc 取消、失焦按提交处理 */
@Composable
private fun RenameField(
    initial: String,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var value by remember(initial) { mutableStateOf(initial) }
    var touched by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BasicTextField(
        value = value,
        onValueChange = { value = it.replace('\n', ' ').take(60) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier
            .background(colors.background, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    touched = true
                } else if (touched && !cancelled) {
                    onCommit(value)
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter -> {
                        onCommit(value)
                        true
                    }

                    Key.Escape -> {
                        cancelled = true
                        onCancel()
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(focus),
    )
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("MM-dd")

private fun shortTime(epochMillis: Long): String = runCatching {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    if (time.toLocalDate().toEpochDay() == LocalDate.now().toEpochDay()) time.format(timeFormat) else time.format(dateFormat)
}.getOrDefault("")
