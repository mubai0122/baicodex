//! 数据库连接与建表。
//!
//! 表结构沿用 v13，**一个字段都不删**——用户的聊天记录是最不能丢的东西。
//! 新版本只做加法：新增列用 `ALTER TABLE ... ADD COLUMN`，失败（列已存在）就忽略。
//!
//! 四个表：
//!
//! | 表 | 作用 | 增长量级 |
//! |---|---|---|
//! | `session` | 会话头（标题、置顶、工作区、当前分支头） | 千 |
//! | `message` | 消息树（`parent_id` 成链，兄弟节点表示重新生成） | 十万 |
//! | `memory` | 长期记忆（跨会话） | 百 |
//! | `usage` | 用量账（每次请求一条） | 十万 |
//!
//! 为什么消息与记忆分表：记忆要跨会话检索，消息只在一个会话里读。混在一起会让
//! "读一个会话"变成全表扫描。

use std::path::{Path, PathBuf};
use std::sync::Mutex;

use rusqlite::Connection;

use crate::core::base::error::{BError, BResult};

/// schema 版本。
pub const VERSION: i64 = 14;

/// 数据库句柄。
///
/// 内部一把大锁。SQLite 本身是串行的，再多几把锁只会增加死锁面；真正的并发瓶颈在
/// 网络请求，不在数据库。写事务很短（毫秒级），锁竞争可以忽略。
#[derive(Debug)]
pub struct Db {
    conn: Mutex<Connection>,
    path: Option<PathBuf>,
    /// 是否是内存库（测试用）。
    memory: bool,
}

impl Db {
    /// 打开（或新建）并迁移。
    pub fn open(path: impl AsRef<Path>) -> BResult<Self> {
        let path = path.as_ref();
        if let Some(dir) = path.parent() {
            std::fs::create_dir_all(dir)?;
        }
        let conn = Connection::open(path)
            .map_err(|e| BError::new(crate::core::base::error::ErrorCode::Db, "打开数据库失败").with_detail(e.to_string()))?;
        tune(&conn);
        migrate(&conn)?;
        Ok(Self { conn: Mutex::new(conn), path: Some(path.to_path_buf()), memory: false })
    }

    /// 内存库。测试与"临时会话"用。
    pub fn memory() -> BResult<Self> {
        let conn = Connection::open_in_memory()?;
        tune(&conn);
        migrate(&conn)?;
        Ok(Self { conn: Mutex::new(conn), path: None, memory: true })
    }

    pub fn is_memory(&self) -> bool {
        self.memory
    }

    pub fn path(&self) -> Option<&Path> {
        self.path.as_deref()
    }

    /// 在锁内执行一段操作。
    pub fn call<T>(&self, f: impl FnOnce(&mut Connection) -> BResult<T>) -> BResult<T> {
        let mut g = match self.conn.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        f(&mut g)
    }

    /// 事务。`f` 返回 Err 时自动回滚。
    pub fn tx<T>(&self, f: impl FnOnce(&rusqlite::Transaction<'_>) -> BResult<T>) -> BResult<T> {
        self.call(|c| {
            let tx = c.transaction()?;
            let out = f(&tx)?;
            tx.commit()?;
            Ok(out)
        })
    }

    /// 简单查询：拿一行。
    pub fn one<T>(&self, sql: &str, params: impl rusqlite::Params, f: impl FnOnce(&rusqlite::Row<'_>) -> rusqlite::Result<T>) -> BResult<Option<T>> {
        self.call(|c| {
            let mut st = c.prepare(sql)?;
            let mut rows = st.query(params)?;
            match rows.next()? {
                Some(r) => Ok(Some(f(r)?)),
                None => Ok(None),
            }
        })
    }

    /// 简单查询：拿多行。
    pub fn all<T>(&self, sql: &str, params: impl rusqlite::Params, f: impl Fn(&rusqlite::Row<'_>) -> rusqlite::Result<T>) -> BResult<Vec<T>> {
        self.call(|c| {
            let mut st = c.prepare(sql)?;
            let rows = st.query_map(params, f)?;
            let mut out = Vec::new();
            for r in rows {
                out.push(r?);
            }
            Ok(out)
        })
    }

    /// 执行一条写语句，返回影响行数。
    pub fn exec(&self, sql: &str, params: impl rusqlite::Params) -> BResult<usize> {
        self.call(|c| Ok(c.execute(sql, params)?))
    }

    /// 完整性检查。返回 `ok` 或损坏描述。
    pub fn integrity(&self) -> String {
        match self.call(|c| {
            let mut st = c.prepare("PRAGMA integrity_check")?;
            let s: String = st.query_row([], |r| r.get(0))?;
            Ok(s)
        }) {
            Ok(s) => s,
            Err(e) => format!("检查失败：{e}"),
        }
    }

    /// 把 WAL 合并回主库（备份、导出前调用）。
    pub fn checkpoint(&self) {
        let _ = self.call(|c| {
            c.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")?;
            Ok(())
        });
    }
}

/// 连接参数。
fn tune(conn: &Connection) {
    // WAL：读写不互斥，崩溃恢复快。
    conn.pragma_update(None, "journal_mode", "WAL").ok();
    // NORMAL：WAL 下足够安全，比 FULL 快一个数量级。
    conn.pragma_update(None, "synchronous", "NORMAL").ok();
    // 外键约束：本 schema 不用外键，关掉省一次检查。
    conn.pragma_update(None, "foreign_keys", "OFF").ok();
    // 忙等 3 秒，避免多进程（TUI + 后台任务）同时写时立刻失败。
    conn.busy_timeout(std::time::Duration::from_secs(3)).ok();
    conn.pragma_update(None, "temp_store", "MEMORY").ok();
    conn.pragma_update(None, "cache_size", -8000).ok();
}

/// 建表 + 逐版本补齐。幂等，可重复调用。
pub fn migrate(conn: &Connection) -> BResult<()> {
    conn.execute_batch(
        r#"
        CREATE TABLE IF NOT EXISTS session(
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT '',
            descr TEXT,
            pinned INTEGER NOT NULL DEFAULT 0,
            persona TEXT,
            workspace TEXT,
            head_id INTEGER,
            archived INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS message(
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL DEFAULT '',
            reasoning TEXT,
            tool_calls TEXT,
            tool_call_id TEXT,
            tool_name TEXT,
            error INTEGER NOT NULL DEFAULT 0,
            images TEXT,
            meta TEXT,
            parent_id INTEGER,
            starred INTEGER NOT NULL DEFAULT 0,
            files TEXT,
            created_at INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_msg_session ON message(session_id,_id);
        CREATE INDEX IF NOT EXISTS idx_msg_parent ON message(session_id,parent_id);
        CREATE INDEX IF NOT EXISTS idx_session_time ON session(updated_at DESC);
        CREATE TABLE IF NOT EXISTS memory(
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            text TEXT NOT NULL,
            session_id TEXT,
            created_at INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_memory_time ON memory(created_at DESC);
        CREATE TABLE IF NOT EXISTS usage(
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            at INTEGER NOT NULL,
            provider TEXT NOT NULL DEFAULT '',
            model TEXT NOT NULL DEFAULT '',
            session_id TEXT NOT NULL DEFAULT '',
            prompt INTEGER NOT NULL DEFAULT 0,
            cached INTEGER NOT NULL DEFAULT 0,
            completion INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_usage_at ON usage(at);
        CREATE TABLE IF NOT EXISTS app_meta(
            k TEXT PRIMARY KEY,
            v TEXT NOT NULL
        );
        "#,
    )?;

    // 老库补列：列已存在时 SQLite 报 duplicate column，忽略即可。
    for sql in [
        "ALTER TABLE message ADD COLUMN reasoning TEXT",
        "ALTER TABLE message ADD COLUMN images TEXT",
        "ALTER TABLE message ADD COLUMN meta TEXT",
        "ALTER TABLE session ADD COLUMN descr TEXT",
        "ALTER TABLE session ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE session ADD COLUMN persona TEXT",
        "ALTER TABLE session ADD COLUMN workspace TEXT",
        "ALTER TABLE message ADD COLUMN starred INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE message ADD COLUMN parent_id INTEGER",
        "ALTER TABLE session ADD COLUMN head_id INTEGER",
        "ALTER TABLE message ADD COLUMN files TEXT",
        "ALTER TABLE session ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
    ] {
        let _ = conn.execute(sql, []);
    }

    // 单线历史串成链：parent_id 指向同会话里上一条，head 落到最后一条。
    // 只处理 NULL——已有树结构（重新生成产生的兄弟）不能被覆盖。
    let _ = conn.execute(
        "UPDATE message SET parent_id=IFNULL((
            SELECT MAX(p._id) FROM message p
            WHERE p.session_id=message.session_id AND p._id<message._id),0)
         WHERE parent_id IS NULL",
        [],
    );
    let _ = conn.execute(
        "UPDATE session SET head_id=(SELECT MAX(_id) FROM message WHERE session_id=session.id)
         WHERE head_id IS NULL OR head_id=0",
        [],
    );

    // 清洗账本：非 UUID 会话、未来时间戳、负数一律清掉。
    // 这些是历史 bug 留下的脏数据，留着会让统计页的数字离谱。
    let now = chrono::Utc::now().timestamp_millis();
    let _ = conn.execute("DELETE FROM usage WHERE session_id NOT LIKE '%-%-%-%-%'", []);
    let _ = conn.execute(
        "DELETE FROM usage WHERE at<=0 OR at>?1 OR prompt<0 OR completion<0 OR prompt+completion<=0",
        [now + 86_400_000],
    );
    let _ = conn.execute("UPDATE usage SET cached=MAX(0,MIN(cached,prompt))", []);

    conn.pragma_update(None, "user_version", VERSION).ok();
    Ok(())
}

/// 读/写元信息。
pub fn meta_get(conn: &Connection, k: &str) -> Option<String> {
    conn.query_row("SELECT v FROM app_meta WHERE k=?1", [k], |r| r.get(0)).ok()
}

pub fn meta_set(conn: &Connection, k: &str, v: &str) -> BResult<()> {
    conn.execute("INSERT INTO app_meta(k,v) VALUES(?1,?2) ON CONFLICT(k) DO UPDATE SET v=excluded.v", [k, v])?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn memory_db_has_tables() {
        let db = Db::memory().unwrap();
        let n: i64 = db
            .one("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('session','message','memory','usage','app_meta')", [], |r| r.get(0))
            .unwrap()
            .unwrap();
        assert_eq!(n, 5);
    }

    #[test]
    fn migration_is_idempotent() {
        let db = Db::memory().unwrap();
        db.call(|c| migrate(c)).unwrap();
        db.call(|c| migrate(c)).unwrap();
    }

    #[test]
    fn meta_roundtrip() {
        let db = Db::memory().unwrap();
        db.call(|c| {
            meta_set(c, "install", "abc")?;
            assert_eq!(meta_get(c, "install").as_deref(), Some("abc"));
            Ok(())
        })
        .unwrap();
    }
}
