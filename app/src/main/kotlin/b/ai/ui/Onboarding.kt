package b.ai.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import b.ai.core.Cfg
import b.ai.core.WorkMode
import b.ai.core.Workflows
import b.ai.exec.Linux
import b.ai.exec.Root
import b.ai.exec.Router
import b.ai.exec.Shiz
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 引导流程：工作方式 → 权限检查（含零宽弹窗）→ 工作流 → 装环境（可跳过）→ API key（可跳过） */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    var zwSupport by remember { mutableStateOf<Boolean?>(null) }
    var modeMsg by remember { mutableStateOf<String?>(null) }
    var modeOk by remember { mutableStateOf(false) }
    var showZwPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(tick) {
        zwSupport = withContext(Dispatchers.IO) { Router.probe() }
    }

    LaunchedEffect(step, tick) {
        if (step != 1) return@LaunchedEffect
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
                modeMsg = "使用 App 自身沙箱权限；/sdcard 需存储权限"
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
                    probeName = Router.probeName(),
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
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(28.dp))
        Text("BaiAgent", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("设备上的本地 AI Agent", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        Text(
            when (step) {
                0 -> "1/5 选择工作方式"
                1 -> "2/5 检查权限"
                2 -> "3/5 选择工作流"
                3 -> "4/5 装运行环境"
                else -> "5/5 配置模型"
            },
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))

        when (step) {
            0 -> StepMode()
            1 -> StepPermission(
                zwSupport = zwSupport,
                modeMsg = modeMsg,
                modeOk = modeOk,
                onRecheck = {
                    Router.reset()
                    tick++
                },
                onRequestShizuku = {
                    Shiz.requestPermission { granted ->
                        modeOk = granted
                        modeMsg = if (granted) "已授权（Shizuku）" else "授权被拒绝，可再点一次申请"
                    }
                }
            )
            2 -> StepWorkflow()
            3 -> StepEnv()
            else -> CardBox { ModelSetupPanel() }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (step > 0) {
                GhostButton("上一步") { step-- }
                Spacer(Modifier.width(10.dp))
            }
            if (step == 4) {
                GhostButton("跳过") {
                    Cfg.onboarded = true
                    onDone()
                }
                Spacer(Modifier.width(10.dp))
            }
            PrimaryButton(
                text = if (step < 4) "下一步" else "开始使用",
                enabled = step < 4 || Cfg.ready
            ) {
                if (step < 4) step++ else {
                    Cfg.onboarded = true
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
        Text("设备支持零宽路径", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "实测发现用零宽变换后的路径可以读到 Android/data 与 Android/obb。是否开启该拓展？\n\n" +
                "命中方式：$probeName\n开启后普通用户模式也能读写其他 App 的数据目录；" +
                "这属于系统漏洞利用，ROM 更新后可能失效。",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton("暂不开启") { onClose() }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("开启零宽") { onEnable() }
        }
    }
} //零宽拓展弹窗

@Composable
private fun StepMode() {
    CardBox {
        WorkMode.entries.forEach { m ->
            ChoiceRow(m.label, m.desc, Cfg.workMode == m) { Cfg.workMode = m }
        }
    }
}

@Composable
private fun StepPermission(
    zwSupport: Boolean?,
    modeMsg: String?,
    modeOk: Boolean,
    onRecheck: () -> Unit,
    onRequestShizuku: () -> Unit
) {
    val ctx = LocalContext.current
    CardBox {
        Text("当前工作方式：${Cfg.workMode.label}", fontSize = 14.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            modeMsg ?: "检测中…",
            fontSize = 12.sp,
            color = if (modeMsg == null) TextSecondary else if (modeOk) OkGreen else DangerRed
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "零宽路径检测：" + when (zwSupport) {
                null -> "检测中…"
                true -> "支持（${Router.probeName()}）"
                false -> "不支持"
            },
            fontSize = 12.sp,
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Row {
            GhostButton("重新检测") { onRecheck() }
            if (Cfg.workMode == WorkMode.SHIZUKU) {
                Spacer(Modifier.width(10.dp))
                GhostButton("申请 Shizuku 授权") { onRequestShizuku() }
            }
            if (Cfg.workMode == WorkMode.NORMAL && Build.VERSION.SDK_INT <= 32) {
                Spacer(Modifier.width(10.dp))
                GhostButton("申请存储权限") {
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
    }
}

@Composable
private fun StepWorkflow() {
    CardBox {
        Workflows.all.forEach { (id, label) ->
            ChoiceRow(label, "只影响 system prompt 的侧重，不改工具集", Cfg.workflow == id) {
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
    val scope = rememberCoroutineScope()
    val canRun = Linux.canRun()
    CardBox {
        Text("要装运行环境吗？", fontSize = 14.sp, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "内置 Ubuntu 24.04：apt 装依赖、编译、跑脚本都在里面，不依赖 Termux。" +
                "约 105MB，解包 1-3 分钟。不需要就点「下一步」跳过，之后随时能在 设置 → 扩展 → Linux 环境 里装。",
            fontSize = 12.sp,
            color = TextSecondary
        )
        if (!canRun) {
            Spacer(Modifier.height(8.dp))
            Text(
                "当前是 ${Cfg.workMode.label} 模式，内置 Ubuntu 只有 Root 模式能跑；要装先回上一步切成 Root。",
                fontSize = 12.sp,
                color = DangerRed
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (ready) "环境状态：已就绪" else "环境状态：未安装",
            fontSize = 12.sp,
            color = if (ready) OkGreen else TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton(
                text = if (busy) "安装中…" else if (ready) "重新安装" else "安装内置 Ubuntu",
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
                GhostButton("自检") {
                    scope.launch { self = withContext(Dispatchers.IO) { Linux.selfTest().out } }
                }
            }
        }
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