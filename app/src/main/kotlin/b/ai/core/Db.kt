package b.ai.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本地库：会话 + 记录 + 独立用量账 */
class Db(c: Context) : SQLiteOpenHelper(c.applicationContext, "bai.db", null, 8) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS session(" +
                "id TEXT PRIMARY KEY,title TEXT NOT NULL,category TEXT NOT NULL DEFAULT '',descr TEXT," +
                "pinned INTEGER NOT NULL DEFAULT 0," +
                "persona TEXT,workspace TEXT," +
                "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS message(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL," +
                "role TEXT NOT NULL,content TEXT NOT NULL DEFAULT '',reasoning TEXT," +
                "tool_calls TEXT," +
                "tool_call_id TEXT,tool_name TEXT,error INTEGER NOT NULL DEFAULT 0," +
                "images TEXT,meta TEXT," +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_session ON message(session_id,_id)")
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
    } //版本迁移
}