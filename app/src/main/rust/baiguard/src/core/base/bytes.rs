//! 字节级工具：全部按字节推进，不切 UTF-8 字符边界，中文与大写多字节字符原样透传

/// 小写化：只认 ASCII 大写字母，其余原样
pub const fn lower(b: u8) -> u8 {
    if b >= b'A' && b <= b'Z' {
        b + 32
    } else {
        b
    }
} //小写化

/// 是不是 ASCII 数字
pub const fn digit(b: u8) -> bool {
    b >= b'0' && b <= b'9'
}

/// 是不是 ASCII 大写字母
pub const fn upper(b: u8) -> bool {
    b >= b'A' && b <= b'Z'
}

/// 是不是 ASCII 小写字母
pub const fn lower_ch(b: u8) -> bool {
    b >= b'a' && b <= b'z'
}

/// 是不是标识符字符：字母 / 数字 / 下划线
pub const fn ident(b: u8) -> bool {
    digit(b) || upper(b) || lower_ch(b) || b == b'_'
}

/// 是不是可打印 ASCII（掩码串只留这一类字符）
pub const fn printable(b: u8) -> bool {
    b >= 0x20 && b < 0x7f
}

/// src 从 at 起是否以 pat 开头（忽略大小写）；pat 为空或越界一律返回 false
pub fn starts_ci(src: &[u8], at: usize, pat: &[u8]) -> bool {
    if pat.is_empty() || at >= src.len() || pat.len() > src.len() - at {
        return false;
    }
    (0..pat.len()).all(|k| lower(src[at + k]) == lower(pat[k]))
} //忽略大小写前缀匹配

/// 从 at 起找 sub 第一次出现的位置
pub fn find(src: &[u8], at: usize, sub: &[u8]) -> Option<usize> {
    if sub.is_empty() || at >= src.len() || sub.len() > src.len() - at {
        return None;
    }
    (at..=src.len() - sub.len()).find(|i| &src[*i..*i + sub.len()] == sub)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lower_only_ascii() {
        assert_eq!(lower(b'A'), b'a');
        assert_eq!(lower(b'Z'), b'z');
        assert_eq!(lower(0xE4), 0xE4);
    }

    #[test]
    fn starts_ci_bounds() {
        assert!(starts_ci(b"Password", 0, b"pass"));
        assert!(starts_ci(b"xTOKEN", 1, b"token"));
        assert!(!starts_ci(b"tok", 0, b"token"));
        assert!(!starts_ci(b"token", 3, b"token"));
    }

    #[test]
    fn find_works() {
        assert_eq!(find(b"aXbXc", 0, b"X"), Some(1));
        assert_eq!(find(b"aXbXc", 2, b"X"), Some(3));
        assert_eq!(find(b"abc", 0, b"z"), None);
    }
}