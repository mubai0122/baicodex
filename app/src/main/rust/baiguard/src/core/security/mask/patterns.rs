//! 显式凭据形态：PEM 私钥块、认证头、JWT
//!
//! 这三种不依赖键名，靠开头特征直接识别；命中后整段打码，不保留任何原文片段。

use super::rules::Rules;
use crate::core::base::{bytes, marks, text};

/// PEM 私钥 / 证书整块：从 `-----BEGIN ...` 到对应的 `-----END ...-----`
pub fn pem(src: &[u8], at: usize) -> Option<usize> {
    if !src[at..].starts_with(b"-----BEGIN ") {
        return None;
    }
    let tail = bytes::find(src, at + 11, b"-----END ")?;
    let close = bytes::find(src, tail + 9, b"-----")?;
    let mut end = close + 5;
    if end < src.len() && src[end] == b'\r' {
        end += 1;
    }
    if end < src.len() && src[end] == b'\n' {
        end += 1;
    }
    if end - at < 32 {
        return None;
    }
    Some(end)
} //私钥整块

/// 认证头：`Bearer xxx` / `Basic xxx`，标签留着，后面那串打码
pub fn auth(src: &[u8], at: usize, r: &Rules, out: &mut Vec<u8>) -> Option<usize> {
    let tag = r.hit_auth(src, at)?;
    let mut j = text::skip(src, at + tag, text::BLANK);
    j = text::skip(src, j, text::QUOTES);
    let end = text::token_end(src, j);
    if end - j < 8 {
        return None;
    }
    out.extend_from_slice(&src[at..j]);
    marks::push(out, &r.mark, end - j);
    Some(end)
} //认证头

/// JWT：`eyJ` 开头、两段点分隔的三段 base64url
pub fn jwt(src: &[u8], at: usize, r: &Rules, out: &mut Vec<u8>) -> Option<usize> {
    if !(src[at..].starts_with(b"eyJ") || src[at..].starts_with(b"eyA")) {
        return None;
    }
    let mut j = at;
    let mut dots = 0usize;
    while j < src.len() && (text::b64url(src[j]) || src[j] == b'.') {
        if src[j] == b'.' {
            dots += 1;
        }
        j += 1;
    }
    if dots != 2 || j - at < r.alnum_len {
        return None;
    }
    marks::push(out, &r.mark, j - at);
    Some(j)
} //JWT

#[cfg(test)]
mod tests {
    use super::*;

    fn rules() -> Rules {
        Rules::builtin()
    }

    #[test]
    fn pem_block() {
        let s = b"key:\n-----BEGIN RSA PRIVATE KEY-----\nMIIEow\n-----END RSA PRIVATE KEY-----\nafter";
        let at = 5;
        let end = pem(s, at).expect("该命中");
        assert!(end < s.len());
        assert_eq!(&s[end..], b"after");
    }

    #[test]
    fn pem_needs_end() {
        let s = b"-----BEGIN RSA PRIVATE KEY-----\nMIIEow\n";
        assert!(pem(s, 0).is_none());
    }

    #[test]
    fn bearer_masked() {
        let r = rules();
        let mut out = Vec::new();
        let end = auth(b"Bearer abcdefghijklmn", 0, &r, &mut out).expect("该命中");
        assert_eq!(end, 21);
        let s = String::from_utf8_lossy(&out).to_string();
        assert!(s.starts_with("Bearer "), "{}", s);
        assert!(!s.contains("abcdefgh"), "{}", s);
    }

    #[test]
    fn auth_too_short() {
        let r = rules();
        let mut out = Vec::new();
        assert!(auth(b"Bearer ab", 0, &r, &mut out).is_none());
        assert!(out.is_empty());
    }

    #[test]
    fn jwt_masked() {
        let r = rules();
        let token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnop";
        let mut out = Vec::new();
        let end = jwt(token.as_bytes(), 0, &r, &mut out).expect("该命中");
        assert_eq!(end, token.len());
        assert!(!String::from_utf8_lossy(&out).contains("eyJ"));
    }

    #[test]
    fn jwt_needs_three_parts() {
        let r = rules();
        let mut out = Vec::new();
        assert!(jwt(b"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0", 0, &r, &mut out).is_none());
    }
}