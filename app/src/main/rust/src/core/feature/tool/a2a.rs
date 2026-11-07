//! A2A 工具：`a2a`。
//!
//! 一个入口六个动作：`nodes` / `card` / `send` / `get` / `cancel` / `tasks`。
//! 对端可以写保存过的名字，也可以直接写 URL（临时用，不落盘）。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::jsonx;
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::net::a2a;

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

/// 执行。
pub async fn call(app: &App, args: &Value) -> ToolOutcome {
    if !app.cfg().tool.allow_network {
        return ToolOutcome::err("联网工具被关掉了（设置 → 工具 → 允许联网）", Route::Sandbox);
    }
    let act = {
        let a = s(args, "action").to_lowercase();
        if a.is_empty() { "nodes".into() } else { a }
    };
    let nodes = a2a::load();

    if act == "nodes" || act == "list_nodes" {
        return ToolOutcome::ok(a2a::list_text(&nodes), Route::Sandbox);
    }
    // 保存 / 删除对端（设置页之外的快捷入口）。
    if act == "add" || act == "save" {
        let name = s(args, "name");
        let url = s(args, "node");
        let url = if url.is_empty() { s(args, "url") } else { url };
        if url.trim().is_empty() {
            return ToolOutcome::err("a2a add 需要 node（URL）", Route::Sandbox);
        }
        let n = a2a::upsert(&name, &url, &s(args, "token"));
        return ToolOutcome::ok(format!("已保存对端「{}」→ {}", n.name, n.url), Route::Sandbox);
    }
    if act == "remove" || act == "delete" {
        let key = s(args, "node");
        if key.trim().is_empty() {
            return ToolOutcome::err("a2a remove 需要 node（名字或 id）", Route::Sandbox);
        }
        return if a2a::remove(&key) {
            ToolOutcome::ok(format!("已删除对端「{key}」"), Route::Sandbox)
        } else {
            ToolOutcome::err(format!("没有叫「{key}」的对端"), Route::Sandbox)
        };
    }

    let key = s(args, "node");
    let Some(node) = a2a::find(&nodes, &key) else {
        return ToolOutcome::err(
            format!("找不到对端「{key}」。先调 action=nodes 看已保存的，或直接写 http(s):// 地址。"),
            Route::Sandbox,
        );
    };

    let r = match act.as_str() {
        "card" | "info" => a2a::card(&node).await,
        "send" | "ask" | "run" => a2a::send(&node, &jsonx::get_str(args, &["text", "message", "task"], "")).await,
        "get" | "status" => a2a::get_task(&node, &jsonx::get_str(args, &["task", "id"], "")).await,
        "cancel" | "stop" => a2a::cancel(&node, &jsonx::get_str(args, &["task", "id"], "")).await,
        "tasks" => a2a::list_tasks(&node, &s(args, "context")).await,
        other => {
            return ToolOutcome::err(
                format!("不认识的 action：{other}（可用 nodes / add / remove / card / send / get / cancel / tasks）"),
                Route::Sandbox,
            )
        }
    };
    let out = ToolOutcome::ok(r.text, Route::Sandbox).with_meta(serde_json::json!({ "node": node.name }));
    if r.ok {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}
