package b.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Agent
import b.ai.agent.Tools
import b.ai.core.AgentMode
import b.ai.core.Avatar
import b.ai.core.Attach
import b.ai.core.Cards
import b.ai.core.Cfg
import b.ai.core.ChatMsg
import b.ai.core.Images
import b.ai.core.MsgMeta
import b.ai.core.Personas
import b.ai.core.Profiles
import b.ai.core.Skills
import b.ai.core.Speak
import b.ai.core.Voice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 输入框占位提示候选：设置→外观 里填多行就每行一条随机轮换；没填就是「请输入问题」 */
private fun hintPool(ctx: Context): List<String> {
    val custom = Cfg.hintText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return custom.ifEmpty { listOf(ctx.getString(R.string.cs_hint)) }
} //占位提示候选

/** 对话主界面 */
@Composable
fun ChatScreen(onOpenDrawer: () -> Unit, onOpenSettings: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    //会话切换：把输入框换成这个会话自己的草稿，切走再回来不丢
    LaunchedEffect(Agent.sessionId) {
        input = Cfg.draft(Agent.sessionId.orEmpty())
    }
    //停手 0.8 秒才落盘，避免每敲一个字都写一次 SharedPreferences
    LaunchedEffect(input, Agent.sessionId) {
        val sid = Agent.sessionId ?: return@LaunchedEffect
        delay(800)
        Cfg.setDraft(sid, input)
    }
    //随机 placeholder：候选来自设置（默认「请输入问题」），发送后重抽一次
    val hintWords = hintPool(LocalContext.current)
    var hint by remember(hintWords) { mutableStateOf(hintWords.random()) }
    var modelPanel by remember { mutableStateOf(false) }
    var fullInput by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ChatMsg?>(null) }
    var menuFor by remember { mutableStateOf<ChatMsg?>(null) }
    var promptReq by remember { mutableStateOf<PromptReq?>(null) }
    var wsPanel by remember { mutableStateOf(false) }
    var attachPanel by remember { mutableStateOf(false) }
    var cfgPanel by remember { mutableStateOf(false) }
    var modePanel by remember { mutableStateOf(false) }
    var skillPanel by remember { mutableStateOf(false) }
    var personaPanel by remember { mutableStateOf(false) }
    //附件分类：同一个选择器按这里的 mime 与标签区分图片 / 音频 / 视频 / 音乐
    var attachKind by remember { mutableStateOf(ctx.getString(R.string.cs_attach)) }
    var pendingImgs by remember { mutableStateOf<List<String>>(emptyList()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    //相册多选：压缩进私有目录，发送时按 base64 内联给模型
    val pickImgs = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) {
                    uris.mapNotNull { Images.importFrom(ctx, it) }
                }
                pendingImgs = pendingImgs + added
            }
        }
    }
    //任意文件：复制进私有目录，路径插到输入框，模型自己用 read_file 看
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val p = withContext(Dispatchers.IO) { Attach.importFrom(ctx, uri) }
                if (p != null) {
                    input = (if (input.isBlank()) "" else "$input\n") + "[$attachKind] $p"
                }
            }
        }
    }

    //定时任务通知点开：把内容填进输入框，等用户看过再发
    LaunchedEffect(IntentInbox.prompt) {
        val p = IntentInbox.prompt ?: return@LaunchedEffect
        IntentInbox.prompt = null
        input = p
    }

    LaunchedEffect(Agent.messages.size) {
        val last = Agent.messages.size - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    //流式增量进来时贴底，但只在用户本就停在底部时滚，别抢手动翻阅
    LaunchedEffect(Agent.streamText.length, Agent.messages.size) {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (info.totalItemsCount > 0 && last >= info.totalItemsCount - 2) {
            listState.scrollToItem(info.totalItemsCount - 1)
        }
    }

    //safeDrawing = 状态栏/导航条/输入法取并集，只留一份底部间距，不会和 IME 叠出空白
    Column(Modifier.fillMaxSize().pageBg().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(onOpenDrawer, onOpenWorkspace = { wsPanel = true }, onPersona = { personaPanel = !personaPanel })

        //输入区上方的内联面板：+ / 三条横线 / 技能包，跟 @ 面板同一位置
        val panelOpen = attachPanel || cfgPanel || skillPanel || modelPanel || personaPanel
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                //面板开着时点聊天区空白就收起；detectTapGestures 只在没拖动时触发，列表照常滚
                .then(
                    if (panelOpen) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                attachPanel = false
                                cfgPanel = false
                                skillPanel = false
                                modelPanel = false
                                personaPanel = false
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            //面板弹出时不渲染启动卡：直接消失，位置留给面板，也避免关闭时手指落在卡片上误触
            if (Agent.messages.isEmpty() && !panelOpen) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onPick = { input = it }
                )
            } else if (Agent.messages.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Agent.messages) { m ->
                        Column(Modifier.fillMaxWidth()) {
                            //重发 / 重新生成长出来的分支：同父有几条就在这条上方给 ‹ n/m ›
                            if (m.bTotal > 1) {
                                BranchBar(m.bIdx, m.bTotal) { d -> Agent.switchBranch(m.id, d) }
                            }
                            MessageItem(
                                m,
                                onToolDetail = { detail = it },
                                onLongPress = { menuFor = it }
                            )
                        }
                    }
                    //流式增量：边收边渲染，落库前就看得见字在长
                    if (Agent.busy && Agent.runningHere() && (Agent.streamText.isNotEmpty() || Agent.streamReason.isNotEmpty())) {
                        item("live") { StreamingMsg(Agent.streamText, Agent.streamReason) }
                    }
                }
            }
        }

        //阶段文案 + 秒表：请求模型时一直有东西在动，不会看起来卡死
        var tick by remember { mutableStateOf(0L) }
        LaunchedEffect(Agent.busy, Agent.busySince) {
            while (Agent.busy && Agent.busySince > 0L) {
                tick = System.currentTimeMillis()
                kotlinx.coroutines.delay(500)
            }
            tick = 0L
        }
        val showStatus = Agent.busy || Agent.status == stringResource(R.string.cs_stopped)
        if (showStatus && Agent.status.isNotBlank()) {
            val el = if (Agent.busy && Agent.busySince > 0L && tick > 0L) {
                ((tick - Agent.busySince) / 1000L).coerceAtLeast(0L)
            } else 0L
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val live = buildString {
                append(Agent.status)
                if (el > 0) append(" · ${el}s")
                if (Agent.streamText.isNotEmpty()) append(" · " + stringResource(R.string.cs_chars_unit, Agent.streamText.length))
            }
            Text(
                    live,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.weight(1f))
                if (Agent.busy) {
                    Text(
                        stringResource(R.string.cs_stop),
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier
                            .background(DangerRedSoft, RoundedCornerShape(8.dp))
                            .tap { Agent.stop() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

//输入区上方：+ / 三条横线的内联面板，跟 @ 面板同一位置；进场短促，弹出/收起都跟手
        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn(tween(90)) + slideInVertically(tween(150)) { it / 2 },
            exit = fadeOut(tween(80)) + slideOutVertically(tween(120)) { it / 2 }
        ) {
            when {
                attachPanel -> AttachPanel(
                    onClose = { attachPanel = false },
                    onPickImage = {
                        attachPanel = false
                        pickImgs.launch("image/*")
                    },
                    onPickFile = { kind, mime ->
                        attachPanel = false
                        attachKind = kind
                        pickFile.launch(arrayOf(mime))
                    },
                    onSkill = {
                        attachPanel = false
                        skillPanel = true
                    }
                )
                skillPanel -> SkillPanel(
                    onClose = { skillPanel = false },
                    onInsert = { txt ->
                        skillPanel = false
                        input = if (input.isBlank()) txt else "$input\n$txt"
                    }
                )
                modelPanel -> ModelPanel(
                    onClose = { modelPanel = false },
                    onOpenSettings = {
                        modelPanel = false
                        onOpenSettings(0)
                    }
                )
                personaPanel -> PersonaPanel(
                    onClose = { personaPanel = false },
                    onManage = {
                        personaPanel = false
                        //扩展是第 5 个 tab（索引 4）：模型 / 对话 / 外观 / 统计 / 扩展 / 关于
                        onOpenSettings(4)
                    }
                )
                else -> CfgPanel(onClose = { cfgPanel = false })
            }
        }

        //末尾 @token 命中就在输入区上方弹出候选：技能填正文、工具填名字、files 接路径
        val mi = mentionOf(input)
        //两个面板互斥：+ 面板开着时不弹 @ 候选，免得同时占两倍高度把输入框挤扁
        if (mi != null && !panelOpen) {
            MentionPanel(token = mi.token) { text ->
                input = input.substring(0, mi.start) + text
            }
        }

        //输入框以 / 开头：弹快捷指令，点一条当场执行并把结果写进占位提示
        val si = slashToken(input)
        if (si != null && !panelOpen) {
            SlashPanel(si) { cmd ->
                input = ""
                val msg = execSlash(cmd, ctx, onOpenSettings, persona = { personaPanel = true })
                if (msg.isNotEmpty()) hint = msg
            }
        }

        //左下角 chip 显示当前配置名；没配过就退回模型名
        val modelLabel = Profiles.list.firstOrNull { it.id == Cfg.profileId }?.label
            ?: Cfg.model.ifBlank { stringResource(R.string.cs_pick_model) }
        InputBar(
            input = input,
            hint = hint,
            modelLabel = modelLabel,
            modeLabel = stringResource(Cfg.agentMode.labelRes),
            onOpenMode = { modePanel = true },
            onChange = { input = it },
            images = pendingImgs,
            //三条杠 / 加号都能再点一次关掉，与其他面板互斥
            onOpenConfig = {
                val on = !cfgPanel
                cfgPanel = on
                if (on) {
                    attachPanel = false
                    skillPanel = false
                    modelPanel = false
                    personaPanel = false
                }
            },
            onOpenAttach = {
                val on = !attachPanel
                attachPanel = on
                if (on) {
                    cfgPanel = false
                    skillPanel = false
                    modelPanel = false
                    personaPanel = false
                }
            },
            onOpenModel = {
                val on = !modelPanel
                modelPanel = on
                if (on) {
                    cfgPanel = false
                    attachPanel = false
                    skillPanel = false
                    personaPanel = false
                }
            },
            onExpand = { fullInput = true },
            onRemoveImage = { p ->
                pendingImgs = pendingImgs - p
                Images.delete(p)
            },
            onSend = {
                val t = input.trim()
                val imgs = pendingImgs
                if (t.isNotEmpty() || imgs.isNotEmpty()) {
                    input = ""
                    pendingImgs = emptyList()
                    hint = hintWords.random()
                    fullInput = false
                    Agent.send(if (t.isEmpty()) ctx.getString(R.string.cs_see_images) else t, imgs)
                }
            }
        )
    }

    //工具详情、长按菜单、重发/重生成输入框、工作区绑定统一走根弹窗层，卡片本身不会被录进模糊背景
    HostInOverlay(detail != null || menuFor != null || promptReq != null || wsPanel || fullInput || modePanel) {
        val d = detail
        val mn = menuFor
        val pr = promptReq
        when {
            modePanel -> ModeSheet { modePanel = false }
            fullInput -> FullInputDialog(
                value = input,
                onChange = { input = it },
                images = pendingImgs,
                onRemoveImage = { p ->
                    pendingImgs = pendingImgs - p
                    Images.delete(p)
                },
                canSend = !Agent.busy && (input.isNotBlank() || pendingImgs.isNotEmpty()),
                onClose = { fullInput = false },
                onSend = {
                    val t = input.trim()
                    val imgs = pendingImgs
                    if (t.isNotEmpty() || imgs.isNotEmpty()) {
                        input = ""
                        pendingImgs = emptyList()
                        hint = hintWords.random()
                        fullInput = false
                        Agent.send(if (t.isEmpty()) ctx.getString(R.string.cs_see_images) else t, imgs)
                    }
                }
            )
            wsPanel -> WorkspaceDialog { wsPanel = false }
            d != null -> ToolDetailDialog(d) { detail = null }
            mn != null -> MsgMenuDialog(
                m = mn,
                onEdit = {
                    promptReq = PromptReq(mn, true)
                    menuFor = null
                },
                onRegen = {
                    Agent.regenerate(mn.id)
                    menuFor = null
                },
                onRegenWith = {
                    promptReq = PromptReq(mn, false)
                    menuFor = null
                },
                onCopy = {
                    copyText(ctx, mn.content)
                    menuFor = null
                },
                onStar = {
                    Agent.toggleStar(mn, !mn.starred)
                    menuFor = null
                },
                onSpeak = {
                    Speak.say(mn.content, true)
                    menuFor = null
                },
                onDelete = {
                    Agent.deleteMessage(mn)
                    menuFor = null
                },
                onClose = { menuFor = null }
            )
            pr != null -> PromptDialog(
                title = if (pr.edit) stringResource(R.string.cs_edit_resend) else stringResource(R.string.cs_regen_hint),
                hint = if (pr.edit) stringResource(R.string.cs_edit_desc) else stringResource(R.string.cs_regen_hint_desc),
                initial = if (pr.edit) pr.m.content else "",
                confirm = if (pr.edit) stringResource(R.string.cs_send) else stringResource(R.string.cs_regen),
                onClose = { promptReq = null },
                onConfirm = { text ->
                    promptReq = null
                    if (pr.edit) Agent.editResend(pr.m.id, text) else Agent.regenerate(pr.m.id, text)
                }
            )
        }
    }
} //聊天界面

@Composable
private fun TopBar(onOpenDrawer: () -> Unit, onOpenWorkspace: () -> Unit, onPersona: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(SurfaceMuted, CircleShape)
                .tap { onOpenDrawer() },
            contentAlignment = Alignment.Center
        ) { IcMenu(TextPrimary) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Agent.sessionTitle.ifBlank { stringResource(R.string.ag_new_session) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                //角色胶囊：头像 + 名字，跟在标题后面
                val role = Personas.current()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (role == null) SurfaceMuted else AccentBlueSoft,
                            RoundedCornerShape(10.dp)
                        )
                        .tap { onPersona() }
                        .padding(start = 3.dp, end = 7.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    AvatarView(
                        role?.avatar.orEmpty(),
                        16.dp,
                        if (role == null) TextSecondary else AccentBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        role?.let { Personas.displayName(it) }?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.cs_default_persona),
                        fontSize = 10.sp,
                        color = if (role == null) TextSecondary else AccentBlue,
                        maxLines = 1
                    )
                }
            }
            Text(
                "${Cfg.model.ifBlank { stringResource(R.string.ms_no_model) }} · ${stringResource(Cfg.workMode.labelRes)} · " +
                    if (Cfg.think) stringResource(R.string.cs_think) else stringResource(R.string.cs_think_off),
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        //右上角两个动作：新建会话（加号）、工作区绑定（代码尖括号）
        Box(
            Modifier
                .size(34.dp)
                .background(SurfaceMuted, CircleShape)
                .tap { Agent.newSession() },
            contentAlignment = Alignment.Center
        ) { IcPlus(TextPrimary) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(34.dp)
                .background(SurfaceMuted, CircleShape)
                .tap { onOpenWorkspace() },
            contentAlignment = Alignment.Center
        ) { IcCode(TextPrimary) }
    }
} //顶栏：抽屉 + 标题 + 角色胶囊 + 新建会话 + 工作区绑定

/** 角色快速切换：只改 Cfg.personaId，系统提示每轮重算，所以下一句就生效 */
@Composable
private fun PersonaPanel(onClose: () -> Unit, onManage: () -> Unit) {
    CardBox {
        SectionTitle(stringResource(R.string.cs_persona_title), stringResource(R.string.cs_persona_sub))
        ChoiceRow(stringResource(R.string.cs_default_persona), stringResource(R.string.cs_persona_none), Cfg.personaId.isBlank()) { Agent.setPersona("") }
        Personas.list.forEach { p ->
            ChoiceRow(
                Personas.displayName(p).ifBlank { stringResource(R.string.cs_persona_unnamed) },
                Personas.brief(p),
                Cfg.personaId == p.id
            ) { Agent.setPersona(p.id) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.cs_persona_manage), modifier = Modifier.height(38.dp)) { onManage() }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cs_persona_manage_sub), fontSize = 10.sp, color = TextSecondary)
        }
    }
} //角色面板

/** 空会话标题的候选：按当前时段分池——23:00–04:30 深夜、06:00–06:59 清早，其余走通用问候 */
private fun greetingPool(ctx: Context, h: Int, m: Int): List<String> = when {
    //跨零点算一段：23 点之后、4 点 30 之前都算深夜
    h >= 23 || h < 4 || (h == 4 && m < 30) -> listOf(
        ctx.getString(R.string.cs_greet_n1),
        ctx.getString(R.string.cs_greet_n2),
        ctx.getString(R.string.cs_greet_n3)
    )
    h == 6 -> listOf(
        ctx.getString(R.string.cs_greet_a1),
        ctx.getString(R.string.cs_greet_a2)
    )
    else -> listOf(
        ctx.getString(R.string.cs_greet_g1),
        ctx.getString(R.string.cs_greet_g2),
        ctx.getString(R.string.cs_greet_g3),
        ctx.getString(R.string.cs_greet_g4),
        ctx.getString(R.string.cs_greet_g5),
        ctx.getString(R.string.cs_greet_g6),
        ctx.getString(R.string.cs_greet_g7)
    )
} //按时段挑问候池

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit
) {
    val hint = stringResource(R.string.cs_empty_tip)
    //打字机：逐字冒出来，每字 100ms；打完再让卡片依次飞入
    var typed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        typed = 0
        for (i in 1..hint.length) {
            typed = i
            kotlinx.coroutines.delay(100L)
        }
    }
    var flying by remember { mutableStateOf(false) }
    LaunchedEffect(typed) { if (typed >= hint.length) flying = true }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        //标题按时段随机：进空会话抽一次，同一时段内不跳；跨时段（池子变了）自动重抽
        val cal = java.util.Calendar.getInstance()
        val pool = greetingPool(LocalContext.current, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        val greeting = remember(pool) { pool.random() }
        Text(
            greeting,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            hint.take(typed) + if (typed < hint.length) "▌" else "",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(14.dp))
        //两列能力卡，按顺序逐张飞入：点一下把起手式填进输入框
        Cards.all.chunked(2).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                row.forEachIndexed { i, c ->
                    FeatureTile(
                        icon = c.icon,
                        title = stringResource(c.titleRes),
                        desc = stringResource(c.descRes),
                        index = rowIdx * 2 + i,
                        show = flying,
                        modifier = Modifier.weight(1f)
                    ) { onPick(c.prompt) }
                    Spacer(Modifier.width(6.dp))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
} //空会话引导：打字机文案 + 能力卡依次飞入

@Composable
private fun FeatureTile(
    icon: String,
    title: String,
    desc: String,
    index: Int = 0,
    show: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    //每张卡错开 80ms：从下方 32dp 淡入并放大到 1，依次落位
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        kotlinx.coroutines.delay(index * 80L)
        go = true
    }
    val p by animateFloatAsState(
        targetValue = if (go) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "tileIn$index"
    )
    GlassPanel(
        modifier = modifier
            .graphicsLayer {
                alpha = p
                translationY = (1f - p) * 32.dp.toPx()
                val s = 0.92f + 0.08f * p
                scaleX = s
                scaleY = s
            },
        corner = 14.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
        onClick = onClick
    ) {
        Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) { CardGlyph(icon, AccentBlue) }
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
        Text(desc, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
} //一张能力卡

/** 能力卡的图标：全部自绘矢量，不用 emoji 字形（不同机型字形差异太大） */
@Composable
private fun CardGlyph(icon: String, color: Color) {
    when (icon) {
        "cube" -> IcCube(color)
        "search" -> IcSearch(color, side = 20.dp)
        "layers" -> IcLayers(color)
        "train" -> IcTrain(color)
        "tap" -> IcTap(color)
        "code" -> IcCode(color, side = 20.dp)
        else -> IcGrid(color, side = 20.dp)
    }
} //能力卡图标

@Composable
private fun BarChip(
    active: Boolean,
    flatActive: Color,
    accent: Color = AccentBlue,
    onTap: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    //开了质感按钮就换成带厚度的 3D 胶囊，否则维持原来那块扁平面板
    if (Cfg.tactileBtn) {
        TactileChip(active = active, accent = accent, onClick = onTap, content = content)
    } else {
        Row(
            Modifier
                .background(if (active) flatActive else SurfaceMuted, RoundedCornerShape(9.dp))
                .tap { onTap() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
} //输入区小胶囊

/** 发送键：开了质感按钮走 3D 圆钮，否则还是原来那颗扁圆 */
@Composable
private fun SendButton(canSend: Boolean, onSend: () -> Unit) {
    if (Cfg.tactileBtn) {
        TactileCircle(active = canSend, enabled = canSend, onClick = onSend) {
            if (Agent.busy) {
                Text("…", fontSize = 14.sp, color = TextSecondary)
            } else {
                IcSend(if (canSend) Color.White else TextSecondary, side = 15.dp)
            }
        }
    } else {
        Box(
            Modifier
                .size(34.dp)
                .background(if (canSend) AccentBlue else SurfaceMuted, CircleShape)
                .tap(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (Agent.busy) {
                Text("…", fontSize = 14.sp, color = TextSecondary)
            } else {
                IcSend(if (canSend) Color.White else TextSecondary, side = 15.dp)
            }
        }
    }
} //发送键

@Composable
private fun InputBar(
    input: String,
    hint: String,
    modelLabel: String,
    modeLabel: String,
    onOpenMode: () -> Unit,
    onChange: (String) -> Unit,
    images: List<String>,
    onOpenConfig: () -> Unit,
    onOpenAttach: () -> Unit,
    onOpenModel: () -> Unit,
    onExpand: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit
) {
    val ctx = LocalContext.current
    //悬浮模式：只把输入框本体拎出来浮着，外层不铺任何背景，留着 BgGray 底
    val floating = Cfg.floatInput
    val outer = Modifier
        .fillMaxWidth()
        .padding(
            start = if (floating) 12.dp else 12.dp,
            end = if (floating) 12.dp else 12.dp,
            top = if (floating) 8.dp else 4.dp,
            bottom = if (floating) 12.dp else 10.dp
        )
    Column(outer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            //深度思考开关：直接改配置，下一轮请求生效
            BarChip(
                active = Cfg.think,
                flatActive = AccentBlueSoft,
                onTap = { Cfg.think = !Cfg.think }
            ) {
                IcSpark(if (Cfg.think) AccentBlue else TextSecondary, side = 12.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    if (Cfg.think) stringResource(R.string.cs_think_effort, Cfg.thinkEffort) else stringResource(R.string.cs_think),
                    fontSize = 10.sp,
                    color = if (Cfg.think) AccentBlue else TextSecondary
                )
            }
            Spacer(Modifier.width(6.dp))
            //最大模式：不下发 max_tokens、思考顶到 high，长任务不容易被截断
            BarChip(
                active = Cfg.maxMode,
                flatActive = AccentBlueSoft,
                onTap = { Cfg.maxMode = !Cfg.maxMode }
            ) {
                Text(
                    stringResource(R.string.cs_max),
                    fontSize = 10.sp,
                    color = if (Cfg.maxMode) AccentBlue else TextSecondary
                )
            }
            Spacer(Modifier.width(6.dp))
            //语音：走系统识别，说完的文本追加进输入框
            BarChip(
                active = Voice.listening,
                accent = DangerRed,
                flatActive = DangerRedSoft,
                onTap = { Voice.toggle(ctx) { t -> onChange((input + t).trim()) } }
            ) {
                IcMic(if (Voice.listening) DangerRed else TextSecondary, side = 12.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (Voice.listening) stringResource(R.string.cs_listening) else stringResource(R.string.cs_voice),
                    fontSize = 10.sp,
                    color = if (Voice.listening) DangerRed else TextSecondary
                )
            }
            Spacer(Modifier.weight(1f))
            if (input.isNotEmpty()) {
                Text(stringResource(R.string.cs_chars_unit, input.length), fontSize = 10.sp, color = TextSecondary)
            }
        }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            //选中就是缩略图，右上角 × 直接删
            ImgThumbs(images = images, size = 56.dp, onRemove = onRemoveImage)
        }
        if (Voice.error.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(Voice.error, fontSize = 10.sp, color = DangerRed)
        }
        Spacer(Modifier.height(6.dp))
        //输入容器与发送键都从 44dp 起算，单行时严格等高；多行时发送键贴底
        //悬浮模式：输入框和发送键各自带投影浮起来，周围仍是页面底色，不再铺整块白卡
        val floatShadow = if (floating) Modifier.shadow(12.dp, RoundedCornerShape(14.dp)) else Modifier
        val canSend = !Agent.busy && (input.isNotBlank() || images.isNotEmpty())
        //输入框和纸飞机键同一个白卡：小圈内嵌右侧，不再单独立 66dp 大按钮
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(floatShadow)
                .background(SurfaceWhite, RoundedCornerShape(14.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
                .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                    //正文独占一行：全屏 / 配置 / 附件统一挪到底部那一排
                    Box(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                        if (input.isEmpty()) {
                            Text(
                                hint,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = onChange,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                            maxLines = 5,
                            cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    //底部一行：左边模型胶囊，右边配置与附件，两头分开排
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .background(SurfaceMuted, RoundedCornerShape(8.dp))
                                .tap { onOpenModel() }
                                .padding(start = 7.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modelLabel,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 146.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.size(9.dp).graphicsLayer { rotationZ = -90f }) {
                                IcBack(TextSecondary, side = 9.dp, stroke = 1.5.dp)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        //运行模式胶囊：点开选计划 / 标准 / 代码 / 极简 / 创造
                        Row(
                            Modifier
                                .background(
                                    if (Cfg.agentMode == AgentMode.STANDARD) SurfaceMuted else AccentBlueSoft,
                                    RoundedCornerShape(8.dp)
                                )
                                .tap { onOpenMode() }
                                .padding(start = 7.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modeLabel,
                                fontSize = 10.sp,
                                color = if (Cfg.agentMode == AgentMode.STANDARD) TextSecondary else AccentBlue,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (Cfg.agentMode == AgentMode.STANDARD) TextSecondary else AccentBlue,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        //配置 / 附件 / 全屏全在底部这一排，左边模型右边三个图标
                        Box(
                            Modifier.size(30.dp).tap { onOpenConfig() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cs_config),
                                tint = if (Cfg.maxMode || Cfg.think) AccentBlue else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            Modifier.size(30.dp).tap { onOpenAttach() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.cs_attach),
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            Modifier.size(30.dp).tap { onExpand() },
                            contentAlignment = Alignment.Center
                        ) { IcExpand(TextSecondary, side = 16.dp, stroke = 1.5.dp) }
                    }
                }
            Spacer(Modifier.width(6.dp))
            //纸飞机小圈：内嵌在输入框右侧，禁用时灰底灰图标
            SendButton(canSend = canSend, onSend = onSend)
        }
    }
} //输入区：图片 / 文件 / 语音 / 发送

/** 全屏输入：长文 / 多行代码不用在小框里挤，顶部工具条 + 整屏编辑区 + 底部发送 */
@Composable
private fun FullInputDialog(
    value: String,
    onChange: (String) -> Unit,
    images: List<String>,
    onRemoveImage: (String) -> Unit,
    canSend: Boolean,
    onClose: () -> Unit,
    onSend: () -> Unit
) {
    val fr = remember { FocusRequester() }
    //进来就抢焦点，键盘自动弹；抢不到（极少数机型）不影响手动点
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    //根弹窗层本身透明，这里必须自己铺底色，否则会透出底下的聊天内容
    Box(Modifier.fillMaxSize().background(BgGray).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.cs_fullscreen), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.cs_chars_unit, value.length), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(28.dp).background(SurfaceMuted, CircleShape).tap { onClose() },
                    contentAlignment = Alignment.Center
                ) { IcClose(TextSecondary, side = 12.dp) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .background(SurfaceWhite, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = TextPrimary,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxSize().focusRequester(fr)
                )
            }
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                //全屏输入同样铺缩略图，多了横向滑
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    ImgThumbs(images = images, size = 64.dp, onRemove = onRemoveImage)
                }
            }
            Box(
                Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(if (canSend) AccentBlue else SurfaceMuted, RoundedCornerShape(14.dp))
                    .tap(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (Agent.busy) "…" else stringResource(R.string.cs_send),
                    fontSize = 14.sp,
                    color = if (canSend) Color.White else TextSecondary
                )
            }
        }
    }
} //全屏输入弹窗

@Composable
private fun StreamingMsg(text: String, reason: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    //流式气泡也算一条消息，头像与圆角跟正式回复保持一致
    if (Cfg.avatarOn) {
        AvatarView(Avatar.aiPath(), 28.dp)
        Spacer(Modifier.width(6.dp))
    }
    Column(
        Modifier
            .weight(1f)
            .background(SurfaceWhite, bubbleShape(14.dp))
            .padding(horizontal = bubblePadH(12.dp), vertical = bubblePadV(12.dp))
        ) {
        if (reason.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IcSpark(TextSecondary, side = 12.dp)
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.cs_think_chars, reason.length), fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                reason.replace("\n", " ").takeLast(110),
                fontSize = 11.sp,
                color = TextSecondary
            )
            if (text.isNotBlank()) Spacer(Modifier.height(8.dp))
        }
        if (text.isNotBlank()) {
            Text(text + "▍", fontSize = 13.sp, color = TextPrimary)
        }
    }
    }
} //流式输出中的临时气泡

/** 分支切换条：‹ n/m ›，点箭头在同父的几条回答之间翻，翻到哪条 head 就跟到哪条 */
@Composable
private fun BranchBar(idx: Int, total: Int, onSwitch: (Int) -> Unit) {
    Row(
        Modifier.padding(start = 42.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "‹",
            fontSize = 15.sp,
            color = AccentBlue,
            modifier = Modifier
                .background(AccentBlueSoft, RoundedCornerShape(7.dp))
                .tap { onSwitch(-1) }
                .padding(horizontal = 7.dp, vertical = 1.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text("$idx/$total", fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.width(5.dp))
        Text(
            "›",
            fontSize = 15.sp,
            color = AccentBlue,
            modifier = Modifier
                .background(AccentBlueSoft, RoundedCornerShape(7.dp))
                .tap { onSwitch(1) }
                .padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
} //分支切换条

@Composable
private fun MessageItem(m: ChatMsg, onToolDetail: (ChatMsg) -> Unit, onLongPress: (ChatMsg) -> Unit) {
    when (m.role) {
        "user" -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .background(AccentBlueSoft, bubbleShape(14.dp))
                    .longPress { onLongPress(m) }
                    .padding(horizontal = bubblePadH(12.dp), vertical = bubblePadV(12.dp))
                    ) {
                Column {
                    if (m.images.isNotEmpty()) {
                        //发出去的图按原图尺寸展示（不裁成小方块），多了横向滑
                        ImgFull(images = m.images, maxWidth = 240.dp, maxHeight = 340.dp)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(m.content, fontSize = 13.sp, color = TextPrimary)
                }
            }
            if (Cfg.avatarOn) {
                Spacer(Modifier.width(6.dp))
                AvatarView(Cfg.avatarUser, 28.dp)
            }
        }

        "tool" -> ToolMsg(m, onOpen = { onToolDetail(m) })
        "summary" -> SummaryMsg(m)

        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            //AI 头像：设置里选「跟角色卡」就用当前角色那张，否则用自定义图
            if (Cfg.avatarOn) {
                AvatarView(Avatar.aiPath(), 28.dp)
                Spacer(Modifier.width(6.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .background(SurfaceWhite, bubbleShape(14.dp))
                    .longPress { onLongPress(m) }
                    .padding(horizontal = bubblePadH(12.dp), vertical = bubblePadV(12.dp))
                    ) {
                if (m.reasoning.isNotBlank()) ThinkBlock(m)
                if (m.content.isNotBlank()) {
                    MdText(m.content, color = if (m.isError) DangerRed else TextPrimary)
                }
                if (m.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.toolCalls.forEach { c -> Tag("→ ${Tools.label(c.name)}") }
                    }
                    Spacer(Modifier.height(4.dp))
                    m.toolCalls.forEach { c ->
                        Text(
                            c.arguments.replace("\n", " ").take(140),
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                val mt = m.meta
                if (mt != null && (Cfg.showUsage || Cfg.showMeta)) {
                    Spacer(Modifier.height(6.dp))
                    MetaLine(mt)
                }
                }
            }
    }
} //单条消息渲染

/**消息气泡圆角：开了「消息强制高圆角」就用 16dp 大圆角，否则用常规圆角 */
private fun bubbleShape(regular: Dp): RoundedCornerShape =
    if (Cfg.bubbleCapsule) RoundedCornerShape(16.dp) else RoundedCornerShape(regular)

/**气泡内边距：大圆角要留余量，否则文字会顶到弧线上 */
private fun bubblePadH(regular: Dp): Dp = if (Cfg.bubbleCapsule) regular + 4.dp else regular

private fun bubblePadV(regular: Dp): Dp = if (Cfg.bubbleCapsule) 11.dp else regular
/**回复底部那行小字：用量与请求信息，按设置里的两个开关拼 */
@Composable
private fun MetaLine(m: MsgMeta) {
    val parts = ArrayList<String>(7)
    if (Cfg.showUsage) {
        parts.add(tokText(m.totalTokens) + " tok")
        if (m.speed > 0) parts.add(String.format(Locale.US, "%.0f tok/s", m.speed))
    }
    if (Cfg.showMeta) {
        if (m.ms > 0) parts.add(stringResource(R.string.cs_elapsed) + secText(m.ms) + "s")
        if (m.firstMs > 0) parts.add(stringResource(R.string.cs_first) + secText(m.firstMs) + "s")
        if (m.at > 0) parts.add(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.at)))
        if (m.provider.isNotBlank()) parts.add(m.provider)
        if (m.model.isNotBlank()) parts.add(m.model)
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        fontSize = 9.sp,
        lineHeight = 13.sp,
        color = TextSecondary
    )
} //回复元信息

private fun tokText(n: Int): String =
    if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000.0) else n.toString() //token 数

private fun secText(ms: Long): String =
    if (ms < 10_000L) String.format(Locale.US, "%.1f", ms / 1000.0) else (ms / 1000L).toString() //秒数

@Composable
private fun ThinkBlock(m: ChatMsg) {
    var open by remember(m.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(SurfaceMuted, RoundedCornerShape(10.dp))
            .tap { open = !open }
            .padding(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IcSpark(TextSecondary, side = 12.dp)
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.cs_think_proc), fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                if (open) stringResource(R.string.cs_collapse) else stringResource(R.string.cs_reason_chars, m.reasoning.length),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (open) m.reasoning else m.reasoning.replace("\n", " ").take(90) + "…",
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = if (open) Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()) else Modifier
        )
    }
} //思考过程：默认一行，点开看全文

@Composable
private fun ToolMsg(m: ChatMsg, onOpen: () -> Unit) {
    val first = m.content.lineSequence().firstOrNull()?.take(80) ?: ""
    //工具卡片跟 AI 气泡同宽：左边让出头像占的 34dp，右边不再留缺口
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (Cfg.avatarOn) 34.dp else 0.dp)
            .background(SurfaceWhite, bubbleShape(12.dp))
            .border(
                1.dp,
                if (m.isError) DangerRed.copy(alpha = 0.35f) else BorderGray,
                bubbleShape(12.dp)
            )
            .tap { onOpen() }
            .padding(horizontal = bubblePadH(10.dp), vertical = bubblePadV(10.dp))
            ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(
                        if (m.isError) DangerRedSoft else AccentBlueSoft,
                        RoundedCornerShape(7.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    toolGlyph(LocalContext.current, m.toolName),
                    fontSize = 11.sp,
                    color = if (m.isError) DangerRed else AccentBlue
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Tools.label(m.toolName ?: ""),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    if (first.isBlank()) stringResource(R.string.cs_no_output) else first,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            Text(
                if (m.isError) stringResource(R.string.cs_fail) else stringResource(R.string.cs_done),
                fontSize = 10.sp,
                color = if (m.isError) DangerRed else OkGreen
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cs_detail), fontSize = 11.sp, color = AccentBlue)
        }
    }
} //工具结果：点开看详情弹窗

/** 工具类型单字标记，省得再造一堆矢量图 */
private fun toolGlyph(ctx: Context, name: String?): String = when (name) {
    Tools.READ -> ctx.getString(R.string.cs_op_read)
    Tools.WRITE -> ctx.getString(R.string.cs_op_write)
    Tools.MAKE_DIR -> ctx.getString(R.string.cs_op_create)
    Tools.DELETE -> ctx.getString(R.string.cs_op_delete)
    Tools.WEB_SEARCH -> ctx.getString(R.string.cs_op_search)
    else -> ctx.getString(R.string.cs_op_shell)
}

/** 从历史里的 assistant.tool_calls 反查本次调用的参数 */
private fun argsOf(m: ChatMsg): String? {
    val id = m.toolCallId ?: return null
    return Agent.messages
        .lastOrNull { it.role == "assistant" && it.toolCalls.any { c -> c.id == id } }
        ?.toolCalls?.firstOrNull { it.id == id }?.arguments
}

@Composable
private fun ToolDetailDialog(m: ChatMsg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val args = remember(m.id, m.toolCallId) { argsOf(m) }
    var copied by remember(m.id) { mutableStateOf(false) }
    ScrimCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Tools.label(m.toolName ?: ""),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            if (m.isError) Tag(stringResource(R.string.cs_fail), DangerRed, DangerRedSoft) else Tag(stringResource(R.string.cs_done), OkGreen, SurfaceMuted)
        }
        Spacer(Modifier.height(10.dp))
        if (!args.isNullOrBlank()) {
            Text(stringResource(R.string.cs_params), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    prettyJson(args),
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(stringResource(R.string.cs_out_chars, m.content.length), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .background(SurfaceMuted, RoundedCornerShape(10.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (m.content.isBlank()) stringResource(R.string.cs_no_output) else m.content,
                fontSize = 11.sp,
                color = if (m.isError) DangerRed else TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (copied) stringResource(R.string.cs_copied) else stringResource(R.string.cs_copy_output),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap {
                        runCatching {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("bai", m.content))
                        }
                        copied = true
                    }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.cs_close),
                fontSize = 12.sp,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlue, RoundedCornerShape(10.dp))
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //工具详情弹窗

private fun prettyJson(raw: String): String =
    runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw)

@Composable
private fun SummaryMsg(m: ChatMsg) {
    var open by remember(m.id) { mutableStateOf(false) }
    val body = m.content.replace(Regex("""\[\[upto:\d+]]"""), "").trim()
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceMuted, bubbleShape(12.dp))
            .tap { open = !open }
            .padding(horizontal = bubblePadH(10.dp), vertical = bubblePadV(10.dp))
            ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cs_summary), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cs_summary_done), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(if (open) stringResource(R.string.cs_collapse) else stringResource(R.string.cs_expand), fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (open) body else body.take(110) + if (body.length > 110) "…" else "",
            fontSize = 11.sp,
            color = TextPrimary
        )
    }
} //上下文摘要卡片

/** 长按菜单里发起的输入请求：edit=true 编辑重发，false 带提示重新生成 */
private data class PromptReq(val m: ChatMsg, val edit: Boolean)

private fun copyText(ctx: Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("bai", text))
    }
} //复制到剪贴板

@Composable
private fun MsgMenuDialog(
    m: ChatMsg,
    onEdit: () -> Unit,
    onRegen: () -> Unit,
    onRegenWith: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    //删除不可恢复：第一下只是把按钮变成「确认删除」，第二下才真删
    var del by remember(m.id) { mutableStateOf(false) }
    ScrimCard(onDismiss = onClose) {
        Text(
            if (m.role == "user") stringResource(R.string.cs_this_msg) else stringResource(R.string.cs_this_reply),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 96.dp)
                .background(SurfaceMuted, RoundedCornerShape(10.dp))
                .padding(9.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (m.content.isBlank()) stringResource(R.string.cs_no_content) else m.content,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))
        if (m.role == "user") {
            MenuRow(stringResource(R.string.cs_edit_resend), stringResource(R.string.cs_edit_resend_desc), onEdit)
        } else {
            MenuRow(stringResource(R.string.cs_regen), stringResource(R.string.cs_regen_desc), onRegen)
            MenuRow(stringResource(R.string.cs_regen_hint2), stringResource(R.string.cs_regen_hint_desc2), onRegenWith)
        }
        MenuRow(stringResource(R.string.cs_copy_content), stringResource(R.string.cs_copy_content_desc), onCopy)
        MenuRow(
            if (m.starred) stringResource(R.string.cs_star_off) else stringResource(R.string.cs_star),
            if (m.starred) stringResource(R.string.cs_star_off_desc) else stringResource(R.string.cs_star_desc),
            onStar
        )
        if (m.content.isNotBlank()) {
            MenuRow(stringResource(R.string.cs_speak), stringResource(R.string.cs_speak_desc)) { onSpeak() }
        }
        MenuRow(
            if (del) stringResource(R.string.cs_del_msg_confirm) else stringResource(R.string.cs_del_msg),
            stringResource(R.string.cs_del_msg_desc)
        ) {
            if (del) onDelete() else del = true
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.cs_cancel),
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceMuted, RoundedCornerShape(10.dp))
                .tap { onClose() }
                .padding(vertical = 11.dp)
        )
    }
} //长按消息的操作菜单

@Composable
private fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(SurfaceMuted, RoundedCornerShape(10.dp))
            .tap { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(title, fontSize = 13.sp, color = TextPrimary)
        Text(sub, fontSize = 10.sp, color = TextSecondary)
    }
} //菜单里的一行

@Composable
private fun PromptDialog(
    title: String,
    hint: String,
    initial: String,
    confirm: String,
    onClose: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    ScrimCard(onDismiss = onClose) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(hint, fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 220.dp)
                .background(SurfaceMuted, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                cursorBrush = SolidColor(AccentBlue),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.cs_cancel),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                confirm,
                fontSize = 12.sp,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (text.isBlank()) SurfaceMuted else AccentBlue, RoundedCornerShape(10.dp))
                    .tap { if (text.isNotBlank()) onConfirm(text.trim()) }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //编辑重发 / 带提示重生成的输入弹窗