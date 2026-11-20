/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:14
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * 任务运行期的 CPU / 网络保活锁。
 *
 * 背景（1.18.4 用户反馈「一换后台 AI 就不跑了」）：
 *   Agent 的流式请求跑在普通 IO 线程上，屏幕熄灭或系统进入浅睡眠后 CPU 会被挂起，
 *   读流线程停摆，直到 HttpURLConnection 的 readTimeout（300s）才抛错——
 *   用户体感就是「切后台，AI 不跑了」，回来只看到卡在半句。
 *   PARTIAL_WAKE_LOCK 保证 CPU 不进 suspend；WifiLock 保证 Wi-Fi 不被省电策略断开。
 *
 * 用法（只给 Island 用，成对调用）：
 *   Keep.acquire() →任务开始
 *   Keep.release() →任务收工 / 用户停手
 *
 * 安全兜底：
 *   1. 引用计数：主智能体 + 子智能体并发时只有 0→1 那次真加锁，1→0 那次真解锁；
 *   2. 30 分钟自动释放：任何异常路径忘了解锁也不会长期耗电（每次 acquire 重新计时）。
 */
package b.ai.device.sys.keep

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import b.ai.App

object Keep {
    /** Wakelock tag：日志与 dumpsys power 里能一眼认出来 */
    private const val TAG_WAKE = "baicodex:task"

    /** WifiLock tag */
    private const val TAG_WIFI = "baicodex:wifi"

    /** 兜底超时：异常路径忘了解锁时最多占这么久（30 分钟） */
    private const val TIMEOUT_MS = 30 * 60 * 1000L

    /** 当前持有者数量（主 + 子智能体） */
    private var count = 0

    private var wake: PowerManager.WakeLock? = null

    private var wifi: WifiManager.WifiLock? = null

    /** 任务开始：加锁（可重入，靠计数真正生效一次） */
    @Synchronized
    fun acquire() {
        count++
        if (count > 1) { //已经有锁在身，只把兜底超时往后推，避免中途被系统自动放掉
            runCatching { wake?.acquire(TIMEOUT_MS) }
            return
        }
        val app = App.self
        if (wake == null) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            //setReferenceCounted(false)：由本对象自己管计数，系统那层不要再叠一层
            wake = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG_WAKE)?.apply {
                setReferenceCounted(false)
            }
        }
        runCatching { wake?.acquire(TIMEOUT_MS) }
        if (wifi == null) {
            val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            //高功耗模式：屏幕熄了也不让 Wi-Fi 进省电，否则流会直接断在半句
            @Suppress("DEPRECATION")
            wifi = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG_WIFI)?.apply {
                setReferenceCounted(false)
            }
        }
        runCatching { wifi?.acquire() }
    } //加锁

    /** 任务收工 / 用户停手：减计数，归零才真解锁 */
    @Synchronized
    fun release() {
        if (count > 0) count--
        if (count > 0) return //还有别的智能体在跑，锁留着
        runCatching { if (wake?.isHeld == true) wake?.release() }
        runCatching { if (wifi?.isHeld == true) wifi?.release() }
    } //解锁

    /** 是否正在持锁：设置页 / 日志排查用 */
    @Synchronized
    fun held(): Boolean = count > 0
} //任务保活锁
