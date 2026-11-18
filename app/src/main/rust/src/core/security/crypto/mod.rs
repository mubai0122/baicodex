//! 基础密码学动作：逐字节异或与常量时间比较，guard / secret 迁过来后直接复用
//!
//! 这里只放不依赖密钥材料的纯运算，密钥派生与封包逻辑以后另起子模块。

/// 逐字节异或：out = a ^ b，长度取三者最小值
pub fn xor(out: &mut [u8], a: &[u8], b: &[u8]) {
    let n = out.len().min(a.len()).min(b.len());
    for i in 0..n {
        out[i] = a[i] ^ b[i];
    }
} //逐字节异或

/// 常量时间比较：不因为提前返回泄露内容，长度不同直接判不等
pub fn ct_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for i in 0..a.len() {
        diff |= a[i] ^ b[i];
    }
    diff == 0
} //常量时间比较

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn xor_roundtrip() {
        let a = [1u8, 2, 3, 4];
        let b = [9u8, 8, 7, 6];
        let mut out = [0u8; 4];
        xor(&mut out, &a, &b);
        assert_eq!(out, [8, 10, 4, 2]);
        let mut back = [0u8; 4];
        xor(&mut back, &out, &b);
        assert_eq!(back, a);
    }

    #[test]
    fn xor_length_mismatch() {
        let mut out = [0u8; 8];
        xor(&mut out, &[1, 2], &[3, 4]);
        assert_eq!(&out[..2], &[2, 6]);
        assert_eq!(&out[2..], &[0, 0, 0, 0, 0, 0]);
    }

    #[test]
    fn eq_semantics() {
        assert!(ct_eq(b"abc", b"abc"));
        assert!(!ct_eq(b"abc", b"abd"));
        assert!(!ct_eq(b"abc", b"ab"));
        assert!(ct_eq(b"", b""));
    }
}