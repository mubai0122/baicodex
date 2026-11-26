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
 * 工作区页（对齐手机端的「工作区」）：当前目录、最近用过、目录统计与顶层条目。
 * 统计只读；「忘记」只删记录不删目录，避免误删用户文件。
 */

package b.ai.desktop.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.util.openPath
import b.ai.desktop.core.workspace.WsStat
import b.ai.desktop.core.workspace.fmtSize
import b.ai.desktop.feature.settings.PillButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.SettingRow
import b.ai.desktop.feature.settings.TextSetting
import b.ai.desktop.feature.settings.pickDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorkspaceCenter(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(state.config.workspace) }
    var stat by remember { mutableStateOf<WsStat?>(null) }
    var scanning by remember { mutableStateOf(false) }

    suspend fun rescan() {
        if (scanning) return
        scanning = true
        stat = withContext(Dispatchers.IO) { state.workspaceStat() }
        scanning = false
    }

    LaunchedEffect(state.config.workspace) {
        stat = null
        rescan()
    }

    SectionTitle("当前工作区", "文件工具和命令工具都只能在这个目录里动作，越界会被拦下")
    TextSetting(
        label = "目录",
        value = draft,
        onChange = { draft = it },
        placeholder = state.config.workspaceDir().absolutePath,
    )
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillButton("选择目录…") {
            pickDirectory(state.config.workspaceDir(), "选择工作区目录")?.let { draft = it }
        }
        PillButton("应用", primary = true) {
            state.flash(state.setWorkspace(draft))
            draft = state.config.workspace
        }
        PillButton("打开") { openPath(state.config.workspaceDir().absolutePath) }
        PillButton("恢复默认") {
            state.flash(state.resetWorkspace())
            draft = state.config.workspace
        }
    }
    Text(
        "生效中：${state.config.workspaceDir().absolutePath}",
        fontSize = 10.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 5.dp),
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("最近用过", "只做快捷切换；「忘记」不会删除磁盘上的目录")
    if (state.config.recentWorkspaces.isEmpty()) {
        Text("还没有切换过工作区", fontSize = 10.sp, color = colors.onSurfaceVariant)
    } else {
        state.config.recentWorkspaces.forEach { path ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    path,
                    fontSize = 10.sp,
                    color = if (path == state.config.workspaceDir().absolutePath) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                PillButton("切换") {
                    state.flash(state.setWorkspace(path))
                    draft = state.config.workspace
                }
                Spacer(Modifier.width(6.dp))
                PillButton("忘记") { state.forgetWorkspace(path) }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("目录统计", "跳过 .git / node_modules / build，避免在大仓库上卡住")
        Spacer(Modifier.weight(1f))
        if (scanning) {
            Text("扫描中…", fontSize = 10.sp, color = colors.onSurfaceVariant)
        } else {
            PillButton("重新扫描") { scope.launch { rescan() } }
        }
    }

    val snapshot = stat
    if (snapshot == null) {
        Text("正在统计…", fontSize = 10.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    } else if (!snapshot.exists) {
        Text("目录不存在：${snapshot.path}", fontSize = 10.sp, color = colors.error)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Metric("文件", snapshot.files.toString())
            Metric("目录", snapshot.dirs.toString())
            Metric("占用", if (snapshot.truncated) "≥ ${fmtSize(snapshot.bytes)}" else fmtSize(snapshot.bytes))
        }
        if (snapshot.truncated) {
            Text(
                "条目超过 2 万，数字是下限；完整体积请用资源管理器看",
                fontSize = 9.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.size(8.dp))
        SectionTitle("顶层条目")
        if (snapshot.top.isEmpty()) {
            Text("空目录", fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
        snapshot.top.take(12).forEach { entry ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (entry.isDir) "▸ ${entry.name}" else "· ${entry.name}",
                    fontSize = 10.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (entry.isDir) "${entry.childCount} 项" else fmtSize(entry.size),
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (snapshot.top.size > 12) {
            Text("…还有 ${snapshot.top.size - 12} 项", fontSize = 9.sp, color = colors.onSurfaceVariant)
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SettingRow("数据目录", "配置、会话、插件、Git 凭据都在这里") {
        PillButton("打开") { openPath(b.ai.desktop.core.config.ConfigStore.appDir().absolutePath) }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 9.sp, color = colors.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    }
}

/** 聊天区的工作区页：整窗覆盖 + 顶部返回 */
@Composable
fun WorkspacePage(state: AppState, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("工作区", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("文件与命令工具只能在这里读写", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                PillButton("返回") { onClose() }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.widthIn(max = 820.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    WorkspaceCenter(state)
                    Spacer(Modifier.width(1.dp).padding(bottom = 24.dp))
                }
            }
        }
    }
}
