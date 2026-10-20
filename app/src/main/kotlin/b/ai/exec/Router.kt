package b.ai.exec

import b.ai.core.Cfg
import b.ai.core.Paths
import b.ai.core.Perms
import b.ai.core.Route
import b.ai.core.WorkMode
import java.io.File

/**
 * 权限选路 + 零宽路径探测
 *
 * 说明：零宽（CVE-2024-43093 一类）在不同 ROM 上行为不一致，这里不做版本猜测，
 * 而是启动时/开关打开时按候选变换逐个实测 list() 是否成功，命中哪个就用哪个。
 * 全部未命中 = 该设备不支持零宽。
 */
object Router {
    private const val DATA_ROOT = "/storage/emulated/0/Android/data"
    private const val OBB_ROOT = "/storage/emulated/0/Android/obb"

    private val variants: List<Pair<String, (String) -> String>> = listOf(
        "原路径" to { p: String -> p },
        "尾随点" to { p: String -> "$p/." },
        "双斜杠" to { p: String -> p.replace("/Android/", "//Android/") },
        "段内零宽" to { p: String -> p.replace("/Android/", "/Android\u200b/") },
        "尾随零宽" to { p: String -> "$p\u200b" },
        "段后零宽" to { p: String -> p.replace("/data", "/data\u200b").replace("/obb", "/obb\u200b") }
    )

    @Volatile private var picked: Pair<String, (String) -> String>? = null
    @Volatile private var probed = false
    @Volatile private var probing = false

    /** 原路径能不能真的列出 Android/data：canRead 与 isExternalStorageManager 都会骗人，只信 list() */
    @Volatile private var nativeOk = false

    /** 已探测到的结论；null = 还没探测过（不要让 UI 在组合期触发探测） */
    val known: Boolean? get() = if (probed) picked != null else null

    /** 是否可用零宽路径访问 Android/data 与 Android/obb（只读缓存，不触发探测） */
    val zeroWidthUsable: Boolean get() = picked != null

    fun probeName(): String = if (picked != null) (picked?.first ?: "未知") else "不可用"

    /** 后台探测一次，避免在主线程做 File.list() 卡 UI */
    fun probeAsync() {
        if (probed || probing) return
        probing = true
        Thread {
            runCatching { probe() }
            probing = false
        }.apply { isDaemon = true }.start()
    } //后台探测入口

    /** 阻塞探测：只允许在工作线程调用 */
    fun probe(): Boolean {
        probed = true
        picked = null
        nativeOk = canList(DATA_ROOT)
        //原路径能列出来就不用零宽。判据只认 list()：canRead 与 isExternalStorageManager 在 FUSE 上都会骗人
        if (nativeOk) return false
        for (v in variants) {
            if (canList(v.second(DATA_ROOT))) {
                picked = v
                return true
            }
        }
        return false
    } //逐个候选实测

    /** 真的列一次目录：能拿到列表才算这条路通 */
    private fun canList(p: String): Boolean =
        runCatching { File(p).list() != null }.getOrDefault(false)

    fun reset() {
        probed = false
        picked = null
        nativeOk = false
    }

    fun map(path: String): String = picked?.second?.invoke(path) ?: path

    private val restricted = listOf(
        DATA_ROOT, OBB_ROOT, "/data/", "/system/", "/vendor/", "/product/", "/root/", "/proc/"
    )

    fun route(path: String): Route {
        val p = path.trim()
        return when (Cfg.workMode) {
            WorkMode.ROOT -> Route.ROOT
            WorkMode.SHIZUKU -> Route.SHIZUKU
            WorkMode.NORMAL -> {
                val hit = restricted.any { p.startsWith(it) }
                val zwZone = p.startsWith(DATA_ROOT) || p.startsWith(OBB_ROOT)
                when {
                    !hit -> Route.SANDBOX
                    //Android/data 与 obb：原路径实测能列就直接读，其次才轮到零宽
                    zwZone && nativeOk -> Route.ALL_FILES
                    zwZone && Cfg.zeroWidth && zeroWidthUsable -> Route.ZERO_WIDTH
                    else -> Route.SANDBOX
                }
            }
        }
    } //按目标路径与设备状态选路

    /** 「所有文件访问」是否真的生效：系统标志会骗人（uid op 允许但 FUSE 仍拒），必须叠加探测结果 */
    fun allFiles(): Boolean = Perms.allFiles() && nativeOk

    /** 原路径能不能真的列 Android/data（探测结果，不触发探测） */
    val canReadData: Boolean get() = nativeOk

    /** Android/data 与 Android/obb 现在实际靠什么访问，给 UI 与系统提示用 */
    fun dataZone(): String {
        if (known == null) return "检测中…"
        if (nativeOk) {
            return if (Perms.allFiles()) "可直接读写（所有文件访问）" else "可直接读写（原路径）"
        }
        if (zeroWidthUsable) return "零宽路径（${probeName()}）"
        return if (Cfg.zeroWidth) "零宽已开启但本机不支持" else "不可访问（需要 Shizuku / root 或零宽）"
    } //受限目录的实际通道

    /** 手动重探：刚在系统里授予权限、或擦过数据之后用 */
    fun reprobe() {
        reset()
        probeAsync()
    } //重新探测

    /** 修正用户/模型给的不规范路径：相对路径挂到数据根 baicode 下 */
    fun normalize(raw: String): String {
        var p = raw.trim().trim('"').trim('\'')
        val base = Paths.base().absolutePath
        if (p.startsWith("~/")) p = p.replaceFirst("~/", "$base/")
        if (p == "~") p = base
        if (!p.startsWith("/")) p = "$base/$p"
        while (p.contains("//")) p = p.replace("//", "/")
        return p
    } //归一化路径
}