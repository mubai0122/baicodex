/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.device.sys.host

import android.view.View
import android.view.WindowManager
import b.ai.core.cfg.app.Cfg

object SecureWin {
    private class W(val wm: WindowManager, val v: View, val p: WindowManager.LayoutParams)

    private val list = ArrayList<W>()

    fun open(wm: WindowManager, v: View, p: WindowManager.LayoutParams) {
        if (Cfg.noShot) p.flags = p.flags or WindowManager.LayoutParams.FLAG_SECURE
        list.removeAll { it.v === v }
        list.add(W(wm, v, p))
    } //登记一个窗口

    fun close(v: View) {
        list.removeAll { it.v === v }
    } //窗口撤掉了

    fun apply() {
        val on = Cfg.noShot
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        list.removeAll { it.v.parent == null }
        list.forEach { w ->
            w.p.flags = if (on) w.p.flags or flag else w.p.flags and flag.inv()
            runCatching { w.wm.updateViewLayout(w.v, w.p) }
        }
    } //按开关重设所有独立窗口
} //独立窗口防截取