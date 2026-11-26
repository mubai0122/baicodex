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
 * 工作区统计：文件数、占用体积、顶层条目，给设置里的工作区页用。
 * 只读，不修改任何文件；条目过多时提前收手，避免在大目录上卡住界面。
 */

package b.ai.desktop.core.workspace

import java.io.File
import java.util.Locale

/** 顶层一项 */
data class WsEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val childCount: Int,
)

/** 工作区概览 */
data class WsStat(
    val path: String,
    val exists: Boolean,
    val files: Int,
    val dirs: Int,
    val bytes: Long,
    /** 超过扫描上限时为 true，数字是「至少这么多」 */
    val truncated: Boolean,
    val top: List<WsEntry>,
)

object WorkspaceStat {
    private const val MaxFiles = 20_000

    fun scan(root: File): WsStat {
        if (!root.exists()) {
            return WsStat(root.absolutePath, exists = false, files = 0, dirs = 0, bytes = 0, truncated = false, top = emptyList())
        }
        var files = 0
        var dirs = 0
        var bytes = 0L
        var truncated = false

        root.walkTopDown().onEnter { dir ->
            // 跳过依赖与版本库目录：它们的体积不代表用户内容，还特别慢
            val name = dir.name.lowercase()
            dir == root || (name !in SkipDirs)
        }.forEach { file ->
            if (file.isDirectory) {
                dirs++
            } else {
                files++
                bytes += runCatching { file.length() }.getOrDefault(0L)
            }
            if (files > MaxFiles) {
                truncated = true
                return@forEach
            }
        }

        val top = root.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.take(24)
            ?.map { child ->
                if (child.isDirectory) {
                    WsEntry(child.name, true, 0L, child.listFiles()?.size ?: 0)
                } else {
                    WsEntry(child.name, false, runCatching { child.length() }.getOrDefault(0L), 0)
                }
            }.orEmpty()

        return WsStat(
            path = root.absolutePath,
            exists = true,
            files = files,
            dirs = dirs,
            bytes = bytes,
            truncated = truncated,
            top = top,
        )
    }

    private val SkipDirs = setOf(".git", "node_modules", ".gradle", ".idea", "build", ".cxx", "__pycache__")
}

fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
