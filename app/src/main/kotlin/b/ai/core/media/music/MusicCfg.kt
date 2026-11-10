package b.ai.core.media.music

import b.ai.core.cfg.io.Paths
import b.ai.core.cfg.io.Store
import b.ai.core.cfg.secret.Secret
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 音乐平台配置：启用、优先级、音质档位、缓存目录。
 * 登录态（cookie）只进加密库，源文件与明文配置里都不留任何账号信息；
 * 播放器还没接，先把「谁优先、什么音质、缓存放哪、登录没登录」这四件事落到实处。
 */
object MusicCfg {
    private const val FILE = "music.json"

    /** 平台清单：id → 给人看的名字；顺序是默认优先级 */
    val PLATS = linkedMapOf(
        "netease" to "网易云音乐",
        "qq" to "QQ 音乐",
        "kuwo" to "酷我音乐"
    )

    /** 音质档位：各家内部 code 不一样，这里只存统一的档位名，切平台时由平台自己映射 */
    val QUALITIES = listOf("标准 128k", "高品 192k", "极高 320k", "无损")

    /** 一套音乐配置；order 里出现的平台才有优先级，没出现的排在后面 */
    class Cfg(val order: List<String>, val on: Map<String, Boolean>, val quality: String)

    fun load(): Cfg {
        val raw = Store.read(FILE)
        val o = if (raw.isNullOrBlank()) null else runCatching { JSONObject(raw) }.getOrNull()
        val arr = o?.optJSONArray("order")
        val order = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val id = arr.optString(i).trim()
                if (id.isEmpty() || id !in PLATS || id in order) continue
                order.add(id)
            }
        }
        PLATS.keys.forEach { if (it !in order) order.add(it) }
        val onObj = o?.optJSONObject("on")
        val on = LinkedHashMap<String, Boolean>()
        PLATS.keys.forEach { id ->
            //默认只开网易云：一次只让一个平台说话，免得同一条命令三家一起抢
            on[id] = onObj?.optBoolean(id) ?: (id == "netease")
        }
        val q = o?.optString("quality")?.trim().orEmpty()
        return Cfg(order, on, if (q in QUALITIES) q else QUALITIES[2])
    } //读配置

    /** 写配置：空串 = 成功，否则是给人看的原因 */
    fun save(c: Cfg): String {
        val arr = JSONArray()
        c.order.filter { it in PLATS }.forEach { arr.put(it) }
        if (arr.length() == 0) return "至少要留一个平台"
        val onObj = JSONObject()
        c.on.forEach { (k, v) -> onObj.put(k, v) }
        val o = JSONObject()
            .put("order", arr)
            .put("on", onObj)
            .put("quality", if (c.quality in QUALITIES) c.quality else QUALITIES[2])
        return if (Store.write(FILE, o.toString())) "" else "配置写不进磁盘"
    } //写配置

    /** 唯一打开着的平台：没有开任何平台返回空串 */
    fun active(): String = load().let { c -> c.order.firstOrNull { c.on[it] == true } ?: "" }

    /** 把某个平台往上挪一位；已经在最前面就原样返回 */
    fun moveUp(id: String): String {
        val c = load()
        val o = ArrayList(c.order)
        val i = o.indexOf(id)
        if (i <= 0) return ""
        val t = o[i - 1]
        o[i - 1] = id
        o[i] = t
        return save(Cfg(o, c.on, c.quality))
    } //上移优先级

    private fun key(id: String) = "music_cookie_" + id

    fun cookie(id: String): String = if (id in PLATS) Secret.get(key(id)).trim() else ""

    /** 存 cookie：空串等于清除；只留一段，粘多了也只取第一行 */
    fun setCookie(id: String, value: String): String {
        if (id !in PLATS) return "未知平台：$id"
        val v = value.trim().lineSequence().firstOrNull().orEmpty().trim()
        if (v.isEmpty()) return "cookie 是空的"
        if (v.length < 12) return "cookie 太短，至少要有 MUSIC_U / key 这类凭据"
        Secret.put(key(id), v)
        return ""
    } //写 cookie

    fun clearCookie(id: String) {
        if (id !in PLATS) return
        Secret.put(key(id), "")
    } //清 cookie

    /** 缓存目录：baicode/music，播放器下来的音频与封面都放这里 */
    fun cacheDir(): File = Paths.dir("music")

    /** 缓存占用字节数：递归累加，拿不到权限就返回 0 */
    fun cacheSize(): Long {
        val root = cacheDir()
        var sum = 0L
        root.walkTopDown().forEach { if (it.isFile) sum += runCatching { it.length() }.getOrDefault(0L) }
        return sum
    } //缓存占用

    /** 清缓存：只删 music 目录下的文件与空目录，返回清掉的字节数 */
    fun clearCache(): Long {
        val root = cacheDir()
        var freed = 0L
        root.walkTopDown().sortedByDescending { it.path.length }.forEach { f ->
            if (f == root) return@forEach
            if (f.isFile) {
                val n = runCatching { f.length() }.getOrDefault(0L)
                if (runCatching { f.delete() }.getOrDefault(false)) freed += n
            } else {
                runCatching { f.delete() }
            }
        }
        return freed
    } //清缓存

    /** 人类可读的体积：1024 进制，给界面与工具回显用 */
    fun sizeText(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${bytes} B" else String.format("%.1f %s", v, units[i])
    } //体积文案
} //音乐平台配置