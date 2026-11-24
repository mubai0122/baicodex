package b.ai.core.dsh

/**
 * DshBridge.kt — Kotlin 与 Rust 内核的 JNI 通信桥（阶段 0 占位）
 *
 * 说明：
 * 1. 阶段 0 的 dsh 本体走 proot + Node（见 DshCore），本文件仅预留 Rust 侧接口
 * 2. 阶段 1 由 Rust crate 产出 libdsh_core.so，实现下列 native 方法
 * 3. 未加载到 so 时所有入口返回降级结果，不抛异常，避免影响主流程
 */
object DshBridge {
    /** so 是否可用：阶段 0 一定 false */
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("dsh_core") }.isSuccess
    }

    //JNI 实现（Rust 侧）：阶段 0 无实现，仅在 available 为 true 时调用
    private external fun nativeStart(): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeStatus(): String
    private external fun nativeLogs(): String
    private external fun nativeSendPrompt(prompt: String): String

    /** 启动 Rust 内核；so 缺失时返回 false（由上层回退到 DshCore） */
    fun start(): Boolean = if (available) runCatching { nativeStart() }.getOrDefault(false) else false

    /** 停止 Rust 内核 */
    fun stop(): Boolean = if (available) runCatching { nativeStop() }.getOrDefault(true) else true

    /** 状态（JSON 字符串）；so 缺失返回空对象 */
    fun statusJson(): String = if (available) runCatching { nativeStatus() }.getOrDefault("{}") else "{}"

    /** 日志尾部；so 缺失返回提示 */
    fun logs(): String = if (available) runCatching { nativeLogs() }.getOrDefault("") else "dsh_core.so 未加载（阶段 0）"

    /** 发送 prompt（阶段 1 走 Rust 流式通道）；so 缺失返回空串 */
    fun sendPrompt(prompt: String): String =
        if (available) runCatching { nativeSendPrompt(prompt) }.getOrDefault("") else ""
} //Rust 内核桥