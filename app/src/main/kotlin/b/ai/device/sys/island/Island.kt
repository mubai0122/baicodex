/**
 * 任务通知 / 灵动岛门面。
 *
 * 外部只认这三个方法：
 *   taskStart()  → 一轮任务开始，发常驻通知（支持岛的机型会自动挂上岛）
 *   taskUpdate() → 中途换文案 / 进度，不重起通知
 *   taskDone()   → 一轮任务收工，切完成态；按设置驻留一段时间再撤
 * 具体厂商差异由 Vendor.create() 分派，这里不管。
 */
package b.ai.device.sys.island

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import b.ai.App
import b.ai.R
import b.ai.core.cfg.app.Cfg
import b.ai.device.sys.keep.Keep

object Island {
    private val main = Handler(Looper.getMainLooper())

    /** 厂商实现只造一次；Vendor.rom 在进程内不变（与前台服务共用同一个实例） */
    private val impl: IslandImpl by lazy { Vendor.shared }

    /** 驻留倒计时：新一轮任务起来时必须能撤销上一次的延迟撤通知 */
    private var pending: Runnable? = null

    /** 是否有进行中的任务通知：taskUpdate 靠它判断该不该改字 */
    private var running = false

    private fun defaultTitle(): String = App.self.getString(R.string.island_task_title)

    /** 撤掉预约中的自动收岛 */
    private fun clearPending() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    } //取消倒计时

    /** 一轮任务开始：必须有前台服务 + CPU 锁，islandOn 只决定通知样式 */
    fun taskStart(title: String, text: String) {
        clearPending()
        running = true
        //保活与「要不要显示任务通知」解耦：任务在跑就必须有前台服务与唤醒锁，
        //否则进程一进后台就被系统冻结（1.18.4 用户反馈的「切后台 AI 不跑了」就是这个）
        Keep.acquire()
        val t = title.ifBlank { defaultTitle() }
        //islandOn 关掉时仍起前台服务，只把正文换成最简说明，避免用户以为后台在偷偷跑
        val body = if (Cfg.islandOn) text else App.self.getString(R.string.island_bg_keep)
        impl.show(IslandSpec(t, body, -1, Stage.RUN))
    } //任务开始

    /** 中途更新：只在任务进行中调用，不会重置驻留倒计时 */
    fun taskUpdate(title: String, text: String, progress: Int = -1) {
        if (!Cfg.islandOn || !running) return
        impl.show(IslandSpec(title.ifBlank { defaultTitle() }, text, progress, Stage.RUN))
    } //任务进行中

    /**
     * 一轮任务收工。
     * islandDone 关掉 → 立刻撤；开着 → 先切完成态，再按 islandDoneSec 秒后撤。
     */
    fun taskDone(title: String, text: String) {
        running = false
        //任务收工：CPU / Wi-Fi 锁立刻还回去，完成态只剩一条通知，不需要保活
        Keep.release()
        if (!Cfg.islandOn) {
            clearPending()
            impl.cancel()
            return
        }
        clearPending()
        //完成通知总是发一条（标题是会话名、正文是「任务已完成」），让用户知道这轮收工了。
        //驻留时长按设置走；设置为 0 时也给 8 秒，够看见但不至于一直挂着。
        val sec = if (Cfg.islandDone) Cfg.islandDoneSec.coerceIn(0, 3600) else 0
        val stay = if (sec > 0) sec else 8
        //完成态允许划掉：它不是「正在运行」，没理由长期占着通知栏与胶囊
        impl.show(IslandSpec(title.ifBlank { defaultTitle() }, text, 100, Stage.DONE, pinned = false))
        val r = Runnable {
            pending = null
            impl.cancel()
        } //到点自动收岛
        pending = r
        main.postDelayed(r, stay * 1000L)
    } //任务完成

    /** 用户主动停手 / 退出：不留残留通知 */
    fun cancel() {
        running = false
        clearPending()
        //用户停手 / 进程收尾：锁一定要还，否则白耗电
        Keep.release()
        impl.cancel()
    } //立即撤

    /**
     * 通知不可用的原因；null 表示可用。
     * 设置页要把这行显示出来：岛上不显示的头号原因就是「没给通知权限」或「通道被关」，
     * 不提示的话用户只能看到「没效果」，无从下手。
     */
    fun blockedReason(): String? {
        if (!Cfg.islandOn) return null
        val c = App.self
        //Android 13 起通知是运行时权限，没授予 → 系统侧直接丢弃
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return c.getString(R.string.island_no_perm)
        }
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && !nm.areNotificationsEnabled()) return c.getString(R.string.island_no_perm)
        //通道被单独关掉（应用通知开着，但「任务进度」这个通道被关了）
        if ((Vendor.shared as? BaseIsland)?.channelOff() == true) return c.getString(R.string.island_ch_off)
        return null
    } //为什么没显示
} //任务通知门面