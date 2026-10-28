package b.ai.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import b.ai.core.ApkInstall
import b.ai.net.Update
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 更新弹窗的全局状态：启动时静默检查与设置里手动检查都写这里，由根弹窗层统一渲染 */
object UpdateUi {
    /** 有更新时非空；关掉为 null */
    var prompt by mutableStateOf<Update.Info?>(null)

    /** 更新历史弹窗数据；null 表示不显示 */
    var history by mutableStateOf<List<Update.His>?>(null)

    /** 检查结果提示：已是最新 / 更新已关闭 / 检查失败，统一走弹窗，不再在卡片里塞小字 */
    var tip by mutableStateOf<String?>(null)

    /** 全部关掉 */
    fun close() {
        prompt = null
        history = null
        tip = null
    }
} //更新弹窗状态

/** 更新内容正文：服务端用 \n 分行，逐行渲染 */
private fun lines(s: String): List<String> =
    s.split("\n").map { it.trimEnd() }.filter { it.isNotBlank() }

@Composable
fun UpdateDialog(info: Update.Info, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val force = info.forceUpdateRequired
    val direct = info.downloadDirectUrl
    val jump = info.downloadJumpUrl
    //-1 = 没开始；0..100 = 下载中；101 = 已下完并拉起安装器
    var pct by remember { mutableStateOf(-1) }
    var msg by remember { mutableStateOf("") }
    val main = remember { Handler(Looper.getMainLooper()) }

    /** 直链下载到私有目录，下完立刻拉系统安装器 */
    fun startDownload() {
        if (pct in 0..100) return
        pct = 0
        msg = ctx.getString(R.string.up_startdl)
        scope.launch {
            val dest = File(File(ctx.filesDir, "update"), "bai-${info.latestCode}.apk")
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    Update.download(direct, dest) { p -> main.post { pct = p.coerceAtLeast(0) } }
                }
            }
            r.onSuccess { f ->
                msg = ctx.getString(R.string.up_done, f.length() / 1024 / 1024)
                val err = ApkInstall.install(ctx, f)
                if (err != null) {
                    msg = err
                    pct = -1
                } else {
                    pct = 101
                }
            }.onFailure {
                msg = ctx.getString(R.string.up_fail, it.message?.take(80) ?: "")
                pct = -1
            }
        }
    } //下载并安装

    //强制更新不传 onDismiss：点卡片外部关不掉
    //系统返回键也要吞掉，否则 ScrimCard 拦不住的返回能绕过强制更新
    BackHandler(enabled = force) { }
    ScrimCard(onDismiss = if (force) null else onClose) {
        Text(
            info.title.ifBlank { stringResource(R.string.up_new) },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOf(
                info.subtitle,
                ctx.getString(R.string.up_ver, info.latestVersion),
                info.updateDate
            ).filter { it.isNotBlank() }.joinToString(" · "),
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                val ls = lines(info.content)
                if (ls.isEmpty()) Text(stringResource(R.string.up_no_content), fontSize = 12.sp, color = TextSecondary)
                ls.forEach {
                    Text(it, fontSize = 12.sp, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        //下载进度条：只有拿得到 contentLength 时才显示百分比，未知长度只显示「下载中」
        if (pct in 0..100) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .liquidBg(RoundedCornerShape(3.dp), tint = SurfaceMuted)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((if (pct <= 0) 2 else pct) / 100f)
                        .height(6.dp)
                        .liquidBg(RoundedCornerShape(3.dp), tint = AccentBlue)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        if (msg.isNotBlank()) {
            Text(msg, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        if (direct.isBlank() && jump.isBlank()) {
            Text(
                if (force) stringResource(R.string.up_no_url_force)
                else stringResource(R.string.up_no_url),
                fontSize = 11.sp,
                color = if (force) DangerRed else TextSecondary
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                //直链优先：下完自动拉起安装器，不需要用户再找包
                direct.isNotBlank() -> {
                    Text(
                        when {
                            pct in 0..100 -> if (pct <= 0) ctx.getString(R.string.up_prep) else ctx.getString(R.string.up_downloading, pct)
                            pct == 101 -> ctx.getString(R.string.up_installer)
                            else -> ctx.getString(R.string.up_dl_install)
                        },
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                            .tap { startDownload() }
                            .padding(vertical = 11.dp)
                    )
                }
                //没有直链就走下载页：应用内浏览器打不开就用系统浏览器
                jump.isNotBlank() -> Text(
                    ctx.getString(R.string.up_open_page),
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                        .tap { openBrowser(ctx, jump) }
                        .padding(vertical = 11.dp)
                )
                force -> Text(
                    ctx.getString(R.string.up_exit),
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                        .tap { (ctx as? Activity)?.finishAffinity() }
                        .padding(vertical = 11.dp)
                )
            }
            if (direct.isNotBlank() && jump.isNotBlank()) {
                Text(
                    ctx.getString(R.string.up_page),
                    fontSize = 12.sp,
                    color = AccentBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(AccentBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .tap { openBrowser(ctx, jump) }
                        .padding(vertical = 11.dp)
                )
            }
            //强制更新不给关：没有稍后按钮
            if (!force) {
                Text(
                    ctx.getString(R.string.up_later),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(if (direct.isBlank() && jump.isBlank()) 1f else 1f)
                        .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                        .tap { onClose() }
                        .padding(vertical = 11.dp)
                )
            }
        }
    }
} //检查更新弹窗

/** 用系统浏览器打开下载页，打不开就静默失败（弹窗里已有提示文案） */
private fun openBrowser(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
} //打开下载页

@Composable
fun UpdateHistoryDialog(list: List<Update.His>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    ScrimCard(onDismiss = onClose) {
        Text(stringResource(R.string.up_hist), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(ctx.getString(R.string.up_hist_sub, list.size), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.up_hist_empty), fontSize = 12.sp, color = TextSecondary)
                }
                list.forEachIndexed { i, h ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        h.title.ifBlank { ctx.getString(R.string.up_ver2, h.version) },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        listOf(h.subtitle, ctx.getString(R.string.up_ver2, h.version), h.date)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    lines(h.content).forEach {
                        Text(it, fontSize = 11.sp, color = TextPrimary)
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                stringResource(R.string.up_close),
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
} //更新历史弹窗

/** 检查更新的结果提示：已是最新、更新已关、检查失败都用这张卡，不再在设置页留一行小字 */
@Composable
fun UpdateTipDialog(body: String, onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text(
            stringResource(R.string.se_054),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                stringResource(R.string.up_close),
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
} //检查更新提示弹窗