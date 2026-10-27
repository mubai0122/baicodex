package b.ai.exec

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import b.ai.agent.Agent
import b.ai.agent.FlowRunner
import b.ai.core.Flows
import b.ai.core.Schedules

/** 定时任务广播：开机/改时间/升级后重排闹钟；闹钟到点则发通知并（可选）直接跑一轮 */
class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = ctx.applicationContext
        Schedules.init(app)
        Flows.init(app)
        when {
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED -> Alarms.rescheduleAll(app)
            action.startsWith(Alarms.ACTION_FIRE) -> fire(app, intent.getStringExtra(Alarms.EXTRA_ID))
            action.startsWith(Alarms.ACTION_FLOW) -> fireFlow(app, intent.getStringExtra(Alarms.EXTRA_FLOW))
        }
    } //按 action 分流

    private fun fire(ctx: Context, id: String?) {
        val t = Schedules.byId(id ?: return) ?: return
        //先把下一次时间写回去，再干活：避免同一分钟的重复唤醒重复执行
        if (t.daily) {
            val next = Schedules.rollForward(t.at, true, System.currentTimeMillis())
            Schedules.touch(t.id, next)
            Alarms.schedule(ctx, t.copy(at = next))
        } else {
            Schedules.toggle(t.id, false)
        }
        Alarms.notify(ctx, t)
        //自动执行：进程活着且 Agent 空闲才跑；要确认的高危工具会弹在 App 里等用户
        if (t.auto && !Agent.busy) runCatching { Agent.send(t.prompt) }
    } //闹钟到点
    /** 工作流到点：先把下一次时间写回去再干活，然后交给引擎在自己的协程域里跑 */
    private fun fireFlow(ctx: Context, id: String?) {
        val f = Flows.byId(id ?: return) ?: return
        if (f.steps.isEmpty()) return
        if (f.daily) {
            val next = Schedules.rollForward(f.at, true, System.currentTimeMillis())
            Flows.touch(f.id, next)
            Alarms.scheduleFlow(ctx, f.copy(at = next))
        } else {
            Flows.toggle(f.id, false)
        }
        if (!f.auto) {
            Alarms.notifyFlowDone(f, true, "到点了，但这条流没开自动执行；点开 App 手动跑一次。")
            return
        }
        Alarms.notifyFlowText(f, "开始执行，共 " + f.steps.size + " 步")
        FlowRunner.launch(f)
    } //工作流到点
} //定时任务广播接收器