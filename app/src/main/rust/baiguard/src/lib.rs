//! baiguard：Rust 侧基础层，目录按 base / security / feature 三分类，C ABI 全部收在 ffi。
//!
//! core/base       与业务无关的字节、文本、掩码写入工具
//! core/security   安全相关：数据输出脱敏、常量时间比较、异或
//! core/feature    功能相关：自检
//! ffi             纯 C ABI 薄层，只做指针与切片互转，禁止写业务逻辑
//!
//! 新增能力一律往 core 下三分类里放，lib.rs 只留模块声明。

pub mod core;
pub mod ffi;