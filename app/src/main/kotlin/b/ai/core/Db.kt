package b.ai.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本地库：会话 + 记录 + 独立用量账 */
class Db(c: Context) : SQLiteOpenHelper(c.applicationContext, "bai.db", null, 13) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS session(" +
                "id TEXT PRIMARY KEY,title TEXT NOT NULL,category TEXT NOT NULL DEFAULT '',descr TEXT," +
                "pinned INTEGER NOT NULL DEFAULT 0," +
                "persona TEXT,workspace TEXT,head_id INTEGER," +
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS message(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL," +
                "role TEXT NOT NULL,content TEXT NOT NULL DEFAULT '',reasoning TEXT," +
                "tool_calls TEXT," +
                "tool_call_id TEXT,tool_name TEXT,error INTEGER NOT NULL DEFAULT 0," +
                "images TEXT,meta TEXT,parent_id INTEGER," +
                "starred INTEGER NOT NULL DEFAULT 0,files TEXT," +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_session ON message(session_id,_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_parent ON message(session_id,parent_id)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS memory(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT NOT NULL," +
                "session_id TEXT,created_at INTEGER NOT NULL)"
        )
        createUsage(db)
    } //建表

    /** 用量账本：一次请求一行，与会话无关，删会话 / 清消息都不动它 */
    private fun createUsage(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS usage(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL," +
                "provider TEXT NOT NULL DEFAULT '',model TEXT NOT NULL DEFAULT ''," +
                "session_id TEXT NOT NULL DEFAULT ''," +
                "prompt INTEGER NOT NULL DEFAULT 0,cached INTEGER NOT NULL DEFAULT 0," +
                "completion INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_at ON usage(at)")
    } //建用量表

    /** 老账搬家：把现存消息里的 message.meta 一条条搬进 usage，已删会话的账搬不回来 */
    private fun backfillUsage(db: SQLiteDatabase) {
        val rows = ArrayList<RawMeta>()
        db.rawQuery(
            "SELECT created_at,session_id,meta FROM message WHERE meta IS NOT NULL AND meta!=''",
            null
        ).use { c ->
            while (c.moveToNext()) {
                rows.add(RawMeta(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: ""))
            }
        }
        rows.forEach { r ->
            val o = runCatching { org.json.JSONObject(r.meta) }.getOrNull() ?: return@forEach
            val p = o.optInt("promptTokens", 0)
            val cp = o.optInt("completionTokens", 0)
            if (p + cp <= 0) return@forEach
            db.execSQL(
                "INSERT INTO usage(at,provider,model,session_id,prompt,cached,completion) VALUES(?,?,?,?,?,?,?)",
                arrayOf<Any>(
                    r.at,
                    o.optString("provider"),
                    o.optString("model"),
                    r.sid,
                    p,
                    o.optInt("cachedTokens", 0).coerceIn(0, p),
                    cp
                )
            )
        }
    } //老账搬家

    private class RawMeta(val at: Long, val sid: String, val meta: String)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //v1→v2：补思考过程列，老记录保持为空
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE message ADD COLUMN reasoning TEXT") }
        }
        //v2→v3：补图片列，存 JSON 数组形式的本地路径
        if (oldVersion < 3) {
            runCatching { db.execSQL("ALTER TABLE message ADD COLUMN images TEXT") }
        }
        //v3→v4：补统计列，存每轮的 token / 速度 / 耗时 JSON
        if (oldVersion < 4) {
            runCatching { db.execSQL("ALTER TABLE message ADD COLUMN meta TEXT") }
        }
        //v4→v5：会话补一句描述，模型开场时生成
        if (oldVersion < 5) {
            runCatching { db.execSQL("ALTER TABLE session ADD COLUMN descr TEXT") }
        }
        //v5→v6：会话补置顶标记，1 = 钉在列表最上面
        if (oldVersion < 6) {
            runCatching { db.execSQL("ALTER TABLE session ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0") }
        }
        //v6→v7：会话各自记角色与工作区，切会话时恢复，不再全局共用一份
        if (oldVersion < 7) {
            runCatching { db.execSQL("ALTER TABLE session ADD COLUMN persona TEXT") }
            runCatching { db.execSQL("ALTER TABLE session ADD COLUMN workspace TEXT") }
        }
        //v7→v8：用量独立成表，删会话不再把账一起删掉；老账从现存消息的 meta 搬一次
        if (oldVersion < 8) {
            runCatching { createUsage(db) }
            runCatching { backfillUsage(db) }
        }
        //v8→v9：消息补收藏标记，1 = 已收藏
        if (oldVersion < 9) {
            runCatching { db.execSQL("ALTER TABLE message ADD COLUMN starred INTEGER NOT NULL DEFAULT 0") }
        }
        //v9→v10：清洗账本里的假数据——会话 id 不是本 App 的 UUID 形态、时间戳跑到未来、缓存数大于输入数的一律清掉
        if (oldVersion < 10) {
            runCatching { sanitizeUsage(db) }
        }
        //v10→v11：消息改成树——每条指向父消息（0 = 会话根），会话记住当前走到哪一条
        if (oldVersion < 11) {
            runCatching { db.execSQL("ALTER TABLE message ADD COLUMN parent_id INTEGER") }
            runCatching { db.execSQL("ALTER TABLE session ADD COLUMN head_id INTEGER") }
            //老库是单线历史：按 _id 顺序串成一条链，head 指向最后一条
            runCatching {
                db.execSQL(
                    "UPDATE message SET parent_id=IFNULL((" +
                        "SELECT MAX(p._id) FROM message p " +
                        "WHERE p.session_id=message.session_id AND p._id<message._id),0)"
                )
            }
            runCatching {
                db.execSQL("UPDATE session SET head_id=(SELECT MAX(_id) FROM message WHERE session_id=session.id)")
            }
            runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_parent ON message(session_id,parent_id)") }
        }
        //v11→v12：永久记忆表——模型觉得重要的事写这里，用户不删就一直随提示下发
        if (oldVersion < 12) {
            runCatching {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory(" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT NOT NULL," +
                        "session_id TEXT,created_at INTEGER NOT NULL)"
                )
            }
        }
        //v12→v13：消息补附件文件列，存 JSON 数组形式的本地路径
        if (oldVersion < 13) {
            runCatching { db.execSQL("ALTER TABLE message ADD COLUMN files TEXT") }
        }
    } //版本迁移

    /** 清洗账本：只留本 App 真实写入的行，顺手把越界数值夹回合理区间 */
    private fun sanitizeUsage(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL("DELETE FROM usage WHERE session_id NOT LIKE '%-%-%-%-%'")
        db.execSQL("DELETE FROM usage WHERE at <= 0 OR at > ?", arrayOf<Any>(now + 86_400_000L))
        db.execSQL("DELETE FROM usage WHERE prompt < 0 OR completion < 0 OR prompt + completion <= 0")
        db.execSQL("DELETE FROM usage WHERE prompt > 5000000 OR completion > 5000000")
        db.execSQL("UPDATE usage SET cached = MAX(0, MIN(cached, prompt))")
    } //清洗账本
}