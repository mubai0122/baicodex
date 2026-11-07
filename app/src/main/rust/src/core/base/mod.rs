//! 基础设施层：不认识任何业务名词。
//!
//! 这里的每个模块都应当能在任意 Rust 项目里原样复用。判断标准很简单——
//! 如果把它搬到别的项目要改代码，说明它不该放在这一层。

pub mod atomic;
pub mod bytes;
pub mod clock;
pub mod dirs;
pub mod error;
pub mod fsx;
pub mod ids;
pub mod jsonx;
pub mod log;
pub mod path;
pub mod sys;
pub mod text;

pub use error::{BError, BResult, ErrorCode};
