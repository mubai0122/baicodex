package b.ai.ui

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
import b.ai.agent.Agent
import b.ai.core.Repo
import b.ai.core.SessionRow
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

private fun ts(t: Long): String =
    if (t <= 0L) "" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))

/** 会话历史：切换 / 重命名 / 删除（删除要二次确认） */
@Composable
fun SessionSheet(onClose: () -> Unit) {
    var list by remember { mutableStateOf(Repo.sessions()) }
    var editing by remember { mutableStateOf<SessionRow?>(null) }
    var draft by remember { mutableStateOf("") }
    var arming by remember { mutableStateOf<String?>(null) }

    fun reload() {
        list = Repo.sessions()
    }

    ScrimCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("会话历史", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text("${list.size}", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "关闭",
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier
                    .background(AccentBlueSoft, RoundedCornerShape(9.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        val target = editing
        if (target != null) {
            Text("重命名：${target.title}", fontSize = 12.sp, color = TextSecondary)
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
                GhostButton("取消") { editing = null }
                Spacer(Modifier.width(10.dp))
                PrimaryButton("保存") {
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
                    .verticalScroll(rememberScrollState())
            ) {
                if (list.isEmpty()) {
                    Text("还没有会话", fontSize = 12.sp, color = TextSecondary)
                }
                list.forEach { s ->
                    val current = s.id == Agent.sessionId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (current) AccentBlueSoft else SurfaceWhite,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (current) AccentBlue else BorderGray,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                Agent.openSession(s.id)
                                onClose()
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (current) {
                                Spacer(Modifier.width(6.dp))
                                Tag("当前")
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${s.count} 条", fontSize = 10.sp, color = TextSecondary)
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
                            Text(
                                "改名",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier
                                    .background(SurfaceMuted, RoundedCornerShape(8.dp))
                                    .clickable {
                                        editing = s
                                        draft = s.title
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val armed = arming == s.id
                            Text(
                                if (armed) "确认删除" else "删除",
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
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton("新会话") {
                    Agent.newSession()
                    reload()
                    onClose()
                }
            }
        }
    }
} //会话历史弹窗