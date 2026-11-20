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
 * 降级实现：不认识岛的 ROM（或厂商未开放第三方接入）走这里。
 * 表现就是一条普通常驻通知——有通知栏，没岛。
 */
package b.ai.device.sys.island.plain

import b.ai.device.sys.island.BaseIsland

open class PlainIsland : BaseIsland()