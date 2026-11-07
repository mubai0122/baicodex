//! 长期记忆。
//!
//! 记忆不是"把聊天记录存起来"——那是消息树的事。记忆是**从对话里提炼出的稳定事实**：
//!
//! - 用户偏好："回答简洁点，别列一堆选项"
//! - 环境事实："项目在 ~/work/baicodex，用 pnpm"
//! - 长期约定："提交信息用中文"
//!
//! 这些事实跨会话有效，且**不应该每轮都全量塞进提示词**——几百条会把预算吃光。
//! 所以：存全部，按当前问题召回前几条（打分见 [rank]）。
//!
//! 写入时机由 Agent 决定（模型调用 `remember` 工具），不是每句话都记。
//! 自动抽取很容易记一堆废话，反而降低召回质量。

pub mod rank;

use serde::Serialize;

use crate::core::base::error::BResult;
use crate::core::feature::storage::Db;

/// 一条记忆。
#[derive(Debug, Clone, Serialize)]
pub struct MemRow {
    pub id: i64,
    pub text: String,
    pub at: i64,
    /// 从哪个会话记下的。空表示手动添加。
    #[serde(default)]
    pub session: String,
}

/// 内存镜像。启动时加载一次，写入后同步更新。
#[derive(Debug, Default, Clone)]
pub struct Memory {
    rows: Vec<MemRow>,
}

impl Memory {
    /// 从库加载。
    pub fn load(db: &Db) -> BResult<Self> {
        Ok(Self { rows: db.memory_list()? })
    }

    pub fn reload(&mut self, db: &Db) -> BResult<()> {
        self.rows = db.memory_list()?;
        Ok(())
    }

    pub fn rows(&self) -> &[MemRow] {
        &self.rows
    }

    pub fn len(&self) -> usize {
        self.rows.len()
    }

    pub fn is_empty(&self) -> bool {
        self.rows.is_empty()
    }

    /// 加一条。返回 false 表示"已有相同内容，没重复记"。
    ///
    /// 判重口径（忽略首尾空白与大小写）：完全相同，或一方包含另一方。
    /// 包含也算重复：用户把同一件事说得更啰嗦一点，不该变成两条记忆。
    /// 如果新说法**更完整**（包含旧的），就把旧条目升级成新的，别丢掉细节。
    ///
    /// 不做语义相似度——那需要模型，而"记一条记忆"不该再花一次请求。
    pub fn add(&mut self, db: &Db, text: &str, session: Option<&str>, max_items: usize) -> BResult<bool> {
        let t = text.trim();
        if t.chars().count() < 2 {
            return Ok(false);
        }
        let key = t.to_lowercase();
        let hit = self.rows.iter().position(|r| {
            let old = r.text.trim().to_lowercase();
            // 空条目（历史脏数据）不能参与包含判断：空串被任何串包含。
            if old.chars().count() < 2 {
                return false;
            }
            old == key || old.contains(&key) || key.contains(&old)
        });
        if let Some(i) = hit {
            let old_len = self.rows[i].text.trim().chars().count();
            if key.chars().count() > old_len {
                let merged = crate::core::base::text::truncate(t, 500);
                db.memory_set_text(self.rows[i].id, &merged)?;
                self.rows[i].text = merged;
            }
            return Ok(false);
        }
        let id = db.memory_add(t, session)?;
        self.rows.insert(
            0,
            MemRow {
                id,
                text: crate::core::base::text::truncate(t, 500),
                at: crate::core::base::clock::now_ms(),
                session: session.unwrap_or("").to_string(),
            },
        );
        if max_items > 0 && self.rows.len() > max_items {
            db.memory_trim(max_items)?;
            self.rows.truncate(max_items);
        }
        Ok(true)
    }

    /// 删一条。
    pub fn remove(&mut self, db: &Db, id: i64) -> BResult<bool> {
        let ok = db.memory_delete(id)?;
        if ok {
            self.rows.retain(|r| r.id != id);
        }
        Ok(ok)
    }

    /// 清空。
    pub fn clear(&mut self, db: &Db) -> BResult<usize> {
        let n = db.memory_clear()?;
        self.rows.clear();
        Ok(n)
    }

    /// 召回：给定查询，返回前 `top` 条（已按分数降序）。
    pub fn recall(&self, query: &str, top: usize, min: f64) -> Vec<&MemRow> {
        if self.rows.is_empty() || query.trim().is_empty() {
            return Vec::new();
        }
        let corpus = rank::Corpus::build(self.rows.iter().map(|r| r.text.as_str()));
        let now = crate::core::base::clock::now_ms();
        let docs: Vec<rank::Doc<'_>> = self
            .rows
            .iter()
            .map(|r| rank::Doc { text: r.text.as_str(), at: r.at })
            .collect();
        rank::rank(query, docs, &corpus, now, top, min)
            .into_iter()
            .filter_map(|(i, _)| self.rows.get(i))
            .collect()
    }

    /// 拼成提示词里的一段。没有召回结果时返回空串（调用方据此决定是否加这一段）。
    pub fn prompt(&self, query: &str, top: usize, min: f64, max_chars: usize) -> String {
        let picked = self.recall(query, top, min);
        if picked.is_empty() {
            return String::new();
        }
        let mut s = String::from("## 你记得的事\n\n");
        let mut used = 0usize;
        for m in picked {
            let line = format!("- {}\n", crate::core::base::text::one_line(&m.text));
            if used + line.chars().count() > max_chars {
                break;
            }
            used += line.chars().count();
            s.push_str(&line);
        }
        s
    }

    /// token 估算（记忆段的预算）。
    pub fn tokens(&self) -> usize {
        self.rows.iter().map(|r| crate::core::base::text::estimate_tokens(&r.text)).sum()
    }
}

/// 便捷：记一条。失败只记日志，不影响主流程。
pub fn remember(db: &Db, text: &str, session: Option<&str>) {
    let t = text.trim();
    if t.chars().count() < 2 {
        return;
    }
    if let Err(e) = db.memory_add(t, session) {
        crate::log_warn!("memory", "写记忆失败：{e}");
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn add_dedup_recall() {
        let db = Db::memory().unwrap();
        let mut m = Memory::load(&db).unwrap();
        assert!(m.add(&db, "项目在 ~/work/app", None, 100).unwrap());
        assert!(!m.add(&db, "项目在 ~/work/app", None, 100).unwrap());
        assert!(!m.add(&db, "项目在 ~/work/app 目录", None, 100).unwrap());
        m.add(&db, "用户喜欢简洁的回答", None, 100).unwrap();
        let got = m.recall("项目在哪", 3, 0.0);
        assert!(!got.is_empty());
        assert!(got[0].text.contains("work/app"));
    }

    #[test]
    fn prompt_empty_when_no_match() {
        let db = Db::memory().unwrap();
        let mut m = Memory::load(&db).unwrap();
        m.add(&db, "部署脚本在 a", None, 100).unwrap();
        assert!(m.prompt("完全无关的词汇", 3, 0.5, 500).is_empty());
    }

    #[test]
    fn trim_respects_max() {
        let db = Db::memory().unwrap();
        let mut m = Memory::load(&db).unwrap();
        for i in 0..5 {
            m.add(&db, &format!("记忆内容 {i} 号"), None, 3).unwrap();
        }
        assert_eq!(m.len(), 3);
    }
}
