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
 * 三星 One UI 适配。
 *
 * 现状：One UI 7 的 Now Bar / Live Updates 基于 Android 16 的 ProgressStyle 通知模板，
 * 本项目 targetSdk 34 拿不到该模板，因此走标准常驻通知降级。
 * 升级 targetSdk 后可改用 Notification.ProgressStyle。
 */
package b.ai.device.sys.island.samsung

import b.ai.device.sys.island.plain.PlainIsland

class SamsungIsland : PlainIsland() {
    override val channelId = "task_island_sam"
}