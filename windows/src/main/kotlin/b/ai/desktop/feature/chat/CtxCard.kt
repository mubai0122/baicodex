/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 顶栏预算环 + 点击展开的预算卡（对齐手机端的上下文占用环 / 小卡片）。
 * 环按真实百分比画弧，70% 起由主色转红；卡片报四个数：已用/预算、消息条数、
 * 本会话服务端回报的累计 token、以及偏满时的处理建议。
 */

package b.ai.desktop.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.balance.BalanceWatch
import b.ai.desktop.core.util.CtxStat
import b.ai.desktop.core.util.fmtTokens
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * 上下文占用环。底色跟输入区胶囊一致，避免在顶栏里显得突兀。
 * @param active 预算卡是否展开（展开时描一圈主色边，表示当前选中）
 */
@Composable
fun CtxRing(stat: CtxStat, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tone = if (stat.nearLimit) colors.error else colors.primary
    val pct = stat.pct

    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (active) colors.primary.copy(alpha = 0.10f) else colors.surfaceVariant.copy(alpha = 0.6f))
            .clickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.outlineVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (pct > 0f) {
                drawArc(
                    color = tone,
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            if (stat.budget <= 0) "—" else "${(pct * 100).toInt()}%",
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (stat.budget <= 0) colors.onSurfaceVariant else tone,
        )
    }
}

/** 点击预算环后展开的卡片：四个数字 + 一句建议 */
@Composable
fun CtxCard(
    stat: CtxStat,
    state: AppState,
    onClose: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val balance = BalanceWatch.last

    // 打开卡片时顺手查一次（60 秒内直接用缓存，不会每次打接口）
    LaunchedEffect(Unit) { state.refreshBalance(force = false) }

    // 点卡片外面关掉；卡片自己吞掉点击，否则选字/点按钮会连带关闭
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 20.dp)
                .width(248.dp)
                .clickable(
                    interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("上下文用量", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onClose).padding(2.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                // 进度条：和环同色，70% 起转红
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.outlineVariant),
                ) {
                    val tone = if (stat.nearLimit) colors.error else colors.primary
                    Box(
                        Modifier
                            .fillMaxWidth(stat.pct.coerceAtLeast(0.01f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tone),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmtTokens(stat.used),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (stat.nearLimit) colors.error else colors.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (stat.budget > 0) "/ ${fmtTokens(stat.budget)} tokens" else "tokens（未设预算）",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(6.dp))
                InfoRow("消息", "${stat.count} 条")
                InfoRow(
                    "本会话消耗",
                    if (stat.spent > 0) "${fmtTokens(stat.spent)} tokens" else "服务端未回 usage",
                )
                InfoRow(
                    "剩余空间",
                    if (stat.budget > 0) "${fmtTokens((stat.budget - stat.used).coerceAtLeast(0))} tokens" else "—",
                )
                // 余额：和设置里的「模型 → 余额」同一个数据源，60 秒冷却
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("余额", fontSize = 10.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            BalanceWatch.busy -> "查询中…"
                            balance == null -> "未查询"
                            balance.ok -> balance.text
                            else -> "查不到"
                        },
                        fontSize = 10.sp,
                        color = when {
                            balance?.ok != true -> colors.onSurfaceVariant
                            balance.below(state.config.balanceThreshold) -> colors.error
                            else -> colors.onSurface
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    val cooling = BalanceWatch.cooldownLeft()
                    Text(
                        if (BalanceWatch.busy) "" else if (cooling > 0) "${cooling}s" else "刷新",
                        fontSize = 9.sp,
                        color = if (cooling > 0) colors.onSurfaceVariant else colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (cooling > 0) Color.Transparent else colors.primary.copy(alpha = 0.10f))
                            .clickable(enabled = cooling == 0 && !BalanceWatch.busy) {
                                scope.launch { state.refreshBalance(force = true) }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        stat.budget <= 0 -> "设置里把「上下文预算」调大，这里才会显示百分比。"
                        stat.nearLimit -> "已经超过 70%：继续聊容易挤掉早期内容，建议开新会话，或在设置里调低「上下文消息上限」。"
                        else -> "还有余量。工具输出越长占得越多，长任务可以把「工具结果预算」调小。"
                    },
                    fontSize = 9.sp,
                    lineHeight = 15.sp,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton("新建会话", primary = true, onClick = onNewSession)
                    MiniButton("上下文设置", onClick = onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 10.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 10.sp, color = colors.onSurface)
    }
}

@Composable
private fun MiniButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (primary) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = if (primary) Color.White else colors.onSurface,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
