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
 * Baicodex Windows - 设置页外壳（对齐手机端的两级结构）
 * 一级：分类（模型 / 对话 / 外观 / 统计 / 扩展 / 关于）；
 * 二级：该分类下的条目，初始为空，点一级后填充，切一级会清空二级选择；
 * 右侧：条目内容。插件与工作区不在这里——它们是聊天区的一级页面。
 */

package b.ai.desktop.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.i18n.I18n
import b.ai.desktop.feature.settings.sections.AboutKeysPage
import b.ai.desktop.feature.settings.sections.AboutRewardPage
import b.ai.desktop.feature.settings.sections.AboutVersionPage
import b.ai.desktop.feature.settings.sections.AccountSettings
import b.ai.desktop.feature.settings.sections.ChatContextPage
import b.ai.desktop.feature.settings.sections.ChatInOutPage
import b.ai.desktop.feature.settings.sections.ChatPromptPage
import b.ai.desktop.feature.settings.sections.ChatSessionPage
import b.ai.desktop.feature.settings.sections.ChatTagsPage
import b.ai.desktop.feature.settings.sections.ChatStarPage
import b.ai.desktop.feature.settings.sections.ExtAgentPage
import b.ai.desktop.feature.settings.sections.ExtDataPage
import b.ai.desktop.feature.settings.sections.ExtSafetyPage
import b.ai.desktop.feature.settings.sections.ExtUsagePage
import b.ai.desktop.feature.settings.sections.ExtMcpPage
import b.ai.desktop.feature.settings.sections.ExtPrivacyPage
import b.ai.desktop.feature.settings.sections.ExtSchedulePage
import b.ai.desktop.feature.settings.sections.ExtAdbPage
import b.ai.desktop.feature.settings.sections.ExtBackupPage
import b.ai.desktop.feature.settings.sections.ExtSearchPage
import b.ai.desktop.feature.settings.sections.ExtToolPage
import b.ai.desktop.feature.settings.sections.LookLangPage
import b.ai.desktop.feature.settings.sections.LookMarkdownPage
import b.ai.desktop.feature.settings.sections.LookPersonPage
import b.ai.desktop.feature.settings.sections.LookThemePage
import b.ai.desktop.feature.settings.sections.LookTypePage
import b.ai.desktop.feature.settings.sections.ModelBalancePage
import b.ai.desktop.feature.settings.sections.ModelConfigPage
import b.ai.desktop.feature.settings.sections.ModelMemoryPage
import b.ai.desktop.feature.settings.sections.ModelParamsPage
import b.ai.desktop.feature.settings.sections.ModelThinkPage
import b.ai.desktop.feature.settings.sections.ModelVoicePage
import b.ai.desktop.feature.settings.sections.StatPage

/** 一级分类，顺序与手机端一致 */
enum class SettingsGroup(val label: String, val hint: String) {
    MODEL("模型", "连接、参数、思考、语音"),
    CHAT("对话", "上下文、注入、会话、优化、收藏"),
    LOOK("外观", "主题、布局、字体、Markdown"),
    STAT("统计", "用量与分布"),
    EXT("扩展", "工具权限、联网、数据"),
    ABOUT("关于", "版本、快捷键、账号"),
}

/** 二级条目 */
data class SettingsPage(
    val id: String,
    val group: SettingsGroup,
    val label: String,
    val hint: String,
)

val SettingsPages: List<SettingsPage> = listOf(
    SettingsPage("model_cfg", SettingsGroup.MODEL, "模型配置", "提供商 + Key + 模型，可存多套"),
    SettingsPage("model_param", SettingsGroup.MODEL, "生成参数", "温度、top_p、输出上限、超时"),
    SettingsPage("model_think", SettingsGroup.MODEL, "思考默认", "新会话是否先推理、默认档位"),
    SettingsPage("model_voice", SettingsGroup.MODEL, "语音朗读", "自动朗读与系统语速"),
    SettingsPage("model_mem", SettingsGroup.MODEL, "长期记忆", "跨会话保留的偏好与事实"),
    SettingsPage("model_balance", SettingsGroup.MODEL, "余额", "查询来源与低余额提醒"),

    SettingsPage("chat_ctx", SettingsGroup.CHAT, "上下文", "预算环与历史条数"),
    SettingsPage("chat_inout", SettingsGroup.CHAT, "注入与输出", "运行环境、文风、回复信息、草稿"),
    SettingsPage("chat_session", SettingsGroup.CHAT, "会话", "自动命名、删除确认、条数"),
    SettingsPage("chat_prompt", SettingsGroup.CHAT, "提示词优化", "用哪个连接改写提示词"),
    SettingsPage("chat_star", SettingsGroup.CHAT, "收藏夹", "跨会话的收藏消息"),
    SettingsPage("chat_tags", SettingsGroup.CHAT, "提示词标签", "常用片段一键插入"),

    SettingsPage("ui_look", SettingsGroup.LOOK, "主题与布局", "深浅色、气泡与侧边栏"),
    SettingsPage("ui_type", SettingsGroup.LOOK, "字体与输入", "正文字号、行距、输入提示"),
    SettingsPage("ui_person", SettingsGroup.LOOK, "个性化", "主题色与界面基调"),
    SettingsPage("ui_lang", SettingsGroup.LOOK, "语言", "界面显示语言"),
    SettingsPage("ui_md", SettingsGroup.LOOK, "Markdown", "代码字号与整段预览"),

    SettingsPage("stat", SettingsGroup.STAT, "用量统计", "会话、token、按模型、按天"),

    SettingsPage("ext_agent", SettingsGroup.EXT, "智能体", "角色与系统提示"),
    SettingsPage("ext_schedule", SettingsGroup.EXT, "定时任务", "到点自动执行"),
    SettingsPage("ext_mcp", SettingsGroup.EXT, "MCP 服务", "本地或远程 MCP 能力"),
    SettingsPage("ext_tavily", SettingsGroup.EXT, "联网搜索", "Tavily / Bing / Google 与原生搜索"),
    SettingsPage("ext_privacy", SettingsGroup.EXT, "隐私与加密", "凭据加密与数据边界"),
    SettingsPage("ext_backup", SettingsGroup.EXT, "备份与恢复", "导出 / 导入数据包"),
    SettingsPage("ext_adb", SettingsGroup.EXT, "手机互联", "ADB · 双通道传输 · 局域网探测"),
    SettingsPage("ext_tool", SettingsGroup.EXT, "工具权限", "10 个工具开关与命令超时"),
    SettingsPage("ext_safety", SettingsGroup.EXT, "沙箱与确认", "文件沙箱、工具确认、危险命令拦截"),
    SettingsPage("ext_data", SettingsGroup.EXT, "数据与备份", "导出、打开目录、清空"),
    SettingsPage("ext_usage", SettingsGroup.EXT, "使用上报", "设备码、接口与开关"),

    SettingsPage("about", SettingsGroup.ABOUT, "版本与更新", "版本、路径、重置"),
    SettingsPage("about_keys", SettingsGroup.ABOUT, "快捷键与操作", "按键、右键、页面入口"),
    SettingsPage("about_account", SettingsGroup.ABOUT, "GitHub 账号", "登录与凭据"),
    SettingsPage("about_reward", SettingsGroup.ABOUT, "反馈与赞赏", "提问题、支持作者"),
)

@Composable
fun SettingsScreen(
    state: AppState,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    // 初始为空：没点一级时二级不显示条目；从预算卡跳进来时直接落到上下文页
    var group by remember(state.showSettings) {
        mutableStateOf(SettingsGroup.entries.firstOrNull { it.name == state.settingsGroup })
    }
    var page by remember(state.showSettings) {
        mutableStateOf(SettingsPages.firstOrNull { it.id == state.settingsPage })
    }
    // 直接打开设置（没指定条目）时，自动落到第一个分类的第一项
    if (group == null && state.settingsGroup == null) {
        group = SettingsGroup.MODEL
        page = SettingsPages.firstOrNull { it.group == SettingsGroup.MODEL }
        state.settingsGroup = SettingsGroup.MODEL.name
    }

    Row(Modifier.fillMaxSize().background(colors.background)) {
        // 一级
        Column(
            modifier = Modifier
                .width(168.dp)
                .fillMaxHeight()
                .background(colors.surface)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(I18n.t("设置"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "✕",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onClose).padding(4.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsGroup.entries.forEach { item ->
                    val active = item == group
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                group = item
                                // 默认选中二级第一项，右侧马上有内容，不留空白
                                page = SettingsPages.firstOrNull { it.group == item }
                                state.settingsGroup = item.name
                                state.settingsPage = page?.id
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                I18n.t(item.label),
                                fontSize = 12.sp,
                                color = if (active) colors.primary else colors.onSurface,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            )
                            Text(
                                I18n.t(item.hint),
                                fontSize = 9.sp,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Baicodex Windows 0.1.0",
                fontSize = 9.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        VerticalDivider(color = colors.outlineVariant)

        // 二级：初始空，跟随一级填充
        Column(
            modifier = Modifier
                .width(196.dp)
                .fillMaxHeight()
                .background(colors.background)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            val current = group
            if (current == null) {
                Text(
                    I18n.t("先选左边的分类"),
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    I18n.t(current.label),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .padding(vertical = 4.dp),
                ) {
                    SettingsPages.filter { it.group == current }.forEachIndexed { index, item ->
                        val active = item.id == page?.id
                        if (index > 0) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 10.dp),
                                color = colors.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (active) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable {
                                    page = item
                                    state.settingsPage = item.id
                                }
                                .padding(start = 10.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(3.dp, 18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (active) colors.primary else Color.Transparent),
                            )
                            Spacer(Modifier.width(if (active) 6.dp else 9.dp))
                            Column {
                                Text(
                                    I18n.t(item.label),
                                    fontSize = 11.sp,
                                    color = if (active) colors.primary else colors.onSurface,
                                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                                )
                                Text(
                                    I18n.t(item.hint),
                                    fontSize = 9.sp,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = colors.outlineVariant)

        // 内容
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val target = page
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    target?.let { I18n.t(it.label) } ?: I18n.t("设置"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    target?.let { I18n.t(it.hint) } ?: I18n.t("从左边选分类，再选条目"),
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outlineVariant)

            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 手机端同款：整页内容装进一张大圆角卡
                    Column(
                        Modifier
                            .widthIn(max = 760.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        if (target == null) EmptyHint(group) else PageContent(state, target.id)
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(group: SettingsGroup?) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 40.dp)) {
        Text(
            if (group == null) I18n.t("还没有选择分类") else "「${group?.let { I18n.t(it.label) }}」下还没有选择条目",
            fontSize = 13.sp,
            color = colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Text(
            if (group == null) {
                "左边是一级分类，点一下，中间会出现这个分类下的设置项。"
            } else {
                "中间的列表就是这个分类的全部设置项，点一条在右边编辑。"
            },
            fontSize = 11.sp,
            lineHeight = 18.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PageContent(state: AppState, id: String) {
    when (id) {
        "model_cfg" -> ModelConfigPage(state)
        "model_param" -> ModelParamsPage(state)
        "model_think" -> ModelThinkPage(state)
        "model_voice" -> ModelVoicePage(state)
        "model_mem" -> ModelMemoryPage(state)
        "model_balance" -> ModelBalancePage(state)
        "chat_ctx" -> ChatContextPage(state)
        "chat_inout" -> ChatInOutPage(state)
        "chat_session" -> ChatSessionPage(state)
        "chat_prompt" -> ChatPromptPage(state)
        "chat_star" -> ChatStarPage(state)
        "chat_tags" -> ChatTagsPage(state)
        "ui_look" -> LookThemePage(state)
        "ui_person" -> LookPersonPage(state)
        "ui_lang" -> LookLangPage(state)
        "ui_type" -> LookTypePage(state)
        "ui_md" -> LookMarkdownPage(state)
        "stat" -> StatPage(state)
        "ext_agent" -> ExtAgentPage(state)
        "ext_schedule" -> ExtSchedulePage(state)
        "ext_mcp" -> ExtMcpPage(state)
        "ext_tavily" -> ExtSearchPage(state)
        "ext_privacy" -> ExtPrivacyPage(state)
        "ext_backup" -> ExtBackupPage(state)
        "ext_adb" -> ExtAdbPage(state)
        "ext_tool" -> ExtToolPage(state)
        "ext_safety" -> ExtSafetyPage(state)
        "ext_data" -> ExtDataPage(state)
        "ext_usage" -> ExtUsagePage(state)
        "about" -> AboutVersionPage(state)
        "about_keys" -> AboutKeysPage(state)
        "about_account" -> AccountSettings(state)
        "about_reward" -> AboutRewardPage(state)
    }
}
