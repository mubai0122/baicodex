package b.ai.core

import org.json.JSONObject

/**
 * skill 模板 DSL 引擎的 Kotlin 门面。
 * 校验与生成都在 C++ 的 dsl.cpp 里，这里只负责搬字符串，绝不在 Kotlin 另写一套规则。
 */
object Dsl {
    /** 引擎 .so 加载状态：加载失败时所有调用直接返回错误，不影响 App 其它功能 */
    @Volatile
    private var loaded = false

    init {
        loaded = runCatching { System.loadLibrary("baidsl") }.isSuccess
    } //载入 native 库

    val ready: Boolean get() = loaded

    /** 加载期校验：通过返回 null，不通过返回带行号的错误 */
    fun lint(tpl: String, kind: String, params: Map<String, String>): String? {
        if (!loaded) return "DSL 引擎未加载"
        val r = nativeLint(tpl, kind, json(params))
        return if (r.startsWith("ERR\n")) r.removePrefix("ERR\n") else null
    } //校验模板

    /** 执行期：校验 + 插值生成最终命令或 http 报文 */
    fun render(
        tpl: String,
        kind: String,
        params: Map<String, String>,
        secrets: Map<String, String>,
        args: Map<String, String>
    ): Pair<Boolean, String> {
        if (!loaded) return false to "DSL 引擎未加载"
        val r = nativeRender(tpl, kind, json(params), json(secrets), json(args))
        return if (r.startsWith("OK\n")) true to r.substring(3) else false to r.removePrefix("ERR\n")
    } //生成命令

    private fun json(m: Map<String, String>): String {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    } //扁平 map → JSON

    private external fun nativeLint(tpl: String, kind: String, params: String): String

    private external fun nativeRender(
        tpl: String, kind: String, params: String, secrets: String, args: String
    ): String
} //DSL 引擎门面