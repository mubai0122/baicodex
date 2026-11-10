package b.ai.net.mi

import android.util.Log
import b.ai.native.miot.MiLanNative
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * 米家局域网直连（不走云）：UDP 54321 广播探测拿 IP，再按 did+token 加密对话。
 * 传输、报文编解码、探测包全在 C++/Rust（MiLanNative → core/feature/miot），这里只留设备模型与参数拼装。
 * 方法名与云 MIoT 一致：get_properties / set_properties / action，区别只是承载在加密 UDP 上。
 */
object MiLan {
    private const val TAG = "bai.mi.lan"
    const val PORT = 54321

    private val seq = AtomicInteger((System.currentTimeMillis() % 100000).toInt())

    /** 局域网里探到的一台设备；token 只能从云端或手动补，探测包本身不带 */
    class Dev(val did: Long, var ip: String, var tokenHex: String = "") {
        var model: String = ""
        var name: String = ""
        var online: Boolean = false
        //设备时钟和本机的秒级差，发报文时要把时间戳折回去
        var offset: Long = 0L

        fun key(): String = did.toString()
    } //一台设备
/** 扫一遍局域网：探测应答里有 did 与设备时间戳，够定位设备但仍需 token 才能说话 */
    fun discover(timeoutMs: Int = 2500): List<Dev> {
        val now = System.currentTimeMillis() / 1000
        val found = LinkedHashMap<Long, Dev>()
        for (line in MiLanNative.discover(timeoutMs)) {
            val part = line.split('|')
            if (part.size != 3) continue
            val did = part[0].toLongOrNull() ?: continue
            if (did <= 0) continue
            val ip = part[1]
            if (ip.isEmpty()) continue
            val stamp = part[2].toLongOrNull() ?: 0L
            val d = found.getOrPut(did) { Dev(did, ip) }
            d.ip = ip
            d.online = true
            d.offset = now - stamp
        }
        return found.values.toList()
    } //扫一遍局域网

    /** 发一条报文等一条回包；超时 / 校验失败 / id 不匹配都返回 null */
    fun call(dev: Dev, method: String, params: Any?, timeoutMs: Int = 4000): JSONObject? {
        if (dev.ip.isEmpty()) return null
        val id = seq.incrementAndGet()
        val msg = JSONObject().put("id", id).put("method", method)
        if (params != null) msg.put("params", params)
        val stamp = (System.currentTimeMillis() / 1000 - dev.offset).toInt()
        val r = MiLanNative.call(dev.ip, dev.did, dev.tokenHex, stamp, msg.toString(), timeoutMs)
        if (r == null) {
            Log.w(TAG, "没拿到回包: ${dev.did}")
            return null
        }
        //回包时间戳用来校正时钟差：设备对时间戳敏感，差太多会被当重放丢掉
        dev.offset = System.currentTimeMillis() / 1000 - r.first
        val obj = runCatching { JSONObject(r.second) }.getOrNull() ?: return null
        if (obj.optInt("id", -1) != id) return null
        return obj
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
        if (dev.ip.isEmpty()) return false
        val stamp = MiLanNative.alive(dev.ip, dev.did, timeoutMs)
        if (stamp <= 0L) return false
        dev.offset = System.currentTimeMillis() / 1000 - stamp
        dev.online = true
        return true
    } //单台探活
} //局域网直连