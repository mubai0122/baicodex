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
internal fun BarChip(
    active: Boolean,
    flatActive: Color,
    accent: Color = AccentBlue,
    //外层修饰符：用来上报锚点矩形（运行模式胶囊要按它定位面板）
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    //开了液态玻璃走玻璃；没开玻璃才轮得到质感按钮，否则 3D 胶囊会把玻璃整个盖掉
    val glassOn = Cfg.glassCard && (LocalCardBackdrop.current ?: CardBackdropHolder.current) != null
    //开着的胶囊再加一圈同色描边：淡底在深色主题下容易被玻璃吃掉，只靠底色看不出开没开
    val edge = Modifier.border(1.dp, if (active) accent else Color.Transparent, RoundedCornerShape(9.dp))
    if (glassOn) {
        Row(
            Modifier
                .then(modifier)
                .liquidBg(RoundedCornerShape(9.dp), tint = if (active) flatActive else SurfaceMuted, highlightAlpha = 0.85f, gate = 0.dp)
                .then(edge)
                .tap { onTap() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    } else if (Cfg.tactileBtn) {
        TactileChip(active = active, accent = accent, modifier = modifier, onClick = onTap, content = content)
    } else {
        Row(
            Modifier
                .then(modifier)
                .liquidBg(RoundedCornerShape(9.dp), tint = if (active) flatActive else SurfaceMuted)
                .then(edge)
                .tap { onTap() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
} //输入区小胶囊

/** 发送键三态：空闲=发送；发送中=红底白方块终止；发送中又有内容=半透明发送，点了进队列 */
@Composable
internal fun SendButton(hasContent: Boolean, onSend: () -> Unit) {
    //忙且手上没东西可排：只能是终止键
    if (Agent.busy && !hasContent) {
        Box(
            Modifier
                .size(36.dp)
                .background(DangerRed, CircleShape)
                .tap { Agent.stop() },
            contentAlignment = Alignment.Center
        ) {
            IcStopSquare(Color.White, side = 14.dp)
        }
        return
    }
    //忙但输入区有内容：半透明发送，点一下排进队列
    val queued = Agent.busy && hasContent
    //发送键不套毛玻璃：纯色实心，颜色比主题色压深一档（亮色下更沉、暗色下不刺眼）
    val tint = if (hasContent) lerp(AccentBlue, Color.Black, 0.18f) else SurfaceMuted
    val iconColor = if (hasContent) Color.White else TextSecondary
    Box(Modifier.graphicsLayer { alpha = if (queued) 0.5f else 1f }) {
        if (Cfg.tactileBtn) {
            TactileCircle(active = hasContent, enabled = hasContent, onClick = onSend) {
                IcSend(iconColor, side = 15.dp)
            }
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .background(tint, CircleShape)
                    .tap(enabled = hasContent) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                IcSend(iconColor, side = 15.dp)
            }
        }
    }
} //发送键 / 终止键 / 排队键

/** 发送队列：一条一行，行序就是发送顺序；每条左铅笔（取回编辑）右发送（提到最前）最右取消（撤掉） */
@Composable
internal fun QueueBar(
    items: List<Agent.QueuedMsg>,
    onEdit: (Long) -> Unit,
    onSendNow: (Long) -> Unit,
    onDrop: (Long) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cs_queue_title), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.cs_queue_tip),
                fontSize = 9.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        //最多露 3 条，再多只报个数，别把消息区挤没
        items.take(3).forEachIndexed { i, q ->
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //铅笔：取回输入框改完自己发
                Box(Modifier.size(18.dp).tap { onEdit(q.id) }, contentAlignment = Alignment.Center) {
                    IcPencil(AccentBlue, side = 12.dp, stroke = 1.4.dp)
                }
                Spacer(Modifier.width(4.dp))
                Text("${i + 1}", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(11.dp))
                Text(
                    q.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                //发送：空闲即刻发，正忙提到队首
                Box(Modifier.size(20.dp).tap { onSendNow(q.id) }, contentAlignment = Alignment.Center) {
                    IcSend(AccentBlue, side = 12.dp)
                }
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(18.dp).tap { onDrop(q.id) }, contentAlignment = Alignment.Center) {
                    IcClose(TextSecondary, side = 11.dp, stroke = 1.6.dp)
                }
            }
        }
        if (items.size > 3) {
            Text("+${items.size - 3}", fontSize = 10.sp, color = TextSecondary)
        }
    }
} //发送队列条

/** 工具执行中的占位卡：宽度、圆角、缩进与执行完的结果行完全一致，只是右侧挂个转圈 */
@Composable
internal fun RunningCard(rt: RunningTool) {
    val summary = remember(rt.seq) { argSummary(rt.args) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (Cfg.avatarOn) 34.dp else 0.dp)
            .liquidBg(bubbleShape(12.dp))
            .border(1.dp, BorderGray, bubbleShape(12.dp))
            .padding(horizontal = bubblePadH(10.dp), vertical = bubblePadV(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .liquidBg(RoundedCornerShape(7.dp), tint = AccentBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            ToolIcon(rt.name, AccentBlue, side = 13.dp)
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (rt.fromSub) stringResource(R.string.cs_tool_sub) else "") + Tools.label(rt.name),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 1.8.dp,
            color = AccentBlue
        )
    }
} //执行中卡片

/** 参数摘要：挑一个最能说明在干嘛的字段压成一行 */
internal fun argSummary(raw: String): String {
    val o = runCatching { JSONObject(raw) }.getOrNull() ?: return raw.replace("\n", " ").take(80)
    for (k in arrayOf("path", "command", "query", "name", "task", "url", "pattern", "from", "text", "action")) {
        val v = o.optString(k)
        if (v.isNotBlank()) return v.replace("\n", " ").take(80)
    }
    return raw.replace("\n", " ").take(80)
} //参数摘要

/** 数字缩写：12,345 显示成 12.3k，1,234,567 显示成 1.2M */
internal fun fmtNum(v: Long): String = when {
    v >= 1_000_000L -> String.format(Locale.US, "%.1fM", v / 1_000_000f)
    v >= 10_000L -> String.format(Locale.US, "%.1fk", v / 1000f)
    else -> v.toString()
} //数字缩写

internal fun fmtNum(v: Int): String = fmtNum(v.toLong())

/**
 * 未压缩那一段的统计：跟 Agent.compact 同一口径——摘要（summary）之后的消息才算。
 * 返回 [token 估算, 条数, 这些轮次真实用掉的 token 合计]；token 估算走 Cfg.estTokens，与压缩判定同一口径。
 */
internal fun ctxStat(list: List<ChatMsg>): IntArray {
    val sm = list.lastOrNull { it.role == "summary" }
    val bound = sm?.let {
        Regex("""\[\[upto:(\d+)]]""").find(it.content)?.groupValues?.get(1)?.toLongOrNull()
    } ?: 0L
    val fresh = list.filter { it.role != "summary" && it.id > bound }
    val tokens = fresh.sumOf { m -> Cfg.estTokens(m.content) + m.toolCalls.sumOf { Cfg.estTokens(it.arguments) } }
    return intArrayOf(tokens, fresh.size, fresh.sumOf { it.meta?.totalTokens ?: 0 })
} //未压缩段统计

/** 上下文占用的环形进度：环长按真实百分比画，70% 以下是绿的，70% 起变红 */
@Composable
internal fun CtxRing(tokens: Int, budget: Int, onPos: (Int, Int) -> Unit, onTap: () -> Unit) {
    val pct = if (budget <= 0) 0f else (tokens.toFloat() / budget).coerceIn(0f, 1f)
    val c = if (pct < 0.7f) OkGreen else DangerRed
    //尺寸与底色都跟顶栏那个加号一致，摆在一起才不突兀
    Box(
        Modifier
            .size(34.dp)
            //锚点：小卡片按环的右下角落位
            .onGloballyPositioned {
                val r = it.boundsInRoot()
                onPos(r.right.toInt(), r.bottom.toInt())
            }
            .liquidBg(CircleShape, tint = SurfaceMuted, gate = 0.dp)
            .tap { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(26.dp)) {
            val sw = 2.dp.toPx()
            val style = Stroke(width = sw, cap = StrokeCap.Round)
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(BorderGray, -90f, 360f, false, tl, sz, style = style)
            if (pct > 0f) drawArc(c, -90f, 360f * pct, false, tl, sz, style = style)
        }
        Text(
            "${(pct * 100).toInt()}%",
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = c
        )
    }
} //上下文占用环

/** 点环之后的小卡片：贴预算环右下方，只报三个数 */
@Composable
internal fun CtxCard(tokens: Int, budget: Int, freshCount: Int, freshTok: Int, anchorX: Int, anchorY: Int) {
    val sid = Agent.sessionId.orEmpty()
    var spent by remember(sid) { mutableStateOf(0L) }
    LaunchedEffect(sid) {
        spent = withContext(Dispatchers.IO) { Repo.usageSession(sid).sum() }
    }
    val over = budget > 0 && tokens.toLong() * 100 / budget >= 70
    val d = LocalDensity.current
    //宽度按内容压到 168dp；右边缘跟环对齐，左边不出屏
    val wDp = 168.dp
    val x = (anchorX - with(d) { wDp.toPx() }).coerceAtLeast(with(d) { 8.dp.toPx() })
    val y = anchorY + with(d) { 6.dp.toPx() }
    Column(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(wDp)
            .liquidBg(RoundedCornerShape(14.dp), veil = 0.42f, gate = 0.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            stringResource(R.string.ctx_tip_title),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.ctx_tip_used, fmtNum(tokens), fmtNum(budget)),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (over) DangerRed else OkGreen
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.ctx_tip_total, fmtNum(spent)),
            fontSize = 10.sp,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.ctx_tip_fresh, fmtNum(freshTok), freshCount),
            fontSize = 10.sp,
            color = TextSecondary
        )
        //余额板块：点击拉一次，成功后冷却 60 秒，冷却中显示倒计时
        //时钟只用来驱动倒计时重算，不参与业务状态
        var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(500L)
                nowMs = System.currentTimeMillis()
            }
        }
        //剩余冷却毫秒：从上次查询完成时刻算起
        val leftMs = (BAL_COOL_MS - (nowMs - BalWatch.lastMs)).coerceIn(0L, BAL_COOL_MS)
        val leftSec = ((leftMs + 999L) / 1000L).toInt()
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha = 0.18f)))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .tap {
                    if (busy || leftSec > 0) return@tap
                    //Key 从「余额查询来源」解析：没配任何可用配置就直接不查
                    val src = Profiles.balanceSource() ?: return@tap
                    if (src.apiKey.isBlank()) return@tap
                    busy = true
                    PetBalance.fetchNumAsync(src.apiKey) { b ->
                        BalWatch.put(b)
                        busy = false
                    }
                }
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.ctx_bal),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.weight(1f))
            //这一行永远是余额值：查不到才退回「点击查询」
            Text(
                when {
                    busy -> stringResource(R.string.ctx_bal_loading)
                    else -> BalWatch.last?.text?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.ctx_bal_tap)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (busy) TextSecondary else TextPrimary
            )
        }
        //冷却单独一行：余额值不挪位，剩余秒数在这里倒数
        Row(
            Modifier.fillMaxWidth().padding(top = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.ctx_bal_cd),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (leftSec > 0) stringResource(R.string.ctx_bal_cd_v, leftSec)
                else stringResource(R.string.ctx_bal_cd_ok),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //上下文小卡片

/** 余额查询冷却时长：1 分钟 */
internal const val BAL_COOL_MS = 60_000L

