package b.ai.core

import android.content.ContentValues
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File
import java.io.FileOutputStream

/** 会话与记录读写 */
object Repo {
    private var helper: Db? = null
    private var statHelper: StatDb? = null
    //本 App 的会话 id 一律是标准 UUID；账本里不认这个形态的行都算外来脏数据
    private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun init(c: Context) {
        if (helper == null) helper = Db(c)
        if (statHelper == null) {
            val h = StatDb(c)
            statHelper = h
            //账本搬一次家：老库里那张 usage 表整表搬进统计库，之后主库不再碰它
            runCatching { h.adopt(w()) }
        }
    }

    private fun w() = helper!!.writableDatabase
    private fun r() = helper!!.readableDatabase
    private fun sw() = statHelper!!.writableDatabase
    private fun sr() = statHelper!!.readableDatabase

    /** 备份导出前压一次 WAL：两个库都压，拷走的就是完整状态 */
    fun checkpoint() {
        runCatching { w().rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } }
        runCatching { sw().rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } }
    } //压 WAL

    /**
     * 迁移前整库备份：压完 WAL 后走 Backup.export 打一个 zip 落到 数据根/backup/premigrate-时间戳.zip。
     * 备份失败返回 null，调用方照旧迁移（不因为备份失败卡住流程）。
     */
    fun backupAll(ctx: Context): String? = runCatching {
        checkpoint()
        val dir = File(Paths.ensure(), "backup").also { runCatching { it.mkdirs() } }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val f = File(dir, "premigrate-$stamp.zip")
        FileOutputStream(f).use { out -> if (Backup.export(ctx, out)) f.absolutePath else null }
    }.getOrNull() //迁移前整库备份

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

    /** 工作区搬家后把会话里记的旧路径换成新路径：返回改了多少行 */
    fun remapWorkspace(old: String, new: String): Int {
        val v = ContentValues().apply { put("workspace", new) }
        return runCatching {
            w().update("session", v, "workspace=?", arrayOf(old))
        }.getOrDefault(0)
    } //改会话语录里的工作区路径

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
        //条数与最后一条先按会话折一遍，再按主键回查内容：整表只扫一次，会话再多也不卡
        val sql = "SELECT s.id,s.title,s.category,s.descr,s.updated_at," +
            "IFNULL(m.content,''),IFNULL(g.cnt,0)," +
            "s.pinned,s.persona,s.workspace " +
            "FROM session s " +
            "LEFT JOIN (SELECT session_id,COUNT(1) cnt,MAX(_id) maxid FROM message GROUP BY session_id) g " +
            "ON g.session_id=s.id " +
            "LEFT JOIN message m ON m._id=g.maxid " +
            "ORDER BY s.pinned DESC, s.updated_at DESC"
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

    /**
     * 读当前分支：从会话的 head 沿 parent_id 回溯到根，再按 _id 正序返回。
     * 顺手把每条消息在同父兄弟里的序号与总数算出来，界面据此显示 ‹ n/m ›。
     */
    fun messages(sessionId: String): List<ChatMsg> {
        val out = ArrayList<ChatMsg>()
        val sql = "WITH RECURSIVE path(id) AS (" +
            "SELECT IFNULL((SELECT head_id FROM session WHERE id=?)," +
            "(SELECT IFNULL(MAX(_id),0) FROM message WHERE session_id=?)) " +
            "UNION ALL " +
            "SELECT IFNULL(m.parent_id,0) FROM message m JOIN path p ON m._id=p.id " +
            "WHERE IFNULL(m.parent_id,0)>0) " +
            "SELECT _id,role,content,tool_calls,tool_call_id,tool_name,error,reasoning,images,meta,starred," +
            "IFNULL(parent_id,0) FROM message " +
            "WHERE _id IN (SELECT id FROM path WHERE id>0) ORDER BY _id ASC"
        r().rawQuery(sql, arrayOf(sessionId, sessionId)).use { c ->
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
                        meta = parseMeta(c.getString(9)),
                        starred = c.getInt(10) == 1,
                        parentId = c.getLong(11)
                    )
                )
            }
        }
        if (out.isEmpty()) return out
        val slots = branchSlots(sessionId)
        if (slots.isEmpty()) return out
        return out.map { m ->
            val slot = slots[m.id]
            if (slot == null) m else m.copy(bIdx = slot.first, bTotal = slot.second)
        }
    } //读当前分支

    /**改写某条摘要的正文：重新总结只换文字，不动它覆盖到哪一段历史 */
    fun updateSummary(id: Long, text: String) {
        w().update(
            "message",
            ContentValues().apply { put("content", text) },
            "_id=?",
            arrayOf(id.toString())
        )
    } //改写摘要正文

    /** 收藏开关：只改标记，不动内容与时间，删会话时跟着消息一起没 */
    fun setStarred(id: Long, on: Boolean) {
        w().update(
            "message",
            ContentValues().apply { put("starred", if (on) 1 else 0) },
            "_id=?",
            arrayOf(id.toString())
        )
    } //收藏开关

    /** 收藏夹：跨会话按收藏时间倒序，带上会话标题，点进去能跳回原会话 */
    fun starred(limit: Int = 300): List<StarHit> {
        val out = ArrayList<StarHit>()
        val sql = "SELECT m._id,m.role,m.content,m.created_at,m.session_id,s.title,m.reasoning " +
            "FROM message m LEFT JOIN session s ON s.id=m.session_id " +
            "WHERE m.starred=1 ORDER BY m.created_at DESC LIMIT ?"
        r().rawQuery(sql, arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) {
                out.add(
                    StarHit(
                        msg = ChatMsg(
                            id = c.getLong(0),
                            role = c.getString(1),
                            content = c.getString(2) ?: "",
                            reasoning = c.getString(6) ?: "",
                            starred = true
                        ),
                        at = c.getLong(3),
                        sessionId = c.getString(4) ?: "",
                        title = c.getString(5) ?: ""
                    )
                )
            }
        }
        return out
    } //收藏夹列表

    /** 回滚到 fromId 之前：只把 head 挪到它的父节点，旧内容全留在树里，之后写的自然长成新分支 */
    fun rollback(sessionId: String, fromId: Long) {
        val p = parentOf(fromId)
        setHead(sessionId, p)
        touch(sessionId)
    } //回滚到某条之前

    /** 删掉 fromId 及其整棵子树：留下孤儿子节点会让分支指向不存在的父节点 */
    fun deleteFrom(sessionId: String, fromId: Long): Int {
        val ids = subtreeIds(sessionId, fromId)
        var n = 0
        ids.chunked(300).forEach { chunk ->
            n += w().delete("message", "_id IN (" + chunk.joinToString(",") + ")", null)
        }
        fixHead(sessionId)
        touch(sessionId)
        return n
    } //删子树

    /** 删单条消息：连同它带出来的分支一起删，head 落到最近的存活祖先上 */
    fun deleteMessage(sessionId: String, id: Long) {
        deleteFrom(sessionId, id)
    } //删单条消息

    /** 切到 msgId 所在那一支：head 落到这一支最深的叶子，界面就会显示这条分支 */
    fun switchTo(sessionId: String, msgId: Long) {
        setHead(sessionId, deepestLeaf(sessionId, msgId))
        touch(sessionId)
    } //切分支

    /** 同父兄弟：界面上的 ‹ › 靠这张表找相邻一条 */
    fun siblings(sessionId: String, msgId: Long): List<Long> {
        val out = ArrayList<Long>()
        r().rawQuery(
            "SELECT _id FROM message WHERE session_id=? AND CAST(IFNULL(parent_id,0) AS INTEGER)=CAST(? AS INTEGER) ORDER BY _id",
            arrayOf(sessionId, parentOf(msgId).toString())
        ).use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        return out
    } //兄弟消息

    /** 每个消息在同父兄弟里的 (序号, 总数)，没有兄弟的会话返回空表 */
    private fun branchSlots(sessionId: String): Map<Long, Pair<Int, Int>> {
        val groups = HashMap<Long, ArrayList<Long>>()
        r().rawQuery(
            "SELECT _id,IFNULL(parent_id,0) FROM message WHERE session_id=? ORDER BY _id",
            arrayOf(sessionId)
        ).use { c ->
            while (c.moveToNext()) groups.getOrPut(c.getLong(1)) { ArrayList() }.add(c.getLong(0))
        }
        val out = HashMap<Long, Pair<Int, Int>>()
        groups.values.forEach { ids ->
            if (ids.size > 1) ids.forEachIndexed { i, id -> out[id] = (i + 1) to ids.size }
        }
        return out
    } //分支序号

    /** 当前走到的位置：会话头指针，没设过就退回最后一条 */
    private fun headOf(sessionId: String): Long {
        var h = 0L
        runCatching {
            r().rawQuery("SELECT IFNULL(head_id,0) FROM session WHERE id=?", arrayOf(sessionId)).use { c ->
                if (c.moveToFirst()) h = c.getLong(0)
            }
        }
        if (h > 0L) return h
        return runCatching {
            r().rawQuery("SELECT IFNULL(MAX(_id),0) FROM message WHERE session_id=?", arrayOf(sessionId)).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
        }.getOrDefault(0L)
    } //当前头

    private fun setHead(sessionId: String, id: Long) {
        runCatching {
            w().update("session", ContentValues().apply { put("head_id", id) }, "id=?", arrayOf(sessionId))
        }
    } //挪头指针

    private fun parentOf(id: Long): Long = runCatching {
        r().rawQuery("SELECT IFNULL(parent_id,0) FROM message WHERE _id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }.getOrDefault(0L) //父节点

    private fun subtreeIds(sessionId: String, root: Long): List<Long> {
        val out = ArrayList<Long>()
        out.add(root)
        var i = 0
        while (i < out.size) {
            val cur = out[i]
            i++
            r().rawQuery(
                "SELECT _id FROM message WHERE session_id=? AND CAST(IFNULL(parent_id,0) AS INTEGER)=CAST(? AS INTEGER)",
                arrayOf(sessionId, cur.toString())
            ).use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        }
        return out
    } //整棵子树

    private fun deepestLeaf(sessionId: String, from: Long): Long {
        var cur = from
        while (true) {
            val next = runCatching {
                r().rawQuery(
                    "SELECT IFNULL(MAX(_id),0) FROM message WHERE session_id=? AND CAST(IFNULL(parent_id,0) AS INTEGER)=CAST(? AS INTEGER)",
                    arrayOf(sessionId, cur.toString())
                ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            }.getOrDefault(0L)
            if (next <= 0L) return cur
            cur = next
        }
    } //这一支的叶子

    /** 头指针指向被删掉的消息时，往上找到最近还活着的祖先 */
    private fun fixHead(sessionId: String) {
        var h = 0L
        runCatching {
            r().rawQuery("SELECT IFNULL(head_id,0) FROM session WHERE id=?", arrayOf(sessionId)).use { c ->
                if (c.moveToFirst()) h = c.getLong(0)
            }
        }
        if (h <= 0L) return
        while (h > 0L) {
            val alive = runCatching {
                r().rawQuery("SELECT 1 FROM message WHERE _id=?", arrayOf(h.toString())).use { it.moveToFirst() }
            }.getOrDefault(false)
            if (alive) break
            h = parentOf(h)
        }
        setHead(sessionId, h)
    } //修正头指针

    /** 追加一条：默认接在当前 head 后面（于是成了新分支），写完 head 就跟到这条 */
    fun append(sessionId: String, m: ChatMsg): ChatMsg {
        val calls = if (m.toolCalls.isEmpty()) null else JSONArray().also { a ->
            m.toolCalls.forEach { a.put(it.toJson()) }
        }?.toString()
        val parent = if (m.parentId > 0L) m.parentId else headOf(sessionId)
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
            put("parent_id", parent)
            put("created_at", System.currentTimeMillis())
        })
        setHead(sessionId, id)
        touch(sessionId)
        return m.copy(id = id, parentId = parent)
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
        //写库前先做体检：脏数据（时间戳跑未来、数字离谱、会话 id 不是本 App 的 UUID）一律拒收，统计页就不会被假数据撑爆
        if (prompt < 0 || completion < 0 || prompt > 5_000_000 || completion > 5_000_000) return
        val now = System.currentTimeMillis()
        val stamp = if (at in (now - 7L * 86_400_000L)..(now + 60_000L)) at else now
        val sid = if (UUID_RE.matches(sessionId)) sessionId else ""
        runCatching {
            sw().insert("usage", null, ContentValues().apply {
                put("at", stamp)
                put("provider", provider)
                put("model", model)
                put("session_id", sid)
                put("prompt", prompt)
                put("cached", cached.coerceIn(0, prompt))
                put("completion", completion)
            })
        }
    } //记一笔用量

    /**统计用：一次 SQL 聚合出总量（行数 / prompt / cached / completion），不把行搬进内存 */
    fun usageTotals(): LongArray {
        val out = LongArray(4)
        sr().rawQuery(
            "SELECT COUNT(*),IFNULL(SUM(prompt),0),IFNULL(SUM(MIN(cached,prompt)),0),IFNULL(SUM(completion),0) " +
                "FROM usage WHERE prompt+completion>0",
            null
        ).use { c -> if (c.moveToNext()) { for (i in 0..3) out[i] = c.getLong(i) } }
        return out
    } //账本总量

    /**统计用：按本地日聚合，日期折叠交给 SQL，页面只拿几百行 */
    fun usageDaily(): List<RawDay> {
        val out = ArrayList<RawDay>()
        sr().rawQuery(
            "SELECT strftime('%Y-%m-%d',at/1000,'unixepoch','localtime') d," +
                "IFNULL(SUM(prompt),0),IFNULL(SUM(MIN(cached,prompt)),0),IFNULL(SUM(completion),0) " +
                "FROM usage WHERE prompt+completion>0 AND at>0 GROUP BY d ORDER BY d",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(RawDay(c.getString(0) ?: "", c.getLong(1), c.getLong(2), c.getLong(3)))
        }
        return out
    } //按日聚合

    /**统计用：按模型聚合，只取前 20 个，避免长尾模型把列表拉长 */
    fun usageModels(): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        sr().rawQuery(
            "SELECT model,IFNULL(SUM(prompt+completion),0) t FROM usage WHERE prompt+completion>0 " +
                "GROUP BY model ORDER BY t DESC LIMIT 20",
            null
        ).use { c -> while (c.moveToNext()) out.add((c.getString(0) ?: "") to c.getLong(1)) }
        return out
    } //按模型聚合

    /** 单个会话的累计用量：prompt / completion，调用方负责 IO 线程 */
    fun usageSession(sid: String): LongArray {
        val out = LongArray(2)
        if (sid.isBlank()) return out
        runCatching {
            sr().rawQuery(
                "SELECT IFNULL(SUM(prompt),0),IFNULL(SUM(completion),0) FROM usage " +
                    "WHERE session_id=? AND prompt+completion>0",
                arrayOf(sid)
            ).use { c ->
                if (c.moveToFirst()) {
                    out[0] = c.getLong(0)
                    out[1] = c.getLong(1)
                }
            }
        }
        return out
    } //单会话用量

    /** 统计用：读独立账本（usage 表），调用方负责 IO 线程 */
    fun eachUsageAt(
        block: (at: Long, provider: String, model: String, prompt: Long, cached: Long, completion: Long) -> Unit
    ) {
        sr().query(
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

    /** 永久记忆：新的排前面，界面直接照这个顺序画 */
    fun memoryList(): List<MemRow> {
        val out = ArrayList<MemRow>()
        r().query("memory", arrayOf("_id", "text", "created_at"), null, null, null, null, "_id DESC").use { c ->
            while (c.moveToNext()) out.add(MemRow(c.getLong(0), c.getString(1) ?: "", c.getLong(2)))
        }
        return out
    } //记忆列表

    fun memoryAdd(text: String, sessionId: String): Boolean = runCatching {
        w().insert("memory", null, ContentValues().apply {
            put("text", text)
            put("session_id", sessionId.ifBlank { null })
            put("created_at", System.currentTimeMillis())
        }) > 0L
    }.getOrDefault(false) //写记忆

    fun memoryDelete(id: Long) {
        w().delete("memory", "_id=?", arrayOf(id.toString()))
    } //删记忆

    fun memoryClear() {
        w().delete("memory", null, null)
    } //清空记忆

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