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
 * Baicodex Windows - 设置 · 扩展组
 * 工具权限 / 联网搜索 / 数据与备份。
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.tool.toolCatalog
import b.ai.desktop.core.usage.UsageReporter
import b.ai.desktop.core.util.openPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import b.ai.desktop.feature.settings.DdOption
import b.ai.desktop.feature.settings.DropdownSetting
import b.ai.desktop.feature.settings.dd
import b.ai.desktop.feature.settings.plusCurrent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import b.ai.desktop.core.schedule.ScheduledTask
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.SettingRow
import b.ai.desktop.feature.settings.StepperSetting
import b.ai.desktop.feature.settings.TextSetting
import b.ai.desktop.feature.settings.ToggleSetting

/** 工具权限：10 个内置工具的开关与命令超时 */
@Composable
fun ExtToolPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val catalog = toolCatalog()
    val saved = state.config.enabledTools

    SectionTitle("工具开关", "关掉后模型看不到这个工具，也就不会调用；全部打开时不写配置")
    catalog.forEach { (name, label) ->
        val enabled = saved.isEmpty() || name in saved
        ToggleSetting(
            title = label,
            desc = name,
            checked = enabled,
            onChange = { want ->
                val current = saved.ifEmpty { catalog.map { it.first } }
                val next = if (want) (current + name).distinct() else current - name
                state.editConfig {
                    it.copy(enabledTools = if (next.size == catalog.size) emptyList() else next)
                }
            },
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("编译工具链", "JDK / Gradle / Android SDK 等目录，分号分隔；run_command 的 PATH 会前置它们")
    var toolchain by remember(state.config.toolchainPath) { mutableStateOf(state.config.toolchainPath) }
    TextSetting(
        label = "工具链目录",
        value = toolchain,
        onChange = { toolchain = it },
        placeholder = "C:////AndroidSdk////jdk-21;C:////AndroidSdk////gradle-9.0.0",
    )
    PillButton("应用", primary = true) {
        state.editConfig { it.copy(toolchainPath = toolchain.trim()) }
        state.flash("工具链路径已更新，下一条命令生效")
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("命令执行", "run_command 只在工作区目录里跑，超时后强制结束")
    DropdownSetting(
        title = "默认超时",
        desc = "模型没指定 timeout_sec 时用这个值",
        options = dd(10 to "10s", 15 to "15s", 30 to "30s", 45 to "45s", 60 to "60s", 90 to "90s", 120 to "120s")
            .plusCurrent(state.config.commandTimeoutSec) { "${it}s" },
        current = state.config.commandTimeoutSec,
        onChange = { value -> state.editConfig { it.copy(commandTimeoutSec = value) } },
    )
    if (saved.isNotEmpty() && "run_command" !in saved) {
        Text(
            "run_command 已关闭：模型只能读写文件，不能执行构建/测试命令。",
            fontSize = 10.sp,
            color = colors.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("插件工具", "插件页里启用的插件会追加自己的命令工具")
    Text(
        if (state.activePlugins.isEmpty()) {
            "当前没有启用的插件。"
        } else {
            "已启用 " + state.activePlugins.joinToString("、") { it.title } +
                "，声明的工具会一起下发（和工作区工具同名的会被跳过）。"
        },
        fontSize = 10.sp,
        lineHeight = 17.sp,
        color = colors.onSurfaceVariant,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("打开插件页") {
            // 插件是聊天区的一级页面，压在设置下面会被盖住，先收起设置
            state.showSettings = false
            state.settingsGroup = null
            state.settingsPage = null
            state.showPlugins = true
        }
        Text(
            "${state.plugins.size} 个已安装 · ${state.activePlugins.size} 个启用",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/** 数据与备份：导出全部会话、打开数据目录、清空会话 */
@Composable
fun ExtDataPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    var confirmClear by remember { mutableStateOf(false) }

    SectionTitle("位置")
    SettingRow("数据目录", "配置、会话、插件、Git 凭据、头像缓存") {
        PillButton("打开") { openPath(ConfigStore.appDir().absolutePath) }
    }
    SettingRow("会话文件", "sessions.json，纯文本，可直接备份") {
        PillButton("打开") { openPath(ConfigStore.appDir().absolutePath) }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("导出", "导出为 Markdown，工具输出放在代码块里")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("导出当前会话", primary = true) {
            state.currentSession?.let { state.exportSession(it) } ?: state.flash("当前没有会话")
        }
        PillButton("导出全部会话") {
            val file = state.exportAll()
            if (file != null) state.flash("已导出：${file.name}")
        }
    }
    Text(
        "导出全部会把所有会话拼成一份文档，按会话顺序分段。",
        fontSize = 9.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("清空", "只删本机的会话记录，不动配置文件和工作区文件")
    if (confirmClear) {
        Column(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(colors.error.copy(alpha = 0.08f))
                .padding(12.dp),
        ) {
            Text(
                "确认清空全部 ${state.sessions.size} 个会话？这个操作不可撤销，建议先导出。",
                fontSize = 11.sp,
                color = colors.error,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("确认清空", primary = true) {
                    state.clearAllSessions()
                    confirmClear = false
                }
                PillButton("取消") { confirmClear = false }
            }
        }
    } else {
        PillButton("清空全部会话") { confirmClear = true }
    }

    Spacer(Modifier.width(1.dp).padding(top = 10.dp))
}

/**
 * 使用上报（手机端同款协议）：启动累加一次在线数，只发设备码哈希 + 计数 + 时间戳 + nonce。
 * 设备码 = SHA256(主板序列号 | CPU ProcessorId | 系统盘序列号)，原始标识不上传、不落盘。
 */
@Composable
fun ExtUsagePage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }
    var tipOk by remember { mutableStateOf(true) }
    var endpoint by remember(state.config.usageEndpoint) { mutableStateOf(state.config.usageEndpoint) }
    val device = state.deviceInfo
    val last = UsageReporter.state

    SectionTitle("使用上报", "只在启动时累加一次在线计数，用来知道有多少台机器在用")
    ToggleSetting(
        title = "启用上报",
        desc = if (state.config.usageReportEnabled) "每次启动向上报接口发一次（失败静默）" else "关闭后一次请求都不发",
        checked = state.config.usageReportEnabled,
        onChange = { value -> state.editConfig { it.copy(usageReportEnabled = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("设备码", "硬件指纹哈希，换主板或系统盘才会变；重装系统不变")
    if (device == null) {
        Text("正在采集…", fontSize = 10.sp, color = colors.onSurfaceVariant)
    } else {
        Text(
            device.pretty,
            fontSize = 11.sp,
            color = colors.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Text(
            "采集来源：${device.sources.joinToString(" + ")}" +
                if (device.sources.size < 3) "（有项没读到，指纹仍稳定）" else "",
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        device.notes.forEach { note ->
            Text("· $note", fontSize = 9.sp, color = colors.error, modifier = Modifier.padding(top = 2.dp))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("复制") {
                val ok = b.ai.desktop.core.util.copyToClipboard(device.code)
                tipOk = ok
                tip = if (ok) "设备码已复制" else "复制失败：剪贴板不可用"
            }
            PillButton("重新采集") {
                state.refreshDeviceCode()
                tip = "已重新采集"
                tipOk = true
            }
        }
        Text(
            "原始序列号不落盘也不上传；本机只保存哈希，见 device.json。",
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("接口", "默认与手机端同一个接口；自建服务端可以改")
    TextSetting(
        label = "上报地址",
        value = endpoint,
        onChange = { endpoint = it },
        placeholder = UsageReporter.DefaultEndpoint,
    )
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("应用地址") {
            state.editConfig { it.copy(usageEndpoint = endpoint.trim()) }
            state.flash("上报地址已保存")
        }
        PillButton(
            if (busy) "上报中…" else "立即上报",
            primary = true,
            enabled = !busy,
        ) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { state.reportUsageNow() }
                busy = false
                tipOk = result.ok
                tip = if (result.ok) "上报成功：${result.message.take(60)}" else "上报失败：${result.message.take(80)}"
            }
        }
    }

    if (last != null) {
        Text(
            "上次：${java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(last.at), java.time.ZoneId.systemDefault())}" +
                " · ${if (last.ok) "成功" else "失败"} · ${last.message.take(80)}",
            fontSize = 9.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (tip != null) {
        Text(
            tip!!,
            fontSize = 10.sp,
            color = if (tipOk) colors.primary else colors.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}


/** 智能体角色 + 自定义系统提示：改的是 config.systemPrompt，注入/文风/记忆照旧叠加在它后面 */
@Composable
fun ExtAgentPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val roles = listOf(
        "编码助手" to b.ai.desktop.core.config.DEFAULT_SYSTEM_PROMPT,
        "通用助手" to "你是 Baicodex 桌面版里的通用助手。回答先给结论再给理由；" +
            "涉及本机文件或命令时优先用工具实际操作，而不是只给说明。",
        "翻译官" to "你是专业翻译。收到中文就译成地道英文，收到英文就译成流畅中文；" +
            "保留代码与专有名词原文，不要解释，只输出译文。",
        "写作助手" to "你是文字功底扎实的写作助手。按用户给的场景与语气输出成稿，" +
            "先给成稿再给可选的修改建议；不要输出空洞的客套话。",
    )

    var prompt by remember(state.config.systemPrompt) { mutableStateOf(state.config.systemPrompt) }
    val roleIndex = roles.indexOfFirst { it.second == state.config.systemPrompt }
    var selectedRole by remember(state.config.systemPrompt) { mutableStateOf(if (roleIndex >= 0) roleIndex else roles.size) }

    SectionTitle("智能体角色", "选一个直接套用；下面的文本框可以随意改")
    DropdownSetting(
        title = "角色",
        desc = null,
        options = roles.mapIndexed { index, (label, _) -> DdOption(index, label) } +
            DdOption(roles.size, "自定义（当前文本）"),
        current = selectedRole,
        onChange = { index ->
            selectedRole = index
            if (index < roles.size) {
                prompt = roles[index].second
                state.editConfig { it.copy(systemPrompt = prompt) }
                state.flash("已套用「${roles[index].first}」")
            }
        },
    )

    TextSetting(
        label = "系统提示词",
        value = prompt,
        onChange = { prompt = it },
        placeholder = "定义这个智能体是谁、怎么做事",
        singleLine = false,
        modifier = Modifier.heightIn(min = 140.dp),
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("保存", primary = true) {
            state.editConfig { it.copy(systemPrompt = prompt.trim()) }
            state.flash("系统提示已保存")
        }
        PillButton("恢复默认") {
            prompt = b.ai.desktop.core.config.DEFAULT_SYSTEM_PROMPT
            state.editConfig { it.copy(systemPrompt = prompt) }
            selectedRole = 0
            state.flash("已恢复默认提示词")
        }
    }
    Text(
        "这段提示是每轮对话的开场白；「注入与输出」里的时间/工作区/文风和长期记忆会拼在它后面一起下发。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}


/** 定时任务：到点把提示词发进当前会话，模型照常执行（含工具） */
@Composable
fun ExtSchedulePage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("任务", "到点自动发送；应用必须开着才会触发，错过了不追补")
    if (state.tasks.isEmpty()) {
        Text("还没有任务", fontSize = 11.sp, color = colors.onSurfaceVariant)
    }
    state.tasks.forEach { task ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.label.ifBlank { "未命名任务" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${task.time} · ${if (task.repeat == "daily") "每天" else "仅今天"}" +
                        (if (task.lastRunDate.isNotBlank()) " · 上次 ${task.lastRunDate}" else ""),
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = task.enabled,
                    onCheckedChange = { value ->
                        state.saveTasks(state.tasks.map { if (it.id == task.id) it.copy(enabled = value) else it })
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            }
            Text(
                task.prompt,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("立即执行") { state.runTaskNow(task) }
                PillButton("删除") { state.saveTasks(state.tasks.filter { it.id != task.id }) }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("新建任务")
    var label by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("09:00") }
    var daily by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }
    TextSetting("名称", label, { label = it }, "例如：早上整理工作区")
    TextSetting("时间 HH:mm", time, { time = it }, "09:00")
    ToggleSetting(
        title = "每天执行",
        desc = if (daily) "每天都跑" else "只跑今天这一次，跑完自动停",
        checked = daily,
        onChange = { daily = it },
    )
    TextSetting("提示词", prompt, { prompt = it }, "到点要发给模型的完整提示词", singleLine = false)
    PillButton("添加任务", primary = true) {
        val t = time.trim()
        val valid = runCatching { java.time.LocalTime.parse(t) }.isSuccess
        when {
            prompt.isBlank() -> state.flash("提示词不能为空")
            !valid -> state.flash("时间格式要用 HH:mm，比如 09:00")
            else -> {
                state.saveTasks(
                    state.tasks + ScheduledTask(
                        label = label.trim(),
                        time = t,
                        prompt = prompt.trim(),
                        repeat = if (daily) "daily" else "once",
                    ),
                )
                label = ""
                prompt = ""
                state.flash("任务已添加")
            }
        }
    }
    Text(
        "任务在检查器里排队触发；执行链路与手动发送完全一致——沙箱、工具确认、预算卡都照常起作用。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}


/** MCP 服务：本地或远程的 stdio MCP 服务器，连上后工具直接进工具表 */
@Composable
fun ExtMcpPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    SectionTitle("已配置的服务", "连接方式：stdio 子进程；JSON-RPC 按行分帧")
    if (state.config.mcpServers.isEmpty()) {
        Text(
            "还没有服务。填一条命令（例如 npx、node、python 脚本），连上后它的工具会出现在模型面前。",
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = colors.onSurfaceVariant,
        )
    }
    state.config.mcpServers.forEach { cfg ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cfg.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${cfg.command} ${cfg.args}".trim(),
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = cfg.enabled,
                    onCheckedChange = { value ->
                        state.editConfig { c ->
                            c.copy(mcpServers = c.mcpServers.map { if (it.id == cfg.id) it.copy(enabled = value) else it })
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("连接测试") {
                    scope.launch {
                        val result = runCatching {
                            val tools = b.ai.desktop.core.mcp.McpManager.session(cfg, forceReconnect = true).listTools()
                            "连上了：${tools.size} 个工具 · ${tools.take(5).joinToString(", ") { it.name }}"
                        }.getOrElse { "连接失败：${it.message}" }
                        state.flash(result)
                    }
                }
                PillButton("删除") {
                    state.editConfig { c -> c.copy(mcpServers = c.mcpServers.filter { it.id != cfg.id }) }
                    state.flash("已删除（会话在下次使用时清理）")
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("添加服务")
    var label by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    TextSetting("名称", label, { label = it }, "例如：本地文件服务")
    TextSetting("启动命令", command, { command = it }, "npx / node / python")
    TextSetting("参数", args, { args = it }, "-y @modelcontextprotocol/server-filesystem D://workspace")
    PillButton("添加", primary = true) {
        when {
            command.isBlank() -> state.flash("启动命令不能为空")
            else -> {
                val cfg = b.ai.desktop.core.mcp.McpServerCfg(
                    label = label.trim(),
                    command = command.trim(),
                    args = args.trim(),
                )
                state.editConfig { c -> c.copy(mcpServers = c.mcpServers + cfg) }
                label = ""
                command = ""
                args = ""
                state.flash("已添加，点「连接测试」验证")
            }
        }
    }
    Text(
        "提示：npx 类服务第一次启动要下包，可能要等十几秒；参数里带空格的路径用引号包起来。" +
            "连上后每个 MCP 工具都以 mcp_ 前缀出现在工具表里，与本地工具、插件工具同名时后者优先。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}


/** 隐私与加密：凭据落盘加密（AES-GCM，机器绑定） */
@Composable
fun ExtPrivacyPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("凭据加密", "API Key 落盘前用 AES-256-GCM 加密，前缀 enc:v1:")
    ToggleSetting(
        title = "加密凭据",
        desc = if (state.config.encryptSecrets) {
            "config.json 里的 Key 已是密文；内存中始终明文供请求使用"
        } else {
            "config.json 里的 Key 是明文；打开后下一次保存即加密"
        },
        checked = state.config.encryptSecrets,
        onChange = { value ->
            state.editConfig {
                it.copy(
                    encryptSecrets = value,
                    vaultSalt = if (it.vaultSalt.isBlank()) b.ai.desktop.core.security.Vault.newSalt() else it.vaultSalt,
                )
            }
            state.flash(if (value) "已开启：下次保存配置时加密生效" else "已关闭：下次保存时改回明文")
        },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("绑定设备", "密钥里要不要混入机器标识；默认关")
    ToggleSetting(
        title = "绑定本机（MachineGuid）",
        desc = if (state.config.vaultBindDevice) {
            "密钥 = 盐 + 机器标识：拷走 config.json 到别的机器解不开，换机/重装系统需重填 Key"
        } else {
            "密钥只用盐派生：文件照样是密文，但备份拷到别的机器仍可解开（配置可随身带）"
        },
        checked = state.config.vaultBindDevice,
        onChange = { value ->
            state.editConfig { it.copy(vaultBindDevice = value) }
            b.ai.desktop.core.auth.GitHubAuth.bindDevice = value
            // 下一次保存会用新模式重封；历史密文两种派生都能读，不会读不回来
            state.flash(if (value) "已绑定本机：下次保存时按新密钥重封" else "已取消绑定：下次保存时按盐重封")
        },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("说明", "哪些数据在哪里、怎么保护")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        val machine = b.ai.desktop.core.security.Vault.machineKey()
        Text(
            "· 加密密钥由「盐" + (if (state.config.vaultBindDevice) " + 机器标识" else "") + "」派生" +
                (if (state.config.vaultBindDevice) "，机器标识指纹：${machine.take(8)}…" else "") +
                "\n· GitHub token 始终加密存 auth.json（与本页开关无关）；是否绑机器跟随上面的开关" +
                "\n· " + (if (state.config.vaultBindDevice) "拷走 config.json / auth.json 到别的机器解不开" else "未绑定机器：配置备份拷到别的机器仍可解开") +
                "\n· 会话记录仍是明文本地文件；想彻底清掉用「数据与备份」里的清空" +
                "\n· 上报只发哈希设备码与计数，不带任何原始硬件标识与对话内容",
            fontSize = 10.sp,
            lineHeight = 17.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 联网搜索：搜索平台（Tavily / Bing / Google）+ 网关原生搜索默认开关，一页管完 */
@Composable
fun ExtSearchPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val providers = b.ai.desktop.core.search.WebSearch.providers
    val current = providers.indexOfFirst { it.id == state.config.searchProvider }

    SectionTitle("客户端搜索平台", "选一个平台就挂 web_search 工具；模型会自己决定什么时候搜")
    DropdownSetting(
        title = "搜索平台",
        desc = providers.firstOrNull { it.id == state.config.searchProvider }?.hint
            ?: "未选择：web_search 不进工具表；只走网关的原生搜索",
        options = listOf(DdOption(-1, "不启用")) + providers.mapIndexed { i, p -> DdOption(i, p.label) },
        current = current,
        onChange = { index ->
            val id = providers.getOrNull(index)?.id.orEmpty()
            state.editConfig { it.copy(searchProvider = id) }
            state.flash(if (id.isBlank()) "web_search 已移除" else "已选 ${providers[index].label}，配置齐了立即生效")
        },
    )

    // 动态配置项：Tavily 要 Key；Google 要 Key + cx；Bing 免 Key
    val provider = providers.firstOrNull { it.id == state.config.searchProvider }
    if (provider != null && provider.fields.isNotEmpty()) {
        provider.fields.forEach { field ->
            val stored = when (provider.id) {
                "google" -> if (field.id == "key") state.config.googleSearchKey else state.config.googleSearchCx
                else -> state.config.tavilyKey
            }
            var value by remember(provider.id, field.id, stored) { mutableStateOf(stored) }
            TextSetting(
                "${provider.label} ${field.label}",
                value,
                { value = it },
                field.hint,
                password = field.id == "key",
            )
            PillButton("保存 ${field.label}", primary = true) {
                val trimmed = value.trim()
                state.editConfig {
                    when (provider.id) {
                        "google" ->
                            if (field.id == "key") it.copy(googleSearchKey = trimmed) else it.copy(googleSearchCx = trimmed)
                        else -> it.copy(tavilyKey = trimmed)
                    }
                }
                state.flash(
                    if (trimmed.isBlank()) "已清空，web_search 已移除"
                    else "已保存：平台配置齐了，web_search 立即生效",
                )
            }
        }
    } else if (provider != null) {
        Text(
            "这个平台免 Key，选上就能用；被限流时换个平台或稍后再试。",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
        )
    }

    DropdownSetting(
        title = "检索范围",
        desc = "仅 Tavily 支持；其他平台忽略",
        options = listOf(DdOption(0, "general 综合网页"), DdOption(1, "news 新闻")),
        current = if (state.config.searchTopic == "news") 1 else 0,
        onChange = { index ->
            state.editConfig { it.copy(searchTopic = if (index == 1) "news" else "general") }
        },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("网关原生搜索", "走服务端的 web_search_options，由端点决定支不支持")
    ToggleSetting(
        title = "新会话默认开启",
        desc = if (state.config.defaultNativeSearch) "新建会话的联网开关默认打开" else "默认关闭，需要时点输入区上方的联网胶囊",
        checked = state.config.defaultNativeSearch,
        onChange = { value -> state.editConfig { it.copy(defaultNativeSearch = value) } },
    )
    Text(
        "与「原生搜索」的关系：原生搜索由网关在服务端执行；web_search 是本机直连所选平台。" +
            "两个都开着时模型会挑合适的。Key 存 config，配合凭据加密一起落盘。" +
            "\n端点不支持原生搜索时（返回 400/422 说字段不认识），客户端会剔除重发一次，退化成普通回答。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}


/** 备份与恢复：把 config / sessions / tasks / 记忆打包成 zip，恢复时覆盖后重启生效 */
@Composable
fun ExtBackupPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }

    SectionTitle("备份", "打包 config、会话、定时任务；auth.json（GitHub）与工作区文件不打进来")
    PillButton("导出备份 (.zip)", primary = true) {
        scope.launch {
            message = runCatching {
                val dialog = java.awt.FileDialog(null as? java.awt.Frame ?: java.awt.Frame(), "保存备份", java.awt.FileDialog.SAVE).apply {
                    file = "baicodex-backup.zip"
                }
                dialog.isVisible = true
                val dir = dialog.directory ?: return@runCatching "已取消"
                val target = java.io.File(dir, dialog.file ?: "baicodex-backup.zip")
                val files = listOf("config.json", "sessions.json", "tasks.json")
                java.util.zip.ZipOutputStream(target.outputStream().buffered()).use { zip ->
                    files.forEach { name ->
                        val f = java.io.File(b.ai.desktop.core.config.ConfigStore.appDir(), name)
                        if (f.exists()) {
                            zip.putNextEntry(java.util.zip.ZipEntry(name))
                            zip.write(f.readBytes())
                            zip.closeEntry()
                        }
                    }
                }
                "备份完成：${target.absolutePath}"
            }.getOrElse { "备份失败：${it.message}" }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("恢复", "用备份覆盖当前数据；覆盖后重启应用生效")
    PillButton("选择备份并恢复") {
        scope.launch {
            message = runCatching {
                val dialog = java.awt.FileDialog(null as? java.awt.Frame ?: java.awt.Frame(), "选择备份", java.awt.FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.directory?.let { java.io.File(it, dialog.file ?: "") } ?: return@runCatching "已取消"
                if (!file.exists()) return@runCatching "已取消"
                var restored = 0
                java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.substringAfterLast('/')
                        if (name in listOf("config.json", "sessions.json", "tasks.json")) {
                            java.io.File(b.ai.desktop.core.config.ConfigStore.appDir(), name)
                                .writeBytes(zip.readBytes())
                            restored++
                        }
                        entry = zip.nextEntry
                    }
                }
                if (restored == 0) "备份里没有可恢复的数据" else "已恢复 $restored 个文件，重启应用后生效"
            }.getOrElse { "恢复失败：${it.message}" }
        }
    }
    if (message.isNotBlank()) {
        Text(message, fontSize = 10.sp, color = colors.primary, modifier = Modifier.padding(top = 8.dp))
    }
    Text(
        "备份里可能包含模型 Key（开启「隐私与加密」后是密文）：把备份发给别人前想清楚。" +
            "\nGitHub 登录态（auth.json）刻意不打包；恢复后需要重新登录。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}


/** adb devices 为空时给一份完整的排查清单（按命中率排序） */
private const val NOT_DETECTED_HINT =
    "排查清单（按命中率排序）：\n" +
        "1. 手机是「仅充电」模式 → 通知栏把 USB 用途改成「传输文件 / MTP」\n" +
        "2. 没开 USB 调试 → 设置 → 开发者选项 → 打开「USB 调试」（没有开发者选项就先连点 7 次版本号）\n" +
        "3. 手机上弹过「允许 USB 调试吗」但没点 → 解锁手机勾「始终允许」再确定\n" +
        "4. 数据线是纯充电线 → 换一根能传数据的线\n" +
        "5. 以上都试过仍为空 → 点「重启 adb 服务」后重插"

/** 手机互联（仅桌面端）：内置 ADB 状态、设备清单、无线连接、局域网探测 */
@Composable
fun ExtAdbPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    SectionTitle("启用", "关掉后 adb 工具不挂进模型工具表（不影响已开的会话）")
    ToggleSetting(
        title = "手机互联（ADB）",
        desc = "设备清单 / shell / 文件双通道传输 / 安装卸载 / 截图 / 日志 / 电量，14 种操作",
        checked = state.config.adbEnabled,
        onChange = { value -> state.editConfig { it.copy(adbEnabled = value) } },
    )
    ToggleSetting(
        title = "双通道传输",
        desc = "同一台手机同时被 USB 和内网看到时，文件切两半并发传，到手机再合并；失败自动回退单通道",
        checked = state.config.adbDualChannel,
        onChange = { value -> state.editConfig { it.copy(adbDualChannel = value) } },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("ADB 状态")
    var adbInfo by remember(state.config.adbPath) { mutableStateOf("检测中…") }
    androidx.compose.runtime.LaunchedEffect(state.config.adbPath) {
        val path = b.ai.desktop.core.adb.ADB.locate(state.config.adbPath)
        adbInfo = if (path == null) {
            "没找到 adb.exe：填下面的路径，或把 platform-tools 放到 C:////AndroidSdk////platform-tools"
        } else {
            runCatching { "$path\n" + b.ai.desktop.core.adb.ADB.run(listOf("version"), path).lineSequence().first() }
                .getOrElse { "$path（版本查询失败）" }
        }
    }
    Text(adbInfo, fontSize = 10.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
    var adbPath by remember(state.config.adbPath) { mutableStateOf(state.config.adbPath) }
    TextSetting("adb.exe 路径", adbPath, { adbPath = it }, "留空自动定位；或填完整路径")
    PillButton("保存路径", primary = true) {
        state.editConfig { it.copy(adbPath = adbPath.trim()) }
        state.flash("adb 路径已保存")
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("设备与连接", "插上就自动刷新；读不到按下面的排查清单走")
    var devices by remember { mutableStateOf("还没查询") }
    var deviceHint by remember { mutableStateOf("") }

    fun renderDevices() {
        val path = b.ai.desktop.core.adb.ADB.locate(state.config.adbPath)
        if (path == null) {
            devices = "adb 不可用：没找到 adb.exe"
            deviceHint = "把 platform-tools 解压到 C:\\AndroidSdk\\platform-tools，或在上面填 adb.exe 完整路径"
            return
        }
        runCatching { b.ai.desktop.core.adb.ADB.startServer(path) }
        val list = runCatching { b.ai.desktop.core.adb.ADB.devices(path) }
            .getOrElse { devices = "查询失败：${it.message}"; deviceHint = ""; return }
        devices = if (list.isEmpty()) {
            deviceHint = NOT_DETECTED_HINT
            "没有设备（adb devices 为空）"
        } else {
            deviceHint = list.filter { it.state != "device" }
                .joinToString("\n") { "· ${it.serial}：${b.ai.desktop.core.adb.ADB.stateHint(it.state)}" }
            list.joinToString("\n") {
                "· ${it.serial}  ${b.ai.desktop.core.adb.ADB.stateHint(it.state).take(6)}  ${it.model}  " +
                    "[${if (it.serial.contains(":")) "内网" else "USB"}]"
            }
        }
    }

    // 进页面自动查一次；保存 adb 路径后也跟着刷新
    androidx.compose.runtime.LaunchedEffect(state.config.adbPath) { renderDevices() }
    Text(devices, fontSize = 10.sp, lineHeight = 16.sp, color = colors.onSurface)
    if (deviceHint.isNotBlank()) {
        Text(
            deviceHint,
            fontSize = 9.sp,
            lineHeight = 15.sp,
            color = colors.error.takeIf { devices.contains("没有设备") || devices.contains("查询失败") }
                ?: colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        PillButton("刷新设备") { scope.launch { renderDevices() } }
        PillButton("重启 adb 服务") {
            scope.launch {
                val path = b.ai.desktop.core.adb.ADB.locate(state.config.adbPath)
                state.flash(
                    if (path == null) "adb 不可用"
                    else runCatching {
                        b.ai.desktop.core.adb.ADB.run(listOf("kill-server"), path)
                        b.ai.desktop.core.adb.ADB.startServer(path)
                        renderDevices()
                        "adb 服务已重启"
                    }.getOrElse { "失败：${it.message}" },
                )
            }
        }
        PillButton("切无线模式") {
            scope.launch {
                val path = b.ai.desktop.core.adb.ADB.locate(state.config.adbPath)
                if (path == null) {
                    state.flash("adb 不可用")
                } else {
                    state.flash(runCatching { b.ai.desktop.core.adb.ADB.enableTcpip(5555, path) }
                        .getOrElse { "失败：${it.message}" })
                }
            }
        }
    }

    var ipPort by remember { mutableStateOf("") }
    TextSetting("无线目标", ipPort, { ipPort = it }, "手机IP:5555；留空则自动扫本机网段")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("无线连接", primary = true) {
            scope.launch {
                val path = b.ai.desktop.core.adb.ADB.locate(state.config.adbPath)
                if (path == null) {
                    state.flash("adb 不可用")
                } else {
                    state.flash(runCatching {
                        val target = ipPort.trim().ifBlank {
                            val prefix = b.ai.desktop.core.adb.ADB.localSubnetPrefix()
                                ?: return@runCatching "拿不到本机网段，请手填 IP"
                            val hits = b.ai.desktop.core.adb.ADB.scanSubnet(prefix)
                            if (hits.isEmpty()) return@runCatching "网段 $prefix.* 没探测到 5555 端口"
                            hits.first() + ":5555"
                        }
                        val t = if (target.contains(":")) target else "$target:5555"
                        b.ai.desktop.core.adb.ADB.connect(t, path)
                    }.getOrElse { "失败：${it.message}" })
                }
            }
        }
        PillButton("扫描网段") {
            scope.launch {
                val prefix = b.ai.desktop.core.adb.ADB.localSubnetPrefix()
                if (prefix == null) {
                    state.flash("拿不到本机网段")
                } else {
                    state.flash("正在扫 $prefix.*:5555 …")
                    val hits = b.ai.desktop.core.adb.ADB.scanSubnet(prefix)
                    state.flash(if (hits.isEmpty()) "$prefix.* 没有开 5555 的设备" else "发现：${hits.joinToString("、")}")
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    Text(
        "用法：USB 插上手机（传输文件模式 + USB 调试授权），设备列表出「device 已授权」即可用；" +
            "或 USB 连接后点「切无线模式」再连内网。" +
            "\nUSB 和内网同时在列时，模型传文件会自动走双通道分块。" +
            "\n模型侧工具名是 adb，14 种操作：devices / connect / disconnect / tcpip / shell / ls / cat / push / pull / install / uninstall / screencap / logcat / battery。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
    )
}
