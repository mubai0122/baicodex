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
 * 麦克风录音：16kHz 单声道 16bit，停的时候封一个标准 WAV 头。
 * 纯 javax.sound.sampled，不引第三方库；最长录 120 秒自动停。
 */

package b.ai.desktop.core.audio

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

object AudioRecorder {

    private val FORMAT = AudioFormat(16_000f, 16, 1, true, false)

    /** 一次录音会话：start() 后可以 stop() 拿 WAV 字节 */
    class Session private constructor(
        private val line: TargetDataLine,
    ) {
        private val buffer = ByteArrayOutputStream(1 shl 20)
        private val thread = Thread({ runLoop() }, "baicodex-rec").apply { isDaemon = true }
        @Volatile
        private var stopRequested = false

        val seconds: Int get() = synchronized(buffer) { buffer.size() / 32_000 }

        private fun runLoop() {
            line.open(FORMAT)
            line.start()
            val chunk = ByteArray(4096)
            while (!stopRequested) {
                val n = line.read(chunk, 0, chunk.size)
                if (n > 0) {
                    synchronized(buffer) { buffer.write(chunk, 0, n) }
                    // 120 秒自动停
                    if (buffer.size() > 16_000 * 2 * 120) stopRequested = true
                }
            }
            line.stop()
            line.close()
        }

        fun start() = thread.start()

        /** 停止并返回 WAV 字节 */
        fun stop(): ByteArray {
            stopRequested = true
            thread.join(2_000)
            return synchronized(buffer) { wavBytes(buffer.toByteArray()) }
        }

        companion object {
            fun open(): Session {
                val info = DataLine.Info(TargetDataLine::class.java, FORMAT)
                if (!AudioSystem.isLineSupported(info)) {
                    throw IllegalStateException("没有可用的麦克风")
                }
                val line = AudioSystem.getLine(info) as TargetDataLine
                return Session(line)
            }
        }
    }

    /** PCM → 标准 WAV（44 字节头 + 数据） */
    fun wavBytes(pcm: ByteArray): ByteArray {
        val sampleRate = 16_000
        val channels = 1
        val bitsPerSample = 16
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val total = pcm.size
        val out = ByteArrayOutputStream(44 + total)
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        out.write("RIFF".toByteArray()); le32(36 + total)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16)
        le16(1); le16(channels); le32(sampleRate); le32(byteRate); le16(blockAlign); le16(bitsPerSample)
        out.write("data".toByteArray()); le32(total)
        out.write(pcm)
        return out.toByteArray()
    }
}
