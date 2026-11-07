//! 字节与编码。
//!
//! 只放"到处都要用、又容易写错"的底层函数：hex、base64、随机数、常量时间比较、
//! 摘要、大小格式化。加密算法本身不在这里（见 `core::security::secret`）。

use base64::Engine;
use rand::RngCore;
use sha2::{Digest, Sha256};

/// 小写 hex 编码。
pub fn hex_encode(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut out = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        out.push(HEX[(b >> 4) as usize] as char);
        out.push(HEX[(b & 0x0f) as usize] as char);
    }
    out
}

/// hex 解码。长度为奇数或含非法字符时返回 None。
///
/// 不 panic、不"尽力而为"：密钥、盐、摘要这类输入一旦不合法，静默截断比报错危险得多。
pub fn hex_decode(s: &str) -> Option<Vec<u8>> {
    let s = s.trim();
    if s.len() % 2 != 0 {
        return None;
    }
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len() / 2);
    let mut i = 0;
    while i < b.len() {
        let hi = (b[i] as char).to_digit(16)?;
        let lo = (b[i + 1] as char).to_digit(16)?;
        out.push(((hi << 4) | lo) as u8);
        i += 2;
    }
    Some(out)
}

/// 标准 base64（带 padding）。
pub fn b64_encode(bytes: &[u8]) -> String {
    base64::engine::general_purpose::STANDARD.encode(bytes)
}

/// 标准 base64 解码。失败返回 None。
pub fn b64_decode(s: &str) -> Option<Vec<u8>> {
    base64::engine::general_purpose::STANDARD.decode(s.trim()).ok()
}

/// URL 安全 base64（无 padding），放 URL、文件名里不会出问题。
pub fn b64url_encode(bytes: &[u8]) -> String {
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}

/// URL 安全 base64 解码。
pub fn b64url_decode(s: &str) -> Option<Vec<u8>> {
    base64::engine::general_purpose::URL_SAFE_NO_PAD.decode(s.trim()).ok()
}

/// 加密安全随机字节。
pub fn random_bytes(n: usize) -> Vec<u8> {
    let mut v = vec![0u8; n];
    rand::thread_rng().fill_bytes(&mut v);
    v
}

/// 随机 hex 串（半密钥用）。
pub fn random_hex(n: usize) -> String {
    hex_encode(&random_bytes(n.div_ceil(2)))[..n].to_string()
}

/// 常量时间比较。
///
/// 普通 `==` 会在第一个不同字节处返回，攻击者能通过计时猜出前缀。比较密钥、
/// 签名、token 时必须用这个。长度不同直接 false——长度本身不算秘密。
pub fn ct_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for i in 0..a.len() {
        diff |= a[i] ^ b[i];
    }
    diff == 0
}

/// SHA-256，返回小写 hex。
pub fn sha256_hex(data: &[u8]) -> String {
    let mut h = Sha256::new();
    h.update(data);
    hex_encode(&h.finalize())
}

/// SHA-256 原始字节。
pub fn sha256(data: &[u8]) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(data);
    let out = h.finalize();
    let mut arr = [0u8; 32];
    arr.copy_from_slice(&out);
    arr
}

/// MD5。只用于"内容指纹/去重"，不用于任何安全目的。
pub fn md5_hex(data: &[u8]) -> String {
    use md5::Md5;
    let mut h = Md5::new();
    h.update(data);
    hex_encode(&h.finalize())
}

/// 异或到目标缓冲区：`dst = a ^ b`。
pub fn xor_into(dst: &mut [u8], a: &[u8], b: &[u8]) {
    let n = dst.len().min(a.len()).min(b.len());
    for i in 0..n {
        dst[i] = a[i] ^ b[i];
    }
}

/// 大端 4 字节。
pub fn be_u32(v: u32) -> [u8; 4] {
    v.to_be_bytes()
}

/// 从大端 4 字节读。
pub fn read_be_u32(b: &[u8]) -> u32 {
    let mut a = [0u8; 4];
    let n = b.len().min(4);
    a[..n].copy_from_slice(&b[..n]);
    u32::from_be_bytes(a)
}

/// 人类可读的大小：`1.2 MB`。
pub fn human_size(n: u64) -> String {
    const UNITS: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    let mut v = n as f64;
    let mut i = 0;
    while v >= 1024.0 && i < UNITS.len() - 1 {
        v /= 1024.0;
        i += 1;
    }
    if i == 0 {
        format!("{n} B")
    } else {
        format!("{v:.1} {}", UNITS[i])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hex_roundtrip() {
        let b = b"\x00\x0f\xffabc";
        assert_eq!(hex_decode(&hex_encode(b)).unwrap(), b);
        assert!(hex_decode("abc").is_none());
        assert!(hex_decode("zz").is_none());
    }

    #[test]
    fn base64_roundtrip() {
        let b = "中文 and bytes".as_bytes();
        assert_eq!(b64_decode(&b64_encode(b)).unwrap(), b);
        assert_eq!(b64url_decode(&b64url_encode(b)).unwrap(), b);
    }

    #[test]
    fn compare_is_constant_time_shape() {
        assert!(ct_eq(b"abcdef", b"abcdef"));
        assert!(!ct_eq(b"abcdef", b"abcdeg"));
        assert!(!ct_eq(b"a", b"aa"));
    }

    #[test]
    fn sizes() {
        assert_eq!(human_size(999), "999 B");
        assert_eq!(human_size(1536), "1.5 KB");
    }
}
