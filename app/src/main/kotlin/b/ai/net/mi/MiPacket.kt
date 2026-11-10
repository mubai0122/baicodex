package b.ai.net.mi

import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 米家局域网报文（0x2131）编解码。
 * 头 32 字节：magic2 + 总长2 + did8 + 时间戳4 + 校验16；body 是 JSON 过 AES-128-CBC，
 * token 同时当密钥和 IV，校验位是对整包（含 token）算的 MD5。
 */
object MiPacket {
    const val H0 = 0x21
    const val H1 = 0x31
    const val HEAD = 32

    private const val AES = "AES/CBC/PKCS5Padding"

    private fun md5(b: ByteArray, len: Int): ByteArray =
        MessageDigest.getInstance("MD5").apply { update(b, 0, len) }.digest() //整包 MD5

    fun hex2bytes(hex: String): ByteArray? {
        val s = hex.trim().replace(":", "").replace(" ", "")
        if (s.length != 32) return null
        val out = ByteArray(16)
        for (i in 0 until 16) {
            val hi = Character.digit(s[i * 2], 16)
            val lo = Character.digit(s[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    } //token 是 32 位十六进制字符串

    fun bytes2hex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x))
        return sb.toString()
    } //反向：给人看的 token

    private fun putLong(b: ByteArray, at: Int, v: Long) {
        for (i in 0 until 8) b[at + i] = ((v shr (56 - 8 * i)) and 0xFF).toByte()
    } //大端写 8 字节

    fun didOf(b: ByteArray): Long {
        if (b.size < 12) return -1
        var v = 0L
        for (i in 4 until 12) v = (v shl 8) or (b[i].toLong() and 0xFF)
        return v
    } //从报文头取 did

    fun stampOf(b: ByteArray): Long {
        if (b.size < 16) return 0
        return ((b[12].toLong() and 0xFF) shl 24) or ((b[13].toLong() and 0xFF) shl 16) or
            ((b[14].toLong() and 0xFF) shl 8) or (b[15].toLong() and 0xFF)
    } //设备侧时间戳，用来算时钟差

    fun probe(virtualDid: Long): ByteArray {
        val b = ByteArray(32)
        b[0] = H0.toByte()
        b[1] = H1.toByte()
        b[2] = 0
        b[3] = 32
        for (i in 4 until 16) b[i] = 0xFF.toByte()
        "MDID".toByteArray(Charsets.US_ASCII).copyInto(b, 16)
        putLong(b, 20, virtualDid)
        return b
    } //32 字节广播探测包：did 段填全 F，MDID 标记后跟自己的虚拟 did

    fun pack(token: ByteArray, did: Long, stamp: Int, json: String): ByteArray? {
        return runCatching {
            val c = Cipher.getInstance(AES).apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(token, "AES"), IvParameterSpec(token))
            }
            val enc = c.doFinal(json.toByteArray(Charsets.UTF_8))
            val total = HEAD + enc.size
            val out = ByteArray(total)
            out[0] = H0.toByte()
            out[1] = H1.toByte()
            out[2] = ((total shr 8) and 0xFF).toByte()
            out[3] = (total and 0xFF).toByte()
            putLong(out, 4, did)
            out[12] = ((stamp shr 24) and 0xFF).toByte()
            out[13] = ((stamp shr 16) and 0xFF).toByte()
            out[14] = ((stamp shr 8) and 0xFF).toByte()
            out[15] = (stamp and 0xFF).toByte()
            System.arraycopy(token, 0, out, 16, 16)
            System.arraycopy(enc, 0, out, HEAD, enc.size)
            System.arraycopy(md5(out, total), 0, out, 16, 16)
            out
        }.getOrNull()
    } //打一包：明文 JSON → 加密 → 填头 → 补校验

    fun unpack(token: ByteArray, raw: ByteArray): JSONObject? {
        return runCatching {
            if (raw.size <= HEAD) return null
            if (raw[0] != H0.toByte() || raw[1] != H1.toByte()) return null
            val len = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
            //设备会把总长写小或写超出实收长度，一律按实收裁，别信头里的长度
            val body = raw.copyOfRange(0, minOf(raw.size, if (len in HEAD..raw.size) len else raw.size))
            val md5Orig = body.copyOfRange(16, 32)
            System.arraycopy(token, 0, body, 16, 16)
            val calc = md5(body, body.size)
            if (!md5Orig.contentEquals(calc)) return null
            val c = Cipher.getInstance(AES).apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(token, "AES"), IvParameterSpec(token))
            }
            val clear = c.doFinal(body, HEAD, body.size - HEAD)
            //个别设备在 JSON 尾巴补 \0，去掉再解析
            val s = String(clear, Charsets.UTF_8).trimEnd('\u0000')
            if (s.isEmpty()) null else JSONObject(s)
        }.getOrNull()
    } //解一包：先验校验位，再解密，失败一律返回 null
} //报文编解码