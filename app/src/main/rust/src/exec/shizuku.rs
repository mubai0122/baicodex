//! Shizuku 通道：借 UID 2000（shell）执行。
//!
//! 桌面端对应的是"有 adb shell 权限"的场景。三条入口按优先级找：
//!
//! 1. `rish` —— Shizuku 官方 shell，参数直接跟命令；
//! 2. `adb shell` —— 已连 adb 的桌面 / 无线调试；
//! 3. `rish_shizuku` —— 部分旧版 Shizuku 的脚本名。
//!
//! 找到一个就缓存；用的时候失败了（比如 Shizuku 服务被杀）会作废缓存，
//! 下次重新找，避免"明明重连了却一直报未连接"。

use std::sync::Mutex;
use std::time::Duration;

use super::shell::{run_full, ShellResult, DEFAULT_CAP};

/// 入口形态。不同入口的参数排列不一样，用一个枚举记住，不要拼字符串。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Entry {
    /// `rish -c "<cmd>"`
    Rish,
    /// `rish "<cmd>"`
    RishPlain,
    /// `adb shell "<cmd>"`
    Adb,
}

impl Entry {
    fn argv(self) -> Vec<&'static str> {
        match self {
            Entry::Rish => vec!["rish", "-c"],
            Entry::RishPlain => vec!["rish"],
            Entry::Adb => vec!["adb", "shell"],
        }
    }
}

fn cache() -> &'static Mutex<Option<Option<Entry>>> {
    static C: Mutex<Option<Option<Entry>>> = Mutex::new(None);
    &C
}

/// 探测并缓存入口。`None` 表示三重都不可用。
async fn entry() -> Option<Entry> {
    if let Ok(c) = cache().lock() {
        if let Some(v) = *c {
            return v;
        }
    }
    let found = probe().await;
    if let Ok(mut c) = cache().lock() {
        *c = Some(found);
    }
    found
}

async fn probe() -> Option<Entry> {
    for e in [Entry::Rish, Entry::RishPlain, Entry::Adb] {
        let mut argv = e.argv();
        argv.push("id");
        let r = run_full(&argv, None, Duration::from_secs(8), None, 4096, &[]).await;
        // adb 在没连设备时会返回非 0 且带 "no devices"，那也是"不可用"。
        if r.ok() && (r.out.contains("uid=2000") || r.out.contains("uid=0")) {
            return Some(e);
        }
    }
    None
}

/// 通道是否可用。
pub async fn available() -> bool {
    entry().await.is_some()
}

/// 作废缓存（连接状态可能变了）。
pub fn invalidate() {
    if let Ok(mut c) = cache().lock() {
        *c = None;
    }
}

/// 跑一条命令。
pub async fn run(cmd: &str, timeout: Duration) -> ShellResult {
    run_capped(cmd, timeout, DEFAULT_CAP).await
}

/// 跑一条命令，限制输出。
pub async fn run_capped(cmd: &str, timeout: Duration, cap: usize) -> ShellResult {
    if cmd.trim().is_empty() {
        return ShellResult::fail("命令为空");
    }
    let Some(e) = entry().await else {
        return ShellResult::fail(
            "Shizuku 未连接：先在设备上启动 Shizuku 并授权本应用（桌面端可 adb connect 后用 adb shell）",
        );
    };
    let mut argv = e.argv();
    argv.push(cmd);
    let r = run_full(&argv, None, timeout, None, cap, &[]).await;
    if r.code == -1 && r.out.contains("命令不存在") {
        // rish 被卸载 / adb 掉线：清缓存，下次重新探测。
        invalidate();
        return ShellResult::fail("Shizuku 入口失效（rish 或 adb 不在了），已重置连接状态");
    }
    if r.out.contains("no devices") || r.out.contains("device offline") {
        invalidate();
        return ShellResult::fail("adb 没有在线设备");
    }
    r
}

/// 读文件。
pub async fn read_file(path: &str, max: usize) -> ShellResult {
    let cmd = format!("cat {}", super::shell::quote(path));
    run_capped(&cmd, Duration::from_secs(30), max.max(4096)).await
}

/// 写文件（base64 管道，内容不会破坏命令）。
pub async fn write_file(path: &str, content: &str) -> ShellResult {
    let b64 = crate::core::base::bytes::b64_encode(content.as_bytes());
    let cmd = format!(
        "mkdir -p $(dirname {p}) && printf %s {b} | base64 -d > {p}",
        p = super::shell::quote(path),
        b = super::shell::quote(&b64)
    );
    run(&cmd, Duration::from_secs(60)).await
}
