/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:24
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings
import b.ai.core.data.io.Layout
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.widget.tap
import b.ai.net.api.llm.text

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import b.ai.core.misc.apk.ApkInstall
import b.ai.net.api.update.Update
import b.ai.net.api.update.Update.pickAbi
import b.ai.net.api.update.UpdateMirror
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
    //按本机 ABI 选包：接口给了 abis 就用对应架构的直链与指纹，没给就退回 downloadDirectUrl
    val picked = remember(info) { info.pickAbi() }
    val direct = picked?.url?.takeIf { it.isNotBlank() } ?: info.downloadDirectUrl
    val jump = info.downloadJumpUrl
    //服务端给的包指纹：manifest / abis / 顶层三级兜底，取第一个非空的
    val pkgSha = picked?.sha256?.ifBlank { null } ?: info.sha256
    //本地缓存名带 ABI：两种架构的包不能互相覆盖，否则装上去就是「应用未安装」
    val pkgName = remember(info, picked) { "bai-${info.latestCode}-${picked?.abi ?: "any"}.apk" }
    //阶段：0 未开始 / 1 拉清单并测速选镜像 / 2 下载 / 3 已下完、正在装
    var stage by remember { mutableStateOf(0) }
    //测速进度：按「已出结果的节点数 / 总节点数」推进，与下载进度互不干扰
    var probePct by remember { mutableStateOf(0) }
    var probeMsg by remember { mutableStateOf("") }
    //下载进度：字节口径，既能算百分比也能显示 MB 与速度
    var dlDone by remember { mutableStateOf(0L) }
    var dlTotal by remember { mutableStateOf(0L) }
    var dlSpeed by remember { mutableStateOf(0L) }
    var msg by remember { mutableStateOf("") }
    val main = remember { Handler(Looper.getMainLooper()) }
    val busy = stage in 1..3

    /** 本地已下好且指纹对得上的包：权限页来回一趟、弹窗关掉重开都不该重下 */
    fun cachedCopy(): File? {
        val f = File(Layout.dir(ctx, Layout.APK), pkgName)
        if (!f.isFile || f.length() <= 0) return null
        //服务端给了 sha256 就按它验，没给就看上次下载成功时留下的旁注
        val want = pkgSha.ifBlank {
            runCatching { File(f.absolutePath + ".sha").readText().trim() }.getOrDefault("")
        }.lowercase()
        if (want.length != 64) return null
        return if (Update.sha256(f) == want) f else null
    } //可复用的本地包

    /** 交给安装器：有 root / Shizuku 就静默装，没有才拉系统安装器 */
    suspend fun installNow(f: File) {
        stage = 3
        msg = ctx.getString(R.string.up_installer)
        val r = withContext(Dispatchers.IO) { ApkInstall.install(ctx, f) }
        msg = r.msg
        if (!r.ok) stage = 0 //装不上就复位，允许用户重试
    } //装包

    /** 拉清单 → 测速选镜像 → 下载 → 校验 → 装包；节点失败自动换下一个 */
    fun startDownload() {
        if (busy) return
        stage = 1
        probePct = 0
        probeMsg = ctx.getString(R.string.up_probe_fetch)
        msg = ctx.getString(R.string.up_startdl)
        scope.launch {
            //本地有完整包：直接装，不重新下载
            val ready = withContext(Dispatchers.IO) { runCatching { cachedCopy() }.getOrNull() }
            if (ready != null) {
                msg = ctx.getString(R.string.up_reuse, ready.length() / 1024 / 1024)
                installNow(ready)
                return@launch
            }
            val dest = File(Layout.dir(ctx, Layout.APK), pkgName)
            //节点全部由服务端清单下发，客户端不硬编码任何镜像地址；按本机 ABI 取对应那一块
            val manifest = withContext(Dispatchers.IO) { UpdateMirror.fetchManifest(abi = picked?.abi) }
            val nodes = manifest?.nodes ?: direct.takeIf { it.isNotBlank() }
                ?.let { listOf(UpdateMirror.Node("direct", "官方直链", it)) }
                .orEmpty()
            if (nodes.isEmpty()) {
                stage = 0
                msg = ctx.getString(R.string.up_no_mirror, UpdateMirror.lastError.ifBlank { "清单为空" })
                return@launch
            }
            if (manifest == null) msg = ctx.getString(R.string.up_probe_fallback, UpdateMirror.lastError)
            probeMsg = ctx.getString(R.string.up_probe_start, nodes.size)

            //并行测速：每出一个结果就推进一次进度条，界面始终知道在测哪个节点
            val probes = withContext(Dispatchers.IO) {
                UpdateMirror.probeAll(nodes) { p, done, total ->
                    main.post {
                        probePct = (done * 100 / total.coerceAtLeast(1)).coerceIn(0, 100)
                        probeMsg = ctx.getString(
                            R.string.up_probe_one,
                            done, total, p.node.name,
                            if (p.ok) "TTFB ${p.ttfbMs}ms / ${p.speedText()}" else b.ai.App.self.getString(R.string.tx_d1e4a7e9)
                        )
                    }
                }
            }
            probePct = 100
            val best = UpdateMirror.pick(probes)
            if (best == null && manifest != null) msg = ctx.getString(R.string.up_probe_none)

            //下载顺序：择优优先，其余可用节点依次兜底；最多试 3 个
            val queue = UpdateMirror.order(probes).filter { it.ok }.map { it.node }
                .ifEmpty { nodes }.take(3)
            stage = 2
            dlDone = 0L
            dlTotal = manifest?.meta?.size?.takeIf { it > 0 } ?: picked?.size ?: 0L
            var pkg: File? = null
            for ((i, node) in queue.withIndex()) {
                msg = ctx.getString(R.string.up_node, node.name, i + 1, queue.size)
                dlDone = 0L
                dlSpeed = 0L
                val r = withContext(Dispatchers.IO) {
                    runCatching {
                        var lastT = 0L
                        var lastD = 0L
                        UpdateMirror.download(node.url, dest) { done, total ->
                            val now = System.currentTimeMillis()
                            //400ms 节流：每个 64KB 都刷 UI 会拖慢下载
                            if (now - lastT >= 400) {
                                val spd = if (lastT > 0) (done - lastD) * 1000 / (now - lastT) else 0L
                                lastT = now
                                lastD = done
                                main.post {
                                    dlDone = done
                                    if (total > 0) dlTotal = total
                                    dlSpeed = spd
                                }
                            }
                        }
                    }
                }
                val f = r.getOrNull()
                if (f != null) {
                    pkg = f
                    break
                }
                msg = ctx.getString(
                    R.string.up_node_fail, node.name,
                    r.exceptionOrNull()?.message?.take(60) ?: ""
                )
            }
            val file = pkg
            if (file == null) {
                stage = 0
                return@launch
            }
            if (dlTotal > 0) dlDone = dlTotal

            //指纹校验：服务端给了 sha256 就必须对上，对不上说明包坏了（本机 ABI 的包优先）
            val want = manifest?.meta?.sha256.orEmpty().ifBlank { pkgSha }
            val sum = withContext(Dispatchers.IO) { runCatching { Update.sha256(file) }.getOrDefault("") }
            if (want.isNotBlank() && !sum.equals(want, ignoreCase = true)) {
                runCatching { file.delete() }
                msg = ctx.getString(R.string.up_bad_pkg)
                stage = 0
                return@launch
            }
            if (sum.isNotBlank()) withContext(Dispatchers.IO) {
                runCatching { File(file.absolutePath + ".sha").writeText(sum) }
            }
            msg = ctx.getString(R.string.up_done, file.length() / 1024 / 1024)
            installNow(file)
        }
    } //拉清单+测速+下载+装包

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
                info.updateDate,
                //本机自动选中的包：让用户知道下的是哪个架构，出问题好排查
                picked?.abi?.let { "$it 包" } ?: ""
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
                .verticalScroll(glassScrollState())
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
        //第一条：测速选镜像（0→100 表示已测完的节点比例）
        if (stage >= 1) {
            UpBar(
                title = probeMsg.ifBlank { ctx.getString(R.string.up_probe_fetch) },
                pct = probePct
            )
        }
        //第二条：下载（字节口径，单独一条，不与测速混用）
        if (stage >= 2) {
            val pct = if (dlTotal > 0) (dlDone * 100 / dlTotal).toInt().coerceIn(0, 100) else -1
            UpBar(
                title = ctx.getString(
                    R.string.up_dl_title,
                    sizeText(dlDone),
                    if (dlTotal > 0) sizeText(dlTotal) else "?",
                    if (dlSpeed > 0) speedText(dlSpeed) else "…"
                ),
                pct = pct
            )
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
        //下载/安装期间（stage 1..3）隐藏「下载页」次按钮：此时它点不了，还白占 1/3 宽把主按钮挤扁
        val running = stage in 1..3
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                //直链优先：下完自动拉起安装器，不需要用户再找包
                direct.isNotBlank() -> {
                    Text(
                        when (stage) {
                            1 -> ctx.getString(R.string.up_btn_probe, probePct)
                            2 -> if (dlTotal > 0) ctx.getString(
                                R.string.up_downloading,
                                (dlDone * 100 / dlTotal).toInt().coerceIn(0, 100)
                            ) else ctx.getString(R.string.up_prep)
                            //安装阶段只给固定短文案：msg 里是「下载完成，共 16 MB」「已交给系统安装器」这类长句，
                            //塞进 1/3 宽按钮会换行把三按钮全部挤压，完整说明留在上方那行 msg
                            3 -> ctx.getString(R.string.up_installer)
                            else -> ctx.getString(R.string.up_dl_install)
                        },
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1, //单行：宁可省略也不能把按钮撑高
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
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
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
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
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                        .tap { (ctx as? Activity)?.finishAffinity() }
                        .padding(vertical = 11.dp)
                )
            }
            //下载/安装中不显示下载页按钮，给主按钮与「稍后再说」留足宽度
            if (direct.isNotBlank() && jump.isNotBlank() && !running) {
                Text(
                    ctx.getString(R.string.up_page),
                    fontSize = 12.sp,
                    color = AccentBlue,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
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
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                        .tap { onClose() }
                        .padding(vertical = 11.dp)
                )
            }
        }
    }
} //检查更新弹窗

/** 一条细进度条 + 一行说明：测速与下载各用一条，互不干扰 */
@Composable
private fun UpBar(title: String, pct: Int) {
    Text(title, fontSize = 11.sp, color = TextSecondary)
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .liquidBg(RoundedCornerShape(3.dp), tint = SurfaceMuted)
    ) {
        //未知总长（pct < 0）时只画一小段，表示在动但不装作知道进度
        val f = if (pct < 0) 0.12f else (pct.coerceIn(0, 100).coerceAtLeast(2)) / 100f
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(6.dp)
                .liquidBg(RoundedCornerShape(3.dp), tint = AccentBlue)
        )
    }
    Spacer(Modifier.height(6.dp))
} //进度条

/** 字节数转人看的体积 */
private fun sizeText(b: Long): String = when {
    b <= 0 -> "0 KB"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1024.0 / 1024.0)
} //体积文本

/** 速度转人看的字符串 */
private fun speedText(bps: Long): String = when {
    bps <= 0 -> "0 KB/s"
    bps < 1024 * 1024 -> "${bps / 1024} KB/s"
    else -> String.format("%.1f MB/s", bps / 1024.0 / 1024.0)
} //速度文本

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
                .verticalScroll(glassScrollState())
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