//! 前端门面。
//!
//! `core::feature::app::App` 是内核本体，方法很多但都是"内核视角"的。这一层给
//! 前端（TUI / Kotlin / C ABI）一组**面向界面**的入口：发消息、停止、切会话、
//! 读状态，都只需要字符串与 JSON。
//!
//! 为什么要多这一层：前端不该知道 `ConfigStore` / `Db` / `Vault` 的存在。它只该知道
//! "有一个会话、能发消息、能收事件"。这层薄壳把依赖收在一边，将来换存储、换加密方式
//! 都不会波及前端。

use std::sync::Arc;

use serde_json::{json, Value};

use crate::core::base::error::BResult;
use crate::core::feature::agent;
use crate::core::feature::app::App;
use crate::core::feature::model::session::SessionRow;
use crate::core::feature::tool::registry as R;

/// 一个已经启动的内核。
#[derive(Clone)]
pub struct Facade {
    pub app: Arc<App>,
}

impl std::fmt::Debug for Facade {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Facade").field("session", &self.app.session_id()).finish()
    }
}

impl Facade {
    /// 启动。`base` 为空时用默认数据根。
    pub fn boot(base: &str) -> BResult<Self> {
        let p = if base.trim().is_empty() {
            crate::core::feature::app::default_base()
        } else {
            std::path::PathBuf::from(base.trim())
        };
        Ok(Self { app: App::boot(p)? })
    }

    /// 发一条消息。
    pub async fn send(&self, text: &str, images: Vec<String>, files: Vec<String>) -> BResult<Value> {
        let o = agent::send(&self.app, text, images, files).await?;
        Ok(json!({
            "text": o.text,
            "rounds": o.rounds,
            "tools": o.tools,
            "stopped": o.stopped,
            "prompt_tokens": o.prompt,
            "completion_tokens": o.completion,
        }))
    }

    /// 重新生成。
    pub async fn regenerate(&self) -> BResult<Value> {
        let o = agent::regenerate(&self.app).await?;
        Ok(json!({ "text": o.text, "rounds": o.rounds, "stopped": o.stopped }))
    }

    /// 停止。
    pub fn stop(&self) {
        agent::stop(&self.app);
    }

    /// 当前状态快照（界面刷新用）。
    pub fn snapshot(&self) -> Value {
        let st = self.app.state.lock().unwrap_or_else(|e| e.into_inner());
        let cfg = self.app.cfg();
        json!({
            "session": st.session_id,
            "title": st.session_title,
            "busy": st.busy,
            "status": st.status,
            "waiting": st.waiting_kind(),
            "stream": st.stream_text,
            "reason": st.stream_reason,
            "running": st.running,
            "error": st.last_error,
            "mode": cfg.agent.mode.as_str(),
            "work_mode": cfg.agent.work_mode.as_str(),
            "model": cfg.provider.model,
            "tokens": crate::core::feature::usage::report(&self.app.db, &cfg).total_tokens(),
            "cost": crate::core::feature::usage::report(&self.app.db, &cfg).money,
        })
    }

    /// 会话列表。
    pub fn sessions(&self) -> Vec<SessionRow> {
        self.app.state.lock().unwrap_or_else(|e| e.into_inner()).sessions.clone()
    }

    /// 当前会话的消息。
    pub fn messages(&self) -> Value {
        json!(self.app.messages())
    }

    /// 工具清单（名称 + 标签 + 风险），设置页展示用。
    pub fn tools(&self) -> Value {
        let cfg = self.app.cfg();
        let list: Vec<Value> = R::builtin_names()
            .into_iter()
            .map(|n| {
                json!({
                    "name": n,
                    "label": R::label(n),
                    "risk": R::risk_of(n).as_str(),
                    "allowed": R::allowed(n, cfg.agent.mode),
                    "high_risk": R::is_high_risk(n),
                })
            })
            .collect();
        Value::Array(list)
    }

    /// 新会话。
    pub fn new_session(&self) -> BResult<()> {
        self.app.new_session()
    }

    /// 切会话。
    pub fn open_session(&self, id: &str) -> BResult<()> {
        self.app.open_session(id)
    }

    /// 删除会话。返回"删之前它是否存在"，界面据此决定要不要提示。
    pub fn delete_session(&self, id: &str) -> BResult<bool> {
        if !crate::core::feature::storage::session_repo::exists(&self.app.db, id) {
            return Ok(false);
        }
        self.app.db.delete_session(id)?;
        // 删的正好是当前会话：清空状态，免得界面还停在一条已经不存在的会话上。
        {
            let mut st = self.app.state.lock().unwrap_or_else(|e| e.into_inner());
            if st.session_id.as_deref() == Some(id) {
                st.session_id = None;
                st.session_title.clear();
                st.messages.clear();
                st.stream_text.clear();
                st.stream_reason.clear();
            }
        }
        self.app.refresh_sessions();
        Ok(true)
    }

    /// 改自定义系统提示词并落盘。
    pub fn set_system_prompt(&self, text: &str) -> BResult<()> {
        let t = text.trim().to_string();
        self.app.save_cfg(|c| c.agent.system_prompt = t)?;
        Ok(())
    }

    /// 重命名。
    pub fn rename_session(&self, id: &str, title: &str) -> BResult<()> {
        self.app.db.rename_session(id, title)?;
        self.app.refresh_sessions();
        Ok(())
    }

    /// 订阅事件。
    pub fn subscribe(&self) -> tokio::sync::broadcast::Receiver<crate::core::feature::app::AppEvent> {
        self.app.subscribe()
    }

    /// 退出。
    pub fn shutdown(&self) {
        self.app.shutdown();
    }
}
