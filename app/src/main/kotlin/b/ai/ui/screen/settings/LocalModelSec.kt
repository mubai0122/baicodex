package b.ai.ui.screen.settings

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

/** 模型目录：filesDir/models 下所有 .gguf，导入也落这里 */
private fun llmFiles(ctx: Context): List<File> {
    val d = File(ctx.filesDir, "models").apply { mkdirs() }
    return d.listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }?.sortedBy { it.name.lowercase() }
        ?: emptyList()
} //列本地 GGUF

private fun gpuLabel(n: Int): String = when {
    n <= 0 -> "0（纯 CPU）"
    n >= 99 -> "99（全部层）"
    else -> n.toString()
} //GPU 层数的显示名

private fun human(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
} //体积可读化

private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    }
}.getOrNull() //取 SAF 里的原始文件名

private fun sizeOf(ctx: Context, uri: Uri): Long = runCatching {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.SIZE)
        if (i >= 0 && c.moveToFirst()) c.getLong(i) else -1L
    } ?: -1L
}.getOrDefault(-1L) //SAF 里能拿到的大小，拿不到给 -1

/** 从 SAF 拷进模型目录：先查剩余空间，同名自动加序号，不覆盖已有模型 */
private fun importGguf(ctx: Context, uri: Uri): Pair<File?, String> = runCatching {
    val dir = ModelHub.dir(ctx)
    val need = sizeOf(ctx, uri)
    val free = ModelHub.freeBytes(ctx)
    if (need > 0L && free < need + 64L * 1024 * 1024) {
        return@runCatching Pair(null, "空间不足：需要 ${human(need)}，可用 ${human(free)}")
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
private fun LocalModelBody() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var files by remember { mutableStateOf(llmFiles(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("用一句话介绍你自己") }
    var handle by remember { mutableStateOf(0L) }
    var sess by remember { mutableStateOf(0L) }
    var delTarget by remember { mutableStateOf<File?>(null) }
    var dlName by remember { mutableStateOf("") }
    var dlHave by remember { mutableStateOf(0L) }
    var dlTotal by remember { mutableStateOf(-1L) }
    var dlCancel by remember { mutableStateOf(false) }
    val supported = Llama.available()
    var gateLevel by remember { mutableStateOf(0) } //0 不弹，1 偏大（5 秒），2 过大（15 秒）
    var gateLeft by remember { mutableStateOf(0) }
    var gateFile by remember { mutableStateOf<File?>(null) }

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
                    note = "已加载：${f.name}（$desc · 训练上下文 $nctx）"
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
                    "完成：$rc token / ${ms}ms（约 $tps tok/s）"
                } else {
                    "生成失败($rc)：" + err.ifBlank { "未知原因" }
                }
            }
        }
    } //试跑一轮：prompt 走模型自带 chat template

    fun startDownload(m: RecModel) {
        if (busy) return
        val free = ModelHub.freeBytes(ctx)
        if (free < 600L * 1024 * 1024) {
            note = "存储空间不足：可用 ${human(free)}，至少要留 600MB"
            return
        }
        busy = true
        dlName = m.file
        dlHave = 0L
        dlTotal = -1L
        dlCancel = false
        note = "开始下载 ${m.label}"
        scope.launch(Dispatchers.IO) {
            val dst = File(ModelHub.dir(ctx), m.file)
            fun prog(h: Long, t: Long) {
                main.post {
                    dlHave = h
                    dlTotal = t
                }
            }
            var ok = ModelHub.download(m.url, dst, ::prog) { dlCancel }
            //主源失败且不是用户取消，换镜像再试一次；已下部分会接着续
            if (!ok && !dlCancel) ok = ModelHub.download(m.mirror, dst, ::prog) { dlCancel }
            withContext(Dispatchers.Main) {
                busy = false
                dlName = ""
                files = llmFiles(ctx)
                note = when {
                    ok -> "下载完成：${m.file}"
                    dlCancel -> "已取消下载（已下部分保留，下次接着下）"
                    else -> "下载失败：主源与镜像都没成功，检查网络后重试"
                }
            }
        }
    } //下载推荐模型：主源失败自动换镜像

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        note = "导入中…"
        scope.launch(Dispatchers.IO) {
            val (f, why) = importGguf(ctx, uri)
            withContext(Dispatchers.Main) {
                busy = false
                files = llmFiles(ctx)
                note = if (f != null) "已导入：${f.name}（${human(f.length())}）" else why
            }
        }
    } //选 GGUF 并拷进私有目录

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
                if (gateLevel >= 2) "模型过大，加载后手机可能卡到没法用" else "模型偏大，加载后可能卡顿",
                fontSize = 16.sp,
                color = if (gateLevel >= 2) AccentBlue else TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (f != null) "所选模型 ${human(f.length())}" else "所选模型") +
                    (if (mem.total > 0L) "，占本机总内存 ${human(mem.total)} 的 ${(ratio * 100).toInt()}%" else "") +
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
                    "取消",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .tap { gateLevel = 0 }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    if (gateLeft > 0) "请先看完说明（${gateLeft}s）" else "仍然加载",
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
        SectionTitle("本地模型", "GGUF 放在 App 私有目录，纯 CPU 推理，断网也能用")
        CardBox {
            Text(
                if (supported) "推理引擎：已就绪（llama.cpp · CPU）" else "推理引擎：未加载（当前 ABI 没编进来）",
                fontSize = 12.sp,
                color = if (supported) TextPrimary else AccentBlue
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "量化建议 Q4_K_M：7B 约 4.4GB 内存，3B 约 2GB，1.5B 约 1.2GB。手机建议 1.5B～3B。",
                fontSize = 10.sp,
                color = TextSecondary
            )
            val mem = DevMem.info(ctx)
            Spacer(Modifier.height(6.dp))
            Text(
                "设备：${DevMem.soc()}" +
                    (if (mem.total > 0L) " · 内存 ${human(mem.total)}（可用 ${human(mem.avail)}）" else "") +
                    " · 后端 ${if (Cfg.llmLocal) "本地 llama.cpp" else "云端 API"}（${LocalEngine.status()}）",
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(if (busy) "请稍候…" else "导入 GGUF 文件") { if (!busy) pick.launch("*/*") }
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            Text("推荐模型", fontSize = 13.sp, color = TextPrimary)
            Text("主源 ModelScope，失败自动换 hf-mirror；断点续传，中断后点下载会接着下", fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            ModelHub.recs.forEachIndexed { i, m ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                val done = ModelHub.installed(ctx, m)
                val running = dlName == m.file
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label, fontSize = 12.sp, color = TextPrimary)
                        Text(m.note, fontSize = 10.sp, color = TextSecondary)
                    }
                    Text(
                        when {
                            done -> "已下载"
                            running -> "下载中"
                            busy -> "…"
                            else -> "下载"
                        },
                        fontSize = 11.sp,
                        color = if (done) TextSecondary else AccentBlue,
                        modifier = (if (done || busy) Modifier else Modifier.tap { startDownload(m) })
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            if (dlName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val pct = if (dlTotal > 0L) (dlHave * 100 / dlTotal).toInt() else -1
                Text(
                    "正在下载：${human(dlHave)}" + (if (dlTotal > 0L) " / ${human(dlTotal)}（$pct%）" else "（大小未知）"),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                GhostButton("取消下载") { dlCancel = true }
            }
        }

        if (files.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text("还没有模型文件。点上面导入，或用文件管理器把 .gguf 放进 files/models/。", fontSize = 11.sp, color = TextSecondary)
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
                                human(f.length()) + if (Cfg.llmModel == f.absolutePath) " · 当前选中" else "",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            if (busy) "…" else "加载",
                            fontSize = 11.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .tap { if (!busy) loadFile(f) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        Text(
                            "删除",
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
                Text("确认删除 ${f.name}？（${human(f.length())}）", fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row {
                    GhostButton("取消", Modifier.weight(1f)) { delTarget = null }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("删除", Modifier.weight(1f)) {
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
                        files = llmFiles(ctx)
                        delTarget = null
                        note = if (ok) "已删除：${f.name}" else "删除失败：${f.name}"
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            SelectField(
                "对话后端",
                if (Cfg.llmLocal) "本地模型" else "云端 API",
                listOf(true, false),
                desc = "选本地后聊天 / 子 Agent / 上下文总结全走 GGUF；本地没有工具调用能力，会自动降级成纯聊天",
                optionLabel = { if (it) "本地模型" else "云端 API" },
                isSelected = { Cfg.llmLocal == it },
                onSelect = {
                    Cfg.llmLocal = it
                    note = if (it) {
                        if (LocalEngine.route()) "已切到本地模型，下次对话加载 ${File(Cfg.llmModel).name}"
                        else "已切到本地，但当前没有可用模型（先加载一个）"
                    } else {
                        "已切回云端 API"
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "线程数",
                if (Cfg.llmThreads == 0) "自动" else Cfg.llmThreads.toString(),
                listOf(0, 2, 4, 6, 8),
                desc = "自动 = 核心数减 2，最多 6；核越多数值越高越费电",
                optionLabel = { if (it == 0) "自动" else it.toString() },
                isSelected = { Cfg.llmThreads == it },
                onSelect = { Cfg.llmThreads = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "上下文长度",
                Cfg.llmCtx.toString(),
                listOf(1024, 2048, 4096, 8192, 16384),
                desc = "越长越吃内存，超过模型训练上下文会自动压回",
                optionLabel = { it.toString() },
                isSelected = { Cfg.llmCtx == it },
                onSelect = { Cfg.llmCtx = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "温度",
                Cfg.llmTemp.toString(),
                listOf(0f, 0.3f, 0.7f, 0.8f, 1.0f, 1.3f),
                desc = "0 = 每次都给同一个答案；越高越发散",
                optionLabel = { it.toString() },
                isSelected = { kotlin.math.abs(Cfg.llmTemp - it) < 0.01f },
                onSelect = { Cfg.llmTemp = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "top-p",
                Cfg.llmTopP.toString(),
                listOf(0.7f, 0.8f, 0.9f, 0.95f, 1.0f),
                desc = "只从累计概率前 p 的词里挑，越小越稳",
                optionLabel = { it.toString() },
                isSelected = { kotlin.math.abs(Cfg.llmTopP - it) < 0.01f },
                onSelect = { Cfg.llmTopP = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "重复惩罚",
                Cfg.llmRep.toString(),
                listOf(1.0f, 1.05f, 1.1f, 1.2f, 1.3f),
                desc = "1.0 = 关闭；复读机现象明显时调到 1.1～1.2",
                optionLabel = { it.toString() },
                isSelected = { kotlin.math.abs(Cfg.llmRep - it) < 0.01f },
                onSelect = { Cfg.llmRep = it }
            )
            Spacer(Modifier.height(8.dp))
            SelectField(
                "GPU 层数",
                gpuLabel(Cfg.llmGpu),
                listOf(0, 10, 20, 99),
                desc = "仅 arm64 生效，走 OpenCL（Adreno 750 / 8 系已验证）；驱动不支持时自动回退纯 CPU。改完要重新加载模型",
                optionLabel = { gpuLabel(it) },
                isSelected = { Cfg.llmGpu == it },
                onSelect = {
                    Cfg.llmGpu = it
                    note = "GPU 层数已设为 ${gpuLabel(it)}，重新加载模型后生效"
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        CardBox {
            Field(
                label = "试跑",
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "随便问一句",
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Row {
                GhostButton(if (sessionAlive(sess)) "生成" else "先加载模型", Modifier.weight(1f)) {
                    if (sessionAlive(sess) && !busy) runOnce()
                }
                Spacer(Modifier.width(8.dp))
                GhostButton("停止", Modifier.weight(1f)) {
                    if (sess != 0L) Llama.cancel(sess)
                }
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

private fun sessionAlive(c: Long): Boolean = c != 0L //句柄非 0 就当作可用的会话