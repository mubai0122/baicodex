package b.ai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import b.ai.net.Feedback
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.theme.WarnAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 反馈详情：看处理进度、和开发者互相留言、处理完后评价 */
@Composable
fun FeedbackDetailScreen(id: Int, onBack: () -> Unit) {
    var item by remember { mutableStateOf<Feedback.Item?>(null) }
    var msgs by remember { mutableStateOf<List<Feedback.Msg>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var replyText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var stars by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = !busy) { onBack() }
    //每次 reload 拉一次详情；留言或评价成功后 +1 刷新状态
    LaunchedEffect(reload) {
        val r = withContext(Dispatchers.IO) { runCatching { Feedback.detail(id) } }
        r.onSuccess { (it, list) ->
            item = it
            msgs = list
            if (it.rating > 0) stars = it.rating
        }.onFailure {
            tip = b.ai.App.self.getString(R.string.fb_fail, it.message?.take(80).orEmpty())
            ok = false
        }
    }

    //卡片玻璃的采样源：纯背景一层，不含卡片自己，否则卡片会采到上一帧的自己
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }

    ProvideCardBackdrop(cardBackdrop) {
    Box(Modifier.fillMaxSize()) {
        //纯背景一层，卡片玻璃只采它
        Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        //顶栏：返回 + 标题
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
            ) {
                IcBack(TextPrimary, 15.dp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.fb_detail_title) + " #$id",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(glassScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            val it = item
            if (it == null) {
                CardBox {
                    Text(
                        if (tip.isBlank()) stringResource(R.string.fb_loading) else tip,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }
            //反馈本体
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${it.id}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    StateChip(it.status)
                    Spacer(Modifier.weight(1f))
                    Text(it.createdAt, fontSize = 10.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(it.content, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp)
                //附件：点一下交给外部应用打开，图片直接看，日志用文本查看器
                if (it.files.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    it.files.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                                .tap { openFile(ctx, f.url) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (f.type == Feedback.TYPE_IMAGE) "🖼" else "📄", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    f.name,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(fmtSize(f.size), fontSize = 10.sp, color = TextSecondary)
                            }
                            Text(stringResource(R.string.fb_att_open), fontSize = 10.sp, color = AccentBlue)
                        }
                    }
                }
            }
            //留言线程：我发在右边，开发者发在左边
            if (msgs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionTitle(stringResource(R.string.fb_thread))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    msgs.forEach { m ->
                        val admin = m.role == "admin"
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = if (admin) Alignment.Start else Alignment.End
                        ) {
                            Text(
                                if (admin) stringResource(R.string.fb_from_admin)
                                else stringResource(R.string.fb_from_me),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                m.content,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                lineHeight = 19.sp,
                                modifier = Modifier
                                    .liquidBg(RoundedCornerShape(12.dp)
                                    , tint = if (admin) SurfaceWhite else AccentBlueSoft)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(m.createdAt, fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tip,
                    fontSize = 11.sp,
                    color = if (ok) OkGreen else DangerRed,
                    lineHeight = 17.sp
                )
            }
            //已评价：只展示分数，不再给输入框
            if (it.rating > 0) {
                Spacer(Modifier.height(12.dp))
                CardBox {
                    Text(
                        stringResource(R.string.fb_rated),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "★".repeat(it.rating) + "☆".repeat(5 - it.rating),
                        fontSize = 16.sp,
                        color = WarnAmber
                    )
                    if (it.ratingText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(it.ratingText, fontSize = 12.sp, color = TextPrimary, lineHeight = 18.sp)
                    }
                }
            }
            //处理完成：可以评价
            if (it.canRate) {
                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.fb_rate_q))
                CardBox {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { n ->
                            Text(
                                if (n <= stars) "★" else "☆",
                                fontSize = 24.sp,
                                color = if (n <= stars) WarnAmber else TextSecondary,
                                modifier = Modifier
                                    .tap { if (!busy) stars = n }
                                    .padding(2.dp)
                            )
                        }
                    }
                    Field(
                        label = "",
                        value = rateText,
                        onValueChange = { v -> if (v.length <= 300) rateText = v },
                        placeholder = stringResource(R.string.fb_rate_ph),
                        singleLine = false,
                        minLines = 3
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = stringResource(R.string.fb_rate_send),
                        enabled = !busy && stars > 0
                    ) {
                        busy = true
                        tip = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching { Feedback.rate(id, stars, rateText) }
                            }
                            busy = false
                            r.onSuccess {
                                ok = true
                                tip = b.ai.App.self.getString(R.string.fb_rate_ok)
                                reload++
                            }.onFailure {
                                ok = false
                                tip = b.ai.App.self.getString(R.string.fb_fail, it.message?.take(80).orEmpty())
                            }
                        }
                    }
                }
            }
            //还没关闭：可以继续留言（处理中互留言）
            if (it.canReply) {
                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.fb_reply_title))
                CardBox {
                    Field(
                        label = "",
                        value = replyText,
                        onValueChange = { v -> if (v.length <= 1000) replyText = v },
                        placeholder = stringResource(R.string.fb_reply_ph),
                        singleLine = false,
                        minLines = 3
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = stringResource(R.string.fb_reply_send),
                        enabled = !busy && replyText.trim().isNotEmpty()
                    ) {
                        busy = true
                        tip = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching { Feedback.reply(id, replyText) }
                            }
                            busy = false
                            r.onSuccess {
                                replyText = ""
                                ok = true
                                tip = b.ai.App.self.getString(R.string.fb_reply_ok)
                                reload++
                            }.onFailure {
                                ok = false
                                tip = b.ai.App.self.getString(R.string.fb_fail, it.message?.take(80).orEmpty())
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.fb_closed_tip),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    } //反馈详情
    } //兜底容器
    } //玻璃作用域
} //反馈详情页

/** 附件体积文案：不足 1MB 用 KB */
private fun fmtSize(n: Long): String =
    if (n >= 1024 * 1024) String.format("%.1f MB", n / 1048576.0) else "${maxOf(1L, n / 1024)} KB"

/** 外部打开附件地址；没有能处理的应用就提示一句 */
private fun openFile(ctx: Context, url: String) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }.onFailure {
        Toast.makeText(ctx, ctx.getString(R.string.fb_att_open_fail), Toast.LENGTH_SHORT).show()
    }
} //打开附件