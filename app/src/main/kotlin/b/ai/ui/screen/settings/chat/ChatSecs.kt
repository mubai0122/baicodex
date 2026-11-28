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

/** 总结补充要求可用的占位变量：取值在 Agent.summarySystem / SessionOps 里替换，这里只负责让用户点得着 */
/** 总结提示词可用的占位变量：变量名 → 会被替换成什么（点一下追加到补充要求末尾） */
private val SUM_VARS = listOf(
    "{level}" to "当前摘要档位名：简明 / 详细 / 全面",
    "{words}" to "字数上限的数字，0 表示不限",
    "{limit}" to "整句写法：如「800字以内」，不限时是一句说明",
    "{keep}" to "保留最近 N 条消息，跟随「保留条数」",
    "{budget}" to "上下文预算 token 数，跟随「上下文预算」"
)

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
        //总结补充要求：留空不追加，填了拼在内置提示词后面；自动总结、立即总结与长按重总结都走它
        Field(
            label = stringResource(R.string.se_sum_prompt),
            value = Cfg.summaryPrompt,
            onValueChange = { Cfg.summaryPrompt = it },
            //空输入框里直接给说明：这一栏只做「补充」，内置段落骨架不动
            placeholder = "补充总结要求，原要求不受影响",
            singleLine = false,
            minLines = 3
        )
        Spacer(Modifier.height(6.dp))
        Spacer(Modifier.height(12.dp))
        //可用变量：一行一个，左边变量名可点插入，右边写清这个变量会被替换成什么
        Text("可用变量（点一下插入到上面输入框末尾）", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth()) {
            SUM_VARS.forEach { (v, desc) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        //整行可点：省得只点得中那个小胶囊
                        .tap {
                            //分隔符按需补：空内容直接写，末尾是空白就只追加
                            val cur = Cfg.summaryPrompt
                            Cfg.summaryPrompt = when {
                                cur.isBlank() -> v
                                cur.last().isWhitespace() -> cur + v
                                else -> cur + " " + v
                            }
                        }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        v,
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft, gate = 0.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(desc, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                }
            }
        } //变量逐行列表
    }

    }
} //上下文与压缩子页

/** 记忆条数滑块的右端：拉到最右端视为不限（存 0），避开了再造一个「不限」开关 */
private const val MEM_MAX = 100f

/**注入块：本机实时信息拼进系统提示。项与项之间互不影响，开一项发一项，全关就一个字都不多发 */
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
        //注入项：每项一条开关，开一项注入一项；拼接顺序跟这里的顺序一致
        CapSwitch("时间", "日期进系统提示，时分随每条请求刷新", Cfg.injectTime) { Cfg.injectTime = it }
        Spacer(Modifier.height(6.dp))
        CapSwitch("电量", "当前电量百分数与是否在充电", Cfg.injectBattery) { Cfg.injectBattery = it }
        Spacer(Modifier.height(6.dp))
        //位置：没授权先要权限，授权回调里再落地开关，避免出现「开关亮了但没权限」
        CapSwitch("位置", "大致位置，首次开启会申请定位权限", Cfg.injectPlace) { on ->
            if (on && !Env.hasPlacePerm(ctx)) {
                locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                Cfg.injectPlace = on
            }
        }
        //子项紧跟父开关：展开的内容落在「位置」下面，不掉到整卡最底部
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
        } //位置子项
        Spacer(Modifier.height(6.dp))
        //通知：系统侧要单独授权，缺权限就跳到授权页
        CapSwitch("通知", "最近几条通知摘要，需要通知使用权", Cfg.injectNoti) { on ->
            if (on && !b.ai.core.task.hook.Noti.allow(ctx)) {
                Cfg.injectNoti = true
                PermAsk.kind.value = "noti"
            } else {
                Cfg.injectNoti = on
            }
        }
        //子项紧跟父开关：通知条数只在「通知」下面出现
        FollowBlock(Cfg.injectNoti) {
            Spacer(Modifier.height(6.dp))
            NotiLimitRow()
        } //通知子项
        Spacer(Modifier.height(6.dp))
        //用量：使用情况访问权限同理
        CapSwitch("用量", "今日各应用使用时长，需要使用情况访问权限", Cfg.injectUsage) { on ->
            if (on && !b.ai.core.data.usage.Usage.allow(ctx)) {
                Cfg.injectUsage = true
                PermAsk.kind.value = "usage"
            } else {
                Cfg.injectUsage = on
            }
        }
        Spacer(Modifier.height(6.dp))
        CapSwitch("设备", "屏幕分辨率、内存、ABI 与 CPU 核数", Cfg.injectDevice) { Cfg.injectDevice = it }
        Spacer(Modifier.height(6.dp))
        CapSwitch("记忆", "长期记忆条目，条数可在下方调整", Cfg.injectMem) { Cfg.injectMem = it }
        //子项紧跟父开关：记忆条数滑块贴在「记忆」下面，默认 35，拉到最右端 = 不限
        FollowBlock(Cfg.injectMem) {
            Spacer(Modifier.height(8.dp))
            GlassSlider(
                "记忆注入条数",
                if (Cfg.injectMemCount <= 0) "不限" else stringResource(R.string.se2_n_items, Cfg.injectMemCount),
                if (Cfg.injectMemCount <= 0) MEM_MAX else Cfg.injectMemCount.toFloat().coerceAtMost(MEM_MAX),
                1f..MEM_MAX,
                1f
            ) { v ->
                val n = v.toInt()
                //拉到最右端存 0 = 不限，其余按实际条数存
                Cfg.injectMemCount = if (n >= MEM_MAX.toInt()) 0 else n
            }
        } //记忆子项
        Spacer(Modifier.height(6.dp))
        //一条状态行：把「哪几项真的生效」说清，避免勾了没反应时找不到原因
        Text(
            injectSummary(ctx),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //注入块

/** 注入项状态摘要：只报当前开着的项，全关时给一句提示 */
private fun injectSummary(ctx: android.content.Context): String {
    val on = ArrayList<String>(7)
    if (Cfg.injectTime) on.add("时间")
    if (Cfg.injectBattery) on.add("电量")
    if (Cfg.injectPlace) on.add("位置")
    if (Cfg.injectNoti) on.add("通知")
    if (Cfg.injectUsage) on.add("用量")
    if (Cfg.injectDevice) on.add("设备")
    if (Cfg.injectMem) on.add("记忆")
    if (on.isEmpty()) return "当前没有注入任何信息"
    val mem = if (!Cfg.injectMem) "" else if (Cfg.injectMemCount <= 0) "（记忆不限条数）" else "（记忆 " + Cfg.injectMemCount + " 条）"
    return "已注入：" + on.joinToString("、") + mem
} //注入摘要

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

/** 收藏夹子页：按会话聚合消息级收藏，点「打开」跳到聊天页并定位到那条消息 */
internal fun LazyListScope.chatStarSec(onOpenChat: (String, Long) -> Unit) {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle("收藏夹", "按会话归组；点「打开」跳到聊天并定位到那条消息")
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
                        //原来只 openSession：设置页还留在原地，界面上什么都不动，看着就是「没反应」。
                        //现在交给外层切页面，并带上该会话里最近那条收藏的消息 id，聊天页会滚过去定位
                        val mid = list.maxByOrNull { it.at }?.msg?.id ?: 0L
                        onOpenChat(sid, mid)
                        Agent.refreshSessions()
                    }
                }
            }
        }
    }
} //收藏夹子页


/** 输入与输出：回复怎么展示/怎么流式、草稿怎么存、输出偏好常驻文案；与提示词优化分开 */
/**注入 → 输入 → 输出：按「喂进去什么 / 你自己写什么 / 回来的东西怎么显示」重新排，注入与输入改得最勤所以放前面 */
internal fun LazyListScope.chatInOutSec() {
    //第一块：注入（本机实时信息）
    item { InjectBlock() }

    //第二块：输入（草稿怎么存）
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle("输入", stringResource(R.string.tx_b472edb3))
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

    //第三块：输出（回复信息 + 输出偏好，同一张卡里上下两段）
    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle("输出", stringResource(R.string.tx_1e4ab751))
    CardBox {
        //附带请求信息：默认收起的一行，点开才逐项列开关；每项一条独立开关，全部默认开启
        MetaFold()
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
        Spacer(Modifier.height(14.dp))
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
} //注入输入与输出子页

/**
 * 附带请求信息：收起时只占一行，显示「已开/总数」，点一下展开成逐项开关。
 * 每一项都是一条独立开关（不是芯片多选），默认全开；不用的时候整行收起，不占版面。
 */
@Composable
private fun MetaFold() {
    //展开状态只活在本页生命周期里：离开设置页就回到收起，跟其它折叠块一致
    var open by remember { mutableStateOf(false) }
    //顶层三组：用量、速度、请求信息（后者的子项再逐条列在下面）
    val top = listOf(Cfg.showUsage, Cfg.showSpeed, Cfg.showMeta)
    Row(
        Modifier
            .fillMaxWidth()
            .tap { open = !open }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("附带请求信息", fontSize = 13.5.sp, color = TextPrimary)
            Text(
                "回复底部那行小字附带什么，逐项可关，默认全开",
                fontSize = 10.5.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(10.dp))
        //收起时给个进度：一眼看出还有几项开着，不用展开
        Text("${top.count { it }}/${top.size}", fontSize = 12.sp, color = AccentBlue)
    } //折叠标题行
    FollowBlock(open) {
        Spacer(Modifier.height(6.dp))
        //用量：token 数一定显，速度单独一项
        CapSwitch("用量", "本轮 token 数", Cfg.showUsage) { Cfg.showUsage = it }
        Spacer(Modifier.height(6.dp))
        CapSwitch("生成速度", "tok/s，流式才有", Cfg.showSpeed) { Cfg.showSpeed = it }
        Spacer(Modifier.height(6.dp))
        //请求信息总开关：关掉后下面的耗时/首字/时间/厂商/模型全部不显示
        CapSwitch("请求信息", "耗时、首字、时间、厂商、模型", Cfg.showMeta) { Cfg.showMeta = it }
        FollowBlock(Cfg.showMeta) {
            Spacer(Modifier.height(6.dp))
            CapSwitch("耗时", "整轮生成用了多久", Cfg.showLatency) { Cfg.showLatency = it }
            Spacer(Modifier.height(6.dp))
            CapSwitch("首字延迟", "首个字出来的时间", Cfg.showFirst) { Cfg.showFirst = it }
            Spacer(Modifier.height(6.dp))
            CapSwitch("时间", "这条回复的时间点", Cfg.showTime) { Cfg.showTime = it }
            Spacer(Modifier.height(6.dp))
            CapSwitch("厂商", "走的是哪家服务", Cfg.showProvider) { Cfg.showProvider = it }
            Spacer(Modifier.height(6.dp))
            CapSwitch("模型", "具体模型名", Cfg.showModel) { Cfg.showModel = it }
        } //请求信息细分
    } //展开的子项
} //附带请求信息折叠组
/** 提示词优化：目标语种候选（auto = 自动，由模型按原文判断），关掉「保留原语言」后才出现 */
private val OPT_LANGS = listOf("auto", "中文", "英文", "日文", "韩文", "法文", "德文", "俄文", "西班牙文", "葡萄牙文")

/** 语种下拉的显示名：auto（以及历史存下的空串）都显示成「自动」 */
private fun floatLangLabel(v: String): String = if (v.isBlank() || v == "auto") "自动" else v

/** 优化偏好档位：管「写多少」。取值要跟 PromptOptimizer.levelRule 对齐 */
private val OPT_LEVELS = listOf("auto", "brief", "full", "detail")

/** 优化偏好风格：管「往哪个方向写」。取值要跟 PromptOptimizer.toneRule 对齐 */
private val OPT_TONES = listOf("auto", "strict", "code", "draw", "science")

/** 档位显示名：认不出的值一律当「自动」 */
private fun levelLabel(key: String): String = when (key) {
    "brief" -> "极简"
    "full" -> "全面"
    "detail" -> "详细"
    else -> "自动"
} //档位名

/** 风格显示名：认不出的值一律当「自动」 */
private fun toneLabel(key: String): String = when (key) {
    "strict" -> "严谨"
    "code" -> "代码"
    "draw" -> "绘图"
    "science" -> "科学"
    else -> "自动"
} //风格名

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
        //关掉「保留原语言」才出现目标语种：空 = 默认语种（不改语言），选了就要求结果统一成该语种
        FollowBlock(!Cfg.promptOptKeep) {
            Spacer(Modifier.height(8.dp))
            SelectField(
                "默认语种",
                floatLangLabel(Cfg.promptOptLang),
                OPT_LANGS,
                optionLabel = { floatLangLabel(it) },
                isSelected = { it == Cfg.promptOptLang },
                onSelect = { Cfg.promptOptLang = it }
            )
            Spacer(Modifier.height(6.dp))
            // 语义写清楚：「保留」是原文语种不动，「不保留」才按这里选的语种统一输出
            Text(
                "开启保留原语言时：原文写的中文就还是中文，只改表达不翻译；" +
                    "关闭后按这里的语种统一输出，选「自动」则由模型按原文判断。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        } //语种下拉
        Spacer(Modifier.height(8.dp))
        //永不询问优化方向：默认开。开了以后优化器不许反问，哪怕原话只有一个字也自己补全，补不了就原样返回
        CapSwitch(
            "永不询问优化方向",
            if (Cfg.promptOptNoAsk) "优化时不会反问，信息不够就按最短理解直接改；实在改不了就保留原话" else "信息不够时会输出「请先确认」让你补充",
            Cfg.promptOptNoAsk
        ) { Cfg.promptOptNoAsk = it }
        Spacer(Modifier.height(8.dp))
        //自定义优化偏好：总开关，开了才展开档位与风格两个下拉（拼进优化提示词的篇幅与取向要求）
        CapSwitch(
            "自定义优化偏好",
            if (Cfg.promptOptPrefOn) "按下面的档位与风格改写" else "关闭时由优化模型自己判断详略与写法",
            Cfg.promptOptPrefOn
        ) { Cfg.promptOptPrefOn = it }
        FollowBlock(Cfg.promptOptPrefOn) {
            Spacer(Modifier.height(8.dp))
            SelectField(
                "档位",
                levelLabel(Cfg.promptOptLevel),
                OPT_LEVELS,
                optionLabel = { levelLabel(it) },
                isSelected = { it == Cfg.promptOptLevel },
                onSelect = { Cfg.promptOptLevel = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "风格",
                toneLabel(Cfg.promptOptTone),
                OPT_TONES,
                optionLabel = { toneLabel(it) },
                isSelected = { it == Cfg.promptOptTone },
                onSelect = { Cfg.promptOptTone = it }
            )
        } //偏好下拉
        Spacer(Modifier.height(8.dp))
        //开启自定义补充优化：开了才显示下面的输入框，关掉时既不显示也不往提示词里拼
        CapSwitch(
            "开启自定义补充优化",
            if (Cfg.promptOptExtraOn) "下面的「优化方向自定义」会追加到优化提示词末尾" else "关闭时不追加任何自定义写法要求",
            Cfg.promptOptExtraOn
        ) { Cfg.promptOptExtraOn = it }
        FollowBlock(Cfg.promptOptExtraOn) {
            Field(
                label = stringResource(R.string.tx_84e40c91),
                value = Cfg.promptOptStyle,
                onValueChange = { Cfg.promptOptStyle = it },
                placeholder = stringResource(R.string.tx_65557288),
                singleLine = false,
                minLines = 3
            )
        } //自定义方向
    }
    }
} //提示词优化子页

internal fun LazyListScope.chatPermSec() {
    item { Spacer(Modifier.height(14.dp)) }
    permTab()
} //权限子页