package b.ai.exec

import android.annotation.SuppressLint
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import b.ai.R
import b.ai.core.Cfg
import b.ai.core.PetSay
import b.ai.core.PetSound

/**
 * 桌宠悬浮窗。位置默认固定，动画都做在 View 自己身上：
 * 原地缓动改图片的 translationY，点击 Q 弹改容器的 translationY，
 * 只有开了「游泳状态」才会去动窗口坐标（每 40ms 一次 updateViewLayout）。
 */
object Pet {
    private val main = Handler(Looper.getMainLooper())

    /** 按住移动超过这个像素就算拖动，不算点击 */
    private const val DRAG_SLOP = 12

    /** 单击要等这么久，期间没有第二次点击才按单击处理 */
    private const val TAP_MS = 300L

    /** 默认边长（dp） */
    private const val DEF_SIZE = 120

    /** 游泳刷帧间隔 */
    private const val SWIM_TICK = 40L

    @Volatile
    private var a11yCtx: Context? = null

    private var appCtx: Context? = null

    private var host: WindowManager? = null
    private var petView: FrameLayout? = null
    private var petLp: WindowManager.LayoutParams? = null
    private var inner: FrameLayout? = null
    private var icon: ImageView? = null
    private var floatAnim: ObjectAnimator? = null
    private var lastApply = 0L
    private var applyPend: Runnable? = null

    /** 窗口顶部给上跳留出的余量（px）：不留的话弹跳会被窗口边界裁掉 */
    private var padTop = 0

    private var swimRun: Runnable? = null
    private var swimVx = 1f
    private var swimVy = 1f

    private var bubbleView: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var bubbleHide: Runnable? = null
    private var waitTap: Runnable? = null

    fun attachApp(c: Context) {
        appCtx = c.applicationContext
        PetSound.warm(appCtx!!) //顺手把音效采样预加载，避免第一次点击没声
    }

    /** 无障碍连上/断开时换宿主层级（无障碍窗层不需要悬浮窗权限） */
    fun attachA11y(c: Context) {
        a11yCtx = c
        if (Cfg.petOn && Cfg.petGlobal) main.post { rebuild() }
    }

    fun detachA11y() {
        a11yCtx = null
        if (Cfg.petOn && Cfg.petGlobal) main.post { rebuild() }
    }

    fun a11yActive(): Boolean = a11yCtx != null

    /** 无障碍在跑就不要任何权限；否则看有没有悬浮窗权限 */
    fun canShow(c: Context?): Boolean {
        if (a11yActive()) return true
        val cc = c ?: return false
        return runCatching { Settings.canDrawOverlays(cc) }.getOrDefault(false)
    }

    fun show() = main.post { render() }

    fun hide() = main.post { remove() }

    /** 改了尺寸、动效一类要重建窗口的配置之后调它 */
    fun rebuildSoon() = main.post { if (Cfg.petOn && Cfg.petGlobal && petView != null) rebuild() }
    /**
     * 配置页拖动滑块/点开关时调它：桌宠正在显示就按新配置重画，
     * 120ms 内只重建一次，免得拖动时抖成一片。
     */
    fun applyLive() {
        main.post {
            if (!Cfg.petOn || !Cfg.petGlobal || petView == null) return@post
            val wait = 120L - (System.currentTimeMillis() - lastApply)
            if (wait <= 0L) {
                lastApply = System.currentTimeMillis()
                rebuild()
                return@post
            }
            if (applyPend != null) return@post
            val r = Runnable {
                applyPend = null
                lastApply = System.currentTimeMillis()
                if (Cfg.petOn && Cfg.petGlobal && petView != null) rebuild()
            }
            applyPend = r
            main.postDelayed(r, wait)
        }
    } //实时应用配置

    /** 面板里「试一下」：弹一下并说话 */
    fun sayNow(c: Context) {
        bounce()
        showBubble(c, PetSay.next(c))
    }

    private fun rebuild() {
        //重建前把当前位置记回配置，不然游泳/拖动过的位置会跳回去
        val lp = petLp
        if (lp != null) {
            if (Cfg.petX != lp.x || Cfg.petY != lp.y) {
                Cfg.petX = lp.x
                Cfg.petY = lp.y
            }
        }
        remove()
        render()
    } //重建窗口

    private fun current(): Context? = a11yCtx ?: appCtx

    private fun windowType(): Int = when {
        a11yCtx != null -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        Build.VERSION.SDK_INT >= 26 -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else -> {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    } //选窗口层级

    private fun render() {
        if (petView != null) return
        val c = current() ?: return
        if (!canShow(c)) return
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val size = dp(c, Cfg.petSize.coerceIn(60, 260))
        //上跳与缓动都在窗口内完成，所以窗口要高出「幅度」这么多
        padTop = dp(c, maxOf(Cfg.petFloatAmp, Cfg.petBouncePct) + 4)
        val box = FrameLayout(c)

        //白底卡片 / 脚下阴影都垫在立绘下面，本身不吃触摸
        if (Cfg.petCard) {
            box.background = GradientDrawable().apply {
                cornerRadius = dp(c, 16).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(c, 1), Color.parseColor("#E2E5E9"))
            }
        }
        if (Cfg.petShadow) {
            val sh = View(c)
            val shw = (size * 0.62f).toInt().coerceAtLeast(dp(c, 18))
            val shh = (size * 0.14f).toInt().coerceAtLeast(dp(c, 5))
            val gl = FrameLayout.LayoutParams(shw, shh)
            gl.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            gl.bottomMargin = dp(c, 2)
            sh.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#29000000"))
            }
            box.addView(sh, gl)
        }

        val iv = ImageView(c).apply {
            setImageResource(R.drawable.whale)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = Cfg.petAlpha / 100f
            scaleX = if (Cfg.petFlip) -1f else 1f
        }
        //inner 负责上下缓动，iv 负责点击 Q 弹：两个动画各动各的，不会互相打断
        val wrap = FrameLayout(c)
        wrap.addView(iv, FrameLayout.LayoutParams(size, size))
        val wl = FrameLayout.LayoutParams(size, size)
        wl.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        wl.topMargin = padTop
        box.addView(wrap, wl)

        val lp = WindowManager.LayoutParams(
            size, size + padTop, windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Cfg.petX
            y = Cfg.petY
        }
        //从没摆过位置：落到右侧中间
        if (Cfg.petX < 0 || Cfg.petY < 0) {
            val m = c.resources.displayMetrics
            lp.x = (m.widthPixels - size - dp(c, 8)).coerceAtLeast(0)
            lp.y = (m.heightPixels / 2).coerceAtLeast(0)
        }
        if (runCatching { wm.addView(box, lp) }.isFailure) return
        host = wm
        petView = box
        petLp = lp
        inner = wrap
        icon = iv
        box.setOnTouchListener(touch(c, wm, lp))
        startAnim(c)
    } //构建并显示

    private fun startAnim(c: Context) {
        startFloat(c)
        if (Cfg.petSwim) startSwim(c) else stopSwim()
    } //按配置启动动效

    @SuppressLint("ClickableViewAccessibility")
    private fun touch(c: Context, wm: WindowManager, lp: WindowManager.LayoutParams): View.OnTouchListener {
        var downX = 0f
        var downY = 0f
        var x0 = 0
        var y0 = 0
        var moved = false
        return View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    x0 = lp.x
                    y0 = lp.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (kotlin.math.abs(dx) > DRAG_SLOP || kotlin.math.abs(dy) > DRAG_SLOP)) moved = true
                    if (moved) {
                        lp.x = x0 + dx.toInt()
                        lp.y = y0 + dy.toInt()
                        runCatching { wm.updateViewLayout(petView, lp) }
                        relocateBubble()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        Cfg.petX = lp.x
                        Cfg.petY = lp.y
                    } else {
                        tap()
                    }
                    true
                }

                else -> false
            }
        }
    } //拖动与点击

    private fun tap() {
        //第二次点击落在等待窗口里：撤掉单击，改成查余额
        val pending = waitTap
        if (pending != null) {
            main.removeCallbacks(pending)
            waitTap = null
            bounce()
            val c = current() ?: return
            if (Cfg.petDblBal) {
                PetSay.balance(c) { showBubble(c, it) }
            } else if (Cfg.petTalk) {
                showBubble(c, PetSay.next(c))
            }
            return
        }
        val r = Runnable {
            waitTap = null
            bounce()
            val c = current() ?: return@Runnable
            if (Cfg.petTalk) showBubble(c, PetSay.next(c))
        }
        waitTap = r
        main.postDelayed(r, TAP_MS)
    } //双击查余额，单击说话

    /** 点一下往上弹再落回原位，回落带一点过冲，就是 Q 弹的手感 */
    private fun bounce() {
        val v = icon ?: return
        if (Cfg.petBounce) {
            val c = v.context
            v.animate().cancel()
            v.animate().translationY(-dp(c, Cfg.petBouncePct).toFloat())
                .setDuration(110)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    v.animate().translationY(0f)
                        .setDuration(320)
                        .setInterpolator(OvershootInterpolator(2.4f))
                        .start()
                }.start()
        }
        PetSound.boing(v.context, if (Cfg.petSound) Cfg.petSoundVol / 100f else 0f)
    } //上下 Q 弹 + 音效

    private fun showBubble(c: Context, text: String) {
        val wm = host ?: return
        if (text.isBlank()) return
        dismissBubble()
        val dark = Cfg.petBubbleDark
        val tv = TextView(c).apply {
            this.text = text
            setTextColor(if (dark) Color.WHITE else Color.parseColor("#1B1F26"))
            textSize = Cfg.petFont.toFloat()
            if (Cfg.petFontBold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxWidth = dp(c, Cfg.petBubbleW)
            setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(c, Cfg.petBubbleR).toFloat()
                setColor(if (dark) Color.parseColor("#F21B1F26") else Color.WHITE)
                if (!dark) setStroke(dp(c, 1), Color.parseColor("#E2E5E9"))
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        //先量出宽高、把位置算好并置成全透明再挂上去：否则第一帧会实打实闪在屏幕左上角 (0,0)
        tv.alpha = 0f
        val pl = petLp
        if (pl != null) {
            tv.measure(
                View.MeasureSpec.makeMeasureSpec(dp(c, Cfg.petBubbleW), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            if (tv.measuredWidth > 0 && tv.measuredHeight > 0) {
                lp.x = (pl.x + pl.width / 2 - tv.measuredWidth / 2).coerceAtLeast(0)
                lp.y = (pl.y + padTop - tv.measuredHeight - dp(c, 6)).coerceAtLeast(0)
            }
        }
        if (runCatching { wm.addView(tv, lp) }.isFailure) return
        bubbleView = tv
        bubbleLp = lp
        //真宽高出来后再对一次位置，然后原地淡入（位置已经对了，不会再跳）
        tv.post {
            relocateBubble()
            tv.animate().alpha(1f).setDuration(140).start()
        }
        val r = Runnable { fadeBubble() }
        bubbleHide = r
        main.postDelayed(r, Cfg.petBubbleMs.toLong())
    } //说话气泡

    private fun relocateBubble() {
        val v = bubbleView ?: return
        val lp = bubbleLp ?: return
        val pl = petLp ?: return
        val wm = host ?: return
        val c = v.context
        if (v.width <= 0 || v.height <= 0) return
        lp.x = (pl.x + pl.width / 2 - v.width / 2).coerceAtLeast(0)
        lp.y = (pl.y + padTop - v.height - dp(c, 6)).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(v, lp) }
    } //气泡跟着宠物走

    private fun fadeBubble() {
        val v = bubbleView ?: return
        v.animate().alpha(0f).setDuration(220).withEndAction { dismissBubble() }.start()
    }

    private fun dismissBubble() {
        bubbleHide?.let { main.removeCallbacks(it) }
        bubbleHide = null
        bubbleView?.let { runCatching { host?.removeView(it) } }
        bubbleView = null
        bubbleLp = null
    } //收气泡

    private fun startFloat(c: Context) {
        floatAnim?.cancel()
        floatAnim = null
        val v = inner ?: return
        val amp = dp(c, Cfg.petFloatAmp).toFloat()
        if (!Cfg.petFloat || amp <= 0f) {
            v.translationY = 0f
            return
        }
        //只动图片自己：窗口坐标一动不动，所以位置是固定的
        floatAnim = ObjectAnimator.ofFloat(v, "translationY", 0f, -amp).apply {
            duration = Cfg.petFloatMs.toLong()
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    } //原地浮动

    private fun startSwim(c: Context) {
        stopSwim()
        val wm = host ?: return
        val m = c.resources.displayMetrics
        swimVx = if (Math.random() < 0.5) -1f else 1f
        swimVy = if (Math.random() < 0.5) -1f else 1f
        val r = object : Runnable {
            var last = System.currentTimeMillis()
            override fun run() {
                val p = petLp ?: return
                val v = petView ?: return
                val now = System.currentTimeMillis()
                val dt = (now - last).coerceIn(0L, 120L) / 1000f
                last = now
                val speed = Cfg.petSwimSpeed.toFloat()
                val maxX = (m.widthPixels - p.width).coerceAtLeast(0).toFloat()
                val maxY = (m.heightPixels - p.height).coerceAtLeast(0).toFloat()
                var nx = p.x + swimVx * speed * dt
                var ny = p.y + swimVy * speed * dt
                if (Cfg.petSwimBounce) {
                    if (nx <= 0f) {
                        nx = 0f
                        swimVx = -swimVx
                    } else if (nx >= maxX) {
                        nx = maxX
                        swimVx = -swimVx
                    }
                    if (ny <= 0f) {
                        ny = 0f
                        swimVy = -swimVy
                    } else if (ny >= maxY) {
                        ny = maxY
                        swimVy = -swimVy
                    }
                } else {
                    if (nx <= 0f) nx = maxX else if (nx >= maxX) nx = 0f
                    if (ny <= 0f) ny = maxY else if (ny >= maxY) ny = 0f
                }
                p.x = nx.toInt()
                p.y = ny.toInt()
                runCatching { wm.updateViewLayout(v, p) }
                relocateBubble()
                main.postDelayed(this, SWIM_TICK)
            }
        }
        swimRun = r
        main.postDelayed(r, SWIM_TICK)
    } //游泳：窗口自己跑

    private fun stopSwim() {
        swimRun?.let { main.removeCallbacks(it) }
        swimRun = null
    } //停游泳

    private fun remove() {
        waitTap?.let { main.removeCallbacks(it) }
        waitTap = null
        stopSwim()
        floatAnim?.cancel()
        floatAnim = null
        dismissBubble()
        petView?.let { runCatching { host?.removeView(it) } }
        petView = null
        petLp = null
        inner = null
        icon = null
        host = null
    } //收起

    private fun dp(c: Context, v: Int): Int = (c.resources.displayMetrics.density * v).toInt()
} //桌宠悬浮窗