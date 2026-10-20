package b.ai.net

import b.ai.core.Stations
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 一趟车：trainNo 是 12306 内部车次编号，查经停站要用它 */
data class Train(
    val code: String,
    val trainNo: String,
    val fromCode: String,
    val toCode: String,
    val fromName: String,
    val toName: String,
    val start: String,
    val arrive: String,
    val duration: String,
    val seats: List<Pair<String, String>>,
    val canBuy: Boolean
)

/**
 * 12306 余票查询。
 * 协议：GET /otn/leftTicket/init 拿 cookie 与「当前查询路径」（官方会不定期把 query 改成 queryG），
 * 再 GET /otn/{path} 拿 result[]；经停走 /otn/czxx/queryByTrainNo。
 */
object Rail {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val BASE = "https://kyfw.12306.cn"
    private const val INIT = "$BASE/otn/leftTicket/init"

    private val cookies = LinkedHashMap<String, String>()
    private var queryPath = "otn/leftTicket/queryG"
    private var warmedAt = 0L

    /** 席别列下标 → 名称（对齐官方前端 queryLeftTicket_end_js.js 的 cN() 解析表）；无票的不显示 */
    private val SEAT_COLS = listOf(
        21 to "高级软卧", 23 to "软卧", 24 to "软座", 25 to "特等座", 26 to "无座",
        28 to "硬卧", 29 to "硬座", 30 to "二等座", 31 to "一等座", 32 to "商务座", 33 to "动卧"
    )

    /** 工具入口：action 决定查什么 */
    fun run(
        action: String,
        from: String,
        to: String,
        date: String,
        middle: String?,
        trainType: String?,
        sort: String?,
        limit: Int,
        trainNo: String?
    ): String = when (action.lowercase(Locale.ROOT)) {
        "route" -> route(date, trainNo.orEmpty(), from, to)
        "transfer" -> transfer(date, from, to, middle, trainType.orEmpty(), limit)
        else -> tickets(date, from, to, trainType.orEmpty(), sort.orEmpty(), limit)
    } //按 action 分发

    private fun tickets(date: String, from: String, to: String, types: String, sort: String, limit: Int): String {
        val d = normDate(date) ?: return "日期格式不对：$date（要 yyyy-MM-dd，且只能查未来 15 天内）"
        val f = Stations.resolve(from).firstOrNull() ?: return "找不到出发站：$from"
        val t = Stations.resolve(to).firstOrNull() ?: return "找不到到达站：$to"
        val all = runCatching { query(d, f.code, t.code) }.getOrElse { return "查询失败：${it.message}" }
        if (all.isEmpty()) return "$d ${f.name} → ${t.name}：没查到车次（可能超出发售期，或站名不对）"
        val filtered = if (types.isBlank()) all else all.filter { types.uppercase().contains(it.code.first()) }
        val sorted = when (sort) {
            "arrive" -> filtered.sortedBy { it.arrive }
            "duration" -> filtered.sortedBy { min(it.duration) }
            else -> filtered.sortedBy { it.start }
        }
        val n = limit.coerceIn(1, 40)
        val sb = StringBuilder()
        sb.append(d).append(" ").append(f.name).append(" → ").append(t.name)
        sb.append("：共 ").append(all.size).append(" 趟")
        if (types.isNotBlank()) sb.append("，筛 ").append(types.uppercase()).append(" 后 ").append(filtered.size).append(" 趟")
        sb.append("，显示 ").append(minOf(n, sorted.size)).append(" 趟\n")
        sorted.take(n).forEachIndexed { i, tr -> sb.append(line(i + 1, tr)).append("\n") }
        sb.append("\n要查某趟经停站，用 action=route + 对应 train_no（上面 no= 后面的值）。")
        return sb.toString().trim()
    } //余票查询

    private fun transfer(
        date: String,
        from: String,
        to: String,
        middle: String?,
        types: String,
        limit: Int
    ): String {
        val d = normDate(date) ?: return "日期格式不对：$date（要 yyyy-MM-dd）"
        val f = Stations.resolve(from).firstOrNull() ?: return "找不到出发站：$from"
        val t = Stations.resolve(to).firstOrNull() ?: return "找不到到达站：$to"
        if (middle.isNullOrBlank()) {
            return "中转要指定中间城市（middle，例如「南京」「武汉」「郑州」）。" +
                "不确定走哪中转时，先 action=tickets 查直达，再按线路挑一个大站。"
        }
        val m = Stations.resolve(middle).firstOrNull() ?: return "找不到中转站：$middle"
        val leg1 = runCatching { query(d, f.code, m.code) }.getOrElse { return "第一段查询失败：${it.message}" }
        val leg2 = runCatching { query(d, m.code, t.code) }.getOrElse { return "第二段查询失败：${it.message}" }
        if (leg1.isEmpty() || leg2.isEmpty()) {
            return "$d ${f.name} → ${m.name} → ${t.name}：某一段没有车次（${leg1.size} / ${leg2.size}），换个中转城市试试"
        }
        val a = if (types.isBlank()) leg1 else leg1.filter { types.uppercase().contains(it.code.first()) }
        val b = if (types.isBlank()) leg2 else leg2.filter { types.uppercase().contains(it.code.first()) }
        data class Pair2(val one: Train, val two: Train, val gap: Int, val total: Int)

        val combos = ArrayList<Pair2>()
        a.filter { it.canBuy }.forEach { one ->
            val dep1 = min(one.start)
            val arr1 = min(one.arrive)
            if (dep1 < 0 || arr1 < 0) return@forEach
            //第一段自己跨夜时（20:06 发、次日 00:37 到），到达时间要顺延一天再算间隔
            val arrReal = if (arr1 < dep1) arr1 + 1440 else arr1
            b.filter { it.canBuy }.forEach { two ->
                val dep2 = min(two.start)
                val dur2 = min(two.duration)
                if (dep2 < 0 || dur2 < 0) return@forEach
                var depReal = dep2
                while (depReal < arrReal) depReal += 1440
                val gap = depReal - arrReal
                val total = depReal + dur2 - dep1
                //间隔 30 分钟以上、6 小时以内才算靠谱换乘
                if (gap in 30..360) combos.add(Pair2(one, two, gap, total))
            }
        }
        if (combos.isEmpty()) {
            return "$d ${f.name} → ${m.name} → ${t.name}：没有衔接≥30分钟的有票组合（${a.size} × ${b.size} 趟里）"
        }
        val n = limit.coerceIn(1, 20)
        val sb = StringBuilder()
        sb.append(d).append(" ").append(f.name).append(" → ").append(t.name)
        sb.append(" 中转（经 ").append(m.name).append("）：有票组合 ").append(combos.size).append(" 组，按总耗时排前 ")
        sb.append(minOf(n, combos.size)).append("\n")
        combos.sortedBy { it.total }.take(n).forEachIndexed { i, c ->
            sb.append(i + 1).append(") ").append(c.one.code).append(" ")
            sb.append(c.one.fromName).append(c.one.start).append("→").append(c.one.toName).append(c.one.arrive)
            sb.append("（").append(c.one.duration).append("）｜")
            sb.append(c.two.code).append(" ")
            sb.append(c.two.fromName).append(c.two.start).append("→").append(c.two.toName).append(c.two.arrive)
            sb.append("（").append(c.two.duration).append("）｜")
            sb.append("衔接 ").append(fmtGap(c.gap))
            sb.append("｜总 ").append(fmtGap(c.total)).append("\n")
            sb.append("   席别：").append(seatText(c.one.seats)).append(" ／ ").append(seatText(c.two.seats)).append("\n")
            sb.append("   no=").append(c.one.trainNo).append(" , ").append(c.two.trainNo).append("\n")
        }
        return sb.toString().trim()
    } //中转：两段各自查完再按衔接时间组合

    private fun route(date: String, trainNo: String, from: String, to: String): String {
        val d = normDate(date) ?: return "日期格式不对：$date（要 yyyy-MM-dd）"
        if (trainNo.isBlank()) {
            return "查经停站要 train_no（形如 650000K52806），不是车次号 K528。" +
                "先用 action=tickets 查一次，把结果里 no= 后面的值传进来。"
        }
        val f = Stations.resolve(from).firstOrNull() ?: return "找不到出发站：$from"
        val t = Stations.resolve(to).firstOrNull() ?: return "找不到到达站：$to"
        val path = "otn/czxx/queryByTrainNo?train_no=$trainNo" +
            "&from_station_telecode=${f.code}&to_station_telecode=${t.code}&depart_date=$d"
        val text = runCatching { get(path, INIT) }.getOrElse { return "查询失败：${it.message}" }
        val o = runCatching { JSONObject(text) }.getOrElse { return "响应不是 JSON：${text.take(200)}" }
        val arr = o.optJSONObject("data")?.optJSONArray("data") ?: return "没查到经停站（train_no 或日期不对）"
        if (arr.length() == 0) return "没查到经停站（train_no 或日期不对）"
        val sb = StringBuilder()
        sb.append("$d $trainNo 经停（${f.name} → ${t.name}）：\n")
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            sb.append(s.optString("station_no")).append(" ").append(s.optString("station_name"))
            val a = s.optString("arrive_time")
            val b = s.optString("start_time")
            if (a.isNotBlank() && a != "----") sb.append(" 到 ").append(a)
            if (b.isNotBlank() && b != "----") sb.append(" 发 ").append(b)
            sb.append("\n")
        }
        return sb.toString().trim()
    } //经停站

    private fun line(i: Int, t: Train): String {
        val sb = StringBuilder()
        sb.append(i).append(". ").append(t.code).append(" ").append(t.start).append("→").append(t.arrive)
        sb.append(" ").append(t.duration)
        sb.append(" ").append(t.fromName).append("→").append(t.toName)
        val s = seatText(t.seats)
        if (s.isNotBlank()) sb.append(" | ").append(s)
        if (!t.canBuy) sb.append(" | 不可预订")
        sb.append(" | no=").append(t.trainNo)
        return sb.toString()
    } //一行车次

    private fun seatText(seats: List<Pair<String, String>>): String =
        seats.joinToString(" ") { (n, v) -> "$n:$v" } //席别余票

    private fun fmtGap(m: Int): String {
        if (m <= 0) return "0分"
        val h = m / 60
        val mm = m % 60
        return if (h > 0) "${h}小时${mm}分" else "${mm}分"
    } //分钟数转文字

    /** "HH:MM" → 当天分钟数，非法返回 -1 */
    private fun min(hhmm: String): Int {
        val p = hhmm.split(":")
        if (p.size != 2) return -1
        val h = p[0].toIntOrNull() ?: return -1
        val m = p[1].toIntOrNull() ?: return -1
        if (h !in 0..23 || m !in 0..59) return -1
        return h * 60 + m
    } //解析时刻

    private fun normDate(raw: String): String? {
        val s = raw.trim().ifBlank { return null }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        fmt.isLenient = false
        val date = runCatching { fmt.parse(s) }.getOrNull() ?: return null
        val cal = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < today.timeInMillis) return null
        val limitMs = today.timeInMillis + 15L * 24 * 3600 * 1000
        if (cal.timeInMillis > limitMs) return null
        return fmt.format(date)
    } //校验日期：今天起 15 天内

    /** 拉 cookie 与当前查询路径，5 分钟内复用 */
    private fun warm(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && cookies.isNotEmpty() && now - warmedAt < 5 * 60_000L) return
        val html = get("otn/leftTicket/init", null)
        Regex("CLeftTicketUrl\\s*=\\s*'([^']+)'").find(html)?.groupValues?.get(1)?.let {
            //官方给的是相对 /otn/ 的路径（leftTicket/queryG），少了 otn 前缀会 404
            val p = it.removePrefix("/")
            queryPath = if (p.startsWith("otn/")) p else "otn/$p"
        }
        if (cookies.isEmpty()) throw IOException("12306 初始化失败：没拿到会话 cookie")
        warmedAt = now
    } //预热会话

    private fun query(date: String, fromCode: String, toCode: String): List<Train> {
        val o = rawQuery(date, fromCode, toCode) ?: return emptyList()
        val data = o.optJSONObject("data") ?: return emptyList()
        val map = data.optJSONObject("map") ?: JSONObject()
        val arr = data.optJSONArray("result") ?: return emptyList()
        val out = ArrayList<Train>(arr.length())
        for (i in 0 until arr.length()) {
            val f = arr.optString(i).split("|")
            if (f.size < 36) continue
            //12306 把字段值做了 URL 编码（"无" 是 %E6%97%A0、"有" 是 %E6%9C%89），不解码会原样显示成 %XX；
            //只解用到的字段，yp_info 那类 base64 里的 '+' 不能被 URLDecoder 变成空格
            val d = f.toMutableList()
            (listOf(3, 8, 9, 10, 11) + SEAT_COLS.map { it.first }).forEach { idx ->
                if (idx in d.indices) d[idx] = dec(d[idx])
            }
            out.add(
                Train(
                    code = d[3],
                    trainNo = d[2],
                    fromCode = d[6],
                    toCode = d[7],
                    fromName = map.optString(d[6], d[6]),
                    toName = map.optString(d[7], d[7]),
                    start = d[8],
                    arrive = d[9],
                    duration = d[10],
                    seats = parseSeats(d),
                    canBuy = d[11] == "Y"
                )
            )
        }
        //有行却一条都解析不出（字段数变了 / 协议又改），不能再报「没查到车次」
        //——那是把协议失效说成没票，会让上层得出错误结论
        if (arr.length() > 0 && out.isEmpty()) {
            throw IOException("12306 返回了 ${arr.length()} 条余票行，但字段数不足 36、解析不出（协议可能又变了）；这不代表没有车次")
        }
        return out
    } //解析 result 行

    /** 会话过期（302 + {"c_url":...}）就清 cookie 重来一次，最多两次 */
    private fun rawQuery(date: String, fromCode: String, toCode: String): JSONObject? {
        for (attempt in 0..1) {
            warm(attempt > 0)
            val path = "$queryPath?leftTicketDTO.train_date=$date" +
                "&leftTicketDTO.from_station=$fromCode" +
                "&leftTicketDTO.to_station=$toCode&purpose_codes=ADULT"
            val body = runCatching { get(path, INIT) }.getOrNull()
            if (body != null) {
                val o = runCatching { JSONObject(body) }.getOrNull()
                if (o != null && o.optInt("httpstatus") == 200 && o.optJSONObject("data") != null) return o
                //404 / HTML 页面：查询路径或校验变了，必须报出来，不能静默退化成「没查到车次」
                if (o == null) {
                    throw IOException("12306 查询失败：HTTP $lastStatus，响应不是 JSON（查询路径 $queryPath 可能失效）")
                }
                if (!o.optBoolean("status", true) && o.has("c_url")) {
                    //官方换了查询路径或会话失效：清 cookie 重新预热
                    cookies.clear()
                    warmedAt = 0L
                }
            } else if (attempt == 1) {
                throw IOException("12306 请求失败：两次都没拿到响应（网络或连接被拒）")
            }
        }
        return null
    } //带重试的原始查询

    /** 字段值 URL 解码：失败就原样返回，别因为一个坏字段把整条车次丢掉 */
    private fun dec(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun parseSeats(f: List<String>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        SEAT_COLS.forEach { (idx, name) ->
            val v = f.getOrNull(idx)?.trim().orEmpty()
            //"无"／"--" 表示这一趟不卖或无票，直接不列，省得刷一屏噪声
            if (v.isBlank() || v == "无" || v == "--") return@forEach
            out.add(name to if (v == "*") "无票" else v)
        }
        return out
    } //取席别余票

    @Volatile private var lastStatus = 0

    /** GET：手工维护 cookie（HttpURLConnection 不会自己存），302 的 body 也要读出来判错 */
    private fun get(path: String, referer: String?): String {
        val url = if (path.startsWith("http")) path else "$BASE/$path"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "*/*")
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        if (referer != null) c.setRequestProperty("Referer", referer)
        if (cookies.isNotEmpty()) {
            c.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        return try {
            lastStatus = c.responseCode
            grabCookies(c)
            readBody(c)
        } finally {
            c.disconnect()
        }
    } //GET 并收集 Set-Cookie

    private fun readBody(c: HttpURLConnection): String {
        val s = runCatching { c.inputStream }.getOrNull()
            ?: runCatching { c.errorStream }.getOrNull()
            ?: return ""
        return s.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } //读响应正文

    private fun grabCookies(c: HttpURLConnection) {
        val values = c.headerFields.entries
            .firstOrNull { it.key?.equals("Set-Cookie", true) == true }?.value
            ?: return
        values.forEach { raw ->
            val pair = raw.substringBefore(";").trim()
            val k = pair.substringBefore("=").trim()
            val v = pair.substringAfter("=", "").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) cookies[k] = v
        }
    } //收 cookie
} //12306 余票客户端