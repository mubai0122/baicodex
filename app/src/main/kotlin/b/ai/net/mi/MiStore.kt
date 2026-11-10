package b.ai.net.mi

import b.ai.core.cfg.io.Store
import b.ai.core.cfg.secret.Secret
import org.json.JSONArray
import org.json.JSONObject

/**
 * 米家设备台账：非敏感字段落 miot.json（可读可改），设备 token 落加密库单独存。
 * token 是能直接开灯的钥匙，写进明文 JSON 等于把家门钥匙贴在门上。
 */
object MiStore {
    private const val FILE = "miot.json"

    /** 一台设备在台账里的样子 */
    class Row(
        val did: String,
        var name: String,
        var model: String,
        var urn: String,
        var ip: String,
        var online: Boolean,
        var homeId: String,
        var roomName: String,
        var lastSeen: Long
    )

    private fun tokKey(did: String) = "miot_tok_$did" //设备 token 的加密库键名

    fun token(did: String): String = Secret.get(tokKey(did))

    fun putToken(did: String, token: String) {
        if (did.isEmpty() || token.isEmpty()) return
        Secret.put(tokKey(did), token)
    } //存 token

    fun load(): MutableList<Row> {
        val raw = Store.read(FILE) ?: return ArrayList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return ArrayList()
        val out = ArrayList<Row>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val did = o.optString("did")
            if (did.isEmpty()) continue
            out.add(
                Row(
                    did = did,
                    name = o.optString("name"),
                    model = o.optString("model"),
                    urn = o.optString("urn"),
                    ip = o.optString("ip"),
                    online = o.optBoolean("online", false),
                    homeId = o.optString("homeId"),
                    roomName = o.optString("roomName"),
                    lastSeen = o.optLong("lastSeen", 0L)
                )
            )
        }
        return out
    } //读台账

    fun save(rows: List<Row>): Boolean {
        val arr = JSONArray()
        for (r in rows) {
            arr.put(
                JSONObject()
                    .put("did", r.did)
                    .put("name", r.name)
                    .put("model", r.model)
                    .put("urn", r.urn)
                    .put("ip", r.ip)
                    .put("online", r.online)
                    .put("homeId", r.homeId)
                    .put("roomName", r.roomName)
                    .put("lastSeen", r.lastSeen)
            )
        }
        return Store.write(FILE, arr.toString())
    } //写台账（不含 token）

    /** 云端清单并进台账：已有的更新名字/型号/IP/token，没有的补一条，云端没了的保留（本地还能连） */
    fun merge(cloud: List<MiCloud.Dev>): MutableList<Row> {
        val rows = load()
        val byDid = LinkedHashMap<String, Row>()
        rows.forEach { byDid[it.did] = it }
        val now = System.currentTimeMillis()
        for (d in cloud) {
            val old = byDid[d.did]
            if (old == null) {
                val r = Row(d.did, d.name, d.model, d.urn, d.ip, d.online, d.homeId, d.roomName, now)
                rows.add(r)
                byDid[d.did] = r
            } else {
                old.name = d.name
                old.model = d.model
                old.urn = d.urn
                if (d.ip.isNotEmpty()) old.ip = d.ip
                old.online = d.online
                old.homeId = d.homeId
                old.roomName = d.roomName
                old.lastSeen = now
            }
            if (d.token.isNotEmpty()) putToken(d.did, d.token)
        }
        save(rows)
        return rows
    } //并库

    fun find(did: String): Row? = load().firstOrNull { it.did == did }

    fun clear() {
        val rows = load()
        rows.forEach { Secret.put(tokKey(it.did), "") }
        Store.write(FILE, "[]")
    } //清空台账与 token
} //设备台账