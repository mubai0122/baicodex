//! Root 通道：`su -c`。
//!
//! ## 为什么要探测 + 缓存
//!
//! `su` 在没授权时会**弹窗等用户点**。如果每次工具调用都先探一次，界面会被
//! 反复打脸；更糟的是探测本身会阻塞几秒。这里只在第一次真用时探一次，结果缓存
//! 30 秒，失败就明确告诉模型"没 root，换方案"。
//!
//! ## 为什么不加 `-m`/`--mount-master`
//!
//! 加了会把命令塞进 mount namespace，某些设备上 `/sdcard` 看不到。默认不加，
//! 需要时由调用方在命令里自己处理。

use std::sync::Mutex;
use std::time::{Duration, Instant};

use super::shell::{run_full, ShellResult, DEFAULT_CAP};

const PROBE_TTL: Duration = Duration::from_secs(30);

fn cache() -> &'static Mutex<Option<(Instant, bool)>> {
    static C: Mutex<Option<(Instant, bool)>> = Mutex::new(None);
    &C
}

/// 有没有可用的 root。
///
/// 探测命令极短（`id -u`），并且**不会**带 `-c` 之外的参数，避免某些 su 实现
/// 把它当成交互会话。
pub async fn available() -> bool {
    if let Ok(c) = cache().lock() {
        if let Some((at, ok)) = *c {
            if at.elapsed() < PROBE_TTL {
                return ok;
            }
        }
    }
    let ok = probe().await;
    if let Ok(mut c) = cache().lock() {
        *c = Some((Instant::now(), ok));
    }
    ok
}

async fn probe() -> bool {
    // 5 秒足够弹窗；再久就是没有 su 或用户没点，当作不可用。
    let r = run_full(&["su", "-c", "id -u"], None, Duration::from_secs(5), None, 4096, &[]).await;
    r.ok() && r.out.trim() == "0"
}

/// 清掉缓存（用户在设置里刚授过权时调）。
pub fn invalidate() {
    if let Ok(mut c) = cache().lock() {
        *c = None;
    }
}

/// 用 root 跑一条命令。
pub async fn run(cmd: &str, timeout: Duration) -> ShellResult {
    run_capped(cmd, timeout, DEFAULT_CAP).await
}

/// 用 root 跑一条命令，限制输出。
pub async fn run_capped(cmd: &str, timeout: Duration, cap: usize) -> ShellResult {
    if cmd.trim().is_empty() {
        return ShellResult::fail("命令为空");
    }
    let r = run_full(&["su", "-c", cmd], None, timeout, None, cap, &[]).await;
    if r.code == -1 && (r.out.contains("没有执行权限") || r.out.contains("命令不存在")) {
        // su 本身起不来：这不是命令的问题，是设备没 root。
        invalidate();
        return ShellResult::fail(format!("这台设备没有可用的 su（或没授权给本应用）：{}", r.out));
    }
    r
}

/// 用 root 读一个文件（`/data` 下那些普通身份读不到的）。
pub async fn read_file(path: &str, max: usize) -> ShellResult {
    let cmd = format!("cat {}", super::shell::quote(path));
    run_capped(&cmd, Duration::from_secs(30), max.max(4096)).await
}

/// 用 root 写一个文件。
///
/// 内容走 base64 管道：里面有换行、引号、NUL 以外任何东西都不会破坏命令。
pub async fn write_file(path: &str, content: &str) -> ShellResult {
    let b64 = crate::core::base::bytes::b64_encode(content.as_bytes());
    let cmd = format!(
        "mkdir -p $(dirname {p}) && printf %s {b} | base64 -d > {p}",
        p = super::shell::quote(path),
        b = super::shell::quote(&b64)
    );
    run(&cmd, Duration::from_secs(60)).await
}
