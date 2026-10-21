package b.ai.core

import android.content.ContentValues
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 会话与记录读写 */
object Repo {
    private var helper: Db? = null

    fun init(c: Context) {
        if (helper == null) helper = Db(c)
    }

    private fun w() = helper!!.writableDatabase
    private fun r() = helper!!.readableDatabase

    fun newSession(title: String = "", category: String = "默认"): SessionRow {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        w().insert("session", null, ContentValues().apply {
            put("id", id)
            put("title", title)
            put("category", category)
            put("descr", "")
            put("created_at", now)
            put("updated_at", now)
        })
        return SessionRow(id, title, category)
    } //新建会话

    fun latestSession(): SessionRow? {
        r().query(
            "session",
            arrayOf("id", "title", "category", "descr", "persona", "workspace"),
            null, null, null, null, "updated_at DESC", "1"
        ).use { c ->
            if (c.moveToFirst()) {
                return SessionRow(
                    c.getString(0),
                    c.getString(1),
                    c.getString(2),
                    descr = c.getString(3) ?: "",
                    persona = c.getString(4) ?: "",
                    workspace = c.getString(5) ?: ""
                )
            }
        }
        return null
    } //取最新会话

    /** 按 id 取单个会话：切会话时恢复角色与工作区用 */
    fun session(id: String): SessionRow? {
        r().query(
            "session",
            arrayOf("id", "title", "category", "descr", "persona", "workspace"),
            "id=?", arrayOf(id), null, null, null, "1"
        ).use { c ->
            if (c.moveToFirst()) {
                return SessionRow(
                    c.getString(0),
                    c.getString(1),
                    c.getString(2),
                    descr = c.getString(3) ?: "",
                    persona = c.getString(4) ?: "",
                    workspace = c.getString(5) ?: ""
                )
            }
        }
        return null
    } //单条会话

    fun setPersona(sessionId: String, personaId: String) {
        w().update(
            "session",
            ContentValues().apply { put("persona", personaId) },
            "id=?",
            arrayOf(sessionId)
        )
    } //会话角色

    fun setWorkspace(sessionId: String, path: String) {
        w().update(
            "session",
            ContentValues().apply { put("workspace", path) },
            "id=?",
            arrayOf(sessionId)
        )
    } //会话工作区

    /** 工作区 → 使用它的会话标题，工作区信息页用 */
    fun workspaceBindings(): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        r().query("session", arrayOf("title", "workspace"), null, null, null, null, "updated_at DESC")
            .use { c ->
                while (c.moveToNext()) {
                    val w = (c.getString(1) ?: "").trim().trimEnd('/')
                    if (w.isEmpty()) continue
                    out.getOrPut(w) { mutableListOf() }.add(c.getString(0) ?: "未命名会话")
                }
            }
        return out
    } //工作区与会话的绑定关系

    fun touch(sessionId: String, title: String? = null) {
        val v = ContentValues().apply { put("updated_at", System.currentTimeMillis()) }
        if (title != null) v.put("title", title)
        w().update("session", v, "id=?", arrayOf(sessionId))
    }

    fun sessions(): List<SessionRow> {
        val out = ArrayList<SessionRow>()
        val sql = "SELECT s.id,s.title,s.category,s.descr,s.updated_at," +
            "(SELECT content FROM message WHERE session_id=s.id ORDER BY _id DESC LIMIT 1)," +
            "(SELECT COUNT(1) FROM message WHERE session_id=s.id)," +
            "s.pinned,s.persona,s.workspace " +
            "FROM session s ORDER BY s.pinned DESC, s.updated_at DESC"
        r().rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SessionRow(
                        id = c.getString(0),
                        title = c.getString(1),
                        category = c.getString(2),
                        descr = c.getString(3) ?: "",
                        updatedAt = c.getLong(4),
                        preview = c.getString(5) ?: "",
                        count = c.getInt(6),
                        pinned = c.getInt(7) == 1,
                        persona = c.getString(8) ?: "",
                        workspace = c.getString(9) ?: ""
                    )
                )
            }
        }
        return out
    } //会话列表：置顶的排前面，其余按最近更新

    fun renameSession(sessionId: String, title: String) {
        w().update(
            "session",
            ContentValues().apply { put("title", title) },
            "id=?",
            arrayOf(sessionId)
        )
    } //改名

    /** 置顶开关：钉住的会话排在列表最上面，不受最近更新时间影响 */
    fun setPinned(sessionId: String, on: Boolean) {
        w().update(
            "session",
            ContentValues().apply { put("pinned", if (on) 1 else 0) },
            "id=?",
            arrayOf(sessionId)
        )
    } //置顶

    /** 模型开场起的名：标题 + 一句描述，一起落库 */
    fun setTitleDescr(sessionId: String, title: String, descr: String) {
        w().update(
            "session",
            ContentValues().apply {
                put("title", title)
                put("descr", descr)
            },
            "id=?",
            arrayOf(sessionId)
        )
    }

    fun deleteSession(sessionId: String) {
        w().delete("message", "session_id=?", arrayOf(sessionId))
        w().delete("session", "id=?", arrayOf(sessionId))
    } //删会话连同记录

    /** 只清消息、保留会话本身：快捷指令 /清空 用 */
    fun clearMessages(sessionId: String) {
        w().delete("message", "session_id=?", arrayOf(sessionId))
    } //清空会话记录

    fun stats(sessionId: String): Pair<Int, Int> {
        var count = 0
        var chars = 0
        r().query(
            "message", arrayOf("content"), "session_id=?", arrayOf(sessionId),
            null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                count++
                chars += (c.getString(0) ?: "").length
            }
        }
        return count to chars
    } //会话规模：条数与字符数

    /** 按标题与消息内容搜会话，返回命中的会话 id */
    fun searchSessionIds(q: String): Set<String> {
        val key = q.trim()
        if (key.isBlank()) return emptySet()
        val like = "%$key%"
        val out = HashSet<String>()
        r().rawQuery("SELECT DISTINCT session_id FROM message WHERE content LIKE ?", arrayOf(like)).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        r().rawQuery("SELECT id FROM session WHERE title LIKE ?", arrayOf(like)).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    } //会话搜索

    fun messages(sessionId: String): List<ChatMsg> {
        val out = ArrayList<ChatMsg>()
        r().query(
            "message",
            arrayOf("_id", "role", "content", "tool_calls", "tool_call_id", "tool_name", "error", "reasoning", "images", "meta"),
            "session_id=?", arrayOf(sessionId), null, null, "_id ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ChatMsg(
                        id = c.getLong(0),
                        role = c.getString(1),
                        content = c.getString(2) ?: "",
                        toolCalls = parseCalls(c.getString(3)),
                        toolCallId = c.getString(4),
                        toolName = c.getString(5),
                        isError = c.getInt(6) == 1,
                        reasoning = c.getString(7) ?: "",
                        images = parseList(c.getString(8)),
                        meta = parseMeta(c.getString(9))
                    )
                )
            }
        }
        return out
    } //读会话全部记录

    /** 删掉 id >= fromId 的记录（编辑重发 / 重新生成回滚用） */
    fun deleteFrom(sessionId: String, fromId: Long): Int {
        val n = w().delete("message", "session_id=? AND _id>=?", arrayOf(sessionId, fromId.toString()))
        touch(sessionId)
        return n
    }

    fun append(sessionId: String, m: ChatMsg): ChatMsg {
        val calls = if (m.toolCalls.isEmpty()) null else JSONArray().also { a ->
            m.toolCalls.forEach { a.put(it.toJson()) }
        }?.toString()
        val id = w().insert("message", null, ContentValues().apply {
            put("session_id", sessionId)
            put("role", m.role)
            put("content", m.content)
            put("reasoning", m.reasoning)
            put("tool_calls", calls)
            put("tool_call_id", m.toolCallId)
            put("tool_name", m.toolName)
            put("error", if (m.isError) 1 else 0)
            put("images", if (m.images.isEmpty()) null else JSONArray(m.images).toString())
            put("meta", m.meta?.toJson()?.toString())
            put("created_at", System.currentTimeMillis())
        })
        touch(sessionId)
        return m.copy(id = id)
    } //追加一条记录

    /** 记一笔用量：每次请求一行，与会话解耦——删会话 / 清消息都不回滚它 */
    fun recordUsage(
        at: Long,
        provider: String,
        model: String,
        sessionId: String,
        prompt: Int,
        cached: Int,
        completion: Int
    ) {
        if (prompt + completion <= 0) return
        runCatching {
            w().insert("usage", null, ContentValues().apply {
                put("at", if (at > 0) at else System.currentTimeMillis())
                put("provider", provider)
                put("model", model)
                put("session_id", sessionId)
                put("prompt", prompt)
                put("cached", cached.coerceIn(0, prompt))
                put("completion", completion)
            })
        }
    } //记一笔用量

    /** 统计用：读独立账本（usage 表），调用方负责 IO 线程 */
    fun eachUsageAt(
        block: (at: Long, provider: String, model: String, prompt: Long, cached: Long, completion: Long) -> Unit
    ) {
        r().query(
            "usage", arrayOf("at", "provider", "model", "prompt", "cached", "completion"),
            null, null, null, null, "at ASC"
        ).use { c ->
            while (c.moveToNext()) {
                block(
                    c.getLong(0),
                    c.getString(1) ?: "",
                    c.getString(2) ?: "",
                    c.getLong(3),
                    c.getLong(4),
                    c.getLong(5)
                )
            }
        }
    } //读账本

    /** 统计用：把每条带用量元数据的记录交给外面聚合，调用方负责 IO 线程 */
    fun eachUsage(block: (at: Long, meta: String) -> Unit) {
        r().query(
            "message", arrayOf("created_at", "meta"),
            "meta IS NOT NULL AND meta != ''", null, null, null, "_id ASC"
        ).use { c ->
            while (c.moveToNext()) block(c.getLong(0), c.getString(1) ?: "")
        }
    } //遍历用量记录

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
    } //反序列化图片路径列表

    private fun parseMeta(raw: String?): MsgMeta? {
        if (raw.isNullOrBlank()) return null
        return runCatching { MsgMeta.of(JSONObject(raw)) }.getOrNull()
    } //反序列化单轮统计

    private fun parseCalls(raw: String?): List<ToolCall> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ToolCall(o.optString("id"), o.optString("name"), o.optString("arguments"))
            }
        }.getOrDefault(emptyList())
    } //反序列化 tool_calls
}