//! 路径沙箱。
//!
//! 回答一个问题：**这个路径，能不能读/写？**
//!
//! 判定顺序（命中即停）：
//!
//! ```text
//! 1. 系统敏感文件（/dev/mem、/etc/shadow、~/.ssh）→ 拒
//! 2. 用户显式放行列表                          → 允许
//! 3. deny 列表                                 → 拒
//! 4. 允许根内                                  → 允许（写还要看是否只读根）
//! 5. 只读根内                                  → 允许读，拒写
//! 6. 其它                                      → 拒
//! ```
//!
//! 两种检查都要做：
//! - [Sandbox::check] 只做字符串规整（防 `..`），快，用于"先看看合不合法"。
//! - [Sandbox::check_real] 会解析软链（防 `/tmp/link → /etc/shadow`），用于真正
//!   要动手之前。
//!
//! 只做字符串检查就敢写文件，等于给软链留了后门；每次都解析软链又太慢。
//! 所以是"两段式"：先粗筛，动作前再细查。

pub mod allow;

use std::path::{Path, PathBuf};

use serde::Serialize;

use crate::core::base::path as pathx;

/// 判定结果。
#[derive(Debug, Clone, Serialize)]
pub struct Verdict {
    pub allowed: bool,
    /// 允许时：是否可写。
    pub writable: bool,
    /// 规整后的绝对路径。
    pub path: String,
    /// 原因（拒绝时说清是哪条规则）。
    pub reason: String,
}

impl Verdict {
    fn deny(p: &str, reason: impl Into<String>) -> Self {
        Self { allowed: false, writable: false, path: p.to_string(), reason: reason.into() }
    }

    fn ok(p: &str, writable: bool, reason: impl Into<String>) -> Self {
        Self { allowed: true, writable, path: p.to_string(), reason: reason.into() }
    }
}

/// 沙箱。
#[derive(Debug, Clone)]
pub struct Sandbox {
    roots: Vec<PathBuf>,
    read_only: Vec<PathBuf>,
    deny: Vec<PathBuf>,
    /// 用户显式放行的敏感路径（默认空）。
    allow_sensitive: Vec<PathBuf>,
}

impl Sandbox {
    /// 用默认表建一个。
    pub fn new(base: impl AsRef<Path>) -> Self {
        Self {
            roots: allow::default_roots(base),
            read_only: allow::default_read_only(),
            deny: allow::default_deny(),
            allow_sensitive: Vec::new(),
        }
    }

    /// 完全自定义。
    pub fn with(roots: Vec<PathBuf>, read_only: Vec<PathBuf>, deny: Vec<PathBuf>) -> Self {
        Self { roots, read_only, deny, allow_sensitive: Vec::new() }
    }

    /// 显式放行一个敏感路径。用户自己加的白名单，优先级高于内置敏感表。
    pub fn allow_path(&mut self, p: impl AsRef<Path>) {
        self.allow_sensitive.push(pathx::normalize(p));
    }

    pub fn roots(&self) -> &[PathBuf] {
        &self.roots
    }

    /// 字符串级检查。
    pub fn check(&self, raw: &str, write: bool) -> Verdict {
        let p = pathx::normalize(pathx::expand_tilde(raw));
        let s = p.to_string_lossy().to_string();
        self.judge(&p, &s, write)
    }

    /// 解析软链后检查。写入/删除前必须用这个。
    pub fn check_real(&self, raw: &str, write: bool) -> Verdict {
        let logical = pathx::normalize(pathx::expand_tilde(raw));
        let real = pathx::real_path(&logical);
        let s = real.to_string_lossy().to_string();
        self.judge(&real, &s, write)
    }

    fn judge(&self, p: &Path, s: &str, write: bool) -> Verdict {
        // 1. 用户显式放行优先
        if self.allow_sensitive.iter().any(|x| p.starts_with(x)) {
            return Verdict::ok(s, true, "在用户放行列表里");
        }
        // 2. 系统敏感 / 默认敏感
        if allow::is_sensitive(p) {
            return Verdict::deny(s, "这是敏感路径（私钥/系统凭据），默认不允许访问");
        }
        // 3. 自定义 deny
        if self.deny.iter().any(|x| p.starts_with(x)) {
            return Verdict::deny(s, "该路径在禁止列表里");
        }
        // 4. 只读根
        if self.read_only.iter().any(|x| p.starts_with(x)) {
            if write {
                return Verdict::deny(s, "这是只读目录，允许读但不允许改");
            }
            return Verdict::ok(s, false, "只读目录");
        }
        // 5. 允许根
        if self.roots.iter().any(|x| p.starts_with(x)) {
            return Verdict::ok(s, true, "在允许目录内");
        }
        // 6. 其它
        Verdict::deny(s, "不在允许目录内；可把所在目录加进工作区")
    }

    /// 便捷判断。
    pub fn can_read(&self, p: &str) -> bool {
        self.check_real(p, false).allowed
    }

    pub fn can_write(&self, p: &str) -> bool {
        self.check_real(p, true).allowed
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sb() -> Sandbox {
        let base = std::env::temp_dir().join("bai-sandbox-test");
        Sandbox::with(vec![base.clone()], vec![PathBuf::from("/etc")], vec![base.join("secret")])
    }

    #[test]
    fn inside_root_ok() {
        let base = std::env::temp_dir().join("bai-sandbox-test");
        let s = sb();
        assert!(s.check(&base.join("a/b.txt").to_string_lossy(), true).allowed);
    }

    #[test]
    fn escape_denied() {
        let s = sb();
        let p = std::env::temp_dir().join("bai-sandbox-test/../outside.txt");
        let v = s.check(&p.to_string_lossy(), true);
        assert!(!v.allowed);
    }

    #[test]
    fn read_only_cannot_write() {
        let s = sb();
        assert!(s.check("/etc/hosts", false).allowed);
        assert!(!s.check("/etc/hosts", true).allowed);
    }

    #[test]
    fn deny_list() {
        let base = std::env::temp_dir().join("bai-sandbox-test");
        let s = sb();
        assert!(!s.check(&base.join("secret/x").to_string_lossy(), false).allowed);
    }

    #[test]
    fn sensitive_beats_allow_root() {
        // 即使 home 在允许根里，~/.ssh 也要拒
        let home = pathx::home();
        let s = Sandbox::with(vec![home.clone()], vec![], vec![]);
        assert!(!s.check(&home.join(".ssh/id_rsa").to_string_lossy(), false).allowed);
    }

    #[test]
    fn explicit_allow_overrides_sensitive() {
        let home = pathx::home();
        let mut s = Sandbox::with(vec![home.clone()], vec![], vec![]);
        s.allow_path(home.join(".ssh"));
        assert!(s.check(&home.join(".ssh/id_rsa").to_string_lossy(), false).allowed);
    }
}
