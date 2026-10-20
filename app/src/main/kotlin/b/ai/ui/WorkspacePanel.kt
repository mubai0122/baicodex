package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.core.Paths
import b.ai.exec.Workspace
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 工作区绑定卡：设置页内联与聊天页弹窗共用一张 */
@Composable
internal fun WorkspaceSection() {
    var draft by remember { mutableStateOf(Workspace.path()) }
    var files by remember { mutableStateOf(0) }
    var dirs by remember { mutableStateOf(0) }
    var bytes by remember { mutableStateOf(0L) }
    var tree by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    //扫盘可能上千个文件，一律走 IO 线程，扫完再回主线程更新
    fun reload() {
        scope.launch {
            busy = true
            val list = withContext(Dispatchers.IO) { Workspace.scan() }
            files = list.count { !it.dir }
            dirs = list.count { it.dir }
            bytes = list.filter { !it.dir }.sumOf { it.size }
            tree = withContext(Dispatchers.IO) { Workspace.tree() }
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    SectionTitle("工作区", "相对路径都落在工作区里，命令也默认在这里执行")
    CardBox {
        //新会话自动换工作区：开 = 每建一个会话配一个新哈希目录；关 = 空工作区
        CapSwitch(
            "新会话自动绑定工作区",
            "开启后每个新会话都配一个独立目录；关闭则不绑定，变成空工作区",
            Cfg.autoBindWorkspace
        ) { on ->
            Cfg.autoBindWorkspace = on
            if (!on) Cfg.sessionWorkspace = ""
            Workspace.invalidate()
            draft = Workspace.path()
            reload()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                Workspace.custom() -> "已绑定：${Workspace.path()}"
                Workspace.path().isEmpty() -> "未绑定（空工作区）"
                Cfg.sessionWorkspace.isNotBlank() -> "本会话工作区：${Workspace.path()}"
                else -> "默认工作区：${Workspace.path()}"
            },
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 2
        )
        Text(
            when {
                Workspace.path().isEmpty() ->
                    "现在不绑任何目录：相对路径不生效，模型只能用绝对路径；想绑定就填下面的路径再点「绑定」"
                else -> "$files 个文件 / $dirs 个目录 / ${Workspace.human(bytes)}"
            },
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 3
        )
        Field(
            "工作区路径",
            draft,
            { draft = it },
            placeholder = "${Paths.base().absolutePath}/xxxx-xxxx-xxxx-xxxx-xxxx-xxxx"
        )
        Spacer(Modifier.height(6.dp))
        Workspace.quickPicks().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, p) ->
                    PolicyChip(label, Workspace.path() == p) { draft = p }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("绑定", enabled = !busy, modifier = Modifier.weight(1f)) {
                scope.launch {
                    busy = true
                    val ok = withContext(Dispatchers.IO) { Workspace.bind(draft) }
                    if (ok) {
                        draft = Workspace.path()
                        note = "已绑定，索引已生成"
                    } else {
                        note = "绑定失败：路径为空、建不出目录，或选的是数据根 / 外部存储根"
                    }
                    busy = false
                    reload()
                }
            }
            GhostButton("恢复默认", modifier = Modifier.weight(1f)) {
                Workspace.unbind()
                draft = Workspace.path()
                note = if (Cfg.autoBindWorkspace) "已回到默认工作区" else "已解绑，当前是空工作区"
                reload()
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            GhostButton("重建索引", modifier = Modifier.weight(1f)) {
                scope.launch {
                    busy = true
                    val n = withContext(Dispatchers.IO) { Workspace.index() }
                    note = if (n > 0) "索引已重写为 ${Workspace.INDEX}（$n 条）" else "没有可索引的内容"
                    busy = false
                    reload()
                }
            }
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(note, fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text("目录树预览（每层前 40 项，进系统提示的就是这份）", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                busy -> "扫描中…"
                tree.isBlank() -> "目录是空的；把文件丢进来或让模型直接写相对路径"
                else -> tree.take(1200)
            },
            fontSize = 10.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
} //工作区卡：当前工作区、快捷目录、绑定 / 恢复默认 / 重建索引、目录树预览

/** 聊天页右上角 </> 打开的弹窗版 */
@Composable
internal fun WorkspaceDialog(onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("工作区", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "关闭",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .background(SurfaceMuted, RoundedCornerShape(8.dp))
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
        ) {
            WorkspaceSection()
            Spacer(Modifier.height(6.dp))
        }
    }
} //工作区弹窗