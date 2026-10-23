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
import androidx.compose.foundation.border
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
import androidx.compose.runtime.CompositionLocalProvider
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
import android.app.Activity
import b.ai.core.Avatar
import b.ai.core.Backup
import b.ai.core.Cfg
import b.ai.core.Lang
import b.ai.core.Mcp
import b.ai.core.McpServer
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
import b.ai.core.Secret
import b.ai.core.SkillPacks
import b.ai.core.SkillPack
import b.ai.core.Wallpaper
import b.ai.core.WorkMode
import b.ai.exec.Shiz
import b.ai.exec.Alarms
import b.ai.exec.Linux
import b.ai.exec.Router
import b.ai.exec.Toolchain
import b.ai.exec.Workspace
import b.ai.net.Update
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
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

private val SettingsTabs = listOf(
    FloatTab(R.string.se_001) { c -> Icon(Icons.Filled.Settings, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_002) { c -> Icon(Icons.Filled.Email, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_003) { c -> Icon(Icons.Filled.Create, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_004) { c -> Icon(Icons.Filled.List, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_005) { c -> Icon(Icons.Filled.MoreVert, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.se_006) { c -> Icon(Icons.Filled.Info, null, tint = c, modifier = Modifier.size(18.dp)) }
) //设置分类：模型 / 对话 / 外观 / 统计 / 扩展 / 关于（权限已并入对话；索引按标签匹配，改顺序不用动索引）

/** 设置项索引：搜索只查这张表，命中后直接跳到对应 tab；tab 写标签而不是数字，避免换顺序时错位 */
private data class SettingEntry(val labelRes: Int, val tabRes: Int, val hintRes: Int = 0, val hintRaw: String = "")

private val SettingIndex = listOf(
    SettingEntry(R.string.se_007, R.string.se_001, R.string.se_008),
    SettingEntry(R.string.se_009, R.string.se_001, R.string.se_010),
    SettingEntry(R.string.se_011, R.string.se_001, R.string.se_012),
    SettingEntry(R.string.se_013, R.string.se_001, R.string.se_014),
    SettingEntry(R.string.se_015, R.string.se_002, R.string.se_016),
    SettingEntry(R.string.se_017, R.string.se_002, R.string.se_018),
    SettingEntry(R.string.se_019, R.string.se_002, R.string.se_020),
    SettingEntry(R.string.se_021, R.string.se_002, R.string.se_022),
    SettingEntry(R.string.se_023, R.string.se_002, R.string.se_024),
    SettingEntry(R.string.se_025, R.string.se_002, R.string.se_026),
    SettingEntry(R.string.se_027, R.string.se_002, R.string.se_028),
    SettingEntry(R.string.se_029, R.string.se_002, hintRaw = ""),
    SettingEntry(R.string.se_030, R.string.se_003, R.string.se_031),
    SettingEntry(R.string.se_032, R.string.se_003, hintRaw = ""),
    SettingEntry(R.string.se_033, R.string.se_003, R.string.se_034),
    SettingEntry(R.string.se_035, R.string.se_003, R.string.se_036),
    SettingEntry(R.string.se_037, R.string.se_003, hintRaw = ""),
    SettingEntry(R.string.se_038, R.string.se_004, R.string.se_039),
    SettingEntry(R.string.se_040, R.string.se_004, R.string.se_041),
    SettingEntry(R.string.se_042, R.string.se_004, R.string.se_043),
    SettingEntry(R.string.se_044, R.string.se_005, R.string.se_045),
    SettingEntry(R.string.se_046, R.string.se_005, hintRaw = "web_search"),
    SettingEntry(R.string.se_047, R.string.se_005, hintRaw = ""),
    SettingEntry(R.string.se_048, R.string.se_005, hintRaw = ""),
    SettingEntry(R.string.se_049, R.string.se_005, hintRaw = ""),
    SettingEntry(R.string.mcp_title, R.string.se_005, R.string.mcp_sub),
    SettingEntry(R.string.se_050, R.string.se_005, hintRaw = ""),
    SettingEntry(R.string.se_051, R.string.se_005, hintRaw = "JDK / gradle / Android SDK"),
    SettingEntry(R.string.se_052, R.string.se_005, R.string.se_053),
    SettingEntry(R.string.se_054, R.string.se_006, R.string.se_055)
) //搜索索引

/** 按标签找 tab 下标，找不到就落回 0 */
private fun tabIndex(res: Int): Int =
    SettingsTabs.indexOfFirst { it.labelRes == res }.coerceAtLeast(0)

/** 设置：底部悬浮 tab 分类，各分类独立滚动；backProgress 是返回手势进度，用来做跟手跟退 */
@Composable
fun SettingsScreen(backProgress: Float = 0f, initialTab: Int = 0, onBack: () -> Unit) {
    val ctx = LocalContext.current
    //统计要读 SQLite，放 IO 线程，避免进设置页时卡主线程
    var stats by remember { mutableStateOf(0 to 0) }
    var tab by remember { mutableStateOf(initialTab) }
    var draft by remember { mutableStateOf<Skill?>(null) }
    var personaDraft by remember { mutableStateOf<Persona?>(null) }
    var mcpDraft by remember { mutableStateOf<McpServer?>(null) }
    //技能包详情：就地改 README / skill.json / 模板，填它声明出来的密钥
    var packDraft by remember { mutableStateOf<SkillPack?>(null) }
    var taskDraft by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf(false) }
    var fq by remember { mutableStateOf("") }
    //下拉选择面板的当前内容：非空时挂到根弹窗层，和其它弹窗互斥
    var pick by remember { mutableStateOf<Pair<String, List<PickItem>>?>(null) }
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
            .pageBg()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.se_056), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(if (find) stringResource(R.string.se_057) else stringResource(SettingsTabs[tab].labelRes), fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(if (find) AccentBlueSoft else SurfaceWhite, RoundedCornerShape(9.dp))
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
                        .background(AccentBlueSoft, RoundedCornerShape(9.dp))
                        .tap { onBack() }
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
                            ctx.getString(it.labelRes).contains(key, true) ||
                                (if (it.hintRes != 0) ctx.getString(it.hintRes) else it.hintRaw).contains(key, true)
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
                                        .tap { tab = tabIndex(e.tabRes); find = false; fq = "" }
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
                    find -> Text(
                        stringResource(R.string.se_063),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    else -> CompositionLocalProvider(LocalPicker provides { t, l -> pick = t to l }) {
                        when (tab) {
                            0 -> ModelTab()
                            1 -> ChatTab(stats)
                            2 -> UiTab()
                            3 -> StatTab()
                            4 -> ExtTab(
                                onEdit = { draft = it },
                                onEditPersona = { personaDraft = it },
                                onEditMcp = { mcpDraft = it },
                                onNewTask = { taskDraft = true },
                                onEditPack = { packDraft = it }
                            )
                            5 -> AboutTab()
                            else -> UiTab()
                        }
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
    HostInOverlay(active = draft != null || personaDraft != null || mcpDraft != null || taskDraft || pick != null || packDraft != null || PermAsk.kind.value != null) {
        //注入类权限的提示：开关一开就弹，讲清要什么再送去系统页
        PermAsk.kind.value?.let { k -> PermAskDialog(k) { PermAsk.kind.value = null } }
        packDraft?.let { p -> PackDialog(p, onClose = { packDraft = null }) }
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
        pick?.let { (t, l) -> PickSheet(t, l) { pick = null } }
    }
} //设置界面

@Composable
private fun ModelTab() {
    SectionTitle(stringResource(R.string.se_007), stringResource(R.string.se_064))
    ProfileList()

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_009))
    CardBox { ModelSetupPanel() }

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
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_013), stringResource(R.string.se_075))
    CardBox {
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
                onCheckedChange = { Cfg.think = it },
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
        Spacer(Modifier.height(10.dp))
        CapSwitch(
            stringResource(R.string.se_080),
            if (Cfg.maxMode) stringResource(R.string.se_081) else stringResource(R.string.se_082),
            Cfg.maxMode
        ) { Cfg.maxMode = it }
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
} //模型分类

@Composable
private fun ChatTab(stats: Pair<Int, Int>) {
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
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se2_pct, Cfg.ctxPct), fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.se2_kchar, Cfg.ctxCharBudget / 1000), fontSize = 11.sp, color = TextSecondary)
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
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_017), stringResource(R.string.se_104))
    CardBox {
        val ctx = LocalContext.current
        //位置权限不在自动申请清单里：只有你真的打开这个开关才弹，免得缺权限卡住工具调用
        val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            Cfg.injectPlace = it
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
        Spacer(Modifier.height(8.dp))
        //通知与使用时长都要系统侧单独授权：开关先落地，缺权限就弹窗把人送去系统页
        CapSwitch(
            stringResource(R.string.se_320),
            when {
                !Cfg.injectNoti -> stringResource(R.string.se_321)
                !b.ai.core.Noti.allow(ctx) -> stringResource(R.string.se_322)
                else -> stringResource(R.string.se_323, b.ai.core.Noti.count(ctx))
            },
            Cfg.injectNoti
        ) { on ->
            if (on && !b.ai.core.Noti.allow(ctx)) {
                Cfg.injectNoti = true
                PermAsk.kind.value = "noti"
            } else {
                Cfg.injectNoti = on
            }
        }
        if (Cfg.injectNoti) {
            Spacer(Modifier.height(6.dp))
            NotiLimitRow()
        }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_328),
            when {
                !Cfg.injectUsage -> stringResource(R.string.se_329)
                !b.ai.core.Usage.allow(ctx) -> stringResource(R.string.se_330)
                else -> stringResource(R.string.se_331, b.ai.core.Usage.hm(b.ai.core.Usage.today(ctx).first))
            },
            Cfg.injectUsage
        ) { on ->
            if (on && !b.ai.core.Usage.allow(ctx)) {
                Cfg.injectUsage = true
                PermAsk.kind.value = "usage"
            } else {
                Cfg.injectUsage = on
            }
        }
    }

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
    }

    Spacer(Modifier.height(14.dp))
    val ctx2 = LocalContext.current
    SectionTitle(stringResource(R.string.se_122), stringResource(R.string.se_123))
    CardBox {
        CapSwitch(
            stringResource(R.string.se_124),
            if (Cfg.speakReplies) stringResource(R.string.se_125) else stringResource(R.string.se_126),
            Cfg.speakReplies
        ) { on ->
            Cfg.speakReplies = on
            //打开时试念一句，能立刻确认手机装了中文语音包；关掉时把正在念的掐掉
            if (on) Speak.say(ctx2.getString(R.string.se_127), force = true) else Speak.stop()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.se_128),
            fontSize = 11.sp,
            color = TextSecondary
        )
    }

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

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_023), stringResource(R.string.se_132))
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
                            .background(SurfaceWhite, RoundedCornerShape(8.dp))
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

@Composable
private fun PermTab() {
    SectionTitle(stringResource(R.string.se_025), stringResource(R.string.se_146))
    CardBox {
        WorkMode.entries.forEach { m ->
            ChoiceRow(stringResource(m.labelRes), stringResource(m.descRes), Cfg.workMode == m) { Cfg.workMode = m }
        }
    }

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

    Spacer(Modifier.height(14.dp))
    SectionTitle("特殊权限", "按当前模式分档：普通用户要无障碍 + 保活 + 悬浮窗，Root 只要悬浮窗")
    CardBox {
        val specials = Perms.specials(Cfg.workMode)
        if (specials.contains(Perms.SP_A11Y)) {
            A11yPermCard("AI 读屏、点屏靠这个。没开也能聊，只是屏幕类工具会跑不通。")
            Spacer(Modifier.height(12.dp))
        }
        SpecialPermCard(specials.filter { it != Perms.SP_A11Y })
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("Shizuku", "用 shell / root 身份跑命令时走它；授权前要先在 Shizuku 里启动服务")
    CardBox {
        val ctx = LocalContext.current
        var st by remember { mutableStateOf(Shiz.probe(ctx)) }
        var msg by remember { mutableStateOf<String?>(null) }
        Text(st.desc, fontSize = 12.sp, color = if (st.ok) OkGreen else TextSecondary)
        if (msg != null) {
            Spacer(Modifier.height(6.dp))
            Text(msg!!, fontSize = 11.sp, color = DangerRed)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("重新检测") { st = Shiz.probe(ctx) }
            GhostButton("申请授权") {
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
    }

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
} //权限分类（已并入对话分类，入口不再单独占一个 tab）

//字号档位：存的是倍率，1f = 跟随系统
private val FontPresets = listOf(R.string.se_163 to 0.9f, R.string.se_164 to 1f, R.string.se_165 to 1.15f, R.string.se_166 to 1.3f)

@Composable
private fun UiTab() {
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
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_169), stringResource(R.string.se_170))
    CardBox {
        ChoiceRow(stringResource(R.string.se_171), stringResource(R.string.se_172), Cfg.avatarOn) {
            Cfg.avatarOn = !Cfg.avatarOn
        }
        ChoiceRow(stringResource(R.string.se_173), stringResource(R.string.se_174), Cfg.bubbleCapsule) {
            Cfg.bubbleCapsule = !Cfg.bubbleCapsule
        }
        ChoiceRow(stringResource(R.string.se_175), stringResource(R.string.se_176), Cfg.waifuMode) {
            Cfg.waifuMode = !Cfg.waifuMode
        }
    }
    Spacer(Modifier.height(10.dp))
    AvatarPick(stringResource(R.string.se_177), stringResource(R.string.se_178), Cfg.avatarUser) { p ->
        Avatar.forget(Cfg.avatarUser)
        Cfg.avatarUser = p
    }
    AvatarPick(stringResource(R.string.se_179), stringResource(R.string.se_180), Avatar.aiPath()) { p ->
        Avatar.forget(Cfg.avatarAi)
        Cfg.avatarAi = p
        //清空就等于放弃自定义，回落到跟角色卡
        Cfg.avatarAiMode = if (p.isEmpty()) "persona" else "custom"
    }
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

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_033), stringResource(R.string.se_195))
    CardBox {
        CapSwitch(
            stringResource(R.string.se_196),
            stringResource(R.string.se_197),
            Cfg.floatInput
        ) { Cfg.floatInput = it }
    }

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
    }

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

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_037), stringResource(R.string.se2_back_sub))
    CardBox {
        CapSwitch(
            stringResource(R.string.se2_back_title),
            stringResource(R.string.se2_back_desc),
            Cfg.predictiveBack
        ) { Cfg.predictiveBack = it }
    }
} //外观分类：字号 / 输入框 / 手势

@Composable
private fun ExtTab(
    onEdit: (Skill) -> Unit,
    onEditPersona: (Persona) -> Unit,
    onEditMcp: (McpServer) -> Unit,
    onNewTask: () -> Unit,
    onEditPack: (SkillPack) -> Unit
) {
    val ctx = LocalContext.current
    WorkspaceSection()

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_044), stringResource(R.string.se2_task_sub))
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

    Spacer(Modifier.height(14.dp))
    LinuxSection()

    Spacer(Modifier.height(14.dp))
    ToolchainSection()

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

    Spacer(Modifier.height(14.dp))
    McpSection(onEdit = onEditMcp)

    Spacer(Modifier.height(14.dp))
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
    }

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

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.skp_t), stringResource(R.string.skp_d))
    SkillPackBox(onEdit = onEditPack)

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.skp_secret_t), stringResource(R.string.skp_secret_d))
    SecretBox()

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_050), stringResource(R.string.se_235))
    CardBox {
        Tools.all.forEach { tool ->
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
    }

} //扩展分类：功能专区 + 技能专区 + 工具权限

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
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
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

/** 技能密钥：技能包里的 $secret.xxx 从这里取，明文存数据根 */
@Composable
private fun SecretBox() {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf(Secret.all()) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    CardBox {
        if (all.isEmpty()) Text(stringResource(R.string.skp_secret_none), fontSize = 11.sp, color = TextSecondary)
        all.forEach { (k, v) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(k, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                    Text("•••• " + v.takeLast(4), fontSize = 9.sp, color = TextSecondary)
                }
                Text(
                    stringResource(R.string.skp_secret_del),
                    fontSize = 11.sp,
                    color = DangerRed,
                    modifier = Modifier
                        .background(DangerRedSoft, RoundedCornerShape(8.dp))
                        .tap {
                            Secret.put(k, "")
                            all = Secret.all()
                            tip = ""
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Field(stringResource(R.string.skp_secret_name), name, { name = it })
        Spacer(Modifier.height(6.dp))
        Field(stringResource(R.string.skp_secret_val), value, { value = it }, secret = true)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.skp_secret_save), modifier = Modifier.height(34.dp)) {
                val n = name.trim()
                if (!Secret.validName(n)) {
                    tip = ctx.getString(R.string.skp_secret_bad)
                    return@GhostButton
                }
                if (value.isEmpty()) {
                    tip = ctx.getString(R.string.skp_secret_empty)
                    return@GhostButton
                }
                Secret.put(n, value)
                all = Secret.all()
                name = ""
                value = ""
                tip = ctx.getString(R.string.skp_secret_saved, n)
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(tip, fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
} //密钥清单

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
        latest = info?.latestVersion ?: ""
    }

    SectionTitle(stringResource(R.string.se_006))
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
            Text(
                "${Update.clientVersion()}",
                fontSize = 12.sp,
                color = TextPrimary
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
                tip = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.check() } }
                    busy = false
                    r.onSuccess { info ->
                        latest = "${info.latestVersion}"
                        when {
                            //服务端关了更新开关：提示可以忽略，不弹卡片
                            !info.updateEnabled -> tip = ctx.getString(R.string.se2_upd_off, info.latestVersion)
                            info.hasUpdate -> {
                                tip = ctx.getString(R.string.se2_upd_new, info.latestVersion)
                                UpdateUi.prompt = info
                            }
                            else -> tip = ctx.getString(R.string.se2_upd_latest, info.latestVersion)
                        }
                    }.onFailure { tip = ctx.getString(R.string.se2_upd_fail, it.message?.take(80).orEmpty()) }
                }
            }
            GhostButton(if (busyHis) stringResource(R.string.se_133) else stringResource(R.string.se_250), modifier = Modifier.height(38.dp)) {
                if (busyHis) return@GhostButton
                busyHis = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.history() } }
                    busyHis = false
                    r.onSuccess { UpdateUi.history = it }
                        .onFailure { tip = ctx.getString(R.string.se2_upd_hist_fail, it.message?.take(80).orEmpty()) }
                }
            }
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 11.sp, color = TextSecondary)
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_252))
    CardBox {
        LinkRow(stringResource(R.string.se_253), SITE_URL) { openUrl(ctx, SITE_URL) }
        Spacer(Modifier.height(6.dp))
        //仓库还没建，留空就显示成灰的、点不动
        LinkRow("GitHub", GITHUB_URL.ifBlank { stringResource(R.string.se_254) }, enabled = GITHUB_URL.isNotBlank()) {
            openUrl(ctx, GITHUB_URL)
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_bk), stringResource(R.string.se_bk_d))
    CardBox {
        var bkTip by remember { mutableStateOf("") }
        var bkBusy by remember { mutableStateOf(false) }
        //导出：系统选择器给个默认文件名，zip 里是 db + 配置 + baicode 小文件 + 头像
        val bkSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                bkBusy = true
                bkTip = ctx.getString(R.string.se_bk_doing)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openOutputStream(uri)?.use { out -> Backup.export(ctx, out) } ?: false
                        }.getOrDefault(false)
                    }
                    bkBusy = false
                    bkTip = ctx.getString(if (ok) R.string.se_bk_ok else R.string.se_bk_fail)
                }
            }
        }
        //导入：先解到私有 restore 目录，重启后由 App.onCreate 换上去
        val bkOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                bkBusy = true
                bkTip = ctx.getString(R.string.se_bk_doing)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openInputStream(uri)?.use { ins -> Backup.stageImport(ctx, ins) } ?: false
                        }.getOrDefault(false)
                    }
                    bkBusy = false
                    bkTip = ctx.getString(if (ok) R.string.se_bk_imported else R.string.se_bk_bad)
                }
            }
        }
        Text(stringResource(R.string.se_bk_note), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(stringResource(R.string.se_bk_export), modifier = Modifier.height(38.dp)) {
                if (!bkBusy) bkSave.launch("BaiAgent-backup-${System.currentTimeMillis()}.zip")
            }
            GhostButton(stringResource(R.string.se_bk_import), modifier = Modifier.height(38.dp)) {
                if (!bkBusy) bkOpen.launch(arrayOf("application/zip"))
            }
        }
        if (bkTip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(bkTip, fontSize = 11.sp, color = TextSecondary)
        }
    }
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
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
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
            if (skill.id.isBlank()) stringResource(R.string.se_233) else stringResource(R.string.se_257),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
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
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
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
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
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
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
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

/** 通知注入条数：0 或留空 = 不限；滑块 5 条一档，右侧能直接敲数字 */
@Composable
private fun NotiLimitRow() {
    var text by remember { mutableStateOf(if (Cfg.notiLimit <= 0) "" else Cfg.notiLimit.toString()) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se_324), fontSize = 12.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { s ->
                    val d = s.filter { it.isDigit() }.take(4)
                    text = d
                    Cfg.notiLimit = d.toIntOrNull() ?: 0
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.se_325), fontSize = 12.sp) },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 12.sp),
                modifier = Modifier.width(84.dp)
            )
        }
        androidx.compose.material3.Slider(
            value = Cfg.notiLimit.coerceIn(0, 100).toFloat(),
            onValueChange = {
                val v = ((it / 5).toInt() * 5).coerceIn(0, 100)
                Cfg.notiLimit = v
                text = if (v == 0) "" else v.toString()
            },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (Cfg.notiLimit <= 0) stringResource(R.string.se_326) else stringResource(R.string.se_327, Cfg.notiLimit),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //通知注入条数

@Composable
private fun ToolchainSection() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var rep by remember { mutableStateOf<Toolchain.Report?>(null) }
    LaunchedEffect(tick) {
        loading = true
        rep = withContext(Dispatchers.IO) { Toolchain.probe(ctx) }
        loading = false
    }
    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("bai", text))
    }

    SectionTitle(
        stringResource(R.string.se_051),
        stringResource(R.string.se_291)
    )
    CardBox {
        val r = rep
        if (loading) {
            Text(stringResource(R.string.se_292), fontSize = 13.sp, color = TextSecondary)
        } else if (r == null || !r.reachable) {
            Text(r?.note ?: stringResource(R.string.se_293), fontSize = 12.sp, color = DangerRed)
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
            GhostButton(stringResource(R.string.se_294), modifier = Modifier.height(38.dp)) { tick++ }
            GhostButton(stringResource(R.string.se_295), modifier = Modifier.height(38.dp)) {
                copy(Toolchain.INSTALL_CMD)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_296), fontSize = 11.sp, color = TextSecondary)
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
        PolicyChip(stringResource(R.string.se_297), false) { onCopy(cmd) }
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
    //终端式进度：总阶段数 + 当前阶段文案，配合逐行日志画进度条
    var stepCur by remember { mutableStateOf(0) }
    var stepTotal by remember { mutableStateOf(0) }
    var stepText by remember { mutableStateOf("") }
    LaunchedEffect(depsTick) {
        if (depsTick == 0) {
            depsOk = Linux.depsInstalled()
            return@LaunchedEffect
        }
        depsBusy = true
        depsLog = ctx.getString(R.string.se_298)
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
        stepCur = 0
        stepTotal = 0
        stepText = ""
        log = ctx.getString(R.string.se_299)
        val main = Handler(Looper.getMainLooper())
        val sb = StringBuilder()
        withContext(Dispatchers.IO) {
            Linux.install(
                force = installed,
                onStep = { cur, total, txt ->
                    main.post {
                        stepCur = cur
                        stepTotal = total
                        stepText = txt
                    }
                }
            ) { line ->
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
        stringResource(R.string.se_300),
        stringResource(R.string.se_301)
    )
    CardBox {
        val mode = Cfg.workMode
        Text(
            when {
                installed -> stringResource(R.string.se_302)
                Linux.prootReady() -> stringResource(R.string.se_303)
                else -> stringResource(R.string.se_304)
            },
            fontSize = 13.sp,
            color = if (installed) OkGreen else TextSecondary
        )
        if (mode != WorkMode.ROOT) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.se_305) +
                    stringResource(R.string.wm_cur_fmt, stringResource(mode.labelRes)),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) stringResource(R.string.se_307) else if (installed) stringResource(R.string.se_308) else stringResource(R.string.se_309), modifier = Modifier.height(38.dp)) {
                if (!busy) tick++
            }
            GhostButton(
                if (depsBusy) stringResource(R.string.se_310) else if (depsOk) stringResource(R.string.se_311) else stringResource(R.string.se_312),
                modifier = Modifier.height(38.dp)
            ) {
                when {
                    depsBusy || busy -> Unit
                    !installed -> depsLog = ctx.getString(R.string.se_313)
                    else -> depsTick++
                }
            }
            GhostButton(stringResource(R.string.se_314), modifier = Modifier.height(38.dp)) {
                scope.launch {
                    test = ctx.getString(R.string.se_315)
                    test = withContext(Dispatchers.IO) {
                        val r = Linux.selfTest()
                        if (r.ok) r.out else ctx.getString(R.string.se_316, r.code, r.out.take(300))
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
            Spacer(Modifier.height(8.dp))
            TerminalBox(
                title = stringResource(R.string.se_310),
                cur = if (depsBusy) 1 else 0,
                total = if (depsBusy) 1 else 0,
                step = if (depsBusy) stringResource(R.string.se_310) else "",
                text = depsLog
            )
        }
        if (log.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            TerminalBox(
                title = stringResource(R.string.se_300),
                cur = stepCur,
                total = stepTotal,
                step = if (busy) stepText else "",
                text = log
            )
        }
        if (test.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(test, fontSize = 10.sp, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_317), fontSize = 11.sp, color = TextSecondary)
        CmdLine("apt-get update && apt-get install -y openjdk-17-jdk-headless") { copy(it) }
        CmdLine("apt-get install -y git curl unzip python3 python3-pip") { copy(it) }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.se_318) +
                stringResource(R.string.se_319),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //Linux 环境安装卡

/** 终端式进度框：白底等宽字，顶部总进度条 + 当前步骤，日志一长就自动贴底 */
@Composable
private fun TerminalBox(
    title: String,
    cur: Int,
    total: Int,
    step: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    //跟终端一样，来新行就滚到最底
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    val frac = if (total <= 0) 0f else (cur.toFloat() / total).coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .background(SurfaceWhite, RoundedCornerShape(12.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.sp, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (total > 0) Text("$cur/$total", fontSize = 10.sp, color = TextSecondary)
        }
        //总进度条：阶段粒度，细到 3dp 不抢眼
        if (total > 0) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(SurfaceMuted)) {
                Box(Modifier.fillMaxWidth(frac).height(3.dp).background(AccentBlue))
            }
        }
        if (step.isNotBlank()) {
            Text(
                step,
                fontSize = 10.sp,
                color = AccentBlue,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 230.dp)
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
        }
    }
} //终端式进度框
/** 背景图一行：选图 / 清除，图存在时下面跟一条模糊滑杆 */
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
                .background(AccentBlueSoft, RoundedCornerShape(8.dp))
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
                    .background(SurfaceMuted, RoundedCornerShape(8.dp))
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