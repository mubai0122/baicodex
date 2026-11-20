/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:14
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * 岛上通知的公共骨架：通道、通知本体、发与撤。
 * 厂商实现只覆写 decorate()，往 builder 里塞自家系统的字段。
 */
package b.ai.device.sys.island

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import b.ai.App
import b.ai.MainActivity
import b.ai.R

/** 各厂商岛实现的统一接口 */
interface IslandImpl {
    /** 发 / 更新一条任务通知；同一 id 覆盖，不会叠成一摞 */
    fun show(spec: IslandSpec)

    /** 撤掉任务通知（含岛上形态） */
    fun cancel()
}

/** 公共骨架：通道、通知本体、发与撤；厂商实现只补 extras */
abstract class BaseIsland : IslandImpl {
    protected val ctx: Context get() = App.self

    /** 通道 id：各厂商分开，换机后残留的旧通道不互相干扰 */
    protected open val channelId = "task_island"

    /** 通知 id：与桌宠的 0x9e1 刻意错开；必须与 IslandSvc.ID 相同，否则撤不干净 */
    protected open val notifyId = IslandSvc.ID

    /**
     * 真实通道 id。
     * 通道的「重要级」只在创建那一刻生效，旧版建过的通道改代码也不会升级，
     * 所以统一加 v2 后缀：老通道留着不影响，新通道按当前 importance 建。
     */
    private val realChannel: String get() = channelId + "_v2"

    protected open fun channelName(): String = ctx.getString(R.string.island_ch_name)

    /** 通道重要级：要能上岛，小米 / 华为都要求非静默通道 */
    protected open fun importance(): Int = NotificationManager.IMPORTANCE_DEFAULT

    /** 进行中不可划掉；完成态要驻留时同样不可划掉，否则 ColorOS 会当场清掉 */
    private fun ongoing(spec: IslandSpec) = spec.pinned

    /** 通知权限：13 起是运行时权限，没授权宁可不发也不崩 */
    protected fun hasPerm(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } //权限判断

    /** 通道是否被用户/系统关了通知（importance = NONE）；用于设置页提示，不用于发通知 */
    fun channelOff(): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        val ch = nm.getNotificationChannel(realChannel) ?: return false
        return ch.importance == NotificationManager.IMPORTANCE_NONE
    } //通道被关

    private fun ensureChannel(nm: NotificationManager) {
        val exist = nm.getNotificationChannel(realChannel)
        if (exist != null) {
            //已存在：只有「级别被系统压到静默」才重建；用户手动关掉的（NONE）尊重原样，改由设置页提示
            if (exist.importance != NotificationManager.IMPORTANCE_NONE &&
                exist.importance < importance()
            ) {
                runCatching { nm.deleteNotificationChannel(realChannel) }
            } else {
                return
            }
        }
        val ch = NotificationChannel(realChannel, channelName(), importance()).apply {
            setShowBadge(false)
            enableVibration(false) //任务通知会随轮次频繁更新，一震就是灾难
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC //锁屏也要看得见
        }
        nm.createNotificationChannel(ch)
    } //通道只建一次

    /**
     * 前置检查：通知权限、通道（不存在就建）、通道是否被关。
     * @return 可用的 NotificationManager；null 表示这条通知发不出去，调用方直接放弃
     */
    protected fun prepare(): NotificationManager? {
        if (!hasPerm()) return null
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        ensureChannel(nm)
        //通道被用户关成 NONE：发了也是通知栏不显示，干脆不发，设置页会提示
        if (nm.getNotificationChannel(realChannel)?.importance == NotificationManager.IMPORTANCE_NONE) return null
        return nm
    } //能不能发

    /** 真实通道 id 给父类外部（平台 API 构建通知时要用） */
    protected val channelKey: String get() = realChannel

    /** 点通知回 App 的 PendingIntent：厂商实现自己构建通知时复用 */
    protected fun openApp(): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    ) //回到聊天页

    /** 通知 / 岛胶囊上的「停止任务」：打给前台服务，由它去叫 Agent 收工 */
    protected fun stopIntent(): PendingIntent = PendingIntent.getService(
        ctx, 7,
        Intent(ctx, IslandSvc::class.java).setAction(IslandSvc.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    ) //停止任务

    /**
     * 组装通知：没有通知权限、或通道被用户关掉时返回 null（调用方据此跳过一切发送）。
     * 厂商覆写 decorate 往 builder 里塞自家字段；需要整套换掉通知的厂商可直接覆写本方法。
     */
    open fun notification(spec: IslandSpec): Notification? {
        if (prepare() == null) return null

        val b = NotificationCompat.Builder(ctx, realChannel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setOngoing(ongoing(spec))
            .setOnlyAlertOnce(true) //每轮更新不重复提示音
            .setSilent(true) //常驻通知不该响、不该震
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            //分类决定系统在折叠 / 锁屏 / 岛上的呈现：进行中是进度，完成是状态
            .setCategory(
                if (spec.stage == Stage.RUN) NotificationCompat.CATEGORY_PROGRESS
                else NotificationCompat.CATEGORY_STATUS
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) //锁屏上也要能看见
            //挂前台服务时立刻显示，避免 ColorOS 上等到「几秒后才刷出来」
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
        //进行中才需要「停止任务」：完成态的按钮点了没意义，还会让岛胶囊多一格
        if (spec.stage == Stage.RUN) {
            b.addAction(
                NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher,
                    ctx.getString(R.string.island_stop),
                    stopIntent()
                ).build()
            )
        }
        if (spec.progress >= 0) {
            b.setProgress(100, spec.progress.coerceIn(0, 100), false)
        } else if (spec.stage == Stage.RUN) {
            b.setProgress(0, 0, true) //不确定进度：系统画转圈
        } else {
            b.setProgress(0, 0, false)
        }
        decorate(b, spec)
        return runCatching { b.build() }.getOrNull()
    } //通知本体

    /** 厂商钩子：往 builder 里塞自己的岛上字段。默认什么都不做（普通常驻通知） */
    protected open fun decorate(b: NotificationCompat.Builder, spec: IslandSpec) {}

    override fun show(spec: IslandSpec) {
        val n = notification(spec) ?: return //权限 / 通道不可用：静默跳过，状态由设置页提示
        //首选前台服务承载：ColorOS / HyperOS 不会回收前台服务的通知，后台跑任务时它也还在
        if (IslandSvc.push(ctx, spec)) return
        //退路（Android 12+ 从后台禁起前台服务时会走到这里）：普通通知，至少通知栏能看到
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { nm.notify(notifyId, n) }
    } //发通知

    override fun cancel() {
        //停服务会连带撤掉前台通知；再补一次 nm.cancel 兜住「只走过普通 notify」的情况
        IslandSvc.stop(ctx)
    } //撤通知
}