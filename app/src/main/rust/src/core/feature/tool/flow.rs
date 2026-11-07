//! 工作流工具：`flow_run` / `flow_save` / `flow_list`。
//!
//! `flow_run` 必须由主循环执行：它要把每个步骤当成一次完整的 Agent 回合跑，
//! 中间还要发事件、等用户确认、检查停止信号。这些能力 `dispatch` 里没有，
//! 所以这里明确报错而不是假装跑一遍。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::jsonx;
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::core::feature::workflow::{Flow, Step};
use crate::exec::alarms;

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

fn b(v: &Value, k: &str, d: bool) -> bool {
    jsonx::get_bool(v, &[k], d)
}

fn i(v: &Value, k: &str) -> i64 {
    jsonx::get_i64(v, &[k], 0)
}

/// `flow_run`：只能由主循环驱动。
pub async fn run_tool(_app: &App, args: &Value) -> ToolOutcome {
    let id = jsonx::get_str(args, &["id", "name", "flow"], "");
    let note = if id.trim().is_empty() {
        "flow_run 需要 id（先用 flow_list 看有哪些）"
    } else {
        "flow_run 需要由主循环执行（它要跑完整的 Agent 回合）；单独调用无法执行"
    };
    ToolOutcome::err(note, Route::Sandbox)
}

/// `flow_save`。
pub fn save_tool(app: &App, args: &Value) -> ToolOutcome {
    let raw = args.get("steps").and_then(|v| v.as_array()).cloned().unwrap_or_default();
    let mut steps = Vec::new();
    for st in raw {
        let task = jsonx::get_str(&st, &["task", "text"], "");
        if task.trim().is_empty() {
            continue;
        }
        steps.push(Step {
            title: {
                let t = s(&st, "title");
                if t.trim().is_empty() { "步骤".into() } else { t }
            },
            task,
            retries: i(&st, "retry").clamp(0, 3) as u32,
            skip_on_fail: s(&st, "on_fail") == "skip",
        });
    }
    if steps.is_empty() {
        return ToolOutcome::err("flow_save 需要至少一个带 task 的步骤", Route::Sandbox);
    }

    let daily = b(args, "daily", false);
    let at = alarms::parse_at(&s(args, "at"), daily);
    let name = {
        let n = s(args, "name").trim().to_string();
        if n.is_empty() { "工作流".to_string() } else { n }
    };
    let auto = b(args, "auto", true);
    let on = b(args, "on", true);
    let id_arg = jsonx::get_str(args, &["id", "flow"], "");

    let mut flows = app.flows.write().unwrap_or_else(|e| e.into_inner());
    let existing = flows.get(&id_arg).cloned();

    let flow = match existing {
        Some(mut f) => {
            f.name = name;
            f.steps = steps;
            f.auto = auto;
            f.on = on;
            if at > 0 {
                f.at = at;
                f.daily = daily;
            } else if !s(args, "at").trim().is_empty() {
                // 用户显式写了个已过期的时间：清掉定时而不是沿用旧的。
                f.at = 0;
                f.daily = false;
            }
            f
        }
        None => {
            let f = Flow {
                id: String::new(),
                name,
                steps,
                rounds: crate::core::feature::workflow::DEFAULT_ROUNDS,
                at,
                daily,
                auto,
                on,
                last_at: 0,
                last_ok: false,
                last_note: String::new(),
            };
            if f.at == 0 && !s(args, "at").trim().is_empty() {
                return ToolOutcome::err(
                    format!(
                        "时间「{}」不合法或已经过去了。每天型的写 HH:MM（如 08:30），单次的写 yyyy-MM-dd HH:mm",
                        s(args, "at")
                    ),
                    Route::Sandbox,
                );
            }
            f
        }
    };

    if let Some(bad) = flow.bad() {
        return ToolOutcome::err(format!("工作流不合法：{bad}"), Route::Sandbox);
    }

    let id = flows.upsert(flow.clone());
    let saved = flows.get(&id).cloned().unwrap_or(flow);
    drop(flows);

    let mut msg = format!("已保存工作流「{}」（id={}），共 {} 步", saved.name, id, saved.steps.len());
    if saved.at > 0 {
        msg.push_str(&format!(
            "，{}{}",
            if saved.daily { "每天 " } else { "" },
            alarms::fmt(saved.at)
        ));
        msg.push_str(if saved.auto { " 自动执行" } else { " 只提醒" });
    } else {
        msg.push_str("，未绑定定时");
    }
    msg.push_str("。要马上跑就调 flow_run 带上这个 id。");
    ToolOutcome::ok(msg, Route::Sandbox).with_meta(serde_json::json!({ "id": id }))
}

/// `flow_list`。
pub fn list_tool(app: &App) -> ToolOutcome {
    let flows = app.flows.read().unwrap_or_else(|e| e.into_inner());
    if flows.is_empty() {
        return ToolOutcome::ok("还没有保存过工作流。可以用 flow_save 建一条。", Route::Sandbox);
    }
    let mut out = format!("共 {} 条工作流：\n", flows.len());
    for f in &flows.list {
        out.push('\n');
        out.push_str(&f.detail());
        out.push('\n');
    }
    ToolOutcome::ok(out.trim_end().to_string(), Route::Sandbox)
}
