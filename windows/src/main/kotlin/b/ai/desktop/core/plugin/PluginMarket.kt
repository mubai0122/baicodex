/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 插件索引：一个 JSON 地址，形如
 *   { "plugins": [ { "id": "...", "name": "...", "description": "...",
 *                    "version": "1.0.0", "zip": "https://…/x.zip" | "git": "https://…" } ] }
 * 也兼容直接给数组。桌面端不内置任何索引地址——没有源就说没有，不塞假数据。
 */

package b.ai.desktop.core.plugin

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 索引里的一条可安装项 */
data class RemotePlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val source: String,
    /** true = git 仓库，false = zip 直链 */
    val isGit: Boolean,
)

object PluginMarket {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetch(indexUrl: String): Result<List<RemotePlugin>> = runCatching {
        val url = indexUrl.trim()
        if (url.isBlank()) throw IOException("先填索引地址")
        val body = request(url)
        val root = runCatching { json.parseToJsonElement(body) }
            .getOrElse { throw IOException("索引不是合法 JSON：${it.message}") }

        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["plugins"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun str(key: String): String = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            val zip = str("zip").ifBlank { str("url") }
            val git = str("git")
            val source = git.ifBlank { zip }
            if (source.isBlank()) return@mapNotNull null
            RemotePlugin(
                id = str("id").ifBlank { str("name") },
                name = str("name").ifBlank { str("id") },
                description = str("description"),
                version = str("version").ifBlank { "-" },
                source = source,
                isGit = git.isNotBlank(),
            )
        }.also { if (it.isEmpty()) throw IOException("索引里没有可安装的条目（需要 id/name 和 zip 或 git）") }
    }

    /** 粗判一个地址是 git 还是 zip，供「直接粘贴链接安装」用 */
    fun looksLikeGit(source: String): Boolean {
        val s = source.trim().lowercase()
        return s.endsWith(".git") || s.startsWith("git@") || s.startsWith("git://")
    }

    private fun request(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("索引请求失败：HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
