package b.ai.net

import android.os.Build
import b.ai.core.Cfg
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 用户反馈：提交 + 我的反馈（列表 / 详情 / 留言 / 评价），失败把服务端原话抛给界面 */
object Feedback {
    private const val BASE = "https://agent.ai.baiccb.cn/api/get/"
    private const val SUBMIT = BASE + "feedback.php"
    private const val MINE = BASE + "feedback-mine.php"

    /** 分类键，与服务器 feedback.php 校验的三个值一一对应 */
    const val BUG = "bug"
    const val IDEA = "idea"
    const val OTHER = "other"

    /** 状态：0 待处理 / 1 处理中 / 2 已处理（可评价、可留言）/ 3 已结束（禁止留言） */
    const val PENDING = 0
    const val PROCESSING = 1
    const val DONE = 2
    const val CLOSED = 3

    /** 一条反馈在列表里的样子；content 只有详情里才有 */
    data class Item(
        val id: Int,
        val category: String,
        val status: Int,
        val content: String,
        val createdAt: String,
        val updatedAt: String,
        val msgCount: Int,
        val lastRole: String,
        val lastText: String,
        val lastAt: String,
        val rating: Int,
        val ratingText: String,
        val canReply: Boolean,
        val canRate: Boolean
    )

    /** 一条留言：role = user（我）或 admin（开发者） */
    data class Msg(val role: String, val content: String, val createdAt: String)

    /** 同步提交，调用方自己切 IO 线程；成功返回记录 id，并把设备凭据存下来 */
    fun submit(category: String, content: String, contact: String): Int {
        val body = JSONObject()
            .put("deviceCode", Usage.deviceCode())
            .put("category", category)
            .put("content", content.trim())
            .put("appVersion", Update.clientVersion())
            .put("android", Build.VERSION.RELEASE ?: "")
            .put("model", Build.MODEL ?: "")
            .put("contact", contact.trim())
        val json = call(SUBMIT, body)
        val data = json.optJSONObject("data")
        val token = data?.optString("token").orEmpty()
        if (token.isNotBlank()) Cfg.fbToken = token
        return data?.optInt("id", 0) ?: 0
    } //提交反馈

    /** 我的反馈列表；本机还没提交过或者凭据丢了就返回空 */
    fun list(): List<Item> {
        if (Cfg.fbToken.isBlank()) return emptyList()
        val json = call(MINE, auth("list"))
        val arr = json.optJSONObject("data")?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { item(arr.getJSONObject(it)) }
    } //我的反馈列表

    /** 单条详情：反馈本体 + 按时间正序的留言 */
    fun detail(id: Int): Pair<Item, List<Msg>> {
        val json = call(MINE, auth("detail").put("id", id))
        val data = json.optJSONObject("data") ?: error("返回格式异常")
        val it = item(data.getJSONObject("item"))
        val arr = data.optJSONArray("msgs") ?: return it to emptyList()
        val msgs = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Msg(o.optString("role"), o.optString("content"), o.optString("createdAt"))
        }
        return it to msgs
    } //反馈详情

    /** 继续留言：已结束（status=3）时服务端会拒，直接把它的中文原因抛上去 */
    fun reply(id: Int, text: String): Int {
        val json = call(MINE, auth("reply").put("id", id).put("text", text.trim()))
        return json.optJSONObject("data")?.optInt("status", 0) ?: 0
    } //用户留言

    /** 给这次处理打分；成功后这条就变成已结束，不能再留言 */
    fun rate(id: Int, stars: Int, text: String): Int {
        val json = call(MINE, auth("rate").put("id", id).put("stars", stars).put("text", text.trim()))
        return json.optJSONObject("data")?.optInt("status", 0) ?: 0
    } //评价处理结果

    private fun auth(act: String) = JSONObject()
        .put("act", act)
        .put("deviceCode", Usage.deviceCode())
        .put("token", Cfg.fbToken)

    private fun item(o: JSONObject) = Item(
        id = o.optInt("id"),
        category = o.optString("category"),
        status = o.optInt("status"),
        content = o.optString("content"),
        createdAt = o.optString("createdAt"),
        updatedAt = o.optString("updatedAt"),
        msgCount = o.optInt("msgCount"),
        lastRole = o.optString("lastRole"),
        lastText = o.optString("lastText"),
        lastAt = o.optString("lastAt"),
        rating = if (o.isNull("rating")) 0 else o.optInt("rating"),
        ratingText = o.optString("ratingText"),
        canReply = o.optBoolean("canReply"),
        canRate = o.optBoolean("canRate")
    ) //解析一条记录

    /** POST 一个 JSON，code != 200 就把服务端 msg 抛出去 */
    private fun call(url: String, body: JSONObject): JSONObject {
        val text = post(url, body.toString())
        val json = runCatching { JSONObject(text) }.getOrNull() ?: error("返回格式异常")
        val code = json.optInt("code")
        if (code != 200) error(json.optString("msg", "请求失败"))
        return json
    } //统一请求

    private fun post(url: String, payload: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (text.isBlank()) error("HTTP $http")
            text
        } finally {
            c.disconnect()
        }
    } //POST JSON
} //用户反馈