package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.App
import b.ai.R
import b.ai.core.Paths
import b.ai.exec.Router
import b.ai.exec.Workspace
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 目录选择器的数据层：快捷入口、一层列表、上级目录 */
object WsPick {
    data class Ent(val name: String, val path: String, val dir: Boolean, val size: Long)

    /** 快捷入口：默认工作区、应用目录、数据根、下载、文档、图片、SD 卡根 */
    fun roots(): List<Pair<Int, String>> {
        val ws = Workspace.path().ifBlank { Paths.base().absolutePath }
        return listOf(
            R.string.wp_rt_ws to ws,
            R.string.wp_rt_app to App.self.filesDir.absolutePath,
            R.string.wp_rt_base to Paths.base().absolutePath,
            R.string.wp_rt_dl to Router.normalize("/sdcard/Download"),
            R.string.wp_rt_doc to Router.normalize("/sdcard/Documents"),
            R.string.wp_rt_pic to Router.normalize("/sdcard/Pictures"),
            R.string.wp_rt_sd to Router.normalize("/sdcard")
        )
    } //快捷入口

    /** 列一层：目录在前按名字排，隐藏项跳过，最多 400 条 */
    fun list(path: String): List<Ent> {
        val arr = runCatching { File(path).listFiles() }.getOrNull() ?: return emptyList()
        return arr.asSequence()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .take(400)
            .map { Ent(it.name, it.absolutePath, it.isDirectory, if (it.isDirectory) 0L else it.length()) }
            .toList()
    } //列一层

    /** 上一级：到 SD 卡根或根目录就停，避免在文件系统里乱跑 */
    fun up(path: String): String? {
        val p = path.trimEnd('/')
        if (p.isEmpty() || p == "/" || p == "/sdcard" || p == Router.normalize("/sdcard")) return null
        return File(p).parentFile?.absolutePath
    } //上一级
} //目录选择数据层

/** 目录选择器：快捷入口 + 逐层浏览 + 选中当前目录（只读预览，不做增删改） */
@Composable
internal fun WsPickerBody(start: String, onCancel: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf(start.ifBlank { WsPick.roots().first().second }) }
    var ents by remember { mutableStateOf<List<WsPick.Ent>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    //listFiles 走 IO：公共目录上千个文件时不能卡 UI 线程
    LaunchedEffect(cur) {
        loading = true
        val r = withContext(Dispatchers.IO) { runCatching { WsPick.list(cur) } }
        ents = r.getOrDefault(emptyList())
        err = if (r.isFailure) r.exceptionOrNull()?.message.orEmpty() else ""
        loading = false
    }

    Text(
        stringResource(R.string.wp_pick_title),
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
    )
    Spacer(Modifier.height(8.dp))
    WsPick.roots().chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (labelRes, p) ->
                PolicyChip(ctx.getString(labelRes), cur.trimEnd('/') == p.trimEnd('/')) { cur = p }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Text(
        cur,
        fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace, maxLines = 2
    )
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
            .background(SurfaceMuted, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            WsPick.up(cur)?.let { up ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap { cur = up }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⌃", fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.wp_pick_up),
                            fontSize = 12.sp, color = TextPrimary
                        )
                    }
                }
            }
            items(ents.size) { i ->
                val e = ents[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap(enabled = e.dir) { cur = e.path }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (e.dir) "▸" else "·", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        e.name,
                        fontSize = 12.sp, color = TextPrimary, maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (e.dir) stringResource(R.string.wp_pick_dir) else Workspace.human(e.size),
                        fontSize = 10.sp, color = TextSecondary
                    )
                }
            }
        }
        if (loading || err.isNotBlank() || ents.isEmpty()) {
            Text(
                when {
                    loading -> stringResource(R.string.wp_scanning)
                    err.isNotBlank() -> ctx.getString(R.string.wp_pick_err, err.take(60))
                    else -> stringResource(R.string.wp_pick_empty)
                },
                fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(stringResource(R.string.wp_pick_cancel), modifier = Modifier.weight(1f)) { onCancel() }
        PrimaryButton(stringResource(R.string.wp_pick_here), modifier = Modifier.weight(1f)) {
            onPick(Router.normalize(cur).trimEnd('/'))
        }
    }
} //目录选择器
