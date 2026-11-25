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

package b.ai.ui.screen.settings.model

import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.kit.*
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

/**编排子页：任务分解与规划。只改系统提示里的规划要求，不放宽任何工具权限 */
internal fun LazyListScope.modelOrchSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_pg_orch), stringResource(R.string.se_pg_orch_sub))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_orch_plan_t),
                if (Cfg.planFirst) stringResource(R.string.se_orch_plan_on) else stringResource(R.string.se_orch_plan_off),
                Cfg.planFirst
            ) { Cfg.planFirst = it }
            //拆解形式跟着开关滑出：关着时整段不存在
            FollowBlock(Cfg.planFirst) {
                Spacer(Modifier.height(8.dp))
                SelectField(
                    stringResource(R.string.se_orch_shape),
                    if (Cfg.planDag) stringResource(R.string.se_orch_dag) else stringResource(R.string.se_orch_chain),
                    listOf(false, true),
                    optionLabel = { if (it) stringResource(R.string.se_orch_dag) else stringResource(R.string.se_orch_chain) },
                    isSelected = { Cfg.planDag == it },
                    onSelect = { Cfg.planDag = it }
                )
                Text(stringResource(R.string.se_orch_shape_d), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                //复杂目标才拆解：默认开，防止一句话需求也被拆成三段
                CapSwitch(
                    stringResource(R.string.se_orch_complex_t),
                    stringResource(R.string.se_orch_complex_d),
                    Cfg.planComplexOnly
                ) { Cfg.planComplexOnly = it }
                Spacer(Modifier.height(8.dp))
                //计划要不要贴给用户看
                CapSwitch(
                    stringResource(R.string.se_orch_show_t),
                    stringResource(R.string.se_orch_show_d),
                    Cfg.planShow
                ) { Cfg.planShow = it }
                Spacer(Modifier.height(8.dp))
                //失败时是自动重排还是停下来问
                CapSwitch(
                    stringResource(R.string.se_orch_retry_t),
                    stringResource(R.string.se_orch_retry_d),
                    Cfg.planRetry
                ) { Cfg.planRetry = it }
                Spacer(Modifier.height(10.dp))
                //子任务上限：0 =不限
                SelectField(
                    stringResource(R.string.se_orch_steps),
                    if (Cfg.planMaxSteps <= 0) stringResource(R.string.se_orch_steps_none)
                    else stringResource(R.string.se_orch_steps_n, Cfg.planMaxSteps),
                    listOf(0, 3, 5, 8, 12),
                    optionLabel = {
                        if (it <= 0) stringResource(R.string.se_orch_steps_none)
                        else stringResource(R.string.se_orch_steps_n, it)
                    },
                    isSelected = { Cfg.planMaxSteps == it },
                    onSelect = { Cfg.planMaxSteps = it }
                )
                Text(stringResource(R.string.se_orch_steps_d), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                //额外要求：手写约束原样拼进编排段末尾
                Field(
                    label = stringResource(R.string.se_orch_extra),
                    value = Cfg.planExtra,
                    onValueChange = { Cfg.planExtra = it },
                    placeholder = stringResource(R.string.se_orch_extra_ph),
                    singleLine = false,
                    minLines = 3
                )
                Text(stringResource(R.string.se_orch_extra_d), fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.se_orch_note), fontSize = 10.sp, color = TextSecondary)
        }
    }
} //编排子页

internal fun LazyListScope.modelVoiceSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_v_in_t), stringResource(R.string.se_v_in_d))
        VoiceInCard()
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_v_out_t), stringResource(R.string.se_v_out_d))
        VoiceOutCard()
    }
} //语音子页

internal fun LazyListScope.modelMemSec(onOpenMemory: () -> Unit) {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_44e4d7a3), stringResource(R.string.tx_08b3859a))
        CardBox {
            val ctxM = LocalContext.current
            val rows = Memory.rows
            //思维导图：中心节点 + 左侧主干线 + 每条记忆一个分支节点
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).liquidBg(CircleShape, tint = AccentBlue, gate = 0.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (rows.isEmpty()) stringResource(R.string.tx_74f960c9) else stringResource(R.string.tx_6adeb861, rows.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        if (rows.isEmpty()) stringResource(R.string.tx_cedb8821) else stringResource(R.string.tx_4dc6270e),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.padding(start = 4.dp)) {
                    rows.forEachIndexed { i, r ->
                        //条目之间要能看出边界：底色和卡片太接近时整块会糊成一片（用户反馈「无分割」），
                        //所以每条加一圈细边框，并留 4dp 呼吸位
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            //主干 + 分支：一格宽度里画「└─」形状，最后一条不画竖线
                            Canvas(Modifier.width(16.dp).height(38.dp)) {
                                val x = 5.dp.toPx()
                                val mid = size.height / 2f
                                drawLine(
                                    BorderGray, androidx.compose.ui.geometry.Offset(x, 0f),
                                    androidx.compose.ui.geometry.Offset(x, if (i == rows.lastIndex) mid else size.height),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawLine(
                                    BorderGray, androidx.compose.ui.geometry.Offset(x, mid),
                                    androidx.compose.ui.geometry.Offset(size.width, mid),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawCircle(AccentBlue, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width, mid))
                            }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 32.dp)
                                    .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                    .border(0.8.dp, BorderGray, RoundedCornerShape(9.dp))
                                    .padding(start = 9.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    r.text,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "✕",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.tap { Memory.delete(r.id) }.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            //整理：选一个模型把零散条目合并去重，结果先摆出来，点应用才落库
            val profiles = Profiles.list
            val cur = profiles.firstOrNull { it.id == Cfg.memProfileId }
            SelectField(
                stringResource(R.string.tx_582085bb),
                cur?.label?.ifBlank { cur.model } ?: "跟随当前聊天",
                listOf("") + profiles.map { it.id },
                optionLabel = { id ->
                    if (id.isBlank()) stringResource(R.string.tx_929ea71b)
                    else profiles.firstOrNull { it.id == id }?.label?.ifBlank { id } ?: id
                },
                isSelected = { Cfg.memProfileId == it },
                onSelect = { Cfg.memProfileId = it; MemOrganizer.clear() }
            )
            Spacer(Modifier.height(8.dp))
            val scope = rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    if (MemOrganizer.busy) stringResource(R.string.tx_653d2130) else stringResource(R.string.tx_607c0a3b),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (!MemOrganizer.busy) scope.launch { MemOrganizer.run() }
                }
                if (MemOrganizer.suggestions.isNotEmpty()) {
                    GhostButton(stringResource(R.string.tx_9f8beb63, MemOrganizer.suggestions.size), modifier = Modifier.height(36.dp)) {
                        android.widget.Toast.makeText(
                            ctxM, MemOrganizer.apply(), android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    GhostButton(stringResource(R.string.tx_5ac2011a), modifier = Modifier.height(36.dp)) { MemOrganizer.clear() }
                }
            }
            if (MemOrganizer.error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(MemOrganizer.error, fontSize = 10.sp, color = DangerRed)
            }
            //待确认结果：跟记忆节点同一套连线，让「整理前 / 整理后」看起来是一回事
            if (MemOrganizer.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.tx_865347b7), fontSize = 11.sp, color = AccentBlue)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.padding(start = 4.dp)) {
                    MemOrganizer.suggestions.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            Canvas(Modifier.width(16.dp).height(30.dp)) {
                                val x = 5.dp.toPx()
                                val mid = size.height / 2f
                                drawLine(
                                    AccentBlueSoft, androidx.compose.ui.geometry.Offset(x, 0f),
                                    androidx.compose.ui.geometry.Offset(x, if (i == MemOrganizer.suggestions.lastIndex) mid else size.height),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawLine(
                                    AccentBlueSoft, androidx.compose.ui.geometry.Offset(x, mid),
                                    androidx.compose.ui.geometry.Offset(size.width, mid),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                                drawCircle(AccentBlue, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width, mid))
                            }
                            Text(
                                t,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                                    .border(0.8.dp, AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
} //记忆子页

//语音识别卡片（从原 modelTab 迁出，保持逐行一致）
@Composable
internal fun VoiceInCard() {
    val sysLabel = stringResource(R.string.se_v_mode_sys)
    val apiLabel = stringResource(R.string.se_v_mode_api)
    val modeLabel: (String) -> String = { if (it == "api") apiLabel else sysLabel }
    val preset = AsrPresets.of(Cfg.asrVendor)
    val scope = rememberCoroutineScope()
    var models by remember(Cfg.asrVendor) {
        mutableStateOf(preset.models.ifEmpty { listOf(Cfg.asrModel) }.filter { it.isNotBlank() })
    }
    var loading by remember { mutableStateOf(false) }
    var pickErr by remember { mutableStateOf("") }
    //STT 引擎：两个本地（系统识别 / Sherpa NCNN）+ 两个远程（OpenAI / Deepgram）。
    //标签写明本地还是远程，离线模式下只列本地那两项，避免选了远程又被拒绝
    val engineNames = mapOf(
        "sys" to "系统识别（本地，免费）",
        "sherpa" to "Sherpa NCNN（本地）",
        "openai" to "OpenAI STT（远程）",
        "deepgram" to "Deepgram STT（远程）"
    )
    val engine = Audio.engine()
    //sherpa 的 so 只随 arm64-v8a 打：运行时加载不起来时这一项直接不列（v7a 设备点了也只会退化）
    val sherpaOk = remember { b.ai.core.model.voice.SherpaAsr.runtimeOk() }
    val engineOpts = buildList {
        add("sys")
        if (sherpaOk) add("sherpa")
        //离线模式只给本地：远程项直接不列出来，比选完再弹提示更干净
        if (!Cfg.asrOffline) {
            add("openai")
            add("deepgram")
        }
    }
    CardBox {
        SelectField(
            stringResource(R.string.tx_2604bcce),
            //老配置里存着 sherpa 但这台设备跑不起来时，如实显示回落后的实际引擎
            when {
                engine == "sherpa" && !sherpaOk -> "系统识别（sherpa 运行时不可用）"
                else -> engineNames[engine] ?: engine
            },
            engineOpts,
            desc = if (Cfg.asrOffline) stringResource(R.string.tx_fdbc9a5f) else stringResource(R.string.tx_3df99821),
            optionLabel = { engineNames[it] ?: it },
            isSelected = { engine == it },
            onSelect = {
                Cfg.asrEngine = it
                //旧的 asrMode 同步一份：别处还在读它判断走不走接口
                Cfg.asrMode = if (it == "sys" || it == "sherpa") "sys" else "api"
            }
        )
        //从属字段缩进一层：引擎是父项，下面的 Key / 模型 / 目录都属于它（用户反馈「与上一栏无缩进」）
        Column(Modifier.fillMaxWidth().padding(start = 12.dp)) {
        if (engine == "deepgram") {
            Field(
                label = "Deepgram API Key",
                value = Cfg.asrDgKey,
                onValueChange = { Cfg.asrDgKey = it },
                placeholder = stringResource(R.string.tx_d822e614),
                secret = true
            )
            SelectField(
                stringResource(R.string.tx_19497c0b),
                Cfg.asrDgModel,
                listOf("nova-3", "nova-2", "enhanced", "base"),
                optionLabel = { it },
                isSelected = { Cfg.asrDgModel == it },
                onSelect = { Cfg.asrDgModel = it }
            )
            Field(
                label = stringResource(R.string.tx_85624c8e),
                value = Cfg.asrDgUrl,
                onValueChange = { Cfg.asrDgUrl = it },
                placeholder = "https://api.deepgram.com"
            )
            Text(
                stringResource(R.string.tx_f490eaaa),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (engine == "sherpa") {
            Field(
                label = stringResource(R.string.tx_e796d32d),
                value = Cfg.asrSherpaDir,
                onValueChange = { Cfg.asrSherpaDir = it },
                placeholder = stringResource(R.string.tx_25b7fe70)
            )
            //提示改成动态判定：原来是写死的红字，内置了也照样喊「未内置」，反而误导用户
            val dir = Cfg.asrSherpaDir
            val bundled = remember { b.ai.core.model.voice.SherpaAsr.bundledModelOk() }
            val dirOk = dir.isBlank() || b.ai.core.model.voice.SherpaAsr.modelOk(dir)
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val (msg, tint) = when {
                !sherpaOk -> "本包在 $abi 上没有 sherpa 运行时，选它会改用系统识别" to DangerRed
                !dirOk -> "目录不完整：需 7 个模型文件（encoder/decoder/joiner 的 param·bin + tokens.txt）" to DangerRed
                dir.isBlank() && bundled -> "运行时与模型均已内置，目录留空即用内置模型" to OkGreen
                dir.isBlank() -> "包内没带模型，请下载或填一个模型目录" to DangerRed
                else -> "使用外部模型：$dir" to TextSecondary
            }
            Text(msg, fontSize = 10.sp, color = tint)
            //模型下载：默认取本仓库 models 目录（七个文件逐个拉），也支持任意「目录前缀」或 zip 直链
            var dlBusy by remember { mutableStateOf(false) }
            var dlPct by remember { mutableStateOf(0) }
            var dlNote by remember { mutableStateOf("") }
            Spacer(Modifier.height(8.dp))
            Field(
                label = "模型下载地址",
                value = Cfg.asrSherpaSrc,
                onValueChange = { Cfg.asrSherpaSrc = it },
                placeholder = b.ai.core.model.voice.ModelFetch.DEFAULT_SRC
            )
            //按钮跟输入框之间要留空：Row 紧贴 Field 底边会看起来糊成一团
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostButton(
                    if (dlBusy) "下载中 $dlPct%" else "下载模型",
                    modifier = Modifier.height(36.dp)
                ) {
                    if (dlBusy) return@GhostButton
                    //地址留空就用默认源；下载在 IO 线程，进度靠回调刷回主线程
                    val src = Cfg.asrSherpaSrc.ifBlank { b.ai.core.model.voice.ModelFetch.DEFAULT_SRC }
                    dlBusy = true
                    dlPct = 0
                    dlNote = "准备中…"
                    scope.launch {
                        try {
                            val dir = b.ai.core.model.voice.ModelFetch.fetch(src) { p ->
                                dlPct = p.percent
                                dlNote = p.note
                            }
                            //下完直接把目录填进去，用户不用手抄路径
                            Cfg.asrSherpaDir = dir.absolutePath
                            dlPct = 100
                            dlNote = "已就绪：" + dir.absolutePath
                        } catch (e: Exception) {
                            dlNote = "下载失败：" + (e.message ?: e.javaClass.simpleName)
                        } finally {
                            dlBusy = false
                        }
                    }
                }
                GhostButton("用已下载的", modifier = Modifier.height(36.dp)) {
                    val list = b.ai.core.model.voice.ModelFetch.installed()
                    if (list.isEmpty()) {
                        dlNote = "还没有下过模型"
                    } else {
                        val d = list.last()
                        Cfg.asrSherpaDir = d.absolutePath
                        dlNote = "已切换：" + d.name
                    }
                }
            }
            if (dlNote.isNotBlank()) {
                Text(
                    dlNote,
                    fontSize = 10.sp,
                    color = if (dlNote.startsWith("下载失败")) DangerRed else TextSecondary
                )
            }
        }
        if (engine == "openai") {
            SelectField(
                stringResource(R.string.tx_79f09f89),
                preset.label,
                AsrPresets.all,
                optionLabel = { it.label },
                isSelected = { Cfg.asrVendor == it.id },
                onSelect = { p ->
                    Cfg.asrVendor = p.id
                    Cfg.asrBase = ""
                    pickErr = ""
                    models = p.models
                    if (p.models.isNotEmpty()) Cfg.asrModel = p.models.first()
                }
            )
            Field(
                label = stringResource(R.string.se_v_base),
                value = Cfg.asrBase,
                onValueChange = { Cfg.asrBase = it },
                placeholder = preset.base.ifBlank { Cfg.baseUrl.ifBlank { "https://api.openai.com/v1" } }
            )
            Field(
                label = stringResource(R.string.se_v_key),
                value = Cfg.asrKey,
                onValueChange = { Cfg.asrKey = it },
                placeholder = stringResource(R.string.se_v_key_ph),
                secret = true
            )
            if (models.isNotEmpty()) {
                SelectField(
                    stringResource(R.string.tx_8000f187),
                    Cfg.asrModel,
                    models,
                    optionLabel = { it },
                    isSelected = { Cfg.asrModel == it },
                    onSelect = { Cfg.asrModel = it }
                )
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                if (loading) stringResource(R.string.tx_629de16a) else stringResource(R.string.tx_e70a40b7),
                modifier = Modifier.height(38.dp)
            ) {
                if (loading) return@GhostButton
                loading = true
                pickErr = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Audio.listModels() } }
                    loading = false
                    r.onSuccess { list ->
                        if (list.isEmpty()) pickErr = b.ai.App.self.getString(R.string.tx_9be9c76a)
                        else { models = list; Cfg.asrModel = list.first() }
                    }.onFailure { pickErr = it.message ?: "拉取失败" }
                }
            }
            Field(
                label = stringResource(R.string.tx_f1153d97),
                value = Cfg.asrModel,
                onValueChange = { Cfg.asrModel = it },
                placeholder = "whisper-1"
            )
            if (pickErr.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(pickErr, fontSize = 10.sp, color = DangerRed)
            }
            SelectField(
                stringResource(R.string.tx_95e4a689),
                "${Cfg.asrRate} Hz",
                AsrPresets.rates,
                optionLabel = { "$it Hz" },
                isSelected = { Cfg.asrRate == it },
                onSelect = { Cfg.asrRate = it }
            )
            SelectField(
                stringResource(R.string.tx_0c4316d9),
                stringResource(R.string.tx_a6f0b1dc, Cfg.asrMaxSec),
                AsrPresets.maxSecs,
                optionLabel = { stringResource(R.string.tx_b0c9ab6f) },
                isSelected = { Cfg.asrMaxSec == it },
                onSelect = { Cfg.asrMaxSec = it }
            )
            if (preset.tip.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(preset.tip, fontSize = 10.sp, color = TextSecondary)
            }
        }
        Field(
            label = stringResource(R.string.se_v_lang),
            value = Cfg.asrLang,
            onValueChange = { Cfg.asrLang = it },
            placeholder = "zh-CN"
        )
        } //引擎从属字段
        Spacer(Modifier.height(6.dp))
        CapSwitch(
            stringResource(R.string.tx_4a61c29a),
            if (Cfg.asrOffline) stringResource(R.string.tx_5e408cf7)
            else stringResource(R.string.tx_f643dfa4),
            Cfg.asrOffline
        ) { on ->
            Cfg.asrOffline = on
            //打开离线时把远程引擎切回本地默认；不切的话引擎下拉会显示一个不存在的选项
            if (on && Audio.engine() != "sys" && Audio.engine() != "sherpa") {
                Cfg.asrEngine = "sys"
                Cfg.asrMode = "sys"
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.se_v_tip), fontSize = 10.sp, color = TextSecondary)
    }
} //语音识别卡片

@Composable
internal fun VoiceOutCard() {
    val ctx = LocalContext.current
    val sysLabel = stringResource(R.string.se_v_mode_sys)
    val apiLabel = stringResource(R.string.se_v_mode_api)
    val modeLabel: (String) -> String = { if (it == "api") apiLabel else sysLabel }
    CardBox {
        CapSwitch(
            stringResource(R.string.se_124),
            if (Cfg.speakReplies) stringResource(R.string.se_125) else stringResource(R.string.se_126),
            Cfg.speakReplies
        ) { on ->
            Cfg.speakReplies = on
            if (on) Speak.say(ctx.getString(R.string.se_127), force = true) else Speak.stop()
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.se_128), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        SelectField(
            stringResource(R.string.se_v_mode),
            modeLabel(Cfg.ttsMode),
            listOf("sys", "api"),
            optionLabel = { modeLabel(it) },
            isSelected = { Cfg.ttsMode == it },
            onSelect = { Cfg.ttsMode = it }
        )
        if (Cfg.ttsMode == "api") {
            Field(
                label = stringResource(R.string.se_v_base),
                value = Cfg.ttsBase,
                onValueChange = { Cfg.ttsBase = it },
                placeholder = Cfg.baseUrl.ifBlank { "https://api.openai.com/v1" }
            )
            Field(
                label = stringResource(R.string.se_v_key),
                value = Cfg.ttsKey,
                onValueChange = { Cfg.ttsKey = it },
                placeholder = stringResource(R.string.se_v_key_ph),
                secret = true
            )
            Field(
                label = stringResource(R.string.se_v_model),
                value = Cfg.ttsModel,
                onValueChange = { Cfg.ttsModel = it },
                placeholder = "tts-1"
            )
            Field(
                label = stringResource(R.string.se_v_voice),
                value = Cfg.ttsVoice,
                onValueChange = { Cfg.ttsVoice = it },
                placeholder = "alloy"
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.se_v_play), modifier = Modifier.height(38.dp)) {
                Speak.say(ctx.getString(R.string.se_127), force = true)
            }
            if (Audio.error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(Audio.error, fontSize = 10.sp, color = DangerRed)
            }
        }
    }
} //语音合成卡片

