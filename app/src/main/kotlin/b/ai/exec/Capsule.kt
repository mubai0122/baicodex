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

    /** 常驻态多久没有任何工具动作就自动收：防止模型忘了 quiet */
    private const val IDLE_MS = 300_000L

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
        end()
    }

    fun attachApp(c: Context) {
        appCtx = c.applicationContext
    }
    /** 是否处于多步任务常驻态：模型调 screen start 打开，screen quiet 关掉 */
    @Volatile
    var pinned: Boolean = false
        private set

    private var lastAt = 0L

    private val guard = object : Runnable {
        override fun run() {
            if (!pinned) return
            if (System.currentTimeMillis() - lastAt > IDLE_MS) end() else main.postDelayed(this, 15_000L)
        }
    } //看门狗：模型忘了安静也不会让胶囊永远挂着

    /** 显示或改文案：主线程做，重复调用只改字不重建窗口 */
    fun show(text: String) {
        main.post {
            lastAt = System.currentTimeMillis()
            render(text)
        }
    }

    fun hide() {
        main.post { if (!pinned) remove() }
    }

    /** 常驻显示：多步操作开始时调一次，之后每步只改文案，不再开合窗口 */
    fun begin(text: String) {
        main.post {
            pinned = true
            lastAt = System.currentTimeMillis()
            render(text)
            main.removeCallbacks(guard)
            main.postDelayed(guard, 15_000L)
        }
    } //开启常驻

    /** 收起并退出常驻：模型调 screen quiet，或本轮结束兜底调用 */
    fun end() {
        main.post {
            pinned = false
            main.removeCallbacks(guard)
            remove()
        }
    } //关闭常驻

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