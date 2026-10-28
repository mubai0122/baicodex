package b.ai.net

import b.ai.core.Cfg
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 账号：发验证码 / 注册 / 登录 / 我的资料 / 公开主页 / 改资料 / 换头像 / 注销。
 * 登录成功后把 token 与资料写进 Cfg，其它模块只认 Cfg.accToken 这一个登录态。
 */
object Auth {
    private const val BASE = "https://agent.ai.baiccb.cn/api/get/user.php"

    /** 服务端头像上限 512KB，客户端压缩后一般几十 KB */
    const val AV_MAX = 512 * 1024

    /** 一个用户：公开主页不返回 email，自己的资料里才有 */
    data class User(
        val uid: Int = 0,
        val uname: String = "",
        val nick: String = "",
        val email: String = "",
        val avatar: String = "",
        val bio: String = "",
        val site: String = "",
        val plugins: Int = 0,
        val downloads: Long = 0,
        val views: Long = 0,
        val likes: Int = 0,
        val favs: Int = 0,
        val followers: Int = 0,
        val following: Int = 0,
        val followed: Boolean = false,
        val createdAt: String = ""
    )

    /** 已登录：本地有 token 就算 */
    val logged: Boolean get() = Cfg.accToken.isNotBlank()

    /** 本地缓存的自己，断网时先用它填空 */
    fun cached(): User = User(
        uid = Cfg.accUid,
        uname = Cfg.accUname,
        nick = Cfg.accNick,
        avatar = Cfg.accAvatar
    )

    /** 登录成功或刷新资料后同步到本地 */
    fun saveLocal(u: User) {
        Cfg.accUid = u.uid
        Cfg.accUname = u.uname
        Cfg.accNick = u.nick
        Cfg.accAvatar = u.avatar
    } //写本地登录态

    /** 退出登录：本地立即清空，服务端 token 尽力注销 */
    fun clearLocal() {
        Cfg.accToken = ""
        Cfg.accUid = 0
        Cfg.accUname = ""
        Cfg.accNick = ""
        Cfg.accAvatar = ""
    } //清本地登录态

    /** 发注册验证码 */
    fun sendCode(email: String) {
        parsed(post(JSONObject().put("act", "code").put("email", email.trim())))
    } //发验证码

    /** 注册：头像可选，头像传失败不影响建号，服务端会忽略 */
    fun register(
        email: String,
        code: String,
        uname: String,
        nick: String,
        pass: String,
        bio: String = "",
        site: String = "",
        avatar: File? = null
    ): User {
        val fields = linkedMapOf(
            "act" to "register",
            "email" to email.trim(),
            "code" to code.trim(),
            "uname" to uname.trim(),
            "nick" to nick.trim(),
            "pass" to pass,
            "bio" to bio.trim(),
            "site" to site.trim()
        )
        val json = if (avatar == null) {
            val body = JSONObject()
            fields.forEach { (k, v) -> body.put(k, v) }
            parsed(post(body))
        } else {
            parsed(multipart(fields, avatar))
        }
        val data = json.optJSONObject("data") ?: error("返回格式异常")
        val token = data.optString("token")
        if (token.isBlank()) error("返回格式异常")
        Cfg.accToken = token
        val u = user(data.optJSONObject("user") ?: error("返回格式异常"))
        saveLocal(u)
        Cfg.accOnboarded = true
        return u
    } //注册

    /** 登录：account 可以填用户名也可以填邮箱 */
    fun login(account: String, pass: String): User {
        val data = parsed(
            post(JSONObject().put("act", "login").put("account", account.trim()).put("pass", pass))
        ).optJSONObject("data") ?: error("返回格式异常")
        val token = data.optString("token")
        if (token.isBlank()) error("返回格式异常")
        Cfg.accToken = token
        val u = user(data.optJSONObject("user") ?: error("返回格式异常"))
        saveLocal(u)
        Cfg.accOnboarded = true
        return u
    } //登录

    /** 拉自己的资料（顺带校验 token 还有效） */
    fun me(): User {
        val json = parsed(post(JSONObject().put("act", "me")))
        val u = user(json.optJSONObject("data")?.optJSONObject("user") ?: error("返回格式异常"))
        saveLocal(u)
        return u
    } //我的资料

    /** 看别人的主页：返回（对方资料, TA 的已上架插件）；uname 优先，没有就按 uid 找 */
    fun profile(uname: String = "", uid: Int = 0): Pair<User, List<Plugins.Item>> {
        val body = JSONObject().put("act", "profile")
        if (uname.isNotBlank()) body.put("uname", uname.trim()) else body.put("uid", uid)
        val data = parsed(post(body)).optJSONObject("data") ?: error("返回格式异常")
        val u = user(data.optJSONObject("user") ?: error("返回格式异常"))
        val arr = data.optJSONArray("items")
        val items = if (arr == null) emptyList() else (0 until arr.length()).map { Plugins.item(arr.getJSONObject(it)) }
        return u to items
    } //公开主页

    /** 关注 / 取关来回切：返回（现在关注没, TA 的粉丝数, TA 的关注数） */
    fun follow(uid: Int): Triple<Boolean, Int, Int> {
        val d = parsed(post(JSONObject().put("act", "follow").put("uid", uid))).optJSONObject("data")
            ?: error("返回格式异常")
        return Triple(d.optBoolean("followed"), d.optInt("followers"), d.optInt("following"))
    } //关注切换

    /** 关注列表：uid 传 0 就是看自己的，fans 为真取粉丝列表 */
    fun follows(uid: Int = 0, fans: Boolean = false, page: Int = 1): Pair<List<User>, Int> {
        val body = JSONObject()
            .put("act", if (fans) "followers" else "following")
            .put("page", page)
        if (uid > 0) body.put("uid", uid)
        val data = parsed(post(body)).optJSONObject("data") ?: error("返回格式异常")
        val arr = data.optJSONArray("items") ?: return emptyList<User>() to 0
        return (0 until arr.length()).map { user(arr.getJSONObject(it)) } to data.optInt("total")
    } //关注与粉丝列表

    /** 改昵称 / 简介 / 个人主页地址，只传要改的 */
    fun save(nick: String? = null, bio: String? = null, site: String? = null): User {
        val body = JSONObject().put("act", "save")
        nick?.let { body.put("nick", it) }
        bio?.let { body.put("bio", it) }
        site?.let { body.put("site", it) }
        val u = user(parsed(post(body)).optJSONObject("data")?.optJSONObject("user") ?: error("返回格式异常"))
        saveLocal(u)
        return u
    } //改资料

    /** 换头像：上传图片，返回更新后的自己 */
    fun uploadAvatar(file: File): User {
        val json = parsed(multipart(linkedMapOf("act" to "avatar", "token" to Cfg.accToken), file))
        val u = user(json.optJSONObject("data")?.optJSONObject("user") ?: error("返回格式异常"))
        saveLocal(u)
        return u
    } //换头像

    /** 头像本地缓存：服务端地址转成本地文件，界面直接用 AvatarView 显示；失败返回空串 */
    fun face(uid: Int, url: String): String {
        val u = url.trim()
        if (u.isBlank()) return ""
        val d = File(b.ai.App.self.filesDir, "faces").apply { runCatching { mkdirs() } }
        val f = File(d, "u$uid-" + u.hashCode().toString(16) + ".jpg")
        if (f.length() > 0L) return f.absolutePath
        val full = if (u.startsWith("http")) u else "https://agent.ai.baiccb.cn" + (if (u.startsWith("/")) "" else "/") + u
        val ok = runCatching {
            val c = URL(full).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 15000
            try {
                c.inputStream.use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
            } finally {
                c.disconnect()
            }
            f.length() > 0L
        }.getOrDefault(false)
        if (!ok) {
            runCatching { f.delete() }
            return ""
        }
        return f.absolutePath
    } //头像本地缓存

    /** 注销：服务端 token 置空，本地立即清 */
    fun logout() {
        runCatching { post(JSONObject().put("act", "logout").put("token", Cfg.accToken)) }
        clearLocal()
    } //注销

    /** JSON 请求；失败把服务端的中文原因抛给界面 */
    private fun post(body: JSONObject): String {
        if (Cfg.accToken.isNotBlank() && !body.has("token")) {
            body.put("token", Cfg.accToken)
        }
        val c = URL(BASE).openConnection() as HttpURLConnection
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

    /** 表单 + 一个文件；注册带头像、换头像都走它 */
    private fun multipart(fields: Map<String, String>, file: File): String {
        val boundary = "----baiacc" + System.currentTimeMillis().toString(16)
        val c = URL(BASE).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 60000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        c.setChunkedStreamingMode(64 * 1024)
        return try {
            c.outputStream.use { os ->
                fun w(s: String) = os.write(s.toByteArray(Charsets.UTF_8))
                fields.forEach { (k, v) -> w("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n") }
                w("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"avatar.jpg\"\r\n")
                w("Content-Type: image/jpeg\r\n\r\n")
                file.inputStream().use { ins -> ins.copyTo(os) }
                w("\r\n--$boundary--\r\n")
            }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (text.isBlank()) error("HTTP $http")
            text
        } finally {
            c.disconnect()
        }
    } //POST 表单

    /** 解析回包：code 不是 200 就把 msg 抛出去 */
    private fun parsed(text: String): JSONObject {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: error("返回格式异常")
        val code = json.optInt("code")
        if (code != 200) error(json.optString("msg", "请求失败"))
        return json
    } //解析回包

    /** 一条用户 JSON 转数据类 */
    private fun user(o: JSONObject) = User(
        uid = o.optInt("uid"),
        uname = o.optString("uname"),
        nick = o.optString("nick"),
        email = o.optString("email"),
        avatar = o.optString("avatar"),
        bio = o.optString("bio"),
        site = o.optString("site"),
        plugins = o.optInt("plugins"),
        downloads = o.optLong("downloads"),
        views = o.optLong("views"),
        likes = o.optInt("likes"),
        favs = o.optInt("favs"),
        followers = o.optInt("followers"),
        following = o.optInt("following"),
        followed = o.optBoolean("followed"),
        createdAt = o.optString("createdAt")
    ) //解析用户
} //账号接口