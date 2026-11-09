package b.ai.device.shell.root
import b.ai.device.shell.local.drainProcess
import b.ai.device.shell.local.ShellResult

import android.app.Service
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
        //探针：服务进程起来后到底有没有加载到本类，看 /data/local/tmp/bai_exec_marker
        runCatching {
            File(MARK).writeText("loaded pid=" + Process.myPid() + " uid=" + Process.myUid() + " at=" + System.currentTimeMillis())
        }
        Log.i(TAG, "init pid=" + Process.myPid() + " uid=" + Process.myUid())
    } //类加载探针

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "onBind pid=" + Process.myPid())
        return ExecBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

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
        const val MARK = "/data/local/tmp/bai_exec_marker"
    }
}