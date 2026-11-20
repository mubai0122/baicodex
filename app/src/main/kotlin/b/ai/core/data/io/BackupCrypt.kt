/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:06
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.data.io

import b.ai.BuildConfig
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的加解密：整份 AES-256-GCM，落在 .baicodex 里。
 *
 * 密钥来源：local.properties 的 baicodex.backup.key（1.17.2 起换成新的随机串）。
 * 1.17.1 及以前这个键没有配置，实际用的是占位串 baicodex-backup-placeholder，
 * 也就是说老备份的密钥是公开可推导的 —— 这是本次换钥的原因。
 *
 * 换钥后的策略：
 *  - 写出（seal）只用新密钥；
 *  - 读入（openEx）先试新密钥，失败再试旧占位串密钥，老备份还能导入一次；
 *  - 旧密钥只存在于读取路径，不参与任何加密。
 *
 * 结构：头 17B（baicodexencryption）+ 版本 1B + nonce 12B + 密文 + tag 16B，头作 AAD。
 */
object BackupCrypt {
    private const val MAGIC = "baicodexencryption"
    private const val VER = 1
    private const val NONCE = 12
    private const val TAG_BITS = 128
    /** 文件后缀：不加密的 zip 不再产出，老的 zip 备份导入时按旧路径走 */
    const val EXT = ".baicodex"
    /**
     * 1.17.1 及以前「正式包」实际生效的备份密钥：旧机 local.properties 里配的那串随机值。
     * 从已装旧包的 StringFog 密文里逆向得到，所有旧用户共用这一把（构建期常量）。
     */
    const val LEGACY = "35eac1baafc12fb15ca543e718aab71e023faee100f3b0e4c12d140d89fa0cf6"

    /**
     * 历史密钥表：只用于读，不参与任何加密。
     * 顺序＝尝试顺序：先是正式包用的随机串，再是 local.properties 没配时回落的占位串。
     */
    val LEGACY_KEYS = listOf(LEGACY, "baicodex-backup-placeholder")

    /** 解密结果：OK 新密钥解开；LEGACY 旧密钥解开（老备份，导入后应重新导出）；FAIL 都不行 */
    enum class Result { OK, LEGACY, FAIL }

    /** 当前密钥：local.properties 的串过一遍 SHA-256，得到 32 字节 */
    private val key: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(BuildConfig.BAI_BK_KEY.toByteArray())
    } //当前密钥

    /** 历史密钥：只用于读老备份，任何新文件都不用它加密 */
    private val legacyKeys: List<ByteArray> by lazy {
        BackupCrypt.LEGACY_KEYS.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
        }
    } //历史密钥表

    /** 判断一个文件是不是本格式的备份：只看头，不整份读 */
    fun isSealed(f: File): Boolean = runCatching {
        if (f.length() < MAGIC.length + 1) return false
        val head = ByteArray(MAGIC.length)
        f.inputStream().use { it.read(head) }
        String(head, Charsets.US_ASCII) == MAGIC
    }.getOrDefault(false) //是否密文

    /** 加密写入：src 是明文 zip，out 由调用方关；始终使用新密钥 */
    fun seal(src: File, out: OutputStream) {
        val nonce = ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        val head = MAGIC.toByteArray(Charsets.US_ASCII) + byteArrayOf(VER.toByte())
        out.write(head)
        out.write(nonce)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(head)
        src.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                out.write(c.update(buf, 0, n))
            }
        }
        out.write(c.doFinal())
        out.flush()
    } //加密备份

    /** 解密到 dst（旧签名，只要成功）：失败返回 false，不留半份明文 */
    fun open(src: File, dst: File): Boolean = openEx(src, dst) != Result.FAIL

    /**
     * 解密到 dst，并区分用的是哪把密钥。
     * 头/版本不对、文件被截断、两把密钥都解不开都返回 FAIL，且不留下半份明文。
     */
    fun openEx(src: File, dst: File): Result {
        if (decTo(src, dst, key)) return Result.OK
        //新密钥解不开，再按顺序试历史密钥：正式包随机串 → 占位串
        for (k in legacyKeys) if (decTo(src, dst, k)) return Result.LEGACY
        runCatching { if (dst.isFile) dst.delete() }
        return Result.FAIL
    } //解密并区分密钥

    /** 单次解密尝试：结构不对或 GCM 校验失败都返回 false，dst 不留残文件 */
    private fun decTo(src: File, dst: File, k: ByteArray): Boolean = runCatching {
        val skip = MAGIC.length + 1 + NONCE
        if (src.length() <= skip) return false
        src.inputStream().use { ins ->
            val head = ByteArray(MAGIC.length + 1)
            ins.read(head)
            if (String(head, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return false
            if (head[MAGIC.length].toInt() != VER) return false
            val nonce = ByteArray(NONCE)
            ins.read(nonce)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            c.updateAAD(head)
            dst.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    val p = c.update(buf, 0, n)
                    if (p != null) out.write(p)
                }
                out.write(c.doFinal())
            }
        }
        true
    }.getOrDefault(false) //单次解密
} //备份加解密