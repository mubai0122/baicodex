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

package b.ai.ui.screen.settings
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

private val SettingsTabs = listOf(
    FloatTab(R.string.se_001) { c -> Icon(Icons.Filled.Settings, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_002) { c -> Icon(Icons.Filled.Email, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_003) { c -> Icon(Icons.Filled.Create, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_005) { c -> Icon(Icons.Filled.MoreVert, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_004) { c -> Icon(Icons.Filled.List, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_006) { c -> Icon(Icons.Filled.Info, null, tint = c, modifier = Modifier.size(18.dp)) }
) //设置分类：模型 / 对话 / 外观 / 扩展 / 统计 / 关于（权限已并入对话，语音并入模型；索引按标签匹配）

/** Markdown 排版预览用的样例：标题、正文、行内样式、列表、引用、代码块各一段，改参数立刻能看出差别 */
private const val MD_PREVIEW = "## 小标题\n\n正文示例：这里有 `行内代码`、**加粗**和 *斜体*。\n\n- 一级列表项\n  - 二级列表项\n- 另一项\n\n> 引用的一行话\n\n```kotlin\nval hello = \"world\"\n```"

/** 色系主题名字，索引与 theme 的 ThemeTint 一一对应 */
private val TINT_NAMES = listOf(
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
private data class SettingEntry(val labelRes: Int, val tabRes: Int, val hintRes: Int = 0, val hintRaw: String = "", val pageId: String? = null)
/** 页面级搜索词：新增子页只要加进 SePages 就能被搜到，不用再手写索引 */
private val PageHints = mapOf(
    "model_cfg" to "提供商 APIKey 模型 能力 深度思考 切换",
    "model_param" to "温度 top_p token 参数 思考强度",
    "model_voice" to "TTS 朗读 音色 语速 语音识别 STT 麦克风",
    "model_mem" to "记忆 长期记忆 偏好 自动整理",
    "model_local" to "GGUF 本地模型 离线 量化 推理",
    "chat_ctx" to "上下文 轮数 压缩 摘要 用量",
    "chat_inject" to "注入 时间 电池 位置 通知 用量 系统提示",
    "chat_prompt" to "提示词优化 四芒星 模型 保留原语言 优化方向",
    "chat_inout" to "输入与输出 回复信息 用量 元信息 waifu 流式 草稿 输出偏好 文风",
    "chat_session" to "会话 历史 导出 收藏夹 工作区",
    "chat_tags" to "提示词 标签 片段 全局启用",
    "chat_perm" to "权限 工作方式 Shizuku Root 无障碍 悬浮窗 实验性",
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
private val pageEntries: List<SettingEntry> by lazy {
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

private val SettingIndex = pageEntries + listOf(
    SettingEntry(R.string.se_007, R.string.se_001, R.string.se_008, pageId = "model_cfg"),
    SettingEntry(R.string.se_009, R.string.se_001, R.string.se_010, pageId = "model_cfg"),
    SettingEntry(R.string.se_011, R.string.se_001, R.string.se_012, pageId = "model_cfg"),
    SettingEntry(R.string.se_013, R.string.se_001, R.string.se_014, pageId = "model_param"),
    SettingEntry(R.string.se_v_in_t, R.string.se_001, R.string.se_v_in_d, pageId = "model_voice"),
    SettingEntry(R.string.se_v_out_t, R.string.se_001, R.string.se_v_out_d, pageId = "model_voice"),
    SettingEntry(R.string.se_124, R.string.se_001, R.string.se_125, pageId = "model_voice"),
    SettingEntry(R.string.se_015, R.string.se_002, R.string.se_016, pageId = "chat_ctx"),
    SettingEntry(R.string.se_017, R.string.se_002, R.string.se_018, pageId = "chat_inject"),
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
private fun tabIndex(res: Int): Int =
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
    var taskDraft by remember { mutableStateOf(false) }
    var flowDraft by remember { mutableStateOf(false) }
    var a2aDraft by remember { mutableStateOf(false) }
    //通知触发规则面板：跟定时任务同一套弹窗挂载方式
    var triggerDraft by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var fq by remember { mutableStateOf("") }
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

            if (find) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        .liquidBg(RoundedCornerShape(12.dp), gate = 0.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(7.dp))
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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
                                    stringResource(R.string.tx_7725233f),
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
                        "model_voice" -> modelVoiceSec()
                        "model_mem" -> modelMemSec(onOpenMemory)
                        "model_local" -> modelLocalSec()
                        "chat_ctx" -> chatCtxSec(stats)
                        "chat_inject" -> chatInjectSec()
                        "chat_prompt" -> chatPromptSec()
                        "chat_inout" -> chatInOutSec()
                        "chat_session" -> chatSessionSec(stats, onOpenStars)
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
                        "about" -> aboutTab(onOpenFeedback)
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
    HostInOverlay(active = draft != null || personaDraft != null || mcpDraft != null || taskDraft || flowDraft || a2aDraft || triggerDraft || pick != null || packDraft != null || PermAsk.kind.value != null || WsPurge.open || WorkspacePick.open || ThinkEdit.rule != null) {
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
    }
} //设置界面

//模型分类：拆成一个个 item，LazyColumn 只组合看得见的那几张卡
private fun LazyListScope.modelCfgSec(onOpenMemory: () -> Unit) {
    item {
        SectionTitle(stringResource(R.string.se_007), stringResource(R.string.se_064))
        ProfileList()
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_009))
        CardBox { ModelSetupPanel() }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_011), stringResource(R.string.se_065))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_066),
                if (Cfg.modelVision) stringResource(R.string.se_067) else stringResource(R.string.se_068),
                Cfg.modelVision
            ) { Cfg.modelVision = it; Profiles.capture() }
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_069),
                if (Cfg.modelToolCall) stringResource(R.string.se_070)
                else stringResource(R.string.se_071),
                Cfg.modelToolCall
            ) { Cfg.modelToolCall = it; Profiles.capture() }
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_072),
                if (Cfg.modelReasoning) stringResource(R.string.se_073) else stringResource(R.string.se_074),
                Cfg.modelReasoning
            ) { Cfg.modelReasoning = it; Profiles.capture() }
            Spacer(Modifier.height(8.dp))
            //原生联网搜索：厂商不支持时开关强制显示为关，点了也不改配置，避免下发不认识字段打 400
            val ctxN = LocalContext.current
            val nsOk = Profiles.supportsNativeSearch(Cfg.providerId)
            CapSwitch(
                stringResource(R.string.se_386),
                when {
                    !nsOk -> stringResource(R.string.se_389)
                    Cfg.modelNativeSearch -> stringResource(R.string.se_387)
                    else -> stringResource(R.string.se_388)
                },
                Cfg.modelNativeSearch && nsOk
            ) { on ->
                if (nsOk) {
                    //静默互斥：DeepSeek 反过来把思考打开，其它厂商把思考关掉，都不弹提示
                    if (on) Cfg.think = Profiles.searchNeedsThink(Cfg.providerId)
                    Cfg.modelNativeSearch = on
                    Profiles.capture()
                }
            }
            Spacer(Modifier.height(10.dp))
            //思考档位：按参考图改成可展开的档位编辑器（显示名 / 写入路径 / 写入值），
            //不再是一个逗号串输入框——档位数量和每个档位写什么值本来就是两件事
            var profLv by remember(Cfg.profileId) { mutableStateOf(profLevels()) }
            var profOpen by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().tap { profOpen = !profOpen }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tx_c56fbda3), fontSize = 13.5.sp, color = TextPrimary)
                        Text(
                            if (profLv.isEmpty()) stringResource(R.string.tx_707c976d) else stringResource(R.string.tx_8bdbcde3, profLv.size),
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                    IcChevron(TextSecondary, down = !profOpen, side = 13.dp)
                }
                AnimatedVisibility(
                    visible = profOpen,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(80)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
                ) {
                    Column {
                        LevelEditor(profLv, "reasoning_effort", "\"medium\"") { list ->
                            profLv = list
                            //两份一起写：名字串给滑块显示，明细给请求下发；配置项跟着落盘
                            Cfg.profThinkLevels = list.joinToString(",") { it.label.trim() }.trim(',', ' ')
                            val arr = org.json.JSONArray()
                            list.forEach { arr.put(it.toJson()) }
                            Cfg.profThinkLevelsJson = arr.toString()
                            Profiles.capture()
                        }
                        Text(
                            stringResource(R.string.tx_f0365c26),
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
    } //模型配置子页

//模型参数子页：深度思考、温度与额外请求参数
private fun LazyListScope.modelParamSec() {
    item {
        //思考/搜索互斥提示要用到，提前取一次
        val ctxP = LocalContext.current
        SectionTitle(stringResource(R.string.se_013), stringResource(R.string.se_075))
        CardBox {
            //思考常显：不再按模型名单门控，发不发只看「模型能力 → 推理 / 思考参数」
            if (Cfg.modelReasoning || Cfg.think) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.se_076), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (Cfg.think) stringResource(R.string.se_077) else stringResource(R.string.se_078),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = Cfg.think,
                    onCheckedChange = { on ->
                        //静默互斥：关思考时把搜索一起收掉（DeepSeek 的搜索不能脱离思考），不弹提示
                        if (!on && Cfg.modelNativeSearch) Cfg.modelNativeSearch = false
                        Cfg.think = on
                        Profiles.capture()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.se_079),
                Cfg.thinkEffort,
                listOf("low", "medium", "high"),
                optionLabel = { it },
                isSelected = { Cfg.thinkEffort == it },
                onSelect = { Cfg.thinkEffort = it }
            )
            } //仅支持思考的模型才显示深度思考
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.se_080),
                if (Cfg.maxMode) stringResource(R.string.se_081) else stringResource(R.string.se_082),
                Cfg.maxMode
            ) { Cfg.maxMode = it }
            Spacer(Modifier.height(8.dp))
            //原生联网搜索：与「模型能力」里同一项，参数页也放一份方便调；厂商不支持时恒为关且点不动
            val ctx = LocalContext.current
            val nsOk = Profiles.supportsNativeSearch(Cfg.providerId)
            CapSwitch(
                stringResource(R.string.se_386),
                when {
                    !nsOk -> stringResource(R.string.se_389)
                    Cfg.modelNativeSearch -> stringResource(R.string.se_387)
                    else -> stringResource(R.string.se_388)
                },
                Cfg.modelNativeSearch && nsOk
            ) { on ->
                when {
                    //厂商不支持：点不动
                    !nsOk -> Unit
                    //DeepSeek：搜索必须在思考模式下 → 允许开搜索，同时自动把思考打开，不做拦截
                    on && Profiles.searchNeedsThink(Cfg.providerId) -> {
                        Cfg.modelNativeSearch = true
                        if (!Cfg.think) {
                            Cfg.think = true
                            android.widget.Toast.makeText(
                                ctx,
                                ctx.getString(R.string.tx_8e1b47f0),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        Profiles.capture()
                    }
                    //其它厂商：与思考互斥，思考开着时不允许打开搜索，只提示不改另一个开关
                    on && Cfg.think -> android.widget.Toast.makeText(
                        ctx,
                        ctx.getString(R.string.tx_ec691c16),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    else -> {
                        Cfg.modelNativeSearch = on
                        Profiles.capture()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.se_083),
                if (Cfg.temperature < 0f) stringResource(R.string.se_084) else Cfg.temperature.toString(),
                listOf(-1f, 0f, 0.3f, 0.7f, 1f, 1.3f),
                optionLabel = { if (it < 0f) stringResource(R.string.se_084) else it.toString() },
                isSelected = { kotlin.math.abs(Cfg.temperature - it) < 0.01f },
                onSelect = { Cfg.temperature = it }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.se_085),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            //创造性参数：只影响「怎么写」不影响「写什么」，跟温度归一组
            Text(stringResource(R.string.tx_c71ac55d), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            //参数改成预设下拉：滑杆调 top_p 会调出 0.83 这种没意义的中间值，预设档更好选也更好复现
            SelectField(
                stringResource(R.string.pm_top_p),
                value = if (Cfg.topP < 0f) stringResource(R.string.tx_18c63459) else String.format("%.2f", Cfg.topP),
                desc = stringResource(R.string.pm_top_p_key),
                options = listOf(-1f, 0.5f, 0.7f, 0.8f, 0.9f, 0.95f, 1f),
                optionLabel = { if (it < 0f) stringResource(R.string.tx_9559ce64) else String.format("%.2f", it) },
                isSelected = { kotlin.math.abs(Cfg.topP - it) < 0.001f },
                onSelect = { Cfg.topP = it }
            )
            SelectField(
                stringResource(R.string.pm_top_k),
                value = if (Cfg.topK <= 0) stringResource(R.string.tx_18c63459) else Cfg.topK.toString(),
                desc = stringResource(R.string.pm_top_k_key),
                options = listOf(0, 20, 40, 60, 80, 100),
                optionLabel = { if (it <= 0) stringResource(R.string.tx_9559ce64) else it.toString() },
                isSelected = { Cfg.topK == it },
                onSelect = { Cfg.topK = it }
            )
            Text(
                stringResource(R.string.tx_42c4c7c5),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            //重复控制：治「车轱辘话」「复读同一个词」，跟创造性分开一组更好找
            Text(stringResource(R.string.tx_69c6c8f3), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            SelectField(
                stringResource(R.string.pm_presence),
                value = if (Cfg.presencePenalty < 0f) stringResource(R.string.tx_18c63459) else String.format("%.1f", Cfg.presencePenalty),
                desc = stringResource(R.string.pm_presence_key),
                options = listOf(-1f, 0f, 0.3f, 0.5f, 1f, 1.5f, 2f),
                optionLabel = { if (it < 0f) stringResource(R.string.tx_9559ce64) else String.format("%.1f", it) },
                isSelected = { kotlin.math.abs(Cfg.presencePenalty - it) < 0.001f },
                onSelect = { Cfg.presencePenalty = it }
            )
            SelectField(
                stringResource(R.string.pm_frequency),
                value = if (Cfg.frequencyPenalty < 0f) stringResource(R.string.tx_18c63459) else String.format("%.1f", Cfg.frequencyPenalty),
                desc = stringResource(R.string.pm_frequency_key),
                options = listOf(-1f, 0f, 0.3f, 0.5f, 1f, 1.5f, 2f),
                optionLabel = { if (it < 0f) stringResource(R.string.tx_9559ce64) else String.format("%.1f", it) },
                isSelected = { kotlin.math.abs(Cfg.frequencyPenalty - it) < 0.001f },
                onSelect = { Cfg.frequencyPenalty = it }
            )
            SelectField(
                stringResource(R.string.pm_repeat),
                value = if (Cfg.repeatPenalty <= 0f) stringResource(R.string.tx_18c63459) else String.format("%.2f", Cfg.repeatPenalty),
                desc = stringResource(R.string.pm_repeat_key),
                options = listOf(0f, 1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.3f),
                optionLabel = { if (it <= 0f) stringResource(R.string.tx_9559ce64) else String.format("%.2f", it) },
                isSelected = { kotlin.math.abs(Cfg.repeatPenalty - it) < 0.001f },
                onSelect = { Cfg.repeatPenalty = it }
            )
            Text(
                stringResource(R.string.tx_c93d4306),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.se_086), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Field(
                label = stringResource(R.string.se_087),
                value = Cfg.extraParams,
                onValueChange = { Cfg.extraParams = it },
                placeholder = """{"enable_thinking":true}""",
                secret = false,
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.se_088) +
                    stringResource(R.string.se_089),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //模型参数子页

/** 思考配置：规则列表按顺序匹配，首条命中生效；编辑走弹窗（参考图那套字段） */
private fun LazyListScope.modelThinkSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(
            stringResource(R.string.tx_5dc7fa2b),
            stringResource(R.string.tx_81ef7ad6)
        )
        CardBox {
            val rules = remember(Cfg.thinkRules) { ThinkRules.list() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${rules.count { it.on }}/${rules.size} 条启用",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                //新增入口放右上角，跟列表页习惯一致
                Text(
                    stringResource(R.string.tx_03dc6b51),
                    fontSize = 11.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                        .tap { ThinkEdit.open(ThinkRules.Rule(pattern = "", path = "reasoning_effort"), true) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            rules.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .tap { ThinkEdit.open(r.copy(), false) }
                            .padding(vertical = 8.dp)
                    ) {
                        //主标题显示匹配的完整模型名（用户要求：匹配的是模型，不是供应商）
                        Text(
                            if (r.pattern.isBlank()) stringResource(R.string.tx_ed972708)
                            else r.pattern.split(',', '，').joinToString(", ") { it.trim() }.take(40),
                            fontSize = 13.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            ThinkRules.controlName(r.control) + " · " + r.levelText() + " · " +
                                ThinkRules.MATCH_NAMES.firstOrNull { it.first == r.match }?.second.orEmpty() +
                                " · " + r.path,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    //顺序即优先级：上移 / 下移直接改顺序，无需进弹窗
                    Text(
                        "↑",
                        fontSize = 13.sp,
                        color = if (i > 0) TextSecondary else SurfaceMuted,
                        modifier = Modifier
                            .tap { if (i > 0) ThinkRules.save(rules.swap(i, i - 1)) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                    Text(
                        "↓",
                        fontSize = 13.sp,
                        color = if (i < rules.lastIndex) TextSecondary else SurfaceMuted,
                        modifier = Modifier
                            .tap { if (i < rules.lastIndex) ThinkRules.save(rules.swap(i, i + 1)) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                    Switch(
                        checked = r.on,
                        onCheckedChange = { on -> r.on = on; ThinkRules.save(rules) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_a75eff1a), modifier = Modifier.height(38.dp)) { ThinkRules.reset() }
            Spacer(Modifier.height(6.dp))
            Text(
                "说明：「路径」是请求体里的位置，点号分层（thinking.type 表示 {\"thinking\":{\"type\":…}}）；" +
                    "「值」按 JSON 写，字符串带引号、布尔写 true/false、数字直写。匹配模型可写多个，逗号分隔。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //思考配置子页

/** 列表交换两行：直接改顺序，优先级跟着走 */
private fun List<ThinkRules.Rule>.swap(a: Int, b: Int): List<ThinkRules.Rule> {
    val out = toMutableList()
    val t = out[a]
    out[a] = out[b]
    out[b] = t
    return out
} //交换规则顺序

/** 思考规则编辑弹窗的挂载状态：同一时刻只开一个 */
private object ThinkEdit {
    var rule by mutableStateOf<ThinkRules.Rule?>(null)
    var fresh by mutableStateOf(false)

    /** 打开：rule 传副本，fresh 表示新建 */
    fun open(r: ThinkRules.Rule, isNew: Boolean) {
        rule = r
        fresh = isNew
    } //打开弹窗
} //思考规则弹窗

/** 档位编辑器：可增删、每条可展开改「显示名 / 写入路径 / 写入值」（思考配置与模型配置共用） */
@Composable
private fun LevelEditor(
    levels: List<ThinkRules.Level>,
    basePath: String,
    baseValue: String,
    onChange: (List<ThinkRules.Level>) -> Unit
) {
    var openIdx by remember { mutableStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tx_c8b75cb0), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.tx_7c138c54),
            fontSize = 11.sp,
            color = AccentBlue,
            modifier = Modifier
                .tap {
                    if (levels.size < 5) {
                        onChange(
                            levels + ThinkRules.Level(
                                label = b.ai.App.self.getString(R.string.tx_8cae3683, levels.size + 1),
                                path = basePath,
                                value = baseValue
                            )
                        )
                        openIdx = levels.size
                    }
                }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        )
    }
    Spacer(Modifier.height(2.dp))
    levels.forEachIndexed { i, lv ->
        val unfolded = openIdx == i
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .border(0.8.dp, BorderGray, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("≡", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).tap { openIdx = if (unfolded) -1 else i }) {
                    Text(stringResource(R.string.tx_9c61a929) + (i + 1), fontSize = 12.sp, color = TextPrimary)
                    Text(
                        lv.label.ifBlank { "未命名" } + " · " + lv.value.ifBlank { "未填值" },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .tap {
                            onChange(levels.toMutableList().also { it.removeAt(i) })
                            openIdx = (openIdx).coerceAtMost(levels.lastIndex - 1)
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) { IcTrash(DangerRed, side = 13.dp) }
                Box(
                    Modifier.tap { openIdx = if (unfolded) -1 else i }.padding(4.dp)
                ) { IcChevron(TextSecondary, down = !unfolded, side = 13.dp) }
            }
            if (unfolded) {
                Spacer(Modifier.height(6.dp))
                Field(stringResource(R.string.tx_8e6ab923), lv.label, { v ->
                    onChange(levels.toMutableList().also { it[i] = it[i].copy(label = v) })
                }, placeholder = "low")
                Field(stringResource(R.string.tx_694297ce), lv.path, { v ->
                    onChange(levels.toMutableList().also { it[i] = it[i].copy(path = v) })
                }, placeholder = basePath)
                Field(stringResource(R.string.tx_d3e52c96), lv.value, { v ->
                    onChange(levels.toMutableList().also { it[i] = it[i].copy(value = v) })
                }, placeholder = baseValue)
            }
        }
    }
} //档位编辑器

/** 当前配置的档位明细：没存过明细就按名字串摊平（老配置升级上来的情况） */
private fun profLevels(): List<ThinkRules.Level> {
    val raw = Cfg.profThinkLevelsJson
    if (raw.isNotBlank()) {
        val arr = runCatching { org.json.JSONArray(raw) }.getOrNull()
        if (arr != null) {
            val out = ArrayList<ThinkRules.Level>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += ThinkRules.Level(
                    label = o.optString("label", ""),
                    path = o.optString("path", ""),
                    value = o.optString("value", "")
                )
            }
            if (out.isNotEmpty()) return out
        }
    }
    return Cfg.profThinkLevels.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        .map { ThinkRules.Level(label = it, path = "", value = "") }
} //当前配置档位


/**
 * 思考规则编辑弹窗：按参考图排——启用 / 控件 / 匹配方式 / 匹配模型 / 默认路径 /
 * 始终开启 / 开启关闭写入（折叠）/ 滑块档位（折叠，可增删每个档位）。
 */
@Composable
private fun ThinkRuleDialog(
    rule: ThinkRules.Rule,
    fresh: Boolean,
    onSave: (ThinkRules.Rule) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit
) {
    var enabled by remember(rule) { mutableStateOf(rule.on) }
    var control by remember(rule) { mutableStateOf(rule.control) }
    var match by remember(rule) { mutableStateOf(rule.match) }
    var pattern by remember(rule) { mutableStateOf(rule.pattern) }
    var path by remember(rule) { mutableStateOf(rule.path) }
    var alwaysOn by remember(rule) { mutableStateOf(rule.alwaysOn) }
    var onValue by remember(rule) { mutableStateOf(rule.onValue) }
    var offValue by remember(rule) { mutableStateOf(rule.offValue) }
    //档位是可增删的列表：在副本上改，点保存才写回，取消不留痕
    var levels by remember(rule) { mutableStateOf(rule.levelList().map { it.copy() }) }
    var writeOpen by remember { mutableStateOf(false) }
    var levelOpen by remember { mutableStateOf(true) }
    var openIdx by remember { mutableStateOf(0) }

    ScrimCard {
        Text(
            if (fresh) stringResource(R.string.tx_520a162c) else stringResource(R.string.tx_a441b416),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.tx_3fe3558b), fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.weight(1f, fill = false).heightIn(max = 440.dp).verticalScroll(glassScrollState())
        ) {
            ThinkToggleRow(stringResource(R.string.tx_8d2bc55b), if (enabled) stringResource(R.string.tx_cf2b2abc) else stringResource(R.string.tx_2ac5cf31), enabled) { enabled = it }
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.tx_e1b2f870), fontSize = 11.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThinkSeg(listOf("slider" to stringResource(R.string.tx_011d09ad), "bool" to "开关", "enum" to "枚举"), control) { control = it }
            }
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.tx_078ab933), fontSize = 11.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThinkSeg(ThinkRules.MATCH_NAMES, match) { match = it }
            }
            Spacer(Modifier.height(2.dp))
            //匹配模型写完整型号，不写供应商：模型名才算「命中」，厂商前缀会把同厂所有型号都吞进来
            Field(
                stringResource(R.string.tx_1bfaf093),
                pattern,
                { pattern = it },
                placeholder = "glm-4.6, deepseek-chat, gemini-2.5-pro"
            )
            Text(
                stringResource(R.string.tx_eb67ae08),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Field(stringResource(R.string.tx_16a79761), path, { path = it }, placeholder = "reasoning_effort")
            Spacer(Modifier.height(2.dp))
            ThinkToggleRow(stringResource(R.string.tx_520e366b), stringResource(R.string.tx_2280a03a), alwaysOn) { alwaysOn = it }

            //开启 / 关闭时写入：默认收起，需要改固定值时再展开
            ThinkFold(
                title = stringResource(R.string.tx_1bf1dcd4),
                sub = stringResource(R.string.tx_6b8361f5),
                open = writeOpen,
                onToggle = { writeOpen = !writeOpen }
            ) {
                Field(stringResource(R.string.tx_abbb0ab4), onValue, { onValue = it }, placeholder = "\"enabled\"")
                Field(stringResource(R.string.tx_e2956683), offValue, { offValue = it }, placeholder = "\"disabled\"")
            }

            //滑块档位：每个档位一条，可增删；档位可以带自己的写入路径（如 Claude 的 budget_tokens）
            ThinkFold(
                title = stringResource(R.string.tx_4fa79f06),
                sub = stringResource(R.string.tx_8bdbcde3, levels.size),
                open = levelOpen,
                onToggle = { levelOpen = !levelOpen }
            ) {
                LevelEditor(levels, path, onValue) { levels = it }
                Text(
                    stringResource(R.string.tx_1a212ec6),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) {
                Text(
                    stringResource(R.string.tx_2f4aaddd),
                    fontSize = 12.sp,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(DangerRedSoft, RoundedCornerShape(10.dp))
                        .tap { onDelete() }
                        .padding(vertical = 11.dp)
                )
            }
            Text(
                stringResource(R.string.tx_625fb26b),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, gate = 0.dp)
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.tx_be5fbbe3),
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue, gate = 0.dp)
                    .tap {
                        onSave(
                            rule.copy().apply {
                                this.on = enabled
                                this.control = control
                                this.match = match
                                this.pattern = pattern
                                this.path = path.ifBlank { "reasoning_effort" }
                                this.alwaysOn = alwaysOn
                                this.onValue = onValue
                                this.offValue = offValue
                                this.levels = levels.map { it.copy() }
                                this.legacyLevels = ""
                            }
                        )
                    }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //思考规则弹窗

/** 可折叠分组：标题 + 副标题 + 箭头，展开内容带高度动画（避免多个分组同时弹开挤在一起） */
@Composable
private fun ThinkFold(
    title: String,
    sub: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().tap { onToggle() }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, color = TextPrimary)
                Text(sub, fontSize = 10.sp, color = TextSecondary)
            }
            IcChevron(TextSecondary, down = !open, side = 13.dp)
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(180)) + expandVertically(tween(220), expandFrom = Alignment.Top),
            exit = fadeOut(tween(80)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
        ) {
            Column { content() }
        }
    }
} //可折叠分组状态



/** 分段选择：一行小胶囊，选中高亮蓝 */
@Composable
private fun ThinkSeg(items: List<Pair<String, String>>, cur: String, onPick: (String) -> Unit) {
    items.forEach { (k, label) ->
        Text(
            label,
            fontSize = 11.sp,
            color = if (cur == k) AccentBlue else TextSecondary,
            modifier = Modifier
                .liquidBg(
                    RoundedCornerShape(8.dp),
                    tint = if (cur == k) AccentBlueSoft else SurfaceMuted,
                    gate = 0.dp
                )
                .tap { onPick(k) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
} //分段选择

/** 弹窗里的一行开关 */
@Composable
private fun ThinkToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = TextPrimary)
            Text(sub, fontSize = 10.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
    }
} //弹窗开关行


private fun LazyListScope.modelVoiceSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_v_in_t), stringResource(R.string.se_v_in_d))
        VoiceInCard()
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_v_out_t), stringResource(R.string.se_v_out_d))
        VoiceOutCard()
    }
} //语音子页

private fun LazyListScope.modelMemSec(onOpenMemory: () -> Unit) {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_44e4d7a3), stringResource(R.string.tx_08b3859a))
        CardBox {
            val ctxM = LocalContext.current
            val rows = Memory.rows
            //思维导图：中心节点 + 左侧主干线 + 每条记忆一个分支节点
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).liquidBg(CircleShape, tint = AccentBlue, gate = 0.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (rows.isEmpty()) stringResource(R.string.tx_74f960c9) else stringResource(R.string.tx_6adeb861, rows.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        if (rows.isEmpty()) stringResource(R.string.tx_cedb8821) else stringResource(R.string.tx_4dc6270e),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.padding(start = 4.dp)) {
                    rows.forEachIndexed { i, r ->
                        //条目之间要能看出边界：底色和卡片太接近时整块会糊成一片（用户反馈「无分割」），
                        //所以每条加一圈细边框，并留 4dp 呼吸位
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            //主干 + 分支：一格宽度里画「└─」形状，最后一条不画竖线
                            Canvas(Modifier.width(16.dp).height(38.dp)) {
                                val x = 5.dp.toPx()
                                val mid = size.height / 2f
                                drawLine(
                                    BorderGray, androidx.compose.ui.geometry.Offset(x, 0f),
                                    androidx.compose.ui.geometry.Offset(x, if (i == rows.lastIndex) mid else size.height),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawLine(
                                    BorderGray, androidx.compose.ui.geometry.Offset(x, mid),
                                    androidx.compose.ui.geometry.Offset(size.width, mid),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawCircle(AccentBlue, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width, mid))
                            }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 32.dp)
                                    .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                    .border(0.8.dp, BorderGray, RoundedCornerShape(9.dp))
                                    .padding(start = 9.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    r.text,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "✕",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.tap { Memory.delete(r.id) }.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            //整理：选一个模型把零散条目合并去重，结果先摆出来，点应用才落库
            val profiles = Profiles.list
            val cur = profiles.firstOrNull { it.id == Cfg.memProfileId }
            SelectField(
                stringResource(R.string.tx_582085bb),
                cur?.label?.ifBlank { cur.model } ?: "跟随当前聊天",
                listOf("") + profiles.map { it.id },
                optionLabel = { id ->
                    if (id.isBlank()) stringResource(R.string.tx_929ea71b)
                    else profiles.firstOrNull { it.id == id }?.label?.ifBlank { id } ?: id
                },
                isSelected = { Cfg.memProfileId == it },
                onSelect = { Cfg.memProfileId = it; MemOrganizer.clear() }
            )
            Spacer(Modifier.height(8.dp))
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    if (MemOrganizer.busy) stringResource(R.string.tx_653d2130) else stringResource(R.string.tx_607c0a3b),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (!MemOrganizer.busy) scope.launch { MemOrganizer.run() }
                }
                if (MemOrganizer.suggestions.isNotEmpty()) {
                    GhostButton(stringResource(R.string.tx_9f8beb63, MemOrganizer.suggestions.size), modifier = Modifier.height(36.dp)) {
                        android.widget.Toast.makeText(
                            ctxM, MemOrganizer.apply(), android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    GhostButton(stringResource(R.string.tx_5ac2011a), modifier = Modifier.height(36.dp)) { MemOrganizer.clear() }
                }
            }
            if (MemOrganizer.error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(MemOrganizer.error, fontSize = 10.sp, color = DangerRed)
            }
            //待确认结果：跟记忆节点同一套连线，让「整理前 / 整理后」看起来是一回事
            if (MemOrganizer.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.tx_865347b7), fontSize = 11.sp, color = AccentBlue)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.padding(start = 4.dp)) {
                    MemOrganizer.suggestions.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Canvas(Modifier.width(16.dp).height(30.dp)) {
                                val x = 5.dp.toPx()
                                val mid = size.height / 2f
                                drawLine(
                                    AccentBlueSoft, androidx.compose.ui.geometry.Offset(x, 0f),
                                    androidx.compose.ui.geometry.Offset(x, if (i == MemOrganizer.suggestions.lastIndex) mid else size.height),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawLine(
                                    AccentBlueSoft, androidx.compose.ui.geometry.Offset(x, mid),
                                    androidx.compose.ui.geometry.Offset(size.width, mid),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawCircle(AccentBlue, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width, mid))
                            }
                            Text(
                                t,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                                    .border(0.8.dp, AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
} //记忆子页

//语音识别卡片（从原 modelTab 迁出，保持逐行一致）
@Composable
private fun VoiceInCard() {
    val sysLabel = stringResource(R.string.se_v_mode_sys)
    val apiLabel = stringResource(R.string.se_v_mode_api)
    val modeLabel: (String) -> String = { if (it == "api") apiLabel else sysLabel }
    val preset = AsrPresets.of(Cfg.asrVendor)
    val scope = rememberCoroutineScope()
    var models by remember(Cfg.asrVendor) {
        mutableStateOf(preset.models.ifEmpty { listOf(Cfg.asrModel) }.filter { it.isNotBlank() })
    }
    var loading by remember { mutableStateOf(false) }
    var pickErr by remember { mutableStateOf("") }
    //STT 引擎：两个本地（系统识别 / Sherpa NCNN）+ 两个远程（OpenAI / Deepgram）。
    //标签写明本地还是远程，离线模式下只列本地那两项，避免选了远程又被拒绝
    val engineNames = mapOf(
        "sys" to "系统识别（本地，免费）",
        "sherpa" to "Sherpa NCNN（本地）",
        "openai" to "OpenAI STT（远程）",
        "deepgram" to "Deepgram STT（远程）"
    )
    val engine = Audio.engine()
    //sherpa 的 so 只随 arm64-v8a 打：运行时加载不起来时这一项直接不列（v7a 设备点了也只会退化）
    val sherpaOk = remember { b.ai.core.model.voice.SherpaAsr.runtimeOk() }
    val engineOpts = buildList {
        add("sys")
        if (sherpaOk) add("sherpa")
        //离线模式只给本地：远程项直接不列出来，比选完再弹提示更干净
        if (!Cfg.asrOffline) {
            add("openai")
            add("deepgram")
        }
    }
    CardBox {
        SelectField(
            stringResource(R.string.tx_2604bcce),
            //老配置里存着 sherpa 但这台设备跑不起来时，如实显示回落后的实际引擎
            when {
                engine == "sherpa" && !sherpaOk -> "系统识别（sherpa 运行时不可用）"
                else -> engineNames[engine] ?: engine
            },
            engineOpts,
            desc = if (Cfg.asrOffline) stringResource(R.string.tx_fdbc9a5f) else stringResource(R.string.tx_3df99821),
            optionLabel = { engineNames[it] ?: it },
            isSelected = { engine == it },
            onSelect = {
                Cfg.asrEngine = it
                //旧的 asrMode 同步一份：别处还在读它判断走不走接口
                Cfg.asrMode = if (it == "sys" || it == "sherpa") "sys" else "api"
            }
        )
        //从属字段缩进一层：引擎是父项，下面的 Key / 模型 / 目录都属于它（用户反馈「与上一栏无缩进」）
        Column(Modifier.fillMaxWidth().padding(start = 12.dp)) {
        if (engine == "deepgram") {
            Field(
                label = "Deepgram API Key",
                value = Cfg.asrDgKey,
                onValueChange = { Cfg.asrDgKey = it },
                placeholder = stringResource(R.string.tx_d822e614),
                secret = true
            )
            SelectField(
                stringResource(R.string.tx_19497c0b),
                Cfg.asrDgModel,
                listOf("nova-3", "nova-2", "enhanced", "base"),
                optionLabel = { it },
                isSelected = { Cfg.asrDgModel == it },
                onSelect = { Cfg.asrDgModel = it }
            )
            Field(
                label = stringResource(R.string.tx_85624c8e),
                value = Cfg.asrDgUrl,
                onValueChange = { Cfg.asrDgUrl = it },
                placeholder = "https://api.deepgram.com"
            )
            Text(
                stringResource(R.string.tx_f490eaaa),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (engine == "sherpa") {
            Field(
                label = stringResource(R.string.tx_e796d32d),
                value = Cfg.asrSherpaDir,
                onValueChange = { Cfg.asrSherpaDir = it },
                placeholder = stringResource(R.string.tx_25b7fe70)
            )
            //提示改成动态判定：原来是写死的红字，内置了也照样喊「未内置」，反而误导用户
            val dir = Cfg.asrSherpaDir
            val bundled = remember { b.ai.core.model.voice.SherpaAsr.bundledModelOk() }
            val dirOk = dir.isBlank() || b.ai.core.model.voice.SherpaAsr.modelOk(dir)
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val (msg, tint) = when {
                !sherpaOk -> "本包在 $abi 上没有 sherpa 运行时，选它会改用系统识别" to DangerRed
                !dirOk -> "目录不完整：需 7 个模型文件（encoder/decoder/joiner 的 param·bin + tokens.txt）" to DangerRed
                dir.isBlank() && bundled -> "运行时与模型均已内置，目录留空即用内置模型" to OkGreen
                dir.isBlank() -> "包内没带模型，请下载或填一个模型目录" to DangerRed
                else -> "使用外部模型：$dir" to TextSecondary
            }
            Text(msg, fontSize = 10.sp, color = tint)
            //模型下载：默认取本仓库 models 目录（七个文件逐个拉），也支持任意「目录前缀」或 zip 直链
            var dlBusy by remember { mutableStateOf(false) }
            var dlPct by remember { mutableStateOf(0) }
            var dlNote by remember { mutableStateOf("") }
            Spacer(Modifier.height(8.dp))
            Field(
                label = "模型下载地址",
                value = Cfg.asrSherpaSrc,
                onValueChange = { Cfg.asrSherpaSrc = it },
                placeholder = b.ai.core.model.voice.ModelFetch.DEFAULT_SRC
            )
            //按钮跟输入框之间要留空：Row 紧贴 Field 底边会看起来糊成一团
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostButton(
                    if (dlBusy) "下载中 $dlPct%" else "下载模型",
                    modifier = Modifier.height(36.dp)
                ) {
                    if (dlBusy) return@GhostButton
                    //地址留空就用默认源；下载在 IO 线程，进度靠回调刷回主线程
                    val src = Cfg.asrSherpaSrc.ifBlank { b.ai.core.model.voice.ModelFetch.DEFAULT_SRC }
                    dlBusy = true
                    dlPct = 0
                    dlNote = "准备中…"
                    scope.launch {
                        try {
                            val dir = b.ai.core.model.voice.ModelFetch.fetch(src) { p ->
                                dlPct = p.percent
                                dlNote = p.note
                            }
                            //下完直接把目录填进去，用户不用手抄路径
                            Cfg.asrSherpaDir = dir.absolutePath
                            dlPct = 100
                            dlNote = "已就绪：" + dir.absolutePath
                        } catch (e: Exception) {
                            dlNote = "下载失败：" + (e.message ?: e.javaClass.simpleName)
                        } finally {
                            dlBusy = false
                        }
                    }
                }
                GhostButton("用已下载的", modifier = Modifier.height(36.dp)) {
                    val list = b.ai.core.model.voice.ModelFetch.installed()
                    if (list.isEmpty()) {
                        dlNote = "还没有下过模型"
                    } else {
                        val d = list.last()
                        Cfg.asrSherpaDir = d.absolutePath
                        dlNote = "已切换：" + d.name
                    }
                }
            }
            if (dlNote.isNotBlank()) {
                Text(
                    dlNote,
                    fontSize = 10.sp,
                    color = if (dlNote.startsWith("下载失败")) DangerRed else TextSecondary
                )
            }
        }
        if (engine == "openai") {
            SelectField(
                stringResource(R.string.tx_79f09f89),
                preset.label,
                AsrPresets.all,
                optionLabel = { it.label },
                isSelected = { Cfg.asrVendor == it.id },
                onSelect = { p ->
                    Cfg.asrVendor = p.id
                    Cfg.asrBase = ""
                    pickErr = ""
                    models = p.models
                    if (p.models.isNotEmpty()) Cfg.asrModel = p.models.first()
                }
            )
            Field(
                label = stringResource(R.string.se_v_base),
                value = Cfg.asrBase,
                onValueChange = { Cfg.asrBase = it },
                placeholder = preset.base.ifBlank { Cfg.baseUrl.ifBlank { "https://api.openai.com/v1" } }
            )
            Field(
                label = stringResource(R.string.se_v_key),
                value = Cfg.asrKey,
                onValueChange = { Cfg.asrKey = it },
                placeholder = stringResource(R.string.se_v_key_ph),
                secret = true
            )
            if (models.isNotEmpty()) {
                SelectField(
                    stringResource(R.string.tx_8000f187),
                    Cfg.asrModel,
                    models,
                    optionLabel = { it },
                    isSelected = { Cfg.asrModel == it },
                    onSelect = { Cfg.asrModel = it }
                )
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                if (loading) stringResource(R.string.tx_629de16a) else stringResource(R.string.tx_e70a40b7),
                modifier = Modifier.height(38.dp)
            ) {
                if (loading) return@GhostButton
                loading = true
                pickErr = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Audio.listModels() } }
                    loading = false
                    r.onSuccess { list ->
                        if (list.isEmpty()) pickErr = b.ai.App.self.getString(R.string.tx_9be9c76a)
                        else { models = list; Cfg.asrModel = list.first() }
                    }.onFailure { pickErr = it.message ?: "拉取失败" }
                }
            }
            Field(
                label = stringResource(R.string.tx_f1153d97),
                value = Cfg.asrModel,
                onValueChange = { Cfg.asrModel = it },
                placeholder = "whisper-1"
            )
            if (pickErr.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(pickErr, fontSize = 10.sp, color = DangerRed)
            }
            SelectField(
                stringResource(R.string.tx_95e4a689),
                "${Cfg.asrRate} Hz",
                AsrPresets.rates,
                optionLabel = { "$it Hz" },
                isSelected = { Cfg.asrRate == it },
                onSelect = { Cfg.asrRate = it }
            )
            SelectField(
                stringResource(R.string.tx_0c4316d9),
                stringResource(R.string.tx_a6f0b1dc, Cfg.asrMaxSec),
                AsrPresets.maxSecs,
                optionLabel = { stringResource(R.string.tx_b0c9ab6f) },
                isSelected = { Cfg.asrMaxSec == it },
                onSelect = { Cfg.asrMaxSec = it }
            )
            if (preset.tip.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(preset.tip, fontSize = 10.sp, color = TextSecondary)
            }
        }
        Field(
            label = stringResource(R.string.se_v_lang),
            value = Cfg.asrLang,
            onValueChange = { Cfg.asrLang = it },
            placeholder = "zh-CN"
        )
        } //引擎从属字段
        Spacer(Modifier.height(6.dp))
        CapSwitch(
            stringResource(R.string.tx_4a61c29a),
            if (Cfg.asrOffline) stringResource(R.string.tx_5e408cf7)
            else stringResource(R.string.tx_f643dfa4),
            Cfg.asrOffline
        ) { on ->
            Cfg.asrOffline = on
            //打开离线时把远程引擎切回本地默认；不切的话引擎下拉会显示一个不存在的选项
            if (on && Audio.engine() != "sys" && Audio.engine() != "sherpa") {
                Cfg.asrEngine = "sys"
                Cfg.asrMode = "sys"
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.se_v_tip), fontSize = 10.sp, color = TextSecondary)
    }
} //语音识别卡片

@Composable
private fun VoiceOutCard() {
    val ctx = LocalContext.current
    val sysLabel = stringResource(R.string.se_v_mode_sys)
    val apiLabel = stringResource(R.string.se_v_mode_api)
    val modeLabel: (String) -> String = { if (it == "api") apiLabel else sysLabel }
    CardBox {
        CapSwitch(
            stringResource(R.string.se_124),
            if (Cfg.speakReplies) stringResource(R.string.se_125) else stringResource(R.string.se_126),
            Cfg.speakReplies
        ) { on ->
            Cfg.speakReplies = on
            if (on) Speak.say(ctx.getString(R.string.se_127), force = true) else Speak.stop()
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_128), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        SelectField(
            stringResource(R.string.se_v_mode),
            modeLabel(Cfg.ttsMode),
            listOf("sys", "api"),
            optionLabel = { modeLabel(it) },
            isSelected = { Cfg.ttsMode == it },
            onSelect = { Cfg.ttsMode = it }
        )
        if (Cfg.ttsMode == "api") {
            Field(
                label = stringResource(R.string.se_v_base),
                value = Cfg.ttsBase,
                onValueChange = { Cfg.ttsBase = it },
                placeholder = Cfg.baseUrl.ifBlank { "https://api.openai.com/v1" }
            )
            Field(
                label = stringResource(R.string.se_v_key),
                value = Cfg.ttsKey,
                onValueChange = { Cfg.ttsKey = it },
                placeholder = stringResource(R.string.se_v_key_ph),
                secret = true
            )
            Field(
                label = stringResource(R.string.se_v_model),
                value = Cfg.ttsModel,
                onValueChange = { Cfg.ttsModel = it },
                placeholder = "tts-1"
            )
            Field(
                label = stringResource(R.string.se_v_voice),
                value = Cfg.ttsVoice,
                onValueChange = { Cfg.ttsVoice = it },
                placeholder = "alloy"
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.se_v_play), modifier = Modifier.height(38.dp)) {
                Speak.say(ctx.getString(R.string.se_127), force = true)
            }
            if (Audio.error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(Audio.error, fontSize = 10.sp, color = DangerRed)
            }
        }
    }
} //语音合成卡片

private fun LazyListScope.chatCtxSec(stats: Pair<Int, Int>) {
    item {
    SectionTitle(stringResource(R.string.se_090), stringResource(R.string.se_091))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_092), fontSize = 14.sp, color = TextPrimary)
                Text(stringResource(R.string.se2_cur_msgs, stats.first, stats.second), fontSize = 11.sp, color = TextSecondary)
            }
            Switch(
                checked = Cfg.ctxAuto,
                onCheckedChange = { Cfg.ctxAuto = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        SelectField(
            stringResource(R.string.se_094),
            if (Cfg.ctxTotal >= 1_000_000) "1M" else "${Cfg.ctxTotal / 1000}k",
            listOf(32_000, 64_000, 128_000, 256_000, 1_000_000),
            optionLabel = { if (it >= 1_000_000) "1M" else "${it / 1000}k" },
            isSelected = { Cfg.ctxTotal == it },
            onSelect = { Cfg.ctxTotal = it }
        )
        //配置自己带了窗口时这里的全局值不生效，不说清就会出现「改了没反应」
        if (Cfg.profCtxTotal > 0) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.se2_ctx_own), fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se2_pct, Cfg.ctxPct), fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.se2_ktok, Cfg.ctxTokenBudget / 1000), fontSize = 11.sp, color = TextSecondary)
        }
        //滑杆 10-99：到窗口的这个比例就开始压缩，默认 70%
        androidx.compose.material3.Slider(
            value = Cfg.ctxPct.toFloat(),
            onValueChange = { Cfg.ctxPct = it.toInt() },
            valueRange = 10f..99f,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.se_097),
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        SelectField(
            stringResource(R.string.se_098),
            stringResource(R.string.se2_n_items, Cfg.ctxKeep),
            listOf(4, 8, 16),
            optionLabel = { stringResource(R.string.se2_n_items, it) },
            isSelected = { Cfg.ctxKeep == it },
            onSelect = { Cfg.ctxKeep = it }
        )
        Spacer(Modifier.height(8.dp))
        SelectField(
            stringResource(R.string.se_100),
            stringResource(R.string.se2_n_items, Cfg.ctxMsgCount),
            listOf(8, 16, 32),
            optionLabel = { stringResource(R.string.se2_n_items, it) },
            isSelected = { Cfg.ctxMsgCount == it },
            onSelect = { Cfg.ctxMsgCount = it }
        )
        Spacer(Modifier.height(8.dp))
        //摘要档位：字数上限跟着档位走，具体写多少由总结模型按内容自己控
        SelectField(
            stringResource(R.string.tx_ecc33185),
            b.ai.core.model.chat.Sum.levelName(Cfg.summaryLevel),
            listOf(0, 1, 2),
            optionLabel = { b.ai.core.model.chat.Sum.levelName(it) },
            isSelected = { Cfg.summaryLevel == it },
            onSelect = { Cfg.summaryLevel = it }
        )
        Text(
            "简洁：上限 1000 字，可能跟随轮次丢失关键；详细：上限 2000 字，推荐；全面：不限字数，最推荐。" +
                "当前 " + b.ai.core.model.chat.Sum.levelHint(Cfg.summaryLevel),
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        //总结用模型：留空跟随聊天选中的那条配置，选了就单独走它的 base_url / Key / 模型
        val sumProfiles = Profiles.list
        val sumCur = sumProfiles.firstOrNull { it.id == Cfg.summaryProfileId }
        SelectField(
            stringResource(R.string.tx_637321f6),
            sumCur?.label ?: "跟随当前聊天",
            listOf("") + sumProfiles.map { it.id },
            optionLabel = { id -> sumProfiles.firstOrNull { it.id == id }?.label ?: "跟随当前聊天" },
            isSelected = { it == Cfg.summaryProfileId },
            onSelect = { Cfg.summaryProfileId = it }
        )
        Spacer(Modifier.height(8.dp))
        //标题生成用模型：同一套候选，留空跟随当前聊天；新建会话取名只花几十 token
        val titleCur = sumProfiles.firstOrNull { it.id == Cfg.titleProfileId }
        SelectField(
            stringResource(R.string.tx_3c70a377),
            titleCur?.label ?: "跟随当前聊天",
            listOf("") + sumProfiles.map { it.id },
            optionLabel = { id -> sumProfiles.firstOrNull { it.id == id }?.label ?: "跟随当前聊天" },
            isSelected = { it == Cfg.titleProfileId },
            onSelect = { Cfg.titleProfileId = it }
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.se_101), modifier = Modifier.height(38.dp)) { Agent.compactNow() }
            Spacer(Modifier.width(10.dp))
            Text(
                if (Agent.busy) stringResource(R.string.se_102) else stringResource(R.string.se_103),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        //总结补充要求：留空不追加，填了拼在内置四段提示词后面（结构永远保留）；自动总结、立即总结与长按重总结都走它
        Field(
            label = stringResource(R.string.se_sum_prompt),
            value = Cfg.summaryPrompt,
            onValueChange = { Cfg.summaryPrompt = it },
            placeholder = stringResource(R.string.se_sum_prompt_hint),
            singleLine = false,
            minLines = 3
        )
        Text(
            stringResource(R.string.se_sum_prompt_note),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }

    }
} //上下文与压缩子页

private fun LazyListScope.chatInjectSec() {
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_017), stringResource(R.string.se_104))
    CardBox {
        val ctx = LocalContext.current
        //位置权限不在自动申请清单里：只有你真的打开这个开关才弹，免得缺权限卡住工具调用
        val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            Cfg.injectPlace = it
        }
        //精确位置要单独要权限：用户开了「精确位置注入」才弹
        val finePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
            Cfg.injectPrecise = m[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        CapSwitch(
            stringResource(R.string.se_105),
            if (Cfg.injectTime) stringResource(R.string.se_106) else stringResource(R.string.se_107),
            Cfg.injectTime
        ) { Cfg.injectTime = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_108),
            if (Cfg.injectBattery) stringResource(R.string.se_109) else stringResource(R.string.se_110),
            Cfg.injectBattery
        ) { Cfg.injectBattery = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_111),
            when {
                !Cfg.injectPlace -> stringResource(R.string.se_112)
                Env.hasPlacePerm(ctx) -> stringResource(R.string.se_113)
                else -> stringResource(R.string.se_114)
            },
            Cfg.injectPlace
        ) { on ->
            if (on && !Env.hasPlacePerm(ctx)) {
                locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                Cfg.injectPlace = on
            }
        }
        //注入位置打开后才滑出扩展项：精确位置注入（现取一次定位）、自动转地名（结果加密后本机缓存）
        FollowBlock(Cfg.injectPlace) {
            Spacer(Modifier.height(6.dp))
            CapSwitch(
                stringResource(R.string.se_366),
                when {
                    !Cfg.injectPrecise -> stringResource(R.string.se_368)
                    Env.hasFinePerm(ctx) -> stringResource(R.string.se_367)
                    else -> stringResource(R.string.se_114)
                },
                Cfg.injectPrecise
            ) { on ->
                if (on && !Env.hasFinePerm(ctx)) {
                    finePerm.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    Cfg.injectPrecise = on
                }
            }
            Spacer(Modifier.height(6.dp))
            CapSwitch(
                stringResource(R.string.se_369),
                if (Cfg.placeGeo) stringResource(R.string.se_370) else stringResource(R.string.se_371),
                Cfg.placeGeo
            ) { Cfg.placeGeo = it }
        }
        Spacer(Modifier.height(8.dp))
        //通知与使用时长都要系统侧单独授权：开关先落地，缺权限就弹窗把人送去系统页
        CapSwitch(
            stringResource(R.string.se_320),
            when {
                !Cfg.injectNoti -> stringResource(R.string.se_321)
                !b.ai.core.task.hook.Noti.allow(ctx) -> stringResource(R.string.se_322)
                else -> stringResource(R.string.se_323, b.ai.core.task.hook.Noti.count(ctx))
            },
            Cfg.injectNoti
        ) { on ->
            if (on && !b.ai.core.task.hook.Noti.allow(ctx)) {
                Cfg.injectNoti = true
                PermAsk.kind.value = "noti"
            } else {
                Cfg.injectNoti = on
            }
        }
        FollowBlock(Cfg.injectNoti) {
            Spacer(Modifier.height(6.dp))
            NotiLimitRow()
        }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_328),
            when {
                !Cfg.injectUsage -> stringResource(R.string.se_329)
                !b.ai.core.data.usage.Usage.allow(ctx) -> stringResource(R.string.se_330)
                else -> stringResource(R.string.se_331, b.ai.core.data.usage.Usage.hm(b.ai.core.data.usage.Usage.today(ctx).first))
            },
            Cfg.injectUsage
        ) { on ->
            if (on && !b.ai.core.data.usage.Usage.allow(ctx)) {
                Cfg.injectUsage = true
                PermAsk.kind.value = "usage"
            } else {
                Cfg.injectUsage = on
            }
        }
    }

    }

} //注入子页

private fun LazyListScope.chatSessionSec(stats: Pair<Int, Int>, onOpenStars: () -> Unit) {
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_021), stringResource(R.string.se_129))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Agent.sessionTitle.ifBlank { stringResource(R.string.ag_new_session) }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                Text(
                    stringResource(R.string.se2_rec, stats.first, stats.second, Agent.sessions.size),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            GhostButton(stringResource(R.string.se_131), modifier = Modifier.height(38.dp)) { Agent.newSession() }
        }
    }

    }

    item {
    val appCtx = LocalContext.current
    val appScope = rememberCoroutineScope()
    //会话与导出：原来在抽屉底部，收进「对话」页
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_70b562da), stringResource(R.string.tx_041b5d0d))
    CardBox {
        LinkRow("收藏夹", "查看收藏的会话", onClick = onOpenStars)
        Spacer(Modifier.height(8.dp))
        GhostButton(stringResource(R.string.dw_export), modifier = Modifier.height(38.dp)) {
            appScope.launch {
                val p = withContext(Dispatchers.IO) { Agent.exportCurrent() }
                android.widget.Toast.makeText(
                    appCtx,
                    if (p == null) appCtx.getString(R.string.dw_none) else appCtx.getString(R.string.dw_exported, p),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_023), stringResource(R.string.se_132))
    WsSection()
    }
} //会话与导出子页

/** 输入与输出：回复怎么展示/怎么流式、草稿怎么存、输出偏好常驻文案；与提示词优化分开 */
private fun LazyListScope.chatInOutSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_ae27e4da), stringResource(R.string.tx_1e4ab751))
        CardBox {
            //输出偏好：常驻拼进系统提示，跟角色人设并行不覆盖
            Field(
                label = stringResource(R.string.tx_c083b46f),
                value = Cfg.replyStyle,
                onValueChange = { Cfg.replyStyle = it },
                placeholder = stringResource(R.string.tx_214a7d7d),
                singleLine = false,
                minLines = 3
            )
            Text(
                stringResource(R.string.tx_5489c494),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_019), stringResource(R.string.se_115))
    CardBox {
        CapSwitch(
            stringResource(R.string.se_116),
            if (Cfg.showUsage) stringResource(R.string.se_117) else stringResource(R.string.se_118),
            Cfg.showUsage
        ) { Cfg.showUsage = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_119),
            if (Cfg.showMeta) stringResource(R.string.se_120) else stringResource(R.string.se_121),
            Cfg.showMeta
        ) { Cfg.showMeta = it }
        Spacer(Modifier.height(8.dp))
        //Waifu 模式决定回复怎么切成多条，跟「显示什么」是同一类事，所以归到回复信息里
        CapSwitch(stringResource(R.string.se_175), stringResource(R.string.se_176), Cfg.waifuMode) {
            Cfg.waifuMode = it
        }
        //分句气泡动画依赖上面那条：只有切成多条时才有「一条条弹出来」的画面
        FollowBlock(Cfg.waifuMode) {
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_wbounce_t),
                stringResource(R.string.se_wbounce_d),
                Cfg.waifuBounce
            ) { Cfg.waifuBounce = it }
            Spacer(Modifier.height(8.dp))
            //更真实的分句引擎：切得更碎 + 气泡收窄到屏宽 3/7 + 更自然的弹出动画
            CapSwitch(
                stringResource(R.string.se_wreal_t),
                stringResource(R.string.se_wreal_d),
                Cfg.waifuReal
            ) { Cfg.waifuReal = it }
            Spacer(Modifier.height(8.dp))
            //打字速度：既决定两条气泡的间隔，也决定「正在输入」停留多久
            GlassSlider(
                stringResource(R.string.se_wtype_t),
                stringResource(R.string.se_wtype_v, Cfg.waifuTypeMs),
                Cfg.waifuTypeMs.toFloat(),
                20f..150f,
                step = 5f
            ) { Cfg.waifuTypeMs = it.toInt() }
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_wtyping_t),
                stringResource(R.string.se_wtyping_d),
                Cfg.waifuTyping
            ) { Cfg.waifuTyping = it }
        }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_ns_t),
            if (Cfg.noStream) stringResource(R.string.se_ns_on) else stringResource(R.string.se_ns_off),
            Cfg.noStream
        ) { Cfg.noStream = it }
    }

    }
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_22b4334f), stringResource(R.string.tx_b472edb3))
    CardBox {
        //图片默认不落盘：草稿里只留文字与文件引用，隐私风险与体积都能控住
        CapSwitch(
            stringResource(R.string.tx_4f3d139b),
            if (Cfg.draftKeepImg) stringResource(R.string.tx_a114c7a8) else stringResource(R.string.tx_79a86fd2),
            Cfg.draftKeepImg
        ) { Cfg.draftKeepImg = it }
        //有效期是跟随项：只有「保留图片」开着（草稿真的落盘）才滑下来，关着时整项不存在
        FollowBlock(Cfg.draftKeepImg) {
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_090286ed),
                if (Cfg.draftDays <= 0) stringResource(R.string.tx_5756d0e9) else stringResource(R.string.tx_5cd47992, Cfg.draftDays),
                listOf(0, 1, 3, 7, 30),
                optionLabel = { if (it <= 0) stringResource(R.string.tx_5756d0e9) else stringResource(R.string.tx_52273aab) },
                isSelected = { Cfg.draftDays == it },
                onSelect = { Cfg.draftDays = it }
            )
            Text(
                stringResource(R.string.tx_0dd7ef22),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    }
} //输入与输出子页

private fun LazyListScope.chatPromptSec() {
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_a2d86e87), stringResource(R.string.tx_0e48617f))
    CardBox {
        val profiles = Profiles.list
        val cur = profiles.firstOrNull { it.id == Cfg.promptOptProfileId }
        //留空跟随当前聊天：省一份配置，多数人不需要单独开专用模型
        SelectField(
            stringResource(R.string.tx_c7049188),
            cur?.label ?: "跟随当前聊天",
            listOf("") + profiles.map { it.id },
            optionLabel = { id -> profiles.firstOrNull { it.id == id }?.label ?: "跟随当前聊天" },
            isSelected = { it == Cfg.promptOptProfileId },
            onSelect = { Cfg.promptOptProfileId = it }
        )
        Spacer(Modifier.height(8.dp))
        //保留原语言：中英混写时不至于被模型统一翻成英文
        CapSwitch(
            stringResource(R.string.tx_b886dc73),
            if (Cfg.promptOptKeep) stringResource(R.string.tx_d28ecec9) else stringResource(R.string.tx_7ec62ce2),
            Cfg.promptOptKeep
        ) { Cfg.promptOptKeep = it }
        Spacer(Modifier.height(8.dp))
        Field(
            label = stringResource(R.string.tx_84e40c91),
            value = Cfg.promptOptStyle,
            onValueChange = { Cfg.promptOptStyle = it },
            placeholder = stringResource(R.string.tx_65557288),
            singleLine = false,
            minLines = 3
        )
        Text(
            stringResource(R.string.tx_70d093e2),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
    }
} //提示词优化子页

private fun LazyListScope.chatPermSec() {
    item { Spacer(Modifier.height(14.dp)) }
    permTab()
} //权限子页

/** 工作区信息：数据根下的哈希目录 + 会话绑定的目录，未被任何会话绑定的可以一键删除 */
@Composable
private fun WsSection() {
    var list by remember { mutableStateOf<List<Workspace.WsInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tip by remember { mutableStateOf("") }
    //点一次「删除」先变成「确认删除 / 取消」，避免手滑删掉目录
    var confirm by remember { mutableStateOf<String?>(null) }
    //旧版散在数据根下的工作区个数 / 正在迁移 / 迁移进度文案（"正在迁移 1/7：xxx"）
    var legacy by remember { mutableStateOf(0) }
    var migrating by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        loading = true
        list = withContext(Dispatchers.IO) {
            runCatching { Workspace.listAll() }.getOrDefault(emptyList())
        }
        legacy = withContext(Dispatchers.IO) {
            runCatching { Workspace.legacyDirs().size }.getOrDefault(0)
        }
        loading = false
    } //重载列表与待迁移数量
    LaunchedEffect(Unit) { reload() }
    //一键清理跑完会让 ver 加一，这里跟着重扫，列表立刻变成清理后的样子
    LaunchedEffect(WsPurge.ver) { if (WsPurge.ver > 0) reload() }

    CardBox {
        //旧版工作区还挂在数据根下：给一块带进度的迁移区，搬完自动刷新列表
        if (legacy > 0 || migrating) {
            Text(stringResource(R.string.ws_mig_t), fontSize = 13.sp, color = TextPrimary)
            Text(stringResource(R.string.ws_mig_d), fontSize = 10.sp, color = TextSecondary)
            if (prog.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(prog, fontSize = 10.sp, color = AccentBlue)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                stringResource(R.string.ws_mig_btn, legacy),
                modifier = Modifier.height(36.dp)
            ) {
                if (migrating) return@GhostButton
                migrating = true
                prog = ""
                scope.launch {
                    val err = withContext(Dispatchers.IO) {
                        runCatching {
                            Workspace.migrate { i, total, name, files, bytes ->
                                //回调用的是 IO 线程，进度文案得切回主线程再写状态
                                scope.launch {
                                    prog = ctx.getString(
                                        R.string.ws_mig_step, i, total, name, files,
                                        Workspace.human(bytes)
                                    )
                                }
                            }
                        }.getOrElse { "copy:$it" }
                    }
                    migrating = false
                    prog = when (err) {
                        null -> ctx.getString(R.string.ws_mig_done, legacy)
                        "none" -> ctx.getString(R.string.ws_mig_none)
                        else -> ctx.getString(R.string.ws_mig_fail, err.orEmpty().removePrefix("copy:"))
                    }
                    reload()
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            loading -> Text(stringResource(R.string.se_133), fontSize = 11.sp, color = TextSecondary)
            list.isEmpty() -> Text(
                stringResource(R.string.se_134),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        list.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        w.name + if (w.current) stringResource(R.string.se_135) else "",
                        fontSize = 13.sp,
                        color = if (w.current) AccentBlue else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        stringResource(R.string.se2_ws_files, w.files, Workspace.human(w.bytes)) +
                            if (w.unbound) stringResource(R.string.se_137) else stringResource(R.string.se2_ws_bound) +
                            w.sessions.take(2).joinToString("、") +
                            if (w.sessions.size > 2) stringResource(R.string.se2_ws_more, w.sessions.size) else "",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Text(w.path, fontSize = 9.sp, color = TextSecondary, maxLines = 1)
                }
                if (confirm == w.path) {
                    Text(
                        stringResource(R.string.se_140),
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier
                            .background(DangerRedSoft, RoundedCornerShape(8.dp))
                            .tap {
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) { Workspace.delete(ctx, w.path) }
                                    tip = err ?: ctx.getString(R.string.se2_ws_deleted, w.name)
                                    confirm = null
                                    reload()
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.se_142),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), gate = 0.dp)
                            .tap { confirm = null }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                } else if (w.unbound && !w.current && w.exists) {
                    Text(
                        stringResource(R.string.se_143),
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier
                            .background(DangerRedSoft, RoundedCornerShape(8.dp))
                            .tap { confirm = w.path }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        //还有没被任何会话绑定的目录才给清理入口，全都绑上了就整块不出现
        val idle = list.count { it.unbound && !it.current && it.exists }
        if (idle > 0) {
            GhostButton(
                stringResource(R.string.wsq_btn, idle),
                modifier = Modifier.height(38.dp)
            ) { WsPurge.open = true }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.se_144), modifier = Modifier.height(38.dp)) { scope.launch { reload() } }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.se_145),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //工作区信息

private fun LazyListScope.permTab() {
    item {
    SectionTitle(stringResource(R.string.se_025), stringResource(R.string.se_146))
    CardBox {
        WorkMode.entries.forEach { m ->
            ChoiceRow(stringResource(m.labelRes), stringResource(m.descRes), Cfg.workMode == m) { Cfg.workMode = m }
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_027), stringResource(R.string.se_147))
    CardBox {
        val ctx = LocalContext.current
        var missing by remember { mutableStateOf(Perms.missing(ctx)) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { missing = Perms.missing(ctx) }
        Text(stringResource(R.string.se_148), fontSize = 14.sp, color = TextPrimary)
        Text(
            if (missing.isEmpty()) stringResource(R.string.se_149)
            else stringResource(R.string.se_150) + missing.joinToString("、") { Perms.label(ctx, it) },
            fontSize = 11.sp,
            color = if (missing.isEmpty()) OkGreen else DangerRed
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (missing.isEmpty()) stringResource(R.string.se_028) else stringResource(R.string.se_151), modifier = Modifier.height(38.dp)) {
                launcher.launch(Perms.runtime().toTypedArray())
            }
            GhostButton(stringResource(R.string.se_152), modifier = Modifier.height(38.dp)) { Perms.openAppSettings(ctx) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.se_153) + if (Perms.allFiles()) stringResource(R.string.se_154) else stringResource(R.string.se_155),
            fontSize = 11.sp,
            color = if (Perms.allFiles()) OkGreen else TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        GhostButton(stringResource(R.string.se_156), modifier = Modifier.height(38.dp)) { Perms.openAllFiles(ctx) }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_7fc624f8), stringResource(R.string.tx_ac0cdee7))
    CardBox {
        val specials = Perms.specials(Cfg.workMode)
        //无障碍卡一律出现：Shizuku / Root 模式下它是「一键激活」入口，不需要去系统页手点
        A11yPermCard("AI 读屏、点屏靠这个。没开也能聊，只是屏幕类工具会跑不通。")
        Spacer(Modifier.height(12.dp))
        SpecialPermCard(specials.filter { it != Perms.SP_A11Y })
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle("Shizuku", stringResource(R.string.tx_bd7451f2))
    CardBox {
        val ctx = LocalContext.current
        var st by remember { mutableStateOf(Shiz.probe(ctx)) }
        var msg by remember { mutableStateOf<String?>(null) }
        var note by remember { mutableStateOf<String?>(null) }
        Text(st.desc, fontSize = 12.sp, color = if (st.ok) OkGreen else TextSecondary)
        if (msg != null) {
            Spacer(Modifier.height(6.dp))
            Text(msg!!, fontSize = 11.sp, color = DangerRed)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(stringResource(R.string.tx_f515f8fb)) { st = Shiz.probe(ctx) }
            GhostButton(stringResource(R.string.tx_5e34a485)) {
                Shiz.ask { r ->
                    msg = when (r) {
                        Shiz.Perm.GRANTED -> null
                        Shiz.Perm.NEVER_ASK -> ctx.getString(R.string.shiz_manual)
                        Shiz.Perm.NO_SERVICE -> ctx.getString(R.string.shiz_no_service)
                        Shiz.Perm.ERROR -> ctx.getString(R.string.shiz_timeout)
                        else -> ctx.getString(R.string.shiz_denied)
                    }
                    st = Shiz.probe(ctx)
                }
            }
            GhostButton(stringResource(R.string.shiz_open_app)) {
                Shiz.openManager(ctx)
                st = Shiz.probe(ctx)
            }
        }
        Spacer(Modifier.height(10.dp))
        //Shizuku 有 shell，直接写 secure 设置就能把无障碍挂回去，不必每次进系统设置手点
        CapSwitch(
            stringResource(R.string.se_337),
            when {
                !Cfg.autoA11y -> stringResource(R.string.se_338)
                !st.ok -> stringResource(R.string.se_339)
                else -> stringResource(R.string.se_340)
            },
            Cfg.autoA11y
        ) { on ->
            Cfg.autoA11y = on
            if (on) {
                msg = null
                note = null
                Thread({
                    val ok = b.ai.device.sys.a11y.AutoA11y.run(ctx)
                    val doneText = ctx.getString(R.string.se_357)
                    val failText = ctx.getString(R.string.se_363)
                    st = Shiz.probe(ctx)
                    if (ok) note = doneText else msg = failText
                }, "auto-a11y").apply { isDaemon = true }.start()
            } else {
                note = null
            }
        }
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(note!!, fontSize = 11.sp, color = OkGreen)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_029))
    CardBox {
        val ctx = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_157), fontSize = 14.sp, color = TextPrimary)
                //探测在后台线程跑：每秒把结果刷一次，否则 UI 会一直停在「检测中…」
                var zone by remember { mutableStateOf(Router.dataZone(ctx)) }
                var zoneOk by remember { mutableStateOf(Router.zoneOk()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        zone = Router.dataZone(ctx)
                        zoneOk = Router.zoneOk()
                        delay(1000)
                    }
                }
                Text(
                    stringResource(R.string.se_zone_cur, zone),
                    fontSize = 11.sp,
                    color = if (zoneOk) OkGreen else TextSecondary
                )
            }
            Switch(
                checked = Cfg.zeroWidth,
                onCheckedChange = { Cfg.zeroWidth = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.se_160) +
                stringResource(R.string.se_161),
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(stringResource(R.string.se_162), modifier = Modifier.height(38.dp)) { Router.reprobe() }
    }
    }
} //权限分类（已并入对话分类，入口不再单独占一个 tab）

//字号档位：存的是倍率，1f = 跟随系统
private val FontPresets = listOf(R.string.se_163 to 0.9f, R.string.se_164 to 1f, R.string.se_165 to 1.15f, R.string.se_166 to 1.3f)

/**工具权限一次先铺多少条：几十个下拉同屏组合是设置页最贵的一帧 */
private const val TOOL_PAGE = 12

//回到底部圆钮的三档配色文案：0 默认白 / 1 继承液态玻璃 / 2 继承主题色
private fun scrollBtnStyleLabels() = listOf("默认白", "继承液态玻璃", "继承主题色")

//外观总页：语言 / 主题色 / 字号 / 背景 / 玻璃质感与卡片外观 / 输入框 / Markdown 排版 / 输入提示
//（原「主题」与「输入与质感」两页合并：用户要的所有观感配置都收在这一页，不再分家）
private fun LazyListScope.uiLookSec() {
    item {
        //切语言：存完配置要重建 Activity，界面才会真的换语言
        val act = LocalContext.current as? Activity
        val pickLang: (String) -> Unit = { code ->
            if (Cfg.lang != code) {
                Cfg.lang = code
                act?.recreate()
            }
        }
        SectionTitle(stringResource(R.string.se_167), stringResource(R.string.se_168))
        CardBox {
            val langs = Lang.options
            SelectField(
                stringResource(R.string.se_167),
                langs.firstOrNull { it.first == Cfg.lang }?.second ?: stringResource(R.string.se_187),
                langs.map { it.first },
                optionLabel = { code -> langs.first { it.first == code }.second },
                isSelected = { Cfg.lang == it },
                onSelect = { pickLang(it) }
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_030), stringResource(R.string.se_185))
        CardBox {
            SelectField(
                stringResource(R.string.se_030),
                when (Cfg.darkTheme) {
                    1 -> stringResource(R.string.se_188)
                    2 -> stringResource(R.string.se_189)
                    else -> stringResource(R.string.se_187)
                },
                listOf(0, 1, 2),
                optionLabel = {
                    when (it) {
                        1 -> stringResource(R.string.se_188)
                        2 -> stringResource(R.string.se_189)
                        else -> stringResource(R.string.se_187)
                    }
                },
                isSelected = { Cfg.darkTheme == it },
                onSelect = { Cfg.darkTheme = it }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (Cfg.darkTheme) {
                    1 -> stringResource(R.string.se_190)
                    2 -> stringResource(R.string.se_191)
                    else -> stringResource(R.string.se_192)
                },
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_tint_t), stringResource(R.string.se_tint_d))
        CardBox {
            SelectField(
                stringResource(R.string.se_tint_t),
                stringResource(TINT_NAMES[Cfg.themeTint.coerceIn(0, TINT_NAMES.lastIndex)]),
                TINT_NAMES.indices.toList(),
                optionLabel = { stringResource(TINT_NAMES[it]) },
                isSelected = { Cfg.themeTint == it },
                onSelect = { Cfg.themeTint = it }
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_032), stringResource(R.string.se_193))
        CardBox {
            SelectField(
                stringResource(R.string.se_032),
                FontPresets.firstOrNull { kotlin.math.abs(Cfg.fontScale - it.second) < 0.01f }?.let { stringResource(it.first) }
                    ?: stringResource(R.string.se_164),
                FontPresets.map { it.second },
                optionLabel = { v -> stringResource(FontPresets.first { it.second == v }.first) },
                isSelected = { kotlin.math.abs(Cfg.fontScale - it) < 0.01f },
                onSelect = { Cfg.fontScale = it }
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.se_font_cur, (Cfg.fontScale * 100).toInt()), fontSize = 11.sp, color = TextSecondary)
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_8456bc40), stringResource(R.string.tx_0b5342ec))
        CardBox {
            //字体目录每次进页面重扫：用户可能刚用文件管理器把字体丢进 fonts
            var fontVer by remember { mutableIntStateOf(0) }
            val opts = remember(fontVer) { FontStore.options() }
            val ctx = LocalContext.current
            val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val f = FontStore.import(ctx, uri)
                    if (f != null) {
                        fontVer = fontVer + 1
                        Toast.makeText(ctx, ctx.getString(R.string.tx_946262ed) + f.name, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, ctx.getString(R.string.tx_84defbe5), Toast.LENGTH_SHORT).show()
                    }
                }
            } //导入字体
            SelectField(
                stringResource(R.string.tx_99557469),
                FontStore.label(Cfg.uiFontFile),
                opts,
                desc = stringResource(R.string.tx_a5bccdfb),
                optionLabel = { if (it.isEmpty()) stringResource(R.string.tx_a52f5c20) else FontStore.label(it) },
                isSelected = { Cfg.uiFontFile == it },
                onSelect = { Cfg.uiFontFile = it }
            )
            SelectField(
                stringResource(R.string.tx_c4e93556),
                FontStore.label(Cfg.userFontFile),
                opts,
                optionLabel = { if (it.isEmpty()) stringResource(R.string.tx_112be922) else FontStore.label(it) },
                isSelected = { Cfg.userFontFile == it },
                onSelect = { Cfg.userFontFile = it }
            )
            SelectField(
                stringResource(R.string.tx_f2af02ae),
                FontStore.label(Cfg.aiFontFile),
                opts,
                optionLabel = { if (it.isEmpty()) stringResource(R.string.tx_112be922) else FontStore.label(it) },
                isSelected = { Cfg.aiFontFile == it },
                onSelect = { Cfg.aiFontFile = it }
            )
            Spacer(Modifier.height(8.dp))
            GlassSlider(
                stringResource(R.string.tx_7b5ff17c),
                "${Cfg.globalIndent} dp",
                Cfg.globalIndent.toFloat(),
                0f..32f,
                1f
            ) { Cfg.globalIndent = it.toInt() }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.tx_d94fd817), modifier = Modifier.height(36.dp)) {
                    pickFont.launch(arrayOf("*/*"))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (opts.size <= 1) stringResource(R.string.tx_4d6e461d) else stringResource(R.string.tx_935695b1, opts.size - 1),
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_366d6528), stringResource(R.string.tx_73d7e891))
        CardBox {
            GlassSlider(
                stringResource(R.string.tx_4210b61b),
                "${Cfg.dialogCorner} dp",
                Cfg.dialogCorner.toFloat(),
                0f..32f,
                2f
            ) { Cfg.dialogCorner = it.toInt() }
            GlassSlider(
                stringResource(R.string.tx_b972541e),
                "${Cfg.sliderTrack}%",
                Cfg.sliderTrack.toFloat(),
                5f..60f,
                5f
            ) { Cfg.sliderTrack = it.toInt() }
            GlassSlider(
                stringResource(R.string.tx_a83942eb),
                "${Cfg.sliderTrackH} dp",
                Cfg.sliderTrackH.toFloat(),
                12f..48f,
                2f
            ) { Cfg.sliderTrackH = it.toInt() }
            Text(stringResource(R.string.tx_5d94fa40), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_9bfd20b1), fontSize = 13.5.sp, color = TextPrimary)
                    Text(
                        if (Cfg.sliderAccent) stringResource(R.string.tx_0556c32b) else stringResource(R.string.tx_daf51302),
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = Cfg.sliderAccent,
                    onCheckedChange = { Cfg.sliderAccent = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceWhite,
                        checkedTrackColor = AccentBlue
                    )
                )
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_bg), stringResource(R.string.se_bg_d))
        CardBox {
            SelectField(
                stringResource(R.string.se_bg),
                when (Cfg.bgStyle) {
                    1 -> stringResource(R.string.se_bg_grad)
                    2 -> stringResource(R.string.se_bg_dots)
                    else -> stringResource(R.string.se_bg_plain)
                },
                listOf(0, 1, 2),
                optionLabel = {
                    when (it) {
                        1 -> stringResource(R.string.se_bg_grad)
                        2 -> stringResource(R.string.se_bg_dots)
                        else -> stringResource(R.string.se_bg_plain)
                    }
                },
                isSelected = { Cfg.bgStyle == it },
                onSelect = { Cfg.bgStyle = it }
            )
            Spacer(Modifier.height(10.dp))
            //背景图：从相册选一张铺满页面，模糊度单独一条滑杆
            BgImagePick()
        }
    }
    //玻璃质感：卡片 / 底栏 / 模糊 / 折射 / 高光 / 乳白，外加两种模式都生效的卡片外观（不透明度、圆角）
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_glass_t), stringResource(R.string.se_glass_d))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_glass_card),
                stringResource(R.string.se_glass_card_d),
                Cfg.glassCard
            ) { Cfg.glassCard = it }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.se_tactile),
                stringResource(R.string.se_tactile_d),
                Cfg.tactileBtn
            ) { Cfg.tactileBtn = it }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.se_glass_bar),
                stringResource(R.string.se_glass_bar_d),
                Cfg.glassBar
            ) { Cfg.glassBar = it }
            Spacer(Modifier.height(10.dp))
            //模糊强度 0-20dp：0 只留折射，越大越糊；底栏与底部面板共用这个值
            //走统一滑杆：这样才跟其它滑杆一样能继承主题色 / 调轨道浓度
            GlassSlider(
                stringResource(R.string.se_glass_blur),
                stringResource(R.string.se_glass_blur_v, Cfg.glassBlur),
                Cfg.glassBlur.toFloat(),
                0f..20f,
                step = 1f
            ) { Cfg.glassBlur = it.toInt() }
            Text(stringResource(R.string.se_glass_blur_d), fontSize = 10.sp, color = TextSecondary)
            //开了玻璃卡片才展开细调：折射 / 高光 / 乳白，三个直接影响观感的参数
            FollowBlock(Cfg.glassCard) {
                Spacer(Modifier.height(10.dp))
                GlassSlider(stringResource(R.string.tx_713fd45a), "${Cfg.glassLens} dp", Cfg.glassLens.toFloat(), 0f..24f, step = 1f) {
                    Cfg.glassLens = it.toInt()
                }
                Text(stringResource(R.string.tx_1b605a1e), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                GlassSlider(stringResource(R.string.tx_d73f1ed6), "${Cfg.glassHi}%", Cfg.glassHi.toFloat(), 0f..100f, step = 5f) {
                    Cfg.glassHi = it.toInt()
                }
                Text(stringResource(R.string.tx_e82c50b0), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                GlassSlider(stringResource(R.string.tx_fd5f796f), "${Cfg.glassVeil}%", Cfg.glassVeil.toFloat(), 0f..60f, step = 2f) {
                    Cfg.glassVeil = it.toInt()
                }
                Text(stringResource(R.string.tx_ba3f1876), fontSize = 10.sp, color = TextSecondary)
            }
            //卡片不透明度：两种模式都生效——白卡模式直接调白底 alpha，玻璃模式按比例缩乳白遮罩
            Spacer(Modifier.height(10.dp))
            GlassSlider(stringResource(R.string.tx_fa5d97dd), "${Cfg.cardAlpha}%", Cfg.cardAlpha.toFloat(), 0f..100f, step = 5f) {
                Cfg.cardAlpha = it.toInt()
            }
            Text(stringResource(R.string.tx_47aeba9f), fontSize = 10.sp, color = TextSecondary)
            //卡片圆角：预设档位滑杆，不做输入框；白卡与玻璃卡共用
            Spacer(Modifier.height(10.dp))
            GlassSlider(stringResource(R.string.tx_3d86509a), "${Cfg.cardCorner} dp", Cfg.cardCorner.toFloat(), 0f..32f, step = 4f) {
                Cfg.cardCorner = it.toInt()
            }
            Text(stringResource(R.string.tx_ec83af0d), fontSize = 10.sp, color = TextSecondary)
            //回到底部圆钮的配色：三档都给白卡 / 玻璃卡两种模式用，选哪个当场生效
            Spacer(Modifier.height(10.dp))
            SelectField(
                label = stringResource(R.string.tx_f778da7e),
                value = scrollBtnStyleLabels()[Cfg.scrollBtnStyle],
                options = listOf(0, 1, 2),
                desc = stringResource(R.string.tx_96a5feb3),
                optionLabel = { scrollBtnStyleLabels()[it] },
                isSelected = { it == Cfg.scrollBtnStyle },
                onSelect = { Cfg.scrollBtnStyle = it }
            )
        }
    }
    //输入框：浮动输入、全屏入口、超长粘贴转文件
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_033), stringResource(R.string.se_195))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_196),
                stringResource(R.string.se_197),
                Cfg.floatInput
            ) { Cfg.floatInput = it }
            Spacer(Modifier.height(10.dp))
            //新逻辑：超 700 字才出现全屏入口；关掉就让它一直挂在语音右边
            CapSwitch(
                stringResource(R.string.se_fullin_t),
                stringResource(R.string.se_fullin_d),
                Cfg.fullInputNew
            ) { Cfg.fullInputNew = it }
            Spacer(Modifier.height(10.dp))
            //粘贴超长文本自动转文件：默认开，阈值滑杆 500 起、500 一档
            CapSwitch(
                stringResource(R.string.se_paste_t),
                stringResource(R.string.se_paste_d),
                Cfg.pasteToFile
            ) { Cfg.pasteToFile = it }
            FollowBlock(Cfg.pasteToFile) {
                Spacer(Modifier.height(6.dp))
                //同上：统一走 miuix 滑杆，500 一档，能继承主题色
                GlassSlider(
                    stringResource(R.string.se_paste_limit),
                    stringResource(R.string.se_paste_limit_v, Cfg.pasteLimit),
                    Cfg.pasteLimit.toFloat(),
                    500f..20000f,
                    step = 500f
                ) { Cfg.pasteLimit = it.toInt() }
                Text(stringResource(R.string.se_paste_limit_d), fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
    //输入提示文案：输入框空白时的占位提示
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_035), stringResource(R.string.se_198))
        CardBox {
            Field(
                label = stringResource(R.string.se_199),
                value = Cfg.hintText,
                onValueChange = { Cfg.hintText = it },
                placeholder = stringResource(R.string.se_200),
                singleLine = false,
                minLines = 3
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (Cfg.hintText.isBlank()) stringResource(R.string.se2_hint_none)
                else stringResource(R.string.se2_hint_n, Cfg.hintText.lines().count { it.isNotBlank() }),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
} //外观总页

//个性化子页：头像、气泡胶囊与桌宠
private fun LazyListScope.uiPersonSec(onOpenPet: () -> Unit) {
    item {
        SectionTitle(stringResource(R.string.se_169), stringResource(R.string.se_170))
        CardBox {
            CapSwitch(stringResource(R.string.se_171), stringResource(R.string.se_172), Cfg.avatarOn) {
                Cfg.avatarOn = it
            }
            Spacer(Modifier.height(8.dp))
            //Waifu 已挪到「回复信息」分区，跟关闭流式输出放一起
            CapSwitch(stringResource(R.string.se_173), stringResource(R.string.se_174), Cfg.bubbleCapsule) {
                Cfg.bubbleCapsule = it
            }
        }
    }
    item {
        Spacer(Modifier.height(10.dp))
        AvatarPick(stringResource(R.string.se_177), stringResource(R.string.se_178), Cfg.avatarUser) { p ->
            Avatar.forget(Cfg.avatarUser)
            Cfg.avatarUser = p
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        AvatarPick(stringResource(R.string.se_179), stringResource(R.string.se_180), Avatar.aiPath()) { p ->
            Avatar.forget(Cfg.avatarAi)
            Cfg.avatarAi = p
            //清空就等于放弃自定义，回落到跟角色卡
            Cfg.avatarAiMode = if (p.isEmpty()) "persona" else "custom"
        }
    }
    item {
        Spacer(Modifier.height(16.dp))
        CardBox {
            ChoiceRow(stringResource(R.string.se_181), stringResource(R.string.se_182), Cfg.avatarAiMode != "custom") {
                Cfg.avatarAiMode = "persona"
            }
            ChoiceRow(
                stringResource(R.string.se_183),
                if (Cfg.avatarAi.isEmpty()) stringResource(R.string.se_184) else stringResource(R.string.se_186),
                Cfg.avatarAiMode == "custom"
            ) {
                if (Cfg.avatarAi.isNotBlank()) Cfg.avatarAiMode = "custom"
            }
        }
    }
    item {
        Spacer(Modifier.height(10.dp))
        //桌宠 + 回车发送合成一张卡：两行都走 CardBox，圆角 / 描边 / 玻璃 / 内边距跟设置页其它卡片一致
        CardBox {
            //桌宠：一整行独立开关，配置图标就在开关左边，点它进桌宠设置页
            PetEntryRow(onOpenPet)
            Spacer(Modifier.height(10.dp))
            //回车发送：外接键盘/物理键盘才有意义；关掉后回车只换行，发送靠右侧按钮
            CapSwitch(
                "回车键发送",
                if (Cfg.enterSend) "回车直接发送，Shift+回车换行" else "回车只换行，不发送",
                Cfg.enterSend
            ) { Cfg.enterSend = it }
        }
    }
    } //个性化子页

//（原「输入与质感」子页内容已合并到「外观」总页 uiLookSec）

private fun LazyListScope.uiNavSec() {
    //悬浮聊天窗：先自查权限，再给开关，最后是窗口参数——三项都在一页里调完
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_2ecb1bb9), stringResource(R.string.tx_bb007351))
        CardBox {
            val fctx = LocalContext.current
            val overlayOk = Pet.canShow(fctx)
            val aliveOk = Perms.batteryFree(fctx)
            //权限自查：没悬浮窗权限时开关打开也显示不出来，以前只表现为「点了没反应」
            SelfCheckRow(
                stringResource(R.string.tx_720ab972),
                if (overlayOk) stringResource(R.string.tx_0c47bf01) else stringResource(R.string.tx_b6f6ccf7),
                overlayOk
            ) { PetGrant.openOverlaySetting(fctx) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_2911c1e5),
                if (aliveOk) stringResource(R.string.tx_cd36e684) else stringResource(R.string.tx_adc2cb46),
                aliveOk
            ) { Perms.openSpecial(fctx, Perms.SP_BATTERY) }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.tx_3b4c7ed4),
                if (Cfg.fcOn) stringResource(R.string.tx_d742e670) else stringResource(R.string.tx_9ec0f5da),
                Cfg.fcOn
            ) { on ->
                if (!on) {
                    Cfg.fcOn = false
                    FChat.hide()
                } else if (Pet.canShow(fctx)) {
                    Cfg.fcOn = true
                    FChat.show(fctx)
                } else {
                    //没悬浮窗权限：开关不落地，先送去系统设置授权
                    Cfg.fcOn = false
                    PetGrant.openOverlaySetting(fctx)
                }
            }
            //窗口参数：改完立刻 apply()，窗口不重建、位置不变
            Spacer(Modifier.height(10.dp))
            GlassSlider(stringResource(R.string.tx_cae01fea), "${Cfg.fcW} dp", Cfg.fcW.toFloat(), 220f..480f, step = 10f) {
                Cfg.fcW = it.toInt()
                FChat.apply()
            }
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_c73258a2), "${Cfg.fcH} dp", Cfg.fcH.toFloat(), 260f..900f, step = 20f) {
                Cfg.fcH = it.toInt()
                FChat.apply()
            }
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_e013ea26), "${Cfg.fcAlpha}%", Cfg.fcAlpha.toFloat(), 40f..100f, step = 5f) {
                Cfg.fcAlpha = it.toInt()
                FChat.apply()
            }
            Text(stringResource(R.string.tx_529f8817), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_48f369a7), "${Cfg.fcFont} sp", Cfg.fcFont.toFloat(), 10f..22f, step = 1f) {
                Cfg.fcFont = it.toInt()
                FChat.apply()
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                //位置复位：把 fcX / fcY 归 -1，apply() 时会落回默认的右上角
                GhostButton(stringResource(R.string.tx_e90e4777), modifier = Modifier.height(36.dp)) {
                    Cfg.fcX = -1
                    Cfg.fcY = -1
                    FChat.apply()
                }
                GhostButton(
                    if (Cfg.fcOn) stringResource(R.string.tx_5f4766cc) else stringResource(R.string.tx_ea02bb1f),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (Cfg.fcOn) {
                        Cfg.fcOn = false
                        FChat.hide()
                    } else if (Pet.canShow(fctx)) {
                        Cfg.fcOn = true
                        FChat.show(fctx)
                    } else {
                        PetGrant.openOverlaySetting(fctx)
                    }
                }
            }
        }
    }
    //返回手势：预测性返回 + 直达系统手势导航设置
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_037), stringResource(R.string.se2_back_sub))
        CardBox {
            val gctx = LocalContext.current
            CapSwitch(
                stringResource(R.string.se2_back_title),
                stringResource(R.string.se2_back_desc),
                Cfg.predictiveBack
            ) { Cfg.predictiveBack = it }
            Spacer(Modifier.height(6.dp))
            Text(
                "安卓 13 起支持「预测性返回」：滑动手势时先把当前页缩小、露出上一页，中途松手还能取消。" +
                    "系统没开这个能力时开关不会有效果，开关状态由应用自己记。",
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_0c1e4c99), modifier = Modifier.height(36.dp)) {
                //直接跳到系统手势导航页：各家 ROM 的入口不同，跳不过去就退到应用详情页
                runCatching {
                    //用字面量 action：部分编译 SDK 里没有 ACTION_GESTURE_NAVIGATION_SETTINGS 这个常量
                    gctx.startActivity(
                        Intent("android.settings.GESTURE_NAVIGATION_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { Perms.openAppSettings(gctx) }
            }
        }
    }
} //外观分类：悬浮与手势

//扩展分类：一张卡一个 item，没滚到的卡不组合（工具权限那张卡最重，几十个下拉）
private fun LazyListScope.extAutoSec(
    onNewTask: () -> Unit,
    onNewFlow: () -> Unit,
    onNewTrigger: () -> Unit
) {
    //运行总览：三类自动化各自的开关状态，外加一键全停 / 全恢复
    item {
        SectionTitle(stringResource(R.string.tx_85067db3), stringResource(R.string.tx_3ab3186c))
        CardBox {
            val tasks0 = Schedules.list
            val flows0 = Flows.list
            val rules0 = Triggers.list
            Text(
                "定时任务 " + tasks0.count { it.on } + "/" + tasks0.size +
                    " · 工作流 " + flows0.count { it.on } + "/" + flows0.size +
                    " · 通知触发 " + rules0.count { it.on } + "/" + rules0.size,
                fontSize = 13.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(stringResource(R.string.tx_e5c4f1fc), modifier = Modifier.weight(1f).height(38.dp)) {
                    //先取快照再逐个切：toggle 会改 state，边遍历边改会漏
                    tasks0.filter { it.on }.forEach { Schedules.toggle(it.id, false) }
                    flows0.filter { it.on }.forEach { Flows.toggle(it.id, false) }
                    rules0.filter { it.on }.forEach { Triggers.toggle(it.id, false) }
                }
                GhostButton(stringResource(R.string.tx_f4590f7d), modifier = Modifier.weight(1f).height(38.dp)) {
                    tasks0.filterNot { it.on }.forEach { Schedules.toggle(it.id, true) }
                    flows0.filterNot { it.on }.forEach { Flows.toggle(it.id, true) }
                    rules0.filterNot { it.on }.forEach { Triggers.toggle(it.id, true) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.tx_950a94dd),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    item {
        SectionTitle(stringResource(R.string.se_044), stringResource(R.string.se2_task_sub))
        CardBox {
            val ctx = LocalContext.current
            val tasks = Schedules.list
            val on = tasks.count { it.on }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (tasks.isEmpty()) stringResource(R.string.se2_task_none) else stringResource(R.string.se2_task_n, tasks.size, on),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (tasks.isEmpty()) stringResource(R.string.se2_task_hint)
                        else tasks.filter { it.on }.take(2)
                            .joinToString("；") { "${if (it.daily) "每天 " else ""}${Alarms.fmt(it.at)} ${it.prompt.replace("\n", " ").take(16)}" }
                            .ifBlank { ctx.getString(R.string.se2_task_off) },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                GhostButton(stringResource(R.string.se2_manage)) { onNewTask() }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_228526dc), stringResource(R.string.tx_7bb29de8))
        CardBox {
            val flows = Flows.list
            val enabled = flows.count { it.on }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "共 " + flows.size + " 条，启用 " + enabled + " 条",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (flows.isEmpty()) stringResource(R.string.tx_df74b581)
                        else flows.take(2).joinToString("；") { f ->
                            f.name + "（" + f.steps.size + " 步" +
                                (if (f.at > 0L) " · " + Alarms.fmt(f.at) else "") + "）"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                GhostButton(stringResource(R.string.tx_86454fc7)) { onNewFlow() }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.ntg_title), stringResource(R.string.ntg_sub))
        CardBox {
            val rules = Triggers.list
            val on = rules.count { it.on }
            val any = stringResource(R.string.ntg_any)
            val all = stringResource(R.string.ntg_allkw)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (rules.isEmpty()) stringResource(R.string.ntg_none)
                        else stringResource(R.string.ntg_count, rules.size, on),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (rules.isEmpty()) stringResource(R.string.ntg_hint)
                        else rules.filter { it.on }.take(2)
                            .joinToString("；") { t -> t.app.ifBlank { any } + " · " + t.keyword.ifBlank { all } }
                            .ifBlank { stringResource(R.string.ntg_offall) },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                GhostButton(stringResource(R.string.ntg_manage)) { onNewTrigger() }
            }
        }
    }
    //到点能不能跑起来：定时任务三件套（精确闹钟 / 通知 / 后台保活）缺一个就会「设了没动静」
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_0e2af5f5), stringResource(R.string.tx_5b921cf1))
        CardBox {
            val c = LocalContext.current
            val exact = Alarms.canExact(c)
            val noti = Build.VERSION.SDK_INT < 33 ||
                Perms.granted(c, android.Manifest.permission.POST_NOTIFICATIONS)
            val alive = Perms.batteryFree(c)
            SelfCheckRow(
                stringResource(R.string.tx_15ec4b98),
                if (exact) stringResource(R.string.tx_2627160e) else stringResource(R.string.tx_06972491),
                exact
            ) { Alarms.openExactAlarmSettings(c) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_5660bcd2),
                if (noti) stringResource(R.string.tx_a6a648c1) else stringResource(R.string.tx_8508566e),
                noti
            ) { Perms.openNotiSettings(c) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_2911c1e5),
                if (alive) stringResource(R.string.tx_cc49b1fd) else stringResource(R.string.tx_b90dde67),
                alive
            ) { Perms.openSpecial(c, Perms.SP_BATTERY) }
        }
    }
    //接下来要跑：定时任务与已排期的工作流按时间合并，只列前三条
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_9f2e930a), stringResource(R.string.tx_02895542))
        CardBox {
            val soon = (Schedules.active.map { it.at to it.prompt.replace("\n", " ").take(18) } +
                Flows.list.filter { it.on && it.at > 0L }.map { it.at to ("工作流 · " + it.name) })
                .sortedBy { it.first }
                .take(3)
            if (soon.isEmpty()) {
                Text(stringResource(R.string.tx_70607644), fontSize = 12.sp, color = TextSecondary)
            } else {
                soon.forEachIndexed { i, (at, name) ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    Text(Alarms.fmt(at) + "  ·  " + name, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
    //任务通知与灵动岛：原挂在「外观」页，本质是任务通知的显示策略，跟自动化放一起
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_709d84ce), stringResource(R.string.tx_a71af5ad))
        CardBox {
            val c = LocalContext.current
            val notiOk = Build.VERSION.SDK_INT < 33 ||
                Perms.granted(c, android.Manifest.permission.POST_NOTIFICATIONS)
            val aliveOk = Perms.batteryFree(c)
            SelfCheckRow(
                stringResource(R.string.tx_846cdaf0),
                if (notiOk) stringResource(R.string.tx_c4da04aa) else stringResource(R.string.tx_b324c1b6),
                notiOk
            ) { Perms.openNotiSettings(c) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_2911c1e5),
                if (aliveOk) stringResource(R.string.tx_3a605fa6) else stringResource(R.string.tx_2474be5f),
                aliveOk
            ) { Perms.openSpecial(c, Perms.SP_BATTERY) }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tx_3aaccc6e, Vendor.rom.label), fontSize = 10.sp, color = TextSecondary)
            //通道级问题自查项覆盖不到（通知分类被关 / 厂商焦点通知没开），原样保留这条说明
            val blocked = Island.blockedReason()
            if (blocked != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    blocked,
                    fontSize = 11.sp,
                    color = DangerRed,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(8.dp), tint = DangerRedSoft)
                        .tap { Perms.openNotiSettings(c) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
                Text(
                    stringResource(R.string.tx_af29b0db),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.tx_8280b5ae),
                stringResource(R.string.tx_12c3b1d7),
                Cfg.islandOn
            ) { Cfg.islandOn = it }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.tx_b7a7dafe),
                stringResource(R.string.tx_edd7189b),
                Cfg.islandDone
            ) { Cfg.islandDone = it }
            FollowBlock(Cfg.islandDone) {
                Spacer(Modifier.height(8.dp))
                GlassSlider(
                    stringResource(R.string.tx_110dd655),
                    stringResource(R.string.tx_a6f0b1dc, Cfg.islandDoneSec),
                    Cfg.islandDoneSec.toFloat(),
                    0f..300f,
                    step = 10f
                ) { Cfg.islandDoneSec = it.toInt() }
                Text(stringResource(R.string.tx_8c7432b9), fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
} //自动化子页

/**
 * 一行自查：状态徽标（已通过 / 未通过）+ 名称 + 说明；未通过时右侧给「去开启」，点了跳系统页。
 * 徽标用文字而不是纯色圆点：圆点看不出到底是「装饰」还是「检查结果」。
 */
@Composable
private fun SelfCheckRow(name: String, desc: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) stringResource(R.string.tx_ecfa64c1) else stringResource(R.string.tx_4fcdbfe5),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (ok) OkGreen else DangerRed,
            modifier = Modifier
                .background(if (ok) OkGreen.copy(alpha = 0.12f) else DangerRedSoft, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = if (ok) TextSecondary else DangerRed)
        }
        if (!ok) {
            Text(
                stringResource(R.string.tx_5e213ddb),
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier.tap { onFix() }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
} //自查一行

//接入子页：A2A 节点、搜索平台、MCP 服务
private fun LazyListScope.extLinkSec(
    onOpenA2a: () -> Unit,
    onEditMcp: (McpServer) -> Unit
) {
    item {
        SectionTitle("A2A", stringResource(R.string.tx_6e63dd3c))
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "本机服务：" + if (A2aSrv.running) stringResource(R.string.tx_d9524747) + A2aSrv.baseUrl() else stringResource(R.string.tx_b15d9127),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (A2aNodes.list.isEmpty()) stringResource(R.string.tx_c51ed4da)
                        else stringResource(R.string.tx_ea9bc057) + A2aNodes.list.size + stringResource(R.string.tx_a3af3303) +
                            A2aNodes.list.take(2).joinToString("；") { it.name },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                GhostButton(stringResource(R.string.tx_231fdea9)) { onOpenA2a() }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_046), stringResource(R.string.se2_search_sub))
        CardBox {
            val providers = Search.providers
            val sel = Cfg.searchProvider
            if (providers.isEmpty()) {
                Text(stringResource(R.string.se2_search_empty), fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.se2_search_empty2),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                SelectField(
                    stringResource(R.string.se_046),
                    providers.firstOrNull { it.id == sel }?.label ?: stringResource(R.string.se2_search_off),
                    listOf("") + providers.map { it.id },
                    optionLabel = { id -> providers.firstOrNull { it.id == id }?.label ?: stringResource(R.string.se2_search_off) },
                    isSelected = { sel == it },
                    onSelect = { Cfg.searchProvider = it }
                )
                val cur = Search.selected
                if (cur != null) {
                    Spacer(Modifier.height(8.dp))
                    if (cur.fields.isEmpty()) {
                        Text(stringResource(R.string.se2_search_needless, cur.label), fontSize = 12.sp, color = OkGreen)
                    } else {
                        cur.fields.forEach { f ->
                            Field(
                                label = "${cur.label} ${f.label}",
                                value = Cfg.searchCfg(cur.id, f.id),
                                onValueChange = { Cfg.setSearchCfg(cur.id, f.id, it) },
                                placeholder = f.hint,
                                secret = f.id == "key"
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(cur.hint, fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    val miss = Search.missing()
                    Text(
                        if (miss == null) stringResource(R.string.se2_search_ok)
                        else stringResource(R.string.se2_search_miss, miss.label),
                        fontSize = 10.sp,
                        color = if (miss == null) OkGreen else DangerRed
                    )
                }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        McpSection(onEdit = onEditMcp)
    }
    } //接入子页

//技能与角色子页：角色卡、系统提示词、技能、技能包
private fun LazyListScope.extAgentSec(
    onEdit: (Skill) -> Unit,
    onEditPersona: (Persona) -> Unit,
    onEditPack: (SkillPack) -> Unit
) {
    item {
        SectionTitle(stringResource(R.string.se_047), stringResource(R.string.se2_persona_sub))
        CardBox {
            ChoiceRow(stringResource(R.string.se2_persona_none), stringResource(R.string.se2_persona_none_sub), Cfg.personaId.isBlank()) { Agent.setPersona("") }
            Personas.list.forEach { p ->
                PersonaRow(
                    p = p,
                    selected = Cfg.personaId == p.id,
                    onSelect = { Agent.setPersona(p.id) },
                    onEdit = { onEditPersona(p) }
                )
            }
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.se2_persona_add), modifier = Modifier.height(38.dp)) {
                onEditPersona(Persona(id = "", name = ""))
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_048), stringResource(R.string.se2_prompt_sub))
        CardBox {
            Field(
                label = stringResource(R.string.se2_prompt_label),
                value = Cfg.systemPrompt,
                onValueChange = { Cfg.systemPrompt = it },
                placeholder = stringResource(R.string.se_226),
                secret = false,
                singleLine = false,
                minLines = 3
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (Cfg.systemPrompt.isBlank()) stringResource(R.string.se_227)
                else stringResource(R.string.se2_prompt_len, Cfg.systemPrompt.length),
                fontSize = 10.sp,
                color = if (Cfg.systemPrompt.isBlank()) TextSecondary else OkGreen
            )
            Spacer(Modifier.height(6.dp))
            //花括号参数：只在这里注入，点一下追加到提示末尾；拼系统提示时替换成真实值，聊天输入不受影响
            Text(stringResource(R.string.tx_f05a0996), fontSize = 10.sp, color = TextSecondary)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PromptVars.ALL.forEach { v ->
                    Text(
                        v.token,
                        fontSize = 10.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                            .tap {
                                //分隔符按需补：空提示直接写，末尾是空白就只追加 token
                                val cur = Cfg.systemPrompt
                                Cfg.systemPrompt = when {
                                    cur.isBlank() -> v.token
                                    cur.last().isWhitespace() -> cur + v.token
                                    else -> cur + " " + v.token
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.tx_2ae34d78),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            //拼接顺序：角色卡自带提示与这里写的预设谁在前；默认角色在前
            SelectField(
                stringResource(R.string.tx_28d68b24),
                if (Cfg.promptOrder == 1) stringResource(R.string.tx_1cf2d4cf) else stringResource(R.string.tx_06d23d3f),
                listOf(0, 1),
                optionLabel = { if (it == 1) stringResource(R.string.tx_1cf2d4cf) else stringResource(R.string.tx_06d23d3f) },
                isSelected = { Cfg.promptOrder == it },
                onSelect = { Cfg.promptOrder = it }
            )
            Text(
                "两段是叠加关系，不是二选一：角色卡里的提示与这里的预设都会进系统提示，" +
                    "这一段只决定谁在前面。角色提示写得强势时选「自定义在前」，把人设压不住的那部分放到前面。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_049), stringResource(R.string.se_229))
        CardBox {
            val list = Skills.list
            if (list.isEmpty()) {
                Text(stringResource(R.string.se_230), fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.se_231) +
                        stringResource(R.string.se_232),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                list.forEach { s ->
                    SkillRow(s, onEdit = { onEdit(s) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.se_233), modifier = Modifier.height(38.dp)) {
                    onEdit(Skill("", "", "", "", true))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.se2_skill_enabled, Skills.enabled.size, list.size),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.skp_t), stringResource(R.string.skp_d))
        SkillPackBox(onEdit = onEditPack)
    }
    } //技能与角色子页

//工具权限子页：每个工具一条允许/询问/拒绝
private fun LazyListScope.extToolSec() {
    item {
        SectionTitle(stringResource(R.string.se_050), stringResource(R.string.se_235))
        //MCP 能力不列在这里：它们由 MCP 服务自己拉取，到「扩展 → MCP 服务」里单独管
        val tools = Tools.all.filterNot { it.startsWith(Tools.MCP) }
        //一次只组合前 12 条：几十个下拉挤在同一个 item 里，进页面就要全量测量一遍
        var more by remember { mutableStateOf(false) }
        CardBox {
            (if (more) tools else tools.take(TOOL_PAGE)).forEach { tool ->
                val pol = Cfg.policy(tool)
                SelectField(
                    Tools.label(tool),
                    when (pol) {
                        "allow" -> stringResource(R.string.se_239)
                        "deny" -> stringResource(R.string.se_240)
                        else -> stringResource(R.string.se_238)
                    },
                    listOf("ask", "allow", "deny"),
                    desc = if (Tools.isHighRisk(tool)) stringResource(R.string.se2_risk_high, tool)
                    else stringResource(R.string.se2_risk_normal, tool),
                    optionLabel = {
                        when (it) {
                            "allow" -> stringResource(R.string.se_239)
                            "deny" -> stringResource(R.string.se_240)
                            else -> stringResource(R.string.se_238)
                        }
                    },
                    isSelected = { pol == it },
                    onSelect = { Cfg.setPolicy(tool, it) }
                )
            }
            if (!more && tools.size > TOOL_PAGE) {
                Spacer(Modifier.height(6.dp))
                GhostButton(stringResource(R.string.se_tools_more, tools.size - TOOL_PAGE)) {
                    more = true
                }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_c0a99954), stringResource(R.string.tx_bb3f978b))
        val ctx = LocalContext.current
        var wipeMsg by remember { mutableStateOf("") }
        var asking by remember { mutableStateOf(false) }
        HostInOverlay(asking) {
            if (asking) {
                ScrimCard(onDismiss = { asking = false }) {
                    Text(stringResource(R.string.tx_9ca1cb1f), fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "会清掉 WebView 的 Cookie、WebStorage、缓存与表单记录，之后各网站都要重新登录。" +
                            "音乐专区的登录态在加密库里，不受影响。",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            stringResource(R.string.tx_625fb26b),
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.tap { asking = false }.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Text(
                            stringResource(R.string.tx_0a60a66c),
                            fontSize = 13.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .tap {
                                    asking = false
                                    wipeMsg = WebWipe.clearAll(ctx)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        CardBox {
            Text(
                "会清 WebView 的 Cookie、WebStorage、缓存与表单记录。清完各网站要重新登录；" +
                    "音乐专区的登录态存在加密库里，不受影响，要清请到对应平台卡片单独清。",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_2898cdb7)) { asking = true }
            if (wipeMsg.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(wipeMsg, fontSize = 11.sp, color = TextPrimary)
            }
        }
    }
} //工具权限子页

//工作区子页：数据根目录、本机 Linux 环境
private fun LazyListScope.extWsSec() {
    //前置检查：工作区能绑到共享存储的前提是「全部文件访问」，没给就会绑不进任意目录
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_59fee9a5), stringResource(R.string.tx_0450050c))
        CardBox {
            val c = LocalContext.current
            val allOk = Perms.allFiles()
            SelfCheckRow(
                stringResource(R.string.tx_3ceeb66d),
                if (allOk) stringResource(R.string.tx_f3b58907) else stringResource(R.string.tx_119c6296),
                allOk
            ) { Perms.openAllFiles(c) }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tx_90d5ce80),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    item {
        //设置页内联整张工作区卡：数据根、绑定路径、目录统计与绑定操作都在这里，
        //「浏览」按钮走 WorkspacePick，由设置页的弹窗挂载点接住并弹出目录选择器
        WorkspaceSection(onBrowse = { WorkspacePick.open = true })
    }
} //工作区子页

/** Linux 沙箱单独一页：与工作区解耦，整页就是一个终端 + 一键装开发环境 */
private fun LazyListScope.extLinuxSec() {
    item {
        LinuxTermPage()
    }
} //Linux 沙箱子页

//还剩文件沙箱一个专区的计划清单：页面上先讲清会有什么，别点进去一片空白
/** 文件沙箱专区的计划清单：存资源 id，交给 extPlanSec 在 Composable 里解析 */
private val EX_BOX_PLAN = listOf(
    R.string.tx_a39e54aa,
    R.string.tx_ccd94af0,
    R.string.tx_423b0645
)

/** 专区计划页：清单 + 一句状态说明，等实现落地后替换成真控件 */
private fun LazyListScope.extPlanSec(titleRes: Int, subRes: Int, points: List<Int>) {
    item {
        SectionTitle(stringResource(titleRes), stringResource(subRes))
        CardBox {
            points.forEachIndexed { i, res ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                Text("· " + stringResource(res), fontSize = 12.sp, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.se_hub_soon), fontSize = 10.sp, color = TextSecondary)
        }
    }
} //专区计划页
/** 技能包：外部目录里有哪些包、哪些函数校验没过；改完包点重新扫描 */
@Composable
private fun SkillPackBox(onEdit: (SkillPack) -> Unit) {
    val ctx = LocalContext.current
    var packs by remember { mutableStateOf(SkillPacks.packs) }
    var broken by remember { mutableStateOf(SkillPacks.broken()) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.skp_count, packs.size, packs.sumOf { it.funcs.size }),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(stringResource(R.string.skp_d), fontSize = 9.sp, color = TextSecondary, maxLines = 2)
            }
            GhostButton(stringResource(R.string.skp_rescan), modifier = Modifier.height(34.dp)) {
                runCatching { SkillPacks.reload(ctx) }
                packs = SkillPacks.packs
                broken = SkillPacks.broken()
            }
        }
        packs.forEach { p ->
            Spacer(Modifier.height(6.dp))
            //包整块可点：进详情改 README / skill.json / 模板，并填它声明出来的密钥
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, gate = 0.dp)
                    .tap { onEdit(p) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    p.name.ifBlank { p.id } + "（" + p.id + "）",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    p.funcs.joinToString("、") { it.name }.ifBlank { p.desc },
                    fontSize = 9.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
        if (broken.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.skp_broken), fontSize = 10.sp, color = DangerRed)
            broken.forEach { f ->
                Text("· ${f.skill}/${f.name}：${f.bad}", fontSize = 9.sp, color = DangerRed)
            }
        }
    }
} //技能包列表

//官网地址：服务端根地址在构建期从 local.properties 注入，仓库里没有真值
internal val SITE_URL: String get() = b.ai.core.cfg.app.Bai.host
internal const val GITHUB_URL = "https://github.com/mubai0122/baicodex"

/** 关于：应用名 + 一句话描述 + 版本号 / 最新版本 + 检查更新 + 更新历史 + 官网 / GitHub 链接 */
private fun LazyListScope.aboutTab(onOpenFeedback: () -> Unit) {
    //应用信息 +检查更新 +更新历史：状态只活在这一块里，滚出屏幕随 item 一起释放
    item {
    var latest by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var busyHis by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    //进页面静默查一次，只为了把「最新版本」那行填上，不弹卡片
    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) { runCatching { Update.check() }.getOrNull() }
        latest = info?.latestVersion ?: ""
    }

    SectionTitle(stringResource(R.string.se_006))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .liquidBg(RoundedCornerShape(11.dp), tint = AccentBlueSoft, gate = 0.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Info, null, tint = AccentBlue, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Baicodex", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    stringResource(R.string.se_241),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se_242), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            //连点 5 次解锁开发者模式：解锁后才在关于页最下方放出开关，平时不占地方
            Text(
                "${Update.clientVersion()}",
                fontSize = 12.sp,
                color = TextPrimary,
                modifier = Modifier.tap {
                    if (!Cfg.devShow && Dev.tap()) {
                        Cfg.devShow = true
                        UpdateUi.tip = ctx.getString(R.string.tx_7e382efd)
                    }
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se_243), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                latest.ifBlank { stringResource(R.string.se_244) },
                fontSize = 12.sp,
                color = if (latest.isBlank()) TextSecondary else OkGreen
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) stringResource(R.string.se_245) else stringResource(R.string.se_054), modifier = Modifier.height(38.dp)) {
                if (busy) return@GhostButton
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.check() } }
                    busy = false
                    r.onSuccess { info ->
                        latest = "${info.latestVersion}"
                        when {
                            //服务端关了更新开关
                            !info.updateEnabled -> UpdateUi.tip = ctx.getString(R.string.se2_upd_off, info.latestVersion)
                            info.hasUpdate -> UpdateUi.prompt = info
                            else -> UpdateUi.tip = ctx.getString(R.string.se2_upd_latest, info.latestVersion)
                        }
                    }.onFailure {
                        UpdateUi.tip = ctx.getString(R.string.se2_upd_fail, it.message?.take(80).orEmpty())
                    }
                }
            }
            GhostButton(if (busyHis) stringResource(R.string.se_133) else stringResource(R.string.se_250), modifier = Modifier.height(38.dp)) {
                if (busyHis) return@GhostButton
                busyHis = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.history() } }
                    busyHis = false
                    r.onSuccess { UpdateUi.history = it }
                        .onFailure {
                            UpdateUi.tip = ctx.getString(R.string.se2_upd_hist_fail, it.message?.take(80).orEmpty())
                        }
                }
            }
        }
    }
    }


    item {
    val ctx = LocalContext.current
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_252))
    CardBox {
        //服务端地址没配（fork 出去没填 local.properties）：链接置灰，点了也没意义
        LinkRow(stringResource(R.string.se_253), SITE_URL, enabled = b.ai.core.cfg.app.Bai.configured) {
            openUrl(ctx, SITE_URL)
        }
        Spacer(Modifier.height(6.dp))
        //仓库还没建，留空就显示成灰的、点不动
        LinkRow("GitHub", GITHUB_URL.ifBlank { stringResource(R.string.se_254) }, enabled = GITHUB_URL.isNotBlank()) {
            openUrl(ctx, GITHUB_URL)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    //反馈入口：单独一张卡，点进去是整页表单（分类 + 内容 + 联系方式）
    CardBox {
        Row(
            Modifier.fillMaxWidth().tap { onOpenFeedback() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft, gate = 0.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Email, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.fb_entry),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(stringResource(R.string.fb_entry_d), fontSize = 11.sp, color = TextSecondary)
            }
            Text("›", fontSize = 18.sp, color = TextSecondary)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    //评价调研：开关控制要不要接受邀请；下面一条是手动入口，点开就是同款 Star 卡片
    SectionTitle(stringResource(R.string.rate_title), stringResource(R.string.rate_title_d))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rate_sw), fontSize = 14.sp, color = TextPrimary)
                Text(stringResource(R.string.rate_sw_d), fontSize = 11.sp, color = TextSecondary)
            }
            Switch(
                checked = !Cfg.noRate,
                onCheckedChange = { Cfg.noRate = !it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted, gate = 0.dp)
                .tap { Rate.askManual() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rate_star_row), fontSize = 13.sp, color = TextPrimary)
                Text(stringResource(R.string.rate_star_row_d), fontSize = 11.sp, color = TextSecondary)
            }
            Text("›", fontSize = 18.sp, color = TextSecondary)
        }
    }
    }

    item {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_bk), stringResource(R.string.se_bk_d))
    CardBox {
        var bkTip by remember { mutableStateOf("") }
        var bkBusy by remember { mutableStateOf(false) }
        //导出：系统选择器给个默认文件名，打出来的是加密后的 .baicodex
        val bkSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                bkBusy = true
                bkTip = ctx.getString(R.string.se_bk_doing)
                scope.launch {
                    //把导出过程里的每一行日志收起来：失败时把最后一条原因（头不对 / 密钥不对）直接给用户看
                    val logs = java.util.Collections.synchronizedList(ArrayList<String>())
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                                Backup.export(ctx, out, Cfg.bkSecrets) { logs.add(it) }
                            } ?: false
                        }.getOrDefault(false)
                    }
                    bkBusy = false
                    bkTip = if (ok) ctx.getString(R.string.se_bk_ok)
                    else logs.lastOrNull()?.let { ctx.getString(R.string.se_bk_fail_x, it) }
                        ?: ctx.getString(R.string.se_bk_fail)
                }
            }
        }
        //导入：先解到私有 restore 目录，重启后由 App.onCreate 换上去
        val bkOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                bkBusy = true
                bkTip = ctx.getString(R.string.se_bk_doing)
                scope.launch {
                    //同上：解不开 / 不是 zip 这类原因直接抛到界面上，不让用户只看到一句「失败」
                    val logs = java.util.Collections.synchronizedList(ArrayList<String>())
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                                Backup.stageImport(ctx, ins) { logs.add(it) }
                            } ?: false
                        }.getOrDefault(false)
                    }
                    bkBusy = false
                    bkTip = if (ok) ctx.getString(R.string.se_bk_imported)
                    else logs.lastOrNull()?.let { ctx.getString(R.string.se_bk_bad_x, it) }
                        ?: ctx.getString(R.string.se_bk_bad)
                }
            }
        }
        Text(stringResource(R.string.se_bk_note), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        //加密配置开关：默认开，导出时把凭据密文一起打进包里，换机能直接恢复
        CapSwitch(
            stringResource(R.string.se_bk_secrets),
            stringResource(R.string.se_bk_secrets_d),
            Cfg.bkSecrets
        ) { Cfg.bkSecrets = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(stringResource(R.string.se_bk_export), modifier = Modifier.height(38.dp)) {
                if (!bkBusy) bkSave.launch("Baicodex-backup-${System.currentTimeMillis()}.baicodex")
            }
            GhostButton(stringResource(R.string.se_bk_import), modifier = Modifier.height(38.dp)) {
                //老备份是 zip、新备份是 .baicodex，系统都不认识这两个后缀的 mime，放开成任意文件
                if (!bkBusy) bkOpen.launch(arrayOf("*/*"))
            }
        }
        if (bkTip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(bkTip, fontSize = 11.sp, color = TextSecondary)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    //隐私与安全：单独一个分块放在关于页最后，不占分类位，也不挨着评价与调研
    SectionTitle(stringResource(R.string.se_sec_tab), stringResource(R.string.se_sec_sub))
    GuardSection()
    }

    item {
    Spacer(Modifier.height(14.dp))
    //新增保护：注入与泄漏相关的两个开关，同样不占分类位，排在隐私与安全后面
    SectionTitle(stringResource(R.string.se_pt_tab), stringResource(R.string.se_pt_sub))
    ProtectSection()
    }

    //Rust 内核：并行接入阶段的可见入口，只跑自检，不参与任何业务读写（删掉这块即可回滚）
    //位置：关于页最底部（用户要求「Rust 自检放最下面」），排在开发者工具之前
    item {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_kernel_t))
    CardBox {
        Text(stringResource(R.string.se_kernel_d), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            //版本 + 状态：没带内核的 ABI（v7a）如实说明，不假装成功
            Text("v" + BxKernel.version().ifBlank { "—" }, fontSize = 12.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            val okNow = out.contains("\"ok\":true")
            val (label, tint) = when {
                !BxKernel.available -> stringResource(R.string.se_kernel_na) to TextSecondary
                okNow -> stringResource(R.string.se_kernel_ok) to OkGreen
                out.isBlank() -> stringResource(R.string.se_kernel_run) to TextSecondary
                else -> stringResource(R.string.se_kernel_fail) to DangerRed
            }
            Text(label, fontSize = 11.sp, color = tint)
        }
        Spacer(Modifier.height(10.dp))
        GhostButton(
            if (busy) stringResource(R.string.se_kernel_busy) else stringResource(R.string.se_kernel_run),
            modifier = Modifier.height(38.dp)
        ) {
            if (busy) return@GhostButton
            busy = true
            scope.launch {
                //自检会真写盘（临时桶写完即删），放到 IO 线程跑
                val r = withContext(Dispatchers.IO) { BxKernel.selfcheck() }
                busy = false
                out = r
                err = if (r.isBlank()) BxKernel.lastError() else ""
            }
        }
        if (out.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(out, fontSize = 9.sp, color = TextSecondary)
        }
        if (err.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(err, fontSize = 9.sp, color = DangerRed)
        }
    }
    }

    //开发者工具：只有连点版本号解锁过才出现，摆在关于页最下面，不干扰普通用户
    //（这里是 LazyListScope，不是 Composable 作用域，展开动画交给 item 内部做）
    if (Cfg.devShow) {
        item {
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.tx_c2e48855))
            DevSection()
        }
    }
} //关于分类

/** 数据保障开关：开启走强制警告 +15s 冷静期，开启后只能强制清空，没有回退 */
@Composable
private fun GuardSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Guard.on) }
    //0 空闲 1 开启警告 2 关闭警告 3 执行中 4 已清空
    var mode by remember { mutableStateOf(0) }
    var tip by remember { mutableStateOf("") }
    //开启进度：1 生成密钥 2 迁移旧数据 3 加密落盘 4 完成
    var step by remember { mutableIntStateOf(1) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_gd_title), fontSize = 14.sp, color = TextPrimary)
                Text(
                    guardStateText(),
                    fontSize = 11.sp,
                    color = if (on && !Guard.ready) DangerRed else TextSecondary
                )
            }
            Switch(
                checked = on,
                //开关是受控的：点「关」只弹警告，真清空成功才把状态改掉
                onCheckedChange = { want -> if (want != on) mode = if (want) 1 else 2 },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.se_gd_note),
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.se_gd_covers),
            fontSize = 10.sp,
            color = TextSecondary
        )
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 11.sp, color = TextSecondary)
        }
    }
    HostInOverlay(mode != 0) {
        when (mode) {
            1 -> GuardWarnDialog(
                cost = if (Guard.lastCostMs > 0) String.format(stringResource(R.string.tx_1da7adbb), Guard.lastCostMs / 1000f) else stringResource(R.string.tx_09e4e337),
                device = Guard.deviceName(),
                onCancel = { mode = 0 },
                onConfirm = {
                    step = 1
                    mode = 3
                    scope.launch {
                        val done = withContext(Dispatchers.IO) {
                            runCatching { Guard.enable { step = it } }.getOrDefault(false)
                        }
                        on = Guard.on
                        mode = 0
                        if (done) {
                            tip = ctx.getString(R.string.se_gd_tip_on)
                        } else {
                            Toast.makeText(ctx, R.string.se_gd_fail, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
            2 -> GuardOffDialog(
                onCancel = { mode = 0 },
                onConfirm = {
                    mode = 5
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { Guard.disable() } }
                        on = false
                        mode = 4
                    }
                }
            )
            3 -> GuardProgressDialog(step)
            5 -> GuardBusyDialog(stringResource(R.string.se_gd_busy))
            4 -> SimpleDialog(
                stringResource(R.string.se_gd_cleared_title),
                stringResource(R.string.se_gd_cleared_body),
                onClose = { android.os.Process.killProcess(android.os.Process.myPid()) }
            )
        }
    }
} //数据保障开关

/** 新增保护：数据输出脱敏与禁止截图录屏，两个开关都不碰数据，随时可关 */
@Composable
private fun ProtectSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mask by remember { mutableStateOf(Cfg.maskOut) }
    var shot by remember { mutableStateOf(Cfg.noShot) }
    //脱敏开启前的强制警告
    var warn by remember { mutableStateOf(false) }
    //开启后的结果提示：服务端规则到底拉到没拉到
    var tip by remember { mutableStateOf("") }
    //当前规则规模：高 8 位键名数，低 8 位前缀数
    var shape by remember { mutableIntStateOf(Mask.shape()) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_pt_mask_t), fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (mask) stringResource(R.string.se_pt_mask_on) else stringResource(R.string.se_pt_mask_off),
                    fontSize = 11.sp,
                    color = if (mask) AccentBlue else TextSecondary
                )
            }
            Switch(
                checked = mask,
                //开要走 15 秒警告，关直接生效
                onCheckedChange = { want ->
                    if (want != mask) {
                        if (want) {
                            warn = true
                        } else {
                            Cfg.maskOut = false
                            mask = false
                            tip = ""
                        }
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_pt_mask_note), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.se_pt_mask_stream), fontSize = 10.sp, color = TextSecondary)
        if (mask) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.se_pt_mask_rules, (shape shr 8) and 0xFF, shape and 0xFF),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 11.sp, color = TextSecondary)
        }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_pt_shot_t), fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (shot) stringResource(R.string.se_pt_shot_on) else stringResource(R.string.se_pt_shot_off),
                    fontSize = 11.sp,
                    color = if (shot) AccentBlue else TextSecondary
                )
            }
            Switch(
                checked = shot,
                //先落盘再改窗口标记：进程被杀重启后也能按新状态恢复
                onCheckedChange = { want ->
                    Cfg.noShot = want
                    shot = want
                    (ctx as? MainActivity)?.applySecure(want)
                    //悬浮聊天窗、桌宠、气泡是独立窗口，Activity 的标记管不到它们
                    SecureWin.apply()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_pt_shot_note), fontSize = 11.sp, color = TextSecondary)
    }
    HostInOverlay(warn) {
        if (warn) {
            MaskWarnDialog(
                onCancel = { warn = false },
                onConfirm = {
                    warn = false
                    //setter 里会顺手关掉流式输出：逐字显示没法事后过滤
                    Cfg.maskOut = true
                    mask = true
                    tip = ""
                    scope.launch {
                        val done = withContext(Dispatchers.IO) {
                            runCatching { MaskRules.sync(force = true) }.getOrDefault(false)
                        }
                        shape = Mask.shape()
                        tip = ctx.getString(if (done) R.string.se_pt_mask_ok else R.string.se_pt_mask_fail)
                    }
                }
            )
        }
    }
} //新增保护

/** 一行链接：右侧显示地址，不可点时整行变灰 */
@Composable
private fun LinkRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(enabled = enabled) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 11.sp,
            color = if (enabled) AccentBlue else TextSecondary,
            maxLines = 1
        )
    }
} //链接一行

/** 用系统浏览器打开链接 */
internal fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
} //打开链接

@Composable
private fun McpSection(onEdit: (McpServer) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var tipOk by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.mcp_title), stringResource(R.string.mcp_sub))
    CardBox {
        val list = Mcp.list
        if (list.isEmpty()) {
            Text(stringResource(R.string.mcp_none), fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.mcp_none_sub), fontSize = 11.sp, color = TextSecondary)
        } else {
            list.forEach { s ->
                McpRow(
                    server = s,
                    count = Mcp.tools.count { it.serverId == s.id },
                    busy = busy == s.id,
                    onToggle = { Mcp.toggle(s.id, it) },
                    onRefresh = {
                        busy = s.id
                        tip = ""
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Mcp.refresh(s) }
                            val err = Mcp.lastError
                            busy = ""
                            tipOk = err.isEmpty() && n > 0
                            tip = if (tipOk) ctx.getString(R.string.mcp_ok, s.name, n)
                            else ctx.getString(
                                R.string.mcp_fail,
                                s.name,
                                err.ifBlank { ctx.getString(R.string.mcp_no_tool) }
                            )
                        }
                    },
                    onEdit = { onEdit(s) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.mcp_add), modifier = Modifier.height(38.dp)) {
                onEdit(McpServer(id = "", name = "", url = ""))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.mcp_count_all, Mcp.tools.size),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 10.sp, color = if (tipOk) OkGreen else DangerRed)
        }
    }
} //MCP 服务分区：列表 + 开关 + 刷新能力 + 新增

@Composable
private fun McpRow(
    server: McpServer,
    count: Int,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(server.name.ifBlank { server.url }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                stringResource(
                    R.string.mcp_row_sub,
                    if (server.kind == McpServer.KIND_REST) stringResource(R.string.mcp_kind_rest)
                    else stringResource(R.string.mcp_kind_mcp),
                    server.url,
                    count
                ),
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        PolicyChip(
            if (busy) stringResource(R.string.mcp_testing) else stringResource(R.string.mcp_refresh),
            busy
        ) { if (!busy) onRefresh() }
        Spacer(Modifier.width(6.dp))
        PolicyChip(stringResource(R.string.mcp_edit), false) { onEdit() }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = server.on,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
    }
} //MCP 服务一行

@Composable
private fun McpDialog(
    server: McpServer,
    onSave: (McpServer) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(server.id) { mutableStateOf(server.name) }
    var url by remember(server.id) { mutableStateOf(server.url) }
    var token by remember(server.id) { mutableStateOf(server.token) }
    var kind by remember(server.id) { mutableStateOf(server.kind) }
    var tip by remember { mutableStateOf("") }
    var tipOk by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    ScrimCard(onDismiss = onClose) {
        Text(
            if (server.id.isBlank()) stringResource(R.string.mcp_new_title) else stringResource(R.string.mcp_edit_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Column(Modifier.weight(1f, fill = false).heightIn(max = 340.dp).verticalScroll(glassScrollState())) {
            Field(stringResource(R.string.mcp_name), name, { name = it }, placeholder = stringResource(R.string.mcp_name_ph))
            Field(stringResource(R.string.mcp_url), url, { url = it }, placeholder = stringResource(R.string.mcp_url_ph))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.mcp_kind), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PolicyChip(stringResource(R.string.mcp_kind_mcp), kind == McpServer.KIND_MCP) {
                    kind = McpServer.KIND_MCP
                }
                Spacer(Modifier.width(6.dp))
                PolicyChip(stringResource(R.string.mcp_kind_rest), kind == McpServer.KIND_REST) {
                    kind = McpServer.KIND_REST
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.mcp_kind_hint), fontSize = 10.sp, color = TextSecondary)
            Field(
                stringResource(R.string.mcp_token),
                token,
                { token = it },
                placeholder = stringResource(R.string.mcp_token_ph),
                secret = true
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                if (testing) stringResource(R.string.mcp_testing) else stringResource(R.string.mcp_test),
                modifier = Modifier.height(38.dp)
            ) {
                if (testing) return@GhostButton
                if (url.isBlank()) {
                    tipOk = false
                    tip = ctx.getString(R.string.mcp_need_url)
                    return@GhostButton
                }
                testing = true
                tip = ""
                scope.launch {
                    //测试只拉清单不写缓存，避免拿临时 id 污染能力表
                    val tmp = McpServer(server.id.ifBlank { "tmp" }, name, url.trim(), token.trim(), kind, true)
                    val n = withContext(Dispatchers.IO) { Mcp.listTools(tmp).size }
                    val err = Mcp.lastError
                    testing = false
                    tipOk = err.isEmpty() && n > 0
                    tip = if (tipOk) ctx.getString(R.string.mcp_test_ok, n)
                    else ctx.getString(R.string.mcp_test_fail, err.ifBlank { ctx.getString(R.string.mcp_no_tool) })
                }
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(tip, fontSize = 10.sp, color = if (tipOk) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (server.id.isNotBlank()) {
                DialogButton(stringResource(R.string.se_143), DangerRed, DangerRedSoft) { onDelete() }
            }
            DialogButton(stringResource(R.string.se_142), TextSecondary, SurfaceMuted) { onClose() }
            DialogButton(stringResource(R.string.se_264), androidx.compose.ui.graphics.Color.White, AccentBlue) {
                if (url.isBlank()) {
                    tipOk = false
                    tip = ctx.getString(R.string.mcp_need_url)
                } else {
                    onSave(
                        server.copy(
                            name = name.trim().ifBlank { url.trim() },
                            url = url.trim(),
                            token = token.trim(),
                            kind = kind
                        )
                    )
                }
            }
        }
    }
} //MCP 服务编辑弹窗

/** 弹窗底部等宽文字按钮 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DialogButton(
    text: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Text(
        text,
        fontSize = 12.sp,
        color = fg,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .background(bg, RoundedCornerShape(10.dp))
            .tap { onClick() }
            .padding(vertical = 11.dp)
    )
} //弹窗按钮

@Composable
private fun SkillRow(s: Skill, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val nm = Skills.displayName(ctx, s).ifBlank { ctx.getString(R.string.sk_unnamed) }
            Text(nm, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                if (s.desc.isBlank()) stringResource(R.string.se2_skill_len, s.body.length)
                else Skills.displayDesc(ctx, s),
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Switch(
            checked = s.on,
            onCheckedChange = { Skills.toggle(s.id, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.se_256),
            fontSize = 11.sp,
            color = AccentBlue,
            modifier = Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                .tap { onEdit() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
} //技能一行

@Composable
private fun SkillDialog(
    skill: Skill,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var name by remember(skill.id) { mutableStateOf(skill.name) }
    var desc by remember(skill.id) { mutableStateOf(skill.desc) }
    var body by remember(skill.id) { mutableStateOf(skill.body) }
    ScrimCard {
        Text(
            if (skill.id.isBlank()) stringResource(R.string.se_233) else stringResource(R.string.se_257),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f, fill = false).heightIn(max = 340.dp).verticalScroll(glassScrollState())) {
            Field(stringResource(R.string.se_258), name, { name = it }, placeholder = stringResource(R.string.se_259))
            Field(stringResource(R.string.se_260), desc, { desc = it }, placeholder = stringResource(R.string.se_261))
            Field(
                stringResource(R.string.se_262),
                body,
                { body = it },
                placeholder = stringResource(R.string.se_263),
                singleLine = false,
                minLines = 6
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (skill.id.isNotBlank()) {
                Text(
                    stringResource(R.string.se_143),
                    fontSize = 12.sp,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(DangerRedSoft, RoundedCornerShape(10.dp))
                        .tap { onDelete() }
                        .padding(vertical = 11.dp)
                )
            }
            Text(
                stringResource(R.string.se_142),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, gate = 0.dp)
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.se_264),
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue, gate = 0.dp)
                    .tap { onSave(name, desc, body) }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //技能编辑弹窗

@Composable
private fun PersonaRow(p: Persona, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .liquidBg(RoundedCornerShape(12.dp), tint = if (selected) AccentBlueSoft else SurfaceMuted, gate = 0.dp)
            .tap { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //角色头像：有图就画圆图，没有就默认人形
        Box(Modifier.padding(end = 8.dp)) { AvatarView(p.avatar, 28.dp) }
        Column(Modifier.weight(1f)) {
            Text(Personas.displayName(p).ifBlank { stringResource(R.string.se_265) }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                Personas.brief(p) + if (p.profileId.isNotBlank()) {
                    stringResource(
                        R.string.se2_bind_fmt,
                        Profiles.list.firstOrNull { it.id == p.profileId }?.label ?: stringResource(R.string.se_267)
                    )
                } else "",
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        if (selected) {
            Text(stringResource(R.string.se_268), fontSize = 10.sp, color = AccentBlue)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            stringResource(R.string.se_256),
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier
                .liquidBg(RoundedCornerShape(8.dp), gate = 0.dp)
                .tap { onEdit() }
                .padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
} //角色一行

@Composable
private fun PersonaDialog(
    persona: Persona,
    onSave: (Persona) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var name by remember(persona.id) { mutableStateOf(persona.name) }
    var avatar by remember(persona.id) { mutableStateOf(persona.avatar) }
    var desc by remember(persona.id) { mutableStateOf(persona.desc) }
    var prompt by remember(persona.id) { mutableStateOf(persona.prompt) }
    var scenario by remember(persona.id) { mutableStateOf(persona.scenario) }
    var firstMsg by remember(persona.id) { mutableStateOf(persona.firstMsg) }
    var examples by remember(persona.id) { mutableStateOf(persona.examples) }
    var postHistory by remember(persona.id) { mutableStateOf(persona.postHistory) }
    var profileId by remember(persona.id) { mutableStateOf(persona.profileId) }
    ScrimCard {
        Text(
            if (persona.id.isBlank()) stringResource(R.string.se2_persona_add) else stringResource(R.string.se_269),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.se_270),
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f, fill = false).heightIn(max = 400.dp).verticalScroll(glassScrollState())) {
            //头像走图片：选一张落私有目录，聊天里 AI 头像选「跟角色卡」时就用它
            AvatarPick(
                stringResource(R.string.se_271),
                if (avatar.isBlank()) stringResource(R.string.se_272) else stringResource(R.string.se_273),
                avatar
            ) { avatar = it }
            Field(stringResource(R.string.se_274), name, { name = it }, placeholder = stringResource(R.string.se_275))
            Field(stringResource(R.string.se_276), desc, { desc = it }, placeholder = stringResource(R.string.se_277))
            Field(
                stringResource(R.string.se_278),
                prompt,
                { prompt = it },
                placeholder = stringResource(R.string.se_279),
                singleLine = false,
                minLines = 6
            )
            Field(
                stringResource(R.string.se_280),
                scenario,
                { scenario = it },
                placeholder = stringResource(R.string.se_281),
                singleLine = false,
                minLines = 2
            )
            Field(
                stringResource(R.string.se_282),
                firstMsg,
                { firstMsg = it },
                placeholder = stringResource(R.string.se_283),
                singleLine = false,
                minLines = 2
            )
            Field(
                stringResource(R.string.se_284),
                examples,
                { examples = it },
                placeholder = stringResource(R.string.se_285),
                singleLine = false,
                minLines = 4
            )
            Field(
                stringResource(R.string.se_286),
                postHistory,
                { postHistory = it },
                placeholder = stringResource(R.string.se_287),
                singleLine = false,
                minLines = 3
            )
            //绑模型：选中这张卡时把整套模型配置一起切过去，不选就跟当前一致
            val boundName = Profiles.list.firstOrNull { it.id == profileId }?.label.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.se_288) + if (profileId.isBlank()) stringResource(R.string.se_289) else boundName,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.width(8.dp))
                if (profileId.isNotBlank()) {
                    Text(
                        stringResource(R.string.se_290),
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                            .tap { profileId = "" }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Profiles.list.forEach { pf ->
                ChoiceRow(
                    pf.label.ifBlank { pf.model },
                    pf.model,
                    profileId == pf.id
                ) { profileId = if (profileId == pf.id) "" else pf.id }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (persona.id.isNotBlank()) {
                Text(
                    stringResource(R.string.se_143),
                    fontSize = 12.sp,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(DangerRedSoft, RoundedCornerShape(10.dp))
                        .tap { onDelete() }
                        .padding(vertical = 11.dp)
                )
            }
            Text(
                stringResource(R.string.se_142),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, gate = 0.dp)
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.se_264),
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue, gate = 0.dp)
                    .tap {
                        onSave(
                            persona.copy(
                                name = name,
                                avatar = avatar,
                                desc = desc,
                                prompt = prompt,
                                scenario = scenario,
                                firstMsg = firstMsg,
                                examples = examples,
                                postHistory = postHistory,
                                profileId = profileId
                            )
                        )
                    }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //角色编辑弹窗

@Composable
internal fun PolicyChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (selected) AccentBlue else TextSecondary,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier
            .liquidBg(RoundedCornerShape(8.dp)
            , tint = if (selected) AccentBlueSoft else SurfaceMuted, gate = 0.dp)
            .tap { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
} //可选项胶囊：单行不换行，宽度由调用方的 weight 决定

@Composable
internal fun CapSwitch(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
    }
} //能力勾选一行：标题 + 说明 + 开关

/**通知注入条数：0 或留空 = 不限；输入框贴右侧并做扁，高度跟滑块一致 */
@Composable
private fun NotiLimitRow() {
    var text by remember { mutableStateOf(if (Cfg.notiLimit <= 0) "" else Cfg.notiLimit.toString()) }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.se_324), fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Slider(
            value = Cfg.notiLimit.coerceIn(0, 100).toFloat(),
            onValueChange = {
                val v = ((it / 5).toInt() * 5).coerceIn(0, 100)
                Cfg.notiLimit = v
                text = if (v == 0) "" else v.toString()
            },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(52.dp)
                .height(32.dp)
                .liquidBg(RoundedCornerShape(9.dp), gate = 0.dp)
                .border(1.dp, BorderGray, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { s ->
                    val d = s.filter { it.isDigit() }.take(4)
                    text = d
                    Cfg.notiLimit = d.toIntOrNull() ?: 0
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(AccentBlue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.se_325), fontSize = 11.sp, color = TextSecondary)
                        }
                        inner()
                    }
                }
            )
        }
    }
} //通知注入条数

@Composable
private fun BgImagePick() {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val p = Wallpaper.import(ctx, uri) ?: return@rememberLauncherForActivityResult
        Cfg.bgImg = p
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.bg_img_t), fontSize = 13.sp, color = TextPrimary)
            Text(stringResource(R.string.bg_img_d), fontSize = 10.sp, color = TextSecondary)
        }
        Text(
            stringResource(R.string.cm_pick_image),
            fontSize = 11.sp,
            color = AccentBlue,
            modifier = Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                .tap { pick.launch("image/*") }
                .padding(horizontal = 9.dp, vertical = 5.dp)
        )
        //已经选过图才显示清除，没图时不占位置
        if (Cfg.bgImg.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.bg_clear),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted, gate = 0.dp)
                    .tap { Cfg.bgImg = "" }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
    if (Cfg.bgImg.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.bg_blur, Cfg.bgBlur), fontSize = 11.sp, color = TextPrimary)
        androidx.compose.material3.Slider(
            value = Cfg.bgBlur.toFloat(),
            onValueChange = { Cfg.bgBlur = it.toInt() },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
} //背景图设置

/** 桌宠入口：独立一行，开关在右，配置图标贴在开关左边 */
@Composable
private fun PetEntryRow(onOpenPet: () -> Unit) {
    val ctx = LocalContext.current
    //不自己画底：统一由外层 CardBox 提供圆角 / 描边 / 玻璃 / 内边距，避免跟设置页其它卡片不一致
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pet_sec), fontSize = 14.sp, color = TextPrimary)
            Text(
                if (!Cfg.petOn) stringResource(R.string.pet_route_none)
                else if (Cfg.petGlobal) stringResource(R.string.pet_global)
                else stringResource(R.string.pet_in_app),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Box(
            Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                .tap { onOpenPet() }
                .padding(7.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_pet_cfg),
                contentDescription = stringResource(R.string.pet_pg_t),
                tint = AccentBlue,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = Cfg.petOn,
            onCheckedChange = { on ->
                Cfg.petOn = on
                if (on) {
                    //全局展示才需要悬浮窗：无障碍在跑就不需要额外权限
                    if (Cfg.petGlobal && b.ai.device.pet.Pet.canShow(ctx)) b.ai.device.pet.PetSvc.start(ctx)
                } else {
                    b.ai.device.pet.PetSvc.stop(ctx)
                    b.ai.device.pet.Pet.hide()
                }
            }
        )
    }
} //桌宠入口

/** 跟随项统一展开容器：高度 + 透明度一起动画，滑下来不生硬，收起也不会瞬间消失 */
@Composable
private fun FollowBlock(visible: Boolean, content: @Composable () -> Unit) {
    //高度只由一个 animateContentSize 驱动，内容只负责淡入淡出。
    //之前 AnimatedVisibility 的 expandVertically 本身就是高度动画，跟内层一叠加，两次测量打架，
    //展开途中文案会被后一项盖住（用户反馈「文字都压一起」）。clipToBounds 再兜一层，不让内容溢出。
    Column(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .animateContentSize(animationSpec = tween(240, easing = FastOutSlowInEasing))
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(170, delayMillis = 60, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(100, easing = LinearOutSlowInEasing))
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
} //跟随项展开

/**
 * miuix 风格滑杆：标题 + 右侧当前值 + 26dp 粗轨道 + **完整包裹轨道的白色圆形滑块**。
 *
 * 几何口径（三条必须一致，否则视觉上歪）：
 * 1. 轨道高 26dp，滑块直径 32dp —— 滑块比轨道大 6dp，上下各包出 3dp，轨道从滑块中间穿过；
 * 2. 滑块中心 x = 半滑块宽 + frac × (总宽 − 滑块宽)：两端各留半个滑块，首尾一定能取到极值；
 * 3. 已填充段**画到滑块中心**为止，不是画到 width×frac —— 后者比分界点少半个滑块，看起来像脱节。
 * 4. 手指 x → 值：先用 (x − 半滑块宽)/(总宽 − 滑块宽) 求比例，再按 step 吸附。
 * 触控高度 44dp（Material 最小可点区），Canvas 与滑块都在其中垂直居中。
 */
@Composable
private fun GlassSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(valueText, fontSize = 11.sp, color = TextSecondary)
    }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    //颜色：轨道始终是淡灰（浓度可调），激活段与滑块描边按「是否继承主题」二选一
    val trackColor = TextSecondary.copy(alpha = Cfg.sliderTrack / 100f)
    val activeColor = if (Cfg.sliderAccent) AccentBlue else SurfaceWhite.copy(alpha = 0.92f)
    val edgeColor = if (Cfg.sliderAccent) AccentBlue else BorderGray
    var width by remember { mutableIntStateOf(0) }
    //轨道高度可配：圆点直径 = 轨道 + 6dp，行高跟着撑开，避免圆点被裁
    val track = Cfg.sliderTrackH.dp
    val thumb = (Cfg.sliderTrackH + 6).dp
    val rowH = (Cfg.sliderTrackH + 18).coerceAtLeast(44).dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    val half = thumbPx / 2f
    //手指 x → 值：Rust 侧 bx_slider_value 同一套公式（见 core/feature/compute/slider.rs），此处为绘制期直算
    fun pick(x: Float) {
        val w = width.toFloat()
        if (w <= 0f) return
        val usable = (w - thumbPx).coerceAtLeast(1f)
        var v = range.start + ((x - half) / usable).coerceIn(0f, 1f) * span
        if (step > 0f) v = range.start + (Math.round((v - range.start) / step) * step)
        onChange(v.coerceIn(range.start, range.endInclusive))
    } //坐标换值
    //滑块中心 x（像素）与轨道可绘制宽度：Canvas 与 thumb 共用，保证分界点严丝合缝
    val usable = (width - thumbPx).coerceAtLeast(0f)
    val centerX = half + frac * usable
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .onSizeChanged { width = it.width }
            .pointerInput(range, step, width) { detectTapGestures { pick(it.x) } }
            .pointerInput(range, step, width) {
                detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { ch, _ -> pick(ch.position.x) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        //轨道 + 已填充段：填充段延伸到滑块中心，圆角与轨道同高的一半
        Canvas(Modifier.fillMaxWidth().height(track)) {
            val h = size.height
            val r = CornerRadius(h / 2f)
            drawRoundRect(trackColor, size = Size(size.width, h), cornerRadius = r)
            //填充宽度 = 滑块中心到左端；小于一个圆角直径时按直径画，避免圆角被压扁
            if (frac > 0f) {
                val fillW = centerX.coerceAtLeast(h)
                drawRoundRect(activeColor, size = Size(fillW, h), cornerRadius = r)
            }
        }
        //滑块：白色实心圆 + 细描边，直径 32dp 完整包住 26dp 轨道
        Box(
            Modifier
                .offset { IntOffset((centerX - half).roundToInt(), 0) }
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .background(SurfaceWhite, CircleShape)
                .border(1.dp, edgeColor, CircleShape)
        )
    }
} //miuix 风格滑杆
/**
 * Markdown 排版独立成页：AI 回复正文的字号 / 行距 / 段间距 / 列表缩进 / 左右留白 / 代码字号，
 * 改完立刻生效，卡片下方用聊天同一套 MdText 做实时预览。
 */
private fun LazyListScope.mdSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(
            stringResource(R.string.tx_ae6b6416),
            stringResource(R.string.tx_8539beb4)
        )
        CardBox {
            GlassSlider(stringResource(R.string.tx_5a3701e9), "${Cfg.mdFont} sp", Cfg.mdFont.toFloat(), 11f..18f, step = 1f) {
                Cfg.mdFont = it.toInt()
            }
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_6ca4489d), "${Cfg.mdLine}%", Cfg.mdLine.toFloat(), 100f..220f, step = 5f) {
                Cfg.mdLine = it.toInt()
            }
            Text(stringResource(R.string.tx_fa9bac8d), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_13853b5a), "${Cfg.mdGap} dp", Cfg.mdGap.toFloat(), 0f..20f, step = 2f) {
                Cfg.mdGap = it.toInt()
            }
            Text(stringResource(R.string.tx_9fcf1606), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_cd758e7e), stringResource(R.string.tx_b4d10c2d, Cfg.mdIndent), Cfg.mdIndent.toFloat(), 0f..32f, step = 2f) {
                Cfg.mdIndent = it.toInt()
            }
            Text(stringResource(R.string.tx_8851751d), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_b76fe1f2), "${Cfg.mdPad} dp", Cfg.mdPad.toFloat(), 0f..24f, step = 2f) {
                Cfg.mdPad = it.toInt()
            }
            Text(stringResource(R.string.tx_75af6edf), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_279a3789), "${Cfg.mdCode} sp", Cfg.mdCode.toFloat(), 9f..16f, step = 1f) {
                Cfg.mdCode = it.toInt()
            }
            Text(stringResource(R.string.tx_fe8fe0cc), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tx_645dbc55), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            //预览直接用聊天同一套 MdText：改哪个参数，这里立刻能看出来
            MdText(MD_PREVIEW)
        }
    }
} //Markdown 排版子页