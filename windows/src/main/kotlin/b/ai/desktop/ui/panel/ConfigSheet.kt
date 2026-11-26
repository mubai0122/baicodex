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
 * 本轮配置面板（对齐手机端 CfgPanel）
 * 深度思考 + 强度充能条 / 原生联网搜索 / 模型工具调用 / 工具结果预算 / 最大模式
 */

package b.ai.desktop.ui.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.ui.component.ChargeSlider
import b.ai.desktop.ui.component.SheetCard

@Composable
fun ConfigSheet(
    thinkingEnabled: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    effortLevels: List<String>,
    effortIndex: Int,
    onEffortIndexChange: (Int) -> Unit,
    nativeSearch: Boolean,
    onNativeSearchChange: (Boolean) -> Unit,
    toolCalls: Boolean,
    onToolCallsChange: (Boolean) -> Unit,
    budgetText: String,
    onBudgetCycle: () -> Unit,
    maxMode: Boolean,
    onMaxModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    SheetCard(title = "本轮配置", onClose = onClose, maxHeight = 360.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 深度思考：开关放最上面，紧跟着强度充能条
            ConfigRow(
                title = "深度思考",
                desc = if (thinkingEnabled) {
                    "已开启 · 强度 ${effortLevels.getOrElse(effortIndex) { "medium" }.uppercase()}"
                } else {
                    "关闭（下一轮请求不思考）"
                },
                checked = thinkingEnabled,
                onCheckedChange = onThinkingChange,
            )
            ChargeSlider(
                levels = effortLevels,
                index = effortIndex,
                enabled = thinkingEnabled,
                onIndex = onEffortIndexChange,
            )
            ConfigRow(
                title = "原生联网搜索",
                desc = if (nativeSearch) "本次注入服务端搜索工具" else "本次不注入",
                checked = nativeSearch,
                onCheckedChange = onNativeSearchChange,
            )
            ConfigRow(
                title = "模型工具调用",
                desc = if (toolCalls) "允许模型调用本地工具" else "禁用工具调用",
                checked = toolCalls,
                onCheckedChange = onToolCallsChange,
            )
            BudgetRow(budgetText = budgetText, onBudgetCycle = onBudgetCycle)
            ConfigRow(
                title = "最大模式",
                desc = if (maxMode) "单轮上限提升到 8192" else "默认上限",
                checked = maxMode,
                onCheckedChange = onMaxModeChange,
            )
        }
    }
}

@Composable
private fun ConfigRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = colors.onSurface)
            Text(desc, fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
        )
    }
}

@Composable
private fun BudgetRow(budgetText: String, onBudgetCycle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("工具结果预算", fontSize = 12.sp, color = colors.onSurface)
            Text(
                if (budgetText == "max") "不限" else "$budgetText tok",
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.primary.copy(alpha = 0.10f),
            modifier = Modifier.clickable(onClick = onBudgetCycle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(budgetText, fontSize = 11.sp, color = colors.primary)
                Spacer(Modifier.width(4.dp))
                Text("›", fontSize = 12.sp, color = colors.primary)
            }
        }
    }
}
