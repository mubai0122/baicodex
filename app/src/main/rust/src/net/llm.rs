//! OpenAI 兼容客户端：一个 base_url + 一个 key，支持 SSE 流式、工具调用、多 key 轮换。
//!
//! 只做三件事：组请求体、收流、把流拼回一条 message。厂商差异在这里吃掉，
//! 上层（`agent`）不需要知道 DeepSeek 的 `thinking`、Gemini 的 `usageMetadata`。
//!
//! ## 几个必须处理的现实问题
//!
//! 1. **服务端可能忽略 `stream=true`**，直接回一整包 JSON。这时不能报错，按普通响应解析。
//! 2. **SSE 的行可能被网络切成两半**。必须保留不完整的尾部，等下一块再拼。
//! 3. **`tool_calls` 是分片到达的**，按 `index` 合并，`arguments` 是字符串拼接。
//! 4. **DeepSeek 的思考默认开**，不想思考时必须显式 `thinking: {"type":"disabled"}`。
//! 5. **停止按钮**要能在流中途真的停下，所以要每块检查取消标记。

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use futures_util::StreamExt;
use serde_json::{json, Map, Value};

use super::client;
use crate::core::base::error::{BError, BResult, ErrorCode};
use crate::core::feature::model::provider::AuthStyle;

/// 流式回调：`(增量正文, 增量思考)`。
///
/// 两个参数分开传，是因为界面要把思考过程折叠显示，混在一起就拆不出来了。
pub type DeltaFn<'a> = dyn FnMut(&str, &str) + Send + 'a;

/// 请求选项。
#[derive(Debug, Clone, Default)]
pub struct ChatOptions {
    pub temperature: Option<f64>,
    pub top_p: Option<f64>,
    pub think: bool,
    /// `low` / `medium` / `high`。
    pub effort: String,
    /// 厂商私有字段（已解析的 JSON 对象）。
    pub extra: Map<String, Value>,
    /// 0 = 不带 max_tokens。
    pub max_tokens: i64,
    /// 上不封顶：不传 max_tokens，即使配了也不传。
    pub max_mode: bool,
    pub model_tools: bool,
    pub model_reasoning: bool,
    pub no_stream: bool,
    pub provider_id: String,
    /// 工具定义（OpenAI 格式的数组）。
    pub tools: Option<Value>,
}

/// 客户端。
#[derive(Debug, Clone)]
pub struct Llm {
    base: String,
    key: String,
    auth: AuthStyle,
    /// 额外请求头（中转站常要 Referer / X-Title）。
    headers: Vec<(String, String)>,
}

impl Llm {
    pub fn new(base_url: &str, key: &str, auth: AuthStyle) -> Self {
        Self { base: normalize(base_url), key: key.to_string(), auth, headers: Vec::new() }
    }

    /// 换 key（多 key 轮换时用）。
    pub fn with_key(mut self, key: &str) -> Self {
        self.key = key.to_string();
        self
    }

    pub fn with_headers(mut self, headers: Vec<(String, String)>) -> Self {
        self.headers = headers;
        self
    }

    pub fn base(&self) -> &str {
        &self.base
    }

    pub fn has_key(&self) -> bool {
        !self.key.trim().is_empty() || self.auth == AuthStyle::None
    }

    fn endpoint(&self, path: &str) -> String {
        format!("{}{}", self.base.trim_end_matches('/'), path)
    }

    fn apply(&self, mut rb: reqwest::RequestBuilder) -> reqwest::RequestBuilder {
        if let Some((k, v)) = self.auth.header(&self.key) {
            rb = rb.header(k, v);
        }
        for (k, v) in &self.headers {
            rb = rb.header(k.as_str(), v.as_str());
        }
        rb
    }

    /// 组请求体。
    pub fn body(&self, model: &str, messages: &Value, opt: &ChatOptions) -> Value {
        let mut b = Map::new();
        b.insert("model".into(), json!(model));
        b.insert("messages".into(), messages.clone());

        if opt.model_tools {
            if let Some(tools) = &opt.tools {
                let empty = tools.as_array().map(|a| a.is_empty()).unwrap_or(true);
                if !empty {
                    b.insert("tools".into(), tools.clone());
                    b.insert("tool_choice".into(), json!("auto"));
                }
            }
        }

        if let Some(t) = opt.temperature {
            if t >= 0.0 {
                b.insert("temperature".into(), json!(t));
            }
        }
        if let Some(p) = opt.top_p {
            if p >= 0.0 {
                b.insert("top_p".into(), json!(p));
            }
        }
        if !opt.max_mode && opt.max_tokens > 0 {
            b.insert("max_tokens".into(), json!(opt.max_tokens));
        }
        if opt.think && opt.model_reasoning && !opt.effort.trim().is_empty() {
            b.insert("reasoning_effort".into(), json!(opt.effort.trim()));
        }
        // DeepSeek 官方格式的思考开关默认是"开"：必须显式 disabled 才真关。
        let ds = opt.provider_id.to_lowercase().contains("deepseek")
            || self.base.to_lowercase().contains("deepseek");
        if ds && !opt.think {
            b.insert("thinking".into(), json!({ "type": "disabled" }));
        }
        // 厂商私有字段浅合并：允许覆盖上面任何一项（用户显式配置优先）。
        for (k, v) in &opt.extra {
            b.insert(k.clone(), v.clone());
        }
        Value::Object(b)
    }

    /// 非流式一次拿完。
    pub async fn chat(&self, model: &str, messages: &Value, opt: &ChatOptions) -> BResult<Value> {
        self.guard()?;
        let body = self.body(model, messages, opt);
        let rb = self
            .apply(client::shared().post(self.endpoint("/chat/completions")))
            .json(&body);
        let resp = rb.send().await.map_err(|e| net_err(&e))?;
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        if !status.is_success() {
            return Err(http_err(status.as_u16(), &text));
        }
        message_of(&text)
    }

    /// 流式：逐块回调增量，最后拼成一条 message 返回。
    ///
    /// `cancel` 为真时立刻返回 `Cancelled`——用户在界面上点了停止，不该等到整段生成完。
    pub async fn chat_stream(
        &self,
        model: &str,
        messages: &Value,
        opt: &ChatOptions,
        cancel: Option<Arc<AtomicBool>>,
        on_delta: &mut DeltaFn<'_>,
    ) -> BResult<Value> {
        self.guard()?;
        let mut body = self.body(model, messages, opt);
        body["stream"] = json!(true);
        // 让服务端在最后一块带上 usage，否则流式拿不到 token 数。
        if body.get("stream_options").is_none() {
            body["stream_options"] = json!({ "include_usage": true });
        }

        let rb = self
            .apply(client::stream().post(self.endpoint("/chat/completions")))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .json(&body);
        let resp = rb.send().await.map_err(|e| net_err(&e))?;

        let status = resp.status();
        if !status.is_success() {
            let text = resp.text().await.unwrap_or_default();
            return Err(http_err(status.as_u16(), &text));
        }

        let is_sse = resp
            .headers()
            .get("content-type")
            .and_then(|v| v.to_str().ok())
            .map(|s| s.to_lowercase().contains("event-stream"))
            .unwrap_or(false);

        if !is_sse {
            // 服务端忽略了 stream=true：按整包解析。
            let text = resp.text().await.unwrap_or_default();
            return message_of(&text);
        }

        let mut acc = Acc::default();
        let mut buf = String::new();
        let mut stream = resp.bytes_stream();

        while let Some(chunk) = stream.next().await {
            if let Some(c) = &cancel {
                if c.load(Ordering::Relaxed) {
                    return Err(BError::cancelled());
                }
            }
            let bytes = chunk.map_err(|e| net_err(&e))?;
            buf.push_str(&String::from_utf8_lossy(&bytes));

            // SSE 以 \n 分行；最后一段可能不完整，留在 buf 里等下一块。
            while let Some(pos) = buf.find('\n') {
                let line = buf[..pos].trim_end_matches('\r').to_string();
                buf.drain(..=pos);
                let line = line.trim();
                if line.is_empty() || line.starts_with(':') {
                    continue;
                }
                let Some(data) = line.strip_prefix("data:") else { continue };
                let data = data.trim();
                if data.is_empty() {
                    continue;
                }
                if data == "[DONE]" {
                    buf.clear();
                    break;
                }
                let Ok(o) = serde_json::from_str::<Value>(data) else { continue };
                acc.absorb(&o, on_delta);
            }
        }

        Ok(acc.finish())
    }

    /// 列模型。
    pub async fn models(&self) -> BResult<Vec<String>> {
        self.guard()?;
        let rb = self.apply(client::shared().get(self.endpoint("/models")));
        let resp = rb.send().await.map_err(|e| net_err(&e))?;
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        if !status.is_success() {
            return Err(http_err(status.as_u16(), &text));
        }
        Ok(parse_models(&text))
    }

    fn guard(&self) -> BResult<()> {
        if self.base.trim().is_empty() {
            return Err(BError::new(ErrorCode::Invalid, "还没有填接口地址（base_url）"));
        }
        if !self.has_key() {
            return Err(BError::new(ErrorCode::Auth, "还没有填 API Key"));
        }
        Ok(())
    }
}

fn normalize(base: &str) -> String {
    let mut b = base.trim().trim_end_matches('/').to_string();
    // 用户经常把完整端点贴进来，去掉重复的后缀。
    for suffix in ["/chat/completions", "/completions"] {
        if let Some(s) = b.strip_suffix(suffix) {
            b = s.trim_end_matches('/').to_string();
        }
    }
    b
}

fn net_err(e: &reqwest::Error) -> BError {
    let mut detail = e.to_string();
    let mut src = std::error::Error::source(e);
    while let Some(inner) = src {
        detail.push('：');
        detail.push_str(&inner.to_string());
        src = inner.source();
    }
    let msg = if e.is_timeout() {
        "请求超时（网络慢或服务端没响应）"
    } else if e.is_connect() {
        "连不上服务端（检查网络 / 代理 / base_url）"
    } else {
        "网络错误"
    };
    BError::new(ErrorCode::Network, msg).with_detail(detail).retry_hint(true)
}

fn http_err(code: u16, body: &str) -> BError {
    let brief = crate::core::base::text::truncate(body.trim(), 600);
    let (ec, msg) = match code {
        401 | 403 => (ErrorCode::Auth, "鉴权失败：Key 无效或没有权限"),
        404 => (ErrorCode::NotFound, "接口不存在：检查 base_url 是否少了 /v1"),
        429 => (ErrorCode::Network, "被限流（429）：稍等或换一个 key"),
        500..=599 => (ErrorCode::Network, "服务端错误"),
        _ => (ErrorCode::Invalid, "请求被拒绝"),
    };
    let retry = matches!(code, 401 | 403 | 429) || code >= 500;
    BError::new(ec, format!("{msg}（HTTP {code}）"))
        .with_detail(brief)
        .retry_hint(retry)
}

/// 流式累加器。
#[derive(Default)]
struct Acc {
    content: String,
    reason: String,
    calls: Vec<Value>,
    usage: Option<Value>,
}

impl Acc {
    fn absorb(&mut self, o: &Value, on_delta: &mut DeltaFn<'_>) {
        if let Some(u) = o.get("usage") {
            if !u.is_null() {
                self.usage = Some(u.clone());
            }
        }
        let delta = &o["choices"][0]["delta"];
        if delta.is_null() {
            return;
        }
        let t = delta["content"].as_str().unwrap_or("");
        let rz = delta["reasoning_content"]
            .as_str()
            .or_else(|| delta["reasoning"].as_str())
            .unwrap_or("");
        if !t.is_empty() {
            self.content.push_str(t);
            on_delta(t, "");
        }
        if !rz.is_empty() {
            self.reason.push_str(rz);
            on_delta("", rz);
        }
        merge_calls(&mut self.calls, &delta["tool_calls"]);
    }

    fn finish(self) -> Value {
        let arr: Vec<Value> = self
            .calls
            .iter()
            .map(|c| {
                let args = c["arguments"].as_str().unwrap_or("");
                json!({
                    "id": c["id"],
                    "type": "function",
                    "function": {
                        "name": c["name"],
                        "arguments": if args.trim().is_empty() { "{}" } else { args }
                    }
                })
            })
            .collect();
        let mut msg = json!({
            "content": self.content,
            "reasoning_content": self.reason,
            "tool_calls": arr
        });
        if let Some(u) = self.usage {
            msg["usage"] = u;
        }
        msg
    }
}

/// 按 index 合并流式 tool_calls 分片。
fn merge_calls(calls: &mut Vec<Value>, delta: &Value) {
    let Some(arr) = delta.as_array() else { return };
    for part in arr {
        let idx = part["index"].as_u64().unwrap_or(0) as usize;
        while calls.len() <= idx {
            calls.push(json!({ "id": "", "name": "", "arguments": "" }));
        }
        let cur = &mut calls[idx];
        if let Some(id) = part["id"].as_str() {
            if !id.is_empty() {
                cur["id"] = json!(id);
            }
        }
        let f = &part["function"];
        if let Some(n) = f["name"].as_str() {
            if !n.is_empty() {
                cur["name"] = json!(n);
            }
        }
        if let Some(a) = f["arguments"].as_str() {
            if !a.is_empty() {
                let merged = format!("{}{}", cur["arguments"].as_str().unwrap_or(""), a);
                cur["arguments"] = json!(merged);
            }
        }
    }
}

fn message_of(raw: &str) -> BResult<Value> {
    let obj: Value = serde_json::from_str(raw).map_err(|_| {
        BError::new(
            ErrorCode::Invalid,
            format!("响应不是 JSON：{}", crate::core::base::text::truncate(raw, 300)),
        )
    })?;
    let msg = obj["choices"][0]["message"].clone();
    if msg.is_null() {
        return Err(
            BError::new(ErrorCode::Invalid, "响应里没有 choices[0].message").with_detail(crate::core::base::text::truncate(raw, 400))
        );
    }
    let mut m = msg;
    if !obj["usage"].is_null() {
        m["usage"] = obj["usage"].clone();
    }
    Ok(m)
}

fn parse_models(text: &str) -> Vec<String> {
    let Ok(v) = serde_json::from_str::<Value>(text) else {
        // 有的网关返回纯文本，一行一个。
        return text
            .lines()
            .map(|l| l.trim().trim_matches('"').trim_end_matches(',').to_string())
            .filter(|l| l.len() > 2)
            .collect();
    };
    let arr = v["data"]
        .as_array()
        .or_else(|| v["models"].as_array())
        .cloned()
        .unwrap_or_default();
    let mut out: Vec<String> = Vec::new();
    for item in arr {
        let id = item["id"]
            .as_str()
            .or_else(|| item["slug"].as_str())
            .or_else(|| item["name"].as_str())
            .unwrap_or("");
        if !id.is_empty() && !out.iter().any(|x| x == id) {
            out.push(id.to_string());
        }
    }
    out.sort_by_key(|a| a.to_lowercase());
    out
}

/// 取文本字段：JSON null 当空串。
pub fn text_of(v: &Value, key: &str) -> String {
    v.get(key).and_then(|x| x.as_str()).unwrap_or("").to_string()
}

/// 解析 usage：各家字段不同，统一成 `(prompt, cached, completion)`。
pub fn parse_usage(msg: &Value) -> (i64, i64, i64) {
    let u = &msg["usage"];
    let prompt = u["prompt_tokens"].as_i64().unwrap_or(0);
    let completion = u["completion_tokens"].as_i64().unwrap_or(0);
    let cached = u["prompt_tokens_details"]["cached_tokens"]
        .as_i64()
        .or_else(|| u["cache_read_input_tokens"].as_i64())
        .or_else(|| u["cached_tokens"].as_i64())
        .or_else(|| msg["usageMetadata"]["cachedContentTokenCount"].as_i64())
        .unwrap_or(0);
    (prompt, cached.clamp(0, prompt.max(0)), completion)
}

/// 这条 message 有没有工具调用。
pub fn has_tool_calls(msg: &Value) -> bool {
    msg["tool_calls"].as_array().map(|a| !a.is_empty()).unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_strips_endpoint() {
        assert_eq!(normalize("https://api.x.com/v1/chat/completions"), "https://api.x.com/v1");
        assert_eq!(normalize("https://api.x.com/v1/"), "https://api.x.com/v1");
    }

    #[test]
    fn merge_tool_call_fragments() {
        let mut calls = Vec::new();
        merge_calls(&mut calls, &json!([{"index":0,"id":"a","function":{"name":"read_file","arguments":"{\"pa"}}]));
        merge_calls(&mut calls, &json!([{"index":0,"function":{"arguments":"th\":\"a\"}"}}]));
        let acc = Acc { calls, ..Default::default() };
        let m = acc.finish();
        assert_eq!(m["tool_calls"][0]["function"]["arguments"], "{\"path\":\"a\"}");
    }

    #[test]
    fn usage_fallbacks() {
        let m = json!({"usage":{"prompt_tokens":100,"completion_tokens":20,"prompt_tokens_details":{"cached_tokens":40}}});
        assert_eq!(parse_usage(&m), (100, 40, 20));
        assert_eq!(parse_usage(&json!({})), (0, 0, 0));
    }
}
