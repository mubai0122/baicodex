//! 审计链。
//!
//! 每条审计记录带 `prev`（上一条的哈希）和 `hash`（本条内容的哈希），形成链：
//!
//! ```text
//! hash_n = SHA256(prev_hash + canonical_json(record_n))
//! ```
//!
//! 改任何一条历史记录，后面所有哈希都对不上——[Chain::verify] 会指出从第几条开始断。
//! 这不能阻止有写权限的人"整条链重算"，但能防住"偷偷改一行"这种最常见的篡改，
//! 也能在出问题时证明"这份日志不是事后编的"。
//!
//! 记录时机：执行高危工具、读写凭据、改策略、开关加密、root 操作。
//! **不记**普通读文件——那会把日志撑爆，而且没有审计价值。

use serde::{Deserialize, Serialize};

use crate::core::base::bytes::sha256_hex;

/// 一条审计记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Record {
    /// 递增序号，从 1 开始。
    pub seq: u64,
    /// 时间（毫秒）。
    pub at: i64,
    /// 谁发起的：`agent` / `user` / `system`。
    pub actor: String,
    /// 动作：`tool.shell`、`vault.write`、`policy.change`、`guard.enable`。
    pub action: String,
    /// 对象：路径、桶名、工具名。
    #[serde(default)]
    pub target: String,
    /// 是否成功。
    pub ok: bool,
    /// 补充说明（已脱敏）。
    #[serde(default)]
    pub detail: String,
    /// 上一条的哈希。第一条是 64 个 0。
    #[serde(default)]
    pub prev: String,
    /// 本条哈希。
    #[serde(default)]
    pub hash: String,
}

impl Record {
    pub fn new(actor: &str, action: &str, target: &str) -> Self {
        Self {
            seq: 0,
            at: crate::core::base::clock::now_ms(),
            actor: actor.to_string(),
            action: action.to_string(),
            target: target.to_string(),
            ok: true,
            detail: String::new(),
            prev: String::new(),
            hash: String::new(),
        }
    }

    pub fn ok(mut self, ok: bool) -> Self {
        self.ok = ok;
        self
    }

    pub fn detail(mut self, d: impl Into<String>) -> Self {
        self.detail = crate::core::security::redact::clean(&d.into());
        self
    }

    /// 参与哈希计算的规范形式（不含 hash/prev 自身）。
    fn canonical(&self) -> String {
        // 手写拼接而不是 serde_json：字段顺序固定、不受 serde 版本影响，
        // 否则升级依赖会导致历史链全部验不过。
        format!(
            "{}|{}|{}|{}|{}|{}|{}",
            self.seq, self.at, self.actor, self.action, self.target, self.ok, self.detail
        )
    }
}

/// 链尾。
pub const GENESIS: &str = "0000000000000000000000000000000000000000000000000000000000000000";

/// 计算一条记录的哈希。
pub fn digest(prev: &str, r: &Record) -> String {
    sha256_hex(format!("{prev}|{}", r.canonical()).as_bytes())
}

/// 链。
#[derive(Debug, Clone, Default)]
pub struct Chain {
    pub records: Vec<Record>,
}

/// 校验结果。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Verify {
    /// 完好，`0..len` 条全部通过。
    Ok(usize),
    /// 第 `index` 条（0 基）断了，原因是 `why`。
    Broken { index: usize, why: String },
}

impl Verify {
    pub fn is_ok(&self) -> bool {
        matches!(self, Verify::Ok(_))
    }

    pub fn describe(&self) -> String {
        match self {
            Verify::Ok(n) => format!("{n} 条记录，链完整"),
            Verify::Broken { index, why } => format!("第 {} 条断了：{why}", index + 1),
        }
    }
}

impl Chain {
    pub fn push(&mut self, mut r: Record) -> Record {
        r.seq = self.records.last().map(|x| x.seq + 1).unwrap_or(1);
        r.prev = self
            .records
            .last()
            .map(|x| x.hash.clone())
            .unwrap_or_else(|| GENESIS.to_string());
        r.hash = digest(&r.prev, &r);
        self.records.push(r.clone());
        r
    }

    /// 从已有记录重建（读文件后调用）。
    pub fn from_records(records: Vec<Record>) -> Self {
        Self { records }
    }

    /// 校验整条链。
    ///
    /// 检查三件事：序号连续、`prev` 指向上一条的 `hash`、`hash` 与内容匹配。
    pub fn verify(&self) -> Verify {
        let mut prev = GENESIS.to_string();
        for (i, r) in self.records.iter().enumerate() {
            if r.seq != (i as u64) + 1 {
                return Verify::Broken { index: i, why: format!("序号是 {}，应为 {}", r.seq, i + 1) };
            }
            if r.prev != prev {
                return Verify::Broken { index: i, why: "prev 与上一条 hash 不匹配".into() };
            }
            let h = digest(&prev, r);
            if h != r.hash {
                return Verify::Broken { index: i, why: "内容与 hash 不匹配（记录被改过）".into() };
            }
            prev = r.hash.clone();
        }
        Verify::Ok(self.records.len())
    }

    pub fn last_hash(&self) -> String {
        self.records.last().map(|r| r.hash.clone()).unwrap_or_else(|| GENESIS.to_string())
    }

    /// 按动作过滤。
    pub fn find(&self, action_prefix: &str) -> Vec<&Record> {
        self.records.iter().filter(|r| r.action.starts_with(action_prefix)).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rec() -> Record {
        Record::new("agent", "tool.shell", "/bin/sh")
    }

    #[test]
    fn chain_verifies() {
        let mut c = Chain::default();
        for _ in 0..5 {
            c.push(rec());
        }
        assert_eq!(c.verify(), Verify::Ok(5));
    }

    #[test]
    fn tamper_is_caught() {
        let mut c = Chain::default();
        for i in 0..5 {
            let mut r = rec();
            r.target = format!("/bin/cmd{i}");
            c.push(r);
        }
        c.records[2].target = "/bin/evil".into();
        assert!(matches!(c.verify(), Verify::Broken { index: 2, .. }));
    }

    #[test]
    fn deletion_is_caught() {
        let mut c = Chain::default();
        for _ in 0..4 {
            c.push(rec());
        }
        c.records.remove(1);
        assert!(!c.verify().is_ok());
    }

    #[test]
    fn detail_is_redacted_on_insert() {
        let mut c = Chain::default();
        c.push(rec().detail("token=sk-abcdefghijklmnopqrstuvwxyz"));
        assert!(!c.records[0].detail.contains("abcdefghijklmnop"));
        assert!(c.verify().is_ok());
    }
}
