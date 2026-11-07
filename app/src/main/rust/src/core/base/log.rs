//! 日志。
//!
//! 两个约束决定了这里不用 `tracing-subscriber` 那一套：
//!
//! 1. **库不该决定日志往哪写**。宿主可能想写到文件、想弹到界面、想干脆丢掉。
//! 2. **Android 上没有 stderr**。`eprintln!` 会被吞掉，出问题时什么都看不到。
//!
//! 所以这里维护一个固定容量的环形缓冲，前端按需取（TUI 的"诊断"页、Kotlin 的
//! 日志面板都读它），同时可选地把同样内容打到 stderr。
//!
//! 注意：**写进这里的消息必须是脱敏后的**。脱敏在 `core::security::redact`，
//! 这里不认识密钥、token 长什么样。

use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, OnceLock};

/// 日志级别。数值越大越严重，方便过滤。
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    Debug = 0,
    Info = 1,
    Warn = 2,
    Error = 3,
}

impl Level {
    pub fn as_str(self) -> &'static str {
        match self {
            Level::Debug => "DEBUG",
            Level::Info => "INFO",
            Level::Warn => "WARN",
            Level::Error => "ERROR",
        }
    }
}

/// 一条日志。
#[derive(Debug, Clone)]
pub struct Entry {
    pub at: i64,
    pub level: Level,
    pub target: String,
    pub message: String,
}

struct Ring {
    cap: usize,
    items: Mutex<VecDeque<Entry>>,
    console: AtomicBool,
    min: Mutex<Level>,
}

fn ring() -> &'static Ring {
    static R: OnceLock<Ring> = OnceLock::new();
    R.get_or_init(|| Ring {
        cap: 512,
        items: Mutex::new(VecDeque::with_capacity(512)),
        console: AtomicBool::new(false),
        min: Mutex::new(Level::Info),
    })
}

/// 是否同时打到 stderr。二进制入口在启动时打开；库默认关。
pub fn set_console(on: bool) {
    ring().console.store(on, Ordering::Relaxed);
}

/// 设置最低级别。
pub fn set_min(level: Level) {
    if let Ok(mut m) = ring().min.lock() {
        *m = level;
    }
}

/// 记录一条。target 建议用 `模块/子模块`，比如 `agent/loop`。
pub fn record(level: Level, target: &str, message: impl Into<String>) {
    let min = ring().min.lock().map(|m| *m).unwrap_or(Level::Info);
    if level < min {
        return;
    }
    let msg = message.into();
    let e = Entry {
        at: crate::core::base::clock::now_ms(),
        level,
        target: target.to_string(),
        message: msg.clone(),
    };
    if let Ok(mut q) = ring().items.lock() {
        if q.len() >= ring().cap {
            q.pop_front();
        }
        q.push_back(e);
    }
    if ring().console.load(Ordering::Relaxed) {
        eprintln!("[{}] {} {}", level.as_str(), target, msg);
    }
}

/// 取最近 `n` 条（时间正序）。
pub fn tail(n: usize) -> Vec<Entry> {
    let q = match ring().items.lock() {
        Ok(q) => q,
        Err(p) => p.into_inner(),
    };
    let start = q.len().saturating_sub(n);
    q.iter().skip(start).cloned().collect()
}

/// 清空缓冲。
pub fn clear() {
    if let Ok(mut q) = ring().items.lock() {
        q.clear();
    }
}

/// 导出成文本，给"复制诊断信息"按钮用。
pub fn dump(n: usize) -> String {
    let mut s = String::new();
    for e in tail(n) {
        s.push_str(&crate::core::base::clock::fmt_minute(e.at));
        s.push(' ');
        s.push_str(e.level.as_str());
        s.push(' ');
        s.push_str(&e.target);
        s.push(' ');
        s.push_str(&e.message);
        s.push('\n');
    }
    s
}

#[macro_export]
macro_rules! log_debug {
    ($target:expr, $($arg:tt)*) => {
        $crate::core::base::log::record($crate::core::base::log::Level::Debug, $target, format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_info {
    ($target:expr, $($arg:tt)*) => {
        $crate::core::base::log::record($crate::core::base::log::Level::Info, $target, format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_warn {
    ($target:expr, $($arg:tt)*) => {
        $crate::core::base::log::record($crate::core::base::log::Level::Warn, $target, format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_error {
    ($target:expr, $($arg:tt)*) => {
        $crate::core::base::log::record($crate::core::base::log::Level::Error, $target, format!($($arg)*))
    };
}
