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

/** 粘贴超长文本：自动转存成文件并直接挂成待发送附件，输入框里不留任何文字 */
internal fun pasteGuard(old: String, v: String, onFile: (String) -> Unit): String {
    val limit = Cfg.pasteLimit.coerceAtLeast(200)
    if (!Cfg.pasteToFile) return v
    if (v.length - old.length < limit) return v
    //这次多出来的那一段 = 去掉公共前缀与公共后缀后夹在中间的部分（粘贴就是这种形状）
    var p = 0
    val max = minOf(old.length, v.length)
    while (p < max && old[p] == v[p]) p++
    var s = 0
    while (s < max - p && old[old.length - 1 - s] == v[v.length - 1 - s]) s++
    val ins = v.substring(p, v.length - s)
    if (ins.length < limit) return v
    val path = Pastes.save(ins) ?: return v
    //挂成附件后它就长期保留：两分钟 TTL 会赶在用户点发送之前把文件删掉
    Pastes.keep(path)
    Pastes.purge()
    onFile(path)
    return v.substring(0, p) + v.substring(v.length - s)
} //粘贴转存

/** 全屏输入：长文 / 多行代码不用在小框里挤，顶部工具条 + 整屏编辑区 + 底部发送 */
@Composable
internal fun FullInputDialog(
    value: String,
    onChange: (String) -> Unit,
    images: List<String>,
    files: List<String>,
    onRemoveImage: (String) -> Unit,
    onRemoveFile: (String) -> Unit,
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
                    Modifier.size(28.dp).liquidBg(CircleShape, tint = SurfaceMuted).tap { onClose() },
                    contentAlignment = Alignment.Center
                ) { IcClose(TextSecondary, side = 12.dp) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .liquidBg(RoundedCornerShape(16.dp))
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
            if (files.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    FileChips(files = files, onRemove = onRemoveFile)
                }
            }
            Box(
                Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .liquidBg(RoundedCornerShape(14.dp), tint = if (canSend) AccentBlue else SurfaceMuted)
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
internal fun StreamingMsg(text: String, reason: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    //流式气泡也算一条消息，头像与圆角跟正式回复保持一致
    if (Cfg.avatarOn) {
        AvatarView(Avatar.aiPath(), 28.dp)
        Spacer(Modifier.width(6.dp))
    }
    Column(
        Modifier
            .weight(1f)
            .liquidBg(bubbleShape(14.dp))
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

/**一屏铺多少轮：进会话只组合这么多，往上滑或点胶囊再放一批 */
internal const val ROUND_PAGE = 12

