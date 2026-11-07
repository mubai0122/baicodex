//! MCP 工具：`mcp_服务_能力`。
//!
//! 名字是运行时拼出来的，`registry` 里没有常量。这一层做三件事：
//!
//! 1. 从 `Mcp` 快照里解析出"哪个服务、哪个能力"；
//! 2. 放掉锁再 await（`RwLock` 跨 await 会卡住整个应用）；
//! 3. 失败原因回写 `last_error`，并把文本交给 `ToolOutcome`。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::net::mcp as proto;

/// 执行。
pub async fn call(app: &App, name: &str, args: &Value) -> ToolOutcome {
    // 快照：拿完就放锁。
    let resolved = {
        let m = app.mcp.read().unwrap_or_else(|e| e.into_inner());
        m.resolve(name)
    };
    let (server, tool) = match resolved {
        Ok(v) => v,
        Err(e) => {
            let msg = e.user_text();
            if let Ok(mut w) = app.mcp.write() {
                w.set_error(msg.clone());
            }
            return ToolOutcome::err(msg, Route::Sandbox);
        }
    };

    match proto::call_tool(&server, &tool, args.clone()).await {
        Ok(text) => {
            if let Ok(mut w) = app.mcp.write() {
                w.clear_error();
            }
            ToolOutcome::ok(text, Route::Sandbox)
                .with_meta(serde_json::json!({ "mcp": server.name, "tool": tool }))
        }
        Err(e) => {
            let msg = e.user_text();
            if let Ok(mut w) = app.mcp.write() {
                w.set_error(msg.clone());
            }
            ToolOutcome::err(format!("MCP 调用失败：{msg}"), Route::Sandbox)
        }
    }
}

/// 刷新一个服务的能力表（设置页的"刷新"按钮走这里）。
pub async fn refresh(app: &App, id: &str) -> Result<usize, String> {
    // 先把要改的数据拿出来，在锁外做网络请求。
    let cfg = {
        let m = app.mcp.read().unwrap_or_else(|e| e.into_inner());
        m.servers.iter().find(|s| s.id == id || s.name == id).cloned()
    };
    let Some(cfg) = cfg else { return Err("服务不存在".into()) };
    match proto::list_tools(&cfg.to_server()).await {
        Ok(list) => {
            let n = list.len();
            if let Ok(mut w) = app.mcp.write() {
                w.tools.retain(|t| t.server_id != cfg.id);
                w.tools.extend(list);
                w.tools.sort_by(|a, b| a.full.cmp(&b.full));
                w.clear_error();
                w.save();
            }
            Ok(n)
        }
        Err(e) => {
            let msg = e.user_text();
            if let Ok(mut w) = app.mcp.write() {
                w.set_error(msg.clone());
            }
            Err(msg)
        }
    }
}
