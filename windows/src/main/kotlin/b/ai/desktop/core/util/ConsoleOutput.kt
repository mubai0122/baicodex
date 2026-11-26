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
 * 子进程输出解码：Windows 上 cmd 可能按控制台代码页（简体中文是 GBK）输出，
 * 也可能被 chcp 65001 改成 UTF-8。先按 UTF-8 严格解，解不动再退 GBK，
 * 这样中文命令输出不会变成一串问号。
 */

package b.ai.desktop.core.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object ConsoleOutput {

    /** 先 UTF-8 严格解码，失败退 GBK（再失败就按 UTF-8 容错解） */
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val strictUtf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        if (strictUtf8 != null) return strictUtf8

        val gbk = runCatching { Charset.forName("GBK").decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
        if (gbk != null) return gbk
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * 把流读到 EOF，最多保留 cap 字节（超出继续读掉，避免进程写满管道卡住）。
     */
    fun read(input: InputStream, cap: Int = 80_000): ByteArray {
        val kept = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (n <= 0) break
            if (kept.size() < cap) {
                kept.write(buffer, 0, minOf(n, cap - kept.size()))
            }
        }
        return kept.toByteArray()
    }
}
