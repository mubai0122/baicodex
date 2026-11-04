//! 数据输出脱敏：规则由服务端以极简 KV 下发，源码里只留一套兜底
//!
//! 三层判断顺序固定：显式形态（PEM / 认证头 / JWT）→ 键值对 → 已知前缀 → 高熵串。
//! 全部按字节处理，不切 UTF-8 字符边界，所以中文与表情不会被打穿。

pub mod patterns;
pub mod rules;
pub mod scan;

use std::sync::Mutex;

/// 当前生效的规则：没载入过就走内置兜底
static HELD: Mutex<Option<rules::Rules>> = Mutex::new(None);

/// 载入规则文本，成功返回 true；空文本视为不更新，保留上一版
pub fn load(text: &[u8]) -> bool {
    if text.is_empty() {
        return false;
    }
    let parsed = rules::parse(text);
    match HELD.lock() {
        Ok(mut g) => {
            *g = Some(parsed);
            true
        }
        Err(_) => false,
    }
} //载入规则

/// 按当前规则打码
pub fn apply(src: &[u8]) -> Vec<u8> {
    if src.is_empty() {
        return Vec::new();
    }
    let fallback = rules::Rules::builtin();
    match HELD.lock() {
        Ok(g) => match g.as_ref() {
            Some(r) => scan::run(src, r),
            None => scan::run(src, &fallback),
        },
        Err(_) => scan::run(src, &fallback),
    }
} //打码

/// 当前规则的规模（键名数, 前缀数），自检与日志用
pub fn shape() -> (usize, usize) {
    match HELD.lock() {
        Ok(g) => match g.as_ref() {
            Some(r) => (r.keys.len(), r.prefixes.len()),
            None => {
                let b = rules::Rules::builtin();
                (b.keys.len(), b.prefixes.len())
            }
        },
        Err(_) => (0, 0),
    }
} //规则规模

#[cfg(test)]
mod tests {
    use super::*;

    ///全局规则是进程级单例，测试必须串行跑，所以三件事挤在同一个用例里
    #[test]
    fn state_flow() {
        //1)没载入过规则时用内置兜底，不能直接透传原文
        let out = apply(b"password: hunter2");
        let s = String::from_utf8_lossy(&out).to_string();
        assert!(!s.contains("hunter2"), "{}", s);
        assert!(s.contains("password"), "{}", s);

        //2)普通中文文本原样留下
        let plain = "今天天气不错，去写点代码。";
        assert_eq!(String::from_utf8_lossy(&apply(plain.as_bytes())).to_string(), plain);

        //3)载入服务端规则后按新规则走
        assert!(load(b"keys=mysecret\nmask=#\n"));
        let hit = String::from_utf8_lossy(&apply(b"mysecret=abcdefgh")).to_string();
        assert!(hit.contains("######"), "{}", hit);
    }
}