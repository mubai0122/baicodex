package b.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    //邮箱必填：格式不对就不让提交
    val emailOk = EMAIL_RE.matches(contact.trim())
    val scope = rememberCoroutineScope()
    BackHandler(enabled = !busy) { onBack() }
    //进页面拉一次我的反馈，提交成功后 reload+1 再拉一次
    LaunchedEffect(reload) {
        mine = withContext(Dispatchers.IO) {
            runCatching { Feedback.list() }.getOrDefault(emptyList())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgGray)
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
                    .background(SurfaceWhite, RoundedCornerShape(9.dp))
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
                        runCatching { Feedback.submit(cat, content, contact) }
                    }
                    busy = false
                    r.onSuccess {
                        content = ""
                        contact = ""
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
            .background(SurfaceWhite, RoundedCornerShape(14.dp))
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
            .background(if (selected) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(10.dp))
            .tap { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
} //分类胶囊