/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:26
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 桌面端 Vbai 设备码登录（对齐手机端 VbaiAuth）：
 *   ① POST /api/app/device/start 领码，用系统默认浏览器打开 verify_url；
 *   ② 每 poll_after 秒 POST /api/app/device/poll，直到用户确认；
 *   ③ 拿到的 api_key.value 用 Vault 加密后写 <appDir>/vbai.json，界面只回显掩码。
 * 全程不需要 app_secret，Key 明文只在本次响应里出现一次。
 */

package b.ai.desktop.core.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.security.Vault
import b.ai.desktop.core.util.openUrl
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 设备码登录成功后的账号快照：key 落盘加密，界面只展示 masked */
@Serializable
data class VbaiAccount(
    /** API Key 明文（落盘时被 Vault 加密替换） */
    val key: String = "",
    /** 掩码，例如 svb-v1-b818c2••••••••••••1c98 */
    val masked: String = "",
    val user: String = "",
    val plan: String = "",
    val balance: String = "",
    /** OpenAI 兼容中转根 */
    val baseUrl: String = "",
    /** 控制台会话 token：只在需要打开控制台时用，不进日志 */
    val consoleToken: String = "",
    /** 加密盐（空 = 老文件明文） */
    val salt: String = "",
    val savedAt: Long = 0L,
) {
    val signedIn: Boolean get() = key.isNotBlank() && user.isNotBlank()
}

object VbaiAuth {
    const val APP_ID = "vbai-code"
    private const val API = "https://ai.baiccb.cn/api"
    const val BASE = "https://ai.baiccb.cn/v1"

    /** 官网：宣传页「打开官网」按钮用（注册 / 充值 / 控制台都在这里） */
    const val SITE = "https://ai.baiccb.cn/"

    /** 领码同一 IP 有 30/小时限额，本地 60s 节流 */
    private const val START_GUARD_MS = 60_000L

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var lastStartAt = 0L

    /** 界面直接读它，登录 / 退出生效后自动重组 */
    var account by mutableStateOf<VbaiAccount?>(null)
        private set

    /** 已登录：拿到 Key 就算（账号信息可能还没刷新） */
    val logged: Boolean get() = account?.key?.isNotBlank() == true

    /** 流程状态：空 / start / wait / ok / expired / error */
    var stage by mutableStateOf("")
        private set
    var msg by mutableStateOf("")
        private set
    var verify by mutableStateOf("")
        private set
    var left by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set

    /** 加密是否绑定机器（跟随设置里的开关） */
    var bindDevice: Boolean = false

    private fun file(): File = File(ConfigStore.appDir(), "vbai.json")

    fun load() {
        account = runCatching {
            val f = file()
            if (!f.exists()) return@runCatching null
            json.decodeFromString<VbaiAccount>(f.readText())
                // openAny：绑定开关拨过也能读回历史密文
                .let { if (it.salt.isBlank()) it else it.copy(key = Vault.openAny(it.key, it.salt)) }
                .takeIf { it.signedIn }
        }.getOrNull()
    } //读盘

    /** Key 始终加密落盘；config.json 里不放，避免导出配置时带出去 */
    private fun persist(value: VbaiAccount?) {
        account = value
        val f = file()
        runCatching {
            if (value == null) {
                f.delete()
                return@runCatching
            }
            val salt = value.salt.ifBlank { Vault.newSalt() }
            val onDisk = value.copy(salt = salt, key = Vault.seal(value.key, salt, bindDevice = bindDevice))
            f.writeText(json.encodeToString(VbaiAccount.serializer(), onDisk))
        }
    } //落盘

    fun key(): String = account?.key.orEmpty()

    fun base(): String = account?.baseUrl?.ifBlank { BASE } ?: BASE

    fun logout() {
        persist(null)
        stage = ""
        msg = ""
        verify = ""
        left = 0
    } //退出

    fun cancel() {
        if (busy) {
            busy = false
            msg = "已取消"
        }
    } //取消

    /**
     * 全流程登录：领码 → 开浏览器 → 轮询。返回 true 表示拿到 Key。
     * 网络与等待都在 IO 线程，界面只读上面的状态位。
     */
    suspend fun login(): Boolean {
        if (busy) return false
        val now = System.currentTimeMillis()
        if (now - lastStartAt < START_GUARD_MS) {
            stage = "error"
            msg = "刚申请过，请稍等 " + (START_GUARD_MS - (now - lastStartAt)) / 1000 + " 秒再试"
            return false
        }
        lastStartAt = now
        busy = true
        stage = "start"
        msg = "正在申请登录码…"
        left = 0
        return withContext(Dispatchers.IO) {
            try {
                val start = post("/app/device/start", """{"app_id":"$APP_ID"}""")
                if (start == null) {
                    stage = "error"
                    msg = lastErr
                    return@withContext false
                }
                val d = start["data"]?.jsonObject ?: JsonObject(emptyMap())
                val code = d["code"]?.jsonPrimitive?.content.orEmpty()
                if (code.isBlank()) {
                    stage = "error"
                    msg = "平台没有返回登录码"
                    return@withContext false
                }
                val ex = d["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300
                val wait = (d["poll_after"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2).coerceIn(1, 10)
                verify = d["verify_url"]?.jsonPrimitive?.content.orEmpty()
                stage = "wait"
                msg = "已打开浏览器，请登录并在网页上点「确认授权」"
                //系统默认浏览器最稳：内嵌窗口会被登录页的滑块拦住
                runCatching { openUrl(verify) }

                val deadline = System.currentTimeMillis() + ex * 1000L
                while (System.currentTimeMillis() < deadline) {
                    delay(wait * 1000L)
                    if (!busy) return@withContext false
                    left = ((deadline - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                    val r = post("/app/device/poll", """{"app_id":"$APP_ID","code":"$code"}""")
                    if (r == null) {
                        msg = "网络不稳，正在重试…（剩余 ${left}s）"
                        continue
                    }
                    when (r["data"]?.jsonObject?.get("status")?.jsonPrimitive?.content.orEmpty()) {
                        "ok" -> {
                            adopt(r["data"]!!.jsonObject)
                            stage = "ok"
                            msg = "登录成功"
                            return@withContext true
                        }
                        "expired" -> {
                            stage = "expired"
                            msg = "登录码已过期，请重新发起登录"
                            return@withContext false
                        }
                        else -> msg = "等待授权…（剩余 ${left}s）"
                    }
                }
                stage = "expired"
                msg = "登录码已过期，请重新发起登录"
                false
            } catch (e: Exception) {
                stage = "error"
                msg = e.message ?: "登录失败"
                false
            } finally {
                busy = false
            }
        }
    } //设备码登录

    /** 成功回调：JSON 里字段名与文档 §3.3 一致 */
    private fun adopt(d: JsonObject) {
        val us = d["user"]?.jsonObject
        val ak = d["api_key"]?.jsonObject
        val ss = d["session"]?.jsonObject
        val bal = us?.get("balance")?.jsonPrimitive?.content.orEmpty()
        persist(
            VbaiAccount(
                key = ak?.get("value")?.jsonPrimitive?.content.orEmpty(),
                masked = ak?.get("masked")?.jsonPrimitive?.content.orEmpty(),
                user = us?.get("username")?.jsonPrimitive?.content.orEmpty(),
                plan = us?.get("plan")?.jsonPrimitive?.content.orEmpty(),
                balance = bal,
                baseUrl = ak?.get("base_url")?.jsonPrimitive?.content.orEmpty().ifBlank { BASE },
                consoleToken = ss?.get("token")?.jsonPrimitive?.content.orEmpty(),
                salt = "",
                savedAt = System.currentTimeMillis(),
            )
        )
    } //落库

    @Volatile
    private var lastErr = ""

    /** POST JSON：失败返回 null（原因在 lastErr），非 2xx 也按平台 message 取提示 */
    private fun post(path: String, body: String): JsonObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val txt = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val j = runCatching { json.parseToJsonElement(txt).jsonObject }.getOrNull()
            if (j == null) {
                lastErr = when (code) {
                    429 -> "请求太频繁，请稍后再试"
                    410 -> "登录码已作废，请重新发起"
                    else -> "网络异常（HTTP $code）"
                }
                null
            } else if (code !in 200..299 || j["ok"]?.jsonPrimitive?.content != "true") {
                lastErr = j["message"]?.jsonPrimitive?.content.orEmpty().ifBlank { "请求失败（HTTP $code）" }
                null
            } else j
        } catch (e: Exception) {
            lastErr = "网络异常：" + (e.message ?: e.javaClass.simpleName)
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    } //POST JSON
} //桌面端 Vbai 设备码登录