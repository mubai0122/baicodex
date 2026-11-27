/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:20
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.chat.main

import b.ai.ui.screen.chat.at.*
import b.ai.ui.screen.chat.dialog.*
import b.ai.ui.screen.chat.list.*
import b.ai.ui.screen.chat.sheet.*
import b.ai.ui.widget.longPress
import b.ai.ui.widget.tapLong
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.glass.CardBackdropHolder
import b.ai.ui.glass.GlassPanel
import b.ai.ui.glass.LocalCardBackdrop
import b.ai.ui.glass.TactileChip
import b.ai.ui.glass.TactileCircle
import b.ai.ui.input.CfgPanel
import b.ai.ui.input.ModelPanel
import b.ai.ui.input.SheetHead
import b.ai.ui.input.SheetShell
import b.ai.ui.input.SkillPanel
import b.ai.ui.input.TagPanel
import b.ai.ui.screen.pet.ModelIntroDialog
import b.ai.ui.screen.pet.PetInApp
import b.ai.ui.screen.settings.perm.A11yPermDialog

import b.ai.ui.widget.AvatarView
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.FileChips
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcBack
import b.ai.ui.widget.IcClose
import b.ai.ui.widget.IcCode
import b.ai.ui.widget.IcCopy
import b.ai.ui.widget.IcCube
import b.ai.ui.widget.IcExpand
import b.ai.ui.widget.IcWand
import b.ai.ui.widget.IcGrid
import b.ai.ui.widget.IcLayers
import b.ai.ui.widget.IcMenu
import b.ai.ui.widget.IcKeyboard
import b.ai.ui.widget.EdgeGlow
import b.ai.ui.widget.IcMic
import b.ai.ui.widget.IcPencil
import b.ai.ui.widget.IcPlus
import b.ai.ui.widget.IcSearch
import b.ai.ui.widget.IcSend
import b.ai.ui.widget.IcSpark
import b.ai.ui.widget.IcWandToCheck
import b.ai.ui.widget.IcStopSquare
import b.ai.ui.widget.IcTap
import b.ai.ui.widget.IcTrain
import b.ai.ui.widget.ImgFull
import b.ai.ui.widget.ImgThumbs
import b.ai.ui.widget.ImgView
import b.ai.ui.widget.ImgViewerDialog
import b.ai.ui.widget.MdText
import b.ai.ui.widget.ScrollDownButton
import b.ai.ui.widget.Tag
import b.ai.ui.widget.ToolIcon
import b.ai.ui.dialog.BottomSheetCard
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.LocalPageBackdrop
import b.ai.ui.glass.ProvideCardBackdrop
import b.ai.ui.glass.backdropPageInset
import b.ai.ui.glass.drawPageBg
import b.ai.ui.glass.glassPageLayer
import b.ai.ui.glass.glassLazyListState
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.glass.pageBg
import b.ai.ui.input.AttachPanel
import b.ai.ui.overlay.Ime
import b.ai.core.cfg.io.Drafts
import b.ai.ui.screen.settings.agent.IntentInbox
import b.ai.ui.widget.tap
import b.ai.net.api.llm.text
import b.ai.net.api.llm.OptResult
import b.ai.net.api.llm.PromptOptimizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import b.ai.agent.run.Agent
import b.ai.agent.run.RunningTool
import b.ai.agent.tool.Tools
import b.ai.core.cfg.app.AgentMode
import b.ai.core.media.img.Avatar
import b.ai.core.data.io.Attach
import b.ai.core.data.card.Cards
import b.ai.core.cfg.app.Cfg
import b.ai.core.pet.balance.BalWatch
import b.ai.core.pet.balance.PetBalance
import androidx.compose.ui.draw.clip
import b.ai.core.model.chat.ChatMsg
import b.ai.core.media.img.Images
import b.ai.core.model.chat.MsgMeta
import b.ai.core.data.io.Pastes
import b.ai.agent.persona.Personas
import b.ai.core.data.db.Repo
import b.ai.core.data.profile.Profiles
import b.ai.core.plugin.skill.Skills
import b.ai.core.model.voice.Speak
import b.ai.core.model.voice.Audio
import b.ai.core.model.voice.Voice
import b.ai.device.fs.Diff
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
import b.ai.ui.theme.LocalUserFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 输入框占位提示候选：设置→外观 里填多行就每行一条随机轮换；没填就是「请输入问题」 */
internal fun hintPool(ctx: Context): List<String> {
    val custom = Cfg.hintText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return custom.ifEmpty { listOf(ctx.getString(R.string.cs_hint)) }
} //占位提示候选

/** 对话主界面 */
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: (Int) -> Unit = {},
    //工作区绑定已经改成独立页面，这里只发跳转信号
    onOpenWorkspace: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    //待发送附件在下面声明，这里先留一个「草稿载入完成」的标记，避免载入前就被写回覆盖
    var draftReady by remember { mutableStateOf(false) }
    //随机 placeholder：候选来自设置（默认「请输入问题」），发送后重抽一次
    val hintWords = hintPool(LocalContext.current)
    var hint by remember(hintWords) { mutableStateOf(hintWords.random()) }
    //错误回复「去配置」：缺模型去模型页（0），缺权限去外观页的自查项（2），缺环境去扩展页（3）
    val fixTo: (String) -> Unit = { kind ->
        onOpenSettings(
            when (kind) {
                Agent.ERR_MODEL -> 0
                Agent.ERR_PERM -> 2
                else -> 3
            }
        )
    }
    var fullInput by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ChatMsg?>(null) }
    var menuFor by remember { mutableStateOf<ChatMsg?>(null) }
    //「复制部分内容」的全文面板：跟操作菜单分开，菜单关掉它才出现
    var copyPartFor by remember { mutableStateOf<ChatMsg?>(null) }
    //摘要卡片的菜单：重新总结 / 加指令重总结；sumNote 为真时弹指令输入框
    var sumMenu by remember { mutableStateOf<ChatMsg?>(null) }
    var sumNote by remember { mutableStateOf(false) }
    var promptReq by remember { mutableStateOf<PromptReq?>(null) }
    //上下文用量小卡片：原来走根弹窗层是一张居中大卡，现在改成贴顶栏预算环的小卡片
    var ctxOpen by remember { mutableStateOf(false) }
    //预算环在根坐标系里的右下角，小卡片按它锚定
    var ctxRingX by remember { mutableIntStateOf(0) }
    var ctxRingY by remember { mutableIntStateOf(0) }
    //信息面板只允许开一个：六个布尔量各自为政会让「点模型却弹出运行模式」这类串台必然发生
    var panel by remember { mutableStateOf(InfoPanel.None) }
    //面板锚点：只在点下去那一刻写入，绝不能在布局回调里写，否则页面一重排就会把锚点冲掉
    var panelAnchor by remember { mutableStateOf(Rect.Zero) }
    //顶栏角色胶囊自己的矩形：布局回调只写这一份，点它时再搬进 panelAnchor
    var rPersona by remember { mutableStateOf(Rect.Zero) }
    //整页在根坐标系里的上下边（像素）：面板往上顶 / 往下翻都按这两个值算，容器不在 (0,0) 也不会偏
    var rootTop by remember { mutableIntStateOf(0) }
    var rootBottom by remember { mutableIntStateOf(0) }
    //输入区实测高度：点空白收起的遮罩只铺到输入区上沿，胶囊还要能继续点
    var inputH by remember { mutableIntStateOf(0) }
    //附件分类：同一个选择器按这里的 mime 与标签区分图片 / 音频 / 视频 / 音乐
    var attachKind by remember { mutableStateOf(ctx.getString(R.string.cs_attach)) }
    var pendingImgs by remember { mutableStateOf<List<String>>(emptyList()) }
    //待发送文件：选文件 / 超长粘贴进来的，发送前就挂在输入区，跟选完文件是一个形态
    var pendingFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    //会话切换：把输入框换成这个会话自己的草稿（drafts/<sid>.prkcovs），切走再回来不丢
    LaunchedEffect(Agent.sessionId) {
        val sid = Agent.sessionId.orEmpty()
        draftReady = false
        Drafts.sweep()
        val d = withContext(Dispatchers.IO) { Drafts.load(sid) }
        input = d.text
        pendingImgs = d.images
        pendingFiles = d.files
        draftReady = true
    }
    //停手 0.8 秒才落盘，避免每敲一个字都写一次文件
    LaunchedEffect(input, pendingImgs, pendingFiles, Agent.sessionId) {
        val sid = Agent.sessionId ?: return@LaunchedEffect
        if (!draftReady) return@LaunchedEffect
        delay(800)
        Drafts.save(sid, input, pendingImgs, pendingFiles)
    }
    //滑动联动：滑动期间全站玻璃降成平涂，停手 180ms 再升回来（气泡一屏五条，每条一次离屏必掉帧）
    val listState = glassLazyListState()
    val scope = rememberCoroutineScope()
    //用户是不是停在底部：以列表真实布局为准（最后一条可见、且底部余量 <24dp），不用「消息数」猜
    val den = LocalDensity.current
    val bottomSlack = with(den) { 24.dp.roundToPx() }
    val atBottom by remember(Agent.sessionId, bottomSlack) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            info.totalItemsCount == 0 ||
                (last != null && last.index >= info.totalItemsCount - 1 &&
                    last.offset + last.size - info.viewportEndOffset <= bottomSlack)
        }
    }
    //是否跟随贴底：滚动停下来时按落点决定（落到底部就继续跟，翻到上面就停跟）；
    //发送消息、点「回到底部」都会把它强制拉回 true
    var follow by remember(Agent.sessionId) { mutableStateOf(true) }
    //底部面板的采样源：录制整页内容；面板自己浮在页面外，不会采到自己
    val (pageTop, pageBottom) = backdropPageInset()
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        //采样层必须含真背景：只垫纯色的话，玻璃在没内容的地方采到的就是一块灰白
        drawPageBg(offsetY = -pageTop, fullHeight = size.height + pageTop + pageBottom)
        drawContent()
    }
    //卡片玻璃的采样源：纯背景一层，不能含消息卡自己，否则采到上一帧的自己
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }
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
    //任意文件：复制进私有目录，直接挂成待发送附件，模型自己用 read_file 看
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val p = withContext(Dispatchers.IO) { Attach.importFrom(ctx, uri) }
                if (p != null && p !in pendingFiles) pendingFiles = pendingFiles + p
            }
        }
    }

    //语音模式：点语音按钮把输入行整条换成「长按说话」，语音位同时换成键盘位；开关状态记忆在 Cfg
    var voiceMode by remember { mutableStateOf(Cfg.voiceInput) }
    //长按光效进度：按下 0→1 沿边框走一圈，松手回 0
    val glow = remember { Animatable(0f) }

    //发完就收起键盘：输入法长时间占着屏幕下部，正文可见区就只剩一条
    val kbCtl = LocalSoftwareKeyboardController.current
    val focusMgr = LocalFocusManager.current

    //把当前输入与附件发出去：手打和语音识别回来走同一条路
    fun sendNow(extra: String = "") {
        val t = (input + extra).trim()
        val imgs = pendingImgs
        val fs = pendingFiles
        if (t.isEmpty() && imgs.isEmpty() && fs.isEmpty()) return
        input = ""
        pendingImgs = emptyList()
        pendingFiles = emptyList()
        hint = hintWords.random()
        fullInput = false
        kbCtl?.hide()
        focusMgr.clearFocus()
        val fallback = if (imgs.isNotEmpty()) ctx.getString(R.string.cs_see_images)
        else ctx.getString(R.string.cs_see_files)
        //发出去就把「跟随贴底」拉回来：紧接着的消息增长会自己把列表滚到最新一条
        follow = true
        //发出去就把这轮的草稿清掉，切回来不该再看见已经发过的内容
        Agent.sessionId?.let { Drafts.clear(it) }
        Agent.send(if (t.isEmpty()) fallback else t, imgs, fs)
    } //发送当前输入

    //长按开始：点火光效 + 开听；识别回来的文本直接发出去
    fun holdStart() {
        Voice.holdStart(ctx) { t -> if (t.isNotBlank()) sendNow(t) }
        scope.launch { glow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    } //长按说话

    //松手：上滑取消就丢掉这段，否则等识别结果自动发送；光效一起退场
    fun holdEnd(cancelled: Boolean) {
        if (cancelled) Voice.holdCancel() else Voice.holdEnd()
        scope.launch { glow.animateTo(0f, tween(200)) }
    } //松手结束

    //定时任务通知点开：把内容填进输入框，等用户看过再发
    LaunchedEffect(IntentInbox.prompt) {
        val p = IntentInbox.prompt ?: return@LaunchedEffect
        IntentInbox.prompt = null
        input = p
    }

    //进会话只跳到底一次，不要动画：以前每次消息数变化都 animate，分页铺开加流式会让动画反复重启，看着就是疯狂滚动
    LaunchedEffect(Agent.sessionId, Agent.focusMsg) {
        //有落点（收藏夹跳过来的那条）时别贴底，交给下面的定位 effect
        if (Agent.focusMsg != null) return@LaunchedEffect
        //等这一帧把列表铺出来，否则 totalItemsCount 还是 0，跳了个空
        withFrameNanos { }
        val last = Agent.messages.size - 1
        if (last >= 0) listState.scrollToItem(last, Int.MAX_VALUE / 2)
        follow = true
    }
    //滚动落点决定是否继续跟随：程序自己贴底也会走这里，落到尾部就还是 true，不影响
    LaunchedEffect(Agent.sessionId) {
        var began = false
        snapshotFlow { listState.isScrollInProgress }.collect { on ->
            if (on) {
                //手指一动手就暂停跟随：正在翻历史时被拽回底部是最烦的那种抢
                follow = false
                began = true
            } else if (began) {
                began = false
                follow = atBottom //落到底部就恢复跟随
            }
        }
    }
    //内容增长贴底：新消息落库 / 流式字变长 / 工具卡片插入都算；节流 100ms，别每帧重新摆放列表
    LaunchedEffect(Agent.sessionId, follow) {
        if (!follow) return@LaunchedEffect
        snapshotFlow {
            //三路拼成一个数，任何一路动了都会 emit
            Agent.messages.size * 1_000_000L + Agent.streamText.length + Agent.runningTools.size * 10_000_000L
        }
            .sample(100)
            .collect {
                val n = listState.layoutInfo.totalItemsCount
                //贴底用瞬移不用动画：流式期间每 100ms 一段动画会互相打架，看着就是抽搐
                if (n > 0) listState.scrollToItem(n - 1, Int.MAX_VALUE / 2)
            }
    }

    //1.7.0 起只提示一次「全新模型配置」；旧的桌宠引导属于 1.6.0，不再反复弹
    var petIntro by remember { mutableStateOf(!Cfg.petModelTip) }
    //输入区上方的内联面板：+ / 三条横线 / 技能包，跟 @ 面板同一位置
    //键盘兜底已下沉到 Root 的根容器，这里不再重复补（重复补会多留一段空白）
    val panelOpen = panel != InfoPanel.None
     //只在消息列表变了才重算上下文占用，免得输入框每敲一个字都扫一遍全量消息；顶栏和小卡片共用
     val cst = remember(Agent.messages) { ctxStat(Agent.messages) }
    //外层 Box 只为了给「应用内桌宠」一个右下角锚点
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            val r = it.boundsInRoot()
            rootTop = r.top.roundToInt()
            rootBottom = r.bottom.roundToInt()
        }
    ) {
    //纯背景一层，卡片玻璃只采它
    Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
    ProvideCardBackdrop(cardBackdrop) {
//safeDrawing = 状态栏/导航条/输入法取并集，只留一份底部间距，不会和 IME 叠出空白
    //底部键盘不在这里留白：safeDrawing 含 ime，键盘动画期间会让整页（含背景玻璃层）每帧重排
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).glassPageLayer(pageBackdrop)) {
         TopBar(
             onOpenDrawer,
             onOpenWorkspace = onOpenWorkspace,
             onPersona = { panelAnchor = rPersona; panel = if (panel == InfoPanel.Persona) InfoPanel.None else InfoPanel.Persona },
            onPersonaPos = { rPersona = it },
             onOpenCtx = { ctxOpen = true },
             onCtxPos = { x, y -> ctxRingX = x; ctxRingY = y },
             ctxTok = cst[0],
             ctxFreshCount = cst[1],
             ctxFreshTok = cst[2]
         )

        //面板开着时返回键先收面板，别直接退出应用
        BackHandler(enabled = panelOpen) {
            panel = InfoPanel.None
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                //面板开着时点聊天区空白就收起；detectTapGestures 只在没拖动时触发，列表照常滚
                .then(
                    if (panelOpen) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures {
                                panel = InfoPanel.None
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
                //进会话只铺最近 12 轮：往上滑或点顶部胶囊再放一批，别一进来就把整段历史排版一遍
                var shown by remember(Agent.sessionId) { mutableIntStateOf(ROUND_PAGE) }
                val rounds = remember(Agent.messages) { roundsOf(Agent.messages) }
                //总结卡不占轮次，永远贴在最上面
                val sums = remember(Agent.messages) { Agent.messages.filter { it.role == "summary" } }
                val head = (rounds.size - shown).coerceAtLeast(0)
                //卡片样式：0=分式按条铺，1=整体式按轮铺（一轮一张卡，见 RoundCard）
                val whole = Cfg.chatCardStyle == 1
                val flat = remember(rounds, head) {
                    if (head == 0) rounds.flatten() else rounds.drop(head).flatten()
                }
                val visRounds = remember(rounds, head) {
                    if (head == 0) rounds else rounds.drop(head)
                }
                //滑到最顶自动再放一批；列表项都有稳定 key，往前面插内容不会把当前可见位置顶跑
                LaunchedEffect(Agent.sessionId, head) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (i, off) -> if (i == 0 && off == 0 && head > 0) shown += ROUND_PAGE }
                }
                //收藏夹跳转落点：把目标消息滚到可见位置。落在还没铺开的历史里就先多放一批，effect 会随 head 重跑
                LaunchedEffect(Agent.sessionId, Agent.focusMsg, head, whole) {
                    val fid = Agent.focusMsg ?: return@LaunchedEffect
                    //消息已经不在当前列表（被压缩/删除）：直接丢掉落点，别一直挂着
                    if (Agent.messages.none { it.id == fid }) {
                        Agent.clearFocusMsg()
                        return@LaunchedEffect
                    }
                    val reachable = if (whole) visRounds.any { r -> r.any { it.id == fid } }
                    else flat.any { it.id == fid }
                    if (!reachable) {
                        //还有更早的轮次没铺开：再放一批就重进这个 effect
                        if (head > 0) shown += ROUND_PAGE else Agent.clearFocusMsg()
                        return@LaunchedEffect
                    }
                    //等一帧让 LazyColumn 把这一批真的铺出来，否则 index 是旧布局的
                    withFrameNanos { }
                    val idx = if (whole) visRounds.indexOfFirst { r -> r.any { it.id == fid } }
                    else flat.indexOfFirst { it.id == fid }
                    if (idx < 0) {
                        Agent.clearFocusMsg()
                        return@LaunchedEffect
                    }
                    //列表头部的固定项：总结卡 + （还有更早轮次时的胶囊）
                    val lead = sums.size + if (head > 0) 1 else 0
                    //落到历史里就不再跟着最新一条跑，否则贴底逻辑会立刻把位置抢回去
                    follow = false
                    listState.animateScrollToItem((lead + idx).coerceAtLeast(0))
                    Agent.clearFocusMsg()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    //总结卡始终置顶：它压住的是更早那段上下文，跟着一起折叠就再也看不到了
                    items(sums, key = { "sum" + it.id }) { m ->
                        Column(Modifier.fillMaxWidth()) {
                            MessageItem(
                                m,
                                onToolDetail = { detail = it },
                                onLongPress = { menuFor = it },
                                onSumLong = { sumMenu = it },
                                onFix = fixTo
                            )
                        }
                    }
                    //还有更早的轮次没铺开时，最上面中间给一颗胶囊，点它多放一批
                    if (head > 0) {
                        item("more-rounds") {
                            MoreRoundsPill(stringResource(R.string.cs_more_rounds, head)) {
                                shown += ROUND_PAGE
                            }
                        }
                    }
                    //整体式：一轮一张卡。key 用轮首条 id，插新内容不会把已有轮重建
                    if (whole) {
                        items(visRounds, key = { "r" + it.first().id }) { r ->
                            RoundCard(
                                round = r,
                                onToolDetail = { detail = it },
                                onLongPress = { menuFor = it },
                                onSumLong = { sumMenu = it },
                                onFix = fixTo
                            )
                        }
                    } else {
                    //key 用消息 id：不给 key 时列表一更新就整段重新组合，滚动也跟着抖
                    items(flat, key = { it.id }) { m ->
                        BubbleBounce(m.id, m.role == "user") {
                        Column(Modifier.fillMaxWidth()) {
                            //重发 / 重新生成长出来的分支：同父有几条就在这条上方给 ‹ n/m ›，靠向自己那一侧
                            if (m.bTotal > 1) {
                                BranchBar(m.bIdx, m.bTotal, m.role == "user") { d -> Agent.switchBranch(m.id, d) }
                            }
                            MessageItem(
                                m,
                                onToolDetail = { detail = it },
                                onLongPress = { menuFor = it },
                                onSumLong = { sumMenu = it },
                                onFix = fixTo
                            )
                        }
                        }
                    }
                    }
                    //工具执行中的转圈卡：模型一返回调用就出现，执行完才换成正常结果行
                    if (Agent.runningHere()) {
                        Agent.runningTools.forEach { rt ->
                            item("run-" + rt.seq) { RunningCard(rt) }
                        }
                    }
                    //流式增量：边收边渲染，落库前就看得见字在长
                    if (Agent.busy && Agent.runningHere() && (Agent.streamText.isNotEmpty() || Agent.streamReason.isNotEmpty())) {
                        item("live") { StreamingMsg(Agent.streamText, Agent.streamReason) }
                    }
                    //分句间隙的「对方正在输入…」：只在这一条已发完、下一条还没到时出现
                    if (Agent.typing && Cfg.waifuMode && Cfg.waifuTyping) {
                        item("typing") { TypingBubble() }
                    }
                }
                //回到底部：翻在历史里时浮在右下角，点它带动画跳回最新一条；
                //AI 进行中时圆钮外圈转一道主题色光弧（见 ScrollDownButton），配色三档走 Cfg.scrollBtnStyle
                if (!atBottom) {
                    ScrollDownButton(
                        busy = Agent.busy,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            //外框比圆钮大一圈（环的留白），padding 相应收小，视觉位置与改造前一致
                            .padding(end = 10.dp, bottom = 6.dp)
                    ) {
                        follow = true
                        scope.launch {
                            val n = listState.layoutInfo.totalItemsCount
                            if (n > 0) listState.animateScrollToItem(n - 1)
                        }
                    }
                }
            }
        }

        //dsh 引擎标记：开着就常驻一条，点一下当场关掉（关掉后下一轮走自家循环）
        if (Cfg.dshEngine) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dh_chat_badge),
                    fontSize = 11.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .background(AccentBlueSoft, RoundedCornerShape(8.dp))
                        .tap { Cfg.dshEngine = false }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
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
        //输入区上方：+ / 三条横线的内联面板不再挤在 Column 里，改成页面外浮层（见 Column 之后），才能采到真背景


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
                val msg = execSlash(cmd, ctx, onOpenSettings, persona = { panel = InfoPanel.Persona })
                if (msg.isNotEmpty()) hint = msg
            }
        }

        //左下角 chip 显示当前配置名；没配过就退回模型名
        val modelLabel = Profiles.list.firstOrNull { it.id == Cfg.profileId }?.label
            ?: Cfg.model.ifBlank { stringResource(R.string.cs_pick_model) }
        //不上报 ime 的 ROM（鸿蒙 4 这类）靠 fallback 补高度；官方上报时它恒为 0
        val fb = with(LocalDensity.current) { Ime.fallback.toDp() }
        //发送队列：发送中又敲进去的内容排在这儿，上一轮收工按序自动发
        if (Agent.queue.isNotEmpty()) {
            QueueBar(
                items = Agent.queue,
                onEdit = { id ->
                    //取回编辑：从队列摘掉，正文与附件原样放回输入区
                    Agent.pullQueued(id)?.let { q ->
                        input = q.text
                        pendingImgs = pendingImgs + q.images.filterNot { it in pendingImgs }
                        pendingFiles = pendingFiles + q.files.filterNot { it in pendingFiles }
                    }
                },
                onSendNow = { Agent.promoteQueued(it) },
                onDrop = { Agent.dropQueued(it) }
            )
        }
        //键盘留白必须走布局：graphicsLayer 平移只改绘制位置，会把列表底和输入区之间裂出一条空白
        //成本已由「无模糊不进离屏」压下去了，这里不再为省一次 measure 牺牲对齐
        Box(Modifier.fillMaxWidth().padding(bottom = fb).imePadding().onSizeChanged { inputH = it.height }) {
        InputBar(
            input = input,
            hint = hint,
            modelLabel = modelLabel,
            modeLabel = stringResource(Cfg.agentMode.labelRes),
            onOpenMode = { panel = if (panel == InfoPanel.Mode) InfoPanel.None else InfoPanel.Mode },
            onChange = { v -> input = pasteGuard(input, v) { p -> pendingFiles = pendingFiles + p } },
            images = pendingImgs,
            files = pendingFiles,
            //三条杠 / 加号 / 模型都能再点一次关掉；面板状态只有一个字段，天然互斥
            onOpenConfig = { panel = if (panel == InfoPanel.Cfg) InfoPanel.None else InfoPanel.Cfg },
            onOpenAttach = { panel = if (panel == InfoPanel.Attach) InfoPanel.None else InfoPanel.Attach },
            onOpenModel = { panel = if (panel == InfoPanel.Model) InfoPanel.None else InfoPanel.Model },
            onExpand = { fullInput = true },
            onRemoveImage = { p ->
                pendingImgs = pendingImgs - p
                Images.delete(p)
            },
            onRemoveFile = { p ->
                pendingFiles = pendingFiles - p
                //粘贴转存的文件跟着一起清；用户自己选的文件不动原件
                if (p.startsWith(Pastes.dir().absolutePath)) Pastes.forget(p)
            },
            voiceMode = voiceMode,
            onToggleVoice = { voiceMode = !voiceMode; Cfg.voiceInput = voiceMode },
            onAnchor = { panelAnchor = it },
            onHoldStart = { holdStart() },
            onHoldEnd = { c -> holdEnd(c) },
            onSend = { sendNow() }
        )
        }
    }

    //底部面板：跟预算卡同一套跟手逻辑；横向贴被点胶囊，纵向顶在它上沿，太靠上（顶栏角色胶囊）就翻到它下面
    val den = LocalDensity.current
    val inputDp = with(den) { inputH.toDp() }
    val scrW = with(den) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    //卡片宽度：左右各留 12dp 撑到屏宽，只有很宽的屏幕才封顶 480dp
    val fullW = scrW - with(den) { 24.dp.toPx() }
    val cardW = minOf(with(den) { 480.dp.toPx() }, fullW)
    //右侧那排（三条杠 / 加号）右边缘对齐，左侧那排（模型 / 模式）左边缘对齐
    val rawX = if (panelAnchor.center.x > scrW / 2f) panelAnchor.right - cardW else panelAnchor.left
    //撑满屏宽时锚点对齐没意义，直接左右各 12dp，跟 SheetShell 的内容内边距对上
    val cardX = if (cardW >= fullW) with(den) { 12.dp.toPx() }
    else rawX.coerceIn(with(den) { 8.dp.toPx() }, (scrW - cardW - with(den) { 8.dp.toPx() }).coerceAtLeast(0f))
    //锚点在容器上半就翻到控件下方，下半屏一律顶在控件上沿，中间留 6dp
    val pageH = (rootBottom - rootTop).toFloat()
    val placeAbove = pageH > 0f && (panelAnchor.top - rootTop) > pageH * 0.4f
    val gapPx = with(den) { 6.dp.toPx() }
    //夹一层保险：padding 一旦超过容器高度，Compose 会把内容顶到最上面，绝不能让它出现这种值
    val maxPad = (pageH - with(den) { 8.dp.toPx() }).coerceAtLeast(0f)
    val padBottomPx = if (placeAbove) (rootBottom - panelAnchor.top + gapPx).coerceIn(0f, maxPad) else 0f
    val padTopPx = if (placeAbove) 0f else (panelAnchor.bottom - rootTop + gapPx).coerceIn(0f, maxPad)
    CompositionLocalProvider(LocalPageBackdrop provides pageBackdrop) {
        //点面板外的空白收起；下沿卡在输入区上，输入区里的胶囊还能继续点
        if (panelOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = inputDp)
                    .tap { panel = InfoPanel.None }
            )
        }
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier
                .align(if (placeAbove) Alignment.BottomStart else Alignment.TopStart)
                .padding(top = with(den) { padTopPx.toDp() }, bottom = with(den) { padBottomPx.toDp() })
                .offset { IntOffset(cardX.roundToInt(), 0) }
                .width(with(den) { cardW.toDp() }),
            enter = fadeIn(tween(90)) + slideInVertically(tween(150)) { it / 2 },
            exit = fadeOut(tween(80)) + slideOutVertically(tween(120)) { it / 2 }
        ) {
            when (panel) {
                InfoPanel.Mode -> ModeCard(onClose = { panel = InfoPanel.None })
                InfoPanel.Attach -> AttachPanel(
                    onClose = { panel = InfoPanel.None },
                    onPickImage = {
                        panel = InfoPanel.None
                        pickImgs.launch("image/*")
                    },
                    onPickFile = { kind, mime ->
                        panel = InfoPanel.None
                        attachKind = kind
                        pickFile.launch(arrayOf(mime))
                    },
                    onSkill = { panel = InfoPanel.Skill },
                    onTag = { panel = InfoPanel.Tag }
                )
                InfoPanel.Tag -> TagPanel(
                    onClose = { panel = InfoPanel.None },
                    onInsert = { txt ->
                        panel = InfoPanel.None
                        input = if (input.isBlank()) txt else "$input\n$txt"
                    }
                )
                InfoPanel.Skill -> SkillPanel(
                    onClose = { panel = InfoPanel.None },
                    onInsert = { txt ->
                        panel = InfoPanel.None
                        input = if (input.isBlank()) txt else "$input\n$txt"
                    }
                )
                InfoPanel.Model -> ModelPanel(
                    onClose = { panel = InfoPanel.None },
                    onOpenSettings = {
                        panel = InfoPanel.None
                        onOpenSettings(0)
                    }
                )
                InfoPanel.Persona -> PersonaPanel(
                    onClose = { panel = InfoPanel.None },
                    onManage = {
                        panel = InfoPanel.None
                        //扩展是第 4 个 tab（索引 3）：模型 / 对话 / 外观 / 扩展 / 统计 / 关于
                        onOpenSettings(3)
                    }
                )
                InfoPanel.Cfg -> CfgPanel(onClose = { panel = InfoPanel.None })
                InfoPanel.None -> Unit
            }
        }
    }

    //工具详情、长按菜单、重发/重生成输入框统一走根弹窗层，卡片本身不会被录进模糊背景
    HostInOverlay(
        detail != null || menuFor != null || promptReq != null || fullInput ||
            sumMenu != null || sumNote || b.ai.device.sys.a11y.A11y.needPerm.value || ImgView.path.value != null ||
            petIntro || copyPartFor != null
    ) {
        val d = detail
        val mn = menuFor
        val sm = sumMenu
        val pr = promptReq
        val img = ImgView.path.value
        when {
            //全屏看图排最前：点开哪儿都能盖住其它面板
            img != null -> ImgViewerDialog(img) { ImgView.close() }
            petIntro -> ModelIntroDialog(
                onClose = {
                    Cfg.petModelTip = true
                    Cfg.petSeen = b.ai.net.api.update.Update.clientCode()
                    petIntro = false
                }
            )
            b.ai.device.sys.a11y.A11y.needPerm.value -> A11yPermDialog { b.ai.device.sys.a11y.A11y.permHandled() }
            fullInput -> FullInputDialog(
                value = input,
                onChange = { v -> input = pasteGuard(input, v) { p -> pendingFiles = pendingFiles + p } },
                images = pendingImgs,
                files = pendingFiles,
                onRemoveImage = { p ->
                    pendingImgs = pendingImgs - p
                    Images.delete(p)
                },
                onRemoveFile = { p ->
                    pendingFiles = pendingFiles - p
                    if (p.startsWith(Pastes.dir().absolutePath)) Pastes.forget(p)
                },
                canSend = !Agent.busy &&
                    (input.isNotBlank() || pendingImgs.isNotEmpty() || pendingFiles.isNotEmpty()),
                onClose = { fullInput = false },
                onSend = {
                    val t = input.trim()
                    val imgs = pendingImgs
                    val fs = pendingFiles
                    if (t.isNotEmpty() || imgs.isNotEmpty() || fs.isNotEmpty()) {
                        input = ""
                        pendingImgs = emptyList()
                        pendingFiles = emptyList()
                        hint = hintWords.random()
                        fullInput = false
                        val fallback = when {
                            imgs.isNotEmpty() -> ctx.getString(R.string.cs_see_images)
                            else -> ctx.getString(R.string.cs_see_files)
                        }
                        Agent.send(if (t.isEmpty()) fallback else t, imgs, fs)
                    }
                }
            )
            d != null -> ToolDetailDialog(d) { detail = null }
            sm != null -> SummaryMenuDialog(
                onRegen = {
                    Agent.recompact()
                    sumMenu = null
                },
                onRegenNote = {
                    sumNote = true
                    sumMenu = null
                },
                onCopy = {
                    copyText(ctx, sm.content.replace(Regex("""\[\[upto:\d+]]"""), "").trim())
                    sumMenu = null
                },
                onClose = { sumMenu = null }
            )
            sumNote -> PromptDialog(
                title = stringResource(R.string.cs_sum_note),
                hint = stringResource(R.string.cs_sum_note_hint),
                initial = "",
                confirm = stringResource(R.string.cs_sum_note_confirm),
                onClose = { sumNote = false },
                onConfirm = { text ->
                    sumNote = false
                    Agent.recompact(text)
                }
            )
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
                onCopyPart = {
                    copyPartFor = mn
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
            copyPartFor != null -> MessageTextSheet(
                m = copyPartFor!!,
                onCopyAll = {
                    copyText(ctx, copyPartFor!!.content)
                    copyPartFor = null
                },
                onClose = { copyPartFor = null }
            )
            pr != null -> PromptDialog(
                title = if (pr.edit) stringResource(R.string.cs_edit_resend) else stringResource(R.string.cs_regen_hint),
                hint = if (pr.edit) stringResource(R.string.cs_edit_desc) else stringResource(R.string.cs_regen_hint_desc),
                initial = if (pr.edit) pr.m.content else "",
                confirm = if (pr.edit) stringResource(R.string.cs_send) else stringResource(R.string.cs_regen),
                onClose = { promptReq = null },
                onConfirm = { text ->
                    promptReq = null
                    if (pr.edit) Agent.editResend(pr.m.id, text, pr.m.images, pr.m.files) else Agent.regenerate(pr.m.id, text)
                }
            )
        }
    }
        //上下文用量小卡片：贴着顶栏预算环弹出，点外面任意处收起
    if (ctxOpen) {
        Box(Modifier.fillMaxSize().tap { ctxOpen = false })
        CtxCard(cst[0], Cfg.ctxTokenBudget, cst[1], cst[2], ctxRingX, ctxRingY)
    }
        //语音长按的边框光效：画在最外层，压在整页之上，只看得到贴着机身边缘的一条
        EdgeGlow(glow.value)
        //应用内桌宠：只在「仅应用内展示」时出现，压在输入区右上，不挡发送键
        PetInApp(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 96.dp))
    }
    }
} //聊天界面