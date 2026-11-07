//! 自动标题。
//!
//! 会话列表里一排"新会话"没法用，所以第一条回答之后生成一次标题。
//!
//! ## 为什么用另一个模型调用
//!
//! 让主对话"顺便起个标题"会污染回答（模型会在正文里写"标题：…"）。单独发一次
//! 极短的请求，成本几乎为零，格式也可控。
//!
//! 只在**标题还是默认值**且消息数很少时生成：用户手动改过名字就不该被覆盖。

use serde_json::json;

use crate::core::feature::agent::{client, current_key, options};
use crate::core::feature::app::App;
use crate::core::feature::config::schema::Config;
use crate::net::llm;

/// 默认标题（新建会话时的占位）。
pub const DEFAULT_TITLE: &str = "新会话";

/// 最多生成几次：失败一次就够，别每轮都发请求。
const MAX_ATTEMPTS_PER_SESSION: usize = 1;

/// 是否该生成。
pub fn should(app: &App, cfg: &Config) -> bool {
    if !cfg.agent.auto_title {
        return false;
    }
    let st = app.state.lock().unwrap_or_else(|e| e.into_inner());
    let title = st.session_title.trim();
    if !title.is_empty() && title != DEFAULT_TITLE && title != "新对话" {
        return false;
    }
    // 只在"用户说了 1-2 句、助手答了 1 句"这种刚开头的时候生成。
    let users = st.messages.iter().filter(|m| m.role == crate::core::feature::model::message::Role::User).count();
    users <= 2
}

/// 生成并写入。失败静默——标题不重要，不该弹错误。
pub async fn maybe_generate(app: &App, cfg: &Config, last_reply: &str) {
    if !should(app, cfg) {
        return;
    }
    let Some(sid) = app.session_id() else { return };
    let first_user = {
        let st = app.state.lock().unwrap_or_else(|e| e.into_inner());
        st.messages
            .iter()
            .find(|m| m.role == crate::core::feature::model::message::Role::User)
            .map(|m| m.content.clone())
            .unwrap_or_default()
    };
    if first_user.trim().is_empty() {
        return;
    }

    let title = match make_title(app, cfg, &first_user, last_reply).await {
        Some(t) => t,
        None => fallback_title(&first_user),
    };
    if title.is_empty() {
        return;
    }
    if app.db.rename_session(&sid, &title).is_ok() {
        {
            let mut st = app.state.lock().unwrap_or_else(|e| e.into_inner());
            st.session_title = title.clone();
        }
        app.refresh_sessions();
    }
    let _ = MAX_ATTEMPTS_PER_SESSION;
}

/// 调模型起标题。
async fn make_title(app: &App, cfg: &Config, user: &str, reply: &str) -> Option<String> {
    if current_key(app).trim().is_empty() {
        return None;
    }
    let client = client(app).ok()?;
    let model = cfg.provider.model.clone();
    let mut opt = options(app, None);
    opt.tools = None;
    opt.temperature = Some(0.2);
    opt.max_tokens = 32;
    opt.max_mode = false;
    opt.no_stream = true;

    let prompt = format!(
        "给下面这轮对话起一个标题。要求：8 个汉字以内，不要引号、不要标点、不要「关于」开头，直接给标题本身。\n\
         用户：{}\n助手：{}",
        crate::core::base::text::truncate(user.trim(), 300),
        crate::core::base::text::truncate(reply.trim(), 300)
    );
    let body = json!([{ "role": "user", "content": prompt }]);
    let r = client.chat(&model, &body, &opt).await.ok()?;
    let t = llm::text_of(&r, "content");
    Some(clean(&t)).filter(|x| !x.is_empty())
}

/// 清洗：去引号、去换行、截断。
pub fn clean(raw: &str) -> String {
    let mut s = crate::core::base::text::one_line(raw);
    for p in ["标题：", "标题:", "「", "」", "\"", "'", "《", "》"] {
        s = s.replace(p, "");
    }
    let s = s.trim().trim_matches('。').trim();
    crate::core::base::text::truncate(s, 16)
}

/// 模型不可用时的兜底：取用户第一句的前几个字。
pub fn fallback_title(user: &str) -> String {
    let t = crate::core::base::text::one_line(user);
    let t = t.trim();
    if t.is_empty() {
        return DEFAULT_TITLE.into();
    }
    // 太短的一律补成"…"，否则列表里全是"帮"。
    let cut = crate::core::base::text::truncate(t, 12);
    if cut.chars().count() < 2 {
        DEFAULT_TITLE.into()
    } else {
        cut
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clean_strips_quotes() {
        assert_eq!(clean("「修复登录」"), "修复登录");
        assert_eq!(clean("标题：重构工具层\n"), "重构工具层");
    }

    #[test]
    fn fallback_never_empty() {
        assert_eq!(fallback_title(""), DEFAULT_TITLE);
        assert_eq!(fallback_title("帮"), DEFAULT_TITLE);
        assert!(!fallback_title("帮我看看这个报错").is_empty());
    }
}
