package b.ai.net.mi

import android.util.Log
import b.ai.core.cfg.secret.Secret
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

/**
 * 米家账号登录（OAuth2 授权码模式）。
 * 授权页在 account.xiaomi.com，换/续 token 在 {region}.ha.api.io.mi.com 的 /app/v2/ha/oauth/get_token。
 * 设备身份固定 "ha." + uuid，state 是 sha1("d=" + 设备身份) 的十六进制。
 */
object MiAuth {
    private const val TAG = "bai.mi.auth"

    const val AUTH_URL = "https://account.xiaomi.com/oauth2/authorize"
    private const val API_HOST = "ha.api.io.mi.com"
    private const val TOKEN_PATH = "/app/v2/ha/oauth/get_token"

    private const val K_UUID = "miot_uuid"
    private const val K_ACCESS = "miot_access"
    private const val K_REFRESH = "miot_refresh"
    private const val K_EXPIRES = "miot_expires"

    /** 换回来的三件套；expiresTs 是本地毫秒，已按 0.8 折过，提前续期 */
    data class Token(val access: String, val refresh: String, val expiresTs: Long)

    /** 设备身份，首次生成后永久存加密库，换身份等于换一个"客户端" */
    fun deviceId(): String {
        var u = Secret.get(K_UUID)
        if (u.isEmpty()) {
            u = UUID.randomUUID().toString()
            Secret.put(K_UUID, u)
        }
        return "ha.$u"
    } //ha.<uuid>

    fun state(): String {
        val d = "d=" + deviceId()
        val h = MessageDigest.getInstance("SHA-1").digest(d.toByteArray(Charsets.UTF_8))
        return MiPacket.bytes2hex(h)
    } //sha1(d=<设备身份>)

    /** 授权链接：redirect 决定 code 回到哪，client_id 没注入就返回空串（云端能力直接关闭） */
    fun authUrl(redirect: String = MiConf.redirect(), skipConfirm: Boolean = true): String {
        val cid = MiConf.clientId()
        if (cid.isEmpty()) return ""
        val q = listOf(
            "redirect_uri" to redirect,
            "client_id" to cid,
            "response_type" to "code",
            "device_id" to deviceId(),
            "state" to state(),
            "skip_confirm" to skipConfirm.toString()
        ).joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        return "$AUTH_URL?$q"
    } //拼授权页地址

    private fun host(region: String): String =
        if (region.isEmpty() || region == "cn") API_HOST else "$region.$API_HOST" //海外区多一段前缀

    private fun call(data: JSONObject, region: String): Token? {
        val conn = runCatching {
            (URL("https://${host(region)}$TOKEN_PATH?data=" +
                URLEncoder.encode(data.toString(), "UTF-8")).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "BaiAgent/${deviceId()}")
            }
        }.getOrNull() ?: return null
        try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "换 token 失败，HTTP ${conn.responseCode}")
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (root.optInt("code", -1) != 0) {
                Log.w(TAG, "换 token 被拒: ${root.optString("message")}")
                return null
            }
            val r = root.optJSONObject("result") ?: return null
            val access = r.optString("access_token")
            val refresh = r.optString("refresh_token")
            if (access.isEmpty() || refresh.isEmpty()) return null
            val ttl = r.optLong("expires_in", 0L)
            return Token(access, refresh, System.currentTimeMillis() + ttl * 800L)
        } catch (e: Exception) {
            Log.w(TAG, "换 token 异常: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    } //打一次 get_token，成功即落库

    fun login(code: String, redirect: String = MiConf.redirect(), region: String = MiConf.region()): Token? {
        val cid = MiConf.clientId()
        if (cid.isEmpty()) {
            Log.w(TAG, "没注入 client_id，换 token 无从谈起")
            return null
        }
        val data = JSONObject()
            .put("client_id", cid)
            .put("redirect_uri", redirect)
            .put("code", code)
            .put("device_id", deviceId())
        val t = call(data, region) ?: return null
        save(t)
        return t
    } //授权码换 token

    fun refresh(region: String = MiConf.region()): Token? {
        val cid = MiConf.clientId()
        if (cid.isEmpty()) return null
        val rt = Secret.get(K_REFRESH)
        if (rt.isEmpty()) return null
        val data = JSONObject()
            .put("client_id", cid)
            .put("redirect_uri", MiConf.redirect())
            .put("refresh_token", rt)
        val t = call(data, region) ?: return null
        save(t)
        return t
    } //用 refresh_token 续期

    /** 拿一个能用的 access_token：没过期直接给，过期先续，续不上返回 null（UI 要提示重登） */
    fun access(region: String = MiConf.region()): String? {
        val a = Secret.get(K_ACCESS)
        if (a.isEmpty()) return null
        val exp = Secret.get(K_EXPIRES).toLongOrNull() ?: 0L
        if (System.currentTimeMillis() < exp) return a
        return refresh(region)?.access
    } //自动续期

    fun save(t: Token) {
        Secret.put(K_ACCESS, t.access)
        Secret.put(K_REFRESH, t.refresh)
        Secret.put(K_EXPIRES, t.expiresTs.toString())
    } //落库（加密文件）

    fun logout() {
        Secret.put(K_ACCESS, "")
        Secret.put(K_REFRESH, "")
        Secret.put(K_EXPIRES, "")
    } //退出登录，只清 token 不动设备身份

    /** 登录态是否存在（不保证还能用） */
    fun logged(): Boolean = Secret.get(K_ACCESS).isNotEmpty() || Secret.get(K_REFRESH).isNotEmpty()

    /** 参数从哪来：MiConf（本地注入），源码里不留任何应用身份 */
    const val SOURCE = "miot_conf.json"
} //米家登录