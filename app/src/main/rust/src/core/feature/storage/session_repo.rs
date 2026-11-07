//! 会话读写。
//!
//! 会话 id 用 UUID v4，与旧版一致——老库能直接用，备份恢复也不用转换。
//! 列表查询用一条 LEFT JOIN 把"条数"和"最后一条预览"带出来，避免 N+1：
//!
//! ```sql
//! FROM session s
//! LEFT JOIN (SELECT session_id, COUNT(1), MAX(_id) FROM message GROUP BY session_id) g
//! LEFT JOIN message m ON m._id = g.maxid
//! ```

use rusqlite::{params, Row};

use super::schema::Db;
use crate::core::base::error::{BError, BResult};
use crate::core::feature::model::session::SessionRow;

impl Db {
    /// 新会话。
    pub fn new_session(&self, title: &str, category: &str) -> BResult<SessionRow> {
        let id = uuid::Uuid::new_v4().to_string();
        let now = crate::core::base::clock::now_ms();
        self.exec(
            "INSERT INTO session(id,title,category,descr,created_at,updated_at)
             VALUES(?1,?2,?3,'',?4,?4)",
            params![id, title, category, now],
        )?;
        Ok(SessionRow {
            id,
            title: title.into(),
            category: category.into(),
            updated_at: now,
            ..Default::default()
        })
    }

    /// 最近更新的会话。
    pub fn latest_session(&self) -> BResult<Option<SessionRow>> {
        self.one(
            "SELECT id,title,category,descr,persona,workspace,updated_at,pinned
             FROM session WHERE archived=0 ORDER BY updated_at DESC LIMIT 1",
            [],
            map_session,
        )
    }

    pub fn session(&self, id: &str) -> BResult<Option<SessionRow>> {
        self.one(
            "SELECT id,title,category,descr,persona,workspace,updated_at,pinned
             FROM session WHERE id=?1",
            [id],
            map_session,
        )
    }

    /// 会话必须存在；不存在就报错（写消息前的校验）。
    pub fn require_session(&self, id: &str) -> BResult<SessionRow> {
        self.session(id)?
            .ok_or_else(|| BError::not_found(format!("会话 {id} 不存在")))
    }

    pub fn set_persona(&self, sid: &str, persona: &str) -> BResult<()> {
        self.exec("UPDATE session SET persona=?2 WHERE id=?1", params![sid, persona])?;
        Ok(())
    }

    pub fn set_workspace(&self, sid: &str, path: &str) -> BResult<()> {
        self.exec("UPDATE session SET workspace=?2 WHERE id=?1", params![sid, path])?;
        Ok(())
    }

    pub fn rename_session(&self, sid: &str, title: &str) -> BResult<()> {
        self.exec("UPDATE session SET title=?2 WHERE id=?1", params![sid, title])?;
        Ok(())
    }

    pub fn set_pinned(&self, sid: &str, on: bool) -> BResult<()> {
        self.exec("UPDATE session SET pinned=?2 WHERE id=?1", params![sid, on as i32])?;
        Ok(())
    }

    pub fn set_archived(&self, sid: &str, on: bool) -> BResult<()> {
        self.exec("UPDATE session SET archived=?2 WHERE id=?1", params![sid, on as i32])?;
        Ok(())
    }

    pub fn set_title_descr(&self, sid: &str, title: &str, descr: &str) -> BResult<()> {
        self.exec("UPDATE session SET title=?2,descr=?3 WHERE id=?1", params![sid, title, descr])?;
        Ok(())
    }

    pub fn set_category(&self, sid: &str, category: &str) -> BResult<()> {
        self.exec("UPDATE session SET category=?2 WHERE id=?1", params![sid, category])?;
        Ok(())
    }

    /// 删会话：消息一起删。用量账不回滚（花过的钱不会因为删会话退回来）。
    pub fn delete_session(&self, sid: &str) -> BResult<()> {
        self.tx(|tx| {
            tx.execute("DELETE FROM message WHERE session_id=?1", [sid])?;
            tx.execute("DELETE FROM session WHERE id=?1", [sid])?;
            Ok(())
        })
    }

    pub fn clear_messages(&self, sid: &str) -> BResult<()> {
        self.tx(|tx| {
            tx.execute("DELETE FROM message WHERE session_id=?1", [sid])?;
            tx.execute("UPDATE session SET head_id=0 WHERE id=?1", [sid])?;
            Ok(())
        })
    }

    pub fn touch(&self, sid: &str) -> BResult<()> {
        self.exec(
            "UPDATE session SET updated_at=?2 WHERE id=?1",
            params![sid, crate::core::base::clock::now_ms()],
        )?;
        Ok(())
    }

    /// 会话列表：置顶优先，其余按最近更新。
    pub fn sessions(&self) -> BResult<Vec<SessionRow>> {
        self.sessions_filtered(false)
    }

    /// `include_archived=false` 时只列归档外的。
    pub fn sessions_filtered(&self, include_archived: bool) -> BResult<Vec<SessionRow>> {
        let sql = format!(
            "SELECT s.id,s.title,s.category,s.descr,s.updated_at,
                    IFNULL(m.content,''),IFNULL(g.cnt,0),
                    s.pinned,s.persona,s.workspace
             FROM session s
             LEFT JOIN (SELECT session_id,COUNT(1) cnt,MAX(_id) maxid FROM message GROUP BY session_id) g
               ON g.session_id=s.id
             LEFT JOIN message m ON m._id=g.maxid
             {}
             ORDER BY s.pinned DESC, s.updated_at DESC",
            if include_archived { "" } else { "WHERE s.archived=0" }
        );
        self.all(&sql, [], |r| {
            Ok(SessionRow {
                id: r.get(0)?,
                title: r.get(1)?,
                category: r.get(2)?,
                descr: r.get(3).unwrap_or_default(),
                updated_at: r.get(4)?,
                preview: crate::core::base::text::truncate(
                    &crate::core::base::text::one_line(&r.get::<_, String>(5).unwrap_or_default()),
                    80,
                ),
                count: r.get(6).unwrap_or(0),
                pinned: r.get::<_, i32>(7).unwrap_or(0) == 1,
                persona: r.get(8).unwrap_or_default(),
                workspace: r.get(9).unwrap_or_default(),
            })
        })
    }

    /// 按标题与消息内容搜会话 id。
    ///
    /// 消息内容用 LIKE：几万条消息里模糊搜，SQLite 全表扫描也就几十毫秒；
    /// 上 FTS5 会让"每个用户的库结构不同"，备份恢复时容易出岔子。
    pub fn search_session_ids(&self, q: &str) -> BResult<Vec<String>> {
        let key = q.trim();
        if key.is_empty() {
            return Ok(Vec::new());
        }
        let like = format!("%{key}%");
        let mut out: Vec<String> = self.all(
            "SELECT DISTINCT session_id FROM message WHERE content LIKE ?1",
            [&like],
            |r| r.get(0),
        )?;
        let titles: Vec<String> = self.all("SELECT id FROM session WHERE title LIKE ?1", [&like], |r| r.get(0))?;
        for id in titles {
            if !out.contains(&id) {
                out.push(id);
            }
        }
        Ok(out)
    }

    /// 会话规模：条数与字符数。
    pub fn stats(&self, sid: &str) -> BResult<(i64, i64)> {
        let list = self.all(
            "SELECT content,IFNULL(reasoning,'') FROM message WHERE session_id=?1",
            [sid],
            |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)),
        )?;
        let count = list.len() as i64;
        let chars: i64 = list.iter().map(|(a, b)| (a.chars().count() + b.chars().count()) as i64).sum();
        Ok((count, chars))
    }

    /// 所有分类（去重，空字符串不算）。
    pub fn categories(&self) -> BResult<Vec<String>> {
        self.all(
            "SELECT DISTINCT category FROM session WHERE category<>'' ORDER BY category",
            [],
            |r| r.get(0),
        )
    }

    /// 按分类列会话。
    pub fn sessions_in(&self, category: &str) -> BResult<Vec<SessionRow>> {
        Ok(self.sessions()?.into_iter().filter(|s| s.category == category).collect())
    }

    /// 会话数 / 消息数（"关于"页、存储统计用）。
    pub fn counts(&self) -> BResult<(i64, i64)> {
        let s: i64 = self.one("SELECT COUNT(*) FROM session", [], |r| r.get(0))?.unwrap_or(0);
        let m: i64 = self.one("SELECT COUNT(*) FROM message", [], |r| r.get(0))?.unwrap_or(0);
        Ok((s, m))
    }

    /// 导出全部会话为 JSON（备份）。
    pub fn export_sessions(&self) -> BResult<serde_json::Value> {
        let sessions = self.sessions_filtered(true)?;
        let mut arr = Vec::new();
        for s in sessions {
            let msgs = self.messages(&s.id)?;
            arr.push(serde_json::json!({
                "session": s,
                "messages": msgs.iter().map(|m| serde_json::to_value(m).unwrap_or_default()).collect::<Vec<_>>(),
            }));
        }
        Ok(serde_json::json!({ "version": 1, "sessions": arr }))
    }
}

fn map_session(r: &Row<'_>) -> rusqlite::Result<SessionRow> {
    Ok(SessionRow {
        id: r.get(0)?,
        title: r.get(1)?,
        category: r.get(2)?,
        descr: r.get(3).unwrap_or_default(),
        persona: r.get(4).unwrap_or_default(),
        workspace: r.get(5).unwrap_or_default(),
        updated_at: r.get(6).unwrap_or(0),
        pinned: r.get::<_, i32>(7).unwrap_or(0) == 1,
        ..Default::default()
    })
}

/// 会话是否存在的快捷判断。
pub fn exists(db: &Db, sid: &str) -> bool {
    db.session(sid).map(|s| s.is_some()).unwrap_or(false)
}

/// 取"没有就建"的会话：`id` 为空时新建。
pub fn ensure(db: &Db, id: &str, title: &str) -> BResult<SessionRow> {
    if !id.trim().is_empty() {
        if let Some(s) = db.session(id)? {
            return Ok(s);
        }
    }
    db.new_session(title, "")
}

/// 兼容旧调用：可选地按标题找会话。
pub fn find_by_title(db: &Db, title: &str) -> BResult<Option<SessionRow>> {
    db.one(
        "SELECT id,title,category,descr,persona,workspace,updated_at,pinned
         FROM session WHERE title=?1 ORDER BY updated_at DESC LIMIT 1",
        [title],
        map_session,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_list_delete() {
        let db = Db::memory().unwrap();
        let a = db.new_session("第一个", "工作").unwrap();
        let b = db.new_session("第二个", "生活").unwrap();
        db.append(&a.id, &crate::core::feature::model::message::ChatMsg::user("hi")).unwrap();
        assert_eq!(db.sessions().unwrap().len(), 2);
        assert_eq!(db.categories().unwrap(), vec!["工作", "生活"]);
        db.delete_session(&b.id).unwrap();
        assert_eq!(db.sessions().unwrap().len(), 1);
        assert!(db.session(&b.id).unwrap().is_none());
    }

    #[test]
    fn search_by_content() {
        let db = Db::memory().unwrap();
        let a = db.new_session("笔记", "").unwrap();
        db.append(&a.id, &crate::core::feature::model::message::ChatMsg::user("苹果派的做法")).unwrap();
        assert_eq!(db.search_session_ids("苹果").unwrap(), vec![a.id.clone()]);
        assert!(db.search_session_ids("香蕉").unwrap().is_empty());
    }

    #[test]
    fn ensure_creates_when_missing() {
        let db = Db::memory().unwrap();
        let s = ensure(&db, "", "临时").unwrap();
        assert!(exists(&db, &s.id));
        assert!(!exists(&db, "nope"));
    }
}
