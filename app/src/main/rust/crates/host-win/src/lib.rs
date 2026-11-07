//! Windows 原生宿主。
//!
//! ## 这一层负责什么
//!
//! 只有六件事，别的一概不做：
//!
//! ```text
//! window    窗口生命周期、消息循环、无边框几何
//! backdrop  系统毛玻璃（Mica / Acrylic / 降级）
//! webview   WebView2 实例、透明背景、尺寸跟随
//! bridge    JSON 协议：前端 ↔ 内核，外加事件推送
//! tray      托盘图标与菜单
//! app       装配顺序与退出码
//! ```
//!
//! ## 这一层**不**负责什么
//!
//! - 不做业务判断（哪个工具能跑、上下文怎么裁）——那是 `baicodex` 内核的事；
//! - 不画画（按钮长什么样、玻璃有多厚）——那是 `web/` 前端的事；
//! - 不存业务数据——内核自己管 sqlite 和加密库。
//!
//! 判断标准很简单：**换个平台（macOS / Linux）要重写的部分，才属于这一层。**

#![cfg_attr(not(windows), allow(dead_code, unused))]

#[cfg(windows)]
pub mod app;
#[cfg(windows)]
pub mod backdrop;
#[cfg(windows)]
pub mod bridge;
#[cfg(windows)]
pub mod cli;
#[cfg(windows)]
pub mod error;
#[cfg(windows)]
pub mod webview;
#[cfg(windows)]
pub mod win;

/// 简单日志：带时间戳写到 stderr。桌面端没有终端时会被丢弃，不影响功能。
#[cfg(windows)]
pub fn log_line(s: &str) {
    eprintln!("[{}] {s}", chrono_like_now());
}

#[cfg(windows)]
fn chrono_like_now() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let secs = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    // 只到秒，够用；格式化交给自己，避免为一行日志引 chrono
    let (h, m, s) = ((secs / 3600) % 24, (secs / 60) % 60, secs % 60);
    format!("{h:02}:{m:02}:{s:02}")
}

/// 非 Windows 上的占位：让 `cargo check --workspace` 在 Linux CI 上也能过。
pub fn unsupported() -> &'static str {
    "baicodex-desktop 只能在 Windows 上运行。Linux/macOS 请用 `baicodex` 终端版或 Android 客户端。"
}
