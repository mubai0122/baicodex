//! 执行层：把"要做的事"落到具体进程、文件、屏幕、设备上。
//!
//! 这一层是**唯一**允许碰平台差异的地方。`feature` 里想跑命令，只能经过
//! [shell]；想改文件，只能经过 [file_ops]。绕过它们直接 `std::process::Command`
//! 或 `std::fs::write` 会在三个地方同时出问题：沙箱失效、路由失效、审计失效。
//!
//! ```text
//! shell        起进程（三通道共用）
//! ├── root     su -c
//! ├── shizuku  rish / adb shell
//! └── linux    proot / chroot / bwrap
//! workspace    工作区绑定与索引
//! file_ops     文件读写（路由 + 沙箱 + 编码）
//! a11y         屏幕：控件树 / 截图 / 手势
//! capsule      顶部胶囊状态
//! device       剪贴板 / 通知 / 打开链接
//! apk          安装包解析
//! alarms       定时表达式解析
//! ssh          远程主机
//! js           JS 脚本
//! ```
//!
//! ## 加一个后端要遵守什么
//!
//! 1. 入参出参用本层的结构体（[ShellResult] / [file_ops::Out]），不要返回平台原始错误；
//! 2. 缺依赖、缺权限时返回**可读的失败原因**，让模型能换方案，而不是 `Err(Os { .. })`；
//! 3. 有超时的都要有上限，宁可截断也不要挂住整轮对话。

pub mod a11y;
pub mod adb;
pub mod alarms;
pub mod apk;
pub mod capsule;
pub mod device;
pub mod file_ops;
pub mod js;
pub mod linux;
pub mod root;
pub mod shell;
pub mod shizuku;
pub mod ssh;
pub mod workspace;

pub use file_ops::{FileOps, Out};
pub use shell::{run as sh, ShellResult};