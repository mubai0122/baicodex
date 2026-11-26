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
 * 内置 ADB：定位 adb.exe、跑命令、设备清单、无线连接、局域网探测。
 */

package b.ai.desktop.core.adb

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String,
    val isWireless: Boolean,
) {
    /** wireless serial 形如 192.168.1.5:5555 */
    val display: String get() = "$model($serial)"
}

object ADB {

    /** 定位 adb：配置路径 → 常见 SDK 目录 → PATH */
    fun locate(configured: String = ""): String? {
        val candidates = buildList {
            if (configured.isNotBlank()) add(configured)
            val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            if (!sdk.isNullOrBlank()) add("$sdk\\platform-tools\\adb.exe")
            add("C:\\AndroidSdk\\platform-tools\\adb.exe")
            add(System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk\\platform-tools\\adb.exe" } ?: "")
        }.filter { it.isNotBlank() }
        candidates.firstOrNull { File(it).exists() }?.let { return it }
        // PATH 里找
        System.getenv("PATH")?.split(";")?.forEach { dir ->
            val f = File(dir, "adb.exe")
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    /** 跑 adb 命令；超时/找不到 adb 都抛异常，错误信息给界面看 */
    fun run(args: List<String>, adbPath: String, timeoutSec: Long = 30): String {
        val output = runCatchingOutput(listOf(adbPath) + args, timeoutSec)
        return output
    }

    private fun runCatchingOutput(command: List<String>, timeoutSec: Long): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw AdbException("命令超时（${timeoutSec}s）：${command.drop(1).joinToString(" ").take(120)}")
        }
        val text = process.inputStream.readBytes().decodeToString()
        // adb 在 stderr 上写错误（redirectErrorStream 已合并），退出码非 0 直接报
        if (process.exitValue() != 0 && text.isBlank()) {
            throw AdbException("adb 退出码 ${process.exitValue()}")
        }
        return text.trim()
    }

    /** 设备清单：USB 与无线（serial 含 : 的当无线） */
    fun devices(adbPath: String): List<AdbDevice> {
        val out = run(listOf("devices", "-l"), adbPath)
        return out.lineSequence()
            .filter { it.contains("\t") }
            .map { line ->
                val parts = line.split(Regex("\\s+"))
                val serial = parts[0]
                val state = parts[1]
                val model = line.substringAfter("model:", "").substringBefore(" ").ifBlank { serial }
                AdbDevice(serial, state, model, isWireless = serial.contains(":"))
            }
            .toList()
    }

    /** 设备状态的中文说法与下一步动作；device 之外的都算「连上了但用不了」 */
    fun stateHint(state: String): String = when (state) {
        "device" -> "已授权，可用"
        "unauthorized" -> "手机上弹了「允许 USB 调试吗」还没点允许 → 解锁手机，勾选「始终允许」再确定；点了还没好就撤销授权重来"
        "offline" -> "连接不稳定 → 换根数据线 / 换个 USB 口，或 adb kill-server 后重插"
        "recovery", "sideload" -> "手机在刷机模式，普通操作不可用"
        else -> "状态 $state：重插 USB，或在手机上撤销 USB 调试授权后重新授权"
    }

    /** 启动 adb 服务：有的机器 adb server 没起来，devices 直接失败 */
    fun startServer(adbPath: String): String = run(listOf("start-server"), adbPath, 15)

    /** 原始 devices -l 输出，给诊断用 */
    fun rawDevices(adbPath: String): String = run(listOf("devices", "-l"), adbPath)

    /** 无线连接 ip:port；返回可读结果 */
    fun connect(ipPort: String, adbPath: String): String = run(listOf("connect", ipPort), adbPath, 15)

    fun disconnect(ipPort: String, adbPath: String): String = run(listOf("disconnect", ipPort), adbPath, 10)

    /** 把已连 USB 的设备切到 tcpip 模式（默认 5555），之后就能走内网 */
    fun enableTcpip(port: Int, adbPath: String, serial: String? = null): String {
        val args = mutableListOf("tcpip", port.toString())
        return run(serialPrefixed(args, serial), adbPath, 20)
    }

    private fun serialPrefixed(args: List<String>, serial: String?): List<String> =
        if (serial.isNullOrBlank()) args else listOf("-s", serial) + args

    // ---------------------------------------------------------------- 局域网探测

    /** 单点探测：能不能在超时内握上 TCP（adb 无线端口一般是 5555） */
    fun probe(ip: String, port: Int = 5555, timeoutMs: Int = 800): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    }.getOrDefault(false)

    /**
     * 扫一个网段的 5555 端口（例如 192.168.1.0/24 → 最多 254 个 IP）。
     * 32 线程并发，单次 /24 大约 8 秒以内；找到的全部返回。
     */
    fun scanSubnet(subnetPrefix: String, port: Int = 5555, timeoutMs: Int = 500): List<String> {
        val hits = java.util.Collections.synchronizedList(mutableListOf<String>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(32)
        try {
            (1..254).map { i ->
                pool.submit {
                    val ip = "$subnetPrefix.$i"
                    if (probe(ip, port, timeoutMs)) hits.add(ip)
                }
            }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        return hits.sorted()
    }

    /** 本机所在网段前缀（从非回环 IPv4 取前三段）；拿不到返回 null */
    fun localSubnetPrefix(): String? = runCatching {
        val addr = java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            as? java.net.Inet4Address
        addr?.hostAddress?.split(".")?.take(3)?.joinToString(".")
    }.getOrNull()
}

class AdbException(message: String) : Exception(message)
