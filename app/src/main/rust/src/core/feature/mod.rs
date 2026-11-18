//! 功能层：业务能力按「块」分目录，一块一个职责，不再平铺成一堆 .rs。
//!
//! | 块           | 职责                                          |
//! |--------------|-----------------------------------------------|
//! | `store/`     | 凭据库 vault、配置库 prefs、版本化迁移 migrate |
//! | `memory/`    | 记忆：规范化 / 切词 / 打分 / 注入拼装          |
//! | `device/`    | 设备协议：米家（miot）                         |
//! | `compute/`   | 纯计算：滑杆几何、请求重试策略                 |
//! | `selfcheck/` | 自检：证明这一层真被链接进来并跑通一次完整链路 |
//!
//! 规则：块与块之间不互相 `use`，公共能力往下沉到 `base` / `security`。
//! 旧路径（`core::feature::vault` 之类）由下面几条 `pub use` 保持有效。

pub mod compute;
pub mod device;
pub mod memory;
pub mod selfcheck;
pub mod store;

//旧路径别名：crate::core::feature::{vault,prefs,migrate,miot} 继续可用
pub use device::miot;
pub use store::{migrate, prefs, vault};

pub use memory::{Hit, Item, KIND_LONG, KIND_NORMAL};
pub use store::prefs::Prefs;
pub use store::vault::{BucketInfo, BucketState, Vault};