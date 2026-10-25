package b.ai.exec

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import b.ai.MainActivity
import b.ai.R

/** 桌宠前台服务：只管让悬浮窗活着，显示与交互全在 Pet 里 */
class PetSvc : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Pet.attachApp(this)
        startForegroundCompat()
        Pet.show()
    } //起来就显示

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Pet.hide()
        super.onDestroy()
    } //退出收窗口

    companion object {
        private const val CH = "pet_keep"
        private const val ID = 0x9e1

        fun start(c: Context) {
            runCatching { c.startForegroundService(Intent(c, PetSvc::class.java)) }
        } //启动

        fun stop(c: Context) {
            runCatching { c.stopService(Intent(c, PetSvc::class.java)) }
        } //停止
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, getString(R.string.pet_keep_ch), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.pet_keep_title))
            .setContentText(getString(R.string.pet_keep_text))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        //specialUse 这个类型常量是 API 34 才有的，低版本传进去会被系统拒
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
    } //常驻通知
} //桌宠服务