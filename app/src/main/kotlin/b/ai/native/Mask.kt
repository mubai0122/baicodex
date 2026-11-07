package b.ai.native
import b.ai.core.cfg.app.Cfg
import b.ai.net.api.text

import android.util.Log

/**
 * 数据输出脱敏：规则文本由服务端下发，匹配与替换全在 native（arm64 走 Rust，其余 ABI 走 C++ 同构实现）。
 * 这里是唯一的 Kotlin 入口，任何要吐给用户看的模型输出都过 [text]。
 */
object Mask {
    private const val TAG = "bai.mask"

    /** 规则缓存键：KV 原文，格式见 core/security/mask/rules */
    private const val KEY = "rules"

    /** native 库是否可用：不可用时 text 直接原样返回，绝不因为脱敏把回复吞掉 */
    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    /** 进程起来时先装缓存里的规则，断网也能用上一份 */
    fun boot() {
        if (!ok) {
            Log.w(TAG, "native 未加载，脱敏退化为只靠提示词")
            return
        }
        val cache = Vault.get(KEY, Vault.MASK)
        if (cache.isNotBlank() && !install(cache)) Log.w(TAG, "缓存规则没能装载")
    } //装配缓存规则

    /** 装一份规则文本，成功返回 true；空文本不动，保留上一版 */
    fun install(kv: String): Boolean {
        if (!ok || kv.isBlank()) return false
        return runCatching { nativeRules(kv) }.getOrDefault(false)
    } //载入规则

    /** 真正的过滤入口：开关关着或 native 不可用时原样返回 */
    fun text(s: String): String {
        if (!ok || s.isEmpty() || !Cfg.maskOut) return s
        return runCatching { nativeMask(s) }.getOrDefault(s)
    } //文本脱敏

    /** 当前规则规模：高 8 位键名数，低 8 位前缀数，界面与日志用 */
    fun shape(): Int = if (ok) runCatching { nativeShape() }.getOrDefault(0) else 0

    private external fun nativeRules(kv: String): Boolean
    private external fun nativeMask(s: String): String
    private external fun nativeShape(): Int
} //数据输出脱敏