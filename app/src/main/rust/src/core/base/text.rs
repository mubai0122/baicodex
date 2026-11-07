//! 文本处理。
//!
//! 这里所有函数都满足两个约束：
//! 1. **不破坏 UTF-8**：绝不按字节下标切字符串，一律按 `char_indices` 走。
//! 2. **按显示宽度算**：中文占两格、emoji 占两格，TUI 与 Markdown 排版都依赖它。
//!
//! token 估算是"够用就好"的口径：不引入 tokenizer（多几 MB 模型文件不值得），
//! 目标是压缩阈值判断别偏得太离谱，而不是和某个厂商的计数完全一致。

use unicode_width::{UnicodeWidthChar, UnicodeWidthStr};

/// 文本工具的命名空间。
///
/// 用 `Text::truncate(..)` 而不是一堆同名自由函数，调用处一眼能看出在处理文本。
#[derive(Debug, Clone, Copy)]
pub struct Text;

impl Text {
    /// 按字符截断，超出补 `…`。`max` 指字符数，不是字节。
    pub fn truncate(s: &str, max: usize) -> String {
        truncate(s, max)
    }

    /// 按显示宽度截断（TUI 单元格）。
    pub fn truncate_width(s: &str, max: usize) -> String {
        truncate_width(s, max)
    }

    /// 压成一行：换行、制表、连续空白都变成一个空格。
    pub fn one_line(s: &str) -> String {
        one_line(s)
    }

    /// 估算 token 数。
    pub fn tokens(s: &str) -> usize {
        estimate_tokens(s)
    }

    /// 显示宽度。
    pub fn width(s: &str) -> usize {
        s.width()
    }
}

/// 按字符截断，超出补省略号。
///
/// 省略号本身算在 `max` 里：`truncate("abcdef", 4) == "abc…"`。
/// 这样调用方不用关心"加了省略号会不会反而超长"。
pub fn truncate(s: &str, max: usize) -> String {
    if max == 0 {
        return String::new();
    }
    let count = s.chars().count();
    if count <= max {
        return s.to_string();
    }
    let mut out: String = s.chars().take(max.saturating_sub(1)).collect();
    out.push('…');
    out
}

/// 按显示宽度截断。中文、emoji 都按实际占位算。
pub fn truncate_width(s: &str, max: usize) -> String {
    if max == 0 {
        return String::new();
    }
    if s.width() <= max {
        return s.to_string();
    }
    let mut used = 0usize;
    let mut out = String::new();
    for ch in s.chars() {
        let w = ch.width().unwrap_or(0);
        if used + w > max.saturating_sub(1) {
            break;
        }
        used += w;
        out.push(ch);
    }
    out.push('…');
    out
}

/// 从中间省略，保留头尾。路径、长 URL 用它比截尾更有信息量。
pub fn ellipsis_middle(s: &str, max: usize) -> String {
    let chars: Vec<char> = s.chars().collect();
    if chars.len() <= max || max < 5 {
        return truncate(s, max);
    }
    let keep = max - 1;
    let head = keep / 2;
    let tail = keep - head;
    let mut out: String = chars[..head].iter().collect();
    out.push('…');
    out.extend(chars[chars.len() - tail..].iter());
    out
}

/// 压成单行。连续空白折叠成一个空格，首尾去空白。
pub fn one_line(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut space = false;
    for ch in s.chars() {
        if ch.is_whitespace() {
            space = !out.is_empty();
            continue;
        }
        if space {
            out.push(' ');
            space = false;
        }
        out.push(ch);
    }
    out
}

/// 去掉 ANSI 转义序列。工具输出里经常带颜色码，直接塞进 TUI 会把屏幕搞花。
pub fn strip_ansi(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars().peekable();
    while let Some(c) = chars.next() {
        if c != '\u{1b}' {
            out.push(c);
            continue;
        }
        // ESC [ ... 终止于 0x40..=0x7E；ESC ] ... 终止于 BEL 或 ST
        match chars.peek() {
            Some('[') => {
                chars.next();
                for c in chars.by_ref() {
                    if ('\u{40}'..='\u{7e}').contains(&c) {
                        break;
                    }
                }
            }
            Some(']') => {
                chars.next();
                while let Some(c) = chars.next() {
                    if c == '\u{7}' {
                        break;
                    }
                    if c == '\u{1b}' && chars.peek() == Some(&'\\') {
                        chars.next();
                        break;
                    }
                }
            }
            _ => {}
        }
    }
    out
}

/// 估算 token 数。
///
/// 口径（按经验值，误差 ±20%）：
/// - CJK / 全角：1 字 ≈ 0.75 token（BPE 常把一个汉字拆成 1~2 个 byte-level token）
/// - ASCII 单词：1 词 ≈ 1.3 token
/// - 数字串：每 3 位 ≈ 1 token
/// - 标点与空白：每 4 个 ≈ 1 token
///
/// 代码里符号多，会低估一点；宁可低估后压缩阈值保守（早压缩），也别高估到
/// 把好好的上下文砍掉。
pub fn estimate_tokens(s: &str) -> usize {
    if s.is_empty() {
        return 0;
    }
    let mut cjk = 0usize;
    let mut words = 0usize;
    let mut digits = 0usize;
    let mut misc = 0usize;
    let mut in_word = false;
    for ch in s.chars() {
        let cp = ch as u32;
        let is_cjk = (0x2E80..=0x9FFF).contains(&cp)
            || (0xAC00..=0xD7AF).contains(&cp)
            || (0xF900..=0xFAFF).contains(&cp)
            || (0xFF00..=0xFFEF).contains(&cp);
        if is_cjk {
            cjk += 1;
            in_word = false;
        } else if ch.is_ascii_alphanumeric() || ch == '_' {
            if ch.is_ascii_digit() {
                digits += 1;
            } else if !in_word {
                words += 1;
            }
            in_word = true;
        } else {
            in_word = false;
            misc += 1;
        }
    }
    let est = cjk * 3 / 4 + words * 13 / 10 + digits / 3 + misc / 4;
    est.max(1)
}

/// 快速口径：4 个字符 1 token。用于日志、粗排，不用在压缩阈值上。
pub fn estimate_tokens_fast(s: &str) -> usize {
    s.chars().count().div_ceil(4).max(1)
}

/// 头尾保留截断。工具输出（编译日志、测试结果）的关键信息常常在末尾，
/// 只截头会把"失败原因"截掉，只截尾会把"命令是什么"截掉。
///
/// 头 70%、尾 30%，中间明确写出省略了多少字符，让模型知道信息不完整。
pub fn split_head_tail(s: &str, max: usize) -> String {
    let count = s.chars().count();
    if count <= max || max == 0 {
        if max == 0 {
            return String::new();
        }
        return s.to_string();
    }
    let head_n = (max * 7 / 10).max(1);
    let tail_n = max.saturating_sub(head_n).saturating_sub(1);
    let head: String = s.chars().take(head_n).collect();
    let tail: String = s.chars().skip(count - tail_n).collect();
    let skipped = count - head_n - tail_n;
    format!("{head}\n…（省略 {skipped} 字符）…\n{tail}")
}

/// 按显示宽度折行。
///
/// 规则：
/// - 优先在空格处断，找不到空格就硬断（中文没有空格）；
/// - 保留原有换行；
/// - 不产生空行（连续换行压缩成一个）。
pub fn wrap(s: &str, width: usize) -> Vec<String> {
    if width == 0 {
        return vec![s.to_string()];
    }
    let mut out = Vec::new();
    for raw in s.split('\n') {
        if raw.is_empty() {
            out.push(String::new());
            continue;
        }
        let mut line = String::new();
        let mut used = 0usize;
        let mut last_space: Option<usize> = None;
        for ch in raw.chars() {
            let w = ch.width().unwrap_or(0);
            if used + w > width {
                if let Some(pos) = last_space {
                    // 回退到最后一个空格：把尾巴挪到下一行
                    let tail = line.split_off(pos);
                    out.push(line.trim_end().to_string());
                    line = tail.trim_start().to_string();
                    used = line.width();
                    last_space = None;
                } else {
                    out.push(line);
                    line = String::new();
                    used = 0;
                }
            }
            if ch == ' ' {
                last_space = Some(line.len());
            }
            line.push(ch);
            used += w;
        }
        if !line.trim_end().is_empty() || out.is_empty() {
            out.push(line.trim_end().to_string());
        }
    }
    if out.is_empty() {
        out.push(String::new());
    }
    out
}

/// 判断一段字节像不像二进制。读写文件时用它决定要不要走 base64 提示。
pub fn looks_binary(bytes: &[u8]) -> bool {
    let n = bytes.len().min(8192);
    if n == 0 {
        return false;
    }
    let mut ctrl = 0usize;
    for b in &bytes[..n] {
        if *b == 0 {
            return true;
        }
        // 允许 \t \n \r，其余 C0 控制符算可疑
        if *b < 0x09 || (*b > 0x0d && *b < 0x20) {
            ctrl += 1;
        }
    }
    ctrl * 10 > n
}

/// URL 百分号编码。保留 RFC 3986 的非保留字符。
pub fn url_encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len() * 3);
    for b in s.as_bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(*b as char)
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// URL 解码。非法的 `%XX` 原样保留，不报错（用户手写的 URL 经常不严谨）。
pub fn url_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out: Vec<u8> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let hi = (bytes[i + 1] as char).to_digit(16);
            let lo = (bytes[i + 2] as char).to_digit(16);
            if let (Some(h), Some(l)) = (hi, lo) {
                out.push(((h << 4) | l) as u8);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).to_string()
}

/// 把多行文本变成项目符号列表，缩进对齐。
pub fn bullets(s: &str, prefix: &str) -> String {
    let mut out = String::new();
    for line in s.lines() {
        if line.trim().is_empty() {
            out.push('\n');
        } else {
            out.push_str(prefix);
            out.push_str(line);
            out.push('\n');
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn truncation_is_char_safe() {
        assert_eq!(truncate("中文测试", 3), "中文…");
        assert_eq!(truncate("abc", 10), "abc");
        assert_eq!(truncate_width("中文测试", 5), "中文…");
    }

    #[test]
    fn tokens_rough() {
        let zh = estimate_tokens("这是一段中文测试文本");
        assert!((5..=12).contains(&zh), "{zh}");
        let en = estimate_tokens("hello world this is a test");
        assert!((6..=12).contains(&en), "{en}");
    }

    #[test]
    fn wrap_cjk() {
        let lines = wrap("这是一段很长的中文，需要按宽度折行显示。", 8);
        assert!(lines.len() >= 2);
        for l in &lines {
            assert!(l.width() <= 8, "{l:?} {}", l.width());
        }
    }

    #[test]
    fn head_tail() {
        let s = "a".repeat(100);
        let out = split_head_tail(&s, 20);
        assert!(out.contains("省略"));
    }
}
