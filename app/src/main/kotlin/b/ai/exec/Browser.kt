package b.ai.exec

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 浏览器 Agent：单实例 WebView 常驻在窗口里（1×1 不可见），Agent 用 JS 读正文、点链接、填表单 */
object Browser {
    private var view: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private val loading = AtomicBoolean(false)
    private var curUrl = ""
    private var curTitle = ""

    /** 取当前 WebView：没有就建一个，挂到窗口上（不挂的 WebView 在某些机型上会冻住 JS） */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensure(): WebView {
        view?.let { return it }
        val ctx = b.ai.App.act ?: b.ai.App.self
        val w = WebView(ctx)
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadsImagesAutomatically = false
        s.blockNetworkImage = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.userAgentString = s.userAgentString.orEmpty() + " BaiAgent/1.6"
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                loading.set(true)
            }

            override fun onPageFinished(v: WebView?, u: String?) {
                loading.set(false)
                curUrl = v?.url.orEmpty()
                curTitle = runCatching { v?.title.orEmpty() }.getOrDefault("")
            }

            override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse? {
                //图片一律不加载：Agent 只要文字，省流量也省时间
                val t = r?.requestHeaders?.get("Accept").orEmpty()
                if (r?.url?.path.orEmpty().matches(Regex(""".*\.(png|jpe?g|gif|webp|svg|ico)$""", RegexOption.IGNORE_CASE)) ||
                    t.startsWith("image/")
                ) {
                    return WebResourceResponse("image/png", null, null)
                }
                return null
            }
        }
        view = w
        //挂进窗口但缩到 1×1 透明：既不停 JS，也不影响用户看到的内容
        runCatching {
            val act = b.ai.App.act
            val root = act?.window?.decorView as? ViewGroup
            if (root != null) {
                w.alpha = 0f
                root.addView(w, ViewGroup.LayoutParams(1, 1))
            }
        }
        return w
    } //建立并挂载 WebView

    private fun <T> onMain(ms: Long, block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        var out: T? = null
        val gate = CountDownLatch(1)
        main.post {
            out = runCatching(block).getOrNull()
            gate.countDown()
        }
        return if (gate.await(ms, TimeUnit.MILLISECONDS)) out else null
    } //切主线程取结果

    private fun awaitLoad(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (!loading.get()) return
            Thread.sleep(120)
        }
    } //等页面加载完

    private fun unquote(raw: String?): String {
        if (raw.isNullOrEmpty() || raw == "null") return ""
        if (!raw.startsWith("\"")) return raw
        return runCatching { org.json.JSONArray("[$raw]").optString(0) }.getOrDefault(raw)
    }

    private fun js(script: String, ms: Long = 15_000): String {
        val w = onMain(ms) { ensure() } ?: return "失败：主线程忙，脚本没跑起来"
        val gate = CountDownLatch(1)
        var out = ""
        main.post {
            val ok = runCatching {
                w.evaluateJavascript(script) { r ->
                    out = unquote(r)
                    gate.countDown()
                }
            }
            if (ok.isFailure) gate.countDown()
        }
        if (!gate.await(ms, TimeUnit.MILLISECONDS)) return "失败：脚本执行超时（页面可能还在加载）"
        return out
    } //执行 JS 并取回字符串

    private fun q(s: String): String = JSONObject.quote(s)

    private fun head(): String {
        val info = onMain(3_000) { "${curTitle.ifBlank { "无标题" }}｜${curUrl}" }.orEmpty()
        return info
    } //标题与地址

    fun run(action: String, url: String, text: String, target: String, script: String, limit: Int): String {
        val act = action.trim().lowercase().ifEmpty { "open" }
        if (act == "open" || act == "go") {
            val u = url.trim()
            if (u.isEmpty()) return "失败：open 需要 url"
            val full = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
            val w = onMain(10_000) { ensure() } ?: return "失败：浏览器没起来"
            loading.set(true)
            main.post { runCatching { w.loadUrl(full) } }
            awaitLoad(25_000)
            val body = js(SCRIPT_TEXT, 20_000)
            if (body.isBlank()) {
                return head() + "\n\n（没读到正文：http 明文可能被系统拦了，或页面还在渲染。" +
                    "换 https 地址，或稍后再 action=text 读一次）"
            }
            val cut = if (body.length > 4000) body.substring(0, 4000) + "\n…已截断" else body
            return head() + "\n\n" + cut
        }
        if (act == "eval") {
            val s = script.ifBlank { text }
            if (s.isBlank()) return "失败：eval 需要 script"
            return "JS 返回：\n" + js(s).take(6000)
        }
        return when (act) {
            "text" -> {
                val r = js(SCRIPT_TEXT, 20_000)
                val cap = limit.coerceIn(500, 20_000)
                head() + "\n\n" + (if (r.length > cap) r.substring(0, cap) + "\n…已截断（共 ${r.length} 字）" else r)
            }
            "links" -> head() + "\n\n" + js(SCRIPT_LINKS).ifBlank { "页面上没有链接" }
            "find" -> {
                val k = text.ifBlank { target }
                if (k.isBlank()) return "失败：find 需要 text（要找的文字）"
                head() + "\n\n" + js(SCRIPT_FIND(k)).ifBlank { "没找到「$k」" }
            }
            "click" -> {
                val k = text.ifBlank { target }
                if (k.isBlank()) return "失败：click 需要 text（要点的文字）"
                val r = js(SCRIPT_CLICK(k))
                if (r == "NOTFOUND") return "没找到可点的「$k」；先 action=find 看看页面上有什么"
                awaitLoad(8_000)
                "已点击「$k」\n" + head() + "\n\n" + js(SCRIPT_TEXT, 20_000).take(1500)
            }
            "input" -> {
                if (text.isBlank()) return "失败：input 需要 text（要填的内容）"
                val r = js(SCRIPT_INPUT(target, text))
                if (r == "NOTFOUND") return "页面上没有可输入的框"
                "已填入「${text.take(30)}」\n" + head()
            }
            "scroll" -> {
                val n = text.trim().toIntOrNull() ?: 800
                js("window.scrollBy(0,$n);'ok'")
                Thread.sleep(400)
                head() + "\n\n" + js(SCRIPT_TEXT, 20_000).take(1500)
            }
            "back" -> {
                onMain(5_000) { view?.goBack(); true }
                awaitLoad(12_000)
                "已后退\n" + head()
            }
            "forward" -> {
                onMain(5_000) { view?.goForward(); true }
                awaitLoad(12_000)
                "已前进\n" + head()
            }
            "reload" -> {
                loading.set(true)
                onMain(5_000) { view?.reload(); true }
                awaitLoad(20_000)
                "已刷新\n" + head()
            }
            "url" -> head()
            "close" -> {
                onMain(5_000) {
                    val w = view
                    (w?.parent as? ViewGroup)?.removeView(w)
                    w?.destroy()
                    view = null
                    curUrl = ""
                    curTitle = ""
                    true
                }
                "浏览器已关闭，下次会自动重建"
            }
            else -> "失败：看不懂的 action=$act（open / text / links / find / click / input / scroll / back / forward / reload / url / eval / close）"
        }
    } //浏览器动作分发

    private val SCRIPT_TEXT =
        "(function(){var b=document.body;if(!b)return '';var t=b.innerText||'';" +
            "t=t.replace(/\\u00a0/g,' ').replace(/[ \\t]+/g,' ').replace(/\\n{3,}/g,'\\n\\n');" +
            "return t.trim();})()"

    private val SCRIPT_LINKS =
        "(function(){var out=[];var as=document.querySelectorAll('a[href]');" +
            "for(var i=0;i<as.length&&out.length<100;i++){var a=as[i];" +
            "var t=(a.innerText||'').replace(/\\s+/g,' ').trim();if(!t)continue;" +
            "out.push(t.slice(0,60)+' -> '+a.href);}return out.join('\\n');})()"

    private fun SCRIPT_FIND(k: String): String =
        "(function(k){var out=[];var all=document.querySelectorAll('a,button,[role=button],input,textarea,h1,h2,h3,li');" +
            "for(var i=0;i<all.length&&out.length<40;i++){var e=all[i];" +
            "var t=(e.innerText||e.value||e.placeholder||'').replace(/\\s+/g,' ').trim();" +
            "if(!t||t.indexOf(k)<0)continue;out.push(e.tagName.toLowerCase()+' | '+t.slice(0,90));}" +
            "return out.join('\\n');})(" + q(k) + ")"

    private fun SCRIPT_CLICK(k: String): String =
        "(function(k){var all=document.querySelectorAll('a,button,[role=button],input[type=submit],li,span,div');" +
            "var hit=null,i,e,t;for(i=0;i<all.length;i++){e=all[i];t=(e.innerText||'').replace(/\\s+/g,' ').trim();" +
            "if(t&&t===k){hit=e;break;}}" +
            "if(!hit){for(i=0;i<all.length;i++){e=all[i];t=(e.innerText||'').replace(/\\s+/g,' ').trim();" +
            "if(t&&t.indexOf(k)>=0&&t.length<=k.length*4){hit=e;break;}}}" +
            "if(!hit)return 'NOTFOUND';hit.scrollIntoView({block:'center'});hit.click();return 'OK';})(" + q(k) + ")"

    private fun SCRIPT_INPUT(hint: String, v: String): String =
        "(function(k,v){var all=document.querySelectorAll('input,textarea,[contenteditable=true]');var el=null;" +
            "for(var i=0;i<all.length;i++){var e=all[i];if(e.type==='hidden')continue;" +
            "var h=(e.placeholder||e.getAttribute('aria-label')||e.name||'').toString();" +
            "if(k&&h.indexOf(k)<0&&i!==all.length-1)continue;el=e;break;}" +
            "if(!el)return 'NOTFOUND';el.focus();el.value=v;" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));" +
            "el.dispatchEvent(new Event('change',{bubbles:true}));return 'OK';})(" + q(hint) + "," + q(v) + ")"
}