package b.ai.exec

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import b.ai.agent.Agent
import b.ai.core.Schedules

/** 定时任务广播：开机/改时间/升级后重排闹钟；闹钟到点则发通知并（可选）直接跑一轮 */
class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = ctx.applicationContext
        Schedules.init(app)
        when {
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED -> Alarms.rescheduleAll(app)
            action.startsWith(Alarms.ACTION_FIRE) -> fire(app, intent.getStringExtra(Alarms.EXTRA_ID))
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
} //定时任务广播接收器