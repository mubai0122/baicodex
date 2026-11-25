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

@Composable
internal fun TopBar(
    onOpenDrawer: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onPersona: () -> Unit,
    onPersonaPos: (Rect) -> Unit,
    onOpenCtx: () -> Unit,
    onCtxPos: (Int, Int) -> Unit,
    ctxTok: Int,
    ctxFreshCount: Int,
    ctxFreshTok: Int
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //触摸目标提到 40dp：34dp 在窄边距下手指容易落空，图标本身仍是 18dp 不变
        Box(
            Modifier
                .size(40.dp)
                .liquidBg(CircleShape, tint = SurfaceMuted, gate = 0.dp)
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
                        .liquidBg(RoundedCornerShape(10.dp)
                        , tint = if (role == null) SurfaceMuted else AccentBlueSoft, gate = 0.dp)
                        .onGloballyPositioned { onPersonaPos(it.boundsInRoot()) }
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
        //上下文占用环：占加号左边那个位置，点开是贴它下方的小卡片
        CtxRing(tokens = ctxTok, budget = Cfg.ctxTokenBudget, onPos = onCtxPos) { onOpenCtx() }
        Spacer(Modifier.width(8.dp))
        //右上角两个动作：新建会话（加号）、工作区绑定（代码尖括号）
        Box(
            Modifier
                .size(34.dp)
                .liquidBg(CircleShape, tint = SurfaceMuted, gate = 0.dp)
                .tap { Agent.newSession() },
            contentAlignment = Alignment.Center
        ) { IcPlus(TextPrimary) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(34.dp)
                .liquidBg(CircleShape, tint = SurfaceMuted, gate = 0.dp)
                .tap { onOpenWorkspace() },
            contentAlignment = Alignment.Center
        ) { IcCode(TextPrimary) }
    }
} //顶栏：抽屉 + 标题 + 角色胶囊 + 新建会话 + 工作区绑定

/** 角色快速切换：只改 Cfg.personaId，系统提示每轮重算，所以下一句就生效 */
@Composable
internal fun PersonaPanel(onClose: () -> Unit, onManage: () -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.cs_persona_title), onClose)
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
internal fun greetingPool(ctx: Context, h: Int, m: Int): List<String> = when {
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
internal fun EmptyState(
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
internal fun FeatureTile(
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
internal fun CardGlyph(icon: String, color: Color) {
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

