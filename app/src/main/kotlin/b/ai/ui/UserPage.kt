package b.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import b.ai.net.Auth
import b.ai.net.Plugins
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 作者公开主页：头像昵称、简介官网、六项统计（粉丝 / 关注可点开列表）、关注按钮、TA 上架的插件 */
@Composable
fun UserPage(uname: String, onBack: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<Auth.User?>(null) }
    var items by remember { mutableStateOf<List<Plugins.Item>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    //非空就是打开了关注 / 粉丝列表：true 粉丝，false 关注的人
    var listFor by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(uname) {
        loading = true
        err = ""
        val r = withContext(Dispatchers.IO) { runCatching { Auth.profile(uname) } }
        loading = false
        r.onSuccess { (u, list) ->
            user = u
            items = list
        }.onFailure { err = it.message?.take(80).orEmpty() }
    }
    BackHandler { onBack() }

    //关注 / 取关：没登录先拉登录卡，成功后只改本地这一份资料，不整页重拉
    fun onFollow() {
        val u = user ?: return
        if (!Auth.logged) {
            AccUi.open("login")
            return
        }
        if (busy || u.uid <= 0) return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Auth.follow(u.uid) } }
            busy = false
            r.onSuccess { (now, fans, follows) ->
                user = u.copy(followed = now, followers = fans, following = follows)
            }.onFailure { err = it.message?.take(80).orEmpty() }
        }
    }

    //卡片玻璃的采样源：纯背景一层，不含卡片自己
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }
    ProvideCardBackdrop(cardBackdrop) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .liquidBg(RoundedCornerShape(9.dp))
                            .tap { onBack() }
                            .padding(8.dp)
                    ) { IcBack(TextPrimary, 15.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.acc_home), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(glassScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    if (err.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        CardBox { Text(err, fontSize = 12.sp, color = DangerRed) }
                    }
                    val u = user
                    if (u == null) {
                        if (loading) {
                            Spacer(Modifier.height(10.dp))
                            CardBox { Text(stringResource(R.string.plg_loading), fontSize = 12.sp, color = TextSecondary) }
                        }
                        Spacer(Modifier.height(100.dp))
                        return@Column
                    }
                    Spacer(Modifier.height(6.dp))
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccFace(u.uid, u.avatar, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    u.nick.ifBlank { u.uname },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("@" + u.uname, fontSize = 11.sp, color = TextSecondary)
                                if (u.createdAt.length >= 10) {
                                    Text(
                                        stringResource(R.string.acc_joined, u.createdAt.take(10)),
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            //自己的主页不出现关注按钮
                            if (u.uid > 0 && u.uid != Cfg.accUid) {
                                Text(
                                    stringResource(if (u.followed) R.string.acc_followed else R.string.acc_follow),
                                    fontSize = 12.sp,
                                    color = if (u.followed) TextSecondary else AccentBlue,
                                    modifier = Modifier
                                        .liquidBg(
                                            RoundedCornerShape(10.dp),
                                            tint = if (u.followed) SurfaceMuted else AccentBlueSoft
                                        )
                                        .tap(enabled = !busy) { onFollow() }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                        if (u.bio.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(u.bio, fontSize = 12.sp, color = TextPrimary, lineHeight = 18.sp)
                        }
                        if (u.site.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                u.site,
                                fontSize = 11.sp,
                                color = AccentBlue,
                                modifier = Modifier
                                    .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                                    .tap {
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(
                                                    if (u.site.startsWith("http")) u.site else "https://" + u.site
                                                )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat(stringResource(R.string.acc_stat_plugins), u.plugins.toString())
                            Stat(stringResource(R.string.acc_stat_dl), u.downloads.toString())
                            Stat(stringResource(R.string.acc_stat_views), u.views.toString())
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat(stringResource(R.string.acc_stat_like), u.likes.toString())
                            Stat(stringResource(R.string.acc_stat_fav), u.favs.toString())
                            Stat(
                                stringResource(R.string.acc_stat_fans),
                                u.followers.toString(),
                                onClick = { listFor = true }
                            )
                            Stat(
                                stringResource(R.string.acc_stat_following),
                                u.following.toString(),
                                onClick = { listFor = false }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionTitle(stringResource(R.string.acc_up_list), stringResource(R.string.acc_up_hint))
                    if (items.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        CardBox { Text(stringResource(R.string.acc_no_plugin), fontSize = 12.sp, color = TextSecondary) }
                    }
                    items.forEach { it ->
                        Spacer(Modifier.height(10.dp))
                        CardBox {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    it.name,
                                    fontSize = 13.sp,
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
                                    stringResource(R.string.acc_find_in_market),
                                    fontSize = 11.sp,
                                    color = AccentBlue,
                                    modifier = Modifier
                                        .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                                        .tap { onPick(it.name) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            if (it.descr.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(it.descr, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row {
                                val vw = stringResource(R.string.acc_stat_views)
                                Text(
                                    stringResource(R.string.plg_downloads, it.downloads) +
                                        "　♥" + it.likes + "　★" + it.favs + "　" + vw + " " + it.views,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    //关注 / 粉丝列表挂在根弹窗层：本页是整页子页面，自己开一层，别和广场那层抢
    HostInOverlay(active = listFor != null) {
        val fans = listFor
        val uid = user?.uid ?: 0
        if (fans != null && uid > 0) {
            FollowSheet(uid = uid, fans = fans, onClose = { listFor = null })
        }
    }
} //作者主页

/** 关注 / 粉丝列表卡：每行一个头像昵称，右边直接关注或取关 */
@Composable
private fun FollowSheet(uid: Int, fans: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Auth.User>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var acting by remember { mutableIntStateOf(0) }

    fun fetch(p: Int) {
        loading = true
        err = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Auth.follows(uid, fans, p) } }
            loading = false
            r.onSuccess { (l, t) ->
                list = if (p == 1) l else list + l
                total = t
                page = p
                more = list.size < t
            }.onFailure { err = it.message?.take(80).orEmpty() }
        }
    }

    //关注 / 取关：只改这一行，列表顺序和总数由服务端说了算，不整卡重拉
    fun toggle(x: Auth.User) {
        if (!Auth.logged) {
            AccUi.open("login")
            return
        }
        if (acting != 0) return
        acting = x.uid
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Auth.follow(x.uid) } }
            acting = 0
            r.onSuccess { (now, _, _) ->
                list = list.map { y -> if (y.uid == x.uid) y.copy(followed = now) else y }
            }.onFailure { err = it.message?.take(80).orEmpty() }
        }
    }

    LaunchedEffect(uid, fans) { fetch(1) }

    BottomSheetCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (fans) R.string.acc_stat_fans else R.string.acc_stat_following),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(total.toString(), fontSize = 11.sp, color = TextSecondary)
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
        Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(glassScrollState())) {
            Column {
                if (list.isEmpty()) {
                    Text(
                        if (loading) stringResource(R.string.plg_loading) else stringResource(R.string.acc_follow_empty),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                list.forEach { x ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccFace(x.uid, x.avatar, 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                x.nick.ifBlank { x.uname },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "@" + x.uname + "　" + stringResource(R.string.acc_stat_fans) + " " + x.followers,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        if (x.uid > 0 && x.uid != Cfg.accUid) {
                            Text(
                                stringResource(if (x.followed) R.string.acc_followed else R.string.acc_follow),
                                fontSize = 11.sp,
                                color = if (x.followed) TextSecondary else AccentBlue,
                                modifier = Modifier
                                    .liquidBg(
                                        RoundedCornerShape(9.dp),
                                        tint = if (x.followed) SurfaceMuted else AccentBlueSoft
                                    )
                                    .tap(enabled = acting == 0) { toggle(x) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                if (more && !loading) {
                    Text(
                        stringResource(R.string.plg_load_more),
                        fontSize = 12.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tap { fetch(page + 1) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
} //关注粉丝列表卡

/** 主页里的一个统计块：上面数字下面标题；传了 onClick 就是可点的入口 */
@Composable
private fun Stat(label: String, value: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .liquidBg(RoundedCornerShape(10.dp), tint = if (onClick == null) SurfaceMuted else AccentBlueSoft)
            .then(if (onClick == null) Modifier else Modifier.tap { onClick() })
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(label, fontSize = 9.sp, color = TextSecondary)
        }
    }
} //统计块