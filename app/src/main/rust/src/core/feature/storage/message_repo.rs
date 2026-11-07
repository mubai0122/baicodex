//! 消息树读写。
//!
//! 核心 SQL 只有一条递归 CTE：
//!
//! ```sql
//! WITH RECURSIVE path(id) AS (
//!     SELECT head_id FROM session WHERE id=?          -- 从当前头开始
//!     UNION ALL
//!     SELECT parent_id FROM message JOIN path ...     -- 一路回溯到根
//! )
//! SELECT ... WHERE _id IN path ORDER BY _id
//! ```
//!
//! 为什么不存"当前分支 id 列表"：消息树会分叉、会回滚、会删子树，维护一份物化的
//! 分支列表等于给自己找不一致。递归查询一次几千行，毫秒级，没必要优化。
//!
//! 重新生成 = 把 head 挪回父节点，再 append 一条新的。旧回答仍在树里，成为兄弟。

use std::collections::HashMap;

use rusqlite::{params, OptionalExtension, Row};

use super::schema::Db;
use crate::core::base::error::{BError, BResult};
use crate::core::feature::model::message::{ChatMsg, MsgMeta, Role, ToolCall};

impl Db {
    /// 读当前分支，并补上同父兄弟的序号 / 总数。
    pub fn messages(&self, sid: &str) -> BResult<Vec<ChatMsg>> {
        let mut list = self.all(
            "WITH RECURSIVE path(id) AS (
                SELECT IFNULL((SELECT head_id FROM session WHERE id=?1),
                              (SELECT IFNULL(MAX(_id),0) FROM message WHERE session_id=?2))
                UNION ALL
                SELECT IFNULL(m.parent_id,0) FROM message m JOIN path p ON m._id=p.id
                WHERE IFNULL(m.parent_id,0)>0)
             SELECT _id,role,content,tool_calls,tool_call_id,tool_name,error,reasoning,
                    images,meta,starred,IFNULL(parent_id,0),files
             FROM message WHERE _id IN (SELECT id FROM path WHERE id>0)
             ORDER BY _id ASC",
            params![sid, sid],
            map_msg,
        )?;
        if list.is_empty() {
            return Ok(list);
        }
        let slots = self.branch_slots(sid)?;
        for m in &mut list {
            if let Some((i, n)) = slots.get(&m.id) {
                m.b_idx = *i;
                m.b_total = *n;
            }
        }
        Ok(list)
    }

    /// 读一条。收藏夹、引用回复用。
    pub fn message(&self, id: i64) -> BResult<Option<ChatMsg>> {
        self.one(
            "SELECT _id,role,content,tool_calls,tool_call_id,tool_name,error,reasoning,
                    images,meta,starred,IFNULL(parent_id,0),files
             FROM message WHERE _id=?1",
            [id],
            map_msg,
        )
    }

    /// 追加一条：接在当前 head 后面（于是成了新分支），写完 head 跟到这条。
    pub fn append(&self, sid: &str, m: &ChatMsg) -> BResult<ChatMsg> {
        let calls = calls_to_json(&m.tool_calls);
        let images = if m.images.is_empty() { None } else { Some(serde_json::to_string(&m.images).unwrap_or_default()) };
        let files = if m.files.is_empty() { None } else { Some(serde_json::to_string(&m.files).unwrap_or_default()) };
        let meta = m.meta.as_ref().map(|x| serde_json::to_string(x).unwrap_or_default());
        let now = crate::core::base::clock::now_ms();
        let explicit_parent = m.parent_id;
        let role = m.role.as_str();
        let content = m.content.clone();
        let reasoning = m.reasoning.clone();
        let tool_call_id = m.tool_call_id.clone();
        let tool_name = m.tool_name.clone();
        let is_error = m.is_error;

        let (id, parent) = self.tx(|tx| {
            let parent = if explicit_parent > 0 { explicit_parent } else { head_of(tx, sid)? };
            tx.execute(
                "INSERT INTO message(session_id,role,content,reasoning,tool_calls,tool_call_id,
                                     tool_name,error,images,files,meta,parent_id,created_at)
                 VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13)",
                params![
                    sid, role, content, reasoning, calls, tool_call_id, tool_name,
                    is_error as i32, images, files, meta, parent, now,
                ],
            )?;
            let id = tx.last_insert_rowid();
            set_head(tx, sid, id)?;
            tx.execute("UPDATE session SET updated_at=?2 WHERE id=?1", params![sid, now])?;
            Ok((id, parent))
        })?;

        let mut out = m.clone();
        out.id = id;
        out.parent_id = parent;
        Ok(out)
    }

    /// 批量追加（导入、子智能体结果回填）。整批一个事务，要么全成要么全不成。
    pub fn append_batch(&self, sid: &str, list: &[ChatMsg]) -> BResult<Vec<ChatMsg>> {
        if list.is_empty() {
            return Ok(Vec::new());
        }
        let now = crate::core::base::clock::now_ms();
        self.tx(|tx| {
            let mut parent = head_of(tx, sid)?;
            let mut out = Vec::with_capacity(list.len());
            for m in list {
                let p = if m.parent_id > 0 { m.parent_id } else { parent };
                tx.execute(
                    "INSERT INTO message(session_id,role,content,reasoning,tool_calls,tool_call_id,
                                         tool_name,error,images,files,meta,parent_id,created_at)
                     VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13)",
                    params![
                        sid,
                        m.role.as_str(),
                        m.content,
                        m.reasoning,
                        calls_to_json(&m.tool_calls),
                        m.tool_call_id,
                        m.tool_name,
                        m.is_error as i32,
                        if m.images.is_empty() { None } else { Some(serde_json::to_string(&m.images).unwrap_or_default()) },
                        if m.files.is_empty() { None } else { Some(serde_json::to_string(&m.files).unwrap_or_default()) },
                        m.meta.as_ref().map(|x| serde_json::to_string(x).unwrap_or_default()),
                        p,
                        now,
                    ],
                )?;
                let id = tx.last_insert_rowid();
                parent = id;
                let mut c = m.clone();
                c.id = id;
                c.parent_id = p;
                out.push(c);
            }
            set_head(tx, sid, parent)?;
            tx.execute("UPDATE session SET updated_at=?2 WHERE id=?1", params![sid, now])?;
            Ok(out)
        })
    }

    /// 回滚到 `from_id` 之前：只把 head 挪到它的父节点，旧内容全留在树里。
    pub fn rollback(&self, sid: &str, from_id: i64) -> BResult<()> {
        self.call(|c| {
            let p = parent_of(c, from_id)?;
            set_head(c, sid, p)
        })
    }

    /// 切到 `msg_id` 所在那一支：head 落到这一支最深的叶子。
    pub fn switch_to(&self, sid: &str, msg_id: i64) -> BResult<()> {
        self.call(|c| {
            let leaf = deepest_leaf(c, sid, msg_id)?;
            set_head(c, sid, leaf)
        })
    }

    /// 同父兄弟（界面的 ‹ › 靠它找相邻一条）。
    pub fn siblings(&self, sid: &str, msg_id: i64) -> BResult<Vec<i64>> {
        self.call(|c| {
            let parent = parent_of(c, msg_id)?;
            let mut st = c.prepare(
                "SELECT _id FROM message WHERE session_id=?1 AND IFNULL(parent_id,0)=?2 ORDER BY _id",
            )?;
            let rows = st.query_map(params![sid, parent], |r| r.get::<_, i64>(0))?;
            let mut out = Vec::new();
            for r in rows {
                out.push(r?);
            }
            Ok(out)
        })
    }

    /// 同父兄弟的序号与总数，只对"有分叉"的节点返回。
    fn branch_slots(&self, sid: &str) -> BResult<HashMap<i64, (i32, i32)>> {
        let pairs = self.all(
            "SELECT _id,IFNULL(parent_id,0) FROM message WHERE session_id=?1 ORDER BY _id",
            [sid],
            |r| Ok((r.get::<_, i64>(0)?, r.get::<_, i64>(1)?)),
        )?;
        let mut groups: HashMap<i64, Vec<i64>> = HashMap::new();
        for (id, parent) in pairs {
            groups.entry(parent).or_default().push(id);
        }
        let mut out = HashMap::new();
        for (_, ids) in groups {
            if ids.len() > 1 {
                let n = ids.len() as i32;
                for (i, id) in ids.into_iter().enumerate() {
                    out.insert(id, (i as i32 + 1, n));
                }
            }
        }
        Ok(out)
    }

    /// 删单条：连同它带出来的子树一起删，head 落到最近的存活祖先。
    pub fn delete_message(&self, sid: &str, id: i64) -> BResult<usize> {
        self.tx(|tx| {
            let ids = subtree_ids(tx, sid, id)?;
            let mut n = 0;
            for chunk in ids.chunks(300) {
                let placeholders = chunk.iter().map(|_| "?").collect::<Vec<_>>().join(",");
                let sql = format!("DELETE FROM message WHERE _id IN ({placeholders})");
                let mut st = tx.prepare(&sql)?;
                n += st.execute(rusqlite::params_from_iter(chunk.iter()))?;
            }
            fix_head(tx, sid)?;
            Ok(n)
        })
    }

    /// 收藏 / 取消收藏。
    pub fn set_starred(&self, id: i64, on: bool) -> BResult<()> {
        self.exec("UPDATE message SET starred=?2 WHERE _id=?1", params![id, on as i32])?;
        Ok(())
    }

    /// 收藏夹：跨会话按时间倒序。
    pub fn starred(&self, limit: i64) -> BResult<Vec<(ChatMsg, String, String)>> {
        self.all(
            "SELECT m._id,m.role,m.content,m.created_at,m.session_id,IFNULL(s.title,''),m.reasoning
             FROM message m LEFT JOIN session s ON s.id=m.session_id
             WHERE m.starred=1 ORDER BY m.created_at DESC LIMIT ?1",
            [limit],
            |r| {
                let mut m = ChatMsg {
                    id: r.get(0)?,
                    role: Role::parse(&r.get::<_, String>(1)?),
                    content: r.get(2).unwrap_or_default(),
                    reasoning: r.get(6).unwrap_or_default(),
                    starred: true,
                    ..Default::default()
                };
                m.tool_calls = Vec::new();
                Ok((m, r.get::<_, String>(4)?, r.get::<_, String>(5)?))
            },
        )
    }

    /// 改写摘要正文：重新总结只换文字，不动它覆盖到哪一段历史。
    pub fn update_summary(&self, id: i64, text: &str) -> BResult<()> {
        self.exec("UPDATE message SET content=?2 WHERE _id=?1", params![id, text])?;
        Ok(())
    }

    /// 改一条消息的正文（编辑用户消息后重发）。
    pub fn update_content(&self, id: i64, text: &str) -> BResult<()> {
        self.exec("UPDATE message SET content=?2 WHERE _id=?1", params![id, text])?;
        Ok(())
    }

    /// 当前 head。
    pub fn head(&self, sid: &str) -> BResult<i64> {
        self.call(|c| head_of(c, sid))
    }

    /// 当前分支的消息条数与字符数（压缩阈值判断用）。
    pub fn branch_size(&self, sid: &str) -> BResult<(i64, i64)> {
        let list = self.messages(sid)?;
        let mut chars = 0i64;
        for m in &list {
            chars += m.content.chars().count() as i64 + m.reasoning.chars().count() as i64;
        }
        Ok((list.len() as i64, chars))
    }
}

/// 库里的 tool_calls 列 → 结构。
pub fn calls_to_json(calls: &[ToolCall]) -> Option<String> {
    if calls.is_empty() {
        return None;
    }
    Some(
        serde_json::to_string(
            &calls
                .iter()
                .map(|c| serde_json::json!({ "id": c.id, "name": c.name, "arguments": c.arguments }))
                .collect::<Vec<_>>(),
        )
        .unwrap_or_else(|_| "[]".into()),
    )
}

fn map_msg(r: &Row<'_>) -> rusqlite::Result<ChatMsg> {
    let role: String = r.get(1)?;
    let tool_calls: Option<String> = r.get(3)?;
    let images: Option<String> = r.get(8)?;
    let meta: Option<String> = r.get(9)?;
    let files: Option<String> = r.get(12)?;
    Ok(ChatMsg {
        id: r.get(0)?,
        role: Role::parse(&role),
        content: r.get(2).unwrap_or_default(),
        tool_calls: parse_calls(tool_calls),
        tool_call_id: r.get(4)?,
        tool_name: r.get(5)?,
        is_error: r.get::<_, i32>(6).unwrap_or(0) == 1,
        reasoning: r.get(7).unwrap_or_default(),
        images: parse_list(images),
        meta: meta.and_then(|t| serde_json::from_str::<MsgMeta>(&t).ok()),
        starred: r.get::<_, i32>(10).unwrap_or(0) == 1,
        parent_id: r.get(11).unwrap_or(0),
        files: parse_list(files),
        b_idx: 0,
        b_total: 0,
    })
}

fn parse_list(raw: Option<String>) -> Vec<String> {
    raw.and_then(|t| serde_json::from_str::<Vec<String>>(&t).ok()).unwrap_or_default()
}

fn parse_calls(raw: Option<String>) -> Vec<ToolCall> {
    #[derive(serde::Deserialize)]
    struct Raw {
        id: String,
        name: String,
        #[serde(default)]
        arguments: String,
    }
    raw.and_then(|t| serde_json::from_str::<Vec<Raw>>(&t).ok())
        .map(|v| v.into_iter().map(|r| ToolCall::new(r.id, r.name, r.arguments)).collect())
        .unwrap_or_default()
}

fn head_of(conn: &rusqlite::Connection, sid: &str) -> BResult<i64> {
    let h: i64 = conn
        .query_row("SELECT IFNULL(head_id,0) FROM session WHERE id=?1", [sid], |r| r.get(0))
        .optional()?
        .unwrap_or(0);
    if h > 0 {
        return Ok(h);
    }
    // 老库没写 head：退化成"最后一条"
    Ok(conn
        .query_row("SELECT IFNULL(MAX(_id),0) FROM message WHERE session_id=?1", [sid], |r| r.get(0))
        .unwrap_or(0))
}

fn set_head(conn: &rusqlite::Connection, sid: &str, id: i64) -> BResult<()> {
    conn.execute("UPDATE session SET head_id=?2 WHERE id=?1", params![sid, id])?;
    Ok(())
}

fn parent_of(conn: &rusqlite::Connection, id: i64) -> BResult<i64> {
    Ok(conn
        .query_row("SELECT IFNULL(parent_id,0) FROM message WHERE _id=?1", [id], |r| r.get(0))
        .optional()?
        .unwrap_or(0))
}

fn subtree_ids(conn: &rusqlite::Connection, sid: &str, root: i64) -> BResult<Vec<i64>> {
    let mut out = vec![root];
    let mut i = 0;
    while i < out.len() {
        let cur = out[i];
        i += 1;
        let mut st = conn.prepare("SELECT _id FROM message WHERE session_id=?1 AND IFNULL(parent_id,0)=?2")?;
        let rows = st.query_map(params![sid, cur], |r| r.get::<_, i64>(0))?;
        for id in rows {
            out.push(id?);
        }
    }
    Ok(out)
}

fn deepest_leaf(conn: &rusqlite::Connection, sid: &str, from: i64) -> BResult<i64> {
    let mut cur = from;
    // 防御：数据被手工改坏成环时，最多走 10000 层就停
    for _ in 0..10_000 {
        let next: i64 = conn
            .query_row(
                "SELECT IFNULL(MAX(_id),0) FROM message WHERE session_id=?1 AND IFNULL(parent_id,0)=?2",
                params![sid, cur],
                |r| r.get(0),
            )
            .unwrap_or(0);
        if next <= 0 {
            return Ok(cur);
        }
        cur = next;
    }
    Ok(cur)
}

fn fix_head(conn: &rusqlite::Connection, sid: &str) -> BResult<()> {
    let mut h = head_of(conn, sid)?;
    while h > 0 {
        let alive: bool = conn
            .query_row("SELECT 1 FROM message WHERE _id=?1", [h], |_| Ok(true))
            .optional()?
            .unwrap_or(false);
        if alive {
            break;
        }
        h = parent_of(conn, h)?;
    }
    set_head(conn, sid, h)
}

/// 给错误信息用的上下文包装。
pub fn sql_err(e: rusqlite::Error) -> BError {
    BError::internal("SQLite 操作失败").with_detail(e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn db_with_session() -> (Db, String) {
        let db = Db::memory().unwrap();
        let s = db.new_session("测试", "").unwrap();
        (db, s.id)
    }

    #[test]
    fn append_and_read() {
        let (db, sid) = db_with_session();
        db.append(&sid, &ChatMsg::user("你好")).unwrap();
        db.append(&sid, &ChatMsg::assistant("在")).unwrap();
        let list = db.messages(&sid).unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(list[0].content, "你好");
        assert_eq!(list[1].parent_id, list[0].id);
    }

    #[test]
    fn regenerate_creates_sibling() {
        let (db, sid) = db_with_session();
        let q = db.append(&sid, &ChatMsg::user("问题")).unwrap();
        db.append(&sid, &ChatMsg::assistant("回答 1")).unwrap();
        db.rollback(&sid, q.id + 1).unwrap();
        db.append(&sid, &ChatMsg::assistant("回答 2")).unwrap();
        // 当前分支只有 2 条
        let list = db.messages(&sid).unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(list[1].content, "回答 2");
        // 兄弟有 2 个
        let sibs = db.siblings(&sid, list[1].id).unwrap();
        assert_eq!(sibs.len(), 2);
        // 切回第一个
        db.switch_to(&sid, sibs[0]).unwrap();
        let list = db.messages(&sid).unwrap();
        assert_eq!(list[1].content, "回答 1");
    }

    #[test]
    fn delete_subtree_fixes_head() {
        let (db, sid) = db_with_session();
        let q = db.append(&sid, &ChatMsg::user("q")).unwrap();
        db.append(&sid, &ChatMsg::assistant("a1")).unwrap();
        db.append(&sid, &ChatMsg::assistant("a2")).unwrap();
        let n = db.delete_message(&sid, q.id).unwrap();
        assert_eq!(n, 3);
        assert_eq!(db.messages(&sid).unwrap().len(), 0);
        assert_eq!(db.head(&sid).unwrap(), 0);
    }

    #[test]
    fn star_and_list() {
        let (db, sid) = db_with_session();
        let m = db.append(&sid, &ChatMsg::assistant("值得收藏")).unwrap();
        db.set_starred(m.id, true).unwrap();
        let list = db.starred(10).unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].0.content, "值得收藏");
    }

    #[test]
    fn tool_calls_survive_roundtrip() {
        let (db, sid) = db_with_session();
        let mut m = ChatMsg::assistant("");
        m.tool_calls.push(ToolCall::new("c1", "read_file", r#"{"path":"/a"}"#));
        db.append(&sid, &m).unwrap();
        let list = db.messages(&sid).unwrap();
        assert_eq!(list[0].tool_calls.len(), 1);
        assert_eq!(list[0].tool_calls[0].name, "read_file");
    }
}
