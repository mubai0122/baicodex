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
 * ADB 工具（仅桌面端）：给模型一个手机互联的操作面。
 * 14 种操作：devices / connect / disconnect / tcpip / shell / ls / cat /
 *   push / pull / install / uninstall / screencap / logcat / battery。
 * 传文件走 TransferManager：同机双通道（USB+内网）自动分块并发，失败回退单通道。
 * 本地路径安全约束：push 的源与 pull 的目标只允许工作区或系统临时目录。
 */

package b.ai.desktop.core.tool.tools

import b.ai.desktop.core.adb.ADB
import b.ai.desktop.core.adb.AdbException
import b.ai.desktop.core.adb.TransferManager
import b.ai.desktop.core.tool.Tool
import b.ai.desktop.core.tool.ToolResult
import b.ai.desktop.core.tool.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

class AdbTool(
    private val adbPathProvider: () -> String?,
    private val workspaceRoot: File,
    private val allowDualChannel: () -> Boolean,
) : Tool {

    override val spec = ToolSpec(
        name = "adb",
        description = "手机互联（仅桌面端）：通过内置 ADB 操作已连接的安卓设备。" +
            "支持设备清单、无线连接、shell、文件传输（USB+内网双通道分块）、安装卸载、" +
            "截图、日志、电量等。操作设备前先 devices 看清单；多设备时必须传 serial。",
        parameters = buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put("operation", str("操作：devices/connect/disconnect/tcpip/shell/ls/cat/push/pull/install/uninstall/screencap/logcat/battery"))
                    put("serial", str("设备序列号；多设备时必填（devices 输出里拿）"))
                    put("ip_port", str("无线目标，如 192.168.1.5:5555（connect/disconnect 用）"))
                    put("command", str("shell 里要执行的命令（shell/ls 用）"))
                    put("remote_path", str("手机上的路径（cat/pull/push 目标目录/install 之外的文件操作用）"))
                    put("local_path", str("电脑路径，只允许工作区内或系统临时目录（push 源 / pull 目标 / screencap 保存）"))
                    put("package_name", str("应用包名（install/uninstall 用；install 另需 local_path 指向 apk）"))
                    put("lines", num("logcat 抓取行数，默认 200"))
                },
            )
            put("required", JsonArray(listOf(JsonPrimitive("operation"))))
        },
    )

    private fun str(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun num(description: String) = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val adbPath = adbPathProvider()
            ?: return@withContext ToolResult(false, "没找到 adb.exe：可在设置 → 扩展 → 手机互联里指定路径")
        val op = (args["operation"] as? JsonPrimitive)?.content?.lowercase().orEmpty()

        try {
            when (op) {
                "devices" -> ToolResult(true, formatDevices(adbPath))

                "connect" -> {
                    val target = args.stringArg("ip_port").orEmpty().ifBlank {
                        // 没给目标就扫本机网段 5555，找到了连第一个
                        val prefix = ADB.localSubnetPrefix()
                            ?: return@withContext ToolResult(false, "没有 ip_port，也拿不到本机网段")
                        val hits = ADB.scanSubnet(prefix)
                        if (hits.isEmpty()) return@withContext ToolResult(false, "网段 $prefix.* 里没探测到 5555 端口；请先在手机上开无线调试")
                        hits.first()
                    }
                    val target2 = if (target.contains(":")) target else "$target:5555"
                    ToolResult(true, ADB.connect(target2, adbPath))
                }

                "disconnect" -> ToolResult(true, ADB.disconnect(args.stringArg("ip_port").orEmpty(), adbPath))

                "tcpip" -> {
                    val serial = pickSerial(adbPath, args)
                    ToolResult(true, ADB.enableTcpip(5555, adbPath, serial) + "\n之后用 connect <手机IP>:5555 走内网")
                }

                "shell" -> {
                    val serial = pickSerial(adbPath, args)
                    val command = args.stringArg("command").orEmpty()
                    if (command.isBlank()) return@withContext ToolResult(false, "缺少 command")
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "shell", command), adbPath, 120)))
                }

                "ls" -> {
                    val serial = pickSerial(adbPath, args)
                    val path = args.stringArg("remote_path").orEmpty().ifBlank { "/sdcard" }
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "shell", "ls -l \"$path\""), adbPath, 60)))
                }

                "cat" -> {
                    val serial = pickSerial(adbPath, args)
                    val path = args.stringArg("remote_path").orEmpty()
                    if (path.isBlank()) return@withContext ToolResult(false, "缺少 remote_path")
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "shell", "cat \"$path\""), adbPath, 60)))
                }

                "push" -> {
                    val serial = pickSerial(adbPath, args)
                    val local = resolveLocal(args.stringArg("local_path").orEmpty())
                        ?: return@withContext ToolResult(false, "local_path 必须在工作区或系统临时目录内")
                    val dir = args.stringArg("remote_path").orEmpty().ifBlank { "/sdcard/Download" }
                    val channels = channelsFor(adbPath, serial)
                    val out = TransferManager(adbPath).push(local, dir, channels, allowDualChannel(), serial)
                    ToolResult(true, out)
                }

                "pull" -> {
                    val serial = pickSerial(adbPath, args)
                    val remote = args.stringArg("remote_path").orEmpty()
                    if (remote.isBlank()) return@withContext ToolResult(false, "缺少 remote_path")
                    val localDir = resolveLocal(args.stringArg("local_path").orEmpty().ifBlank { workspaceRoot.path })
                        ?: return@withContext ToolResult(false, "local_path 必须在工作区或系统临时目录内")
                    val channels = channelsFor(adbPath, serial)
                    val out = TransferManager(adbPath).pull(remote, localDir, channels, allowDualChannel())
                    ToolResult(true, "已拉取 → $out")
                }

                "install" -> {
                    val serial = pickSerial(adbPath, args)
                    val apk = resolveLocal(args.stringArg("local_path").orEmpty())
                        ?: return@withContext ToolResult(false, "local_path 必须指向工作区或临时目录里的 apk")
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "install", "-r", apk.absolutePath), adbPath, 600)))
                }

                "uninstall" -> {
                    val serial = pickSerial(adbPath, args)
                    val pkg = args.stringArg("package_name").orEmpty()
                    if (pkg.isBlank()) return@withContext ToolResult(false, "缺少 package_name")
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "uninstall", pkg), adbPath, 120)))
                }

                "screencap" -> {
                    val serial = pickSerial(adbPath, args)
                    val stamp = System.currentTimeMillis()
                    val target = resolveLocal(args.stringArg("local_path").orEmpty().ifBlank { workspaceRoot.path })
                        ?: return@withContext ToolResult(false, "local_path 必须在工作区或系统临时目录内")
                    val remote = "/data/local/tmp/bcx-shot-$stamp.png"
                    ADB.run(listOf("-s", serial, "shell", "screencap -p $remote"), adbPath, 60)
                    val out = TransferManager(adbPath).pull(remote, target, channelsFor(adbPath, serial), false)
                    ADB.run(listOf("-s", serial, "shell", "rm -f $remote"), adbPath, 30)
                    ToolResult(true, "截图已保存 → $out")
                }

                "logcat" -> {
                    val serial = pickSerial(adbPath, args)
                    val lines = (args["lines"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 200
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "logcat", "-d", "-t", lines.toString()), adbPath, 60)))
                }

                "battery" -> {
                    val serial = pickSerial(adbPath, args)
                    ToolResult(true, clip(ADB.run(listOf("-s", serial, "shell", "dumpsys battery"), adbPath, 60)))
                }

                else -> ToolResult(false, "未知操作：$op。可用：devices/connect/disconnect/tcpip/shell/ls/cat/push/pull/install/uninstall/screencap/logcat/battery")
            }
        } catch (e: AdbException) {
            ToolResult(false, e.message ?: "adb 操作失败")
        } catch (t: Throwable) {
            ToolResult(false, "adb 操作失败：${t.message}")
        }
    }

    /** 多设备且没给 serial：只剩一台就直接用，否则报错让模型先看 devices */
    private fun pickSerial(adbPath: String, args: JsonObject): String {
        val given = args.stringArg("serial").orEmpty()
        if (given.isNotBlank()) return given
        val online = ADB.devices(adbPath).filter { it.state == "device" }
        return when (online.size) {
            1 -> online[0].serial
            0 -> throw AdbException("没有在线设备；先用 devices 看清单，或 connect ip:5555 无线连接")
            else -> throw AdbException("有 ${online.size} 台在线设备，请带 serial 参数指定：${online.joinToString("、") { it.serial }}")
        }
    }

    /** 通道推断：同一台设备被 USB 和内网同时看到时返回双通道 */
    private fun channelsFor(adbPath: String, serial: String): TransferManager.Channels {
        val online = ADB.devices(adbPath).filter { it.state == "device" }
        val self = online.firstOrNull { it.serial == serial }
        val lan = self?.serial?.takeIf { it.contains(":") }
        val usb = self?.serial?.takeUnless { it.contains(":") }
            // USB 目标也可能在内网：找同 model 的无线条目配对
            ?: online.firstOrNull { it.model == self?.model && it.serial.contains(":") }?.serial
        return TransferManager.Channels(usb, lan)
    }

    /** 本地路径约束：工作区内或系统临时目录内 */
    private fun resolveLocal(raw: String): File? {
        val path = raw.trim()
        if (path.isBlank()) return null
        val file = File(path).canonicalFile
        val inside = sequenceOf(workspaceRoot.canonicalFile, java.io.File(System.getProperty("java.io.tmpdir")).canonicalFile)
            .any { root -> file == root || file.path.startsWith(root.path + File.separator) }
        return if (inside) file else null
    }

    private fun String.isWirelessSerial(): Boolean = contains(":")

    private fun formatDevices(adbPath: String): String {
        val devices = runCatching { ADB.devices(adbPath) }.getOrElse { return "adb 查询失败：${it.message}" }
        if (devices.isEmpty()) {
            return "没有设备（adb devices 为空）。排查顺序：\n" +
                "1. 手机 USB 用途改成「传输文件 / MTP」（仅充电模式读不到）\n" +
                "2. 手机开「USB 调试」（开发者选项）\n" +
                "3. 解锁手机，允许「USB 调试」弹窗并勾「始终允许」\n" +
                "4. 换一根能传数据的线；还不行就让用户在设置页点「重启 adb 服务」\n" +
                "无线：手机开无线调试后 connect <手机IP>:5555"
        }
        return devices.joinToString("\n") { d ->
            val channel = if (d.serial.isWirelessSerial()) "内网" else "USB"
            val hint = if (d.state == "device") "" else "  ← ${ADB.stateHint(d.state)}"
            "· ${d.serial}  ${d.state}  ${d.model}  [$channel]$hint"
        } + "\n（在线设备操作不用带 serial；两台以上必须带）"
    }

    private fun clip(text: String, limit: Int = 4000): String =
        if (text.length <= limit) text else text.take(limit) + "\n…（截断，共 ${text.length} 字）"
}

private fun JsonObject.stringArg(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
