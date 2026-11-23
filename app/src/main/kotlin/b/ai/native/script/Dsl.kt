/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.native.script
import b.ai.net.api.llm.text

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
        loaded = runCatching { System.loadLibrary("baicodex") }.isSuccess
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

    /**
     * 工具结果治理：public 包装。
     * 注意：native 方法必须是 private —— Kotlin 会给 internal 成员拼上 `$模块名_变体名` 后缀，
     * JNI 符号随之变成 Java_..._nativeGovern_00024BaiAgent_1app_1release，C++ 侧找不到实现会抛
     * UnsatisfiedLinkError（v1.20.3 就因为这里写成 internal 炸过）。
     */
    fun govern(text: String, maxChars: Int): String {
        if (!loaded) return "ERR\nDSL 引擎未加载"
        return runCatching { nativeGovern(text, maxChars) }.getOrElse { "ERR\n${it.message ?: "native 调用失败"}" }
    } //工具结果治理（安全包装）

    private external fun nativeGovern(text: String, maxChars: Int): String
} //DSL 引擎门面