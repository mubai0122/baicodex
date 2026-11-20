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
 * 任务通知的前台服务壳。
 *
 * 为什么必须有它：
 *   ColorOS / HyperOS / MIUI 对「后台应用的普通通知」有强清理策略——App 不在前台时，
 *   单纯 nm.notify() 发出来的通知会被延后、折叠甚至直接丢掉，表现就是「常驻通知没用」。
 *   挂到前台服务上，系统才把它当「正在运行的进程」对待，通知不会被回收。
 *
 * 用法：
 *   IslandSvc.push(ctx, spec)  → 起/更新任务通知（失败返回 false，调用方退回普通 notify）
 *   IslandSvc.stop(ctx)        → 撤通知并停服务
 *
 * 通知本体由 Vendor.shared.notification() 构建，厂商 extras（小米 / 华为）在这里同样生效。
 */
package b.ai.device.sys.island

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class IslandSvc : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //胶囊 / 通知上的「停止」：先让 Agent 收工（它自己会走一遍 Island.cancel），再撤服务
        if (intent?.action == ACTION_STOP) {
            runCatching { b.ai.agent.run.Agent.stop() }
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) { //系统重建服务时没有 extras，直接退
            stopSelf()
            return START_NOT_STICKY
        }
        val spec = IslandSpec.from(
            title = intent.getStringExtra(IslandSpec.K_TITLE),
            text = intent.getStringExtra(IslandSpec.K_TEXT),
            progress = intent.getIntExtra(IslandSpec.K_PROGRESS, -1),
            run = intent.getBooleanExtra(IslandSpec.K_RUN, true),
            pinned = intent.getBooleanExtra(IslandSpec.K_PINNED, true)
        )
        val n = (Vendor.shared as? BaseIsland)?.notification(spec)
        if (n == null) { //没通知权限：不占着前台服务，否则 5 秒内不 startForeground 会被系统杀
            stopSelf()
            return START_NOT_STICKY
        }
        //Android 14 起 startForeground 必须声明类型，dataSync 与本场景（跑网络/文件任务）一致
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        val ok = runCatching { ServiceCompat.startForeground(this, ID, n, type) }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    } //收到指令即更新通知

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    } //退出时连通知一起撤

    companion object {
        /** 通知 id 必须与 BaseIsland 共用：否则 cancel 只能撤掉其中一条 */
        const val ID = 0x7a01

        /** 停止动作：胶囊 / 通知上的按钮打过来，交给 Agent.stop 收工 */
        const val ACTION_STOP = "b.ai.island.STOP"

        /**
         * 起服务并发通知。
         * @return false 表示系统拒绝（Android 12+ 后台禁起前台服务），调用方应退回普通 notify
         */
        fun push(ctx: Context, spec: IslandSpec): Boolean = runCatching {
            val i = Intent(ctx, IslandSvc::class.java)
                .putExtra(IslandSpec.K_TITLE, spec.title)
                .putExtra(IslandSpec.K_TEXT, spec.text)
                .putExtra(IslandSpec.K_PROGRESS, spec.progress)
                .putExtra(IslandSpec.K_RUN, spec.stage == Stage.RUN)
                .putExtra(IslandSpec.K_PINNED, spec.pinned)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, i)
            } else {
                ctx.startService(i)
            }
            true
        }.getOrDefault(false) //成功进服务

        /** 撤通知 + 停服务：两件事都要做，服务活着会一直占前台 */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, IslandSvc::class.java)) }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(ID)
        } //撤干净
    }
} //任务通知前台服务