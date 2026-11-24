/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 22:15:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 本文件作用：GitHub 账号卡（替代原来走自有服务器的账号卡）。
 *  两条登录路径：① 应用内 WebView 登录，cookie 自动回写；② 外部浏览器登录，cookie 粘回来回写。
 *  全程不碰任何自有服务器，cookie 只进本机加密库。
 */

package b.ai.ui.screen.user

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.net.api.github.GitHubLogin
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.AvatarView
import b.ai.ui.widget.IcClose
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 账号弹窗状态：login 未登录引导 / edit 已登录管理，空串 = 全关；stamp 每次登录态变化 +1 */
object GhUi {
    var mode by mutableStateOf("")
    var stamp by mutableIntStateOf(0)
    fun open(m: String) { mode = m }
    fun close() { mode = "" }
    fun done() { mode = ""; stamp++ }
} //账号弹窗状态

/** 账号卡：由主界面弹窗层统一挂载 */
@Composable
fun GhDialogs() {
    if (GhUi.mode.isNotEmpty()) GhCard()
} //账号卡组

/** GitHub 头像：本地缓存文件，没图退回默认人形；stamp 变化时重取一次 */
@Composable
fun GhFace(side: Dp) {
    var path by remember(GhUi.stamp) { mutableStateOf("") }
    LaunchedEffect(GhUi.stamp) {
        path = withContext(Dispatchers.IO) { GitHubLogin.face() }
    }
    AvatarView(path, side)
} //GitHub 头像

/** 账号卡本体：未登录给两条登录路径，已登录给账号信息与退出 */
@Composable
private fun GhCard() {
    val ctx = LocalContext.current
    var paste by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val user = GitHubLogin.user()
    val on = GitHubLogin.logged

    val scope = rememberCoroutineScope()

    /** 回写：网络校验放 IO，成功后关卡片并让头像重取 */
    fun adopt(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) {
            tip = "先把 cookie 粘进来"
            ok = false
            return
        }
        if (busy) return
        busy = true
        scope.launch {
            //GitHubLogin.adopt 内部会请求一次 github.com 做身份校验，必须离开主线程
            val r = withContext(Dispatchers.IO) { GitHubLogin.adopt(t) }
            busy = false
            r.onSuccess { u ->
                ok = true
                tip = "已登录：@$u"
                paste = ""
                GhUi.done()
            }.onFailure {
                ok = false
                tip = it.message.orEmpty()
            }
        }
    } //回写

    ScrimCard(onDismiss = { if (!busy) GhUi.close() }) {
        //标题行：右上角一枚玻璃圆圈包着的叉（SVG 描边），替代原来压在最底部的「关闭」按钮
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("GitHub 账号", fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(26.dp)
                    .liquidBg(CircleShape, tint = SurfaceWhite, veil = 0.3f, gate = 0.dp)
                    .tap(enabled = !busy) { GhUi.close() },
                contentAlignment = Alignment.Center
            ) { IcClose(TextSecondary, side = 12.dp) }
        }
        Text(
            "登录只用于本地身份显示（头像 + 用户名），cookie 存进本机加密库，不经过任何自有服务器。",
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (on) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhFace(44.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("@$user", fontSize = 14.sp, color = TextPrimary)
                    Text("已登录 · cookie 已存本机", fontSize = 10.sp, color = OkGreen)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                GhostButton("重新登录", modifier = Modifier.height(38.dp)) {
                    GhUi.open("login")
                }
                Spacer(Modifier.width(10.dp))
                GhostButton("退出登录", modifier = Modifier.height(38.dp)) {
                    GitHubLogin.logout()
                    tip = "已退出登录"
                    ok = true
                    GhUi.done()
                }
            }
        } else {
            Text("① 应用内登录（推荐）：登录完自动回写", fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            GhostButton("在应用内登录 GitHub", modifier = Modifier.height(38.dp)) {
                GhWeb.open = true
            }
            Spacer(Modifier.height(12.dp))
            Text("② 外部浏览器登录：登录后把 cookie 粘回下面", fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Row {
                GhostButton("用浏览器打开登录页", modifier = Modifier.height(38.dp)) {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/login"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    tip = "登录完成后回到这里，把 github.com 的 cookie 粘到下面"
                    ok = false
                }
                Spacer(Modifier.width(10.dp))
                GhostButton("从剪贴板读取", modifier = Modifier.height(38.dp)) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val t = cm?.primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null }
                    if (t.isNullOrBlank()) {
                        tip = "剪贴板里没有文本"
                        ok = false
                    } else {
                        paste = t
                        adopt(t)
                    }
                }
            }
            Field(
                label = "github.com cookie",
                value = paste,
                onValueChange = { paste = it },
                placeholder = "user_session=…; _gh_sess=…",
                secret = true,
                singleLine = false,
                minLines = 3,
                //封顶 5 行：整串 cookie 动辄十几行，不封顶会把下面的按钮顶出卡片
                maxLines = 5
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(if (busy) "正在核对…" else "回写这串 cookie", modifier = Modifier.height(38.dp)) {
                adopt(paste)
            }
            Text(
                "cookie 只存本机加密库，界面上不会回显；换账号直接重新登录即可。",
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (tip.isNotBlank()) {
            Text(
                tip,
                fontSize = 11.sp,
                color = if (ok) OkGreen else DangerRed,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
} //账号卡