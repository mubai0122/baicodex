/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:45:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 语音识别客户端：OpenAI 兼容的 /audio/transcriptions（multipart/form-data）。
 * 默认跟随当前模型的 baseUrl（网关转发也认这个路径）；模型名可配（whisper-1 等）。
 * 录音只在这条链路上传，不落盘、不进聊天记录。
 */

package b.ai.desktop.core.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object SttClient {

    data class Result(val ok: Boolean, val text: String)

    /** 转写一段 WAV */
    fun transcribe(
        baseUrl: String,
        apiKey: String,
        model: String,
        language: String,
        wav: ByteArray,
    ): Result {
        val base = baseUrl.trimEnd('/')
        val endpoint = if (base.endsWith("/v1")) "$base/audio/transcriptions" else "$base/v1/audio/transcriptions"
        val boundary = "bcx-" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            conn.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.write("\r\n".toByteArray())
                }
                field("model", model.ifBlank { "whisper-1" })
                if (language.isNotBlank()) field("language", language)
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray())
                out.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
                out.write(wav)
                out.write("\r\n".toByteArray())
                out.write("--$boundary--\r\n".toByteArray())
            }

            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                Result(false, "HTTP $status：${body.take(200)}")
            } else {
                val text = runCatching {
                    val json = Json.parseToJsonElement(body).jsonObject
                    json["text"]?.jsonPrimitive?.content
                }.getOrNull()
                if (text.isNullOrBlank()) Result(false, "响应里没有文本：${body.take(160)}")
                else Result(true, text)
            }
        } catch (t: Throwable) {
            Result(false, t.message ?: t.toString())
        } finally {
            conn.disconnect()
        }
    }
}
