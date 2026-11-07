//! 上下文：提示词、预算、压缩。
//!
//! 三件事必须分开，因为它们失败的后果不一样：
//!
//! - **提示词**（[prompt]）写错了，模型行为跑偏，但不会崩；
//! - **预算**（[budget]）算错了，要么爆上下文、要么压得太狠丢信息；
//! - **压缩**（[compact]）做错了，历史的因果关系被抹掉，模型开始自相矛盾。
//!
//! 所以这里不出现任何网络调用与数据库写入——纯函数，可单测。

pub mod budget;
pub mod compact;
pub mod prompt;

pub use budget::Budget;
pub use compact::Plan;

/// 上下文里给"当前这轮"留的余量：模型回一句、调一次工具，大概要这么多字符。
pub const TURN_RESERVE: usize = 8_000;

/// 一条消息在计费上的估算：正文字符 + 每个工具调用 40 字符 + 每条消息 8 字符。
///
/// 比 `chars × 1.8` 准一点：工具调用的 JSON 开销固定，不按正文长度线性增长。
pub fn weight(text_chars: usize, tool_calls: usize) -> usize {
    text_chars + tool_calls * 40 + 8
}

/// 字符 → token 的粗估。中文 1 字≈1 token，英文 1 词≈0.75 token，
/// 混合内容取 1.6 折中（偏保守，宁可多留余量）。
pub fn chars_to_tokens(chars: usize) -> usize {
    (chars as f64 / 1.6).ceil() as usize
}

/// token → 字符。
pub fn tokens_to_chars(tokens: usize) -> usize {
    (tokens as f64 * 1.6) as usize
}
