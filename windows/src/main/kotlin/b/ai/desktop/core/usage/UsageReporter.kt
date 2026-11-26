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
 * 使用上报：与手机端同一套协议（POST /api/use/put.php）。
 *  body = { deviceCode, count, timestamp(秒), nonce(32 位随机 hex) }
 *  累加语义，所以每次启动只发一次、不重试；409 = nonce 已用过（上次其实记上了），按成功处理。
 * 只上传哈希后的设备码与计数，不带任何原始硬件标识、不带对话内容。
 * 设置里可关；关闭时一次请求都不会发。
 */

package b.ai.desktop.core.usage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.device.DeviceCode
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 最近一次上报的结果（设置页展示用） */
@Serializable
data class ReportState(
    val at: Long = 0L,
    val ok: Boolean = false,
    val message: String = "",
    val deviceCode: String = "",
)

object UsageReporter {
    /** 与手机端同一个接口；可在设置里改（自建服务端时用） */
    const val DefaultEndpoint = "https://agent.ai.baiccb.cn/api/use/put.php"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    var state by mutableStateOf<ReportState?>(null)
        private set

    private fun stateFile(): File = File(ConfigStore.appDir(), "usage.json")

    fun loadState(): ReportState? {
        val cached = runCatching {
            val file = stateFile()
            if (!file.exists()) null else json.decodeFromString<ReportState>(file.readText())
        }.getOrNull()
        state = cached
        return cached
    }

    /** 启动时调一次：后台线程发，失败静默；返回是否真的发起了请求 */
    fun reportOnceAsync(enabled: Boolean, endpoint: String = DefaultEndpoint) {
        if (!enabled) return
        Thread {
            runCatching { report(endpoint) }
        }.apply { isDaemon = true }.start()
    }

    // ---------------------------------------------------------------- 安装/卸载事件

    const val InstallEndpoint = "https://agent.ai.baiccb.cn/api/use/put_install.php"
    const val UninstallEndpoint = "https://agent.ai.baiccb.cn/api/use/put_uninstall.php"

    /** 首次安装上报的标记：发成功过就不再发（重装且清数据时会重新记一次） */
    private fun installFlag(): File = File(ConfigStore.appDir(), "install.flag")

    /**
     * 首次运行上报安装事件：只在没有任何标记时发一次，成功或明确失败都落标记，
     * 免得断网的机器每次启动都重试。后台线程，失败静默。
     */
    fun reportInstallOnceAsync(enabled: Boolean) {
        if (!enabled) return
        val flag = installFlag()
        if (flag.exists()) return
        Thread {
            val ok = runCatching { reportEvent(InstallEndpoint) }.getOrDefault(false)
            runCatching {
                if (!flag.exists()) flag.writeText(if (ok) "ok" else "skip")
            }
        }.apply { isDaemon = true }.start()
    }

    /** 事件上报（安装/卸载），带客户端本地日期；返回是否 2xx/409 成功入账 */
    fun reportEvent(endpoint: String): Boolean {
        val code = DeviceCode.code()
        val localDate = java.time.LocalDate.now().toString()
        val body = buildJsonObject {
            put("deviceCode", code)
            put("count", 1)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("nonce", nonce())
            put("date", localDate)
        }.toString()

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            return status in 200..299 || status == 409
        } finally {
            conn.disconnect()
        }
    }

    /** 手动上报（设置页按钮），返回可读结果 */
    fun report(endpoint: String = DefaultEndpoint): ReportState {
        val code = DeviceCode.code()
        val result = try {
            val (status, body) = post(endpoint, code, 1)
            when {
                status in 200..299 -> ReportState(System.currentTimeMillis(), true, body.take(120).ifBlank { "已上报" }, code)
                status == 409 -> ReportState(System.currentTimeMillis(), true, "已记过账（nonce 重复）", code)
                else -> ReportState(System.currentTimeMillis(), false, "HTTP $status：${body.take(120)}", code)
            }
        } catch (t: Throwable) {
            val reason = t.message?.trim().orEmpty().ifBlank { t::class.simpleName ?: "未知错误" }
            ReportState(System.currentTimeMillis(), false, reason.take(160), code)
        }
        state = result
        runCatching { stateFile().writeText(json.encodeToString(ReportState.serializer(), result)) }
        return result
    }

    private fun post(endpoint: String, code: String, count: Int): Pair<Int, String> {
        val body = buildJsonObject {
            put("deviceCode", code)
            put("count", count)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("nonce", nonce())
        }.toString()

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return status to text
        } finally {
            conn.disconnect()
        }
    }

    private fun nonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
