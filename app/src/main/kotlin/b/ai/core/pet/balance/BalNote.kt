/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 02:30:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.pet.balance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import b.ai.App
import b.ai.MainActivity
import b.ai.R
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.profile.Profiles

/** 余额不足的系统通知：渠道独立，点开回 App；同 id 原地更新，不会刷屏 */
object BalNote {
    private const val CH = "balance_low"
    private const val ID = 0x9A17

    private fun channel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CH, c.getString(R.string.bal_ch_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = c.getString(R.string.bal_ch_desc)
            }
        )
    } //通知渠道

    /** 发一条余额提醒：text 是当前余额文案（如「1.20元」），threshold 是用户设的阈值 */
    fun low(c: Context, text: String, threshold: Int) {
        runCatching {
            channel(c)
            val open = Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                c,
                ID,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = c.getString(R.string.bal_note_title)
            val body = c.getString(R.string.bal_note_text, text, threshold)
            val n = NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            c.getSystemService(NotificationManager::class.java)?.notify(ID, n)
        }
    } //余额提醒
} //余额通知

/** 余额巡检：进 App 查一次，低于阈值就发通知；也缓存最近一次结果给预算卡用 */
object BalWatch {
    /** 最近一次查到的余额（null = 还没查过或查失败） */
    @Volatile
    var last: Bal? = null
        private set

    /** 最近一次查询完成的时间戳（毫秒） */
    @Volatile
    var lastMs: Long = 0L
        private set

    /** 缓存给调用方（预算卡点击查询时顺手刷新） */
    fun put(b: Bal) {
        last = b
        lastMs = System.currentTimeMillis()
    } //写缓存

    /** 进 App 自动查一次：开关关着 / 没有可用配置就直接跳过；Key 从「余额查询来源」解析 */
    fun boot() {
        if (!Cfg.balNotify) return
        val src = Profiles.balanceSource() ?: return
        if (src.apiKey.isBlank()) return
        PetBalance.fetchNumAsync(src.apiKey) { b ->
            put(b)
            //只看人民币：外币余额的阈值没有意义
            if (b.below(Cfg.balThreshold.toDouble())) {
                runCatching { BalNote.low(App.self, b.text, Cfg.balThreshold) }
            }
        }
    } //启动巡检
} //余额巡检