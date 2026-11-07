//! 终端前端。
//!
//! 两个实现：`tui`（ratatui，默认）与 `repl`（纯行模式，`--no-tui` 或管道输入时用）。
//!
//! 这一层**只有渲染与按键**。所有行为都在内核里，前端不做判断：
//!
//! - 按键 → 调 `Facade` 的方法或发 `ffi::call`；
//! - 事件 → 更新本地缓存 → 重绘。
//!
//! 判断写在终端里是分叉的开始：手机上"点停止"与终端里"按 Esc"必须走同一条路。

pub mod format;
pub mod repl;

#[cfg(feature = "tui")]
pub mod tui;

pub use format::{kind_tag, one_line, wrap_block};
