package b.ai.exec

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import b.ai.MainActivity
import b.ai.R
import b.ai.core.Flow
import b.ai.core.Flows
import b.ai.core.Schedules
import b.ai.core.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 定时任务调度：精确闹钟优先，没有权限就退化成不精确闹钟；到点发通知 + 可选自动执行 */
object Alarms {
    const val CH = "bai_task"
    const val EXTRA_ID = "task_id"
    const val EXTRA_PROMPT = "task_prompt"
    const val ACTION_FIRE = "b.ai.action.TASK_FIRE"
    const val EXTRA_FLOW = "flow_id"
    const val ACTION_FLOW = "b.ai.action.FLOW_FIRE"
    /** 工作流进度通知单开一个低优先级渠道：跑的过程中改字不响铃 */
    private const val FLOW_CH = "bai_flow"

    private fun am(c: Context): AlarmManager = c.getSystemService(AlarmManager::class.java)

    /** Android 12+ 精确闹钟要单独授权；低版本恒为 true */
    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || runCatching { am(c).canScheduleExactAlarms() }.getOrDefault(true)

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CH, "定时任务", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "到点提醒并执行定时任务"
                enableVibration(true)
            }
        )
    } //通知渠道：建一次就够

    private fun fireIntent(c: Context, t: Task): PendingIntent {
        val i = Intent(c, TaskReceiver::class.java)
            .setAction("$ACTION_FIRE.${t.id}")
            .putExtra(EXTRA_ID, t.id)
        return PendingIntent.getBroadcast(
            c,
            t.id.hashCode(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } //到点唤醒用的广播

    fun schedule(c: Context, t: Task) {
        if (!t.on || t.at <= 0L) return
        val a = am(c)
        val pi = fireIntent(c, t)
        val exact = canExact(c)
        runCatching {
            if (exact) {
                a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.at, pi)
            } else {
                a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.at, pi)
            }
        }
    } //登记一个闹钟

    fun cancel(c: Context, id: String) {
        val t = Schedules.byId(id) ?: return
        runCatching { am(c).cancel(fireIntent(c, t)) }
    } //撤销闹钟

    private fun flowChannel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(FLOW_CH) != null) return
        nm.createNotificationChannel(
            NotificationChannel(FLOW_CH, "工作流进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "工作流跑到第几步的进度提示"
            }
        )
    } //工作流进度渠道
    private fun flowIntent(c: Context, f: Flow): PendingIntent {
        val i = Intent(c, TaskReceiver::class.java)
            .setAction("$ACTION_FLOW.${f.id}")
            .putExtra(EXTRA_FLOW, f.id)
        return PendingIntent.getBroadcast(
            c,
            f.id.hashCode(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } //工作流到点广播
    fun scheduleFlow(c: Context, f: Flow) {
        if (!f.on || f.at <= 0L || f.steps.isEmpty()) return
        val a = am(c)
        val pi = flowIntent(c, f)
        runCatching {
            if (canExact(c)) {
                a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.at, pi)
            } else {
                a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.at, pi)
            }
        }
    } //登记工作流闹钟
    fun cancelFlow(c: Context, id: String) {
        val f = Flows.byId(id) ?: return
        runCatching { am(c).cancel(flowIntent(c, f)) }
    } //撤销工作流闹钟
    /** 跑到一半的进度：同一条通知原地改字，不响铃也不进历史 */
    fun notifyFlowText(f: Flow, text: String) {
        val c = b.ai.App.self
        flowChannel(c)
        val n = NotificationCompat.Builder(c, FLOW_CH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("工作流「" + f.name.take(16) + "」")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(f.id.hashCode(), n) }
    } //跑中的进度
    /** 跑完：成功失败都弹一条，点开回 App 看完整报告 */
    fun notifyFlowDone(f: Flow, ok: Boolean, text: String) {
        val c = b.ai.App.self
        ensureChannel(c)
        val open = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            c,
            f.id.hashCode() + 3,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle((if (ok) "工作流完成：" else "工作流失败：") + f.name.take(16))
            .setContentText(text.replace("\n", " ").take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1500)))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(f.id.hashCode(), n) }
    } //结果通知
    /** 开机、时区变化、升级后重排：过期的单次任务直接关掉，每天的顺延到下一个点 */
    fun rescheduleAll(c: Context) {
        ensureChannel(c)
        Flows.init(c)
        val now = System.currentTimeMillis()
        Schedules.list.forEach { t ->
            if (!t.on) return@forEach
            if (t.at <= now) {
                if (t.daily) {
                    val next = Schedules.rollForward(t.at, true, now)
                    Schedules.touch(t.id, next)
                    schedule(c, t.copy(at = next))
                } else {
                    Schedules.toggle(t.id, false)
                }
            } else {
                schedule(c, t)
            }
        }
        //工作流走同一套重排：过期单次直接关掉，每天的顺延
        Flows.list.forEach { f ->
            if (!f.on || f.steps.isEmpty()) return@forEach
            if (f.at <= now) {
                if (f.daily) {
                    val next = Schedules.rollForward(f.at, true, now)
                    Flows.touch(f.id, next)
                    scheduleFlow(c, f.copy(at = next))
                } else {
                    Flows.toggle(f.id, false)
                }
            } else {
                scheduleFlow(c, f)
            }
        }
    } //全量重排

    fun notify(c: Context, t: Task) {
        ensureChannel(c)
        val open = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_PROMPT, t.prompt)
        val pi = PendingIntent.getActivity(
            c,
            t.id.hashCode() + 7,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("定时任务到点")
            .setContentText(t.prompt.replace("\n", " ").take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(t.prompt))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(t.id.hashCode(), n) }
    } //到点通知：点开直接把内容填进输入框

    fun openExactAlarmSettings(c: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //跳到「闹钟和提醒」授权页

    /** 已被放进电池优化白名单 = 不需要再申请 */
    fun batteryOk(c: Context): Boolean =
        runCatching {
            c.getSystemService(android.os.PowerManager::class.java)
                .isIgnoringBatteryOptimizations(c.packageName)
        }.getOrDefault(true)

    fun openBatterySettings(c: Context) {
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //申请忽略电池优化（否则 Doze 会把闹钟压到几十分钟后）

    fun fmt(at: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))
} //定时任务调度与通知