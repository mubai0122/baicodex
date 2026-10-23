package b.ai.exec

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.provider.Settings
import b.ai.App
import b.ai.R
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shizuku 通道：binder/权限真检测 + UserService(shell 身份) 执行命令 */
object Shiz {
    const val PKG = "moe.shizuku.privileged.api"
    const val REQ_CODE = 4210
    internal const val DESC = "b.ai.exec.IExec"
    internal const val TX_EXEC = 1
    internal const val TX_UID = 2

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    /** 授权结果：区分「没服务」「被永久拒绝」「超时没回应」，UI 才能给对的引导 */
    enum class Perm { GRANTED, DENIED, NEVER_ASK, NO_SERVICE, ERROR }

    data class Probe(val state: State, val desc: String, val uid: Int = -1) {
        val ok: Boolean get() = state == State.READY
    }

    @Volatile
    private var remote: IBinder? = null

    @Volatile
    private var latch = CountDownLatch(1)

    @Volatile
    private var bound = false

    private var permListener: Shizuku.OnRequestPermissionResultListener? = null

    @Volatile
    private var permCb: ((Boolean) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var askSeq = 0

    private val svcArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(App.self, ShizukuShell::class.java))
            .daemon(false)
            .version(1)
            .processNameSuffix("exec")
            .debuggable(false)
    }

    private val svcConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            bound = false
            latch = CountDownLatch(1)
        }
    }

    /** 装了 App 不等于能用，只用来区分"没装"和"没启动" */
    fun installed(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    }.getOrDefault(false)

    fun probe(ctx: Context): Probe {
        if (!Shizuku.pingBinder()) {
            return if (installed(ctx)) {
                Probe(State.NOT_RUNNING, ctx.getString(R.string.shiz_not_run))
            } else {
                Probe(State.NOT_INSTALLED, ctx.getString(R.string.shiz_not_installed))
            }
        }
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        val ver = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            return Probe(State.NO_PERMISSION, ctx.getString(R.string.shiz_old, ver), uid)
        }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED)
        if (granted == PackageManager.PERMISSION_GRANTED) {
            val backend = ctx.getString(if (uid == 0) R.string.shiz_back_root else R.string.shiz_back_adb)
            return Probe(State.READY, ctx.getString(R.string.shiz_ready, ver, uid, backend), uid)
        }
        return Probe(State.NO_PERMISSION, ctx.getString(R.string.shiz_no_perm), uid)
    } //真检测：binder 存活 + 服务版本 + 授权状态

    fun requestPermission(cb: (Boolean) -> Unit) {
        ask { cb(it == Perm.GRANTED) }
    } //旧接口：只要成败

    /** 申请授权：一定回调一次；被「拒绝且不再询问」时不发请求只报状态，六秒没回应算超时 */
    fun ask(cb: (Perm) -> Unit) {
        if (!Shizuku.pingBinder()) {
            cb(Perm.NO_SERVICE)
            return
        }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED)
        if (granted == PackageManager.PERMISSION_GRANTED) {
            cb(Perm.GRANTED)
            return
        }
        //官方语义：这里为 true 表示用户选过「拒绝且不再询问」，再 request 也不会弹窗，只能去 Shizuku 里手动开
        if (runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)) {
            cb(Perm.NEVER_ASK)
            return
        }
        ensureListener()
        val seq = ++askSeq
        permCb = { g ->
            if (seq == askSeq) {
                permCb = null
                cb(if (g) Perm.GRANTED else Perm.DENIED)
            }
        }
        main.postDelayed({
            if (seq == askSeq && permCb != null) {
                permCb = null
                cb(Perm.ERROR)
            }
        }, 6000)
        //requestPermission 只能在主线程发，且必须 binder 活着，否则抛 IllegalStateException
        main.post {
            runCatching { Shizuku.requestPermission(REQ_CODE) }.onFailure {
                if (seq == askSeq) {
                    permCb = null
                    cb(Perm.ERROR)
                }
            }
        }
    } //申请授权（带超时与永久拒绝判定）

    private fun ensureListener() {
        if (permListener != null) return
        val l = Shizuku.OnRequestPermissionResultListener { code, grant ->
            if (code == REQ_CODE) {
                val c = permCb
                permCb = null
                if (c != null) main.post { c.invoke(grant == PackageManager.PERMISSION_GRANTED) }
            }
        }
        permListener = l
        runCatching { Shizuku.addRequestPermissionResultListener(l) }
    } //监听只挂一次

    /** 拉起 Shizuku App：手动授权、启动服务都在这边做 */
    fun openManager(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG)
        if (i != null) {
            runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$PKG"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //打开 Shizuku

    private fun connect(timeoutMs: Long = 10_000L): IBinder? {
        remote?.let { if (it.isBinderAlive) return it else remote = null }
        if (!Shizuku.pingBinder()) return null
        if (!bound) {
            val ok = runCatching { Shizuku.bindUserService(svcArgs, svcConn) }.isSuccess
            if (!ok) return null
            bound = true
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return remote
    } //只绑一次：每次新建 args/conn 会让 Shizuku 侧连接累积

    fun run(cmd: String, input: String? = null, timeoutMs: Long = 120_000L): ShellResult {
        if (!Shizuku.pingBinder()) {
            return ShellResult(-1, "[Shizuku] 服务未运行：先在 Shizuku App 里启动服务")
        }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            return ShellResult(-1, "[Shizuku] 未授权：先到权限步骤点「申请 Shizuku 授权」")
        }
        val b = connect() ?: return ShellResult(-1, "[Shizuku] 用户服务连接失败（服务刚重启？重试一次）")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESC)
            data.writeString(cmd)
            data.writeString(input)
            data.writeInt(timeoutMs.toInt())
            if (!b.transact(TX_EXEC, data, reply, 0)) {
                ShellResult(-1, "[Shizuku] 事务被拒绝（调用方校验失败）")
            } else {
                reply.readException()
                val code = reply.readInt()
                ShellResult(code, reply.readString() ?: "")
            }
        } catch (e: Exception) {
            ShellResult(-1, "[Shizuku] 执行异常: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            reply.recycle()
            data.recycle()
        }
    } //经用户服务以 shell/root 身份执行命令
}