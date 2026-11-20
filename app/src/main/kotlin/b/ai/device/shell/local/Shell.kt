/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:11
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.device.shell.local
import b.ai.net.api.llm.text

import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

/** 吃掉进程输出并等待结束（stdout/stderr 合并） */
internal fun drainProcess(p: Process, timeoutMs: Long, input: String? = null): ShellResult {
    val sb = StringBuilder()
    val reader = Thread {
        runCatching {
            p.inputStream.bufferedReader().forEachLine { line ->
                synchronized(sb) { sb.appendLine(line) }
            }
        }
    }
    reader.isDaemon = true
    reader.start()
    runCatching {
        if (input != null) p.outputStream.use { it.write(input.toByteArray()) } else p.outputStream.close()
    }
    val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    if (!done) p.destroyForcibly()
    reader.join(2000)
    val text = synchronized(sb) { sb.toString() }.trim()
    return if (done) ShellResult(p.exitValue(), text) else ShellResult(-1, "$text\n[超时 ${timeoutMs}ms，已强制结束]")
}

/** 沙箱通道：以应用自身 UID 执行 sh */
object Shell {
    fun run(cmd: String, input: String? = null, timeoutMs: Long = 60_000L): ShellResult {
        return try {
            val p = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            drainProcess(p, timeoutMs, input)
        } catch (e: Exception) {
            ShellResult(-1, "[启动失败] ${e.message ?: e.javaClass.simpleName}")
        }
    } //应用身份执行命令

    /** 按 argv 直接起进程，不做二次转义，stdin 立刻关掉；su -c 走这里 */
    fun exec(argv: List<String>, timeoutMs: Long = 60_000L): ShellResult {
        return try {
            val p = ProcessBuilder(argv).redirectErrorStream(true).start()
            drainProcess(p, timeoutMs)
        } catch (e: Exception) {
            ShellResult(-1, "[启动失败] ${e.message ?: e.javaClass.simpleName}")
        }
    } //按参数数组执行命令
}