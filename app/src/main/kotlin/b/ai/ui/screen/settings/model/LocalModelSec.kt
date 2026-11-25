/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:22
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.model
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*

import androidx.compose.ui.res.stringResource
import b.ai.R
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.profile.Profiles
import b.ai.core.llm.DevMem
import b.ai.core.llm.LocalEngine
import b.ai.core.llm.ModelHub
import b.ai.core.llm.RecModel
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.ScrimCard
import b.ai.native.llm.Llama
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.SelectField
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** 模型目录：公共路径 /sdcard/baicode/models 下所有 .gguf，导入与下载也落这里 */
internal fun llmFiles(): List<File> = ModelHub.list()

internal fun gpuLabel(n: Int): String = when {
    n <= 0 -> b.ai.App.self.getString(R.string.tx_4b72c2b7)
    n >= 99 -> b.ai.App.self.getString(R.string.tx_f47f7cb9)
    else -> n.toString()
} //GPU 层数的显示名

internal fun human(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
} //体积可读化

internal fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    }
}.getOrNull() //取 SAF 里的原始文件名

internal fun sizeOf(ctx: Context, uri: Uri): Long = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.SIZE)
        if (i >= 0 && c.moveToFirst()) c.getLong(i) else -1L
    } ?: -1L
}.getOrDefault(-1L) //SAF 里能拿到的大小，拿不到给 -1

/** 从 SAF 拷进模型目录：先查剩余空间，同名自动加序号，不覆盖已有模型 */
internal fun importGguf(ctx: Context, uri: Uri): Pair<File?, String> = runCatching {
    val dir = ModelHub.dir()
    val need = sizeOf(ctx, uri)
    val free = ModelHub.freeBytes()
    if (need > 0L && free < need + 64L * 1024 * 1024) {
        return@runCatching Pair(null, b.ai.App.self.getString(R.string.tx_fee8e550, human(need), human(free)))
    }
    val raw = displayName(ctx, uri)?.takeIf { it.isNotBlank() } ?: "model.gguf"
    val base = if (raw.endsWith(".gguf", true)) raw else "$raw.gguf"
    var out = File(dir, base)
    var i = 1
    while (out.exists()) {
        out = File(dir, base.removeSuffix(".gguf") + "_$i.gguf")
        i++
    }
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { o -> input.copyTo(o, 1 shl 20) }
    } ?: return@runCatching Pair(null, "读不出文件流，可能是权限或文件已被移走")
    Pair(out, "")
}.getOrElse { Pair(null, "导入失败：" + (it.message ?: "未知错误")) } //导入结果：文件名或原因

internal fun LazyListScope.modelLocalSec() {
    item { LocalModelBody() }
} //设置·模型分类下的「本地模型」分区

@Composable
internal fun LocalModelBody() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var files by remember { mutableStateOf(llmFiles()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("用一句话介绍你自己") }
    var customUrl by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf(0L) }
    var sess by remember { mutableStateOf(0L) }
    var delTarget by remember { mutableStateOf<File?>(null) }
    //下载状态在 ModelHub 单例里：关掉设置页再进来，正在下的进度照样在
    val dl by ModelHub.dl.collectAsState()
    val supported = Llama.available()
    var gateLevel by remember { mutableStateOf(0) } //0 不弹，1 偏大（5 秒），2 过大（15 秒）
    var gateLeft by remember { mutableStateOf(0) }
    var gateFile by remember { mutableStateOf<File?>(null) }

    //老版本把模型存在 filesDir/models，进页面先搬一次到公共目录，别让用户重下 4 个 G
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val moved = runCatching { ModelHub.migrate(ctx) }.getOrDefault(emptyList())
            if (moved.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    files = llmFiles()
                    note = b.ai.App.self.getString(R.string.tx_1f6f6b55, moved.size, ModelHub.dir().absolutePath)
                }
            }
        }
    }

    fun doLoad(f: File) {
        if (sess != 0L) Llama.ctxFree(sess)
        if (handle != 0L) Llama.free(handle)
        sess = 0L
        handle = 0L
        busy = true
        note = "加载中…（首次加载大模型要十几秒）"
        out = ""
        scope.launch(Dispatchers.IO) {
            val h = Llama.load(f.absolutePath, Cfg.llmThreads, Cfg.llmGpu)
            val c = if (h != 0L) Llama.ctxNew(h, Cfg.llmCtx) else 0L
            if (h != 0L && c != 0L) Llama.setSampling(c, Cfg.llmTemp, Cfg.llmTopP, 40, Cfg.llmRep, -1)
            val meta = if (h != 0L) Llama.meta(h) else emptyMap()
            val err = Llama.lastErr()
            withContext(Dispatchers.Main) {
                busy = false
                if (h == 0L || c == 0L) {
                    if (h != 0L) Llama.free(h)
                    note = "加载失败：" + err.ifBlank { "未知原因" }
                } else {
                    handle = h
                    sess = c
                    Cfg.llmModel = f.absolutePath
                    val desc = meta["desc"].orEmpty()
                    val nctx = meta["n_ctx_train"] ?: "?"
                    //加载即用：顺手把聊天后端切到这条本地模型，省得用户回聊天页还要去「模型配置」里再选一遍
                    val lbl = runCatching { Profiles.useLocal(f) }.getOrDefault("")
                    note = b.ai.App.self.getString(R.string.tx_0d011d38, f.name) +
                        if (lbl.isNotEmpty()) "\n聊天后端已切到「$lbl」" else ""
                }
            }
        }
    } //加载并建上下文：失败把 native 的原因原样带出来

    /** 加载前先按总内存算占比：超 60% 弹 5 秒、超 90% 弹 15 秒，看完才允许继续 */
    fun loadFile(f: File) {
        val level = DevMem.risk(DevMem.ratio(ctx, f.length()))
        if (level == 0) {
            doLoad(f)
            return
        }
        gateFile = f
        gateLevel = level
    } //带内存风险的加载入口

    fun runOnce() {
        val c = sess
        if (c == 0L) {
            note = "先加载一个模型"
            return
        }
        out = ""
        note = "生成中…"
        val h = handle
        val q = prompt
        scope.launch(Dispatchers.IO) {
            val p = Llama.chat(h, listOf("user" to q), true) ?: q
            val t0 = System.currentTimeMillis()
            val rc = Llama.run(c, p, 128) { t -> main.post { out += t } }
            val ms = System.currentTimeMillis() - t0
            val err = Llama.lastErr()
            withContext(Dispatchers.Main) {
                note = if (rc >= 0) {
                    val tps = if (ms > 0) rc * 1000L / ms else 0L
                    b.ai.App.self.getString(R.string.tx_651eb991, ms)
                } else {
                    "生成失败($rc)：" + when (rc) {
                        -2 -> b.ai.App.self.getString(R.string.tx_76d88dfc)
                        -3 -> b.ai.App.self.getString(R.string.tx_2111ccbb)
                        -5 -> b.ai.App.self.getString(R.string.tx_06e12adc)
                        else -> err.ifBlank { b.ai.App.self.getString(R.string.tx_f91d273b) }
                    }
                }
            }
        }
    } //试跑一轮：prompt 走模型自带 chat template

    //S9.4 文字连通性：固定发「测试测试」，拿到非空回复就算通，顺带报耗时
    fun connTest() {
        val c = sess
        if (c == 0L) {
            note = "先加载一个模型再测"
            return
        }
        out = ""
        note = "连通性测试中…"
        val h = handle
        scope.launch(Dispatchers.IO) {
            val p = Llama.chat(h, listOf("user" to "测试测试"), true) ?: "测试测试"
            val t0 = System.currentTimeMillis()
            val sb = StringBuilder()
            val rc = Llama.run(c, p, 32) { t -> sb.append(t).also { _ -> main.post { out += t } } }
            val ms = System.currentTimeMillis() - t0
            val err = Llama.lastErr()
            withContext(Dispatchers.Main) {
                val txt = sb.toString().trim()
                note = if (rc >= 0 && txt.isNotEmpty()) {
                    val tps = if (ms > 0) rc * 1000L / ms else 0L
                    b.ai.App.self.getString(R.string.tx_8164359b, ms, txt.length, txt.take(40))
                } else {
                    "连通性测试失败($rc)：" + when (rc) {
                        -5 -> b.ai.App.self.getString(R.string.tx_06e12adc)
                        else -> err.ifBlank { b.ai.App.self.getString(R.string.tx_3066b700) }
                    }
                }
            }
        }
    } //S9.4 文字连通性测试

    /** 下载到模型目录：url 必填，mirror 可选（推荐模型才有备源），file 是落盘文件名 */
    fun downloadTo(url: String, mirror: String?, file: String, label: String) {
        if (dl.running) {
            note = "已有下载在进行，先等它下完或取消"
            return
        }
        val free = ModelHub.freeBytes()
        if (free < 300L * 1024 * 1024) {
            note = b.ai.App.self.getString(R.string.tx_9fc79d7a, human(free))
            return
        }
        note = ""
        ModelHub.startDownload(url, mirror, file, label)
    } //下载模型：主源失败自动换镜像

    fun startDownload(m: RecModel) = downloadTo(m.url, m.mirror, m.file, m.label) //下载推荐模型

    //下完（含失败、取消）刷新一次文件列表，顺带把结果显示到 note
    LaunchedEffect(dl.running, dl.msg) {
        if (!dl.running) {
            files = llmFiles()
            if (dl.msg.isNotBlank()) note = dl.msg
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        note = "导入中…"
        scope.launch(Dispatchers.IO) {
            val (f, why) = importGguf(ctx, uri)
            withContext(Dispatchers.Main) {
                busy = false
                files = llmFiles()
                note = if (f != null) b.ai.App.self.getString(R.string.tx_cbdacbb2, f.name, human(f.length())) else why
            }
        }
    } //选 GGUF 并拷进模型目录

    //倒计时读完才允许点「仍然加载」，逼着把风险说明看完
    LaunchedEffect(gateLevel) {
        if (gateLevel == 0) return@LaunchedEffect
        gateLeft = if (gateLevel >= 2) 15 else 5
        while (gateLeft > 0) {
            delay(1000)
            gateLeft -= 1
        }
    }

    HostInOverlay(gateLevel != 0) {
        val f = gateFile
        val mem = DevMem.info(ctx)
        val ratio = if (mem.total > 0L && f != null) f.length().toDouble() / mem.total else 0.0
        ScrimCard(onDismiss = { gateLevel = 0 }) {
            Text(
                if (gateLevel >= 2) stringResource(R.string.tx_b7f18f1e) else stringResource(R.string.tx_aa76a23a),
                fontSize = 16.sp,
                color = if (gateLevel >= 2) AccentBlue else TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (f != null) stringResource(R.string.tx_f934698e, human(f.length())) else stringResource(R.string.tx_20b2103b)) +
                    (if (mem.total > 0L) stringResource(R.string.tx_957699dd, human(mem.total), (ratio * 100).toInt()) else "") +
                    "。\n\n" +
                    if (gateLevel >= 2) {
                        "这个体积已经超过总内存的 90%：加载时会长时间无响应，之后后台应用会被系统杀掉、输入法与动画掉帧，最坏情况是 App 被 OOM 直接结束、手机短时间无法操作。\n\n强烈建议改用 1.5B 或 3B 的 Q4_K_M 量化。"
                    } else {
                        "超过总内存的 60%：加载后可用内存会明显减少，可能出现后台应用被杀、切换应用重载、输入法掉帧。\n\n建议优先选更小的量化或更小的参数量。"
                    },
                fontSize = 12.sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.tx_625fb26b),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .tap { gateLevel = 0 }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    if (gateLeft > 0) stringResource(R.string.tx_45b8d0ba, gateLeft) else stringResource(R.string.tx_7fae055f),
                    fontSize = 13.sp,
                    color = if (gateLeft > 0) TextSecondary else AccentBlue,
                    modifier = Modifier
                        .tap {
                            if (gateLeft == 0) {
                                val g = gateFile
                                gateLevel = 0
                                gateFile = null
                                if (g != null) doLoad(g)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    Column {
        SectionTitle(stringResource(R.string.tx_0d22d46a), stringResource(R.string.tx_a9997318) + ModelHub.dir().absolutePath + "，断网也能用")
        CardBox {
            Text(
                if (supported) {
                    "推理引擎：已就绪（llama.cpp · " + (if (Cfg.llmGpu > 0) stringResource(R.string.tx_fe42d9b6, Cfg.llmGpu) else stringResource(R.string.tx_bc083719)) + "）"
                } else {
                    "推理引擎：不可用 · " + Llama.loadError().ifBlank { "当前 ABI 没编进来" }
                },
                fontSize = 12.sp,
                color = if (supported) TextPrimary else AccentBlue
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tx_b6e53767, ModelHub.dir().absolutePath) + (if (ModelHub.isPublic()) stringResource(R.string.tx_13968e19) else stringResource(R.string.tx_fc8f9040)),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tx_bebd3740),
                fontSize = 10.sp,
                color = TextSecondary
            )
            val mem = DevMem.info(ctx)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.tx_a33a9bb0, DevMem.soc()) +
                    (if (mem.total > 0L) stringResource(R.string.tx_3108ce07, human(mem.total), human(mem.avail)) else "") +
                    " · 后端 ${if (Cfg.llmLocal) "本地 llama.cpp" else "云端 API"}（${LocalEngine.status()}）",
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(if (busy) stringResource(R.string.tx_5ec38d72) else stringResource(R.string.tx_8174c488)) { if (!busy) pick.launch("*/*") }
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            Text(stringResource(R.string.tx_40f0918b), fontSize = 13.sp, color = TextPrimary)
            Text(stringResource(R.string.tx_8194ea1b), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            ModelHub.recs.forEachIndexed { i, m ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                val done = ModelHub.installed(m)
                val running = dl.running && dl.file == m.file
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label, fontSize = 12.sp, color = TextPrimary)
                        Text(m.note, fontSize = 10.sp, color = TextSecondary)
                    }
                    Text(
                        when {
                            done -> stringResource(R.string.tx_9719fec8)
                            running -> stringResource(R.string.tx_2d455ce5)
                            dl.running -> "…"
                            else -> stringResource(R.string.tx_f26ef914)
                        },
                        fontSize = 11.sp,
                        color = if (done) TextSecondary else AccentBlue,
                        modifier = (if (done || dl.running) Modifier else Modifier.tap { startDownload(m) })
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            if (dl.running && dl.file.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val pct = if (dl.total > 0L) (dl.have * 100 / dl.total).toInt() else -1
                Text(
                    stringResource(R.string.tx_66a69bbf, dl.file) + human(dl.have) +
                        (if (dl.total > 0L) " / ${human(dl.total)}（$pct%）" else stringResource(R.string.tx_73a8747c)),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                GhostButton(stringResource(R.string.tx_84fac467)) { ModelHub.cancelDownload() }
            }
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            Text(stringResource(R.string.tx_a8f37df4), fontSize = 13.sp, color = TextPrimary)
            Text(
                stringResource(R.string.tx_fb085f04),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Field(
                label = stringResource(R.string.tx_bfe68d58),
                value = customUrl,
                onValueChange = { customUrl = it },
                placeholder = "https://…/xxx.gguf"
            )
            if (customUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.tx_697e775f, ModelHub.nameOf(customUrl)), fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(if (busy) stringResource(R.string.tx_5ec38d72) else stringResource(R.string.tx_639b6f4b)) {
                val u = customUrl.trim()
                when {
                    busy -> Unit
                    !u.startsWith("http://") && !u.startsWith("https://") -> note = b.ai.App.self.getString(R.string.tx_0cac8c4c)
                    else -> {
                        val n = ModelHub.nameOf(u)
                        downloadTo(u, null, n, n)
                    }
                }
            }
        }

        if (files.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text(
                    stringResource(R.string.tx_960ae310, ModelHub.dir().absolutePath),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            CardBox {
                files.forEachIndexed { i, f ->
                    if (i > 0) Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontSize = 13.sp, color = TextPrimary)
                            Text(
                                human(f.length()) + if (Cfg.llmModel == f.absolutePath) stringResource(R.string.tx_2165b30a) else "",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            if (busy) "…" else stringResource(R.string.tx_4793ea43),
                            fontSize = 11.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .tap { if (!busy) loadFile(f) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        Text(
                            stringResource(R.string.tx_2f4aaddd),
                            fontSize = 11.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .tap { delTarget = f }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        //删除要二次确认：模型文件动辄几个 G，误点一次就得重新下
        delTarget?.let { f ->
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text(stringResource(R.string.tx_67957ccd, f.name, human(f.length())), fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row {
                    GhostButton(stringResource(R.string.tx_625fb26b), Modifier.weight(1f)) { delTarget = null }
                    Spacer(Modifier.width(8.dp))
                    GhostButton(stringResource(R.string.tx_2f4aaddd), Modifier.weight(1f)) {
                        if (Cfg.llmModel == f.absolutePath) Cfg.llmModel = ""
                        if (sess != 0L) {
                            Llama.ctxFree(sess)
                            sess = 0L
                        }
                        if (handle != 0L) {
                            Llama.free(handle)
                            handle = 0L
                        }
                        val ok = f.delete()
                        files = llmFiles()
                        delTarget = null
                        note = if (ok) b.ai.App.self.getString(R.string.tx_642e64e8, f.name) else b.ai.App.self.getString(R.string.tx_f0c678c7, f.name)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            //后端只由「模型配置」决定（选本地模型那条就本地，选云端厂商就云端），这里只显示状态，避免两处开关打架
            Text(
                "对话后端：" + if (Cfg.llmLocal) stringResource(R.string.tx_0d22d46a) else stringResource(R.string.tx_040a63aa),
                fontSize = 13.sp,
                color = TextPrimary
            )
            Text(
                stringResource(R.string.tx_08e0d546),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_aadb83b2),
                if (Cfg.llmThreads == 0) stringResource(R.string.tx_3aed2c11) else Cfg.llmThreads.toString(),
                listOf(0, 2, 4, 6, 8),
                desc = stringResource(R.string.tx_cd08d0ab),
                optionLabel = { if (it == 0) stringResource(R.string.tx_3aed2c11) else it.toString() },
                isSelected = { Cfg.llmThreads == it },
                onSelect = { Cfg.llmThreads = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_3b566354),
                Cfg.llmCtx.toString(),
                listOf(1024, 2048, 4096, 8192, 16384),
                desc = stringResource(R.string.tx_57a3a77c),
                optionLabel = { it.toString() },
                isSelected = { Cfg.llmCtx == it },
                onSelect = { Cfg.llmCtx = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_c9bf0b88),
                Cfg.llmTemp.toString(),
                listOf(0f, 0.3f, 0.7f, 0.8f, 1.0f, 1.3f),
                desc = stringResource(R.string.tx_2bd34d47),
                optionLabel = { it.toString() },
                isSelected = { kotlin.math.abs(Cfg.llmTemp - it) < 0.01f },
                onSelect = { Cfg.llmTemp = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "top-p",
                Cfg.llmTopP.toString(),
                listOf(0.7f, 0.8f, 0.9f, 0.95f, 1.0f),
                desc = stringResource(R.string.tx_f6c58938),
                optionLabel = { it.toString() },
                isSelected = { kotlin.math.abs(Cfg.llmTopP - it) < 0.01f },
                onSelect = { Cfg.llmTopP = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_43a45e02),
                Cfg.llmRep.toString(),
                listOf(1.0f, 1.05f, 1.1f, 1.2f, 1.3f),
                desc = stringResource(R.string.tx_da9de6ce),
                optionLabel = { it.toString() },
                isSelected = { kotlin.math.abs(Cfg.llmRep - it) < 0.01f },
                onSelect = { Cfg.llmRep = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_bec4568c),
                gpuLabel(Cfg.llmGpu),
                listOf(0, 10, 20, 99),
                desc = stringResource(R.string.tx_02cd23ff),
                optionLabel = { gpuLabel(it) },
                isSelected = { Cfg.llmGpu == it },
                onSelect = {
                    Cfg.llmGpu = it
                    note = b.ai.App.self.getString(R.string.tx_3c1c4b90, gpuLabel(it))
                }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                stringResource(R.string.tx_0976ccb2),
                Cfg.llmMaxTok.toString(),
                listOf(256, 512, 1024, 2048),
                desc = stringResource(R.string.tx_fed34fcc),
                optionLabel = { "$it token" },
                isSelected = { Cfg.llmMaxTok == it },
                onSelect = { Cfg.llmMaxTok = it }
            )
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            Field(
                label = stringResource(R.string.tx_42824410),
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = stringResource(R.string.tx_a1faddfd),
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Row {
                GhostButton(if (sessionAlive(sess)) stringResource(R.string.tx_4dfe7036) else stringResource(R.string.tx_ba2a95b6), Modifier.weight(1f)) {
                    if (sessionAlive(sess) && !busy) runOnce()
                }
                Spacer(Modifier.width(8.dp))
                GhostButton(stringResource(R.string.tx_095e938e), Modifier.weight(1f)) {
                    if (sess != 0L) Llama.cancel(sess)
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(if (sessionAlive(sess)) stringResource(R.string.tx_4d4c3fd6) else stringResource(R.string.tx_433fd817), Modifier.fillMaxWidth()) {
                if (sessionAlive(sess) || handle != 0L) {
                    if (sess != 0L) Llama.ctxFree(sess)
                    if (handle != 0L) Llama.free(handle)
                    sess = 0L
                    handle = 0L
                    LocalEngine.release() //对话链路那份句柄一起收掉，不然内存还是占着
                    out = ""
                    note = "已卸载模型，内存已释放"
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(if (sessionAlive(sess)) stringResource(R.string.tx_fc960862) else stringResource(R.string.tx_ba2a95b6), Modifier.fillMaxWidth()) {
                if (sessionAlive(sess) && !busy) connTest()
            }
            if (out.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(out, fontSize = 12.sp, color = TextPrimary)
            }
        }

        if (note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            CardBox { Text(note, fontSize = 11.sp, color = TextSecondary) }
        }
    }
} //本地模型设置页：导入 / 列表 / 参数 / 试跑

internal fun sessionAlive(c: Long): Boolean = c != 0L //句柄非 0 就当作可用的会话