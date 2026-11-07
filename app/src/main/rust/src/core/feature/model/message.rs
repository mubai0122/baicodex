//! 消息与工具调用。
//!
//! 消息树的结构在这里定义，SQL 在 `feature::storage::message_repo`。两层刻意分开：
//! 模型层只描述"一条消息长什么样"，不关心它怎么落库。
//!
//! 树关系用三个字段表达：
//! - `parent_id`：父消息。0 表示会话根。
//! - `b_idx` / `b_total`：同一父节点下的第几个 / 共几个。`b_total > 1` 时界面显示 ‹1/3›。
//!
//! 重新生成不会删除旧回答，而是**在同一父节点下追加一个兄弟**。这样用户可以来回切，
//! 也能比较两个回答——删除式重生成是产品事故（用户常后悔）。

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

/// 消息角色。
///
/// `Summary` 是本地生成的上下文摘要：它存在于消息树里（用户能看见"这里压缩过"），
/// 但**不回传给模型**——回传会让模型把摘要当成自己说过的话。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Role {
    System,
    User,
    Assistant,
    Tool,
    Summary,
}

impl Role {
    pub fn as_str(self) -> &'static str {
        match self {
            Role::System => "system",
            Role::User => "user",
            Role::Assistant => "assistant",
            Role::Tool => "tool",
            Role::Summary => "summary",
        }
    }

    pub fn parse(s: &str) -> Role {
        match s.trim().to_ascii_lowercase().as_str() {
            "system" => Role::System,
            "assistant" | "ai" => Role::Assistant,
            "tool" | "function" => Role::Tool,
            "summary" => Role::Summary,
            _ => Role::User,
        }
    }

    /// 会不会进请求体。
    pub fn to_api(self) -> bool {
        !matches!(self, Role::Summary)
    }

    /// 界面上显示的角色名。
    pub fn label(self) -> &'static str {
        match self {
            Role::System => "系统",
            Role::User => "你",
            Role::Assistant => "助手",
            Role::Tool => "工具",
            Role::Summary => "摘要",
        }
    }
}

/// 一次工具调用。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    /// 模型给的原始参数字符串。**可能不是合法 JSON**，执行前再解析。
    pub arguments: String,
}

impl ToolCall {
    pub fn new(id: impl Into<String>, name: impl Into<String>, arguments: impl Into<String>) -> Self {
        Self { id: id.into(), name: name.into(), arguments: arguments.into() }
    }

    /// 宽松解析。坏了就当空对象，让工具自己报"缺参数"——比在这里报 JSON 语法错
    /// 更有用，因为模型看到"缺少 path"能立刻改，看到"unexpected token"只会重复。
    pub fn args(&self) -> Value {
        serde_json::from_str(&self.arguments).unwrap_or_else(|_| json!({}))
    }

    /// 是否是发给某个工具的。
    pub fn is_for(&self, name: &str) -> bool {
        self.name == name
    }
}

/// 单轮统计。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct MsgMeta {
    pub at: i64,
    pub ms: i64,
    /// 首字延迟。0 表示没记录（非流式请求）。
    pub first_ms: i64,
    pub prompt_tokens: i64,
    pub completion_tokens: i64,
    pub cached_tokens: i64,
    pub provider: String,
    pub model: String,
}

impl MsgMeta {
    pub fn total_tokens(&self) -> i64 {
        self.prompt_tokens + self.completion_tokens
    }

    /// 生成速度（token/s）。
    ///
    /// 只算"首字之后"的时间：首字之前是排队与预填充，把它摊进速度会显得模型很慢，
    /// 但用户体感上"等首字"和"看它打字"是两件事。
    pub fn speed(&self) -> f64 {
        if self.completion_tokens <= 0 || self.ms <= 0 {
            return 0.0;
        }
        let gen_ms = if self.first_ms > 0 && self.first_ms < self.ms {
            (self.ms - self.first_ms).max(1)
        } else {
            self.ms.max(1)
        };
        self.completion_tokens as f64 / (gen_ms as f64 / 1000.0)
    }

    /// 一行摘要。
    pub fn line(&self) -> String {
        let mut parts = Vec::new();
        if self.total_tokens() > 0 {
            parts.push(format!("{} tok", self.total_tokens()));
        }
        if self.speed() > 0.0 {
            parts.push(format!("{:.0} tok/s", self.speed()));
        }
        if self.ms > 0 {
            parts.push(crate::core::base::clock::human_ms(self.ms));
        }
        if !self.model.is_empty() {
            parts.push(self.model.clone());
        }
        parts.join(" · ")
    }
}

/// 一条消息。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMsg {
    pub id: i64,
    pub role: Role,
    pub content: String,
    #[serde(default)]
    pub tool_calls: Vec<ToolCall>,
    pub tool_call_id: Option<String>,
    pub tool_name: Option<String>,
    #[serde(default)]
    pub is_error: bool,
    /// 思考过程。只展示，**不回传**（回传会污染下一轮推理）。
    #[serde(default)]
    pub reasoning: String,
    /// 用户附图（本地绝对路径）。
    #[serde(default)]
    pub images: Vec<String>,
    /// 用户附文件（本地绝对路径），路径会写进正文。
    #[serde(default)]
    pub files: Vec<String>,
    pub meta: Option<MsgMeta>,
    #[serde(default)]
    pub starred: bool,
    #[serde(default)]
    pub parent_id: i64,
    #[serde(default)]
    pub b_idx: i32,
    #[serde(default)]
    pub b_total: i32,
}

impl Default for ChatMsg {
    fn default() -> Self {
        Self {
            id: 0,
            role: Role::User,
            content: String::new(),
            tool_calls: Vec::new(),
            tool_call_id: None,
            tool_name: None,
            is_error: false,
            reasoning: String::new(),
            images: Vec::new(),
            files: Vec::new(),
            meta: None,
            starred: false,
            parent_id: 0,
            b_idx: 0,
            b_total: 0,
        }
    }
}

impl ChatMsg {
    pub fn user(content: impl Into<String>) -> Self {
        Self { role: Role::User, content: content.into(), ..Default::default() }
    }

    pub fn assistant(content: impl Into<String>) -> Self {
        Self { role: Role::Assistant, content: content.into(), ..Default::default() }
    }

    pub fn tool(call: &ToolCall, text: impl Into<String>, is_error: bool) -> Self {
        Self {
            role: Role::Tool,
            content: text.into(),
            tool_call_id: Some(call.id.clone()),
            tool_name: Some(call.name.clone()),
            is_error,
            ..Default::default()
        }
    }

    pub fn summary(text: impl Into<String>) -> Self {
        Self { role: Role::Summary, content: text.into(), ..Default::default() }
    }

    /// 是否是"空的助手消息"（只有 thinking，没有正文与工具调用）。
    /// 有些推理模型会返回这种，直接展示会得到一个空气泡。
    pub fn is_empty_assistant(&self) -> bool {
        self.role == Role::Assistant
            && self.content.trim().is_empty()
            && self.tool_calls.is_empty()
            && self.reasoning.trim().is_empty()
    }

    /// 给会话标题用的一行文本。
    pub fn title_source(&self) -> String {
        let s = if self.content.trim().is_empty() {
            self.tool_calls.first().map(|c| format!("[{}]", c.name)).unwrap_or_default()
        } else {
            self.content.clone()
        };
        crate::core::base::text::truncate(&crate::core::base::text::one_line(&s), 40)
    }

    /// 转成 OpenAI Chat Completions 的 message 结构。
    ///
    /// `vision` 由调用方按模型能力传入：不支持图片的模型塞多模态内容会被中转站 400。
    pub fn to_api_json(&self, vision: bool) -> Value {
        let mut o = json!({ "role": self.role.as_str() });
        let body = self.body_with_files();

        if self.role == Role::Assistant && !self.tool_calls.is_empty() {
            o["content"] = json!(self.content);
            o["tool_calls"] = json!(self
                .tool_calls
                .iter()
                .map(|c| json!({
                    "id": c.id,
                    "type": "function",
                    "function": { "name": c.name, "arguments": c.arguments }
                }))
                .collect::<Vec<_>>());
            return o;
        }

        if self.role == Role::Tool {
            o["tool_call_id"] = json!(self.tool_call_id.clone().unwrap_or_default());
            if let Some(n) = &self.tool_name {
                if !n.is_empty() {
                    o["name"] = json!(n);
                }
            }
            o["content"] = json!(if self.content.trim().is_empty() { "[空]" } else { self.content.as_str() });
            return o;
        }

        if self.role == Role::User && !self.images.is_empty() {
            if !vision {
                let note = format!("（用户附了 {} 张图片，当前模型不支持图片输入，看不到图片内容）", self.images.len());
                let text = if body.trim().is_empty() { note } else { format!("{body}\n{note}") };
                o["content"] = json!(text);
                return o;
            }
            let mut parts = vec![json!({
                "type": "text",
                "text": if body.trim().is_empty() { "看这些图片".to_string() } else { body.clone() }
            })];
            for p in &self.images {
                // 读不了/太大就跳过这一张：一张图失败不该让整轮请求失败。
                if let Ok(url) = crate::core::feature::image::data_url(p) {
                    parts.push(json!({ "type": "image_url", "image_url": { "url": url } }));
                }
            }
            o["content"] = json!(parts);
            return o;
        }

        o["content"] = json!(if body.trim().is_empty() { "[空]" } else { body.as_str() });
        o
    }

    /// 正文 + 附件说明。附件路径要写进正文，模型才知道有这些东西。
    fn body_with_files(&self) -> String {
        if self.files.is_empty() {
            return self.content.clone();
        }
        let mut s = String::new();
        if !self.content.trim().is_empty() {
            s.push_str(&self.content);
            s.push('\n');
        }
        s.push_str(&format!(
            "（用户附了 {} 个文件，本地绝对路径如下，需要时用 read_file 读）\n",
            self.files.len()
        ));
        for f in &self.files {
            s.push_str(f);
            s.push('\n');
        }
        s
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tool_message_shape() {
        let c = ToolCall::new("call_1", "read_file", r#"{"path":"/tmp/a"}"#);
        let m = ChatMsg::tool(&c, "content", false);
        let v = m.to_api_json(false);
        assert_eq!(v["role"], "tool");
        assert_eq!(v["tool_call_id"], "call_1");
        assert_eq!(v["name"], "read_file");
    }

    #[test]
    fn assistant_with_calls_keeps_content() {
        let mut m = ChatMsg::assistant("");
        m.tool_calls.push(ToolCall::new("1", "shell", "{}"));
        let v = m.to_api_json(false);
        assert_eq!(v["tool_calls"][0]["function"]["name"], "shell");
        assert_eq!(v["content"], "");
    }

    #[test]
    fn files_go_into_body() {
        let mut m = ChatMsg::user("看看这个");
        m.files.push("/tmp/a.log".into());
        let v = m.to_api_json(false);
        assert!(v["content"].as_str().unwrap().contains("/tmp/a.log"));
    }

    #[test]
    fn speed_ignores_queue_time() {
        let m = MsgMeta { ms: 3000, first_ms: 1000, completion_tokens: 100, ..Default::default() };
        // 2 秒生成 100 token
        assert!((m.speed() - 50.0).abs() < 0.1);
    }

    #[test]
    fn bad_args_do_not_panic() {
        let c = ToolCall::new("1", "x", "{not json");
        assert_eq!(c.args(), json!({}));
    }
}
