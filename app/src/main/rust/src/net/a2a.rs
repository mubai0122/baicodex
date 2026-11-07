//! A2A（Agent2Agent）客户端：看对端名片、派任务、查进度、取消、列任务。
//!
//! 对端可以是保存过的名字 / id，也可以直接写 `http(s)://` 地址（临时用，不落盘）。
//! 协议按 JSON-RPC 2.0：`message/send`、`tasks/get`、`tasks/cancel`、`tasks/list`。
//!
//! ## 名片发现的顺序
//!
//! 1. `/.well-known/agent-card.json`（新规范）
//! 2. `/.well-known/agent.json`（旧规范）
//! 3. `/agent.json`（土办法）
//! 4. RPC `agent/getCard`（有些实现只暴露 RPC）
//!
//! 四条都试一遍比让用户去查文档快。

use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use super::client;
use crate::core::base::{dirs, fsx};

const FILE: &str = "a2a.json";

/// 一个对端。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Node {
    pub id: String,
    pub name: String,
    pub url: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub token: String,
}

impl Node {
    pub fn endpoint(&self) -> String {
        self.url.trim().trim_end_matches('/').to_string()
    }
}

/// 调用结果。
#[derive(Debug, Clone)]
pub struct Res {
    pub ok: bool,
    pub text: String,
}

impl Res {
    fn ok(t: impl Into<String>) -> Self {
        Self { ok: true, text: t.into() }
    }
    fn err(t: impl Into<String>) -> Self {
        Self { ok: false, text: t.into() }
    }
}

/// 读已保存的对端。
pub fn load() -> Vec<Node> {
    let p = dirs::file(FILE);
    let Ok(t) = std::fs::read_to_string(p) else { return Vec::new() };
    serde_json::from_str(&t).unwrap_or_default()
}

/// 保存对端列表。
pub fn save(nodes: &[Node]) -> bool {
    let Ok(t) = serde_json::to_string_pretty(nodes) else { return false };
    fsx::ensure_dir(dirs::base()).is_ok() && fsx::write_text(dirs::file(FILE), &t).is_ok()
}

/// 加一个对端（同名会覆盖 URL）。
pub fn upsert(name: &str, url: &str, token: &str) -> Node {
    let mut list = load();
    let n = name.trim();
    let u = url.trim().trim_end_matches('/').to_string();
    if let Some(x) = list.iter_mut().find(|x| x.name == n) {
        x.url = u;
        if !token.is_empty() {
            x.token = token.to_string();
        }
        let out = x.clone();
        save(&list);
        return out;
    }
    let node = Node {
        id: uuid::Uuid::new_v4().to_string(),
        name: n.to_string(),
        url: u,
        token: token.to_string(),
    };
    list.push(node.clone());
    save(&list);
    node
}

/// 删一个对端。
pub fn remove(key: &str) -> bool {
    let mut list = load();
    let before = list.len();
    list.retain(|x| x.id != key && x.name != key);
    if list.len() == before {
        return false;
    }
    save(&list)
}

/// 按 id / 名字 / 直连 URL 找一个对端。
pub fn find(nodes: &[Node], key: &str) -> Option<Node> {
    let k = key.trim();
    if k.is_empty() {
        return None;
    }
    if k.starts_with("http://") || k.starts_with("https://") {
        return Some(Node { id: String::new(), name: k.to_string(), url: k.trim_end_matches('/').to_string(), token: String::new() });
    }
    nodes
        .iter()
        .find(|n| n.id == k)
        .or_else(|| nodes.iter().find(|n| n.name == k))
        .cloned()
}

fn rpc_body(method: &str, params: Value) -> Value {
    json!({ "jsonrpc": "2.0", "id": 1, "method": method, "params": params })
}

async fn post(node: &Node, method: &str, params: Value) -> Result<Value, String> {
    let mut rb = client::shared()
        .post(node.endpoint())
        .timeout(Duration::from_secs(120))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json");
    if !node.token.trim().is_empty() {
        rb = rb.bearer_auth(node.token.trim());
    }
    let resp = rb.json(&rpc_body(method, params)).send().await.map_err(|e| format!("请求失败：{e}"))?;
    let status = resp.status();
    let text = resp.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(format!("HTTP {}：{}", status.as_u16(), crate::core::base::text::truncate(text.trim(), 200)));
    }
    serde_json::from_str::<Value>(&text).map_err(|e| format!("响应不是 JSON：{e}"))
}

/// 看对端名片。
pub async fn card(node: &Node) -> Res {
    for path in ["/.well-known/agent-card.json", "/.well-known/agent.json", "/agent.json"] {
        let url = format!("{}{path}", node.endpoint());
        let mut rb = client::shared().get(&url);
        if !node.token.trim().is_empty() {
            rb = rb.bearer_auth(node.token.trim());
        }
        if let Ok(resp) = rb.send().await {
            if resp.status().is_success() {
                if let Ok(text) = resp.text().await {
                    if let Ok(v) = serde_json::from_str::<Value>(&text) {
                        return Res::ok(render_card(&v));
                    }
                }
            }
        }
    }
    match post(node, "agent/getCard", json!({})).await {
        Ok(v) => Res::ok(render_card(&v["result"])),
        Err(e) => Res::err(format!("拿不到对端名片：{e}")),
    }
}

fn render_card(v: &Value) -> String {
    let name = v["name"].as_str().unwrap_or("（无名）");
    let desc = v["description"].as_str().unwrap_or("");
    let url = v["url"].as_str().unwrap_or("");
    let ver = v["version"].as_str().unwrap_or("");
    let mut s = format!("{name}\n{desc}\n接口：{url}\n版本：{ver}\n");
    if let Some(skills) = v["skills"].as_array() {
        s.push_str("技能：\n");
        for k in skills {
            s.push_str(&format!(
                "- {}：{}（id={}）\n",
                k["name"].as_str().unwrap_or(""),
                k["description"].as_str().unwrap_or(""),
                k["id"].as_str().unwrap_or("")
            ));
        }
    }
    s.trim_end().to_string()
}

/// 派任务。
pub async fn send(node: &Node, text: &str) -> Res {
    if text.trim().is_empty() {
        return Res::err("send 需要 text（要派过去的任务内容）");
    }
    let params = json!({
        "message": {
            "role": "user",
            "parts": [{ "kind": "text", "text": text }],
            "messageId": uuid::Uuid::new_v4().to_string()
        }
    });
    match post(node, "message/send", params).await {
        Ok(v) => {
            if let Some(err) = v.get("error") {
                if !err.is_null() {
                    return Res::err(format!("对端报错：{}", err["message"].as_str().unwrap_or("未知")));
                }
            }
            let task = &v["result"];
            let id = task["id"].as_str().unwrap_or("");
            let state = task["status"]["state"].as_str().unwrap_or("submitted");
            let mut out = format!("任务已提交：id={id}，状态={state}\n");
            if let Some(parts) = task["status"]["message"]["parts"].as_array() {
                for p in parts {
                    if let Some(t) = p["text"].as_str() {
                        out.push_str(t);
                        out.push('\n');
                    }
                }
            }
            if state != "completed" {
                out.push_str("\n（还没跑完，用 action=get 带这个 task id 查进度）");
            }
            Res::ok(out)
        }
        Err(e) => Res::err(format!("派任务失败：{e}")),
    }
}

/// 查任务。
pub async fn get_task(node: &Node, id: &str) -> Res {
    if id.trim().is_empty() {
        return Res::err("get 需要 task（任务 id）");
    }
    match post(node, "tasks/get", json!({ "id": id.trim() })).await {
        Ok(v) => {
            let t = &v["result"];
            let state = t["status"]["state"].as_str().unwrap_or("unknown");
            let mut s = format!("任务 {id}：{state}\n");
            if let Some(arts) = t["artifacts"].as_array() {
                for a in arts {
                    if let Some(parts) = a["parts"].as_array() {
                        for p in parts {
                            if let Some(x) = p["text"].as_str() {
                                s.push_str(x);
                                s.push('\n');
                            }
                        }
                    }
                }
            }
            // 有的实现把结果放在 status.message 里而不是 artifacts。
            if s.lines().count() <= 1 {
                if let Some(parts) = t["status"]["message"]["parts"].as_array() {
                    for p in parts {
                        if let Some(x) = p["text"].as_str() {
                            s.push_str(x);
                            s.push('\n');
                        }
                    }
                }
            }
            Res::ok(s.trim_end().to_string())
        }
        Err(e) => Res::err(format!("查进度失败：{e}")),
    }
}

/// 取消任务。
pub async fn cancel(node: &Node, id: &str) -> Res {
    if id.trim().is_empty() {
        return Res::err("cancel 需要 task（任务 id）");
    }
    match post(node, "tasks/cancel", json!({ "id": id.trim() })).await {
        Ok(v) => Res::ok(format!(
            "已请求取消：{}",
            v["result"]["status"]["state"].as_str().unwrap_or("unknown")
        )),
        Err(e) => Res::err(format!("取消失败：{e}")),
    }
}

/// 列任务。
pub async fn list_tasks(node: &Node, context: &str) -> Res {
    let params = if context.trim().is_empty() { json!({}) } else { json!({ "contextId": context.trim() }) };
    match post(node, "tasks/list", params).await {
        Ok(v) => {
            let arr = v["result"]["tasks"].as_array().cloned().unwrap_or_default();
            if arr.is_empty() {
                return Res::ok("对端没有任务");
            }
            let mut s = String::new();
            for t in arr.iter().take(30) {
                s.push_str(&format!(
                    "- {}  {}\n",
                    t["id"].as_str().unwrap_or(""),
                    t["status"]["state"].as_str().unwrap_or("")
                ));
            }
            Res::ok(s.trim_end().to_string())
        }
        Err(e) => Res::err(format!("列任务失败：{e}")),
    }
}

/// 对端列表。
pub fn list_text(nodes: &[Node]) -> String {
    if nodes.is_empty() {
        return "还没有保存过 A2A 对端。设置 → 扩展 → A2A 里加一条，或直接在 node 里写 http(s):// 地址。".into();
    }
    let mut s = format!("共 {} 个对端：\n", nodes.len());
    for n in nodes {
        s.push_str(&format!(
            "- {}  {}{}\n",
            n.name,
            n.url,
            if n.token.is_empty() { "" } else { "（带 token）" }
        ));
    }
    s.trim_end().to_string()
}
