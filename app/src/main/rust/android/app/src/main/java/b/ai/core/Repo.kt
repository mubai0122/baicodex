package b.ai.core

import b.ai.native.Kernel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据访问门面。
 *
 * 老版本这里是一个 690 行的类，直接持 `SQLiteOpenHelper` 写 SQL。现在 SQL 在
 * Rust（`core/feature/storage/*`），这里只做两件事：把 Kotlin 调用转成 op，
 * 以及把 JSON 转成 [SessionRow] / [ChatMsg]。
 *
 * ## 为什么保留这个对象而不是让界面直接调 Kernel
 *
 * 界面说的是"会话""消息"，Kernel 说的是 op 名。中间这层薄翻译能挡住 op 改名
 * 的冲击，也让 UI 只依赖 [b.ai.core] 这个包，不直接依赖 native 层。
 *
 * ## 同步 / 异步
 *
 * 这里的同步方法内部走 [b.ai.native.NativeBridge.callBlocking]，**只能在工作线程
 * 调**。协程里请用对应的 `xxxAsync`。之所以保留同步版：老界面大量在
 * `withContext(Dispatchers.IO) { Repo.sessions() }` 里用，改成 suspend 会牵动
 * 几十处调用点，收益不值。
 */
object Repo {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // —— 会话 ——

    fun sessions(): List<SessionRow> = parseSessions(Sync.array("sessions"))

    fun session(id: String): SessionRow? =
        sessions().firstOrNull { it.id == id }

    fun latestSession(): SessionRow? = sessions().firstOrNull()

    fun newSession(title: String = "", category: String = "默认"): SessionRow {
        Sync.run("new_session")
        latestSession()?.let { return it }
        // 理论上不会走到这里：new_session 一定会有结果。真发生了说明库坏了。
        return SessionRow(id = "", title = title, category = category)
    }

    fun openSession(id: String) = Sync.run("open_session", JSONObject().put("id", id))

    fun renameSession(id: String, title: String) =
        Sync.run("rename_session", JSONObject().put("id", id).put("title", title))

    fun setPinned(id: String, on: Boolean) =
        Sync.run("pin_session", JSONObject().put("id", id).put("on", on))

    fun deleteSession(id: String) = Sync.run("delete_session", JSONObject().put("id", id))

    fun setPersona(sessionId: String, personaId: String) =
        Sync.run("set_session_persona", JSONObject().put("id", sessionId).put("persona", personaId))

    fun setWorkspace(sessionId: String, path: String) =
        Sync.run("set_session_workspace", JSONObject().put("id", sessionId).put("path", path))

    fun searchSessionIds(q: String): Set<String> =
        Sync.array("search_sessions", JSONObject().put("q", q))
            .let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } }.toSet() }

    /** 会话统计：消息条数、token 合计。 */
    fun stats(sessionId: String): Pair<Int, Int> {
        val d = Sync.data("session_stats", JSONObject().put("id", sessionId))
        return d.optInt("messages") to d.optLong("tokens").toInt()
    }

    // —— 消息 ——

    fun messages(sessionId: String): List<ChatMsg> =
        parseMessages(Sync.array("messages"))

    fun setStarred(id: Long, on: Boolean) =
        Sync.run("star_message", JSONObject().put("id", id).put("on", on))

    /** 收藏夹：跨会话，按时间倒序。 */
    fun starred(limit: Int = 300): List<StarHit> {
        val a = Sync.array("starred", JSONObject().put("limit", limit))
        val out = ArrayList<StarHit>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val m = o.optJSONObject("message") ?: continue
            out.add(
                StarHit(
                    msg = parseMessage(m),
                    at = m.optLong("at"),
                    sessionId = o.optString("session"),
                    title = o.optString("title")
                )
            )
        }
        return out
    }

    fun deleteMessage(sessionId: String, id: Long) =
        Sync.run("delete_message", JSONObject().put("id", id))

    fun switchTo(sessionId: String, msgId: Long) =
        Sync.run("switch_branch", JSONObject().put("id", msgId))

    fun siblings(sessionId: String, msgId: Long): List<Long> {
        val a = Sync.array("siblings", JSONObject().put("id", msgId))
        return (0 until a.length()).map { a.optLong(it) }
    }

    // —— 用量 ——

    fun usageSession(sid: String): LongArray {
        val (p, c) = runCatching { Kernel.usageSession(sid) }.getOrDefault(0L to 0L)
        return longArrayOf(p, c)
    }

    fun usageTotals(): LongArray {
        val d = runCatching { Sync.data("usage") }.getOrDefault(JSONObject())
        return longArrayOf(
            d.optLong("calls"),
            d.optLong("prompt"),
            d.optLong("cached"),
            d.optLong("completion")
        )
    }

    // —— 记忆 ——

    fun memoryList(): List<MemRow> {
        val a = Sync.array("memory_list")
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            MemRow(
                id = o.optLong("id"),
                text = o.optString("text"),
                at = o.optLong("at")
            )
        }
    }

    fun memoryAdd(text: String, sessionId: String): Boolean =
        Sync.data("memory_save", JSONObject().put("text", text)).optBoolean("ok", false)

    fun memoryDelete(id: Long) =
        Sync.run("memory_delete", JSONObject().put("id", id))

    // —— 备份 ——

    /** 备份库与配置，返回 zip 路径；失败返回 null。 */
    fun backupAll(): String? =
        runCatching { Sync.data("backup").optString("data") }.getOrNull()?.takeIf { it.isNotEmpty() }

    // —— 异步包装 ——

    fun refreshAsync(onDone: (List<SessionRow>) -> Unit = {}) = scope.launch {
        val list = runCatching { sessions() }.getOrDefault(emptyList())
        onDone(list)
    }

    // —— 解析 ——

    private fun parseSessions(a: JSONArray): List<SessionRow> = buildList {
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            add(
                SessionRow(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    category = o.optString("category"),
                    updatedAt = o.optLong("updated_at"),
                    preview = o.optString("preview"),
                    count = o.optInt("count"),
                    descr = o.optString("descr"),
                    pinned = o.optBoolean("pinned"),
                    persona = o.optString("persona"),
                    workspace = o.optString("workspace")
                )
            )
        }
    }

    private fun parseMessages(a: JSONArray): List<ChatMsg> = buildList {
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            add(parseMessage(o))
        }
    }

    private fun parseMessage(o: JSONObject): ChatMsg {
        val calls = ArrayList<ToolCall>()
        o.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                calls.add(ToolCall(c.optString("id"), c.optString("name"), c.optString("arguments")))
            }
        }
        return ChatMsg(
            id = o.optLong("id"),
            role = o.optString("role"),
            content = o.optString("content"),
            toolCalls = calls,
            toolCallId = o.optString("tool_call_id").takeIf { it.isNotEmpty() },
            toolName = o.optString("tool_name").takeIf { it.isNotEmpty() },
            isError = o.optBoolean("is_error"),
            reasoning = o.optString("reasoning"),
            images = o.optJSONArray("images").stringList(),
            files = o.optJSONArray("files").stringList(),
            meta = MsgMeta.of(o.optJSONObject("meta")),
            starred = o.optBoolean("starred"),
            parentId = o.optLong("parent_id"),
            bIdx = o.optInt("b_idx"),
            bTotal = o.optInt("b_total")
        )
    }
}

/**
 * 同步调用的小包装。
 *
 * 单独抽出来是为了让上面每个方法都是一行——那些方法本身没有任何判断，
 * 摊开写只会让人以为这里有逻辑。**只允许在工作线程调**。
 */
private object Sync {

    fun call(op: String, args: JSONObject = JSONObject()): JSONObject {
        val v = b.ai.native.NativeBridge.callBlocking(op, args)
        if (!v.optBoolean("ok", false)) {
            val e = v.optJSONObject("error") ?: JSONObject()
            throw b.ai.native.KernelException.of(e)
        }
        return v
    }

    fun data(op: String, args: JSONObject = JSONObject()): JSONObject =
        call(op, args).optJSONObject("data") ?: JSONObject()

    fun array(op: String, args: JSONObject = JSONObject()): JSONArray =
        call(op, args).optJSONArray("data") ?: JSONArray()

    fun run(op: String, args: JSONObject = JSONObject()) {
        call(op, args)
    }
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
}
