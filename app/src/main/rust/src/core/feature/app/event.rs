//! 界面事件。
//!
//! App 不直接画界面，也不认识 Kotlin 或 ratatui。它只广播 [AppEvent]，
//! 谁订阅谁负责渲染。终端用 `broadcast::Receiver` 收，Android 用 JNI 回调收。
//!
//! 事件是**增量**的：`Delta` 只带新增的几个字，不带全量文本。一屏几千字全量重发，
//! 在低端设备上会明显卡顿。

use serde::Serialize;

use crate::core::feature::model::message::ChatMsg;
use crate::core::feature::model::session::SessionRow;

/// 应用事件。
#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum AppEvent {
    /// 状态栏一句话。
    Status { text: String },
    /// 流式增量：正文与思考分开推。
    Delta { content: String, reason: String },
    /// 用户消息已落库。界面别自己"先画一条"，否则顺序会和真实入库顺序错开。
    UserMessage { id: i64, text: String, images: Vec<String> },
    /// 一条消息已落库（界面按 id 去重）。
    ///
    /// 装 `Box` 而不是裸 `ChatMsg`：事件走广播，必须 `Clone`，而 `ChatMsg`
    /// 带正文与图片列表、近 300 字节，每个订阅者都拷一遍不划算。
    Message { message: Box<ChatMsg> },
    /// 工具开始。
    ToolStart { seq: u64, name: String, args: String, from_sub: bool },
    /// 工具结束。
    ToolEnd { seq: u64 },
    /// 忙闲变化。
    Busy { busy: bool },
    /// 会话列表变化。
    Sessions { list: Vec<SessionRow> },
    /// 一次性提示。
    Notice { text: String },
    /// 需要确认工具调用。
    ConfirmRequest,
    /// 需要用户作答。
    AskRequest,
    /// 计划待确认。
    PlanRequest,
    /// 用量变化（一轮结束后刷新统计页）。
    Usage { prompt: i64, completion: i64 },
    /// 出错了。错误在这里就转成了给人看的字，界面直接显示，不要再拼一次。
    Error { code: String, message: String },
    /// 退出。
    Quit,
}

/// 全部事件名，顺序与 [`AppEvent::kind`] 一致。
///
/// 这是给跨语言生成器用的：`crates/proto` 拿它跟 TypeScript 声明比对，少一个
/// 就报错。加变体时编译器会逼你改 `kind()`，而这份清单就贴在它上面，很难漏。
pub const KINDS: &[&str] = &[
    "status",
    "delta",
    "user_message",
    "message",
    "tool_start",
    "tool_end",
    "busy",
    "sessions",
    "notice",
    "confirm_request",
    "ask_request",
    "plan_request",
    "usage",
    "error",
    "quit",
];

impl AppEvent {
    /// 事件名，给日志用。
    pub fn kind(&self) -> &'static str {
        match self {
            AppEvent::Status { .. } => "status",
            AppEvent::Delta { .. } => "delta",
            AppEvent::UserMessage { .. } => "user_message",
            AppEvent::Message { .. } => "message",
            AppEvent::ToolStart { .. } => "tool_start",
            AppEvent::ToolEnd { .. } => "tool_end",
            AppEvent::Busy { .. } => "busy",
            AppEvent::Sessions { .. } => "sessions",
            AppEvent::Notice { .. } => "notice",
            AppEvent::ConfirmRequest => "confirm_request",
            AppEvent::AskRequest => "ask_request",
            AppEvent::PlanRequest => "plan_request",
            AppEvent::Usage { .. } => "usage",
            AppEvent::Error { .. } => "error",
            AppEvent::Quit => "quit",
        }
    }
}

/// 事件接收端。
///
/// 除了 broadcast，App 还允许挂一个"直连"接收器：JNI 场景下 Kotlin 侧注册一个
/// 回调，比让它自己开线程轮询广播省事，也少一层缓冲延迟。
pub trait EventSink: Send + Sync {
    fn on_event(&self, event: AppEvent);
}

/// 把事件转成 JSON 行。JNI / C ABI 都走这个格式。
pub fn to_json(event: &AppEvent) -> String {
    serde_json::to_string(event).unwrap_or_else(|_| "{}".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serializes_with_type_tag() {
        let e = AppEvent::Busy { busy: true };
        let s = to_json(&e);
        assert!(s.contains("\"type\":\"busy\""));
        assert!(s.contains("\"busy\":true"));
    }
}
