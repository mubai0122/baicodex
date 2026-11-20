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
 * 华为实况窗（HarmonyOS / EMUI 的岛形态）适配。
 *
 * 字段全部走 notification.extras，键名以 notification.live. 开头：
 *   notification.live.type      → 形态，ISLAND = 岛
 *   notification.live.event     → 生命周期事件（创建 / 结束）
 *   notification.live.capsule   → 折叠态胶囊（Bundle：type/title/content/颜色）
 *   notification.live.feature   → 展开态特性区（type=3 进度可视化）
 *   notification.live.operation → 操作区（同 type=3 进度）
 *   notification.live.titleOverlay → 状态栏只显示标题，避免正文挤在一行
 * 注意：三方应用需在华为侧申请实况窗权益，未开通时字段被忽略、通知照常。
 */
package b.ai.device.sys.island.huawei

import android.app.NotificationManager
import android.os.Bundle
import androidx.core.app.NotificationCompat
import b.ai.device.sys.island.BaseIsland
import b.ai.device.sys.island.IslandSpec
import b.ai.device.sys.island.Stage

class HuaweiIsland : BaseIsland() {
    override val channelId = "task_island_hw"

    /** 实况窗要求非静默通道 */
    override fun importance() = NotificationManager.IMPORTANCE_HIGH

    override fun decorate(b: NotificationCompat.Builder, spec: IslandSpec) {
        val run = spec.stage == Stage.RUN
        val prog = if (spec.progress >= 0) spec.progress.coerceIn(0, 100) else 0

        val ex = Bundle().apply {
            putString("notification.live.type", "ISLAND")
            //event 是生命周期：结束态要让系统主动收窗
            putString("notification.live.event", if (run) "live_view_create" else "live_view_close")
            putString("notification.live.titleOverlay", clip(spec.title, 20))

            //折叠态胶囊
            putBundle(
                "notification.live.capsule",
                Bundle().apply {
                    putInt("type", 1) //1 = 图文
                    putString("title", clip(spec.title, 20))
                    putString("content", clip(spec.text, 40))
                    putString("colorTitle", "#FF1A1A1A")
                    putString("colorContent", "#FF666666")
                }
            )
            //展开态：进度型可视化
            putBundle(
                "notification.live.feature",
                Bundle().apply {
                    putInt("type", 3) //3 = 进度
                    putInt("progress", if (run) prog else 100)
                }
            )
            //操作区：与 feature 同型，系统按 type 决定渲染
            putBundle(
                "notification.live.operation",
                Bundle().apply {
                    putInt("type", 3)
                    putInt("progress", if (run) prog else 100)
                }
            )
        }
        b.addExtras(ex)
    } //写 notification.live.*

    private fun clip(s: String, max: Int): String {
        val t = s.replace('\n', ' ').trim()
        return if (t.length <= max) t else t.take(max - 1) + "…"
    } //防超长
}