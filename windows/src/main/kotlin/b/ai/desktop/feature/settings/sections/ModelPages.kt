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
 * Baicodex Windows - 设置 · 模型组
 * 模型配置 / 生成参数 / 思考默认 / 语音朗读，四页各自独立保存，互不覆盖别的字段。
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.balance.BalanceWatch
import b.ai.desktop.core.config.ModelProfile
import b.ai.desktop.core.util.Speech
import b.ai.desktop.data.Effort
import b.ai.desktop.feature.settings.ChoiceSetting
import b.ai.desktop.feature.settings.DdOption
import b.ai.desktop.feature.settings.DropdownSetting
import b.ai.desktop.feature.settings.dd
import b.ai.desktop.feature.settings.ddDefault
import b.ai.desktop.feature.settings.ddRange
import b.ai.desktop.feature.settings.plusCurrent
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.StepperSetting
import b.ai.desktop.feature.settings.TextSetting
import b.ai.desktop.feature.settings.ToggleSetting
import b.ai.desktop.core.auth.VbaiAuth
import b.ai.desktop.core.util.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 模型配置：多连接列表 + 当前连接的连接信息 */
@Composable
fun ModelConfigPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val profiles = state.config.profiles
    val active = state.config.activeProfile
    val scope = rememberCoroutineScope()

    SectionTitle("连接列表", "一条一套提供商 + Key + 模型，点一条即切换")
    if (profiles.isEmpty()) {
        Text("还没有配置，先新建一个", fontSize = 11.sp, color = colors.onSurfaceVariant)
    }
    profiles.forEach { profile ->
        ProfileRow(
            profile = profile,
            current = profile.id == active?.id,
            onSelect = { state.selectProfile(profile.id) },
            onDelete = { state.deleteProfile(profile.id) },
        )
    }
    Row(Modifier.padding(top = 6.dp)) {
        PillButton("＋ 新建连接") {
            //一条可用配置都没有时先问：要不要用开发者自建的中转站（登录即用）
            val blank = active == null || (active.baseUrl.isBlank() && active.apiKey.isBlank() && active.model.isBlank())
            if (blank && !VbaiAuth.logged) {
                vbaiNewFlow(state, scope)
            } else {
                state.addProfile()
                state.flash("已新建连接，填好信息再保存")
            }
        }
    }

    if (active == null) return

    var name by remember(active.id) { mutableStateOf(active.name) }
    var baseUrl by remember(active.id) { mutableStateOf(active.baseUrl) }
    var apiKey by remember(active.id) { mutableStateOf(active.apiKey) }
    var model by remember(active.id) { mutableStateOf(active.model) }
    var toolsEnabled by remember(active.id) { mutableStateOf(active.toolsEnabled) }
    var supportsThinking by remember(active.id) { mutableStateOf(active.supportsThinking) }
    var supportsNativeSearch by remember(active.id) { mutableStateOf(active.supportsNativeSearch) }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("提供商预设", "选一家自动填 baseUrl 和常用模型；都能再手改")
    var presetIndex by remember(active.id) { mutableStateOf(ProviderPresets.indexOf(baseUrl)) }
    DropdownSetting(
        title = "提供商",
        desc = null,
        options = ProviderPresets.options.mapIndexed { index, preset -> DdOption(index, preset.label) },
        current = presetIndex,
        onChange = { index ->
            presetIndex = index
            val preset = ProviderPresets.options.getOrNull(index) ?: return@DropdownSetting
            if (preset.baseUrl.isNotBlank()) baseUrl = preset.baseUrl
            if (preset.models.isNotEmpty()) model = preset.models.first()
        },
    )
    val suggestions = ProviderPresets.options.getOrNull(presetIndex)?.models.orEmpty()
    val hit = suggestions.indexOf(model)
    if (suggestions.isNotEmpty() && model.isNotBlank()) {
        val options = suggestions.mapIndexed { index, name -> DdOption(index, name) } +
            // 当前模型不在常用列表里也显示出来，别让下拉框撒谎
            (if (hit < 0) listOf(DdOption(suggestions.size, model)) else emptyList())
        DropdownSetting(
            title = "常用模型",
            desc = "点一条填进「模型名」；不在列表里的直接在下面手输",
            options = options,
            current = if (hit >= 0) hit else suggestions.size,
            onChange = { index -> model = suggestions.getOrElse(index) { model } },
        )
    }

    SectionTitle("连接信息", "只改这一页的字段，采样参数在「生成参数」")
    TextSetting("名称", name, { name = it }, "显示名，可留空")
    TextSetting("baseUrl", baseUrl, { baseUrl = it }, "https://api.deepseek.com/v1")
    TextSetting("API Key", apiKey, { apiKey = it }, "sk-…", password = true)
    TextSetting("模型名", model, { model = it }, "deepseek-chat")
    ToggleSetting(
        title = "允许工具调用",
        desc = if (toolsEnabled) "把工具清单下发给模型" else "只聊天，不下发工具",
        checked = toolsEnabled,
        onChange = { toolsEnabled = it },
    )

    SectionTitle("模型能力", "模型真支持的能力：关掉的功能就算开关开着也不会下发，避免 422")
    ToggleSetting(
        title = "支持深度思考",
        desc = if (supportsThinking) "reasoning 参数正常下发" else "不下发 reasoning；输入区的思考开关会被忽略",
        checked = supportsThinking,
        onChange = { supportsThinking = it },
    )
    ToggleSetting(
        title = "支持原生搜索",
        desc = if (supportsNativeSearch) "native_search 参数正常下发" else "不下发原生搜索；联网开关会被忽略",
        checked = supportsNativeSearch,
        onChange = { supportsNativeSearch = it },
    )

    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("保存", primary = true) {
            state.updateProfile(
                id = active.id,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                toolsEnabled = toolsEnabled,
                temperature = active.temperature,
                maxTokens = active.maxTokens,
                topP = active.topP,
                timeoutSec = active.timeoutSec,
                supportsThinking = supportsThinking,
                supportsNativeSearch = supportsNativeSearch,
            )
            state.flash("连接信息已保存")
        }
        PillButton("测试连接") { scope.launch { state.testConnection() } }
        PillButton("撤销修改") {
            name = active.name
            baseUrl = active.baseUrl
            apiKey = active.apiKey
            model = active.model
            toolsEnabled = active.toolsEnabled
            supportsThinking = active.supportsThinking
            supportsNativeSearch = active.supportsNativeSearch
        }
    }
}

/** 生成参数：温度 / top_p / 输出上限 / 超时 / 并行工具调用 */
@Composable
fun ModelParamsPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val active = state.config.activeProfile
    if (active == null) {
        Text("先到「模型配置」建一个连接", fontSize = 11.sp, color = colors.onSurfaceVariant)
        return
    }

    var tempStep by remember(active.id) {
        mutableStateOf(if (active.temperature < 0) -1 else (active.temperature * 10).roundToInt())
    }
    var topPStep by remember(active.id) {
        mutableStateOf(if (active.topP < 0) -1 else (active.topP * 10).roundToInt())
    }
    var maxTokens by remember(active.id) { mutableStateOf(active.maxTokens) }
    var timeoutSec by remember(active.id) { mutableStateOf(active.timeoutSec) }

    SectionTitle("采样", "留「默认」就不下发，交给服务端按模型自己决定")
    DropdownSetting(
        title = "温度",
        desc = "越低越稳定，越高越发散",
        options = ddRange(0, 20, 1) { String.format("%.1f", it / 10.0) }.let { ddDefault(-1, "默认", it) },
        current = tempStep,
        onChange = { tempStep = it },
    )
    DropdownSetting(
        title = "top_p",
        desc = "nucleus 采样；和温度二选一调即可",
        options = ddRange(1, 10, 1) { String.format("%.1f", it / 10.0) }.let { ddDefault(-1, "默认", it) },
        current = topPStep,
        onChange = { topPStep = it },
    )

    SectionTitle("输出与超时")
    DropdownSetting(
        title = "最大输出 tokens",
        desc = "单轮回复上限；最大模式另有 8192 兜底",
        options = dd(
            0 to "默认",
            1024 to "1,024", 2048 to "2,048", 4096 to "4,096", 8192 to "8,192",
            16384 to "16,384", 32768 to "32,768",
        ).plusCurrent(maxTokens) { it.toString() },
        current = maxTokens,
        onChange = { maxTokens = it },
    )
    DropdownSetting(
        title = "请求超时",
        desc = "深度思考可能跑很久，超时太短会被中途掐断",
        options = dd(30 to "30s", 60 to "60s", 90 to "90s", 120 to "120s", 180 to "180s", 300 to "300s", 600 to "600s", 900 to "900s")
            .plusCurrent(timeoutSec) { "${it}s" },
        current = timeoutSec,
        onChange = { timeoutSec = it },
    )
    ToggleSetting(
        title = "并行工具调用",
        desc = if (state.config.parallelToolCalls) "一轮里可以同时发多个工具调用" else "一轮只允许一个工具调用",
        checked = state.config.parallelToolCalls,
        onChange = { value -> state.editConfig { it.copy(parallelToolCalls = value) } },
    )

    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("保存", primary = true) {
            state.updateProfile(
                id = active.id,
                name = active.name,
                baseUrl = active.baseUrl,
                apiKey = active.apiKey,
                model = active.model,
                toolsEnabled = active.toolsEnabled,
                temperature = if (tempStep < 0) -1.0 else tempStep / 10.0,
                maxTokens = maxTokens,
                topP = if (topPStep < 0) -1.0 else topPStep / 10.0,
                timeoutSec = timeoutSec,
                supportsThinking = active.supportsThinking,
                supportsNativeSearch = active.supportsNativeSearch,
            )
            state.flash("生成参数已保存")
        }
        PillButton("撤销修改") {
            tempStep = if (active.temperature < 0) -1 else (active.temperature * 10).roundToInt()
            topPStep = if (active.topP < 0) -1 else (active.topP * 10).roundToInt()
            maxTokens = active.maxTokens
            timeoutSec = active.timeoutSec
        }
        Text(
            "温度高时 top_p 保持默认，两个一起调通常会过冲",
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/** 思考默认：只影响新建会话，已开会话里的开关不动 */
@Composable
fun ModelThinkPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val levels = remember(state.modelName) { Effort.levels(state.modelName) }

    SectionTitle("新会话默认", "只决定新建会话时工具栏的初始状态，随时能在输入区改")
    ToggleSetting(
        title = "默认开启深度思考",
        desc = if (state.config.defaultThinking) "新会话一进来就先推理再回答" else "默认不思考，需要时点输入区上方开关",
        checked = state.config.defaultThinking,
        onChange = { value -> state.editConfig { it.copy(defaultThinking = value) } },
    )
    ChoiceSetting(
        title = "默认档位",
        options = levels,
        selectedIndex = Effort.indexOf(levels, state.config.defaultEffort),
        onSelect = { index ->
            state.editConfig { it.copy(defaultEffort = levels.getOrElse(index) { "medium" }) }
        },
    )
    ToggleSetting(
        title = "默认最大思考",
        desc = if (state.config.defaultMaxMode) "新会话开到最大思考档（更慢更深）" else "跟随普通档位",
        checked = state.config.defaultMaxMode,
        onChange = { value -> state.editConfig { it.copy(defaultMaxMode = value) } },
    )
    ToggleSetting(
        title = "默认原生搜索",
        desc = if (state.config.defaultNativeSearch) "新会话默认开网关原生联网搜索" else "新会话默认不联网",
        checked = state.config.defaultNativeSearch,
        onChange = { value -> state.editConfig { it.copy(defaultNativeSearch = value) } },
    )
    ToggleSetting(
        title = "显示思考过程",
        desc = if (state.config.showReasoning) "服务端返回的推理内容折叠显示在回复上方" else "完全隐藏思考块",
        checked = state.config.showReasoning,
        onChange = { value -> state.editConfig { it.copy(showReasoning = value) } },
    )
    Text(
        "档位表跟着当前模型走（${state.modelName}）：${levels.joinToString(" / ")}；" +
            "端点不认时会在请求时自动降档，不会整轮失败。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    ToggleSetting(
        title = "默认开启最大模式",
        desc = "抬高单轮输出上限；长文档任务用得上，日常聊天会变慢",
        checked = state.config.defaultMaxMode,
        onChange = { value -> state.editConfig { it.copy(defaultMaxMode = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("说明")
    Text(
        "深度思考开启后，服务端返回的 reasoning 会显示在回复上方（可在「对话 → 注入与输出」里关掉展示）。\n" +
            "思考内容为空通常意味着：这个模型不支持、端点没回 reasoning 字段，或档位是 none。",
        fontSize = 10.sp,
        lineHeight = 17.sp,
        color = colors.onSurfaceVariant,
    )
}

/** 语音朗读：自动朗读 + 语速，走 Windows 自带 SAPI，不引第三方依赖 */
@Composable
fun ModelVoicePage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("朗读", "用系统语音念回复；不需要联网，也不会把内容发出去")
    ToggleSetting(
        title = "回复后自动朗读",
        desc = if (state.config.autoSpeak) "每轮回答结束后自动念出来" else "只在你右键「朗读」时念",
        checked = state.config.autoSpeak,
        onChange = { value -> state.editConfig { it.copy(autoSpeak = value) } },
    )
    DropdownSetting(
        title = "语速",
        desc = "SAPI Rate：负数更慢，正数更快",
        options = ddRange(-10, 10, 1) { if (it == 0) "0（默认）" else if (it > 0) "+$it" else "$it" },
        current = state.config.speechRate,
        onChange = { value -> state.editConfig { it.copy(speechRate = value) } },
    )

    Row(Modifier.padding(top = 10.dp)) {
        PillButton("试听", primary = true) {
            val ok = Speech.speak(
                "这是 Baicodex 的朗读测试：当前语速 ${state.config.speechRate}，中文和 English 都能念。",
                state.config.speechRate,
            )
            state.flash(if (ok) "开始朗读" else "朗读失败：系统语音不可用")
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("语音识别", "输入框的麦克风走这里：录音 → OpenAI 兼容 /audio/transcriptions → 文本进草稿")
    TextSetting("识别模型", state.config.sttModel, { value -> state.editConfig { it.copy(sttModel = value) } }, "whisper-1")
    DropdownSetting(
        title = "识别语言",
        desc = null,
        options = listOf(
            DdOption(0, "自动"),
            DdOption(1, "中文 zh"),
            DdOption(2, "英文 en"),
            DdOption(3, "日文 ja"),
        ),
        current = when (state.config.sttLanguage) {
            "zh" -> 1
            "en" -> 2
            "ja" -> 3
            else -> 0
        },
        onChange = { index ->
            val lang = when (index) {
                1 -> "zh"
                2 -> "en"
                3 -> "ja"
                else -> ""
            }
            state.editConfig { it.copy(sttLanguage = lang) }
        },
    )
    TextSetting(
        "识别端点",
        state.config.sttEndpoint,
        { value -> state.editConfig { it.copy(sttEndpoint = value.trim()) } },
        "留空 = 跟随当前连接的 baseUrl",
    )
    Text(
        "录音最长 120 秒自动停；音频只发往识别端点，不落盘、不进聊天记录。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        "Windows 上走 System.Speech；如果机器精简掉了语音组件，试听会直接报不可用。",
        fontSize = 9.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ProfileRow(
    profile: ModelProfile,
    current: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (current) colors.primary.copy(alpha = 0.08f) else colors.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.label,
                    fontSize = 12.sp,
                    color = if (current) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Text("当前", fontSize = 9.sp, color = colors.primary)
                }
            }
            Text(
                listOf(profile.baseUrl, profile.model).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { "还没填连接信息" },
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "删除",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDelete).padding(6.dp),
        )
    }
}

/**
 * 长期记忆：一段跨会话保留的文本，开启后每轮拼进系统提示。
 * 不做自动整理（那会偷偷改用户的东西），只提供编辑与开关。
 */
@Composable
fun ModelMemoryPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    var text by remember(state.config.memoryText) { mutableStateOf(state.config.memoryText) }

    SectionTitle("长期记忆", "写在这里的偏好与事实，每轮都会带进系统提示")
    ToggleSetting(
        title = "启用记忆",
        desc = if (state.config.memoryEnabled) "每轮请求都会带上下面这段（会占上下文预算）" else "关闭后不注入，内容仍保留",
        checked = state.config.memoryEnabled,
        onChange = { value -> state.editConfig { it.copy(memoryEnabled = value) } },
    )
    TextSetting(
        label = "记忆内容",
        value = text,
        onChange = { text = it },
        placeholder = "例：我用 Kotlin 和 Compose；回答先给结论；不要用 emoji",
        singleLine = false,
    )
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("保存", primary = true) {
            state.editConfig { it.copy(memoryText = text.trim()) }
            state.flash("记忆已保存")
        }
        PillButton("清空") {
            text = ""
            state.editConfig { it.copy(memoryText = "") }
        }
        Text(
            "${text.length} 字",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
    Text(
        "记忆不会自己生长：想让它记住新东西，把它写进来。删掉就相当于忘掉。",
        fontSize = 9.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * 余额：手机端预算卡里的那一块，桌面同样显示在预算卡里，这里可以配置来源与阈值。
 * 只查 DeepSeek 兼容的 /user/balance；查不到就说原因，不编数字。
 */
@Composable
fun ModelBalancePage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val balance = BalanceWatch.last
    val profiles = state.config.profiles

    SectionTitle("当前余额", "来自 /user/balance，预算卡里显示的也是这个数")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                balance == null -> "还没查过"
                balance.ok -> balance.text
                else -> "查不到"
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                balance?.ok != true -> colors.onSurfaceVariant
                balance.below(state.config.balanceThreshold) -> colors.error
                else -> colors.primary
            },
        )
        Spacer(Modifier.width(10.dp))
        PillButton(
            if (BalanceWatch.busy) "查询中…" else "刷新",
            primary = true,
            enabled = !BalanceWatch.busy,
        ) {
            scope.launch { state.refreshBalance(force = true) }
        }
        Spacer(Modifier.width(8.dp))
        if (balance?.error != null) {
            Text(
                balance.error!!,
                fontSize = 9.sp,
                color = colors.error,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
    if (balance != null && balance.ok) {
        Text(
            "来源：${balance.source} · ${if (balance.at > 0) java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(balance.at), java.time.ZoneId.systemDefault()).toString().take(5) else ""}",
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (balance != null && !balance.ok) {
        Text(
            balance.error ?: "查不到余额",
            fontSize = 9.sp,
            lineHeight = 15.sp,
            color = colors.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("查询来源", "官方 /user/balance 只认 DeepSeek 官方 Key；网关若实现了该接口会优先用网关")
    val options = buildList {
        add("跟随当前连接")
        profiles.forEach { add(it.label) }
    }
    val selected = profiles.indexOfFirst { it.id == state.config.balanceProfileId } + 1
    if (profiles.isEmpty()) {
        Text("还没有模型连接，先到「模型配置」建一个", fontSize = 11.sp, color = colors.onSurfaceVariant)
    } else {
        ChoiceSetting(
            title = "使用连接",
            options = options,
            selectedIndex = selected.coerceIn(0, options.lastIndex),
            onSelect = { index ->
                state.editConfig { it.copy(balanceProfileId = profiles.getOrNull(index - 1)?.id.orEmpty()) }
                state.flash("余额来源已更新")
            },
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("提醒", "只在预算卡和提示条上提醒，不发系统通知")
    ToggleSetting(
        title = "低余额提醒",
        desc = if (state.config.balanceAlert) "余额低于阈值时预算卡标红并弹一次提示" else "不提醒",
        checked = state.config.balanceAlert,
        onChange = { value -> state.editConfig { it.copy(balanceAlert = value) } },
    )
    DropdownSetting(
        title = "阈值",
        desc = "低于这个金额才提醒（只对人民币生效）",
        options = dd(5 to "5 元", 10 to "10 元", 20 to "20 元", 30 to "30 元", 50 to "50 元", 80 to "80 元", 100 to "100 元")
            .plusCurrent(state.config.balanceThreshold.toInt()) { "$it 元" },
        current = state.config.balanceThreshold.toInt(),
        onChange = { value -> state.editConfig { it.copy(balanceThreshold = value.toDouble()) } },
    )
}

/** 提供商预设：选一家带出 baseUrl 与常用模型，key 永远要用户自己填 */
object ProviderPresets {
    class Preset(val label: String, val baseUrl: String, val models: List<String>)

    val options = listOf(
        Preset("自定义", "", emptyList()),
        Preset("DeepSeek 官方", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner")),
        //Vbai 中转站对齐手机端厂商表：同一个 base，名字统一，桌面端也能一眼认出
        Preset("Vbai 中转站", "https://ai.baiccb.cn/v1", listOf("deepseek-v4.1-flash", "deepseek-v4-pro", "kimi-k3", "glm-5")),
        Preset("OpenRouter", "https://openrouter.ai/api/v1", listOf("openai/gpt-5.2", "anthropic/claude-4.5-sonnet", "google/gemini-3-pro", "deepseek/deepseek-chat")),
        Preset("硅基流动", "https://api.siliconflow.cn/v1", listOf("deepseek-ai/DeepSeek-V4", "Qwen/Qwen4-72B-Instruct", "THUDM/GLM-5-9B")),
        Preset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-5", "glm-5-air", "glm-5-flash")),
        Preset("月之暗面 Kimi", "https://api.moonshot.cn/v1", listOf("kimi-k3", "moonshot-v1-128k")),
        Preset("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen4-max", "qwen4-plus", "qwen4-turbo")),
    )

    /** 按已填的 baseUrl 反查预设位置；没命中回 0（自定义） */
    fun indexOf(baseUrl: String): Int =
        options.indexOfFirst { it.baseUrl.isNotBlank() && it.baseUrl == baseUrl.trim() }.coerceAtLeast(0)
}

/**
 * 无可用配置时的新建引导（桌面端）：先问要不要用开发者自建的中转站，
 * 是 → 弹宣传页（打开官网 / 登录账号），登录成功直接写进模型配置；否 → 走原来的空白配置。
 * 用 Swing 对话框：桌面端没有统一的 Compose 弹窗体系，这里保持最小依赖。
 */
private fun vbaiNewFlow(state: AppState, scope: CoroutineScope) {
    val ask = javax.swing.JOptionPane.showOptionDialog(
        null,
        "是否使用开发者创立的中转站？\n\nVbai 中转站由开发者自建：一个 Key 就能用多家模型，登录即用。",
        "新建连接",
        javax.swing.JOptionPane.DEFAULT_OPTION,
        javax.swing.JOptionPane.QUESTION_MESSAGE,
        null,
        arrayOf("否，我自己填", "是，先看看"),
        "是，先看看"
    )
    if (ask != 1) {
        state.addProfile()
        state.flash("已新建连接，填好信息再保存")
        return
    } //不走中转站：保持原流程

    val pick = javax.swing.JOptionPane.showOptionDialog(
        null,
        "Vbai 中转站\n\n" +
            "· 登录即用：设备码授权，不用自己找 Key\n" +
            "· 统一计费：套餐与余额在账号页可见\n" +
            "· 兼容 OpenAI：base_url " + VbaiAuth.BASE + "\n\n" +
            "当前 Key：" + (VbaiAuth.account?.masked?.ifBlank { "已登录" } ?: "未登录"),
        "Vbai 中转站",
        javax.swing.JOptionPane.DEFAULT_OPTION,
        javax.swing.JOptionPane.INFORMATION_MESSAGE,
        null,
        arrayOf("关闭", "打开官网", "登录账号"),
        "登录账号"
    )
    when (pick) {
        1 -> openUrl(VbaiAuth.SITE)
        2 -> scope.launch {
            //登录内部自己开浏览器 + 轮询；成功后直接把 Key 写进模型配置
            val ok = VbaiAuth.login()
            state.flash(if (ok) state.applyVbaiProfile() else VbaiAuth.msg)
        }
    }
} //新建时推荐中转站
