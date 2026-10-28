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
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 工作区绑定卡：设置页内联与聊天页弹窗共用一张 */
@Composable
internal fun WorkspaceSection(onBrowse: () -> Unit = {}) {
    var draft by remember { mutableStateOf(Workspace.path()) }
    var files by remember { mutableStateOf(0) }
    var dirs by remember { mutableStateOf(0) }
    var bytes by remember { mutableStateOf(0L) }
    var tree by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

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

    SectionTitle(stringResource(R.string.wp_title), stringResource(R.string.wp_title_d))
    CardBox {
        //新会话自动换工作区：开 = 每建一个会话配一个新哈希目录；关 = 空工作区
        CapSwitch(
            stringResource(R.string.wp_auto),
            stringResource(R.string.wp_auto_d),
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
                Workspace.custom() -> stringResource(R.string.wp_bound, Workspace.path())
                Workspace.path().isEmpty() -> stringResource(R.string.wp_unbound)
                Cfg.sessionWorkspace.isNotBlank() -> stringResource(R.string.wp_cur, Workspace.path())
                else -> stringResource(R.string.wp_default, Workspace.path())
            },
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 2
        )
        Text(
            when {
                Workspace.path().isEmpty() ->
                    stringResource(R.string.wp_none)
                else -> stringResource(R.string.wp_stat, files, dirs, Workspace.human(bytes))
            },
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 3
        )
        Field(
            stringResource(R.string.wp_path),
            draft,
            { draft = it },
            placeholder = "${Paths.base().absolutePath}/xxxx-xxxx-xxxx-xxxx-xxxx-xxxx"
        )
        Spacer(Modifier.height(6.dp))
        GhostButton(stringResource(R.string.wp_browse), modifier = Modifier.fillMaxWidth()) { onBrowse() }
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
            PrimaryButton(stringResource(R.string.wp_bind), enabled = !busy, modifier = Modifier.weight(1f)) {
                scope.launch {
                    busy = true
                    val ok = withContext(Dispatchers.IO) { Workspace.bind(draft) }
                    if (ok) {
                        draft = Workspace.path()
                        note = ctx.getString(R.string.wp_bound_ok)
                    } else {
                        note = ctx.getString(R.string.wp_bind_fail)
                    }
                    busy = false
                    reload()
                }
            }
            GhostButton(stringResource(R.string.wp_reset), modifier = Modifier.weight(1f)) {
                Workspace.unbind()
                draft = Workspace.path()
                note = if (Cfg.autoBindWorkspace) ctx.getString(R.string.wp_reset_ok) else ctx.getString(R.string.wp_reset_unbound)
                reload()
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            GhostButton(stringResource(R.string.wp_reindex), modifier = Modifier.weight(1f)) {
                scope.launch {
                    busy = true
                    val n = withContext(Dispatchers.IO) { Workspace.index() }
                    note = if (n > 0) ctx.getString(R.string.wp_reindex_ok, Workspace.INDEX, n) else ctx.getString(R.string.wp_reindex_none)
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
        Text(stringResource(R.string.wp_tree), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                busy -> stringResource(R.string.wp_scanning)
                tree.isBlank() -> stringResource(R.string.wp_tree_empty)
                else -> tree.take(1200)
            },
            fontSize = 10.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
} //工作区卡：当前工作区、快捷目录、绑定 / 恢复默认 / 重建索引、目录树预览

/** 聊天页右上角 </> 打开的弹窗版：同一个卡片里在「绑定卡」和「目录选择器」之间切换 */
@Composable
internal fun WorkspaceDialog(onClose: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    //绑定成功后换 key 重建绑定卡，草稿、统计与目录树一起刷新
    var ver by remember { mutableStateOf(0) }
    var tip by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    ScrimCard(onDismiss = if (picking) null else onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (picking) R.string.wp_pick_title else R.string.wp_title),
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            if (!picking) {
                Text(
                    stringResource(R.string.wp_close),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                        .tap { onClose() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (tip.isNotBlank() && !picking) {
            Text(tip, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        if (picking) {
            //选择器里有 LazyColumn，外层不能再套 verticalScroll，否则测高变无限
            WsPickerBody(
                start = Workspace.path(),
                onCancel = { picking = false },
                onPick = { p ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Workspace.bind(p) }
                        picking = false
                        tip = if (ok) ctx.getString(R.string.wp_bound_ok) else ctx.getString(R.string.wp_bind_fail)
                        ver++
                    }
                }
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                key(ver) { WorkspaceSection(onBrowse = { picking = true }) }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
} //工作区弹窗