package b.ai.net.music

import android.util.Base64
import android.util.Log
import b.ai.core.cfg.seed.Seeds
import b.ai.core.media.music.MusicCfg
import b.ai.native.security.MiCrypto
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

//网易云登录只走 music.163.com 自己的 weapi 通道：明文 /api/ 会被服务端当成旧客户端，登录接口直接拒
object NeteaseLogin {
    private const val TAG = "bai.music.login"
    private const val BASE = "https://music.163.com"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    //weapi 的固定 key、IV 与 RSA 参数一律从种子注入读，源码里不留这几个值
    private val iv: String = Seeds.neteaseIv()
    private val preset: String = Seeds.neteasePreset()
    private val rand = SecureRandom()
    private val exponent: BigInteger by lazy { BigInteger(Seeds.neteaseRsaE(), 16) }
    private val modulus: BigInteger by lazy { BigInteger(Seeds.neteaseRsaN(), 16) }

    private class Resp(val code: Int, val text: String, val cookies: List<String>); //一次应答

    private fun cookieOf(): String = MusicCfg.cookie("netease")

    private fun csrfOf(): String =
        pairsOf(cookieOf()).firstOrNull { it.first == "__csrf" }?.second.orEmpty() //cookie 里的 csrf

    private fun pairsOf(raw: String): List<Pair<String, String>> {
        var text = raw.trim()
        if (text.startsWith("Cookie:", ignoreCase = true)) text = text.substring(7).trim()
        if (text.isEmpty()) return emptyList()
        //只粘了 MUSIC_U 的值也算一种写法
        if (!text.contains('=')) return listOf("MUSIC_U" to text)
        val out = ArrayList<Pair<String, String>>()
        text.split(';', '\n', ',').forEach { part ->
            val chunk = part.trim()
            val i = chunk.indexOf('=')
            if (i <= 0) return@forEach
            val k = chunk.substring(0, i).trim()
            val v = chunk.substring(i + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty() && k.matches(Regex("^[A-Za-z0-9_\\-]+$"))) out.add(k to v)
        }
        return out
    } //把 cookie 行拆成键值对

    private fun postForm(path: String, form: String): Resp? {
        val c = runCatching {
            (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", "$BASE/")
                setRequestProperty("Origin", BASE)
                val ck = cookieOf()
                if (ck.isNotEmpty()) setRequestProperty("Cookie", ck)
                outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            }
        }.getOrNull() ?: return null
        return try {
            val code = c.responseCode
            val text = runCatching {
                (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val cookies = runCatching { c.headerFields["Set-Cookie"].orEmpty() }.getOrDefault(emptyList())
            c.disconnect()
            Resp(code, text, cookies)
        } catch (e: Exception) {
            Log.w(TAG, "请求失败: ${e.message}")
            runCatching { c.disconnect() }
            null
        }
    } //表单 POST，顺手把 Set-Cookie 带回来

    private fun aes(text: String, key: String): String? {
        val out = MiCrypto.aes128Cbc(
            key.toByteArray(Charsets.UTF_8), iv.toByteArray(Charsets.UTF_8),
            text.toByteArray(Charsets.UTF_8), true
        ) ?: return null
        return Base64.encodeToString(out, Base64.NO_WRAP)
    } //AES-128-CBC 一次，key 与 IV 都是 16 字节

    private fun encrypt(json: String): Pair<String, String>? {
        val secret = buildString(16) { repeat(16) { append(BASE62[rand.nextInt(BASE62.length)]) } }
        val inner = aes(json, preset) ?: return null
        val params = aes(inner, secret) ?: return null
        val reversed = secret.reversed().toByteArray(Charsets.UTF_8)
        val encSecKey = BigInteger(1, reversed).modPow(exponent, modulus).toString(16).padStart(256, '0')
        return params to encSecKey
    } //两次 AES 加一次模幂：固定 key 加一遍，随机 key 再加一遍，随机 key 反过来做 RSA

    private fun mergeCookies(lines: List<String>) {
        if (lines.isEmpty()) return
        val cur = LinkedHashMap<String, String>()
        pairsOf(cookieOf()).forEach { (k, v) -> cur[k] = v }
        var dirty = false
        lines.forEach { line ->
            val first = line.substringBefore(';').trim()
            val i = first.indexOf('=')
            if (i <= 0) return@forEach
            val k = first.substring(0, i).trim()
            val v = first.substring(i + 1).trim()
            if (k.isEmpty() || v.isEmpty() || !k.matches(Regex("^[A-Za-z0-9_\\-]+$"))) return@forEach
            cur[k] = v
            dirty = true
        }
        if (!dirty) return
        MusicCfg.setCookie("netease", cur.entries.joinToString("; ") { "${it.key}=${it.value}" })
    } //回包里的 cookie 并进加密库：登录成功那一刻 MUSIC_U 就落在这里

    private fun weapi(path: String, params: JSONObject = JSONObject()): JSONObject? {
        val payload = JSONObject(params.toString())
        val csrf = csrfOf()
        if (csrf.isNotEmpty()) payload.put("csrf_token", csrf)
        val enc = encrypt(payload.toString()) ?: return null
        val body = "params=" + URLEncoder.encode(enc.first, "UTF-8") +
            "&encSecKey=" + URLEncoder.encode(enc.second, "UTF-8")
        val r = postForm(path, body) ?: return null
        mergeCookies(r.cookies)
        if (r.text.isBlank()) return null
        return runCatching { JSONObject(r.text) }.getOrNull()
    } //统一入口：登录、账号、歌单全走这条

    private fun errText(code: Int, msg: String): String = when (code) {
        400 -> "手机号格式不对"
        501 -> "这个手机号还没注册网易云"
        502, 503 -> "验证码错误，请重新输入"
        504 -> "验证码已过期，请重新获取"
        508, 509 -> "操作太频繁，歇一会儿再试"
        250 -> "触发了安全验证，过几分钟再试，或改用粘贴 cookie"
        else -> msg.ifBlank { "登录失败（$code）" }
    } //错误码翻成人话

    fun me(): String {
        val j = weapi("/weapi/nuser/account/get") ?: return "网络异常，稍后再试"
        val p = j.optJSONObject("profile") ?: return "未登录（cookie 无效或已过期）"
        val uid = p.optLong("userId")
        if (uid <= 0L) return "未登录（cookie 无效或已过期）"
        val nick = p.optString("nickname").ifBlank { "网易云用户" }
        return nick
    } //当前登录账号的昵称，失败时返回给人看的原因

    fun sendCaptcha(phone: String, area: String = "86"): String {
        val p = phone.trim()
        if (p.length < 6) return "手机号太短"
        val j = weapi(
            "/weapi/sms/captcha/sent",
            JSONObject().put("cellphone", p).put("ctcode", area.trim().ifEmpty { "86" })
        ) ?: return "网络异常，稍后再试"
        val code = j.optInt("code", -1)
        if (code == 200) return ""
        return errText(code, j.optString("message").ifEmpty { j.optString("msg") })
    } //发短信验证码：空串是成功

    fun loginByCaptcha(phone: String, captcha: String, area: String = "86"): String {
        val p = phone.trim()
        val c = captcha.trim()
        if (p.isEmpty() || c.isEmpty()) return "手机号和验证码都要填"
        val j = weapi(
            "/weapi/login/cellphone",
            JSONObject()
                .put("phone", p)
                .put("countrycode", area.trim().ifEmpty { "86" })
                .put("captcha", c)
                .put("rememberLogin", "true")
        ) ?: return "网络异常，稍后再试"
        val code = j.optInt("code", -1)
        if (code != 200) return errText(code, j.optString("message").ifEmpty { j.optString("msg") })
        //登录接口偶尔先回 200 再异步落 cookie，补一次账号确认
        val name = me()
        if (name.startsWith("未登录") || name.startsWith("网络")) return "登录已通过，但账号信息没取到，重试一次"
        return "登录成功：$name"
    } //手机号 + 验证码登录，成功后 cookie 已在加密库

    fun loginByCookie(raw: String): String {
        val pairs = pairsOf(raw)
        if (pairs.isEmpty()) return "没有识别到有效的 cookie"
        val text = pairs.joinToString("; ") { "${it.first}=${it.second}" }
        if (pairs.none { it.first == "MUSIC_U" }) return "cookie 里没有 MUSIC_U，请重新复制完整 cookie"
        val err = MusicCfg.setCookie("netease", text)
        if (err.isNotEmpty()) return err
        val name = me()
        if (name.startsWith("未登录") || name.startsWith("网络")) {
            MusicCfg.clearCookie("netease")
            return "cookie 无效或已过期，请重新获取"
        }
        return "登录成功：$name"
    } //粘贴 cookie 兜底：先校验能取到账号，取不到就把刚写进去的清掉

    fun logout() {
        MusicCfg.clearCookie("netease")
    } //退出登录
} //网易云登录