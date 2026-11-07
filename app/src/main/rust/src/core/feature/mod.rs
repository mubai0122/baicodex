//! 功能层：这个产品"要做什么"。
//!
//! 按条目分目录，每个目录一个主题，目录内按职责拆文件。新增功能时先想清楚
//! 它属于哪个条目；如果哪个都不属于，说明要么该新建条目，要么这个功能不该加。
//!
//! ```text
//! model      领域模型：消息、会话、模式、供应商
//! app        应用状态：App 句柄、事件、会话状态
//! config     配置：字段、加载、配置档
//! storage    持久化：建表、会话、消息树、记忆、用量
//! memory     长期记忆：打分召回
//! context    上下文：提示词、预算、压缩
//! agent      Agent 主循环：流式、轮换、计划、分支
//! tool       工具：注册、定义、分发、治理，以及分类实现
//! skill      技能：正文型技能与技能包
//! mcp        MCP：JSON-RPC / REST 两种接入
//! workflow   工作流：步骤、重试、定时
//! subagent   子智能体：并行拆分任务
//! persona    人设
//! usage      用量与花费
//! image      图片：MIME、数据 URL、截图
//! plugin     插件市场清单
//! js         技能包 JS 运行时
//! ```

pub mod agent;
pub mod app;
pub mod config;
pub mod context;
pub mod image;
pub mod js;
pub mod mcp;
pub mod memory;
pub mod model;
pub mod persona;
pub mod plugin;
pub mod skill;
pub mod storage;
pub mod subagent;
pub mod tool;
pub mod usage;
pub mod workflow;
