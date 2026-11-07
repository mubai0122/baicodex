//! 网络层：模型、搜索、12306、A2A、MCP、直发 HTTP。
//!
//! 这一层只管"把请求发出去、把响应变成人能读的东西"。**不做业务判断**
//! ——要不要发、发给谁、结果怎么用，都在 `core/feature` 里决定。
//!
//! ```text
//! client   HTTP 客户端复用（超时/UA 三档）
//! http     直发请求、下载、技能报文
//! llm      OpenAI 兼容客户端（SSE 流式 + 工具调用 + 多 key 轮换）
//! search   Tavily / Bing / Google CSE
//! rail     12306 余票 / 中转 / 经停
//! a2a      Agent2Agent 对端
//! mcp      MCP 客户端（stdio / http）
//! browser  静态抓页面（无渲染引擎）
//! relay    内网直连通道（给另一台机器按区间读写文件，自己带鉴权）
//! ```
//!
//! ## 一条纪律
//!
//! 所有对外请求都要有超时。没有超时的 `await` 在网络抖动时会把整轮对话挂死，
//! 用户看到的是"卡住了、点停止也没用"。默认值见 [client]。

pub mod a2a;
pub mod browser;
pub mod client;
pub mod http;
pub mod llm;
pub mod mcp;
pub mod rail;
pub mod relay;
pub mod search;

pub use llm::{ChatOptions, Llm};