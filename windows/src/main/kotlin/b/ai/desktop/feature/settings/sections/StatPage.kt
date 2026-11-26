/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:42:59
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 设置 · 统计
 * 全部数字来自本机会话文件：条数、真实 token（服务端回报）、估算 token、按模型、按天。
 * 没有服务端回报时明确标「估算」，不把估算当成真实账。
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.util.CtxStat
import b.ai.desktop.core.util.fmtTokens
import b.ai.desktop.feature.settings.SectionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun StatPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val sessions = state.sessions

    val allMessages = sessions.flatMap { it.messages }
    val realTokens = allMessages.sumOf { it.tokens }
    val estTokens = CtxStat.of(allMessages, 0).used
    val userCount = allMessages.count { it.role == MsgRole.USER }
    val replyCount = allMessages.count { it.role == MsgRole.ASSISTANT }
    val toolCount = allMessages.count { it.role == MsgRole.TOOL }

    SectionTitle("总览", "全部会话累计；真实 token 只统计服务端回了 usage 的回复")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("会话", sessions.size.toString())
        Metric("提问", userCount.toString())
        Metric("回复", replyCount.toString())
        Metric("工具调用", toolCount.toString())
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("真实 tokens", if (realTokens > 0) fmtTokens(realTokens) else "—")
        Metric("估算 tokens", fmtTokens(estTokens))
        Metric("字数", fmtTokens(allMessages.sumOf { it.text.length }))
    }
    if (realTokens == 0 && allMessages.isNotEmpty()) {
        Text(
            "服务端没回 usage，这里只有估算值；换支持返回用量的端点后会出现真实数字。",
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    // 按模型：只统计有模型名的回复（旧消息可能没有）
    val byModel = allMessages
        .filter { it.role == MsgRole.ASSISTANT && it.model.isNotBlank() }
        .groupBy { it.model }
        .mapValues { (_, list) -> list.sumOf { if (it.tokens > 0) it.tokens else CtxStat.of(listOf(it), 0).used } }
        .toList()
        .sortedByDescending { it.second }

    SectionTitle("按模型", "同一条回复只算一次，单位 token")
    if (byModel.isEmpty()) {
        Text("还没有带模型记录的回复", fontSize = 10.sp, color = colors.onSurfaceVariant)
    } else {
        val peak = byModel.maxOf { it.second }.coerceAtLeast(1)
        byModel.forEach { (model, tokens) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    model,
                    fontSize = 10.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(150.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((tokens.toFloat() / peak).coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.primary.copy(alpha = 0.75f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(fmtTokens(tokens), fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    // 最近 14 天：按消息创建日聚合
    val days = (13 downTo 0).map { back ->
        val date = LocalDate.now().minusDays(back.toLong())
        val count = allMessages.count { msg ->
            runCatching {
                LocalDate.ofInstant(Instant.ofEpochMilli(msg.createdAt), ZoneId.systemDefault())
            }.getOrNull() == date
        }
        date to count
    }
    val peakDay = days.maxOf { it.second }.coerceAtLeast(1)

    SectionTitle("最近 14 天", "每根柱子是一天的消息条数")
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { (date, count) ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((count.toFloat() / peakDay * 48f).coerceAtLeast(if (count > 0) 3f else 1f).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (count > 0) colors.primary.copy(alpha = 0.65f)
                            else colors.outlineVariant.copy(alpha = 0.5f),
                        ),
                )
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 7.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    // 近 14 天活跃柱状图：会话里最后一条消息的日期归档
    val perDay = LinkedHashMap<LocalDate, Int>()
    val today = LocalDate.now()
    for (i in 13 downTo 0) perDay[today.minusDays(i.toLong())] = 0
    sessions.forEach { session ->
        val msg = session.messages.lastOrNull() ?: return@forEach
        val day = Instant.ofEpochMilli(msg.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        if (perDay.containsKey(day)) perDay[day] = perDay[day]!! + 1
    }
    SectionTitle("近 14 天", "每天最后一条消息落在哪天就记在哪天")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val max = (perDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
        perDay.forEach { (day, count) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    if (count > 0) count.toString() else "",
                    fontSize = 8.sp,
                    color = colors.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(((if (count == 0) 2 else 6 + (count * 46f / max)).toInt()).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (count > 0) colors.primary else colors.surfaceVariant),
                ) {}
                Text(
                    "${day.monthValue}/${day.dayOfMonth}",
                    fontSize = 7.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    // 本月方格图（对齐手机端：GitHub 式按天格子）+ 活跃天数
    val monthStart = today.withDayOfMonth(1)
    val activeDays = perDay.count { it.value > 0 && !it.key.isBefore(monthStart) }
    SectionTitle("本月方格图", "这个月每天的消息条数；颜色越深越活跃 · 本月活跃 $activeDays 天")
    val daysInMonth = today.lengthOfMonth()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (d in 1..daysInMonth) {
            val day = monthStart.withDayOfMonth(d)
            val count = perDay[day] ?: 0
            val max = (perDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
            val alpha = when {
                count == 0 -> 0.15f
                count * 3 <= max -> 0.35f
                count * 2 <= max -> 0.6f
                else -> 0.95f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.primary.copy(alpha = alpha)),
            ) {}
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    Text(
        String.format(
            Locale.US,
            "生成日期：%s · 数据来源：本机 sessions.json",
            LocalDate.now().toString(),
        ),
        fontSize = 9.sp,
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun Metric(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 9.sp, color = colors.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    }
}
