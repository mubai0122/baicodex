/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:11
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.device.shell.root

/** 用户服务与客户端约好的事务号：单独放一个空对象，服务端类加载时不会把整个 Shiz 拽进来 */
internal object ExecProto {
    const val DESC = "b.ai.exec.IExec"
    const val TX_EXEC = 1
    const val TX_UID = 2
} //用户服务协议常量
