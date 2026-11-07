//! 标识符生成。
//!
//! 全库的 id 只有三种形态，各有各的用途，不要混用：
//!
//! | 形态 | 例子 | 用途 |
//! |---|---|---|
//! | 24 位 hex，六段 | `a1b2-c3d4-e5f6-0718-293a-4b5c` | 会话目录名（人能读、能口头念） |
//! | 带前缀短 id | `call_9f3a2c1b4d5e` | 工具调用 id（模型回传时要唯一） |
//! | 自增整数 | `42` | 消息 id（只在会话内唯一） |
//!
//! 会话 id 用随机而不是时间戳：同一秒内建两个会话不会撞，且看不出创建顺序。

use rand::Rng;

/// id 生成器。无状态，纯函数集合；保留结构体是为了将来能注入确定性种子（测试）。
#[derive(Debug, Clone, Copy, Default)]
pub struct IdGen;

impl IdGen {
    /// 24 位小写 hex，六段四位。用作会话目录名。
    pub fn session() -> String {
        let bytes: [u8; 12] = rand::thread_rng().gen();
        let hex: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
        (0..6)
            .map(|i| hex[i * 4..i * 4 + 4].to_string())
            .collect::<Vec<_>>()
            .join("-")
    }

    /// 指定长度的随机 hex。
    pub fn hex(len: usize) -> String {
        let n = len.div_ceil(2);
        let bytes: Vec<u8> = (0..n).map(|_| rand::thread_rng().gen()).collect();
        let mut s: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
        s.truncate(len);
        s
    }

    /// 带前缀的短 id：`call_` + 12 位。
    pub fn short(prefix: &str) -> String {
        format!("{prefix}_{}", Self::hex(12))
    }

    /// 纯数字串（验证码、短码）。
    pub fn digits(n: usize) -> String {
        let mut rng = rand::thread_rng();
        (0..n).map(|_| char::from(b'0' + rng.gen_range(0..10))).collect()
    }

    /// 把标题压成安全的文件名：中文保留，空格变 `-`，其余标点去掉，最长 40 字符。
    pub fn slug(title: &str) -> String {
        let mut out = String::new();
        let mut dash = false;
        for ch in title.chars() {
            if ch.is_alphanumeric() || is_cjk(ch) {
                out.push(ch);
                dash = false;
            } else if (ch.is_whitespace() || matches!(ch, '-' | '_' | '.' | '/' | '\\'))
                && !dash && !out.is_empty() {
                    out.push('-');
                    dash = true;
                }
            if out.chars().count() >= 40 {
                break;
            }
        }
        let s = out.trim_matches('-').to_string();
        if s.is_empty() {
            Self::hex(8)
        } else {
            s
        }
    }

    /// 判断一个字符串像不像我们生成的会话 id。
    pub fn is_session_id(s: &str) -> bool {
        s.len() == 29
            && s.split('-').count() == 6
            && s.split('-').all(|p| p.len() == 4 && p.chars().all(|c| c.is_ascii_hexdigit()))
    }
}

fn is_cjk(ch: char) -> bool {
    let c = ch as u32;
    (0x4E00..=0x9FFF).contains(&c) || (0x3400..=0x4DBF).contains(&c)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn session_shape() {
        let id = IdGen::session();
        assert_eq!(id.len(), 29);
        assert!(IdGen::is_session_id(&id));
    }

    #[test]
    fn slug_safe() {
        assert_eq!(IdGen::slug("重建 项目/结构？"), "重建-项目-结构");
        // 全是标点时退回随机短 id：不 panic，也不返回空串（空文件名会出问题）
        let fallback = IdGen::slug("///");
        assert_eq!(fallback.len(), 8);
        assert!(fallback.chars().all(|c| c.is_ascii_hexdigit()));
        assert!(!IdGen::slug("///").contains('/'));
    }
}
