package b.ai

import android.app.ActivityManager
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Process
import b.ai.core.Cfg
import b.ai.core.Js
import b.ai.core.Backup
import b.ai.core.Brands
import b.ai.core.Providers
import b.ai.core.Paths
import b.ai.core.Mcp
import b.ai.core.Memory
import b.ai.core.Personas
import b.ai.core.Profiles
import b.ai.core.Flows
import b.ai.core.Schedules
import b.ai.core.Skills
import b.ai.core.Triggers
import b.ai.core.SkillPacks
import b.ai.core.Repo
import b.ai.core.WorkMode
import b.ai.exec.A2aSrv
import b.ai.exec.Alarms
import b.ai.exec.Root
import b.ai.exec.Router
import b.ai.exec.Workspace
import b.ai.net.Usage

class App : Application() {
    /**
     * 这里比 onCreate 更早：先把 SharedPreferences 的 XML 解析丢到后台线程。
     * bai_cfg.xml 十几 KB、几百个键，解析一次四十毫秒上下，正好能跟 ContentProvider 初始化并行跑完
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        android.util.Log.w("Boot", "attachBaseContext")
        if (procName() != packageName) return
        Thread({
            runCatching { base.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE) }
        }, "sp-warm").apply { isDaemon = true }.start()
    } //进程上下文预热

    override fun onCreate() {
        super.onCreate()
        val t0 = android.os.SystemClock.uptimeMillis()
        android.util.Log.w("Boot", "onCreate start")
        self = this
        //上次进程被杀留下的过期粘贴文件，启动时清一遍
        runCatching { b.ai.core.Pastes.purge() }
        //当前在前台的 Activity：WebView 需要窗口上下文，拿不到就退回 Application
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityResumed(a: android.app.Activity) {
                act = a
            }

            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {
                if (act === a) act = null
            }
        })
        //:exec 进程只跑 Shizuku 命令，不做应用初始化，否则会多挂一个胶囊、白扫一遍库
        if (procName() != packageName) return
        //胶囊的兜底宿主：没有无障碍时靠悬浮窗权限显示
        b.ai.exec.Capsule.attachApp(this)
        //桌宠的兜底宿主：没有无障碍时靠悬浮窗权限显示
        b.ai.exec.Pet.attachApp(this)
        //悬浮聊天窗的宿主：WindowManager 里挂 ComposeView，靠 applicationContext 活着
        b.ai.exec.FChat.attach(this)
        Paths.ensure()
        //导入的备份在这里换上去：必须排在 Cfg.init / Repo.init 之前
        runCatching { Backup.applyPending(this) }
        Cfg.init(this)
        android.util.Log.w("Boot", "Cfg.init done +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //背景图：缩略图同步读进内存 + 真图后台读盘缓存，首帧就有对色的底，不会先白一下
        runCatching { b.ai.core.Wallpaper.warm(this, Cfg.bgImg, Cfg.bgBlur) }
        android.util.Log.w("Boot", "Wallpaper done +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //桌宠默认全局展示：有权限就直接起前台服务，没权限交给界面弹授权（起服务要过系统，挪到后台）
        Profiles.init()
        android.util.Log.w("Boot", "Profiles.init done +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //会话库：只建 helper 不打开库，便宜；聊天首屏就要读会话，不能等后台线程（等就会 NPE）
        runCatching { Repo.init(this) }
        Root.init()
        Router.probeAsync()
        android.util.Log.w("Boot", "Router done +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //下面这些是 IO / 进程 / 扫盘级的重活，没有一个参与首帧，全部丢到一个后台线程串行跑。
        //以前它们都在 Application.onCreate 主线程上，实测把进前台的帧顶到 50th 250ms、Slow UI thread 占一半
        Thread({
            //桌宠的常驻服务：走 startForeground，系统那边一圈调用，首帧用不到
            runCatching {
                if (Cfg.petOn && Cfg.petGlobal && b.ai.exec.Pet.canShow(self)) b.ai.exec.PetSvc.start(self)
            }
            //功能级的配置库（技能、角色、定时任务、工作流、A2A 节点、价格、触发器、MCP）一律首帧之后再读
            runCatching { Skills.init(self) }
            runCatching { Personas.init(self) }
            runCatching { Schedules.init(self) }
            runCatching { Flows.init(self) }
            runCatching { b.ai.core.A2aNodes.init(self) }
            runCatching { b.ai.core.Prices.init(self) }
            runCatching { Triggers.init(self) }
            runCatching { Mcp.init(self) }
            // A2A 服务端：上次开着就恢复监听；端口被占就失败，不崩
            if (Cfg.a2aOn) runCatching { A2aSrv.start(Cfg.a2aPort) }
            //厂商图标：C 层抓，磁盘缓存 168 天，之后设置页打开直接出图
            runCatching {
                val want = LinkedHashMap<String, String>()
                Providers.builtin.forEach { if (it.baseUrl.isNotBlank()) want[it.id] = it.baseUrl }
                Profiles.list.forEach { if (it.baseUrl.isNotBlank()) want[it.providerId] = it.baseUrl }
                want.forEach { (id, url) -> runCatching { Brands.ensure(self, id, url) } }
            }
            //插件仓库与记忆库：都要读盘/开库，聊天首屏用不到
            runCatching { Repo.adoptUsage() }
            runCatching { Memory.init() }
            //技能包清单：扫外部目录 + lint 所有模板
            runCatching { SkillPacks.reload(this) }
            //JS 技能：把脚本目录里的 *.js 跑一遍，脚本里 registerTool 的工具就此下发
            runCatching { Js.loadScripts() }
            //绑过工作区但还没有索引时后台补一份
            runCatching { Workspace.warmIndex() }
            //root 模式下预热 su shell：把授权框挪到启动时，别让第一次文件读写干等
            runCatching { if (Cfg.workMode == WorkMode.ROOT) Root.warmUp() }
            //每次进程起来重排一遍闹钟：升级/被系统清掉/改过时间都能自动恢复
            runCatching { Alarms.rescheduleAll(this) }
            //每次进程起来上报一次使用计数：失败静默
            runCatching { Usage.reportOnce() }
        }, "boot-init").apply { isDaemon = true }.start()
        //掉帧探针：只记 ≥80ms 的长帧并带上玻璃档位，页面静止时不触发
        b.ai.ui.PerfProbe.install()
        android.util.Log.w("Boot", "onCreate end +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //首屏过去 1.2 秒后把玻璃标成「已经上过屏」：之后新建的大卡片不再错峰排队，切分类时直接进真玻璃
        Thread({ runCatching { Thread.sleep(1200); b.ai.ui.GlassPerf.ready = true } }, "glass-ready").apply { isDaemon = true }.start()
    } //进程入口：初始化配置、数据库、定时任务与 root 通道，并后台探测零宽路径

    private fun procName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= 28) return@runCatching Application.getProcessName()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName ?: packageName
    }.getOrDefault(packageName) //当前进程名

    companion object {
        lateinit var self: App
            private set

        /** 前台 Activity：浏览器要窗口上下文，没有就退回 Application */
        var act: android.app.Activity? = null
    }
}