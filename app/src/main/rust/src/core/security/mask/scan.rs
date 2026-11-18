//! 扫描主循环：PEM → 键值对 → 已知前缀 → 认证头 → JWT → 高熵串 → 原样
//!
//! 只在一处「词边界」上试各类匹配：前一个字节不是标识符字符才算边界，
//! 这样 `mytoken` / `tokenizer` 里的 token 不会被当成键名。

use super::patterns;
use super::rules::Rules;
use crate::core::base::{bytes, marks, text};

/// 按规则扫描整段文本，返回打码后的字节
pub fn run(src: &[u8], r: &Rules) -> Vec<u8> {
    let n = src.len();
    let mut out: Vec<u8> = Vec::with_capacity(n + 8);
    let mut i = 0usize;
    while i < n {
        let boundary = i == 0 || !bytes::ident(src[i - 1]);
        if boundary {
            if let Some(end) = patterns::pem(src, i) {
                marks::push(&mut out, &r.mark, end - i);
                i = end;
                continue;
            }
            if let Some(end) = pair(src, i, r, &mut out) {
                i = end;
                continue;
            }
            if let Some(end) = prefix(src, i, r, &mut out) {
                i = end;
                continue;
            }
            if let Some(end) = patterns::auth(src, i, r, &mut out) {
                i = end;
                continue;
            }
            if let Some(end) = patterns::jwt(src, i, r, &mut out) {
                i = end;
                continue;
            }
            let end = text::token_end(src, i);
            if end > i {
                if entropy(&src[i..end], r) {
                    marks::push(&mut out, &r.mark, end - i);
                } else {
                    out.extend_from_slice(&src[i..end]);
                }
                i = end;
                continue;
            }
        }
        out.push(src[i]);
        i += 1;
    }
    out
} //扫描整段

/// 键值对：键名后跟 : 或 =，把值整段打码；值太短就不动，避免把结构打烂
fn pair(src: &[u8], at: usize, r: &Rules, out: &mut Vec<u8>) -> Option<usize> {
    let k = r.hit_key(src, at)?;
    let mut j = text::skip(src, at + k, b" \t");
    j = text::skip(src, j, text::QUOTES);
    let sep = *src.get(j)?;
    if sep != b':' && sep != b'=' {
        return None;
    }
    j += 1;
    j = text::skip(src, j, b" \t");
    j = text::skip(src, j, text::QUOTES);
    let end = text::value_end(src, j);
    if end - j < r.min_val {
        return None;
    }
    out.extend_from_slice(&src[at..j]);
    marks::push(out, &r.mark, end - j);
    Some(end)
} //键值对

/// 已知前缀：前缀连同后面的密文一起打码，整体长度不够就当普通文本
fn prefix(src: &[u8], at: usize, r: &Rules, out: &mut Vec<u8>) -> Option<usize> {
    let p = r.hit_prefix(src, at)?;
    let end = text::token_end(src, at + p);
    if end - at < r.min_len {
        return None;
    }
    marks::push(out, &r.mark, end - at);
    Some(end)
} //已知前缀

/// 高熵判定：长度够、大小写与数字三类齐全、分隔符占比不高，才当密文
fn entropy(tok: &[u8], r: &Rules) -> bool {
    if tok.len() < r.alnum_len {
        return false;
    }
    let (mut up, mut low, mut dig, mut sep) = (0usize, 0usize, 0usize, 0usize);
    for &b in tok {
        if bytes::upper(b) {
            up += 1;
        } else if bytes::lower_ch(b) {
            low += 1;
        } else if bytes::digit(b) {
            dig += 1;
        } else {
            sep += 1;
        }
    }
    if up == 0 || low == 0 || dig == 0 {
        return false;
    }
    //分隔符超过八分之一更像路径或域名，按普通文本处理
    if sep * 8 > tok.len() {
        return false;
    }
    //每类至少占十分之一，挡掉 user_0001_aaaa 这种半结构化串
    let tenth = (tok.len() / 10).max(1);
    up >= tenth && low >= tenth && dig >= tenth
} //高熵判定

#[cfg(test)]
mod tests {
    use super::*;

    fn r() -> Rules {
        Rules::builtin()
    }

    fn mask(s: &[u8]) -> String {
        String::from_utf8_lossy(&run(s, &r())).to_string()
    }

    #[test]
    fn json_pair() {
        let out = mask(b"{\"password\":\"hunter2hunter2\",\"note\":\"ok\"}");
        assert!(!out.contains("hunter2hunter2"), "{}", out);
        assert!(out.contains("\"password\":"), "{}", out);
        assert!(out.contains("\"note\":\"ok\""), "{}", out);
    }

    #[test]
    fn ini_and_url() {
        let out = mask(b"token=abcdefgh&user=bai");
        assert!(!out.contains("abcdefgh"), "{}", out);
        assert!(out.contains("user=bai"), "{}", out);
    }

    #[test]
    fn authorization_header() {
        let out = mask(b"Authorization: Bearer sk-abcdefghijklmnopqrstuvwxyz");
        assert!(!out.contains("abcdefghijklmnop"), "{}", out);
        assert!(out.contains("Authorization"), "{}", out);
    }

    #[test]
    fn known_prefix() {
        let out = mask(b"key is ghp_0123456789abcdefghijklmnopqrstuvwxyz");
        assert!(!out.contains("0123456789"), "{}", out);
        assert!(out.contains("key is"), "{}", out);
    }

    #[test]
    fn high_entropy() {
        let out = mask(b"dXNlcjpwYXNzd29yZDEyMzQ1Njc4OQ");
        assert!(!out.contains("dXNlcjpwYXNz"), "{}", out);
    }

    #[test]
    fn urls_and_hashes_survive() {
        //长 URL 与 git 短哈希不能被打掉
        let url = mask(b"https://example.com/a/very/long/path/to/some/file.html");
        assert!(url.contains("very/long/path"), "{}", url);
        let hash = mask(b"commit 3f2a1b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a");
        assert!(hash.contains("3f2a1b9c"), "{}", hash);
    }

    #[test]
    fn not_a_boundary() {
        //mytoken 里的 token 不算键名
        let out = mask(b"mytoken=abcdefgh");
        assert!(out.contains("abcdefgh"), "{}", out);
    }

    #[test]
    fn cn_text_untouched() {
        let s = "配置里写 password 这一项是键名，值才敏感。";
        assert_eq!(mask(s.as_bytes()), s);
    }

    #[test]
    fn empty_and_short() {
        assert_eq!(mask(b""), "");
        assert_eq!(mask(b"a"), "a");
    }
}