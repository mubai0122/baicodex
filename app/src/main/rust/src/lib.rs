//! # baicodex 内核
//!
//! 一套 Rust 逻辑层，三种前端：终端 TUI、Android（Kotlin UI + JNI）、以及任何愿意
//! 通过 C ABI 说话的进程。**界面不在这个 crate 里**——`ui` 模块只是终端前端的一个
//! 可选实现，关掉 `tui` feature 后整个库不依赖任何终端库。
//!
//! ## 分层（从上到下，依赖只能向下，禁止反向）
//!
//! ```text
//! app                 应用服务层：把下面几层装配成"一次会话"
//! ├── core/feature    功能相关：模型、配置、存储、记忆、上下文、Agent、工具、技能、
//! │                   MCP、工作流、子智能体、人设、用量、图片、插件、JS
//! ├── core/security   安全相关：策略、数据保障（三层密钥）、加密落盘、凭据、权限、
//! │                   零宽路由、沙箱、脱敏、审计
//! ├── core/base       基础设施：错误、时间、文本、ID、路径、原子文件、JSON、字节、系统
//! ├── exec            系统执行适配：shell / 文件 / 工作区 / root / shizuku / linux /
//! │                   ssh / 无障碍 / 剪贴板 / 通知 / 闹钟 / 浏览器 / APK
//! └── net             HTTP / LLM / 搜索 / 12306 / A2A
//! ```
//!
//! ## 三条硬规则
//!
//! 1. **`core/base` 不认识业务**。它只做字符串、时间、文件、错误这类任何人都要用的活。
//! 2. **`core/security` 不认识模型**。它只回答"这个动作允不允许、这份数据加没加密"。
//! 3. **`core/feature` 不直接碰平台**。要执行命令找 `exec`，要发请求找 `net`，
//!    要读配置找 `core::feature::config`。平台差异被挡在 `exec` 里。
//!
//! ## 给 Kotlin / C++ 的契约
//!
//! 所有跨语言调用都走 [ffi]：入参出参只有一个 JSON 字符串，失败也返回 JSON
//! （`{"ok":false,"code":"...","message":"..."}`），不抛异常、不裸传指针。
//! Kotlin 侧见 `b/ai/native/NativeBridge.kt`，C++ 侧见 `app/src/main/cpp/rust_bridge.h`。

#![deny(rust_2018_idioms)]
#![warn(missing_debug_implementations)]

pub mod app;
pub mod core;
pub mod exec;
pub mod ffi;
pub mod net;
pub mod serve;
pub mod ui;

/// 内核版本。`MAJOR` 变更表示 FFI 契约不兼容。
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// 给前端做"关于"页用的一行信息。
pub fn build_info() -> BuildInfo {
    BuildInfo {
        name: env!("CARGO_PKG_NAME"),
        version: VERSION,
        arch: std::env::consts::ARCH,
        os: std::env::consts::OS,
        features: cfg!(feature = "tui"),
    }
}

/// 构建信息（可序列化，直接走 FFI）。
#[derive(Debug, Clone, serde::Serialize)]
pub struct BuildInfo {
    pub name: &'static str,
    pub version: &'static str,
    pub arch: &'static str,
    pub os: &'static str,
    #[serde(skip)]
    pub features: bool,
}

/// 常用类型的集中出口。
///
/// 只导出"跨层都要用"的东西；业务类型请从各自模块取，别在这里堆成杂物间。
pub mod prelude {
    pub use crate::core::base::error::{BError, BResult, ErrorCode};
    pub use crate::core::base::ids::IdGen;
    pub use crate::core::base::text::Text;
    pub use crate::core::feature::model::message::{ChatMsg, Role, ToolCall};
    pub use crate::core::feature::model::mode::{AgentMode, WorkMode};
    pub use crate::core::feature::model::session::{Route, SessionRow};
    pub use crate::core::security::policy::Policy;
}