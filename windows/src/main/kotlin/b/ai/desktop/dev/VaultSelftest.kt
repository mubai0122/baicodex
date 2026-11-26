/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 06:10:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 凭据加密自检：`gradle run --args="--selftest-vault"`。全程离线：
 * 加解密回环、绑定开关两种模式互读（迁移友好）、落盘配置保存/读取回环。
 */

package b.ai.desktop.dev

import b.ai.desktop.core.config.AppConfig
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.config.ModelProfile
import b.ai.desktop.core.security.Vault
import java.io.File

private var pass = 0
private var fail = 0

private fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) pass++ else fail++
    println("[${if (ok) "OK " else "ERR"}] $name${if (detail.isNotBlank()) "：$detail" else ""}")
}

fun runVaultSelftest() {
    val salt = Vault.newSalt()
    val secret = "sk-test-0123456789abcdef"

    // 1. 两种模式各自的加解密回环
    val sealedBind = Vault.seal(secret, salt, bindDevice = true)
    val sealedFree = Vault.seal(secret, salt, bindDevice = false)
    check("密文带 enc:v1: 前缀", Vault.isSealed(sealedBind) && Vault.isSealed(sealedFree))
    check("绑定模式回环", Vault.open(sealedBind, salt, bindDevice = true) == secret)
    check("非绑定模式回环", Vault.open(sealedFree, salt, bindDevice = false) == secret)
    check("同明文两次加密密文不同（随机 IV）", sealedBind != Vault.seal(secret, salt, bindDevice = true))

    // 2. 错盐 / 关掉绑定读绑定密文 → 解不开，原样返回
    check("错盐解不开", Vault.open(sealedBind, Vault.newSalt(), bindDevice = true) == sealedBind)
    check("非绑定模式读绑定密文失败", Vault.open(sealedBind, salt, bindDevice = false) == sealedBind)
    check("明文原样返回", Vault.open(secret, salt) == secret)

    // 3. openAny：绑定开关拨过，历史密文仍读得回来（迁移友好）
    check("openAny 读绑定密文", Vault.openAny(sealedBind, salt) == secret)
    check("openAny 读非绑定密文", Vault.openAny(sealedFree, salt) == secret)

    // 4. 落盘回环（重定向到临时目录，不碰真实 %APPDATA%）
    val tmp = File(System.getProperty("java.io.tmpdir"), "baicodex-vault-selftest")
    tmp.deleteRecursively()
    try {
        ConfigStore.dirOverride = tmp
        val original = AppConfig(
            profiles = listOf(
                ModelProfile(name = "测试连接", baseUrl = "https://example.com/v1", apiKey = secret, model = "test-model"),
            ),
            activeProfileId = "",
            tavilyKey = "tvly-selftest",
            encryptSecrets = true,
            vaultSalt = salt,
            vaultBindDevice = false,
        )
        ConfigStore.save(original)
        val onDisk = tmp.resolve("config.json").readText()
        check("落盘后 Key 是密文", onDisk.contains("enc:v1:"))
        check("落盘后看不到明文 Key", !onDisk.contains(secret))
        check("落盘后看不到明文 Tavily Key", !onDisk.contains("tvly-selftest"))
        check("无残留临时文件", !tmp.resolve("config.json.tmp").exists())

        val reloaded = ConfigStore.load()
        check("重新读取 Key 还原", reloaded.profiles.first().apiKey == secret)
        check("重新读取 Tavily Key 还原", reloaded.tavilyKey == "tvly-selftest")

        // 5. 绑定开关拨到开 → 再存一次按新密钥重封，读取仍还原
        ConfigStore.save(reloaded.copy(vaultBindDevice = true))
        val reloaded2 = ConfigStore.load()
        check("切换绑定后重新读取 Key 还原", reloaded2.profiles.first().apiKey == secret)

        // 6. 半截 JSON（模拟写入中断）→ 读取给默认配置，坏文件留底
        tmp.resolve("config.json").writeText("{\"profiles\":[")
        val broken = ConfigStore.load()
        check("坏文件读取不给崩", broken.profiles.isEmpty() && broken.vaultSalt.isBlank())
        check("坏文件留底 config.json.corrupt", tmp.resolve("config.json.corrupt").exists())
    } finally {
        ConfigStore.dirOverride = null
        tmp.deleteRecursively()
    }

    // 7. 机器标识采集稳定（reg 读不到时回退+缓存，密钥材料不漂移）
    val m1 = Vault.machineKey()
    val m2 = Vault.machineKey()
    check("机器标识两次采集一致", m1 == m2 && m1.isNotBlank())

    println("机器标识指纹：${m1.take(8)}…（绑定模式混入密钥）")
    println("自检结束：$pass 通过 / $fail 失败")
    if (fail > 0) kotlin.system.exitProcess(1)
}
