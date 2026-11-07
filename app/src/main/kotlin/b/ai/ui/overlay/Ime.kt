package b.ai.ui.overlay

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 键盘高度兜底。
 *
 * 窗口是 adjustNothing + edge-to-edge，正常情况靠 WindowInsets.safeDrawing 里的 ime 撑出底部间距。
 * 但部分 ROM（实测鸿蒙 4）的输入法不上报 ime insets，safeDrawing 里那一段恒为 0，输入框就被键盘盖住。
 * 这里用「窗口可见区域被顶掉的高度」估一个键盘高度，只在官方没上报时生效，两种设备都不会重复留白。
 */
object Ime {
    /** 兜底键盘高度（px）。官方上报 ime insets 时恒为 0 */
    var fallback by mutableStateOf(0)
        private set
    //上一次的诊断串：数值没变就不重复打日志，否则键盘动画期间每帧一条
    private var lastLog = ""

    fun attach(act: Activity) {
        val root = act.window?.decorView ?: return
        //全局布局变化包含键盘弹出/收起，用这个比 OnApplyWindowInsets 覆盖面广
        root.viewTreeObserver.addOnGlobalLayoutListener { sync(root) }
        sync(root)
    } //挂监听

    private fun sync(root: View) {
        val wi = ViewCompat.getRootWindowInsets(root)
        val imePx = wi?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val navPx = wi?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        //官方给了就别叠加，否则键盘高度会被算两遍
        if (imePx > 0) {
            fallback = 0
            log("官方上报", root, imePx, navPx, 0, 0)
            return
        }
        val r = Rect()
        //注意：getWindowVisibleDisplayFrame 返回 void，不能当布尔用
        root.getWindowVisibleDisplayFrame(r)
        if (root.height <= 0) return
        //可见区底边被顶掉的高度，减掉导航条那一段才是键盘本身
        val covered = root.height - r.bottom - navPx
        val h = root.height
        //太小是状态栏抖动，接近整屏的是整屏面板（全屏看图、分屏拖拽）都不当键盘；
        //上限放到 0.9：横屏和大键盘能占掉大半屏，原来的 3/4 会把这类判成「不是键盘」而不上移
        val fb = when {
            covered < h / 8 -> 0
            covered > h * 9 / 10 -> 0
            else -> covered
        }
        fallback = fb
        log("可见区域差", root, imePx, navPx, covered, fb)
    } //按可见区域差算键盘高度

    private fun log(how: String, root: View, ime: Int, nav: Int, covered: Int, fb: Int) {
        //只在数值变化时记一条：ROM 到底是「不报 insets」还是「可见区域也不变」，靠这一行分辨
        val s = "$how root=${root.height} ime=$ime nav=$nav covered=$covered fb=$fb"
        if (s == lastLog) return
        lastLog = s
        android.util.Log.w("Ime", s)
    } //诊断日志
}