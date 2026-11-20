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
 * vivo OriginOS 原子通知 / 灵动岛适配。
 *
 * 现状：OriginOS 的原子通知只对白名单应用开放，三方通知在岛上的呈现由系统自行决定，
 * 没有可写入的公开 extras 键。这里走标准常驻通知降级。
 * 若拿到 OriginOS 接入文档，只需在 decorate() 里补字段。
 */
package b.ai.device.sys.island.vivo

import b.ai.device.sys.island.plain.PlainIsland

class VivoIsland : PlainIsland() {
    override val channelId = "task_island_vivo"
}