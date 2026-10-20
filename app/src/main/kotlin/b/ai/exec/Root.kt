package b.ai.exec

import java.io.File

/**
 * root 通道：每条命令一次 `su -c`，stdout/stderr 由本进程直接读，退出码取进程真实值。
 * 不用 libsu 常驻会话——本机 KernelSU 下它连 `echo hi` 都读不回任何输出，退出码也不可信。
 */
object Root {
    enum class State { NO_SU, DENIED, TIMEOUT, GRANTED }

    /** 等 su 授权框的上限：够用户点授权框，又不至于把工具调用拖死 */
    private const val GRANT_TIMEOUT = 60_000L

    data class Probe(val state: State, val desc: String, val suPath: String?) {
        val ok: Boolean get() = state == State.GRANTED
    }

    private val commonPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su"
    )

    @Volatile
    private var cached: String? = null

    fun init() {
        //一次性 su 不需要预热任何 shell；保留入口让调用点不用改
    } //占位初始化

    /** 启动时在后台探一次：把 su 授权框挪到启动阶段，别等第一次工具调用时才卡住 */
    fun warmUp() {
        if (cached != null) return
        Thread { runCatching { probe(GRANT_TIMEOUT) } }.apply { isDaemon = true }.start()
    } //后台预热

    fun suPath(): String? {
        cached?.let { return it }
        val p = commonPaths.firstOrNull { File(it).exists() }
            ?: Shell.run("command -v su", timeoutMs = 5_000).out
                .lineSequence().map { it.trim() }.firstOrNull { it.startsWith("/") }
        cached = p
        return p
    } //设备上是否存在 su 二进制

    /** 一次 su -c：cmd 作为单个参数传入，不用自己拼引号 */
    private fun call(cmd: String, timeoutMs: Long): ShellResult {
        val s = suPath()
            ?: return ShellResult(-1, "设备没有 su：需先解锁 bootloader 并刷入 Magisk / KernelSU / APatch")
        return Shell.exec(listOf(s, "-c", cmd), timeoutMs)
    } //调用示例：Shell.exec 直接读进程输出

    fun probe(timeoutMs: Long = GRANT_TIMEOUT): Probe {
        val path = suPath()
            ?: return Probe(State.NO_SU, "设备没有 su：需先解锁 bootloader 并刷入 Magisk / KernelSU / APatch", null)
        val r = call("id", timeoutMs)
        return when {
            r.code == -1 -> Probe(State.TIMEOUT, "等待 su 授权超时：请在 su 授权框点「允许」后重试", path)
            r.out.contains("uid=0") -> Probe(State.GRANTED, "已获得 root（$path）", path)
            else -> Probe(
                State.DENIED,
                "su 未授权：在 su 管理器里给本应用放行（${r.out.take(120).ifBlank { "无输出，退出码 ${r.code}" }}）",
                path
            )
        }
    } //四态区分：无 su 二进制 / 被拒 / 超时 / 已获得

    fun run(cmd: String, timeoutMs: Long = 120_000L): ShellResult {
        val r = call(cmd, timeoutMs)
        //超时或起不来：call 已经带了原因，原样带回
        if (r.code == -1) return ShellResult(-1, "[root] ${r.out}")
        //退出码非 0 且一个字都没有：基本是 su 被拒（授权框点了拒绝）
        if (r.code != 0 && r.out.isBlank()) {
            return ShellResult(r.code, "[root] 命令返回 ${r.code} 且无输出：多半是 su 被拒，去 su 管理器放行本应用")
        }
        return r
    } //root 身份执行命令：输出与退出码都来自真实进程

    fun reset() {
        cached = null
    }
}