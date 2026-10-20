package b.ai.exec

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process

/** 跑在 Shizuku 用户服务进程里的命令执行器（shell 或 root 身份，取决于 Shizuku 后端） */
class ShizukuShell : Service() {
    override fun onBind(intent: Intent?): IBinder = ExecBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private class ExecBinder : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val caller = Binder.getCallingUid()
            val allowed = caller == Process.myUid() || caller == 0 || caller == 2000
            if (!allowed) {
                data.enforceInterface(Shiz.DESC)
                reply?.writeException(SecurityException("caller uid $caller not allowed"))
                return true
            }
            when (code) {
                Shiz.TX_EXEC -> {
                    data.enforceInterface(Shiz.DESC)
                    val cmd = data.readString() ?: ""
                    val stdin = data.readString()
                    val timeout = data.readInt().toLong()
                    val r = exec(cmd, stdin, if (timeout > 0) timeout else 120_000L)
                    reply?.writeNoException()
                    reply?.writeInt(r.code)
                    reply?.writeString(r.out)
                    return true
                }

                Shiz.TX_UID -> {
                    data.enforceInterface(Shiz.DESC)
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
}
