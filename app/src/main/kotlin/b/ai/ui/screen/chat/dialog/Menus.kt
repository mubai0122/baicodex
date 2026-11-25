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

package b.ai.ui.screen.chat.dialog

import b.ai.ui.screen.chat.at.*
import b.ai.ui.screen.chat.list.*
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

internal fun prettyJson(raw: String): String =
    runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw)

@Composable
internal fun SummaryMsg(m: ChatMsg, onLongPress: () -> Unit) {
    var open by remember(m.id) { mutableStateOf(false) }
    val body = m.content.replace(Regex("""\[\[upto:\d+]]"""), "").trim()
    Column(
        Modifier
            .fillMaxWidth()
            .liquidBg(bubbleShape(12.dp), tint = SurfaceMuted)
            //点一下展开/收起，长按出「重新总结」菜单
            .tapLong(onClick = { open = !open }, onLong = onLongPress)
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

/** 摘要卡片的长按菜单：重做一次摘要，或带着自己的要求重做 */
@Composable
internal fun SummaryMenuDialog(
    onRegen: () -> Unit,
    onRegenNote: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    ScrimCard(onDismiss = onClose) {
        Text(
            stringResource(R.string.cs_summary),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.cs_sum_menu_desc), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        MenuRow(stringResource(R.string.cs_sum_regen), stringResource(R.string.cs_sum_regen_desc), onRegen)
        MenuRow(stringResource(R.string.cs_sum_note), stringResource(R.string.cs_sum_note_desc), onRegenNote)
        MenuRow(stringResource(R.string.cs_copy_content), stringResource(R.string.cs_copy_content_desc), onCopy)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.cs_cancel),
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .tap { onClose() }
                .padding(vertical = 11.dp)
        )
    }
} //摘要菜单

/** 长按菜单里发起的输入请求：edit=true 编辑重发，false 带提示重新生成 */
internal data class PromptReq(val m: ChatMsg, val edit: Boolean)

internal fun copyText(ctx: Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("bai", text))
    }
} //复制到剪贴板

@Composable
internal fun MsgMenuDialog(
    m: ChatMsg,
    onEdit: () -> Unit,
    onRegen: () -> Unit,
    onRegenWith: () -> Unit,
    onCopy: () -> Unit,
    onCopyPart: () -> Unit,
    onStar: () -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    //删除不可恢复：第一下只是把按钮变成「确认删除」，第二下才真删
    var del by remember(m.id) { mutableStateOf(false) }
    //改成从底部升起的操作卡：去掉消息原文预览，先给一块显眼的复制区
    BottomSheetCard(onDismiss = onClose, maxFrac = 0.82f) {
        Text(
            stringResource(R.string.cs_msg_ops),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        //顶部仍是「复制全部」：整条复制是最常用的动作，保持原来的一眼可见
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = AccentBlueSoft)
                .tap { onCopy() }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IcCopy(AccentBlue, side = 17.dp, stroke = 1.6.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cs_copy_all),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
                Text(stringResource(R.string.cs_copy_content_desc), fontSize = 10.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(glassScrollState())) {
            if (m.role == "user") {
                MenuRow(stringResource(R.string.cs_edit_resend), stringResource(R.string.cs_edit_resend_desc), onEdit)
            } else {
                MenuRow(stringResource(R.string.cs_regen), stringResource(R.string.cs_regen_desc), onRegen)
                MenuRow(stringResource(R.string.cs_regen_hint2), stringResource(R.string.cs_regen_hint_desc2), onRegenWith)
            }
            //复制部分消息：打开全文面板，在里面长按选任意片段
            MenuRow(stringResource(R.string.tx_fda7a982), stringResource(R.string.tx_cadff0ad)) { onCopyPart() }
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
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.cs_cancel),
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .tap { onClose() }
                .padding(vertical = 11.dp)
        )
    }
} //长按消息的操作菜单

/**
 * 复制部分内容：把整条消息原样摊开，交给 SelectionContainer，
 * 用户长按就能选任意片段复制；顶部另有「复制全部」兜底。
 */
@Composable
internal fun MessageTextSheet(m: ChatMsg, onCopyAll: () -> Unit, onClose: () -> Unit) {
    BottomSheetCard(onDismiss = onClose, maxFrac = 0.86f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tx_841c2e0d),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.tx_2733a243),
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                    .tap { onCopyAll() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.tx_b768f17f), fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(glassScrollState())) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    m.content.replace(Regex("""\[\[upto:\d+]]"""), "").trim(),
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    color = TextPrimary
                )
            }
        }
    }
} //消息全文面板


@Composable
internal fun MenuRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .tap { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(title, fontSize = 13.sp, color = TextPrimary)
        Text(sub, fontSize = 10.sp, color = TextSecondary)
    }
} //菜单里的一行

@Composable
internal fun PromptDialog(
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
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .padding(10.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                cursorBrush = SolidColor(AccentBlue),
                modifier = Modifier.fillMaxWidth().verticalScroll(glassScrollState())
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
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
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
                    .liquidBg(RoundedCornerShape(10.dp), tint = if (text.isBlank()) SurfaceMuted else AccentBlue)
                    .tap { if (text.isNotBlank()) onConfirm(text.trim()) }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //编辑重发 / 带提示重生成的输入弹窗

/**
 * 错误回复按钮组（消息回复式错误处理）。
 *   · 重发：把 head 退回这条错误上方的用户消息，原地重跑这一轮；
 *   · 仍然重发：无视这次失败，把当初那条用户消息当新消息再发一次；
 *   · 去配置：只有 errKind 非空（缺模型 / 缺权限 / 缺环境）才出现，点了跳对应设置页。
 */
@Composable
internal fun ErrActions(m: ChatMsg, onFix: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ErrChip(stringResource(R.string.cs_fail_retry), primary = true) { Agent.retryErr(m.id) }
        ErrChip(stringResource(R.string.cs_fail_again)) { Agent.resendErr(m.id) }
        if (m.errKind.isNotBlank()) ErrChip(stringResource(R.string.cs_err_go_cfg)) { onFix(m.errKind) }
    }
} //错误回复按钮组

/** 错误气泡里的一个小按钮：主按钮蓝底白字，其余灰底 */
@Composable
internal fun ErrChip(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.sp,
        color = if (primary) Color.White else TextPrimary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .liquidBg(RoundedCornerShape(9.dp), tint = if (primary) AccentBlue else SurfaceMuted)
            .tap { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
} //错误按钮小块

//输入区上方那排信息面板同一时刻只能有一个；用枚举代替一堆布尔量，避免「点模型弹出运行模式」这类串台
enum class InfoPanel { None, Attach, Cfg, Skill, Tag, Model, Mode, Persona }
