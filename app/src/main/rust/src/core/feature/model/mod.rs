//! 领域模型：只描述"东西长什么样"，不含任何行为。
//!
//! 这一层的文件都**不依赖** `storage` / `net` / `exec`。理由很实际：模型要被
//! SQL 映射、被 JSON 序列化、被 FFI 传给 Kotlin，任何一个方向都不该把数据库或
//! HTTP 客户端拖进来。
//!
//! | 文件 | 内容 |
//! |---|---|
//! | `message` | 消息、角色、工具调用、消息树分支字段 |
//! | `session` | 会话行、访问通道 [Route] |
//! | `mode` | [WorkMode] 权限档位、[AgentMode] 工作方式 |
//! | `provider` | 供应商、鉴权方式、模型能力 |
//!
//! 加字段的原则：**能算出来的不存**。`b_total` 能算，但它在列表里每条都要用，
//! 算一次存下来比每次聚合便宜；反之 `token 数` 能算，就不存——它随模型变。

pub mod message;
pub mod mode;
pub mod provider;
pub mod session;

pub use message::{ChatMsg, Role, ToolCall};
pub use mode::{AgentMode, WorkMode};
pub use provider::{AuthStyle, ModelCaps, Provider};
pub use session::{Route, SessionRow};
