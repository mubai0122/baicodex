/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:23
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.kit

import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.agent.memory.MemOrganizer
import b.ai.agent.memory.Memory
import b.ai.native.security.BxKernel
import b.ai.ui.dialog.guardStateText
import b.ai.ui.glass.glassScrollState
import b.ai.ui.dialog.GuardBusyDialog
import b.ai.ui.dialog.GuardOffDialog
import b.ai.ui.dialog.GuardProgressDialog
import b.ai.ui.dialog.GuardWarnDialog
import b.ai.ui.dialog.MaskWarnDialog
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.dialog.SimpleDialog
import b.ai.ui.screen.user.Rate
import b.ai.ui.widget.AvatarView
import b.ai.ui.dialog.FlowSheet
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.PackDialog
import b.ai.ui.dialog.PermAsk
import b.ai.ui.dialog.PermAskDialog
import b.ai.ui.glass.GlassPerf
import b.ai.ui.glass.ProvideCardBackdrop
import b.ai.ui.glass.backdropPageInset
import b.ai.ui.glass.drawPageBg
import b.ai.ui.glass.glassLazyListState
import b.ai.ui.glass.glassPageLayer
import b.ai.ui.glass.liquidBg
import b.ai.ui.glass.pageBg
import b.ai.ui.screen.stat.StatUi
import b.ai.ui.screen.stat.statTab
import b.ai.ui.widget.AvatarPick
import b.ai.core.data.db.Repo
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.Field
import b.ai.ui.widget.FloatTab
import b.ai.ui.widget.FloatTabBar
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcChevron
import b.ai.ui.widget.IcTrash
import b.ai.ui.widget.LocalPicker
import b.ai.ui.widget.MdText
import b.ai.ui.widget.PickItem
import b.ai.ui.widget.PickSheet
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.SelectField
import b.ai.ui.widget.WsPurge
import b.ai.ui.widget.WsPurgeDialog
import b.ai.ui.widget.tap
import b.ai.core.cfg.app.Bai
import b.ai.core.net.WebWipe
import b.ai.core.task.hook.Noti
import b.ai.device.sys.a11y.AutoA11y
import b.ai.device.pet.PetSvc
import b.ai.net.api.llm.text
import b.ai.net.api.llm.ThinkRules
import b.ai.net.mi.MiConf
import b.ai.native.security.MiCrypto

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Handler
import android.os.Build
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.run.Agent
import b.ai.agent.tool.Tools
import android.app.Activity
import b.ai.core.media.img.Avatar
import b.ai.core.model.voice.Audio
import b.ai.core.model.voice.AsrPresets
import b.ai.core.data.io.Backup
import b.ai.native.security.Guard
import b.ai.core.cfg.app.Cfg
import b.ai.device.sys.island.Island
import b.ai.device.sys.island.Vendor
import b.ai.core.cfg.app.Lang
import b.ai.net.mcp.Mcp
import b.ai.native.security.Mask
import b.ai.net.mcp.McpServer
import b.ai.core.cfg.env.Env
import b.ai.core.cfg.env.PromptVars
import b.ai.core.model.voice.Speak
import b.ai.core.perm.Perms
import b.ai.agent.persona.Persona
import b.ai.agent.persona.Personas
import b.ai.core.data.profile.Profiles
import b.ai.net.api.a2a.A2aNodes
import b.ai.agent.flow.Flows
import b.ai.net.api.a2a.A2aSrv
import b.ai.core.task.alarm.Schedules
import b.ai.core.task.hook.Triggers
import b.ai.net.search.Search
import b.ai.core.plugin.skill.Skill
import b.ai.core.plugin.skill.Skills
import b.ai.core.cfg.secret.Secret
import b.ai.core.plugin.skill.SkillPacks
import b.ai.core.plugin.skill.SkillPack
import b.ai.core.media.wall.Wallpaper
import b.ai.core.model.chat.WorkMode
import b.ai.device.shell.root.Shiz
import b.ai.device.sys.alarm.Alarms
import b.ai.device.app.FChat
import b.ai.device.shell.local.Linux
import b.ai.device.pet.Pet
import b.ai.device.sys.host.PetGrant
import b.ai.device.sys.host.SecureWin
import b.ai.device.sys.probe.Router
import b.ai.device.shell.local.Toolchain
import b.ai.device.fs.Workspace
import b.ai.net.api.update.Update
import b.ai.net.doc.MaskRules
import b.ai.MainActivity
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
//miuix 风格滑杆自绘要用的一批：Canvas 画轨道、pointerInput 吃拖动、offset 摆滑块
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import b.ai.ui.theme.FontStore
import b.ai.ui.theme.LocalAiFont
import b.ai.ui.theme.LocalUserFont
import kotlin.math.roundToInt

@Composable
internal fun PersonaRow(p: Persona, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .liquidBg(RoundedCornerShape(12.dp), tint = if (selected) AccentBlueSoft else SurfaceMuted, gate = 0.dp)
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
                .liquidBg(RoundedCornerShape(8.dp), gate = 0.dp)
                .tap { onEdit() }
                .padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
} //角色一行

@Composable
internal fun PersonaDialog(
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
        Column(Modifier.weight(1f, fill = false).heightIn(max = 400.dp).verticalScroll(glassScrollState())) {
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
                            .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
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
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, gate = 0.dp)
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
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue, gate = 0.dp)
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
internal fun PolicyChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (selected) AccentBlue else TextSecondary,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier
            .liquidBg(RoundedCornerShape(8.dp)
            , tint = if (selected) AccentBlueSoft else SurfaceMuted, gate = 0.dp)
            .tap { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
} //可选项胶囊：单行不换行，宽度由调用方的 weight 决定

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

/**通知注入条数：0 或留空 = 不限；输入框贴右侧并做扁，高度跟滑块一致 */
@Composable
internal fun NotiLimitRow() {
    var text by remember { mutableStateOf(if (Cfg.notiLimit <= 0) "" else Cfg.notiLimit.toString()) }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.se_324), fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Slider(
            value = Cfg.notiLimit.coerceIn(0, 100).toFloat(),
            onValueChange = {
                val v = ((it / 5).toInt() * 5).coerceIn(0, 100)
                Cfg.notiLimit = v
                text = if (v == 0) "" else v.toString()
            },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(52.dp)
                .height(32.dp)
                .liquidBg(RoundedCornerShape(9.dp), gate = 0.dp)
                .border(1.dp, BorderGray, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { s ->
                    val d = s.filter { it.isDigit() }.take(4)
                    text = d
                    Cfg.notiLimit = d.toIntOrNull() ?: 0
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(AccentBlue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.se_325), fontSize = 11.sp, color = TextSecondary)
                        }
                        inner()
                    }
                }
            )
        }
    }
} //通知注入条数

@Composable
internal fun BgImagePick() {
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
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
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
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted, gate = 0.dp)
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

/** 桌宠入口：独立一行，开关在右，配置图标贴在开关左边 */
@Composable
internal fun PetEntryRow(onOpenPet: () -> Unit) {
    val ctx = LocalContext.current
    //不自己画底：统一由外层 CardBox 提供圆角 / 描边 / 玻璃 / 内边距，避免跟设置页其它卡片不一致
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pet_sec), fontSize = 14.sp, color = TextPrimary)
            Text(
                if (!Cfg.petOn) stringResource(R.string.pet_route_none)
                else if (Cfg.petGlobal) stringResource(R.string.pet_global)
                else stringResource(R.string.pet_in_app),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Box(
            Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                .tap { onOpenPet() }
                .padding(7.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_pet_cfg),
                contentDescription = stringResource(R.string.pet_pg_t),
                tint = AccentBlue,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = Cfg.petOn,
            onCheckedChange = { on ->
                Cfg.petOn = on
                if (on) {
                    //全局展示才需要悬浮窗：无障碍在跑就不需要额外权限
                    if (Cfg.petGlobal && b.ai.device.pet.Pet.canShow(ctx)) b.ai.device.pet.PetSvc.start(ctx)
                } else {
                    b.ai.device.pet.PetSvc.stop(ctx)
                    b.ai.device.pet.Pet.hide()
                }
            }
        )
    }
} //桌宠入口

/** 跟随项统一展开容器：高度 + 透明度一起动画，滑下来不生硬，收起也不会瞬间消失 */
@Composable
internal fun FollowBlock(visible: Boolean, content: @Composable () -> Unit) {
    //高度只由一个 animateContentSize 驱动，内容只负责淡入淡出。
    //之前 AnimatedVisibility 的 expandVertically 本身就是高度动画，跟内层一叠加，两次测量打架，
    //展开途中文案会被后一项盖住（用户反馈「文字都压一起」）。clipToBounds 再兜一层，不让内容溢出。
    Column(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .animateContentSize(animationSpec = tween(240, easing = FastOutSlowInEasing))
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(170, delayMillis = 60, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(100, easing = LinearOutSlowInEasing))
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
} //跟随项展开

/**
 * miuix 风格滑杆：标题 + 右侧当前值 + 26dp 粗轨道 + **完整包裹轨道的白色圆形滑块**。
 *
 * 几何口径（三条必须一致，否则视觉上歪）：
 * 1. 轨道高 26dp，滑块直径 32dp —— 滑块比轨道大 6dp，上下各包出 3dp，轨道从滑块中间穿过；
 * 2. 滑块中心 x = 半滑块宽 + frac × (总宽 − 滑块宽)：两端各留半个滑块，首尾一定能取到极值；
 * 3. 已填充段**画到滑块中心**为止，不是画到 width×frac —— 后者比分界点少半个滑块，看起来像脱节。
 * 4. 手指 x → 值：先用 (x − 半滑块宽)/(总宽 − 滑块宽) 求比例，再按 step 吸附。
 * 触控高度 44dp（Material 最小可点区），Canvas 与滑块都在其中垂直居中。
 */
@Composable
internal fun GlassSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(valueText, fontSize = 11.sp, color = TextSecondary)
    }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    //颜色：轨道始终是淡灰（浓度可调），激活段与滑块描边按「是否继承主题」二选一
    val trackColor = TextSecondary.copy(alpha = Cfg.sliderTrack / 100f)
    val activeColor = if (Cfg.sliderAccent) AccentBlue else SurfaceWhite.copy(alpha = 0.92f)
    val edgeColor = if (Cfg.sliderAccent) AccentBlue else BorderGray
    var width by remember { mutableIntStateOf(0) }
    //轨道高度可配：圆点直径 = 轨道 + 6dp，行高跟着撑开，避免圆点被裁
    val track = Cfg.sliderTrackH.dp
    val thumb = (Cfg.sliderTrackH + 6).dp
    val rowH = (Cfg.sliderTrackH + 18).coerceAtLeast(44).dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    val half = thumbPx / 2f
    //手指 x → 值：Rust 侧 bx_slider_value 同一套公式（见 core/feature/compute/slider.rs），此处为绘制期直算
    fun pick(x: Float) {
        val w = width.toFloat()
        if (w <= 0f) return
        val usable = (w - thumbPx).coerceAtLeast(1f)
        var v = range.start + ((x - half) / usable).coerceIn(0f, 1f) * span
        if (step > 0f) v = range.start + (Math.round((v - range.start) / step) * step)
        onChange(v.coerceIn(range.start, range.endInclusive))
    } //坐标换值
    //滑块中心 x（像素）与轨道可绘制宽度：Canvas 与 thumb 共用，保证分界点严丝合缝
    val usable = (width - thumbPx).coerceAtLeast(0f)
    val centerX = half + frac * usable
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .onSizeChanged { width = it.width }
            .pointerInput(range, step, width) { detectTapGestures { pick(it.x) } }
            .pointerInput(range, step, width) {
                detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { ch, _ -> pick(ch.position.x) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        //轨道 + 已填充段：填充段延伸到滑块中心，圆角与轨道同高的一半
        Canvas(Modifier.fillMaxWidth().height(track)) {
            val h = size.height
            val r = CornerRadius(h / 2f)
            drawRoundRect(trackColor, size = Size(size.width, h), cornerRadius = r)
            //填充宽度 = 滑块中心到左端；小于一个圆角直径时按直径画，避免圆角被压扁
            if (frac > 0f) {
                val fillW = centerX.coerceAtLeast(h)
                drawRoundRect(activeColor, size = Size(fillW, h), cornerRadius = r)
            }
        }
        //滑块：白色实心圆 + 细描边，直径 32dp 完整包住 26dp 轨道
        Box(
            Modifier
                .offset { IntOffset((centerX - half).roundToInt(), 0) }
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .background(SurfaceWhite, CircleShape)
                .border(1.dp, edgeColor, CircleShape)
        )
    }
} //miuix 风格滑杆
/**
 * Markdown 排版独立成页：AI 回复正文的字号 / 行距 / 段间距 / 列表缩进 / 左右留白 / 代码字号，
 * 改完立刻生效，卡片下方用聊天同一套 MdText 做实时预览。
 */
internal fun LazyListScope.mdSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(
            stringResource(R.string.tx_ae6b6416),
            stringResource(R.string.tx_8539beb4)
        )
        CardBox {
            GlassSlider(stringResource(R.string.tx_5a3701e9), "${Cfg.mdFont} sp", Cfg.mdFont.toFloat(), 11f..18f, step = 1f) {
                Cfg.mdFont = it.toInt()
            }
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_6ca4489d), "${Cfg.mdLine}%", Cfg.mdLine.toFloat(), 100f..220f, step = 5f) {
                Cfg.mdLine = it.toInt()
            }
            Text(stringResource(R.string.tx_fa9bac8d), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_13853b5a), "${Cfg.mdGap} dp", Cfg.mdGap.toFloat(), 0f..20f, step = 2f) {
                Cfg.mdGap = it.toInt()
            }
            Text(stringResource(R.string.tx_9fcf1606), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_cd758e7e), stringResource(R.string.tx_b4d10c2d, Cfg.mdIndent), Cfg.mdIndent.toFloat(), 0f..32f, step = 2f) {
                Cfg.mdIndent = it.toInt()
            }
            Text(stringResource(R.string.tx_8851751d), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_b76fe1f2), "${Cfg.mdPad} dp", Cfg.mdPad.toFloat(), 0f..24f, step = 2f) {
                Cfg.mdPad = it.toInt()
            }
            Text(stringResource(R.string.tx_75af6edf), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_279a3789), "${Cfg.mdCode} sp", Cfg.mdCode.toFloat(), 9f..16f, step = 1f) {
                Cfg.mdCode = it.toInt()
            }
            Text(stringResource(R.string.tx_fe8fe0cc), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tx_645dbc55), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            //预览直接用聊天同一套 MdText：改哪个参数，这里立刻能看出来
            MdText(MD_PREVIEW)
        }
    }
} //Markdown 排版子页
