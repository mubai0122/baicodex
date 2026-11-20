/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:20
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.overlay
import androidx.compose.ui.res.stringResource
import b.ai.R
import b.ai.ui.glass.GlassPerf

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
                            b.ai.App.self.getString(R.string.tx_983d58f9, gap, GlassPerf.scrolling) +
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