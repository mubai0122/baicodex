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
 * Baicodex Windows - 设置 · 对话组
 * 上下文 / 注入与输出 / 会话 / 提示词优化 / 收藏夹。
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.util.fmtTokens
import b.ai.desktop.feature.settings.ChoiceSetting
import b.ai.desktop.feature.settings.DdOption
import b.ai.desktop.feature.settings.DropdownSetting
import b.ai.desktop.feature.settings.dd
import b.ai.desktop.feature.settings.plusCurrent
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.StepperSetting
import b.ai.desktop.feature.settings.TextSetting
import b.ai.desktop.feature.settings.ToggleSetting
import kotlinx.coroutines.launch

/** 上下文：预算 + 历史条数（都不改变模型能力，只控制送多少进去） */
@Composable
fun ChatContextPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("上下文预算", "顶栏的预算环按它算百分比；只是提示，不会截断请求")
    DropdownSetting(
        title = "预算",
        desc = if (state.config.ctxBudget == 0) "不显示占用环" else "按估算 token 计算，70% 起预算环变红",
        options = dd(
            0 to "不显示",
            8_000 to "8K", 16_000 to "16K", 32_000 to "32K", 48_000 to "48K",
            64_000 to "64K", 96_000 to "96K", 128_000 to "128K", 200_000 to "200K",
        ).plusCurrent(state.config.ctxBudget) { fmtTokens(it) },
        current = state.config.ctxBudget,
        onChange = { value -> state.editConfig { it.copy(ctxBudget = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("上下文压缩", "对齐手机端：超过压缩线的历史压成摘要，跟着最近消息一起下发")
    ToggleSetting(
        title = "压缩超限历史",
        desc = if (state.config.ctxCompress) {
            "被截掉的那段先压成摘要（role=summary 语义），原始消息不动"
        } else {
            "直接截断，更早的消息不进上下文"
        },
        checked = state.config.ctxCompress,
        onChange = { value -> state.editConfig { it.copy(ctxCompress = value) } },
    )
    DropdownSetting(
        title = "上下文窗口",
        desc = "压缩线按「窗口 × 阈值」算；模型自己报的窗口以它为准",
        options = dd(
            32_000 to "32K", 64_000 to "64K", 128_000 to "128K",
            256_000 to "256K", 1_000_000 to "1M",
        ).plusCurrent(state.config.ctxTotal) { if (it >= 1_000_000) "1M" else "${it / 1000}K" },
        current = state.config.ctxTotal,
        onChange = { value -> state.editConfig { it.copy(ctxTotal = value) } },
    )
    DropdownSetting(
        title = "压缩阈值",
        desc = "估算 token 到窗口的这个比例就开始压缩（当前线 ≈ " +
            fmtTokens(state.config.ctxTotal * state.config.ctxPct.coerceIn(10, 99) / 100) + "）",
        options = dd(
            50 to "50%", 60 to "60%", 70 to "70%", 80 to "80%", 90 to "90%",
        ).plusCurrent(state.config.ctxPct) { "$it%" },
        current = state.config.ctxPct,
        onChange = { value -> state.editConfig { it.copy(ctxPct = value.coerceIn(10, 99)) } },
    )
    DropdownSetting(
        title = "压缩后保留",
        desc = "压缩线以上时，最近这几条原话不压，原样下发",
        options = dd(4 to "4 条", 8 to "8 条", 16 to "16 条").plusCurrent(state.config.ctxKeep) { "$it 条" },
        current = state.config.ctxKeep,
        onChange = { value -> state.editConfig { it.copy(ctxKeep = value) } },
    )
    DropdownSetting(
        title = "摘要档位",
        desc = when (state.config.summaryLevel) {
            "brief" -> "简洁：上限 1500 字，可能跟随轮次丢失关键"
            "full" -> "全面：不限字数（硬上限 5 万 token），最完整"
            else -> "详细：上限 12000 字，默认档，够放下完整现场"
        },
        options = listOf(
            DdOption(0, "简洁 · 1500 字"),
            DdOption(1, "详细 · 12000 字"),
            DdOption(2, "全面 · 不限字数"),
        ),
        current = when (state.config.summaryLevel) {
            "brief" -> 0
            "full", "detailed" -> 2
            else -> 1
        },
        onChange = { index ->
            state.editConfig {
                it.copy(summaryLevel = when (index) {
                    0 -> "brief"
                    2 -> "full"
                    else -> "standard"
                })
            }
        },
    )
    DropdownSetting(
        title = "压缩用哪个连接",
        desc = "选轻量模型省钱；留空跟随当前对话模型",
        options = listOf(DdOption(0, "跟随当前对话模型")) +
            state.config.profiles.mapIndexed { index, profile -> DdOption(index + 1, profile.label) },
        current = state.config.profiles.indexOfFirst { it.id == state.config.summaryProfileId }.let {
            if (it >= 0) it + 1 else 0
        },
        onChange = { index ->
            val id = state.config.profiles.getOrNull(index - 1)?.id.orEmpty()
            state.editConfig { it.copy(summaryProfileId = id) }
        },
    )
    var sumPrompt by rememberText(state.config.summaryPrompt)
    TextSetting(
        label = "总结补充要求",
        value = sumPrompt,
        onChange = { sumPrompt = it },
        placeholder = "留空不追加；填了拼在内置压缩提示词后面",
    )
    PillButton("保存补充要求") {
        state.editConfig { it.copy(summaryPrompt = sumPrompt.trim()) }
        state.flash("总结补充要求已保存")
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(modifier = Modifier.padding(top = 6.dp)) {
        PillButton("立即压缩当前会话", primary = true) {
            scope.launch { state.compactNow() }
        }
    }
    Text(
        "立即压缩 = 现在就按压缩规则生成摘要，下次发送直接带上；没到压缩线会提示。",
        fontSize = 9.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )

    SectionTitle("历史", "每轮只带最近 N 条；太长既慢又贵，也更容易忘掉开头")
    DropdownSetting(
        title = "上下文消息上限",
        desc = "裁剪后若开头是孤立的工具回执，会自动往后挪，避免协议报错；0 = 按压缩保留条数",
        options = dd(
            0 to "全部（压缩按保留条数算）",
            10 to "10 条", 20 to "20 条", 30 to "30 条", 40 to "40 条", 60 to "60 条",
            80 to "80 条", 100 to "100 条", 150 to "150 条", 200 to "200 条",
        ).plusCurrent(state.config.historyLimit) { "$it 条" },
        current = state.config.historyLimit,
        onChange = { value -> state.editConfig { it.copy(historyLimit = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    val stat = state.ctxStat()
    SectionTitle("当前会话", "实时估算，和预算环同一份数据")
    Text(
        "已用 ${fmtTokens(stat.used)} / ${if (stat.budget > 0) fmtTokens(stat.budget) else "未设"} tokens · " +
            "${stat.count} 条消息" +
            if (stat.spent > 0) " · 服务端回报累计 ${fmtTokens(stat.spent)}" else "",
        fontSize = 10.sp,
        color = if (stat.nearLimit) colors.error else colors.onSurfaceVariant,
    )
    if (stat.nearLimit) {
        Text(
            "已经偏满：建议开新会话，或在「对话」里把消息上限调小。",
            fontSize = 9.sp,
            color = colors.error,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 注入与输出：运行时信息、输出偏好、回复信息、草稿 */
@Composable
fun ChatInOutPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    var style by rememberText(state.config.stylePrompt)

    SectionTitle("注入", "开关只控制「拼不拼进系统提示」，不改写你的提示词")
    ToggleSetting(
        title = "注入运行环境",
        desc = if (state.config.injectContext) "把当前时间、系统、工作区路径拼进系统提示" else "模型不知道现在几点、在哪个目录",
        checked = state.config.injectContext,
        onChange = { value -> state.editConfig { it.copy(injectContext = value) } },
    )

    SectionTitle("输出偏好", "追加在系统提示末尾，比如「先给结论」「注释用中文」")
    TextSetting(
        label = "偏好",
        value = style,
        onChange = { style = it },
        placeholder = "回答先给结论，再给必要的过程；代码注释用中文",
        singleLine = false,
    )
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("应用", primary = true) {
            state.editConfig { it.copy(stylePrompt = style.trim()) }
            state.flash("输出偏好已保存")
        }
        PillButton("清空") {
            style = ""
            state.editConfig { it.copy(stylePrompt = "") }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("回复信息", "气泡下方那行小字（对齐手机端：模型/时间 与 用量 分开控）")
    ToggleSetting(
        title = "显示模型与时间",
        desc = if (state.config.showMeta) "回复下方显示 模型名 · 时间" else "不显示",
        checked = state.config.showMeta,
        onChange = { value -> state.editConfig { it.copy(showMeta = value) } },
    )
    ToggleSetting(
        title = "显示 token 与耗时",
        desc = if (state.config.showUsage) "真实用量才显示，没有服务端回报就不编数字" else "不显示用量",
        checked = state.config.showUsage,
        onChange = { value -> state.editConfig { it.copy(showUsage = value) } },
    )
    ToggleSetting(
        title = "显示时间",
        desc = "气泡下方的 HH:mm",
        checked = state.config.showTimestamp,
        onChange = { value -> state.editConfig { it.copy(showTimestamp = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("草稿")
    ToggleSetting(
        title = "按会话保留草稿",
        desc = if (state.config.keepDrafts) "切走再切回来，输入框里的字还在" else "切换会话即清空输入框",
        checked = state.config.keepDrafts,
        onChange = { value -> state.editConfig { it.copy(keepDrafts = value) } },
    )
}

/** 会话：自动命名、删除确认、条数显示 */
@Composable
fun ChatSessionPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("列表")
    ToggleSetting(
        title = "显示消息条数",
        desc = "侧边栏会话项右侧的「N 条」",
        checked = state.config.showMessageCount,
        onChange = { value -> state.editConfig { it.copy(showMessageCount = value) } },
    )
    ToggleSetting(
        title = "首条消息自动命名",
        desc = if (state.config.autoTitle) "新会话用第一句提问的前 24 字当标题" else "标题保持「新会话」，右键可重命名",
        checked = state.config.autoTitle,
        onChange = { value -> state.editConfig { it.copy(autoTitle = value) } },
    )

    SectionTitle("删除")
    ToggleSetting(
        title = "删除前二次确认",
        desc = if (state.config.confirmDelete) "会话和消息都要再点一下确认" else "右键即删，不追问",
        checked = state.config.confirmDelete,
        onChange = { value -> state.editConfig { it.copy(confirmDelete = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("当前")
    Text(
        "共 ${state.sessions.size} 个会话，当前「${state.currentTitle}」${state.messages.size} 条消息。" +
            "置顶会话始终排在前面，顺序就是置顶先后。",
        fontSize = 10.sp,
        lineHeight = 17.sp,
        color = colors.onSurfaceVariant,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("导出当前会话") { state.exportSession(state.currentSession ?: return@PillButton) }
        PillButton("新会话") { state.newConversation() }
    }
}

/** 提示词优化：选优化用的连接；不选就跟随当前对话模型 */
@Composable
fun ChatPromptPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val profiles = state.config.profiles
    val options = buildList {
        add("跟随当前对话")
        profiles.forEach { add(it.label) }
    }
    val selected = profiles.indexOfFirst { it.id == state.config.optimizerProfileId } + 1

    SectionTitle("优化模型", "输入区那个四芒星按这里选的连接改写提示词")
    if (profiles.isEmpty()) {
        Text("还没有模型连接，先到「模型 → 模型配置」建一个", fontSize = 11.sp, color = colors.onSurfaceVariant)
    } else {
        ChoiceSetting(
            title = "使用连接",
            options = options,
            selectedIndex = selected.coerceIn(0, options.lastIndex),
            onSelect = { index ->
                val id = profiles.getOrNull(index - 1)?.id.orEmpty()
                state.editConfig { it.copy(optimizerProfileId = id) }
                state.refreshOptimizerBackend()
                state.flash(if (id.isEmpty()) "优化将使用当前对话模型" else "优化模型已切换")
            },
        )
        Text(
            "当前生效：${state.optimizerProfile()?.label ?: "未配置（点四芒星会提示先配模型）"}",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("行为", "这些是固定的，不提供开关")
    Text(
        "· 只改表达，不新增你没提的需求，也不替你选技术栈；\n" +
            "· 超过 4000 字的输入不做优化（自己写得更清楚，改了反而丢信息）；\n" +
            "· 优化结果会备份，点四芒星旁边的回退能拿回原文。",
        fontSize = 10.sp,
        lineHeight = 17.sp,
        color = colors.onSurfaceVariant,
    )
}

/** 收藏夹：跨会话看所有收藏，点一条跳到对应会话 */
@Composable
fun ChatStarPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val items = state.sessions.flatMap { session ->
        session.messages.mapIndexedNotNull { index, msg ->
            if (msg.starred) Triple(session, index, msg) else null
        }
    }

    SectionTitle("收藏的消息", "右键消息可以收藏；这里按会话顺序列出全部")
    if (items.isEmpty()) {
        Text("还没有收藏。聊天区右键任意消息 →「收藏这条」。", fontSize = 11.sp, color = colors.onSurfaceVariant)
        return
    }

    items.forEach { (session, index, msg) ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.45f))
                .clickable {
                    state.selectConversation(session.id)
                    state.showSettings = false
                    // 从设置里跳走，下次打开还是「初始空」的两级
                    state.settingsGroup = null
                    state.settingsPage = null
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title,
                    fontSize = 10.sp,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (msg.role == MsgRole.USER) "你" else "模型",
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "取消收藏",
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { state.toggleStarIn(session.id, index) }.padding(4.dp),
                )
            }
            Text(
                msg.text.replace('\n', ' ').take(120).ifBlank { "（空内容）" },
                fontSize = 11.sp,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 给文本框一个可重置的初值 */
@Composable
private fun rememberText(initial: String) = remember(initial) { mutableStateOf(initial) }


/** 提示词标签：常用片段库，一键插进输入框 */
@Composable
fun ChatTagsPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("标签库", "输入框右下角的标签按钮会把这里的一条插到草稿末尾")
    if (state.config.promptTags.isEmpty()) {
        Text("还没有标签，先加一条", fontSize = 11.sp, color = colors.onSurfaceVariant)
    }
    state.config.promptTags.forEach { tag ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tag.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Spacer(Modifier.weight(1f))
                PillButton("删除") { state.savePromptTags(state.config.promptTags.filter { it.id != tag.id }) }
            }
            Text(
                tag.text,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("新建标签")
    var label by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    TextSetting("标签名", label, { label = it }, "例如：代码审查")
    TextSetting("内容", text, { text = it }, "插进输入框的完整文本", singleLine = false)
    PillButton("添加", primary = true) {
        when {
            label.isBlank() -> state.flash("标签名不能为空")
            text.isBlank() -> state.flash("内容不能为空")
            else -> {
                state.savePromptTags(
                    state.config.promptTags + b.ai.desktop.core.config.PromptTag(label = label.trim(), text = text.trim()),
                )
                label = ""
                text = ""
                state.flash("标签已添加")
            }
        }
    }
    Text(
        "与「智能体」里的系统提示词不同：标签是插进对话草稿的一次性片段，适合复用指令和常用话术。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
