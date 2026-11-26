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
 * Baicodex Windows - 附件面板（对齐手机端 AttachPanel）
 * 一级：图片 / 文件 / 音频 / 视频 / 音乐 / 提示词标签 / 技能包
 * 二级：提示词标签预设列表
 */

package b.ai.desktop.ui.panel

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.data.AttachmentKind
import b.ai.desktop.data.presetPromptTags
import b.ai.desktop.ui.component.SheetActionRow
import b.ai.desktop.ui.component.SheetCard
import b.ai.desktop.ui.component.SheetHighlightRow

@Composable
fun AttachSheet(
    onPickKind: (AttachmentKind) -> Unit,
    onPromptTag: (String) -> Unit,
    onClose: () -> Unit,
) {
    var showTags by remember { mutableStateOf(false) }
    var skillsHint by remember { mutableStateOf(false) }

    if (showTags) {
        SheetCard(title = "提示词标签", onClose = { showTags = false }) {
            presetPromptTags.forEach { (label, body) ->
                SheetActionRow(
                    title = label,
                    desc = body.trim().removeSuffix("："),
                    onClick = { onPromptTag(body) },
                )
            }
        }
        return
    }

    SheetCard(title = "添加附件", onClose = onClose) {
        AttachmentKind.entries.forEach { kind ->
            SheetActionRow(title = kind.label, desc = kind.desc, onClick = { onPickKind(kind) })
        }
        SheetActionRow(
            title = "提示词标签",
            desc = "把常用片段插进输入框",
            onClick = { showTags = true },
        )
        Spacer(Modifier.height(6.dp))
        SheetHighlightRow(
            title = "技能包",
            desc = "管理已安装的技能",
            onClick = { skillsHint = true },
        )
        if (skillsHint) {
            Text(
                "技能管理还没接入，后续放到设置里统一管理。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "图片 / 音频 / 视频会打开系统文件选择器；提示词标签直接插入输入框。",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
