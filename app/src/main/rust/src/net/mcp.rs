//! MCP 客户端协议层：握手、拉能力表、调能力。
//!
//! 只做传输与协议，**不做持久化**（那是 `core::feature::mcp` 的事）。
//!
//! 走 MCP 1.0 的 Streamable HTTP：
//!
//! 1. `initialize` 握手 → 记下 `Mcp-Session-Id`；
//! 2. `notifications/initialized` 告知就绪（不发会有一半服务直接 400）；
//! 3. `tools/list` / `tools/call`。
//!
//! 响应可能是 `application/json`，也可能是 `text/event-stream`——后者要逐行找
//! `data:`，取**最后一条能解析的** JSON。另有 `rest` 兜底模式，给那些把能力摊成
//! 普通 REST 接口的服务用。

use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use super::client;
use crate::core::base::error::{BError, BResult, ErrorCode};
use crate::core::base::text;

/// 协议版本。服务端不认会回自己的版本，两侧都以服务端为准。
pub const PROTOCOL: &str = "2024-11-05";

/// 传输形态。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[derive(Default)]
pub enum Kind {
    /// JSON-RPC over HTTP。
    #[default]
    Streamable,
    /// 普通 REST：`GET /tools`、`POST /tools/{name}`。
    Rest,
}

impl Kind {
    pub fn parse(s: &str) -> Kind {
        match s.trim().to_ascii_lowercase().as_str() {
            "rest" | "http" => Kind::Rest,
            _ => Kind::Streamable,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Kind::Streamable => "streamable",
            Kind::Rest => "rest",
        }
    }
}

/// 一个服务。
#[derive(Debug, Clone, Default)]
pub struct Server {
    pub id: String,
    pub name: String,
    pub url: String,
    pub token: String,
    pub kind: Kind,
    pub on: bool,
}


impl Server {
    pub fn endpoint(&self) -> String {
        self.url.trim().trim_end_matches('/').to_string()
    }
}

/// 一个能力。
///
/// 这个类型既用于协议解析，也被 `feature::mcp` 存成能力缓存——所以带 serde。
/// 加 serde 不会让 net 反向依赖 feature。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Tool {
    pub server_id: String,
    pub server: String,
    /// 服务端原始名字。
    pub name: String,
    /// `mcp_服务_能力`，模型看到的名字。
    pub full: String,
    pub desc: String,
    /// 服务端给的 parameters，原样透传。
    pub schema: Value,
}

impl Tool {
    /// 给工具定义用的 JSON。
    pub fn definition(&self) -> Value {
        let mut params = self.schema.clone();
        if !params.is_object() {
            params = json!({ "type": "object", "properties": {} });
        }
        json!({
            "type": "function",
            "function": {
                "name": self.full,
                "description": format!("[MCP:{}] {}", self.server, text::truncate(self.desc.trim(), 300)),
                "parameters": params,
            }
        })
    }
}

/// 生成本地工具名：只留字母数字，其余压成 `_`。
pub fn norm_full(server: &str, tool: &str) -> String {
    let clean = |s: &str| -> String {
        s.chars()
            .map(|c| if c.is_ascii_alphanumeric() { c.to_ascii_lowercase() } else { '_' })
            .collect::<String>()
            .trim_matches('_')
            .to_string()
    };
    format!("mcp_{}_{}", clean(server), clean(tool))
}

fn rpc(method: &str, params: Value, id: u64) -> Value {
    json!({ "jsonrpc": "2.0", "id": id, "method": method, "params": params })
}

fn init_body() -> Value {
    rpc(
        "initialize",
        json!({
            "protocolVersion": PROTOCOL,
            "capabilities": {},
            "clientInfo": { "name": "baicodex", "version": crate::VERSION }
        }),
        1,
    )
}

/// 发一条 JSON-RPC，返回 `(响应, session_id)`。
async fn post_rpc(s: &Server, body: &Value, session: &str) -> BResult<(Value, String)> {
    let url = s.endpoint();
    if url.is_empty() {
        return Err(BError::invalid("MCP 服务地址是空的"));
    }
    let mut rb = client::shared()
        .post(&url)
        .timeout(Duration::from_secs(90))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream");
    if !s.token.trim().is_empty() {
        rb = rb.bearer_auth(s.token.trim());
    }
    if !session.is_empty() {
        rb = rb.header("Mcp-Session-Id", session);
    }

    let resp = rb.body(body.to_string()).send().await.map_err(|e| {
        BError::new(ErrorCode::Network, format!("连不上 MCP 服务：{e}")).retry_hint(true)
    })?;

    let sid = resp
        .headers()
        .get("mcp-session-id")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_string();
    let ctype = resp
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_lowercase();
    let status = resp.status();
    let raw = resp.text().await.unwrap_or_default();

    if !status.is_success() {
        return Err(BError::new(
            ErrorCode::Network,
            format!("MCP 返回 HTTP {}", status.as_u16()),
        )
        .with_detail(text::truncate(raw.trim(), 400)));
    }

    if ctype.contains("event-stream") {
        // SSE：逐行找 data:，取最后一条能解析的 JSON。
        let mut last = Value::Null;
        for line in raw.lines() {
            let Some(d) = line.strip_prefix("data:") else { continue };
            let d = d.trim();
            if d.is_empty() || d == "[DONE]" {
                continue;
            }
            if let Ok(v) = serde_json::from_str::<Value>(d) {
                last = v;
            }
        }
        if last.is_null() {
            return Err(BError::invalid("MCP 返回了空事件流"));
        }
        return Ok((last, sid));
    }

    let v: Value = serde_json::from_str(&raw).map_err(|_| {
        BError::invalid("MCP 响应不是 JSON").with_detail(text::truncate(raw.trim(), 300))
    })?;
    Ok((v, sid))
}

/// 握手。返回 session id（服务端不给就是空串）。
async fn handshake(s: &Server) -> BResult<String> {
    let (reply, sid) = post_rpc(s, &init_body(), "").await?;
    if let Some(err) = reply.get("error") {
        if !err.is_null() {
            return Err(BError::new(
                ErrorCode::Unsupported,
                format!("MCP 握手失败：{}", err["message"].as_str().unwrap_or("未知错误")),
            ));
        }
    }
    // 握手成功后必须发这条通知；失败不影响后续（有些服务不要求）。
    let note = json!({ "jsonrpc": "2.0", "method": "notifications/initialized" });
    let _ = post_rpc(s, &note, &sid).await;
    Ok(sid)
}

/// 拉能力表。
pub async fn list_tools(s: &Server) -> BResult<Vec<Tool>> {
    if s.kind == Kind::Rest {
        return list_rest(s).await;
    }
    let sid = handshake(s).await?;
    let (reply, _) = post_rpc(s, &rpc("tools/list", json!({}), 2), &sid).await?;
    if let Some(err) = reply.get("error") {
        if !err.is_null() {
            return Err(BError::new(
                ErrorCode::Unsupported,
                format!("列能力失败：{}", err["message"].as_str().unwrap_or("未知错误")),
            ));
        }
    }
    let arr = reply["result"]["tools"].as_array().cloned().unwrap_or_default();
    let mut out = Vec::new();
    for t in arr {
        let name = t["name"].as_str().unwrap_or("").to_string();
        if name.is_empty() {
            continue;
        }
        out.push(Tool {
            server_id: s.id.clone(),
            server: s.name.clone(),
            full: norm_full(&s.name, &name),
            desc: t["description"].as_str().unwrap_or("").to_string(),
            schema: t.get("inputSchema").cloned().unwrap_or_else(|| json!({"type":"object"})),
            name,
        });
    }
    Ok(out)
}

/// 调一个能力。
pub async fn call_tool(s: &Server, tool: &str, args: Value) -> BResult<String> {
    if s.kind == Kind::Rest {
        return call_rest(s, tool, args).await;
    }
    let sid = handshake(s).await?;
    let req = rpc("tools/call", json!({ "name": tool, "arguments": args }), 3);
    let (reply, _) = post_rpc(s, &req, &sid).await?;
    if let Some(err) = reply.get("error") {
        if !err.is_null() {
            return Err(BError::new(
                ErrorCode::Internal,
                format!("MCP 报错：{}", err["message"].as_str().unwrap_or("未知错误")),
            )
            .with_detail(err.to_string()));
        }
    }
    let result = &reply["result"];
    // 协议上的错误标记：isError=true 时 content 里是错误说明。
    let is_err = result["isError"].as_bool().unwrap_or(false);
    let text_out = flatten(result);
    if is_err {
        return Err(BError::new(ErrorCode::Internal, text_out));
    }
    Ok(if text_out.trim().is_empty() { "（MCP 没有返回内容）".into() } else { text_out })
}

/// 把 `result.content` 里的文本拼起来；没有 content 就整包 JSON。
fn flatten(result: &Value) -> String {
    if let Some(arr) = result["content"].as_array() {
        let mut out = Vec::new();
        for c in arr {
            match c["type"].as_str().unwrap_or("") {
                "text" => out.push(c["text"].as_str().unwrap_or("").to_string()),
                "resource" => {
                    let uri = c["resource"]["uri"].as_str().unwrap_or("");
                    let txt = c["resource"]["text"].as_str().unwrap_or("");
                    out.push(format!("[资源 {uri}]\n{txt}"));
                }
                "image" => {
                    let mime = c["mimeType"].as_str().unwrap_or("image");
                    out.push(format!("[图片 {mime}，已省略二进制]"));
                }
                other => out.push(format!("[{other}]")),
            }
        }
        return out.join("\n");
    }
    if result.is_null() {
        return String::new();
    }
    result.to_string()
}

async fn list_rest(s: &Server) -> BResult<Vec<Tool>> {
    let url = format!("{}/tools", s.endpoint());
    let mut rb = client::shared().get(&url).timeout(Duration::from_secs(30));
    if !s.token.trim().is_empty() {
        rb = rb.bearer_auth(s.token.trim());
    }
    let resp = rb.send().await.map_err(|e| BError::new(ErrorCode::Network, format!("连不上：{e}")))?;
    let status = resp.status();
    let raw = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(BError::new(ErrorCode::Network, format!("HTTP {}", status.as_u16()))
            .with_detail(text::truncate(raw.trim(), 300)));
    }
    let v: Value = serde_json::from_str(&raw).map_err(|e| BError::invalid(format!("返回不是 JSON：{e}")))?;
    let arr = v
        .as_array()
        .cloned()
        .or_else(|| v["tools"].as_array().cloned())
        .unwrap_or_default();
    let mut out = Vec::new();
    for t in arr {
        let name = t["name"].as_str().unwrap_or("").to_string();
        if name.is_empty() {
            continue;
        }
        out.push(Tool {
            server_id: s.id.clone(),
            server: s.name.clone(),
            full: norm_full(&s.name, &name),
            desc: t["description"].as_str().unwrap_or("").to_string(),
            schema: t.get("parameters").cloned().unwrap_or_else(|| json!({"type":"object"})),
            name,
        });
    }
    Ok(out)
}

async fn call_rest(s: &Server, tool: &str, args: Value) -> BResult<String> {
    let url = format!("{}/tools/{}", s.endpoint(), text::url_encode(tool));
    let mut rb = client::shared().post(&url).timeout(Duration::from_secs(90)).json(&args);
    if !s.token.trim().is_empty() {
        rb = rb.bearer_auth(s.token.trim());
    }
    let resp = rb.send().await.map_err(|e| BError::new(ErrorCode::Network, format!("连不上：{e}")))?;
    let status = resp.status();
    let raw = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(BError::new(ErrorCode::Network, format!("HTTP {}", status.as_u16()))
            .with_detail(text::truncate(raw.trim(), 300)));
    }
    Ok(raw)
}

/// 探活：能不能握手。
pub async fn ping(s: &Server) -> BResult<()> {
    if s.kind == Kind::Rest {
        let url = format!("{}/tools", s.endpoint());
        let resp = client::shared()
            .get(&url)
            .timeout(Duration::from_secs(15))
            .send()
            .await
            .map_err(|e| BError::new(ErrorCode::Network, format!("连不上：{e}")))?;
        return if resp.status().is_success() {
            Ok(())
        } else {
            Err(BError::new(ErrorCode::Network, format!("HTTP {}", resp.status().as_u16())))
        };
    }
    handshake(s).await.map(|_| ())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn full_name_sanitized() {
        assert_eq!(norm_full("My Server", "read-file"), "mcp_my_server_read_file");
        assert_eq!(norm_full("s", "a.b"), "mcp_s_a_b");
    }

    #[test]
    fn flatten_text_parts() {
        let r = json!({"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]});
        assert_eq!(flatten(&r), "a\nb");
    }

    #[test]
    fn kind_parse() {
        assert_eq!(Kind::parse("REST"), Kind::Rest);
        assert_eq!(Kind::parse(""), Kind::Streamable);
    }
}
