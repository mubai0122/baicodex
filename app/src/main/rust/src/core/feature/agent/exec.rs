//! 工具调用的执行闸门。
//!
//! 一次工具调用要过五道门，顺序不能换：
//!
//! ```text
//! 1. 参数解析      非法 JSON → 直接失败（别猜模型想传什么）
//! 2. 模式校验      这个模式下有没有这个工具
//! 3. 策略判定      硬红线 / 参数规则 / 工具默认 / 风险兜底
//! 4. 用户确认      Ask 时才问；Deny 不问也不执行
//! 5. 审计落盘      记完再执行，执行失败也留痕
//! ```
//!
//! ## 为什么策略判定在确认之前
//!
//! 反过来的话，用户会先看到"要不要允许 rm -rf /"，再被系统拒绝——那是在浪费用户的
//! 注意力，也在暗示"点了允许就能过"。

use serde_json::Value;

use crate::core::feature::app::App;
use crate::core::feature::model::message::ToolCall;
use crate::core::feature::model::mode::AgentMode;
use crate::core::feature::model::session::Route;
use crate::core::feature::tool::{dispatch, registry as R, ToolOutcome};
use crate::core::security::policy::{Decision, Policy};

/// 执行一次调用。
pub async fn run_call(app: &App, call: &ToolCall) -> ToolOutcome {
    let name = call.name.trim().to_string();

    // 1. 参数
    let args = match parse_args(&call.arguments) {
        Ok(v) => v,
        Err(e) => return ToolOutcome::err(format!("参数不是合法 JSON：{e}"), Route::Sandbox),
    };

    // 2. 模式校验
    let cfg = app.cfg();
    if !R::allowed(&name, cfg.agent.mode) && !is_dynamic(app, &name) {
        return ToolOutcome::err(
            format!("当前模式（{}）下没有 {name} 这个工具", cfg.agent.mode.label()),
            Route::Sandbox,
        );
    }
    if cfg.agent.mode == AgentMode::Plan && R::PLAN_OFF.contains(&name.as_str()) {
        return ToolOutcome::err(
            format!("计划模式下 {name} 不可用：先出计划，用户确认后再动手"),
            Route::Sandbox,
        );
    }

    // 3. 策略
    let risk = R::risk_of(&name);
    let decision = {
        let p = app.policy.read().unwrap_or_else(|e| e.into_inner());
        p.decide(&name, &call.arguments, risk)
    };
    if decision.policy == Policy::Deny {
        audit(app, &name, &call.arguments, false, &decision.reason);
        return ToolOutcome::err(format!("被拒绝：{}", decision.reason), Route::Sandbox);
    }

    // 4. 确认
    if decision.policy == Policy::Ask {
        let ok = app
            .confirm_tool(call, &args, Route::Sandbox, R::is_high_risk(&name))
            .await;
        if !ok {
            audit(app, &name, &call.arguments, false, "用户拒绝");
            return ToolOutcome::err("用户拒绝了这个操作。不要重复同一调用，换方案或直接说明。", Route::Sandbox);
        }
    }

    // 5. 执行
    let seq = app.begin_tool(&name, &call.arguments, false);
    let out = if needs_loop(&name) {
        run_loop_tool(app, &name, &args).await
    } else {
        dispatch::execute(app, &name, &args).await
    };
    app.end_tool(seq);
    audit(app, &name, &call.arguments, out.ok, &crate::core::base::text::truncate(&out.text, 200));
    out
}

/// 这些工具需要主循环的能力（提问、子智能体、工作流）。
fn needs_loop(name: &str) -> bool {
    matches!(name, R::ASK | R::SUB | R::FLOW_RUN)
}

/// 动态工具（MCP / 技能包）不受 `registry` 的静态表约束。
fn is_dynamic(app: &App, name: &str) -> bool {
    if name.starts_with(R::MCP_PREFIX) {
        return app.mcp.read().map(|m| m.find(name).is_some()).unwrap_or(false);
    }
    app.packs.read().map(|p| p.find(name).is_some()).unwrap_or(false)
}

fn parse_args(raw: &str) -> Result<Value, String> {
    let t = raw.trim();
    if t.is_empty() {
        return Ok(Value::Object(Default::default()));
    }
    // 有些模型会把参数再包一层字符串。
    if t.starts_with('"') && t.ends_with('"') {
        if let Ok(inner) = serde_json::from_str::<String>(t) {
            return serde_json::from_str::<Value>(&inner).map_err(|e| e.to_string());
        }
    }
    serde_json::from_str::<Value>(t).map_err(|e| e.to_string())
}

async fn run_loop_tool(app: &App, name: &str, args: &Value) -> ToolOutcome {
    match name {
        R::ASK => ask(app, args).await,
        R::SUB => subagent(app, args).await,
        R::FLOW_RUN => flow(app, args).await,
        _ => ToolOutcome::err("这个工具需要主循环，但主循环不认识它", Route::Sandbox),
    }
}

/// `ask_user`：把问题抛给界面，等回答。
async fn ask(app: &App, args: &Value) -> ToolOutcome {
    let raw = args.get("questions").and_then(|v| v.as_array()).cloned().unwrap_or_default();
    let mut qs = Vec::new();
    for q in raw {
        let text = crate::core::base::jsonx::get_str(&q, &["question", "q", "text"], "");
        if text.trim().is_empty() {
            continue;
        }
        let opts = q
            .get("options")
            .and_then(|v| v.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|o| {
                        let label = o.as_str().map(|s| s.to_string()).or_else(|| {
                            o.get("label").and_then(|x| x.as_str()).map(|s| s.to_string())
                        })?;
                        let desc = o.get("desc").and_then(|x| x.as_str()).unwrap_or("").to_string();
                        Some(crate::core::feature::app::AskOption { label, desc })
                    })
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        qs.push(crate::core::feature::app::AskQuestion {
            id: crate::core::base::jsonx::get_str(&q, &["id"], ""),
            question: text,
            options: opts,
            multi: crate::core::base::jsonx::get_bool(&q, &["multi"], false),
            other: crate::core::base::jsonx::get_bool(&q, &["other"], true),
        });
    }
    if qs.is_empty() {
        return ToolOutcome::err("ask_user 需要 questions（每题给 2-4 个选项）", Route::Sandbox);
    }
    if qs.len() > 4 {
        qs.truncate(4);
    }
    let answers = app.ask_user(qs).await;
    if answers.iter().all(|a| a.trim().is_empty()) {
        return ToolOutcome::err("用户没有回答（可能关掉了弹窗）。换个方式问，或先按最合理的假设做并说明。", Route::Sandbox);
    }
    let mut s = String::from("用户回答：\n");
    for (i, a) in answers.iter().enumerate() {
        s.push_str(&format!("{}. {}\n", i + 1, a));
    }
    ToolOutcome::ok(s.trim_end().to_string(), Route::Sandbox)
}

/// `subagent_run`：拆出若干子任务并行跑。
async fn subagent(app: &App, args: &Value) -> ToolOutcome {
    let tasks = crate::core::feature::subagent::parse_tasks(args);
    if tasks.is_empty() {
        return ToolOutcome::err("subagent_run 需要 tasks（每个子任务一句完整的话）", Route::Sandbox);
    }
    if tasks.len() > 6 {
        return ToolOutcome::err("子任务最多 6 个（再多并行也没意义）", Route::Sandbox);
    }
    let results = crate::core::feature::subagent::run(app, tasks).await;
    let mut out = String::new();
    for r in &results {
        out.push_str(&format!(
            "【{}】{}{}\n{}\n\n",
            r.title,
            if r.ok { "" } else { "失败 " },
            if r.stopped { "（已停止）" } else { "" },
            r.text.trim()
        ));
    }
    let all_ok = results.iter().all(|r| r.ok);
    let o = ToolOutcome::ok(out.trim_end().to_string(), Route::Sandbox)
        .with_meta(serde_json::json!({ "tasks": results.len() }));
    if all_ok {
        o
    } else {
        ToolOutcome { ok: false, ..o }
    }
}

/// `flow_run`：跑一条工作流。
async fn flow(app: &App, args: &Value) -> ToolOutcome {
    let id = crate::core::base::jsonx::get_str(args, &["id", "name", "flow"], "");
    let flow = {
        let flows = app.flows.read().unwrap_or_else(|e| e.into_inner());
        flows.get(&id).cloned()
    };
    let Some(f) = flow else {
        return ToolOutcome::err(
            format!("没有叫「{id}」的工作流：先用 flow_list 看有哪些"),
            Route::Sandbox,
        );
    };
    if let Some(bad) = f.bad() {
        return ToolOutcome::err(format!("工作流不合法：{bad}"), Route::Sandbox);
    }
    app.notice(format!("开始执行工作流「{}」（{} 步）", f.name, f.steps.len()));
    let mut log = String::new();
    let mut ok_all = true;

    for (i, step) in f.steps.iter().enumerate() {
        if app.stopped() {
            log.push_str(&format!("\n第 {} 步之前被停止", i + 1));
            break;
        }
        app.status(format!("工作流 {}/{}：{}", i + 1, f.steps.len(), step.title));
        let mut attempt = 0u32;
        loop {
            let text = format!(
                "（工作流「{}」第 {}/{} 步：{}）\n{}",
                f.name,
                i + 1,
                f.steps.len(),
                step.title,
                step.task
            );
            // 这里必须 Box::pin：turn::run → drive → 执行工具 → flow_run → 又回到
            // turn::run，形成异步递归。不装箱的话 future 类型无限大，编译器会拒绝。
            let r = Box::pin(crate::core::feature::agent::turn::run(app, &text)).await;
            match r {
                Ok(o) if !o.text.trim().is_empty() => {
                    log.push_str(&format!("【{}】\n{}\n\n", step.title, o.text.trim()));
                    break;
                }
                Ok(_) => {
                    log.push_str(&format!("【{}】没有输出\n\n", step.title));
                    break;
                }
                Err(e) => {
                    if attempt < step.retries {
                        attempt += 1;
                        app.notice(format!("第 {} 步失败，重试 {attempt}/{}", i + 1, step.retries));
                        continue;
                    }
                    ok_all = false;
                    log.push_str(&format!("【{}】失败：{}\n\n", step.title, e.user_text()));
                    if step.skip_on_fail {
                        break;
                    }
                    {
                        let mut flows = app.flows.write().unwrap_or_else(|e| e.into_inner());
                        flows.record(&f.id, false, &e.user_text());
                    }
                    return ToolOutcome::err(log.trim_end().to_string(), Route::Sandbox);
                }
            }
        }
    }

    {
        let mut flows = app.flows.write().unwrap_or_else(|e| e.into_inner());
        flows.record(&f.id, ok_all, &log);
    }
    let o = ToolOutcome::ok(log.trim_end().to_string(), Route::Sandbox)
        .with_meta(serde_json::json!({ "flow": f.id, "steps": f.steps.len() }));
    if ok_all {
        o
    } else {
        ToolOutcome { ok: false, ..o }
    }
}

/// 记审计。审计写失败不影响执行——它是旁路。
fn audit(app: &App, tool: &str, args: &str, ok: bool, detail: &str) {
    if !app.cfg().security.audit {
        return;
    }
    let target = crate::core::base::text::truncate(&crate::core::security::redact::clean(args), 160);
    app.audit.log("agent", tool, &target, ok, crate::core::security::redact::clean(detail));
}

/// 给界面用的"这条调用会被怎么处理"，不执行。
pub fn preview(app: &App, name: &str, args_json: &str) -> Decision {
    let risk = R::risk_of(name);
    let p = app.policy.read().unwrap_or_else(|e| e.into_inner());
    p.decide(name, args_json, risk)
}
