package b.ai.core.net

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 清网页侧残留：Cookie、WebStorage、WebView 缓存与表单历史，用于一键退出所有网页登录态 */
object WebWipe {
    /** 必须在主线程调用：WebView 的构造与 clearXxx 都只能在主线程 */
    fun clearAll(ctx: Context): String {
        val cm = runCatching { CookieManager.getInstance() }.getOrNull()
        var cookies = -1
        if (cm != null) {
            runCatching { cm.setAcceptCookie(true) }
            runCatching {
                val latch = CountDownLatch(1)
                cm.removeAllCookies {
                    cookies = 0
                    latch.countDown()
                }
                latch.await(2, TimeUnit.SECONDS)
            }
            runCatching { cm.removeSessionCookies(null) }
            runCatching { cm.flush() }
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        val cacheOk = runCatching {
            val wv = WebView(ctx.applicationContext)
            wv.clearCache(true)
            wv.clearFormData()
            wv.clearHistory()
            wv.destroy()
            true
        }.getOrDefault(false)
        return buildString {
            append("已清除 Cookie")
            if (cookies == -1) append("（无待清的 Cookie）")
            append("、WebStorage")
            if (cacheOk) append("、WebView 缓存与表单")
            append("。音乐专区的登录态在加密库里，不受影响，要清请到该平台卡片单独清。")
        }
    } //一键清网页数据
} //WebView 与 Cookie 清理