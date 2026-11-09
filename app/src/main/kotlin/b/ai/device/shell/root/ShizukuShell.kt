package b.ai.device.shell.root
import b.ai.device.shell.local.drainProcess
import b.ai.device.shell.local.ShellResult

import android.os.Binder
import android.os.Parcel
import android.os.Process
import android.util.Log
import java.io.File
/** Shizuku 用户服务入口：Shizuku 在 app_process 里 newInstance 后直接强转 IBinder，不走 Service 生命周期，所以入口类本身必须直接是 Binder */
class ShizukuShell : Binder() {
    init {
        //把死因写盘：app_process 子进程的 stderr 不进 logcat，只能自己落文件
        dump("ctor")
        Thread.setDefaultUncaughtExceptionHandler { t, e -> dump("uncaught/" + t.name + " " + Log.getStackTraceString(e)) }
    } //构造期探针 + 兜底异常捕获
    private val impl = ExecBinder()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TX_DESTROY) {
            dump("destroy")
            System.exit(0)
            return true
        }
        return impl.dispatch(code, data, reply, flags)
    } //把事务转给执行器；Shizuku 发来的销毁事务在此退出进程


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
        fun dispatch(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = onTransact(code, data, reply, flags)

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(ExecProto.DESC)
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
        const val TX_DESTROY = 16_777_115
    } //Shizuku 服务端销毁用户服务用的固定事务号
}