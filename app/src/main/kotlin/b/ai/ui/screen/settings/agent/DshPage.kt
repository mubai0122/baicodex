package b.ai.ui.screen.settings.agent
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*

import b.ai.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import b.ai.core.data.log.Logs
import b.ai.core.dsh.DshCore
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DshPage.kt — dsh（DeepSeek Harness）Web UI 宿主页
 *
 * 两种用法：
 * 1. 侧边栏「插件」入口：DshScreen(onBack) —— 全屏 + 进页自动拉起服务
 * 2. 开发者工具自检页：DshPage() —— 内嵌固定高度 WebView，手动启动/停止
 *
 * 认证说明：dsh web 必须用启动日志里打印的带 token 地址打开，
 * 裸地址 http://127.0.0.1:3080 会被判定为未认证，统一走 DshCore.webUrl()。
 *
 * 日志接入（白屏排查口径）：
 * - 内核侧命令/阶段日志 → Logs（/sdcard/baicode/logs，按天加密落盘），tag = dsh
 * - 页面侧 onPageStarted / onPageFinished / onReceivedError / console 消息 → Logs，tag = dsh-web
 * - 顶栏「日志」直接把日志仓当天明文摊在页面上，并支持复制，不用连电脑
 */
@Composable
fun DshPage(
    fullScreen: Boolean = false,
    autoStart: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("未启动") }
    var running by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) } //启动流程中：防重入 + 显示进度
    var err by remember { mutableStateOf("") } //页面级错误：白屏时先看这里
    var pct by remember { mutableIntStateOf(0) } //WebView 加载进度
    var logOpen by remember { mutableStateOf(false) }
    var logTick by remember { mutableIntStateOf(0) } //点「刷新」自增，触发日志重读

    //WebView 只创建一次；回调里的每一环都进日志仓，白屏时可回溯到底哪一步断了
    val webView = remember {
        val w = WebView(ctx)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.databaseEnabled = true
        w.settings.allowFileAccess = false
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pct = 0
                Logs.i(TAG_WEB, "onPageStarted $url")
            } //开始加载

            override fun onPageFinished(view: WebView?, url: String?) {
                Logs.i(TAG_WEB, "onPageFinished $url")
                //先记录高度链，再注入撑满样式，日志里能直接看到修复前后对比
                applyLayoutFix(view)
                //SPA 首帧后还要 fetch + 渲染，探针分三次打：立刻 / 2s / 5s，白屏能看出是没渲染还是渲染了但没内容
                probeWeb(view, "t0")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ probeWeb(view, "t2s") }, 2_000L)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ probeWeb(view, "t5s") }, 5_000L)
            } //加载完成

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val msg = "${request?.url} code=${error?.errorCode} ${error?.description}"
                Logs.e(TAG_WEB, "onReceivedError $msg")
                //只对主文档报错弹给用户，子资源失败不打扰
                if (request?.isForMainFrame == true) err = ctx.getString(R.string.dh_page_load_failed, msg)
            } //加载错误

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                //只留本机 dsh 在页面内导航，其它链接交给系统浏览器
                val u = request?.url?.toString().orEmpty()
                return if (u.startsWith("http://127.0.0.1") || u.startsWith("http://localhost")) false
                else {
                    Logs.i(TAG_WEB, "外链交给系统浏览器：$u")
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request?.url)) }
                    true
                }
            } //链接拦截
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                pct = newProgress
            } //进度

            override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                if (m == null) return false
                val text = "[${m.messageLevel()}] ${m.message()} @${m.sourceId()}:${m.lineNumber()}"
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Logs.e(TAG_WEB, "console $text")
                    if (err.isBlank()) err = ctx.getString(R.string.dh_page_script_error, m.message())
                } else {
                    Logs.d(TAG_WEB, "console $text")
                }
                return true
            } //console
        }
        w
    } //webView

    /** 启动流程：跑完把状态行与页面都切到目标 */
    fun boot() {
        if (loading) return
        loading = true
        err = ""
        state = "启动中…（首次会自举 Node 24 并安装 dsh，可能需要几分钟）"
        scope.launch {
            val buff = StringBuilder()
            val ok = DshCore.start { line ->
                //阶段日志刷到状态行（回调在 IO 线程，切主线程写 state）
                buff.append(line).append('\n')
                val txt = buff.toString().takeLast(300)
                scope.launch { state = txt }
            }
            loading = false
            running = ok
            if (ok) {
                val url = DshCore.webUrl()
                Logs.i(TAG_WEB, "装载入口 ${url.take(80)}…")
                webView.loadUrl(url)
                state = "运行中 · ${DshCore.status()}"
            } else {
                val st = DshCore.status()
                val tail = DshCore.logs().takeLast(800)
                err = ctx.getString(R.string.dh_state_start_failed, st["error"] ?: ctx.getString(R.string.dh_unknown_reason))
                state = tail
                Logs.e(TAG_WEB, "启动失败 ${st["error"]}\n$tail")
            }
        }
    } //启动

    /** 停止流程 */
    fun shutdown() {
        if (loading) return
        scope.launch {
            DshCore.stop()
            running = false
            state = "已停止"
        }
    } //停止

    //进页自动拉起（侧边栏入口用）
    if (autoStart) {
        LaunchedEffect(Unit) { boot() }
    } else if (onBack == null) {
        //开发者工具内嵌：进页先按已有日志解析入口 URL
        LaunchedEffect(Unit) {
            val url = runCatching { DshCore.webUrl() }.getOrDefault("http://127.0.0.1:3080")
            webView.loadUrl(url)
            if (state == "未启动") state = "入口：$url"
        }
    }

    //日志面板内容：读当天明文尾部，只在打开 / 点刷新时读盘
    val logText by produceState(ctx.getString(R.string.dh_reading), logOpen, logTick) {
        value = if (!logOpen) "" else withContext(Dispatchers.IO) {
            runCatching {
                val day = Logs.days().lastOrNull() ?: return@runCatching ctx.getString(R.string.dh_log_empty, Logs.root().absolutePath)
                "文件 ${day}.log\n\n" + Logs.read(day).takeLast(12_000)
            }.getOrElse { ctx.getString(R.string.dh_log_read_failed, it.message) }
        }
    }

    //全屏时吃系统栏内边距（状态栏 / 导航条 / 刘海），否则标题栏会被状态栏盖住
    val root = if (fullScreen) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    else Modifier.fillMaxSize()

    //状态行显示文本：state 内部只作状态键（中文哨兵值），这里映射成当前语言文案
    val stateText = when {
        state.startsWith("入口：") -> stringResource(R.string.dh_state_entry, state.removePrefix("入口："))
        state == "未启动" -> stringResource(R.string.dh_not_started)
        state.startsWith("启动中") -> stringResource(R.string.dh_state_starting)
        state.startsWith("运行中 · ") -> stringResource(R.string.dh_state_running, state.removePrefix("运行中 · "))
        state == "已停止" -> stringResource(R.string.dh_state_stopped)
        state.startsWith("启动失败：") -> stringResource(R.string.dh_state_start_failed, state.removePrefix("启动失败："))
        else -> state
    } //状态行文本

    Column(modifier = root.padding(if (fullScreen) 0.dp else 8.dp)) {
        //顶栏：返回 / 状态 / 日志 / 启停
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    stringResource(R.string.dh_back_arrow),
                    fontSize = 13.sp,
                    color = AccentBlue,
                    modifier = Modifier.tap { onBack() }.padding(end = 10.dp)
                )
            }
            Text(
                if (running) stringResource(R.string.dh_running) else stringResource(R.string.dh_not_running),
                fontSize = 13.sp,
                color = if (running) OkGreen else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            GhostButton(stringResource(R.string.dh_log), modifier = Modifier.height(30.dp)) {
                logOpen = !logOpen
                logTick++
            }
            GhostButton(if (loading) stringResource(R.string.dh_starting) else if (running) stringResource(R.string.dh_stop) else stringResource(R.string.dh_start), modifier = Modifier.height(30.dp)) {
                if (loading) return@GhostButton
                if (running) shutdown() else boot()
            }
        }

        //诊断行：页面错误（白屏根因）优先，其次是内核阶段日志
        if (err.isNotBlank()) {
            Text(
                err,
                fontSize = 10.sp,
                color = DangerRed,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
        if (stateText.isNotBlank()) {
            Text(
                stateText,
                fontSize = 10.sp,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        //日志面板：占上半屏，等宽文本，带刷新 / 复制
        if (logOpen) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dh_log_repo, pct),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                GhostButton(stringResource(R.string.dh_reload), modifier = Modifier.height(28.dp)) { webView.reload() }
                GhostButton(stringResource(R.string.dh_fit), modifier = Modifier.height(28.dp)) {
                    //诊断用开关：关掉强制高度后看下拉面板是否恢复正常，用于二分根因
                    toggleLayoutFix(webView)
                    logTick++
                }
                GhostButton(stringResource(R.string.dh_sample), modifier = Modifier.height(28.dp)) {
                    //手动再应用一次撑满样式 + 打一次诊断：页面卡住/白屏时点这里，日志里立刻多一条
                    applyLayoutFix(webView)
                    probeWeb(webView, "manual")
                    logTick++
                }
                GhostButton(stringResource(R.string.dh_copy), modifier = Modifier.height(28.dp)) { copy(ctx, logText) }
            }
            Text(
                logText,
                fontSize = 9.sp,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(SurfaceMuted)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            )
        }

        //WebView：全屏模式吃掉剩余高度，内嵌模式固定高度
        WebPane(fullScreen) {
            AndroidView(
                factory = { webView },
                modifier = webModifier(fullScreen)
            ) { _ ->
                //URL 由 boot() / LaunchedEffect 统一注入（含 token），此处不重复加载
            }
        }
    }
} //dsh 页面

/** WebView 容器：只为把 weight 作用域留在 Column 内 */
@Composable
internal fun ColumnScope.WebPane(fullScreen: Boolean, content: @Composable ColumnScope.() -> Unit) {
    content()
} //占位容器

/** WebView 尺寸：全屏吃剩余高度，内嵌固定 460dp 便于与开发者工具其它项同屏 */
@Composable
internal fun ColumnScope.webModifier(fullScreen: Boolean): Modifier =
    if (fullScreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(460.dp)

/** 复制文本到剪贴板：日志直接贴给开发者 */
internal fun copy(ctx: Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dsh-log", text))
    }
} //复制日志

/**
 * 布局撑满修复（装一次，长期生效）：实测在 Android WebView 里 dsh 页面的高度链是
 * html.boxHeight=0 / body.clientHeight=0 / #root.clientHeight=0，而 window.innerHeight
 * 与 html.clientHeight 都是正常值 —— 页面 CSS 的 `html,body,#root{height:100%}` 在这台
 * WebView 里算不出视口高，内容被塞进 0 高容器裁掉 → 整屏空白（DOM/innerText 都完整）。
 * 用 CSSOM 写 inline !important 像素高度绕开百分比链，并注册 resize / visualViewport
 * 监听：侧栏折叠、日志面板收起、横竖屏切换都会改变 WebView 尺寸，必须重算，否则内容被挤。
 */
internal fun applyLayoutFix(view: WebView?) {
    val js = """
        (function(){try{
          if(window.__baicodeFixNow){window.__baicodeFixNow();return 'refresh';}
          var set=function(e,k,v){if(e&&e.style)e.style.setProperty(k,v,'important');};
          var now=function(){
            if(window.__baicodeFixOff) return;
            var H=window.innerHeight+'px';
            var h=document.documentElement,b=document.body,r=document.getElementById('root');
            set(h,'height',H); set(h,'min-height',H);
            set(b,'height',H); set(b,'min-height',H);
            if(r){set(r,'height',H); set(r,'min-height',H);}
          };
          window.__baicodeFixNow=now;
          now();
          //尺寸变化（折叠侧栏 / 键盘 / 旋转）后重算，避免内容被压扁
          window.addEventListener('resize',now,{passive:true});
          if(window.visualViewport) window.visualViewport.addEventListener('resize',now,{passive:true});
          return 'installed ih='+window.innerHeight;
        }catch(e){return 'fixerr:'+e}})()
    """.trimIndent()
    view?.evaluateJavascript(js) { r -> Logs.i(TAG_WEB, "layoutFix $r") }
} //布局撑满

/**
 * 撑满开关（诊断用）：关掉后清空 html/body/#root 上的 inline 高度，
 * 用来二分「下拉面板被挤」到底是页面自身问题还是我们强制高度引起的。
 * 再点一次恢复。
 */
internal fun toggleLayoutFix(view: WebView?) {
    val js = """
        (function(){try{
          window.__baicodeFixOff=!window.__baicodeFixOff;
          if(!window.__baicodeFixOff){
            if(window.__baicodeFixNow) window.__baicodeFixNow();
            return 'on';
          }
          var h=document.documentElement,b=document.body,r=document.getElementById('root');
          [h,b,r].forEach(function(e){
            if(e&&e.style){e.style.removeProperty('height');e.style.removeProperty('min-height');}
          });
          return 'off';
        }catch(e){return 'togerr:'+e}})()
    """.trimIndent()
    view?.evaluateJavascript(js) { r -> Logs.i(TAG_WEB, "layoutToggle $r") }
} //撑满开关

/**
 * 布局诊断探针：DOM 有内容却看不见时（白屏），靠它一次性定位是
 * ①视口尺寸为 0 ②元素 opacity/visibility 被遮 ③前景色与背景色相同 ④主题类没挂上。
 * 输出为一行 pipe 分隔的键值，直接进日志。
 */
internal fun probeWeb(view: WebView?, tag: String) {
    val js = """
        (function(){try{
          var out=[];
          var d=document.documentElement,b=document.body,r=document.getElementById('root');
          out.push('vp='+window.innerWidth+'x'+window.innerHeight+' dpr='+window.devicePixelRatio);
          var items=[['html',d],['body',b],['root',r]];
          for(var i=0;i<items.length;i++){
            var n=items[i][0],e=items[i][1];
            if(!e){out.push(n+'=null');continue;}
            var c=getComputedStyle(e),q=e.getBoundingClientRect();
            out.push(n+': '+Math.round(q.width)+'x'+Math.round(q.height)
              +' op='+c.opacity+' vis='+c.visibility+' disp='+c.display
              +' ov='+c.overflow+' bg='+c.backgroundColor+' fg='+c.color);
          }
          out.push('plat='+((d.getAttribute('data-platform'))||'-')
            +' theme='+((d.getAttribute('data-theme'))||(d.className||'-')));
          if(b){
            var kids=[];
            for(var j=0;j<b.children.length && j<8;j++){
              var k=b.children[j],kq=k.getBoundingClientRect();
              kids.push((k.tagName+(k.id?'#'+k.id:''))+'@'+Math.round(kq.width)+'x'+Math.round(kq.height));
            }
            out.push('kids='+kids.join(','));
            out.push('txt='+b.innerText.replace(/\s+/g,' ').slice(0,120));
            //dvh / vh 在这个 WebView 里是否可用：dsh 内部布局大量用 100dvh，坏了同样会显示不全
            var t=document.createElement('div');
            t.style.cssText='position:absolute;left:-9999px;top:0;width:1px;height:100dvh';
            b.appendChild(t);
            var dvh=t.getBoundingClientRect().height;
            t.style.height='100vh';
            var vh=t.getBoundingClientRect().height;
            b.removeChild(t);
            out.push('dvh='+Math.round(dvh)+' vh='+Math.round(vh));
            //浮层诊断：下拉面板/Popover 被挤成一小点时，看它自己的 rect、max-height 与定位方式
            var ovl=[],all=b.getElementsByTagName('*'),n2=0;
            for(var i2=0;i2<all.length&&n2<6;i2++){
              var e2=all[i2],c2=getComputedStyle(e2);
              if((c2.position==='fixed'||c2.position==='absolute')&&c2.zIndex!=='auto'){
                var q2=e2.getBoundingClientRect();
                if(q2.width>40&&q2.height>=0){
                  ovl.push((e2.className||e2.tagName).toString().slice(0,26)
                    +'@'+Math.round(q2.top)+'/'+Math.round(q2.height)
                    +' mh='+c2.maxHeight+' '+c2.position+' z='+c2.zIndex);
                  n2++;
                }
              }
            }
            out.push('ovl='+(ovl.length?ovl.join(' ; '):'-'));
          }
          return out.join(' || ');
        }catch(e){return 'evalerr:'+e}})()
    """.trimIndent()
    view?.evaluateJavascript(js) { r -> Logs.i(TAG_WEB, "probe[$tag] $r") }
} //DOM 探针

/** 侧边栏入口用的全屏包装：进页自动拉起 dsh，返回回到对话 */
@Composable
fun DshScreen(onBack: () -> Unit) {
    DshPage(fullScreen = true, autoStart = true, onBack = onBack)
} //dsh 全屏入口

/** 日志仓 tag：内核侧 / 页面侧分开，便于 grep */
internal const val TAG_WEB = "dsh-web"