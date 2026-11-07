//! 系统与环境探测。
//!
//! 这里回答的是"我在什么机器上、有什么资源"，不回答"用户是谁、在用哪个模型"。
//! 后者属于 `core::feature::config`。
//!
//! Android 与桌面 Linux 的差异尽量在这一层吃掉：上层只看到 `is_android()` 和
//! 一组统一的能力查询，不写 `cfg!(target_os)` 散落各处。

use std::path::{Path, PathBuf};

/// 运行平台：`android` / `linux` / `macos` / `windows` / `其他`。
pub fn os() -> &'static str {
    if is_android() {
        "android"
    } else if cfg!(target_os = "linux") {
        "linux"
    } else if cfg!(target_os = "macos") {
        "macos"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else {
        "unknown"
    }
}

/// 是否在 Android 上（Termux、proot、原生 App 都算）。
pub fn is_android() -> bool {
    if std::env::var_os("ANDROID_ROOT").is_some() || std::env::var_os("ANDROID_DATA").is_some() {
        return true;
    }
    Path::new("/system/build.prop").exists()
}

/// 是否在容器/沙箱里（proot、docker）。
pub fn is_container() -> bool {
    if Path::new("/.dockerenv").exists() {
        return true;
    }
    std::fs::read_to_string("/proc/1/cgroup")
        .map(|s| s.contains("docker") || s.contains("lxc") || s.contains("proot"))
        .unwrap_or(false)
}

/// 用户名。拿不到就 `user`。
pub fn user() -> String {
    std::env::var("USER")
        .or_else(|_| std::env::var("LOGNAME"))
        .unwrap_or_else(|_| "user".into())
}

/// 主机名。
pub fn hostname() -> String {
    std::fs::read_to_string("/proc/sys/kernel/hostname")
        .map(|s| s.trim().to_string())
        .or_else(|_| std::env::var("HOSTNAME"))
        .unwrap_or_else(|_| "localhost".into())
}

/// 临时目录。Android 上没有 `/tmp` 时退回家目录下的 `.cache/tmp`。
pub fn tmp_dir() -> PathBuf {
    let t = std::env::temp_dir();
    if t.is_dir() {
        return t;
    }
    let fallback = crate::core::base::path::home().join(".cache/tmp");
    let _ = std::fs::create_dir_all(&fallback);
    fallback
}

/// CPU 核数。至少 1。
pub fn cpus() -> usize {
    std::thread::available_parallelism().map(|n| n.get()).unwrap_or(1).max(1)
}

/// 内存信息（KB）。
#[derive(Debug, Clone, Copy, Default)]
pub struct MemInfo {
    pub total_kb: u64,
    pub available_kb: u64,
}

/// 读 `/proc/meminfo`。读不到返回 0。
pub fn mem_info() -> MemInfo {
    let mut m = MemInfo::default();
    if let Ok(s) = std::fs::read_to_string("/proc/meminfo") {
        for line in s.lines() {
            let mut it = line.split_whitespace();
            match (it.next(), it.next()) {
                (Some("MemTotal:"), Some(v)) => m.total_kb = v.parse().unwrap_or(0),
                (Some("MemAvailable:"), Some(v)) => m.available_kb = v.parse().unwrap_or(0),
                _ => {}
            }
        }
    }
    m
}

/// 电池：`(百分比, 是否充电)`。桌面没有电池时返回 None。
pub fn battery() -> Option<(u8, bool)> {
    // Android / Linux 的标准 sysfs 路径
    let dirs = [
        "/sys/class/power_supply/battery",
        "/sys/class/power_supply/BAT0",
        "/sys/class/power_supply/BAT1",
    ];
    for d in dirs {
        let cap = std::fs::read_to_string(format!("{d}/capacity"))
            .ok()
            .and_then(|s| s.trim().parse::<u8>().ok());
        if let Some(p) = cap {
            let status = std::fs::read_to_string(format!("{d}/status")).unwrap_or_default();
            let charging = status.trim().eq_ignore_ascii_case("charging")
                || status.trim().eq_ignore_ascii_case("full");
            return Some((p.min(100), charging));
        }
    }
    None
}

/// 系统运行时长（秒）。
pub fn uptime_s() -> u64 {
    std::fs::read_to_string("/proc/uptime")
        .ok()
        .and_then(|s| s.split_whitespace().next().and_then(|x| x.parse::<f64>().ok()))
        .map(|f| f as u64)
        .unwrap_or(0)
}

/// 在 PATH 里找可执行文件。
pub fn which(bin: &str) -> Option<PathBuf> {
    if bin.contains('/') {
        let p = PathBuf::from(bin);
        return if is_executable(&p) { Some(p) } else { None };
    }
    let path = std::env::var_os("PATH")?;
    for dir in std::env::split_paths(&path) {
        let p = dir.join(bin);
        if is_executable(&p) {
            return Some(p);
        }
    }
    None
}

/// 是否可执行。
pub fn is_executable(p: impl AsRef<Path>) -> bool {
    let p = p.as_ref();
    if !p.is_file() {
        return false;
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        p.metadata()
            .map(|m| m.permissions().mode() & 0o111 != 0)
            .unwrap_or(false)
    }
    #[cfg(not(unix))]
    {
        true
    }
}

/// 一组命令里第一个存在的。用于探测 `su` / `rish` / `proot` 这类可选工具。
pub fn first_available(bins: &[&str]) -> Option<(String, PathBuf)> {
    for b in bins {
        if let Some(p) = which(b) {
            return Some(((*b).to_string(), p));
        }
    }
    None
}

/// 进程是否还活着。
pub fn pid_alive(pid: u32) -> bool {
    Path::new(&format!("/proc/{pid}")).exists()
}

/// 当前进程 id。
pub fn pid() -> u32 {
    std::process::id()
}

/// 给子进程用的环境变量白名单。
///
/// 不直接继承 `std::env::vars()`：里面可能有 `OPENAI_API_KEY`、`AWS_SECRET_ACCESS_KEY`
/// 这类东西。模型跑一条 `env` 就能把它们全读走。只放行"程序正常运行必需"的。
pub fn safe_env() -> Vec<(String, String)> {
    const KEEP: &[&str] = &[
        "PATH", "HOME", "USER", "LOGNAME", "SHELL", "LANG", "LC_ALL", "LC_CTYPE", "TERM",
        "TMPDIR", "TZ", "PWD", "ANDROID_ROOT", "ANDROID_DATA", "ANDROID_STORAGE", "EXTERNAL_STORAGE",
        "BAICODE_HOME",
    ];
    let mut out = Vec::new();
    for k in KEEP {
        if let Ok(v) = std::env::var(k) {
            out.push(((*k).to_string(), v));
        }
    }
    // 补充一个最小 PATH，防止父进程 PATH 为空导致 ls/cat 都找不到
    if !out.iter().any(|(k, _)| k == "PATH") {
        out.push(("PATH".into(), "/system/bin:/system/xbin:/usr/bin:/bin".into()));
    }
    out
}

/// 设备一句话描述。提示词里告诉模型"你在什么机器上"，能显著减少它乱猜路径。
pub fn device_line() -> String {
    let os = os();
    let mut s = format!("{os} {}", std::env::consts::ARCH);
    if let Some((p, ch)) = battery() {
        s.push_str(&format!("，电量 {p}%{}", if ch { "（充电中）" } else { "" }));
    }
    if let Ok(model) = std::env::var("BAICODE_DEVICE") {
        if !model.trim().is_empty() {
            s.push_str(&format!("，{model}"));
        }
    }
    s
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn basics_are_sane() {
        assert!(!os().is_empty());
        assert!(cpus() >= 1);
        assert!(tmp_dir().exists() || !tmp_dir().as_os_str().is_empty());
    }

    #[test]
    fn which_self() {
        // sh 在绝大多数环境里都存在
        assert!(which("sh").is_some() || which("bash").is_some());
    }
}
