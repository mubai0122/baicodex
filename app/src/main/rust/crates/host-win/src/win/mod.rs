//! Win32 这一侧的零件。
//!
//! ```text
//! window  ——  窗口类、消息循环、Host 状态机
//! frame   ——  无边框几何：NCCALCSIZE / MINMAXINFO / HITTEST
//! dpi     ——  缩放比与系统度量
//! tray    ——  托盘
//! ```
//!
//! 这里的代码只在 Windows 上编译（`lib.rs` 里整块 `#[cfg(windows)]`）。

pub mod dpi;
pub mod frame;
pub mod tray;
pub mod window;

pub use window::{request_devtools, request_reload, run, shell_open, Host, WM_APP_CMD};
