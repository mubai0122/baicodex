package b.ai.net

import android.util.Log
import b.ai.core.Bai
import b.ai.core.Cfg
import b.ai.core.Mask
import b.ai.core.Vault
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 脱敏规则同步：服务端下发关键词表，本地只做缓存与装载。
 * 规则不算凭据，但也只以密文落盘（跟凭据一起锁在 Vault 里），避免被人直接抄走关键词表去绕。
 */
object MaskRules {
    private const val TAG = "bai.mask.rules"

    /** 规则原文 */
    private const val RULES = "rules"

    /** 上次成功拉取的时间戳 */
    private const val STAMP = "stamp"

    /** 缓存有效期：一天一换；换不到就继续用上一份 */
    private const val TTL = 24 * 3600_000L

    /** 同步一次：开关没开就不打扰服务端；[force] 用于刚打开开关时立刻拉一份。拉到并装成功返回 true */
    fun sync(force: Boolean = false): Boolean {
        if (!Cfg.maskOut && !force) return false
        val now = System.currentTimeMillis()
        val last = Vault.get(STAMP, Vault.MASK).toLongOrNull() ?: 0L
        if (!force && now - last < TTL) return true
        val body = pull() ?: return false
        Vault.put(RULES, body, Vault.MASK)
        Vault.put(STAMP, now.toString(), Vault.MASK)
        if (!Mask.install(body)) {
            Log.w(TAG, "服务端规则装载失败，继续用上一份")
            return false
        }
        return true
    } //同步一次

    /** 拉一次规则原文，失败返回 null */
    private fun pull(): String? {
        val conn = (URL(Bai.api("/api/secure/mask.php")).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (root.optInt("code") != 200) return null
            val rules = root.optJSONObject("data")?.optString("rules").orEmpty().trim()
            //规则文本必须有换行分隔的 KV 结构，否则当成坏数据丢掉
            if (rules.indexOf('=') < 0) null else rules
        } catch (e: Exception) {
            Log.w(TAG, "规则拉取失败: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    } //拉规则
} //脱敏规则同步