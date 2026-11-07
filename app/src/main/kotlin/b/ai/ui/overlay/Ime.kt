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
 * 前提：Activity 是 edge-to-edge + windowSoftInputMode=adjustResize。
 * 官方文档写明只有 adjustResize 才会把 ime insets 发给 app；从前这里写的是 adjustNothing，
 * 于是绝大多数设备上 ime insets 恒为 0，只能靠本类的可见区差兜着。
 * 现在窗口是 adjustResize：正常设备直接吃 WindowInsets.ime；仍有 ROM（实测鸿蒙 4 / API 31）
 * 不按规矩上报，就用「可见区域被顶掉的高度」估一个键盘高度补上，两种设备都不会重复留白。
 * 另外悬浮/未停靠的输入法按平台规范不产生任何窗口 inset（官方 WindowInsets.Type.ime 文档），
 * 那种情况本类也测不出来——只能让用户关掉悬浮键盘，日志里 shown=false 就是它。
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
        //官方给的可见性单独记：ime 高度为 0 但这里也为 false，多半是悬浮/未停靠键盘，系统规范下就是不上报
        val shown = wi?.isVisible(WindowInsetsCompat.Type.ime()) == true
        //官方给了就别叠加，否则键盘高度会被算两遍
        if (imePx > 0) {
            fallback = 0
            log("官方上报 shown=$shown", root, imePx, navPx, 0, 0)
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
        log("可见区域差 shown=$shown", root, imePx, navPx, covered, fb)
    } //按可见区域差算键盘高度
    private fun log(how: String, root: View, ime: Int, nav: Int, covered: Int, fb: Int) {
        //只在数值变化时记一条：ROM 到底是「不报 insets」还是「可见区域也不变」，靠这一行分辨
        val s = "$how root=${root.height} ime=$ime nav=$nav covered=$covered fb=$fb"
        if (s == lastLog) return
        lastLog = s
        android.util.Log.w("Ime", s)
    } //诊断日志
} //键盘兜底