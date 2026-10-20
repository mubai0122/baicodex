package b.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Agent
import b.ai.agent.Tools
import b.ai.core.Avatar
import b.ai.core.Cfg
import b.ai.core.Env
import b.ai.core.Speak
import b.ai.core.Perms
import b.ai.core.Persona
import b.ai.core.Personas
import b.ai.core.Profiles
import b.ai.core.Schedules
import b.ai.core.Search
import b.ai.core.Skill
import b.ai.core.Skills
import b.ai.core.WorkMode
import b.ai.exec.Alarms
import b.ai.exec.Linux
import b.ai.exec.Router
import b.ai.exec.Toolchain
import b.ai.exec.Workspace
import b.ai.net.Update
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
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

private val SettingsTabs = listOf(
    FloatTab("模型") { c -> Icon(Icons.Filled.Settings, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab("对话") { c -> Icon(Icons.Filled.Email, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab("外观") { c -> Icon(Icons.Filled.Create, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab("统计") { c -> Icon(Icons.Filled.List, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab("扩展") { c -> Icon(Icons.Filled.MoreVert, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab("关于") { c -> Icon(Icons.Filled.Info, null, tint = c, modifier = Modifier.size(18.dp)) }
) //设置分类：模型 / 对话 / 外观 / 统计 / 扩展 / 关于（权限已并入对话；索引按标签匹配，改顺序不用动索引）

/** 设置项索引：搜索只查这张表，命中后直接跳到对应 tab；tab 写标签而不是数字，避免换顺序时错位 */
private data class SettingEntry(val label: String, val tab: String, val hint: String = "")

private val SettingIndex = listOf(
    SettingEntry("模型配置", "模型", "一条一套提供商 + Key + 模型"),
    SettingEntry("当前配置", "模型", "整组切换"),
    SettingEntry("模型能力", "模型", "模型真支持的能力"),
    SettingEntry("深度思考", "模型", "先推理再回答"),
    SettingEntry("上下文压缩", "对话", "早期对话压成摘要"),
    SettingEntry("注入", "对话", "本机实时信息拼进系统提示"),
    SettingEntry("回复信息", "对话", "回复下方那行小字"),
    SettingEntry("会话", "对话", "切换与删除"),
    SettingEntry("工作区信息", "对话", "所有工作区与未被绑定的目录"),
    SettingEntry("权限工作方式", "对话", "自动选路"),
    SettingEntry("系统权限", "对话", "重新申请"),
    SettingEntry("实验性功能", "对话", ""),
    SettingEntry("主题", "外观", "深色模式"),
    SettingEntry("字体大小", "外观", ""),
    SettingEntry("输入框", "外观", "悬浮模式"),
    SettingEntry("输入提示", "外观", "占位文字"),
    SettingEntry("导航手势", "外观", ""),
    SettingEntry("用量统计", "统计", "总 token / 柱状图 / 方格图 / 模型分类 / 月活跃"),
    SettingEntry("模型分类", "统计", "各模型累计 token"),
    SettingEntry("本月方格图", "统计", "按天热力图"),
    SettingEntry("定时任务", "扩展", "到点通知与自动执行"),
    SettingEntry("搜索平台", "扩展", "web_search"),
    SettingEntry("智能体角色", "扩展", ""),
    SettingEntry("自定义系统提示", "扩展", ""),
    SettingEntry("技能专区", "扩展", ""),
    SettingEntry("工具权限", "扩展", ""),
    SettingEntry("编译工具链", "扩展", "JDK / gradle / Android SDK"),
    SettingEntry("Linux 环境", "扩展", "内置 Ubuntu"),
    SettingEntry("检查更新", "关于", "版本号与更新历史")
) //搜索索引

/** 按标签找 tab 下标，找不到就落回 0 */
private fun tabIndex(label: String): Int =
    SettingsTabs.indexOfFirst { it.label == label }.coerceAtLeast(0)

/** 设置：底部悬浮 tab 分类，各分类独立滚动；backProgress 是返回手势进度，用来做跟手跟退 */
@Composable
fun SettingsScreen(backProgress: Float = 0f, initialTab: Int = 0, onBack: () -> Unit) {
    //统计要读 SQLite，放 IO 线程，避免进设置页时卡主线程
    var stats by remember { mutableStateOf(0 to 0) }
    var tab by remember { mutableStateOf(initialTab) }
    var draft by remember { mutableStateOf<Skill?>(null) }
    var personaDraft by remember { mutableStateOf<Persona?>(null) }
    var taskDraft by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var fq by remember { mutableStateOf("") }
    LaunchedEffect(Agent.messages.size, Agent.sessionId) {
        stats = withContext(Dispatchers.IO) { Agent.stats() }
    }

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
            .background(BgGray)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(if (find) "搜索" else SettingsTabs[tab].label, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(if (find) AccentBlueSoft else SurfaceWhite, RoundedCornerShape(9.dp))
                        .tap { find = !find; fq = "" }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "搜索设置",
                        tint = if (find) AccentBlue else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .background(AccentBlueSoft, RoundedCornerShape(9.dp))
                        .tap { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("返回", fontSize = 11.sp, color = AccentBlue)
                }
            }

            if (find) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
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
                            Text("搜设置项，如 主题 / Linux / 工具链", fontSize = 12.sp, color = TextSecondary)
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

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)
            ) {
                when {
                    find && fq.isNotBlank() -> {
                        //搜索结果只认索引表：点一条就跳到它所属的 tab
                        val key = fq.trim()
                        val hits = SettingIndex.filter {
                            it.label.contains(key, true) || it.hint.contains(key, true)
                        }
                        if (hits.isEmpty()) {
                            Text(
                                "没有匹配的设置项：$key",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        } else {
                            hits.forEach { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                                        .tap { tab = tabIndex(e.tab); find = false; fq = "" }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(e.label, fontSize = 13.sp, color = TextPrimary)
                                        if (e.hint.isNotBlank()) {
                                            Text(e.hint, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                                        }
                                    }
                                    Text(
                                        "设置 → ${e.tab}",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    find -> Text(
                        "输入关键词搜索设置项，例如「主题」「Linux」「工具链」",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    else -> when (tab) {
                        0 -> ModelTab()
                        1 -> ChatTab(stats)
                        2 -> UiTab()
                        3 -> StatTab()
                        4 -> ExtTab(
                            onEdit = { draft = it },
                            onEditPersona = { personaDraft = it },
                            onNewTask = { taskDraft = true }
                        )
                        5 -> AboutTab()
                        else -> UiTab()
                    }
            }
        }
    }
        FloatTabBar(
            tabs = SettingsTabs,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        )
    }
    //技能/角色/定时任务弹窗共用一个挂载点，同一时刻只会有一个非空
    HostInOverlay(active = draft != null || personaDraft != null || taskDraft) {
        draft?.let { s ->
            SkillDialog(
                skill = s,
                onSave = { n, d, b ->
                    if (s.id.isBlank()) Skills.add(n, d, b) else Skills.update(s.id, n, d, b)
                    draft = null
                },
                onDelete = {
                    if (s.id.isNotBlank()) Skills.remove(s.id)
                    draft = null
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
        if (taskDraft) TaskDialog(onClose = { taskDraft = false })
    }
} //设置界面

@Composable
private fun ModelTab() {
    SectionTitle("模型配置", "一条一套提供商 + Key + 模型，点一下整组切换")
    ProfileList()

    Spacer(Modifier.height(14.dp))
    SectionTitle("当前配置")
    CardBox { ModelSetupPanel() }

    Spacer(Modifier.height(14.dp))
    SectionTitle("模型能力", "勾上模型真支持的能力；没勾的能力请求体里会自动去掉，不会把请求打成 400")
    CardBox {
        CapSwitch(
            "图片输入（视觉）",
            if (Cfg.modelVision) "发图时按多模态数组上传" else "发图时降级为一行文字说明，模型看不到图",
            Cfg.modelVision
        ) { Cfg.modelVision = it; Profiles.capture() }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            "工具调用（function calling）",
            if (Cfg.modelToolCall) "请求体带 tools，模型能读写文件 / 执行命令"
            else "请求体不带 tools，模型只能纯文本回答",
            Cfg.modelToolCall
        ) { Cfg.modelToolCall = it; Profiles.capture() }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            "推理 / 思考参数",
            if (Cfg.modelReasoning) "开思考时下发 reasoning_effort" else "不下发 reasoning_effort，避免不认该字段的接口报错",
            Cfg.modelReasoning
        ) { Cfg.modelReasoning = it; Profiles.capture() }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("深度思考", "开启后模型先推理再回答，思考过程会折叠显示在回复上方")
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("思考模式", fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (Cfg.think) "请求会下发 reasoning_effort" else "直接回答，不下发思考参数",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = Cfg.think,
                onCheckedChange = { Cfg.think = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("思考强度", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("low", "medium", "high").forEach { v ->
                PolicyChip(v, Cfg.thinkEffort == v) { Cfg.thinkEffort = v }
            }
        }
        Spacer(Modifier.height(10.dp))
        CapSwitch(
            "最大模式",
            if (Cfg.maxMode) "不下发 max_tokens，思考强度顶到 high，回答尽量写满" else "按配置的 max_tokens 与思考强度走",
            Cfg.maxMode
        ) { Cfg.maxMode = it }
        Spacer(Modifier.height(8.dp))
        Text("采样温度", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1f, 0f, 0.3f, 0.7f, 1f, 1.3f).forEach { v ->
                PolicyChip(
                    if (v < 0f) "默认" else v.toString(),
                    kotlin.math.abs(Cfg.temperature - v) < 0.01f
                ) { Cfg.temperature = v }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "温度越低越稳定、越高越发散；选「默认」表示不下发 temperature，跟随服务端。",
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Text("附加请求参数（JSON）", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Field(
            label = "参数内容",
            value = Cfg.extraParams,
            onValueChange = { Cfg.extraParams = it },
            placeholder = """{"enable_thinking":true}""",
            secret = false,
            singleLine = false,
            minLines = 2
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "有的中转站/厂商用私有字段开思考，这里直接拼到请求体顶层，" +
                "例如 {\"enable_thinking\":true} 或 {\"thinking\":{\"type\":\"enabled\"}}。留空不加。",
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //模型分类

@Composable
private fun ChatTab(stats: Pair<Int, Int>) {
    SectionTitle("上下文与压缩", "超出预算时把早期对话压成摘要，避免整段历史反复上送")
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("自动压缩", fontSize = 14.sp, color = TextPrimary)
                Text("当前会话 ${stats.first} 条 / ${stats.second} 字", fontSize = 11.sp, color = TextSecondary)
            }
            Switch(
                checked = Cfg.ctxAuto,
                onCheckedChange = { Cfg.ctxAuto = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("模型窗口（总上下文 token）", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(32_000, 64_000, 128_000, 256_000, 1_000_000).forEach { v ->
                PolicyChip(if (v >= 1_000_000) "1M" else "${v / 1000}k", Cfg.ctxTotal == v) {
                    Cfg.ctxTotal = v
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("压缩阈值 ${Cfg.ctxPct}%", fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("约 ${Cfg.ctxCharBudget / 1000}k 字", fontSize = 11.sp, color = TextSecondary)
        }
        //滑杆 10-99：到窗口的这个比例就开始压缩，默认 70%
        androidx.compose.material3.Slider(
            value = Cfg.ctxPct.toFloat(),
            onValueChange = { Cfg.ctxPct = it.toInt() },
            valueRange = 10f..99f,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "按总上下文窗口的百分比折算字符数触发压缩；调低压得更勤、更省 token，调高更保记忆。",
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text("压缩后保留最近条数", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(4, 8, 16).forEach { v ->
                PolicyChip("$v 条", Cfg.ctxKeep == v) { Cfg.ctxKeep = v }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("未压缩条数到多少触发（与字符预算先到先压）", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(8, 16, 32).forEach { v ->
                PolicyChip("$v 条", Cfg.ctxMsgCount == v) { Cfg.ctxMsgCount = v }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("立即总结", modifier = Modifier.height(38.dp)) { Agent.compactNow() }
            Spacer(Modifier.width(10.dp))
            Text(
                if (Agent.busy) "任务进行中，结束后再试" else "手动把早期消息压成一条摘要",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("注入", "把本机实时信息拼进系统提示，模型不用反问你")
    CardBox {
        val ctx = LocalContext.current
        //位置权限不在自动申请清单里：只有你真的打开这个开关才弹，免得缺权限卡住工具调用
        val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            Cfg.injectPlace = it
        }
        CapSwitch(
            "注入当前时间",
            if (Cfg.injectTime) "带上年月日、时分、星期" else "不注入时间",
            Cfg.injectTime
        ) { Cfg.injectTime = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            "注入电量",
            if (Cfg.injectBattery) "带上电量百分比与是否充电" else "不注入电量",
            Cfg.injectBattery
        ) { Cfg.injectBattery = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            "注入位置",
            when {
                !Cfg.injectPlace -> "不注入位置"
                Env.hasPlacePerm(ctx) -> "带上最后已知经纬度与精度"
                else -> "缺定位权限，打开开关会弹系统授权"
            },
            Cfg.injectPlace
        ) { on ->
            if (on && !Env.hasPlacePerm(ctx)) {
                locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                Cfg.injectPlace = on
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("回复信息", "每条回答下面那行小字显示什么")
    CardBox {
        CapSwitch(
            "用量",
            if (Cfg.showUsage) "总 token 与 tok/s" else "不显示用量",
            Cfg.showUsage
        ) { Cfg.showUsage = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            "请求信息",
            if (Cfg.showMeta) "时间 / 供应商 / 模型 / 首响 / 总耗时" else "只显示正文",
            Cfg.showMeta
        ) { Cfg.showMeta = it }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("语音", "说和听都用系统能力，不联网、不传云端")
    CardBox {
        CapSwitch(
            "朗读回复",
            if (Cfg.speakReplies) "每轮最终回答用系统 TTS 念出来，点停止同时断声" else "只显示文字，不念",
            Cfg.speakReplies
        ) { on ->
            Cfg.speakReplies = on
            //打开时试念一句，能立刻确认手机装了中文语音包；关掉时把正在念的掐掉
            if (on) Speak.say("语音朗读已开启", force = true) else Speak.stop()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "语音输入在输入框左侧的麦克风按钮：点一下开始，说完自动填进输入框。首次使用会申请麦克风权限。",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("会话", "切换与删除都在左侧抽屉；关掉 App 重进落在最新会话")
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Agent.sessionTitle, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                Text(
                    "${stats.first} 条记录 · ${stats.second} 字 · 共 ${Agent.sessions.size} 个会话",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            GhostButton("新建会话", modifier = Modifier.height(38.dp)) { Agent.newSession() }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("工作区信息", "每个会话一个独立目录；没被任何会话绑定的可以一键删掉")
    WsSection()

    Spacer(Modifier.height(14.dp))
    PermTab()
} //对话分类（含原「权限」分类：工作方式 / 系统权限 / 实验性功能）

/** 工作区信息：数据根下的哈希目录 + 会话绑定的目录，未被任何会话绑定的可以一键删除 */
@Composable
private fun WsSection() {
    var list by remember { mutableStateOf<List<Workspace.WsInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tip by remember { mutableStateOf("") }
    //点一次「删除」先变成「确认删除 / 取消」，避免手滑删掉目录
    var confirm by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        loading = true
        list = withContext(Dispatchers.IO) {
            runCatching { Workspace.listAll() }.getOrDefault(emptyList())
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    CardBox {
        when {
            loading -> Text("读取中…", fontSize = 11.sp, color = TextSecondary)
            list.isEmpty() -> Text(
                "还没有工作区：发过消息、或绑定过目录之后才会出现",
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
                        w.name + if (w.current) "（当前）" else "",
                        fontSize = 13.sp,
                        color = if (w.current) AccentBlue else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        "${w.files} 个文件 · ${Workspace.human(w.bytes)} · " +
                            if (w.unbound) "未被会话绑定" else "绑定 " +
                            w.sessions.take(2).joinToString("、") +
                            if (w.sessions.size > 2) " 等 ${w.sessions.size} 个会话" else "",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Text(w.path, fontSize = 9.sp, color = TextSecondary, maxLines = 1)
                }
                if (confirm == w.path) {
                    Text(
                        "确认删除",
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier
                            .background(DangerRedSoft, RoundedCornerShape(8.dp))
                            .tap {
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) { Workspace.delete(w.path) }
                                    tip = err ?: "已删除 ${w.name}"
                                    confirm = null
                                    reload()
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "取消",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .background(SurfaceWhite, RoundedCornerShape(8.dp))
                            .tap { confirm = null }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                } else if (w.unbound && !w.current && w.exists) {
                    Text(
                        "删除",
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("刷新", modifier = Modifier.height(38.dp)) { scope.launch { reload() } }
            Spacer(Modifier.width(8.dp))
            Text(
                "当前使用中的、还有会话绑定的目录不显示删除按钮，删不了",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //工作区信息

@Composable
private fun PermTab() {
    SectionTitle("权限工作方式", "切换后工具会自动按新路径选路")
    CardBox {
        WorkMode.entries.forEach { m ->
            ChoiceRow(m.label, m.desc, Cfg.workMode == m) { Cfg.workMode = m }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("系统权限", "缺权限时工具调用会被直接拒绝，可在这里重新申请或跳设置")
    CardBox {
        val ctx = LocalContext.current
        var missing by remember { mutableStateOf(Perms.missing(ctx)) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { missing = Perms.missing(ctx) }
        Text("运行期权限", fontSize = 14.sp, color = TextPrimary)
        Text(
            if (missing.isEmpty()) "已全部授予"
            else "缺：" + missing.joinToString("、") { Perms.label(it) },
            fontSize = 11.sp,
            color = if (missing.isEmpty()) OkGreen else DangerRed
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (missing.isEmpty()) "重新申请" else "申请权限", modifier = Modifier.height(38.dp)) {
                launcher.launch(Perms.runtime().toTypedArray())
            }
            GhostButton("系统设置页", modifier = Modifier.height(38.dp)) { Perms.openAppSettings(ctx) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "所有文件访问（Android 11+ 特殊权限）：" + if (Perms.allFiles()) "已开启" else "未开启",
            fontSize = 11.sp,
            color = if (Perms.allFiles()) OkGreen else TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        GhostButton("去开启全盘读写", modifier = Modifier.height(38.dp)) { Perms.openAllFiles(ctx) }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("实验性功能")
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("零宽路径", fontSize = 14.sp, color = TextPrimary)
                //探测在后台线程跑：每秒把结果刷一次，否则 UI 会一直停在「检测中…」
                var zone by remember { mutableStateOf(Router.dataZone()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        zone = Router.dataZone()
                        delay(1000)
                    }
                }
                Text(
                    "当前检测：$zone",
                    fontSize = 11.sp,
                    color = if (zone.startsWith("可直接读写") || zone.startsWith("零宽路径")) OkGreen else TextSecondary
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
            "开启后普通用户模式下会尝试用零宽变换路径读写 Android/data 与 Android/obb；" +
                "属于系统漏洞利用，ROM 更新后可能失效，失败会直接报权限错误。",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        GhostButton("重新探测", modifier = Modifier.height(38.dp)) { Router.reprobe() }
    }
} //权限分类（已并入对话分类，入口不再单独占一个 tab）

//字号档位：存的是倍率，1f = 跟随系统
private val FontPresets = listOf("小" to 0.9f, "标准" to 1f, "大" to 1.15f, "特大" to 1.3f)

@Composable
private fun UiTab() {
    SectionTitle("个性化", "聊天里显示头像；AI 头像可以跟角色卡，也可以单独指定一张")
    CardBox {
        ChoiceRow("在聊天里显示头像", "关掉就回到纯文字气泡，位置留给正文", Cfg.avatarOn) {
            Cfg.avatarOn = !Cfg.avatarOn
        }
        ChoiceRow("消息强制高圆角", "所有消息气泡用 16dp 大圆角，带一点方形，文字不顶弧线", Cfg.bubbleCapsule) {
            Cfg.bubbleCapsule = !Cfg.bubbleCapsule
        }
        ChoiceRow("Waifu模式", "启用后，AI 的回复将按标点分割逐句发送，营造更自然的对话体验", Cfg.waifuMode) {
            Cfg.waifuMode = !Cfg.waifuMode
        }
    }
    Spacer(Modifier.height(10.dp))
    AvatarPick("我的头像", "用户消息右侧显示；没设置就是默认人形", Cfg.avatarUser) { p ->
        Avatar.forget(Cfg.avatarUser)
        Cfg.avatarUser = p
    }
    AvatarPick("AI 头像", "选「跟角色卡」就用角色自己的图，选「自定义」用下面这张", Avatar.aiPath()) { p ->
        Avatar.forget(Cfg.avatarAi)
        Cfg.avatarAi = p
        //清空就等于放弃自定义，回落到跟角色卡
        Cfg.avatarAiMode = if (p.isEmpty()) "persona" else "custom"
    }
    CardBox {
        ChoiceRow("跟当前智能体角色", "角色卡有图就用角色的，没有就用默认人形", Cfg.avatarAiMode != "custom") {
            Cfg.avatarAiMode = "persona"
        }
        ChoiceRow(
            "用上面这张自定义图",
            if (Cfg.avatarAi.isEmpty()) "还没选图，选了就切过来" else "所有角色共用这一张",
            Cfg.avatarAiMode == "custom"
        ) {
            if (Cfg.avatarAi.isNotBlank()) Cfg.avatarAiMode = "custom"
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("主题", "深色模式跟随系统或手动固定，切换立刻生效")
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2).forEach { (label, v) ->
                PolicyChip(label, Cfg.darkTheme == v) { Cfg.darkTheme = v }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (Cfg.darkTheme) {
                1 -> "固定浅色，系统切深色也不跟"
                2 -> "固定深色，夜间不刺眼"
                else -> "跟系统设置走，系统深色时自动变深"
            },
            fontSize = 11.sp,
            color = TextSecondary
        )
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("字体大小", "整体缩放文字，dp 尺寸不变；点一下立刻生效，不用重启")
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontPresets.forEach { (label, v) ->
                PolicyChip(label, kotlin.math.abs(Cfg.fontScale - v) < 0.01f) { Cfg.fontScale = v }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("当前 ${(Cfg.fontScale * 100).toInt()}%", fontSize = 11.sp, color = TextSecondary)
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("输入框", "悬浮模式只让输入框本体浮起来，四周留白，不铺整块背景")
    CardBox {
        CapSwitch(
            "悬浮输入框",
            "输入框和发送键单独浮起来、带投影，周围保持页面底色；关掉回到贴底样式",
            Cfg.floatInput
        ) { Cfg.floatInput = it }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("输入提示", "输入框空着时显示的文字；一行一条，多条就每次随机取一条")
    CardBox {
        Field(
            label = "占位文字（每行一条）",
            value = Cfg.hintText,
            onValueChange = { Cfg.hintText = it },
            placeholder = "请输入问题",
            singleLine = false,
            minLines = 3
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (Cfg.hintText.isBlank()) "当前：请输入问题"
            else "当前：${Cfg.hintText.lines().count { it.isNotBlank() }} 条里随机一条",
            fontSize = 11.sp,
            color = TextSecondary
        )
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("导航手势", "系统级 back-to-home、跨任务动画在 targetSdk 36+ 默认开启，应用侧关不掉；这里管的是应用内跟手动画")
    CardBox {
        CapSwitch(
            "预测性手势支持",
            "开启后设置页跟着返回手势缩放淡出，滑过阈值才退回聊天、中途松手归位；关闭则直接切回",
            Cfg.predictiveBack
        ) { Cfg.predictiveBack = it }
    }
} //外观分类：字号 / 输入框 / 手势

@Composable
private fun ExtTab(
    onEdit: (Skill) -> Unit,
    onEditPersona: (Persona) -> Unit,
    onNewTask: () -> Unit
) {
    WorkspaceSection()

    Spacer(Modifier.height(14.dp))
    SectionTitle("定时任务", "到点发通知；勾了「到点自动执行」且模型空闲时会自己跑一轮")
    CardBox {
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
                    if (tasks.isEmpty()) "还没有任务" else "已排 ${tasks.size} 条（启用 $on 条）",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    if (tasks.isEmpty()) "定时让模型干一件事，比如每天 08:30 汇总余票"
                    else tasks.filter { it.on }.take(2)
                        .joinToString("；") { "${if (it.daily) "每天 " else ""}${Alarms.fmt(it.at)} ${it.prompt.replace("\n", " ").take(16)}" }
                        .ifBlank { "任务都停用了，点管理里打开" },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
            GhostButton("管理") { onNewTask() }
        }
    }

    Spacer(Modifier.height(14.dp))
    LinuxSection()

    Spacer(Modifier.height(14.dp))
    ToolchainSection()

    Spacer(Modifier.height(14.dp))
    SectionTitle("搜索平台", "搜索是可选的技能：选了平台并填好 Key，才会把 web_search 下发给模型")
    CardBox {
        val providers = Search.providers
        val sel = Cfg.searchProvider
        if (providers.isEmpty()) {
            Text("平台清单待补充", fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "往 core/Search.kt 的 providers 里加一行、在 Tools.search 里加一个分支，" +
                    "这里就会出现平台选择和 Key 输入框。当前未选平台，模型拿不到 web_search。",
                fontSize = 11.sp,
                color = TextSecondary
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolicyChip("不使用", sel.isBlank()) { Cfg.searchProvider = "" }
                providers.forEach { p ->
                    PolicyChip(p.label, sel == p.id) { Cfg.searchProvider = p.id }
                }
            }
            val cur = Search.selected
            if (cur != null) {
                Spacer(Modifier.height(8.dp))
                if (cur.fields.isEmpty()) {
                    Text("${cur.label} 不需要配置，选中即可用", fontSize = 12.sp, color = OkGreen)
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
                    if (miss == null) "已配置，模型可以自己调 web_search"
                    else "还缺 ${miss.label}：调用会直接返回未配置提示",
                    fontSize = 10.sp,
                    color = if (miss == null) OkGreen else DangerRed
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("智能体角色", "每个会话各自记角色；这里选中的是当前会话用的，切换会话会跟着换")
    CardBox {
        ChoiceRow("默认助手", "不加载任何角色提示", Cfg.personaId.isBlank()) { Agent.setPersona("") }
        Personas.list.forEach { p ->
            PersonaRow(
                p = p,
                selected = Cfg.personaId == p.id,
                onSelect = { Agent.setPersona(p.id) },
                onEdit = { onEditPersona(p) }
            )
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("新增角色", modifier = Modifier.height(38.dp)) {
            onEditPersona(Persona(id = "", name = ""))
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("自定义系统提示", "拼在角色之后、工作流之前；留空不加，默认留空")
    CardBox {
        Field(
            label = "系统提示",
            value = Cfg.systemPrompt,
            onValueChange = { Cfg.systemPrompt = it },
            placeholder = "例如：回答一律用中文，先给结论再给步骤",
            secret = false,
            singleLine = false,
            minLines = 3
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (Cfg.systemPrompt.isBlank()) "未填写：只用内置规则"
            else "已填写 ${Cfg.systemPrompt.length} 字，每次请求都会带上",
            fontSize = 10.sp,
            color = if (Cfg.systemPrompt.isBlank()) TextSecondary else OkGreen
        )
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("技能专区", "把操作流程写成技能，启用后会拼进系统提示，模型照着执行")
    CardBox {
        val list = Skills.list
        if (list.isEmpty()) {
            Text("还没有技能", fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "技能就是一份提示词：写清楚触发场景、要走的命令、判断条件。" +
                    "启用后每次请求都会带给模型，不用每次重新解释。",
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
            GhostButton("新增技能", modifier = Modifier.height(38.dp)) {
                onEdit(Skill("", "", "", "", true))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "已启用 ${Skills.enabled.size} / ${list.size}",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("工具权限", "永久同意/拒绝与工具弹窗同步")
    CardBox {
        Tools.all.forEach { tool ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Tools.label(tool), fontSize = 13.sp, color = TextPrimary)
                    Text(
                        if (Tools.isHighRisk(tool)) "高危 · $tool" else "普通 · $tool",
                        fontSize = 10.sp,
                        color = if (Tools.isHighRisk(tool)) DangerRed else TextSecondary
                    )
                }
                PolicyChip("每次确认", Cfg.policy(tool) == "ask") { Cfg.setPolicy(tool, "ask") }
                Spacer(Modifier.width(6.dp))
                PolicyChip("永久同意", Cfg.policy(tool) == "allow") { Cfg.setPolicy(tool, "allow") }
                Spacer(Modifier.width(6.dp))
                PolicyChip("永久拒绝", Cfg.policy(tool) == "deny") { Cfg.setPolicy(tool, "deny") }
            }
        }
    }

} //扩展分类：功能专区 + 技能专区 + 工具权限

//官网地址：服务端基础地址，仓库那栏等建库后再填
private const val SITE_URL = "https://agent.ai.baiccb.cn"
private const val GITHUB_URL = "https://github.com/mubai0122/baicodex"

/** 关于：应用名 + 一句话描述 + 版本号 / 最新版本 + 检查更新 / 更新历史 + 官网 / GitHub 链接 */
@Composable
private fun AboutTab() {
    var latest by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var busyHis by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    //进页面静默查一次，只为了把「最新版本」那行填上，不弹卡片
    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) { runCatching { Update.check() }.getOrNull() }
        latest = info?.let { "${it.latestVersion}（code ${it.latestCode}）" } ?: ""
    }

    SectionTitle("关于")
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(AccentBlueSoft, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Info, null, tint = AccentBlue, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("BaiAgent", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "跑在本机的 AI Agent：模型直连，读写文件、执行命令都在设备上完成。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("版本号", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "${Update.clientVersion()}（code ${Update.clientCode()}）",
                fontSize = 12.sp,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("最新版本", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                latest.ifBlank { "点「检查更新」获取" },
                fontSize = 12.sp,
                color = if (latest.isBlank()) TextSecondary else OkGreen
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) "检查中…" else "检查更新", modifier = Modifier.height(38.dp)) {
                if (busy) return@GhostButton
                busy = true
                tip = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.check() } }
                    busy = false
                    r.onSuccess { info ->
                        latest = "${info.latestVersion}（code ${info.latestCode}）"
                        when {
                            //服务端关了更新开关：提示可以忽略，不弹卡片
                            !info.updateEnabled -> tip = "服务端已关闭更新提示（最新 ${info.latestVersion}）"
                            info.hasUpdate -> {
                                tip = "发现新版本 ${info.latestVersion}（code ${info.latestCode}）"
                                UpdateUi.prompt = info
                            }
                            else -> tip = "已是最新版本 ${info.latestVersion}"
                        }
                    }.onFailure { tip = "检查失败：${it.message?.take(80)}" }
                }
            }
            GhostButton(if (busyHis) "读取中…" else "更新历史", modifier = Modifier.height(38.dp)) {
                if (busyHis) return@GhostButton
                busyHis = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.history() } }
                    busyHis = false
                    r.onSuccess { UpdateUi.history = it }
                        .onFailure { tip = "读取更新历史失败：${it.message?.take(80)}" }
                }
            }
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 11.sp, color = TextSecondary)
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("相关链接")
    CardBox {
        LinkRow("官网", SITE_URL) { openUrl(ctx, SITE_URL) }
        Spacer(Modifier.height(6.dp))
        //仓库还没建，留空就显示成灰的、点不动
        LinkRow("GitHub", GITHUB_URL.ifBlank { "还没建库" }, enabled = GITHUB_URL.isNotBlank()) {
            openUrl(ctx, GITHUB_URL)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "更新判定只比 code（纯数字），version 仅用于展示；服务端关掉更新开关时一律不提示。",
        fontSize = 10.sp,
        color = TextSecondary
    )
} //关于分类

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
private fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
} //打开链接

@Composable
private fun SkillRow(s: Skill, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.name, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                if (s.desc.isBlank()) "${s.body.length} 字" else s.desc,
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
            "编辑",
            fontSize = 11.sp,
            color = AccentBlue,
            modifier = Modifier
                .background(AccentBlueSoft, RoundedCornerShape(8.dp))
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
            if (skill.id.isBlank()) "新增技能" else "编辑技能",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            Field("名称", name, { name = it }, placeholder = "例如：装 APK 并抓日志")
            Field("一句话说明", desc, { desc = it }, placeholder = "什么时候该用它")
            Field(
                "技能正文",
                body,
                { body = it },
                placeholder = "按步骤写清楚命令、路径与判断条件",
                singleLine = false,
                minLines = 6
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (skill.id.isNotBlank()) {
                Text(
                    "删除",
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
                "取消",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                "保存",
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlue, RoundedCornerShape(10.dp))
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
            .background(if (selected) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(12.dp))
            .tap { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //角色头像：有图就画圆图，没有就默认人形
        Box(Modifier.padding(end = 8.dp)) { AvatarView(p.avatar, 28.dp) }
        Column(Modifier.weight(1f)) {
            Text(p.name.ifBlank { "未命名角色" }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                p.brief + if (p.profileId.isNotBlank()) {
                    " · 绑 " + (Profiles.list.firstOrNull { it.id == p.profileId }?.label ?: "已删配置")
                } else "",
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        if (selected) {
            Text("已选", fontSize = 10.sp, color = AccentBlue)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "编辑",
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier
                .background(SurfaceWhite, RoundedCornerShape(8.dp))
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
            if (persona.id.isBlank()) "新增角色" else "编辑角色",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "角色卡决定模型的身份、口吻、开场白与示例风格；人设与后置指令会拼进系统提示最前面。",
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            //头像走图片：选一张落私有目录，聊天里 AI 头像选「跟角色卡」时就用它
            AvatarPick(
                "角色头像",
                if (avatar.isBlank()) "没设置就用默认人形" else "聊天里显示这张",
                avatar
            ) { avatar = it }
            Field("角色名", name, { name = it }, placeholder = "例如：命令行老手")
            Field("一句话简介", desc, { desc = it }, placeholder = "列表里显示，一行说完")
            Field(
                "人设正文",
                prompt,
                { prompt = it },
                placeholder = "写清楚身份、说话方式、优先做什么、禁止做什么",
                singleLine = false,
                minLines = 6
            )
            Field(
                "适用场景",
                scenario,
                { scenario = it },
                placeholder = "什么情况下用这张卡，例如：线上排障、审方案",
                singleLine = false,
                minLines = 2
            )
            Field(
                "开场白",
                firstMsg,
                { firstMsg = it },
                placeholder = "新建会话时自动发出的第一句 AI 消息，留空就没有",
                singleLine = false,
                minLines = 2
            )
            Field(
                "示例对话",
                examples,
                { examples = it },
                placeholder = "一行一轮，例如：\n用户：xxx\n助手：yyy",
                singleLine = false,
                minLines = 4
            )
            Field(
                "后置指令",
                postHistory,
                { postHistory = it },
                placeholder = "放在系统提示最后，比人设更强势，例如：回答不超过 30 行",
                singleLine = false,
                minLines = 3
            )
            //绑模型：选中这张卡时把整套模型配置一起切过去，不选就跟当前一致
            val boundName = Profiles.list.firstOrNull { it.id == profileId }?.label.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "绑定模型：" + if (profileId.isBlank()) "跟随当前" else boundName,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.width(8.dp))
                if (profileId.isNotBlank()) {
                    Text(
                        "解绑",
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .background(AccentBlueSoft, RoundedCornerShape(8.dp))
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
                    "删除",
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
                "取消",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                "保存",
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlue, RoundedCornerShape(10.dp))
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
internal fun PolicyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (selected) AccentBlue else TextSecondary,
        modifier = Modifier
            .background(
                if (selected) AccentBlueSoft else SurfaceMuted,
                RoundedCornerShape(8.dp)
            )
            .tap { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
} //可选项胶囊

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

@Composable
private fun ToolchainSection() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var rep by remember { mutableStateOf<Toolchain.Report?>(null) }
    LaunchedEffect(tick) {
        loading = true
        rep = withContext(Dispatchers.IO) { Toolchain.probe() }
        loading = false
    }
    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("bai", text))
    }

    SectionTitle(
        "编译工具链",
        "编译 APK 要 Termux 里的 JDK + gradle + Android SDK：这里只检测状态并给命令，不自动装"
    )
    CardBox {
        val r = rep
        if (loading) {
            Text("正在检测…", fontSize = 13.sp, color = TextSecondary)
        } else if (r == null || !r.reachable) {
            Text(r?.note ?: "检测失败", fontSize = 12.sp, color = DangerRed)
        } else {
            Text(r.note, fontSize = 12.sp, color = if (r.ok) OkGreen else DangerRed)
            Spacer(Modifier.height(6.dp))
            r.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.ok) "✓" else "✗",
                        fontSize = 12.sp,
                        color = if (item.ok) OkGreen else DangerRed
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(item.name, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("重新检测", modifier = Modifier.height(38.dp)) { tick++ }
            GhostButton("复制装依赖命令", modifier = Modifier.height(38.dp)) {
                copy(Toolchain.INSTALL_CMD)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("在 Termux 里按顺序执行（点右侧复制）：", fontSize = 11.sp, color = TextSecondary)
        CmdLine(Toolchain.INSTALL_CMD) { copy(it) }
        CmdLine(Toolchain.SDK_CMD) { copy(it) }
        CmdLine(Toolchain.ENV_CMD) { copy(it) }
    }
} //工具链检测卡

@Composable
private fun CmdLine(cmd: String, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cmd, fontSize = 10.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        PolicyChip("复制", false) { onCopy(cmd) }
    }
} //一行可复制命令

@Composable
private fun LinuxSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var test by remember { mutableStateOf("") }
    var installed by remember { mutableStateOf(Linux.installed()) }
    var tick by remember { mutableStateOf(0) }
    //依赖安装：单独一套按钮与日志，别和「解包」共用 busy
    var depsTick by remember { mutableStateOf(0) }
    var depsBusy by remember { mutableStateOf(false) }
    var depsLog by remember { mutableStateOf("") }
    var depsOk by remember { mutableStateOf(Linux.depsInstalled()) }
    LaunchedEffect(depsTick) {
        if (depsTick == 0) {
            depsOk = Linux.depsInstalled()
            return@LaunchedEffect
        }
        depsBusy = true
        depsLog = "开始装依赖…"
        val main = Handler(Looper.getMainLooper())
        val sb = StringBuilder()
        withContext(Dispatchers.IO) {
            Linux.installDeps { line ->
                sb.append(line).append('\n')
                val text = sb.toString()
                main.post { depsLog = text }
            }
        }
        depsOk = Linux.depsInstalled()
        depsBusy = false
    }
    LaunchedEffect(tick) {
        if (tick == 0) {
            installed = Linux.installed()
            return@LaunchedEffect
        }
        busy = true
        log = "开始安装…"
        val main = Handler(Looper.getMainLooper())
        val sb = StringBuilder()
        withContext(Dispatchers.IO) {
            Linux.install(force = installed) { line ->
                sb.append(line).append('\n')
                val text = sb.toString()
                main.post { log = text }
            }
        }
        installed = Linux.installed()
        busy = false
    }
    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("bai", text))
    }

    SectionTitle(
        "Linux 环境（内置 Ubuntu 24.04）",
        "自带 proot + Ubuntu 根文件系统，解包到应用私有目录：apt 装依赖、编译、跑脚本都在里面，不依赖 Termux"
    )
    CardBox {
        val mode = Cfg.workMode
        Text(
            when {
                installed -> "已安装：Ubuntu 可以用了"
                Linux.prootReady() -> "proot 已就位，Ubuntu 还没解包"
                else -> "未安装"
            },
            fontSize = 13.sp,
            color = if (installed) OkGreen else TextSecondary
        )
        if (mode != WorkMode.ROOT) {
            Spacer(Modifier.height(6.dp))
            Text(
                "proot 已作为 native 库打进 APK，普通 / Shizuku / Root 三种模式都能跑，不再要求 Root。" +
                    "当前：${mode.label}",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) "安装中…" else if (installed) "重新解包" else "安装 Ubuntu", modifier = Modifier.height(38.dp)) {
                if (!busy) tick++
            }
            GhostButton(
                if (depsBusy) "装依赖中…" else if (depsOk) "重装依赖" else "一键装依赖",
                modifier = Modifier.height(38.dp)
            ) {
                when {
                    depsBusy || busy -> Unit
                    !installed -> depsLog = "先装 Ubuntu 环境，再装依赖"
                    else -> depsTick++
                }
            }
            GhostButton("自检", modifier = Modifier.height(38.dp)) {
                scope.launch {
                    test = "自检中…"
                    test = withContext(Dispatchers.IO) {
                        val r = Linux.selfTest()
                        if (r.ok) r.out else "失败(${r.code})：${r.out.take(300)}"
                    }
                }
            }
        }
        //依赖状态和日志分开显示：状态一行看全貌，日志看 apt 到底卡在哪
        if (installed) {
            Spacer(Modifier.height(8.dp))
            Text(
                Linux.depReport().joinToString("  ") { (k, v) -> if (v) "✓$k" else "✗$k" },
                fontSize = 10.sp,
                color = if (depsOk) OkGreen else TextSecondary
            )
        }
        if (depsLog.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(depsLog, fontSize = 10.sp, color = TextSecondary)
        }
        if (log.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(log, fontSize = 10.sp, color = TextSecondary)
        }
        if (test.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(test, fontSize = 10.sp, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text("装好之后，让模型用 linux_shell 工具在里面跑命令，例如：", fontSize = 11.sp, color = TextSecondary)
        CmdLine("apt-get update && apt-get install -y openjdk-17-jdk-headless") { copy(it) }
        CmdLine("apt-get install -y git curl unzip python3 python3-pip") { copy(it) }
        Spacer(Modifier.height(6.dp))
        Text(
            "宿主 Android 侧（pm / am / uiautomator）仍用 exec_shell；" +
                "/sdcard 在 Ubuntu 里是同一个路径，写出的文件两边都看得见。",
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //Linux 环境安装卡