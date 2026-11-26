/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:08:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 朗读
 * 走 Windows 自带的 SAPI（System.Speech），不引第三方依赖。
 * 文本先写临时文件再让 PowerShell 读，避免命令行转义把中文和引号搞坏。
 */

package b.ai.desktop.core.util

import java.io.File

object Speech {
    private var process: Process? = null

    val speaking: Boolean get() = process?.isAlive == true

    /** @param rate SAPI 语速，-10（最慢）..10（最快），0 = 系统默认 */
    fun speak(text: String, rate: Int = 0): Boolean {
        val clean = text.trim().take(6000)
        if (clean.isEmpty()) return false
        stop()

        return runCatching {
            val file = File.createTempFile("baicodex-speech-", ".txt")
            file.writeText(clean, Charsets.UTF_8)
            val path = file.absolutePath.replace("'", "''")

            val script = buildString {
                append("Add-Type -AssemblyName System.Speech;")
                append("\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;")
                append("\$s.SetOutputToDefaultAudioDevice();")
                append("\$s.Rate = ${rate.coerceIn(-10, 10)};")
                append("try { \$s.Speak([IO.File]::ReadAllText('$path', [Text.Encoding]::UTF8)) } finally { \$s.Dispose() };")
                append("Remove-Item '$path' -Force -ErrorAction SilentlyContinue")
            }

            process = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start()
            true
        }.getOrDefault(false)
    }

    fun stop() {
        runCatching { process?.destroy() }
        process = null
    }
}
