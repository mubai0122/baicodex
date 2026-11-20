/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:17
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.net.doc
import b.ai.net.api.llm.text

import android.util.Log
import b.ai.core.cfg.app.Bai
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 停机开关：服务端 api/get/isuse.php 返回 false 时应用侧拦下并退出；读不到一律按 true 放行 */
object Isuse {
    private val ENDPOINT: String get() = Bai.api("/api/get/isuse.php")
    private const val TAG = "bai.isuse"

    /** 读开关：连 3 秒 / 读 3 秒，任何异常都当 true——服务端故障不能把所有人挡在门外 */
    fun on(): Boolean = runCatching {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 3000
        c.requestMethod = "GET"
        c.setRequestProperty("User-Agent", "BaiAgent-Isuse")
        val text = try {
            val http = c.responseCode
            if (http !in 200..299) error("HTTP $http")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        val body = JSONObject(text)
        val v = body.optJSONObject("data")?.optBoolean("isuse", true) ?: true
        Log.i(TAG, "停机开关 isuse=$v")
        v
    }.getOrElse {
        Log.w(TAG, "停机开关读取失败，按开启放行: ${it.message}")
        true
    } //读开关
} //停机开关