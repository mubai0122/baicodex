/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:12
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.device.sys.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import b.ai.agent.run.Agent
import b.ai.agent.flow.FlowRunner
import b.ai.agent.flow.Flows
import b.ai.core.task.alarm.Schedules

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