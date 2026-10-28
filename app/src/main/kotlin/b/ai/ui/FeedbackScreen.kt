package b.ai.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import b.ai.ui.theme.WarnAmberSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 邮箱格式：够用的宽松校验，只挡明显手误，不追求 RFC 完整 */
private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** 意见反馈：提交（分类 + 内容 + 邮箱）+ 我的反馈列表，点进去看进度和留言 */
@Composable
fun FeedbackScreen(onBack: () -> Unit, onOpenDetail: (Int) -> Unit) {
    var cat by remember { mutableStateOf(Feedback.BUG) }
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<List<Feedback.Item>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var atts by remember { mutableStateOf<List<Feedback.Attach>>(emptyList()) }
    var attErr by remember { mutableStateOf("") }
    //邮箱必填：格式不对就不让提交
    val emailOk = EMAIL_RE.matches(contact.trim())
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mBigImg = stringResource(R.string.fb_att_img_big)
    val mBigLog = stringResource(R.string.fb_att_log_big)
    val mBadExt = stringResource(R.string.fb_att_log_bad)
    val mReadFail = stringResource(R.string.fb_att_read_fail)
    val mImgMax = stringResource(R.string.fb_att_img_max)
    val msgOf: (AttErr) -> String = { e ->
        when (e) {
            AttErr.BIG_IMAGE -> mBigImg
            AttErr.BIG_LOG -> mBigLog
            AttErr.BAD_EXT -> mBadExt
            AttErr.IMG_MAX -> mImgMax
            AttErr.READ_FAIL -> mReadFail
        }
    }
    //选图片：系统相册多选，单张 5MB 内、最多 3 张
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val res = withContext(Dispatchers.IO) { uris.map { readAttach(ctx, it, Feedback.TYPE_IMAGE) } }
                var list = atts
                var e: AttErr? = null
                res.forEach { (a, err) ->
                    if (err != null) {
                        e = err
                    } else if (a != null) {
                        if (list.count { it.type == Feedback.TYPE_IMAGE } >= 3) e = AttErr.IMG_MAX else list = list + a
                    }
                }
                atts = list
                attErr = e?.let(msgOf).orEmpty()
            }
        }
    }
    //选日志：文件管理器里挑一个文本文件，再选会替换旧的
    val pickLog = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val (a, err) = withContext(Dispatchers.IO) { readAttach(ctx, uri, Feedback.TYPE_LOG) }
                if (a != null) {
                    atts = atts.filterNot { it.type == Feedback.TYPE_LOG } + a
                    attErr = ""
                } else {
                    attErr = err?.let(msgOf).orEmpty()
                }
            }
        }
    }
    val imgCount = atts.count { it.type == Feedback.TYPE_IMAGE }
    val logCount = atts.count { it.type == Feedback.TYPE_LOG }
    BackHandler(enabled = !busy) { onBack() }
    //进页面拉一次我的反馈，提交成功后 reload+1 再拉一次
    LaunchedEffect(reload) {
        mine = withContext(Dispatchers.IO) {
            runCatching { Feedback.list() }.getOrDefault(emptyList())
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
                stringResource(R.string.fb_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            SectionTitle(stringResource(R.string.fb_title), stringResource(R.string.fb_title_d))
            CardBox {
                Text(stringResource(R.string.fb_cat), fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatChip(stringResource(R.string.fb_cat_bug), cat == Feedback.BUG) { cat = Feedback.BUG }
                    CatChip(stringResource(R.string.fb_cat_idea), cat == Feedback.IDEA) { cat = Feedback.IDEA }
                    CatChip(stringResource(R.string.fb_cat_other), cat == Feedback.OTHER) { cat = Feedback.OTHER }
                }
                Field(
                    label = stringResource(R.string.fb_content),
                    value = content,
                    onValueChange = { if (it.length <= 2000) { content = it; ok = false } },
                    placeholder = stringResource(R.string.fb_content_ph),
                    singleLine = false,
                    minLines = 6
                )
                Text(
                    "${content.length} / 2000",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
                Field(
                    label = stringResource(R.string.fb_contact),
                    value = contact,
                    onValueChange = { if (it.length <= 128) contact = it },
                    placeholder = stringResource(R.string.fb_contact_ph)
                )
                if (contact.isNotBlank() && !emailOk) {
                    Text(
                        stringResource(R.string.fb_email_bad),
                        fontSize = 10.sp,
                        color = DangerRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                //附件：图片最多 3 张、单张 5MB；日志 1 份、1MB
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fb_att), fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    AttBtn(stringResource(R.string.fb_att_img), imgCount < 3) { pickImg.launch("image/*") }
                    Spacer(Modifier.width(8.dp))
                    AttBtn(stringResource(R.string.fb_att_log), logCount < 1) { pickLog.launch("*/*") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.fb_att_tip),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
                if (attErr.isNotBlank()) {
                    Text(attErr, fontSize = 10.sp, color = DangerRed, modifier = Modifier.padding(top = 4.dp))
                }
                atts.forEach { a ->
                    key(a.name, a.size) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (a.type == Feedback.TYPE_IMAGE) {
                                val bmp = remember(a.name, a.size) { smallBitmap(a.bytes, 240) }
                                if (bmp != null) {
                                    Image(
                                        bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.name,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(sizeText(a.size), fontSize = 10.sp, color = TextSecondary)
                            }
                            Text(
                                "×",
                                fontSize = 16.sp,
                                color = TextSecondary,
                                modifier = Modifier
                                    .tap { if (!busy) atts = atts - a }
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.fb_note), fontSize = 10.sp, color = TextSecondary)
            }
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tip,
                    fontSize = 11.sp,
                    color = if (ok) OkGreen else TextSecondary,
                    lineHeight = 17.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = if (busy) stringResource(R.string.fb_sending) else stringResource(R.string.fb_submit),
                enabled = !busy && content.trim().length >= 4 && emailOk
            ) {
                busy = true
                tip = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { Feedback.submit(cat, content, contact, atts) }
                    }
                    busy = false
                    r.onSuccess {
                        content = ""
                        contact = ""
                        atts = emptyList()
                        attErr = ""
                        ok = true
                        tip = b.ai.App.self.getString(R.string.fb_ok)
                        reload++
                    }.onFailure {
                        ok = false
                        tip = b.ai.App.self.getString(
                            R.string.fb_fail,
                            it.message?.take(80).orEmpty()
                        )
                    }
                }
            }
            //我的反馈：提交过的都在这，点开看进度、留言、评价
            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.fb_mine), stringResource(R.string.fb_mine_hint))
            if (mine.isEmpty()) {
                CardBox {
                    Text(stringResource(R.string.fb_mine_empty), fontSize = 12.sp, color = TextSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mine.forEach { it -> MineRow(it) { onOpenDetail(it.id) } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    } //反馈页
    } //兜底容器
    } //玻璃作用域
} //意见反馈

/** 我的反馈一行：编号 + 状态 + 最近一条消息摘要 */
@Composable
private fun MineRow(it: Feedback.Item, onClick: () -> Unit) {
    val preview = when {
        it.lastText.isNotBlank() && it.lastRole == "admin" -> stringResource(R.string.fb_from_admin) + "：" + it.lastText
        it.lastText.isNotBlank() -> stringResource(R.string.fb_from_me) + "：" + it.lastText
        else -> it.updatedAt
    }
    Column(
        Modifier
            .fillMaxWidth()
            .liquidBg(RoundedCornerShape(14.dp))
            .tap { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${it.id}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            StateChip(it.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preview,
            fontSize = 11.sp,
            color = if (it.lastRole == "admin") TextPrimary else TextSecondary,
            lineHeight = 16.sp
        )
        if (it.rating > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "★".repeat(it.rating) + "☆".repeat(5 - it.rating),
                fontSize = 11.sp,
                color = WarnAmber
            )
        }
    }
} //我的反馈一行

/** 状态胶囊：待处理橙 / 处理中蓝 / 已处理绿 / 已结束灰 */
@Composable
internal fun StateChip(status: Int) {
    val label = when (status) {
        Feedback.PENDING -> stringResource(R.string.fb_st_pending)
        Feedback.PROCESSING -> stringResource(R.string.fb_st_processing)
        Feedback.DONE -> stringResource(R.string.fb_st_done)
        else -> stringResource(R.string.fb_st_closed)
    }
    val fg = when (status) {
        Feedback.PENDING -> WarnAmber
        Feedback.PROCESSING -> AccentBlue
        Feedback.DONE -> OkGreen
        else -> TextSecondary
    }
    val bg = when (status) {
        Feedback.PENDING -> WarnAmberSoft
        Feedback.PROCESSING -> AccentBlueSoft
        Feedback.DONE -> OkGreen.copy(alpha = 0.12f)
        else -> SurfaceMuted
    }
    Text(
        label,
        fontSize = 11.sp,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
} //状态胶囊

/** 分类胶囊：选中填蓝，未选灰底 */
@Composable
private fun CatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (selected) AccentBlue else TextSecondary,
        modifier = Modifier
            .liquidBg(RoundedCornerShape(10.dp), tint = if (selected) AccentBlueSoft else SurfaceMuted)
            .tap { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
} //分类胶囊

/** 附件小按钮：不可用了就变灰且不响应 */
@Composable
private fun AttBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (enabled) AccentBlue else TextSecondary,
        modifier = Modifier
            .liquidBg(RoundedCornerShape(9.dp), tint = if (enabled) AccentBlueSoft else SurfaceMuted)
            .tap { if (enabled) onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
} //附件按钮

/** 附件不合格的原因，界面翻成对应文案 */
private enum class AttErr { BIG_IMAGE, BIG_LOG, BAD_EXT, IMG_MAX, READ_FAIL }

/** 日志只认这四种文本后缀，和服务器白名单一致 */
private val LOG_EXT = listOf(".txt", ".log", ".json", ".md")

/** 从 uri 读成附件：先按元数据挡大小，再读进内存复查；返回 (附件, 错误) */
private fun readAttach(ctx: Context, uri: Uri, type: String): Pair<Feedback.Attach?, AttErr?> {
    val cr = ctx.contentResolver
    var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    var size = 0L
    runCatching {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) c.getString(ni)?.let { name = it }
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    }
    if (name.isBlank()) name = "file"
    val mime = cr.getType(uri).orEmpty()
    val limit = if (type == Feedback.TYPE_IMAGE) 5L * 1024 * 1024 else 1024L * 1024
    if (size > limit) {
        return null to if (type == Feedback.TYPE_IMAGE) AttErr.BIG_IMAGE else AttErr.BIG_LOG
    }
    if (type == Feedback.TYPE_LOG) {
        val low = name.lowercase()
        if (LOG_EXT.none { low.endsWith(it) }) return null to AttErr.BAD_EXT
    } else if (mime.isNotEmpty() && !mime.startsWith("image/")) {
        return null to AttErr.READ_FAIL
    }
    val bytes = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        ?: return null to AttErr.READ_FAIL
    if (bytes.size.toLong() > limit) {
        return null to if (type == Feedback.TYPE_IMAGE) AttErr.BIG_IMAGE else AttErr.BIG_LOG
    }
    return Feedback.Attach(type, name, if (mime.isNotEmpty()) mime else "application/octet-stream", bytes) to null
} //读附件

/** 附件体积文案：不足 1MB 用 KB */
private fun sizeText(n: Long): String =
    if (n >= 1024 * 1024) String.format("%.1f MB", n / 1048576.0) else "${maxOf(1L, n / 1024)} KB"

/** 图片缩略图：先读边长算采样率，避免 5MB 大图直接解码把内存打满 */
private fun smallBitmap(bytes: ByteArray, maxSide: Int): android.graphics.Bitmap? {
    val opt = BitmapFactory.Options()
    opt.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
    var sample = 1
    while (opt.outWidth / sample > maxSide || opt.outHeight / sample > maxSide) {
        sample *= 2
    }
    val real = BitmapFactory.Options()
    real.inSampleSize = sample
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real) }.getOrNull()
} //缩略图