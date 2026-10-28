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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.withContext

/** 作者公开主页：头像昵称、简介官网、四项统计、TA 上架的插件；点插件名回广场搜它 */
@Composable
fun UserPage(uname: String, onBack: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var user by remember { mutableStateOf<Auth.User?>(null) }
    var items by remember { mutableStateOf<List<Plugins.Item>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

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
                            Stat(stringResource(R.string.acc_stat_like), u.likes.toString())
                            Stat(stringResource(R.string.acc_stat_fav), u.favs.toString())
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
                                Text(
                                    stringResource(R.string.plg_downloads, it.downloads) +
                                        "　♥" + it.likes + "　★" + it.favs,
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
} //作者主页

/** 主页里的一个统计块：上面数字下面标题 */
@Composable
private fun Stat(label: String, value: String) {
    Box(
        Modifier
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(label, fontSize = 9.sp, color = TextSecondary)
        }
    }
} //统计块