package b.ai.exec

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 屏幕操作：无障碍侧读控件树、注入手势、截图。服务没开时所有调用一律返回失败原因 */
object A11y {
    @Volatile
    private var svc: A11yService? = null

    fun attach(s: A11yService) {
        svc = s
    }

    fun detach(s: A11yService) {
        if (svc === s) svc = null
    }

    fun ready(): Boolean = svc != null

    /** 没权限时置 true：界面据此弹引导，用户开完再置回 false */
    val needPerm = androidx.compose.runtime.mutableStateOf(false)

    fun askPerm() {
        needPerm.value = true
    }

    fun permHandled() {
        needPerm.value = false
    }

    fun name(): String = svc?.let { it::class.java.simpleName } ?: "未开启"

    /** 服务状态：开着才下发 screen 工具，否则模型看不到这个能力 */
    fun status(): String {
        val s = svc ?: return "无障碍服务未开启：设置 → 无障碍 → 已下载的服务 → BaiAgent 打开"
        val pkg = runCatching { s.rootInActiveWindow?.packageName?.toString() }.getOrNull()
        return "无障碍服务已连接" + (pkg?.let { "，当前前台 $it" } ?: "，暂无可读窗口")
    } //服务是否就绪

    /** 控件树：一行一个可交互/有文字的节点，坐标是屏幕绝对坐标 */
    fun dump(maxNodes: Int = 400): String {
        val s = svc ?: return "无障碍服务未开启，先在设置里打开 BaiAgent 的屏幕操作"
        val root = s.rootInActiveWindow ?: return "拿不到当前窗口的控件树（可能在锁屏 / 权限弹窗 / 纯图片界面）"
        val sb = StringBuilder()
        sb.append("前台包名: ").append(root.packageName).append('\n')
        var n = 0
        val r = Rect()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || n >= maxNodes) return
            val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val click = node.isClickable
            val edit = node.isEditable
            val scroll = node.isScrollable
            val show = text.isNotBlank() || desc.isNotBlank() || id.isNotBlank() || click || edit || scroll
            if (show) {
                n++
                node.getBoundsInScreen(r)
                sb.append(n).append(". ").append(cls)
                if (text.isNotBlank()) sb.append(" text=\"").append(text.take(60)).append('"')
                if (desc.isNotBlank()) sb.append(" desc=\"").append(desc.take(60)).append('"')
                if (id.isNotBlank()) sb.append(" id=").append(id)
                if (click) sb.append(" click")
                if (edit) sb.append(" input")
                if (scroll) sb.append(" scroll")
                sb.append(" [").append(r.left).append(',').append(r.top).append("][").append(r.right).append(',')
                    .append(r.bottom).append("] center=").append((r.left + r.right) / 2).append(',')
                    .append((r.top + r.bottom) / 2).append('\n')
            }
            if (depth < 32) for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        if (n == 0) return "窗口里没有可读控件（包名 ${root.packageName}，可能是纯图片或游戏界面，改用截图判断）"
        sb.append("共 ").append(n).append(" 个可读控件（坐标可直接用于 tap / long / swipe）")
        return sb.toString()
    } //读当前界面控件树

    fun tap(x: Int, y: Int): String = gesture { b ->
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        b.addStroke(GestureDescription.StrokeDescription(p, 0, ViewConfiguration.getTapTimeout().toLong()))
    } //点击坐标

    fun longPress(x: Int, y: Int, ms: Long = 800L): String = gesture { b ->
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        b.addStroke(GestureDescription.StrokeDescription(p, 0, ms.coerceIn(500L, 5000L)))
    } //长按坐标

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long = 300L): String = gesture { b ->
        val p = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        b.addStroke(GestureDescription.StrokeDescription(p, 0, ms.coerceIn(80L, 5000L)))
    } //滑动

    /** 手势注入：dispatchGesture 同步等结果，免得模型以为点过了其实没点 */
    private fun gesture(build: (GestureDescription.Builder) -> Unit): String {
        val s = svc ?: return "无障碍服务未开启，先在设置里打开 BaiAgent 的屏幕操作"
        val b = GestureDescription.Builder()
        build(b)
        val latch = CountDownLatch(1)
        var ok = false
        val sent = runCatching {
            s.dispatchGesture(b.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    ok = true
                    latch.countDown()
                }

                override fun onCancelled(g: GestureDescription?) {
                    ok = false
                    latch.countDown()
                }
            }, null)
        }.getOrDefault(false)
        if (!sent) return "手势没发出去（服务可能刚被系统回收，重开无障碍再试）"
        latch.await(6, TimeUnit.SECONDS)
        return if (ok) "已执行" else "手势被系统取消（目标界面可能刚切走了）"
    } //注入一段手势

    /** 全局动作：返回 / 桌面 / 多任务 / 通知栏 / 快捷设置 */
    fun global(which: String): String {
        val s = svc ?: return "无障碍服务未开启，先在设置里打开 BaiAgent 的屏幕操作"
        val code = when (which) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "不支持的动作：$which（可用 back / home / recents / notifications / quick）"
        }
        return if (s.performGlobalAction(code)) "已执行 $which" else "系统拒绝执行 $which"
    } //系统级按键

    /** 输入文字：先给焦点节点 SET_TEXT，不认再退剪贴板粘贴 */
    fun input(text: String): String {
        val s = svc ?: return "无障碍服务未开启，先在设置里打开 BaiAgent 的屏幕操作"
        val focus = s.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: s.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "当前界面没有输入框焦点，先 tap 一下输入框再输入"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (runCatching { focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)) {
            return "已输入：$text"
        }
        val cm = s.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return "输入失败：拿不到剪贴板"
        runCatching { cm.setPrimaryClip(ClipData.newPlainText("bai", text)) }
        val pasted = runCatching { focus.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)
        return if (pasted) "已粘贴：$text" else "输入失败：这个输入框既不认 SET_TEXT 也不认粘贴，改用 shell 的 input text"
    } //往焦点输入框打字

    /** 截图落盘：失败自动重试（系统对连续截图有间隔限制，短于限制直接返回错误码） */
    fun shot(out: File, retry: Int = 2): String {
        val s = svc ?: return "无障碍服务未开启，先在设置里打开 BaiAgent 的屏幕操作"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "系统低于 Android 11，无障碍截图不可用；改用 Shizuku / Root 通道执行 screencap"
        }
        var last = "截图失败"
        for (i in 0..retry) {
            val r = shotOnce(s, out)
            if (r != null) return r
            last = "截图失败（第 ${i + 1} 次）"
            Thread.sleep(1200L)
        }
        return last
    } //截图并保存

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private fun shotOnce(s: A11yService, out: File): String? {
        val latch = CountDownLatch(1)
        var done: String? = null
        var err = -1
        val cb = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                runCatching {
                    val buf = result.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(buf, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    runCatching { buf.close() }
                    if (bmp != null) {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                        val w = bmp.width
                        val h = bmp.height
                        bmp.recycle()
                        done = out.absolutePath + "（${w}x$h，" + (out.length() / 1024) + "KB）"
                    }
                }
                latch.countDown()
            }

            override fun onFailure(errorCode: Int) {
                err = errorCode
                latch.countDown()
            }
        }
        runCatching { s.takeScreenshot(Display.DEFAULT_DISPLAY, { it.run() }, cb) }
        latch.await(6, TimeUnit.SECONDS)
        if (done != null) return done
        return when (err) {
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> null
            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "截图失败：无障碍没有截图权限"
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "截图失败：显示 ID 不对"
            //ERROR_TAKE_SCREENSHOT_SECURE_WINDOW 是 API34 才有的常量，这里不引用它，免得低版本 NoSuchFieldError
            -1 -> "截图超时"
            else -> null
        }
    } //单次截图
} //屏幕操作

/** 无障碍服务本体：只负责挂载到 A11y，具体能力都在 A11y 里 */
class A11yService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        A11y.attach(this)
        //顺带把胶囊的宿主也换成无障碍窗层：不需要悬浮窗权限就能显示在上面
        Capsule.attachA11y(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        A11y.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        A11y.detach(this)
        Capsule.detachA11y()
        super.onDestroy()
    }
} //屏幕操作服务