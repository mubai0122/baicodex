package b.ai.device.shell.root
import b.ai.device.shell.local.ShellResult
import b.ai.device.shell.local.drainProcess
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import b.ai.App
import b.ai.R
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shizuku 通道：binder/权限真检测 + UserService(shell 身份) 执行命令 */
object Shiz {
    const val PKG = "moe.shizuku.privileged.api"
    const val REQ_CODE = 4210
    private const val TAG = "BaiShiz"
    /** 单次等用户服务回调的上限：这个值是"每条命令的固定开销"，5s×2 就是原来那 10s */
    private const val BIND_TIMEOUT = 3_500L
    /** 连不上之后多久内不再试用户服务、直接走 root */
    private const val BAD_QUIET = 60_000L
    internal const val DESC = ExecProto.DESC
    internal const val TX_EXEC = ExecProto.TX_EXEC
    internal const val TX_UID = ExecProto.TX_UID

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

    /** debuggable 必须跟 App 实际的 debuggable 标志一致：写死 false 时 debug 包起不来用户服务，只会一直“连接失败” */
    private val dbg: Boolean
        get() = (App.self.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val svcArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(App.self, ShizukuShell::class.java))
            .daemon(false)
            .version(2)
            .tag("bai-exec")
            .processNameSuffix("exec")
            .debuggable(dbg)
    }

    private val svcConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service
            if (service == null) bindErr = "用户服务返回空 binder"
            Log.i(TAG, "onServiceConnected binder=" + (service != null))
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected")
            remote = null
            bound = false
            latch = CountDownLatch(1)
        }

        //服务进程起来但 onBind 给不出 binder / binder 中途死掉：这两种回调以前没接，于是只能干等到超时
        override fun onNullBinding(name: ComponentName?) {
            bindErr = "用户服务进程起来了但 onBind 返回空（onNullBinding）"
            Log.w(TAG, "onNullBinding " + name)
            latch.countDown()
        }

        override fun onBindingDied(name: ComponentName?) {
            bindErr = "用户服务 binder 已失效（onBindingDied）"
            Log.w(TAG, "onBindingDied " + name)
            remote = null
            bound = false
            latch.countDown()
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

    /** 上次连接失败的原因，给界面看，别再只回一句“连接失败” */
    @Volatile
    private var bindErr: String? = null

    /** 连接状态归零：超时或断链都要清，否则 bound=true 会让后面每次都干等一个永远不来的回调 */
    private fun resetConn() {
        bound = false
        remote = null
        latch = CountDownLatch(1)
        runCatching { Shizuku.unbindUserService(svcArgs, svcConn, false) }
    } //清连接状态

    /** 用户服务确诊坏掉的静默期：这段时间直接用 root，别再每条命令都白等一轮超时 */
    @Volatile
    private var badUntil = 0L

    private fun connect(timeoutMs: Long = 5_000L): IBinder? {
        remote?.let { if (it.isBinderAlive) return it else remote = null }
        if (!Shizuku.pingBinder()) return null
        if (!bound) {
            bindErr = null
            val r = runCatching { Shizuku.bindUserService(svcArgs, svcConn) }
            if (r.isFailure) {
                val e = r.exceptionOrNull()
                bindErr = "bindUserService 失败：" + (e?.message ?: e?.javaClass?.simpleName.orEmpty())
                return null
            }
            bound = true
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            bindErr = "等用户服务回调超时（${timeoutMs}ms），进程可能没起来"
            resetConn()
        }
        return remote
    } //只绑一次：每次新建 args/conn 会让 Shizuku 侧连接累积

    //拿 binder：一次短超时 + 失败静默期。以前是 connect() 5s 超时再 connect(5s)，串行就是每条命令白等 10s
    private fun binder(): IBinder? {
        if (SystemClock.elapsedRealtime() < badUntil) return null
        val t0 = SystemClock.elapsedRealtime()
        connect(BIND_TIMEOUT)?.let { badUntil = 0; return it }
        //只有绑定阶段"立刻"报错才值得清干净补试一次；等满超时说明服务进程根本不在，重试纯浪费
        if (SystemClock.elapsedRealtime() - t0 < 600L) {
            resetConn()
            connect(BIND_TIMEOUT)?.let { badUntil = 0; return it }
        }
        badUntil = SystemClock.elapsedRealtime() + BAD_QUIET
        Log.w(TAG, "user service marked bad for " + BAD_QUIET + "ms: " + (bindErr ?: ""))
        return null
    } //取用户服务 binder


    /** 用户服务起不来就直说，不再偷偷换 root：换通道会把真正的原因埋掉 */
    private fun whyFail(): ShellResult = ShellResult(-1, "[Shizuku] 用户服务连不上：" + (bindErr ?: "原因未知"))

    //服务端直接起进程：不经过用户服务。用户服务在改过 LoadedApk.makeApplicationInner 的 ROM（Xiaomi/HyperOS/MediaTek）上必崩：Application.getProcessName() 为 null → NPE；newProcess 是 Shizuku 自带的私有静态方法，反射调它
    private val newProc by lazy {
        runCatching {
            Shizuku::class.java
                .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .apply { isAccessible = true }
        }.getOrNull()
    }

    private fun execDirect(cmd: String, input: String?, timeoutMs: Long): ShellResult {
        val m = newProc ?: return ShellResult(-1, "[Shizuku]直连执行失败：当前 Shizuku 版本没有 newProcess")
        return try {
            val p = m.invoke(null, arrayOf("/system/bin/sh", "-c", cmd), null, null) as Process
            val r = drainRemote(p, timeoutMs, input)
            Log.i(TAG, "direct ok code=" + r.code + " cmd=" + cmd.take(40))
            r
        } catch (e: Throwable) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "direct exec failed: " + cause + " cmd=" + cmd.take(40))
            ShellResult(-1, "[Shizuku]直连执行失败：" + (cause.message ?: cause.javaClass.simpleName))
        }
    } //服务端起进程执行

    //收 ShizukuRemoteProcess 的输出：它的 exitValue 会抛 IllegalArgumentException，不能用通用的 drainProcess（那套靠 waitFor(超时) 轮询 exitValue）
    private fun drainRemote(p: Process, timeoutMs: Long, input: String?): ShellResult {
        val sb = StringBuilder()
        fun pump(s: java.io.InputStream?) {
            Thread {
                runCatching { s?.bufferedReader()?.forEachLine { l -> synchronized(sb) { sb.appendLine(l) } } }
            }.apply { isDaemon = true }.start()
        }
        pump(p.inputStream)
        pump(p.errorStream)
        runCatching {
            if (input != null) p.outputStream.use { it.write(input.toByteArray()) } else p.outputStream.close()
        }
        var code = -1
        val waiter = Thread { runCatching { code = p.waitFor() } }.apply { isDaemon = true }
        waiter.start()
        waiter.join(timeoutMs)
        val timeout = waiter.isAlive
        if (timeout) {
            runCatching { p.destroy() }
            waiter.join(1500)
        }
        Thread.sleep(60)
        val text = synchronized(sb) { sb.toString() }.trim()
        return if (timeout) ShellResult(-1, "$text\n[超时 ${timeoutMs}ms，已强制结束]") else ShellResult(code, text)
    } //收远端进程输出

    /** 经用户服务事务执行：直连不可用时的老通道 */
    private fun transact(b: IBinder, cmd: String, input: String?, timeoutMs: Long): ShellResult {
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
    } //用户服务事务

    fun run(cmd: String, input: String? = null, timeoutMs: Long = 120_000L): ShellResult {
        if (!Shizuku.pingBinder()) {
            return ShellResult(-1, "[Shizuku] 服务未运行：先在 Shizuku App 里启动服务")
        }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            return ShellResult(-1, "[Shizuku] 未授权：先到权限步骤点「申请 Shizuku 授权」")
        }
        //首选用户服务：这是 Shizuku 唯一公开的 shell 通道；拿不到 binder 或事务层失败才退直连
        val b = binder()
        if (b != null) {
            val r = transact(b, cmd, input, timeoutMs)
            if (!r.out.startsWith("[Shizuku]")) return r
            return ShellResult(-1, r.out + "；" + execDirect(cmd, input, timeoutMs).out)
        }
        val direct = execDirect(cmd, input, timeoutMs)
        if (!direct.out.startsWith("[Shizuku]直连执行失败")) return direct
        return ShellResult(-1, whyFail().out + "；" + direct.out)
    } //以 shell/root 身份执行命令
}