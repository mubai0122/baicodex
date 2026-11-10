package b.ai.net.mi

/**
 * 米家总入口：云端同步台账，控制时「局域网优先、云端兜底」。
 * 局域网快且不吃外网；token 缺失或不在同一网段时自动回落云 MIoT，用户无感。
 */
object MiHome {
    /** 同步设备清单：拉云端并进台账，返回一句给人看的结果 */
    fun sync(): String {
        if (!MiAuth.logged()) return "还没登录米家账号"
        val list = MiCloud.devices()
        if (list.isEmpty()) return "云端没拉到设备（登录态可能过期，或账号下确实没有设备）"
        MiStore.merge(list)
        return "同步到 ${list.size} 台设备"
    } //同步台账

    /** 扫局域网，把探到的 IP 回填台账；返回发现的台数 */
    fun lanScan(ms: Int = 2500): Int {
        val rows = MiStore.load()
        if (rows.isEmpty()) return 0
        val found = MiLan.discover(ms)
        if (found.isEmpty()) return 0
        val byDid = found.associateBy { it.did.toString() }
        var hit = 0
        val now = System.currentTimeMillis()
        for (r in rows) {
            val d = byDid[r.did] ?: continue
            r.ip = d.ip
            r.online = true
            r.lastSeen = now
            hit++
        }
        if (hit > 0) MiStore.save(rows)
        return hit
    } //局域网探测

    private fun lan(row: MiStore.Row): MiLan.Dev? {
        val tok = MiStore.token(row.did)
        if (tok.isEmpty()) return null
        val did = row.did.toLongOrNull() ?: return null
        if (row.ip.isEmpty()) return null
        return MiLan.Dev(did, row.ip, tok)
    } //把台账行转成局域网句柄，缺 token 或缺 IP 就返回 null

    /** 读一个属性：局域网先试（快），不通走云（慢但只要有网就行） */
    fun read(did: String, siid: Int, piid: Int): Any? {
        val row = MiStore.find(did)
        val d = row?.let { lan(it) }
        if (d != null) {
            val v = MiLan.getProp(d, siid, piid, 2500)
            if (v != null) return v
        }
        val arr = MiCloud.getProps(listOf(Triple(did, siid, piid))) ?: return null
        val o = arr.optJSONObject(0) ?: return null
        return if (o.has("value")) o.opt("value") else null
    } //读属性

    /** 写一个属性，返回 null = 成功 */
    fun write(did: String, siid: Int, piid: Int, value: Any): String? {
        val row = MiStore.find(did)
        val d = row?.let { lan(it) }
        if (d != null) {
            val err = MiLan.setProp(d, siid, piid, value, 2500)
            if (err == null) return null
        }
        return MiCloud.setProp(did, siid, piid, value)
    } //写属性

    fun action(did: String, siid: Int, aiid: Int, args: List<Any> = emptyList()): String? {
        val row = MiStore.find(did)
        val d = row?.let { lan(it) }
        if (d != null && MiLan.action(d, siid, aiid, args) != null) return null
        return if (MiCloud.action(did, siid, aiid, args) != null) null else "动作没执行成功（设备离线或被拒）"
    } //调动作

    /** 手动补一台设备：局域网模式没有云端也能用（IP + token 全靠手填） */
    fun addManual(did: String, ip: String, token: String, name: String = ""): String {
        val dl = did.toLongOrNull() ?: return "did 必须是数字"
        if (MiPacket.hex2bytes(token) == null) return "token 必须是 32 位十六进制"
        val rows = MiStore.load()
        val old = rows.firstOrNull { it.did == did }
        if (old != null) {
            old.ip = ip
            old.name = if (name.isNotEmpty()) name else old.name
        } else {
            rows.add(MiStore.Row(did, name.ifEmpty { "设备 $dl" }, "", "", ip, false, "", "", System.currentTimeMillis()))
        }
        MiStore.putToken(did, token)
        return if (MiStore.save(rows)) "" else "台账写不进磁盘"
    } //手工添加
} //米家总入口