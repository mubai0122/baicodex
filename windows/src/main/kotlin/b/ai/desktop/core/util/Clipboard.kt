/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 00:52:12
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 系统剪贴板
 * 复制消息/会话走 AWT 剪贴板；没有图形环境时返回 false，由调用方提示。
 */

package b.ai.desktop.core.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun copyToClipboard(text: String): Boolean {
    if (text.isBlank()) return false
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)
}
