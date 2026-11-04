//! 扫描游走：跳空白、读出值区、走完整 token，全部按字节推进

use super::bytes;

/// 空白与换行
pub const BLANK: &[u8] = b" \t\r\n";
/// 各种引号：JSON / 单引号 / 反引号
pub const QUOTES: &[u8] = b"\"'`";
/// 值里允许出现的字符：密文里的 _ - . : + / = @ 都要留住；
/// & ? # 是 URL 分隔符，逗号与分号是列表分隔符，一律当作值的终点
pub const VAL: &[u8] = b"-._:+/=@!$%*()[]{}~^";
/// token 里允许出现的字符：比值窄一点，避免把句子里的标点一起吞进来
pub const TOK: &[u8] = b"-._+/=";

/// 从 at 起跳过 set 里的字节
pub fn skip(src: &[u8], mut at: usize, set: &[u8]) -> usize {
    while at < src.len() && set.contains(&src[at]) {
        at += 1;
    }
    at
} //跳字符

/// 值区终点：带引号就读到配对引号（不含），否则按 VAL 集合读到第一个分隔符
pub fn value_end(src: &[u8], at: usize) -> usize {
    if at >= src.len() {
        return at;
    }
    let q = src[at];
    if QUOTES.contains(&q) {
        let mut j = at + 1;
        while j < src.len() {
            if src[j] == b'\\' {
                j += 2; //跳过转义，避免 \" 提前收尾
                continue;
            }
            if src[j] == q {
                return j;
            }
            j += 1;
        }
        return src.len();
    }
    let mut j = at;
    while j < src.len() && (bytes::ident(src[j]) || VAL.contains(&src[j])) {
        j += 1;
    }
    j
} //读值

/// token 终点：从 at 起连续满足标识符或 TOK 的字节
pub fn token_end(src: &[u8], at: usize) -> usize {
    let mut j = at;
    while j < src.len() && (bytes::ident(src[j]) || TOK.contains(&src[j])) {
        j += 1;
    }
    j
} //走 token

/// 是不是 base64url 字符（含点分隔，供 JWT 判定用）
pub fn b64url(b: u8) -> bool {
    bytes::ident(b) || matches!(b, b'-' | b'+' | b'/' | b'=')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn skip_stops() {
        assert_eq!(skip(b"   x", 0, BLANK), 3);
        assert_eq!(skip(b"abc", 0, BLANK), 0);
    }

    #[test]
    fn value_plain() {
        assert_eq!(value_end(b"hunter2,\"next\"", 0), 7);
    }

    #[test]
    fn value_quoted() {
        let s = b"\"p@ss word\",x";
        assert_eq!(value_end(s, 0), 10);
    }

    #[test]
    fn value_escape() {
        let s = b"\"a\\\"b\",y";
        assert_eq!(value_end(s, 0), 5);
    }
}