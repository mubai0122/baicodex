package b.ai.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import b.ai.MainActivity
import b.ai.R

/** 插件类任务的系统通知：下载装好、上传提交完都发一条，点开回 App */
object PluginNote {
    private const val CH = "plugin_task"

    private fun channel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CH, "插件任务", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "插件下载安装与上传提交的结果提示"
            }
        )
    } //通知渠道

    /** 发一条结果通知：同 id 原地更新，点开回主界面 */
    fun done(title: String, text: String, id: Int = 0x91A1) {
        val c = b.ai.App.self
        runCatching {
            channel(c)
            val open = Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                c,
                id,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(500)))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            c.getSystemService(NotificationManager::class.java)?.notify(id, n)
        }
    } //结果通知
} //插件任务通知