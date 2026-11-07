//! 工具输出治理。
//!
//! 工具输出直接回灌给模型，所以它同时是"上下文预算"和"安全边界"：
//!
//! - 一个 `cat` 打了 50 万行，不截断会把整轮对话的预算吃光；
//! - 一个 `env` 打印了所有密钥，不回脱敏就会进模型厂商的服务器；
//! - 一行 200KB 的压缩 JS，不折断会让界面卡死。
//!
//! 这里做三件事：**截断**、**脱敏**、**折断超长行**。顺序不能反：
//! 先脱敏再截断可能把 `sk-` 截成半截导致脱敏规则失效；先截断再脱敏则可能丢掉后半段的正文。
//! 正确顺序是：脱敏 → 折断 → 截断。

use crate::core::feature::tool::ToolOutcome;

/// 治理参数。
#[derive(Debug, Clone, Copy)]
pub struct Budget {
    /// 最多多少字符。0 = 不限。
    pub max_chars: usize,
    /// 单行最多多少字符（防止 base64、压缩 JS 撑爆界面）。
    pub max_line: usize,
    /// 截断时头部占比（0.0~1.0），剩下的留给尾部。
    pub head_ratio: f32,
    /// 是否脱敏。
    pub redact: bool,
}

impl Default for Budget {
    fn default() -> Self {
        Self { max_chars: 32_000, max_line: 2_000, head_ratio: 0.7, redact: true }
    }
}

impl Budget {
    pub fn from_cfg(tool: &crate::core::feature::config::schema::ToolCfg) -> Self {
        Self {
            max_chars: tool.budget_chars.max(1000),
            max_line: 2_000,
            head_ratio: 0.7,
            redact: true,
        }
    }
}

/// 应用治理。
pub fn apply(mut out: ToolOutcome, b: Budget) -> ToolOutcome {
    let mut text = out.text;

    if b.redact {
        text = crate::core::security::redact::clean(&text);
    }

    text = fold_long_lines(&text, b.max_line);

    if b.max_chars > 0 && text.chars().count() > b.max_chars {
        text = truncate_keep_tail(&text, b.max_chars, b.head_ratio);
    }

    if text.trim().is_empty() {
        text = if out.ok { "（没有输出）".into() } else { "（失败，但没有错误信息）".into() };
    }

    out.text = text;
    out
}

/// 把超长行折断。保留原始内容，只插入 `⏎` 标记。
///
/// 为什么不是直接截掉后面：模型可能需要行尾的关键信息（比如一行 JSON 的最后一个字段）。
fn fold_long_lines(s: &str, max_line: usize) -> String {
    if max_line == 0 {
        return s.to_string();
    }
    let mut out = String::with_capacity(s.len());
    for line in s.split_inclusive('\n') {
        if line.chars().count() <= max_line {
            out.push_str(line);
            continue;
        }
        let mut rest = line;
        while rest.chars().count() > max_line {
            let cut: String = rest.chars().take(max_line).collect();
            out.push_str(&cut);
            out.push('\n');
            out.push_str("…（本行过长，已折断）\n");
            let consumed = cut.len();
            rest = &rest[consumed..];
        }
        out.push_str(rest);
    }
    out
}

/// 保头保尾截断。
///
/// 头部保留操作上下文（命令、文件路径），尾部保留结果（编译错误、测试失败的总结）。
/// 中间写明省略多少字符，让模型知道信息不完整——不写的话它会以为自己看到了全部。
fn truncate_keep_tail(s: &str, max: usize, head_ratio: f32) -> String {
    let total = s.chars().count();
    let head_n = ((max as f32) * head_ratio.clamp(0.3, 0.9)) as usize;
    let tail_n = max.saturating_sub(head_n).saturating_sub(40);
    let head: String = s.chars().take(head_n).collect();
    let tail: String = s.chars().skip(total.saturating_sub(tail_n)).collect();
    let skipped = total.saturating_sub(head_n + tail_n);
    format!("{head}\n\n…（中间省略 {skipped} 字符，共 {total} 字符；需要全部内容请用 read_file 分段读或加过滤）…\n\n{tail}")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::core::feature::model::session::Route;
    use crate::core::feature::tool::ToolOutcome;

    fn out(s: &str) -> ToolOutcome {
        ToolOutcome::ok(s, Route::Sandbox)
    }

    #[test]
    fn short_text_untouched() {
        let r = apply(out("hello"), Budget::default());
        assert_eq!(r.text, "hello");
    }

    #[test]
    fn secrets_are_redacted() {
        let r = apply(out("key sk-abcdefghijklmnopqrstuvwxyz"), Budget::default());
        assert!(!r.text.contains("abcdefghijklmnop"));
    }

    #[test]
    fn long_line_is_folded() {
        let s = "x".repeat(5000);
        let r = apply(out(&s), Budget { max_line: 100, ..Default::default() });
        assert!(r.text.contains("已折断"));
    }

    #[test]
    fn truncation_keeps_both_ends() {
        let s: String = (0..5000).map(|i| char::from(b'a' + (i % 26) as u8)).collect();
        let r = apply(out(&s), Budget { max_chars: 500, ..Default::default() });
        assert!(r.text.starts_with("abc"));
        assert!(r.text.contains("中间省略"));
        assert!(r.text.len() < 1000);
    }

    #[test]
    fn empty_gets_placeholder() {
        let r = apply(out("   "), Budget::default());
        assert_eq!(r.text, "（没有输出）");
        let e = apply(ToolOutcome::err("", Route::Sandbox), Budget::default());
        assert!(e.text.contains("没有错误信息"));
    }
}
