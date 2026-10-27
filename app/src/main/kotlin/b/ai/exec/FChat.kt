package b.ai.exec

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import b.ai.core.Cfg
import b.ai.ui.FChatUi
import b.ai.ui.theme.BaiTheme

/**
 * 悬浮聊天窗：WindowManager 里挂一个 ComposeView，跟桌宠同一套宿主思路，但内容整块是 Compose。
 * 尺寸 / 字号 / 透明度 / 位置都存在 Cfg 里，调完直接 updateViewLayout，不重建窗口。
 */
object FChat {
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var host: WindowManager? = null
    private var view: View? = null
    private var lp: WindowManager.LayoutParams? = null
    private var owner: FloatOwner? = null

    @Volatile
    var visible: Boolean = false
        private set

    fun attach(c: Context) {
        ctx = c.applicationContext
    }

    /** 有没有悬浮窗权限：没有就返回 false，由界面负责引导去系统设置 */
    fun canShow(c: Context): Boolean {
        val cc = ctx ?: c.applicationContext
        return runCatching { Settings.canDrawOverlays(cc) }.getOrDefault(false)
    }

    /** 显示窗口：已经在显示就只重排一次 */
    fun show(c: Context? = null): Boolean {
        c?.let { attach(it) }
        val cc = ctx ?: return false
        if (!canShow(cc)) return false
        main.post { render() }
        return true
    }

    fun hide() {
        main.post { remove() }
    }

    /** 调完宽度 / 高度 / 透明度后调用：就地改窗口参数，不重建 */
    fun apply() {
        main.post { layout() }
    }

    /** 拖动整窗：dx / dy 是本次位移的像素 */
    fun moveBy(dx: Float, dy: Float) {
        val v = view ?: return
        val p = lp ?: return
        val dm = ctx?.resources?.displayMetrics ?: return
        p.x = (p.x + dx.toInt()).coerceIn(0, (dm.widthPixels - p.width).coerceAtLeast(0))
        p.y = (p.y + dy.toInt()).coerceIn(0, (dm.heightPixels - p.height).coerceAtLeast(0))
        runCatching { host?.updateViewLayout(v, p) }
    } //拖窗

    /** 拖动结束才落盘：拖动过程中每帧写配置会把主线程拖卡 */
    fun savePos() {
        lp?.let {
            Cfg.fcX = it.x
            Cfg.fcY = it.y
        }
    } //存位置

    private fun dp(v: Int): Int {
        val d = ctx?.resources?.displayMetrics?.density ?: 1f
        return (v * d).toInt()
    }

    private fun render() {
        if (visible) {
            layout()
            return
        }
        val cc = ctx ?: return
        val wm = cc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val o = FloatOwner()
        val cv = ComposeView(cc)
        //WindowManager 里的 ComposeView 没有 Activity 兜底，owner 必须自己挂
        cv.setViewTreeLifecycleOwner(o)
        cv.setViewTreeSavedStateRegistryOwner(o)
        cv.setContent { BaiTheme { FChatUi(onClose = { hide() }) } }
        val p = build(cc)
        runCatching { wm.addView(cv, p) }.onFailure { return }
        host = wm
        view = cv
        lp = p
        owner = o
        visible = true
    } //挂窗

    private fun build(cc: Context): WindowManager.LayoutParams {
        val dm = cc.resources.displayMetrics
        val p = WindowManager.LayoutParams(
            dp(Cfg.fcW.coerceIn(200, 600)),
            dp(Cfg.fcH.coerceIn(240, 900)),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = if (Cfg.fcX >= 0) Cfg.fcX.coerceAtMost((dm.widthPixels - p.width).coerceAtLeast(0))
        else (dm.widthPixels - p.width - dp(10)).coerceAtLeast(0)
        p.y = if (Cfg.fcY >= 0) Cfg.fcY.coerceAtMost((dm.heightPixels - p.height).coerceAtLeast(0))
        else dm.heightPixels / 5
        p.alpha = Cfg.fcAlpha.coerceIn(35, 100) / 100f
        //输入框要弹键盘，窗口就不能带 FLAG_NOT_FOCUSABLE
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        return p
    } //窗口参数

    private fun layout() {
        val v = view ?: return
        val p = lp ?: return
        val dm = ctx?.resources?.displayMetrics ?: return
        p.width = dp(Cfg.fcW.coerceIn(200, 600))
        p.height = dp(Cfg.fcH.coerceIn(240, 900))
        p.alpha = Cfg.fcAlpha.coerceIn(35, 100) / 100f
        p.x = p.x.coerceIn(0, (dm.widthPixels - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (dm.heightPixels - p.height).coerceAtLeast(0))
        runCatching { host?.updateViewLayout(v, p) }
    } //重排窗口

    private fun remove() {
        val v = view ?: return
        runCatching { host?.removeView(v) }
        view = null
        lp = null
        owner = null
        visible = false
    } //收窗
} //悬浮聊天窗

/** ComposeView 挂在 WindowManager 里没有 Activity 兜底，生命周期得自己造一份 */
private class FloatOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ssr = SavedStateRegistryController.create(this)

    init {
        ssr.performAttach()
        ssr.performRestore(null)
        reg.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ssr.savedStateRegistry
} //窗口自己的生命周期