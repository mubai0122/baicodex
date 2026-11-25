/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 22:10:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 本文件作用：GitHub 登录（网页拿 cookie 形态），全程不经过任何自有服务器。
 *  ① 内置 WebView 打开 github.com/login，登录成功后把 CookieManager 里的 cookie 抓出来回写；
 *  ② 外部浏览器登录后，把浏览器里的 cookie 粘回来，同样回写。
 *  cookie 与昵称只进加密库（Secret），界面只回显「已登录 + @用户名」，不回显 cookie 原文。
 */

package b.ai.net.api.github

import b.ai.App
import b.ai.core.cfg.secret.Secret
import b.ai.core.data.io.Layout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GitHubLogin {
    private const val K_COOKIE = "gh_cookie"      //github.com 的 cookie 原文
    private const val K_USER = "gh_user"          //登录用户名（小写 login）
    private const val K_FACE = "gh_face"          //头像下载地址

    /**普通浏览器 UA：GitHub 对非浏览器 UA 会走精简页，meta 里就没有 user-login */
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    /**登录页里 <meta name="user-login" content="xxx">，两个属性顺序都见过，两条正则都留着 */
    private val META_A = Regex("name=\"user-login\"\\s+content=\"([^\"]+)\"")
    private val META_B = Regex("content=\"([^\"]+)\"\\s+name=\"user-login\"")

    /** 已登录：加密库里有 cookie 就算 */
    val logged: Boolean get() = Secret.get(K_COOKIE).isNotBlank()

    fun cookie(): String = Secret.get(K_COOKIE)

    fun user(): String = Secret.get(K_USER)

    /** 头像地址：GitHub 按用户名直出，不用调 REST API */
    fun avatarUrl(): String {
        val u = user()
        return if (u.isBlank()) "" else "https://github.com/$u.png?size=200"
    } //头像地址

    /**
     * 回写：把网页里拿到的 cookie 存下来，并当场验证一次。
     * 验证方式是拿这串 cookie 请求 github.com 首页，能抠出 user-login 才算真登录。
     */
    fun adopt(raw: String): Result<String> {
        val ck = clean(raw)
        if (ck.isBlank()) return Result.failure(IllegalStateException("没读到 cookie：至少要包含 user_session"))
        val name = who(ck)
            ?: return Result.failure(IllegalStateException("这串 cookie 没被 GitHub 认成登录态（可能过期或复制不全），重新登录再试"))
        Secret.put(K_COOKIE, ck)
        Secret.put(K_USER, name)
        Secret.put(K_FACE, avatarUrl())
        return Result.success(name)
    } //回写登录态

    /** 退出登录：只清本地，GitHub 侧的会话在浏览器里，不归我们管 */
    fun logout() {
        Secret.put(K_COOKIE, "")
        Secret.put(K_USER, "")
        Secret.put(K_FACE, "")
    } //退出

    /** 头像落本地缓存（同账号体系的目录口径），拿不到就返回空串，界面退回默认人形 */
    fun face(): String {
        val u = user()
        if (u.isBlank()) return ""
        val d = Layout.dir(App.self, Layout.FACE).apply { runCatching { mkdirs() } }
        val f = File(d, "gh-" + u.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".png")
        if (f.length() > 0L) return f.absolutePath
        val url = Secret.get(K_FACE).ifBlank { avatarUrl() }
        if (url.isBlank()) return ""
        val ok = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", UA)
            try {
                c.inputStream.use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
            } finally {
                c.disconnect()
            }
            f.length() > 0L
        }.getOrDefault(false)
        if (!ok) {
            runCatching { f.delete() }
            return ""
        }
        return f.absolutePath
    } //头像缓存

    /** 多行 / 带 Cookie: 前缀的粘贴都能认：优先取含 user_session 的那行 */
    private fun clean(raw: String): String {
        val lines = raw.split('\n', '\r').map { it.trim() }.filter { it.contains('=') }
        val hit = lines.firstOrNull { it.contains("user_session") } ?: lines.firstOrNull().orEmpty()
        return hit.removePrefix("Cookie:").removePrefix("cookie:").trim()
    } //清洗 cookie

    /** 用 cookie 请求首页，从 meta 里抠登录用户名；返回 null 表示这串 cookie 无效 */
    private fun who(ck: String): String? = runCatching {
        val c = URL("https://github.com/").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        c.setRequestProperty("Cookie", ck)
        val html = try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        (META_A.find(html) ?: META_B.find(html))?.groupValues?.get(1)
    }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
} //GitHub 登录：网页 cookie 形态，不经过自有服务器
