package b.ai.native.security
import b.ai.core.cfg.secret.Secret

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import b.ai.App
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 数据保障：三层密钥的总闸。
 * 一 设备密钥层：随机半密钥 R 由 Android Keystore（优先 StrongBox，退回 TEE）包裹后只存密文，明文 R 不出 Keystore。
 * 二 组合密钥层：K2 = HKDF(第二种子, 设备因子) 在 native 内存里合成，数据密钥 K = R ⊕ K2，K2 用完即销毁。
 * 三 内容加密层：K 交给 secret.cpp 做 AES-256-GCM，文件头版本从 1 变 2。
 * 缺 R 或缺 K2 都是噪音；换机两样同时没有，所以数据不迁机。
 */
object Guard {
    private const val SP = "bai_guard"
    private const val K_ON = "on"
    private const val K_IV = "iv"
    private const val K_CT = "ct"
    private const val K_DEV = "dev"
    private const val K_COST = "cost"
    private const val ALIAS = "bai.guard.r"

    private val sp by lazy { App.self.getSharedPreferences(SP, Context.MODE_PRIVATE) }
    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    /** 开关开着不等于解开了：Keystore 拿不到 R 时 ready 仍然是 false，文件只会解不开，不会被覆盖 */
    val on: Boolean get() = ok && sp.getBoolean(K_ON, false)

    val ready: Boolean get() = ok && runCatching { nativeReady() }.getOrDefault(false)

    /** 设备因子对不上 = 换了机器或刷了机，密文注定解不开 */
    val sameDevice: Boolean get() = sp.getString(K_DEV, "") == deviceTag()

    /** 上次开启实测耗时（毫秒），没跑过是 0 */
    val lastCostMs: Long get() = sp.getLong(K_COST, 0L)

    /** 设备因子摘要：只用来比对「还是不是这台机器」，本身反推不出密钥 */
    fun deviceTag(): String {
        val raw = listOf(
            Build.FINGERPRINT, Build.BOARD, Build.HARDWARE, Build.DEVICE,
            Build.MANUFACTURER, Build.MODEL, androidId()
        ).joinToString("|")
        return sha256(raw).take(16)
    } //设备因子摘要

    /** 设备名，警告文案里给用户看的就是这个 */
    fun deviceName(): String = (Build.MANUFACTURER + " " + Build.MODEL).trim()

    /** 冷启动调用：开关开着就把 K2 与 K 重新拼出来，失败说明这台机器解不开自己的数据 */
    fun boot(): Boolean {
        if (!on) return false
        if (!sameDevice) return false
        val raw = unwrapR() ?: return false
        val rHex = raw.joinToString("") { "%02x".format(it) }
        val done = nativeStart(deviceFactor()) && nativeUnlock(rHex)
        raw.fill(0.toByte())
        //这里只解锁，密文重写交给 boot-init 后台线程：Application.onCreate 主线程只留必要的密钥派生
        return done
    } //开机解锁

    /**
     * 开启：按 1 生成密钥 → 2 迁移旧数据 → 3 加密落盘 → 4 完成 上报进度，
     * 每一步都是真活，不是拿计时器凑出来的动画。
     */
    fun enable(onStep: (Int) -> Unit = {}): Boolean {
        if (on) return true
        if (!ok) return false
        val t0 = SystemClock.elapsedRealtime()
        onStep(1)
        val rHex = nativeRandom()
        if (rHex.length != 64) return false
        val r = hexBytes(rHex)
        val sealed = wrap(r)
        if (sealed == null) {
            r.fill(0.toByte())
            return false
        }
        if (!nativeStart(deviceFactor())) {
            r.fill(0.toByte())
            deleteKey()
            return false
        }
        if (!nativeUnlock(rHex)) {
            r.fill(0.toByte())
            deleteKey()
            return false
        }
        r.fill(0.toByte())
        //迁移：老的明文键表先并进加密库，再把每个桶解进内存统一口径
        onStep(2)
        runCatching { Secret.migrate() }
        Vault.loadAll()
        //先落盘标记再重写：中途被杀，下次开机也认这个开关，旧 v1 文件照样能读
        onStep(3)
        sp.edit()
            .putBoolean(K_ON, true)
            .putString(K_IV, b64(sealed.first))
            .putString(K_CT, b64(sealed.second))
            .putString(K_DEV, deviceTag())
            .apply()
        Vault.flushAll()
        sp.edit().putLong(K_COST, SystemClock.elapsedRealtime() - t0).apply()
        onStep(4)
        return true
    } //开启数据超强加密

    /** 强制关闭：native 密钥清零 → 密文文件全删 → Keystore 条目删除 → 开关标记清空 */
    fun disable(): Boolean {
        if (!ok) return false
        wipenative()
        Vault.wipeAll()
        deleteKey()
        sp.edit().clear().apply()
        return true
    } //强制关闭并清空

    private fun wipenative() = runCatching { nativeLock() }

    private fun deviceFactor(): String = listOf(
        Build.FINGERPRINT, Build.BOARD, Build.HARDWARE, Build.DEVICE,
        Build.MANUFACTURER, Build.MODEL, androidId()
    ).joinToString("|") //交给 native 当 HKDF 的 salt

    private fun androidId(): String = runCatching {
        Settings.Secure.getString(App.self.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrDefault("")

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray =
        runCatching { Base64.decode(s, Base64.NO_WRAP) }.getOrDefault(ByteArray(0))

    private fun hexBytes(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(s[i * 2], 16) shl 4) or Character.digit(s[i * 2 + 1], 16)).toByte()
        }
        return out
    } //64 字符十六进制 → 32 字节

    /** 从 Keystore 里取（或新建）设备密钥，把 R 包成 密文 + IV */
    private fun wrap(r: ByteArray): Pair<ByteArray, ByteArray>? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        val gspec = GCMParameterSpec(128, iv())
        c.init(Cipher.ENCRYPT_MODE, key(), gspec)
        c.iv to c.doFinal(r)
    }.getOrNull()

    /** 只有本机 Keystore 里那把不可导出的密钥能解开 */
    private fun unwrapR(): ByteArray? = runCatching {
        val iv = unb64(sp.getString(K_IV, "") ?: "")
        val ct = unb64(sp.getString(K_CT, "") ?: "")
        if (iv.size != 12 || ct.isEmpty()) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        c.doFinal(ct)
    }.getOrNull()

    private fun iv(): ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        //StrongBox 是独立安全芯片，能上就上；上不了退回 TEE，两者都不让密钥离开硬件
        if (Build.VERSION.SDK_INT >= 28) {
            val hard = runCatching {
                g.init(spec(true))
                g.generateKey()
            }.getOrNull()
            if (hard != null) return hard
        }
        g.init(spec(false))
        return g.generateKey()
    } //设备密钥

    private fun spec(strong: Boolean): KeyGenParameterSpec {
        val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            //IV 由我们自己用 SecureRandom 出，交给 Keystore 随机化会和 StrongBox 打架
            .setRandomizedEncryptionRequired(false)
        if (strong && Build.VERSION.SDK_INT >= 28) b.setIsStrongBoxBacked(true)
        return b.build()
    } //密钥参数

    private fun deleteKey() = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }

    private external fun nativeStart(dev: String): Boolean
    private external fun nativeUnlock(r64: String): Boolean
    private external fun nativeLock()
    private external fun nativeReady(): Boolean
    private external fun nativeRandom(): String
} //数据保障