package b.ai.net.mi

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 米家云 MIoT：拿家庭与设备清单（含每台设备的局域网 token）、读写属性、调动作。
 * 头里 X-Client-BizId 固定 haapi，Authorization 由 "Bearer" 与 token 直接拼接（中间没有空格，
 * 加了空格会被判无效）。
 */
object MiCloud {
    private const val TAG = "bai.mi.cloud"
    private const val API_HOST = "ha.api.io.mi.com"

    /** 云端看到的一台设备；token 是局域网通信的钥匙，isOnline 是云端判的在线 */
    class Dev(
        val did: String,
        val name: String,
        val model: String,
        val urn: String,
        val token: String,
        val online: Boolean,
        val ip: String,
        val homeId: String,
        val roomName: String
    ) {
        fun didLong(): Long = did.toLongOrNull() ?: -1L
    } //一台设备

    /** 一个家庭；share = true 表示是别人分享过来的 */
    class Home(val id: String, val name: String, val share: Boolean)

    private fun host(region: String): String =
        if (region.isEmpty() || region == "cn") API_HOST else "$region.$API_HOST" //海外区多一段前缀

    private fun conn(path: String, method: String, body: String?, region: String): HttpURLConnection? {
        val token = MiAuth.access(region)
        if (token == null) {
            Log.w(TAG, "没有可用 access_token，先登录")
            return null
        }
        val h = host(region)
        val c = runCatching {
            (URL("https://$h$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Host", h)
                setRequestProperty("X-Client-BizId", "haapi")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer$token")
                setRequestProperty("X-Client-AppId", MiConf.clientId())
                setRequestProperty("User-Agent", "BaiAgent/" + MiAuth.deviceId())
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
        }.getOrNull()
        return c
    } //建连接并塞头

    private fun exchange(path: String, method: String, body: String?, region: String): JSONObject? {
        var c = conn(path, method, body, region) ?: return null
        var code = runCatching { c.responseCode }.getOrDefault(-1)
        //401 说明 token 过期：续一次再重放，续不上就彻底失败
        if (code == 401) {
            runCatching { c.disconnect() }
            if (MiAuth.refresh(region) == null) return null
            c = conn(path, method, body, region) ?: return null
            code = runCatching { c.responseCode }.getOrDefault(-1)
        }
        try {
            if (code != 200) {
                Log.w(TAG, "$path HTTP $code")
                return null
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            if (root.optInt("code", -1) != 0) {
                Log.w(TAG, "$path 业务失败 ${root.optInt("code")}: ${root.optString("message")}")
                return null
            }
            return root
        } catch (e: Exception) {
            Log.w(TAG, "$path 异常: ${e.message}")
            return null
        } finally {
            runCatching { c.disconnect() }
        }
    } //打一次云接口，自动续期一次

    fun homes(region: String = "cn"): List<Home> {
        val body = JSONObject()
            .put("limit", 150)
            .put("fetch_share", true)
            .put("fetch_share_dev", true)
            .put("plat_form", 0)
            .put("app_ver", 9)
            .toString()
        val root = exchange("/app/v2/homeroom/gethome", "POST", body, region) ?: return emptyList()
        val r = root.optJSONObject("result") ?: return emptyList()
        val out = ArrayList<Home>()
        val own = r.optJSONArray("homelist")
        if (own != null) for (i in 0 until own.length()) {
            val o = own.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            out.add(Home(id, o.optString("name", "未命名家庭"), false))
        }
        val share = r.optJSONArray("share_home_list")
        if (share != null) for (i in 0 until share.length()) {
            val o = share.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            out.add(Home(id, o.optString("name", "共享家庭"), true))
        }
        return out
    } //家庭列表

    /** 拉设备清单：dids 传空等于拉全部，超过 150 台要按 next_start_did 翻页，这里一次翻到底 */
    fun devices(region: String = "cn"): List<Dev> {
        val out = ArrayList<Dev>()
        var startDid: String? = null
        var guard = 0
        while (guard++ < 20) {
            val req = JSONObject()
                .put("limit", 200)
                .put("get_split_device", true)
                .put("get_third_device", true)
                .put("dids", JSONArray())
            if (startDid != null) req.put("start_did", startDid)
            val root = exchange("/app/v2/home/device_list_page", "POST", req.toString(), region) ?: break
            val r = root.optJSONObject("result") ?: break
            val list = r.optJSONArray("list") ?: break
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val did = o.optString("did")
                val name = o.optString("name")
                val model = o.optString("model")
                val urn = o.optString("spec_type")
                if (did.isEmpty() || name.isEmpty() || model.isEmpty() || urn.isEmpty()) continue
                //路由器的 miwifi.* 有 spec 但没实现，收进来只会是死条目
                if (did.startsWith("miwifi.")) continue
                out.add(
                    Dev(
                        did = did,
                        name = name,
                        model = model,
                        urn = urn,
                        token = o.optString("token"),
                        online = o.optBoolean("isOnline", false),
                        ip = o.optString("local_ip"),
                        homeId = o.optString("home_id"),
                        roomName = o.optString("room_name")
                    )
                )
            }
            val next = r.optString("next_start_did")
            if (!r.optBoolean("has_more", false) || next.isEmpty()) break
            startDid = next
        }
        return out
    } //设备清单

    fun getProps(items: List<Triple<String, Int, Int>>, region: String = "cn"): JSONArray? {
        val arr = JSONArray()
        for ((did, siid, piid) in items) {
            arr.put(JSONObject().put("did", did).put("siid", siid).put("piid", piid))
        }
        val body = JSONObject().put("datasource", 1).put("params", arr).toString()
        return exchange("/app/v2/miotspec/prop/get", "POST", body, region)?.optJSONArray("result")
    } //批量读属性

    /** 写一个属性，返回 null = 云说成功 */
    fun setProp(did: String, siid: Int, piid: Int, value: Any, region: String = "cn"): String? {
        val p = JSONObject().put("did", did).put("siid", siid).put("piid", piid).put("value", value)
        val body = JSONObject().put("params", JSONArray().put(p)).toString()
        val root = exchange("/app/v2/miotspec/prop/set", "POST", body, region)
            ?: return "云端没受理（网络 / 登录态 / 设备离线）"
        return if (root.optInt("code", -1) == 0) null else "云端拒绝，code=${root.optInt("code")}"
    } //写属性

    fun action(did: String, siid: Int, aiid: Int, args: List<Any> = emptyList(), region: String = "cn"): JSONObject? {
        val inArr = JSONArray()
        args.forEach { inArr.put(it) }
        val p = JSONObject().put("did", did).put("siid", siid).put("aiid", aiid).put("in", inArr)
        val body = JSONObject().put("params", p).toString()
        return exchange("/app/v2/miotspec/action", "POST", body, region)?.optJSONObject("result")
    } //调动作
} //米家云