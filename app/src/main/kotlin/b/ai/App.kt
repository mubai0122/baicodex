package b.ai

import android.app.Application
import b.ai.core.Cfg
import b.ai.core.Backup
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
        Paths.ensure()
        //导入的备份在这里换上去：必须排在 Cfg.init / Repo.init 之前
        runCatching { Backup.applyPending(this) }
        Cfg.init(this)
        Profiles.init()
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

    companion object {
        lateinit var self: App
            private set
    }
}