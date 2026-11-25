/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 22:12:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 本文件作用：内置 WebView 的 GitHub 登录页。
 *  打开 github.com/login，用户在里面正常登录（含两步验证），
 *  页面一出现 user_session 就抓 cookie 回写，成功后自动关闭，失败把原因写在底部。
 */

package b.ai.ui.screen.user

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import b.ai.net.api.github.GitHubLogin
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.GhostButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 内置登录页开关：true 时主界面挂一张全屏页 */
object GhWeb {
    var open by mutableStateOf(false)
}

/**
 * 内置 GitHub 登录页。
 * 抓取口径：CookieManager 里 github.com 的 cookie 出现 user_session 就当登录完成，
 * 交给 GitHubLogin.adopt 再验一次（拿 cookie 请求首页抠 user-login），验过才算成功。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GhWebPage(onDone: () -> Unit, onClose: () -> Unit) {
    var msg by remember { mutableStateOf("登录后会自己回来，不用手动关页面") }
    var ok by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    //轮询 cookie：GitHub 登录成功后立刻就会种 user_session，1.2 秒一轮足够快也不费电
    LaunchedEffect(Unit) {
        while (!finished) {
            delay(1200)
            val ck = runCatching { CookieManager.getInstance().getCookie("https://github.com") }.getOrNull().orEmpty()
            if (ck.contains("user_session=")) {
                msg = "拿到 cookie，正在核对账号…"
                val r = withContext(Dispatchers.IO) { GitHubLogin.adopt(ck) }
                r.onSuccess {
                    ok = true
                    finished = true
                    msg = "已登录：@$it"
                    delay(600)
                    onDone()
                }.onFailure {
                    //已经种上 user_session 却验不过，基本是会话过期或 cookie 不全，重试无意义，停下等用户取消
                    msg = "核对失败：" + it.message.orEmpty()
                    finished = true
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceWhite)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("GitHub 登录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "不经过任何自有服务器，登录后只把 cookie 存进本机加密库",
                    fontSize = 10.sp, color = TextSecondary
                )
            }
            GhostButton("取消") { onClose() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        setBackgroundColor(AColor.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(false)
                        settings.userAgentString = settings.userAgentString + " Baicodex"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                //每次页面落地就把 cookie 刷进磁盘，抓取那边才读得到最新值
                                runCatching { CookieManager.getInstance().flush() }
                            }
                        }
                        loadUrl("https://github.com/login")
                    }
                }
            )
        }
        Text(
            msg,
            fontSize = 11.sp,
            color = when {
                ok -> OkGreen
                msg.startsWith("已登录") -> OkGreen
                msg.contains("没") || msg.contains("无效") || msg.contains("过期") -> DangerRed
                else -> TextSecondary
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(2.dp))
    }
} //内置 GitHub 登录页