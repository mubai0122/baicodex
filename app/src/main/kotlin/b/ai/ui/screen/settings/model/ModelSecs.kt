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

//模型分类：拆成一个个 item，LazyColumn 只组合看得见的那几张卡
internal fun LazyListScope.modelCfgSec(onOpenMemory: () -> Unit) {
    item {
        SectionTitle(stringResource(R.string.se_007), stringResource(R.string.se_064))
        ProfileList()
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_009))
        CardBox { ModelSetupPanel() }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_011), stringResource(R.string.se_065))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_066),
                if (Cfg.modelVision) stringResource(R.string.se_067) else stringResource(R.string.se_068),
                Cfg.modelVision
            ) { Cfg.modelVision = it; Profiles.capture() }
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_069),
                if (Cfg.modelToolCall) stringResource(R.string.se_070)
                else stringResource(R.string.se_071),
                Cfg.modelToolCall
            ) { Cfg.modelToolCall = it; Profiles.capture() }
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_072),
                if (Cfg.modelReasoning) stringResource(R.string.se_073) else stringResource(R.string.se_074),
                Cfg.modelReasoning
            ) { Cfg.modelReasoning = it; Profiles.capture() }
            Spacer(Modifier.height(8.dp))
            //原生联网搜索：厂商不支持时开关强制显示为关，点了也不改配置，避免下发不认识字段打 400
            val ctxN = LocalContext.current
            val nsOk = Profiles.supportsNativeSearch(Cfg.providerId)
            CapSwitch(
                stringResource(R.string.se_386),
                when {
                    !nsOk -> stringResource(R.string.se_389)
                    Cfg.modelNativeSearch -> stringResource(R.string.se_387)
                    else -> stringResource(R.string.se_388)
                },
                Cfg.modelNativeSearch && nsOk
            ) { on ->
                if (nsOk) {
                    //静默互斥：DeepSeek 反过来把思考打开，其它厂商把思考关掉，都不弹提示
                    if (on) Cfg.think = Profiles.searchNeedsThink(Cfg.providerId)
                    Cfg.modelNativeSearch = on
                    Profiles.capture()
                }
            }
            Spacer(Modifier.height(10.dp))
            //思考档位：按参考图改成可展开的档位编辑器（显示名 / 写入路径 / 写入值），
            //不再是一个逗号串输入框——档位数量和每个档位写什么值本来就是两件事
            var profLv by remember(Cfg.profileId) { mutableStateOf(profLevels()) }
            var profOpen by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().tap { profOpen = !profOpen }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tx_c56fbda3), fontSize = 13.5.sp, color = TextPrimary)
                        Text(
                            if (profLv.isEmpty()) stringResource(R.string.tx_707c976d) else stringResource(R.string.tx_8bdbcde3, profLv.size),
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                    IcChevron(TextSecondary, down = !profOpen, side = 13.dp)
                }
                AnimatedVisibility(
                    visible = profOpen,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(80)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
                ) {
                    Column {
                        LevelEditor(profLv, "reasoning_effort", "\"medium\"") { list ->
                            profLv = list
                            //两份一起写：名字串给滑块显示，明细给请求下发；配置项跟着落盘
                            Cfg.profThinkLevels = list.joinToString(",") { it.label.trim() }.trim(',', ' ')
                            val arr = org.json.JSONArray()
                            list.forEach { arr.put(it.toJson()) }
                            Cfg.profThinkLevelsJson = arr.toString()
                            Profiles.capture()
                        }
                        Text(
                            stringResource(R.string.tx_f0365c26),
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
    } //模型配置子页

//模型参数子页：深度思考、温度与额外请求参数
internal fun LazyListScope.modelParamSec() {
    item {
        //思考/搜索互斥提示要用到，提前取一次
        val ctxP = LocalContext.current
        SectionTitle(stringResource(R.string.se_013), stringResource(R.string.se_075))
        CardBox {
            //思考常显：不再按模型名单门控，发不发只看「模型能力 → 推理 / 思考参数」
            if (Cfg.modelReasoning || Cfg.think) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.se_076), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (Cfg.think) stringResource(R.string.se_077) else stringResource(R.string.se_078),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = Cfg.think,
                    onCheckedChange = { on ->
                        //静默互斥：关思考时把搜索一起收掉（DeepSeek 的搜索不能脱离思考），不弹提示
                        if (!on && Cfg.modelNativeSearch) Cfg.modelNativeSearch = false
                        Cfg.think = on
                        Profiles.capture()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.se_079),
                Cfg.thinkEffort,
                listOf("low", "medium", "high"),
                optionLabel = { it },
                isSelected = { Cfg.thinkEffort == it },
                onSelect = { Cfg.thinkEffort = it }
            )
            } //仅支持思考的模型才显示深度思考
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.se_080),
                if (Cfg.maxMode) stringResource(R.string.se_081) else stringResource(R.string.se_082),
                Cfg.maxMode
            ) { Cfg.maxMode = it }
            Spacer(Modifier.height(8.dp))
            //原生联网搜索：与「模型能力」里同一项，参数页也放一份方便调；厂商不支持时恒为关且点不动
            val ctx = LocalContext.current
            val nsOk = Profiles.supportsNativeSearch(Cfg.providerId)
            CapSwitch(
                stringResource(R.string.se_386),
                when {
                    !nsOk -> stringResource(R.string.se_389)
                    Cfg.modelNativeSearch -> stringResource(R.string.se_387)
                    else -> stringResource(R.string.se_388)
                },
                Cfg.modelNativeSearch && nsOk
            ) { on ->
                when {
                    //厂商不支持：点不动
                    !nsOk -> Unit
                    //DeepSeek：搜索必须在思考模式下 → 允许开搜索，同时自动把思考打开，不做拦截
                    on && Profiles.searchNeedsThink(Cfg.providerId) -> {
                        Cfg.modelNativeSearch = true
                        if (!Cfg.think) {
                            Cfg.think = true
                            android.widget.Toast.makeText(
                                ctx,
                                ctx.getString(R.string.tx_8e1b47f0),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        Profiles.capture()
                    }
                    //其它厂商：与思考互斥，思考开着时不允许打开搜索，只提示不改另一个开关
                    on && Cfg.think -> android.widget.Toast.makeText(
                        ctx,
                        ctx.getString(R.string.tx_ec691c16),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    else -> {
                        Cfg.modelNativeSearch = on
                        Profiles.capture()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.se_083),
                if (Cfg.temperature < 0f) stringResource(R.string.se_084) else Cfg.temperature.toString(),
                listOf(-1f, 0f, 0.3f, 0.7f, 1f, 1.3f),
                optionLabel = { if (it < 0f) stringResource(R.string.se_084) else it.toString() },
                isSelected = { kotlin.math.abs(Cfg.temperature - it) < 0.01f },
                onSelect = { Cfg.temperature = it }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.se_085),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            //创造性参数：只影响「怎么写」不影响「写什么」，跟温度归一组
            Text(stringResource(R.string.tx_c71ac55d), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            //参数改成预设下拉：滑杆调 top_p 会调出 0.83 这种没意义的中间值，预设档更好选也更好复现
            SelectField(
                stringResource(R.string.pm_top_p),
                value = if (Cfg.topP < 0f) stringResource(R.string.tx_18c63459) else String.format("%.2f", Cfg.topP),
                desc = stringResource(R.string.pm_top_p_key),
                options = listOf(-1f, 0.5f, 0.7f, 0.8f, 0.9f, 0.95f, 1f),
                optionLabel = { if (it < 0f) stringResource(R.string.tx_9559ce64) else String.format("%.2f", it) },
                isSelected = { kotlin.math.abs(Cfg.topP - it) < 0.001f },
                onSelect = { Cfg.topP = it }
            )
            SelectField(
                stringResource(R.string.pm_top_k),
                value = if (Cfg.topK <= 0) stringResource(R.string.tx_18c63459) else Cfg.topK.toString(),
                desc = stringResource(R.string.pm_top_k_key),
                options = listOf(0, 20, 40, 60, 80, 100),
                optionLabel = { if (it <= 0) stringResource(R.string.tx_9559ce64) else it.toString() },
                isSelected = { Cfg.topK == it },
                onSelect = { Cfg.topK = it }
            )
            Text(
                stringResource(R.string.tx_42c4c7c5),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            //重复控制：治「车轱辘话」「复读同一个词」，跟创造性分开一组更好找
            Text(stringResource(R.string.tx_69c6c8f3), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            SelectField(
                stringResource(R.string.pm_presence),
                value = if (Cfg.presencePenalty < 0f) stringResource(R.string.tx_18c63459) else String.format("%.1f", Cfg.presencePenalty),
                desc = stringResource(R.string.pm_presence_key),
                options = listOf(-1f, 0f, 0.3f, 0.5f, 1f, 1.5f, 2f),
                optionLabel = { if (it < 0f) stringResource(R.string.tx_9559ce64) else String.format("%.1f", it) },
                isSelected = { kotlin.math.abs(Cfg.presencePenalty - it) < 0.001f },
                onSelect = { Cfg.presencePenalty = it }
            )
            SelectField(
                stringResource(R.string.pm_frequency),
                value = if (Cfg.frequencyPenalty < 0f) stringResource(R.string.tx_18c63459) else String.format("%.1f", Cfg.frequencyPenalty),
                desc = stringResource(R.string.pm_frequency_key),
                options = listOf(-1f, 0f, 0.3f, 0.5f, 1f, 1.5f, 2f),
                optionLabel = { if (it < 0f) stringResource(R.string.tx_9559ce64) else String.format("%.1f", it) },
                isSelected = { kotlin.math.abs(Cfg.frequencyPenalty - it) < 0.001f },
                onSelect = { Cfg.frequencyPenalty = it }
            )
            SelectField(
                stringResource(R.string.pm_repeat),
                value = if (Cfg.repeatPenalty <= 0f) stringResource(R.string.tx_18c63459) else String.format("%.2f", Cfg.repeatPenalty),
                desc = stringResource(R.string.pm_repeat_key),
                options = listOf(0f, 1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.3f),
                optionLabel = { if (it <= 0f) stringResource(R.string.tx_9559ce64) else String.format("%.2f", it) },
                isSelected = { kotlin.math.abs(Cfg.repeatPenalty - it) < 0.001f },
                onSelect = { Cfg.repeatPenalty = it }
            )
            Text(
                stringResource(R.string.tx_c93d4306),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.se_086), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Field(
                label = stringResource(R.string.se_087),
                value = Cfg.extraParams,
                onValueChange = { Cfg.extraParams = it },
                placeholder = """{"enable_thinking":true}""",
                secret = false,
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.se_088) +
                    stringResource(R.string.se_089),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //模型参数子页

/** 思考配置：规则列表按顺序匹配，首条命中生效；编辑走弹窗（参考图那套字段） */
internal fun LazyListScope.modelThinkSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(
            stringResource(R.string.tx_5dc7fa2b),
            stringResource(R.string.tx_81ef7ad6)
        )
        CardBox {
            val rules = remember(Cfg.thinkRules) { ThinkRules.list() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${rules.count { it.on }}/${rules.size} 条启用",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                //新增入口放右上角，跟列表页习惯一致
                Text(
                    stringResource(R.string.tx_03dc6b51),
                    fontSize = 11.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                        .tap { ThinkEdit.open(ThinkRules.Rule(pattern = "", path = "reasoning_effort"), true) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            rules.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .tap { ThinkEdit.open(r.copy(), false) }
                            .padding(vertical = 8.dp)
                    ) {
                        //主标题显示匹配的完整模型名（用户要求：匹配的是模型，不是供应商）
                        Text(
                            if (r.pattern.isBlank()) stringResource(R.string.tx_ed972708)
                            else r.pattern.split(',', '，').joinToString(", ") { it.trim() }.take(40),
                            fontSize = 13.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            ThinkRules.controlName(r.control) + " · " + r.levelText() + " · " +
                                ThinkRules.MATCH_NAMES.firstOrNull { it.first == r.match }?.second.orEmpty() +
                                " · " + r.path,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    //顺序即优先级：上移 / 下移直接改顺序，无需进弹窗
                    Text(
                        "↑",
                        fontSize = 13.sp,
                        color = if (i > 0) TextSecondary else SurfaceMuted,
                        modifier = Modifier
                            .tap { if (i > 0) ThinkRules.save(rules.swap(i, i - 1)) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                    Text(
                        "↓",
                        fontSize = 13.sp,
                        color = if (i < rules.lastIndex) TextSecondary else SurfaceMuted,
                        modifier = Modifier
                            .tap { if (i < rules.lastIndex) ThinkRules.save(rules.swap(i, i + 1)) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                    Switch(
                        checked = r.on,
                        onCheckedChange = { on -> r.on = on; ThinkRules.save(rules) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_a75eff1a), modifier = Modifier.height(38.dp)) { ThinkRules.reset() }
            Spacer(Modifier.height(6.dp))
            Text(
                "说明：「路径」是请求体里的位置，点号分层（thinking.type 表示 {\"thinking\":{\"type\":…}}）；" +
                    "「值」按 JSON 写，字符串带引号、布尔写 true/false、数字直写。匹配模型可写多个，逗号分隔。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //思考配置子页

/** 列表交换两行：直接改顺序，优先级跟着走 */
internal fun List<ThinkRules.Rule>.swap(a: Int, b: Int): List<ThinkRules.Rule> {
    val out = toMutableList()
    val t = out[a]
    out[a] = out[b]
    out[b] = t
    return out
} //交换规则顺序

/** 思考规则编辑弹窗的挂载状态：同一时刻只开一个 */
internal object ThinkEdit {
    var rule by mutableStateOf<ThinkRules.Rule?>(null)
    var fresh by mutableStateOf(false)

    /** 打开：rule 传副本，fresh 表示新建 */
    fun open(r: ThinkRules.Rule, isNew: Boolean) {
        rule = r
        fresh = isNew
    } //打开弹窗
} //思考规则弹窗

/** 档位编辑器：可增删、每条可展开改「显示名 / 写入路径 / 写入值」（思考配置与模型配置共用） */
@Composable
internal fun LevelEditor(
    levels: List<ThinkRules.Level>,
    basePath: String,
    baseValue: String,
    onChange: (List<ThinkRules.Level>) -> Unit
) {
    var openIdx by remember { mutableStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tx_c8b75cb0), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.tx_7c138c54),
            fontSize = 11.sp,
            color = AccentBlue,
            modifier = Modifier
                .tap {
                    if (levels.size < 5) {
                        onChange(
                            levels + ThinkRules.Level(
                                label = b.ai.App.self.getString(R.string.tx_8cae3683, levels.size + 1),
                                path = basePath,
                                value = baseValue
                            )
                        )
                        openIdx = levels.size
                    }
                }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        )
    }
    Spacer(Modifier.height(2.dp))
    levels.forEachIndexed { i, lv ->
        val unfolded = openIdx == i
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .border(0.8.dp, BorderGray, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("≡", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).tap { openIdx = if (unfolded) -1 else i }) {
                    Text(stringResource(R.string.tx_9c61a929) + (i + 1), fontSize = 12.sp, color = TextPrimary)
                    Text(
                        lv.label.ifBlank { "未命名" } + " · " + lv.value.ifBlank { "未填值" },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .tap {
                            onChange(levels.toMutableList().also { it.removeAt(i) })
                            openIdx = (openIdx).coerceAtMost(levels.lastIndex - 1)
                        }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) { IcTrash(DangerRed, side = 13.dp) }
                Box(
                    Modifier.tap { openIdx = if (unfolded) -1 else i }.padding(4.dp)
                ) { IcChevron(TextSecondary, down = !unfolded, side = 13.dp) }
            }
            if (unfolded) {
                Spacer(Modifier.height(6.dp))
                Field(stringResource(R.string.tx_8e6ab923), lv.label, { v ->
                    onChange(levels.toMutableList().also { it[i] = it[i].copy(label = v) })
                }, placeholder = "low")
                Field(stringResource(R.string.tx_694297ce), lv.path, { v ->
                    onChange(levels.toMutableList().also { it[i] = it[i].copy(path = v) })
                }, placeholder = basePath)
                Field(stringResource(R.string.tx_d3e52c96), lv.value, { v ->
                    onChange(levels.toMutableList().also { it[i] = it[i].copy(value = v) })
                }, placeholder = baseValue)
            }
        }
    }
} //档位编辑器

/** 当前配置的档位明细：没存过明细就按名字串摊平（老配置升级上来的情况） */
internal fun profLevels(): List<ThinkRules.Level> {
    val raw = Cfg.profThinkLevelsJson
    if (raw.isNotBlank()) {
        val arr = runCatching { org.json.JSONArray(raw) }.getOrNull()
        if (arr != null) {
            val out = ArrayList<ThinkRules.Level>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += ThinkRules.Level(
                    label = o.optString("label", ""),
                    path = o.optString("path", ""),
                    value = o.optString("value", "")
                )
            }
            if (out.isNotEmpty()) return out
        }
    }
    return Cfg.profThinkLevels.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        .map { ThinkRules.Level(label = it, path = "", value = "") }
} //当前配置档位


/**
 * 思考规则编辑弹窗：按参考图排——启用 / 控件 / 匹配方式 / 匹配模型 / 默认路径 /
 * 始终开启 / 开启关闭写入（折叠）/ 滑块档位（折叠，可增删每个档位）。
 */
@Composable
internal fun ThinkRuleDialog(
    rule: ThinkRules.Rule,
    fresh: Boolean,
    onSave: (ThinkRules.Rule) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit
) {
    var enabled by remember(rule) { mutableStateOf(rule.on) }
    var control by remember(rule) { mutableStateOf(rule.control) }
    var match by remember(rule) { mutableStateOf(rule.match) }
    var pattern by remember(rule) { mutableStateOf(rule.pattern) }
    var path by remember(rule) { mutableStateOf(rule.path) }
    var alwaysOn by remember(rule) { mutableStateOf(rule.alwaysOn) }
    var onValue by remember(rule) { mutableStateOf(rule.onValue) }
    var offValue by remember(rule) { mutableStateOf(rule.offValue) }
    //档位是可增删的列表：在副本上改，点保存才写回，取消不留痕
    var levels by remember(rule) { mutableStateOf(rule.levelList().map { it.copy() }) }
    var writeOpen by remember { mutableStateOf(false) }
    var levelOpen by remember { mutableStateOf(true) }
    var openIdx by remember { mutableStateOf(0) }

    ScrimCard {
        Text(
            if (fresh) stringResource(R.string.tx_520a162c) else stringResource(R.string.tx_a441b416),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.tx_3fe3558b), fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.weight(1f, fill = false).heightIn(max = 440.dp).verticalScroll(glassScrollState())
        ) {
            ThinkToggleRow(stringResource(R.string.tx_8d2bc55b), if (enabled) stringResource(R.string.tx_cf2b2abc) else stringResource(R.string.tx_2ac5cf31), enabled) { enabled = it }
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.tx_e1b2f870), fontSize = 11.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThinkSeg(listOf("slider" to stringResource(R.string.tx_011d09ad), "bool" to "开关", "enum" to "枚举"), control) { control = it }
            }
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.tx_078ab933), fontSize = 11.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThinkSeg(ThinkRules.MATCH_NAMES, match) { match = it }
            }
            Spacer(Modifier.height(2.dp))
            //匹配模型写完整型号，不写供应商：模型名才算「命中」，厂商前缀会把同厂所有型号都吞进来
            Field(
                stringResource(R.string.tx_1bfaf093),
                pattern,
                { pattern = it },
                placeholder = "glm-4.6, deepseek-chat, gemini-2.5-pro"
            )
            Text(
                stringResource(R.string.tx_eb67ae08),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Field(stringResource(R.string.tx_16a79761), path, { path = it }, placeholder = "reasoning_effort")
            Spacer(Modifier.height(2.dp))
            ThinkToggleRow(stringResource(R.string.tx_520e366b), stringResource(R.string.tx_2280a03a), alwaysOn) { alwaysOn = it }

            //开启 / 关闭时写入：默认收起，需要改固定值时再展开
            ThinkFold(
                title = stringResource(R.string.tx_1bf1dcd4),
                sub = stringResource(R.string.tx_6b8361f5),
                open = writeOpen,
                onToggle = { writeOpen = !writeOpen }
            ) {
                Field(stringResource(R.string.tx_abbb0ab4), onValue, { onValue = it }, placeholder = "\"enabled\"")
                Field(stringResource(R.string.tx_e2956683), offValue, { offValue = it }, placeholder = "\"disabled\"")
            }

            //滑块档位：每个档位一条，可增删；档位可以带自己的写入路径（如 Claude 的 budget_tokens）
            ThinkFold(
                title = stringResource(R.string.tx_4fa79f06),
                sub = stringResource(R.string.tx_8bdbcde3, levels.size),
                open = levelOpen,
                onToggle = { levelOpen = !levelOpen }
            ) {
                LevelEditor(levels, path, onValue) { levels = it }
                Text(
                    stringResource(R.string.tx_1a212ec6),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) {
                Text(
                    stringResource(R.string.tx_2f4aaddd),
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
                stringResource(R.string.tx_625fb26b),
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
                stringResource(R.string.tx_be5fbbe3),
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue, gate = 0.dp)
                    .tap {
                        onSave(
                            rule.copy().apply {
                                this.on = enabled
                                this.control = control
                                this.match = match
                                this.pattern = pattern
                                this.path = path.ifBlank { "reasoning_effort" }
                                this.alwaysOn = alwaysOn
                                this.onValue = onValue
                                this.offValue = offValue
                                this.levels = levels.map { it.copy() }
                                this.legacyLevels = ""
                            }
                        )
                    }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //思考规则弹窗

/** 可折叠分组：标题 + 副标题 + 箭头，展开内容带高度动画（避免多个分组同时弹开挤在一起） */
@Composable
internal fun ThinkFold(
    title: String,
    sub: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().tap { onToggle() }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, color = TextPrimary)
                Text(sub, fontSize = 10.sp, color = TextSecondary)
            }
            IcChevron(TextSecondary, down = !open, side = 13.dp)
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(180)) + expandVertically(tween(220), expandFrom = Alignment.Top),
            exit = fadeOut(tween(80)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top)
        ) {
            Column { content() }
        }
    }
} //可折叠分组状态



/** 分段选择：一行小胶囊，选中高亮蓝 */
@Composable
internal fun ThinkSeg(items: List<Pair<String, String>>, cur: String, onPick: (String) -> Unit) {
    items.forEach { (k, label) ->
        Text(
            label,
            fontSize = 11.sp,
            color = if (cur == k) AccentBlue else TextSecondary,
            modifier = Modifier
                .liquidBg(
                    RoundedCornerShape(8.dp),
                    tint = if (cur == k) AccentBlueSoft else SurfaceMuted,
                    gate = 0.dp
                )
                .tap { onPick(k) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
} //分段选择

/** 弹窗里的一行开关 */
@Composable
internal fun ThinkToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = TextPrimary)
            Text(sub, fontSize = 10.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
    }
} //弹窗开关行


