//! 审计日志。
//!
//! 文件是 JSONL（一行一条），追加写。为什么不用 SQLite：
//!
//! - 审计要在"数据库打不开"的时候也能写（比如数据库被锁、迁移失败）；
//! - 用户想用 `tail -f` 看，想 `grep` 搜，JSONL 最顺手；
//! - 追加写天然是"只增不改"，与审计语义一致。
//!
//! 内存里保留最近 [DEFAULT_CAP] 条，界面能直接列。文件不轮转（用户自己清），
//! 但提供 [Audit::export] 导出为文本。

pub mod chain;

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

use serde::Serialize;

use crate::core::base::error::{BError, BResult};

pub use chain::{Chain, Record, Verify};

/// 内存里保留的条数。
pub const DEFAULT_CAP: usize = 2000;

/// 审计器。
#[derive(Debug)]
pub struct Audit {
    path: PathBuf,
    chain: Mutex<Chain>,
    enabled: AtomicBool,
    cap: usize,
}

impl Audit {
    /// 打开（或新建）。文件坏了不阻塞启动：能读多少读多少。
    pub fn open(path: impl AsRef<Path>) -> Self {
        let path = path.as_ref().to_path_buf();
        let mut records = Vec::new();
        if let Ok(text) = std::fs::read_to_string(&path) {
            for (i, line) in text.lines().enumerate() {
                if line.trim().is_empty() {
                    continue;
                }
                match serde_json::from_str::<Record>(line) {
                    Ok(r) => records.push(r),
                    Err(_) => {
                        // 坏行之后的内容不可信，直接截断
                        crate::log_warn!("audit", "第 {} 行解析失败，后续记录不再加载", i + 1);
                        break;
                    }
                }
            }
        }
        if records.len() > DEFAULT_CAP {
            let start = records.len() - DEFAULT_CAP;
            records.drain(..start);
        }
        Self { path, chain: Mutex::new(Chain::from_records(records)), enabled: AtomicBool::new(true), cap: DEFAULT_CAP }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// 关掉/打开记录。关掉后 [Self::record] 直接返回。
    pub fn set_enabled(&self, on: bool) {
        self.enabled.store(on, Ordering::Relaxed);
    }

    pub fn enabled(&self) -> bool {
        self.enabled.load(Ordering::Relaxed)
    }

    /// 记一条。
    pub fn record(&self, r: Record) -> BResult<Record> {
        if !self.enabled() {
            return Ok(r);
        }
        let mut g = match self.chain.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let stored = g.push(r);
        if g.records.len() > self.cap {
            g.records.remove(0);
        }
        let line = serde_json::to_string(&stored)
            .map_err(|e| BError::internal("审计记录序列化失败").with_detail(e.to_string()))?;
        crate::core::base::atomic::append(&self.path, format!("{line}\n").as_bytes())?;
        Ok(stored)
    }

    /// 便捷方法。
    pub fn log(&self, actor: &str, action: &str, target: &str, ok: bool, detail: impl Into<String>) {
        let r = Record::new(actor, action, target).ok(ok).detail(detail);
        // 审计失败不应该影响主流程，只记一条警告
        if let Err(e) = self.record(r) {
            crate::log_warn!("audit", "写审计失败：{e}");
        }
    }

    /// 校验整条链。
    pub fn verify(&self) -> Verify {
        match self.chain.lock() {
            Ok(g) => g.verify(),
            Err(p) => p.into_inner().verify(),
        }
    }

    /// 最近 n 条（时间正序）。
    pub fn tail(&self, n: usize) -> Vec<Record> {
        let g = match self.chain.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let start = g.records.len().saturating_sub(n);
        g.records[start..].to_vec()
    }

    /// 导出成文本（带校验结论）。
    pub fn export(&self) -> String {
        let g = match self.chain.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let mut s = String::new();
        s.push_str(&format!("# baicodex 审计导出\n# 校验：{}\n", g.verify().describe()));
        for r in &g.records {
            s.push_str(&format!(
                "{}\t{}\t{}\t{}\t{}\t{}\n",
                crate::core::base::clock::fmt_minute(r.at),
                r.actor,
                r.action,
                if r.ok { "ok" } else { "fail" },
                crate::core::security::redact::scrub_home(&r.target),
                r.detail
            ));
        }
        s
    }

    /// 清空内存与文件。用户主动"清除日志"时调用。
    pub fn clear(&self) -> BResult<()> {
        if let Ok(mut g) = self.chain.lock() {
            g.records.clear();
        }
        if self.path.exists() {
            std::fs::remove_file(&self.path)?;
        }
        Ok(())
    }

    /// 统计：各动作条数。
    pub fn stats(&self) -> Vec<(String, usize)> {
        let g = match self.chain.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let mut map: std::collections::BTreeMap<String, usize> = std::collections::BTreeMap::new();
        for r in &g.records {
            *map.entry(r.action.clone()).or_insert(0) += 1;
        }
        map.into_iter().collect()
    }
}

/// 审计摘要，给 FFI 返回。
#[derive(Debug, Serialize)]
pub struct Summary {
    pub count: usize,
    pub verify: String,
    pub ok: bool,
    pub recent: Vec<Record>,
}

impl Audit {
    pub fn summary(&self, n: usize) -> Summary {
        let v = self.verify();
        Summary {
            count: self.tail(usize::MAX).len(),
            ok: v.is_ok(),
            verify: v.describe(),
            recent: self.tail(n),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn append_and_reload() {
        let dir = std::env::temp_dir().join(format!("bai-audit-{}", crate::core::base::ids::IdGen::hex(6)));
        let p = dir.join("audit.jsonl");
        {
            let a = Audit::open(&p);
            a.log("agent", "tool.shell", "/bin/ls", true, "ok");
            a.log("user", "policy.change", "shell", true, "allow");
            assert!(a.verify().is_ok());
        }
        let b = Audit::open(&p);
        assert_eq!(b.tail(10).len(), 2);
        assert!(b.verify().is_ok());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn disabled_skips() {
        let dir = std::env::temp_dir().join(format!("bai-audit-{}", crate::core::base::ids::IdGen::hex(6)));
        let a = Audit::open(dir.join("a.jsonl"));
        a.set_enabled(false);
        a.log("x", "y", "z", true, "");
        assert!(a.tail(10).is_empty());
        std::fs::remove_dir_all(&dir).ok();
    }
}
