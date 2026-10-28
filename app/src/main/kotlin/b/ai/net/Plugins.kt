package b.ai.net
import b.ai.core.Cfg
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 插件广场：浏览 / 详情 / 我提交的 / 验证码 / 上传 / 下载。
 * 上传与下载都走流式，进度直接回调给界面；失败一律把服务端中文原因抛出去。
 */
object Plugins {
    private const val BASE = "https://agent.ai.baiccb.cn/api/get/plugin.php"

    /** 包体积上限与服务端一致：8MB */
    const val ZIP_MAX = 8L * 1024 * 1024

    /** 一条插件：status / state / note 只在「我提交的」里有意义 */
    data class Item(
        val id: Int,
        val name: String,
        val author: String,
        val descr: String,
        val ver: String,
        val tags: String,
        val size: Long,
        val downloads: Int,
        val createdAt: String,
        val status: Int = 1,
        val state: String = "",
        val note: String = "",
        val authorUname: String = "",
        val authorNick: String = "",
        val likes: Int = 0,
        val favs: Int = 0,
        val cmts: Int = 0,
        val liked: Boolean = false,
        val faved: Boolean = false
    )

    /** 一条评论：mine 表示是我发的（可以提醒但不能删，删除走后台） */
    data class Cmt(
        val id: Int,
        val text: String,
        val uname: String,
        val nick: String,
        val avatar: String,
        val mine: Boolean,
        val createdAt: String
    )

    /** 已上架列表：返回（当前页, 总数） */
    fun list(q: String = "", sort: String = "new", page: Int = 1): Pair<List<Item>, Int> {
        val data = parsed(post(BASE, JSONObject()
            .put("act", "list")
            .put("q", q.trim())
            .put("sort", sort)
            .put("page", page))).optJSONObject("data")
        val arr = data?.optJSONArray("items")
        val items = if (arr == null) emptyList() else (0 until arr.length()).map { item(arr.getJSONObject(it)) }
        return items to (data?.optInt("total") ?: 0)
    } //已上架列表

    /** 单条详情 */
    fun detail(id: Int): Item {
        val data = parsed(post(BASE, JSONObject().put("act", "detail").put("id", id))).optJSONObject("data")
            ?: error("返回格式异常")
        return item(data).copy()
    } //插件详情

    /** 我提交过的：服务端按登录 token 查，能带审核状态与备注 */
    fun mine(): List<Item> {
        val data = parsed(post(BASE, JSONObject().put("act", "mine"))).optJSONObject("data")
        val arr = data?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { item(arr.getJSONObject(it)) }
    } //我的上传

    /** 上传压缩包：必须登录，服务端按 token 认作者，落盘后进待审核，返回记录 id */
    fun upload(
        name: String,
        descr: String,
        ver: String,
        tags: String,
        author: String,
        zip: File,
        onProgress: (Int) -> Unit = {}
    ): Int {
        val boundary = "----bai" + System.currentTimeMillis().toString(16)
        val fields = linkedMapOf(
            "act" to "upload",
            "token" to Cfg.accToken,
            "deviceCode" to Usage.deviceCode(),
            "name" to name.trim(),
            "descr" to descr.trim(),
            "ver" to ver.trim(),
            "tags" to tags.trim(),
            "author" to author.trim()
        )
        val c = URL(BASE).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 120000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        c.setChunkedStreamingMode(64 * 1024)
        val total = zip.length().coerceAtLeast(1L)
        var sent = 0L
        return try {
            c.outputStream.use { os ->
                fun w(s: String) = os.write(s.toByteArray(Charsets.UTF_8))
                fields.forEach { (k, v) -> w("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n") }
                w("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"plugin.zip\"\r\n")
                w("Content-Type: application/zip\r\n\r\n")
                zip.inputStream().use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        sent += n
                        onProgress((sent * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
                w("\r\n--$boundary--\r\n")
            }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (text.isBlank()) error("HTTP $http")
            val json = parsed(text)
            json.optJSONObject("data")?.optInt("id") ?: error("返回格式异常")
        } finally {
            c.disconnect()
        }
    } //上传插件包

    /** 下载并解压前的落盘：成功后返回本地 zip 文件 */
    fun download(id: Int, dest: File, onProgress: (Int) -> Unit = {}): File {
        val url = "$BASE?act=download&id=$id&deviceCode=${Usage.deviceCode()}"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 120000
        c.instanceFollowRedirects = true
        return try {
            val http = c.responseCode
            if (http !in 200..299) {
                //出错时服务端回的是 JSON，把里面的中文原因抛上去
                val text = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val msg = runCatching { JSONObject(text).optString("msg") }.getOrNull()
                error(if (msg.isNullOrBlank()) "HTTP $http" else msg)
            }
            val total = c.contentLengthLong.coerceAtLeast(1L)
            var got = 0L
            dest.parentFile?.mkdirs()
            c.inputStream.use { ins ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        got += n
                        onProgress((got * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            dest
        } finally {
            c.disconnect()
        }
    } //下载插件包

    /** 点赞 / 取消点赞：返回（现在点上没, 最新点赞数） */
    fun like(id: Int): Pair<Boolean, Int> {
        val d = parsed(post(BASE, JSONObject().put("act", "like").put("id", id))).optJSONObject("data")
        return (d?.optBoolean("liked") ?: false) to (d?.optInt("likes") ?: 0)
    } //点赞切换

    /** 收藏 / 取消收藏：返回（现在收藏没, 最新收藏数） */
    fun fav(id: Int): Pair<Boolean, Int> {
        val d = parsed(post(BASE, JSONObject().put("act", "fav").put("id", id))).optJSONObject("data")
        return (d?.optBoolean("faved") ?: false) to (d?.optInt("favs") ?: 0)
    } //收藏切换

    /** 我收藏的插件 */
    fun favs(): List<Item> {
        val arr = parsed(post(BASE, JSONObject().put("act", "favs"))).optJSONObject("data")
            ?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { item(arr.getJSONObject(it)) }
    } //我的收藏

    /** 评论列表：不登录也能看，返回（当前页, 总数） */
    fun cmts(id: Int, page: Int = 1): Pair<List<Cmt>, Int> {
        val d = parsed(post(BASE, JSONObject().put("act", "cmts").put("id", id).put("page", page)))
            .optJSONObject("data")
        val arr = d?.optJSONArray("items") ?: return emptyList<Cmt>() to 0
        return (0 until arr.length()).map { cmt(arr.getJSONObject(it)) } to (d?.optInt("total") ?: 0)
    } //评论列表

    /** 发一条评论，返回服务端落库后的那条 */
    fun cmt(id: Int, text: String): Cmt {
        val d = parsed(post(BASE, JSONObject().put("act", "cmt").put("id", id).put("text", text)))
            .optJSONObject("data")?.optJSONObject("cmt") ?: error("返回格式异常")
        return cmt(d)
    } //发评论

    internal fun item(o: JSONObject) = Item(
        id = o.optInt("id"),
        name = o.optString("name"),
        author = o.optString("author"),
        descr = o.optString("descr"),
        ver = o.optString("ver"),
        tags = o.optString("tags"),
        size = o.optLong("size"),
        downloads = o.optInt("downloads"),
        createdAt = o.optString("createdAt"),
        status = o.optInt("status", 1),
        state = o.optString("state"),
        note = o.optString("note"),
        authorUname = o.optString("authorUname"),
        authorNick = o.optString("authorNick"),
        likes = o.optInt("likes"),
        favs = o.optInt("favs"),
        cmts = o.optInt("cmts"),
        liked = o.optBoolean("liked"),
        faved = o.optBoolean("faved")
    ) //解析一条

    /** 解析一条评论，主页与详情页共用 */
    internal fun cmt(o: JSONObject) = Cmt(
        id = o.optInt("id"),
        text = o.optString("text"),
        uname = o.optString("uname"),
        nick = o.optString("nick"),
        avatar = o.optString("avatar"),
        mine = o.optBoolean("mine"),
        createdAt = o.optString("createdAt")
    ) //解析一条评论

    private fun parsed(text: String): JSONObject {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: error("返回格式异常")
        val code = json.optInt("code")
        if (code != 200) error(json.optString("msg", "请求失败"))
        return json
    } //解析回包

    private fun post(url: String, body: JSONObject): String {
        //所有请求自动带上登录态：没登录就不带，服务端按匿名处理
        if (Cfg.accToken.isNotBlank() && !body.has("token")) {
            body.put("token", Cfg.accToken)
        }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (text.isBlank()) error("HTTP $http")
            text
        } finally {
            c.disconnect()
        }
    } //POST JSON
} //插件广场接口