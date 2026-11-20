/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:21
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.misc
import b.ai.device.shell.root.Root
import b.ai.ui.dialog.Overlay
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.screen.settings.A11yPermCard
import b.ai.ui.screen.settings.ModelSetupPanel
import b.ai.ui.screen.settings.MirrorPickerDialog
import b.ai.ui.screen.settings.SpecialPermCard
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.CircleArrowButton
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcTick
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.tap
import b.ai.net.api.llm.text

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import b.ai.R
import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.app.Lang
import b.ai.core.perm.Perms
import b.ai.net.doc.Consent
import b.ai.core.model.chat.WorkMode
import b.ai.core.plugin.skill.Workflows
import b.ai.device.shell.local.Linux
import b.ai.device.shell.local.LinuxMirror
import b.ai.device.sys.probe.Router
import b.ai.device.shell.root.Shiz
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 引导流程：语言 → 工作方式 → 权限检查（含零宽弹窗）→ 工作流 → 装环境（可跳过）→ API key（可跳过）→ 用户协议 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    //协议步的勾选：没勾就不给「同意并进入」
    var tosOk by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var zwSupport by remember { mutableStateOf<Boolean?>(null) }
    var modeMsg by remember { mutableStateOf<String?>(null) }
    var modeOk by remember { mutableStateOf(false) }
    var showZwPrompt by remember { mutableStateOf(false) }
    var shizMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick) {
        zwSupport = withContext(Dispatchers.IO) { Router.probe() }
    }

    LaunchedEffect(step, tick) {
        if (step != 2) return@LaunchedEffect
        modeMsg = null
        modeOk = false
        when (Cfg.workMode) {
            WorkMode.ROOT -> {
                val p = withContext(Dispatchers.IO) { Root.probe() }
                modeOk = p.ok
                modeMsg = p.desc
            } //root：探 su 二进制 + 真实申请授权
            WorkMode.SHIZUKU -> {
                val p = withContext(Dispatchers.IO) { Shiz.probe(ctx) }
                modeOk = p.ok
                modeMsg = p.desc
            } //Shizuku：binder + 授权状态
            WorkMode.NORMAL -> {
                modeOk = Cfg.zeroWidth && zwSupport == true
                modeMsg = ctx.getString(R.string.ob_normal_msg)
            }
        }
        if (Cfg.workMode == WorkMode.NORMAL && zwSupport == true && !Cfg.zeroWidth) {
            showZwPrompt = true
        }
    } //进入权限步骤时做真实检测

    DisposableEffect(showZwPrompt) {
        Overlay.active = showZwPrompt
        val dialog: (@Composable () -> Unit)? = if (showZwPrompt) {
            {
                ZeroWidthDialog(
                    probeName = Router.probeName(ctx),
                    onClose = { showZwPrompt = false },
                    onEnable = {
                        Cfg.zeroWidth = true
                        showZwPrompt = false
                    }
                )
            }
        } else null
        Overlay.content = dialog
        onDispose {
            Overlay.active = false
            Overlay.content = null
        }
    } //零宽弹窗挂到根弹窗层，卡片背后才能真正模糊

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp)
            .verticalScroll(glassScrollState())
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Baicodex", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(stringResource(R.string.ob_sub), fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(
                when (step) {
                    0 -> R.string.ob_step_lang
                    1 -> R.string.ob_step1
                    2 -> R.string.ob_step2
                    3 -> R.string.ob_step_grant
                    4 -> R.string.ob_step3
                    5 -> R.string.ob_step4
                    6 -> R.string.ob_step5
                    else -> R.string.ob_step_tos
                }
            ),
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))

//步骤切换的过渡：前进从右侧滑入、后退从左侧滑入，旧页反向淡出，方向感一致
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val fwd = targetState >= initialState
                (
                    slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
                        initialOffsetX = { w -> if (fwd) w else -w }
                    ) + fadeIn(tween(160))
                ).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(200),
                        targetOffsetX = { w -> if (fwd) -w else w }
                    ) + fadeOut(tween(140))
                )
            },
            label = "obStep"
        ) { s ->
            Box(Modifier.fillMaxWidth()) {
                when (s) {
                    0 -> StepLang()
                    1 -> StepMode()
                    2 -> StepPermission(
                        zwSupport = zwSupport,
                        modeMsg = modeMsg,
                        modeOk = modeOk,
                        onRecheck = {
                            Router.reset()
                            tick++
                        },
                        onRequestShizuku = {
                            shizMsg = null
                            Shiz.ask { r ->
                                when (r) {
                                    Shiz.Perm.GRANTED -> {
                                        modeOk = true
                                        modeMsg = ctx.getString(R.string.ob_shizuku_ok)
                                    }

                                    Shiz.Perm.NEVER_ASK -> shizMsg = ctx.getString(R.string.shiz_manual)
                                    Shiz.Perm.NO_SERVICE -> shizMsg = ctx.getString(R.string.shiz_no_service)
                                    Shiz.Perm.ERROR -> shizMsg = ctx.getString(R.string.shiz_timeout)
                                    else -> shizMsg = ctx.getString(R.string.ob_shizuku_deny)
                                }
                            }
                        },
                        shizMsg = shizMsg,
                        onOpenShizuku = { Shiz.openManager(ctx) }
                    )
                    3 -> StepGrant()
                    4 -> StepWorkflow()
                    5 -> StepEnv()
                    6 -> Column {
                        CardBox { ModelSetupPanel() }
                        Spacer(Modifier.height(12.dp))
                        //藏在最后一步的小开关：不想被评价弹窗打扰就勾这里
                        Row(
                            Modifier.fillMaxWidth().tap { Cfg.noRate = !Cfg.noRate },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(15.dp)
                                    .liquidBg(RoundedCornerShape(5.dp), tint = if (Cfg.noRate) AccentBlue else SurfaceMuted),
                                contentAlignment = Alignment.Center
                            ) { if (Cfg.noRate) IcTick(Color.White, 9.dp) }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.rate_off), fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                    else -> TosBody(tosOk, { tosOk = it }, Modifier.height(320.dp))
                }
            }
        } //步骤内容 + 过渡动画

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                GhostButton(stringResource(R.string.ob_prev)) { step-- }
                Spacer(Modifier.width(10.dp))
            }
            if (step == 6) {
                //跳过 API key 也要过协议，所以直接落到下一步
                GhostButton(stringResource(R.string.ob_skip)) { step = 7 }
                Spacer(Modifier.width(10.dp))
            }
            if (step < 7) {
                //「下一步」是圆形箭头：主操作只留一个圆，小屏也不挤
                CircleArrowButton(
                    enabled = step < 6 || Cfg.ready,
                    desc = stringResource(R.string.ob_next)
                ) { step++ }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.tos_agree),
                    enabled = tosOk
                ) {
                    //同意协议才进应用，并把同意状态上报一次
                    Cfg.tosAgreed = Tos.version()
                    Cfg.onboarded = true
                    Consent.reportIfNeeded()
                    onDone()
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ZeroWidthDialog(probeName: String, onClose: () -> Unit, onEnable: () -> Unit) {
    ScrimCard {
        Text(stringResource(R.string.ob_zw_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ob_zw_body, probeName),
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.ob_zw_later)) { onClose() }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(stringResource(R.string.ob_zw_enable)) { onEnable() }
        }
    }
} //零宽拓展弹窗

@Composable
private fun StepLang() {
    val act = LocalContext.current as? Activity
    CardBox {
        Lang.options.forEach { (code, label) ->
            //语言名用各自的语言写死，任何界面语言下都认得出自己那项
            ChoiceRow(
                label,
                if (code.isBlank()) stringResource(R.string.ob_lang_follow) else null,
                Cfg.lang == code
            ) {
                if (Cfg.lang == code) return@ChoiceRow
                Cfg.lang = code
                //换语言必须重建 Activity 才生效，重建后还停在这一步
                act?.recreate()
            }
        }
    }
} //语言选择

@Composable
private fun StepMode() {
    CardBox {
        WorkMode.entries.forEach { m ->
            ChoiceRow(stringResource(m.labelRes), stringResource(m.descRes), Cfg.workMode == m) { Cfg.workMode = m }
        }
    }
}

@Composable
private fun StepGrant() {
    val ctx = LocalContext.current
    //状态轮询：从系统设置页返回后自动刷新，不用手动点
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            tick++
        }
    }
    //进入本步主动弹一次「所有文件访问」：Android 11+ 这个是特殊权限，只能跳系统页手动开
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoOpened && Build.VERSION.SDK_INT >= 30 && !Perms.allFiles()) {
            autoOpened = true
            Perms.openAllFiles(ctx)
        }
    }
    CardBox {
        Text(stringResource(R.string.ob_grant_note), fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        //全部文件访问：Android 11+ 才有，开了工作区 / 备份 / 下载才能写进公共目录
        if (Build.VERSION.SDK_INT >= 30) {
            val filesOn = remember(tick) { Perms.allFiles() }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ob_grant_files), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (filesOn) R.string.ob_grant_on else R.string.ob_grant_off),
                            fontSize = 11.sp,
                            color = if (filesOn) OkGreen else DangerRed
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.ob_grant_files_desc), fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.width(10.dp))
                if (filesOn) {
                    Text("✓", fontSize = 14.sp, color = OkGreen)
                } else {
                    GhostButton(stringResource(R.string.ob_grant_go)) {
                        Perms.openAllFiles(ctx)
                        tick++
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        //电池保活 + 悬浮窗：交给通用特殊权限卡，内部自带 1.5s 轮询
        SpecialPermCard(
            listOf(Perms.SP_BATTERY, Perms.SP_OVERLAY),
            stringResource(R.string.ob_grant_special)
        )
    }
    Spacer(Modifier.height(12.dp))
    //运行期权限：麦克风 / 通知（13+）等，走系统授权弹窗
    CardBox {
        Text(stringResource(R.string.ob_grant_runtime), fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.ob_grant_runtime_go)) {
                val act = ctx as? Activity ?: return@GhostButton
                ActivityCompat.requestPermissions(act, Perms.runtime().toTypedArray(), 1002)
            }
        }
    }
} //权限授予步骤

@Composable
private fun StepPermission(
    zwSupport: Boolean?,
    modeMsg: String?,
    modeOk: Boolean,
    onRecheck: () -> Unit,
    onRequestShizuku: () -> Unit,
    shizMsg: String?,
    onOpenShizuku: () -> Unit
) {
    val ctx = LocalContext.current
    CardBox {
        Text(stringResource(R.string.ob2_mode_fmt, stringResource(Cfg.workMode.labelRes)), fontSize = 14.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            modeMsg ?: stringResource(R.string.ob2_detecting),
            fontSize = 12.sp,
            color = if (modeMsg == null) TextSecondary else if (modeOk) OkGreen else DangerRed
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.ob2_zero_pre) + when (zwSupport) {
                null -> stringResource(R.string.ob2_detecting)
                true -> stringResource(R.string.ob2_zero_yes, Router.probeName(ctx))
                false -> stringResource(R.string.ob2_zero_no)
            },
            fontSize = 12.sp,
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Row {
            GhostButton(stringResource(R.string.ob2_recheck)) { onRecheck() }
            if (Cfg.workMode == WorkMode.SHIZUKU) {
                Spacer(Modifier.width(10.dp))
                GhostButton(stringResource(R.string.ob2_shiz)) { onRequestShizuku() }
            }
            if (Cfg.workMode == WorkMode.NORMAL && Build.VERSION.SDK_INT <= 32) {
                Spacer(Modifier.width(10.dp))
                GhostButton(stringResource(R.string.ob2_storage)) {
                    val act = ctx as? Activity ?: return@GhostButton
                    val perms = if (Build.VERSION.SDK_INT <= 29) {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    ActivityCompat.requestPermissions(act, perms, 1001)
                }
            }
        }
        if (shizMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(shizMsg, fontSize = 11.sp, color = DangerRed)
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.shiz_open_app)) { onOpenShizuku() }
        }
        Spacer(Modifier.height(14.dp))
        //特殊权限按模式给：普通用户三样都要，Root / Shizuku 只要悬浮窗
        //无障碍卡在引导流程里也一律出现：Shizuku / Root 模式下它是「一键激活」入口
        val needA11y = Perms.specials(Cfg.workMode).contains(Perms.SP_A11Y)
        A11yPermCard("普通用户让 AI 代你操作屏幕，靠的就是这个开关。现在不开启也能先用，之后在设置 → 权限 里随时能开。")
        Spacer(Modifier.height(12.dp))
        SpecialPermCard(
            Perms.specials(Cfg.workMode).filter { it != Perms.SP_A11Y },
            if (needA11y) stringResource(R.string.tx_54763353)
            else stringResource(R.string.tx_34d391e0)
        )
    }
}

@Composable
private fun StepWorkflow() {
    CardBox {
        Workflows.all.forEach { (id, label) ->
            ChoiceRow(label, stringResource(R.string.ob2_mode_d), Cfg.workflow == id) {
                Cfg.workflow = id
            }
        }
    }
}

@Composable
private fun StepEnv() {
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(Linux.installed()) }
    var log by remember { mutableStateOf("") }
    var self by remember { mutableStateOf("") }
    var showMirror by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canRun = Linux.canRun()
    CardBox {
        Text(stringResource(R.string.ob2_linux_q), fontSize = 14.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ob2_linux_d),
            fontSize = 12.sp,
            color = TextSecondary
        )
        if (!canRun) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ob2_linux_warn, stringResource(Cfg.workMode.labelRes)),
                fontSize = 12.sp,
                color = DangerRed
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (ready) stringResource(R.string.ob2_env_ok) else stringResource(R.string.ob2_env_no),
            fontSize = 12.sp,
            color = if (ready) OkGreen else TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton(
                text = if (busy) stringResource(R.string.ob2_installing) else if (ready) stringResource(R.string.ob2_reinstall) else stringResource(R.string.ob2_install),
                enabled = !busy && canRun
            ) {
                if (busy) return@PrimaryButton
                busy = true
                log = ""
                self = ""
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        //解包期间逐行回显，用户能看见卡在哪一步
                        Linux.install(force = ready) { line ->
                            log = (log + line + "\n").takeLast(1500)
                        }
                    }
                    busy = false
                    ready = ok && Linux.installed()
                    if (ready) self = withContext(Dispatchers.IO) { Linux.selfTest().out }
                }
            }
            if (ready && !busy) {
                Spacer(Modifier.width(10.dp))
                GhostButton(stringResource(R.string.ob2_selftest)) {
                    scope.launch { self = withContext(Dispatchers.IO) { Linux.selfTest().out } }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val mLabel = run {
            val mid = Cfg.linuxMirrorNode
            if (mid.isBlank()) stringResource(R.string.tx_6d8aba37)
            else LinuxMirror.FALLBACK_NODES.firstOrNull { it.id == mid }?.name ?: mid
        }
        //镜像源：跟设置页同一个选择器，装环境前就能指定节点
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ob2_mirror_src), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "$mLabel ▸",
                fontSize = 11.sp,
                color = AccentBlue,
                modifier = Modifier.tap { showMirror = true }
            )
        }
        if (showMirror) MirrorPickerDialog { showMirror = false }
        if (log.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(log, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }
        if (self.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(self, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
        }
    }
} //装环境步骤：可选装内置 Ubuntu，不装直接下一步