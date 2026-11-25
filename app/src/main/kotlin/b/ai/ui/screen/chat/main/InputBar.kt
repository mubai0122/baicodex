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
internal fun InputBar(
    input: String,
    hint: String,
    modelLabel: String,
    modeLabel: String,
    onOpenMode: () -> Unit,
    onChange: (String) -> Unit,
    images: List<String>,
    files: List<String>,
    onOpenConfig: () -> Unit,
    onOpenAttach: () -> Unit,
    onOpenModel: () -> Unit,
    onExpand: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onRemoveFile: (String) -> Unit,
    //语音模式：整条输入行换成长按说话，语音位换键盘位
    voiceMode: Boolean = false,
    onToggleVoice: () -> Unit = {},
    //面板锚点上报：点哪个胶囊就把它的屏幕矩形交给页面，面板卡片按它定位
    onAnchor: (Rect) -> Unit = {},
    onHoldStart: () -> Unit = {},
    onHoldEnd: (Boolean) -> Unit = {},
    onSend: () -> Unit
) {
    val ctx = LocalContext.current
    //四个入口胶囊各自的屏幕矩形：点的时候交给页面，面板卡片按它贴边弹出
    var rModel by remember { mutableStateOf(Rect.Zero) }
    var rMode by remember { mutableStateOf(Rect.Zero) }
    var rCfg by remember { mutableStateOf(Rect.Zero) }
    var rAttach by remember { mutableStateOf(Rect.Zero) }
    //长按输入框直接开语音识别：450ms 内抬手就交给文本框正常放光标，按住不放才算长按
    val latestInput by rememberUpdatedState(input)
    val longPressVoice = Modifier.pointerInput(ctx) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var held = true
            withTimeoutOrNull(450L) {
                waitForUpOrCancellation(PointerEventPass.Initial)
                held = false
            }
            if (!held) return@awaitEachGesture
            Voice.toggle(ctx) { t -> onChange((latestInput + t).trim()) }
            //这次长按剩下的事件全吃掉，免得文本框同时弹出文字选择菜单
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
                if (e.changes.none { it.pressed }) break
            }
        }
    }
    //悬浮模式：只把输入框本体拎出来浮着，外层不铺任何背景，留着 BgGray 底
    //实体键盘：用 TextFieldValue 自己持光标，回车发送、Shift+回车换行，左右键自己挪（部分外接键盘/输入法不转给文本框）
    var field by remember { mutableStateOf(TextFieldValue(input)) }
    LaunchedEffect(input) {
        if (field.text != input) field = TextFieldValue(input, TextRange(input.length))
    }
    fun caretTo(i: Int) {
        val at = i.coerceIn(0, field.text.length)
        field = field.copy(selection = TextRange(at))
    }
    //排版结果：上下键要按「同一列、上一行/下一行」落位，只靠字符偏移会在换行处乱跳
    var layoutRes by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    fun caretLine(dy: Int) {
        val lr = layoutRes ?: return
        val at = field.selection.start.coerceIn(0, field.text.length)
        val line = lr.getLineForOffset(at)
        val target = line + dy
        if (target < 0 || target > lr.lineCount - 1) return
        //沿用当前列的水平位置，找不到就取该行末尾
        val x = lr.getHorizontalPosition(at, usePrimaryDirection = true)
        val y = (lr.getLineTop(target) + lr.getLineBottom(target)) / 2f
        caretTo(lr.getOffsetForPosition(androidx.compose.ui.geometry.Offset(x, y)))
    }
    val keyHandling = Modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val t = field.text
        when {
            e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                //回车发送开关（设置 → 个性化）：关掉后回车只换行，Shift+回车始终换行
                if (Cfg.enterSend && !e.isShiftPressed) {
                    onSend()
                } else {
                    val sel = field.selection
                    val nt = t.substring(0, sel.start) + "\n" + t.substring(sel.end)
                    field = TextFieldValue(nt, TextRange(sel.start + 1))
                    onChange(nt)
                }
                true
            }
            e.key == Key.DirectionLeft && !e.isShiftPressed -> {
                caretTo(field.selection.start - 1); true
            }
            e.key == Key.DirectionRight && !e.isShiftPressed -> {
                caretTo(field.selection.end + 1); true
            }
            e.key == Key.DirectionUp && !e.isShiftPressed -> {
                //多行时按行移动；单行没有上一行就交给默认（回到开头由系统处理）
                if (layoutRes != null) { caretLine(-1); true } else false
            }
            e.key == Key.DirectionDown && !e.isShiftPressed -> {
                if (layoutRes != null) { caretLine(1); true } else false
            }
            //小键盘回车已在上面合并处理，这里不再单独分支
            else -> false
        }
    }
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
            //运行模式：五档（计划 / 标准 / 代码 / 极简 / 创造），放在快捷键第一位
            BarChip(
                active = Cfg.agentMode != AgentMode.STANDARD,
                flatActive = AccentBlueSoft,
                onTap = { onAnchor(rMode); onOpenMode() },
                modifier = Modifier.onGloballyPositioned { rMode = it.boundsInRoot() }
            ) {
                Text(
                    modeLabel,
                    fontSize = 10.sp,
                    fontWeight = if (Cfg.agentMode != AgentMode.STANDARD) FontWeight.Bold else FontWeight.Medium,
                    color = if (Cfg.agentMode != AgentMode.STANDARD) AccentBlue else TextSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (Cfg.agentMode != AgentMode.STANDARD) AccentBlue else TextSecondary,
                    modifier = Modifier.size(11.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
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
                    fontWeight = if (Cfg.think) FontWeight.Bold else FontWeight.Medium,
                    color = if (Cfg.think) AccentBlue else TextSecondary
                )
            }
            Spacer(Modifier.width(6.dp))
            //最大模式：把单轮上限抬到 8192，长任务不容易被截断
            BarChip(
                active = Cfg.maxMode,
                flatActive = AccentBlueSoft,
                onTap = { Cfg.maxMode = !Cfg.maxMode }
            ) {
                Text(
                    if (Cfg.maxMode) stringResource(R.string.cs_max_on) else stringResource(R.string.cs_max),
                    fontSize = 10.sp,
                    fontWeight = if (Cfg.maxMode) FontWeight.Bold else FontWeight.Medium,
                    color = if (Cfg.maxMode) AccentBlue else TextSecondary
                )
            }
            Spacer(Modifier.width(6.dp))
            //语音：点一下进语音模式（话筒换成键盘），再点一下回文字输入；听写本身改成按住说话
            BarChip(
                active = voiceMode || Voice.listening,
                accent = DangerRed,
                flatActive = DangerRedSoft,
                onTap = onToggleVoice
            ) {
                if (voiceMode) IcKeyboard(TextSecondary, side = 12.dp)
                else IcMic(if (Voice.listening) DangerRed else TextSecondary, side = 12.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        voiceMode -> stringResource(R.string.cs_keyboard)
                        Voice.listening -> {
                            //接口模式显示已录秒数与上限，看着数字知道还要说多久
                            val sec = Audio.recSec
                            if (Cfg.asrMode == "api" && sec > 0) "$sec s / ${Cfg.asrMaxSec} s"
                            else stringResource(R.string.cs_listening)
                        }
                        else -> stringResource(R.string.cs_voice)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (Voice.listening) DangerRed else TextSecondary
                )
            }
            //全屏输入入口：新逻辑下超 700 字才冒出来；关掉新逻辑就常驻在语音右边
            if (!Cfg.fullInputNew || input.length >= 700) {
                Spacer(Modifier.width(6.dp))
                BarChip(active = false, flatActive = AccentBlueSoft, onTap = onExpand) {
                    IcExpand(TextSecondary, side = 12.dp, stroke = 1.5.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.cs_fullscreen_entry),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
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
        if (files.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            //文件同样挂着一条，× 摘掉
            FileChips(files = files, onRemove = onRemoveFile)
        }
        //错误只在输入区停 3 秒：语音不可用这类提示常驻会把输入框顶高，切键盘也不会自己消（用户反馈）
        LaunchedEffect(Voice.error) {
            if (Voice.error.isBlank()) return@LaunchedEffect
            kotlinx.coroutines.delay(3000)
            Voice.clearError()
        }
        if (Voice.error.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(Voice.error, fontSize = 10.sp, color = DangerRed)
        }
        Spacer(Modifier.height(6.dp))
        //输入容器与发送键都从 44dp 起算，单行时严格等高；多行时发送键贴底
        //悬浮模式：输入框和发送键各自带投影浮起来，周围仍是页面底色，不再铺整块白卡
        val floatShadow = if (floating) Modifier.shadow(12.dp, RoundedCornerShape(14.dp)) else Modifier
        //忙的时候也允许「发送」：点下去进排队队列，按钮自己切半透明态
        val hasContent = input.isNotBlank() || images.isNotEmpty() || files.isNotEmpty()
        //语音模式：整条输入行换成一个「长按说话」条，键盘 / 发送都让位
        if (voiceMode) {
            VoiceHoldBar(
                onStart = onHoldStart,
                onEnd = onHoldEnd,
                modifier = Modifier.fillMaxWidth().then(floatShadow)
            )
        } else {
        //输入框和纸飞机键同一个白卡：小圈内嵌右侧，不再单独立 66dp 大按钮
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(floatShadow)
                .liquidBg(RoundedCornerShape(14.dp), gate = 0.dp)
                .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                            value = field,
                            onValueChange = { v ->
                                //@技能整段删除：退格落在 token 里就整块抹掉，不留半截
                                val t = if (v.text.length < field.text.length) {
                                    atomicMentionDelete(field.text, v.text)
                                } else v.text
                                field = if (t == v.text) v
                                else v.copy(text = t, selection = TextRange(t.length))
                                if (t != input) onChange(t)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                            maxLines = 5,
                            //软键盘右下角给「发送」：手机上回车键是 IME action，不走 KeyEvent，
                            //只接管 onPreviewKeyEvent 的话，外接键盘能用、软键盘还是换行
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Send
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = { onSend() }
                            ),
                            onTextLayout = { layoutRes = it },
                            //@名称 渲染成蓝色标签（只染色不改字，光标位置不变）
                            visualTransformation = mentionVisual(),
                            cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth().then(longPressVoice).then(keyHandling)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    //底部一行：左边模型胶囊（超出就省略，不挤压右侧按钮），右边配置与附件
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                //只包住「模型名 + 折叠箭头」，不再占满整行（用户要求缩到内容宽度、只留一点点白）
                                .widthIn(max = 200.dp)
                                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                                .onGloballyPositioned { rModel = it.boundsInRoot() }
                                .tap { onAnchor(rModel); onOpenModel() }
                                //用户要求：模型框比原来长 1.7 倍，模型名加粗
                                //高度压 2dp：上下内边距各减 1dp，胶囊整体矮 2dp（用户反馈「太高」）
                                .padding(start = 14.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modelLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(11.dp).graphicsLayer { rotationZ = -90f }) {
                                IcBack(TextSecondary, side = 11.dp, stroke = 1.6.dp)
                            }
                        }
                        //胶囊缩到内容宽度后，靠这个弹性空隙把右侧图标组顶到行尾
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        //配置 / 附件两个图标：触点 32dp 等距，图标统一 18dp（全屏入口挪到上面语音右侧）
                        Box(
                            Modifier.size(38.dp).onGloballyPositioned { rCfg = it.boundsInRoot() }.tap { onAnchor(rCfg); onOpenConfig() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cs_config),
                                tint = if (Cfg.maxMode || Cfg.think) AccentBlue else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Box(
                            Modifier.size(38.dp).onGloballyPositioned { rAttach = it.boundsInRoot() }.tap { onAnchor(rAttach); onOpenAttach() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.cs_attach),
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        //提示词优化
                        //原文单独存一份，回滚时原样写回，不做二次加工
                        var optBack by remember { mutableStateOf<String?>(null) }
                        var optBusy by remember { mutableStateOf(false) }
                        val optScope = rememberCoroutineScope()
                        Box(
                            Modifier
                                .size(38.dp)
                                .tap(enabled = input.isNotBlank() && !optBusy) {
                                    val back = optBack
                                    if (back != null) {
                                        //已经优化过：点一下还原原文
                                        onChange(back)
                                        optBack = null
                                    } else {
                                        //第一次：先留原文，再跑优化，结果回填输入框
                                        val origin = input
                                        optScope.launch {
                                            optBusy = true
                                            val out = PromptOptimizer.run(origin, Cfg.agentMode, images + files, modeLabel)
                                            optBusy = false
                                            //按失败原因给准话：别再把「没配模型」「内容超长」「接口报错」混成一句
                                            val why = when (out) {
                                                is OptResult.Ok -> {
                                                    optBack = origin
                                                    onChange(out.text)
                                                    ""
                                                }
                                                OptResult.EmptyInput -> "先写点内容再优化"
                                                OptResult.TooLong -> b.ai.App.self.getString(R.string.tx_f8e3d0b7, PromptOptimizer.MAX_IN)
                                                OptResult.NoModel -> "还没配优化模型：设置 → 模型 → 提示词优化"
                                                OptResult.EmptyOut -> "模型没返回内容，换个优化模型或稍后再试"
                                                is OptResult.Failed -> b.ai.App.self.getString(R.string.tx_52280f7d, out.reason)
                                            }
                                            if (why.isNotEmpty()) {
                                                android.widget.Toast.makeText(
                                                    ctx,
                                                    why,
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (optBack != null) {
                                //已优化：魔法棒直接形变成对号（自绘、单色、无底），点一下回滚
                                IcWandToCheck(AccentBlue, side = 21.dp, stroke = 2.6.dp)
                            } else {
                                Box(Modifier.size(23.dp), contentAlignment = Alignment.Center) {
                                    //可优化态：魔法棒 + 三颗递进闪星；点下去它自己直接变对号，不经过加号
                                    IcWand(
                                        if (optBusy) TextSecondary.copy(alpha = 0.45f) else TextSecondary,
                                        side = 21.dp,
                                        stroke = 2.0.dp
                                    )
                                }
                            }
                        }
                    }
                }
            Spacer(Modifier.width(6.dp))
            //纸飞机小圈：内嵌在输入框右侧，禁用时灰底灰图标
            SendButton(hasContent = hasContent, onSend = onSend)
        }
        }
    }
} //输入区：图片 / 文件 / 语音 / 发送

/** 按住说话的条子：按下开听、上滑取消、松手收尾；光效由外部按回调驱动 */
@Composable
internal fun VoiceHoldBar(
    onStart: () -> Unit,
    onEnd: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var holding by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    val cancelPx = with(LocalDensity.current) { 56.dp.toPx() }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .liquidBg(RoundedCornerShape(14.dp), gate = 0.dp)
            .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    holding = true
                    cancel = false
                    onStart()
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        //上滑超过 56dp 就算取消：松手时这段识别结果直接丢掉
                        val up = down.position.y - c.position.y > cancelPx
                        if (up != cancel) cancel = up
                        if (!c.pressed) break
                        c.consume()
                    }
                    holding = false
                    onEnd(cancel)
                    cancel = false
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IcMic(if (holding) AccentBlue else TextSecondary, side = 15.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                cancel -> stringResource(R.string.cs_voice_cancel)
                holding -> stringResource(R.string.cs_voice_end)
                else -> stringResource(R.string.cs_voice_hold)
            },
            fontSize = 13.sp,
            fontWeight = if (holding) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                cancel -> DangerRed
                holding -> AccentBlue
                else -> TextSecondary
            }
        )
    }
} //长按说话条

