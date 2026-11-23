/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:09
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.pet.balance
import b.ai.net.api.llm.text

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** 余额结果：num 有值才表示成功拿到数字；cny 表示币种是不是人民币（非人民币不做阈值判断） */
data class Bal(val num: Double?, val text: String, val cny: Boolean) {
    /** 低于阈值（只对人民币生效） */
    fun below(cnyThreshold: Double) = cny && num != null && num < cnyThreshold
} //余额结果

/** DeepSeek 开放平台余额查询（峰谷时段判断已迁到 b.ai.core.pet.peak.PeakHours） */
object PetBalance {
    private const val URL_BALANCE = "https://api.deepseek.com/user/balance"
    private const val TIMEOUT_MS = 8000

    private val main = Handler(Looper.getMainLooper())


    /** 后台查余额，结果回主线程；失败也给一句能看的文案（老接口，只要字符串） */
    fun fetchAsync(apiKey: String, cb: (String) -> Unit) {
        fetchNumAsync(apiKey) { cb(it.text) }
    } //异步查（字符串）

    /** 后台查余额，回调带数值 Bal，方便做阈值判断；失败 num 为 null */
    fun fetchNumAsync(apiKey: String, cb: (Bal) -> Unit) {
        if (apiKey.isBlank()) {
            main.post { cb(Bal(null, "", true)) }
            return
        }
        Thread {
            val r = runCatching { fetch(apiKey) }.getOrDefault(Bal(null, "", true))
            main.post { cb(r) }
        }.apply { isDaemon = true }.start()
    } //异步查（数值）

    private fun fetch(apiKey: String): Bal {
        val conn = URL(URL_BALANCE).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
            val code = conn.responseCode
            if (code != 200) return Bal(null, "HTTP $code", true)
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val info = JSONObject(body).optJSONArray("balance_infos")?.optJSONObject(0)
                ?: return Bal(null, "无数据", true)
            val raw = info.optString("total_balance")
            val cur = info.optString("currency", "CNY")
            val cny = cur.equals("CNY", true)
            val num = raw.toDoubleOrNull() ?: return Bal(null, raw, cny)
            val text = String.format(Locale.ROOT, "%.2f", num)
            val unit = if (cny) "元" else " $cur"
            return Bal(num, text + unit, cny)
        } finally {
            conn.disconnect()
        }
    } //同步查
} //余额与峰谷