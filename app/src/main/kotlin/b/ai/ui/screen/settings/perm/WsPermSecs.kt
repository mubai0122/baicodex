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

package b.ai.ui.screen.settings.perm

import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.kit.*
import b.ai.ui.screen.settings.model.*
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

/** 工作区信息：数据根下的哈希目录 + 会话绑定的目录，未被任何会话绑定的可以一键删除 */
@Composable
internal fun WsSection() {
    var list by remember { mutableStateOf<List<Workspace.WsInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tip by remember { mutableStateOf("") }
    //点一次「删除」先变成「确认删除 / 取消」，避免手滑删掉目录
    var confirm by remember { mutableStateOf<String?>(null) }
    //旧版散在数据根下的工作区个数 / 正在迁移 / 迁移进度文案（"正在迁移 1/7：xxx"）
    var legacy by remember { mutableStateOf(0) }
    var migrating by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    suspend fun reload(force: Boolean = false) {
        loading = true
        list = withContext(Dispatchers.IO) {
            runCatching { Workspace.listAll(force) }.getOrDefault(emptyList())
        }
        legacy = withContext(Dispatchers.IO) {
            runCatching { Workspace.legacyDirs().size }.getOrDefault(0)
        }
        loading = false
    } //重载列表与待迁移数量
    LaunchedEffect(Unit) { reload() }
    //一键清理跑完会让 ver 加一，这里跟着重扫，列表立刻变成清理后的样子
    LaunchedEffect(WsPurge.ver) { if (WsPurge.ver > 0) reload() }

    CardBox {
        //旧版工作区还挂在数据根下：给一块带进度的迁移区，搬完自动刷新列表
        if (legacy > 0 || migrating) {
            Text(stringResource(R.string.ws_mig_t), fontSize = 13.sp, color = TextPrimary)
            Text(stringResource(R.string.ws_mig_d), fontSize = 10.sp, color = TextSecondary)
            if (prog.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(prog, fontSize = 10.sp, color = AccentBlue)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                stringResource(R.string.ws_mig_btn, legacy),
                modifier = Modifier.height(36.dp)
            ) {
                if (migrating) return@GhostButton
                migrating = true
                prog = ""
                scope.launch {
                    val err = withContext(Dispatchers.IO) {
                        runCatching {
                            Workspace.migrate { i, total, name, files, bytes ->
                                //回调用的是 IO 线程，进度文案得切回主线程再写状态
                                scope.launch {
                                    prog = ctx.getString(
                                        R.string.ws_mig_step, i, total, name, files,
                                        Workspace.human(bytes)
                                    )
                                }
                            }
                        }.getOrElse { "copy:$it" }
                    }
                    migrating = false
                    prog = when (err) {
                        null -> ctx.getString(R.string.ws_mig_done, legacy)
                        "none" -> ctx.getString(R.string.ws_mig_none)
                        else -> ctx.getString(R.string.ws_mig_fail, err.orEmpty().removePrefix("copy:"))
                    }
                    reload()
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            loading -> Text(stringResource(R.string.se_133), fontSize = 11.sp, color = TextSecondary)
            list.isEmpty() -> Text(
                stringResource(R.string.se_134),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        list.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        //显式加括号：Kotlin 里 "+ if/else" 的 else 分支会把后续 + 表达式一起吞掉
                        w.name +
                            (if (w.isDefault) stringResource(R.string.ws_badge_def) else "") +
                            (if (w.current) stringResource(R.string.se_135) else ""),
                        fontSize = 13.sp,
                        color = if (w.current) AccentBlue else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        stringResource(R.string.se2_ws_files, w.files, Workspace.human(w.bytes)) +
                            (if (w.unbound) stringResource(R.string.se_137) else stringResource(R.string.se2_ws_bound)) +
                            w.sessions.take(2).joinToString("、") +
                            (if (w.sessions.size > 2) stringResource(R.string.se2_ws_more, w.sessions.size) else ""),
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Text(w.path, fontSize = 9.sp, color = TextSecondary, maxLines = 1)
                }
                if (confirm == w.path) {
                    Text(
                        stringResource(R.string.se_140),
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier
                            .background(DangerRedSoft, RoundedCornerShape(8.dp))
                            .tap {
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) { Workspace.delete(ctx, w.path) }
                                    tip = err ?: ctx.getString(R.string.se2_ws_deleted, w.name)
                                    confirm = null
                                    reload()
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.se_142),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), gate = 0.dp)
                            .tap { confirm = null }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                } else if (w.unbound && !w.current && w.exists && !w.isDefault) {
                    Text(
                        stringResource(R.string.se_143),
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier
                            .background(DangerRedSoft, RoundedCornerShape(8.dp))
                            .tap { confirm = w.path }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        //还有没被任何会话绑定的目录才给清理入口，全都绑上了就整块不出现
        val idle = list.count { it.unbound && !it.current && it.exists && !it.isDefault }
        if (idle > 0) {
            GhostButton(
                stringResource(R.string.wsq_btn, idle),
                modifier = Modifier.height(38.dp)
            ) { WsPurge.open = true }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            //重新扫一遍：绕过多级缓存，真去盘上数一遍
            GhostButton(stringResource(R.string.se_144), modifier = Modifier.height(38.dp)) { scope.launch { reload(true) } }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.se_145),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //工作区信息

internal fun LazyListScope.permTab() {
    item {
    SectionTitle(stringResource(R.string.se_025), stringResource(R.string.se_146))
    CardBox {
        WorkMode.entries.forEach { m ->
            ChoiceRow(stringResource(m.labelRes), stringResource(m.descRes), Cfg.workMode == m) { Cfg.workMode = m }
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_027), stringResource(R.string.se_147))
    CardBox {
        val ctx = LocalContext.current
        var missing by remember { mutableStateOf(Perms.missing(ctx)) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { missing = Perms.missing(ctx) }
        Text(stringResource(R.string.se_148), fontSize = 14.sp, color = TextPrimary)
        Text(
            if (missing.isEmpty()) stringResource(R.string.se_149)
            else stringResource(R.string.se_150) + missing.joinToString("、") { Perms.label(ctx, it) },
            fontSize = 11.sp,
            color = if (missing.isEmpty()) OkGreen else DangerRed
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (missing.isEmpty()) stringResource(R.string.se_028) else stringResource(R.string.se_151), modifier = Modifier.height(38.dp)) {
                launcher.launch(Perms.runtime().toTypedArray())
            }
            GhostButton(stringResource(R.string.se_152), modifier = Modifier.height(38.dp)) { Perms.openAppSettings(ctx) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.se_153) + if (Perms.allFiles()) stringResource(R.string.se_154) else stringResource(R.string.se_155),
            fontSize = 11.sp,
            color = if (Perms.allFiles()) OkGreen else TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        GhostButton(stringResource(R.string.se_156), modifier = Modifier.height(38.dp)) { Perms.openAllFiles(ctx) }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_7fc624f8), stringResource(R.string.tx_ac0cdee7))
    CardBox {
        val specials = Perms.specials(Cfg.workMode)
        //无障碍卡一律出现：Shizuku / Root 模式下它是「一键激活」入口，不需要去系统页手点
        A11yPermCard("AI 读屏、点屏靠这个。没开也能聊，只是屏幕类工具会跑不通。")
        Spacer(Modifier.height(12.dp))
        SpecialPermCard(specials.filter { it != Perms.SP_A11Y })
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle("Shizuku", stringResource(R.string.tx_bd7451f2))
    CardBox {
        val ctx = LocalContext.current
        var st by remember { mutableStateOf(Shiz.probe(ctx)) }
        var msg by remember { mutableStateOf<String?>(null) }
        var note by remember { mutableStateOf<String?>(null) }
        Text(st.desc, fontSize = 12.sp, color = if (st.ok) OkGreen else TextSecondary)
        if (msg != null) {
            Spacer(Modifier.height(6.dp))
            Text(msg!!, fontSize = 11.sp, color = DangerRed)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(stringResource(R.string.tx_f515f8fb)) { st = Shiz.probe(ctx) }
            GhostButton(stringResource(R.string.tx_5e34a485)) {
                Shiz.ask { r ->
                    msg = when (r) {
                        Shiz.Perm.GRANTED -> null
                        Shiz.Perm.NEVER_ASK -> ctx.getString(R.string.shiz_manual)
                        Shiz.Perm.NO_SERVICE -> ctx.getString(R.string.shiz_no_service)
                        Shiz.Perm.ERROR -> ctx.getString(R.string.shiz_timeout)
                        else -> ctx.getString(R.string.shiz_denied)
                    }
                    st = Shiz.probe(ctx)
                }
            }
            GhostButton(stringResource(R.string.shiz_open_app)) {
                Shiz.openManager(ctx)
                st = Shiz.probe(ctx)
            }
        }
        Spacer(Modifier.height(10.dp))
        //Shizuku 有 shell，直接写 secure 设置就能把无障碍挂回去，不必每次进系统设置手点
        CapSwitch(
            stringResource(R.string.se_337),
            when {
                !Cfg.autoA11y -> stringResource(R.string.se_338)
                !st.ok -> stringResource(R.string.se_339)
                else -> stringResource(R.string.se_340)
            },
            Cfg.autoA11y
        ) { on ->
            Cfg.autoA11y = on
            if (on) {
                msg = null
                note = null
                Thread({
                    val ok = b.ai.device.sys.a11y.AutoA11y.run(ctx)
                    val doneText = ctx.getString(R.string.se_357)
                    val failText = ctx.getString(R.string.se_363)
                    st = Shiz.probe(ctx)
                    if (ok) note = doneText else msg = failText
                }, "auto-a11y").apply { isDaemon = true }.start()
            } else {
                note = null
            }
        }
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(note!!, fontSize = 11.sp, color = OkGreen)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_029))
    CardBox {
        val ctx = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_157), fontSize = 14.sp, color = TextPrimary)
                //探测在后台线程跑：每秒把结果刷一次，否则 UI 会一直停在「检测中…」
                var zone by remember { mutableStateOf(Router.dataZone(ctx)) }
                var zoneOk by remember { mutableStateOf(Router.zoneOk()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        zone = Router.dataZone(ctx)
                        zoneOk = Router.zoneOk()
                        delay(1000)
                    }
                }
                Text(
                    stringResource(R.string.se_zone_cur, zone),
                    fontSize = 11.sp,
                    color = if (zoneOk) OkGreen else TextSecondary
                )
            }
            Switch(
                checked = Cfg.zeroWidth,
                onCheckedChange = { Cfg.zeroWidth = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.se_160) +
                stringResource(R.string.se_161),
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(stringResource(R.string.se_162), modifier = Modifier.height(38.dp)) { Router.reprobe() }
    }
    }
} //权限分类（已并入对话分类，入口不再单独占一个 tab）

