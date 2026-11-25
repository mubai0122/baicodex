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

package b.ai.ui.screen.settings.about

import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.kit.*
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

/** 数据保障开关：开启走强制警告 +15s 冷静期，开启后只能强制清空，没有回退 */
@Composable
internal fun GuardSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Guard.on) }
    //0 空闲 1 开启警告 2 关闭警告 3 执行中 4 已清空
    var mode by remember { mutableStateOf(0) }
    var tip by remember { mutableStateOf("") }
    //开启进度：1 生成密钥 2 迁移旧数据 3 加密落盘 4 完成
    var step by remember { mutableIntStateOf(1) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_gd_title), fontSize = 14.sp, color = TextPrimary)
                Text(
                    guardStateText(),
                    fontSize = 11.sp,
                    color = if (on && !Guard.ready) DangerRed else TextSecondary
                )
            }
            Switch(
                checked = on,
                //开关是受控的：点「关」只弹警告，真清空成功才把状态改掉
                onCheckedChange = { want -> if (want != on) mode = if (want) 1 else 2 },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.se_gd_note),
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.se_gd_covers),
            fontSize = 10.sp,
            color = TextSecondary
        )
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 11.sp, color = TextSecondary)
        }
    }
    HostInOverlay(mode != 0) {
        when (mode) {
            1 -> GuardWarnDialog(
                cost = if (Guard.lastCostMs > 0) String.format(stringResource(R.string.tx_1da7adbb), Guard.lastCostMs / 1000f) else stringResource(R.string.tx_09e4e337),
                device = Guard.deviceName(),
                onCancel = { mode = 0 },
                onConfirm = {
                    step = 1
                    mode = 3
                    scope.launch {
                        val done = withContext(Dispatchers.IO) {
                            runCatching { Guard.enable { step = it } }.getOrDefault(false)
                        }
                        on = Guard.on
                        mode = 0
                        if (done) {
                            tip = ctx.getString(R.string.se_gd_tip_on)
                        } else {
                            Toast.makeText(ctx, R.string.se_gd_fail, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
            2 -> GuardOffDialog(
                onCancel = { mode = 0 },
                onConfirm = {
                    mode = 5
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { Guard.disable() } }
                        on = false
                        mode = 4
                    }
                }
            )
            3 -> GuardProgressDialog(step)
            5 -> GuardBusyDialog(stringResource(R.string.se_gd_busy))
            4 -> SimpleDialog(
                stringResource(R.string.se_gd_cleared_title),
                stringResource(R.string.se_gd_cleared_body),
                onClose = { android.os.Process.killProcess(android.os.Process.myPid()) }
            )
        }
    }
} //数据保障开关

/** 新增保护：数据输出脱敏与禁止截图录屏，两个开关都不碰数据，随时可关 */
@Composable
internal fun ProtectSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mask by remember { mutableStateOf(Cfg.maskOut) }
    var shot by remember { mutableStateOf(Cfg.noShot) }
    //脱敏开启前的强制警告
    var warn by remember { mutableStateOf(false) }
    //开启后的结果提示：服务端规则到底拉到没拉到
    var tip by remember { mutableStateOf("") }
    //当前规则规模：高 8 位键名数，低 8 位前缀数
    var shape by remember { mutableIntStateOf(Mask.shape()) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_pt_mask_t), fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (mask) stringResource(R.string.se_pt_mask_on) else stringResource(R.string.se_pt_mask_off),
                    fontSize = 11.sp,
                    color = if (mask) AccentBlue else TextSecondary
                )
            }
            Switch(
                checked = mask,
                //开要走 15 秒警告，关直接生效
                onCheckedChange = { want ->
                    if (want != mask) {
                        if (want) {
                            warn = true
                        } else {
                            Cfg.maskOut = false
                            mask = false
                            tip = ""
                        }
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_pt_mask_note), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.se_pt_mask_stream), fontSize = 10.sp, color = TextSecondary)
        if (mask) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.se_pt_mask_rules, (shape shr 8) and 0xFF, shape and 0xFF),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 11.sp, color = TextSecondary)
        }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_pt_shot_t), fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (shot) stringResource(R.string.se_pt_shot_on) else stringResource(R.string.se_pt_shot_off),
                    fontSize = 11.sp,
                    color = if (shot) AccentBlue else TextSecondary
                )
            }
            Switch(
                checked = shot,
                //先落盘再改窗口标记：进程被杀重启后也能按新状态恢复
                onCheckedChange = { want ->
                    Cfg.noShot = want
                    shot = want
                    (ctx as? MainActivity)?.applySecure(want)
                    //悬浮聊天窗、桌宠、气泡是独立窗口，Activity 的标记管不到它们
                    SecureWin.apply()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_pt_shot_note), fontSize = 11.sp, color = TextSecondary)
    }
    HostInOverlay(warn) {
        if (warn) {
            MaskWarnDialog(
                onCancel = { warn = false },
                onConfirm = {
                    warn = false
                    //setter 里会顺手关掉流式输出：逐字显示没法事后过滤
                    Cfg.maskOut = true
                    mask = true
                    tip = ""
                    scope.launch {
                        val done = withContext(Dispatchers.IO) {
                            runCatching { MaskRules.sync(force = true) }.getOrDefault(false)
                        }
                        shape = Mask.shape()
                        tip = ctx.getString(if (done) R.string.se_pt_mask_ok else R.string.se_pt_mask_fail)
                    }
                }
            )
        }
    }
} //新增保护

/** 一行链接：右侧显示地址，不可点时整行变灰 */
@Composable
internal fun LinkRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(enabled = enabled) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 11.sp,
            color = if (enabled) AccentBlue else TextSecondary,
            maxLines = 1
        )
    }
} //链接一行

/** 用系统浏览器打开链接 */
internal fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
} //打开链接

@Composable
internal fun McpSection(onEdit: (McpServer) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var tipOk by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.mcp_title), stringResource(R.string.mcp_sub))
    CardBox {
        val list = Mcp.list
        if (list.isEmpty()) {
            Text(stringResource(R.string.mcp_none), fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.mcp_none_sub), fontSize = 11.sp, color = TextSecondary)
        } else {
            list.forEach { s ->
                McpRow(
                    server = s,
                    count = Mcp.tools.count { it.serverId == s.id },
                    busy = busy == s.id,
                    onToggle = { Mcp.toggle(s.id, it) },
                    onRefresh = {
                        busy = s.id
                        tip = ""
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Mcp.refresh(s) }
                            val err = Mcp.lastError
                            busy = ""
                            tipOk = err.isEmpty() && n > 0
                            tip = if (tipOk) ctx.getString(R.string.mcp_ok, s.name, n)
                            else ctx.getString(
                                R.string.mcp_fail,
                                s.name,
                                err.ifBlank { ctx.getString(R.string.mcp_no_tool) }
                            )
                        }
                    },
                    onEdit = { onEdit(s) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.mcp_add), modifier = Modifier.height(38.dp)) {
                onEdit(McpServer(id = "", name = "", url = ""))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.mcp_count_all, Mcp.tools.size),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 10.sp, color = if (tipOk) OkGreen else DangerRed)
        }
    }
} //MCP 服务分区：列表 + 开关 + 刷新能力 + 新增

@Composable
internal fun McpRow(
    server: McpServer,
    count: Int,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(server.name.ifBlank { server.url }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                stringResource(
                    R.string.mcp_row_sub,
                    if (server.kind == McpServer.KIND_REST) stringResource(R.string.mcp_kind_rest)
                    else stringResource(R.string.mcp_kind_mcp),
                    server.url,
                    count
                ),
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        PolicyChip(
            if (busy) stringResource(R.string.mcp_testing) else stringResource(R.string.mcp_refresh),
            busy
        ) { if (!busy) onRefresh() }
        Spacer(Modifier.width(6.dp))
        PolicyChip(stringResource(R.string.mcp_edit), false) { onEdit() }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = server.on,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
    }
} //MCP 服务一行

@Composable
internal fun McpDialog(
    server: McpServer,
    onSave: (McpServer) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(server.id) { mutableStateOf(server.name) }
    var url by remember(server.id) { mutableStateOf(server.url) }
    var token by remember(server.id) { mutableStateOf(server.token) }
    var kind by remember(server.id) { mutableStateOf(server.kind) }
    var tip by remember { mutableStateOf("") }
    var tipOk by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    ScrimCard(onDismiss = onClose) {
        Text(
            if (server.id.isBlank()) stringResource(R.string.mcp_new_title) else stringResource(R.string.mcp_edit_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Column(Modifier.weight(1f, fill = false).heightIn(max = 340.dp).verticalScroll(glassScrollState())) {
            Field(stringResource(R.string.mcp_name), name, { name = it }, placeholder = stringResource(R.string.mcp_name_ph))
            Field(stringResource(R.string.mcp_url), url, { url = it }, placeholder = stringResource(R.string.mcp_url_ph))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.mcp_kind), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PolicyChip(stringResource(R.string.mcp_kind_mcp), kind == McpServer.KIND_MCP) {
                    kind = McpServer.KIND_MCP
                }
                Spacer(Modifier.width(6.dp))
                PolicyChip(stringResource(R.string.mcp_kind_rest), kind == McpServer.KIND_REST) {
                    kind = McpServer.KIND_REST
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.mcp_kind_hint), fontSize = 10.sp, color = TextSecondary)
            Field(
                stringResource(R.string.mcp_token),
                token,
                { token = it },
                placeholder = stringResource(R.string.mcp_token_ph),
                secret = true
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                if (testing) stringResource(R.string.mcp_testing) else stringResource(R.string.mcp_test),
                modifier = Modifier.height(38.dp)
            ) {
                if (testing) return@GhostButton
                if (url.isBlank()) {
                    tipOk = false
                    tip = ctx.getString(R.string.mcp_need_url)
                    return@GhostButton
                }
                testing = true
                tip = ""
                scope.launch {
                    //测试只拉清单不写缓存，避免拿临时 id 污染能力表
                    val tmp = McpServer(server.id.ifBlank { "tmp" }, name, url.trim(), token.trim(), kind, true)
                    val n = withContext(Dispatchers.IO) { Mcp.listTools(tmp).size }
                    val err = Mcp.lastError
                    testing = false
                    tipOk = err.isEmpty() && n > 0
                    tip = if (tipOk) ctx.getString(R.string.mcp_test_ok, n)
                    else ctx.getString(R.string.mcp_test_fail, err.ifBlank { ctx.getString(R.string.mcp_no_tool) })
                }
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(tip, fontSize = 10.sp, color = if (tipOk) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (server.id.isNotBlank()) {
                DialogButton(stringResource(R.string.se_143), DangerRed, DangerRedSoft) { onDelete() }
            }
            DialogButton(stringResource(R.string.se_142), TextSecondary, SurfaceMuted) { onClose() }
            DialogButton(stringResource(R.string.se_264), androidx.compose.ui.graphics.Color.White, AccentBlue) {
                if (url.isBlank()) {
                    tipOk = false
                    tip = ctx.getString(R.string.mcp_need_url)
                } else {
                    onSave(
                        server.copy(
                            name = name.trim().ifBlank { url.trim() },
                            url = url.trim(),
                            token = token.trim(),
                            kind = kind
                        )
                    )
                }
            }
        }
    }
} //MCP 服务编辑弹窗

/** 弹窗底部等宽文字按钮 */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.DialogButton(
    text: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Text(
        text,
        fontSize = 12.sp,
        color = fg,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .background(bg, RoundedCornerShape(10.dp))
            .tap { onClick() }
            .padding(vertical = 11.dp)
    )
} //弹窗按钮

@Composable
internal fun SkillRow(s: Skill, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val nm = Skills.displayName(ctx, s).ifBlank { ctx.getString(R.string.sk_unnamed) }
            Text(nm, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            Text(
                if (s.desc.isBlank()) stringResource(R.string.se2_skill_len, s.body.length)
                else Skills.displayDesc(ctx, s),
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Switch(
            checked = s.on,
            onCheckedChange = { Skills.toggle(s.id, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.se_256),
            fontSize = 11.sp,
            color = AccentBlue,
            modifier = Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                .tap { onEdit() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
} //技能一行

@Composable
internal fun SkillDialog(
    skill: Skill,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var name by remember(skill.id) { mutableStateOf(skill.name) }
    var desc by remember(skill.id) { mutableStateOf(skill.desc) }
    var body by remember(skill.id) { mutableStateOf(skill.body) }
    ScrimCard {
        Text(
            if (skill.id.isBlank()) stringResource(R.string.se_233) else stringResource(R.string.se_257),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f, fill = false).heightIn(max = 340.dp).verticalScroll(glassScrollState())) {
            Field(stringResource(R.string.se_258), name, { name = it }, placeholder = stringResource(R.string.se_259))
            Field(stringResource(R.string.se_260), desc, { desc = it }, placeholder = stringResource(R.string.se_261))
            Field(
                stringResource(R.string.se_262),
                body,
                { body = it },
                placeholder = stringResource(R.string.se_263),
                singleLine = false,
                minLines = 6
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (skill.id.isNotBlank()) {
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
                    .tap { onSave(name, desc, body) }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //技能编辑弹窗

