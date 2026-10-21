package b.ai.ui
import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Tools
import b.ai.core.Mcp
import b.ai.core.McpTool
import b.ai.core.Skills
import b.ai.exec.Workspace
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 一次 @ 提及：start 是 @ 的下标，token 是 @ 后面的内容 */
data class Mention(val start: Int, val token: String)

/** 取文本末尾正在编辑的 @token；写完（带空格 / 换行）就不再弹面板 */
fun mentionOf(text: String): Mention? {
    val at = text.lastIndexOf('@')
    if (at < 0) return null
    val tail = text.substring(at + 1)
    if (tail.contains('\n') || tail.length > 120) return null
    val fileMode = tail == "files" || tail.startsWith("files ")
    //文件模式允许空格（后面要接路径），其余模式出现空格即视为写完
    if (!fileMode && tail.contains(' ')) return null
    return Mention(at, tail)
} //扫描末尾 @token

@Composable
fun MentionPanel(token: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val fileMode = token == "files" || token.startsWith("files ")
    val raw = if (fileMode) token.removePrefix("files").trim() else token.trim()
    var rows by remember(raw, fileMode) { mutableStateOf<List<File>>(emptyList()) }
    var loaded by remember(raw, fileMode) { mutableStateOf(!fileMode) }
    var wsFiles by remember { mutableStateOf<List<String>>(emptyList()) }

    //工作区文件清单只扫一次，之后按键过滤在内存里做，避免每敲一个字就扫盘
    LaunchedEffect(Unit) {
        wsFiles = withContext(Dispatchers.IO) {
            if (Workspace.bound()) Workspace.filesForMention() else emptyList()
        }
    }

    LaunchedEffect(raw, fileMode) {
        if (!fileMode) return@LaunchedEffect
        loaded = false
        //没写路径时从工作区根开始；工作区没绑才退回 /sdcard。相对路径同样按工作区解析
        val def = Workspace.path().ifBlank { "/sdcard" }
        val base = File(if (raw.isBlank()) def else Workspace.resolve(raw))
        val dir = if (base.isDirectory) base else base.parentFile
        val prefix = if (base.isDirectory) "" else base.name
        rows = withContext(Dispatchers.IO) {
            runCatching {
                (dir?.listFiles() ?: emptyArray())
                    .filter { prefix.isBlank() || it.name.startsWith(prefix, true) }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    .take(120)
            }.getOrDefault(emptyList())
        }
        loaded = true
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp)
            .background(SurfaceWhite, RoundedCornerShape(14.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    fileMode -> stringResource(R.string.mn_files)
                    raw.isBlank() -> stringResource(R.string.mn_all)
                    else -> stringResource(R.string.mn_match, raw)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (fileMode) stringResource(R.string.mn_files_tip) else stringResource(R.string.mn_files_cmd),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        LazyColumn(Modifier.heightIn(max = 120.dp)) {
            if (fileMode) {
                when {
                    !loaded -> item { Hint(stringResource(R.string.mn_loading)) }
                    rows.isEmpty() -> item { Hint(stringResource(R.string.mn_empty_dir)) }
                    else -> items(rows.size) { i ->
                        val f = rows[i]
                        val p = f.absolutePath
                        MentionRow(
                            title = if (f.isDirectory) "${f.name}/" else f.name,
                            sub = if (f.isDirectory) stringResource(R.string.mn_dir) else sizeText(f.length()),
                            onTap = { onPick(if (f.isDirectory) "files $p/" else "files $p") }
                        )
                    }
                }
            } else {
                val ks = if (raw.isBlank()) {
                    Skills.list
                } else {
                    Skills.list.filter { it.name.contains(raw, true) || it.desc.contains(raw, true) }
                }
                val ts = if (raw.isBlank()) {
                    Tools.all
                } else {
                    Tools.all.filter { it.contains(raw, true) || Tools.label(it).contains(raw, true) }
                }
                val wsHit = wsFiles.filter { raw.isBlank() || it.contains(raw, true) }.take(40)
                if (raw.isNotBlank() && ks.isEmpty() && ts.isEmpty() && wsHit.isEmpty()) {
                    item { Hint(stringResource(R.string.mn_no_match, raw)) }
                }
                item { GroupTitle(stringResource(R.string.mn_skills), ks.size) }
                if (ks.isEmpty()) {
                    item { Hint(stringResource(R.string.mn_no_skill)) }
                } else {
                    items(ks.size) { i ->
                        val s = ks[i]
                        MentionRow(
                            title = Skills.displayName(ctx, s) + if (s.on) "" else stringResource(R.string.mn_off),
                            sub = if (s.desc.isBlank()) stringResource(R.string.mn_chars, s.body.length)
                            else Skills.displayDesc(ctx, s),
                            onTap = { onPick(skillText(ctx, s.name, s.body)) }
                        )
                    }
                }
                item { GroupTitle(stringResource(R.string.mn_tools), ts.size) }
                items(ts.size) { i ->
                    val t = ts[i]
                    MentionRow(Tools.label(t), t) { onPick("@$t ") }
                }
                val ms = if (raw.isBlank()) {
                    Mcp.tools
                } else {
                    Mcp.tools.filter {
                        it.name.contains(raw, true) || it.desc.contains(raw, true) || it.server.contains(raw, true)
                    }
                }
                if (ms.isNotEmpty()) {
                    item { GroupTitle(stringResource(R.string.mcp_group), ms.size) }
                    items(ms.size) { i ->
                        val m = ms[i]
                        MentionRow("MCP·" + m.server + "·" + m.name, m.desc.take(60)) { onPick(mcpText(ctx, m)) }
                    }
                }
                if (wsFiles.isNotEmpty()) {
                    item { GroupTitle(stringResource(R.string.mn_ws_files), wsHit.size) }
                    if (wsHit.isEmpty()) {
                        item { Hint(stringResource(R.string.mn_ws_empty, raw)) }
                    } else {
                        items(wsHit.size) { i ->
                            val rel = wsHit[i]
                            MentionRow(rel, Workspace.name()) { onPick("files " + Workspace.resolve(rel)) }
                        }
                    }
                }
                if (raw.isBlank()) {
                    item { GroupTitle(stringResource(R.string.mn_files), 0) }
                    item {
                        MentionRow("files", stringResource(R.string.mn_files_desc) + Workspace.path().ifBlank { "/sdcard" }) {
                            onPick("files " + (Workspace.path().ifBlank { "/sdcard" }) + "/")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
} //@ 提及面板：技能 / 工具 / 文件

@Composable
private fun GroupTitle(text: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceMuted).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 10.sp, color = TextSecondary)
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text("$count", fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.weight(1f))
    }
} //分组标题

@Composable
private fun MentionRow(title: String, sub: String, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tap { onTap() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
        Box(Modifier.width(4.dp))
    }
} //一行候选

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(12.dp))
} //空提示

private fun skillText(ctx: Context, name: String, body: String): String =
    ctx.getString(R.string.mn_use_skill, name) + body.take(4000) + "\n" //技能点击后填进输入框的文本

/** MCP 能力点击后填进输入框的文本：只写能力和服务名，不写 @token（跟技能包一致） */
private fun mcpText(ctx: Context, t: McpTool): String =
    ctx.getString(R.string.mcp_use_pre, t.name, t.server) + "\n"

private fun sizeText(n: Long): String = when {
    n >= 1L shl 20 -> "${n / (1L shl 20)} MB"
    n >= 1L shl 10 -> "${n / (1L shl 10)} KB"
    else -> "$n B"
} //文件大小