package b.ai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import b.ai.agent.Agent
import b.ai.core.Cfg
import b.ai.core.Lang
import b.ai.core.Perms
import b.ai.exec.Alarms
import b.ai.net.Update
import b.ai.ui.AskSheet
import b.ai.ui.BackdropHost
import b.ai.ui.ChatScreen
import b.ai.ui.DrawerPanel
import b.ai.ui.IntentInbox
import b.ai.ui.OnboardingScreen
import b.ai.ui.Overlay
import b.ai.ui.PermNoticeDialog
import b.ai.ui.SettingsScreen
import b.ai.ui.MemorySheet
import b.ai.ui.StarSheet
import b.ai.ui.ToolConfirmDialog
import b.ai.ui.UpdateDialog
import b.ai.ui.UpdateHistoryDialog
import b.ai.ui.UpdateUi
import b.ai.ui.theme.BaiTheme
import b.ai.core.Speak
import b.ai.ui.theme.BgGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    //换语言后要生效就得重建 Activity，所以 Context 在 attach 阶段就包成选定语言
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    } //包语言后的 Context
    //进程退出前把 TTS 引擎关掉，否则念到一半退出会漏声音
    override fun onDestroy() {
        Speak.release()
        super.onDestroy()
    } //释放朗读引擎

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readTaskPrompt(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            BaiTheme { Root() }
        }
    } //入口：透明状态栏/导航条 + 浅色主题

    //通知点开而不是冷启动时也会走这里，重新把任务内容送进输入框
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTaskPrompt(intent)
    }

    private fun readTaskPrompt(i: Intent?) {
        val p = i?.getStringExtra(Alarms.EXTRA_PROMPT) ?: return
        if (p.isNotBlank()) {
            IntentInbox.prompt = p
            i.removeExtra(Alarms.EXTRA_PROMPT)
        }
    } //取出定时任务携带的提示词
}

@Composable
private fun Root() {
    var screen by remember { mutableStateOf(if (Cfg.onboarded) "chat" else "setup") }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    //进应用即申请全部运行期权限：拒绝过一次之后只提示、不反复弹系统框
    var missing by remember { mutableStateOf(Perms.missing(ctx)) }
    var permNotice by remember { mutableStateOf(false) }
    //收藏夹面板：必须挂在根弹窗层，抽屉内部挂弹窗会被抽屉盖住且无法点外部关闭
    var starsOpen by remember { mutableStateOf(false) }
    //永久记忆面板：同样走根弹窗层
    var memOpen by remember { mutableStateOf(false) }
    //返回手势进度：0 = 没在滑，1 = 滑过阈值，用来驱动设置页跟手位移
    var backFx by remember { mutableFloatStateOf(0f) }
    //设置页从哪一页进：聊天页的模型面板点「模型配置」直接落到模型 tab
    var settingsTab by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missing = Perms.missing(ctx)
        permNotice = missing.isNotEmpty()
    }
    LaunchedEffect(Unit) {
        Agent.ensureSession()
        if (missing.isNotEmpty()) permLauncher.launch(Perms.runtime().toTypedArray())
        //启动静默检查更新：确实有新版本才弹卡片，网络失败一声不响
        val info = withContext(Dispatchers.IO) { runCatching { Update.check() }.getOrNull() }
        if (info != null && info.updateEnabled && info.hasUpdate) UpdateUi.prompt = info
    }

    val upd = UpdateUi.prompt
    val updHis = UpdateUi.history
    val active = Agent.pending != null || Agent.ask != null || Overlay.active || permNotice || upd != null || updHis != null ||
        starsOpen || memOpen
    val dialogOn = active
    val anyOverlay = active || drawer.isOpen

    //深色模式下状态栏/导航条要换成浅色图标，不然黑图标贴在深底上看不见
    val wantDark = when (Cfg.darkTheme) {
        1 -> false
        2 -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val act = ctx as? ComponentActivity
    LaunchedEffect(wantDark) {
        act?.enableEdgeToEdge(
            statusBarStyle = if (wantDark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (wantDark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
    }

    //返回键：抽屉开着先关抽屉；设置页在开「预测性手势支持」时走 PredictiveBackHandler，跟手缩放后再回聊天
    BackHandler(enabled = drawer.isOpen) {
        if (drawer.isOpen) scope.launch { drawer.close() }
    }
    //收藏夹面板开着时返回键先关面板，别让它落到抽屉逻辑上
    BackHandler(enabled = starsOpen) { starsOpen = false }
    BackHandler(enabled = memOpen) { memOpen = false }
    //关掉预测性手势时退回普通返回，不做跟手动画
    BackHandler(enabled = !Cfg.predictiveBack && screen == "settings") {
        screen = "chat"
    }
    //手势进行中 progress 0→1 驱动设置页位移与缩小，中途松手回弹，滑过阈值才真的退出
    PredictiveBackHandler(enabled = Cfg.predictiveBack && screen == "settings" && !drawer.isOpen) { progress ->
        try {
            progress.collect { e -> backFx = e.progress }
            screen = "chat"
        } catch (e: CancellationException) {
            throw e
        } finally {
            backFx = 0f
        }
    }

    //抽屉放在背景层外面，否则它自己也会被录进模糊层
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = screen == "chat" && !dialogOn,
        scrimColor = ComposeColor.Transparent,
        drawerContent = {
            DrawerPanel(
                onNewChat = {
                    Agent.newSession()
                    screen = "chat"
                    scope.launch { drawer.close() }
                },
                onOpenSettings = {
                    screen = "settings"
                    scope.launch { drawer.close() }
                },
                onOpenStars = {
                    //先关抽屉再开面板：两层同时在场时弹窗一定被抽屉压在下面
                    starsOpen = true
                    scope.launch { drawer.close() }
                },
                onOpenMemory = {
                    memOpen = true
                    scope.launch { drawer.close() }
                },
                onClose = { scope.launch { drawer.close() } }
            )
        }
    ) {
        Box(Modifier.fillMaxSize().background(BgGray)) {
            BackdropHost(
                active = anyOverlay,
                scrim = dialogOn,
                content = {
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            "setup" -> OnboardingScreen { screen = "chat" }
                            "settings" -> SettingsScreen(backProgress = backFx, initialTab = settingsTab) {
                                screen = "chat"
                            }
                            else -> ChatScreen(
                                onOpenDrawer = { scope.launch { drawer.open() } },
                                onOpenSettings = { t ->
                                    settingsTab = t
                                    screen = "settings"
                                }
                            )
                        }
                    }
                },
                overlay = {
                    val p = Agent.pending
                    val q = Agent.ask
                    when {
                        q != null -> AskSheet(q)
                        p != null -> ToolConfirmDialog(p)
                        permNotice -> PermNoticeDialog(
                            missing = missing,
                            onRetry = {
                                permNotice = false
                                permLauncher.launch(Perms.runtime().toTypedArray())
                            },
                            onSettings = {
                                permNotice = false
                                Perms.openAppSettings(ctx)
                            },
                            onClose = { permNotice = false }
                        )
                        upd != null -> UpdateDialog(upd) { UpdateUi.close() }
                        updHis != null -> UpdateHistoryDialog(updHis) { UpdateUi.close() }
                        memOpen -> MemorySheet { memOpen = false }
                        starsOpen -> StarSheet(
                            onClose = { starsOpen = false },
                            onOpen = { sid ->
                                starsOpen = false
                                screen = "chat"
                                Agent.openSession(sid)
                            }
                        )
                        else -> Overlay.content?.invoke()
                    }
                }
            )
        }
    }
} //根容器：抽屉 + 内容层录制 + 卡片区真模糊 + 全局弹窗层