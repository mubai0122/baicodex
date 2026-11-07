//! 上下文预算。
//!
//! 目标只有一个：**永远不把请求撑爆**。撑爆的后果不是报错，而是模型开始丢开头的
//! 系统提示，或者网关直接 400，用户看到的是"聊到一半突然不听话了"。
//!
//! ## 三层水位
//!
//! ```text
//! 0 ────────── 60% ──────── 85% ──────── 100%
//!      正常      提醒模型      准备压缩      必须压缩
//! ```
//!
//! - 60%：提示词里加一句"上下文有点满了，说重点"；
//! - 85%：允许后台预压缩（不阻塞当前回合）；
//! - 100%：阻塞式压缩，压完再发请求。

use crate::core::feature::config::schema::ContextCfg;
use crate::core::feature::model::message::ChatMsg;

/// 水位。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Level {
    /// 宽裕。
    Ok,
    /// 过 60%，提示模型精简。
    Tight,
    /// 过 85%，该压了。
    High,
    /// 超了，必须压。
    Full,
}

impl Level {
    pub fn as_str(self) -> &'static str {
        match self {
            Level::Ok => "ok",
            Level::Tight => "tight",
            Level::High => "high",
            Level::Full => "full",
        }
    }
}

/// 一次预算计算的结果。
#[derive(Debug, Clone)]
pub struct Budget {
    /// 总预算（字符）。
    pub limit: usize,
    /// 已用（字符，含每条消息的固定开销）。
    pub used: usize,
    /// 给本轮回复留的余量。
    pub reserve: usize,
    pub level: Level,
}

impl Budget {
    /// 还能用多少。
    pub fn headroom(&self) -> usize {
        self.limit.saturating_sub(self.used + self.reserve)
    }

    pub fn ratio(&self) -> f64 {
        if self.limit == 0 {
            return 0.0;
        }
        (self.used as f64 / self.limit as f64).clamp(0.0, 1.5)
    }

    /// 要不要压缩。
    pub fn need_compact(&self) -> bool {
        matches!(self.level, Level::High | Level::Full)
    }

    /// 必须现在就压（不能等下一轮）。
    pub fn must_compact(&self) -> bool {
        self.level == Level::Full
    }

    /// 一句话说明，给状态栏。
    pub fn text(&self) -> String {
        format!(
            "{}%（{}/{} 字符）",
            (self.ratio() * 100.0) as i64,
            self.used,
            self.limit
        )
    }
}

/// 单条消息的字符数（正文 + 工具调用开销）。
pub fn msg_chars(m: &ChatMsg) -> usize {
    let body = m.content.chars().count()
        + m.reasoning.chars().count()
        + m.tool_calls.iter().map(|c| c.name.chars().count() + c.arguments.chars().count() + 40).sum::<usize>();
    body + 8
}

/// 算预算。
///
/// `extra` 是"这轮还要加进去的东西"（系统提示、工具定义、用户新消息）的字符数。
pub fn compute(messages: &[ChatMsg], cfg: &ContextCfg, extra: usize) -> Budget {
    let limit = if cfg.budget_chars > 0 { cfg.budget_chars as usize } else { 240_000 };
    let used: usize = messages.iter().map(msg_chars).sum::<usize>() + extra;
    let reserve = super::TURN_RESERVE.min(limit / 4);
    let ratio = (used as f64 + reserve as f64) / limit as f64;
    let level = if ratio >= 1.0 {
        Level::Full
    } else if ratio >= 0.85 {
        Level::High
    } else if ratio >= 0.60 {
        Level::Tight
    } else {
        Level::Ok
    };
    Budget { limit, used, reserve, level }
}

/// 该保留最近多少条。
///
/// 配置给的是"最少保留条数"，但真正决定的是**字符预算**：保留 12 条如果每条都是
/// 一万字的工具输出，压完还是满的。所以这里同时看条数与字符。
pub fn keep_count(messages: &[ChatMsg], cfg: &ContextCfg, limit: usize) -> usize {
    /// 无论预算多紧，最后几条都要留：它们就是"当前这一问一答"。
    const MIN_KEEP: usize = 2;

    if messages.is_empty() {
        return 0;
    }
    let want = cfg.keep_msgs.max(MIN_KEEP);
    // 留一半给摘要本身，另一半给"压缩后的历史"——两边都塞满等于没压。
    let budget = limit.saturating_sub(super::TURN_RESERVE) / 2;
    let mut chars = 0usize;
    let mut n = 0usize;
    for m in messages.iter().rev() {
        let c = msg_chars(m);
        // 条数到顶：配置说留 12 条，就不再多捞。
        if n >= want {
            break;
        }
        // 预算到顶：但至少留 MIN_KEEP 条，否则压缩后模型接不上话。
        if chars + c > budget && n >= MIN_KEEP {
            break;
        }
        chars += c;
        n += 1;
    }
    n.max(MIN_KEEP.min(messages.len()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::feature::model::message::{ChatMsg, Role};

    fn msg(n: usize) -> ChatMsg {
        ChatMsg { role: Role::User, content: "x".repeat(n), ..Default::default() }
    }

    #[test]
    fn levels() {
        let cfg = ContextCfg { budget_chars: 1000, keep_msgs: 4, ..Default::default() };
        let b = compute(&[msg(100)], &cfg, 0);
        assert_eq!(b.level, Level::Ok);
        let b = compute(&[msg(900)], &cfg, 0);
        assert!(matches!(b.level, Level::High | Level::Full));
        let b = compute(&[msg(5000)], &cfg, 0);
        assert!(b.must_compact());
    }

    #[test]
    fn keep_respects_budget() {
        let cfg = ContextCfg { budget_chars: 4000, keep_msgs: 50, ..Default::default() };
        let v: Vec<ChatMsg> = (0..20).map(|_| msg(300)).collect();
        let k = keep_count(&v, &cfg, 4000);
        assert!(k < 20);
        assert!(k >= 2);
    }
}
