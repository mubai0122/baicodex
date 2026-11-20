/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * OPPO / 一加（ColorOS / OxygenOS）流体云适配。
 *
 * 曾经走过弯路：把任务伪装成「音乐会话」（MediaSession + MediaStyle）骗上岛。
 * 结果胶囊变成媒体卡片，还跟真正的音乐播放器抢同一个位置，语义也不对。
 * 实测 ColorOS 认的是**前台服务承载的普通常驻通知**：
 *   · 通知挂在 IslandSvc（前台服务）上，系统才把它当「正在运行的任务」；
 *   · 通道重要级非静默（DEFAULT），状态栏与流体云入口才会出现；
 *   · 标题 = 会话名、正文 = 当前状态，系统直接拿去当胶囊文案；
 *   · 收工后同一条通知切成完成态，正文变成「任务已完成」。
 * 所以这里只保留通道配置，通知本体统一走 BaseIsland（普通通知 + 进度 + 停止按钮）。
 */
package b.ai.device.sys.island.oppo

import android.app.NotificationManager
import b.ai.device.sys.island.BaseIsland

class OppoIsland : BaseIsland() {
    /** 独立通道：单独关掉任务通知不影响别的通知；旧通道留着不干扰 */
    override val channelId = "task_island_oppo"

    /** 必须非静默：静默通道 ColorOS 不给流体云入口 */
    override fun importance() = NotificationManager.IMPORTANCE_DEFAULT
} //ColorOS 流体云适配