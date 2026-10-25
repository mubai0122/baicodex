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
        //回放的存量通知 postTime 是它当初发布的时间，不是今天的就不当今天收到
        val at = s.postTime
        if (!b.ai.core.Noti.isToday(at)) return
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(s.packageName, 0)).toString()
        }.getOrDefault(s.packageName)
        b.ai.core.Noti.add(this, s.packageName, app, title, false, at)
        fire(s.packageName, app, title)
    } //收到一条

    /** 通知触发：规则命中且 Agent 空闲就把 prompt 丢给它；忙时丢掉这一条，不排队堵住 */
    private fun fire(pkg: String, app: String, title: String) {
        runCatching {
            b.ai.core.Triggers.init(this)
            val hit = b.ai.core.Triggers.match(pkg, app, title) ?: return
            if (b.ai.agent.Agent.busy) return
            b.ai.agent.Agent.send(hit.prompt)
        }
    } //命中规则开跑
} //通知监听服务