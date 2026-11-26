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
 * GitHub 登录（对齐手机端 GitHub 账号卡）：桌面端不做内嵌浏览器，
 * 用 Personal Access Token 登录，登录后：
 *   ① 拉一次 /user 校验身份并取头像，头像落本地；
 *   ② token 存 <appDir>/auth.json（仅本机文件，不上传任何服务器）；
 *   ③ 执行命令类工具会注入 GITHUB_TOKEN / GH_TOKEN，私有仓库 clone、push 直接可用。
 */

package b.ai.desktop.core.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.desktop.core.config.ConfigStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class GhAccount(
    val token: String = "",
    val login: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    /** 头像本地缓存文件名（没有就是空，界面退回首字母） */
    val avatarFile: String = "",
    /** token 加密盐（AES-GCM；是否绑定机器跟随设置里的开关）；空 = 明文（老文件） */
    val salt: String = "",
    val savedAt: Long = 0L,
) {
    val display: String get() = name.ifBlank { login }
    val signedIn: Boolean get() = token.isNotBlank() && login.isNotBlank()
}

object GitHubAuth {
    private const val API_USER = "https://api.github.com/user"
    private const val UA = "Baicodex-Windows/0.1"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** 界面直接读它，登录/退出生效后自动重组 */
    var account by mutableStateOf<GhAccount?>(null)
        private set

    /** 加密是否绑定机器（跟随设置里「隐私与加密」的开关；token 本身始终加密存） */
    var bindDevice: Boolean = false

    private fun authFile(): File = File(ConfigStore.appDir(), "auth.json")

    fun load() {
        account = runCatching {
            val file = authFile()
            if (!file.exists()) return@runCatching null
            json.decodeFromString<GhAccount>(file.readText())
                // openAny：绑定开关拨过也能读回历史密文
                .let { if (it.salt.isBlank()) it else it.copy(token = b.ai.desktop.core.security.Vault.openAny(it.token, it.salt)) }
                .takeIf { it.signedIn }
        }.getOrNull()
    }

    /** token 明文只落本机文件；config.json 里不放，避免导出配置时带出去 */
    private fun persist(value: GhAccount?) {
        account = value
        val file = authFile()
        runCatching {
            if (value == null) {
                file.delete()
                return@runCatching
            }
            // token 加密落盘（始终加密，与加密开关无关）；绑定机器与否跟随设置
            val salt = value.salt.ifBlank { b.ai.desktop.core.security.Vault.newSalt() }
            val onDisk = value.copy(
                salt = salt,
                token = b.ai.desktop.core.security.Vault.seal(value.token, salt, bindDevice = bindDevice),
            )
            file.writeText(json.encodeToString(GhAccount.serializer(), onDisk))
        }
    }

    /**
     * 用 PAT 登录：先校验再落盘，失败不改变现有登录态。
     * @return 成功返回账号；失败返回可读原因
     */
    suspend fun signIn(rawToken: String): Result<GhAccount> = withContext(Dispatchers.IO) {
        val token = rawToken.trim()
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("先把 token 粘进来"))
        runCatching {
            val root = requestJson(API_USER, token)
            val login = root["login"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (login.isBlank()) throw IllegalStateException("这个 token 没有返回账号名，确认它带 read:user 权限")
            val name = root["name"]?.jsonPrimitive?.content.orEmpty()
            val avatarUrl = root["avatar_url"]?.jsonPrimitive?.content.orEmpty()
            val saved = GhAccount(
                token = token,
                login = login,
                name = name,
                avatarUrl = avatarUrl,
                avatarFile = downloadAvatar(avatarUrl, login),
                savedAt = System.currentTimeMillis(),
            )
            persist(saved)
            saved
        }.recoverCatching { error ->
            // 401/403 的错误信息已经带了服务端原文，直接往上抛
            throw error
        }
    }

    fun signOut() {
        account?.avatarFile?.takeIf { it.isNotBlank() }?.let { name ->
            runCatching { File(ConfigStore.appDir(), name).delete() }
        }
        persist(null)
    }

    /** 头像绝对路径（没有文件就是 null，界面用首字母兜底） */
    fun avatarPath(): File? {
        val name = account?.avatarFile?.takeIf { it.isNotBlank() } ?: return null
        return File(ConfigStore.appDir(), name).takeIf { it.exists() }
    }

    /** 给命令工具用的环境变量：私有仓库的 clone / push 不再弹账号密码 */
    fun env(): Map<String, String> {
        val token = account?.token?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return mapOf(
            "GITHUB_TOKEN" to token,
            "GH_TOKEN" to token,
            "GIT_TERMINAL_PROMPT" to "0",
        )
    }

    private fun requestJson(url: String, token: String): Map<String, kotlinx.serialization.json.JsonElement> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val hint = when (code) {
                    401 -> "token 无效或已过期"
                    403 -> "权限不足（需要 read:user）"
                    else -> "HTTP $code"
                }
                throw IllegalStateException("$hint：${body.take(160)}")
            }
            return json.parseToJsonElement(body).jsonObject
        } finally {
            conn.disconnect()
        }
    }

    /** 头像下载失败不影响登录，只是界面显示首字母 */
    private fun downloadAvatar(url: String, login: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", UA)
            }
            try {
                if (conn.responseCode !in 200..299) return ""
                val bytes = conn.inputStream.use { it.readBytes() }
                val file = File(ConfigStore.appDir(), "avatar-$login.png")
                file.writeBytes(bytes)
                file.name
            } finally {
                conn.disconnect()
            }
        }.getOrDefault("")
    }
}
