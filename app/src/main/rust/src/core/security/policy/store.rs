//! 策略持久化。
//!
//! 存在 `<base>/policy.json`，独立于主配置：用户把 config.json 贴给别人时，
//! 不会连"我把 shell 设成了 Allow"一起带出去——那是本机偏好，不该跟着配置走。
//!
//! 读取失败（文件损坏、权限不够）一律退回默认策略集，**绝不因为读不到策略就全放行**。

use std::path::{Path, PathBuf};

use crate::core::base::atomic;
use crate::core::base::error::{BError, BResult};

use super::PolicySet;

/// 策略文件路径。
pub fn path(base: impl AsRef<Path>) -> PathBuf {
    base.as_ref().join("policy.json")
}

/// 读。文件不存在返回默认集（并补齐硬红线）。
pub fn load(p: impl AsRef<Path>) -> PolicySet {
    let p = p.as_ref();
    let mut set = match std::fs::read_to_string(p) {
        Ok(s) => serde_json::from_str::<PolicySet>(&s).unwrap_or_default(),
        Err(_) => PolicySet::default(),
    };
    set.ensure_builtin();
    set
}

/// 写。原子落盘。
pub fn save(p: impl AsRef<Path>, set: &PolicySet) -> BResult<()> {
    let text = serde_json::to_string_pretty(set)
        .map_err(|e| BError::internal("策略序列化失败").with_detail(e.to_string()))?;
    atomic::write(p, text.as_bytes())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::security::Policy;

    #[test]
    fn roundtrip_and_hard_rules_survive() {
        let dir = std::env::temp_dir().join(format!("bai-pol-{}", crate::core::base::ids::IdGen::hex(6)));
        let p = path(&dir);
        let mut s = PolicySet::default();
        s.set("shell", Policy::Deny);
        save(&p, &s).unwrap();
        let back = load(&p);
        assert_eq!(back.get("shell"), Policy::Deny);
        assert!(back.hard_rule_count() >= 8);
        std::fs::remove_dir_all(&dir).ok();
    }
}
