/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:58:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 余额查询：和手机端同一套（DeepSeek 兼容的 /user/balance）。
 *  ① 先按当前连接的 origin 查（网关如果实现了这个接口就直接用网关）；
 *  ② 网关没有就退到官方 https://api.deepseek.com/user/balance；
 *  ③ 拿不到就明说原因，不编数字。返回 {balance_infos:[{currency,total_balance}]}。
 * 结果缓存 60 秒，避免预算卡和设置页反复打接口。
 */

package b.ai.desktop.core.balance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.desktop.core.config.ModelProfile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一次余额查询结果 */
data class Balance(
    /** 有值才算查到 */
    val num: Double? = null,
    /** 展示文案，例如「12.34 元」 */
    val text: String = "",
    val cny: Boolean = true,
    /** 数据来自哪个端点 */
    val source: String = "",
    val at: Long = 0L,
    val error: String? = null,
) {
    val ok: Boolean get() = num != null

    fun below(threshold: Double): Boolean = cny && num != null && num < threshold
}

object BalanceWatch {
    private const val Official = "https://api.deepseek.com/user/balance"

    /** 最近一次结果，界面直接读 */
    var last by mutableStateOf<Balance?>(null)
        private set

    var lastMs by mutableStateOf(0L)
        private set

    var busy by mutableStateOf(false)
        private set

    /** 60 秒内不重复请求；force 用于「刷新」按钮 */
    suspend fun refresh(profile: ModelProfile?, force: Boolean): Balance? {
        if (profile == null || profile.apiKey.isBlank()) {
            val miss = Balance(error = "没有可用的 API Key")
            last = miss
            return miss
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastMs < 60_000 && last != null) return last
        if (busy) return last

        busy = true
        val result = withContext(Dispatchers.IO) { fetch(profile) }
        last = result
        lastMs = System.currentTimeMillis()
        busy = false
        return result
    }

    /** 冷却剩余秒数（预算卡倒计时用） */
    fun cooldownLeft(now: Long = System.currentTimeMillis()): Int {
        val left = 60_000 - (now - lastMs)
        return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
    }

    private fun fetch(profile: ModelProfile): Balance {
        val origin = runCatching { URI(profile.baseUrl).let { "${it.scheme}://${it.authority}" } }.getOrNull()
        val candidates = buildList {
            if (!origin.isNullOrBlank()) add("$origin/user/balance")
            add(Official)
        }.distinct()

        val errors = mutableListOf<String>()
        candidates.forEach { url ->
            val attempt = runCatching { request(url, profile.apiKey) }
            attempt.fold(
                onSuccess = { return it },
                onFailure = { errors += (it.message ?: "请求失败").take(100) },
            )
        }
        // 两个端点都 401：通常是「网关没有余额接口 + Key 不是官方 Key」这种情况，直接给可操作的提示
        val message = if (errors.isNotEmpty() && errors.all { it.contains("401") }) {
            "当前 Key 查不到余额：网关没有 /user/balance，官方接口又不认这个 Key。" +
                "到「余额查询来源」选一条 api.deepseek.com 的官方连接。"
        } else {
            errors.firstOrNull() ?: "余额接口不可用"
        }
        return Balance(error = message, at = System.currentTimeMillis())
    }

    private fun request(url: String, apiKey: String): Balance {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
        }
        try {
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status：${body.take(100)}")
            }

            val root = Json.parseToJsonElement(body).jsonObject
            val info: JsonObject = root["balance_infos"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw IllegalStateException("返回里没有 balance_infos")
            val raw = info["total_balance"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val currency = info["currency"]?.jsonPrimitive?.contentOrNull?.uppercase(Locale.ROOT) ?: "CNY"
            val num = raw.toDoubleOrNull()
                ?: return Balance(text = raw.ifBlank { "无数据" }, cny = currency == "CNY", source = url, at = System.currentTimeMillis())

            val cny = currency == "CNY"
            val unit = if (cny) "元" else " $currency"
            return Balance(
                num = num,
                text = String.format(Locale.ROOT, "%.2f", num) + unit,
                cny = cny,
                source = url,
                at = System.currentTimeMillis(),
            )
        } finally {
            conn.disconnect()
        }
    }
}
