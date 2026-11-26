/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 05:30:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ADB 自检（不需要真机）：定位 adb → version → devices 解析 → 工具规格。
 * gradle run --args="--selftest-adb"
 */

package b.ai.desktop.dev

import b.ai.desktop.core.adb.ADB
import b.ai.desktop.core.tool.tools.AdbTool
import java.io.File

fun runAdbSelftest() {
    val adbPath = ADB.locate()
    if (adbPath == null) {
        println("[FAIL] 找不到 adb.exe")
        return
    }
    println("[OK ] 定位 adb：$adbPath")
    val version = runCatching { ADB.run(listOf("version"), adbPath) }.getOrNull()
    if (version == null || !version.contains("Android Debug Bridge")) {
        println("[FAIL] version 输出不对：${version?.take(120)}")
        return
    }
    println("[OK ] version：${version.lineSequence().first()}")

    val devices = ADB.devices(adbPath)
    println("[OK ] devices 解析：${devices.size} 台（${devices.joinToString("、") { it.serial }}）")

    val tool = AdbTool({ adbPath }, File("build"), { true })
    println("[OK ] 工具规格：${tool.spec.name} · ${tool.spec.description.take(40)}…")
    println("--- ADB 自检通过（设备操作需真机）---")
}
