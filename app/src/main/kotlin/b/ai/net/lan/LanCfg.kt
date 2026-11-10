package b.ai.net.lan

import android.os.Build
import b.ai.core.cfg.io.Store
import b.ai.core.cfg.secret.Secret
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

//局域网互联的配置与台账：开关、本机名称、配对码、端口与并发、已配对设备
object LanCfg {
    private const val FILE = "lan.json"
    private const val K_ID = "lan_self_id"
    private const val K_CODE = "lan_pair_code"

    /** 一台已配对或刚扫到的设备；a2aPort 是对方协作端口，0 表示它没开协作 */
    class Peer(
        val id: String,
        var name: String,
        var ip: String,
        var port: Int,
        var lastSeen: Long,
        var a2aPort: Int = 0
    )

    class Cfg(val on: Boolean, val name: String, val port: Int, val conc: Int, val code: String)

    fun selfId(): String {
        val old = Secret.get(K_ID)
        if (old.isNotEmpty()) return old
        val hex = "0123456789abcdef"
        val id = buildString { repeat(12) { append(hex[SecureRandom().nextInt(16)]) } }
        Secret.put(K_ID, id)
        return id
    } //本机设备号：一次性生成，配对与去重都靠它

    private fun defName(): String {
        val m = Build.MODEL.orEmpty().trim()
        return if (m.isEmpty()) "Android 设备" else m
    } //默认拿机型名，用户可改

    fun code(): String {
        val old = Secret.get(K_CODE)
        if (old.length == 6) return old
        return regenCode()
    } //6 位配对码，存加密库

    fun regenCode(): String {
        val code = String.format("%06d", SecureRandom().nextInt(1_000_000))
        Secret.put(K_CODE, code)
        return code
    } //换一个配对码，旧码立刻失效

    fun applySeedCode(code: String): Boolean {
        if (code.length != 6 || !code.all { it.isDigit() }) return false
        if (Secret.get(K_CODE).isNotEmpty()) return false
        Secret.put(K_CODE, code)
        return true
    } //注入固定配对码：只在还没设过时写，注入值不覆盖用户换过的码

    fun applySeedPort(p: Int): Boolean {
        if (p !in 1024..65535) return false
        if (obj().has("port")) return false
        val c = load()
        return save(Cfg(c.on, c.name, p, c.conc, c.code)).isEmpty()
    } //注入发现端口：配置里没写过 port 才落盘

    private fun obj(): JSONObject =
        runCatching { JSONObject(Store.read(FILE).orEmpty().ifBlank { "{}" }) }.getOrNull() ?: JSONObject()

    fun load(): Cfg {
        val o = obj()
        val port = o.optInt("port", 8388)
        return Cfg(
            on = o.optBoolean("on", false),
            name = o.optString("name").ifBlank { defName() },
            port = if (port in 1024..65535) port else 8388,
            conc = o.optInt("conc", 3).coerceIn(1, 8),
            code = code()
        )
    } //读配置

    fun save(c: Cfg): String {
        val o = obj()
        o.put("on", c.on)
        o.put("name", c.name.trim().ifBlank { defName() })
        o.put("port", c.port.coerceIn(1024, 65535))
        o.put("conc", c.conc.coerceIn(1, 8))
        return if (Store.write(FILE, o.toString())) "" else "配置写不进磁盘"
    } //写配置：空串是成功

    fun peers(): MutableList<Peer> {
        val arr = runCatching { JSONArray(obj().optJSONArray("peers").toString()) }.getOrNull() ?: return ArrayList()
        val out = ArrayList<Peer>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            out.add(
                Peer(
                    id = id,
                    name = o.optString("name"),
                    ip = o.optString("ip"),
                    port = o.optInt("port", 8388),
                    lastSeen = o.optLong("lastSeen", 0L),
                    a2aPort = o.optInt("a2a", 0)
                )
            )
        }
        return out
    } //已配对设备

    fun savePeers(list: List<Peer>): Boolean {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("ip", p.ip)
                    .put("port", p.port).put("lastSeen", p.lastSeen).put("a2a", p.a2aPort)
            )
        }
        val o = obj()
        o.put("peers", arr)
        return Store.write(FILE, o.toString())
    } //写台账

    private fun tokKey(id: String) = "lan_tok_$id"

    fun peerToken(id: String): String = Secret.get(tokKey(id))

    fun putPeerToken(id: String, token: String) {
        Secret.put(tokKey(id), token.trim())
    } //对端协作 token：只在配对时换一次，之后每次派活都带它

    fun addPeer(p: Peer) {
        val list = peers()
        val old = list.firstOrNull { it.id == p.id }
        if (old == null) {
            list.add(p)
        } else {
            if (p.name.isNotEmpty()) old.name = p.name
            if (p.ip.isNotEmpty()) old.ip = p.ip
            old.port = p.port
            old.a2aPort = p.a2aPort
            old.lastSeen = p.lastSeen
        }
        savePeers(list)
    } //配对成功就落台账，同一台设备重复配对只更新

    fun removePeer(id: String) {
        savePeers(peers().filter { it.id != id })
        Secret.put(tokKey(id), "")
    } //解除配对
} //局域网配置