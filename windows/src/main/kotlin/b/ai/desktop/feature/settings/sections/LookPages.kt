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
 * Baicodex Windows - 设置 · 外观组
 * 主题与布局 / 字体与输入 / Markdown 排版与预览。
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.i18n.I18n
import b.ai.desktop.feature.settings.ChoiceSetting
import b.ai.desktop.feature.settings.DdOption
import b.ai.desktop.feature.settings.DropdownSetting
import b.ai.desktop.feature.settings.ddRange
import b.ai.desktop.feature.settings.SettingRow
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.StepperSetting
import b.ai.desktop.feature.settings.TextSetting
import b.ai.desktop.feature.settings.ToggleSetting
import b.ai.desktop.theme.Accent
import b.ai.desktop.theme.UiFonts
import b.ai.desktop.theme.ChatType
import b.ai.desktop.theme.ThemeMode
import b.ai.desktop.ui.widget.MarkdownText

/** 主题与布局：深浅色、气泡与侧边栏宽度、紧凑模式 */
@Composable
fun LookThemePage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("主题", "跟随系统时会实时跟着 Windows 的深浅色切换")
    ChoiceSetting(
        title = "外观模式",
        options = ThemeMode.entries.map { it.label },
        selectedIndex = ThemeMode.entries.indexOf(state.themeMode).coerceAtLeast(0),
        onSelect = { state.themeMode = ThemeMode.entries[it] },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("布局")
    DropdownSetting(
        title = "气泡最大宽度",
        desc = "长回复的换行位置由它决定",
        options = ddRange(420, 900, 20) { "${it}dp" },
        current = state.config.bubbleMaxWidth,
        onChange = { value -> state.editConfig { it.copy(bubbleMaxWidth = value) } },
    )
    DropdownSetting(
        title = "侧边栏宽度",
        desc = "会话列表那一栏；改完立刻生效",
        options = ddRange(220, 360, 8) { "${it}dp" },
        current = state.config.sidebarWidth,
        onChange = { value -> state.editConfig { it.copy(sidebarWidth = value) } },
    )
    ToggleSetting(
        title = "紧凑模式",
        desc = "消息间距与气泡内边距收窄，一屏能多看几条",
        checked = state.config.compact,
        onChange = { value -> state.editConfig { it.copy(compact = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("输入框")
    var placeholder by remember(state.config.inputPlaceholder) {
        mutableStateOf(state.config.inputPlaceholder)
    }
    TextSetting(
        label = "占位提示",
        value = placeholder,
        onChange = { placeholder = it },
        placeholder = "留空则用默认文案",
    )
    PillButton("应用", primary = true) {
        state.editConfig { it.copy(inputPlaceholder = placeholder.trim()) }
        state.flash("输入框提示已更新")
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("外观进阶", "字体、缩放、圆角、背景——全都是改了立刻生效")
    DropdownSetting(
        title = "界面字体",
        desc = "从 Windows 字体里加载；个别字体缺文件会回退默认",
        options = UiFonts.options.mapIndexed { index, f -> DdOption(index, f.label) },
        current = UiFonts.options.indexOfFirst { it.id == state.config.fontFamily }.coerceAtLeast(0),
        onChange = { index ->
            val id = UiFonts.options[index].id
            state.editConfig { it.copy(fontFamily = id) }
            state.applyUiMetrics()
        },
    )
    DropdownSetting(
        title = "全局缩放",
        desc = "整个界面的 dp/sp 一起放大缩小",
        options = ddRange(80, 150, 5) { "$it%" },
        current = state.config.uiScale,
        onChange = { value ->
            state.editConfig { it.copy(uiScale = value) }
            state.applyUiMetrics()
        },
    )
    DropdownSetting(
        title = "气泡圆角",
        options = ddRange(4, 24, 2) { "${it}dp" },
        current = state.config.bubbleCorner,
        onChange = { value ->
            state.editConfig { it.copy(bubbleCorner = value) }
            state.applyUiMetrics()
        },
    )
    DropdownSetting(
        title = "卡片圆角",
        options = ddRange(6, 24, 2) { "${it}dp" },
        current = state.config.cardCorner,
        onChange = { value ->
            state.editConfig { it.copy(cardCorner = value) }
            state.applyUiMetrics()
        },
    )
    DropdownSetting(
        title = "弹窗圆角",
        options = ddRange(6, 28, 2) { "${it}dp" },
        current = state.config.dialogCorner,
        onChange = { value ->
            state.editConfig { it.copy(dialogCorner = value) }
            state.applyUiMetrics()
        },
    )
    DropdownSetting(
        title = "背景",
        desc = null,
        options = listOf(DdOption(0, "纯色"), DdOption(1, "微渐变")),
        current = if (state.config.backdrop == "gradient") 1 else 0,
        onChange = { index ->
            state.editConfig { it.copy(backdrop = if (index == 1) "gradient" else "plain") }
        },
    )
}

/** 字体与输入：正文字号 / 行距 / 输入提示，带实时预览 */
@Composable
fun LookTypePage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("字号", "气泡、Markdown 正文一起变；代码块的字号在「Markdown」页")
    DropdownSetting(
        title = "正文字号",
        options = ddRange(11, 20, 1) { "${it}sp" },
        current = state.config.fontSize,
        onChange = { value -> state.editConfig { it.copy(fontSize = value) } },
    )
    DropdownSetting(
        title = "行距",
        options = ddRange(120, 200, 5) { String.format("%.2f 倍", it / 100.0) },
        current = state.config.lineHeight,
        onChange = { value -> state.editConfig { it.copy(lineHeight = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("预览")
    PreviewCard(state)
}

/** Markdown：代码块字号 + 整段排版预览 */
@Composable
fun LookMarkdownPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("代码块", "长行不折行、横向滚动；这里只改字号")
    DropdownSetting(
        title = "代码字号",
        options = ddRange(10, 18, 1) { "${it}sp" },
        current = state.config.codeFontSize,
        onChange = { value -> state.editConfig { it.copy(codeFontSize = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("预览", "标题、列表、表格、代码块、行内样式都会按当前设置渲染")
    PreviewCard(state)
    Text(
        "支持：标题、有序/无序列表、引用、分割线、表格（按列宽自适应并横向滚动）、" +
            "行内代码、粗体、斜体、链接、代码块（带语言标签与复制按钮）。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun PreviewCard(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val type = ChatType(
        fontSize = state.config.fontSize,
        codeFontSize = state.config.codeFontSize,
        lineHeightPercent = state.config.lineHeight,
    )
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        MarkdownText(
            src = PREVIEW,
            color = colors.onSurface,
            fontSize = type.bodySize,
            lineHeight = type.bodyLine,
            codeFontSize = type.codeSize,
        )
    }
}

private val PREVIEW = """
## 排版预览

正文的**粗体**、*斜体*、`行内代码` 和 [链接](https://agent.ai.baiccb.cn/) 都在这里。

1. 有序列表第一项
2. 第二项，用来看看行距

- 无序列表
- 第二行

> 引用块：说明这段是外部观点。

| 能力 | 状态 |
| --- | --- |
| Markdown | 支持 |
| 表格 | 支持 |

```kotlin
fun main() {
    println("代码块字号跟着设置走")
}
```
"""

/** 个性化：主题色色板，点一下整窗换色（亮暗两套都会跟着换） */
@Composable
fun LookPersonPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val current = state.accent

    SectionTitle("主题色", "主按钮、选中态、链接与高亮都跟着换；中性色保持不变")
    Row(
        Modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Accent.entries.forEach { accent ->
            val active = accent == current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.lightPrimary)
                        .then(
                            if (active) Modifier.border(
                                width = 2.dp,
                                color = colors.onSurface,
                                shape = CircleShape,
                            ) else Modifier
                        )
                        .clickable {
                            state.editConfig { it.copy(accent = accent.id) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    accent.label,
                    fontSize = 9.sp,
                    color = if (active) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("头像", "对齐手机端：气泡旁的 28dp 小头像")
    ToggleSetting(
        title = "显示消息头像",
        desc = if (state.config.avatarOn) "AI 用自定义头像或应用图标；用户用自定义 > GitHub > 首字母" else "气泡独占整行，不留头像位",
        checked = state.config.avatarOn,
        onChange = { value -> state.editConfig { it.copy(avatarOn = value) } },
    )
    AvatarPickRow(state, "自定义 AI 头像", state.config.avatarAiFile) { name ->
        state.editConfig { it.copy(avatarAiFile = name) }
    }
    AvatarPickRow(state, "自定义我的头像", state.config.avatarUserFile) { name ->
        state.editConfig { it.copy(avatarUserFile = name) }
    }
    ToggleSetting(
        title = "胶囊气泡",
        desc = if (state.config.bubbleCapsule) "气泡两端完全圆润（手机端 bubbleCapsule 同款）" else "按上面设置的圆角",
        checked = state.config.bubbleCapsule,
        onChange = { value -> state.editConfig { it.copy(bubbleCapsule = value) } },
    )
    ToggleSetting(
        title = "回车键发送",
        desc = if (state.config.sendOnEnter) "回车发送，Shift+回车换行" else "回车换行，Ctrl+回车发送",
        checked = state.config.sendOnEnter,
        onChange = { value -> state.editConfig { it.copy(sendOnEnter = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("桌宠", "桌面伙伴：透明置顶小窗，可拖动，双击收起")
    ToggleSetting(
        title = "显示桌宠",
        desc = if (state.config.petOn) "悬浮窗已经在桌面上" else "关着；打开立即出现在屏幕上",
        checked = state.config.petOn,
        onChange = { value -> state.editConfig { it.copy(petOn = value) } },
    )
    DropdownSetting(
        title = "桌宠大小",
        options = ddRange(72, 200, 16) { "${it}dp" },
        current = state.config.petSize,
        onChange = { value -> state.editConfig { it.copy(petSize = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("基调")
    ChoiceSetting(
        title = "外观模式",
        options = ThemeMode.entries.map { it.label },
        selectedIndex = ThemeMode.entries.indexOf(state.themeMode).coerceAtLeast(0),
        onSelect = { state.themeMode = ThemeMode.entries[it] },
    )
    Text(
        "主题色保存在配置里，重启也记得住；「外观模式」之前在主题页里也有一份，两处改的是同一个值。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 语言：选项与手机端同一套；t() 是状态驱动，切换后所有接了 i18n 的文案随重组刷新 */
@Composable
fun LookLangPage(state: AppState) {
    DropdownSetting(
        title = I18n.t("界面语言"),
        desc = I18n.t("切完立即生效，不用重启"),
        options = I18n.options.mapIndexed { index, (tag, _) -> DdOption(index, I18n.selfName(tag)) },
        current = I18n.options.indexOfFirst { it.first == state.config.lang }.coerceAtLeast(0),
        onChange = { index ->
            val tag = I18n.options[index].first
            state.editConfig { it.copy(lang = tag) }
            I18n.switch(tag)
        },
    )
    Text(
        I18n.t("翻译目前覆盖设置导航与新增页面，深层内容会随版本逐步补齐。"),
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 自定义头像选择行：选图片复制到 avatars/ 目录；可一键还原默认 */
@Composable
private fun AvatarPickRow(state: AppState, label: String, current: String, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SettingRow(label, if (current.isBlank()) "未自定义（用默认）" else "已设置：$current") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("选图片") {
                val dialog = java.awt.FileDialog(null as? java.awt.Frame ?: java.awt.Frame(), "选择头像图片", java.awt.FileDialog.LOAD)
                dialog.setFile("*.png;*.jpg;*.jpeg;*.webp")
                dialog.isVisible = true
                val picked = dialog.directory?.let { java.io.File(it, dialog.file ?: "") }
                if (picked != null && picked.exists()) {
                    runCatching {
                        val dir = java.io.File(b.ai.desktop.core.config.ConfigStore.appDir(), "avatars").apply { mkdirs() }
                        val target = java.io.File(dir, "${label.hashCode().toUInt()}_${picked.name}")
                        picked.copyTo(target, overwrite = true)
                        onPick(target.name)
                        state.flash("头像已更新")
                    }.onFailure { state.flash("复制失败：${it.message}") }
                }
            }
            if (current.isNotBlank()) {
                PillButton("还原默认") {
                    onPick("")
                    state.flash("已还原默认头像")
                }
            }
        }
    }
}
