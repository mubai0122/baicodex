/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 附件选择
 * 走 AWT 原生文件对话框；用户取消返回 null。
 */

package b.ai.desktop.feature.chat.input

import b.ai.desktop.data.Attachment
import b.ai.desktop.data.AttachmentKind
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun pickAttachment(kind: AttachmentKind): Attachment? {
    val file = chooseFile(kind.label, kind.extensions) ?: return null
    return Attachment(kind = kind, name = file.name, path = file.absolutePath)
}

private fun chooseFile(title: String, extensions: List<String>): File? = try {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (extensions.isNotEmpty()) {
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            extensions.any { lower.endsWith(".$it") }
        }
    }
    dialog.isVisible = true
    val dir = dialog.directory
    val name = dialog.file
    if (dir != null && name != null) File(dir, name) else null
} catch (t: Throwable) {
    // 没有可用图形环境时静默失败，不影响主界面
    null
}
