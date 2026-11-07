package b.ai
import b.ai.core.data.io.Layout
import b.ai.device.shell.root.Root
import b.ai.core.cfg.secret.Secret
import b.ai.core.data.io.Pastes
import b.ai.core.data.profile.Prices
import b.ai.core.media.wall.Wallpaper
import b.ai.device.sys.host.Capsule
import b.ai.device.app.FChat
import b.ai.device.pet.Pet
import b.ai.device.pet.PetSvc
import b.ai.native.security.Guard
import b.ai.native.security.Mask
import b.ai.native.security.Vault
import b.ai.net.doc.MaskRules
import b.ai.net.api.a2a.A2aNodes
import b.ai.ui.glass.GlassPerf
import b.ai.ui.overlay.PerfProbe

import android.app.ActivityManager
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Process
import b.ai.core.cfg.app.Cfg
import b.ai.native.script.Js
import b.ai.core.data.io.Backup
import b.ai.core.model.provider.Brands
import b.ai.core.plugin.skill.Providers
import b.ai.core.cfg.io.Paths
import b.ai.core.cfg.io.Prefs
import b.ai.net.mcp.Mcp
import b.ai.agent.memory.Memory
import b.ai.agent.persona.Personas
import b.ai.core.data.profile.Profiles
import b.ai.agent.flow.Flows
import b.ai.core.task.alarm.Schedules
import b.ai.core.plugin.skill.Skills
import b.ai.core.task.hook.Triggers
import b.ai.core.plugin.skill.SkillPacks
import b.ai.core.data.db.Repo
import b.ai.core.model.chat.WorkMode
import b.ai.net.api.a2a.A2aSrv
import b.ai.device.sys.alarm.Alarms
import b.ai.device.sys.probe.Router
import b.ai.device.fs.Workspace
import b.ai.net.api.site.Usage

class App : Application() {
    /**
     * 这里比 onCreate 更早：先把配置库从盘里读进内存。
     * data/prefs/bai_cfg/ 下 8 个分片，第一次还要从老的 SharedPreferences 导一遍，正好跟 ContentProvider 初始化并行跑完
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        android.util.Log.w("Boot", "attachBaseContext")
        if (procName() != packageName) return
        //有待恢复的备份就不预热：配置一会儿整份换掉，热了白热，还会挡住老 XML 备份的导入
        if (runCatching { Backup.pending(base) }.getOrDefault(false)) return
        Thread({
            runCatching { Prefs.get(base) }
        }, "cfg-warm").apply { isDaemon = true }.start()
    } //进程上下文预热

    override fun onCreate() {
        super.onCreate()
        val t0 = android.os.SystemClock.uptimeMillis()
        android.util.Log.w("Boot", "onCreate start")
        self = this
        //上次进程被杀留下的过期粘贴文件，启动时清一遍
        runCatching { b.ai.core.data.io.Pastes.purge() }
        //当前在前台的 Activity：WebView 需要窗口上下文，拿不到就退回 Application
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityResumed(a: android.app.Activity) {
                act = a
                //外部存储晚挂上（开机自启 / 后台起进程）时模型配置的读盘被延后了：界面回来补一次
                runCatching { b.ai.core.data.profile.Profiles.retry() }
            }

            override fun onActivityPaused(a: android.app.Activity) {
                //切后台前把配置库的内存表刷一遍：写盘是 250ms 合并的，不刷会丢最后半秒
                runCatching { Prefs.flushAll() }
            }
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {
                if (act === a) act = null
            }
        })
        //:exec 进程只跑 Shizuku 命令，不做应用初始化，否则会多挂一个胶囊、白扫一遍库
        if (procName() != packageName) return
        //胶囊的兜底宿主：没有无障碍时靠悬浮窗权限显示
        b.ai.device.sys.host.Capsule.attachApp(this)
        //桌宠的兜底宿主：没有无障碍时靠悬浮窗权限显示
        b.ai.device.pet.Pet.attachApp(this)
        //悬浮聊天窗的宿主：WindowManager 里挂 ComposeView，靠 applicationContext 活着
        b.ai.device.app.FChat.attach(this)
        Paths.ensure()
        //私有目录换布局：data/ img/ cache/ 三块，老目录在这里搬过去。
        //必须排在 Backup.applyPending / Guard.boot / Cfg.init 之前：那时才会读 restore 与密文
        runCatching { b.ai.core.data.io.Layout.migrate(this) }
        //导入的备份在这里换上去：必须排在 Cfg.init / Repo.init 之前
        runCatching { Backup.applyPending(this) }
        //数据保障必须在 Cfg.init 之前解锁：否则加密库读不出 v2 密文，会把整个桶当「解不开」锁掉
        val guardOk = runCatching { b.ai.native.security.Guard.boot() }.getOrDefault(false)
        android.util.Log.w("Boot", "Guard.boot=" + guardOk + " on=" + b.ai.native.security.Guard.on)
        Cfg.init(this)
        android.util.Log.w("Boot", "Cfg.init done +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //背景图：缩略图同步读进内存 + 真图后台读盘缓存，首帧就有对色的底，不会先白一下
        runCatching { b.ai.core.media.wall.Wallpaper.warm(this, Cfg.bgImg, Cfg.bgBlur) }
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
                if (Cfg.petOn && Cfg.petGlobal && b.ai.device.pet.Pet.canShow(self)) b.ai.device.pet.PetSvc.start(self)
            }
            //功能级的配置库（技能、角色、定时任务、工作流、A2A 节点、价格、触发器、MCP）一律首帧之后再读
            //老版本明文 secrets.json 先搬进加密库，技能执行时才能读到 $secret.xxx
            runCatching { b.ai.core.cfg.secret.Secret.migrate() }
            //数据保障开着就把还是旧版的密文重写一遍：中途被杀的迁移在这里收尾
            runCatching { if (b.ai.native.security.Guard.ready) b.ai.native.security.Vault.rewriteAll() }
            runCatching { Skills.init(self) }
            runCatching { Personas.init(self) }
            runCatching { Schedules.init(self) }
            runCatching { Flows.init(self) }
            runCatching { b.ai.net.api.a2a.A2aNodes.init(self) }
            runCatching { b.ai.core.data.profile.Prices.init(self) }
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
            //脱敏：先把上次缓存的服务端规则装回 native，再按有效期后台换一份新的
            runCatching { b.ai.native.security.Mask.boot() }
            runCatching { if (Cfg.maskOut) b.ai.net.doc.MaskRules.sync() }
        }, "boot-init").apply { isDaemon = true }.start()
        //掉帧探针：只记 ≥80ms 的长帧并带上玻璃档位，页面静止时不触发
        b.ai.ui.overlay.PerfProbe.install()
        android.util.Log.w("Boot", "onCreate end +" + (android.os.SystemClock.uptimeMillis() - t0) + "ms")
        //首屏过去 1.2 秒后把玻璃标成「已经上过屏」：之后新建的大卡片不再错峰排队，切分类时直接进真玻璃
        Thread({ runCatching { Thread.sleep(1200); b.ai.ui.glass.GlassPerf.ready = true } }, "glass-ready").apply { isDaemon = true }.start()
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