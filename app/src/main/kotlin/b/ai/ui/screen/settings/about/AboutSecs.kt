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

internal const val GITHUB_URL = "https://github.com/mubai0122/baicodex"

/** 关于：应用名 + 一句话描述 + 版本号 / 最新版本 + 检查更新 + 更新历史 + 官网 / GitHub 链接 */
internal fun LazyListScope.aboutTab(onOpenFeedback: () -> Unit, onOpenReward: () -> Unit) {
    //应用信息 +检查更新 +更新历史：状态只活在这一块里，滚出屏幕随 item 一起释放
    item {
    var latest by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var busyHis by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    //进页面静默查一次，只为了把「最新版本」那行填上，不弹卡片
    LaunchedEffect(Unit) {
        val info = withContext(Dispatchers.IO) { runCatching { Update.check() }.getOrNull() }
        latest = info?.latestVersion ?: ""
    }

    SectionTitle(stringResource(R.string.se_006))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .liquidBg(RoundedCornerShape(11.dp), tint = AccentBlueSoft, gate = 0.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Info, null, tint = AccentBlue, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Baicodex", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    stringResource(R.string.se_241),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se_242), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            //连点 5 次解锁开发者模式：解锁后才在关于页最下方放出开关，平时不占地方
            Text(
                "${Update.clientVersion()}",
                fontSize = 12.sp,
                color = TextPrimary,
                modifier = Modifier.tap {
                    if (!Cfg.devShow && Dev.tap()) {
                        Cfg.devShow = true
                        UpdateUi.tip = ctx.getString(R.string.tx_7e382efd)
                    }
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.se_243), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                latest.ifBlank { stringResource(R.string.se_244) },
                fontSize = 12.sp,
                color = if (latest.isBlank()) TextSecondary else OkGreen
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) stringResource(R.string.se_245) else stringResource(R.string.se_054), modifier = Modifier.height(38.dp)) {
                if (busy) return@GhostButton
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.check() } }
                    busy = false
                    r.onSuccess { info ->
                        latest = "${info.latestVersion}"
                        when {
                            //服务端关了更新开关
                            !info.updateEnabled -> UpdateUi.tip = ctx.getString(R.string.se2_upd_off, info.latestVersion)
                            info.hasUpdate -> UpdateUi.prompt = info
                            else -> UpdateUi.tip = ctx.getString(R.string.se2_upd_latest, info.latestVersion)
                        }
                    }.onFailure {
                        UpdateUi.tip = ctx.getString(R.string.se2_upd_fail, it.message?.take(80).orEmpty())
                    }
                }
            }
            GhostButton(if (busyHis) stringResource(R.string.se_133) else stringResource(R.string.se_250), modifier = Modifier.height(38.dp)) {
                if (busyHis) return@GhostButton
                busyHis = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Update.history() } }
                    busyHis = false
                    r.onSuccess { UpdateUi.history = it }
                        .onFailure {
                            UpdateUi.tip = ctx.getString(R.string.se2_upd_hist_fail, it.message?.take(80).orEmpty())
                        }
                }
            }
        }
    }
    }


    item {
    val ctx = LocalContext.current
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_252))
    CardBox {
        //服务端地址没配（fork 出去没填 local.properties）：链接置灰，点了也没意义
        LinkRow(stringResource(R.string.se_253), SITE_URL, enabled = b.ai.core.cfg.app.Bai.configured) {
            openUrl(ctx, SITE_URL)
        }
        Spacer(Modifier.height(6.dp))
        //仓库还没建，留空就显示成灰的、点不动
        LinkRow("GitHub", GITHUB_URL.ifBlank { stringResource(R.string.se_254) }, enabled = GITHUB_URL.isNotBlank()) {
            openUrl(ctx, GITHUB_URL)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    //反馈入口：单独一张卡，点进去是整页表单（分类 + 内容 + 联系方式）
    CardBox {
        Row(
            Modifier.fillMaxWidth().tap { onOpenFeedback() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft, gate = 0.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Email, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.fb_entry),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(stringResource(R.string.fb_entry_d), fontSize = 11.sp, color = TextSecondary)
            }
            Text("›", fontSize = 18.sp, color = TextSecondary)
        }
    }
    }

    //赞赏作者：紧跟在「意见反馈」卡片下面，单独一张卡
    rewardEntry(onOpenReward)

    item {
    Spacer(Modifier.height(14.dp))
    //评价调研：开关控制要不要接受邀请；下面一条是手动入口，点开就是同款 Star 卡片
    SectionTitle(stringResource(R.string.rate_title), stringResource(R.string.rate_title_d))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rate_sw), fontSize = 14.sp, color = TextPrimary)
                Text(stringResource(R.string.rate_sw_d), fontSize = 11.sp, color = TextSecondary)
            }
            Switch(
                checked = !Cfg.noRate,
                onCheckedChange = { Cfg.noRate = !it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted, gate = 0.dp)
                .tap { Rate.askManual() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rate_star_row), fontSize = 13.sp, color = TextPrimary)
                Text(stringResource(R.string.rate_star_row_d), fontSize = 11.sp, color = TextSecondary)
            }
            Text("›", fontSize = 18.sp, color = TextSecondary)
        }
    }
    }

    item {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_bk), stringResource(R.string.se_bk_d))
    CardBox {
        var bkTip by remember { mutableStateOf("") }
        var bkBusy by remember { mutableStateOf(false) }
        //导出：系统选择器给个默认文件名，打出来的是加密后的 .baicodex
        val bkSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                bkBusy = true
                bkTip = ctx.getString(R.string.se_bk_doing)
                scope.launch {
                    //把导出过程里的每一行日志收起来：失败时把最后一条原因（头不对 / 密钥不对）直接给用户看
                    val logs = java.util.Collections.synchronizedList(ArrayList<String>())
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                                Backup.export(ctx, out, Cfg.bkSecrets) { logs.add(it) }
                            } ?: false
                        }.getOrDefault(false)
                    }
                    bkBusy = false
                    bkTip = if (ok) ctx.getString(R.string.se_bk_ok)
                    else logs.lastOrNull()?.let { ctx.getString(R.string.se_bk_fail_x, it) }
                        ?: ctx.getString(R.string.se_bk_fail)
                }
            }
        }
        //导入：先解到私有 restore 目录，重启后由 App.onCreate 换上去
        val bkOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                bkBusy = true
                bkTip = ctx.getString(R.string.se_bk_doing)
                scope.launch {
                    //同上：解不开 / 不是 zip 这类原因直接抛到界面上，不让用户只看到一句「失败」
                    val logs = java.util.Collections.synchronizedList(ArrayList<String>())
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                                Backup.stageImport(ctx, ins) { logs.add(it) }
                            } ?: false
                        }.getOrDefault(false)
                    }
                    bkBusy = false
                    bkTip = if (ok) ctx.getString(R.string.se_bk_imported)
                    else logs.lastOrNull()?.let { ctx.getString(R.string.se_bk_bad_x, it) }
                        ?: ctx.getString(R.string.se_bk_bad)
                }
            }
        }
        Text(stringResource(R.string.se_bk_note), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        //加密配置开关：默认开，导出时把凭据密文一起打进包里，换机能直接恢复
        CapSwitch(
            stringResource(R.string.se_bk_secrets),
            stringResource(R.string.se_bk_secrets_d),
            Cfg.bkSecrets
        ) { Cfg.bkSecrets = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(stringResource(R.string.se_bk_export), modifier = Modifier.height(38.dp)) {
                if (!bkBusy) bkSave.launch("Baicodex-backup-${System.currentTimeMillis()}.baicodex")
            }
            GhostButton(stringResource(R.string.se_bk_import), modifier = Modifier.height(38.dp)) {
                //老备份是 zip、新备份是 .baicodex，系统都不认识这两个后缀的 mime，放开成任意文件
                if (!bkBusy) bkOpen.launch(arrayOf("*/*"))
            }
        }
        if (bkTip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(bkTip, fontSize = 11.sp, color = TextSecondary)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    //隐私与安全：单独一个分块放在关于页最后，不占分类位，也不挨着评价与调研
    SectionTitle(stringResource(R.string.se_sec_tab), stringResource(R.string.se_sec_sub))
    GuardSection()
    }

    item {
    Spacer(Modifier.height(14.dp))
    //新增保护：注入与泄漏相关的两个开关，同样不占分类位，排在隐私与安全后面
    SectionTitle(stringResource(R.string.se_pt_tab), stringResource(R.string.se_pt_sub))
    ProtectSection()
    }

    //Rust 内核：并行接入阶段的可见入口，只跑自检，不参与任何业务读写（删掉这块即可回滚）
    //位置：关于页最底部（用户要求「Rust 自检放最下面」），排在开发者工具之前
    item {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.se_kernel_t))
    CardBox {
        Text(stringResource(R.string.se_kernel_d), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            //版本 + 状态：没带内核的 ABI（v7a）如实说明，不假装成功
            Text("v" + BxKernel.version().ifBlank { "—" }, fontSize = 12.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            val okNow = out.contains("\"ok\":true")
            val (label, tint) = when {
                !BxKernel.available -> stringResource(R.string.se_kernel_na) to TextSecondary
                okNow -> stringResource(R.string.se_kernel_ok) to OkGreen
                out.isBlank() -> stringResource(R.string.se_kernel_run) to TextSecondary
                else -> stringResource(R.string.se_kernel_fail) to DangerRed
            }
            Text(label, fontSize = 11.sp, color = tint)
        }
        Spacer(Modifier.height(10.dp))
        GhostButton(
            if (busy) stringResource(R.string.se_kernel_busy) else stringResource(R.string.se_kernel_run),
            modifier = Modifier.height(38.dp)
        ) {
            if (busy) return@GhostButton
            busy = true
            scope.launch {
                //自检会真写盘（临时桶写完即删），放到 IO 线程跑
                val r = withContext(Dispatchers.IO) { BxKernel.selfcheck() }
                busy = false
                out = r
                err = if (r.isBlank()) BxKernel.lastError() else ""
            }
        }
        if (out.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(out, fontSize = 9.sp, color = TextSecondary)
        }
        if (err.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(err, fontSize = 9.sp, color = DangerRed)
        }
    }
    }

    //开发者工具：只有连点版本号解锁过才出现，摆在关于页最下面，不干扰普通用户
    //（这里是 LazyListScope，不是 Composable 作用域，展开动画交给 item 内部做）
    if (Cfg.devShow) {
        item {
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.tx_c2e48855))
            DevSection()
        }
    }
} //关于分类

