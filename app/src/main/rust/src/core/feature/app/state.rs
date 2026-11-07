//! 运行态。
//!
//! 这里只放"随时会变"的东西：当前会话、消息列表、正在跑的工具、流式缓冲。
//! 配置、记忆、技能这些"读多写少"的放在各自模块的 `RwLock` 里，不塞进来——
//! 一个巨大的 `AppState` 是 bug 的温床。
//!
//! 一条纪律：**持有这把锁时不要 await**。所有 `oneshot` 的发送都在锁外做，
//! 否则界面线程来取状态时会卡住，表现为"点了没反应"。

use serde::{Deserialize, Serialize};
use tokio::sync::oneshot;

use crate::core::feature::model::message::{ChatMsg, ToolCall};
use crate::core::feature::model::session::{Route, SessionRow};

/// 提问的一个选项。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AskOption {
    pub label: String,
    #[serde(default)]
    pub desc: String,
}

/// 一个问题。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AskQuestion {
    #[serde(default)]
    pub id: String,
    pub question: String,
    #[serde(default)]
    pub options: Vec<AskOption>,
    /// 多选。
    #[serde(default)]
    pub multi: bool,
    /// 允许"其它"自由输入。
    #[serde(default)]
    pub other: bool,
}

impl AskQuestion {
    pub fn new(q: impl Into<String>, options: Vec<(&str, &str)>) -> Self {
        Self {
            id: String::new(),
            question: q.into(),
            options: options.into_iter().map(|(l, d)| AskOption { label: l.into(), desc: d.into() }).collect(),
            multi: false,
            other: true,
        }
    }
}

/// 挂起的提问。
pub struct AskReq {
    pub questions: Vec<AskQuestion>,
    pub tx: oneshot::Sender<Vec<String>>,
}

impl std::fmt::Debug for PendingTool {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // 参数不打印：里面常有 token / 密码。要排查用 audit 那条脱敏后的记录。
        f.debug_struct("PendingTool")
            .field("tool", &self.call.name)
            .field("route", &self.route)
            .field("high_risk", &self.high_risk)
            .finish()
    }
}

impl std::fmt::Debug for AskReq {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // 只报问题个数：问题正文可能带用户隐私。
        f.debug_struct("AskReq").field("questions", &self.questions.len()).finish()
    }
}

/// 用户对计划的裁决。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "verdict", rename_all = "snake_case")]
pub enum PlanVerdict {
    /// 同意，按计划执行。
    Confirm,
    /// 要求修改，附意见。
    Revise { note: String },
    /// 不做了。
    Dismiss,
}

impl PlanVerdict {
    pub fn is_confirm(&self) -> bool {
        matches!(self, PlanVerdict::Confirm)
    }
}

/// 挂起的计划确认。
pub struct PlanReq {
    pub text: String,
    pub tx: oneshot::Sender<PlanVerdict>,
}

impl std::fmt::Debug for PlanReq {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("PlanReq").field("chars", &self.text.chars().count()).finish()
    }
}

/// 等待用户确认的工具调用。
pub struct PendingTool {
    pub call: ToolCall,
    pub args: serde_json::Value,
    pub route: Route,
    pub high_risk: bool,
    pub tx: oneshot::Sender<bool>,
}

/// 正在运行的工具（界面画转圈）。
#[derive(Debug, Clone, Serialize)]
pub struct RunningTool {
    pub seq: u64,
    pub name: String,
    pub args: String,
    /// 是不是子智能体发起的（界面上标注来源）。
    pub from_sub: bool,
}

/// 运行态。
#[derive(Debug, Default)]
pub struct AgentState {
    pub session_id: Option<String>,
    pub session_title: String,
    pub messages: Vec<ChatMsg>,
    pub sessions: Vec<SessionRow>,
    pub busy: bool,
    pub status: String,
    pub running: Vec<RunningTool>,
    /// 工具序号，只增不减。界面用它做 key，避免"结束一个、开始一个"时错位。
    pub run_seq: u64,
    pub stream_text: String,
    pub stream_reason: String,
    /// 本轮已执行的工具轮次。
    pub turn: usize,
    /// 最近一次错误（界面显示红色提示，下次成功即清）。
    pub last_error: Option<String>,
    pub pending: Option<PendingTool>,
    pub ask: Option<AskReq>,
    pub plan: Option<PlanReq>,
}

impl AgentState {
    /// 清空流式缓冲。
    pub fn clear_stream(&mut self) {
        self.stream_text.clear();
        self.stream_reason.clear();
    }

    /// 是否正在跑。
    pub fn running_now(&self) -> bool {
        self.busy
    }

    /// 当前是否有等待用户处理的弹窗。
    pub fn waiting(&self) -> bool {
        self.pending.is_some() || self.ask.is_some() || self.plan.is_some()
    }

    /// 挂起的是哪种交互：`confirm` / `ask` / `plan` / 空。
    ///
    /// 界面据此决定弹哪个面板。给字符串而不是三个 bool，是因为这三者互斥，
    /// 三个布尔值允许"同时为真"这种不存在的状态。
    pub fn waiting_kind(&self) -> &'static str {
        if self.pending.is_some() {
            "confirm"
        } else if self.ask.is_some() {
            "ask"
        } else if self.plan.is_some() {
            "plan"
        } else {
            ""
        }
    }

    /// 收尾：一轮结束或出错时调用。
    pub fn finish(&mut self) {
        self.busy = false;
        self.running.clear();
        self.clear_stream();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finish_clears_transient() {
        let mut st = AgentState { busy: true, ..Default::default() };
        st.running.push(RunningTool { seq: 1, name: "x".into(), args: "{}".into(), from_sub: false });
        st.stream_text = "abc".into();
        st.finish();
        assert!(!st.busy);
        assert!(st.running.is_empty());
        assert!(st.stream_text.is_empty());
    }

    #[test]
    fn plan_verdict_json() {
        let v = PlanVerdict::Revise { note: "再细一点".into() };
        let s = serde_json::to_string(&v).unwrap();
        assert!(s.contains("revise"));
    }
}
