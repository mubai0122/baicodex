//! 能力探测的实现。
//!
//! 所有探测都带超时。`su` 在没有 root 的设备上会卡在授权弹窗，`uiautomator dump`
//! 在无障碍没开时会等几秒才失败——启动流程不能被这些拖住。
//!
//! 探测顺序也是有讲究的：先看环境变量（最快、零副作用），再看文件是否可读，
//! 最后才起子进程。90% 的启动路径不该 fork 任何东西。

use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

use super::Perms;

/// 探测一次。
pub fn detect() -> Perms {
    let android = crate::core::base::sys::is_android();
    let storage = can_list("/sdcard")
        || can_list("/storage/emulated/0")
        || !android;
    let all_files = can_list("/sdcard/Android/data")
        || can_list("/storage/emulated/0/Android/data")
        || !android;

    let root = detect_root();
    let shizuku = detect_shizuku();
    let a11y = detect_a11y(android);

    Perms {
        all_files,
        root,
        shizuku,
        a11y,
        notifications: detect_notify(android),
        mic: std::env::var("BAICODE_MIC").map(|v| v == "1").unwrap_or(!android),
        storage,
        at: crate::core::base::clock::now_ms(),
    }
}

fn can_list(p: &str) -> bool {
    std::fs::read_dir(p).is_ok()
}

fn detect_root() -> bool {
    if std::env::var("BAICODE_ROOT").map(|v| v == "1").unwrap_or(false) {
        return true;
    }
    let Some(out) = run_timeout("su", &["-c", "id"], 2500) else { return false };
    out.0 && out.1.contains("uid=0")
}

fn detect_shizuku() -> bool {
    if std::env::var_os("SHIZUKU_SERVER").is_some() {
        return true;
    }
    if crate::core::base::sys::which("rish").is_some() {
        return true;
    }
    // Shizuku 的 rish 常常只有一个 shell 函数，试一下
    run_timeout("sh", &["-c", "command -v rish || command -v shizuku"], 1200)
        .map(|(ok, _)| ok)
        .unwrap_or(false)
}

fn detect_a11y(android: bool) -> bool {
    if std::env::var("BAICODE_A11Y").map(|v| v == "1").unwrap_or(false) {
        return true;
    }
    if !android {
        // 桌面端没有系统级无障碍；屏幕操作走的是截图 + xdotool，另行探测
        return crate::core::base::sys::which("xdotool").is_some();
    }
    // Android：dump 一次能出 XML 就说明无障碍服务在线
    run_timeout("uiautomator", &["dump", "/dev/null"], 3000)
        .map(|(ok, _)| ok)
        .unwrap_or(false)
}

fn detect_notify(android: bool) -> bool {
    if android {
        return true; // 通知由 Kotlin 侧发，native 只负责转交
    }
    crate::core::base::sys::which("notify-send").is_some()
        || crate::core::base::sys::which("osascript").is_some()
}

/// 跑一个命令，最多等 `ms` 毫秒。返回 `(是否成功, stdout)`。
///
/// 用 `spawn` + 轮询而不是 `output()`：`output()` 会一直等到进程退出，
/// 没有超时能力，`su` 弹窗能把它挂死。
fn run_timeout(prog: &str, args: &[&str], ms: u64) -> Option<(bool, String)> {
    let mut child = Command::new(prog)
        .args(args)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;

    let start = Instant::now();
    let limit = Duration::from_millis(ms);
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                let mut buf = String::new();
                if let Some(mut out) = child.stdout.take() {
                    use std::io::Read;
                    let _ = out.read_to_string(&mut buf);
                }
                return Some((status.success(), buf));
            }
            Ok(None) => {
                if start.elapsed() >= limit {
                    let _ = child.kill();
                    let _ = child.wait();
                    return Some((false, String::new()));
                }
                std::thread::sleep(Duration::from_millis(20));
            }
            Err(_) => return None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timeout_returns_quickly() {
        let t = std::time::Instant::now();
        let r = run_timeout("sh", &["-c", "sleep 5"], 300);
        assert!(r.is_some());
        assert!(t.elapsed() < Duration::from_secs(3));
    }

    #[test]
    fn missing_binary_is_none() {
        assert!(run_timeout("definitely-not-a-real-binary-xyz", &[], 100).is_none());
    }
}
