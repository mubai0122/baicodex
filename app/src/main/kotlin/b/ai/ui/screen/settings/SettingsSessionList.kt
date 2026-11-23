/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 02:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.memory.Memory
import b.ai.agent.run.Agent
import b.ai.agent.run.SessionOps
import b.ai.core.data.db.SessionIo
import b.ai.core.model.chat.SessionRow
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.LocalPicker
import b.ai.ui.widget.PickItem
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.Tag
import b.ai.ui.widget.tap
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间戳：统一 MM-dd HH:mm */
private fun sessTime(t: Long): String {
    if (t <= 0L) return ""
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))
} //时间戳

/**
 * 会话列表卡：默认展开 5 条，点「展开更多」每次多 5 条。
 * 排序：当前会话 → 会话级置顶 → 最后更新时间倒序。
 * 每行右侧一个操作按钮：重命名 / 触发总结（上下文压缩）/ 整理记忆 / 导出（5 种格式）/ 删除（二次确认）。
 * 卡底部还有「导入会话」与「导入后自动加后缀」开关。
 */
fun LazyListScope.sessionListSec() {
    item {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val picker = LocalPicker.current
        var limit by remember { mutableStateOf(5) }
        var busy by remember { mutableStateOf("") }
        val rows = Agent.sessions
        val cur = Agent.sessionId
        val sorted = remember(rows, cur) {
            rows.sortedWith(
                compareByDescending<SessionRow> { it.id == cur }
                    .thenByDescending { it.pinned }
                    .thenByDescending { it.updatedAt }
            )
        } //排序：当前 → 置顶 → 时间
        fun toast(msg: String) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        } //吐司
        //导入走系统选文件：读到内容先落一份临时文件，SessionIo 只吃 File
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val txt = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                if (txt == null) {
                    toast("读不到这个文件")
                } else {
                    val tmp = File(ctx.cacheDir, "session-import.json")
                    runCatching { tmp.writeText(txt) }
                    val msg = SessionIo.import(tmp)
                    runCatching { tmp.delete() }
                    Agent.refreshSessions()
                    toast(msg)
                }
            }
        } //导入会话

        Spacer(Modifier.height(14.dp))
        SectionTitle("会话列表", "共 ${sorted.size} 个 · 当前会话置顶")
        CardBox {
            if (sorted.isEmpty()) {
                Text("还没有会话", fontSize = 12.sp, color = TextSecondary)
            }
            sorted.take(limit).forEachIndexed { i, s ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderGray.copy(alpha = 0.25f))
                    )
                }
                SessionLine(
                    s = s,
                    current = s.id == cur,
                    busy = busy == s.id,
                    onOpen = {
                        Agent.openSession(s.id)
                        Agent.refreshSessions()
                        toast("已切到「${s.title.ifBlank { "未命名" }}」")
                    },
                    onMenu = {
                        //菜单也用项目自己的下拉面板，不用系统 DropdownMenu
                        val items = listOf(
                            PickItem("重命名", false) {
                                SessionDialogs.draft = s.title
                                SessionDialogs.rename = s
                            },
                            PickItem("触发总结（上下文压缩）", false) {
                                if (busy.isBlank()) {
                                    busy = s.id
                                    scope.launch {
                                        val msg = runCatching { SessionOps.compact(s.id) }.getOrDefault("压缩失败")
                                        busy = ""
                                        Agent.refreshSessions()
                                        toast(msg)
                                    }
                                }
                            },
                            PickItem("整理记忆（扫描全部会话）", false) {
                                if (busy.isBlank()) {
                                    busy = s.id
                                    scope.launch {
                                        toast("开始扫描全部会话，可能要一会儿…")
                                        val (org, msg) = runCatching { SessionOps.organizeMemory() }
                                            .getOrDefault(null to "整理失败")
                                        //条目落进永久记忆表：重复的由 Memory.add 自己跳过
                                        org?.lines?.forEach { line -> Memory.add(line, org.sessionId) }
                                        busy = ""
                                        Agent.refreshSessions()
                                        toast(msg)
                                    }
                                }
                            },
                            PickItem("导出会话", false) {
                                //二级下拉：选格式
                                val fmts = SessionIo.Fmt.entries.map { fmt ->
                                    PickItem(fmt.label, false) {
                                        val (f, msg) = SessionIo.exportAs(listOf(s.id), fmt)
                                        toast(msg)
                                        if (f == null) Unit
                                    }
                                }
                                picker("导出格式 · " + s.title.ifBlank { "未命名" }, fmts)
                            },
                            PickItem("删除会话（不可恢复）", false) { SessionDialogs.del = s }
                        )
                        picker("会话操作 · " + s.title.ifBlank { "未命名" }, items)
                    }
                )
            }
            //展开更多：每次多 5 条，到底就隐藏
            if (sorted.size > limit) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "展开更多（还有 ${sorted.size - limit} 个）",
                    fontSize = 12.sp,
                    color = AccentBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                        .clickable { limit += 5 }
                        .padding(vertical = 8.dp)
                )
            }
            if (busy.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("正在处理…", fontSize = 11.sp, color = TextSecondary)
            }

            // ---------- 导入 ----------
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderGray.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("导入会话", fontSize = 13.sp, color = TextPrimary)
                    Text("选本应用导出的 JSON 文件，会重建为新的会话", fontSize = 10.sp, color = TextSecondary)
                }
                GhostButton("选择文件", modifier = Modifier.height(34.dp)) {
                    importLauncher.launch("application/json")
                }
            }
        }

    }
} //会话列表卡

/**
 * 会话重命名 / 删除确认的弹窗状态：必须有外层承载。
 * 原来写在 sessionListSec 的 item 内部，会被 LazyColumn 的 item 裁剪，视觉上落到列表下方，
 * 所以状态提到这里，由设置页根弹窗层（HostInOverlay）统一渲染，保证覆盖在二级弹窗之上。
 */
object SessionDialogs {
    var rename by mutableStateOf<SessionRow?>(null)
    var del by mutableStateOf<SessionRow?>(null)
    var draft by mutableStateOf("")

    /** 有没有待渲染的弹窗 */
    val open: Boolean get() = rename != null || del != null
} //会话弹窗状态

/** 重命名与删除确认：只由设置页根弹窗层调用 */
@Composable
fun SessionDialogsHost() {
    val ctx = LocalContext.current
    val rn = SessionDialogs.rename
    if (rn != null) {
        ScrimCard(onDismiss = { SessionDialogs.rename = null }) {
            Text("重命名会话", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = SessionDialogs.draft,
                onValueChange = { SessionDialogs.draft = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                GhostButton("取消", modifier = Modifier.height(36.dp)) { SessionDialogs.rename = null }
                Spacer(Modifier.width(10.dp))
                PrimaryButton("保存", modifier = Modifier.height(36.dp)) {
                    Agent.renameSession(rn.id, SessionDialogs.draft)
                    SessionDialogs.rename = null
                    Agent.refreshSessions()
                }
            }
        }
    }
    val dr = SessionDialogs.del
    if (dr != null) {
        ScrimCard(onDismiss = { SessionDialogs.del = null }) {
            Text("删除会话", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "确定删除「${dr.title.ifBlank { "未命名" }}」？里面 ${dr.count} 条消息会一起删掉，删了找不回来。",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                GhostButton("取消", modifier = Modifier.height(36.dp)) { SessionDialogs.del = null }
                Spacer(Modifier.width(10.dp))
                Text(
                    "删除",
                    fontSize = 13.sp,
                    color = DangerRed,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(10.dp), tint = DangerRedSoft)
                        .tap {
                            Agent.deleteSession(dr.id)
                            SessionDialogs.del = null
                            Agent.refreshSessions()
                            Toast.makeText(ctx, "已删除", Toast.LENGTH_LONG).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
} //会话弹窗

/** 单行会话：标题 / 当前标记 / 预览 / 时间 + 条数 + 操作按钮 */
@Composable
private fun SessionLine(
    s: SessionRow,
    current: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .liquidBg(RoundedCornerShape(12.dp), tint = if (current) AccentBlueSoft else SurfaceWhite)
            .border(1.dp, if (current) AccentBlue else BorderGray, RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.title.ifBlank { "未命名" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Tag("当前")
                }
                if (s.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Tag("置顶")
                }
            }
            if (s.preview.isNotBlank()) {
                Text(
                    s.preview.replace("\n", " ").take(60),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sessTime(s.updatedAt), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text("${s.count} 条", fontSize = 10.sp, color = TextSecondary)
                if (busy) {
                    Spacer(Modifier.width(8.dp))
                    Text("处理中…", fontSize = 10.sp, color = AccentBlue)
                }
            }
        }
        Row(
            Modifier
                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                .clickable { onMenu() }
                .padding(6.dp)
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "会话操作", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
} //单行会话