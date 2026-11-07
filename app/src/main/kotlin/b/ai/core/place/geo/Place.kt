package b.ai.core.place.geo
import b.ai.core.cfg.app.Bai
import b.ai.core.cfg.app.Cfg
import b.ai.native.Vault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.HandlerThread
import b.ai.App
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 位置注入：取坐标 + 坐标转地名。
 * 逆地理固定走 BigDataCloud，失败依次退系统 Geocoder、Nominatim；地名结果只以密文存进 Vault（PLACE 桶）。
 */
object Place {
    private const val REC = "geo_rows"          //Vault 里那一条记录的键
    private const val TTL = 12 * 3600_000L      //同一个坐标 12 小时内不重查
    private const val MAX = 20                  //缓存最多留 20 条，超出按时间淘汰
    private val UA: String get() = "Baicodex/" + b.ai.BuildConfig.VERSION_NAME + " (Android; +" + Bai.host + ")"
    private const val CUR_MS = 6000L            //现取一次定位最多等 6 秒
    private const val NET_MS = 5000             //单个逆地理接口超时
    private const val FRESH_MS = 120_000L       //现取的定位只要 2 分钟内就算新鲜

    /** 现取到的定位：fresh 表示是这次主动定的，不是历史最后已知 */
    private class Fix(val loc: Location, val fresh: Boolean)

    fun hasCoarse(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFine(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 拼一条给模型看的位置：有地名就是「地名（坐标，精度）」，拿不到地名只给坐标，绝不编 */
    fun describe(c: Context): String {
        if (!hasCoarse(c)) return "位置 未授权（缺定位权限）"
        val fix = locate(c) ?: return "位置 暂无（还没定位过）"
        val lat = fix.loc.latitude
        val lon = fix.loc.longitude
        val pos = String.format(Locale.US, "%.5f,%.5f", lat, lon)
        val acc = fix.loc.accuracy.toInt().coerceAtLeast(0)
        val g = if (Cfg.placeGeo) name(lat, lon) else ""
        val tail = "（" + pos + "，精度约 " + acc + " 米" + (if (fix.fresh) "，现取" else "") + "）"
        return if (g.isBlank()) "位置 " + pos + tail else "位置 " + g + tail
    } //拼位置串

    /** 取定位：开了精确注入且给了精确权限就现取一次，取不到退最后已知 */
    private fun locate(c: Context): Fix? {
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provs = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        if (Cfg.injectPrecise && hasFine(c)) {
            for (p in provs.dropLast(1)) {
                val got = runCatching { current(lm, p) }.getOrNull() ?: continue
                if (System.currentTimeMillis() - got.time < FRESH_MS) return Fix(got, true)
            }
        }
        val last = provs.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return Fix(last, false)
    } //取定位

    /** 单次现取：API 30 走 getCurrentLocation，低版本用一次 requestLocationUpdates，都限时 */
    private fun current(lm: LocationManager, p: String): Location? {
        if (!runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) return null
        val latch = CountDownLatch(1)
        val out = java.util.concurrent.atomic.AtomicReference<Location?>(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sig = CancellationSignal()
            val ex = Executors.newSingleThreadExecutor()
            runCatching {
                lm.getCurrentLocation(p, sig, ex) { l ->
                    out.set(l)
                    latch.countDown()
                }
            }.onFailure { latch.countDown() }
            latch.await(CUR_MS, TimeUnit.MILLISECONDS)
            runCatching { sig.cancel() }
            runCatching { ex.shutdownNow() }
        } else {
            val th = HandlerThread("place-cur")
            th.start()
            val li = object : LocationListener {
                override fun onLocationChanged(l: Location) {
                    out.set(l)
                    latch.countDown()
                }

                override fun onProviderDisabled(provider: String) {
                    latch.countDown()
                }

                override fun onProviderEnabled(provider: String) = Unit

                @Deprecated("低版本接口，仅做兜底")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            runCatching { lm.requestLocationUpdates(p, 0L, 0f, li, th.looper) }.onFailure { latch.countDown() }
            latch.await(CUR_MS, TimeUnit.MILLISECONDS)
            runCatching { lm.removeUpdates(li) }
            runCatching { th.quitSafely() }
        }
        return out.get()
    } //现取一次定位

    /** 坐标转地名：先查加密缓存，再依次试 Nominatim → BigDataCloud → 系统 Geocoder，每家失败重试一次 */
    private fun name(lat: Double, lon: Double): String {
        val k = key(lat, lon)
        val hit = load()[k]
        if (hit != null && hit.second > System.currentTimeMillis() - TTL && hit.first.isNotBlank()) return hit.first
        val got = osm(lat, lon).ifBlank { bigData(lat, lon) }.ifBlank { sysGeo(lat, lon) }
        if (got.isNotBlank()) save(k, got)
        return got
    } //坐标转地名

    private fun bigData(lat: Double, lon: Double): String {
        val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat +
            "&longitude=" + lon + "&localityLanguage=zh"
        repeat(2) {
            val raw = httpGet(url)
            if (raw.isBlank()) return@repeat
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return@repeat
            //localityInfo.administrative 是从大到小的完整行政链，末尾几级能到街道，比 city/locality 细
            val adm = o.optJSONObject("localityInfo")?.optJSONArray("administrative")
            val chain = ArrayList<String>(4)
            if (adm != null) {
                for (i in 0 until adm.length()) {
                    val nm = adm.optJSONObject(i)?.optString("name").orEmpty()
                    if (nm.isNotBlank()) chain.add(nm)
                }
            }
            if (chain.isEmpty()) {
                chain.add(o.optString("principalSubdivision"))
                chain.add(o.optString("city"))
                chain.add(o.optString("locality"))
            }
            val s = join(chain.takeLast(4))
            if (s.isNotBlank()) return s
        }
        return ""
    } //BigDataCloud

    /** 系统 Geocoder：没 GMS 的设备 isPresent 为 false，直接跳过 */
    @Suppress("DEPRECATION")
    private fun sysGeo(lat: Double, lon: Double): String = runCatching {
        if (!Geocoder.isPresent()) return ""
        val list = Geocoder(App.self, Locale.CHINA).getFromLocation(lat, lon, 1).orEmpty()
        val a = list.firstOrNull() ?: return ""
        join(
            listOf(
                a.adminArea.orEmpty(),
                a.subAdminArea.orEmpty(),
                a.locality.orEmpty(),
                a.subLocality.orEmpty(),
                a.thoroughfare.orEmpty()
            )
        )
    }.getOrDefault("") //系统 Geocoder

    private fun osm(lat: Double, lon: Double): String {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=" + lat +
            "&lon=" + lon + "&accept-language=zh-CN&zoom=18&addressdetails=1"
        repeat(2) { n ->
            if (n > 0) Thread.sleep(1200) //Nominatim 限 1 req/s，重试前必须先等一下
            val raw = httpGet(url)
            if (raw.isBlank()) return@repeat
            val dn = runCatching { JSONObject(raw).optString("display_name") }.getOrDefault("")
            if (dn.isBlank()) return@repeat
            //display_name 是小到大，倒过来拼才像中文地址；邮编、国名、片区代号、带门牌的 POI 直接丢掉
            val parts = dn.split(",").map { it.trim() }
                .filter { it.isNotEmpty() && it.none { ch -> ch in '0'..'9' } }
                .filter { it != "中国" && !it.contains("功能区") && !it.contains("核心区") }
            val s = join(parts.reversed())
            if (s.isNotBlank()) return s
        }
        return ""
    } //Nominatim

    /** 普通 GET：Nominatim 要求 UA 标识应用，所以不走 Net 的默认 UA */
    private fun httpGet(url: String): String = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = NET_MS
        c.readTimeout = NET_MS
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        c.setRequestProperty("Accept-Encoding", "identity")
        try {
            if (c.responseCode !in 200..299) return ""
            c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }.getOrDefault("") //普通 GET

    /** 各级行政名去重后拼成一句；太长就不再加下一级，避免半截地名 */
    private fun join(parts: List<String>): String {
        val sb = StringBuilder()
        for (raw in parts) {
            val p = raw.trim()
            if (p.isEmpty() || p == "中国" || p == "中华人民共和国") continue
            if (sb.contains(p)) continue
            if (sb.length + p.length > 32) break
            sb.append(p)
        }
        return sb.toString()
    } //拼地名

    /** 缓存键：坐标收到 3 位小数，约 110 米内共用一个地名 */
    private fun key(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.3f,%.3f", lat, lon) //缓存键

    /** 读缓存：Vault 里的密文解出来是一段 JSON，key -> 地名 + 时间戳 */
    private fun load(): Map<String, Pair<String, Long>> {
        val raw = Vault.get(REC, Vault.PLACE)
        if (raw.isBlank()) return emptyMap()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
        val m = HashMap<String, Pair<String, Long>>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val k = o.optString("k")
            val t = o.optString("t")
            if (k.isBlank() || t.isBlank()) continue
            m[k] = t to o.optLong("ts")
        }
        return m
    } //读缓存

    /** 写缓存：整表加密落盘，只留最近 MAX 条 */
    private fun save(k: String, t: String) {
        val m = HashMap(load())
        m[k] = t to System.currentTimeMillis()
        val rows = m.entries.sortedByDescending { it.value.second }.take(MAX)
        val arr = JSONArray()
        rows.forEach { e ->
            arr.put(
                JSONObject()
                    .put("k", e.key)
                    .put("t", e.value.first)
                    .put("ts", e.value.second)
            )
        }
        runCatching { Vault.put(REC, arr.toString(), Vault.PLACE) }
    } //写缓存
} //位置注入