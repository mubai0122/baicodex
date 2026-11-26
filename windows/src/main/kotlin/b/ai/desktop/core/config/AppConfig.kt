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
 * 应用配置：模型连接、工作区、系统提示词。落盘到 %APPDATA%/Baicodex/config.json
 */

package b.ai.desktop.core.config

import java.io.File
import b.ai.desktop.core.security.Vault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** 一个模型连接（OpenAI 兼容协议） */
@Serializable
data class PromptTag(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val label: String = "",
    val text: String = "",
)

@Serializable
data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    /** 显示名，例如 DeepSeek / 本地 vLLM */
    var name: String = "",
    /** 例如 https://api.deepseek.com/v1 */
    var baseUrl: String = "",
    var apiKey: String = "",
    /** 下发用的模型名，例如 deepseek-chat */
    var model: String = "",
    /** 是否支持原生工具调用（关掉就让模型只能聊天） */
    var toolsEnabled: Boolean = true,
    /** 模型能力声明：不支持时即使开关开着也不下发，避免 422 */
    var supportsThinking: Boolean = true,
    /** 模型能力声明：服务端原生联网搜索 */
    var supportsNativeSearch: Boolean = true,
    /** 采样温度；负数表示不下发（o 系列只接受默认值） */
    var temperature: Double = -1.0,
    /** 单轮输出上限；0 表示不下发（最大模式另有兜底） */
    var maxTokens: Int = 0,
    /** nucleus 采样；负数表示不下发 */
    var topP: Double = -1.0,
    /** 单次请求超时（秒）：长思考模型要给够时间 */
    var timeoutSec: Int = 180,
) {
    val ready: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
    val label: String get() = name.ifBlank { model.ifBlank { "未命名模型" } }
}

@Serializable
data class AppConfig(
    var profiles: List<ModelProfile> = emptyList(),
    var activeProfileId: String = "",
    /** 工具的工作目录；空表示用默认目录 */
    var workspace: String = "",
    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    /** 单轮最多连续工具调用次数 */
    var maxToolRounds: Int = 8,
    /** true = 回车发送、Shift+回车换行；false = 回车换行、Ctrl+回车发送 */
    var sendOnEnter: Boolean = true,
    /** 新消息自动滚到底 */
    var autoScroll: Boolean = true,
    /** 聊天正文字号 */
    var fontSize: Int = 13,
    /** 代码块字号 */
    var codeFontSize: Int = 12,
    /** 行距百分比，155 = 1.55 倍 */
    var lineHeight: Int = 155,
    /** 主题色 id（theme/Accent 里定义）；个性化页可换 */
    var accent: String = "blue",
    /** 界面语言 BCP-47；空串跟随系统（与手机端 Lang 同一套选项） */
    var lang: String = "",
    /** 字体族 id（UiFonts.options）；空 = 默认 */
    var fontFamily: String = "",
    /** 全局缩放百分比：100 = 默认，80–150 */
    var uiScale: Int = 100,
    /** 气泡圆角 dp */
    var bubbleCorner: Int = 8,
    /** 卡片圆角 dp */
    var cardCorner: Int = 12,
    /** 弹窗圆角 dp */
    var dialogCorner: Int = 14,
    /** 背景：plain 纯色 / gradient 微渐变 */
    var backdrop: String = "plain",
    /** 主题模式：system / light / dark（ThemeMode.name） */
    var themeMode: String = "system",
    /** 启用的工具名；空列表 = 全部启用 */
    var enabledTools: List<String> = emptyList(),
    /** 命令工具默认超时（秒） */
    var commandTimeoutSec: Int = 30,
    /** 额外工具链目录（分号分隔），run_command 的 PATH 会前置这些目录 */
    var toolchainPath: String = "",
    /** MCP 服务列表（stdio 子进程） */
    var mcpServers: List<b.ai.desktop.core.mcp.McpServerCfg> = emptyList(),
    /** 手机互联（仅桌面端）：关 = adb 工具不挂进工具表 */
    var adbEnabled: Boolean = true,
    /** adb.exe 路径；空 = 自动定位 */
    var adbPath: String = "",
    /** 双通道传输（USB+内网同时给同一台设备传） */
    var adbDualChannel: Boolean = true,
    /** 语音识别：模型名（whisper-1 等） */
    var sttModel: String = "whisper-1",
    /** 语音识别：语言（空 = 自动）；zh / en / ja … */
    var sttLanguage: String = "",
    /** 语音识别：端点；空 = 跟随当前连接的 baseUrl */
    var sttEndpoint: String = "",
    /** 客户端搜索平台：tavily / bing / google；空 = 不启用（对齐手机端 searchProvider） */
    var searchProvider: String = "",
    /** Tavily Key（searchProvider=tavily 时用） */
    var tavilyKey: String = "",
    /** Google Custom Search：API Key 与搜索引擎 ID（searchProvider=google 时用） */
    var googleSearchKey: String = "",
    var googleSearchCx: String = "",
    /** Tavily 检索范围：general / news */
    var searchTopic: String = "general",
    /** 提示词标签库（一键插进输入框） */
    var promptTags: List<PromptTag> = emptyList(),
    /** 桌宠悬浮窗 */
    var petOn: Boolean = false,
    /** 消息头像（对齐手机端 avatarOn）：AI 与用户气泡旁的小头像 */
    var avatarOn: Boolean = true,
    /** 上下文压缩：超限的历史用模型压成摘要带进上下文（对齐手机端 role=summary） */
    var ctxCompress: Boolean = false,
    /** 上下文窗口（token）：压缩触发阈值按它算；32k/64k/128k/256k/1M（对齐手机端 ctxTotal） */
    var ctxTotal: Int = 128_000,
    /** 压缩阈值百分比 10-99：估算 token 到窗口的这个比例就开始压缩（对齐手机端 ctxPct） */
    var ctxPct: Int = 70,
    /** 压缩后保留最近多少条原话（对齐手机端 ctxKeep）；historyLimit 优先于它 */
    var ctxKeep: Int = 8,
    /** 总结补充要求：拼在内置压缩提示词后面；留空不追加（对齐手机端 summaryPrompt） */
    var summaryPrompt: String = "",
    /** 摘要档位：brief 简洁 1500 字 / standard 详细 12000 字 / full 全面不限（对齐手机端 summaryLevel 0/1/2） */
    var summaryLevel: String = "standard",
    /** 压缩用的连接 id；空 = 跟随当前对话模型（对齐手机端 summaryProfileId） */
    var summaryProfileId: String = "",
    /** 胶囊气泡（对齐手机端 bubbleCapsule）：完全圆润的胶囊形 */
    var bubbleCapsule: Boolean = false,
    /** 自定义头像文件名（appDir/avatars 下）；用户侧 */
    var avatarUserFile: String = "",
    /** 自定义头像文件名；AI 侧 */
    var avatarAiFile: String = "",
    /** 桌宠大小 dp */
    var petSize: Int = 120,
    /** 凭据加密：开 = API Key 落盘前 AES-GCM 加密 */
    var encryptSecrets: Boolean = false,
    /** 加密盐（首次开启时生成；与机器标识一起派生密钥） */
    var vaultSalt: String = "",
    /** 绑定设备：密钥里混入机器标识（MachineGuid），拷走配置到别的机器解不开；默认关，只加密不绑机器 */
    var vaultBindDevice: Boolean = false,
    /** 一次请求最多带多少条历史消息；0 = 全部 */
    var historyLimit: Int = 0,
    /** 是否下发并行工具调用开关 */
    var parallelToolCalls: Boolean = true,
    /** 显示思考过程 */
    var showReasoning: Boolean = true,
    /** 思考块默认展开 */
    var reasoningExpanded: Boolean = false,
    /** 气泡下面显示时间 */
    var showTimestamp: Boolean = true,
    /** 回复下方显示 token / 耗时（对齐手机端 showUsage，与「回复信息」分开） */
    var showUsage: Boolean = true,
    /** 删除会话 / 消息前要二次确认 */
    var confirmDelete: Boolean = true,
    /** 气泡最大宽度 */
    var bubbleMaxWidth: Int = 560,
    /** 侧边栏宽度 */
    var sidebarWidth: Int = 268,
    /** 紧凑模式：消息间距与内边距收窄 */
    var compact: Boolean = false,
    /** 侧边栏显示消息条数 */
    var showMessageCount: Boolean = true,
    /** 上下文预算（token），顶栏预算环按它算百分比；0 = 不显示占用 */
    var ctxBudget: Int = 32000,
    /** 已启用的插件 id；插件本身存 <appDir>/plugins */
    var enabledPlugins: List<String> = emptyList(),
    /** 最近用过的工作区，只在设置里做快捷切换，不自动删除任何目录 */
    var recentWorkspaces: List<String> = emptyList(),

    // ---- 新会话默认值（对话工具栏仍可临时改，改完不影响这里） ----
    /** 新会话默认开启深度思考 */
    var defaultThinking: Boolean = false,
    /** 新会话默认思考档位 */
    var defaultEffort: String = "medium",
    /** 新会话默认最大模式 */
    var defaultMaxMode: Boolean = false,
    /** 新会话默认开原生联网搜索 */
    var defaultNativeSearch: Boolean = false,

    // ---- 注入与输出 ----
    /** 把当前时间 / 平台 / 工作区拼进系统提示（模型能知道「现在」和「在哪」） */
    var injectContext: Boolean = false,
    /** 输出偏好：追加在系统提示末尾，比如「回答先给结论」「代码注释用中文」 */
    var stylePrompt: String = "",
    /** 回复下方显示耗时 / token / 模型名 */
    var showMeta: Boolean = true,
    /** 按会话保留草稿：切走再切回来，输入框里的字还在 */
    var keepDrafts: Boolean = true,
    /** 首条消息自动作为会话标题 */
    var autoTitle: Boolean = true,
    /** 输入框占位提示；空 = 用默认文案 */
    var inputPlaceholder: String = "",

    // ---- 语音 ----
    /** 回复完成后自动朗读 */
    var autoSpeak: Boolean = false,
    /** 朗读语速（SAPI Rate，-10 慢..10 快） */
    var speechRate: Int = 0,

    // ---- 长期记忆 ----
    /** 是否把「记忆」拼进系统提示 */
    var memoryEnabled: Boolean = false,
    /** 跨会话长期记忆正文：用户自己写，或让模型总结后贴进来 */
    var memoryText: String = "",

    // ---- 使用上报（手机端同款：启动累加一次，只发设备码哈希与计数） ----
    /** 是否上报；关掉后一次请求都不发 */
    var usageReportEnabled: Boolean = true,
    /** 自建服务端时改这里；空 = 默认接口 */
    var usageEndpoint: String = "",

    // ---- 余额 ----
    /** 余额查询用哪条连接；空 = 当前连接 */
    var balanceProfileId: String = "",
    /** 低于阈值时在界面上提醒 */
    var balanceAlert: Boolean = true,
    /** 阈值（元） */
    var balanceThreshold: Double = 5.0,

    // ---- 提示词优化 ----
    /** 优化用的模型连接 id；空 = 跟随当前对话模型 */
    var optimizerProfileId: String = "",

    // ---- 沙箱与工具同意 ----
    /** 沙箱档位：strict / standard / relaxed（见 core.sandbox.SandboxMode） */
    var sandboxMode: String = "standard",
    /** 工具同意策略：smart / always / trust（见 core.approval.ApprovalMode） */
    var approvalMode: String = "smart",
    /** 永久放行的工具名：点过「始终允许」的会记在这里 */
    var alwaysAllowTools: List<String> = emptyList(),
    /** 危险命令拦截；关掉后系统级命令降级为「要你确认」而不是直接拒 */
    var commandGuardEnabled: Boolean = true,
    /** 首次启动引导页是否已走过 */
    var onboardingDone: Boolean = false,
) {
    val activeProfile: ModelProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    fun workspaceDir(): File {
        val dir = if (workspace.isBlank()) ConfigStore.defaultWorkspace() else File(workspace)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

const val DEFAULT_SYSTEM_PROMPT = """你是 Baicodex 桌面版里的编码助手。
- 需要看文件、列目录、搜索或执行命令时，直接调用提供的工具，不要凭空猜测文件内容。
- 改文件前先读；小改用 edit_file 做局部替换，整篇重写才用 write_file。
- 保持项目现有风格；删改前先确认路径，不要动工作区外的东西。
- 回答用中文，简洁直接；给出结论和必要的关键代码片段。"""

object ConfigStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 自检/测试用：重定向配置目录（null = 正常走 %APPDATA%） */
    @Volatile
    var dirOverride: java.io.File? = null

    fun appDir(): File {
        dirOverride?.let { return it.apply { mkdirs() } }
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".config")
        return File(base, "Baicodex").apply { mkdirs() }
    }

    fun defaultWorkspace(): File = File(appDir(), "workspace").apply { mkdirs() }

    private fun configFile(): File = File(appDir(), "config.json")

    fun load(): AppConfig {
        val file = configFile()
        if (!file.exists()) return AppConfig()
        // 坏文件先留个底，别让一次崩溃把用户配置无声吞掉
        val raw = file.readText()
        val config = runCatching { json.decodeFromString<AppConfig>(raw) }.getOrElse { _ ->
            runCatching { file.copyTo(file.resolveSibling("config.json.corrupt"), overwrite = true) }
            AppConfig()
        }
        // 内存里永远是明文；密文只存在磁盘上。
        // openAny 两种派生都试（绑定开关切换后老密文也读得回），解不开的项原样保留
        if (config.vaultSalt.isBlank()) return config
        return config.copy(
            profiles = config.profiles.map { it.copy(apiKey = Vault.openAny(it.apiKey, config.vaultSalt)) },
            tavilyKey = Vault.openAny(config.tavilyKey, config.vaultSalt),
            googleSearchKey = Vault.openAny(config.googleSearchKey, config.vaultSalt),
        )
    }

    fun save(config: AppConfig) {
        val salt = if (config.vaultSalt.isBlank()) Vault.newSalt() else config.vaultSalt
        val onDisk = if (!config.encryptSecrets) config.copy(vaultSalt = salt) else config.copy(
            vaultSalt = salt,
            profiles = config.profiles.map {
                it.copy(
                    apiKey = if (Vault.isSealed(it.apiKey)) {
                        it.apiKey
                    } else {
                        Vault.seal(it.apiKey, salt, bindDevice = config.vaultBindDevice)
                    },
                )
            },
            tavilyKey = if (Vault.isSealed(config.tavilyKey)) {
                config.tavilyKey
            } else {
                Vault.seal(config.tavilyKey, salt, bindDevice = config.vaultBindDevice)
            },
            googleSearchKey = if (Vault.isSealed(config.googleSearchKey)) {
                config.googleSearchKey
            } else {
                Vault.seal(config.googleSearchKey, salt, bindDevice = config.vaultBindDevice)
            },
        )
        runCatching {
            // 先写临时文件再原子替换：写到一半崩溃/断电不会留下半截 JSON 让下次读取直接归零
            val tmp = configFile().resolveSibling("config.json.tmp")
            tmp.writeText(json.encodeToString(AppConfig.serializer(), onDisk))
            java.nio.file.Files.move(
                tmp.toPath(),
                configFile().toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
