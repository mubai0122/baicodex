//! v1 → v2 迁移。
//!
//! 什么时候需要：用户开了"数据保障"之后，磁盘上还躺着一堆用内置片段加密的旧文件。
//! 它们能读，但安全性弱。迁移要做的是**原地重写**：
//!
//! ```text
//! 读 blob → 认出 v1 → 用旧密钥解 → 用数据密钥 K 重新 seal(v2) → 原子覆盖
//! ```
//!
//! 三条纪律：
//! 1. **未解锁不迁移**。否则新写的还是 v1，白折腾。
//! 2. **解不开就跳过，不删**。文件可能是别的设备写的，删了就是数据丢失。
//! 3. **先写临时文件再 rename**。迁移到一半被杀，原文件必须还是完整的 v1。

use std::path::{Path, PathBuf};

use serde::Serialize;

use crate::core::base::atomic;
use crate::core::base::error::BResult;

use super::{format, open};

/// 单个文件的迁移结果。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Outcome {
    /// 已经是 v2，不用动。
    AlreadyV2,
    /// 不是加密文件（明文配置），不动。
    Plain,
    /// 成功迁移。
    Migrated,
    /// 认不出/解不开，跳过。
    Skipped,
    /// 写入失败。
    Failed,
}

/// 批量迁移统计。
#[derive(Debug, Clone, Copy, Default, Serialize)]
pub struct Stats {
    pub scanned: usize,
    pub migrated: usize,
    pub already: usize,
    pub plain: usize,
    pub skipped: usize,
    pub failed: usize,
}

impl Stats {
    fn add(&mut self, o: Outcome) {
        self.scanned += 1;
        match o {
            Outcome::Migrated => self.migrated += 1,
            Outcome::AlreadyV2 => self.already += 1,
            Outcome::Plain => self.plain += 1,
            Outcome::Skipped => self.skipped += 1,
            Outcome::Failed => self.failed += 1,
        }
    }
}

/// 迁移一个文件。
pub fn migrate_file(path: impl AsRef<Path>) -> Outcome {
    let path = path.as_ref();
    if !crate::core::security::guard::ready() {
        return Outcome::Skipped;
    }
    let blob = match std::fs::read(path) {
        Ok(b) => b,
        Err(_) => return Outcome::Failed,
    };
    let head = match format::parse_head(&blob) {
        Some(h) => h,
        None => return Outcome::Plain,
    };
    if head.version == 2 {
        return Outcome::AlreadyV2;
    }
    let plain = match open(&blob) {
        Ok(p) => p,
        Err(_) => return Outcome::Skipped,
    };
    let key = match crate::core::security::guard::key() {
        Some(k) => k,
        None => return Outcome::Skipped,
    };
    let new_blob = match format::seal(&key, &plain, 2) {
        Ok(b) => b,
        Err(_) => return Outcome::Failed,
    };
    #[cfg(unix)]
    let written = atomic::write_secure(path, &new_blob, 0o600);
    #[cfg(not(unix))]
    let written = atomic::write(path, &new_blob);
    match written {
        Ok(()) => Outcome::Migrated,
        Err(_) => Outcome::Failed,
    }
}

/// 递归迁移目录下所有文件（只处理 `*.enc` / `*.json` / `*.db` 这几类，
/// 避免去读图片、压缩包这种大文件）。
pub fn migrate_dir(dir: impl AsRef<Path>) -> Stats {
    let mut stats = Stats::default();
    for p in candidates(dir.as_ref()) {
        stats.add(migrate_file(&p));
    }
    stats
}

fn candidates(dir: &Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    let rd = match std::fs::read_dir(dir) {
        Ok(r) => r,
        Err(_) => return out,
    };
    for e in rd.flatten() {
        let p = e.path();
        if p.is_dir() {
            out.extend(candidates(&p));
            continue;
        }
        let ext = crate::core::base::path::ext(&p);
        if matches!(ext.as_str(), "enc" | "json" | "db" | "bin" | "dat") {
            out.push(p);
        }
    }
    out
}

/// 把一段旧格式数据转成新格式（不落盘，给"导入备份"用）。
pub fn rewrap(blob: &[u8]) -> BResult<Vec<u8>> {
    let plain = open(blob)?;
    super::seal(&plain)
}
