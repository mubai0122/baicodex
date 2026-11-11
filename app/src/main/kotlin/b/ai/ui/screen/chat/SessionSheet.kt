package b.ai.ui.screen.chat
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.Tag
import b.ai.net.api.llm.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import b.ai.agent.run.Agent
import b.ai.core.data.db.Repo
import b.ai.core.data.db.SessionIo
import b.ai.core.model.chat.SessionRow
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import b.ai.R

private fun ts(t: Long): String =
    if (t <= 0L) "" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))

/** 会话历史：切换 / 重命名 / 删除（删除要二次确认）/ 批量导出导入 */
@Composable
fun SessionSheet(onClose: () -> Unit) {
    var list by remember { mutableStateOf(Repo.sessions()) }
    var editing by remember { mutableStateOf<SessionRow?>(null) }
    var draft by remember { mutableStateOf("") }
    var arming by remember { mutableStateOf<String?>(null) }
    //管理态：勾选多条后批量导出 / 批量删
    var managing by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    var wipeArm by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    //导入走系统选文件：读到内容先落一份临时文件，SessionIo 只吃 File
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val txt = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (txt == null) {
            note = "读不到这个文件"
        } else {
            val tmp = java.io.File(ctx.cacheDir, "session-import.json")
            runCatching { tmp.writeText(txt) }
            note = SessionIo.import(tmp)
            runCatching { tmp.delete() }
            list = Repo.sessions()
        }
    }

    fun reload() {
        list = Repo.sessions()
    }

    ScrimCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ss_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text("${list.size}", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                if (managing) "完成" else "管理",
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                    .clickable {
                        managing = !managing
                        if (!managing) {
                            picked.clear()
                            wipeArm = false
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.ss_close),
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        val target = editing
        if (target != null) {
            Text(stringResource(R.string.ss_rename, target.title), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(stringResource(R.string.ss_cancel)) { editing = null }
                Spacer(Modifier.width(10.dp))
                PrimaryButton(stringResource(R.string.ss_save)) {
                    Agent.renameSession(target.id, draft)
                    editing = null
                    reload()
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(glassScrollState())
            ) {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.ss_empty), fontSize = 12.sp, color = TextSecondary)
                }
                list.forEach { s ->
                    val current = s.id == Agent.sessionId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .liquidBg(RoundedCornerShape(12.dp)
                            , tint = if (current) AccentBlueSoft else SurfaceWhite)
                            .border(
                                1.dp,
                                if (current) AccentBlue else BorderGray,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (managing) {
                                    if (picked.contains(s.id)) picked.remove(s.id) else picked.add(s.id)
                                } else {
                                    Agent.openSession(s.id)
                                    onClose()
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title.ifBlank { stringResource(R.string.ag_new_session) },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (current) {
                                Spacer(Modifier.width(6.dp))
                                Tag(stringResource(R.string.ss_current))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.ss_count, s.count), fontSize = 10.sp, color = TextSecondary)
                        }
                        if (s.preview.isNotBlank()) {
                            Text(
                                s.preview.replace("\n", " ").take(70),
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ts(s.updatedAt), fontSize = 10.sp, color = TextSecondary)
                            Spacer(Modifier.weight(1f))
                            if (managing) {
                                Text(
                                    if (picked.contains(s.id)) "已选" else "未选",
                                    fontSize = 10.sp,
                                    color = if (picked.contains(s.id)) AccentBlue else TextSecondary
                                )
                            } else {
                            Text(
                                stringResource(R.string.ss_rename_btn),
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier
                                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                                    .clickable {
                                        editing = s
                                        draft = s.title
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val armed = arming == s.id
                            Text(
                                if (armed) stringResource(R.string.ss_del_confirm) else stringResource(R.string.ss_del),
                                fontSize = 10.sp,
                                color = DangerRed,
                                modifier = Modifier
                                    .background(DangerRedSoft, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (armed) {
                                            Agent.deleteSession(s.id)
                                            arming = null
                                            reload()
                                        } else {
                                            arming = s.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            }
                        }
                    }
                }
            }
            if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(note, fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            if (managing) {
                //批量操作：导出选了哪些就导哪些，删一定要点两下
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("${picked.size} 已选", fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    GhostButton("导入…") { picker.launch("application/json") }
                    Spacer(Modifier.width(10.dp))
                    GhostButton("导出选中") {
                        val (f, msg) = SessionIo.export(picked.toList())
                        note = msg
                        if (f != null) picked.clear()
                    }
                    Spacer(Modifier.width(10.dp))
                    GhostButton(
                        if (wipeArm) "确认删除 ${picked.size} 个" else "删除选中"
                    ) {
                        if (!wipeArm) {
                            wipeArm = true
                        } else {
                            picked.forEach { Agent.deleteSession(it) }
                            note = "已删除 ${picked.size} 个会话"
                            picked.clear()
                            wipeArm = false
                            reload()
                        }
                    }
                }
            } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton("导入…") { picker.launch("application/json") }
                Spacer(Modifier.width(10.dp))
                GhostButton(stringResource(R.string.ss_new)) {
                    Agent.newSession()
                    reload()
                    onClose()
                }
            }
            }
        }
    }
} //会话历史弹窗