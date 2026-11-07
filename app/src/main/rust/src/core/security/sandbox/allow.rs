//! 默认允许根与敏感路径表。
//!
//! 把"哪些地方能碰、哪些地方绝对不碰"集中成数据，而不是散落在各处 `if`。
//! 用户改配置时也是改这份表，不用改代码。
//!
//! 敏感路径（`~/.ssh`、`~/.gnupg`、浏览器 cookie 库）默认**既不读也不写**：
//! 模型没有理由去读用户的私钥，而一次误读就可能把私钥写进对话记录、发到模型厂商。
//! 用户确实需要时，可以在设置里显式放行单个路径。

use std::path::PathBuf;

use crate::core::base::path as pathx;

/// 数据根下的工作区名。
const WORKSPACE: &str = "workspace";

/// 默认允许读写的根。
///
/// 顺序有意义：越靠前越"亲近"，工具输出里展示相对路径时优先相对第一个根。
pub fn default_roots(base: impl AsRef<std::path::Path>) -> Vec<PathBuf> {
    let base = base.as_ref().to_path_buf();
    let mut v = vec![base.join(WORKSPACE), base.clone()];
    // 桌面端：当前目录也放行，否则 `baicodex` 在项目里跑起来什么都读不了
    if let Ok(cwd) = std::env::current_dir() {
        if !v.iter().any(|x| x == &cwd) {
            v.push(cwd);
        }
    }
    // Android 共享存储
    for p in ["/sdcard", "/storage/emulated/0"] {
        let pb = PathBuf::from(p);
        if pb.is_dir() && !v.iter().any(|x| x == &pb) {
            v.push(pb);
        }
    }
    v
}

/// 只读根：系统目录允许看，不允许改。模型排查问题时经常需要读这些。
pub fn default_read_only() -> Vec<PathBuf> {
    ["/system", "/vendor", "/product", "/usr", "/etc", "/opt", "/proc", "/sys"]
        .iter()
        .map(PathBuf::from)
        .filter(|p| p.is_dir())
        .collect()
}

/// 默认拒绝的路径（相对 home 展开）。
///
/// 这些不是"技术上有权限"，而是"没有正当理由去碰"。真需要时用户显式加白名单。
pub fn default_deny() -> Vec<PathBuf> {
    let home = pathx::home();
    let rel = [
        ".ssh",
        ".gnupg",
        ".aws",
        ".kube",
        ".config/gh",
        ".config/gcloud",
        ".netrc",
        ".git-credentials",
        ".mozilla",
        ".config/google-chrome",
        ".config/chromium",
        ".local/share/keyrings",
        "Library/Keychains",
    ];
    rel.iter().map(|r| home.join(r)).collect()
}

/// 系统级敏感文件：即使是 root 也不该让模型随手读写。
pub fn system_sensitive() -> Vec<&'static str> {
    vec![
        "/dev/mem",
        "/dev/kmem",
        "/dev/port",
        "/proc/kcore",
        "/proc/self/mem",
        "/sys/kernel/security",
        "/etc/shadow",
        "/etc/sudoers",
    ]
}

/// 判断路径是否命中敏感表。
pub fn is_sensitive(p: impl AsRef<std::path::Path>) -> bool {
    let p = pathx::normalize(p);
    let s = p.to_string_lossy();
    if system_sensitive().iter().any(|x| {
        s == *x || s.starts_with(&format!("{x}/"))
    }) {
        return true;
    }
    for d in default_deny() {
        if p.starts_with(&d) {
            return true;
        }
    }
    // 凭据库与策略文件自身：模型不该读自己的密钥 / 权限配置
    if let Some(name) = p.file_name().and_then(|n| n.to_str()) {
        if name.starts_with("vault-") || name == "policy.json" {
            return true;
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ssh_is_sensitive() {
        let p = pathx::home().join(".ssh/id_rsa");
        assert!(is_sensitive(&p));
    }

    #[test]
    fn normal_file_is_not() {
        assert!(!is_sensitive("/tmp/hello.txt"));
    }

    #[test]
    fn system_files() {
        assert!(is_sensitive("/etc/shadow"));
        assert!(is_sensitive("/dev/mem"));
    }

    #[test]
    fn vault_files_are_sensitive() {
        assert!(is_sensitive("/home/u/.baicode/vault-api.json"));
    }
}
