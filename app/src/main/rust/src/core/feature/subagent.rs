//! 子智能体：把一件大事拆成几个互不依赖的小任务，并行跑，最后汇总。
//!
//! ## 为什么子任务之间不共享上下文
//!
//! 共享会让"并行"变成"串行 + 竞态"：两个子任务同时往同一个消息列表里写，结果不可预测。
//! 所以每个子任务有**自己的消息列表**，只带一句任务描述与最小必要背景。
//! 需要背景就在任务描述里写清楚——这逼着调用方把话说清楚，反而是好事。
//!
//! ## 为什么子任务不能再拆子任务
//!
//! 递归拆分会指数级放大请求数。子任务里的 `subagent_run` 直接拒绝。

use serde_json::{json, Value};

use crate::core::feature::agent::{client, options};
use crate::core::feature::app::event::AppEvent;
use crate::core::feature::app::App;
use crate::core::feature::model::message::{ChatMsg, Role, ToolCall};
use crate::core::feature::tool::{dispatch, spec};
use crate::net::llm;

/// 每个子任务最多几轮工具。
const SUB_ROUNDS: usize = 8;

/// 一个子任务的结果。
#[derive(Debug, Clone)]
pub struct SubResult {
    pub title: String,
    pub ok: bool,
    pub stopped: bool,
    pub text: String,
    pub tools: usize,
}

/// 解析任务列表：`tasks: ["...", "..."]` 或 `tasks: [{title, task}]`。
pub fn parse_tasks(args: &Value) -> Vec<(String, String)> {
    let Some(arr) = args.get("tasks").and_then(|v| v.as_array()) else {
        // 兼容只给一个 task 的写法。
        if let Some(t) = args.get("task").and_then(|v| v.as_str()) {
            let t = t.trim();
            if !t.is_empty() {
                return vec![(short_title(t, 0), t.to_string())];
            }
        }
        return Vec::new();
    };
    let mut out = Vec::new();
    for (i, t) in arr.iter().enumerate() {
        if let Some(s) = t.as_str() {
            let s = s.trim();
            if !s.is_empty() {
                out.push((short_title(s, i), s.to_string()));
            }
            continue;
        }
        let task = t
            .get("task")
            .or_else(|| t.get("prompt"))
            .or_else(|| t.get("text"))
            .and_then(|x| x.as_str())
            .unwrap_or("")
            .trim();
        if task.is_empty() {
            continue;
        }
        let title = t
            .get("title")
            .and_then(|x| x.as_str())
            .map(|x| x.trim().to_string())
            .filter(|x| !x.is_empty())
            .unwrap_or_else(|| short_title(task, i));
        out.push((title, task.to_string()));
    }
    out
}

fn short_title(s: &str, i: usize) -> String {
    let t = crate::core::base::text::truncate(crate::core::base::text::one_line(s).trim(), 14);
    if t.is_empty() {
        format!("子任务 {}", i + 1)
    } else {
        t
    }
}

/// 跑一批子任务。
///
/// 并发用 `join_all` 而不是 `spawn`：子任务借用 `&App`，不需要 `'static`，
/// 也就不需要把 App 塞进 Arc。代价是它们跑在同一个任务上——但它们本来就在等网络，
/// 占不到 CPU。
pub async fn run(app: &App, tasks: Vec<(String, String)>) -> Vec<SubResult> {
    let cfg = app.cfg();
    let limit = cfg.agent.parallel_tools.clamp(1, 6);
    let mut out = Vec::with_capacity(tasks.len());
    for chunk in tasks.chunks(limit) {
        let futs = chunk.iter().map(|(title, task)| run_one(app, title, task));
        let results = futures_util::future::join_all(futs).await;
        for ((title, _), r) in chunk.iter().zip(results) {
            out.push(r.unwrap_or_else(|e| SubResult {
                title: title.clone(),
                ok: false,
                stopped: false,
                text: e,
                tools: 0,
            }));
        }
    }
    out
}

async fn run_one(app: &App, title: &str, task: &str) -> Result<SubResult, String> {
    let cfg = app.cfg();
    let client = client(app).map_err(|e| e.user_text())?;
    let model = cfg.provider.model.clone();
    let defs = spec::definitions(app);
    let mut opt = options(app, Some(defs.clone()));
    opt.no_stream = true; // 子任务不推流：界面只显示"正在跑 N 个子任务"。
    opt.tools = Some(defs);

    let mut messages: Vec<ChatMsg> = vec![ChatMsg {
        role: Role::User,
        content: format!(
            "{task}\n\n（这是一个子任务，独立完成；不要反问，不要提及其他子任务。做完用一段话给结论。）"
        ),
        ..Default::default()
    }];
    let mut tools = 0usize;

    for _ in 0..SUB_ROUNDS {
        if app.stopped() {
            return Ok(SubResult { title: title.into(), ok: false, stopped: true, text: "已停止".into(), tools });
        }
        let body = json!(messages.iter().map(|m| m.to_api_json(cfg.model.vision)).collect::<Vec<_>>());
        let reply = client.chat(&model, &body, &opt).await.map_err(|e| e.user_text())?;
        let content = llm::text_of(&reply, "content");
        let calls = parse_calls(&reply);

        messages.push(ChatMsg {
            role: Role::Assistant,
            content: content.clone(),
            tool_calls: calls.clone(),
            ..Default::default()
        });

        if calls.is_empty() {
            return Ok(SubResult {
                title: title.into(),
                ok: true,
                stopped: false,
                text: if content.trim().is_empty() { "（没有输出）".into() } else { content },
                tools,
            });
        }

        for call in &calls {
            tools += 1;
            let args = serde_json::from_str::<Value>(&call.arguments).unwrap_or_else(|_| json!({}));
            // 子任务里不允许再拆子任务 / 提问（没人能回答）/ 跑工作流（会写库）。
            let name = call.name.as_str();
            let out = if matches!(name, "subagent_run" | "ask_user" | "flow_run") {
                crate::core::feature::tool::ToolOutcome::err(
                    "子任务里不能用这个工具",
                    crate::core::feature::model::session::Route::Sandbox,
                )
            } else if denied(app, name, &call.arguments) {
                crate::core::feature::tool::ToolOutcome::err(
                    "这个操作需要用户确认，子任务不能弹确认；改用只读方式或直接说明",
                    crate::core::feature::model::session::Route::Sandbox,
                )
            } else {
                let seq = app.begin_tool(name, &call.arguments, true);
                let r = dispatch::execute(app, name, &args).await;
                app.end_tool(seq);
                app.emit(AppEvent::ToolEnd { seq });
                r
            };
            messages.push(ChatMsg {
                role: Role::Tool,
                content: out.model_text(),
                tool_call_id: Some(call.id.clone()),
                tool_name: Some(call.name.clone()),
                is_error: !out.ok,
                ..Default::default()
            });
        }
    }

    Ok(SubResult {
        title: title.into(),
        ok: false,
        stopped: false,
        text: format!("跑了 {SUB_ROUNDS} 轮还没收尾，可能任务太大，拆细一点"),
        tools,
    })
}

/// 需要确认的一律当作拒绝（子任务没有界面）。
fn denied(app: &App, name: &str, args: &str) -> bool {
    let risk = crate::core::feature::tool::registry::risk_of(name);
    let d = app
        .policy
        .read()
        .map(|p| p.decide(name, args, risk))
        .unwrap_or_else(|_| crate::core::security::policy::Decision::deny("策略表不可用"));
    d.policy == crate::core::security::policy::Policy::Deny
        || d.policy == crate::core::security::policy::Policy::Ask
}

fn parse_calls(reply: &Value) -> Vec<ToolCall> {
    let Some(arr) = reply["tool_calls"].as_array() else { return Vec::new() };
    arr.iter()
        .filter_map(|c| {
            let f = &c["function"];
            let name = f["name"].as_str().unwrap_or("").trim().to_string();
            if name.is_empty() {
                return None;
            }
            Some(ToolCall {
                id: c["id"].as_str().unwrap_or("").to_string(),
                name,
                arguments: {
                    let a = f["arguments"].as_str().unwrap_or("{}");
                    if a.trim().is_empty() { "{}".into() } else { a.to_string() }
                },
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_both_shapes() {
        let a = json!({"tasks": ["查一下天气", {"title": "读日志", "task": "看 /tmp/a.log"}]});
        let t = parse_tasks(&a);
        assert_eq!(t.len(), 2);
        assert_eq!(t[1].0, "读日志");
    }

    #[test]
    fn single_task_shape() {
        let a = json!({"task": "只做一件事"});
        assert_eq!(parse_tasks(&a).len(), 1);
    }
}
