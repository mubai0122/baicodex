//! 一轮对话：组请求 → 收流 → 执行工具 → 再发，直到模型不再调工具。
//!
//! 循环在 `drive` 里，除它之外都是小函数——主循环难读的根源往往是"顺手在循环里
//! 做了太多别的事"（存库、算钱、生成标题）。
//!
//! ## 轮次上限
//!
//! `agent.max_rounds` 为 0 表示不限（默认）。真的不限会有风险：模型陷入
//! "调工具 → 失败 → 再调同一个"的死循环。所以即使配置为 0，这里还有一道
//! [HARD_LIMIT]：连跑 64 轮工具还没收尾，就当异常终止并告诉用户。

use std::sync::atomic::Ordering;

use serde_json::{json, Value};

use crate::core::base::error::{BError, BResult};
use crate::core::feature::agent::{client, exec, options, title};
use crate::core::feature::app::event::AppEvent;
use crate::core::feature::app::App;
use crate::core::feature::context::budget::{self, Level};
use crate::core::feature::context::compact;
use crate::core::feature::context::prompt;
use crate::core::feature::model::message::{ChatMsg, MsgMeta, Role, ToolCall};
use crate::core::feature::model::mode::AgentMode;
use crate::core::feature::tool::spec;
use crate::net::llm;

/// 硬上限：连跑这么多轮工具调用还没收尾，强制结束。
pub const HARD_LIMIT: usize = 64;

/// 一轮的结果。
#[derive(Debug, Clone, Default)]
pub struct TurnOutcome {
    /// 最后一条助手正文。
    pub text: String,
    /// 执行了几轮请求。
    pub rounds: usize,
    /// 执行了几次工具。
    pub tools: usize,
    /// 是否被用户停止。
    pub stopped: bool,
    pub prompt: i64,
    pub cached: i64,
    pub completion: i64,
}

impl TurnOutcome {
    pub fn tokens(&self) -> i64 {
        self.prompt + self.completion
    }
}

fn set_busy(app: &App, busy: bool) {
    {
        let mut st = app.state.lock().unwrap_or_else(|e| e.into_inner());
        st.busy = busy;
        if !busy {
            st.finish();
        }
    }
    app.emit(AppEvent::Busy { busy });
}

/// 跑一轮。
pub async fn run(app: &App, user_text: &str) -> BResult<TurnOutcome> {
    if app.session_id().is_none() {
        app.ensure_session()?;
    }
    set_busy(app, true);
    let r = drive(app, user_text).await;
    set_busy(app, false);
    match &r {
        Ok(_) => {
            let mut st = app.state.lock().unwrap_or_else(|e| e.into_inner());
            st.last_error = None;
        }
        Err(e) => {
            let mut st = app.state.lock().unwrap_or_else(|e| e.into_inner());
            st.last_error = Some(e.user_text());
        }
    }
    r
}

async fn drive(app: &App, user_text: &str) -> BResult<TurnOutcome> {
    let mut out = TurnOutcome::default();
    let mut rounds = 0usize;

    loop {
        if app.stopped() {
            out.stopped = true;
            app.notice("已停止");
            break;
        }
        rounds += 1;
        out.rounds = rounds;
        if rounds > HARD_LIMIT {
            return Err(BError::budget(format!(
                "这一轮已经连续调了 {HARD_LIMIT} 次工具还没收尾，先停下。可能是某个工具一直失败，看看最近的报错。"
            )));
        }

        // —— 1. 预算与压缩 ——
        let cfg = app.cfg();
        let messages = app.messages();
        let defs = tool_definitions(app, &cfg);
        let extra = prompt::build(app, user_text).chars().count() + estimate_json(&defs);
        let b = budget::compute(&messages, &cfg.context, extra);
        if b.need_compact() && cfg.context.auto_compact {
            app.status(if b.must_compact() { "上下文满了，正在压缩…" } else { "上下文偏满，压缩较早的对话…" });
            match compact_now(app, &cfg.context).await {
                Ok(n) if n > 0 => app.notice(format!("已压缩 {n} 条较早的对话")),
                Ok(_) => {}
                Err(e) => app.notice(format!("压缩失败（继续用原上下文）：{}", e.user_text())),
            }
        } else if b.level == Level::Tight {
            app.status("上下文偏满，回答会尽量简洁");
        }

        // —— 2. 组请求 ——
        let msgs_json = build_messages(app, user_text, cfg.model.vision);
        let llm_client = client(app)?;
        let mut opt = options(app, if cfg.model.tools { Some(defs.clone()) } else { None });
        opt.no_stream = cfg.provider.no_stream || !cfg.agent.stream;
        let model = cfg.provider.model.clone();

        // —— 3. 发请求 ——
        let started = std::time::Instant::now();
        {
            let mut st = app.state.lock().unwrap_or_else(|e| e.into_inner());
            st.clear_stream();
        }
        // 首字延迟：第一次收到正文时记下，用来算"打字速度"（不含排队时间）。
        let first_at = std::sync::Arc::new(std::sync::atomic::AtomicI64::new(0));

        let reply: Value = if opt.no_stream {
            llm_client.chat(&model, &msgs_json, &opt).await?
        } else {
            let cancel = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
            let flag = app.stop_flag.load(Ordering::Relaxed);
            cancel.store(flag, Ordering::Relaxed);
            let c2 = cancel.clone();
            let app_ref = app;
            let f_at = first_at.clone();
            let t0 = started;
            let mut on_delta = move |content: &str, reason: &str| {
                if app_ref.stopped() {
                    c2.store(true, Ordering::Relaxed);
                    return;
                }
                if !content.is_empty() && f_at.load(Ordering::Relaxed) == 0 {
                    f_at.store(t0.elapsed().as_millis() as i64, Ordering::Relaxed);
                }
                {
                    let mut st = app_ref.state.lock().unwrap_or_else(|e| e.into_inner());
                    st.stream_text.push_str(content);
                    st.stream_reason.push_str(reason);
                }
                app_ref.emit(AppEvent::Delta {
                    content: content.to_string(),
                    reason: reason.to_string(),
                });
            };
            let r = llm_client
                .chat_stream(&model, &msgs_json, &opt, Some(cancel.clone()), &mut on_delta)
                .await;
            match r {
                Ok(v) => v,
                Err(e) => {
                    // 鉴权失败且配了多 key：换一个再试一次。
                    if e.is(crate::core::base::error::ErrorCode::Auth) && crate::core::feature::agent::rotate_key(app) {
                        app.notice("这个 key 不可用，已换下一个重试");
                        continue;
                    }
                    return Err(e);
                }
            }
        };

        let elapsed = started.elapsed().as_millis() as i64;
        let (prompt_t, cached_t, completion_t) = llm::parse_usage(&reply);
        out.prompt += prompt_t;
        out.cached += cached_t;
        out.completion += completion_t;

        let content = llm::text_of(&reply, "content");
        let reasoning = llm::text_of(&reply, "reasoning_content");
        let calls = parse_calls(&reply);

        // —— 4. 存助手消息 ——
        let meta = MsgMeta {
            at: crate::core::base::clock::now_ms(),
            ms: elapsed,
            first_ms: first_at.load(Ordering::Relaxed),
            prompt_tokens: prompt_t,
            completion_tokens: completion_t,
            cached_tokens: cached_t,
            provider: cfg.provider.id.clone(),
            model: model.clone(),
        };
        let assistant = ChatMsg {
            role: Role::Assistant,
            content: content.clone(),
            reasoning: reasoning.clone(),
            tool_calls: calls.clone(),
            meta: Some(meta),
            ..Default::default()
        };
        let saved = app.append_local(assistant)?;
        app.emit(AppEvent::Message { message: Box::new(saved.clone()) });
        out.text = content.clone();

        // 用量入库 + 事件
        {
            let t = crate::core::feature::usage::Turn {
                prompt: prompt_t,
                cached: cached_t,
                completion: completion_t,
                dur_ms: elapsed,
            };
            crate::core::feature::usage::record(
                &app.db,
                &cfg,
                &cfg.provider.id,
                &model,
                &app.session_id().unwrap_or_default(),
                &t,
            );
            app.emit(AppEvent::Usage { prompt: prompt_t, completion: completion_t });
        }

        // 标题：只在第一条用户消息之后生成一次。
        if cfg.agent.auto_title {
            title::maybe_generate(app, &cfg, &content).await;
        }

        // —— 5. 没有工具调用：收尾 ——
        if calls.is_empty() {
            if cfg.agent.mode == AgentMode::Plan && cfg.agent.plan_first {
                app.status("计划待确认");
            }
            break;
        }

        // —— 6. 执行工具 ——
        app.status(format!("执行 {} 个工具调用…", calls.len()));
        for call in &calls {
            if app.stopped() {
                out.stopped = true;
                break;
            }
            out.tools += 1;
            let res = exec::run_call(app, call).await;
            let tool_msg = ChatMsg {
                role: Role::Tool,
                content: res.model_text(),
                tool_call_id: Some(call.id.clone()),
                tool_name: Some(call.name.clone()),
                is_error: !res.ok,
                ..Default::default()
            };
            let saved_tool = app.append_local(tool_msg)?;
            app.emit(AppEvent::Message { message: Box::new(saved_tool) });
            // 计划模式下被拒绝的工具：直接结束本轮，别再撞第二次。
            if !res.ok && res.text.contains("计划模式") {
                out.stopped = true;
                break;
            }
        }
        if out.stopped {
            break;
        }
    }

    Ok(out)
}

/// 工具定义（内置 + MCP + 技能包）。
pub fn tool_definitions(app: &App, cfg: &crate::core::feature::config::schema::Config) -> Value {
    let mut arr: Vec<Value> = Vec::new();
    if cfg.model.tools {
        if let Some(builtin) = spec::definitions(app).as_array() {
            arr.extend(builtin.iter().cloned());
        }
        if let Ok(m) = app.mcp.read() {
            arr.extend(m.definitions());
        }
        if let Ok(p) = app.packs.read() {
            for f in p.funcs() {
                arr.push(f.definition());
            }
        }
    }
    Value::Array(arr)
}

fn estimate_json(v: &Value) -> usize {
    serde_json::to_string(v).map(|s| s.len()).unwrap_or(0)
}

/// 组请求消息：系统提示 + 会话历史（摘要条目不回传）。
pub fn build_messages(app: &App, user_text: &str, vision: bool) -> Value {
    let sys = prompt::build(app, user_text);
    let mut arr: Vec<Value> = Vec::with_capacity(16);
    arr.push(json!({ "role": "system", "content": sys }));
    for m in app.messages() {
        if !m.role.to_api() {
            continue;
        }
        arr.push(m.to_api_json(vision));
    }
    Value::Array(arr)
}

/// 从响应里取工具调用。
fn parse_calls(reply: &Value) -> Vec<ToolCall> {
    let Some(arr) = reply["tool_calls"].as_array() else { return Vec::new() };
    arr.iter()
        .filter_map(|c| {
            let f = &c["function"];
            let name = f["name"].as_str().unwrap_or("").trim().to_string();
            if name.is_empty() {
                return None;
            }
            let args = f["arguments"].as_str().unwrap_or("{}").to_string();
            let id = c["id"].as_str().unwrap_or("").to_string();
            Some(ToolCall {
                id: if id.is_empty() { crate::core::base::ids::IdGen::hex(10) } else { id },
                name,
                arguments: if args.trim().is_empty() { "{}".into() } else { args },
            })
        })
        .collect()
}

/// 立刻压缩一次。返回被摘要掉的条数。
pub async fn compact_now(app: &App, cfg: &crate::core::feature::config::schema::ContextCfg) -> BResult<usize> {
    let messages = app.messages();
    let Some(plan) = compact::plan(&messages, cfg, cfg.budget_chars.max(4000) as usize) else {
        return Ok(0);
    };
    if !plan.worth() {
        return Ok(0);
    }

    let req = compact::summary_request(&messages[..plan.cut], cfg, 24_000);
    let llm_client = client(app)?;
    let model = app.cfg().provider.model.clone();
    let opt = options(app, None);
    let reply = llm_client
        .chat(&model, &json!([{ "role": "user", "content": req }]), &opt)
        .await?;
    let summary = llm::text_of(&reply, "content");
    if summary.trim().is_empty() {
        return Err(BError::internal("摘要返回了空内容"));
    }

    // 摘要落库：把被摘要的那一段标成"已归档"，并插入一条 Summary。
    let sid = app.session_id().unwrap_or_default();
    if compact::summary_too_short(&summary, cfg) {
        app.notice("摘要偏短，可能会丢细节");
    }
    let mut msg = ChatMsg {
        role: Role::Summary,
        content: summary.clone(),
        ..Default::default()
    };
    // 摘要挂在最后一条被压缩的消息上，保持树的顺序。
    if let Some(last) = messages.get(plan.cut.saturating_sub(1)) {
        msg.parent_id = last.id;
    }
    let saved = app.db.append(&sid, &msg)?;
    {
        let mut st = app.state.lock().unwrap_or_else(|e| e.into_inner());
        // 界面只保留摘要 + 尾部，避免越滚越长；原文仍在库里可查。
        let mut next: Vec<ChatMsg> = vec![saved];
        next.extend(messages.iter().skip(plan.cut).cloned());
        st.messages = next;
    }
    Ok(plan.cut)
}
