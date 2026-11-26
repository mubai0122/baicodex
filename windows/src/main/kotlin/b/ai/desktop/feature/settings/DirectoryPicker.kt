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
 * 原生目录选择对话框（Swing，跨平台；Windows 的 FileDialog 不能只选目录）。
 */

package b.ai.desktop.feature.settings

import java.io.File

fun pickDirectory(start: File, title: String = "选择目录"): String? = runCatching {
    val chooser = javax.swing.JFileChooser(if (start.isDirectory) start else start.parentFile)
    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = title
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}.getOrNull()
