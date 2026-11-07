//! 顶部胶囊：屏幕操作时显示"正在做什么"。
//!
//! `screen start` 常驻，`quiet` 收起。真正的悬浮窗由平台层画（Android 侧是
//! Kotlin 的 WindowManager，桌面端是 TUI 状态行），这一层只管状态。
//!
//! ## 为什么不做成订阅
//!
//! 胶囊文案变化频率是"每次工具调用一次"，UI 每帧读一次完全够。为这个引入一套
//! 事件订阅，代价是生命周期管理 + 潜在的内存泄漏，不值。

use std::sync::{Mutex, OnceLock};

/// 胶囊状态。
#[derive(Debug, Clone, Default)]
pub struct State {
    pub text: String,
    /// 常驻（`screen start` 之后）。
    pub pinned: bool,
    pub visible: bool,
    /// 开始时间（毫秒），UI 可据此显示已持续多久。
    pub since: i64,
}

fn slot() -> &'static Mutex<State> {
    static S: OnceLock<Mutex<State>> = OnceLock::new();
    S.get_or_init(|| Mutex::new(State::default()))
}

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut g = slot().lock().unwrap_or_else(|e| e.into_inner());
    f(&mut g)
}

/// 常驻显示。
pub fn begin(text: &str) {
    let t = text.trim();
    with(|s| {
        s.text = if t.is_empty() { "正在处理".into() } else { t.to_string() };
        s.pinned = true;
        s.visible = true;
        s.since = crate::core::base::clock::now_ms();
    });
}

/// 临时显示一次。
///
/// 常驻状态下只更新文案、不改 `pinned`：用户点了"常驻"就不该被一次普通工具调用收起。
pub fn show(text: &str) {
    let t = text.trim();
    with(|s| {
        if t.is_empty() {
            return;
        }
        s.text = t.to_string();
        s.visible = true;
        if !s.pinned {
            s.since = crate::core::base::clock::now_ms();
        }
    });
}

/// 收起（非常驻时生效）。
pub fn hide() {
    with(|s| {
        if !s.pinned {
            s.visible = false;
        }
    });
}

/// 彻底收起并清空。
pub fn end() {
    with(|s| {
        s.pinned = false;
        s.visible = false;
        s.text.clear();
        s.since = 0;
    });
}

/// 读当前状态（UI 每帧调）。
pub fn current() -> State {
    with(|s| s.clone())
}

/// 当前应该显示的文案；不该显示时返回空串。
pub fn display() -> String {
    with(|s| if s.visible { s.text.clone() } else { String::new() })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pinned_survives_hide() {
        begin("正在点击");
        hide();
        assert!(current().visible);
        end();
        assert!(!current().visible);
    }
}
