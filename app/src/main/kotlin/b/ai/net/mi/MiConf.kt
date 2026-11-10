package b.ai.net.mi

import b.ai.core.cfg.io.Store
import b.ai.core.cfg.secret.Secret
import org.json.JSONObject

/**
 * 米家接入参数：client_id / 回调地址 / 区域 / 局域网优先 / 超时。
 * client_id 是本应用的接入身份，只存本机加密库，由本地注入（设置页填或构建时写），
 * 源码与明文配置里都不出现；其余非敏感参数落 miot_conf.json。
 */
object MiConf {
    private const val FILE = "miot_conf.json"
    private const val K_CID = "miot_client_id"

    /** 一套接入参数；clientId 为空就是没注入，云端一切免谈 */
    class Cfg(
        val clientId: String,
        val redirect: String,
        val region: String,
        val lanFirst: Boolean,
        val timeoutMs: Int
    )

    @Volatile
    private var cache: Cfg? = null

    fun load(): Cfg {
        cache?.let { return it }
        val raw = Store.read(FILE)
        val o = if (raw.isNullOrBlank()) null else runCatching { JSONObject(raw) }.getOrNull()
        //老版本把 client_id 写在明文 JSON 里，读到就搬进加密库并从 JSON 抹掉
        val legacy = o?.optString("clientId")?.trim().orEmpty()
        var cid = Secret.get(K_CID).trim()
        if (cid.isEmpty() && legacy.isNotEmpty()) {
            Secret.put(K_CID, legacy)
            cid = legacy
            o?.remove("clientId")
            o?.let { Store.write(FILE, it.toString()) }
        }
        val c = Cfg(
            clientId = cid,
            redirect = o?.optString("redirect")?.trim().orEmpty().ifEmpty { DEF_REDIRECT },
            region = o?.optString("region")?.trim().orEmpty().ifEmpty { DEF_REGION },
            lanFirst = o?.optBoolean("lanFirst", true) ?: true,
            timeoutMs = o?.optInt("timeoutMs", 4000) ?: 4000
        )
        cache = c
        return c
    } //读配置（内存缓存一份，改完要 save 才会刷新）

    fun save(c: Cfg): Boolean {
        Secret.put(K_CID, c.clientId.trim())
        val o = JSONObject()
            .put("redirect", c.redirect.trim())
            .put("region", c.region.trim().ifEmpty { DEF_REGION })
            .put("lanFirst", c.lanFirst)
            .put("timeoutMs", c.timeoutMs)
        val ok = Store.write(FILE, o.toString())
        if (ok) cache = null
        return ok
    } //写配置：client_id 进加密库，其余进 JSON

    fun region(): String = load().region

    fun clientId(): String = load().clientId

    fun redirect(): String = load().redirect

    fun timeout(): Int = load().timeoutMs.coerceIn(1000, 20000)

    /** 云端能不能用：只看 client_id 有没有注入 */
    fun ready(): Boolean = clientId().isNotEmpty()

    /** 校验一组参数，空串 = 通过，否则是给人看的原因 */
    fun check(clientId: String, redirect: String, region: String): String {
        val id = clientId.trim()
        if (id.isEmpty()) return "client_id 没填：需要你自己申请的应用身份，不能借用别人的"
        if (id.length !in 6..32 || !id.all { it.isDigit() }) return "client_id 只应该是 6~32 位数字"
        if (!redirect.startsWith("http://") && !redirect.startsWith("https://")) return "回调地址必须以 http:// 或 https:// 开头"
        val r = region.trim()
        if (r.isEmpty()) return "区域没填，国内填 cn"
        if (r.length > 10 || !r.all { it.isLowerCase() || it.isDigit() }) return "区域只能是 cn / de / i2 / ru / sg / us 这类小写短码"
        return ""
    } //参数自检

    const val DEF_REGION = "cn"
    const val DEF_REDIRECT = "https://localhost/miot/auth"
} //米家接入参数