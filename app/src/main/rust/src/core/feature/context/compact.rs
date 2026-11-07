//! 上下文压缩。
//!
//! 把"较早的一段对话"换成一条摘要，保留最近的原文。这里有三个容易做错的地方：
//!
//! 1. **切点落在工具调用中间**。`tool` 消息必须紧跟它对应的 `assistant(tool_calls)`。
//!    切在中间会导致请求体不合法（OpenAI 会 400），所以切点要往前退到"安全边界"。
//! 2. **摘要没保留"做了什么、改了什么"**。用户最常问的是"刚才那个改动是什么"，
//!    摘要里必须有结论与文件路径，而不只是"用户讨论了代码"。
//! 3. **压完立刻又满**。保留段太长的话压缩等于没做，所以保留段也要按预算裁。
//!
//! 这个模块只做"算"和"拼文本"，发请求与落库在 `agent` 里——那样才好测。

use crate::core::feature::config::schema::ContextCfg;
use crate::core::feature::model::message::{ChatMsg, Role};

use super::budget;

/// 压缩计划。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Plan {
    /// 前 `cut` 条要被摘要掉。
    pub cut: usize,
    /// 后面保留的条数。
    pub keep: usize,
    /// 压缩前字符数。
    pub before: usize,
    /// 预估压缩后字符数。
    pub after: usize,
}

impl Plan {
    pub fn saved(&self) -> usize {
        self.before.saturating_sub(self.after)
    }

    /// 值不值得压：省不到 15% 就别压，摘要本身也要花 token。
    pub fn worth(&self) -> bool {
        self.cut >= 2 && self.saved() * 100 / self.before.max(1) >= 15
    }

    pub fn text(&self) -> String {
        format!(
            "压缩 {} 条 → 保留 {} 条（{} → {} 字符）",
            self.cut, self.keep, self.before, self.after
        )
    }
}

/// 算一个压缩计划。不需要压缩时返回 `None`。
pub fn plan(messages: &[ChatMsg], cfg: &ContextCfg, limit: usize) -> Option<Plan> {
    if messages.len() < 6 {
        return None;
    }
    let keep = budget::keep_count(messages, cfg, limit);
    if keep >= messages.len() {
        return None;
    }
    let mut cut = messages.len() - keep;
    cut = safe_cut(messages, cut);
    if cut < 2 {
        return None;
    }
    let before: usize = messages.iter().map(budget::msg_chars).sum();
    // 摘要目标：配置的字数上限，再留 200 字符给"摘要"这条消息本身。
    let summary_chars = (cfg.summary_words.max(100) as usize * 2).clamp(400, 8000) + 200;
    let after: usize = messages[cut..].iter().map(budget::msg_chars).sum::<usize>() + summary_chars;
    Some(Plan { cut, keep: messages.len() - cut, before, after })
}

/// 把切点校准到安全边界：**不能切开 assistant(tool_calls) 与它后面的 tool 消息**。
///
/// 判断方式：如果 `messages[cut]` 是 `tool`，说明它的 assistant 在前面，往前退。
fn safe_cut(messages: &[ChatMsg], cut: usize) -> usize {
    let mut c = cut.min(messages.len());
    while c > 0 && c < messages.len() && messages[c].role == Role::Tool {
        c -= 1;
    }
    // 退到 assistant 上时，再往前退一位，保证那条 assistant 也被摘要掉。
    if c < messages.len() && messages[c].role == Role::Assistant && !messages[c].tool_calls.is_empty() {
        // 本身是安全起点（它带着 tool_calls，后面的 tool 也在这个 assistant 段里）。
    }
    c
}

/// 把一段消息拼成给模型看的转录文本。
///
/// 工具输出截断到 600 字符：摘要不需要原始日志，只需要"跑了什么、结果如何"。
pub fn transcript(messages: &[ChatMsg], max_chars: usize) -> String {
    let mut s = String::new();
    for m in messages {
        if m.role == Role::Summary {
            // 已经有摘要的，把摘要并进来，不要丢历史结论。
            s.push_str(&format!("[此前摘要] {}\n", crate::core::base::text::truncate(&m.content, 1500)));
            continue;
        }
        if !m.content.trim().is_empty() {
            let body = crate::core::base::text::truncate(m.content.trim(), 1200);
            s.push_str(&format!("{}: {}\n", m.role.label(), body));
        }
        for c in &m.tool_calls {
            s.push_str(&format!(
                "  → 调用 {}({})\n",
                c.name,
                crate::core::base::text::truncate(&c.arguments, 240)
            ));
        }
        if m.role == Role::Tool {
            let body = crate::core::base::text::truncate(m.content.trim(), 600);
            if !body.trim().is_empty() {
                s.push_str(&format!("  工具结果{}: {}\n", if m.is_error { "（失败）" } else { "" }, body));
            }
        }
        if s.chars().count() > max_chars {
            break;
        }
    }
    crate::core::base::text::truncate(&s, max_chars)
}

/// 内置摘要提示词。用户可以在配置里覆盖。
pub const DEFAULT_PROMPT: &str = "\
把下面这段对话压缩成摘要，供后续继续对话使用。要求：
1. 先写「已完成」，逐条列出实际做了的事（改了哪个文件、跑了什么命令、结果如何）；
2. 再写「当前状态」，说明还在进行中的事、已知的报错与结论；
3. 最后写「关键信息」，保留用户明确说过的偏好、约束、路径、数字；
4. 不要写寒暄、不要写「用户询问了…」这种元叙述，直接写事实；
5. 用简体中文，条目化，总长不超过 {words} 字。";

/// 拼摘要请求的正文。
pub fn summary_request(messages: &[ChatMsg], cfg: &ContextCfg, max_chars: usize) -> String {
    let tpl = if cfg.summary_prompt.trim().is_empty() { DEFAULT_PROMPT } else { cfg.summary_prompt.trim() };
    let head = tpl.replace("{words}", &cfg.summary_words.max(100).to_string());
    format!("{head}\n\n---\n\n{}", transcript(messages, max_chars))
}

/// 把摘要应用到消息列表：返回 `[摘要] + 最近 keep 条`。
///
/// 摘要消息带 `Role::Summary`——它在界面里可见，但**不进请求体**（由
/// `Role::to_api` 决定）。这样用户能看出"这里压缩过"，模型也不会把摘要当成自己说过的话。
pub fn apply(summary: &str, messages: &[ChatMsg], plan: &Plan) -> Vec<ChatMsg> {
    let mut out: Vec<ChatMsg> = Vec::with_capacity(plan.keep + 1);
    out.push(ChatMsg {
        role: Role::Summary,
        content: summary.trim().to_string(),
        ..Default::default()
    });
    out.extend(messages.iter().skip(plan.cut).cloned());
    out
}

/// 摘要文本的质量检查：太短说明模型敷衍了，要补一句让它重来。
pub fn summary_too_short(summary: &str, cfg: &ContextCfg) -> bool {
    let n = summary.trim().chars().count();
    let want = (cfg.summary_words.max(100) as usize) / 3;
    n < want.max(80)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn m(role: Role, body: &str) -> ChatMsg {
        ChatMsg { role, content: body.to_string(), ..Default::default() }
    }

    #[test]
    fn cut_never_splits_tool_pair() {
        let msgs = vec![
            m(Role::User, "a"),
            m(Role::Assistant, "b"),
            m(Role::Tool, "c"),
            m(Role::Assistant, "d"),
            m(Role::User, "e"),
            m(Role::Assistant, "f"),
        ];
        let c = safe_cut(&msgs, 3);
        assert!(c <= 3);
        assert_ne!(msgs[c].role, Role::Tool);
    }

    #[test]
    fn summary_message_excluded_from_api() {
        assert!(!Role::Summary.to_api());
    }

    #[test]
    fn apply_keeps_tail() {
        let msgs: Vec<ChatMsg> = (0..10).map(|i| m(Role::User, &format!("m{i}"))).collect();
        let p = Plan { cut: 7, keep: 3, before: 1000, after: 300 };
        let out = apply("摘要", &msgs, &p);
        assert_eq!(out.len(), 4);
        assert_eq!(out[0].role, Role::Summary);
        assert_eq!(out[1].content, "m7");
    }
}
