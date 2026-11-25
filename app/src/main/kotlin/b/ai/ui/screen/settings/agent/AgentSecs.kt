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

package b.ai.ui.screen.settings.agent

import b.ai.ui.screen.settings.about.*
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

//扩展分类：一张卡一个 item，没滚到的卡不组合（工具权限那张卡最重，几十个下拉）
internal fun LazyListScope.extAutoSec(
    onNewTask: () -> Unit,
    onNewFlow: () -> Unit,
    onNewTrigger: () -> Unit
) {
    //运行总览：三类自动化各自的开关状态，外加一键全停 / 全恢复
    item {
        SectionTitle(stringResource(R.string.tx_85067db3), stringResource(R.string.tx_3ab3186c))
        CardBox {
            val tasks0 = Schedules.list
            val flows0 = Flows.list
            val rules0 = Triggers.list
            Text(
                "定时任务 " + tasks0.count { it.on } + "/" + tasks0.size +
                    " · 工作流 " + flows0.count { it.on } + "/" + flows0.size +
                    " · 通知触发 " + rules0.count { it.on } + "/" + rules0.size,
                fontSize = 13.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(stringResource(R.string.tx_e5c4f1fc), modifier = Modifier.weight(1f).height(38.dp)) {
                    //先取快照再逐个切：toggle 会改 state，边遍历边改会漏
                    tasks0.filter { it.on }.forEach { Schedules.toggle(it.id, false) }
                    flows0.filter { it.on }.forEach { Flows.toggle(it.id, false) }
                    rules0.filter { it.on }.forEach { Triggers.toggle(it.id, false) }
                }
                GhostButton(stringResource(R.string.tx_f4590f7d), modifier = Modifier.weight(1f).height(38.dp)) {
                    tasks0.filterNot { it.on }.forEach { Schedules.toggle(it.id, true) }
                    flows0.filterNot { it.on }.forEach { Flows.toggle(it.id, true) }
                    rules0.filterNot { it.on }.forEach { Triggers.toggle(it.id, true) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.tx_950a94dd),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    item {
        SectionTitle(stringResource(R.string.se_044), stringResource(R.string.se2_task_sub))
        CardBox {
            val ctx = LocalContext.current
            val tasks = Schedules.list
            val on = tasks.count { it.on }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (tasks.isEmpty()) stringResource(R.string.se2_task_none) else stringResource(R.string.se2_task_n, tasks.size, on),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (tasks.isEmpty()) stringResource(R.string.se2_task_hint)
                        else tasks.filter { it.on }.take(2)
                            .joinToString("；") { "${if (it.daily) "每天 " else ""}${Alarms.fmt(it.at)} ${it.prompt.replace("\n", " ").take(16)}" }
                            .ifBlank { ctx.getString(R.string.se2_task_off) },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                GhostButton(stringResource(R.string.se2_manage)) { onNewTask() }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_228526dc), stringResource(R.string.tx_7bb29de8))
        CardBox {
            val flows = Flows.list
            val enabled = flows.count { it.on }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "共 " + flows.size + " 条，启用 " + enabled + " 条",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (flows.isEmpty()) stringResource(R.string.tx_df74b581)
                        else flows.take(2).joinToString("；") { f ->
                            f.name + "（" + f.steps.size + " 步" +
                                (if (f.at > 0L) " · " + Alarms.fmt(f.at) else "") + "）"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                GhostButton(stringResource(R.string.tx_86454fc7)) { onNewFlow() }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.ntg_title), stringResource(R.string.ntg_sub))
        CardBox {
            val rules = Triggers.list
            val on = rules.count { it.on }
            val any = stringResource(R.string.ntg_any)
            val all = stringResource(R.string.ntg_allkw)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (rules.isEmpty()) stringResource(R.string.ntg_none)
                        else stringResource(R.string.ntg_count, rules.size, on),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (rules.isEmpty()) stringResource(R.string.ntg_hint)
                        else rules.filter { it.on }.take(2)
                            .joinToString("；") { t -> t.app.ifBlank { any } + " · " + t.keyword.ifBlank { all } }
                            .ifBlank { stringResource(R.string.ntg_offall) },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
                GhostButton(stringResource(R.string.ntg_manage)) { onNewTrigger() }
            }
        }
    }
    //到点能不能跑起来：定时任务三件套（精确闹钟 / 通知 / 后台保活）缺一个就会「设了没动静」
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_0e2af5f5), stringResource(R.string.tx_5b921cf1))
        CardBox {
            val c = LocalContext.current
            val exact = Alarms.canExact(c)
            val noti = Build.VERSION.SDK_INT < 33 ||
                Perms.granted(c, android.Manifest.permission.POST_NOTIFICATIONS)
            val alive = Perms.batteryFree(c)
            SelfCheckRow(
                stringResource(R.string.tx_15ec4b98),
                if (exact) stringResource(R.string.tx_2627160e) else stringResource(R.string.tx_06972491),
                exact
            ) { Alarms.openExactAlarmSettings(c) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_5660bcd2),
                if (noti) stringResource(R.string.tx_a6a648c1) else stringResource(R.string.tx_8508566e),
                noti
            ) { Perms.openNotiSettings(c) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_2911c1e5),
                if (alive) stringResource(R.string.tx_cc49b1fd) else stringResource(R.string.tx_b90dde67),
                alive
            ) { Perms.openSpecial(c, Perms.SP_BATTERY) }
        }
    }
    //接下来要跑：定时任务与已排期的工作流按时间合并，只列前三条
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_9f2e930a), stringResource(R.string.tx_02895542))
        CardBox {
            val soon = (Schedules.active.map { it.at to it.prompt.replace("\n", " ").take(18) } +
                Flows.list.filter { it.on && it.at > 0L }.map { it.at to ("工作流 · " + it.name) })
                .sortedBy { it.first }
                .take(3)
            if (soon.isEmpty()) {
                Text(stringResource(R.string.tx_70607644), fontSize = 12.sp, color = TextSecondary)
            } else {
                soon.forEachIndexed { i, (at, name) ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    Text(Alarms.fmt(at) + "  ·  " + name, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
    //任务通知与灵动岛：原挂在「外观」页，本质是任务通知的显示策略，跟自动化放一起
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_709d84ce), stringResource(R.string.tx_a71af5ad))
        CardBox {
            val c = LocalContext.current
            val notiOk = Build.VERSION.SDK_INT < 33 ||
                Perms.granted(c, android.Manifest.permission.POST_NOTIFICATIONS)
            val aliveOk = Perms.batteryFree(c)
            SelfCheckRow(
                stringResource(R.string.tx_846cdaf0),
                if (notiOk) stringResource(R.string.tx_c4da04aa) else stringResource(R.string.tx_b324c1b6),
                notiOk
            ) { Perms.openNotiSettings(c) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_2911c1e5),
                if (aliveOk) stringResource(R.string.tx_3a605fa6) else stringResource(R.string.tx_2474be5f),
                aliveOk
            ) { Perms.openSpecial(c, Perms.SP_BATTERY) }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tx_3aaccc6e, Vendor.rom.label), fontSize = 10.sp, color = TextSecondary)
            //通道级问题自查项覆盖不到（通知分类被关 / 厂商焦点通知没开），原样保留这条说明
            val blocked = Island.blockedReason()
            if (blocked != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    blocked,
                    fontSize = 11.sp,
                    color = DangerRed,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(8.dp), tint = DangerRedSoft)
                        .tap { Perms.openNotiSettings(c) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
                Text(
                    stringResource(R.string.tx_af29b0db),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.tx_8280b5ae),
                stringResource(R.string.tx_12c3b1d7),
                Cfg.islandOn
            ) { Cfg.islandOn = it }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.tx_b7a7dafe),
                stringResource(R.string.tx_edd7189b),
                Cfg.islandDone
            ) { Cfg.islandDone = it }
            FollowBlock(Cfg.islandDone) {
                Spacer(Modifier.height(8.dp))
                GlassSlider(
                    stringResource(R.string.tx_110dd655),
                    stringResource(R.string.tx_a6f0b1dc, Cfg.islandDoneSec),
                    Cfg.islandDoneSec.toFloat(),
                    0f..300f,
                    step = 10f
                ) { Cfg.islandDoneSec = it.toInt() }
                Text(stringResource(R.string.tx_8c7432b9), fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
} //自动化子页

/**
 * 一行自查：状态徽标（已通过 / 未通过）+ 名称 + 说明；未通过时右侧给「去开启」，点了跳系统页。
 * 徽标用文字而不是纯色圆点：圆点看不出到底是「装饰」还是「检查结果」。
 */
@Composable
internal fun SelfCheckRow(name: String, desc: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) stringResource(R.string.tx_ecfa64c1) else stringResource(R.string.tx_4fcdbfe5),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (ok) OkGreen else DangerRed,
            modifier = Modifier
                .background(if (ok) OkGreen.copy(alpha = 0.12f) else DangerRedSoft, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, color = TextPrimary)
            Text(desc, fontSize = 11.sp, color = if (ok) TextSecondary else DangerRed)
        }
        if (!ok) {
            Text(
                stringResource(R.string.tx_5e213ddb),
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier.tap { onFix() }.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
} //自查一行

//接入子页：A2A 节点、搜索平台、MCP 服务
internal fun LazyListScope.extLinkSec(
    onOpenA2a: () -> Unit,
    onEditMcp: (McpServer) -> Unit
) {
    item {
        SectionTitle("A2A", stringResource(R.string.tx_6e63dd3c))
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "本机服务：" + if (A2aSrv.running) stringResource(R.string.tx_d9524747) + A2aSrv.baseUrl() else stringResource(R.string.tx_b15d9127),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (A2aNodes.list.isEmpty()) stringResource(R.string.tx_c51ed4da)
                        else stringResource(R.string.tx_ea9bc057) + A2aNodes.list.size + stringResource(R.string.tx_a3af3303) +
                            A2aNodes.list.take(2).joinToString("；") { it.name },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                GhostButton(stringResource(R.string.tx_231fdea9)) { onOpenA2a() }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_046), stringResource(R.string.se2_search_sub))
        CardBox {
            val providers = Search.providers
            val sel = Cfg.searchProvider
            if (providers.isEmpty()) {
                Text(stringResource(R.string.se2_search_empty), fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.se2_search_empty2),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                SelectField(
                    stringResource(R.string.se_046),
                    providers.firstOrNull { it.id == sel }?.label ?: stringResource(R.string.se2_search_off),
                    listOf("") + providers.map { it.id },
                    optionLabel = { id -> providers.firstOrNull { it.id == id }?.label ?: stringResource(R.string.se2_search_off) },
                    isSelected = { sel == it },
                    onSelect = { Cfg.searchProvider = it }
                )
                val cur = Search.selected
                if (cur != null) {
                    Spacer(Modifier.height(8.dp))
                    if (cur.fields.isEmpty()) {
                        Text(stringResource(R.string.se2_search_needless, cur.label), fontSize = 12.sp, color = OkGreen)
                    } else {
                        cur.fields.forEach { f ->
                            Field(
                                label = "${cur.label} ${f.label}",
                                value = Cfg.searchCfg(cur.id, f.id),
                                onValueChange = { Cfg.setSearchCfg(cur.id, f.id, it) },
                                placeholder = f.hint,
                                secret = f.id == "key"
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(cur.hint, fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    val miss = Search.missing()
                    Text(
                        if (miss == null) stringResource(R.string.se2_search_ok)
                        else stringResource(R.string.se2_search_miss, miss.label),
                        fontSize = 10.sp,
                        color = if (miss == null) OkGreen else DangerRed
                    )
                }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        McpSection(onEdit = onEditMcp)
    }
    } //接入子页

//技能与角色子页：角色卡、系统提示词、技能、技能包
internal fun LazyListScope.extAgentSec(
    onEdit: (Skill) -> Unit,
    onEditPersona: (Persona) -> Unit,
    onEditPack: (SkillPack) -> Unit
) {
    item {
        SectionTitle(stringResource(R.string.se_047), stringResource(R.string.se2_persona_sub))
        CardBox {
            ChoiceRow(stringResource(R.string.se2_persona_none), stringResource(R.string.se2_persona_none_sub), Cfg.personaId.isBlank()) { Agent.setPersona("") }
            Personas.list.forEach { p ->
                PersonaRow(
                    p = p,
                    selected = Cfg.personaId == p.id,
                    onSelect = { Agent.setPersona(p.id) },
                    onEdit = { onEditPersona(p) }
                )
            }
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.se2_persona_add), modifier = Modifier.height(38.dp)) {
                onEditPersona(Persona(id = "", name = ""))
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_048), stringResource(R.string.se2_prompt_sub))
        CardBox {
            Field(
                label = stringResource(R.string.se2_prompt_label),
                value = Cfg.systemPrompt,
                onValueChange = { Cfg.systemPrompt = it },
                placeholder = stringResource(R.string.se_226),
                secret = false,
                singleLine = false,
                minLines = 3
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (Cfg.systemPrompt.isBlank()) stringResource(R.string.se_227)
                else stringResource(R.string.se2_prompt_len, Cfg.systemPrompt.length),
                fontSize = 10.sp,
                color = if (Cfg.systemPrompt.isBlank()) TextSecondary else OkGreen
            )
            Spacer(Modifier.height(6.dp))
            //花括号参数：只在这里注入，点一下追加到提示末尾；拼系统提示时替换成真实值，聊天输入不受影响
            Text(stringResource(R.string.tx_f05a0996), fontSize = 10.sp, color = TextSecondary)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PromptVars.ALL.forEach { v ->
                    Text(
                        v.token,
                        fontSize = 10.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                            .tap {
                                //分隔符按需补：空提示直接写，末尾是空白就只追加 token
                                val cur = Cfg.systemPrompt
                                Cfg.systemPrompt = when {
                                    cur.isBlank() -> v.token
                                    cur.last().isWhitespace() -> cur + v.token
                                    else -> cur + " " + v.token
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.tx_2ae34d78),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            //拼接顺序：角色卡自带提示与这里写的预设谁在前；默认角色在前
            SelectField(
                stringResource(R.string.tx_28d68b24),
                if (Cfg.promptOrder == 1) stringResource(R.string.tx_1cf2d4cf) else stringResource(R.string.tx_06d23d3f),
                listOf(0, 1),
                optionLabel = { if (it == 1) stringResource(R.string.tx_1cf2d4cf) else stringResource(R.string.tx_06d23d3f) },
                isSelected = { Cfg.promptOrder == it },
                onSelect = { Cfg.promptOrder = it }
            )
            Text(
                "两段是叠加关系，不是二选一：角色卡里的提示与这里的预设都会进系统提示，" +
                    "这一段只决定谁在前面。角色提示写得强势时选「自定义在前」，把人设压不住的那部分放到前面。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_049), stringResource(R.string.se_229))
        CardBox {
            val list = Skills.list
            if (list.isEmpty()) {
                Text(stringResource(R.string.se_230), fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.se_231) +
                        stringResource(R.string.se_232),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                list.forEach { s ->
                    SkillRow(s, onEdit = { onEdit(s) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.se_233), modifier = Modifier.height(38.dp)) {
                    onEdit(Skill("", "", "", "", true))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.se2_skill_enabled, Skills.enabled.size, list.size),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.skp_t), stringResource(R.string.skp_d))
        SkillPackBox(onEdit = onEditPack)
    }
    } //技能与角色子页

//工具权限子页：每个工具一条允许/询问/拒绝
internal fun LazyListScope.extToolSec() {
    item {
        SectionTitle(stringResource(R.string.se_050), stringResource(R.string.se_235))
        //MCP 能力不列在这里：它们由 MCP 服务自己拉取，到「扩展 → MCP 服务」里单独管
        val tools = Tools.all.filterNot { it.startsWith(Tools.MCP) }
        //一次只组合前 12 条：几十个下拉挤在同一个 item 里，进页面就要全量测量一遍
        var more by remember { mutableStateOf(false) }
        CardBox {
            (if (more) tools else tools.take(TOOL_PAGE)).forEach { tool ->
                val pol = Cfg.policy(tool)
                SelectField(
                    Tools.label(tool),
                    when (pol) {
                        "allow" -> stringResource(R.string.se_239)
                        "deny" -> stringResource(R.string.se_240)
                        else -> stringResource(R.string.se_238)
                    },
                    listOf("ask", "allow", "deny"),
                    desc = if (Tools.isHighRisk(tool)) stringResource(R.string.se2_risk_high, tool)
                    else stringResource(R.string.se2_risk_normal, tool),
                    optionLabel = {
                        when (it) {
                            "allow" -> stringResource(R.string.se_239)
                            "deny" -> stringResource(R.string.se_240)
                            else -> stringResource(R.string.se_238)
                        }
                    },
                    isSelected = { pol == it },
                    onSelect = { Cfg.setPolicy(tool, it) }
                )
            }
            if (!more && tools.size > TOOL_PAGE) {
                Spacer(Modifier.height(6.dp))
                GhostButton(stringResource(R.string.se_tools_more, tools.size - TOOL_PAGE)) {
                    more = true
                }
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_c0a99954), stringResource(R.string.tx_bb3f978b))
        val ctx = LocalContext.current
        var wipeMsg by remember { mutableStateOf("") }
        var asking by remember { mutableStateOf(false) }
        HostInOverlay(asking) {
            if (asking) {
                ScrimCard(onDismiss = { asking = false }) {
                    Text(stringResource(R.string.tx_9ca1cb1f), fontSize = 16.sp, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "会清掉 WebView 的 Cookie、WebStorage、缓存与表单记录，之后各网站都要重新登录。" +
                            "音乐专区的登录态在加密库里，不受影响。",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            stringResource(R.string.tx_625fb26b),
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.tap { asking = false }.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        Text(
                            stringResource(R.string.tx_0a60a66c),
                            fontSize = 13.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .tap {
                                    asking = false
                                    wipeMsg = WebWipe.clearAll(ctx)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        CardBox {
            Text(
                "会清 WebView 的 Cookie、WebStorage、缓存与表单记录。清完各网站要重新登录；" +
                    "音乐专区的登录态存在加密库里，不受影响，要清请到对应平台卡片单独清。",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_2898cdb7)) { asking = true }
            if (wipeMsg.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(wipeMsg, fontSize = 11.sp, color = TextPrimary)
            }
        }
    }
} //工具权限子页

//工作区子页：数据根目录、本机 Linux 环境
internal fun LazyListScope.extWsSec() {
    //前置检查：工作区能绑到共享存储的前提是「全部文件访问」，没给就会绑不进任意目录
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_59fee9a5), stringResource(R.string.tx_0450050c))
        CardBox {
            val c = LocalContext.current
            val allOk = Perms.allFiles()
            SelfCheckRow(
                stringResource(R.string.tx_3ceeb66d),
                if (allOk) stringResource(R.string.tx_f3b58907) else stringResource(R.string.tx_119c6296),
                allOk
            ) { Perms.openAllFiles(c) }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tx_90d5ce80),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    item {
        //设置页内联整张工作区卡：数据根、绑定路径、目录统计与绑定操作都在这里，
        //「浏览」按钮走 WorkspacePick，由设置页的弹窗挂载点接住并弹出目录选择器
        WorkspaceSection(onBrowse = { WorkspacePick.open = true })
    }
} //工作区子页

/** Linux 沙箱单独一页：与工作区解耦，整页就是一个终端 + 一键装开发环境 */
internal fun LazyListScope.extLinuxSec() {
    item {
        LinuxTermPage()
    }
} //Linux 沙箱子页

/** dsh 插件广场单独一页：列出本机真实装载行，可开关（改动后需重启内核生效） */
internal fun LazyListScope.extDshPlugSec() {
    item {
        DshPluginsPage()
    }
} //dsh 插件广场子页

/**
 * dsh 引擎入口：这里只给一行「打开」，真正的 dsh 界面是全屏页（MainActivity 的 screen="dsh"）。
 * 嵌入 LazyColumn 会把 WebView 塞进滚动区，滚动与输入法都会打架。
 */
internal fun LazyListScope.extDshEngineSec(onOpen: () -> Unit) {
    item {
        SectionTitle(stringResource(R.string.dh_engine_title), stringResource(R.string.dh_engine_sub))
        CardBox {
            Text(
                stringResource(R.string.dh_engine_desc),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.dh_engine_open), modifier = Modifier.fillMaxWidth()) { onOpen() }
        }
    }
    item {
        Spacer(Modifier.height(10.dp))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dh_engine_toggle), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (Cfg.dshEngine) stringResource(R.string.dh_engine_on) else stringResource(R.string.dh_engine_off),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = Cfg.dshEngine,
                    onCheckedChange = { Cfg.dshEngine = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
        }
    }
} //dsh 引擎子页

//还剩文件沙箱一个专区的计划清单：页面上先讲清会有什么，别点进去一片空白
/** 文件沙箱专区的计划清单：存资源 id，交给 extPlanSec 在 Composable 里解析 */
internal val EX_BOX_PLAN = listOf(
    R.string.tx_a39e54aa,
    R.string.tx_ccd94af0,
    R.string.tx_423b0645
)

/** 专区计划页：清单 + 一句状态说明，等实现落地后替换成真控件 */
internal fun LazyListScope.extPlanSec(titleRes: Int, subRes: Int, points: List<Int>) {
    item {
        SectionTitle(stringResource(titleRes), stringResource(subRes))
        CardBox {
            points.forEachIndexed { i, res ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                Text("· " + stringResource(res), fontSize = 12.sp, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.se_hub_soon), fontSize = 10.sp, color = TextSecondary)
        }
    }
} //专区计划页
/** 技能包：外部目录里有哪些包、哪些函数校验没过；改完包点重新扫描 */
@Composable
internal fun SkillPackBox(onEdit: (SkillPack) -> Unit) {
    val ctx = LocalContext.current
    var packs by remember { mutableStateOf(SkillPacks.packs) }
    var broken by remember { mutableStateOf(SkillPacks.broken()) }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.skp_count, packs.size, packs.sumOf { it.funcs.size }),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(stringResource(R.string.skp_d), fontSize = 9.sp, color = TextSecondary, maxLines = 2)
            }
            GhostButton(stringResource(R.string.skp_rescan), modifier = Modifier.height(34.dp)) {
                runCatching { SkillPacks.reload(ctx) }
                packs = SkillPacks.packs
                broken = SkillPacks.broken()
            }
        }
        packs.forEach { p ->
            Spacer(Modifier.height(6.dp))
            //包整块可点：进详情改 README / skill.json / 模板，并填它声明出来的密钥
            Column(
                Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, gate = 0.dp)
                    .tap { onEdit(p) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    p.name.ifBlank { p.id } + "（" + p.id + "）",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    p.funcs.joinToString("、") { it.name }.ifBlank { p.desc },
                    fontSize = 9.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
        if (broken.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.skp_broken), fontSize = 10.sp, color = DangerRed)
            broken.forEach { f ->
                Text("· ${f.skill}/${f.name}：${f.bad}", fontSize = 9.sp, color = DangerRed)
            }
        }
    }
} //技能包列表

//官网地址：服务端根地址在构建期从 local.properties 注入，仓库里没有真值
internal val SITE_URL: String get() = b.ai.core.cfg.app.Bai.host
