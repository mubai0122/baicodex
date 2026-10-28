package b.ai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Avatar
import b.ai.core.Cfg
import b.ai.net.Auth
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** 邮箱格式：够用的宽松校验，和反馈页保持一致 */
internal val ACC_EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** 用户名规则与服务端一致：字母开头，3-24 位字母数字下划线 */
internal val ACC_UNAME_RE = Regex("^[A-Za-z][A-Za-z0-9_]{2,23}$")

/** 账号弹窗状态：login 登录 / reg 注册引导 / edit 改资料，空串 = 全关；stamp 每次登录态变化 +1 */
object AccUi {
    var mode by mutableStateOf("")
    var stamp by mutableIntStateOf(0)
    fun open(m: String) { mode = m }
    fun close() { mode = "" }
    fun done() { mode = ""; stamp++ }
}

/** 账号三张卡：由外层 HostInOverlay 统一挂载，卡片自己不再铺遮罩 */
@Composable
fun AccDialogs() {
    when (AccUi.mode) {
        "login" -> LoginCard()
        "reg" -> RegWizard()
        "edit" -> EditCard()
    }
} //账号弹窗组

/** 账号头像：远端地址先落到本地缓存再显示，没图就退回默认人形 */
@Composable
fun AccFace(uid: Int, url: String, side: Dp) {
    //头像要先落本地：下载放 IO 线程，别在组合里卡首帧
    var path by remember(uid, url) { mutableStateOf("") }
    LaunchedEffect(uid, url) {
        if (url.isNotBlank()) path = withContext(Dispatchers.IO) { Auth.face(uid, url) }
    }
    AvatarView(path, side)
} //账号头像

/** 一行小字提示：ok 决定绿色还是红色 */
@Composable
private fun AccTip(text: String, ok: Boolean) {
    if (text.isBlank()) return
    Text(
        text,
        fontSize = 11.sp,
        color = if (ok) OkGreen else DangerRed,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
} //提示行

/** 小胶囊按钮：注册向导的「下一步」与次要动作共用 */
@Composable
internal fun Pill(text: String, on: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = if (on) AccentBlue else TextSecondary,
        modifier = Modifier
            .liquidBg(RoundedCornerShape(10.dp), tint = if (on) AccentBlueSoft else SurfaceMuted)
            .tap(enabled = on) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
} //小胶囊

/** 登录：用户名或邮箱 + 密码 */
@Composable
private fun LoginCard() {
    val scope = rememberCoroutineScope()
    var acc by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val can = acc.trim().length >= 3 && pass.length >= 6 && !busy

    fun submit() {
        if (!can) return
        busy = true
        err = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Auth.login(acc, pass) }
            }
            busy = false
            r.onSuccess { AccUi.done() }.onFailure { err = it.message?.take(80).orEmpty() }
        }
    }

    ScrimCard(onDismiss = { if (!busy) AccUi.close() }) {
        Text(stringResource(R.string.acc_login), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.acc_login_tip), fontSize = 11.sp, color = TextSecondary)
        Field(
            label = stringResource(R.string.acc_account),
            value = acc,
            onValueChange = { acc = it.take(128) },
            placeholder = stringResource(R.string.acc_account_ph)
        )
        Field(
            label = stringResource(R.string.acc_pass),
            value = pass,
            onValueChange = { pass = it.take(64) },
            placeholder = stringResource(R.string.acc_pass_ph),
            secret = true
        )
        AccTip(err, false)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = if (busy) stringResource(R.string.acc_logging) else stringResource(R.string.acc_login),
            enabled = can,
            modifier = Modifier.fillMaxWidth()
        ) { submit() }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(R.string.acc_reg)) { AccUi.open("reg") }
            Spacer(Modifier.width(8.dp))
            Pill(stringResource(R.string.acc_later), on = !busy) { AccUi.close() }
        }
    }
} //登录卡


/** 改资料：昵称 / 简介 / 官网 / 换头像，顺手放退出登录 */
@Composable
private fun EditCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(Auth.cached()) }
    var nick by remember { mutableStateOf(Cfg.accNick) }
    var bio by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }

    //进卡先拉一次自己的资料，顺便校验 token 还有效
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { runCatching { Auth.me() } }
        r.onSuccess { u ->
            user = u
            nick = u.nick
            bio = u.bio
            site = u.site
        }.onFailure {
            ok = false
            tip = it.message?.take(80).orEmpty()
        }
    }

    val pickFace = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val p = Avatar.importFrom(ctx, uri) ?: error(ctx.getString(R.string.acc_face_bad))
                    Auth.uploadAvatar(File(p))
                }
            }
            busy = false
            r.onSuccess { user = it; ok = true; tip = ctx.getString(R.string.acc_saved) }
                .onFailure { ok = false; tip = it.message?.take(80).orEmpty() }
        }
    }

    BottomSheetCard(onDismiss = { if (!busy) AccUi.close() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccFace(user.uid, user.avatar, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.nick.ifBlank { stringResource(R.string.acc_guest) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text("@" + user.uname, fontSize = 11.sp, color = TextSecondary)
            }
            Pill(stringResource(R.string.acc_pick_face), on = !busy) { pickFace.launch("image/*") }
        }
        Field(
            label = stringResource(R.string.acc_nick),
            value = nick,
            onValueChange = { nick = it.take(24) }
        )
        Field(
            label = stringResource(R.string.acc_bio),
            value = bio,
            onValueChange = { bio = it.take(200) },
            placeholder = stringResource(R.string.acc_bio_ph),
            singleLine = false,
            minLines = 2
        )
        Field(
            label = stringResource(R.string.acc_site),
            value = site,
            onValueChange = { site = it.take(120) },
            placeholder = stringResource(R.string.acc_site_ph)
        )
        AccTip(tip, ok)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = when {
                busy -> stringResource(R.string.acc_saving)
                else -> stringResource(R.string.acc_save)
            },
            enabled = !busy && nick.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) return@PrimaryButton
            busy = true
            tip = ""
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    runCatching { Auth.save(nick = nick.trim(), bio = bio.trim(), site = site.trim()) }
                }
                busy = false
                r.onSuccess { user = it; ok = true; tip = ctx.getString(R.string.acc_saved); AccUi.stamp++ }
                    .onFailure { ok = false; tip = it.message?.take(80).orEmpty() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(R.string.acc_close), on = !busy) { AccUi.close() }
            Spacer(Modifier.weight(1f))
            Pill(stringResource(R.string.acc_logout), on = !busy) {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { Auth.logout() } }
                    AccUi.done()
                }
            }
        }
    }
} //改资料卡