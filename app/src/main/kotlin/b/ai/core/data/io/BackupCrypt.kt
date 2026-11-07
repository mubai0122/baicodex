package b.ai.core.data.io

import b.ai.BuildConfig
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的加解密：整份 AES-256-GCM，落在 .baicodex 里。
 *
 * 密钥跟加密库那把完全分开：加密库是「四段种子 + 设备硬件」绑死本机的，换机就解不开；
 * 备份的作用恰恰是换机，所以这里用 local.properties 里固定的 baicodex.backup.key，
 * 同一把密钥打出的备份在任何一台装了本 App 的设备上都能导入。
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

    /** 密钥：local.properties 的串过一遍 SHA-256，得到 32 字节 */
    private val key: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(BuildConfig.BAI_BK_KEY.toByteArray())
    }

    /** 判断一个文件是不是本格式的备份：只看头，不整份读 */
    fun isSealed(f: File): Boolean = runCatching {
        if (f.length() < MAGIC.length + 1) return false
        val head = ByteArray(MAGIC.length)
        f.inputStream().use { it.read(head) }
        String(head, Charsets.US_ASCII) == MAGIC
    }.getOrDefault(false) //是否密文

    /** 加密写入：src 是明文 zip，out 由调用方关 */
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

    /** 解密到 dst：头/版本不对、密钥换了、文件被截断都会返回 false，不留半份明文 */
    fun open(src: File, dst: File): Boolean = runCatching {
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
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
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
    }.getOrDefault(false) //解密备份
} //备份加解密