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