//! 基类层：与业务无关的地基
//!
//! bytes/kv/marks/text                     字节、KV 文本、掩码写入、扫描游走
//! err/fsx/key/store/sealed/clock/ids      内核存储地基：唯一落盘原语 + 唯一写入入口

pub mod bytes;
pub mod clock;
pub mod err;
pub mod fsx;
pub mod ids;
pub mod key;
pub mod kv;
pub mod marks;
pub mod sealed;
pub mod store;
pub mod text;

pub use err::{BxError, BxResult};
pub use key::Key;
pub use store::{MemStore, Store};
