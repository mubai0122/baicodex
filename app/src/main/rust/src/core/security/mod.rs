//! 安全层：回答三个问题。
//!
//! 1. **能不能做**（[policy]）——工具策略、风险分级、确认闸门。
//! 2. **能不能到那儿**（[perm] / [route] / [sandbox]）——权限档位、路径通道、越界检查。
//! 3. **数据放不放得住**（[guard] / [secret] / [vault] / [redact] / [audit]）——
//!    三层密钥、AES-256-GCM 落盘、凭据分桶、日志脱敏、审计链。
//!
//! 这一层**不认识模型、不认识会话、不认识工具的具体名字**。它只认抽象概念：
//! "某个动作、某个路径、某段文本"。这样换一个产品、换一套工具，这一层不用改。
//!
//! ## 为什么把加密放在这里，而不是 feature
//!
//! 因为"这份数据要不要加密"是安全决策，不是业务决策。业务层只说"存这段 JSON"，
//! 具体是明文还是密文、密钥从哪来，由这一层决定。否则迟早出现"某个新功能忘了加密"。

pub mod audit;
pub mod guard;
pub mod perm;
pub mod policy;
pub mod redact;
pub mod route;
pub mod sandbox;
pub mod secret;
pub mod vault;

pub use policy::{Decision, Policy, Risk};
