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

package b.ai.ui.screen.settings.chat

import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
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

internal fun LazyListScope.chatCtxSec(stats: Pair<Int, Int>) {
    item {
    SectionTitle(stringResource(R.string.se_090), stringResource(R.string.se_091))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.se_092), fontSize = 14.sp, color = TextPrimary)
                Text(stringResource(R.string.se2_cur_msgs, stats.first, stats.second), fontSize = 11.sp, color = TextSecondary)
            }
            Switch(
                checked = Cfg.ctxAuto,
                onCheckedChange = { Cfg.ctxAuto = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(8.dp))
        SelectField(
            stringResource(R.string.se_094),
            if (Cfg.ctxTotal >= 1_000_000) "1M" else "${Cfg.ctxTotal / 1000}k",
            listOf(32_000, 64_000, 128_000, 256_000, 1_000_000),
            optionLabel = { if (it >= 1_000_000) "1M" else "${it / 1000}k" },
            isSelected = { Cfg.ctxTotal == it },
            onSelect = { Cfg.ctxTotal = it }
        )
        //配置自己带了窗口时这里的全局值不生效，不说清就会出现「改了没反应」
        if (Cfg.profCtxTotal > 0) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.se2_ctx_own), fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        //压缩阈值滑杆 10-99：到窗口的这个比例就开始压缩，默认 70%
        //统一用本页自研的 miuix 风格 GlassSlider（标题 + 右侧当前值 + 26dp 轨道），
        //之前这里是 material3 Slider，尺寸/配色/触控区都跟其余滑杆不一致，看着像没继承主题
        GlassSlider(
            stringResource(R.string.se2_pct, Cfg.ctxPct),
            stringResource(R.string.se2_ktok, Cfg.ctxTokenBudget / 1000),
            Cfg.ctxPct.toFloat(),
            10f..99f,
            1f
        ) { Cfg.ctxPct = it.toInt() }
        Text(
            stringResource(R.string.se_097),
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        SelectField(
            stringResource(R.string.se_098),
            stringResource(R.string.se2_n_items, Cfg.ctxKeep),
            listOf(4, 8, 16),
            optionLabel = { stringResource(R.string.se2_n_items, it) },
            isSelected = { Cfg.ctxKeep == it },
            onSelect = { Cfg.ctxKeep = it }
        )
        Spacer(Modifier.height(8.dp))
        SelectField(
            stringResource(R.string.se_100),
            stringResource(R.string.se2_n_items, Cfg.ctxMsgCount),
            listOf(8, 16, 32),
            optionLabel = { stringResource(R.string.se2_n_items, it) },
            isSelected = { Cfg.ctxMsgCount == it },
            onSelect = { Cfg.ctxMsgCount = it }
        )
        Spacer(Modifier.height(8.dp))
        //摘要档位：字数上限跟着档位走，具体写多少由总结模型按内容自己控
        SelectField(
            stringResource(R.string.tx_ecc33185),
            b.ai.core.model.chat.Sum.levelName(Cfg.summaryLevel),
            listOf(0, 1, 2),
            optionLabel = { b.ai.core.model.chat.Sum.levelName(it) },
            isSelected = { Cfg.summaryLevel == it },
            onSelect = { Cfg.summaryLevel = it }
        )
        Text(
            "简洁：上限 1000 字，可能跟随轮次丢失关键；详细：上限 2000 字，推荐；全面：不限字数，最推荐。" +
                "当前 " + b.ai.core.model.chat.Sum.levelHint(Cfg.summaryLevel),
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        //总结用模型：留空跟随聊天选中的那条配置，选了就单独走它的 base_url / Key / 模型
        val sumProfiles = Profiles.list
        val sumCur = sumProfiles.firstOrNull { it.id == Cfg.summaryProfileId }
        SelectField(
            stringResource(R.string.tx_637321f6),
            sumCur?.label ?: "跟随当前聊天",
            listOf("") + sumProfiles.map { it.id },
            optionLabel = { id -> sumProfiles.firstOrNull { it.id == id }?.label ?: "跟随当前聊天" },
            isSelected = { it == Cfg.summaryProfileId },
            onSelect = { Cfg.summaryProfileId = it }
        )
        Spacer(Modifier.height(8.dp))
        //标题生成用模型：同一套候选，留空跟随当前聊天；新建会话取名只花几十 token
        val titleCur = sumProfiles.firstOrNull { it.id == Cfg.titleProfileId }
        SelectField(
            stringResource(R.string.tx_3c70a377),
            titleCur?.label ?: "跟随当前聊天",
            listOf("") + sumProfiles.map { it.id },
            optionLabel = { id -> sumProfiles.firstOrNull { it.id == id }?.label ?: "跟随当前聊天" },
            isSelected = { it == Cfg.titleProfileId },
            onSelect = { Cfg.titleProfileId = it }
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.se_101), modifier = Modifier.height(38.dp)) { Agent.compactNow() }
            Spacer(Modifier.width(10.dp))
            Text(
                if (Agent.busy) stringResource(R.string.se_102) else stringResource(R.string.se_103),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        //总结补充要求：留空不追加，填了拼在内置四段提示词后面（结构永远保留）；自动总结、立即总结与长按重总结都走它
        Field(
            label = stringResource(R.string.se_sum_prompt),
            value = Cfg.summaryPrompt,
            onValueChange = { Cfg.summaryPrompt = it },
            placeholder = stringResource(R.string.se_sum_prompt_hint),
            singleLine = false,
            minLines = 3
        )
        Text(
            stringResource(R.string.se_sum_prompt_note),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }

    }
} //上下文与压缩子页

/**注入块：本机实时信息拼进系统提示。原来是独立子页，现已并入「注入输入与输出」页 */
@Composable
internal fun InjectBlock() {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_017), stringResource(R.string.se_104))
    CardBox {
        val ctx = LocalContext.current
        //位置权限不在自动申请清单里：只有你真的打开这个开关才弹，免得缺权限卡住工具调用
        val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            Cfg.injectPlace = it
        }
        //精确位置要单独要权限：用户开了「精确位置注入」才弹
        val finePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
            Cfg.injectPrecise = m[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        CapSwitch(
            stringResource(R.string.se_105),
            if (Cfg.injectTime) stringResource(R.string.se_106) else stringResource(R.string.se_107),
            Cfg.injectTime
        ) { Cfg.injectTime = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_108),
            if (Cfg.injectBattery) stringResource(R.string.se_109) else stringResource(R.string.se_110),
            Cfg.injectBattery
        ) { Cfg.injectBattery = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_111),
            when {
                !Cfg.injectPlace -> stringResource(R.string.se_112)
                Env.hasPlacePerm(ctx) -> stringResource(R.string.se_113)
                else -> stringResource(R.string.se_114)
            },
            Cfg.injectPlace
        ) { on ->
            if (on && !Env.hasPlacePerm(ctx)) {
                locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                Cfg.injectPlace = on
            }
        }
        //注入位置打开后才滑出扩展项：精确位置注入（现取一次定位）、自动转地名（结果加密后本机缓存）
        FollowBlock(Cfg.injectPlace) {
            Spacer(Modifier.height(6.dp))
            CapSwitch(
                stringResource(R.string.se_366),
                when {
                    !Cfg.injectPrecise -> stringResource(R.string.se_368)
                    Env.hasFinePerm(ctx) -> stringResource(R.string.se_367)
                    else -> stringResource(R.string.se_114)
                },
                Cfg.injectPrecise
            ) { on ->
                if (on && !Env.hasFinePerm(ctx)) {
                    finePerm.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else {
                    Cfg.injectPrecise = on
                }
            }
            Spacer(Modifier.height(6.dp))
            CapSwitch(
                stringResource(R.string.se_369),
                if (Cfg.placeGeo) stringResource(R.string.se_370) else stringResource(R.string.se_371),
                Cfg.placeGeo
            ) { Cfg.placeGeo = it }
        }
        Spacer(Modifier.height(8.dp))
        //通知与使用时长都要系统侧单独授权：开关先落地，缺权限就弹窗把人送去系统页
        CapSwitch(
            stringResource(R.string.se_320),
            when {
                !Cfg.injectNoti -> stringResource(R.string.se_321)
                !b.ai.core.task.hook.Noti.allow(ctx) -> stringResource(R.string.se_322)
                else -> stringResource(R.string.se_323, b.ai.core.task.hook.Noti.count(ctx))
            },
            Cfg.injectNoti
        ) { on ->
            if (on && !b.ai.core.task.hook.Noti.allow(ctx)) {
                Cfg.injectNoti = true
                PermAsk.kind.value = "noti"
            } else {
                Cfg.injectNoti = on
            }
        }
        FollowBlock(Cfg.injectNoti) {
            Spacer(Modifier.height(6.dp))
            NotiLimitRow()
        }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_328),
            when {
                !Cfg.injectUsage -> stringResource(R.string.se_329)
                !b.ai.core.data.usage.Usage.allow(ctx) -> stringResource(R.string.se_330)
                else -> stringResource(R.string.se_331, b.ai.core.data.usage.Usage.hm(b.ai.core.data.usage.Usage.today(ctx).first))
            },
            Cfg.injectUsage
        ) { on ->
            if (on && !b.ai.core.data.usage.Usage.allow(ctx)) {
                Cfg.injectUsage = true
                PermAsk.kind.value = "usage"
            } else {
                Cfg.injectUsage = on
            }
        }
    }
} //注入块

internal fun LazyListScope.chatSessionSec(
    stats: Pair<Int, Int>,
    onOpenImport: () -> Unit
) {
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_021), stringResource(R.string.se_129))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Agent.sessionTitle.ifBlank { stringResource(R.string.ag_new_session) }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                Text(
                    stringResource(R.string.se2_rec, stats.first, stats.second, Agent.sessions.size),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            GhostButton(stringResource(R.string.se_131), modifier = Modifier.height(38.dp)) { Agent.newSession() }
        }
    }

    }

    //会话列表：默认展开 5 条，「展开更多」每次多 5 条；每行右侧是操作菜单
    sessionListSec()

    dsImportEntry(onOpenImport)

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_023), stringResource(R.string.se_132))
    WsSection()
    }
} //会话与导出子页

/** 收藏夹子页：按会话聚合消息级收藏，点一下直接切到那个会话 */
internal fun LazyListScope.chatStarSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle("收藏夹", "按会话归组；点一行切到那个会话")
        CardBox {
            val hits = remember { Repo.starred() }
            if (hits.isEmpty()) {
                Text("还没有收藏任何消息。聊天里长按消息可以收藏。", fontSize = 12.sp, color = TextSecondary)
            }
            //同一会话的收藏折成一行，按最近收藏时间倒序
            val groups = hits.groupBy { it.sessionId }.entries
                .sortedByDescending { e -> e.value.maxOf { it.at } }
            groups.forEachIndexed { i, e ->
                if (i > 0) {
                    Spacer(Modifier.height(8.dp))
                }
                val sid = e.key
                val list = e.value
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            list.firstOrNull()?.title?.ifBlank { "未命名会话" } ?: "未命名会话",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            "收藏 " + list.size + " 条 · " + list.first().msg.content.replace("\n", " ").take(40),
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    GhostButton("打开", modifier = Modifier.height(34.dp)) {
                        Agent.openSession(sid)
                        Agent.refreshSessions()
                    }
                }
            }
        }
    }
} //收藏夹子页


/** 输入与输出：回复怎么展示/怎么流式、草稿怎么存、输出偏好常驻文案；与提示词优化分开 */
internal fun LazyListScope.chatInOutSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_ae27e4da), stringResource(R.string.tx_1e4ab751))
        CardBox {
            //输出偏好：常驻拼进系统提示，跟角色人设并行不覆盖
            Field(
                label = stringResource(R.string.tx_c083b46f),
                value = Cfg.replyStyle,
                onValueChange = { Cfg.replyStyle = it },
                placeholder = stringResource(R.string.tx_214a7d7d),
                singleLine = false,
                minLines = 3
            )
            Text(
                stringResource(R.string.tx_5489c494),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_019), stringResource(R.string.se_115))
    CardBox {
        CapSwitch(
            stringResource(R.string.se_116),
            if (Cfg.showUsage) stringResource(R.string.se_117) else stringResource(R.string.se_118),
            Cfg.showUsage
        ) { Cfg.showUsage = it }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_119),
            if (Cfg.showMeta) stringResource(R.string.se_120) else stringResource(R.string.se_121),
            Cfg.showMeta
        ) { Cfg.showMeta = it }
        Spacer(Modifier.height(8.dp))
        //Waifu 模式决定回复怎么切成多条，跟「显示什么」是同一类事，所以归到回复信息里
        CapSwitch(stringResource(R.string.se_175), stringResource(R.string.se_176), Cfg.waifuMode) {
            Cfg.waifuMode = it
        }
        //分句气泡动画依赖上面那条：只有切成多条时才有「一条条弹出来」的画面
        FollowBlock(Cfg.waifuMode) {
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_wbounce_t),
                stringResource(R.string.se_wbounce_d),
                Cfg.waifuBounce
            ) { Cfg.waifuBounce = it }
            Spacer(Modifier.height(8.dp))
            //更真实的分句引擎：切得更碎 + 气泡收窄到屏宽 3/7 + 更自然的弹出动画
            CapSwitch(
                stringResource(R.string.se_wreal_t),
                stringResource(R.string.se_wreal_d),
                Cfg.waifuReal
            ) { Cfg.waifuReal = it }
            Spacer(Modifier.height(8.dp))
            //打字速度：既决定两条气泡的间隔，也决定「正在输入」停留多久
            GlassSlider(
                stringResource(R.string.se_wtype_t),
                stringResource(R.string.se_wtype_v, Cfg.waifuTypeMs),
                Cfg.waifuTypeMs.toFloat(),
                20f..150f,
                step = 5f
            ) { Cfg.waifuTypeMs = it.toInt() }
            Spacer(Modifier.height(8.dp))
            CapSwitch(
                stringResource(R.string.se_wtyping_t),
                stringResource(R.string.se_wtyping_d),
                Cfg.waifuTyping
            ) { Cfg.waifuTyping = it }
        }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.se_ns_t),
            if (Cfg.noStream) stringResource(R.string.se_ns_on) else stringResource(R.string.se_ns_off),
            Cfg.noStream
        ) { Cfg.noStream = it }
    }

    }
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_22b4334f), stringResource(R.string.tx_b472edb3))
    CardBox {
        //图片默认不落盘：草稿里只留文字与文件引用，隐私风险与体积都能控住
        CapSwitch(
            stringResource(R.string.tx_4f3d139b),
            if (Cfg.draftKeepImg) stringResource(R.string.tx_a114c7a8) else stringResource(R.string.tx_79a86fd2),
            Cfg.draftKeepImg
        ) { Cfg.draftKeepImg = it }
        //有效期是跟随项：只有「保留图片」开着（草稿真的落盘）才滑下来，关着时整项不存在
        FollowBlock(Cfg.draftKeepImg) {
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_090286ed),
                if (Cfg.draftDays <= 0) stringResource(R.string.tx_5756d0e9) else stringResource(R.string.tx_5cd47992, Cfg.draftDays),
                listOf(0, 1, 3, 7, 30),
                optionLabel = { if (it <= 0) stringResource(R.string.tx_5756d0e9) else stringResource(R.string.tx_52273aab, it) },
                isSelected = { Cfg.draftDays == it },
                onSelect = { Cfg.draftDays = it }
            )
            Text(
                stringResource(R.string.tx_0dd7ef22),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
    }
    //注入块接在页尾：同属「给模型喂什么输入」这件事
    item { InjectBlock() }
} //注入输入与输出子页

internal fun LazyListScope.chatPromptSec() {
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.tx_a2d86e87), stringResource(R.string.tx_0e48617f))
    CardBox {
        val profiles = Profiles.list
        val cur = profiles.firstOrNull { it.id == Cfg.promptOptProfileId }
        //留空跟随当前聊天：省一份配置，多数人不需要单独开专用模型
        SelectField(
            stringResource(R.string.tx_c7049188),
            cur?.label ?: "跟随当前聊天",
            listOf("") + profiles.map { it.id },
            optionLabel = { id -> profiles.firstOrNull { it.id == id }?.label ?: "跟随当前聊天" },
            isSelected = { it == Cfg.promptOptProfileId },
            onSelect = { Cfg.promptOptProfileId = it }
        )
        Spacer(Modifier.height(8.dp))
        //保留原语言：中英混写时不至于被模型统一翻成英文
        CapSwitch(
            stringResource(R.string.tx_b886dc73),
            if (Cfg.promptOptKeep) stringResource(R.string.tx_d28ecec9) else stringResource(R.string.tx_7ec62ce2),
            Cfg.promptOptKeep
        ) { Cfg.promptOptKeep = it }
        Spacer(Modifier.height(8.dp))
        Field(
            label = stringResource(R.string.tx_84e40c91),
            value = Cfg.promptOptStyle,
            onValueChange = { Cfg.promptOptStyle = it },
            placeholder = stringResource(R.string.tx_65557288),
            singleLine = false,
            minLines = 3
        )
        Text(
            stringResource(R.string.tx_70d093e2),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
    }
} //提示词优化子页

internal fun LazyListScope.chatPermSec() {
    item { Spacer(Modifier.height(14.dp)) }
    permTab()
} //权限子页

