package b.ai.native.security

import b.ai.App
import b.ai.core.data.io.Layout
import java.io.File

/**
 * Rust 内核（bx-kernel）的 Android 绑定：薄层，只做三件事
 *
 * 1. 选数据根（[Layout.KERNEL]，钉死，掉存储时报错而不是换根）；
 * 2. 调 C ABI（JNI 只翻译类型，业务全在 Rust 的 core/feature/vault|prefs 里）；
 * 3. 把错误码翻译成 Kotlin 异常（[KernelException.code]）。
 *
 * 这里刻意**不做缓存**：缓存已经在 Rust 的 Vault / Prefs 里有一份（带锁），
 * 上层再缓一份就是第二个真相来源 —— 旧 Vault 丢写正是这么来的。
 *
 * 当前阶段是「并行接入」：只跑自检与读写探针，不替换旧 [Vault] / Prefs，
 * 两套并存但不双写，删掉这个文件即可完整回滚。
 */
object BxKernel {
    /** 内核错误码，与 Rust 侧 `ErrorCode` 一一对应 */
    object Code {
        const val OK = 0
        const val IO = 1
        const val NOT_FOUND = 2
        const val CORRUPT = 3
        const val WRONG_KEY = 4
        const val LOCKED = 5
        const val BAD_KEY = 6
        const val VERSION = 7
        const val ROOT = 8
        const val FAULT = 9
        const val INVALID = 10
        /** 这个 ABI 没把 Rust 编进来（只有 arm64-v8a 会编） */
        const val UNAVAILABLE = 11
    }

    /** so 是否加载成功：只在第一次访问时试一次，失败不重试以免反复刷日志 */
    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    /** 内核是否可用（so 加载成功 + 这个 ABI 带了 Rust 实现） */
    val available: Boolean by lazy {
        if (!loaded) false else runCatching { nativeAvailable() }.getOrDefault(false)
    }

    /** 句柄；0 表示未打开 */
    @Volatile
    private var handle: Long = 0L

    /** 是否已打开 */
    val isOpen: Boolean get() = handle != 0L

    /** 数据根：私有目录 data/kernel，内核自管主密钥也在里面（meta/master.key，0600） */
    fun root(): File = Layout.dir(App.self, Layout.KERNEL)

    /**
     * 打开内核（主密钥由内核自管，作用域是应用私有目录）。
     *
     * @return [Code.OK] 或错误码；失败原因见 [lastError]
     */
    @Synchronized
    fun open(): Int {
        if (!available) return Code.UNAVAILABLE
        if (handle != 0L) return Code.OK
        val h = runCatching { nativeOpenDefault(root().absolutePath) }.getOrDefault(0L)
        if (h == 0L) return lastCode().let { if (it == Code.OK) Code.IO else it }
        handle = h
        return Code.OK
    } //打开

    /** 读一条凭据；不存在返回 null，出错抛 [KernelException] */
    fun get(bucket: String, name: String): String? {
        val h = openOrThrow()
        val v = nativeGet(h, bucket, name)
        if (v == null) {
            val code = lastCode()
            if (code != Code.OK && code != Code.NOT_FOUND) throw KernelException(code, lastError())
            return null
        }
        return v
    } //读凭据

    /** 写一条凭据；`value` 为空等于删除 */
    fun put(bucket: String, name: String, value: String) {
        val h = openOrThrow()
        val code = nativePut(h, bucket, name, value)
        if (code != Code.OK) throw KernelException(code, lastError())
    } //写凭据

    /** 删一条凭据 */
    fun remove(bucket: String, name: String) {
        val h = openOrThrow()
        val code = nativeDel(h, bucket, name)
        if (code != Code.OK) throw KernelException(code, lastError())
    } //删凭据

    /** 一个桶的全量快照（JSON 文本） */
    fun snapshot(bucket: String): String {
        val h = openOrThrow()
        return nativeSnapshot(h, bucket) ?: throw KernelException(lastCode(), lastError())
    } //快照

    /** 桶与状态（JSON 数组文本） */
    fun buckets(): String {
        val h = openOrThrow()
        return nativeBuckets(h) ?: throw KernelException(lastCode(), lastError())
    } //桶列表

    /** 读配置项，返回 JSON 值文本；没有返回 null */
    fun prefsGet(page: String, key: String): String? {
        val h = openOrThrow()
        return nativePrefsGet(h, page, key)
    } //读配置

    /** 写配置项，`json` 必须是合法 JSON 值文本（`null` 等于删除） */
    fun prefsSet(page: String, key: String, json: String) {
        val h = openOrThrow()
        val code = nativePrefsSet(h, page, key, json)
        if (code != Code.OK) throw KernelException(code, lastError())
    } //写配置

    /** 一整页配置（JSON 对象文本） */
    fun prefsPage(page: String): String {
        val h = openOrThrow()
        return nativePrefsPage(h, page) ?: throw KernelException(lastCode(), lastError())
    } //整页配置

    /**
     * 自检：在内核自己的临时桶里跑「写 → 读 → 对账 → 删」。
     *
     * @return JSON 文本（含 ok / version / schema / backend），不可用时返回空串
     */
    fun selfcheck(): String = if (!available) "" else runCatching {
        nativeSelfcheck(root().absolutePath).orEmpty()
    }.getOrDefault("")

    /** 内核版本（不可用返回空串） */
    fun version(): String = if (!loaded) "" else runCatching { nativeVersion().orEmpty() }.getOrDefault("")

    /** 最近一次错误的可读文本 */
    fun lastError(): String = if (!loaded) "libbaicodex 没加载起来" else runCatching { nativeLastError().orEmpty() }.getOrDefault("")

    /** 最近一次错误的数值码 */
    fun lastCode(): Int = if (!loaded) Code.UNAVAILABLE else runCatching { nativeLastCode() }.getOrDefault(Code.UNAVAILABLE)

    /** 关闭；可重复调用 */
    @Synchronized
    fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) runCatching { nativeClose(h) }
    } //关闭

    /** 拿句柄；没打开就先开，开不起来按错误码抛 */
    private fun openOrThrow(): Long {
        if (handle != 0L) return handle
        val code = open()
        if (code != Code.OK) throw KernelException(code, lastError())
        return handle
    } //确保已打开

    /** 内核错误，带稳定错误码，便于界面区分「换过种子」与「文件坏了」 */
    class KernelException(val code: Int, message: String) : Exception(message)

    private external fun nativeAvailable(): Boolean
    private external fun nativeOpenDefault(root: String): Long
    private external fun nativeClose(h: Long)
    private external fun nativeGet(h: Long, bucket: String, name: String): String?
    private external fun nativePut(h: Long, bucket: String, name: String, value: String): Int
    private external fun nativeDel(h: Long, bucket: String, name: String): Int
    private external fun nativeSnapshot(h: Long, bucket: String): String?
    private external fun nativeBuckets(h: Long): String?
    private external fun nativePrefsGet(h: Long, page: String, key: String): String?
    private external fun nativePrefsSet(h: Long, page: String, key: String, json: String): Int
    private external fun nativePrefsPage(h: Long, page: String): String?
    private external fun nativeSelfcheck(root: String): String?
    private external fun nativeVersion(): String?
    private external fun nativeLastError(): String?
    private external fun nativeLastCode(): Int
} //Rust 内核绑定
