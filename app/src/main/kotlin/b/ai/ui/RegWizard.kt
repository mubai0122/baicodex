package b.ai.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Avatar
import b.ai.net.Auth
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 注册向导的共享状态：三页各读自己那段，省掉一长串参数 */
private class RegSt {
    var face by mutableStateOf("")
    var nick by mutableStateOf("")
    var uname by mutableStateOf("")
    var email by mutableStateOf("")
    var code by mutableStateOf("")
    var pass by mutableStateOf("")
    var bio by mutableStateOf("")
    var site by mutableStateOf("")
    var page by mutableIntStateOf(0)
    var sending by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var err by mutableStateOf("")
    var cold by mutableLongStateOf(0L)
    var now by mutableLongStateOf(System.currentTimeMillis())
} //注册共享状态

/** 轻量输入框：自绘 BasicTextField + 玻璃底。比 OutlinedTextField 少一层状态机，弹窗首帧快一半 */
@Composable
private fun AccField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    minLines: Int = 1
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 13.sp, color = TextSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                singleLine = minLines <= 1,
                minLines = minLines,
                cursorBrush = SolidColor(AccentBlue),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
} //轻量输入框

/** 提示行：ok 决定绿还是红 */
@Composable
private fun RegTip(text: String, ok: Boolean) {
    if (text.isBlank()) return
    Text(
        text,
        fontSize = 11.sp,
        color = if (ok) b.ai.ui.theme.OkGreen else DangerRed,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
} //提示行

/**
 * 注册引导：一页一屏分页渲染。
 * 原来是三页并排 + 逐像素位移，横向滑出时旁边页的控件会一起画出来（看着就是错乱），
 * 而且三页永远同时在场，测量与重组成本翻三倍。
 */
@Composable
internal fun RegWizard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember { RegSt() }
    val emailOk = ACC_EMAIL_RE.matches(st.email.trim())
    val unameOk = ACC_UNAME_RE.matches(st.uname.trim())
    val p1 = st.nick.trim().isNotEmpty() && unameOk
    val p2 = emailOk && st.code.length == 6 && st.pass.length >= 6
    val waitSec = (((st.cold - st.now) / 1000).toInt()).coerceAtLeast(0)

    //验证码冷却：500ms 一跳，只驱动秒数往下走
    LaunchedEffect(st.cold) {
        while (st.cold > System.currentTimeMillis()) {
            st.now = System.currentTimeMillis()
            delay(500)
        }
        st.now = System.currentTimeMillis()
    }

    //选头像：落成本地小图，注册时一起传
    val pickFace = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val p = withContext(Dispatchers.IO) { Avatar.importFrom(ctx, uri) }
            if (p.isNullOrBlank()) st.err = ctx.getString(R.string.acc_face_bad) else st.face = p
        }
    }

    fun submit() {
        if (!(p1 && p2) || st.busy) return
        st.busy = true
        st.err = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    Auth.register(
                        email = st.email,
                        code = st.code,
                        uname = st.uname,
                        nick = st.nick,
                        pass = st.pass,
                        bio = st.bio,
                        site = st.site,
                        avatar = st.face.takeIf { it.isNotBlank() }?.let { File(it) }
                    )
                }
            }
            st.busy = false
            r.onSuccess { AccUi.done() }.onFailure { st.err = it.message?.take(80).orEmpty() }
        }
    } //提交注册

    ScrimCard(onDismiss = { if (!st.busy) AccUi.close() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.acc_reg), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            //三个指示点：当前页拉长
            repeat(3) { i ->
                Box(
                    Modifier
                        .padding(start = 5.dp)
                        .height(4.dp)
                        .width(if (i == st.page) 16.dp else 6.dp)
                        .clip(CircleShape)
                        .then(
                            if (i == st.page) Modifier.liquidBg(CircleShape, tint = AccentBlueSoft)
                            else Modifier.liquidBg(CircleShape, tint = SurfaceMuted)
                        )
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.acc_reg_tip), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        //只渲染当前页：卡片高度跟着内容走，不会再有并排三页互相压上的情况
        when (st.page) {
            0 -> FacePage(st, unameOk, pickFace)
            1 -> MailPage(st, emailOk, waitSec, scope, ctx)
            else -> AboutPage(st)
        }
        RegTip(st.err, false)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st.page > 0) Pill(stringResource(R.string.acc_back), on = !st.busy) { st.page -= 1 }
            Spacer(Modifier.weight(1f))
            Pill(stringResource(R.string.acc_later), on = !st.busy) { AccUi.close() }
            when (st.page) {
                0 -> Pill(stringResource(R.string.acc_next), on = p1) { st.page = 1 }
                1 -> Pill(stringResource(R.string.acc_next), on = p2) { st.page = 2 }
                else -> Pill(
                    if (st.busy) stringResource(R.string.acc_logging) else stringResource(R.string.acc_finish),
                    on = p1 && p2 && !st.busy
                ) { submit() }
            }
        }
    }
} //注册向导

/** 第一页：头像 + 昵称 + 用户名 */
@Composable
private fun FacePage(st: RegSt, unameOk: Boolean, pickFace: ActivityResultLauncher<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .liquidBg(CircleShape, tint = SurfaceMuted)
                .tap { if (!st.busy) pickFace.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (st.face.isBlank()) {
                Text(stringResource(R.string.acc_pick_face), fontSize = 10.sp, color = TextSecondary)
            } else {
                AvatarView(st.face, 72.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.acc_face_tip), fontSize = 12.sp, color = TextPrimary)
            Text(stringResource(R.string.acc_face_sub), fontSize = 10.sp, color = TextSecondary)
        }
    }
    AccField(
        label = stringResource(R.string.acc_nick),
        value = st.nick,
        onValueChange = { st.nick = it.take(24) },
        placeholder = stringResource(R.string.acc_nick_ph)
    )
    AccField(
        label = stringResource(R.string.acc_uname),
        value = st.uname,
        onValueChange = { st.uname = it.take(24) },
        placeholder = stringResource(R.string.acc_uname_ph)
    )
    if (st.uname.isNotBlank() && !unameOk) {
        Text(stringResource(R.string.acc_uname_bad), fontSize = 10.sp, color = DangerRed)
    }
} //第一页

/** 第二页：邮箱 + 验证码 + 密码 */
@Composable
private fun MailPage(
    st: RegSt,
    emailOk: Boolean,
    waitSec: Int,
    scope: CoroutineScope,
    ctx: Context
) {
    AccField(
        label = stringResource(R.string.acc_email),
        value = st.email,
        onValueChange = { st.email = it.take(128) },
        placeholder = stringResource(R.string.acc_email_ph)
    )
    if (st.email.isNotBlank() && !emailOk) {
        Text(stringResource(R.string.acc_email_bad), fontSize = 10.sp, color = DangerRed)
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
            AccField(
                label = stringResource(R.string.acc_code),
                value = st.code,
                onValueChange = { st.code = it.filter { c -> c.isDigit() }.take(6) },
                placeholder = stringResource(R.string.acc_code_ph)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.padding(bottom = 8.dp)) {
            Pill(
                when {
                    st.sending -> stringResource(R.string.acc_sending)
                    waitSec > 0 -> stringResource(R.string.acc_wait, waitSec)
                    else -> stringResource(R.string.acc_send)
                },
                on = emailOk && waitSec <= 0 && !st.sending
            ) {
                if (!emailOk) {
                    st.err = ctx.getString(R.string.acc_email_bad)
                    return@Pill
                }
                st.sending = true
                st.err = ""
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Auth.sendCode(st.email) } }
                    st.sending = false
                    r.onSuccess { st.cold = System.currentTimeMillis() + 60_000 }
                        .onFailure { st.err = it.message?.take(80).orEmpty() }
                }
            }
        }
    }
    AccField(
        label = stringResource(R.string.acc_pass),
        value = st.pass,
        onValueChange = { st.pass = it.take(64) },
        placeholder = stringResource(R.string.acc_pass_ph),
        secret = true
    )
    Text(stringResource(R.string.acc_mail_tip), fontSize = 10.sp, color = TextSecondary)
} //第二页

/** 第三页：简介 + 官网，都可空 */
@Composable
private fun AboutPage(st: RegSt) {
    AccField(
        label = stringResource(R.string.acc_bio),
        value = st.bio,
        onValueChange = { st.bio = it.take(200) },
        placeholder = stringResource(R.string.acc_bio_ph),
        minLines = 2
    )
    AccField(
        label = stringResource(R.string.acc_site),
        value = st.site,
        onValueChange = { st.site = it.take(120) },
        placeholder = stringResource(R.string.acc_site_ph)
    )
    Text(stringResource(R.string.acc_site_tip), fontSize = 10.sp, color = TextSecondary)
} //第三页
