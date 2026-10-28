package b.ai.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Repo
import b.ai.exec.Workspace
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/**
 * 启动门：升级后旧版哈希工作区必须先搬进 workspace/ 才允许进主界面。
 * 选「暂不迁移」、或在迁移页里退出（不管迁没迁完），都直接结束进程——不允许带着旧目录继续跑。
 */
@Composable
fun MigrateGate(onReady: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var olds by remember { mutableStateOf<List<File>?>(null) } //null = 还在探测旧目录
    var running by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var bak by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) {
            runCatching { Workspace.legacyDirs() }.getOrDefault(emptyList())
        }
        olds = found
        if (found.isEmpty()) onReady()
    } //探测旧目录：一个都没有就直接放行
    BackHandler { quits(ctx) } //门的整个生命周期都吞返回键，返回键等于退出

    val list = olds ?: return //探测中：什么都不画
    if (list.isEmpty()) return //已放行，等重组切走

    fun start() {
        running = true
        err = ""
        lines = emptyList()
        scope.launch {
            //先把两个库整库备份一份：工作区和数据库一起迁，迁坏了还能回退
            bak = withContext(Dispatchers.IO) {
                runCatching { Repo.backupAll(ctx) }.getOrNull()
            }.orEmpty()
            val e = withContext(Dispatchers.IO) {
                runCatching {
                    Workspace.migrate { i, total, name, files, bytes ->
                        //进度回调在 IO 线程，日志得切回主线程写状态
                        scope.launch {
                            lines = lines + ctx.getString(
                                R.string.mig_mg_step, i, total, name, files, Workspace.human(bytes)
                            )
                        }
                    }
                }.getOrElse { "copy:${it.message ?: it}" }
            }
            running = false
            if (e == null) {
                lines = lines + ctx.getString(R.string.mig_mg_done, list.size)
                onReady()
            } else {
                err = e.removePrefix("copy:")
            }
        }
    } //备份数据库并逐个迁移工作区

    if (running || err.isNotEmpty()) {
        MigratePage(list.size, lines, err, bak, ::start, { quits(ctx) })
        return
    }
    //确认弹窗：只有「立即迁移」和「暂不迁移」两条路，点外部关不掉
    ScrimCard {
        Text(
            stringResource(R.string.mig_ms_t),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.mig_ms_d), fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(10.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.mig_mg_total, list.size),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                list.forEach { f ->
                    Text("· " + f.name, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Text(
                stringResource(R.string.mig_ms_quit),
                fontSize = 12.sp,
                color = DangerRed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(DangerRedSoft, RoundedCornerShape(10.dp))
                    .tap { quits(ctx) }
                    .padding(vertical = 11.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.mig_ms_ok),
                fontSize = 12.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                    .tap { start() }
                    .padding(vertical = 11.dp)
            )
        }
    }
}

/** 迁移页：全屏进度，出错给重试 / 退出；退出即杀进程 */
@Composable
private fun MigratePage(
    total: Int,
    lines: List<String>,
    err: String,
    bak: String,
    onRetry: () -> Unit,
    onQuit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgGray)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(R.string.mig_mg_t),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.mig_mg_total, total), fontSize = 11.sp, color = TextSecondary)
        Text(stringResource(R.string.mig_mg_d), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 420.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(10.dp)
        ) {
            Column {
                if (lines.isEmpty()) {
                    Text("…", fontSize = 11.sp, color = TextSecondary)
                }
                lines.forEach { Text(it, fontSize = 11.sp, color = TextPrimary) }
            }
        }
        if (bak.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.mig_ms_bak, bak),
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 2
            )
        }
        if (err.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mig_mg_fail, err), fontSize = 11.sp, color = DangerRed)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.mig_mg_retry), Modifier.height(36.dp)) { onRetry() }
            }
        }
        Spacer(Modifier.height(16.dp))
        GhostButton(stringResource(R.string.mig_mg_quit), Modifier.height(36.dp)) { onQuit() }
    }
} //迁移进度页

/** 强制退出：关掉所有 Activity 后杀进程，下次进来重新走一遍启动门 */
private fun quits(ctx: Context) {
    var c: Context? = ctx
    while (c != null) {
        if (c is Activity) {
            runCatching { c.finishAffinity() }
            break
        }
        c = (c as? ContextWrapper)?.baseContext
    }
    Process.killProcess(Process.myPid())
    exitProcess(0)
} //强制退出应用