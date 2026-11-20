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

package b.ai.native.calc

/**
 * 计算层桥：记忆打分 / 滑杆几何 / 重试策略。
 *
 * 真正的实现在 Rust（`app/src/main/rust/src/core/feature/` 下 memory 与 compute 两块），
 * 本对象只做 JNI 转发；Kotlin 侧不再保留任何业务公式。
 *
 * 兜底：Rust 只在 arm64-v8a 下编进来，v7a 上所有 native 入口返回零值，
 * 此时滑块几何走 [fallback] —— 那是同一套公式的最小副本，
 * **改公式必须两边一起改**，否则 v7a 的滑块会和 arm64 手感不一致。
 */
object Calc {
    /** Rust 计算层是否被链接进来 */
    val available: Boolean by lazy {
        runCatching { nativeAvailable() }.getOrDefault(false)
    }

    //MARK: - 记录类型（数值与 Rust 常量对齐，运行时取一次即可）
    /** 长期记忆 */
    val kindLong: Int by lazy { if (available) nativeKindLong() else 0 }
    /** 普通记忆 */
    val kindNormal: Int by lazy { if (available) nativeKindNormal() else 1 }
    /** 最多重试次数（不含首次请求） */
    val maxRetry: Int by lazy { if (available) nativeRetryMax().takeIf { it > 0 } ?: 5 else 5 }

    //MARK: - 记忆
    /** 规范化：去空白、压换行、按字符截断到 500 */
    fun normalize(raw: String): String =
        if (available) nativeNormalize(raw) ?: raw.trim() else raw.trim().replace(Regex("\\s+"), " ").take(500)

    /** 去重指纹：忽略大小写与标点差异 */
    fun dedup(text: String): String =
        if (available) nativeDedup(text) ?: text else text.lowercase().filter { it.isLetterOrDigit() }

    /** 关键词召回：返回 JSON `[{"id":1,"score":0.8}]` */
    fun rank(itemsJson: String, query: String, nowMs: Long, limit: Int): String =
        if (available) nativeRank(itemsJson, query, nowMs, limit) ?: "[]" else "[]"

    /** 拼装注入文本：mode = full / smart */
    fun compose(itemsJson: String, mode: String, limit: Int, nowMs: Long, query: String): String =
        if (available) nativeCompose(itemsJson, mode, limit, nowMs, query) ?: "" else ""

    /** 统计：返回 JSON `{long,normal,chars,oldestDays,newestDays}` */
    fun stats(itemsJson: String, nowMs: Long): String =
        if (available) nativeStats(itemsJson, nowMs) ?: "{}" else "{}"

    //MARK: - 滑杆几何
    /** 值 → 比例（0..1） */
    fun sliderFrac(value: Float, min: Float, max: Float): Float =
        if (available) nativeSliderFrac(value, min, max) else fallbackFrac(value, min, max)

    /** 比例 → 滑块中心 x（像素） */
    fun sliderX(frac: Float, trackW: Float, thumbW: Float): Float =
        if (available) nativeSliderX(frac, trackW, thumbW) else fallbackX(frac, trackW, thumbW)

    /** 手指 x → 值（含吸附与夹取） */
    fun sliderValue(
        x: Float, trackW: Float, thumbW: Float,
        min: Float, max: Float, step: Float
    ): Float =
        if (available) nativeSliderValue(x, trackW, thumbW, min, max, step)
        else fallbackValue(x, trackW, thumbW, min, max, step)

    //MARK: - 重试策略
    /** 失败分类：0 不重试 / 1 网络 / 2 超时 / 3 限流 / 4 服务端 */
    fun retryClassify(httpCode: Int, msg: String): Int =
        if (available) nativeRetryClassify(httpCode, msg) else 0

    /** 第 attempt 次失败后是否还允许重试 */
    fun retryAllow(attempt: Int, kind: Int): Boolean =
        if (available) nativeRetryAllow(attempt, kind) == 1
        else kind > 0 && attempt in 1..maxRetry

    /** 第 attempt 次失败后的等待毫秒；Rust 返回 ≤0 时按 0 处理（不等待） */
    fun retryDelay(kind: Int, attempt: Int): Long =
        if (available) nativeRetryDelay(kind, attempt).takeIf { it > 0 } ?: 0L
        else 0L

    //MARK: - 兜底公式（仅在 Rust 缺位时使用，与 core/feature/compute/slider.rs 一一对应）
    private fun fallbackFrac(value: Float, min: Float, max: Float): Float {
        val span = if (max - min > 0f) max - min else 1f
        return ((value - min) / span).coerceIn(0f, 1f)
    } //值→比例

    private fun fallbackX(frac: Float, trackW: Float, thumbW: Float): Float {
        val usable = (trackW - thumbW).coerceAtLeast(0f)
        return thumbW / 2f + frac.coerceIn(0f, 1f) * usable
    } //比例→中心 x

    private fun fallbackValue(
        x: Float, trackW: Float, thumbW: Float,
        min: Float, max: Float, step: Float
    ): Float {
        if (trackW <= 0f) return min
        val span = if (max - min > 0f) max - min else 1f
        val usable = (trackW - thumbW).coerceAtLeast(1f)
        val f = ((x - thumbW / 2f) / usable).coerceIn(0f, 1f)
        var v = min + f * span
        if (step > 0f) v = min + Math.round((v - min) / step) * step
        return v.coerceIn(min, max)
    } //手指 x→值

    private external fun nativeAvailable(): Boolean
    private external fun nativeNormalize(raw: String): String?
    private external fun nativeDedup(raw: String): String?
    private external fun nativeRank(itemsJson: String, query: String, nowMs: Long, limit: Int): String?
    private external fun nativeCompose(
        itemsJson: String, mode: String, limit: Int, nowMs: Long, query: String
    ): String?

    private external fun nativeStats(itemsJson: String, nowMs: Long): String?
    private external fun nativeKindLong(): Int
    private external fun nativeKindNormal(): Int
    private external fun nativeSliderFrac(value: Float, min: Float, max: Float): Float
    private external fun nativeSliderX(frac: Float, trackW: Float, thumbW: Float): Float
    private external fun nativeSliderValue(
        x: Float, trackW: Float, thumbW: Float, min: Float, max: Float, step: Float
    ): Float

    private external fun nativeRetryClassify(httpCode: Int, msg: String): Int
    private external fun nativeRetryAllow(attempt: Int, kind: Int): Int
    private external fun nativeRetryDelay(kind: Int, attempt: Int): Long
    private external fun nativeRetryMax(): Int
} //计算层桥