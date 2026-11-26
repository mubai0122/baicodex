/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:25:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 本地凭据加密：API Key / Token 落盘前用 AES-256-GCM 加密，前缀 enc:v1:。
 * 密钥 = SHA-256( 盐(存 config) [‖ 机器标识] )——绑定设备开启时拿到 config.json
 * 到别的机器也解不开；关闭时只靠盐加密，配置可随备份带走。
 * 只保护凭据，会话内容仍按原样存（聊天记录随时可在设置里清空）。
 */

package b.ai.desktop.core.security

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Vault {
    const val PREFIX = "enc:v1:"

    private val json = Base64.getEncoder()
    private val unjson = Base64.getDecoder()

    /** 是否已带加密前缀 */
    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    /**
     * 机器标识：MachineGuid → 主板相关环境变量 → 用户名；尽力而为，机器内稳定即可。
     * 结果缓存到 <appDir>/machine.id：reg.exe 偶尔读不到（沙箱/受限环境）时，
     * 密钥材料仍与首次一致，不会出现「上一次能解、这一次解不开」。
     */
    fun machineKey(): String {
        val guid = runCatching {
            val p = ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                .redirectErrorStream(true).start()
            p.inputStream.readBytes().decodeToString().lineSequence()
                .firstOrNull { it.contains("MachineGuid") }?.substringAfterLast(" ")?.trim().orEmpty()
        }.getOrNull().orEmpty()
        if (guid.isNotBlank()) {
            cacheKey(guid)
            return guid
        }
        cacheFile()?.takeIf { it.exists() }?.let { cached ->
            runCatching { cached.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val fallback = listOf(
            System.getProperty("user.name"),
            System.getenv("COMPUTERNAME") ?: "",
            System.getenv("PROCESSOR_IDENTIFIER") ?: "",
        ).joinToString("|")
        cacheKey(fallback)
        return fallback
    }

    private fun cacheFile(): File? = runCatching {
        b.ai.desktop.core.config.ConfigStore.appDir().resolve("machine.id")
    }.getOrNull()

    private fun cacheKey(value: String) {
        runCatching { cacheFile()?.writeText(value) }
    }

    /** 盐：读 config.vaultSalt；没有就生成一条（调用方负责落盘） */
    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 派生密钥：bindDevice = 密钥里混入机器标识（拷走文件解不开）；
     * 关闭时只用盐——同一份备份到哪都能解开，仍不是明文。
     */
    private fun key(salt: String, bindDevice: Boolean): SecretKeySpec {
        val material = if (bindDevice) "$salt\u241f${machineKey()}" else salt
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    /** 加密；空串原样返回 */
    fun seal(plain: String, salt: String, bindDevice: Boolean = false): String {
        if (plain.isBlank()) return plain
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(salt, bindDevice), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + json.encodeToString(iv + sealed)
    }

    /** 按指定绑定模式解密；不是密文或解不开就原样返回（老配置/损坏数据不至于让应用起不来） */
    fun open(value: String, salt: String, bindDevice: Boolean = false): String {
        if (!isSealed(value)) return value
        return runCatching {
            val all = unjson.decode(value.removePrefix(PREFIX))
            val iv = all.copyOfRange(0, 12)
            val data = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(salt, bindDevice), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrElse { value }
    }

    /**
     * 迁移友好解密：先按「绑机器」试一遍，解不开再按「只靠盐」试一遍。
     * 开关怎么拨，历史密文都读得回来——不会出现一重启 Key 变成一串 enc:v1: 乱码。
     */
    fun openAny(value: String, salt: String): String {
        if (!isSealed(value)) return value
        val bound = open(value, salt, bindDevice = true)
        if (bound != value) return bound
        return open(value, salt, bindDevice = false)
    }
}
