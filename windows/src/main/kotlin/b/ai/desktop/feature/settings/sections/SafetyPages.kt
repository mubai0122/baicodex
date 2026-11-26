/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:42:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 设置 · 扩展 · 沙箱与确认：文件沙箱档位、工具确认策略、危险命令拦截、永久放行名单
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.approval.ApprovalMode
import b.ai.desktop.core.approval.ToolLabels
import b.ai.desktop.core.sandbox.SandboxMode
import b.ai.desktop.core.util.openPath
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.SettingRow
import b.ai.desktop.feature.settings.ToggleSetting

@Composable
fun ExtSafetyPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val workspace = state.config.workspaceDir()

    SectionTitle("文件沙箱", "文件工具只能碰工作区里的东西；越界路径、系统目录、保留设备名一律拒绝")
    SettingRow("工作区", workspace.absolutePath) {
        PillButton("打开目录", onClick = { openPath(workspace.absolutePath) })
    }

    Spacer(Modifier.height(6.dp))
    SandboxMode.entries.forEach { mode ->
        OptionRow(
            title = mode.label,
            desc = mode.hint,
            selected = state.sandboxMode == mode,
            onClick = { state.setSandboxMode(mode) },
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "沙箱是硬边界：写文件、删目录、跑命令都会先过这道检查，配置里的「宽松」也解不开越界限制。",
        fontSize = 10.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("工具确认", "模型每次动磁盘之前问不问、问多少")
    ApprovalMode.entries.forEach { mode ->
        OptionRow(
            title = mode.label,
            desc = mode.hint,
            selected = state.approvalMode == mode,
            onClick = { state.setApprovalMode(mode) },
        )
    }

    Spacer(Modifier.height(6.dp))
    ToggleSetting(
        title = "拦截危险命令",
        desc = "格式化磁盘、改引导、关机、删注册表这类命令直接拒绝，不给确认机会",
        checked = state.config.commandGuardEnabled,
        onChange = { state.setCommandGuard(it) },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("永久放行", "点过「始终允许」的工具会列在这里，随时可以收回")
    val allowList = state.config.alwaysAllowTools
    if (allowList.isEmpty()) {
        Text("还没有放行任何工具。", fontSize = 11.sp, color = colors.onSurfaceVariant)
    } else {
        allowList.forEach { name ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ToolLabels.titleOf(name), fontSize = 12.sp, color = colors.onSurface)
                    Text(name, fontSize = 9.sp, color = colors.onSurfaceVariant)
                }
                PillButton("收回", onClick = { state.revokeAlwaysAllow(name) })
            }
        }
        Spacer(Modifier.height(6.dp))
        PillButton("全部收回", onClick = { state.clearAlwaysAllow() })
    }
}

/** 单选行：标题 + 说明 + 右侧圆点 */
@Composable
private fun OptionRow(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.07f) else colors.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, if (selected) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) colors.primary else colors.onSurface,
            )
            Text(desc, fontSize = 10.sp, lineHeight = 14.sp, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(colors.primary))
            }
        }
    }
}
