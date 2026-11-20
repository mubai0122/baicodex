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

import android.util.Log
import b.ai.core.cfg.app.Bai
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 服务端下发的用户协议：版本号 + 当前语言的正文；拉不到就返回 null，客户端退回内置 assets */
object TosDoc {
    private val BASE: String get() = Bai.api("/api/tos")
    private const val TAG = "bai.tos"

    /** version 决定要不要重新同意，lang 是正文实际语言，body 是纯文本正文 */
    data class Doc(val version: Int, val lang: String, val updated: String, val body: String)

    /** 按语言拉一次协议：lang 只传当前界面语言，服务端按需返回对应正文，不整包下发 */
    fun fetch(lang: String): Doc? {
        val conn = (URL("$BASE/get.php?lang=$lang").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 6000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) return null
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (root.optInt("code") != 200) return null
            val d = root.optJSONObject("data") ?: return null
            val v = d.optInt("version", 0)
            val body = d.optString("body")
            if (v <= 0 || body.isBlank()) return null
            return Doc(version = v, lang = d.optString("lang", lang), updated = d.optString("updated").trim(), body = body)
        } catch (e: Exception) {
            Log.w(TAG, "协议拉取失败: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    } //拉一次协议
}