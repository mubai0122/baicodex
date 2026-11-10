package b.ai.net.lan

import android.util.Log
import b.ai.core.cfg.app.Cfg
import b.ai.native.security.MiCrypto
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

//局域网互联：UDP 广播发现 +配对码握手 +探活；报文全是「前缀 + JSON」，只认自己发的那三种前缀
object Lan {
    private const val TAG = "bai.lan"
    private const val VER = 1

    @Volatile
    private var running = false
    private var srv: DatagramSocket? = null
    private var th: Thread? = null

    fun isRunning(): Boolean = running

    private fun hello(withTok: Boolean = false): String {
        val c = LanCfg.load()
        val o = JSONObject()
            .put("v", VER)
            .put("id", LanCfg.selfId())
            .put("name", c.name)
            .put("port", c.port)
            .put("a2a", if (Cfg.a2aOn) Cfg.a2aPort else 0)
        //协作 token 等于派活权限，只跟着配对握手走一次，绝不进广播包
        if (withTok && Cfg.a2aOn && Cfg.a2aToken.isNotEmpty()) o.put("tok", Cfg.a2aToken)
        return o.toString()
    } //本机名片：端口与协作端口公开，token 只给配对成功的那一方

    fun start(): String {
        if (running) return ""
        val c = LanCfg.load()
        if (!c.on) return "发现开关是关着的，先打开"
        val s = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(c.port))
            }
        }.getOrNull() ?: return "端口 ${c.port} 绑不上（可能被别的应用占着）"
        srv = s
        running = true
        th = Thread({ loop(s) }, "lan-serve").apply { isDaemon = true; start() }
        return ""
    } //开始监听：起成功后别的设备才扫得到本机

    fun stop() {
        running = false
        runCatching { srv?.close() }
        srv = null
        th = null
    } //停止监听

    private fun loop(s: DatagramSocket) {
        s.soTimeout = 800
        val buf = ByteArray(2048)
        while (running && !s.isClosed) {
            val p = DatagramPacket(buf, buf.size)
            if (runCatching { s.receive(p) }.isFailure) continue
            val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
            val reply = when {
                text.startsWith("DISCOVER/1") -> "HELLO/1 " + hello()
                text.startsWith("PAIR/1 ") -> pairIn(text.substring(7), p)
                text.startsWith("PING/1") -> "PONG/1"
                else -> null
            }
            if (reply != null) {
                runCatching {
                    val out = reply.toByteArray(Charsets.UTF_8)
                    s.send(DatagramPacket(out, out.size, p.address, p.port))
                }.onFailure { Log.w(TAG, "回包失败: ${it.message}") }
            }
        }
    } //收包循环：非本协议的包一律不回，免得被当成探测工具

    private fun sha1(s: String): String = MiCrypto.sha1Hex(s.toByteArray(Charsets.UTF_8))

    private fun pairIn(raw: String, from: DatagramPacket): String {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return "NO/1"
        val id = o.optString("id")
        if (id.isEmpty() || id == LanCfg.selfId()) return "NO/1"
        //对方算的是「我给的码 + 我的设备号 + 它的设备号」；我这边用同样的三段自己算一遍
        val key = sha1(LanCfg.load().code + "|" + LanCfg.selfId() + "|" + id)
        if (o.optString("key") != key) {
            Log.w(TAG, "配对请求的摘要对不上：$id")
            return "NO/1"
        }
        LanCfg.addPeer(
            LanCfg.Peer(
                id = id,
                name = o.optString("name"),
                ip = from.address?.hostAddress.orEmpty(),
                port = o.optInt("port", 8388),
                lastSeen = System.currentTimeMillis(),
                a2aPort = o.optInt("a2a", 0)
            )
        )
        val tok = o.optString("tok")
        if (tok.isNotEmpty()) LanCfg.putPeerToken(id, tok)
        return "OK/1 " + hello(true)
    } //配对校验：配对码本身不上网，只上行一次摘要

    fun scan(ms: Int = 2000): List<LanCfg.Peer> {
        val c = LanCfg.load()
        val s = runCatching { DatagramSocket().apply { broadcast = true; soTimeout = 500 } }.getOrNull()
            ?: return emptyList()
        val found = LinkedHashMap<String, LanCfg.Peer>()
        val deadline = System.currentTimeMillis() + ms.coerceIn(500, 10_000)
        val msg = "DISCOVER/1".toByteArray(Charsets.UTF_8)
        try {
            broadcastAddrs().forEach { addr ->
                runCatching { s.send(DatagramPacket(msg, msg.size, addr, c.port)) }
            }
            while (System.currentTimeMillis() < deadline) {
                val buf = ByteArray(2048)
                val p = DatagramPacket(buf, buf.size)
                if (runCatching { s.receive(p) }.isFailure) continue
                val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
                if (!text.startsWith("HELLO/1 ")) continue
                val o = runCatching { JSONObject(text.substring(8)) }.getOrNull() ?: continue
                val id = o.optString("id")
                if (id.isEmpty() || id == LanCfg.selfId()) continue
                found[id] = LanCfg.Peer(
                    id = id,
                    name = o.optString("name"),
                    ip = p.address?.hostAddress.orEmpty(),
                    port = o.optInt("port", c.port),
                    lastSeen = System.currentTimeMillis(),
                    a2aPort = o.optInt("a2a", 0)
                )
            }
        } finally {
            runCatching { s.close() }
        }
        return found.values.toList()
    } //扫一遍：广播一次，然后在限定时间里收 HELLO

    private fun broadcastAddrs(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.interfaceAddresses.forEach { ia -> ia.broadcast?.let { out.add(it) } }
            }
        }
        return out.distinctBy { it.hostAddress }
    } //全局广播 + 每张网卡的子网广播：个别 ROM 只吃后者

    fun pair(target: LanCfg.Peer, code: String): String {
        val me = LanCfg.selfId()
        val c = LanCfg.load()
        val key = sha1(code.trim() + "|" + target.id + "|" + me)
        val o = JSONObject()
            .put("v", VER).put("id", me).put("name", c.name)
            .put("port", c.port).put("key", key)
            .put("a2a", if (Cfg.a2aOn) Cfg.a2aPort else 0)
        //顺便把本机协作凭据递过去：对面配对通过后就能直接派活过来
        if (Cfg.a2aOn && Cfg.a2aToken.isNotEmpty()) o.put("tok", Cfg.a2aToken)
        val s = runCatching { DatagramSocket().apply { soTimeout = 2500 } }.getOrNull() ?: return "本机拿不到端口"
        val msg = ("PAIR/1 " + o.toString()).toByteArray(Charsets.UTF_8)
        val sent = runCatching {
            s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(target.ip), target.port))
        }
        if (sent.isFailure) {
            runCatching { s.close() }
            return "发不出去：${target.ip} 可能不在同一网段"
        }
        val buf = ByteArray(1024)
        val p = DatagramPacket(buf, buf.size)
        val got = runCatching { s.receive(p) }.getOrNull()
        runCatching { s.close() }
        if (got == null) return "对方没回应（码不对、或对面没开发现开关）"
        val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
        if (!text.startsWith("OK/1")) return "配对码不对，或对方拒绝"
        val back = runCatching { JSONObject(text.substring(5).trim()) }.getOrNull()
        target.lastSeen = System.currentTimeMillis()
        target.a2aPort = back?.optInt("a2a", 0) ?: 0
        val tok = back?.optString("tok").orEmpty()
        if (tok.isNotEmpty()) LanCfg.putPeerToken(target.id, tok)
        LanCfg.addPeer(target)
        return ""
    } //配对：对端校验通过才落台账，空串是成功

    fun ping(peer: LanCfg.Peer): Boolean {
        val s = runCatching { DatagramSocket().apply { soTimeout = 1200 } }.getOrNull() ?: return false
        val msg = ("PING/1 " + LanCfg.selfId()).toByteArray(Charsets.UTF_8)
        val ok = runCatching {
            s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(peer.ip), peer.port))
            val buf = ByteArray(256)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            String(p.data, 0, p.length, Charsets.UTF_8).startsWith("PONG")
        }.getOrDefault(false)
        runCatching { s.close() }
        if (ok) {
            peer.lastSeen = System.currentTimeMillis()
            LanCfg.addPeer(peer)
        }
        return ok
    } //探活：收到 PONG 才算在线，顺手刷新 lastSeen
} //局域网互联