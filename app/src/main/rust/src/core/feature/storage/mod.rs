//! 持久化层。
//!
//! 一个 [Db] 句柄，四个领域的读写方法（在各自文件里给 `Db` 加 `impl` 块）：
//!
//! | 文件 | 领域 |
//! |---|---|
//! | `schema` | 连接、建表、迁移、元信息 |
//! | `session_repo` | 会话头 |
//! | `message_repo` | 消息树 |
//! | `memory_repo` | 长期记忆 |
//! | `usage_repo` | 用量账本 |
//!
//! 为什么不给每张表建一个 `Repository` 结构体：它们共用同一个连接、同一个事务，
//! 拆成多个对象只会让"我要在一个事务里同时写消息和会话"变得别扭。

pub mod memory_repo;
pub mod message_repo;
pub mod schema;
pub mod session_repo;
pub mod usage_repo;

pub use schema::{Db, VERSION};

use crate::core::base::error::BResult;

/// 打开默认库（`<base>/bai.db`）。
pub fn open(base: impl AsRef<std::path::Path>) -> BResult<Db> {
    Db::open(base.as_ref().join("bai.db"))
}

/// 库文件大小（MB）。
pub fn size_mb(db: &Db) -> f64 {
    match db.path() {
        Some(p) => std::fs::metadata(p).map(|m| m.len() as f64 / 1024.0 / 1024.0).unwrap_or(0.0),
        None => 0.0,
    }
}
