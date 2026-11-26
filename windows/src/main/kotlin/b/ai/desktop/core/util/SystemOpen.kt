/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 用系统默认程序打开路径 / 链接。桌面端所有「打开目录」「打开官网」都走这里，
 * 失败不抛异常（无桌面环境时只是没反应，不影响主界面）。
 */

package b.ai.desktop.core.util

import java.awt.Desktop
import java.io.File
import java.net.URI

fun openPath(path: String): Boolean = runCatching {
    val file = File(path)
    if (!file.exists()) return@runCatching false
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file)
        true
    } else {
        false
    }
}.getOrDefault(false)

fun openUrl(url: String): Boolean = runCatching {
    if (!Desktop.isDesktopSupported()) return@runCatching false
    Desktop.getDesktop().browse(URI(url))
    true
}.getOrDefault(false)
