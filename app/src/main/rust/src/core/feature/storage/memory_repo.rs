//! 记忆的持久化。
//!
//! 记忆是"跨会话的长期事实"：用户说过喜欢简洁回答、项目路径在哪、常用命令是什么。
//! 它与消息的区别：
//!
//! - 消息只在会话内可见，删会话就没了；
//! - 记忆跨会话，用户可以在设置里逐条删。
//!
//! 打分与召回在 `feature::memory`，这里只管存取。

use rusqlite::params;

use super::schema::Db;
use crate::core::base::error::BResult;
use crate::core::feature::memory::MemRow;

impl Db {
    /// 全部记忆（新的在前）。
    pub fn memory_list(&self) -> BResult<Vec<MemRow>> {
        self.all(
            "SELECT _id,text,created_at,IFNULL(session_id,'') FROM memory ORDER BY _id DESC",
            [],
            |r| {
                Ok(MemRow {
                    id: r.get(0)?,
                    text: r.get(1)?,
                    at: r.get(2)?,
                    session: r.get(3).unwrap_or_default(),
                })
            },
        )
    }

    /// 加一条，返回 id。文本会去首尾空白、截断到 500 字（太长的"记忆"没用，
    /// 模型抓不住重点，还会挤占提示词预算）。
    pub fn memory_add(&self, text: &str, session: Option<&str>) -> BResult<i64> {
        let t = crate::core::base::text::truncate(text.trim(), 500);
        self.call(|c| {
            c.execute(
                "INSERT INTO memory(text,session_id,created_at) VALUES(?1,?2,?3)",
                params![t, session, crate::core::base::clock::now_ms()],
            )?;
            Ok(c.last_insert_rowid())
        })
    }

    /// 改写一条记忆的正文。
    ///
    /// 只在"同一件事说得更完整"时用（见 `feature::memory::add`），不是通用编辑接口。
    pub fn memory_set_text(&self, id: i64, text: &str) -> BResult<bool> {
        let n = self.exec("UPDATE memory SET text=?1 WHERE _id=?2", params![text, id])?;
        Ok(n > 0)
    }

    pub fn memory_delete(&self, id: i64) -> BResult<bool> {
        Ok(self.exec("DELETE FROM memory WHERE _id=?1", [id])? > 0)
    }

    pub fn memory_clear(&self) -> BResult<usize> {
        self.exec("DELETE FROM memory", [])
    }

    pub fn memory_count(&self) -> BResult<i64> {
        Ok(self.one("SELECT COUNT(*) FROM memory", [], |r| r.get(0))?.unwrap_or(0))
    }

    /// 只保留最近 `keep` 条。超过上限时调用。
    pub fn memory_trim(&self, keep: usize) -> BResult<usize> {
        self.exec(
            "DELETE FROM memory WHERE _id NOT IN (SELECT _id FROM memory ORDER BY _id DESC LIMIT ?1)",
            [keep as i64],
        )
    }

    /// 去重：文本完全相同（忽略首尾空白）的只留最新一条。
    pub fn memory_dedup(&self) -> BResult<usize> {
        self.exec(
            "DELETE FROM memory WHERE _id NOT IN (
                SELECT MAX(_id) FROM memory GROUP BY TRIM(text)
            )",
            [],
        )
    }

    /// 搜记忆原文（管理页搜索框）。
    pub fn memory_search(&self, q: &str, limit: usize) -> BResult<Vec<MemRow>> {
        let like = format!("%{}%", q.trim());
        self.all(
            "SELECT _id,text,created_at,IFNULL(session_id,'') FROM memory
             WHERE text LIKE ?1 ORDER BY _id DESC LIMIT ?2",
            params![like, limit as i64],
            |r| {
                Ok(MemRow {
                    id: r.get(0)?,
                    text: r.get(1)?,
                    at: r.get(2)?,
                    session: r.get(3).unwrap_or_default(),
                })
            },
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn add_list_delete() {
        let db = Db::memory().unwrap();
        let id = db.memory_add("用户喜欢简洁", None).unwrap();
        assert_eq!(db.memory_count().unwrap(), 1);
        assert_eq!(db.memory_list().unwrap()[0].text, "用户喜欢简洁");
        assert!(db.memory_delete(id).unwrap());
        assert_eq!(db.memory_count().unwrap(), 0);
    }

    #[test]
    fn long_text_is_truncated() {
        let db = Db::memory().unwrap();
        db.memory_add(&"啊".repeat(800), None).unwrap();
        let m = &db.memory_list().unwrap()[0];
        assert!(m.text.chars().count() <= 500);
    }

    #[test]
    fn dedup_keeps_newest() {
        let db = Db::memory().unwrap();
        db.memory_add("same", None).unwrap();
        db.memory_add("same", None).unwrap();
        assert_eq!(db.memory_dedup().unwrap(), 1);
        assert_eq!(db.memory_count().unwrap(), 1);
    }
}
