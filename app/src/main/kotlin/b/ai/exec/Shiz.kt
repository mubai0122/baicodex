package b.ai.exec

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
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
    private var permCb: ((Boolean) -> Unit)? = null

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
        if (!Shizuku.pingBinder()) {
            cb(false)
            return
        }
        if (permListener == null) {
            val l = Shizuku.OnRequestPermissionResultListener { code, grant ->
                if (code == REQ_CODE) {
                    permCb?.invoke(grant == PackageManager.PERMISSION_GRANTED)
                    permCb = null
                }
            }
            permListener = l
            Shizuku.addRequestPermissionResultListener(l)
        }
        permCb = cb
        runCatching { Shizuku.requestPermission(REQ_CODE) }.onFailure {
            permCb = null
            cb(false)
        }
    } //申请授权，结果走监听回调（主线程）

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