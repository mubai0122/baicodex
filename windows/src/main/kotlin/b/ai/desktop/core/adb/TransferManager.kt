/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 05:25:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 分块双通道传输
 */

package b.ai.desktop.core.adb

import java.io.File
import java.io.RandomAccessFile
import java.util.Base64

class TransferManager(private val adbPath: String) {

    data class Progress(val bytes: Long, val total: Long, val channel: String)

    /** 同一台设备的两条通道：usb = serial，lan = ip:port（都可为 null） */
    data class Channels(val usb: String?, val lan: String?) {
        val dual: Boolean get() = usb != null && lan != null
        val single: String? get() = usb ?: lan
    }

    /** 从设备清单里推通道：无线 serial 就是 LAN 通道 */
    fun channelsOf(devices: List<AdbDevice>): List<Pair<String, Channels>> =
        devices.filter { it.state == "device" }.map { device ->
            val lan = device.serial.takeIf { it.isWirelessSerial() }
            val usb = device.serial.takeUnless { it.isWirelessSerial() }
            device.serial to Channels(usb, lan)
        }

    private fun String.isWirelessSerial(): Boolean = contains(":")

    /**
     * 推文件到远端（remoteDir 下的同名文件）。
     * dual=allowDuelChannel 且两条通道都通时走分块双发；否则单通道 push。
     */
    fun push(
        local: File,
        remoteDir: String,
        channels: Channels,
        allowDual: Boolean,
        serial: String,
        onProgress: ((Progress) -> Unit)? = null,
    ): String {
        if (!local.exists()) throw AdbException("本地文件不存在：${local.path}")
        val remotePath = "${remoteDir.trimEnd('/')}/${local.name}"

        if (allowDual && channels.dual) {
            val result = runCatching { pushDual(local, remotePath, channels) }
            if (result.isSuccess) return result.getOrThrow()
            // 双通道失败回退单通道
        }
        return pushSingle(local, remotePath, channels.single ?: channels.usb ?: channels.lan!!, onProgress)
    }

    /** 拉远端文件到本地目录 */
    fun pull(
        remotePath: String,
        localDir: File,
        channels: Channels,
        allowDual: Boolean,
        onProgress: ((Progress) -> Unit)? = null,
    ): String {
        localDir.mkdirs()
        val name = remotePath.substringAfterLast('/')
        val local = File(localDir, name)

        if (allowDual && channels.dual) {
            val result = runCatching { pullDual(remotePath, local, channels) }
            if (result.isSuccess) return local.absolutePath
        }
        ADB.run(listOf("-s", channels.single ?: channels.usb ?: channels.lan!!, "pull", remotePath, local.absolutePath), adbPath, 600)
        onProgress?.invoke(Progress(local.length(), local.length(), channels.single ?: "single"))
        return local.absolutePath
    }

    // ---------------------------------------------------------------- 单通道

    private fun pushSingle(local: File, remotePath: String, serial: String, onProgress: ((Progress) -> Unit)?): String {
        ADB.run(listOf("-s", serial, "push", local.absolutePath, remotePath), adbPath, 1800)
        onProgress?.invoke(Progress(local.length(), local.length(), serial))
        return "已推送 ${human(local.length())} → $remotePath（通道 $serial）"
    }

    // ---------------------------------------------------------------- 双通道（推）

    /**
     * 推：本地切两半 → USB 半 + LAN 半 → 各自推到 /data/local/tmp → shell cat 合并 → 清理。
     */
    private fun pushDual(local: File, remotePath: String, channels: Channels): String {
        val tmpDir = "/data/local/tmp"
        val stamp = System.currentTimeMillis().toString(36)
        val part1 = "$tmpDir/bcx-${stamp}-a"
        val part2 = "$tmpDir/bcx-${stamp}-b"

        val mid = (local.length() / 2 / CHUNK) * CHUNK + CHUNK // 按块对齐的中点
        writeSlice(local, 0, mid, File(local.parentFile, "${local.name}.part-a"))
        writeSlice(local, mid, local.length(), File(local.parentFile, "${local.name}.part-b"))
        try {
            ADB.run(
                listOf("-s", channels.usb!!, "push", File(local.parentFile, "${local.name}.part-a").absolutePath, part1),
                adbPath, 1800,
            )
            ADB.run(
                listOf("-s", channels.lan!!, "push", File(local.parentFile, "${local.name}.part-b").absolutePath, part2),
                adbPath, 1800,
            )
            // 合并 + 校验大小 + 清理分块
            val check = ADB.run(
                listOf(
                    "-s", channels.usb!!,
                    "shell",
                    "cat '$part1' '$part2' > '$remotePath' && rm -f '$part1' '$part2' && wc -c < '$remotePath'",
                ),
                adbPath, 600,
            )
            val remoteSize = check.filter { it.isDigit() }.toLongOrNull() ?: -1
            if (remoteSize != local.length()) {
                throw AdbException("双通道合并校验失败（远端 $remoteSize ≠ 本地 ${local.length()}）")
            }
            return "已双通道推送 ${human(local.length())} → $remotePath（USB ${(mid / 1024).coerceAtLeast(0)}KB + LAN ${human(local.length() - mid)}）"
        } finally {
            File(local.parentFile, "${local.name}.part-a").delete()
            File(local.parentFile, "${local.name}.part-b").delete()
        }
    }

    /** 拉：远端切两半 → 各自从对应通道拉回 → 本地合并 */
    private fun pullDual(remotePath: String, local: File, channels: Channels): String {
        val tmpDir = "/data/local/tmp"
        val stamp = System.currentTimeMillis().toString(36)
        val part1 = "$tmpDir/bcx-${stamp}-a"
        val part2 = "$tmpDir/bcx-${stamp}-b"

        val sizeOut = ADB.run(listOf("-s", channels.usb!!, "shell", "wc -c < '$remotePath'"), adbPath, 60)
        val total = sizeOut.filter { it.isDigit() }.toLongOrNull()
            ?: throw AdbException("拿不到远端文件大小")
        val mid = (total / 2 / CHUNK) * CHUNK + CHUNK

        // split 命令按字节切：-b 512k 粒度不够细，用 dd 精确切
        ADB.run(listOf("-s", channels.usb!!, "shell", "dd if='$remotePath' of='$part1' bs=4194304 count=$((mid / 4194304 + 1)) 2>/dev/null || head -c $mid '$remotePath' > '$part1'"), adbPath, 600)
        ADB.run(listOf("-s", channels.usb!!, "shell", "tail -c +$((mid + 1)) '$remotePath' > '$part2'"), adbPath, 600)

        val localA = File(local.parentFile, "${local.name}.part-a")
        val localB = File(local.parentFile, "${local.name}.part-b")
        try {
            ADB.run(listOf("-s", channels.usb!!, "pull", part1, localA.absolutePath), adbPath, 1800)
            ADB.run(listOf("-s", channels.lan!!, "pull", part2, localB.absolutePath), adbPath, 1800)
            // 校验第一半大小，过头就裁
            RandomAccessFile(localA, "rw").use { raf ->
                if (raf.length() > mid) raf.setLength(mid)
            }
            RandomAccessFile(local, "rw").use { out ->
                out.setLength(0)
                localA.forEachBlock { bytes, n -> out.write(bytes, 0, n) }
                localB.forEachBlock { bytes, n -> out.write(bytes, 0, n) }
            }
            val merged = local.length()
            ADB.run(listOf("-s", channels.usb!!, "shell", "rm -f '$part1' '$part2'"), adbPath, 60)
            if (merged != total) throw AdbException("双通道合并校验失败（本地 $merged ≠ 远端 $total）")
            return local.absolutePath
        } finally {
            localA.delete()
            localB.delete()
            runCatching { ADB.run(listOf("-s", channels.usb!!, "shell", "rm -f '$part1' '$part2'"), adbPath, 60) }
        }
    }

    private fun writeSlice(source: File, from: Long, to: Long, target: File) {
        RandomAccessFile(source, "r").use { input ->
            input.seek(from)
            val length = (to - from).coerceAtLeast(0)
            val buf = ByteArray(CHUNK)
            RandomAccessFile(target, "rw").use { out ->
                var remaining = length
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
            }
        }
    }

    private fun human(bytes: Long): String = when {
        bytes >= 1 shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        const val CHUNK = 4 * 1024 * 1024
    }
}

/** Base64 给大内容分块用（预留；文本回传用不上时忽略） */
@Suppress("unused")
fun encodeChunk(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
