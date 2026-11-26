/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:38:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 工具确认卡片：模型要动磁盘 / 跑命令时挡在前面。
 * 布局对齐手机端的权限弹窗——标题说「要做什么」，正文给原文，按钮给四个选择。
 */

package b.ai.desktop.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.approval.ApprovalDecision
import b.ai.desktop.core.approval.ApprovalRequest
import b.ai.desktop.core.sandbox.Risk

/**
 * 全屏遮罩 + 居中的确认卡。
 * 点遮罩等于「拒绝」，避免误触；Esc 也走拒绝。
 */
@Composable
fun ToolApprovalOverlay(
    request: ApprovalRequest,
    onDecide: (ApprovalDecision) -> Unit,
    onStopRound: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(indication = null, interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) {
                onDecide(ApprovalDecision.Deny("用户关掉了确认卡片"))
            },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Surface(
            shape = shape,
            color = colors.surface,
            modifier = Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .shadow(20.dp, shape)
                .border(1.dp, colors.outlineVariant, shape)
                // 卡片自己吃掉点击，别让点按钮变成点遮罩
                .clickable(
                    indication = null,
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                ) {},
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RiskBadge(request.risk)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        request.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(request.toolName, fontSize = 10.sp, color = colors.onSurfaceVariant)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    request.summary,
                    fontSize = 12.sp,
                    color = colors.onSurface,
                    fontFamily = if (request.toolName == "run_command") FontFamily.Monospace else null,
                )

                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.7f))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            request.detail.ifBlank { "（无参数）" },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = colors.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "为什么问你：" + request.reason,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (request.risk == Risk.HIGH) colors.error else colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApprovalButton("拒绝", colors.surfaceVariant, colors.onSurface) {
                        onDecide(ApprovalDecision.Deny("用户点了拒绝"))
                    }
                    ApprovalButton("停止本轮", colors.surfaceVariant, colors.onSurface) { onStopRound() }
                    Spacer(Modifier.weight(1f))
                    if (!request.permanent) {
                        ApprovalButton("始终允许", colors.primary.copy(alpha = 0.12f), colors.primary) {
                            onDecide(ApprovalDecision.AllowAlways)
                        }
                    }
                    ApprovalButton("仅本次允许", colors.primary, Color.White) {
                        onDecide(ApprovalDecision.AllowOnce)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskBadge(risk: Risk) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (risk) {
        Risk.LOW -> colors.surfaceVariant to colors.onSurfaceVariant
        Risk.MEDIUM -> colors.primary.copy(alpha = 0.14f) to colors.primary
        Risk.HIGH -> colors.error.copy(alpha = 0.14f) to colors.error
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(risk.label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ApprovalButton(
    text: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.sp, color = foreground, fontWeight = FontWeight.Medium)
    }
}
