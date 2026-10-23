package b.ai.exec

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** 通知监听：只把应用名与标题交给 Noti 落盘，正文一个字都不读 */
class NotiSvc : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val s = sbn ?: return
        if (s.packageName == packageName) return
        val n = s.notification ?: return
        //常驻通知（音乐、下载、前台服务）不算用户要看的推送，直接跳过
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(s.packageName, 0)).toString()
        }.getOrDefault(s.packageName)
        b.ai.core.Noti.add(this, s.packageName, app, title, false)
    } //收到一条
} //通知监听服务