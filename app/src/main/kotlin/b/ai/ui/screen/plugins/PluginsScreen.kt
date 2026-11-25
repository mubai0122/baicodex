/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：抽屉里的「插件」页（全屏）—— dsh 插件的三个入口：
 *  ① 已装：本机真实装载行 + 开关（复用 DshInstalledContent，与设置页同一份实现）；
 *  ② 广场：npm registry（keywords:dsh-plugin）+ 用户自填 market index URL 双数据源；
 *  ③ 创建：在本机生成骨架插件并安装（本地 file: 依赖）。
 *
 * 安装 / 卸载 / 创建都要二次确认；失败自动回滚（见 DshInstall）。
 * 安装过程有阶段进度条（DshInstall 的 onStep 回调 → 确定式进度），失败转红。
 * 底部导航用 FloatTabBar（与设置页同一套液态玻璃底栏），不再用顶部一排按钮。
 * 所有写盘与网络都不在 UI 线程。
 */
package b.ai.ui.screen.plugins

import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.dsh.DshInstall
import b.ai.core.dsh.DshMarket
import b.ai.core.dsh.MarketItem
import b.ai.ui.glass.GlassPerf
import b.ai.ui.glass.ProvideCardBackdrop
import b.ai.ui.glass.backdropPageInset
import b.ai.ui.glass.drawPageBg
import b.ai.ui.glass.glassPageLayer
import b.ai.ui.glass.pageBg
import b.ai.ui.screen.settings.agent.DshInstalledContent
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.Field
import b.ai.ui.widget.FloatTab
import b.ai.ui.widget.FloatTabBar
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 三个页签（与设置页同一套 FloatTab：图标 + 文案） */
private val PluginTabs = listOf(
    FloatTab(R.string.plg_tab_installed) { c -> Icon(Icons.Filled.CheckCircle, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.plg_tab_market) { c -> Icon(Icons.Filled.ShoppingCart, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.plg_tab_create) { c -> Icon(Icons.Filled.Create, null, tint = c, modifier = Modifier.size(18.dp)) }
) //插件分类：已装 / 广场 / 创建（索引 0..2 与内容区 when 对应）

/**
 * 插件页：抽屉「插件」入口。
 * @param onBack 返回聊天页
 */
@Composable
fun PluginsScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    //索引地址只用 SharedPreferences 存，不塞进 Cfg（Cfg 字段多，加字段收益不大）
    val sp = remember { ctx.getSharedPreferences(DshMarket.PREF, Context.MODE_PRIVATE) }

    var tab by remember { mutableIntStateOf(0) }
    var installToken by remember { mutableStateOf(0) }   //安装/卸载后 +1，让已装列表重读
    var needRestart by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }           //安装日志（滚动区内显示）
    var busy by remember { mutableStateOf(false) }       //有任务在跑，禁用按钮
    var pending by remember { mutableStateOf("") }       //待确认的安装 spec（二次确认用）
    var pendingTitle by remember { mutableStateOf("") }

    //安装进度：DshInstall.onStep 回调写入（step 已完成的段数 / total 总段数 / label 当前动作）
    var pStep by remember { mutableIntStateOf(0) }
    var pTotal by remember { mutableIntStateOf(0) }
    var pLabel by remember { mutableStateOf("") }
    var pFail by remember { mutableStateOf(false) }      //失败把进度条转红

    //广场状态
    var query by remember { mutableStateOf(DshMarket.DEFAULT_QUERY) }
    var results by remember { mutableStateOf<List<MarketItem>>(emptyList()) }
    var marketErr by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var indexUrl by remember { mutableStateOf(sp.getString(DshMarket.KEY_INDEXES, "").orEmpty()) }

    //创建状态
    var cId by remember { mutableStateOf("") }
    var cTitle by remember { mutableStateOf("") }
    var cPkg by remember { mutableStateOf("") }

    /** 进度归零（开始新任务或清空日志时调用） */
    fun resetProgress() {
        pStep = 0
        pTotal = 0
        pLabel = ""
        pFail = false
    } //复位进度

    /** 真正执行安装（已确认过） */
    fun runInstall(spec: String) {
        if (busy) return
        busy = true
        log = ""
        resetProgress()
        pTotal = 3   //与 DshInstall.ADD_STEPS 保持一致：准备 pnpm / 安装依赖 / 回滚收尾
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                DshInstall.add(
                    spec,
                    onLog = { line -> log = (log + line + "\n").takeLast(4000) },
                    onStep = { s, t, label -> pStep = s; pTotal = t; pLabel = label }
                )
            }
            log = (log + "—— ${r.message}\n").takeLast(4000)
            pFail = !r.ok
            if (!r.ok) pLabel = r.message
            busy = false
            if (r.ok) {
                installToken++
                needRestart = true
            }
        }
    } //执行安装

    /** 真正执行创建（已确认过） */
    fun runCreate() {
        if (busy) return
        busy = true
        log = ""
        resetProgress()
        pTotal = 4   //与 DshInstall.CREATE_STEPS 保持一致：写骨架 + 安装三段
        val id = cId.trim()
        val title = cTitle.trim()
        val pkg = cPkg.trim()
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                DshInstall.create(
                    id,
                    title,
                    pkg,
                    onLog = { line -> log = (log + line + "\n").takeLast(4000) },
                    onStep = { s, t, label -> pStep = s; pTotal = t; pLabel = label }
                )
            }
            val msg = r.fold({ "—— 已创建并安装：$it" }, { "—— 创建失败：${it.message}" })
            log = (log + msg + "\n").takeLast(4000)
            pFail = r.isFailure
            if (r.isFailure) pLabel = r.exceptionOrNull()?.message ?: "创建失败"
            busy = false
            if (r.isSuccess) {
                installToken++
                needRestart = true
            }
        }
    } //执行创建

    //页面层：底栏玻璃采样这一层（先垫底色，再画页面背景与内容）
    val (pageTop, pageBottom) = backdropPageInset()
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawPageBg(offsetY = -pageTop, fullHeight = size.height + pageTop + pageBottom)
        drawContent()
    }
    //卡片玻璃只采纯背景那一层：卡片自己也在 pageBackdrop 里，采到的会是上一帧的自己
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }

    //结构与设置页完全同构：背景层 + 内容层（glassPageLayer）+ 悬浮底栏（采样 pageBackdrop）
    ProvideCardBackdrop(cardBackdrop) {
    Box(Modifier.fillMaxSize()) {
        //背景单开一层给卡片取样；内容再套一层吃系统栏内边距
        Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassPageLayer(pageBackdrop)
        ) {
            //顶栏：标题 + 返回
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.dh_plug_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dh_plug_sub), fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                GhostButton(stringResource(R.string.dh_back), Modifier.height(32.dp)) { if (!busy) onBack() }
            }

            if (tab == 0 && needRestart) {
                Text(
                    stringResource(R.string.dh_need_restart),
                    fontSize = 10.sp,
                    color = AccentBlue,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }

            //内容区（底部留白给悬浮底栏，最后一行不会被盖住）
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                //安装/创建进度：有任务或刚结束时显示；失败转红
                if (pTotal > 0) {
                    InstallProgress(
                        label = pLabel,
                        step = pStep,
                        total = pTotal,
                        failed = pFail,
                        running = busy
                    )
                    Spacer(Modifier.height(10.dp))
                }

                when (tab) {
                    0 -> DshInstalledContent(installToken) { needRestart = it }

                    1 -> {
                        //确认条：只有点了「安装」且还没确认时才出现
                        if (pending.isNotBlank()) {
                            ConfirmBox(
                                title = ctx.getString(R.string.dh_confirm_install_title, pendingTitle.ifBlank { pending }),
                                body = ctx.getString(R.string.dh_confirm_install_body, pending),
                                okText = ctx.getString(R.string.dh_confirm_install_ok),
                                onOk = { val s = pending; pending = ""; runInstall(s) },
                                onCancel = { pending = "" }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        //① npm 搜索
                        Field(stringResource(R.string.dh_search_npm), query, { query = it })
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PrimaryButton(stringResource(R.string.dh_search), enabled = !searching && !busy) {
                                searching = true
                                marketErr = ""
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { DshMarket.searchNpm(query) }
                                    r.onSuccess { results = it; marketErr = "" }
                                        .onFailure { marketErr = it.message ?: ctx.getString(R.string.dh_search_failed) }
                                    searching = false
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (searching) ctx.getString(R.string.dh_searching) else if (marketErr.isBlank()) ctx.getString(R.string.dh_results_count, results.size) else marketErr,
                                fontSize = 10.sp,
                                color = if (marketErr.isBlank()) TextSecondary else DangerRed
                            )
                        }

                        //② 用户自填索引
                        Spacer(Modifier.height(14.dp))
                        Field(
                            stringResource(R.string.dh_market_url),
                            indexUrl,
                            { indexUrl = it },
                            placeholder = "https://example.com/index.json",
                            singleLine = false,
                            minLines = 2
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GhostButton(stringResource(R.string.dh_read_index), Modifier.height(34.dp)) {
                                val urls = indexUrl.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                                if (urls.isEmpty()) {
                                    marketErr = ctx.getString(R.string.dh_index_required)
                                } else {
                                    sp.edit().putString(DshMarket.KEY_INDEXES, indexUrl).apply()
                                    searching = true
                                    marketErr = ""
                                    scope.launch {
                                        val merged = LinkedHashMap<String, MarketItem>()
                                        withContext(Dispatchers.IO) {
                                            urls.forEach { u ->
                                                DshMarket.fetchIndex(u).onSuccess { list ->
                                                    list.forEach { merged.putIfAbsent(it.name, it) }
                                                }.onFailure { marketErr = "$u：${it.message}" }
                                            }
                                        }
                                        results = merged.values.toList()
                                        searching = false
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.dh_index_hint),
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }

                        //③ 结果列表
                        if (results.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            results.forEach { item ->
                                CardBox(Modifier.padding(bottom = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                            if (item.desc.isNotBlank()) {
                                                Text(item.desc, fontSize = 10.sp, color = TextSecondary)
                                            }
                                            Text(
                                                buildString {
                                                    if (item.version.isNotBlank()) append("v${item.version}  ·  ")
                                                    if (item.author.isNotBlank()) append(item.author).append("  ·  ")
                                                    append(item.source)
                                                },
                                                fontSize = 9.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        GhostButton(if (item.version.isBlank()) stringResource(R.string.dh_install) else stringResource(R.string.dh_install_with_ver, item.version), Modifier.height(32.dp)) {
                                            if (!busy) {
                                                pending = if (item.version.isBlank()) item.name else "${item.name}@${item.version}"
                                                pendingTitle = item.title
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        //创建页：只生成能被 dsh 装载的空插件，工具实现留给用户
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dh_create_desc),
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Field(stringResource(R.string.dh_create_id), cId, { cId = it }, placeholder = "my-tool")
                        Field(stringResource(R.string.dh_create_title), cTitle, { cTitle = it }, placeholder = stringResource(R.string.dh_create_title_ph))
                        Field(stringResource(R.string.dh_create_pkg), cPkg, { cPkg = it }, placeholder = "bxl-plugin-my-tool")
                        Spacer(Modifier.height(10.dp))
                        if (pending == "__create__") {
                            ConfirmBox(
                                title = ctx.getString(R.string.dh_create_confirm_title),
                                body = ctx.getString(R.string.dh_create_confirm_body, cId.trim()),
                                okText = ctx.getString(R.string.dh_create_confirm_ok),
                                onOk = { pending = ""; runCreate() },
                                onCancel = { pending = "" }
                            )
                        } else {
                            PrimaryButton(
                                if (busy) stringResource(R.string.dh_busy) else stringResource(R.string.dh_create_btn),
                                enabled = !busy && cId.isNotBlank()
                            ) { pending = "__create__" }
                        }
                    }
                }

                //日志：有内容才显示
                if (log.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.dh_log), fontSize = 11.sp, color = TextSecondary)
                            Spacer(Modifier.weight(1f))
                            GhostButton(stringResource(R.string.dh_clear), Modifier.height(28.dp)) { log = ""; resetProgress() }
                        }
                        Text(log, fontSize = 9.sp, color = TextPrimary)
                    }
                }
                //底部让位：底栏是悬浮覆盖层，内容要能从它下面穿过去（玻璃才有东西可采），
                //所以留白放在内容末尾，而不是给滚动容器加 padding（与设置页 LazyColumn 的 contentPadding 同义）
                Spacer(Modifier.height(96.dp))
            }
        }
        //悬浮玻璃底栏：与设置页同一写法——叠在内容之上、采样 pageBackdrop
        FloatTabBar(
            tabs = PluginTabs,
            selected = tab,
            onSelect = { GlassPerf.switched(); tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
            backdrop = pageBackdrop
        )
    }
    }
    }
} //插件页

/** 安装/创建进度卡：确定式进度（阶段步数由 DshInstall.onStep 提供），失败转红 */
@Composable
private fun InstallProgress(label: String, step: Int, total: Int, failed: Boolean, running: Boolean) {
    val frac = if (total <= 0) 0f else (step.toFloat() / total).coerceIn(0f, 1f)
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    failed -> stringResource(R.string.dh_install_failed)
                    frac >= 1f -> stringResource(R.string.dh_done)
                    running -> stringResource(R.string.dh_installing)
                    else -> stringResource(R.string.dh_ended)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (failed) DangerRed else TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text("$step/$total", fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (failed) DangerRed else AccentBlue,
            trackColor = AccentBlueSoft
        )
        if (label.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 10.sp, color = if (failed) DangerRed else TextSecondary)
        }
    }
} //进度卡

/** 内联确认条：不用弹窗层，避免依赖 DialogHost 的宿主 */
@Composable
private fun ConfirmBox(
    title: String,
    body: String,
    okText: String,
    onOk: () -> Unit,
    onCancel: () -> Unit
) {
    CardBox {
        Text(title, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.dh_cancel), Modifier.weight(1f).height(34.dp), onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PrimaryButton(okText, modifier = Modifier.weight(1f).height(34.dp), onClick = onOk)
        }
    }
} //确认条