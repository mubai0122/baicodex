package b.ai.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 用量账本单独一个库：统计页怎么查都不占主库的锁，主库只留会话与消息 */
class StatDb(c: Context) : SQLiteOpenHelper(c.applicationContext, "bai_stat.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS usage(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT,at INTEGER NOT NULL," +
                "provider TEXT NOT NULL DEFAULT '',model TEXT NOT NULL DEFAULT ''," +
                "session_id TEXT NOT NULL DEFAULT ''," +
                "prompt INTEGER NOT NULL DEFAULT 0,cached INTEGER NOT NULL DEFAULT 0," +
                "completion INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_at ON usage(at)")
        db.execSQL("CREATE TABLE IF NOT EXISTS flag(k TEXT PRIMARY KEY,v TEXT)")
    } //建账本

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //v1 就是当前形态；以后要给 usage 加列，在这里按 oldVersion 补
    } //版本迁移

    /** 老账搬家：主库里那张 usage 表整表搬到统计库，条数对上才删老表，搬不动就两边都留着 */
    fun adopt(main: SQLiteDatabase): Int {
        val w = writableDatabase
        //搬过就不再搬：上次失败留下的老表也不再重复插，避免账本翻倍
        if (flag(w, "adopted") != null) return 0
        if (!hasTable(main, "usage")) {
            setFlag(w, "adopted", "empty")
            return 0
        }
        val rows = ArrayList<Array<Any>>()
        runCatching {
            main.rawQuery("SELECT at,provider,model,session_id,prompt,cached,completion FROM usage", null).use { c ->
                while (c.moveToNext()) {
                    rows.add(
                        arrayOf(
                            c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
                            c.getLong(4), c.getLong(5), c.getLong(6)
                        )
                    )
                }
            }
        }
        val before = count(w)
        runCatching {
            w.beginTransaction()
            rows.forEach {
                w.execSQL(
                    "INSERT INTO usage(at,provider,model,session_id,prompt,cached,completion) VALUES(?,?,?,?,?,?,?)",
                    it
                )
            }
            w.setTransactionSuccessful()
        }
        runCatching { w.endTransaction() }
        val moved = count(w) - before
        if (moved == rows.size.toLong()) {
            runCatching { main.execSQL("DROP TABLE usage") }
            setFlag(w, "adopted", rows.size.toString())
        } else {
            setFlag(w, "adopted", "mismatch:$moved/${rows.size}")
        }
        return moved.toInt()
    } //老账搬家

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean = runCatching {
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }
    }.getOrDefault(false) //查表在不在

    private fun count(db: SQLiteDatabase): Long = runCatching {
        db.rawQuery("SELECT COUNT(*) FROM usage", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }.getOrDefault(0L) //账本条数

    private fun flag(db: SQLiteDatabase, k: String): String? = runCatching {
        db.rawQuery("SELECT v FROM flag WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() //读标记

    private fun setFlag(db: SQLiteDatabase, k: String, v: String) {
        runCatching { db.execSQL("INSERT OR REPLACE INTO flag(k,v) VALUES(?,?)", arrayOf<Any>(k, v)) }
    } //写标记
} //用量账本独立库