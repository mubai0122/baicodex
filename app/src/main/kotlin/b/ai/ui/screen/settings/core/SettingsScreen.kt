/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:23
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.core

import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.kit.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.agent.memory.MemOrganizer
import b.ai.agent.memory.Memory
import b.ai.native.security.BxKernel
import b.ai.ui.dialog.guardStateText
import b.ai.ui.glass.glassScrollState
import b.ai.ui.dialog.GuardBusyDialog
import b.ai.ui.dialog.GuardOffDialog
import b.ai.ui.dialog.GuardProgressDialog
import b.ai.ui.dialog.GuardWarnDialog
import b.ai.ui.dialog.MaskWarnDialog
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.dialog.SimpleDialog
import b.ai.ui.screen.user.Rate
import b.ai.ui.widget.AvatarView
import b.ai.ui.dialog.FlowSheet
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.PackDialog
import b.ai.ui.dialog.PermAsk
import b.ai.ui.dialog.PermAskDialog
import b.ai.ui.glass.GlassPerf
import b.ai.ui.glass.ProvideCardBackdrop
import b.ai.ui.glass.backdropPageInset
import b.ai.ui.glass.drawPageBg
import b.ai.ui.glass.glassLazyListState
import b.ai.ui.glass.glassPageLayer
import b.ai.ui.glass.liquidBg
import b.ai.ui.glass.pageBg
import b.ai.ui.screen.stat.StatUi
import b.ai.ui.screen.stat.statTab
import b.ai.ui.widget.AvatarPick
import b.ai.core.data.db.Repo
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.Field
import b.ai.ui.widget.FloatTab
import b.ai.ui.widget.FloatTabBar
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcChevron
import b.ai.ui.widget.IcTrash
import b.ai.ui.widget.LocalPicker
import b.ai.ui.widget.MdText
import b.ai.ui.widget.PickItem
import b.ai.ui.widget.PickSheet
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.SelectField
import b.ai.ui.widget.WsPurge
import b.ai.ui.widget.WsPurgeDialog
import b.ai.ui.widget.tap
import b.ai.core.cfg.app.Bai
import b.ai.core.net.WebWipe
import b.ai.core.task.hook.Noti
import b.ai.device.sys.a11y.AutoA11y
import b.ai.device.pet.PetSvc
import b.ai.net.api.llm.text
import b.ai.net.api.llm.ThinkRules
import b.ai.net.mi.MiConf
import b.ai.native.security.MiCrypto

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Handler
import android.os.Build
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.run.Agent
import b.ai.agent.tool.Tools
import android.app.Activity
import b.ai.core.media.img.Avatar
import b.ai.core.model.voice.Audio
import b.ai.core.model.voice.AsrPresets
import b.ai.core.data.io.Backup
import b.ai.native.security.Guard
import b.ai.core.cfg.app.Cfg
import b.ai.device.sys.island.Island
import b.ai.device.sys.island.Vendor
import b.ai.core.cfg.app.Lang
import b.ai.net.mcp.Mcp
import b.ai.native.security.Mask
import b.ai.net.mcp.McpServer
import b.ai.core.cfg.env.Env
import b.ai.core.cfg.env.PromptVars
import b.ai.core.model.voice.Speak
import b.ai.core.perm.Perms
import b.ai.agent.persona.Persona
import b.ai.agent.persona.Personas
import b.ai.core.data.profile.Profiles
import b.ai.net.api.a2a.A2aNodes
import b.ai.agent.flow.Flows
import b.ai.net.api.a2a.A2aSrv
import b.ai.core.task.alarm.Schedules
import b.ai.core.task.hook.Triggers
import b.ai.net.search.Search
import b.ai.core.plugin.skill.Skill
import b.ai.core.plugin.skill.Skills
import b.ai.core.cfg.secret.Secret
import b.ai.core.plugin.skill.SkillPacks
import b.ai.core.plugin.skill.SkillPack
import b.ai.core.media.wall.Wallpaper
import b.ai.core.model.chat.WorkMode
import b.ai.device.shell.root.Shiz
import b.ai.device.sys.alarm.Alarms
import b.ai.device.app.FChat
import b.ai.device.shell.local.Linux
import b.ai.device.pet.Pet
import b.ai.device.sys.host.PetGrant
import b.ai.device.sys.host.SecureWin
import b.ai.device.sys.probe.Router
import b.ai.device.shell.local.Toolchain
import b.ai.device.fs.Workspace
import b.ai.net.api.update.Update
import b.ai.net.doc.MaskRules
import b.ai.MainActivity
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import b.ai.R
//miuix 风格滑杆自绘要用的一批：Canvas 画轨道、pointerInput 吃拖动、offset 摆滑块
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import b.ai.ui.theme.FontStore
import b.ai.ui.theme.LocalAiFont
import b.ai.ui.theme.LocalUserFont
import kotlin.math.roundToInt

internal val SettingsTabs = listOf(
    FloatTab(R.string.se_002) { c -> Icon(Icons.Filled.Email, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_001) { c -> Icon(Icons.Filled.Settings, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_003) { c -> Icon(Icons.Filled.Create, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_005) { c -> Icon(Icons.Filled.MoreVert, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_004) { c -> Icon(Icons.Filled.List, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_006) { c -> Icon(Icons.Filled.Info, null, tint = c, modifier = Modifier.size(18.dp)) }
) //设置分类：模型 / 对话 / 外观 / 扩展 / 统计 / 关于（权限已并入对话，语音并入模型；索引按标签匹配）

/** Markdown 排版预览用的样例：标题、正文、行内样式、列表、引用、代码块各一段，改参数立刻能看出差别 */
internal const val MD_PREVIEW = "## 小标题\n\n正文示例：这里有 `行内代码`、**加粗**和 *斜体*。\n\n- 一级列表项\n  - 二级列表项\n- 另一项\n\n> 引用的一行话\n\n```kotlin\nval hello = \"world\"\n```"

/** 色系主题名字，索引与 theme 的 ThemeTint 一一对应 */
internal val TINT_NAMES = listOf(
    R.string.se_tint_0,
    R.string.se_tint_1,
    R.string.se_tint_2,
    R.string.se_tint_3,
    R.string.se_tint_4,
    R.string.se_tint_5,
    R.string.se_tint_6,
    R.string.se_tint_7
)

/** 设置项索引：搜索只查这张表，命中后直接跳到对应 tab；tab 写标签而不是数字，避免换顺序时错位 */
internal data class SettingEntry(val labelRes: Int, val tabRes: Int, val hintRes: Int = 0, val hintRaw: String = "", val pageId: String? = null)
/** 页面级搜索词：新增子页只要加进 SePages 就能被搜到，不用再手写索引 */
internal val PageHints = mapOf(
    "model_cfg" to "提供商 APIKey 模型 能力 深度思考 切换",
    "model_param" to "温度 top_p token 参数 思考强度",
    //思考配置页此前漏了关键词，搜索「思考规则」找不到，补上
    "model_think" to "思考 思考规则 深度思考 reasoning 模型名匹配 下发字段 强度 预算 规则引擎",
    "model_orch" to "编排 任务分解 规划 拆解 DAG 步骤链 子任务 计划",
    "model_voice" to "TTS 朗读 音色 语速 语音识别 STT 麦克风",
    "model_mem" to "记忆 长期记忆 偏好 自动整理",
    "model_local" to "GGUF 本地模型 离线 量化 推理",
    "chat_ctx" to "上下文 轮数 压缩 摘要 用量",
    //注入已并入「注入输入与输出」页，关键词合到 chat_inout 那条上
    "chat_prompt" to "提示词优化 四芒星 模型 保留原语言 优化方向",
    "chat_inout" to "注入输入与输出 注入 时间 电池 位置 通知 用量 系统提示 回复信息 元信息 waifu 流式 草稿 输出偏好 文风",
    "chat_session" to "会话 历史 导出 收藏夹 工作区",
    "chat_tags" to "提示词 标签 片段 全局启用",
    "chat_perm" to "权限 工作方式 Shizuku Root 无障碍 悬浮窗 实验性",
    //收藏夹页此前漏了关键词
    "chat_star" to "收藏夹 收藏 星标 置顶 重要会话 归档",
    "ui_look" to "语言 主题色 字号 背景 玻璃质感 卡片 圆角 输入框 字体 深色",
    "ui_md" to "Markdown 行距 段间距 代码字号 留白 排版",
    "ui_person" to "头像 气泡 胶囊 桌宠",
    "ui_nav" to "悬浮聊天窗 手势 预测性返回 底栏 导航",
    "ui_emoji" to "表情 自定义 贴纸 分类 重置",
    "ext_auto" to "自动化 定时任务 工作流 通知触发",
    "ext_agent" to "角色 人设 技能 系统提示 技能包 专区",
    "ext_link" to "接入 MCP A2A 搜索平台 节点 服务",
    "ext_tool" to "工具 权限 开关 能力 路由 工具链",
    "ext_ws" to "工作区 目录 根目录 绑定 目录树",
    "ext_linux" to "Linux Ubuntu 沙箱 终端 proot 镜像源 依赖 自检 测速",
    "ext_dsh" to "DeepSeek Harness 引擎 打开 DeepSeek Harness 界面",
    "ext_dsh_plug" to "DeepSeek Harness 插件 广场 工具 技能 联网 MCP 开关 装载 cordis",
    "ext_hub" to "生态 专区 插件 广场 市场",
    "ext_mi" to "小米 米家 IoT 智能家居 设备",
    "ext_mi_login" to "小米 登录 账号 授权 扫码",
    "ext_mi_dev" to "小米 设备 局域网 扫描 属性 台账",
    "ext_mi_ch" to "小米 通道 区域 诊断",
    "ext_music" to "音乐 网易云 QQ音乐 酷我 音质",
    "ext_lan" to "局域网 配对码 发现 传输 端口",
    "ext_box" to "盒子 目录 白名单 读写 越界",
    "stat" to "统计 用量 token 次数 分布 方格",
    "about" to "关于 版本 更新 开源许可 反馈 开发者模式"
)

/** 页面级索引：从 SePages 自动展开，命中后直接落到该子页 */
internal val pageEntries: List<SettingEntry> by lazy {
    //cat → 底栏标签：0 模型 / 1 对话 / 2 外观 / 3 扩展 / 4 统计 / 5 关于
    val tabs = listOf(R.string.se_001, R.string.se_002, R.string.se_003, R.string.se_005, R.string.se_004, R.string.se_006)
    SePages.map { p ->
        SettingEntry(
            labelRes = p.titleRes,
            tabRes = tabs.getOrElse(p.cat) { R.string.se_001 },
            hintRes = p.subRes,
            hintRaw = PageHints[p.id].orEmpty(),
            pageId = p.id
        )
    }
} //页面级索引

internal val SettingIndex = pageEntries + listOf(
    SettingEntry(R.string.se_007, R.string.se_001, R.string.se_008, pageId = "model_cfg"),
    SettingEntry(R.string.se_009, R.string.se_001, R.string.se_010, pageId = "model_cfg"),
    SettingEntry(R.string.se_011, R.string.se_001, R.string.se_012, pageId = "model_cfg"),
    SettingEntry(R.string.se_013, R.string.se_001, R.string.se_014, pageId = "model_param"),
    SettingEntry(R.string.se_v_in_t, R.string.se_001, R.string.se_v_in_d, pageId = "model_voice"),
    SettingEntry(R.string.se_v_out_t, R.string.se_001, R.string.se_v_out_d, pageId = "model_voice"),
    SettingEntry(R.string.se_124, R.string.se_001, R.string.se_125, pageId = "model_voice"),
    SettingEntry(R.string.se_015, R.string.se_002, R.string.se_016, pageId = "chat_ctx"),
    SettingEntry(R.string.se_017, R.string.se_002, R.string.se_018, pageId = "chat_inout"),
    SettingEntry(R.string.se_019, R.string.se_002, R.string.se_020, pageId = "chat_inout"),
    SettingEntry(R.string.se_019, R.string.se_002, hintRaw = "草稿 图片 有效期 输入不丢", pageId = "chat_inout"),
    SettingEntry(R.string.se_019, R.string.se_002, hintRaw = "输出偏好 文风 常驻提示", pageId = "chat_inout"),
    SettingEntry(R.string.se_021, R.string.se_002, R.string.se_022, pageId = "chat_session"),
    SettingEntry(R.string.se_023, R.string.se_002, R.string.se_024, pageId = "chat_session"),
    SettingEntry(R.string.se_025, R.string.se_002, R.string.se_026, pageId = "chat_perm"),
    SettingEntry(R.string.se_027, R.string.se_002, R.string.se_028, pageId = "chat_perm"),
    SettingEntry(R.string.se_029, R.string.se_002, hintRaw = "", pageId = "chat_perm"),
    SettingEntry(R.string.se_030, R.string.se_003, R.string.se_031, pageId = "ui_look"),
    SettingEntry(R.string.se_032, R.string.se_003, hintRaw = "", pageId = "ui_look"),
    SettingEntry(R.string.se_033, R.string.se_003, R.string.se_034, pageId = "ui_look"),
    SettingEntry(R.string.se_035, R.string.se_003, R.string.se_036, pageId = "ui_look"),
    SettingEntry(R.string.se_037, R.string.se_003, hintRaw = "预测性返回 手势", pageId = "ui_nav"),
    //外观总页整包：Markdown 排版、卡片外观、玻璃质感都在这一页，搜索命中后直接落进去
    SettingEntry(R.string.se_pg_ui_theme, R.string.se_003, hintRaw = "卡片圆角 玻璃 背景 主题色 字号 输入框 回到底部按钮", pageId = "ui_look"),
    SettingEntry(R.string.se_pg_md, R.string.se_003, hintRaw = "Markdown 行距 段间距 缩进 留白 代码字号 排版 预览", pageId = "ui_md"),
    //外观新增的字体与控件外观：字体文件 / 各气泡字体 / 全局缩进 / 弹窗圆角 / miuix 滑块
    SettingEntry(R.string.se_032, R.string.se_003, hintRaw = "字体 字体文件 气泡字体 导入 全局缩进 弹窗圆角 滑块 miuix 轨道 主题色", pageId = "ui_look"),
    //余额通知：个性化页最下方的开关与阈值滑块
    SettingEntry(R.string.se_bal, R.string.se_003, hintRaw = "余额 余额通知 低余额提醒 阈值 DeepSeek 充值", pageId = "ui_person"),
    //会话列表：置顶 / 重命名 / 删除确认、C 端后缀、从 DeepSeek 导入历史
    SettingEntry(R.string.se_pg_chat_session, R.string.se_002, hintRaw = "会话列表 重命名 删除 置顶 从 DeepSeek 导入历史 C 端 后缀 工作区", pageId = "chat_session"),
    SettingEntry(R.string.tx_star_page, R.string.se_002, hintRaw = "收藏夹 收藏 星标", pageId = "chat_star"),
    SettingEntry(R.string.se_038, R.string.se_004, R.string.se_039, pageId = "stat"),
    SettingEntry(R.string.se_040, R.string.se_004, R.string.se_041, pageId = "stat"),
    SettingEntry(R.string.se_042, R.string.se_004, R.string.se_043, pageId = "stat"),
    SettingEntry(R.string.se_044, R.string.se_005, R.string.se_045, pageId = "ext_auto"),
    SettingEntry(R.string.se_046, R.string.se_005, hintRaw = "web_search", pageId = "ext_link"),
    SettingEntry(R.string.se_047, R.string.se_005, hintRaw = "", pageId = "ext_agent"),
    SettingEntry(R.string.se_048, R.string.se_005, hintRaw = "", pageId = "ext_agent"),
    SettingEntry(R.string.se_049, R.string.se_005, hintRaw = "", pageId = "ext_agent"),
    SettingEntry(R.string.mcp_title, R.string.se_005, R.string.mcp_sub, pageId = "ext_link"),
    SettingEntry(R.string.se_050, R.string.se_005, hintRaw = "", pageId = "ext_tool"),
    SettingEntry(R.string.se_051, R.string.se_005, hintRaw = "JDK / gradle / Android SDK", pageId = "ext_tool"),
    SettingEntry(R.string.se_052, R.string.se_005, R.string.se_053, pageId = "ext_linux"),
    SettingEntry(R.string.se_054, R.string.se_006, R.string.se_055, pageId = "about"),
    SettingEntry(R.string.se_gd_title, R.string.se_006, R.string.se_sec_sub, pageId = "about")
) //搜索索引

/** 按标签找 tab 下标，找不到就落回 0 */
internal fun tabIndex(res: Int): Int =
    SettingsTabs.indexOfFirst { it.labelRes == res }.coerceAtLeast(0)

/** 设置：底部悬浮 tab 分类，各分类独立滚动；backProgress 是返回手势进度，用来做跟手跟退 */
@Composable
fun SettingsScreen(
    backProgress: Float = 0f,
    initialTab: Int = 0,
    onOpenFeedback: () -> Unit = {},
    onOpenPet: () -> Unit = {},
    onOpenStars: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenDsh: () -> Unit = {},
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    //统计要读 SQLite，放 IO 线程，避免进设置页时卡主线程
    android.util.Log.w("Boot", "settings enter")
    var stats by remember { mutableStateOf(0 to 0) }
    var tab by remember { mutableStateOf(initialTab) }
    var draft by remember { mutableStateOf<Skill?>(null) }
    var personaDraft by remember { mutableStateOf<Persona?>(null) }
    var mcpDraft by remember { mutableStateOf<McpServer?>(null) }
    //技能包详情：就地改 README / skill.json / 模板，填它声明出来的密钥
    var packDraft by remember { mutableStateOf<SkillPack?>(null) }
    //赞赏码与 DeepSeek 导入：两个独立弹窗，与其它弹窗共用一个根挂载点
    var rewardOpen by remember { mutableStateOf(false) }
    var dsImportOpen by remember { mutableStateOf(false) }
    var taskDraft by remember { mutableStateOf(false) }
    var flowDraft by remember { mutableStateOf(false) }
    var a2aDraft by remember { mutableStateOf(false) }
    //通知触发规则面板：跟定时任务同一套弹窗挂载方式
    var triggerDraft by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var fq by remember { mutableStateOf("") }
    //定位提示：从搜索结果跳进子页时，把「你要找的那一项」钉在页首，滚走了也知道找的是哪个
    var locate by remember { mutableStateOf("") }
    //下拉选择面板的当前内容：非空时挂到根弹窗层，和其它弹窗互斥
    var pick by remember { mutableStateOf<Pair<String, List<PickItem>>?>(null) }
    //统计页拆成每个块一个 item 后，各块不能再自己 remember，状态统一提到这里
    val statUi = remember { StatUi() }
    //切分类：新内容第一帧先走平价玻璃再错峰升级（同帧建几十个离屏节点会顶出 200ms 长帧），并回到顶部
    //进页面这一次也按「刚换过内容」算：首帧先平涂，别在进页面那一帧同时建一堆离屏节点
    remember { GlassPerf.switched(); Unit }
    val scroll = glassLazyListState()
    LaunchedEffect(tab, SeNav.top?.id) {
        GlassPerf.switched()
        scroll.scrollToItem(0)
    }
    LaunchedEffect(Agent.messages.size, Agent.sessionId) {
        stats = withContext(Dispatchers.IO) { Agent.stats() }
    }

    //页面层：底栏的玻璃就采样这一层（底色先垫一发，缺内容的地方不至于透成黑）
    val (pageTop, pageBottom) = backdropPageInset()
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        //采样层必须含真背景：只垫纯色的话，底栏玻璃在没内容的地方采到的就是一块灰白
        drawPageBg(offsetY = -pageTop, fullHeight = size.height + pageTop + pageBottom)
        drawContent()
    }
    //卡片玻璃只采纯背景那一层：不能采 pageBackdrop，卡片自己也在里面，采到的会是上一帧的自己
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }

    ProvideCardBackdrop(cardBackdrop) {
    Box(
        Modifier
            .fillMaxSize()
            //返回手势跟手：整页右移 + 轻微缩小 + 淡出，松手没滑过阈值就归位
            .graphicsLayer {
                translationX = size.width * 0.22f * backProgress
                scaleX = 1f - 0.05f * backProgress
                scaleY = 1f - 0.05f * backProgress
                alpha = (1f - 0.55f * backProgress).coerceIn(0f, 1f)
            }
    ) {
        //背景单开一层给卡片取样；内容再套一层吃系统栏内边距
        Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().glassPageLayer(pageBackdrop)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //顶栏只留「设置」一个标题：分类名挪到各卡片上方的 SectionTitle，避免同屏两个标题打架
                Text(
                    stringResource(R.string.se_056),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .liquidBg(RoundedCornerShape(9.dp), tint = if (find) AccentBlueSoft else SurfaceWhite, gate = 0.dp)
                        .tap { find = !find; fq = "" }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.se_058),
                        tint = if (find) AccentBlue else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft, gate = 0.dp)
                        //逐级返回：栈里还有子页就先回上一层，栈空才退出设置
                        .tap {
                            if (SeNav.top != null) {
                                GlassPerf.switched()
                                SeNav.pop()
                            } else {
                                SeNav.clear()
                                onBack()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.se_059),
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.se_059), fontSize = 11.sp, color = AccentBlue)
                }
            }

            //搜索：点右上角放大镜后原位形变成一条横跨的长胶囊，自上而下撑开，不覆盖正文
            val focusSearch = remember { FocusRequester() }
            AnimatedVisibility(
                visible = find,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
                        .height(38.dp)
                        //长胶囊：全圆角；输入框不做玻璃（veil 在深色下是一层黑遮罩，看着像阴影）
                        .background(SurfaceMuted, RoundedCornerShape(50))
                        .border(1.dp, BorderGray, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (fq.isBlank()) TextSecondary else AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (fq.isEmpty()) {
                            Text(stringResource(R.string.se_060), fontSize = 12.sp, color = TextSecondary)
                        }
                        BasicTextField(
                            value = fq,
                            onValueChange = { fq = it },
                            textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                            singleLine = true,
                            cursorBrush = SolidColor(AccentBlue),
                            //展开即聚焦：不用再点一下输入框，键盘直接起来
                            modifier = Modifier.fillMaxWidth().focusRequester(focusSearch)
                        )
                    }
                    //清空：有字才出现，清掉后保持在搜索态继续改词
                    if (fq.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(18.dp).tap { fq = "" }, contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.se_061),
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                //展开动画结束后再抢焦点，太早会落在还没上屏的节点上
                LaunchedEffect(find) { if (find) runCatching { focusSearch.requestFocus() } }
            }

            //内容区：懒加载。原来是 Column + verticalScroll，切一次分类就把整个分类的几十张卡片和上百个文本
            //一起组合、一起排版，实测顶出 109~197ms 长帧；换成 LazyColumn 后只组合看得见的那几张卡
            CompositionLocalProvider(LocalPicker provides { t, l -> pick = t to l }) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                state = scroll,
                //底部让位必须走 contentPadding：列表要一直滚到屏幕底、从悬浮底栏下面穿过去，
                //底栏的玻璃才有东西可采。以前写 Modifier.padding(bottom = 104.dp)，内容在底栏上方
                //就被硬裁断，底栏和内容之间还剩一条空带，看着像底栏自带一块填充
                contentPadding = PaddingValues(bottom = 86.dp)
            ) {
                //定位胶囊：从搜索结果跳进来时钉在页首，说清「你要找的是哪一项」，点×收起
                if (locate.isNotBlank()) {
                    item("locate") { LocatePill(locate) { locate = "" } }
                }
                when {
                    find && fq.isNotBlank() -> {
                        //搜索结果只认索引表：点一条就跳到它所属的 tab
                        val key = fq.trim()
                        val hits = SettingIndex.filter {
                            ctx.getString(it.labelRes).contains(key, true) ||
                                (if (it.hintRes != 0) ctx.getString(it.hintRes) else it.hintRaw).contains(key, true)
                        }
                        if (hits.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.tx_7725233f, fq.trim()),
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        } else {
                            items(hits) { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .liquidBg(RoundedCornerShape(12.dp), gate = 0.dp)
                                        .tap {
                                            GlassPerf.switched()
                                            find = false
                                            fq = ""
                                            //记住命中的设置名：进页后页首给一条定位胶囊
                                            locate = ctx.getString(e.labelRes)
                                            //搜索直接落到具体子页：先按索引表的 pageId 走，没写就退到该分类的默认子页
                                            val pid = e.pageId ?: seDefaultPage(tabIndex(e.tabRes))?.id
                                            val pg = pid?.let { sePage(it) }
                                            if (pg != null) {
                                                tab = pg.cat
                                                SeNav.clear()
                                                SeNav.push(pg)
                                            } else tab = tabIndex(e.tabRes)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(e.labelRes), fontSize = 13.sp, color = TextPrimary)
                                        if (e.hintRes != 0 || e.hintRaw.isNotBlank()) {
                                            Text(
                                                if (e.hintRes != 0) stringResource(e.hintRes) else e.hintRaw,
                                                fontSize = 10.sp, color = TextSecondary, maxLines = 1
                                            )
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.se_tab_jump, stringResource(e.tabRes)),
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    find -> item {
                        Text(
                            stringResource(R.string.se_063),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    else -> {
                    //栈里有页 = 渲染该页分区；栈空 = 该分类有多页就列入口，只有直连内容（统计/关于）才直接渲染
                    val tp = SeNav.top
                    val pid = tp?.id
                    //统计 / 关于：该分类只有一页且标了 direct，点底栏直接渲染内容，不出分类页
                    val pages = sePagesOf(tab)
                    val directId = if (pid == null && pages.size == 1 && pages[0].direct) pages[0].id else null
                    //栈空且不是直连页 = 停在分类页列子页入口；不能拿默认子页顶替，否则多页分类只剩第一页能进
                    val key = pid ?: directId
                    //二级页不再画左侧返回条：标题栏右上角那一个返回键逐级后退，避免两个返回并排

                    //内容一律按子页 key 过滤分区，不能按底栏下标：同一批内容函数按 key 各取一段
                    when (key) {
                        null -> seCatHome(SettingsTabs[tab].labelRes, "", sePagesOf(tab)) { p -> SeNav.push(p) }
                        "model_cfg" -> modelCfgSec(onOpenMemory)
                        "model_param" -> modelParamSec()
                        "model_think" -> modelThinkSec()
                        "model_orch" -> modelOrchSec()
                        "model_voice" -> modelVoiceSec()
                        "model_mem" -> modelMemSec(onOpenMemory)
                        "model_local" -> modelLocalSec()
                        "chat_ctx" -> chatCtxSec(stats)
                        "chat_prompt" -> chatPromptSec()
                        "chat_inout" -> chatInOutSec()
                        "chat_session" -> chatSessionSec(stats) { dsImportOpen = true }
                        "chat_star" -> chatStarSec()
                        "chat_tags" -> promptTagSec()
                        "chat_perm" -> chatPermSec()
                        "ui_look" -> uiLookSec()
                        "ui_md" -> mdSec()
                        "ui_person" -> uiPersonSec(onOpenPet)
                        "ui_nav" -> uiNavSec()
                        "ui_emoji" -> emojiSec()
                        "ext_auto" -> extAutoSec(
                            onNewTask = { taskDraft = true },
                            onNewFlow = { flowDraft = true },
                            onNewTrigger = { triggerDraft = true }
                        )
                        "ext_agent" -> extAgentSec(
                            onEdit = { draft = it },
                            onEditPersona = { personaDraft = it },
                            onEditPack = { packDraft = it }
                        )
                        "ext_link" -> extLinkSec(
                            onOpenA2a = { a2aDraft = true },
                            onEditMcp = { mcpDraft = it }
                        )
                        "ext_tool" -> extToolSec()
                        "ext_ws" -> extWsSec()
                        "ext_linux" -> extLinuxSec()
                        //dsh 引擎：点进去是全屏 dsh 界面（内核由页面自己拉起）
                        "ext_dsh" -> extDshEngineSec(onOpenDsh)
                        //dsh 插件广场：本机真实装载行（读 profile 的 bundle patch）
                        "ext_dsh_plug" -> extDshPlugSec()
                        //生态与专区：返回条已经带标题，这里只列四个专区入口，不重复写标题
                        "ext_hub" -> item {
                            CardBox {
                                seChildren("ext_hub").forEachIndexed { i, p ->
                                    if (i > 0) Spacer(Modifier.height(2.dp))
                                    SeRow(p) { GlassPerf.switched(); SeNav.push(p) }
                                }
                            }
                        }
                        "ext_mi" -> miHubSec()
                        "ext_mi_login" -> miLoginSec()
                        "ext_mi_dev" -> miDevSec()
                        "ext_mi_ch" -> miChSec()
                        "ext_music" -> musicSec()
                        "ext_lan" -> lanSec()
                        "ext_box" -> extPlanSec(R.string.se_hub_box, R.string.se_pg_box_sub, EX_BOX_PLAN)
                        "stat" -> statTab(statUi)
                        "about" -> aboutTab(onOpenFeedback) { rewardOpen = true }
                        else -> seCatHome(SettingsTabs[tab].labelRes, "", sePagesOf(tab)) { p -> SeNav.push(p) }
                    }
                    }
                }
                }
            }
            }
        FloatTabBar(
            tabs = SettingsTabs,
            selected = tab,
            onSelect = { GlassPerf.switched(); SeNav.clear(); tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
            backdrop = pageBackdrop
        )
    }
    }
    }
    //技能/角色/定时任务弹窗共用一个挂载点，同一时刻只会有一个非空
    HostInOverlay(active = draft != null || personaDraft != null || mcpDraft != null || taskDraft || flowDraft || a2aDraft || triggerDraft || pick != null || packDraft != null || PermAsk.kind.value != null || WsPurge.open || WorkspacePick.open || ThinkEdit.rule != null || rewardOpen || dsImportOpen || SessionDialogs.open) {
        //思考规则编辑：新建追加到末尾，编辑替换同 id
        ThinkEdit.rule?.let { r ->
            ThinkRuleDialog(
                rule = r,
                fresh = ThinkEdit.fresh,
                onSave = { saved ->
                    val list = ThinkRules.list()
                    ThinkRules.save(
                        if (ThinkEdit.fresh) list + saved
                        else list.map { if (it.id == saved.id) saved else it }
                    )
                    ThinkEdit.rule = null
                },
                onDelete = if (ThinkEdit.fresh) null else {
                    {
                        ThinkRules.save(ThinkRules.list().filter { it.id != r.id })
                        ThinkEdit.rule = null
                    }
                },
                onClose = { ThinkEdit.rule = null }
            )
        }
        //注入类权限的提示：开关一开就弹，讲清要什么再送去系统页
        PermAsk.kind.value?.let { k -> PermAskDialog(k) { PermAsk.kind.value = null } }
        packDraft?.let { p -> PackDialog(p, onClose = { packDraft = null }) }
        draft?.let { s ->
            SkillDialog(
                skill = s,
                onSave = { n, d, b ->
                    if (s.id.isBlank()) Skills.add(n, d, b) else Skills.update(s.id, n, d, b)
                    draft = null
                    //写不进数据根必须当场说清：以前这里静默，重启才发现改动没了
                    Skills.lastError.takeIf { it.isNotEmpty() }?.let {
                        android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                onDelete = {
                    if (s.id.isNotBlank()) Skills.remove(s.id)
                    draft = null
                    Skills.lastError.takeIf { it.isNotEmpty() }?.let {
                        android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                onClose = { draft = null }
            )
        }
        personaDraft?.let { p ->
            PersonaDialog(
                persona = p,
                onSave = { card ->
                    if (p.id.isBlank()) Personas.add(card) else Personas.update(card)
                    personaDraft = null
                },
                onDelete = {
                    if (p.id.isNotBlank()) Personas.remove(p.id)
                    personaDraft = null
                },
                onClose = { personaDraft = null }
            )
        }
        mcpDraft?.let { s ->
            McpDialog(
                server = s,
                onSave = { ns ->
                    if (ns.id.isBlank()) Mcp.add(ns.name, ns.url, ns.token, ns.kind) else Mcp.upsert(ns)
                    mcpDraft = null
                },
                onDelete = {
                    if (s.id.isNotBlank()) Mcp.remove(s.id)
                    mcpDraft = null
                },
                onClose = { mcpDraft = null }
            )
        }
        if (taskDraft) TaskDialog(onClose = { taskDraft = false })
        if (flowDraft) FlowSheet(onClose = { flowDraft = false })
        if (a2aDraft) A2aPanel(onClose = { a2aDraft = false })
        if (triggerDraft) TriggerDialog(onClose = { triggerDraft = false })
        pick?.let { (t, l) -> PickSheet(t, l) { pick = null } }
        if (WsPurge.open) WsPurgeDialog { WsPurge.open = false }
        //工作区「浏览」：目录选择器与绑定卡是同一个弹窗，在它内部切换
        if (WorkspacePick.open) WorkspaceDialog { WorkspacePick.open = false }
        //赞赏码 / DeepSeek 历史导入：都是独立整页弹窗，关掉即回到设置页
        if (rewardOpen) RewardDialog { rewardOpen = false }
        if (dsImportOpen) DeepSeekImportDialog { dsImportOpen = false }
        //会话重命名 / 删除确认：必须在根弹窗层渲染，挂在列表 item 里会被 LazyColumn 裁剪到列表下方
        if (SessionDialogs.open) SessionDialogsHost()
    }
} //设置界面

