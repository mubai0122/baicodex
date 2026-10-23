package b.ai.exec

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** 顶部胶囊：白色椭圆 + 转圈 + 「正在执行 xx」。有无障碍用无障碍窗层级，没有就退悬浮窗权限 */
object Capsule {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var a11yCtx: Context? = null

    @Volatile
    private var appCtx: Context? = null

    private var view: View? = null
    private var label: TextView? = null
    private var host: WindowManager? = null

    fun attachA11y(c: Context) {
        a11yCtx = c
    }

    fun detachA11y() {
        a11yCtx = null
        hide()
    }

    fun attachApp(c: Context) {
        appCtx = c.applicationContext
    }

    /** 显示或改文案：主线程做，重复调用只改字不重建窗口 */
    fun show(text: String) {
        main.post { render(text) }
    }

    fun hide() {
        main.post { remove() }
    }

    private fun render(text: String) {
        val w = view
        if (w != null && host != null) {
            label?.text = text
            return
        }
        val (c, type) = pick() ?: return
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 14), dp(c, 8), dp(c, 16), dp(c, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 24).toFloat()
                setColor(Color.WHITE)
            }
        }
        val bar = ProgressBar(c).apply {
            isIndeterminate = true
            if (Build.VERSION.SDK_INT >= 21) indeterminateTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#2F6BFF"))
        }
        root.addView(bar, LinearLayout.LayoutParams(dp(c, 16), dp(c, 16)))
        val tv = TextView(c).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B1F26"))
            textSize = 12.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(tv, LinearLayout.LayoutParams(dp(c, 190), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(c, 8)
        })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(c, 54)
        }
        val ok = runCatching { wm.addView(root, lp) }.isSuccess
        if (!ok) return
        view = root
        label = tv
        host = wm
    } //构建并显示胶囊

    private fun remove() {
        val w = view ?: return
        runCatching { host?.removeView(w) }
        view = null
        label = null
        host = null
    } //收起胶囊

    /** 挑层级：无障碍服务在就用 ACCESSIBILITY_OVERLAY（不用悬浮窗权限），否则走 APPLICATION_OVERLAY */
    private fun pick(): Pair<Context, Int>? {
        a11yCtx?.let {
            return it to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        val c = appCtx ?: return null
        val can = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)
        if (!can) return null
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return c to type
    } //选窗口层级

    private fun dp(c: Context, v: Int): Int = (c.resources.displayMetrics.density * v).toInt()
} //任务胶囊