package b.ai.net.mi

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * 米家局域网直连（不走云）：UDP 54321 广播探测拿 IP，再按 did+token 加密对话。
 * 方法名与云 MIoT 一致：get_properties / set_properties / action，区别只是承载在加密 UDP 上。
 */
object MiLan {
    private const val TAG = "bai.mi.lan"
    const val PORT = 54321

    private val seq = java.util.concurrent.atomic.AtomicInteger((System.currentTimeMillis() % 100000).toInt())

    /** 局域网里探到的一台设备；token 只能从云端或手动补，探测包本身不带 */
    class Dev(val did: Long, var ip: String, var tokenHex: String = "") {
        var model: String = ""
        var name: String = ""
        var online: Boolean = false
        //设备时钟和本机的秒级差，发报文时要把时间戳折回去
        var offset: Long = 0L

        fun key(): String = did.toString()
    } //一台设备

    private fun broadcastAddrs(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia: InterfaceAddress in ni.interfaceAddresses) {
                    val b = ia.broadcast ?: continue
                    if (!out.contains(b)) out.add(b)
                }
            }
        }
        return out
    } //全局广播 + 每张网卡的子网广播，个别 ROM 只吃后者

    /**
     * 广播探测：发 32 字节探测包，收满 timeout 的所有应答。
     * 应答前 32 字节里 did 在 4..12、设备时间戳在 12..16，够定位设备但仍需 token 才能说话。
     */
    fun discover(timeoutMs: Int = 2500, virtualDid: Long = System.currentTimeMillis()): List<Dev> {
        val found = LinkedHashMap<Long, Dev>()
        val probe = MiPacket.probe(virtualDid)
        DatagramSocket().use { s ->
            runCatching {
                s.broadcast = true
                s.soTimeout = 400
            }
            for (a in broadcastAddrs()) {
                runCatching { s.send(DatagramPacket(probe, probe.size, a, PORT)) }
            }
            val buf = ByteArray(1400)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "探测收包异常: ${e.message}")
                    break
                }
                //只认设备本体回的包，端口不对的是别的服务
                if (p.port != PORT || p.length < MiPacket.HEAD) continue
                val did = MiPacket.didOf(buf)
                if (did <= 0) continue
                val ip = runCatching { p.address?.hostAddress ?: "" }.getOrDefault("")
                if (ip.isEmpty()) continue
                val d = found.getOrPut(did) { Dev(did, ip) }
                d.ip = ip
                d.online = true
                d.offset = System.currentTimeMillis() / 1000 - MiPacket.stampOf(buf)
            }
        }
        return found.values.toList()
    } //扫一遍局域网

    /** 发一条报文等一条回包；超时 / 校验失败 / id 不匹配都返回 null */
    fun call(dev: Dev, method: String, params: Any?, timeoutMs: Int = 4000): JSONObject? {
        val token = MiPacket.hex2bytes(dev.tokenHex)
        if (token == null) {
            Log.w(TAG, "token 不是 32 位十六进制，拒绝发包: ${dev.did}")
            return null
        }
        val id = seq.incrementAndGet()
        val msg = JSONObject().put("id", id).put("method", method)
        if (params != null) msg.put("params", params)
        val stamp = (System.currentTimeMillis() / 1000 - dev.offset).toInt()
        val packet = MiPacket.pack(token, dev.did, stamp, msg.toString()) ?: return null
        DatagramSocket().use { s ->
            runCatching {
                s.broadcast = true
                s.soTimeout = timeoutMs
            }
            val dst = runCatching { InetAddress.getByName(dev.ip) }.getOrNull() ?: return null
            runCatching { s.send(DatagramPacket(packet, packet.size, dst, PORT)) }.onFailure {
                Log.w(TAG, "发包失败: ${it.message}")
                return null
            }
            val buf = ByteArray(1400)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val left = (deadline - System.currentTimeMillis()).toInt()
                s.soTimeout = left.coerceAtLeast(200)
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (e: SocketTimeoutException) {
                    return null
                } catch (e: Exception) {
                    Log.w(TAG, "收包异常: ${e.message}")
                    return null
                }
                //32 字节是探测应答 / 心跳，不含业务数据
                if (p.length <= MiPacket.HEAD) {
                    if (MiPacket.didOf(buf) == dev.did) dev.online = true
                    continue
                }
                if (MiPacket.didOf(buf) != dev.did) continue
                dev.offset = System.currentTimeMillis() / 1000 - MiPacket.stampOf(buf)
                val obj = MiPacket.unpack(token, buf.copyOf(p.length)) ?: continue
                if (obj.optInt("id", -1) != id) continue
                return obj
            }
        }
        return null
    } //一次请求-应答

    fun getProp(dev: Dev, siid: Int, piid: Int, timeoutMs: Int = 4000): Any? {
        val one = JSONObject().put("did", dev.did.toString()).put("siid", siid).put("piid", piid)
        val o = call(dev, "get_properties", JSONArray().put(one), timeoutMs) ?: return null
        val arr = o.optJSONArray("result") ?: return null
        val r = arr.optJSONObject(0) ?: return null
        if (r.optInt("code", -1) != 0) return null
        return if (r.has("value")) r.opt("value") else null
    } //读一个属性

    /** 写一个属性，返回 null = 成功，否则是给人看的原因 */
    fun setProp(dev: Dev, siid: Int, piid: Int, value: Any, timeoutMs: Int = 4000): String? {
        val one = JSONObject().put("did", dev.did.toString()).put("siid", siid).put("piid", piid).put("value", value)
        val o = call(dev, "set_properties", JSONArray().put(one), timeoutMs) ?: return "设备没回包（离线 / token 不对 / 不在同一网段）"
        val r = o.optJSONArray("result")?.optJSONObject(0) ?: o.optJSONObject("result") ?: return "回包格式不对"
        val code = r.optInt("code", -1)
        return if (code == 0) null else "设备拒绝，code=$code"
    } //写一个属性

    fun action(dev: Dev, siid: Int, aiid: Int, args: List<Any> = emptyList(), timeoutMs: Int = 4000): JSONObject? {
        val arr = JSONArray()
        args.forEach { arr.put(it) }
        val p = JSONObject().put("did", dev.did.toString()).put("siid", siid).put("aiid", aiid).put("in", arr)
        val o = call(dev, "action", p, timeoutMs) ?: return null
        return o.optJSONObject("result") ?: o
    } //调一个动作

    /** 探活：32 字节探测包打过去有应答就算在线 */
    fun alive(dev: Dev, timeoutMs: Int = 1200): Boolean {
        val probe = MiPacket.probe(System.currentTimeMillis())
        DatagramSocket().use { s ->
            runCatching {
                s.broadcast = true
                s.soTimeout = timeoutMs
            }
            val dst = runCatching { InetAddress.getByName(dev.ip) }.getOrNull() ?: return false
            runCatching { s.send(DatagramPacket(probe, probe.size, dst, PORT)) }
            val buf = ByteArray(1400)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                s.soTimeout = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(200)
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (e: Exception) {
                    return false
                }
                if (p.length < MiPacket.HEAD || MiPacket.didOf(buf) != dev.did) continue
                dev.offset = System.currentTimeMillis() / 1000 - MiPacket.stampOf(buf)
                return true
            }
        }
        return false
    } //单台探活
} //局域网直连