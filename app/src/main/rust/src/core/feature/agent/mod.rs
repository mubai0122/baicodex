//! Agent 主循环。
//!
//! 一次"用户发一句话"到"看到回答"，经过：
//!
//! ```text
//! 存用户消息 → 组系统提示 → 算预算（必要时压缩）→ 发请求（流式）
//!   → 有工具调用？执行（过策略与确认）→ 把结果回灌 → 再发请求
//!   → 没有工具调用 → 收尾（存助手消息、记用量、发事件、按需生成标题）
//! ```
//!
//! ## 为什么主循环只写一份
//!
//! 四个前端（TUI / Android / JNI / 子智能体）都要这套流程。每个前端抄一份的后果是
//! 行为不一致：手机上会压缩、终端上不压缩；手机会确认、JNI 不确认。所以循环在这里，
//! 前端只负责"发消息"和"收事件"。
//!
//! ## 停止
//!
//! `App::stop_flag` 每轮检查一次，流式回调里也检查（在 `net::llm` 里）。
//! 停止后**保留已经生成的部分**——用户点停止是想"够了"，不是想"白等一场"。

pub mod exec;
pub mod title;
pub mod turn;

use std::sync::atomic::Ordering;

use crate::core::base::error::{BError, BResult, ErrorCode};
use crate::core::feature::app::event::AppEvent;
use crate::core::feature::app::App;
use crate::core::feature::model::message::{ChatMsg, Role};
use crate::core::feature::model::provider::AuthStyle;
use crate::core::security::vault::BUCKET_API;
use crate::net::llm::{ChatOptions, Llm};

pub use turn::TurnOutcome;

/// 从凭据库取当前 key（多 key 轮换时按 `provider.key_index` 取）。
pub fn current_key(app: &App) -> String {
    let cfg = app.cfg();
    let slots = cfg.provider.key_slots.max(0);
    if slots > 0 {
        let idx = cfg.provider.key_index % slots.max(1);
        let k = app.vault.get(BUCKET_API, &format!("provider:key:{idx}"));
        if !k.trim().is_empty() {
            return k;
        }
    }
    let k = app.vault.get(BUCKET_API, "provider:default");
    if !k.trim().is_empty() {
        return k;
    }
    // 兼容旧配置里残留的明文 key（迁移失败时的兜底，不鼓励）。
    cfg.profile().map(|p| p.api_key.clone()).unwrap_or_default()
}

/// 换到下一个 key（鉴权失败时调用）。返回是否真的换了。
pub fn rotate_key(app: &App) -> bool {
    let mut cfg = app.cfg();
    let slots = cfg.provider.key_slots.max(0);
    if slots <= 1 {
        return false;
    }
    let next = (cfg.provider.key_index + 1) % slots;
    cfg.provider.key_index = next;
    app.update_cfg(|c| c.provider.key_index = next);
    true
}

/// 构造客户端。
pub fn client(app: &App) -> BResult<Llm> {
    let cfg = app.cfg();
    let base = cfg.provider.base_url.trim();
    if base.is_empty() {
        return Err(BError::new(ErrorCode::Invalid, "还没有配置接口地址（设置 → 模型）"));
    }
    let key = current_key(app);
    let auth = if cfg.current_provider().map(|p| p.auth).unwrap_or(AuthStyle::Bearer) == AuthStyle::None {
        AuthStyle::None
    } else if key.trim().is_empty() {
        return Err(BError::new(ErrorCode::Auth, "还没有填 API Key（设置 → 模型）"));
    } else {
        cfg.current_provider().map(|p| p.auth).unwrap_or(AuthStyle::Bearer)
    };
    Ok(Llm::new(base, &key, auth).with_headers(cfg.provider.headers.clone()))
}

/// 请求选项。
pub fn options(app: &App, tools: Option<serde_json::Value>) -> ChatOptions {
    let cfg = app.cfg();
    ChatOptions {
        temperature: if cfg.model.temperature >= 0.0 { Some(cfg.model.temperature as f64) } else { None },
        top_p: if cfg.model.top_p >= 0.0 { Some(cfg.model.top_p as f64) } else { None },
        think: cfg.model.think,
        effort: cfg.model.effort.clone(),
        extra: cfg.model.extra_params.clone(),
        max_tokens: cfg.model.max_tokens,
        max_mode: cfg.model.max_mode,
        model_tools: cfg.model.tools,
        model_reasoning: cfg.model.reasoning,
        no_stream: cfg.provider.no_stream || !cfg.agent.stream,
        provider_id: cfg.provider.id.clone(),
        tools,
    }
}

/// 发一句话（新回合）。
///
/// 返回时消息已经落库，事件已经发出。前端不需要再做任何收尾。
pub async fn send(app: &App, text: &str, images: Vec<String>, files: Vec<String>) -> BResult<TurnOutcome> {
    let text = text.trim();
    if text.is_empty() && images.is_empty() && files.is_empty() {
        return Err(BError::invalid("消息是空的"));
    }
    if app.is_busy() {
        return Err(BError::conflict("正在生成中，等这一轮结束或先点停止"));
    }
    app.clear_stop();

    // 1. 存用户消息
    let user = ChatMsg {
        role: Role::User,
        content: text.to_string(),
        images,
        files,
        ..Default::default()
    };
    let saved = app.append_local(user)?;
    app.emit(AppEvent::UserMessage {
        id: saved.id,
        text: text.to_string(),
        images: saved.images.clone(),
    });

    // 2. 跑循环
    let outcome = turn::run(app, text).await;

    // 3. 收尾：标题 / 用量已在 turn 里处理
    if let Err(e) = &outcome {
        app.emit(AppEvent::Error { code: e.code.as_str().to_string(), message: e.user_text() });
    }
    outcome
}

/// 重试上一轮：以最后一条用户消息为起点再跑一次（生成兄弟消息）。
pub async fn regenerate(app: &App) -> BResult<TurnOutcome> {
    if app.is_busy() {
        return Err(BError::conflict("正在生成中"));
    }
    let msgs = app.messages();
    let last_user = msgs
        .iter()
        .rev()
        .find(|m| m.role == Role::User)
        .cloned()
        .ok_or_else(|| BError::not_found("没有可重试的用户消息"))?;
    app.clear_stop();
    turn::run(app, &last_user.content).await
}

/// 停止当前生成。
pub fn stop(app: &App) {
    app.stop();
    app.emit(AppEvent::Status { text: "正在停止…".into() });
    app.state.lock().unwrap_or_else(|e| e.into_inner()).finish();
}

/// 是否已停止。
pub fn stopped(app: &App) -> bool {
    app.stop_flag.load(Ordering::Relaxed)
}

/// 导出当前会话为 Markdown。
///
/// 只导出**当前分支**：导出全部树会把"重新生成"的废弃回答也带上，用户复制出去
/// 会以为自己说过那些话。思考过程与工具结果各占一个引用块/折叠区，方便回看。
pub fn export_markdown(app: &App) -> String {
    let raw_title = app.lock_state().session_title.clone();
    let title = if raw_title.trim().is_empty() { "会话".to_string() } else { raw_title };
    let mut out = format!("# {title}\n\n");
    let msgs = app.messages();
    for m in msgs {
        match m.role {
            Role::User => {
                out.push_str("## 我\n\n");
                out.push_str(m.content.trim());
                out.push_str("\n\n");
                if !m.images.is_empty() {
                    out.push_str(&format!("（附 {} 张图片）\n\n", m.images.len()));
                }
                if !m.files.is_empty() {
                    out.push_str(&format!("（附 {} 个文件）\n\n", m.files.len()));
                }
            }
            Role::Assistant => {
                out.push_str("## 助手\n\n");
                if !m.reasoning.trim().is_empty() {
                    // 折叠成引用块：正文优先，思考是补充。
                    for line in m.reasoning.trim().lines() {
                        out.push_str("> ");
                        out.push_str(line);
                        out.push('\n');
                    }
                    out.push('\n');
                }
                out.push_str(m.content.trim());
                out.push_str("\n\n");
            }
            Role::Tool => {
                let name = m.tool_name.clone().unwrap_or_else(|| "工具".into());
                out.push_str(&format!("<details><summary>工具 {name}</summary>\n\n```\n"));
                out.push_str(m.content.trim());
                out.push_str("\n```\n\n</details>\n\n");
            }
            // 摘要与 system 不进导出：它们不是对话内容。
            Role::Summary | Role::System => {}
        }
    }
    out
}
