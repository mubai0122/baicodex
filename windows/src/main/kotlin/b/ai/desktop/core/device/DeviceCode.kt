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
 * 设备码：手机端用 Widevine DRM ID，桌面没有对应物，所以改用硬件指纹。
 *  主板序列号 + CPU ProcessorId + 系统盘序列号 → 规范化 → 带固定前缀 SHA-256。
 * 几条要点（都是踩坑点）：
 *  1. 系统盘要按 C: → 分区 → 物理盘 解析，不能直接拿 Index 0（可能是有盘符的第二块盘/U 盘）；
 *  2. 白牌主板常把序列号写成 "To be filled by O.E.M."，必须过滤，否则一堆机器撞同一个码；
 *  3. 缺失项用 "-" 占位，保证同一台机器的拼接顺序稳定，不会因为某次读不到就变成另一个码；
 *  4. 固定前缀（应用盐）让这个哈希只在本应用内有意义，不能拿去和别的指纹库做关联；
 *  5. 只把哈希和「哪几项读到了」写盘，原始序列号不落盘、不上传。
 * 读不到任何硬件信息时依次退到 MachineGuid（重装才变）和随机 UUID（仅本机稳定）。
 */

package b.ai.desktop.core.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.desktop.core.config.ConfigStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeviceInfo(
    /** 64 位十六进制 */
    val code: String = "",
    /** 哪些来源读到了：MB / CPU / DISK / GUID / RANDOM */
    val sources: List<String> = emptyList(),
    val createdAt: Long = 0L,
    /** 采集过程说明：哪一步失败、为什么用兜底（设置页展示） */
    val notes: List<String> = emptyList(),
) {
    /** 展示用：4 位一组，方便用户报给我们 */
    val pretty: String
        get() = code.chunked(4).joinToString("-")
}

object DeviceCode {
    private const val Salt = "baicodex-win-v1"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val placeholders = setOf(
        "TO BE FILLED BY O.E.M.",
        "TO BE FILLED BY OEM",
        "DEFAULT STRING",
        "SYSTEM SERIAL NUMBER",
        "NONE",
        "NOT APPLICABLE",
        "NOT SPECIFIED",
        "UNKNOWN",
        "INVALID",
        "0",
        "00000000",
        "N/A",
        "NA",
    )

    /** 界面直接读；首次在后台算出来后自动重组 */
    var info by mutableStateOf<DeviceInfo?>(null)
        private set

    private fun cacheFile(): File = File(ConfigStore.appDir(), "device.json")

    /** 先读缓存：只有第一次（或用户点「重新采集」）才跑一次 PowerShell/WMI */
    @Synchronized
    fun load(): DeviceInfo {
        val cached = runCatching {
            val file = cacheFile()
            if (!file.exists()) null else json.decodeFromString<DeviceInfo>(file.readText())
        }.getOrNull()
        if (cached != null && cached.code.isNotBlank()) {
            // 三项齐了就直接用；不够三项说明上次是降级采集，再试一次，
            // 只有读到更多来源才替换（只升不降，避免指纹来回变）
            if (cached.sources.size >= 3) {
                info = cached
                return cached
            }
            val retry = collect()
            val better = retry.sources.size > cached.sources.size
            info = if (better) retry else cached
            if (!better) {
                runCatching {
                    cacheFile().writeText(json.encodeToString(DeviceInfo.serializer(), cached))
                }
            }
            return info!!
        }
        val fresh = collect()
        info = fresh
        return fresh
    }

    /** 后台采集（首次启动会慢 0.5~2 秒，别放主线程） */
    fun loadAsync(onDone: (DeviceInfo) -> Unit = {}) {
        Thread {
            val value = info ?: load()
            onDone(value)
        }.apply { isDaemon = true }.start()
    }

    /** 无视缓存重采一次（设置页按钮用） */
    @Synchronized
    fun refresh(): DeviceInfo {
        val fresh = collect()
        info = fresh
        return fresh
    }

    fun code(): String = info?.code ?: load().code

    // ------------------------------------------------------------ 采集

    private fun collect(): DeviceInfo {
        val parts = LinkedHashMap<String, String>()
        val notes = mutableListOf<String>()

        // ① 首选：一条外部查询脚本就能拿到主板/CPU/系统盘的真序列号
        var hardware = readHardware()
        if (hardware.isEmpty()) {
            notes += "首选采集没取到硬件信息（可能被安全策略拦截）"
            // ② 老系统上的第二条路；Win11 24H2 起系统默认已移除
            hardware = readWmic()
            if (hardware.isEmpty()) notes += "备用采集也不可用"
        }
        hardware.forEach { (key, value) -> parts[key] = value }

        // ③ 纯 Java 兜底：不依赖外部进程，至少让指纹稳定
        if (parts.isEmpty()) {
            javaFallback()?.forEach { (key, value) -> parts[key] = value }
            if (parts.isNotEmpty()) {
                notes += "使用 Java 兜底指纹（卷序列号 + CPU 标识），重装或格式化后可能变化"
            }
        }

        if (parts.isEmpty()) {
            readMachineGuid()?.let { parts["GUID"] = it }
            if (parts.isNotEmpty()) notes += "使用系统 MachineGuid，重装后会变"
        }
        if (parts.isEmpty()) {
            parts["RANDOM"] = stableRandom()
            notes += "所有硬件来源都读不到，使用本机随机种子（仅本机稳定）"
        }

        // 固定顺序拼接：缺失项写 "-"，保证同一台机器的输入恒等
        val joined = listOf("MB", "CPU", "DISK").joinToString("|") { key ->
            parts[key] ?: "-"
        } + "|" + (parts["GUID"] ?: "-")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$Salt|$joined".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        val result = DeviceInfo(
            code = digest,
            sources = parts.keys.toList(),
            createdAt = System.currentTimeMillis(),
            notes = notes,
        )
        runCatching {
            cacheFile().writeText(json.encodeToString(DeviceInfo.serializer(), result))
        }
        return result
    }

    /** 一次 PowerShell 把三项都取回来，避免起三次进程 */
    private fun readHardware(): Map<String, String> {
        if (!System.getProperty("os.name").lowercase().contains("win")) return readUnixHardware()

        val script = """
            ${'$'}ErrorActionPreference='SilentlyContinue'
            ${'$'}mb = (Get-CimInstance Win32_BaseBoard | Select-Object -First 1).SerialNumber
            ${'$'}cpu = (Get-CimInstance Win32_Processor | Select-Object -First 1).ProcessorId
            ${'$'}disk = ${'$'}null
            ${'$'}sys = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='${'$'}(${'$'}env:SystemDrive)'" | Select-Object -First 1
            if (${'$'}sys) {
              ${'$'}part = Get-CimAssociatedInstance -InputObject ${'$'}sys -ResultClassName Win32_DiskPartition | Select-Object -First 1
              if (${'$'}part) {
                ${'$'}drive = Get-CimAssociatedInstance -InputObject ${'$'}part -ResultClassName Win32_DiskDrive | Select-Object -First 1
                if (${'$'}drive) { ${'$'}disk = ${'$'}drive.SerialNumber }
              }
            }
            if (-not ${'$'}disk) {
              ${'$'}disk = (Get-CimInstance Win32_DiskDrive | Where-Object { ${'$'}_.MediaType -like '*fixed*' } | Sort-Object Index | Select-Object -First 1).SerialNumber
            }
            "MB=${'$'}mb"
            "CPU=${'$'}cpu"
            "DISK=${'$'}disk"
        """.trimIndent()

        val output = runProcess(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", script))
        if (output.isBlank()) return emptyMap()

        val map = mutableMapOf<String, String>()
        output.lineSequence().forEach { line ->
            val key = line.substringBefore('=', "").trim().uppercase()
            val value = line.substringAfter('=', "").let { normalize(it) }
            if (key in setOf("MB", "CPU", "DISK") && value.isNotBlank()) map[key] = value
        }
        return map
    }

    /** 备用：老系统上的 wmic，Win11 24H2 起默认已移除，读不到就静默 */
    private fun readWmic(): Map<String, String> {
        if (!System.getProperty("os.name").lowercase().contains("win")) return emptyMap()
        val map = mutableMapOf<String, String>()

        fun query(alias: String, field: String): String? =
            runProcess(listOf("wmic", alias, "get", field, "/value"))
                .lineSequence()
                .firstOrNull { it.startsWith(field, ignoreCase = true) && '=' in it }
                ?.substringAfter('=')
                ?.let { normalize(it) }

        query("baseboard", "SerialNumber")?.let { map["MB"] = it }
        query("cpu", "ProcessorId")?.let { map["CPU"] = it }
        query("diskdrive", "SerialNumber")?.let { map["DISK"] = it }
        return map
    }

    /**
     * 纯 Java 兜底：系统盘卷序列号 + CPU 标识，不需要起任何外部进程。
     * 卷序列号在格式化/重装后会变，所以只作为最后手段，并在 notes 里写明。
     */
    private fun javaFallback(): Map<String, String>? {
        val map = mutableMapOf<String, String>()
        System.getenv("PROCESSOR_IDENTIFIER")?.let { normalize(it) }?.let { map["CPU"] = it }

        val drive = System.getenv("SystemDrive").orEmpty().ifBlank { "C:" }
        runCatching {
            val path = java.nio.file.Paths.get("$drive\\")
            java.nio.file.Files.getFileStore(path).getAttribute("volume:vsn")?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { map["DISK"] = "VSN$it" }

        return map.takeIf { it.isNotEmpty() }
    }

    /** 非 Windows：DMI 与机器 ID，够用，允许读不到 */
    private fun readUnixHardware(): Map<String, String> {
        fun read(path: String): String? =
            runCatching { File(path).readText().trim() }.getOrNull()?.let { normalize(it) }

        val map = mutableMapOf<String, String>()
        read("/sys/class/dmi/id/board_serial")?.let { map["MB"] = it }
        read("/proc/cpuinfo")
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("model name") }
            ?.substringAfter(':')
            ?.let { normalize(it) }
            ?.let { map["CPU"] = it }
        read("/etc/machine-id")?.let { map["GUID"] = it }
        return map
    }

    private fun runProcess(command: List<String>): String = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ""
        }
        text
    }.getOrDefault("")

    /** 规范化：去空白、转大写；命中原厂占位符或明显无效的返回空串 */
    private fun normalize(value: String): String {
        val clean = value.trim().replace(Regex("\\s+"), " ").uppercase()
        if (clean.length < 4) return ""
        if (clean in placeholders) return ""
        if (clean.all { it == '0' } || clean.all { it == clean[0] }) return ""
        return clean
    }

    /** 最后兜底：安装时生成的随机值，只在本机稳定 */
    private fun stableRandom(): String {
        val file = File(ConfigStore.appDir(), "device.seed")
        val existing = runCatching { if (file.exists()) file.readText().trim() else "" }.getOrDefault("")
        if (existing.isNotBlank()) return existing
        val value = UUID.randomUUID().toString()
        runCatching { file.writeText(value) }
        return value
    }

    /** 注册表 MachineGuid：重装系统会变，只在前三项都读不到时用 */
    private fun readMachineGuid(): String? = runCatching {
        val output = runProcess(
            listOf("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"),
        )
        val value = Regex("MachineGuid\\s+REG_SZ\\s+(\\S+)").find(output)?.groupValues?.get(1)
        value?.let { normalize(it) }
    }.getOrNull()
}
