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
 * 应用状态：真实会话（落盘）+ 模型配置 + 输入草稿；发送时驱动 Agent 内核。
 */

package b.ai.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import b.ai.desktop.core.approval.ApprovalDecision
import b.ai.desktop.core.approval.ApprovalMode
import b.ai.desktop.core.approval.ApprovalRequest
import b.ai.desktop.core.approval.ToolGate
import b.ai.desktop.core.approval.ToolLabels
import b.ai.desktop.core.auth.GhAccount
import b.ai.desktop.core.sandbox.CommandGuard
import b.ai.desktop.core.sandbox.CommandVerdict
import b.ai.desktop.core.sandbox.Risk
import b.ai.desktop.core.sandbox.SandboxMode
import b.ai.desktop.core.sandbox.SandboxPolicy
import b.ai.desktop.core.tool.ToolCall
import b.ai.desktop.core.balance.Balance
import b.ai.desktop.core.balance.BalanceWatch
import b.ai.desktop.core.device.DeviceCode
import b.ai.desktop.core.device.DeviceInfo
import b.ai.desktop.core.auth.GitHubAuth
import b.ai.desktop.core.config.AppConfig
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.i18n.I18n
import b.ai.desktop.core.schedule.ScheduledTask
import b.ai.desktop.core.audio.AudioRecorder
import b.ai.desktop.core.audio.SttClient
import b.ai.desktop.core.schedule.Scheduler
import b.ai.desktop.core.config.ModelProfile
import b.ai.desktop.core.export.exportAllSessions
import b.ai.desktop.core.export.exportSessionMarkdown
import b.ai.desktop.core.export.sessionMarkdown
import b.ai.desktop.core.kernel.AgentKernel
import b.ai.desktop.core.model.AttachmentRef
import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.model.RunOptions
import b.ai.desktop.core.model.Session
import b.ai.desktop.core.net.OpenAiChatClient
import b.ai.desktop.core.plugin.PluginInfo
import b.ai.desktop.core.plugin.PluginStore
import b.ai.desktop.core.store.SessionStore
import b.ai.desktop.core.tool.defaultToolRegistry
import b.ai.desktop.core.usage.ReportState
import b.ai.desktop.core.usage.UsageReporter
import b.ai.desktop.core.util.CtxStat
import b.ai.desktop.core.util.Speech
import b.ai.desktop.core.util.copyToClipboard
import b.ai.desktop.core.util.fmtTokens
import b.ai.desktop.data.Attachment
import b.ai.desktop.data.AttachmentKind
import b.ai.desktop.core.workspace.WorkspaceStat
import b.ai.desktop.core.workspace.WsStat
import b.ai.desktop.theme.Accent
import b.ai.desktop.theme.UiMetrics
import b.ai.desktop.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AppState {
    private val sessionStore = SessionStore()

    // ---- 配置 ----
    var config by mutableStateOf(ConfigStore.load())
        private set

    // ---- 会话 ----
    var sessions by mutableStateOf(pinnedFirst(sessionStore.load()))
        private set
    var currentId by mutableStateOf(sessions.firstOrNull()?.id ?: "")
        private set

    /** 正在就地重命名的会话 id（null = 没有） */
    var renamingId by mutableStateOf<String?>(null)

    // ---- 输入 ----
    var draft by mutableStateOf("")
    val attachments = mutableStateListOf<Attachment>()

    /** 按会话记住的草稿（开关在「对话 → 注入与输出」） */
    private val draftsBySession = mutableMapOf<String, String>()

    // ---- 插件 ----
    /** 本机插件清单（<appDir>/plugins 下每个目录一个） */
    var plugins by mutableStateOf(PluginStore.list())
        private set

    // ---- Git 账号 ----
    /** GitHub 登录态（token 存 auth.json，界面只读这个快照） */
    var gitAccount by mutableStateOf(GitHubAuth.account)
        private set

    // ---- 界面 ----
    /** 主题模式持久化在 config.themeMode（之前只在内存里，重启就丢） */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == config.themeMode } ?: ThemeMode.SYSTEM
        set(value) {
            editConfig { it.copy(themeMode = value.name) }
        }

    /** 主题色（个性化页可换） */
    val accent: Accent get() = Accent.byId(config.accent)

    /** 背景：纯色 / 微渐变（AppContent 读） */
    val backdrop get() = config.backdrop

    /** 把 config 里的外观度量刷进全局对象（圆角/缩放/字体是状态，改了即生效） */
    fun applyUiMetrics() {
        UiMetrics.apply(
            bubbleCorner = config.bubbleCorner,
            cardCorner = config.cardCorner,
            dialogCorner = config.dialogCorner,
            uiScale = config.uiScale,
            fontId = config.fontFamily,
        )
    }
    var showSettings by mutableStateOf(false)
    var showModelSheet by mutableStateOf(false)
    /** 打开设置时落在哪个分类（SettingsGroup.name）；预算卡里点「上下文设置」会指到 CHAT */
    var settingsGroup by mutableStateOf<String?>(null)
    /** 打开设置时直接落到哪个条目（SettingsPage.id）；空 = 只选分类，右侧等用户点条目 */
    var settingsPage by mutableStateOf<String?>(null)
    /** 插件页（聊天区的一级页面，不再放设置里） */
    var showPlugins by mutableStateOf(false)
    /** 工作区页（聊天区的一级页面，对齐手机端顶栏的绑定入口） */
    var showWorkspace by mutableStateOf(false)

    // ---- 运行态 ----
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 一次性轻提示（复制、置顶、删除等操作的结果），由界面定时清掉 */
    var toast by mutableStateOf<String?>(null)
        private set

    // ---- 工具同意 ----
    /** 正在等用户点头的调用；非空时聊天区弹确认卡片，内核挂起在这里 */
    var pendingApproval by mutableStateOf<ApprovalRequest?>(null)
        private set

    /** 内核侧等的那张「票」；用户点了按钮就 complete */
    private var approvalTicket: kotlinx.coroutines.CompletableDeferred<ApprovalDecision>? = null

    /** 等太久（比如窗口被关掉）就按拒绝处理，免得协程永远挂着 */
    private val approvalTimeoutMs = 5 * 60 * 1000L

    init {
        // GitHub token 始终加密存；是否绑定机器跟随设置里的开关（读到密文前就得定好）
        b.ai.desktop.core.auth.GitHubAuth.bindDevice = config.vaultBindDevice
        GitHubAuth.load()
        gitAccount = GitHubAuth.account
        // 已经绑过工作区的，把它补进「最近用过」，不然那一栏一开始总是空的
        val current = config.workspace
        if (current.isNotBlank() && current !in config.recentWorkspaces) {
            editConfig { it.copy(recentWorkspaces = (listOf(current) + it.recentWorkspaces).take(8)) }
        }
        refreshOptimizerBackend()
        I18n.init(config.lang)
        applyUiMetrics()
        // 定时任务检查器：到点把提示词投进当前会话（回调在后台线程，切到 UI 线程再动状态）
        Scheduler.start { task ->
            java.awt.EventQueue.invokeLater {
                scheduledPrompt = task.prompt
                toast = "定时任务：「${task.label}」已触发"
            }
        }

        // 设备码首次要跑一次 WMI，放后台；顺便恢复上次上报结果
        UsageReporter.loadState()
        DeviceCode.loadAsync()
        // 与手机端一致：进程起来上报一次（接口是累加语义，只发一次、失败静默）
        UsageReporter.reportOnceAsync(config.usageReportEnabled, usageEndpoint())
        // 首次运行记一笔「安装」：install.flag 存在就不再发（卸载清理数据后重装会重新记）
        UsageReporter.reportInstallOnceAsync(config.usageReportEnabled)
    }

    val models: List<String> get() = config.profiles.map { it.label }
    val modelName: String get() = config.activeProfile?.label ?: "未配置模型"
    val modelIndex: Int
        get() = config.profiles.indexOfFirst { it.id == config.activeProfile?.id }.coerceAtLeast(0)

    val currentSession: Session? get() = sessions.firstOrNull { it.id == currentId }
    val messages: List<ChatMessage> get() = currentSession?.messages.orEmpty()
    val currentTitle: String get() = currentSession?.title ?: "新会话"
    val canSend: Boolean get() = !busy && (draft.isNotBlank() || attachments.isNotEmpty())

    // ---------------------------------------------------------------- 配置

    fun editConfig(block: (AppConfig) -> AppConfig) {
        config = block(config)
        ConfigStore.save(config)
    }

    /** 保存某个模型配置 */
    fun updateProfile(
        id: String,
        name: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        toolsEnabled: Boolean,
        temperature: Double,
        maxTokens: Int,
        topP: Double,
        timeoutSec: Int,
        supportsThinking: Boolean = true,
        supportsNativeSearch: Boolean = true,
    ) {
        editConfig { current ->
            current.copy(
                profiles = current.profiles.map {
                    if (it.id != id) it
                    else it.copy(
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        toolsEnabled = toolsEnabled,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        topP = topP,
                        timeoutSec = timeoutSec,
                        supportsThinking = supportsThinking,
                        supportsNativeSearch = supportsNativeSearch,
                    )
                },
            )
        }
    }

    fun deleteProfile(id: String) {
        editConfig { current ->
            val left = current.profiles.filterNot { it.id == id }
            current.copy(
                profiles = left,
                activeProfileId = if (current.activeProfileId == id) {
                    left.firstOrNull()?.id.orEmpty()
                } else {
                    current.activeProfileId
                },
            )
        }
    }

    fun selectProfile(id: String) {
        editConfig { it.copy(activeProfileId = id) }
    }

    fun addProfile() {
        val created = ModelProfile(name = "新模型")
        editConfig { it.copy(profiles = it.profiles + created, activeProfileId = created.id) }
    }

    fun selectModel(index: Int) {
        val profile = config.profiles.getOrNull(index) ?: return
        editConfig { it.copy(activeProfileId = profile.id) }
        showModelSheet = false
    }

    fun toggleModelSheet() {
        showModelSheet = !showModelSheet
    }

    fun closeModelSheet() {
        showModelSheet = false
    }

    // ---------------------------------------------------------------- 语音识别

    /** idle / recording / transcribing */
    var sttState by mutableStateOf("idle")
        private set

    private var recorder: AudioRecorder.Session? = null

    /** 麦克风按钮：开录 → 停录并转写 → 文本接进草稿 */
    fun toggleStt() {
        when (sttState) {
            "idle" -> {
                runCatching {
                    recorder = AudioRecorder.Session.open().also { it.start() }
                    sttState = "recording"
                }.onFailure {
                    sttState = "idle"
                    toast = "录音失败：${it.message}"
                }
            }
            "recording" -> {
                val session = recorder
                recorder = null
                sttState = "transcribing"
                Thread {
                    val result = runCatching {
                        val wav = session?.stop() ?: error("录音会话已丢失")
                        val profile = config.activeProfile
                        val endpoint = config.sttEndpoint.ifBlank { profile?.baseUrl.orEmpty() }
                        SttClient.transcribe(
                            baseUrl = endpoint,
                            apiKey = profile?.apiKey.orEmpty(),
                            model = config.sttModel,
                            language = config.sttLanguage,
                            wav = wav,
                        )
                    }.getOrElse { SttClient.Result(false, it.message ?: it.toString()) }
                    java.awt.EventQueue.invokeLater {
                        sttState = "idle"
                        if (result.ok) {
                            draft = (draft + if (draft.isBlank()) "" else " ") + result.text
                            toast = "已转写 ${result.text.length} 字"
                        } else {
                            toast = "转写失败：${result.text.take(120)}"
                        }
                    }
                }.apply { isDaemon = true }.start()
            }
        }
    }

    // ---------------------------------------------------------------- 定时任务

    /** 到点的任务：ChatScreen 的 LaunchedEffect 看到它就把提示词发出去（UI 线程） */
    var scheduledPrompt by mutableStateOf<String?>(null)
        private set

    /** 任务快照（设置页渲染） */
    var tasks by mutableStateOf(Scheduler.load())
        private set

    fun saveTasks(list: List<ScheduledTask>) {
        Scheduler.save(list)
        tasks = list
    }

    fun clearScheduled() {
        scheduledPrompt = null
    }

    /** 设置页「立即执行」：标记今天已跑 + 直接投递 */
    fun runTaskNow(task: ScheduledTask) {
        Scheduler.markRanToday(task.id)
        tasks = Scheduler.load()
        scheduledPrompt = task.prompt
        flash("「${task.label}」已投递")
    }

    // ---------------------------------------------------------------- 引导

    fun finishOnboarding() {
        editConfig { it.copy(onboardingDone = true) }
    }

    /** 关于页里的「重看引导」 */
    fun replayOnboarding() {
        editConfig { it.copy(onboardingDone = false) }
        showSettings = false
        settingsGroup = null
        settingsPage = null
    }

    // ---------------------------------------------------------------- 沙箱与工具同意

    /** 用户中途点了「停止本轮」：剩余工具一律拒，直到这轮跑完 */
    private var roundStopped = false

    val sandboxMode: SandboxMode get() = SandboxMode.of(config.sandboxMode)
    val approvalMode: ApprovalMode get() = ApprovalMode.of(config.approvalMode)

    fun setSandboxMode(mode: SandboxMode) {
        editConfig { it.copy(sandboxMode = mode.id) }
        flash("沙箱已切到「${mode.label}」")
    }

    fun setApprovalMode(mode: ApprovalMode) {
        editConfig { it.copy(approvalMode = mode.id) }
        flash("工具确认：${mode.label}")
    }

    fun setCommandGuard(enabled: Boolean) {
        editConfig { it.copy(commandGuardEnabled = enabled) }
        flash(if (enabled) "危险命令拦截已开启" else "危险命令拦截已关闭")
    }

    /** 撤销某个工具的永久放行 */
    fun revokeAlwaysAllow(tool: String) {
        editConfig { it.copy(alwaysAllowTools = it.alwaysAllowTools - tool) }
        flash("「${ToolLabels.titleOf(tool)}」恢复为需要确认")
    }

    fun clearAlwaysAllow() {
        editConfig { it.copy(alwaysAllowTools = emptyList()) }
        flash("已清空永久放行名单")
    }

    /**
     * 内核执行每个工具前的闸门。
     * 顺序很重要：命令守卫（硬拦）→ 永久放行 → 策略 → 风险等级。
     */
    private fun buildGate(): (ToolCall) -> ToolGate = gate@{ call ->
        if (roundStopped) return@gate ToolGate.Reject("本轮已被用户终止")

        val mode = SandboxMode.of(config.sandboxMode)
        val policy = ApprovalMode.of(config.approvalMode)
        val risk = SandboxPolicy.riskOf(call.name, call.args, mode)

        // 命令守卫在策略之上：系统级破坏性操作不看「全自动」，一律拦
        if (call.name == "run_command") {
            val command = (call.args["command"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content.orEmpty()
            when (val verdict = CommandGuard.check(command)) {
                is CommandVerdict.Blocked -> {
                    return@gate if (config.commandGuardEnabled) {
                        ToolGate.Reject(verdict.reason)
                    } else if (policy == ApprovalMode.TRUST) {
                        ToolGate.Pass
                    } else {
                        ToolGate.Ask(Risk.HIGH, "${verdict.reason}（拦截已关闭，由你判断）")
                    }
                }
                is CommandVerdict.Confirm -> {
                    if (policy != ApprovalMode.TRUST) {
                        return@gate ToolGate.Ask(Risk.HIGH, "命令守卫：${verdict.reason}")
                    }
                }
                is CommandVerdict.Safe -> Unit
            }
        }

        if (call.name in config.alwaysAllowTools) return@gate ToolGate.Pass

        when (policy) {
            ApprovalMode.TRUST -> ToolGate.Pass
            ApprovalMode.ALWAYS -> ToolGate.Ask(risk, "策略：每次都确认")
            ApprovalMode.SMART ->
                if (risk == Risk.LOW) ToolGate.Pass
                else ToolGate.Ask(risk, riskReason(call, risk))
        }
    }

    private fun riskReason(call: ToolCall, risk: Risk): String {
        val base = when (call.name) {
            "write_file" -> "会整体覆盖目标文件（不存在则新建）"
            "edit_file" -> "会就地修改文件内容"
            "make_dir" -> "会在工作区里新建目录"
            "move_path" -> "会移动路径；目标存在时会被覆盖"
            "delete_path" -> "删除后无法恢复，目录会递归删除"
            "run_command" -> "命令在本机执行，可能超出工作区范围"
            else -> if (risk == Risk.HIGH) "高风险调用" else "会改动本机文件"
        }
        val exec = listOf("path", "to", "from")
            .mapNotNull { (call.args[it] as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .any { SandboxPolicy.isExecutableName(java.io.File(it).name) }
        return if (exec && call.name != "run_command") {
            "$base；目标是可执行 / 脚本文件，运行后可能改动系统"
        } else {
            base
        }
    }

    /** 内核挂起等这里返回；超时按拒绝处理 */
    private suspend fun askApproval(call: ToolCall, ask: ToolGate.Ask): ApprovalDecision {
        val ticket = kotlinx.coroutines.CompletableDeferred<ApprovalDecision>()
        approvalTicket = ticket
        pendingApproval = ApprovalRequest(
            toolName = call.name,
            title = ToolLabels.titleOf(call.name),
            summary = ToolLabels.summaryOf(call),
            detail = ToolLabels.detailOf(call),
            risk = ask.risk,
            reason = ask.reason,
            permanent = call.name in config.alwaysAllowTools,
        )
        val decision = kotlinx.coroutines.withTimeoutOrNull(approvalTimeoutMs) { ticket.await() }
            ?: ApprovalDecision.Deny("等待确认超时")
        pendingApproval = null
        approvalTicket = null
        return decision
    }

    /** 确认卡片上的按钮都走这里 */
    fun decideApproval(decision: ApprovalDecision) {
        val current = pendingApproval
        if (decision is ApprovalDecision.AllowAlways && current != null) {
            editConfig { it.copy(alwaysAllowTools = (it.alwaysAllowTools + current.toolName).distinct()) }
            flash("已放行「${current.title}」，以后不再询问")
        }
        approvalTicket?.complete(decision)
        approvalTicket = null
        pendingApproval = null
    }

    /** 「停止本轮」：当前这个拒掉，后面的也一并拒 */
    fun stopRound() {
        roundStopped = true
        decideApproval(ApprovalDecision.Deny("用户终止了本轮"))
        flash("已终止本轮工具调用")
    }

    // ---------------------------------------------------------------- 提示

    fun flash(text: String) {
        toast = text
    }

    fun clearToast() {
        toast = null
    }

    /** 用当前配置发一条最小请求，确认 baseUrl / Key / 模型名能通 */
    suspend fun testConnection() {
        val profile = config.activeProfile
        if (profile == null || !profile.ready) {
            flash("先填 baseUrl 和模型名")
            return
        }
        flash("正在测试连接…")
        val result = runCatching {
            OpenAiChatClient(profile).complete(
                messages = listOf(
                    kotlinx.serialization.json.buildJsonObject {
                        put("role", "user")
                        put("content", "只回复两个字：可用")
                    },
                ),
                tools = emptyList(),
                options = RunOptions(toolCalls = false, thinking = false, effort = "low"),
            )
        }
        result.fold(
            onSuccess = { reply ->
                val head = reply.text.replace('\n', ' ').take(24).ifBlank { "（空回复）" }
                flash("连接正常：$head")
            },
            onFailure = { error ->
                flash("连接失败：${error.message?.take(90) ?: "未知错误"}")
            },
        )
    }

    /** 恢复默认设置（模型连接与密钥保留） */
    fun resetSettings() {
        editConfig {
            it.copy(
                workspace = "",
                systemPrompt = b.ai.desktop.core.config.DEFAULT_SYSTEM_PROMPT,
                maxToolRounds = 8,
                sendOnEnter = true,
                autoScroll = true,
                fontSize = 13,
                codeFontSize = 12,
                lineHeight = 155,
                enabledTools = emptyList(),
                commandTimeoutSec = 30,
                historyLimit = 0,
                parallelToolCalls = true,
                showReasoning = true,
                reasoningExpanded = false,
                showTimestamp = true,
                confirmDelete = true,
                bubbleMaxWidth = 560,
                sidebarWidth = 268,
                compact = false,
                showMessageCount = true,
                ctxBudget = 32000,
                defaultThinking = false,
                defaultEffort = "medium",
                defaultMaxMode = false,
                defaultNativeSearch = false,
                injectContext = false,
                stylePrompt = "",
                showMeta = true,
                keepDrafts = true,
                autoTitle = true,
                inputPlaceholder = "",
                autoSpeak = false,
                speechRate = 0,
                memoryEnabled = false,
                memoryText = "",
                usageReportEnabled = true,
                usageEndpoint = "",
                balanceProfileId = "",
                balanceAlert = true,
                balanceThreshold = 5.0,
                optimizerProfileId = "",
                sandboxMode = "standard",
                approvalMode = "smart",
                alwaysAllowTools = emptyList(),
                commandGuardEnabled = true,
                accent = "blue",
                lang = "",
                themeMode = "system",
                toolchainPath = "",
                encryptSecrets = false,
                vaultSalt = "",
                vaultBindDevice = false,
                avatarOn = true,
                ctxCompress = false,
                ctxTotal = 128_000,
                ctxPct = 70,
                ctxKeep = 8,
                summaryPrompt = "",
                summaryLevel = "standard",
                summaryProfileId = "",
                searchProvider = "",
                googleSearchKey = "",
                googleSearchCx = "",
                bubbleCapsule = false,
                avatarUserFile = "",
                avatarAiFile = "",
                showUsage = true,
            )
        }
        themeMode = ThemeMode.SYSTEM
        flash("设置已恢复默认")
    }

    // ---------------------------------------------------------------- 插件

    /** 重扫插件目录（安装、卸载、改文件后调用） */
    fun reloadPlugins() {
        plugins = PluginStore.list()
        // 已删除的插件要从启用列表里摘掉，否则系统提示会带一段无主的文字
        val alive = plugins.map { it.id }.toSet()
        if (config.enabledPlugins.any { it !in alive }) {
            editConfig { it.copy(enabledPlugins = it.enabledPlugins.filter { id -> id in alive }) }
        }
    }

    /** 当前真正生效的插件：manifest 正常且被勾选 */
    val activePlugins: List<PluginInfo>
        get() = plugins.filter { it.usable && it.id in config.enabledPlugins }

    fun togglePlugin(id: String, enabled: Boolean) {
        editConfig {
            val next = if (enabled) (it.enabledPlugins + id).distinct() else it.enabledPlugins - id
            it.copy(enabledPlugins = next)
        }
        flash(if (enabled) "已启用插件" else "已停用插件")
    }

    /** 从本地目录安装（覆盖同名插件） */
    suspend fun installPluginFromDir(dir: File): String = withContext(Dispatchers.IO) {
        PluginStore.installFromDir(dir).fold(
            onSuccess = { reloadPlugins(); "已安装：${it.title}" },
            onFailure = { "安装失败：${it.message}" },
        )
    }

    /** 从 zip 直链安装 */
    suspend fun installPluginFromZip(url: String): String = withContext(Dispatchers.IO) {
        PluginStore.installFromZip(url).fold(
            onSuccess = { reloadPlugins(); "已安装：${it.title}" },
            onFailure = { "安装失败：${it.message}" },
        )
    }

    /** 从 git 仓库安装 */
    suspend fun installPluginFromGit(url: String): String = withContext(Dispatchers.IO) {
        PluginStore.installFromGit(url).fold(
            onSuccess = { reloadPlugins(); "已安装：${it.title}" },
            onFailure = { "安装失败：${it.message}" },
        )
    }

    fun uninstallPlugin(id: String): String {
        val result = PluginStore.uninstall(id)
        reloadPlugins()
        return result.fold(
            onSuccess = { "已卸载：$id" },
            onFailure = { "卸载失败：${it.message}" },
        )
    }

    /** 生成骨架插件 */
    fun createPlugin(id: String, name: String, description: String, prompt: String): String {
        val result = PluginStore.create(id, name, description, prompt)
        reloadPlugins()
        return result.fold(
            onSuccess = { "已创建：${it.title}（默认未启用）" },
            onFailure = { "创建失败：${it.message}" },
        )
    }

    /** 插件打开目录，方便用户改 prompt / 加命令 */
    fun openPluginDir(id: String) {
        b.ai.desktop.core.util.openPath(File(PluginStore.root(), id).absolutePath)
    }

    // ---------------------------------------------------------------- Git 账号

    suspend fun gitSignIn(token: String): String = withContext(Dispatchers.IO) {
        GitHubAuth.signIn(token).fold(
            onSuccess = {
                gitAccount = it
                "已登录：@${it.login}"
            },
            onFailure = { "登录失败：${it.message?.take(120) ?: "未知错误"}" },
        )
    }

    fun gitSignOut() {
        GitHubAuth.signOut()
        gitAccount = null
        flash("已退出 GitHub")
    }

    // ---------------------------------------------------------------- 工作区

    fun workspaceStat(): WsStat = WorkspaceStat.scan(config.workspaceDir())

    /** 切换工作区：不存在就建，成功后记进最近列表 */
    fun setWorkspace(path: String): String {
        val clean = path.trim().trim('"')
        if (clean.isBlank()) return "路径不能为空"
        val dir = File(clean)
        if (dir.exists() && !dir.isDirectory) return "这是个文件，不是目录"
        if (!dir.exists() && !dir.mkdirs()) return "目录建不出来：${dir.absolutePath}"
        editConfig {
            val recent = (listOf(dir.absolutePath) + it.recentWorkspaces.filter { p -> p != dir.absolutePath })
                .take(8)
            it.copy(workspace = dir.absolutePath, recentWorkspaces = recent)
        }
        return "工作区已切到：${dir.absolutePath}"
    }

    /** 回到默认工作区 */
    fun resetWorkspace(): String = setWorkspace(ConfigStore.defaultWorkspace().absolutePath)

    /** 只移除最近记录，不碰磁盘上的目录 */
    fun forgetWorkspace(path: String) {
        editConfig { it.copy(recentWorkspaces = it.recentWorkspaces.filter { p -> p != path }) }
    }

    // ---------------------------------------------------------------- 用量

    // ---------------------------------------------------------------- 上报与余额

    /** 设备码（格式化展示）；首次在后台采集，读不到时为空 */
    val deviceInfo: DeviceInfo? get() = DeviceCode.info

    fun usageEndpoint(): String = config.usageEndpoint.ifBlank { UsageReporter.DefaultEndpoint }

    /** 手动上报一次（设置页按钮，调用方放到 IO 线程） */
    fun reportUsageNow(): ReportState {
        val result = UsageReporter.report(usageEndpoint())
        flash(if (result.ok) "已上报设备码" else "上报失败：${result.message.take(60)}")
        return result
    }

    /** 重新采集硬件指纹（只在用户主动点时才跑） */
    fun refreshDeviceCode(): DeviceInfo {
        val info = DeviceCode.refresh()
        flash("设备码已重新采集（来源：${info.sources.joinToString("/")}）")
        return info
    }

    /** 余额查询用哪条连接：显式指定的优先，否则当前连接 */
    fun balanceProfile(): ModelProfile? {
        val picked = config.profiles.firstOrNull { it.id == config.balanceProfileId }
        return (picked ?: config.activeProfile)?.takeIf { it.apiKey.isNotBlank() }
    }

    /** 查余额；force = 用户点了刷新（否则 60 秒内直接用缓存） */
    suspend fun refreshBalance(force: Boolean = false): Balance? {
        val balance = BalanceWatch.refresh(balanceProfile(), force)
        if (balance != null && balance.ok && config.balanceAlert && balance.below(config.balanceThreshold)) {
            flash("余额不足：${balance.text}（阈值 ${config.balanceThreshold} 元）")
        }
        return balance
    }

    /** 当前会话的上下文占用（预算环/预算卡的数据源） */
    fun ctxStat(): CtxStat = CtxStat.of(messages, config.ctxBudget)

    // ---------------------------------------------------------------- 会话

    fun selectConversation(id: String) {
        if (id == currentId) return
        if (config.keepDrafts && currentId.isNotBlank()) draftsBySession[currentId] = draft
        currentId = id
        draft = if (config.keepDrafts) draftsBySession[id].orEmpty() else ""
        if (renamingId != null && renamingId != id) renamingId = null
    }

    fun newConversation() {
        if (config.keepDrafts && currentId.isNotBlank()) draftsBySession[currentId] = draft
        val session = Session()
        sessions = pinnedFirst(listOf(session) + sessions)
        currentId = session.id
        attachments.clear()
        draft = ""
        renamingId = null
        sessionStore.save(sessions)
    }

    fun deleteConversation(id: String) {
        draftsBySession.remove(id)
        val gone = sessions.firstOrNull { it.id == id }?.title
        sessions = sessions.filterNot { it.id == id }
        if (currentId == id) currentId = sessions.firstOrNull()?.id ?: ""
        if (renamingId == id) renamingId = null
        sessionStore.save(sessions)
        if (gone != null) flash("已删除会话「$gone」")
    }

    fun beginRename(id: String) {
        renamingId = id
    }

    fun cancelRename() {
        renamingId = null
    }

    fun renameConversation(id: String, title: String) {
        val clean = title.trim().replace('\n', ' ').take(60)
        renamingId = null
        if (clean.isBlank()) return
        sessions = sessions.map { if (it.id == id) it.copy(title = clean) else it }
        sessionStore.save(sessions)
    }

    fun togglePinned(id: String) {
        val target = sessions.firstOrNull { it.id == id } ?: return
        sessions = pinnedFirst(sessions.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
        sessionStore.save(sessions)
        flash(if (target.pinned) "已取消置顶" else "已置顶「${target.title}」")
    }

    /** 复制整段会话为 Markdown */
    fun copySession(session: Session) {
        val ok = copyToClipboard(sessionMarkdown(session))
        flash(if (ok) "已复制会话 Markdown" else "复制失败：剪贴板不可用")
    }

    /** 导出全部会话为一份 .md */
    fun exportAll(): File? = exportAllSessions(sessions)

    /** 导出为 .md 文件（用户取消不打扰） */
    fun exportSession(session: Session) {
        val file = exportSessionMarkdown(session)
        if (file != null) flash("已导出：${file.name}")
    }

    private fun ensureSession(): String {
        if (currentId.isNotBlank() && sessions.any { it.id == currentId }) return currentId
        val session = Session()
        sessions = pinnedFirst(listOf(session) + sessions)
        currentId = session.id
        return session.id
    }

    private fun pinnedFirst(list: List<Session>): List<Session> =
        list.filter { it.pinned } + list.filterNot { it.pinned }

    /** 只改当前会话的消息，顺手刷新时间并落盘 */
    private fun updateMessages(block: (List<ChatMessage>) -> List<ChatMessage>) {
        val id = currentId
        if (id.isBlank()) return
        sessions = sessions.map { session ->
            if (session.id != id) session
            else session.copy(messages = block(session.messages), updatedAt = System.currentTimeMillis())
        }
        sessionStore.save(sessions)
    }

    // ---------------------------------------------------------------- 消息操作

    fun copyMessage(msg: ChatMessage) {
        val ok = copyToClipboard(msg.text)
        flash(if (ok) "已复制" else "复制失败：剪贴板不可用")
    }

    /**
     * 删除单条消息。删除带工具调用的助手消息时，把它后面紧跟的工具回执一起删掉，
     * 否则历史里会留下没有对应回执的 tool_calls，下次请求必然报协议错误。
     */
    fun deleteMessage(index: Int) {
        val list = messages
        if (index !in list.indices) return
        val drop = mutableSetOf(index)
        if (list[index].role == MsgRole.ASSISTANT) {
            var i = index + 1
            while (i < list.size && list[i].role == MsgRole.TOOL) {
                drop += i
                i++
            }
        }
        updateMessages { it.filterIndexed { i, _ -> i !in drop } }
        flash(if (drop.size > 1) "已删除这条消息及 ${drop.size - 1} 条工具回执" else "已删除这条消息")
    }

    /** 从这条消息起截断（含），尾部删掉永远协议安全 */
    fun deleteMessageFrom(index: Int) {
        val list = messages
        if (index !in list.indices) return
        val removed = list.size - index
        updateMessages { it.take(index) }
        flash("已删除 $removed 条消息")
    }

    /** 以这条消息为结尾复制出一个新会话，方便沿着旧上下文换方向聊 */
    fun branchFrom(index: Int) {
        val session = currentSession ?: return
        if (index !in session.messages.indices) return
        val child = Session(
            title = session.title.take(18) + " · 分支",
            messages = session.messages.take(index + 1),
            updatedAt = System.currentTimeMillis(),
        )
        sessions = pinnedFirst(listOf(child) + sessions)
        currentId = child.id
        draft = ""
        attachments.clear()
        renamingId = null
        sessionStore.save(sessions)
        flash("已从这条消息分叉出新会话")
    }

    /** 用户消息：内容与附件回填输入框，并截断到这条之前，改完手动发 */
    fun editAndResend(index: Int) {
        val msg = messages.getOrNull(index) ?: return
        if (msg.role != MsgRole.USER) return
        draft = msg.text
        attachments.clear()
        msg.attachments.forEach { ref ->
            val kind = AttachmentKind.entries.firstOrNull { it.label == ref.kind } ?: AttachmentKind.FILE
            attachments.add(Attachment(kind = kind, name = ref.name, path = ref.path))
        }
        updateMessages { it.take(index) }
        flash("已回填输入框，改完直接发送")
    }

    /** 收藏 / 取消收藏（对齐手机端） */
    fun toggleStar(index: Int) {
        val msg = messages.getOrNull(index) ?: return
        updateMessages { list ->
            list.mapIndexed { i, m -> if (i == index) m.copy(starred = !m.starred) else m }
        }
        flash(if (msg.starred) "已取消收藏" else "已收藏")
    }

    /** 在指定会话里收藏 / 取消收藏（收藏夹页用，当前会话不一定就是它） */
    fun toggleStarIn(sessionId: String, index: Int) {
        sessions = sessions.map { session ->
            if (session.id != sessionId) session
            else session.copy(
                messages = session.messages.mapIndexed { i, m ->
                    if (i == index) m.copy(starred = !m.starred) else m
                },
            )
        }
        sessionStore.save(sessions)
    }

    /** 清空全部会话（设置里的「数据」页用，必须二次确认） */
    fun clearAllSessions() {
        draftsBySession.clear()
        sessions = emptyList()
        currentId = ""
        sessionStore.save(sessions)
        flash("已清空全部会话")
    }

    /** 朗读这条消息 */
    fun speakMessage(msg: ChatMessage) {
        if (msg.text.isBlank()) {
            flash("这条没有可朗读的内容")
            return
        }
        val ok = Speech.speak(msg.text, config.speechRate)
        flash(if (ok) "开始朗读" else "朗读失败：系统语音不可用")
    }

    /** 重新生成：丢掉最后一条用户消息之后的回复，按同一上下文重跑 */
    suspend fun regenerate(options: RunOptions = RunOptions(), note: String = "") {
        if (busy) return
        val list = messages
        val lastUser = list.indexOfLast { it.role == MsgRole.USER }
        if (lastUser < 0) {
            flash("这个会话还没有提问")
            return
        }
        updateMessages { it.take(lastUser + 1) }
        runKernel(options, note)
    }

    private fun append(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        // 助手消息补上模型名：统计页要按模型分组，内核拿不到这一层配置
        val model = config.activeProfile?.model.orEmpty()
        val stamped = messages.map { msg ->
            if (msg.role == MsgRole.ASSISTANT && msg.model.isBlank()) msg.copy(model = model) else msg
        }
        val id = ensureSession()
        sessions = sessions.map { session ->
            if (session.id != id) {
                session
            } else {
                val isFirstUser = session.messages.none { it.role == MsgRole.USER }
                val newTitle = if (isFirstUser && config.autoTitle) {
                    stamped.firstOrNull { it.role == MsgRole.USER }?.text
                        ?.replace('\n', ' ')
                        ?.trim()
                        ?.take(24)
                        ?.ifBlank { session.title }
                        ?: session.title
                } else {
                    session.title
                }
                session.copy(
                    title = newTitle,
                    messages = session.messages + stamped,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 输入

    fun addAttachment(attachment: Attachment) {
        attachments.add(attachment)
    }

    fun removeAttachment(index: Int) {
        if (index in attachments.indices) attachments.removeAt(index)
    }

    fun appendDraft(text: String) {
        draft = if (draft.isBlank()) text else draft + "\n" + text
    }

    // ---------------------------------------------------------------- 发送

    /** 跑一轮：落用户消息 → 驱动内核（没有模型就在内核入口提示配置） */
    suspend fun send(options: RunOptions = RunOptions()) {
        if (busy) return
        val text = draft.trim()
        if (text.isEmpty() && attachments.isEmpty()) return

        val sent = attachments.toList()
        append(
            listOf(
                ChatMessage(
                    role = MsgRole.USER,
                    text = text,
                    attachments = sent.map { AttachmentRef(it.kind.label, it.name, it.path) },
                ),
            ),
        )
        draft = ""
        attachments.clear()
        runKernel(options)
    }

    /**
     * 历史截断：只发最近 N 条。裁剪后如果开头是孤立的 tool 回执，
     * 往后挪到第一条非 tool 消息，否则请求会被协议校验挡掉。
     */
    private fun clipHistory(list: List<ChatMessage>): List<ChatMessage> {
        val limit = config.historyLimit
        if (limit <= 0 || list.size <= limit) return list
        var start = list.size - limit
        while (start < list.size && list[start].role == MsgRole.TOOL) start++
        return list.subList(start, list.size)
    }

    // ---- 上下文压缩（对齐手机端 role=summary）----
    /** 会话摘要缓存：key = sessionId|被压缩条数；条数变了重新生成 */
    private val summaryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** 摘要档位的字数上限（对齐手机端 Sum：0 简洁 1500 / 1 详细 12000 / 2 全面不限） */
    private fun summaryWordCap(level: String): Int? = when (level) {
        "brief" -> 1500
        "full" -> null
        else -> 12000
    }

    /** 压缩保留条数：消息上限优先，没配就按 ctxKeep（对齐手机端语义） */
    private fun compressKeep(): Int = if (config.historyLimit > 0) config.historyLimit else config.ctxKeep

    /** 压缩触发：估算 token 超过窗口的阈值比例，或条数超上限 */
    private fun overContext(list: List<ChatMessage>): Boolean {
        val win = config.ctxTotal
        if (win > 0) {
            val budget = win.toLong() * config.ctxPct.coerceIn(10, 99) / 100L
            if (b.ai.desktop.core.util.Tokens.estimateAll(list) > budget) return true
        }
        return list.size > compressKeep()
    }

    /**
     * 压缩：开了 ctxCompress 且超限时，把被裁掉的那段用模型压成摘要，
     * 作为一条 SYSTEM 消息放在最近历史前面一起下发。原始消息不动。
     */
    private suspend fun withSummary(sessionId: String, list: List<ChatMessage>): List<ChatMessage> {
        if (!config.ctxCompress || !overContext(list)) return list
        val keep = compressKeep().coerceAtLeast(4)
        if (list.size <= keep) return list
        var start = list.size - keep
        while (start < list.size && list[start].role == MsgRole.TOOL) start++
        if (start <= 0) return list
        val dropped = list.subList(0, start)
        val key = "$sessionId|$start"
        val cached = summaryCache[key]
        val summary = cached ?: run {
            val profile = config.profiles.firstOrNull { it.id == config.summaryProfileId }
                ?: config.activeProfile
                ?: return list
            val transcript = dropped.joinToString("\n") { msg ->
                when (msg.role) {
                    MsgRole.USER -> "用户：${msg.text.take(400)}"
                    MsgRole.ASSISTANT -> "助手：${msg.text.take(400)}"
                    else -> ""
                }
            }.split("\n").filter { it.isNotBlank() }.joinToString("\n")
                .take(16_000)
            if (transcript.isBlank()) return list
            val cap = summaryWordCap(config.summaryLevel)
            val capText = cap?.let { "，${it} 字以内" } ?: "（不限字数，按内容详略得当）"
            val extra = config.summaryPrompt.trim()
            val reply = runCatching {
                b.ai.desktop.core.net.OpenAiChatClient(profile).complete(
                    messages = listOf(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                "把下面这段历史对话压缩成要点摘要（保留关键决定、文件路径、未完成事项）$capText，" +
                                    (if (extra.isNotBlank()) "补充要求：$extra。" else "") +
                                    "直接输出摘要本体：\n\n$transcript",
                            )
                        },
                    ),
                    tools = emptyList(),
                    options = b.ai.desktop.core.model.RunOptions(),
                )
            }.getOrNull() ?: return list   // 压缩失败就不带摘要，按原样发
            val text = reply.text.trim().ifBlank { return list }
            summaryCache[key] = text
            // 旧键清掉，防膨胀
            summaryCache.keys.filter { it.startsWith("$sessionId|") && it != key }.forEach { summaryCache.remove(it) }
            text
        }
        val summaryMsg = ChatMessage(
            role = MsgRole.SYSTEM,
            text = "【前情摘要】以下是对更早对话的压缩概括，随后是最近 ${list.size - start} 条原话：\n$summary",
        )
        return listOf(summaryMsg) + list.subList(start, list.size)
    }

    /** 立即压缩当前会话（对齐手机端 compactNow）：按压缩规则预生成摘要，下次发送直接带上 */
    suspend fun compactNow() {
        val session = currentSession ?: run { flash("当前没有会话"); return }
        val list = session.messages
        if (!overContext(list)) {
            flash("还没到压缩线：估算 ${fmtTokens(b.ai.desktop.core.util.Tokens.estimateAll(list))} / 阈值 " +
                fmtTokens(config.ctxTotal * config.ctxPct / 100))
            return
        }
        flash("正在压缩…")
        val keep = compressKeep().coerceAtLeast(4)
        val start = (list.size - keep).coerceAtLeast(0)
        val sessionId = session.id
        // 先清缓存再走一遍压缩路径，成功与否看消息里有没有摘要
        summaryCache.keys.filter { it.startsWith("$sessionId|") }.forEach { summaryCache.remove(it) }
        val withSum = withSummary(sessionId, list)
        if (withSum.firstOrNull()?.text?.startsWith("【前情摘要】") == true) {
            flash("已压缩：更早 $start 条已归纳成摘要，下次发送生效")
        } else {
            flash("压缩失败：没配总结模型或请求失败；可在「上下文」页选压缩用的连接")
        }
    }

    /** 启用的插件里声明的命令工具；和工作区工具重名的跳过，模型不会看到两份声明 */
    /** 手机互联工具（仅桌面端）：adb 可定位且开关打开时才挂 */
    private fun adbTools(): List<b.ai.desktop.core.tool.Tool> {
        if (!config.adbEnabled) return emptyList()
        val adbPath = b.ai.desktop.core.adb.ADB.locate(config.adbPath)
        if (adbPath == null) {
            return emptyList()
        }
        val taken = defaultToolRegistry(
            config.workspaceDir(),
            config.enabledTools,
            config.commandTimeoutSec,
            sandboxMode,
        ).names().toMutableSet()
        val tool = b.ai.desktop.core.tool.tools.AdbTool(
            adbPathProvider = { b.ai.desktop.core.adb.ADB.locate(config.adbPath) },
            workspaceRoot = config.workspaceDir(),
            allowDualChannel = { config.adbDualChannel },
        )
        return if (taken.add(tool.spec.name)) listOf(tool) else emptyList()
    }

    /** 客户端搜索工具：选了平台且配置齐了就挂 web_search（网关原生搜索不受影响） */
    private fun searchTools(): List<b.ai.desktop.core.tool.Tool> {
        val provider = b.ai.desktop.core.search.WebSearch.byId(config.searchProvider)
            ?: return emptyList()
        if (b.ai.desktop.core.search.WebSearch.missing(provider, ::searchCfg) != null) return emptyList()
        val taken = defaultToolRegistry(
            config.workspaceDir(),
            config.enabledTools,
            config.commandTimeoutSec,
            sandboxMode,
        ).names().toMutableSet()
        val tool = b.ai.desktop.core.tool.tools.WebSearchTool(
            providerProvider = { b.ai.desktop.core.search.WebSearch.byId(config.searchProvider) },
            cfgProvider = { field -> searchCfg(field) },
            topicProvider = { config.searchTopic },
        )
        return if (taken.add(tool.spec.name)) listOf(tool) else emptyList()
    }

    /** 搜索平台配置取值：Tavily 的 key / Google 的 key、cx */
    private fun searchCfg(field: String): String = when (field) {
        "key" -> when (config.searchProvider) {
            "google" -> config.googleSearchKey
            else -> config.tavilyKey
        }
        "cx" -> config.googleSearchCx
        else -> ""
    }

    fun savePromptTags(list: List<b.ai.desktop.core.config.PromptTag>) {
        editConfig { it.copy(promptTags = list) }
    }

    /** MCP 服务提供的工具：连得上就有，连不上跳过并在状态条提示 */
    private suspend fun mcpTools(): List<b.ai.desktop.core.tool.Tool> {
        val result = mutableListOf<b.ai.desktop.core.tool.Tool>()
        val taken = defaultToolRegistry(
            config.workspaceDir(),
            config.enabledTools,
            config.commandTimeoutSec,
            sandboxMode,
        ).names().toMutableSet()
        config.mcpServers.filter { it.enabled }.forEach { cfg ->
            runCatching {
                b.ai.desktop.core.mcp.McpManager.session(cfg).listTools().forEach { info ->
                    val tool = b.ai.desktop.core.mcp.McpTool(cfg.displayName, info, cfg)
                    if (taken.add(tool.spec.name)) result += tool
                }
            }.onFailure {
                status = "MCP「${cfg.displayName}」连不上：${it.message}"
            }
        }
        return result
    }

    private fun pluginTools(): List<b.ai.desktop.core.tool.Tool> {
        val workspace = b.ai.desktop.core.tool.Workspace(config.workspaceDir(), sandboxMode)
        val taken = defaultToolRegistry(
            workspace = config.workspaceDir(),
            enabled = config.enabledTools,
            commandTimeoutSec = config.commandTimeoutSec,
            sandboxMode = sandboxMode,
            extraPath = config.toolchainPath,
        ).names().toMutableSet()

        val result = mutableListOf<b.ai.desktop.core.tool.Tool>()
        activePlugins.forEach { info ->
            PluginStore.tools(info, workspace, config.commandTimeoutSec.coerceAtLeast(60)).forEach { tool ->
                if (taken.add(tool.spec.name)) result += tool
            }
        }
        return result
    }

    /** 基础工具名：插件工具不能和它们撞名 */
    private val registryNames: List<String>
        get() = defaultToolRegistry(
            config.workspaceDir(),
            config.enabledTools,
            config.commandTimeoutSec,
            sandboxMode,
            extraPath = config.toolchainPath,
        ).names()

    /** 系统提示词 = 基础提示 + 运行时信息 + 输出偏好 + 启用插件的 prompt + 本轮额外要求 */
    private fun buildSystemPrompt(note: String): String {
        val builder = StringBuilder(config.systemPrompt.trimEnd())

        if (config.injectContext) {
            val now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE"))
            builder.append("\n\n【运行环境】\n")
                .append("- 时间：$now\n")
                .append("- 系统：${System.getProperty("os.name")} ${System.getProperty("os.version")}\n")
                .append("- 工作区：${config.workspaceDir().absolutePath}\n")
                .append("- 应用：Baicodex Windows 0.1.0")
        }

        if (config.memoryEnabled && config.memoryText.isNotBlank()) {
            builder.append("\n\n【长期记忆】\n").append(config.memoryText.trim())
        }

        if (config.stylePrompt.isNotBlank()) {
            builder.append("\n\n【输出偏好】\n").append(config.stylePrompt.trim())
        }

        activePlugins.mapNotNull { info ->
            val prompt = info.manifest.prompt.trim()
            if (prompt.isBlank()) null else "【插件：${info.title}】\n$prompt"
        }.forEach { block ->
            builder.append("\n\n").append(block)
        }

        if (note.isNotBlank()) {
            builder.append("\n\n本轮必须遵守的额外要求：").append(note.trim())
        }
        return builder.toString()
    }

    /** 按当前会话已有的历史跑一轮，边跑边落库（发送与重新生成共用） */
    private suspend fun runKernel(options: RunOptions, note: String = "") {
        val profile = config.activeProfile
        if (profile == null || !profile.ready) {
            append(
                listOf(
                    ChatMessage(
                        MsgRole.SYSTEM,
                        "还没配置模型：点右上角「设置」，填 baseUrl、API Key 和模型名后再发一次。",
                    ),
                ),
            )
            sessionStore.save(sessions)
            return
        }

        busy = true
        error = null
        try {
            val registry = defaultToolRegistry(
                workspace = config.workspaceDir(),
                enabled = config.enabledTools,
                commandTimeoutSec = config.commandTimeoutSec,
                sandboxMode = sandboxMode,
                extraPath = config.toolchainPath,
            ) + pluginTools() + mcpTools() + searchTools() + adbTools()
            val kernel = AgentKernel(
                client = OpenAiChatClient(profile),
                registry = registry,
                maxRounds = config.maxToolRounds,
                options = options.copy(
                    parallelToolCalls = config.parallelToolCalls,
                    // 模型能力声明：不支持的功能即使开关开着也不下发，免得网关 422
                    thinking = if (config.activeProfile?.supportsThinking == false) false else options.thinking,
                    nativeSearch = if (config.activeProfile?.supportsNativeSearch == false) false else options.nativeSearch,
                ),
                // 每个工具执行前都过闸门；写入/删除/命令会挂起等用户点头
                gate = buildGate(),
                approver = { call, ask -> askApproval(call, ask) },
            )
            kernel.run(
                systemPrompt = buildSystemPrompt(note),
                // 压缩开=withSummary（内部含截断+防协议修正）；关=纯截断
                history = if (config.ctxCompress) {
                    withSummary(currentSession?.id.orEmpty(), messages)
                } else {
                    clipHistory(messages)
                },
                onMessage = { append(listOf(it)) },
                onStatus = { status = it },
            )
        } catch (t: Throwable) {
            val reason = t.message?.trim().orEmpty().ifBlank { t::class.simpleName ?: "未知错误" }
            error = reason
            append(listOf(ChatMessage(MsgRole.SYSTEM, "调用失败：$reason")))
        } finally {
            busy = false
            status = null
            roundStopped = false
            sessionStore.save(sessions)
            if (config.autoSpeak) {
                messages.lastOrNull { it.role == MsgRole.ASSISTANT && it.text.isNotBlank() }
                    ?.let { Speech.speak(it.text, config.speechRate) }
            }
        }
    }

    /** 提示词优化用的连接：优先用专门配置的，没配就跟随当前对话模型 */
    fun optimizerProfile(): ModelProfile? {
        val picked = config.profiles.firstOrNull { it.id == config.optimizerProfileId }
        return (picked ?: config.activeProfile)?.takeIf { it.ready && it.apiKey.isNotBlank() }
    }

    /** 设置页改完优化模型后重挂后端 */
    fun refreshOptimizerBackend() {
        b.ai.desktop.feature.chat.prompt.PromptOptimizer.backend = object :
            b.ai.desktop.feature.chat.prompt.PromptOptimizerBackend {
            override suspend fun optimize(system: String, user: String, temperature: Float): String {
                val profile = optimizerProfile()
                    ?: throw IllegalStateException("还没配优化模型")
                val reply = OpenAiChatClient(profile).complete(
                    messages = listOf(
                        kotlinx.serialization.json.buildJsonObject {
                            put("role", "system")
                            put("content", system)
                        },
                        kotlinx.serialization.json.buildJsonObject {
                            put("role", "user")
                            put("content", user)
                        },
                    ),
                    tools = emptyList(),
                    options = RunOptions(toolCalls = false, thinking = false, effort = "low"),
                )
                return reply.text
            }
        }
    }
}

@Composable
fun rememberAppState(): AppState = remember { AppState() }
