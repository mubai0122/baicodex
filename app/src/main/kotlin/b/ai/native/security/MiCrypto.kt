package b.ai.native.security
import android.util.Log
/**
 * 米家报文用的三样底层能力：AES-128-CBC、MD5、SHA-1。
 * 算法与补齐全在 C 层（core/security/crypto，走 android_openssl），这里只是薄壳；
 * Rust 侧通过同名 C ABI（bai_aes128_cbc_* / bai_md5 / bai_sha1）直接调同一份实现。
 */
object MiCrypto {
    private const val TAG = "bai.crypto"
    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }
    private external fun nativeAes(key: ByteArray, iv: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray?
    private external fun nativeMd5(data: ByteArray): ByteArray?
    private external fun nativeMd5Hex(data: ByteArray): String
    private external fun nativeSha1Hex(data: ByteArray): String
    private external fun nativeSelfTest(): Boolean
    /** 加解密：key / iv 必须是 16 字节；解密失败（密钥不对或补齐不合法）返回 null */
    fun aes128Cbc(key: ByteArray, iv: ByteArray, data: ByteArray, encrypt: Boolean): ByteArray? =
        if (!ok) null else runCatching { nativeAes(key, iv, data, encrypt) }.getOrNull()
    fun md5(data: ByteArray): ByteArray? =
        if (!ok) null else runCatching { nativeMd5(data) }.getOrNull()
    fun md5Hex(data: ByteArray): String =
        if (!ok) "" else runCatching { nativeMd5Hex(data) }.getOrDefault("")
    fun sha1Hex(data: ByteArray): String =
        if (!ok) "" else runCatching { nativeSha1Hex(data) }.getOrDefault("")
    /** 固定向量自检：native 没加载或算错都返回 false，调用点据此决定要不要继续走米家链路 */
    fun selfTest(): Boolean {
        if (!ok) {
            Log.w(TAG, "native 未加载，加解密不可用")
            return false
        }
        val r = runCatching { nativeSelfTest() }.getOrDefault(false)
        if (!r) Log.e(TAG, "crypto 自检失败：摘要或 AES 与标准向量不符")
        return r
    }
} //米家底层加解密