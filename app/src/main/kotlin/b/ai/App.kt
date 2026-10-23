package b.ai

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import b.ai.core.Cfg
import b.ai.core.Backup
import b.ai.core.Brands
import b.ai.core.Providers
import b.ai.core.Paths
import b.ai.core.Mcp
import b.ai.core.Memory
import b.ai.core.Personas
import b.ai.core.Profiles
import b.ai.core.Schedules
import b.ai.core.Skills
import b.ai.core.SkillPacks
import b.ai.core.Repo
import b.ai.core.WorkMode
import b.ai.exec.Alarms
import b.ai.exec.Root
import b.ai.exec.Router
import b.ai.exec.Workspace
import b.ai.net.Usage

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        self = this
        //:exec 进程只跑 Shizuku 命令，不做应用初始化，否则会多挂一个胶囊、白扫一遍库
        if (procName() != packageName) return
        //胶囊的兜底宿主：没有无障碍时靠悬浮窗权限显示
        b.ai.exec.Capsule.attachApp(this)
        Paths.ensure()
        //导入的备份在这里换上去：必须排在 Cfg.init / Repo.init 之前
        runCatching { Backup.applyPending(this) }
        Cfg.init(this)
        Profiles.init()
        //进应用就把厂商图标捞一遍：C 层抓，磁盘缓存 168 天，之后设置页打开直接出图
        Thread({
            runCatching {
                val want = LinkedHashMap<String, String>()
                Providers.builtin.forEach { if (it.baseUrl.isNotBlank()) want[it.id] = it.baseUrl }
                Profiles.list.forEach { if (it.baseUrl.isNotBlank()) want[it.providerId] = it.baseUrl }
                want.forEach { (id, url) -> runCatching { Brands.ensure(this, id, url) } }
            }
        }, "brand-prefetch").apply { isDaemon = true }.start()
        Skills.init(this)
        //技能包清单：外部目录 + 旧 skills.json，启动时扫一遍并 lint 所有模板
        runCatching { SkillPacks.reload(this) }
        Personas.init(this)
        Schedules.init(this)
        Mcp.init(this)
        Repo.init(this)
        Memory.init()
        Root.init()
        Router.probeAsync()
        //每次进程起来重排一遍闹钟：升级/被系统清掉/改过时间都能自动恢复
        runCatching { Alarms.rescheduleAll(this) }
        //root 模式下预热 su shell：把授权框挪到启动时，别让第一次文件读写干等
        if (Cfg.workMode == WorkMode.ROOT) Root.warmUp()
        //绑过工作区但还没有索引（首次 / 被删）时后台补一份，别在发消息时同步扫盘
        Workspace.warmIndex()
        //每次进程起来上报一次使用计数：后台线程发，失败静默，不拖慢启动
        Usage.reportOnce()
    } //进程入口：初始化配置、数据库、定时任务与 root 通道，并后台探测零宽路径

    private fun procName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= 28) return@runCatching Application.getProcessName()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName ?: packageName
    }.getOrDefault(packageName) //当前进程名

    companion object {
        lateinit var self: App
            private set
    }
}