/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:15:17
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 设置页通用控件
 */

package b.ai.desktop.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTitle(text: String, desc: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
        if (desc != null) {
            Text(desc, fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    desc: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = colors.onSurface)
            if (desc != null) {
                Text(desc, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun ToggleSetting(
    title: String,
    desc: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingRow(title, desc) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
        )
    }
}

@Composable
fun TextSetting(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, fontSize = 11.sp) },
        singleLine = singleLine,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = if (singleLine) 54.dp else 104.dp),
        textStyle = MaterialTheme.typography.bodySmall,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

/** 下拉选项：value 是存进配置的值，label 是展示文案 */
data class DdOption(val value: Int, val label: String)

fun dd(vararg pairs: Pair<Int, String>): List<DdOption> = pairs.map { DdOption(it.first, it.second) }

/** 等差数值选项：字号、行距、宽度这类连续范围 */
fun ddRange(first: Int, last: Int, step: Int, format: (Int) -> String = { it.toString() }): List<DdOption> =
    (first..last step step).map { DdOption(it, format(it)) }

/** 在选项前面加一个「默认」档（value 用 -1 这类哨兵值） */
fun ddDefault(value: Int, label: String, options: List<DdOption>): List<DdOption> =
    listOf(DdOption(value, label)) + options

/** 当前配置值不在预设里时补一条，保证下拉里能显示现状 */
fun List<DdOption>.plusCurrent(current: Int, format: (Int) -> String = { it.toString() }): List<DdOption> =
    if (any { it.value == current }) this else this + DdOption(current, format(current))

/**
 * 下拉框设置行：所有「从一个确定集合里选一个」的地方都用它，
 * 取代原来的一减一加步进器——离散选项就该一眼看全、一次选中。
 */
@Composable
fun DropdownSetting(
    title: String,
    desc: String? = null,
    options: List<DdOption>,
    current: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val expanded = remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.value == current }?.label ?: current.toString()

    SettingRow(title, desc, modifier = modifier) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant)
                    .clickable { expanded.value = true }
                    .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 11.sp, color = colors.onSurface)
                Spacer(Modifier.width(4.dp))
                Text("▾", fontSize = 9.sp, color = colors.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
                containerColor = colors.surface,
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontSize = 12.sp,
                                color = if (option.value == current) colors.primary else colors.onSurface,
                                fontWeight = if (option.value == current) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            expanded.value = false
                            onChange(option.value)
                        },
                    )
                }
            }
        }
    }
}

/** 数值步进：设置页里所有数字都用它，省掉输入校验 */
@Composable
fun StepperSetting(
    title: String,
    desc: String? = null,
    value: Int,
    range: IntRange,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    SettingRow(title, desc) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = value > range.first) {
                onChange((value - step).coerceIn(range.first, range.last))
            }
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                Text(format(value), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            StepButton("+", enabled = value < range.last) {
                onChange((value + step).coerceIn(range.first, range.last))
            }
        }
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontSize = 12.sp,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
fun ChoiceSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingRow(title) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, label ->
                val active = index == selectedIndex
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) colors.primary.copy(alpha = 0.12f) else colors.surfaceVariant)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (active) colors.primary else colors.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
fun PillButton(
    text: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val bg = when {
        !enabled -> colors.surfaceVariant.copy(alpha = 0.5f)
        primary -> colors.primary
        else -> colors.surfaceVariant
    }
    val fg = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        primary -> Color.White
        else -> colors.onSurface
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 11.sp, color = fg, fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal)
    }
}
