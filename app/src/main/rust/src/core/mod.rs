//! 核心分类：新增的 Rust 能力按 base / security / feature / platform 归位，不要在 lib.rs 里堆业务
//!
//! 依赖只能向下：platform → feature → security → base，任何模块都不允许反向依赖。

pub mod base;
pub mod feature;
pub mod platform;
pub mod security;
