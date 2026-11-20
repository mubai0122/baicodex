/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:28
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 存储块：内核的「唯一写入路径」三件套。
//!
//! - `vault`   凭据库：分桶存密文，签名 + schema 版本 + 前后对账
//! - `prefs`   配置库：按页存 JSON，和 vault 共用同一个加密后端
//! - `migrate` 版本化迁移：旧格式读一次、按新格式重写，失败整步回滚
//!
//! 三者都只依赖 `core::base` 的抽象（`Store` / `Key` / `SealedStore`），
//! 不认识任何平台 API —— 换平台只换 `core::platform`。

pub mod migrate;
pub mod prefs;
pub mod vault;

pub use migrate::{import_legacy_secrets, Migrator, Step};
pub use prefs::Prefs;
pub use vault::{BucketInfo, BucketState, Vault};