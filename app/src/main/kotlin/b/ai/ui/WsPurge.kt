package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.exec.Workspace
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一键清理弹窗的挂载开关：设置页根弹窗层读它 */
object WsPurge {
    var open by mutableStateOf(false)

    /** 每清完一批加一，工作区列表据此自动刷新 */
    var ver by mutableStateOf(0)
}

@Composable
fun WsPurgeDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    //confirm 先摆清单等确认，running 逐条删，done 收尾给结果
    var stage by remember { mutableStateOf("confirm") }
    var batch by remember { mutableStateOf<List<Workspace.WsInfo>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var bytes by remember { mutableStateOf(0L) }
    var step by remember { mutableStateOf("") }
    var doneCount by remember { mutableStateOf(0) }
    var freed by remember { mutableStateOf(0L) }
    var bad by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) {
            runCatching { Workspace.listAll() }.getOrDefault(emptyList())
                .filter { it.unbound && !it.current && it.exists }
        }
        batch = r
        total = r.size
        bytes = r.sumOf { it.bytes }
    }

    ScrimCard(onDismiss = if (stage == "running") null else onClose) {
        Text(
            stringResource(R.string.wsq_t),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        when (stage) {
            "running" -> {
                Text(stringResource(R.string.wsq_running), fontSize = 13.sp, color = AccentBlue)
                if (step.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(step, fontSize = 10.sp, color = TextSecondary)
                }
            }
            "done" -> {
                Text(
                    stringResource(R.string.wsq_done, doneCount, Workspace.human(freed)),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                if (bad > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.wsq_fail, bad), fontSize = 10.sp, color = TextSecondary)
                }
            }
            else -> {
                Text(
                    stringResource(R.string.wsq_d, total, Workspace.human(bytes)),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                //把要删的目录点名列出，别让用户凭一个数字就点「开始清理」
                batch.take(6).forEach { w ->
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            w.name,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(Workspace.human(w.bytes), fontSize = 10.sp, color = TextSecondary)
                    }
                }
                if (batch.size > 6) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.wsq_more, batch.size - 6),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.wsq_note),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (stage) {
                "running" -> Text(
                    stringResource(R.string.wsq_cancel),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .liquidBg(androidx.compose.foundation.shape.RoundedCornerShape(8.dp), tint = SurfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                "done" -> PrimaryButton(stringResource(R.string.wsq_ok)) { onClose() }
                else -> {
                    GhostButton(stringResource(R.string.wsq_cancel)) { onClose() }
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(stringResource(R.string.wsq_go), enabled = total > 0) {
                        stage = "running"
                        step = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching {
                                    Workspace.purgeUnused(ctx) { i, n, name ->
                                        //回调在 IO 线程，进度文案切回主线程再写状态
                                        scope.launch { step = ctx.getString(R.string.wsq_step, i, n, name) }
                                    }
                                }.getOrElse { Triple(0, 0L, 0) }
                            }
                            doneCount = r.first
                            freed = r.second
                            bad = r.third
                            stage = "done"
                            WsPurge.ver++
                        }
                    }
                }
            }
        }
        if (stage != "done") {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().padding(top = 0.dp)) {
                Text(
                    stringResource(R.string.wsq_keep),
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }
        }
    }
} //一键清理未使用工作区