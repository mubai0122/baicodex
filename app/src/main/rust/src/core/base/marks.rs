//! 掩码写入：标记数量跟着原值长度走但封顶，保证输出永不长于输入，目的缓冲只需 len + 8

use super::bytes;

/// 一次打码最多写几个标记
pub const MAX_MARKS: usize = 6;
/// 掩码串长度上限
pub const MAX_MARK_LEN: usize = 16;

/// 规范化掩码串：只留可打印 ASCII，长度夹到 1..=16，不合格退回单个星号
pub fn normalize(mark: &[u8]) -> Vec<u8> {
    let mut out: Vec<u8> = mark
        .iter()
        .copied()
        .filter(|b| bytes::printable(*b))
        .take(MAX_MARK_LEN)
        .collect();
    if out.is_empty() {
        out.push(b'*');
    }
    out
} //收紧掩码串

/// 把 len 字节的原值替换成掩码推进 out
pub fn push(out: &mut Vec<u8>, mark: &[u8], len: usize) {
    let n = len.min(MAX_MARKS);
    if n == 0 {
        return;
    }
    let m: &[u8] = if mark.is_empty() { &b"*"[..] } else { mark };
    for k in 0..n {
        out.push(m[k % m.len()]);
    }
} //写入掩码

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_sanitizes() {
        assert_eq!(normalize(b""), b"*".to_vec());
        assert_eq!(normalize(b"*#"), b"*#".to_vec());
        //换行与中文多字节被剔掉，避免把结构打乱
        assert_eq!(normalize(b"*\n\t"), b"*".to_vec());
        assert_eq!(normalize(b"\xe2\x97\x8f"), b"*".to_vec());
    }

    #[test]
    fn push_capped() {
        let mut out = Vec::new();
        push(&mut out, b"******", 100);
        assert_eq!(out.len(), MAX_MARKS);
        out.clear();
        push(&mut out, b"**", 5);
        assert_eq!(out, b"*****".to_vec());
        out.clear();
        push(&mut out, b"**", 0);
        assert!(out.is_empty());
    }
}