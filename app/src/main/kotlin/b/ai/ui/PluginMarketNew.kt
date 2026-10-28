package b.ai.ui

import android.net.Uri
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
import java.io.File

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
    //读 Cfg 的登录态：它是 Compose 状态，登录 / 登出会自动重组
    val logged = Auth.logged
    BackHandler(enabled = !busy && cmtFor == null) { if (userFor.isNotBlank()) userFor = "" else onBack() }

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

    //切 tab 统一走这里：进「我的」时按当前登录态拉一次自己的东西
    fun goTab(i: Int) {
        if (busy) return
        tab = i
        tip = ""
        if (i == 1 && author.isBlank()) author = Cfg.accNick
        if (i == 2 && logged) {
            if (!mineLoaded) loadMine()
            if (!favLoaded) loadFavs()
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
        mineLoaded = false
        favLoaded = false
        if (author.isBlank()) author = Cfg.accNick
        if (logged) {
            loadMine()
            loadFavs()
        }
        reload++
    }

    //选压缩包：任何文件都能选，落地前按体积与 zip 头自己判
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            zipErr = ""
            val r = withContext(Dispatchers.IO) { copyZip(ctx, uri) }
            r.onSuccess { f ->
                zipFile = f
                zipName = f.name
                if (name.isBlank()) name = f.name.substringBeforeLast('.').take(64)
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
                                                val res = PluginInstall.install(ctx, zip, it.name)
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
                                onPick = { if (!busy) pickZip.launch("*/*") },
                                upPct = upPct,
                                busy = busy,
                                onSubmit = {
                                    val f = zipFile
                                    if (f == null || busy) return@UploadTab
                                    busy = true
                                    upPct = 0
                                    tip = ""
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) {
                                            runCatching {
                                                Plugins.upload(
                                                    name = name,
                                                    descr = descr,
                                                    ver = ver,
                                                    tags = tags,
                                                    author = author,
                                                    zip = f
                                                ) { p -> scope.launch(Dispatchers.Main) { upPct = p } }
                                            }
                                        }
                                        busy = false
                                        r.onSuccess { id ->
                                            ok = true
                                            tip = ctx.getString(R.string.plg_submit_ok, id)
                                            PluginNote.done(
                                                ctx.getString(R.string.plg_title),
                                                ctx.getString(R.string.plg_submit_ok, id)
                                            )
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
                                onRefresh = {
                                    tip = ""
                                    loadMine()
                                    loadFavs()
                                },
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
    //评论卡挂在根弹窗层；账号卡统一由 MainActivity 的根弹窗层渲染，这里不再挂
    HostInOverlay(active = cmtFor != null) {
        cmtFor?.let { p ->
            CommentSheet(
                p = p,
                onClose = { cmtFor = null },
                onCount = { n ->
                    items = items.map { x -> if (x.id == p.id) x.copy(cmts = n) else x }
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
                onAuthor = { onAuthor(it.authorUname) }
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
    onAuthor: () -> Unit
) {
    CardBox {
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
    onRefresh: () -> Unit,
    onLike: (Plugins.Item) -> Unit,
    onFav: (Plugins.Item) -> Unit,
    onCmt: (Plugins.Item) -> Unit,
    onAuthor: (String) -> Unit
) {
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
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.plg_refresh),
                    fontSize = 12.sp,
                    color = AccentBlue,
                    modifier = Modifier
                        .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                        .tap { onRefresh() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
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
                if (it.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.plg_note_reason, it.note), fontSize = 10.sp, color = DangerRed)
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
                onAuthor = { onAuthor(it.authorUname) }
            )
        }
    }
} //我的

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

/**
 * 把选中的 zip 拷到临时目录：先按文件头确认是 zip、按体积卡 8MB，再落盘。
 * 兜底挡掉把 apk / 图片改名成 zip 的情况。
 */
private fun copyZip(ctx: android.content.Context, uri: Uri): kotlin.Result<File> = runCatching {
    var size = -1L
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.SIZE)
            if (i >= 0 && c.moveToFirst()) size = c.getLong(i)
        }
    }
    if (size > Plugins.ZIP_MAX) error("压缩包不能超过 8MB")
    val head = ByteArray(4)
    ctx.contentResolver.openInputStream(uri)?.use { it.read(head) }
    if (!(head[0] == 0x50.toByte() && head[1] == 0x4B.toByte())) error("这不是 zip 压缩包")
    val dst = File(Paths.dir("plugin_tmp"), "up-" + System.currentTimeMillis().toString(36) + ".zip")
    var total = 0L
    ctx.contentResolver.openInputStream(uri)?.use { ins ->
        dst.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                total += n
                if (total > Plugins.ZIP_MAX) {
                    dst.delete()
                    error("压缩包不能超过 8MB")
                }
                out.write(buf, 0, n)
            }
        }
    } ?: error("这个文件读不了")
    dst
} //选包落临时文件