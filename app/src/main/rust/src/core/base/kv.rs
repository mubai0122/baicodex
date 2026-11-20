/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:27
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 极简 KV 文本解析：一行一个 key=value，不做转义与嵌套（规则由服务端生产、本地消费）

/// 单项长度上限：服务端下发的键名 / 前缀不会这么长，超长当坏数据丢掉
pub const MAX_ITEM_LEN: usize = 64;
/// 单表条数上限：防止坏数据把内存撑爆
pub const MAX_ITEMS: usize = 256;

/// 去两端空白（空格 / tab / 回车 / 换行）
pub fn trim(s: &[u8]) -> &[u8] {
    let mut a = 0usize;
    let mut b = s.len();
    while a < b && matches!(s[a], b' ' | b'\t' | b'\r' | b'\n') {
        a += 1;
    }
    while b > a && matches!(s[b - 1], b' ' | b'\t' | b'\r' | b'\n') {
        b -= 1;
    }
    &s[a..b]
} //去空白

/// 取出全部键值对：键统一转小写，跳过空行与 # 开头的注释行
pub fn pairs(text: &[u8]) -> Vec<(String, String)> {
    let mut out: Vec<(String, String)> = Vec::new();
    for line in text.split(|b| *b == b'\n') {
        let line = trim(line);
        if line.is_empty() || line[0] == b'#' {
            continue;
        }
        let eq = match line.iter().position(|b| *b == b'=') {
            Some(p) => p,
            None => continue,
        };
        let key = String::from_utf8_lossy(trim(&line[..eq])).to_ascii_lowercase();
        let val = String::from_utf8_lossy(trim(&line[eq + 1..])).to_string();
        if key.is_empty() {
            continue;
        }
        out.push((key, val));
    }
    out
} //解析成键值对

/// 逗号分隔列表：丢空项与超长项，条数封顶
pub fn csv(v: &str) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for one in v.split(',') {
        let one = one.trim();
        if one.is_empty() || one.len() > MAX_ITEM_LEN {
            continue;
        }
        if out.len() >= MAX_ITEMS {
            break;
        }
        out.push(one.to_string());
    }
    out
} //切分列表

/// 取整数并夹到 [lo, hi]：解析失败退回 def
pub fn usize_in(v: &str, def: usize, lo: usize, hi: usize) -> usize {
    match v.trim().parse::<usize>() {
        Ok(n) => n.clamp(lo, hi),
        Err(_) => def,
    }
} //读整数

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pairs_basic() {
        let v = pairs(b"# c\nkeys=a,b\n\nminlen = 16\r\nalnum=24\nbad\n");
        assert_eq!(v.len(), 3);
        assert_eq!(v[0].0, "keys");
        assert_eq!(v[0].1, "a,b");
        assert_eq!(v[1].0, "minlen");
        assert_eq!(v[1].1, "16");
    }

    #[test]
    fn csv_limits() {
        assert_eq!(csv("a, b ,,c"), vec!["a", "b", "c"]);
        assert!(csv(&"x".repeat(MAX_ITEM_LEN + 1)).is_empty());
    }

    #[test]
    fn num_clamped() {
        assert_eq!(usize_in("8", 16, 4, 512), 8);
        assert_eq!(usize_in("1", 16, 4, 512), 4);
        assert_eq!(usize_in("9999", 16, 4, 512), 512);
        assert_eq!(usize_in("abc", 16, 4, 512), 16);
    }
}