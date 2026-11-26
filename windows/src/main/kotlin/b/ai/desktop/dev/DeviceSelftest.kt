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
 * 设备码自检：`gradle run --args="--selftest-device"`。
 * 只打印哈希、来源与一致性，不打印任何原始硬件序列号。
 */

package b.ai.desktop.dev

import b.ai.desktop.core.balance.BalanceWatch
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.device.DeviceCode
import b.ai.desktop.core.usage.UsageReporter
import kotlinx.coroutines.runBlocking

fun runDeviceSelftest() = runBlocking {
    val first = DeviceCode.refresh()
    val second = DeviceCode.refresh()
    println("设备码：${first.pretty}")
    println("来源：${first.sources.joinToString(" + ")}")
    first.notes.forEach { println("说明：$it") }
    println("长度：${first.code.length} 位十六进制")
    println("[${if (first.code == second.code) "OK " else "ERR"}] 两次采集一致：${first.code == second.code}")
    println("[${if (first.code.length == 64) "OK " else "ERR"}] 哈希长度为 64")
    println("[${if (first.sources.isNotEmpty()) "OK " else "ERR"}] 至少读到一个来源")

    // 报告一次（累加语义；409 视为已记账）
    val report = UsageReporter.report()
    println("[${if (report.ok) "OK " else "ERR"}] 上报：${report.message.take(100)}")

    // 余额：用当前配置的 Key 试一次，失败也是有效信息
    val config = ConfigStore.load()
    val profile = config.profiles.firstOrNull { it.id == config.activeProfileId } ?: config.profiles.firstOrNull()
    if (profile == null) {
        println("[-- ] 余额：没有模型配置，跳过")
    } else {
        val balance = BalanceWatch.refresh(profile, force = true)
        println(
            if (balance?.ok == true) "[OK ] 余额：${balance.text}（来源 ${balance.source}）"
            else "[-- ] 余额：查不到（${balance?.error ?: "未知"}）",
        )
    }
}
