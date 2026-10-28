package b.ai.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import b.ai.R
import b.ai.core.Cfg
import b.ai.core.Paths
import b.ai.core.PluginInstall
import b.ai.core.PluginNote
import b.ai.net.Auth
import b.ai.net.Plugins
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 底栏三格：广场 / 上传 / 我的，和设置页共用同一套悬浮胶囊底栏 */
private val PlTabs = listOf(
    FloatTab(R.string.plg_tab_market) { c -> Icon(Icons.Filled.Star, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.plg_tab_upload) { c -> Icon(Icons.Filled.Add, null, tint = c, modifier = Modifier.size(18.dp)) },
    FloatTab(R.string.plg_tab_mine) { c -> Icon(Icons.Filled.Person, null, tint = c, modifier = Modifier.size(18.dp)) }
)

/**
 * 插件广场：广场（浏览 / 搜索 / 排序 / 下载安装 / 点赞收藏评论）、上传（登录后传 zip）、
 * 我的（账号卡 + 我上传的 + 我收藏的）。作者主页与评论卡都挂根弹窗层。
 */
@Composable
fun PluginMarket(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var tip by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    //广场
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("new") }
    var items by remember { mutableStateOf<List<Plugins.Item>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loadErr by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    //单个插件的下载进度：只有正在下的那条会显示百分比
    var dlId by remember { mutableIntStateOf(0) }
    var dlPct by remember { mutableIntStateOf(0) }
    //评论卡与作者主页：非空就是打开状态
    var cmtFor by remember { mutableStateOf<Plugins.Item?>(null) }
    var userFor by remember { mutableStateOf("") }
    //上传
    var name by remember { mutableStateOf("") }
    var descr by remember { mutableStateOf("") }
    var ver by remember { mutableStateOf("1.0") }
    var tags by remember { mutableStateOf("") }
    var author by remember { mutableStateOf(Cfg.accNick) }
    var zipFile by remember { mutableStateOf<File?>(null) }
    var zipName by remember { mutableStateOf("") }
    var zipErr by remember { mutableStateOf("") }
    var upPct by remember { mutableIntStateOf(0) }
    //我的
    var mine by remember { mutableStateOf<List<Plugins.Item>>(emptyList()) }
    var favs by remember { mutableStateOf<List<Plugins.Item>>(emptyList()) }
    var mineLoaded by remember { mutableStateOf(false) }
    var favLoaded by remember { mutableStateOf(false) }
    //编辑某个作品的资料：非空就弹编辑卡（只改文字，不动包）
    var editFor by remember { mutableStateOf<Plugins.Item?>(null) }
    //给某个作品发新版本：非空就弹更新卡（换包 + 版本号 + 更新说明）
    var nvFor by remember { mutableStateOf<Plugins.Item?>(null) }
    //详情卡：detVer 是要展开的那条版本，空串就是普通详情
    var detFor by remember { mutableStateOf<Plugins.Item?>(null) }
    var detVer by remember { mutableStateOf("") }
    //已装插件清单：本地装过的全列出来，顺带对一下服务器最新版本（空串＝查不到）
    var inst by remember { mutableStateOf<List<Pair<PluginInstall.Mark, String>>>(emptyList()) }
    var instLoaded by remember { mutableStateOf(false) }
    var instBusy by remember { mutableStateOf(false) }
    var instTip by remember { mutableStateOf("") }
    //读 Cfg 的登录态：它是 Compose 状态，登录 / 登出会自动重组
    val logged = Auth.logged
    BackHandler(enabled = !busy && cmtFor == null && editFor == null && nvFor == null && detFor == null) {
        if (userFor.isNotBlank()) userFor = "" else onBack()
    }

    fun loadMine() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Plugins.mine() } }
            r.onSuccess { mine = it; mineLoaded = true }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    fun loadFavs() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Plugins.favs() } }
            r.onSuccess { favs = it; favLoaded = true }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    //编辑资料：只弹编辑卡，不动版本也不换包
    fun openEdit(it: Plugins.Item) {
        if (busy) return
        tip = ""
        editFor = it
    }

    //更新：单独一张卡，选新目录、填版本号与更新说明，服务端写一条版本历史
    fun openNewVer(it: Plugins.Item) {
        if (busy) return
        tip = ""
        nvFor = it
    }

    //详情：ver 不为空时展开那一条版本记录
    fun openDetail(it: Plugins.Item, ver: String = "") {
        if (busy) return
        detVer = ver
        detFor = it
    }

    //编辑保存成功：把本地这条的两个字段换掉，省一次整页重拉
    fun onEdited(id: Int, n: String, d: String, t: String, a: String, at: String) {
        fun patch(x: Plugins.Item) = if (x.id == id) x.copy(name = n, descr = d, tags = t, author = a, editedAt = at) else x
        mine = mine.map { patch(it) }
        favs = favs.map { patch(it) }
        items = items.map { patch(it) }
    }

    //删自己的作品：服务端连包一起清，本地把已装记录也去掉
    fun onDel(p: Plugins.Item) {
        if (busy) return
        busy = true
        tip = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Plugins.del(p.id) } }
            busy = false
            r.onSuccess {
                ok = true
                tip = ctx.getString(R.string.plg_del_ok)
                mine = mine.filter { x -> x.id != p.id }
                inst = inst.filter { x -> x.first.id != p.id }
                PluginInstall.markDrop(p.id)
            }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    //已装插件清单：本地记录全列出来，顺手问一次服务器最新版本（0 就只列表不比对）
    fun loadInst() {
        if (instBusy) return
        instBusy = true
        instTip = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val root = Paths.skillRoot()
                    val ms = PluginInstall.marks().filter { File(root, it.dir).isDirectory }
                    if (ms.isEmpty()) return@runCatching emptyList<Pair<PluginInstall.Mark, String>>()
                    val vers = runCatching { Plugins.vers(ms.map { it.id }) }.getOrDefault(emptyMap())
                    ms.map { m -> m to (vers[m.id] ?: "") }
                }
            }
            instBusy = false
            r.onSuccess { list ->
                inst = list
                instLoaded = true
                instTip = if (list.isEmpty()) ctx.getString(R.string.plg_inst_empty) else ""
            }.onFailure {
                instLoaded = true
                instTip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    //卸载已装插件：删目录 + 清记录 + 重扫技能包
    fun onUninstall(m: PluginInstall.Mark) {
        if (busy) return
        busy = true
        tip = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { PluginInstall.remove(ctx, m.id, m.dir) } }
            busy = false
            r.onSuccess { res ->
                ok = res.ok
                tip = if (res.ok) ctx.getString(R.string.plg_inst_off_ok) else ctx.getString(R.string.plg_fail, res.msg)
                if (res.ok) inst = inst.filter { x -> x.first.id != m.id }
            }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    //一键更新某个已装插件：下新包 → 覆盖安装（旧目录让位，失败了回滚）
    fun applyUpd(m: PluginInstall.Mark, newVer: String) {
        if (busy) return
        busy = true
        dlId = m.id
        dlPct = 0
        tip = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val zip = Plugins.download(m.id, tempZip(m.id)) { p ->
                        scope.launch(Dispatchers.Main) { dlPct = p }
                    }
                    val res = PluginInstall.install(ctx, zip, m.name, m.id, newVer, m.dir)
                    runCatching { zip.delete() }
                    res
                }
            }
            busy = false
            dlId = 0
            r.onSuccess { res ->
                ok = res.ok
                tip = if (res.ok) ctx.getString(R.string.plg_upd_ok, newVer)
                else ctx.getString(R.string.plg_fail, res.msg)
                if (res.ok) {
                    inst = inst.map { if (it.first.id == m.id) m.copy(ver = newVer) to newVer else it }
                    PluginNote.done(ctx.getString(R.string.plg_title), ctx.getString(R.string.plg_upd_ok, newVer))
                }
            }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

//切 tab 统一走这里：进「我的」时按当前登录态拉一次自己的东西
    fun goTab(i: Int) {
        if (busy) return
        tab = i
        tip = ""
        if (i == 1 && author.isBlank()) author = Cfg.accNick
        if (i == 2 && logged) {
            if (!mineLoaded) loadMine()
            if (!favLoaded) loadFavs()
            if (!instLoaded) loadInst()
        }
    }

    //列表随搜索词、排序、页码、手动刷新变化
    LaunchedEffect(q, sort, page, reload) {
        loading = true
        loadErr = ""
        val r = withContext(Dispatchers.IO) { runCatching { Plugins.list(q, sort, page) } }
        loading = false
        r.onSuccess { (list, count) ->
            items = if (page == 1) list else items + list
            total = count
        }.onFailure { loadErr = it.message?.take(80).orEmpty() }
    }

    //登录态一变就清掉旧的「我的」再拉一遍；广场顺手重拉，点赞收藏的选中态才对
    LaunchedEffect(AccUi.stamp, logged) {
        mine = emptyList()
        favs = emptyList()
        inst = emptyList()
        mineLoaded = false
        favLoaded = false
        instLoaded = false
        instTip = ""
        editFor = null
        nvFor = null
        detFor = null
        if (author.isBlank()) author = Cfg.accNick
        if (logged) {
            loadMine()
            loadFavs()
            loadInst()
        }
        reload++
    }

    //选技能目录：用户只挑目录，压包与校验都在本地做，上传通道不变
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            zipErr = ""
            zipFile = null
            zipName = ""
            val r = withContext(Dispatchers.IO) { runCatching { zipDir(ctx, uri) } }
            r.onSuccess { (f, dirName) ->
                zipFile = f
                zipName = f.name
                if (name.isBlank()) name = dirName.take(64)
            }.onFailure {
                zipFile = null
                zipName = ""
                zipErr = it.message?.take(80).orEmpty()
            }
        }
    }

    //点赞 / 收藏：没登录先去登录，成功后只改本地这一条，不整页重拉
    fun onLike(p: Plugins.Item) {
        if (!logged) {
            AccUi.open("login")
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Plugins.like(p.id) } }
            busy = false
            r.onSuccess { (on, n) ->
                items = items.map { x -> if (x.id == p.id) x.copy(liked = on, likes = n) else x }
                favs = favs.map { x -> if (x.id == p.id) x.copy(liked = on, likes = n) else x }
            }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    fun onFav(p: Plugins.Item) {
        if (!logged) {
            AccUi.open("login")
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Plugins.fav(p.id) } }
            busy = false
            r.onSuccess { (on, n) ->
                items = items.map { x -> if (x.id == p.id) x.copy(faved = on, favs = n) else x }
                favs = if (on) {
                    listOf(p.copy(faved = true, favs = n)) + favs.filter { x -> x.id != p.id }
                } else {
                    favs.filter { x -> x.id != p.id }
                }
                favLoaded = true
            }.onFailure {
                ok = false
                tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
            }
        }
    }

    //作者主页占满整页：从广场点作者名进来
    if (userFor.isNotBlank()) {
        UserPage(
            uname = userFor,
            onBack = { userFor = "" },
            onPick = { n ->
                userFor = ""
                tab = 0
                q = n
                page = 1
            }
        )
        return
    }

    //底栏玻璃的采样源：整页内容一层，不含底栏自己
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        //采样层必须含真背景：只垫纯色的话，底栏玻璃在没内容的地方采到的就是一块灰白
        drawPageBg()
        drawContent()
    }
    //卡片玻璃的采样源：纯背景一层，不含卡片自己
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }
    ProvideCardBackdrop(cardBackdrop) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(Modifier.fillMaxSize().glassPageLayer(pageBackdrop)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier
                                .liquidBg(RoundedCornerShape(9.dp))
                                .tap { if (!busy) onBack() }
                                .padding(8.dp)
                        ) { IcBack(TextPrimary, 15.dp) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.plg_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(PlTabs[tab].labelRes), fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.weight(1f))
                        //右上角：登录了显示自己的头像，点开进「我的」；没登录直接开登录卡
                        if (logged) {
                            Row(
                                Modifier
                                    .liquidBg(RoundedCornerShape(9.dp))
                                    .tap { goTab(2) }
                                    .padding(4.dp)
                            ) { AccFace(Cfg.accUid, Cfg.accAvatar, 24.dp) }
                        } else {
                            Text(
                                stringResource(R.string.acc_login),
                                fontSize = 12.sp,
                                color = AccentBlue,
                                modifier = Modifier
                                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                                    .tap { AccUi.open("login") }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (tip.isNotBlank()) {
                        Text(
                            tip,
                            fontSize = 11.sp,
                            color = if (ok) OkGreen else DangerRed,
                            lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(glassScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        when (tab) {
                            0 -> MarketTab(
                                q = q,
                                onQ = { q = it; page = 1 },
                                sort = sort,
                                onSort = { sort = it; page = 1 },
                                items = items,
                                total = total,
                                loading = loading,
                                err = loadErr,
                                dlId = dlId,
                                dlPct = dlPct,
                                onMore = { if (items.size < total && !loading) page++ },
                                onLike = { onLike(it) },
                                onFav = { onFav(it) },
                                onCmt = { cmtFor = it },
                                onAuthor = { userFor = it },
                                onOpen = { openDetail(it) },
                                onDownload = { it ->
                                    if (busy) return@MarketTab
                                    busy = true
                                    dlId = it.id
                                    dlPct = 0
                                    tip = ""
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val zip = Plugins.download(it.id, tempZip(it.id)) { p ->
                                                    scope.launch(Dispatchers.Main) { dlPct = p }
                                                }
                                                val res = PluginInstall.install(ctx, zip, it.name, it.id, it.ver)
                                                runCatching { zip.delete() }
                                                res
                                            }
                                        }
                                        busy = false
                                        dlId = 0
                                        r.onSuccess { res ->
                                            ok = res.ok
                                            tip = if (res.ok) {
                                                ctx.getString(R.string.plg_installed, res.msg)
                                            } else {
                                                ctx.getString(R.string.plg_fail, res.msg)
                                            }
                                            if (res.ok) {
                                                PluginNote.done(
                                                    ctx.getString(R.string.plg_title),
                                                    ctx.getString(R.string.plg_installed, res.msg)
                                                )
                                                reload++
                                            }
                                        }.onFailure {
                                            ok = false
                                            tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
                                        }
                                    }
                                }
                            )
                            1 -> UploadTab(
                                logged = logged,
                                onLogin = { AccUi.open("login") },
                                name = name,
                                onName = { name = it },
                                descr = descr,
                                onDescr = { descr = it },
                                ver = ver,
                                onVer = { ver = it },
                                tags = tags,
                                onTags = { tags = it },
                                author = author,
                                onAuthor = { author = it },
                                zipName = zipName,
                                zipErr = zipErr,
                                onPick = { if (!busy) pickZip.launch(null) },
                                upPct = upPct,
                                busy = busy,
                                onSubmit = {
                                    val f = zipFile
                                    if (busy || f == null) return@UploadTab
                                    busy = true
                                    upPct = 0
                                    tip = ""
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val (id, st) = Plugins.upload(
                                                    name = name,
                                                    descr = descr,
                                                    ver = ver,
                                                    tags = tags,
                                                    author = author,
                                                    zip = f
                                                ) { p -> scope.launch(Dispatchers.Main) { upPct = p } }
                                                //开关打开时服务端直接给 status 1，别再说「等审核」误导作者
                                                if (st == 1) ctx.getString(R.string.plg_submit_live, id)
                                                else ctx.getString(R.string.plg_submit_ok, id)
                                            }
                                        }
                                        busy = false
                                        r.onSuccess { msg ->
                                            ok = true
                                            tip = msg
                                            PluginNote.done(ctx.getString(R.string.plg_title), msg)
                                            zipFile = null
                                            zipName = ""
                                            upPct = 0
                                            loadMine()
                                        }.onFailure {
                                            ok = false
                                            tip = ctx.getString(R.string.plg_fail, it.message?.take(80).orEmpty())
                                        }
                                    }
                                }
                            )
                            else -> MineTab(
                                logged = logged,
                                onLogin = { AccUi.open("login") },
                                onEdit = { AccUi.open("edit") },
                                mine = mine,
                                favs = favs,
                                mineLoaded = mineLoaded,
                                favLoaded = favLoaded,
                                onEditItem = { openEdit(it) },
                                onNewVerItem = { openNewVer(it) },
                                onDelItem = { onDel(it) },
                                onOpenVer = { p, v -> openDetail(p, v) },
                                inst = inst,
                                instTip = instTip,
                                instBusy = instBusy,
                                onReloadInst = { loadInst() },
                                onApplyUpd = { m, v -> applyUpd(m, v) },
                                onUninstall = { m -> onUninstall(m) },
                                dlId = dlId,
                                dlPct = dlPct,
                                onLike = { onLike(it) },
                                onFav = { onFav(it) },
                                onCmt = { cmtFor = it },
                                onAuthor = { userFor = it }
                            )
                        }
                        //给悬浮底栏让位：栏本体 64dp + 抬起 18dp，再留点余量
                        Spacer(Modifier.height(112.dp))
                    }
                }
            }
            FloatTabBar(
                tabs = PlTabs,
                selected = tab,
                onSelect = { goTab(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
                backdrop = pageBackdrop
            )
        }
    }
    //弹窗都挂在根弹窗层，一次只挂一张：详情 > 更新 > 编辑 > 评论
    HostInOverlay(active = cmtFor != null || editFor != null || nvFor != null || detFor != null) {
        val det = detFor
        val nv = nvFor
        val ed = editFor
        val cp = cmtFor
        when {
            det != null -> DetailSheet(
                p = det,
                focusVer = detVer,
                onClose = { detFor = null; detVer = "" }
            )
            nv != null -> NewVerSheet(
                p = nv,
                onClose = { nvFor = null },
                onDone = { ver, changes, size, st ->
                    nvFor = null
                    ok = true
                    tip = ctx.getString(if (st == 1) R.string.plg_newver_live else R.string.plg_newver_ok)
                    //本地先插一条，列表马上就能看到；下次拉 mine 会被服务端数据覆盖
                    val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    val one = Plugins.Ver(ver, changes, size, now)
                    mine = mine.map { x ->
                        if (x.id != nv.id) x
                        else x.copy(ver = ver, size = size, status = st, state = "", note = "", vers = listOf(one) + x.vers)
                    }
                }
            )
            ed != null -> EditSheet(
                p = ed,
                onClose = { editFor = null },
                onSaved = { n, d, t, a, at ->
                    editFor = null
                    ok = true
                    tip = ctx.getString(R.string.plg_edit_ok)
                    onEdited(ed.id, n, d, t, a, at)
                }
            )
            cp != null -> CommentSheet(
                p = cp,
                onClose = { cmtFor = null },
                onCount = { n ->
                    items = items.map { x -> if (x.id == cp.id) x.copy(cmts = n) else x }
                }
            )
        }
    }
} //插件广场页

/** 广场那一栏：搜索、排序、列表、加载更多 */
@Composable
private fun MarketTab(
    q: String,
    onQ: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    items: List<Plugins.Item>,
    total: Int,
    loading: Boolean,
    err: String,
    dlId: Int,
    dlPct: Int,
    onMore: () -> Unit,
    onLike: (Plugins.Item) -> Unit,
    onFav: (Plugins.Item) -> Unit,
    onCmt: (Plugins.Item) -> Unit,
    onAuthor: (String) -> Unit,
    onOpen: (Plugins.Item) -> Unit,
    onDownload: (Plugins.Item) -> Unit
) {
    Column {
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlChip(stringResource(R.string.plg_sort_new), sort == "new") { onSort("new") }
            PlChip(stringResource(R.string.plg_sort_hot), sort == "hot") { onSort("hot") }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.plg_count, items.size, total),
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        Field(
            label = "",
            value = q,
            onValueChange = { onQ(it.take(40)) },
            placeholder = stringResource(R.string.plg_search)
        )
        if (err.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(err, fontSize = 11.sp, color = DangerRed)
        }
        if (items.isEmpty() && !loading) {
            Spacer(Modifier.height(10.dp))
            CardBox {
                Text(
                    stringResource(if (q.isBlank()) R.string.plg_empty else R.string.plg_no_match),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        items.forEach { it ->
            Spacer(Modifier.height(10.dp))
            PluginCard(
                it = it,
                downloading = dlId == it.id,
                pct = dlPct,
                onDownload = { onDownload(it) },
                onLike = { onLike(it) },
                onFav = { onFav(it) },
                onCmt = { onCmt(it) },
                onAuthor = { onAuthor(it.authorUname) },
                onOpen = { onOpen(it) }
            )
        }
        if (items.size < total) {
            Spacer(Modifier.height(10.dp))
            GhostButton(
                if (loading) stringResource(R.string.plg_loading) else stringResource(R.string.plg_load_more),
                Modifier.fillMaxWidth().height(42.dp)
            ) { onMore() }
        }
    }
} //广场列表

/** 一条插件卡：名字、版本、作者（可点进主页）、说明、标签、下载 + 点赞收藏评论 */
@Composable
private fun PluginCard(
    it: Plugins.Item,
    downloading: Boolean,
    pct: Int,
    onDownload: () -> Unit,
    onLike: () -> Unit,
    onFav: () -> Unit,
    onCmt: () -> Unit,
    onAuthor: () -> Unit,
    onOpen: () -> Unit
) {
    CardBox(modifier = Modifier.tap { onOpen() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                it.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (it.ver.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(it.ver, fontSize = 10.sp, color = TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    downloading -> stringResource(R.string.plg_downloading, pct)
                    else -> stringResource(R.string.plg_download)
                },
                fontSize = 11.sp,
                color = if (downloading) TextSecondary else AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = if (downloading) SurfaceMuted else AccentBlueSoft)
                    .tap(enabled = !downloading) { onDownload() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        if (it.descr.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(it.descr, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            //stringResource 是 @Composable，必须先取出来再拼串
            val dlText = stringResource(R.string.plg_downloads, it.downloads)
            val who = it.authorNick.ifBlank { it.author }
            if (who.isNotBlank()) {
                Text(
                    who,
                    fontSize = 10.sp,
                    color = if (it.authorUname.isNotBlank()) AccentBlue else TextSecondary,
                    modifier = Modifier
                        .tap(enabled = it.authorUname.isNotBlank()) { onAuthor() }
                        .padding(end = 6.dp)
                )
            }
            val meta = dlText + "　" + sizeText(it.size)
            Text(meta, fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val tagList = it.tags.split(',', '，', ' ').map { t -> t.trim() }.filter { t -> t.isNotEmpty() }
        if (tagList.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tagList.take(4).forEach { t -> Tag(t) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Social(if (it.liked) "♥" else "♡", it.likes, it.liked, onLike)
            Social(if (it.faved) "★" else "☆", it.favs, it.faved, onFav)
            Text(
                stringResource(R.string.acc_stat_views) + " " + it.views,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Text(
                stringResource(R.string.acc_cmt) + " " + it.cmts,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.tap { onCmt() }.padding(vertical = 2.dp)
            )
        }
    }
} //插件卡片

/** 卡片底部的点赞 / 收藏计数：点一下就是切换 */
@Composable
private fun Social(sym: String, n: Int, on: Boolean, onClick: () -> Unit) {
    Text(
        "$sym $n",
        fontSize = 11.sp,
        color = if (on) AccentBlue else TextSecondary,
        modifier = Modifier.tap { onClick() }.padding(vertical = 2.dp)
    )
} //点赞收藏计数

/** 评论卡：上面是评论列表，下面是发评论；没登录只看不能发 */
@Composable
private fun CommentSheet(p: Plugins.Item, onClose: () -> Unit, onCount: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Plugins.Cmt>>(emptyList()) }
    var total by remember { mutableIntStateOf(p.cmts) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val logged = Auth.logged

    LaunchedEffect(p.id) {
        loading = true
        val r = withContext(Dispatchers.IO) { runCatching { Plugins.cmts(p.id) } }
        loading = false
        r.onSuccess { (l, t) ->
            list = l
            total = t
        }.onFailure { err = it.message?.take(80).orEmpty() }
    }

    BottomSheetCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.acc_cmt) + " " + total, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.acc_close),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.tap { onClose() }
            )
        }
        if (err.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(err, fontSize = 11.sp, color = DangerRed)
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(glassScrollState())) {
            Column {
                if (list.isEmpty()) {
                    Text(
                        if (loading) stringResource(R.string.plg_loading) else stringResource(R.string.acc_cmt_empty),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                list.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        AccFace(0, c.avatar, 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.nick.ifBlank { c.uname },
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(c.createdAt.take(16), fontSize = 9.sp, color = TextSecondary)
                            }
                            Text(c.text, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (logged) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    FieldBox(
                        label = "",
                        value = text,
                        onValueChange = { text = it.take(300) },
                        placeholder = stringResource(R.string.acc_cmt_ph)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.acc_cmt_send),
                    fontSize = 12.sp,
                    color = if (text.trim().isEmpty() || busy) TextSecondary else AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                        .tap(enabled = text.trim().isNotEmpty() && !busy) {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    runCatching { Plugins.cmt(p.id, text.trim()) }
                                }
                                busy = false
                                r.onSuccess { c ->
                                    list = list + c
                                    total += 1
                                    onCount(total)
                                    text = ""
                                }.onFailure { err = it.message?.take(80).orEmpty() }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.acc_login_first),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.acc_login),
                    fontSize = 12.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                        .tap { AccUi.open("login") }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
} //评论卡

/** 上传那一栏：必须登录；作者默认取自己的昵称 */
@Composable
private fun UploadTab(
    logged: Boolean,
    onLogin: () -> Unit,
    name: String,
    onName: (String) -> Unit,
    descr: String,
    onDescr: (String) -> Unit,
    ver: String,
    onVer: (String) -> Unit,
    tags: String,
    onTags: (String) -> Unit,
    author: String,
    onAuthor: (String) -> Unit,
    zipName: String,
    zipErr: String,
    onPick: () -> Unit,
    upPct: Int,
    busy: Boolean,
    onSubmit: () -> Unit
) {
    Column {
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.plg_tab_upload), stringResource(R.string.plg_upload_tip))
        if (!logged) {
            CardBox {
                Text(
                    stringResource(R.string.acc_need_upload),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.acc_login),
                        fontSize = 12.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                            .tap { onLogin() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Text(
                        stringResource(R.string.acc_reg),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                            .tap { AccUi.open("reg") }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            return@Column
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (zipName.isBlank()) stringResource(R.string.plg_no_zip) else zipName,
                    fontSize = 12.sp,
                    color = if (zipName.isBlank()) TextSecondary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.plg_pick_zip),
                    fontSize = 11.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                        .tap(enabled = !busy) { onPick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            if (zipErr.isNotBlank()) {
                Text(zipErr, fontSize = 10.sp, color = DangerRed, modifier = Modifier.padding(top = 4.dp))
            }
            Field(
                label = stringResource(R.string.plg_name),
                value = name,
                onValueChange = { onName(it.take(64)) }
            )
            Field(
                label = stringResource(R.string.plg_descr),
                value = descr,
                onValueChange = { onDescr(it.take(500)) },
                singleLine = false,
                minLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Field(
                        label = stringResource(R.string.plg_ver),
                        value = ver,
                        onValueChange = { onVer(it.take(24)) }
                    )
                }
                Box(Modifier.weight(1f)) {
                    Field(
                        label = stringResource(R.string.plg_author),
                        value = author,
                        onValueChange = { onAuthor(it.take(64)) }
                    )
                }
            }
            Field(
                label = stringResource(R.string.plg_tags),
                value = tags,
                onValueChange = { onTags(it.take(128)) }
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.plg_note), fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (busy) stringResource(R.string.plg_submitting, upPct) else stringResource(R.string.plg_submit),
            enabled = !busy && name.trim().length >= 2 && descr.trim().length >= 10 && zipName.isNotBlank()
        ) { onSubmit() }
    }
} //上传表单

/** 我的：没登录就是登录引导；登录了就是账号卡 + 我上传的 + 我收藏的 */
@Composable
private fun MineTab(
    logged: Boolean,
    onLogin: () -> Unit,
    onEdit: () -> Unit,
    mine: List<Plugins.Item>,
    favs: List<Plugins.Item>,
    mineLoaded: Boolean,
    favLoaded: Boolean,
    onEditItem: (Plugins.Item) -> Unit,
    onNewVerItem: (Plugins.Item) -> Unit,
    onDelItem: (Plugins.Item) -> Unit,
    onOpenVer: (Plugins.Item, String) -> Unit,
    inst: List<Pair<PluginInstall.Mark, String>>,
    instTip: String,
    instBusy: Boolean,
    onReloadInst: () -> Unit,
    onApplyUpd: (PluginInstall.Mark, String) -> Unit,
    onUninstall: (PluginInstall.Mark) -> Unit,
    dlId: Int,
    dlPct: Int,
    onLike: (Plugins.Item) -> Unit,
    onFav: (Plugins.Item) -> Unit,
    onCmt: (Plugins.Item) -> Unit,
    onAuthor: (String) -> Unit
) {
    //点过一次删除 / 卸载的条目：先说确认，避免手滑
    var delFor by remember { mutableIntStateOf(0) }
    var offFor by remember { mutableIntStateOf(0) }
    Column {
        Spacer(Modifier.height(6.dp))
        if (!logged) {
            SectionTitle(stringResource(R.string.plg_tab_mine), stringResource(R.string.acc_need_mine))
            CardBox {
                Text(
                    stringResource(R.string.acc_login_first),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.acc_login),
                        fontSize = 12.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                            .tap { onLogin() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    Text(
                        stringResource(R.string.acc_reg),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                            .tap { AccUi.open("reg") }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            return@Column
        }
        //账号卡
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccFace(Cfg.accUid, Cfg.accAvatar, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Cfg.accNick.ifBlank { Cfg.accUname },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("@" + Cfg.accUname, fontSize = 11.sp, color = TextSecondary)
                }
                Text(
                    stringResource(R.string.acc_edit),
                    fontSize = 11.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                        .tap { onEdit() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        //我上传的
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.acc_mine_up), stringResource(R.string.plg_mine_hint))
        if (mineLoaded && mine.isEmpty()) {
            CardBox { Text(stringResource(R.string.acc_up_empty), fontSize = 12.sp, color = TextSecondary) }
        }
        mine.forEach { it ->
            Spacer(Modifier.height(10.dp))
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        it.name,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        it.state.ifBlank {
                            when (it.status) {
                                0 -> stringResource(R.string.plg_state0)
                                1 -> stringResource(R.string.plg_state1)
                                else -> stringResource(R.string.plg_state2)
                            }
                        },
                        fontSize = 10.sp,
                        color = when (it.status) {
                            0 -> TextSecondary
                            1 -> OkGreen
                            else -> DangerRed
                        }
                    )
                }
                //作者自己最想看的四个数：下载 / 浏览 / 赞 / 收
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.plg_mine_stat, it.downloads, it.views, it.likes, it.favs),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                if (it.editedAt.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.plg_edited_at, it.editedAt.take(16)),
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }
                if (it.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.plg_note_reason, it.note), fontSize = 10.sp, color = DangerRed)
                }
                //版本历史：一行一个版本，点哪行进详情就看哪一版
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.plg_ver_hist), fontSize = 11.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (it.vers.isEmpty()) {
                        Text(stringResource(R.string.plg_ver_empty), fontSize = 10.sp, color = TextSecondary)
                    } else {
                        it.vers.take(5).forEach { v -> VerRow(v, false) { onOpenVer(it, v.ver) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.plg_mine_edit),
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                            .tap { delFor = 0; onEditItem(it) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Text(
                        stringResource(R.string.plg_mine_newver),
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                            .tap { delFor = 0; onNewVerItem(it) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    if (delFor == it.id) {
                        Text(
                            stringResource(R.string.plg_mine_del_c),
                            fontSize = 11.sp,
                            color = DangerRed,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                .tap { delFor = 0; onDelItem(it) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Text(
                            stringResource(R.string.plg_upd_cancel),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                .tap { delFor = 0 }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    } else {
                        Text(
                            stringResource(R.string.plg_mine_del),
                            fontSize = 11.sp,
                            color = DangerRed,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                .tap { delFor = it.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        //已装插件：全部列成清单，每行可更新 / 卸载
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.plg_upd_title), stringResource(R.string.plg_upd_sub))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    instTip.ifBlank { stringResource(R.string.plg_upd_sub) },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (instBusy) stringResource(R.string.plg_loading) else stringResource(R.string.plg_upd_check),
                    fontSize = 11.sp,
                    color = if (instBusy) TextSecondary else AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                        .tap(enabled = !instBusy) { onReloadInst() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            inst.forEach { pair ->
                val m = pair.first
                val latest = pair.second
                val hasNew = latest.isNotBlank() && latest != m.ver
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.name.ifBlank { m.dir },
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (latest.isBlank()) stringResource(R.string.plg_inst_local, m.ver)
                            else stringResource(R.string.plg_inst_ver, m.ver, latest),
                            fontSize = 10.sp,
                            color = if (hasNew) AccentBlue else TextSecondary
                        )
                        Text(m.dir, fontSize = 9.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (hasNew) {
                        Text(
                            if (dlId == m.id) stringResource(R.string.plg_upd_doing, dlPct)
                            else stringResource(R.string.plg_upd_apply),
                            fontSize = 11.sp,
                            color = if (dlId == m.id) TextSecondary else AccentBlue,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                                .tap(enabled = dlId == 0) { onApplyUpd(m, latest) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    if (offFor == m.id) {
                        Text(
                            stringResource(R.string.plg_inst_off_c),
                            fontSize = 11.sp,
                            color = DangerRed,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                .tap { offFor = 0; onUninstall(m) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.plg_upd_cancel),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                .tap { offFor = 0 }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    } else {
                        Text(
                            stringResource(R.string.plg_inst_off),
                            fontSize = 11.sp,
                            color = DangerRed,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                                .tap { offFor = m.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        //我收藏的
        Spacer(Modifier.height(4.dp))
        SectionTitle(stringResource(R.string.acc_mine_fav), null)
        if (favLoaded && favs.isEmpty()) {
            CardBox { Text(stringResource(R.string.acc_fav_empty), fontSize = 12.sp, color = TextSecondary) }
        }
        favs.forEach { it ->
            Spacer(Modifier.height(10.dp))
            PluginCard(
                it = it,
                downloading = false,
                pct = 0,
                onDownload = {},
                onLike = { onLike(it) },
                onFav = { onFav(it) },
                onCmt = { onCmt(it) },
                onAuthor = { onAuthor(it.authorUname) },
                onOpen = { onOpenVer(it, "") }
            )
        }
    }
} //我的

/** 版本历史一行：版本号 · 时间 · 体积，点开就是这一版的详情 */
@Composable
private fun VerRow(v: Plugins.Ver, focus: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidBg(RoundedCornerShape(9.dp), tint = if (focus) AccentBlueSoft else SurfaceMuted)
            .tap { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                v.ver.ifBlank { "—" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (focus) AccentBlue else TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(v.createdAt.take(16), fontSize = 9.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(sizeText(v.size), fontSize = 9.sp, color = TextSecondary)
        }
        if (v.changes.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(v.changes, fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp)
        }
    }
} //版本历史一行

/** 编辑资料卡：只改名称 / 说明 / 标签 / 作者，版本与包都不动 */
@Composable
private fun EditSheet(
    p: Plugins.Item,
    onClose: () -> Unit,
    onSaved: (String, String, String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p.name) }
    var descr by remember { mutableStateOf(p.descr) }
    var tags by remember { mutableStateOf(p.tags) }
    var author by remember { mutableStateOf(p.author.ifBlank { Cfg.accNick }) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    BottomSheetCard(onDismiss = { if (!busy) onClose() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.plg_edit_title, p.name),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.acc_close),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.tap { if (!busy) onClose() }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.plg_edit_hint2), fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp)
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(glassScrollState())) {
            Column {
                Field(label = stringResource(R.string.plg_name), value = name, onValueChange = { name = it.take(64) })
                Field(
                    label = stringResource(R.string.plg_descr),
                    value = descr,
                    onValueChange = { descr = it.take(500) },
                    singleLine = false,
                    minLines = 3
                )
                Field(label = stringResource(R.string.plg_tags), value = tags, onValueChange = { tags = it.take(128) })
                Field(label = stringResource(R.string.plg_author), value = author, onValueChange = { author = it.take(64) })
                if (err.isNotBlank()) {
                    Text(err, fontSize = 11.sp, color = DangerRed, lineHeight = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = if (busy) stringResource(R.string.plg_loading) else stringResource(R.string.plg_save),
            enabled = !busy && name.trim().length >= 2 && descr.trim().length >= 10
        ) {
            busy = true
            err = ""
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Plugins.edit(p.id, name, descr, tags, author) } }
                busy = false
                r.onSuccess { at -> onSaved(name.trim(), descr.trim(), tags.trim(), author.trim(), at) }
                    .onFailure { err = it.message?.take(80).orEmpty() }
            }
        }
    }
} //编辑资料卡

/** 发新版本卡：选新目录 + 版本号 + 更新说明，换包后要重新过审 */
@Composable
private fun NewVerSheet(
    p: Plugins.Item,
    onClose: () -> Unit,
    onDone: (String, String, Long, Int) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableStateOf("") }
    var changes by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf<File?>(null) }
    var zipName by remember { mutableStateOf("") }
    var zipErr by remember { mutableStateOf("") }
    var pct by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    //选目录：同上传一样本地压包 + 结构校验，选完顺手把旧版本号填进输入框
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            zipErr = ""
            zip = null
            zipName = ""
            val r = withContext(Dispatchers.IO) { runCatching { zipDir(ctx, uri) } }
            r.onSuccess { (f, _) ->
                zip = f
                zipName = f.name
                if (ver.isBlank()) ver = p.ver
            }.onFailure {
                zip = null
                zipName = ""
                zipErr = it.message?.take(80).orEmpty()
            }
        }
    }

    BottomSheetCard(onDismiss = { if (!busy) onClose() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.plg_newver_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.acc_close),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.tap { if (!busy) onClose() }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.plg_newver_hint, p.ver),
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 15.sp
        )
        Box(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(glassScrollState())) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (zipName.isBlank()) stringResource(R.string.plg_no_zip) else zipName,
                        fontSize = 12.sp,
                        color = if (zipName.isBlank()) TextSecondary else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.plg_pick_zip),
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                            .tap(enabled = !busy) { pick.launch(null) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (zipErr.isNotBlank()) {
                    Text(zipErr, fontSize = 10.sp, color = DangerRed, modifier = Modifier.padding(top = 4.dp))
                }
                Field(label = stringResource(R.string.plg_ver), value = ver, onValueChange = { ver = it.take(24) })
                Field(
                    label = stringResource(R.string.plg_changes),
                    value = changes,
                    onValueChange = { changes = it.take(500) },
                    singleLine = false,
                    minLines = 3,
                    placeholder = stringResource(R.string.plg_changes_ph)
                )
                if (err.isNotBlank()) {
                    Text(err, fontSize = 11.sp, color = DangerRed, lineHeight = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = if (busy) stringResource(R.string.plg_submitting, pct) else stringResource(R.string.plg_newver_submit),
            enabled = !busy && ver.isNotBlank() && zip != null
        ) {
            val f = zip ?: return@PrimaryButton
            busy = true
            pct = 0
            err = ""
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    runCatching { Plugins.newver(p.id, ver, changes, f) { q -> scope.launch(Dispatchers.Main) { pct = q } } }
                }
                busy = false
                r.onSuccess { t -> onDone(t.ver.ifBlank { ver.trim() }, changes.trim(), t.size, t.status) }
                    .onFailure { err = it.message?.take(80).orEmpty() }
            }
        }
    }
} //发新版本卡

/** 详情卡：插件信息 + 版本历史，focusVer 那条高亮（从版本行点进来时） */
@Composable
private fun DetailSheet(p: Plugins.Item, focusVer: String, onClose: () -> Unit) {
    BottomSheetCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (p.ver.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(p.ver, fontSize = 10.sp, color = AccentBlue)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.acc_close),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.tap { onClose() }
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(glassScrollState())) {
            Column {
                if (p.descr.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(p.descr, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(6.dp))
                val who = p.authorNick.ifBlank { p.author }
                val meta = stringResource(R.string.plg_downloads, p.downloads) + "　" + sizeText(p.size) +
                    "　" + stringResource(R.string.acc_stat_views) + " " + p.views
                Text(meta, fontSize = 10.sp, color = TextSecondary)
                if (who.isNotBlank()) {
                    Text(who, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                if (p.editedAt.isNotBlank()) {
                    Text(
                        stringResource(R.string.plg_edited_at, p.editedAt.take(16)),
                        fontSize = 9.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val tagList = p.tags.split(',', '，', ' ').map { t -> t.trim() }.filter { t -> t.isNotEmpty() }
                if (tagList.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tagList.take(6).forEach { t -> Tag(t) } }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.plg_ver_hist), fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.vers.isEmpty()) {
                        Text(stringResource(R.string.plg_ver_empty), fontSize = 10.sp, color = TextSecondary)
                    } else {
                        p.vers.forEach { v -> VerRow(v, focusVer.isNotBlank() && v.ver == focusVer) {} }
                    }
                }
            }
        }
    }
} //插件详情卡

/** 排序小胶囊 */
@Composable
private fun PlChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (on) AccentBlue else TextSecondary,
        modifier = Modifier
            .liquidBg(RoundedCornerShape(9.dp), tint = if (on) AccentBlueSoft else SurfaceWhite)
            .tap { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
} //排序小胶囊

/** 插件包体积显示：KB / MB 自动换算 */
private fun sizeText(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) String.format("%.0fKB", kb) else String.format("%.1fMB", kb / 1024)
} //体积文案

/** 下载落点：临时目录下按 id 命名，装完就删 */
private fun tempZip(id: Int): File = File(Paths.dir("plugin_tmp"), "dl-$id.zip")

/** 一个包最多这么多条目、目录原始体积上限，和服务端、解压端对齐 */
private const val ENTRY_MAX = 2000
private const val RAW_MAX = 64L * 1024 * 1024

/** 把选中的目录打成 zip：递归列子项按相对路径入包，条目数 / 原始体积 / 压完体积三道都卡 */
private fun zipDir(ctx: android.content.Context, tree: Uri): Pair<File, String> {
    val res = ctx.contentResolver
    //持久授权尽力拿：拿不到不影响马上要做的压包
    runCatching {
        res.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val rootId = DocumentsContract.getTreeDocumentId(tree)
    val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
    var rootName = "plugin"
    runCatching {
        res.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) rootName = c.getString(0)
        }
    }
    val files = ArrayList<Pair<String, Uri>>()
    val seen = HashSet<String>()
    //递归列目录：跳过隐藏文件与怪名字，目录名进 prefix
    fun walk(dir: Uri, prefix: String) {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
        val c = res.query(
            kids,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        ) ?: error("这个目录读不了，换一个试试")
        c.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val nm = it.getString(1) ?: continue
                if (nm.isBlank() || nm.startsWith(".") || nm.contains('/')) continue
                val child = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val path = prefix + nm
                if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(child, path + "/")
                } else {
                    if (files.size >= ENTRY_MAX) error("文件太多了，一个包最多 $ENTRY_MAX 个")
                    if (seen.add(path)) files.add(path to child)
                }
            }
        }
    }
    walk(root, "")
    if (files.isEmpty()) error("这个目录是空的")
    //和安装时同一条规则：包根目录要有 skill.json 或 SKILL.md
    if (files.none { p -> p.first == "skill.json" || p.first == "SKILL.md" }) {
        error("目录里要有 skill.json 或 SKILL.md")
    }
    val dst = File(Paths.dir("plugin_tmp"), "up-" + System.currentTimeMillis().toString(36) + ".zip")
    var raw = 0L
    try {
        ZipOutputStream(BufferedOutputStream(dst.outputStream())).use { zos ->
            files.forEach { (path, u) ->
                val ins = res.openInputStream(u) ?: error("读不了 " + path)
                ins.use { i ->
                    zos.putNextEntry(ZipEntry(path))
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n <= 0) break
                        raw += n
                        if (raw > RAW_MAX) error("目录太大，处理上限 64MB")
                        zos.write(buf, 0, n)
                    }
                    zos.closeEntry()
                }
            }
        }
    } catch (e: Throwable) {
        dst.delete()
        throw e
    }
    if (dst.length() > Plugins.ZIP_MAX) {
        dst.delete()
        error("压完还超过 8MB，去掉大文件再传")
    }
    return dst to rootName
} //选目录压包