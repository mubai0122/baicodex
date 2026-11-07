//! 供应商、端点与模型能力。
//!
//! 这里只描述"怎么连、怎么鉴权、这个模型能干什么"，不负责发请求（那是 `net::llm`）。
//!
//! 一个常见坑：**同一个供应商的不同模型能力不同**。比如某家中转站上
//! `gpt-4o` 支持图片，`deepseek-chat` 不支持。所以能力挂在 [ModelCaps] 上，
//! 由配置里的模型条目覆盖供应商默认值，而不是按供应商一刀切。

use serde::{Deserialize, Serialize};

/// 鉴权方式。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "kebab-case")]
pub enum AuthStyle {
    /// `Authorization: Bearer <key>`
    #[default]
    Bearer,
    /// `x-api-key: <key>`（Anthropic 原生风格）
    XApiKey,
    /// 不鉴权（本地 Ollama / LM Studio）
    None,
}

impl AuthStyle {
    pub fn header(&self, key: &str) -> Option<(String, String)> {
        match self {
            AuthStyle::Bearer => Some(("Authorization".into(), format!("Bearer {key}"))),
            AuthStyle::XApiKey => Some(("x-api-key".into(), key.to_string())),
            AuthStyle::None => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            AuthStyle::Bearer => "bearer",
            AuthStyle::XApiKey => "x-api-key",
            AuthStyle::None => "none",
        }
    }

    pub fn parse(s: &str) -> AuthStyle {
        match s.trim().to_ascii_lowercase().as_str() {
            "x-api-key" | "xapikey" | "anthropic" => AuthStyle::XApiKey,
            "none" | "no" | "" => AuthStyle::None,
            _ => AuthStyle::Bearer,
        }
    }
}

/// 模型能力。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct ModelCaps {
    /// 能收图片。
    pub vision: bool,
    /// 支持 function calling。
    pub tools: bool,
    /// 会返回 reasoning_content。
    pub reasoning: bool,
    /// 上下文窗口（token）。0 表示未知。
    pub context: u32,
    /// 是否是推理模型（要求不同的参数格式，如 `reasoning_effort`）。
    pub reasoning_model: bool,
}

impl Default for ModelCaps {
    fn default() -> Self {
        Self { vision: false, tools: true, reasoning: false, context: 128_000, reasoning_model: false }
    }
}

impl ModelCaps {
    pub fn vision(mut self, on: bool) -> Self {
        self.vision = on;
        self
    }

    pub fn context(mut self, n: u32) -> Self {
        self.context = n;
        self
    }

    pub fn reasoning(mut self) -> Self {
        self.reasoning = true;
        self.reasoning_model = true;
        self
    }
}

/// 供应商（端点）。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Provider {
    pub id: String,
    pub name: String,
    /// 基础地址，不带 `/chat/completions`。
    pub base_url: String,
    #[serde(default)]
    pub auth: AuthStyle,
    /// 用户自己加的（界面上可删）。
    #[serde(default)]
    pub custom: bool,
    /// 默认模型名。
    #[serde(default)]
    pub model: String,
    /// 默认能力。模型条目可以覆盖。
    #[serde(default)]
    pub caps: ModelCaps,
    /// 额外请求头（中转站常要求 `HTTP-Referer` 之类）。
    #[serde(default)]
    pub headers: Vec<(String, String)>,
}

impl Provider {
    pub fn new(id: &str, name: &str, base: &str, model: &str) -> Self {
        Self {
            id: id.into(),
            name: name.into(),
            base_url: normalize_base(base),
            auth: AuthStyle::Bearer,
            custom: false,
            model: model.into(),
            caps: ModelCaps::default(),
            headers: Vec::new(),
        }
    }

    /// 拼出完整端点。
    pub fn endpoint(&self, path: &str) -> String {
        join(&self.base_url, path)
    }

    /// `chat/completions` 的完整地址。
    pub fn chat_url(&self) -> String {
        self.endpoint("chat/completions")
    }

    /// 模型列表地址。
    pub fn models_url(&self) -> String {
        self.endpoint("models")
    }
}

/// 规范化 base_url：补协议、去尾斜杠、去重复的 `/v1`。
///
/// 用户经常把 `https://api.openai.com/v1/`、`api.openai.com/v1`、
/// `https://api.openai.com` 三种写法混着填。这里统一成第一种。
pub fn normalize_base(raw: &str) -> String {
    let mut s = raw.trim().to_string();
    if s.is_empty() {
        return s;
    }
    if !s.starts_with("http://") && !s.starts_with("https://") {
        s = format!("https://{s}");
    }
    while s.ends_with('/') {
        s.pop();
    }
    // 去掉 `/chat/completions` 这种"用户直接贴了完整地址"的情况
    for suffix in ["/chat/completions", "/completions"] {
        if s.ends_with(suffix) {
            s.truncate(s.len() - suffix.len());
        }
    }
    s
}

/// 拼接 base 与 path，处理好 `/v1` 只出现一次。
pub fn join(base: &str, path: &str) -> String {
    let b = base.trim_end_matches('/');
    let p = path.trim_start_matches('/');
    if b.ends_with("/v1") || b.contains("/v1/") {
        format!("{b}/{p}")
    } else {
        format!("{b}/v1/{p}")
    }
}

/// 内置供应商。用户可以在设置里增删改，这里只是"开箱能用"的起点。
///
/// 只放**兼容 OpenAI Chat Completions** 的端点——不兼容的接入方式（比如 Gemini
/// 原生协议）需要单独的适配器，不能靠改 base_url 蒙混过去。
pub fn builtin() -> Vec<Provider> {
    vec![
        Provider::new("openai", "OpenAI", "https://api.openai.com/v1", "gpt-6-sol"),
        Provider {
            caps: ModelCaps::default().vision(true),
            ..Provider::new("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat")
        },
        Provider {
            caps: ModelCaps::default().vision(true),
            ..Provider::new("moonshot", "月之暗面", "https://api.moonshot.cn/v1", "moonshot-v1-8k")
        },
        Provider::new("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        Provider::new("dashscope", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
        Provider::new("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
        Provider::new("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        Provider::new("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Provider::new("ollama", "Ollama（本地）", "http://127.0.0.1:11434/v1", "qwen2.5:7b"),
        Provider::new("lmstudio", "LM Studio（本地）", "http://127.0.0.1:1234/v1", "local-model"),
        Provider {
            auth: AuthStyle::XApiKey,
            caps: ModelCaps::default().vision(true),
            ..Provider::new("anthropic", "Anthropic（兼容网关）", "https://api.anthropic.com/v1", "claude-3-5-sonnet")
        },
    ]
}

/// 按 id 找内置供应商。
pub fn find(id: &str) -> Option<Provider> {
    builtin().into_iter().find(|p| p.id == id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_variants() {
        assert_eq!(normalize_base("api.openai.com/v1/"), "https://api.openai.com/v1");
        assert_eq!(normalize_base("https://x.com/v1/chat/completions"), "https://x.com/v1");
    }

    #[test]
    fn join_keeps_single_v1() {
        assert_eq!(join("https://api.openai.com/v1", "chat/completions"), "https://api.openai.com/v1/chat/completions");
        assert_eq!(join("https://api.x.com", "models"), "https://api.x.com/v1/models");
    }

    #[test]
    fn auth_headers() {
        assert_eq!(AuthStyle::Bearer.header("k").unwrap().1, "Bearer k");
        assert_eq!(AuthStyle::XApiKey.header("k").unwrap().0, "x-api-key");
        assert!(AuthStyle::None.header("k").is_none());
    }

    #[test]
    fn builtin_ids_unique() {
        let mut ids: Vec<_> = builtin().into_iter().map(|p| p.id).collect();
        let n = ids.len();
        ids.sort();
        ids.dedup();
        assert_eq!(ids.len(), n);
    }
}