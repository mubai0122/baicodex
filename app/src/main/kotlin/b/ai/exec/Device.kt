package b.ai.exec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import b.ai.App
import b.ai.core.Noti
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 系统交互小工具：剪贴板、打开链接、分享、发本地通知、读今日通知；都在主线程队列上跑，超时给失败而不是卡死 */
object Device {
    private const val CHANNEL = "bai_agent"
    private const val CAP = 20_000

    /** 主线程执行并等结果：剪贴板与通知都被主线程绑定，IO 线程直接调用会偶发 RemoteException */
    private fun main(ms: Long = 5_000, block: () -> String): String {
        val h = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var out = ""
        h.post {
            out = runCatching(block).getOrElse { "失败：" + (it.message ?: it.javaClass.simpleName) }
            latch.countDown()
        }
        if (!latch.await(ms, TimeUnit.MILLISECONDS)) return "失败：主线程执行超时"
        return out
    } //主线程取值

    fun clipGet(): String = main {
        val cm = App.self.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val d = cm.primaryClip
        if (d == null || d.itemCount == 0) return@main "剪贴板是空的"
        val sb = StringBuilder()
        for (i in 0 until d.itemCount) {
            val t = d.getItemAt(i).coerceToText(App.self)?.toString().orEmpty()
            if (t.isNotEmpty()) sb.append(t).append('\n')
        }
        val text = sb.toString().trimEnd()
        if (text.isEmpty()) "剪贴板里没有可读文本（可能是图片或文件）"
        else if (text.length > CAP) text.take(CAP) + "\n…已截断（原长 ${text.length} 字符）"
        else text
    } //读剪贴板

    fun clipSet(text: String): String = main {
        if (text.isEmpty()) return@main "失败：要写入的文字为空"
        val cm = App.self.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("BaiAgent", text))
        "已复制到剪贴板（${text.length} 字符）"
    } //写剪贴板

    fun openUrl(url: String): String = main {
        val u = url.trim()
        if (u.isEmpty()) return@main "失败：url 为空"
        val uri = if (u.contains("://")) Uri.parse(u) else Uri.parse("https://$u")
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { App.self.startActivity(i) }.getOrElse {
            //没有浏览器可接就退回复制链接
            val cm = App.self.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BaiAgent", u))
            return@main "没找到能打开链接的应用，已把链接复制到剪贴板：$u"
        }
        "已打开 $u"
    } //打开链接

    fun shareText(text: String): String = main {
        if (text.isBlank()) return@main "失败：要分享的内容为空"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val i = Intent.createChooser(send, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { App.self.startActivity(i) }.getOrElse {
            return@main "拉起分享面板失败：${it.message}"
        }
        "已拉起分享面板"
    } //分享文本

    fun sendNoti(title: String, text: String): String = main {
        val t = title.trim().ifEmpty { "BaiAgent" }
        val nm = App.self.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Agent 提醒", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
        val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(App.self, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(App.self)
        }
        val builder = n.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(t)
            .setContentText(text.take(120))
            .setAutoCancel(true)
        runCatching {
            nm.notify(System.currentTimeMillis().toInt(), builder.build())
        }.getOrElse { return@main "发通知失败（可能没给通知权限）：${it.message}" }
        "已发通知：$t"
    } //发本地通知

    /** 今日通知清单：按应用筛，时间倒序，最多 limit 条 */
    fun notiList(limit: Int, app: String): String {
        val c = App.self
        if (!Noti.allow(c)) return "还没给通知使用权：到 设置 → 权限 → 通知读取 里开一下"
        val total = Noti.count(c)
        if (total <= 0) return "今天还没有收到通知（或监听刚开启）"
        val kw = app.trim()
        val rows = Noti.today(c, 0).asReversed()
            .filter { kw.isEmpty() || it.app.contains(kw, true) }
            .take(limit.coerceIn(1, 200))
        if (rows.isEmpty()) return "今天 $total 条通知里没有来自「$kw」的"
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val sb = StringBuilder("今天共 $total 条" + if (kw.isEmpty()) "" else "，来自「$kw」的 ${rows.size} 条")
        sb.append("：\n")
        for (r in rows) sb.append(fmt.format(Date(r.at))).append("  ").append(r.app).append("·").append(r.title).append('\n')
        return sb.toString().trimEnd()
    } //读今日通知
} //系统交互
