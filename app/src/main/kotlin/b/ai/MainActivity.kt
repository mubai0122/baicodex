package b.ai

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.animation.core.Animatable
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
import b.ai.net.Consent
import b.ai.net.Isuse
import b.ai.net.Update
import b.ai.ui.AnnounceDialog
import b.ai.ui.AskSheet
import b.ai.ui.A11yAuto
import b.ai.ui.A11yAutoDialog
import b.ai.ui.A11ySetup
import b.ai.ui.A11ySetupDialog
import b.ai.ui.BackdropHost
import b.ai.ui.ChatScreen
import b.ai.ui.DrawerCtl
import b.ai.ui.DrawerHost
import b.ai.ui.DrawerPanel
import b.ai.ui.FeedbackDetailScreen
import b.ai.ui.FeedbackScreen
import b.ai.ui.HaltDialog
import b.ai.ui.IntentInbox
import b.ai.ui.JoinGroupDialog
import b.ai.ui.OnboardingScreen
import b.ai.ui.Overlay
import b.ai.ui.PermNoticeDialog
import b.ai.ui.PetPermAsk
import b.ai.ui.PetScreen
import b.ai.ui.SettingsScreen
import b.ai.ui.MemorySheet
import b.ai.ui.MigrateGate
import b.ai.ui.PlanSheet
import b.ai.ui.StarSheet
import b.ai.ui.Rate
import b.ai.ui.StarDialog
import b.ai.ui.ToolConfirmDialog
import b.ai.ui.Tos
import b.ai.ui.TosScreen
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
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    //本次前台开始的时刻（elapsedRealtime），onPause 时结算成累计使用时长
    private var fgSince = 0L
    //换语言后要生效就得重建 Activity，所以 Context 在 attach 阶段就包成选定语言
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    } //包语言后的 Context
    //离开前台就把这段时长累加进 used_sec：只用来判定「用满 2 小时要不要邀请评价」
    override fun onPause() {
        if (fgSince > 0) {
            val add = ((SystemClock.elapsedRealtime() - fgSince) / 1000L).toInt()
            if (add > 0) Cfg.usedSec = Cfg.usedSec + add
            fgSince = 0L
        }
        super.onPause()
    } //累计前台时长

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

    //每次回到前台补一次无障碍：系统常把它掐掉，Shizuku 在的话直接写回 secure 设置
    override fun onResume() {
        super.onResume()
        //回到前台重新开始计时，并顺手判一次要不要弹评价调研
        fgSince = SystemClock.elapsedRealtime()
        Rate.check()
        //悬浮聊天窗：开关开着就补挂一次，进程重启后窗口是会丢的
        if (Cfg.fcOn) runCatching { b.ai.exec.FChat.show(this) }
        if (!b.ai.exec.AutoA11y.need(this)) return
        Thread({ runCatching { b.ai.exec.AutoA11y.run(this) } }, "auto-a11y").apply { isDaemon = true }.start()
    } //自动补无障碍

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
    //启动门：检测到旧版工作区就必须先迁移，没迁完不进主界面（详情见 MigrateGate）
    var gateOpen by remember { mutableStateOf(true) }
    if (gateOpen) {
        MigrateGate { gateOpen = false }
        return
    }
    //协议版本在服务端：启动先按当前界面语言拉一次，拉不到就用内置 assets 正文 + 本地版本兜底
    var tosReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Tos.loadRemote() }
        tosReady = true
    }
    //拉取期间只铺底色，避免先闪一帧主界面再被协议页盖住
    if (!tosReady) {
        Box(Modifier.fillMaxSize().background(BgGray))
        return
    }
    //本地已同意的版本必须不小于服务端版本；服务端降版本不打扰老用户，同意状态上报一次
    var needTos by remember { mutableStateOf(Cfg.tosAgreed < Tos.version()) }
    if (needTos) {
        TosScreen(changed = Cfg.tosAgreed > 0) {
            Cfg.tosAgreed = Tos.version()
            Consent.reportIfNeeded()
            needTos = false
        }
        return
    }
    var screen by remember { mutableStateOf(if (Cfg.onboarded) "chat" else "setup") }
    //抽屉动效自己管：进度 0..1，动画中途换目标就从当前位置接着走（打断）
    val scope = rememberCoroutineScope()
    val drawerCtl = remember(scope) { DrawerCtl(Animatable(0f), scope) }
    val ctx = LocalContext.current
    //进应用即申请全部运行期权限：拒绝过一次之后只提示、不反复弹系统框
    var missing by remember { mutableStateOf(Perms.missing(ctx)) }
    var permNotice by remember { mutableStateOf(false) }
    //桌宠默认全局展示：没悬浮窗权限就先弹一次授权
    var petPerm by remember { mutableStateOf(false) }
    //收藏夹面板：必须挂在根弹窗层，抽屉内部挂弹窗会被抽屉盖住且无法点外部关闭
    var starsOpen by remember { mutableStateOf(false) }
    //永久记忆面板：同样走根弹窗层
    var memOpen by remember { mutableStateOf(false) }
    //返回手势进度：0 = 没在滑，1 = 滑过阈值，用来驱动设置页跟手位移
    var backFx by remember { mutableFloatStateOf(0f) }
    //设置页从哪一页进：聊天页的模型面板点「模型配置」直接落到模型 tab
    var settingsTab by remember { mutableStateOf(0) }
    //反馈详情页看哪一条：从反馈页点「我的反馈」进来时带上编号
    var fbId by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missing = Perms.missing(ctx)
        permNotice = missing.isNotEmpty()
    }
    //停机开关：服务端说停止运营就只留退出提示，别的弹窗一律不参与
    var halted by remember { mutableStateOf(false) }
    //「加入我们」卡片：更新卡片关掉之后再弹，同一版本只弹一次
    var joinOpen by remember { mutableStateOf(false) }
    //启动检查是否已经走完：没走完不弹群卡片，免得抢在更新卡片前面
    var updChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Agent.ensureSession()
        if (missing.isNotEmpty()) permLauncher.launch(Perms.runtime().toTypedArray())
        //停机开关先读：关掉之后检查更新没有意义，直接进退出分支
        if (!withContext(Dispatchers.IO) { Isuse.on() }) {
            halted = true
            return@LaunchedEffect
        }
        //启动静默检查更新：确实有新版本才弹卡片，网络失败一声不响
        val info = withContext(Dispatchers.IO) { runCatching { Update.check() }.getOrNull() }
        if (info != null && info.updateEnabled && info.hasUpdate) UpdateUi.prompt = info
        updChecked = true
        //公告：always=true 的每次都弹，否则同一 id 只弹一次；拉不到就当没有
        val annItem = withContext(Dispatchers.IO) { runCatching { b.ai.net.Announce.check() }.getOrNull() }
        if (annItem != null && (annItem.always || Cfg.annSeen != annItem.id)) b.ai.ui.AnnounceUi.cur = annItem
    }

    val upd = UpdateUi.prompt
    val updHis = UpdateUi.history
    val ann = b.ai.ui.AnnounceUi.cur
    //群卡片要等更新卡片关掉：先看完版本更新再邀请进群，避免两张卡片抢同一个槽
    LaunchedEffect(updChecked, upd, updHis) {
        if (!updChecked || halted || upd != null || updHis != null) return@LaunchedEffect
        if (!Cfg.onboarded || Cfg.joinDismissed || Cfg.joinSeen >= Update.clientCode()) return@LaunchedEffect
        Cfg.joinSeen = Update.clientCode()
        joinOpen = true
    }
    //独立的无障碍激活流程：引导走完（进主界面）就弹一次，系统里已经开着也照样走；跑完记一笔
    LaunchedEffect(Cfg.onboarded) {
        if (Cfg.onboarded && !Cfg.a11yDone) A11ySetup.open.value = true
    }
    val active = halted || joinOpen || Rate.ask || Agent.pending != null || Agent.ask != null || Agent.plan != null || Overlay.active || permNotice || upd != null || updHis != null || ann != null ||
        starsOpen || memOpen || A11ySetup.open.value || A11yAuto.open.value
    val dialogOn = active
    //抽屉一开始动（targetValue 变 Open）就起模糊，不等抽屉动画跑完，视觉上才是跟手
    val drawerOpening = drawerCtl.active
    val anyOverlay = active || drawerOpening

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

    //只在启动时判一次：无障碍在跑或者已有悬浮窗权限就不会弹
    LaunchedEffect(Unit) {
        if (Cfg.petOn && Cfg.petGlobal && Cfg.onboarded && !b.ai.exec.Pet.canShow(ctx)) petPerm = true
    }
    //返回键：抽屉开着先关抽屉；设置页在开「预测性手势支持」时走 PredictiveBackHandler，跟手缩放后再回聊天
    BackHandler(enabled = drawerCtl.active) {
        drawerCtl.close()
    }
    //收藏夹面板开着时返回键先关面板，别让它落到抽屉逻辑上
    BackHandler(enabled = starsOpen) { starsOpen = false }
    BackHandler(enabled = memOpen) { memOpen = false }
    //关掉预测性手势时退回普通返回，不做跟手动画
    BackHandler(enabled = !Cfg.predictiveBack && (screen == "settings" || screen == "pet")) {
        screen = if (screen == "pet") "settings" else "chat"
    }
    //手势进行中 progress 0→1 驱动设置页位移与缩小，中途松手回弹，滑过阈值才真的退出
    PredictiveBackHandler(enabled = Cfg.predictiveBack && (screen == "settings" || screen == "pet") && !drawerCtl.active) { progress ->
        try {
            progress.collect { e -> backFx = e.progress }
            screen = if (screen == "pet") "settings" else "chat"
        } catch (e: CancellationException) {
            throw e
        } finally {
            backFx = 0f
        }
    }

    //抽屉放在背景层外面，否则它自己也会被录进模糊层
    DrawerHost(
        ctl = drawerCtl,
        gestures = screen == "chat" && !dialogOn,
        panel = {
            DrawerPanel(
                onNewChat = {
                    Agent.newSession()
                    screen = "chat"
                    drawerCtl.close()
                },
                onOpenSettings = {
                    screen = "settings"
                    drawerCtl.close()
                },
                onClose = { drawerCtl.close() }
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
                            "settings" -> SettingsScreen(
                                backProgress = backFx,
                                initialTab = settingsTab,
                                onBack = { screen = "chat" },
                                onOpenFeedback = { screen = "feedback" },
                                onOpenPet = { screen = "pet" },
                                //收藏夹与记忆面板都挂在根弹窗层，设置页只负责发信号
                                onOpenStars = { starsOpen = true },
                                onOpenMemory = { memOpen = true }
                            )
                            "pet" -> PetScreen(backProgress = backFx, onBack = { screen = "settings" })
                            "feedback" -> FeedbackScreen(
                                onBack = { screen = "settings" },
                                onOpenDetail = { id -> fbId = id; screen = "fb_detail" }
                            )
                            "fb_detail" -> FeedbackDetailScreen(fbId) { screen = "feedback" }
                            else -> ChatScreen(
                                onOpenDrawer = { drawerCtl.open() },
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
                    val pl = Agent.plan
                    when {
                        //停机是最高优先级：别的弹窗一律让位，只剩退出
                        halted -> HaltDialog {
                            (ctx as? Activity)?.finishAffinity()
                            exitProcess(0)
                        }
                        q != null -> AskSheet(q)
                        pl != null -> PlanSheet(pl)
                        p != null -> ToolConfirmDialog(p)
                        petPerm -> PetPermAsk(
                            onClose = { petPerm = false },
                            onGrant = {
                                petPerm = false
                                b.ai.exec.PetGrant.grant(ctx) { r ->
                                    if (r.ok && Cfg.petGlobal) b.ai.exec.PetSvc.start(ctx)
                                }
                            },
                            onOpen = {
                                petPerm = false
                                b.ai.exec.PetGrant.openOverlaySetting(ctx)
                            }
                        )
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
                        ann != null -> AnnounceDialog(ann) {
                            //always=true 的公告不记已读，下次进应用还弹
                            if (!ann.always) Cfg.annSeen = ann.id
                            b.ai.ui.AnnounceUi.close()
                        }
                        memOpen -> MemorySheet { memOpen = false }
                        //独立激活流程优先于聊天页的失败弹窗，避免两个弹窗抢同一个 Overlay 槽
                        //一键激活优先：它本身就是从无障碍卡片点出来的，别被激活流程弹窗压住
                        A11yAuto.open.value -> A11yAutoDialog { A11yAuto.open.value = false }
                        A11ySetup.open.value -> A11ySetupDialog {
                            A11ySetup.open.value = false
                            Cfg.a11yDone = true
                        }
                        starsOpen -> StarSheet(
                            onClose = { starsOpen = false },
                            onOpen = { sid ->
                                starsOpen = false
                                screen = "chat"
                                Agent.openSession(sid)
                            }
                        )
                        //评价调研：用满 2 小时才出现，弹过或用户关掉就再也不来
                        Rate.ask -> StarDialog(manual = Rate.manual) { never ->
                            if (never) Cfg.noRate = true
                            //手动点开的这次不算「已经弹过」，不改 starShown，将来自动邀请照旧
                            if (!Rate.manual) Cfg.starShown = true
                            Rate.ask = false
                            Rate.manual = false
                        }
                        //群卡片排在最后：其它弹窗都在它之上，勾了永不提醒就彻底不弹
                        joinOpen -> JoinGroupDialog { never ->
                            if (never) Cfg.joinDismissed = true
                            joinOpen = false
                        }
                        else -> Overlay.content?.invoke()
                    }
                }
            )
        }
    }
} //根容器：抽屉 + 内容层录制 + 卡片区真模糊 + 全局弹窗层