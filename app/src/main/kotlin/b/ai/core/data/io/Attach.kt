/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:06
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.data.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** 任意文件附件：复制进私有目录，路径直接交给模型用 read_file 读 */
object Attach {
    fun importFrom(ctx: Context, uri: Uri): String? {
        val name = queryName(ctx, uri) ?: "file-${System.currentTimeMillis()}"
        val dir = Layout.dir(ctx, Layout.ATTACH).apply { mkdirs() }
        val safe = name.replace('/', '_').take(80)
        val direct = File(dir, safe)
        val target = if (direct.exists()) File(dir, "${System.currentTimeMillis()}-$safe") else direct
        val ok = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                target.outputStream().use { out -> ins.copyTo(out) }
            }
        }.getOrNull() != null
        return if (ok) target.absolutePath else null
    } //复制进来并返回本地路径

    private fun queryName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i) else null
            } else null
        }
    }.getOrNull()
} //文件附件