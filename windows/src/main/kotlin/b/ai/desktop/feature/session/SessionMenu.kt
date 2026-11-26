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
 * Baicodex Windows - 会话右键菜单项
 * 左侧列表与会话标题栏共用同一份，避免两处操作不一致。
 */

package b.ai.desktop.feature.session

import b.ai.desktop.core.model.Session
import b.ai.desktop.ui.menu.MenuEntry

fun sessionMenuEntries(
    session: Session,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
): List<MenuEntry> {
    val hasMessages = session.messages.isNotEmpty()
    return listOf(
        MenuEntry.Action(label = "重命名", onClick = onRename),
        MenuEntry.Action(
            label = if (session.pinned) "取消置顶" else "置顶会话",
            onClick = onTogglePin,
        ),
        MenuEntry.Divider,
        MenuEntry.Action(label = "复制会话为 Markdown", enabled = hasMessages, onClick = onCopy),
        MenuEntry.Action(label = "导出为 Markdown…", enabled = hasMessages, onClick = onExport),
        MenuEntry.Divider,
        MenuEntry.Action(label = "删除会话", danger = true, keepOpen = true, onClick = onDelete),
    )
}

/** 删除会话的二次确认（对齐手机端：第一下只把按钮换成确认） */
fun sessionDeleteConfirm(
    session: Session,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
): List<MenuEntry> = listOf(
    MenuEntry.Action(
        label = "确认删除「${session.title.take(12)}」",
        hint = "不可恢复",
        danger = true,
        onClick = onConfirm,
    ),
    MenuEntry.Action(label = "取消", onClick = onCancel),
)
