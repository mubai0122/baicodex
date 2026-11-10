//! 米家局域网报文层：探测包、32 字节头、回包里少数数值字段。
//!
//! 只处理字节：socket 在 C++ 的 core/feature/miot/udp，加解密在 C++ 的 core/security/crypto，
//! 两边的符号通过 C ABI 拼在一起，这里不碰网络也不碰 OpenSSL。

/// 报文头长度：magic2 + 总长2 + did8 + 时间戳4 + 校验16
pub const HEAD: usize = 32;
const H0: u8 = 0x21;
const H1: u8 = 0x31;

/// 32 字节广播探测包：did 段填全 F，"MDID" 之后跟自己的虚拟 did
pub fn probe(virtual_did: u64) -> [u8; HEAD] {
    let mut b = [0u8; HEAD];
    b[0] = H0;
    b[1] = H1;
    b[2] = 0;
    b[3] = HEAD as u8;
    for x in b.iter_mut().take(16).skip(4) {
        *x = 0xFF;
    }
    b[16..20].copy_from_slice(b"MDID");
    b[20..28].copy_from_slice(&virtual_did.to_be_bytes());
    b
} //探测包

/// 头里的 did（大端 8 字节，位于 4..12）；长度不够返回 -1
pub fn head_did(b: &[u8]) -> i64 {
    if b.len() < 12 {
        return -1;
    }
    let mut v: u64 = 0;
    for &x in &b[4..12] {
        v = (v << 8) | x as u64;
    }
    v as i64
} //取 did

/// 设备时间戳（大端 4 字节，位于 12..16），用来算本机与设备的时钟差
pub fn head_stamp(b: &[u8]) -> u32 {
    if b.len() < 16 {
        return 0;
    }
    ((b[12] as u32) << 24) | ((b[13] as u32) << 16) | ((b[14] as u32) << 8) | b[15] as u32
} //取时间戳

/// 在 JSON 文本里找 "key": 后面的整数；找不到返回 i64::MIN（调用方用 get 语义判断）
///
/// 只认顶层或任意深度上第一个「键 + 冒号 + 数字」的位置，够取 id / code 这类字段。
/// 字符串值里出现的同名字样也会被命中，协议层这两个键不会出现在字符串值里。
pub fn json_int(text: &[u8], key: &[u8]) -> i64 {
    if key.is_empty() || text.len() < key.len() + 3 {
        return i64::MIN;
    }
    let mut i = 0usize;
    while i + key.len() + 1 < text.len() {
        let is_key = text[i] == b'"'
            && &text[i + 1..i + 1 + key.len()] == key
            && text[i + 1 + key.len()] == b'"';
        if is_key {
            let mut j = i + 2 + key.len();
            j = skip_ws(text, j);
            if j < text.len() && text[j] == b':' {
                j = skip_ws(text, j + 1);
                let start = j;
                if j < text.len() && text[j] == b'-' {
                    j += 1;
                }
                let digit_at = j;
                while j < text.len() && text[j].is_ascii_digit() {
                    j += 1;
                }
                if j > digit_at {
                    if let Ok(s) = std::str::from_utf8(&text[start..j]) {
                        if let Ok(v) = s.parse::<i64>() {
                            return v;
                        }
                    }
                }
            }
        }
        i += 1;
    }
    i64::MIN
} //取整数字段

fn skip_ws(b: &[u8], mut i: usize) -> usize {
    while i < b.len() && (b[i] == b' ' || b[i] == b'\t' || b[i] == b'\n' || b[i] == b'\r') {
        i += 1;
    }
    i
} //跳过空白

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn probe_header() {
        let p = probe(0x0102030405060708);
        assert_eq!(p.len(), HEAD);
        assert_eq!(p[0], 0x21);
        assert_eq!(p[1], 0x31);
        assert_eq!(p[2], 0);
        assert_eq!(p[3], 32);
        assert_eq!(&p[4..16], &[0xFF; 12]);
        assert_eq!(&p[16..20], b"MDID");
        assert_eq!(&p[20..28], &[1, 2, 3, 4, 5, 6, 7, 8]);
        //探测包的 did 段是全 F，head_did 必须原样读回来（u64 全 F 落到 i64 就是 -1）
        assert_eq!(head_did(&p), -1);
    } //探测包

    #[test]
    fn head_fields() {
        let mut b = [0u8; HEAD];
        b[4..12].copy_from_slice(&123456789u64.to_be_bytes());
        b[12..16].copy_from_slice(&0x5F5E100u32.to_be_bytes());
        assert_eq!(head_did(&b), 123456789);
        assert_eq!(head_stamp(&b), 0x5F5E100);
        assert_eq!(head_did(&b[..8]), -1);
        assert_eq!(head_stamp(&b[..8]), 0);
    } //头字段

    #[test]
    fn json_numbers() {
        let s = br#"{"id":42,"result":[{"code":0,"value":true}],"code":-7}"#;
        assert_eq!(json_int(s, b"id"), 42);
        assert_eq!(json_int(s, b"code"), 0);
        assert_eq!(json_int(s, b"missing"), i64::MIN);
        //带空白的写法
        assert_eq!(json_int(br#"{"id" :  7 }"#, b"id"), 7);
        //负数
        assert_eq!(json_int(br#"{"x":-12}"#, b"x"), -12);
        //字符串里的同名键不当作数字（后面没有冒号）
        assert_eq!(json_int(br#"{"note":"id"}"#, b"id"), i64::MIN);
    } //JSON 取整数
} //测试