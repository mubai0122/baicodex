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

internal val ROUTE_EXIT = Regex("^\\[(root|Shizuku|沙箱|Ubuntu)]\\s*exit=-?\\d+$")
internal val EXIT_PART = Regex("\\s*exit=-?\\d+")

/** 工具结果拆成「摘要 / diff 行」：没有标记就整段当摘要 */
internal fun splitDiff(content: String): Pair<String, List<String>> {
    val s = content.indexOf(Diff.MARK)
    if (s < 0) return content to emptyList()
    val e = content.indexOf(Diff.MARK_END, s + Diff.MARK.length)
    val body = content.substring(0, s).trimEnd()
    val raw = if (e > s) content.substring(s + Diff.MARK.length, e)
    else content.substring(s + Diff.MARK.length)
    return body to raw.trim('\n').lines().filter { it.isNotEmpty() }
} //按 [[diff]] 标记拆分

/** diff 行列表：不铺底色，只靠字体颜色区分（+ 绿 / − 红 / @@ 蓝）；scroll=false 交给外层滚动 */
@Composable
internal fun DiffView(lines: List<String>, scroll: Boolean = true) {
    val box = if (scroll) {
        Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(glassScrollState())
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        box
            //不再铺 SurfaceMuted 底色：用户要求 diff 只用字体颜色区分，不要色块背景
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        lines.forEach { ln ->
            val add = ln.startsWith("+")
            val del = ln.startsWith("-")
            Text(
                ln,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = when {
                    add -> OkGreen
                    del -> DangerRed
                    ln.startsWith("@@") -> AccentBlue
                    else -> TextSecondary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
            )
        }
    }
} //修改行着色列表

/** 从历史里的 assistant.tool_calls 反查本次调用的参数 */
internal fun argsOf(m: ChatMsg): String? {
    val id = m.toolCallId ?: return null
    return Agent.messages
        .lastOrNull { it.role == "assistant" && it.toolCalls.any { c -> c.id == id } }
        ?.toolCalls?.firstOrNull { it.id == id }?.arguments
}

