package b.ai.net.mi

import b.ai.BuildConfig
import b.ai.core.cfg.io.Store
import b.ai.core.cfg.secret.Secret
import org.json.JSONObject

/**
 * 米家接入参数：client_id / 回调地址 / 区域 / 局域网优先 / 超时。
 * 取值优先级：本机加密库 → 老明文配置（迁移）→ 构建期注入的种子（local.properties 的 miot.*）。
 * 种子只在加密库为空时写一次，之后以用户填的为准，换 APK 不会把它冲掉。
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
        var cid = Secret.get(K_CID).trim()
        //老版本把 client_id 写在明文 JSON 里，读到就搬进加密库并从 JSON 抹掉
        if (cid.isEmpty()) {
            val legacy = o?.optString("clientId")?.trim().orEmpty()
            if (legacy.isNotEmpty()) {
                cid = legacy
                Secret.put(K_CID, cid)
                o?.remove("clientId")
                o?.let { Store.write(FILE, it.toString()) }
            }
        }
        //构建期注入的种子：只在加密库空着时灌一次，之后以加密库为准
        if (cid.isEmpty()) {
            val seed = BuildConfig.MIOT_CLIENT_ID.trim()
            if (seed.isNotEmpty()) {
                cid = seed
                Secret.put(K_CID, cid)
            }
        }
        val c = Cfg(
            clientId = cid,
            redirect = o?.optString("redirect")?.trim().orEmpty()
                .ifEmpty { BuildConfig.MIOT_REDIRECT.trim() }
                .ifEmpty { DEF_REDIRECT },
            region = o?.optString("region")?.trim().orEmpty()
                .ifEmpty { BuildConfig.MIOT_REGION.trim() }
                .ifEmpty { DEF_REGION },
            lanFirst = o?.optBoolean("lanFirst", true) ?: true,
            timeoutMs = o?.optInt("timeoutMs", 4000) ?: 4000
        )
        cache = c
        return c
    } //读配置（内存缓存一份，改完要 save 才会刷新）

    /** 写配置：空串 = 成功，否则是不接受的原因（client_id 与回填地址先过自检） */
    fun save(c: Cfg): String {
        if (c.clientId.isNotBlank()) {
            val why = check(c.clientId, c.redirect, c.region)
            if (why.isNotEmpty()) return why
        }
        Secret.put(K_CID, c.clientId.trim())
        val o = JSONObject()
            .put("redirect", c.redirect.trim())
            .put("region", c.region.trim().ifEmpty { DEF_REGION })
            .put("lanFirst", c.lanFirst)
            .put("timeoutMs", c.timeoutMs)
        if (!Store.write(FILE, o.toString())) return "配置写不进磁盘"
        cache = null
        return ""
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