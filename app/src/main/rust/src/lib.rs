/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:30
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! baicodex：项目唯一的 Rust 内核，Android 与 Windows 共用。
//!
//! 位置：`app/src/main/rust/`，**这一层本身就是 crate 根**（Cargo.toml 直接在这，
//! 不再多套一层子目录）。内核可以在这里单独 `cargo test`，不依赖 Gradle。
//!
//! ```text
//! app/src/main/rust/            ← crate 根（Cargo.toml 在这，无嵌套子层）
//! ├── Cargo.toml
//! ├── tests/                    集成测试（崩溃一致性 / 单写路径 / FFI 冒烟等）
//! └── src/
//!     ├── lib.rs                模块声明 + 跨端常量（VERSION / SCHEMA），不放业务
//!     ├── core/                 业务能力，四层，依赖只能向下
//!     │   ├── base/             第 0 层：无依赖原语（字节/文本/Key/原子写/Store/信封）
//!     │   ├── security/         第 1 层：加解密、密钥来源、脱敏
//!     │   ├── platform/         第 1 层：宿主能力探测与数据根选址（平台差异只在这）
//!     │   └── feature/          第 2 层：业务块，一块一个目录
//!     │       ├── store/          凭据 vault / 配置 prefs / 迁移 migrate
//!     │       ├── memory/         记忆 model / rank
//!     │       ├── device/         设备协议 miot
//!     │       ├── compute/        纯计算 slider / retry
//!     │       └── selfcheck/      自检
//!     └── ffi/                  第 3 层：C ABI 薄壳，按域拆文件
//!         ├── store.rs            bx_open* / bx_get / bx_put / bx_prefs*
//!         ├── memory.rs           bx_mem_*
//!         ├── compute.rs          bx_slider_* / bx_retry_*
//!         ├── device.rs           bai_miot_*
//!         └── mod.rs              bai_xor / bai_ct_eq / bai_selftest / bai_mask*
//! ```
//!
//! 加新能力的落点判定：
//! 1. 纯数据变换、不认识平台 → `base`
//! 2. 涉及密钥、隐私、脱敏 → `security`
//! 3. 有业务语义（存什么、算什么）→ `feature/<块>/`，没有合适的块就新建一个目录
//! 4. 只在某一端存在的能力 → 放 `platform`，用 `cfg` 分支，别污染上面三层
//! 5. 要暴露给 Kotlin / C# → `ffi/<域>.rs`，只做翻译，不做判断

pub mod core;
pub mod ffi;

/// 内核版本，随移植来源走；宿主用它做 ABI 握手
pub const VERSION: &str = "2.0.0";

/// 存储格式代数。改信封格式或桶序列化方式时必须 +1，并补迁移
pub const SCHEMA: u32 = 2;

//内核地基的公开面：宿主与测试只认这几个名字，避免各端自己拼路径
pub use core::base::err::{BxError, BxResult, ErrorCode};
pub use core::base::key::Key;
pub use core::base::store::{MemStore, Store};