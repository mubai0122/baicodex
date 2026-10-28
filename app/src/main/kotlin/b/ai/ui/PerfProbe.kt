package b.ai.ui

import android.util.Log
import android.view.Choreographer

/**
 * 掉帧探针：只记录帧间隔 ≥ 80ms 的掉帧，并带上当时的玻璃档位。
 * Choreographer 的帧回调只在真有帧要画时触发，页面静止时不空转，常驻成本接近零。
 * 排查时看 `adb logcat -s PerfProbe` 或系统日志里 PerfProbe 这一 tag 就够了。
 */
object PerfProbe {
    private const val TAG = "PerfProbe"
    private var installed = false
    private var lastNanos = 0L

    fun install() {
        if (installed) return
        installed = true
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val prev = lastNanos
                lastNanos = frameTimeNanos
                //首帧没有前一帧可比，跳过
                if (prev != 0L) {
                    val gap = (frameTimeNanos - prev) / 1_000_000L
                    if (gap >= 48L) {
                        Log.w(
                            TAG,
                            "掉帧 ${gap}ms scrolling=${GlassPerf.scrolling} " +
                                "anim=${GlassPerf.anim} dialog=${GlassPerf.dialog} heavy=${GlassPerf.heavy}"
                        )
                    }
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(cb)
    } //安装探针
} //掉帧探针