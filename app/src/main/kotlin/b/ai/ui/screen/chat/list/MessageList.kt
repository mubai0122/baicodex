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

package b.ai.ui.screen.chat.list

import b.ai.ui.screen.chat.at.*
import b.ai.ui.screen.chat.dialog.*
import b.ai.ui.screen.chat.main.*
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

/**按轮分组：一条用户消息开启一轮，后面的助手/工具消息都归这一轮；总结卡不占轮次 */
internal fun roundsOf(list: List<ChatMsg>): List<List<ChatMsg>> {
    val out = ArrayList<List<ChatMsg>>()
    var cur = ArrayList<ChatMsg>()
    for (m in list) {
        if (m.role == "summary") continue
        if (m.role == "user" && cur.isNotEmpty()) {
            out.add(cur)
            cur = ArrayList()
        }
        cur.add(m)
    }
    if (cur.isNotEmpty()) out.add(cur)
    return out
} //按轮分组

/**展开更多轮次的胶囊：居中最上面，点一下多铺一批 */
@Composable
internal fun MoreRoundsPill(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .liquidBg(CircleShape, tint = SurfaceMuted, gate = 0.dp)
                .tap { onClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontSize = 11.sp, color = TextSecondary)
        }
    }
} //展开更多轮次胶囊

/**分支切换条：‹ n/m ›，点箭头在同父的几条回答之间翻，翻到哪条 head 就跟到哪条；end=true 时贴右边（用户消息） */
@Composable
internal fun BranchBar(idx: Int, total: Int, end: Boolean, onSwitch: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (end) 0.dp else 42.dp,
                end = if (end) 16.dp else 0.dp,
                top = 1.dp,
                bottom = 1.dp
            ),
        horizontalArrangement = if (end) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BranchArrow("‹") { onSwitch(-1) }
        Text(
            "$idx/$total",
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 3.dp)
        )
        BranchArrow("›") { onSwitch(1) }
    }
} //分支切换条

/** 箭头小块：只留一个透明点击区，按下变蓝，不再套背景方块 */
@Composable
internal fun BranchArrow(glyph: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val press by src.collectIsPressedAsState()
    Box(
        Modifier
            .size(20.dp)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 14.sp, color = if (press) AccentBlue else TextSecondary)
    }
} //分支箭头

/**
 * 「对方正在输入…」占位气泡：分句模式下这条已发出、下一条还在等打字时长时出现。
 * 三个点按 360ms 循环轮流点亮，不做无限动画对象，只用一个相位整数驱动，开销最小。
 */
@Composable
internal fun TypingBubble() {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(360)
            phase = (phase + 1) % 3
        }
    } //三点轮流亮
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (Cfg.avatarOn) {
            AvatarView(Avatar.aiPath(), 28.dp)
            Spacer(Modifier.width(6.dp))
        }
        Row(
            Modifier
                .liquidBg(bubbleShape(14.dp))
                .padding(horizontal = bubblePadH(12.dp), vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.cs_typing), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.width(3.dp))
            repeat(3) { i ->
                Text(
                    "·",
                    fontSize = 14.sp,
                    color = if (i == phase) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                )
            }
        }
    }
} //正在输入占位

/**
 * 分句气泡的弹出动画：只给刚追加的那一条跑一次 spring，历史消息一律静态，
 * 否则进会话会满屏一起弹。
 * 两套手感：
 *   · 普通（Q弹）：0.84 起弹、damping 0.42，会过头一点再落回来，像苹果 Messages；
 *   · 真实引擎：damping 0.78 不回头，配合淡入 + 上浮，更像原生列表插入，不喧宾夺主。
 * 动画值只在 graphicsLayer 的 lambda 里读 —— 走绘制阶段，不触发重组、不重新排版。
 */
@Composable
internal fun BubbleBounce(id: Long, end: Boolean, content: @Composable () -> Unit) {
    val on = Cfg.waifuMode && (Cfg.waifuBounce || Cfg.waifuReal)
    //真实引擎下即使关了「Q弹」也要播：它换的是另一套更克制的动画
    val real = Cfg.waifuMode && Cfg.waifuReal
    //首次组合时就把「这条是不是刚来的」定下来，之后重组不再变，滚动时旧消息不会跟着弹
    val fresh = remember(id) { on && id == Agent.lastAppendId }
    if (!fresh) {
        content()
        return
    }
    val a = remember(id) { Animatable(0f) }
    LaunchedEffect(id) {
        val d = if (real) 0.78f else 0.42f
        a.animateTo(1f, spring(dampingRatio = d, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
    }
    Box(
        Modifier.graphicsLayer {
            val p = a.value
            val s = if (real) 0.96f + 0.04f * p else 0.84f + 0.16f * p
            scaleX = s
            scaleY = s
            //真实引擎带淡入，更像消息刚到达；Q弹模式保持原样不透明
            alpha = if (real) 0.35f + 0.65f * p else 1f
            //从说话人那一侧冒出来：用户贴右下、AI 贴左下
            transformOrigin = TransformOrigin(if (end) 1f else 0f, 1f)
            translationY = (1f - p) * (if (real) 14f else 10f).dp.toPx()
        }
    ) { content() }
} //分句气泡 Q弹

@Composable
internal fun MessageItem(
    m: ChatMsg,
    onToolDetail: (ChatMsg) -> Unit,
    onLongPress: (ChatMsg) -> Unit,
    onSumLong: (ChatMsg) -> Unit,
    /** 错误回复的「去配置」：传 errKind，由调用方决定跳哪一页设置 */
    onFix: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    when (m.role) {
        //用户消息：分式与整体式共用同一个 composer，整体式下样式完全不变
        "user" -> UserMsg(m, onLongPress = onLongPress)

        "tool" -> ToolMsg(m, onOpen = { onToolDetail(m) })
        "summary" -> SummaryMsg(m) { onSumLong(m) }

        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            //AI 头像：设置里选「跟角色卡」就用当前角色那张，否则用自定义图
            if (Cfg.avatarOn) {
                AvatarView(Avatar.aiPath(), 28.dp)
                Spacer(Modifier.width(6.dp))
            }
            //真实分句引擎下 AI 气泡收窄到屏幕的 3/7，更像真人一条条发短消息；普通模式仍占满可用宽度
            val narrow = Cfg.waifuMode && Cfg.waifuReal
            val maxW = (LocalConfiguration.current.screenWidthDp * 3 / 7).dp
            val mt = m.meta
            //外层只负责宽度与统计行；气泡背景/内边距放在内层，统计才能落在框外
            Column(
                Modifier
                    .weight(1f, fill = !narrow)
                    .then(if (narrow) Modifier.widthIn(max = maxW) else Modifier)
            ) {
            Column(
                Modifier
                    .liquidBg(bubbleShape(14.dp))
                    //长按即复制：先进剪贴板，再弹操作菜单
                    .longPress { if (m.content.isNotBlank()) copyText(ctx, m.content); onLongPress(m) }
                    .padding(horizontal = bubblePadH(12.dp), vertical = bubblePadV(12.dp))
                    ) {
                if (m.reasoning.isNotBlank()) ThinkBlock(m)
                if (m.content.isNotBlank()) {
                    MdText(m.content, color = if (m.isError) DangerRed else TextPrimary)
                }
                //错误回复：正文下方挂按钮——重发 / 仍然重发，缺配置类再多一个「去配置」
                if (m.isError) {
                    Spacer(Modifier.height(8.dp))
                    ErrActions(m, onFix)
                }
                if (m.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    //FlowRow 自动换行：Row 里塞满多个 Tag 会把每个都挤成竖排并溢出屏幕
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                //分句模式下统计行不进气泡：这里只在非分句模式画在框内
                if (mt != null && !Cfg.waifuMode && (Cfg.showUsage || Cfg.showMeta)) {
                    Spacer(Modifier.height(6.dp))
                    MetaLine(mt)
                }
                }
                //分句模式：所有分句发完之后，统计行挂在最后一条气泡外面（下方），像聊天时间戳
                if (mt != null && Cfg.waifuMode && (Cfg.showUsage || Cfg.showMeta)) {
                    Spacer(Modifier.height(4.dp))
                    MetaLine(mt)
                }
            }
        }
    }
} //单条消息渲染

/**
 * 整体式聊天卡：一轮（你那句 + 该轮所有 AI 回复 / 思考 / 工具 / 附件 / 统计）收进同一张玻璃卡。
 *
 * 结构：
 *  · 头部：头像 + 你那句的摘要 + 「n 条」+ 展开箭头，整条可点；
 *  · 展开区：你的原话（浅底小块）→ 按到达顺序铺本轮回复与工具 → 尾部附带（统计行、分支切换、错误按钮）；
 *  · 折叠：高度走 animateContentSize 形变，箭头跟着转 180°，折叠后只剩一行头部。
 *
 * 取舍：卡内不再套气泡（不出现「卡里还有气泡」的双层玻璃），内容按段落铺开。
 */
@Composable
internal fun RoundCard(
    round: List<ChatMsg>,
    onToolDetail: (ChatMsg) -> Unit,
    onLongPress: (ChatMsg) -> Unit,
    onSumLong: (ChatMsg) -> Unit,
    onFix: (String) -> Unit
) {
    val user = round.firstOrNull { it.role == "user" }
    val rest = round.filter { it.role != "user" }
    //一轮一张卡：卡头不再放摘要标题 / 条数 / 折叠箭头。
    //之前那行标题是把用户原话又缩了一遍，和卡里的真实气泡重复，所以整行去掉，卡始终展开。
    Column(
        Modifier
            .fillMaxWidth()
            //整体式必须和卡片同一套材质：gate=0 强制玻璃，否则会被 156dp 门槛降成平涂
            .liquidBg(bubbleShape(18.dp), gate = 0.dp)
            .padding(horizontal = bubblePadH(12.dp), vertical = bubblePadV(10.dp))
    ) {
        //你的原话：走和分式完全同一个 UserMsg，样式一字不改
        if (user != null) UserMsg(user, onLongPress = onLongPress, onCard = true)
        //本轮回复左侧统一挂一张 AI 头像（整轮一张，和 ChatGPT 一致；多条消息各挂一张会刷屏）
        val body: @Composable () -> Unit = {
            //本轮所有回复：思考块 / 正文 / 工具入口 / 错误按钮，按到达顺序铺
            rest.forEach { m ->
                when (m.role) {
                    //执行命令在卡内只留一行入口，参数与输出全在弹窗里看
                    "tool" -> ToolLine(m) { onToolDetail(m) }
                    "summary" -> SummaryMsg(m) { onSumLong(m) }
                    else -> RoundAiBody(m, onLongPress = onLongPress, onFix = onFix)
                }
                //重发 / 重新生成后出现的 ‹ n/m › 切换条：贴卡片右下角，跟消息尾巴同一侧
                if (m.bTotal > 1) {
                    BranchBar(m.bIdx, m.bTotal, true) { d -> Agent.switchBranch(m.id, d) }
                }
            }
            //卡片最下方：整轮用量汇总（token 合计 / 耗时 / 模型，只露出一行）
            val metas = rest.mapNotNull { it.meta }
            if (metas.isNotEmpty() && (Cfg.showUsage || Cfg.showMeta)) {
                Spacer(Modifier.height(8.dp))
                MetaLine(mergeMeta(metas))
            }
        }
        //关注头像开关：关掉时行为和之前完全一致，一点位移都没有
        if (Cfg.avatarOn && rest.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                AvatarView(Avatar.aiPath(), 28.dp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) { body() }
            }
        } else {
            body()
        }
    }
} //整体式聊天卡

/**
 * 整体式卡内的一条工具执行：只留一行入口（图标 + 工具名 + 耗时 + 「详情」），
 * 参数与输出全部收进 ToolDetailDialog，不在卡内展开，也不再带 34dp 头像缩进（之前因此偏右）。
 */
@Composable
internal fun ToolLine(m: ChatMsg, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap { onOpen() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .liquidBg(RoundedCornerShape(6.dp), tint = if (m.isError) DangerRedSoft else SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            ToolIcon(m.toolName, if (m.isError) DangerRed else AccentBlue, side = 11.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            Tools.label(m.toolName ?: ""),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (m.isError) {
            Text(stringResource(R.string.cs_fail), fontSize = 9.sp, color = DangerRed)
            Spacer(Modifier.width(6.dp))
        }
        if (m.toolMs > 0) {
            Text(
                msText(m.toolMs),
                fontSize = 9.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(stringResource(R.string.cs_detail), fontSize = 10.sp, color = AccentBlue)
    }
} //卡内工具入口：内容只在弹窗

/** 整轮汇总：把本轮每条回复的统计加起来，token 取和、耗时取和，模型/供应商取最后一条有值的 */
internal fun mergeMeta(list: List<MsgMeta>): MsgMeta {
    if (list.size == 1) return list.first()
    return list.reduce { a, b ->
        a.copy(
            ms = a.ms + b.ms,
            firstMs = if (a.firstMs > 0) a.firstMs else b.firstMs,
            promptTokens = a.promptTokens + b.promptTokens,
            completionTokens = a.completionTokens + b.completionTokens,
            cachedTokens = a.cachedTokens + b.cachedTokens,
            at = if (b.at > 0) b.at else a.at,
            provider = b.provider.ifBlank { a.provider },
            model = b.model.ifBlank { a.model }
        )
    }
} //整轮统计汇总

/** 整体式卡内的一条 AI 回复：不带自己的气泡，只有正文、思考、统计与错误按钮 */
@Composable
internal fun RoundAiBody(m: ChatMsg, onLongPress: (ChatMsg) -> Unit, onFix: (String) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .longPress {
                if (m.content.isNotBlank()) copyText(ctx, m.content)
                onLongPress(m)
            }
    ) {
        if (m.reasoning.isNotBlank()) ThinkBlock(m)
        if (m.content.isNotBlank()) {
            MdText(m.content, color = if (m.isError) DangerRed else TextPrimary)
        }
        if (m.isError) {
            Spacer(Modifier.height(8.dp))
            ErrActions(m, onFix)
        }
        if (m.toolCalls.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
    }
} //卡内 AI 回复

/**
 * 用户消息气泡：靠右、蓝调玻璃底、可选头像。
 * 分式（MessageItem）和整体式（RoundCard）都走这里，保证整体式不改变你发出去那条的样子。
 */
@Composable
internal fun UserMsg(
    m: ChatMsg,
    onLongPress: (ChatMsg) -> Unit,
    /** true = 铺在整体式卡片内部：气泡叠在卡自身的玻璃上，veil 不够就会被吸到看不见 */
    onCard: Boolean = false
) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                //卡内：卡本身就是玻璃，气泡不再叠第二层玻璃。
                //veil 是「乳白/墨黑遮罩」不是不透明度（深色下是 62% 黑），抬高只会压出一层灰影，
                //所以卡内直接用不透明的 AccentBlueSoft 实底，颜色和分式一致且不会被背景吸掉。
                .then(
                    if (onCard) Modifier.background(AccentBlueSoft, bubbleShape(14.dp))
                    else Modifier.liquidBg(bubbleShape(14.dp), tint = AccentBlueSoft)
                )
                //长按即复制：先进剪贴板，再弹操作菜单
                .longPress { if (m.content.isNotBlank()) copyText(ctx, m.content); onLongPress(m) }
                //左右内边距额外加上「全局缩进」
                .padding(
                    horizontal = bubblePadH(12.dp) + Cfg.globalIndent.dp,
                    vertical = bubblePadV(12.dp)
                )
        ) {
            Column {
                if (m.images.isNotEmpty()) {
                    //发出去的图按原图尺寸展示（不裁成小方块），多了横向滑；长图点开看全图
                    ImgFull(images = m.images, maxWidth = 240.dp, maxHeight = 340.dp, onOpen = { ImgView.open(it) })
                    Spacer(Modifier.height(6.dp))
                }
                if (m.files.isNotEmpty()) {
                    //附件文件同样挂在气泡里，看得见自己带了什么
                    FileChips(files = m.files)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    m.content,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontFamily = LocalUserFont.current
                )
            }
        }
        if (Cfg.avatarOn) {
            Spacer(Modifier.width(6.dp))
            AvatarView(Cfg.avatarUser, 28.dp)
        }
    }
} //用户消息气泡

/**消息气泡圆角：开了「消息强制高圆角」就用 16dp 大圆角，否则用常规圆角 */
internal fun bubbleShape(regular: Dp): RoundedCornerShape =
    if (Cfg.bubbleCapsule) RoundedCornerShape(16.dp) else RoundedCornerShape(regular)

/**气泡内边距：大圆角要留余量，否则文字会顶到弧线上 */
internal fun bubblePadH(regular: Dp): Dp = if (Cfg.bubbleCapsule) regular + 4.dp else regular

internal fun bubblePadV(regular: Dp): Dp = if (Cfg.bubbleCapsule) 11.dp else regular
/**回复底部那行小字：用量与请求信息，按设置里的两个开关拼 */
@Composable
internal fun MetaLine(m: MsgMeta) {
    val parts = ArrayList<String>(7)
    if (Cfg.showUsage) {
        parts.add(tokText(m.totalTokens) + " tok")
        //速度：只有真跑出过速度才有得显，单独一项开关
        if (m.speed > 0 && Cfg.showSpeed) parts.add(String.format(Locale.US, "%.0f tok/s", m.speed))
    }
    //元信息总开关关掉时整段细分都不生效，与旧配置（show_meta=false）语义保持一致
    if (Cfg.showMeta) {
        if (m.ms > 0 && Cfg.showLatency) parts.add(stringResource(R.string.cs_elapsed) + secText(m.ms) + "s")
        if (m.firstMs > 0 && Cfg.showFirst) parts.add(stringResource(R.string.cs_first) + secText(m.firstMs) + "s")
        if (m.at > 0 && Cfg.showTime) parts.add(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.at)))
        if (m.provider.isNotBlank() && Cfg.showProvider) parts.add(m.provider)
        if (m.model.isNotBlank() && Cfg.showModel) parts.add(m.model)
    } //元信息细分
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        fontSize = 9.sp,
        lineHeight = 13.sp,
        color = TextSecondary
    )
} //回复元信息

internal fun tokText(n: Int): String =
    if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000.0) else n.toString() //token 数

internal fun secText(ms: Long): String =
    if (ms < 10_000L) String.format(Locale.US, "%.1f", ms / 1000.0) else (ms / 1000L).toString() //秒数

/** 工具执行耗时：固定两位整数 + 三位小数，等宽字体下不会随数值跳动 */
internal fun msText(ms: Long): String = String.format(Locale.US, "%.3f ms", ms / 1000.0)

@Composable
internal fun ThinkBlock(m: ChatMsg) {
    var open by remember(m.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
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
            modifier = if (open) Modifier.heightIn(max = 240.dp).verticalScroll(glassScrollState()) else Modifier
        )
    }
} //思考过程：默认一行，点开看全文

@Composable
internal fun ToolMsg(m: ChatMsg, onOpen: () -> Unit) {
    //写文件类工具的结果尾部带 diff 段，拆出来单独渲染成红绿行
    val split = remember(m.id, m.content) { splitDiff(m.content) }
    val body = split.first
    val diff = split.second
    var diffOpen by remember(m.id) { mutableStateOf(false) }
    val first = summaryLine(body)
    //工具卡片跟 AI 气泡同宽：左边让出头像占的 34dp，右边不再留缺口
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (Cfg.avatarOn) 34.dp else 0.dp)
            .liquidBg(bubbleShape(12.dp))
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
                    .liquidBg(RoundedCornerShape(7.dp)
                    , tint = if (m.isError) DangerRedSoft else AccentBlueSoft),
                contentAlignment = Alignment.Center
            ) {
                ToolIcon(
                    m.toolName,
                    if (m.isError) DangerRed else AccentBlue,
                    side = 13.dp
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
            //失败才挂徽标：成功的卡片不需再确认一次，省出的宽度留给摘要
            if (m.isError) {
                Text(stringResource(R.string.cs_fail), fontSize = 10.sp, color = DangerRed)
                Spacer(Modifier.width(6.dp))
            }
            if (m.toolMs > 0) {
                Text(
                    msText(m.toolMs),
                    fontSize = 9.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(stringResource(R.string.cs_detail), fontSize = 11.sp, color = AccentBlue)
        }
        //有 diff 就多一行「部分修改 +N −M」，点开就地看改动
        if (diff.isNotEmpty()) {
            val plus = diff.count { it.startsWith("+") }
            val minus = diff.count { it.startsWith("-") }
            Row(
                Modifier
                    .fillMaxWidth()
                    .tap { diffOpen = !diffOpen }
                    .padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.cs_part_diff),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
                Text("+$plus", fontSize = 10.sp, color = OkGreen)
                Spacer(Modifier.width(4.dp))
                Text("−$minus", fontSize = 10.sp, color = DangerRed)
                Spacer(Modifier.weight(1f))
                Text(
                    if (diffOpen) stringResource(R.string.cs_collapse) else stringResource(R.string.cs_expand),
                    fontSize = 10.sp,
                    color = AccentBlue
                )
            }
            if (diffOpen) {
                Spacer(Modifier.height(6.dp))
                DiffView(diff)
            }
        }
    }
} //工具结果：点开看详情弹窗，有改动就展开红绿行

/** 摘要首行：跳过「[通道] exit=0」这种纯路由行，并把行内的 exit=N 抹掉 */
internal fun summaryLine(body: String): String {
    for (raw in body.lineSequence()) {
        val t = raw.trim()
        if (t.isEmpty()) continue
        if (ROUTE_EXIT.matches(t)) continue
        return t.replace(EXIT_PART, "").take(80)
    }
    return ""
} //摘要首行
