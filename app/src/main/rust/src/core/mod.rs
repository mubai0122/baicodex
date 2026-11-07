//! 内核主体。三个子层按职责切分，依赖方向单一：
//!
//! ```text
//! feature  ──▶ security ──▶ base
//!    │            │           ▲
//!    └────────────┴───────────┘
//! ```
//!
//! 写新代码时的判断题：
//!
//! - 换个产品也能用 → `base`
//! - 跟"能不能做、安不安全、加不加密"有关 → `security`
//! - 跟"这个产品要做什么"有关 → `feature`
//!
//! 反面例子（都算屎山）：
//! - 在 `base/text.rs` 里读配置
//! - 在 `security/policy.rs` 里拼提示词
//! - 在 `feature/tool/fs.rs` 里直接 `std::fs::write`（应走 `exec::file_ops`）

pub mod base;
pub mod feature;
pub mod security;
