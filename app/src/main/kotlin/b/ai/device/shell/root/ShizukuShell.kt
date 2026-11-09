package b.ai.device.shell.root
import b.ai.device.shell.local.drainProcess
import b.ai.device.shell.local.ShellResult

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import java.io.File
/** 跑在 Shizuku 用户服务进程里的命令执行器（shell 或 root 身份，取决于 Shizuku 后端） */
class ShizukuShell : Service() {
    init {
        //把死因写盘：app_process 子进程的 stderr 不进 logcat，只能自己落文件
        dump("ctor")
        Thread.setDefaultUncaughtExceptionHandler { t, e -> dump("uncaught/" + t.name + " " + Log.getStackTraceString(e)) }
    } //构造期探针 + 兜底异常捕获

    override fun attachBaseContext(base: Context) {
        dump("attach-in")
        try {
            super.attachBaseContext(base)
            dump("attach-ok")
        } catch (e: Throwable) {
            dump("attach-throw " + Log.getStackTraceString(e))
            throw e
        }
    } //attach 探针

    override fun onCreate() {
        dump("create-in")
        try {
            super.onCreate()
            dump("create-ok")
        } catch (e: Throwable) {
            dump("create-throw " + Log.getStackTraceString(e))
            throw e
        }
    } //onCreate 探针

    override fun onBind(intent: Intent?): IBinder {
        dump("bind")
        return ExecBinder()
    } //onBind 探针

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun dump(step: String) {
        Log.i(TAG, step + " pid=" + Process.myPid() + " uid=" + Process.myUid())
        runCatching {
            File(MARK).appendText(
                step + " pid=" + Process.myPid() + " uid=" + Process.myUid() +
                    " t=" + System.currentTimeMillis() + "\n"
            )
        }
    } //落盘探针

    private class ExecBinder : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val caller = Binder.getCallingUid()
            val allowed = caller == Process.myUid() || caller == 0 || caller == 2000
            if (!allowed) {
                data.enforceInterface(ExecProto.DESC)
                reply?.writeException(SecurityException("caller uid $caller not allowed"))
                return true
            }
            when (code) {
                ExecProto.TX_EXEC -> {
                    data.enforceInterface(ExecProto.DESC)
                    val cmd = data.readString() ?: ""
                    val stdin = data.readString()
                    val timeout = data.readInt().toLong()
                    val r = exec(cmd, stdin, if (timeout > 0) timeout else 120_000L)
                    reply?.writeNoException()
                    reply?.writeInt(r.code)
                    reply?.writeString(r.out)
                    return true
                }

                ExecProto.TX_UID -> {
                    data.enforceInterface(ExecProto.DESC)
                    reply?.writeNoException()
                    reply?.writeInt(Process.myUid())
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        } //解析客户端事务并回填结果

        private fun exec(cmd: String, stdin: String?, timeoutMs: Long): ShellResult {
            return try {
                val p = ProcessBuilder("/system/bin/sh", "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                drainProcess(p, timeoutMs, stdin)
            } catch (e: Exception) {
                ShellResult(-1, "[user-service] 启动失败: ${e.message ?: e.javaClass.simpleName}")
            }
        } //在用户服务进程内跑 sh
    }

    companion object {
        const val TAG = "BaiExec"
        const val MARK = "/data/local/tmp/bai_exec_trace"
    }
}