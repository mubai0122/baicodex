/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:28
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 自检：进程起来后确认 Rust 这一层真的在跑，返回值 4 表示全绿（沿用 C++ 侧的口径）
//!
//! 只做纯计算校验，不碰文件与网络，可以随便在启动路径上调。

use crate::core::security::{crypto, mask};

/// 跑一遍全部自检，返回 0 表示有项没过
pub fn run() -> i32 {
    if !xor_ok() || !eq_ok() || !mask_ok() {
        return 0;
    }
    4
} //自检总入口

fn xor_ok() -> bool {
    let a = [1u8, 2, 3, 4];
    let b = [9u8, 8, 7, 6];
    let mut out = [0u8; 4];
    crypto::xor(&mut out, &a, &b);
    out == [8u8, 10, 4, 2]
} //异或

fn eq_ok() -> bool {
    let a = [1u8, 2, 3, 4];
    let b = [9u8, 8, 7, 6];
    crypto::ct_eq(&a, &a) && !crypto::ct_eq(&a, &b)
} //常量时间比较

fn mask_ok() -> bool {
    //普通句子必须原样留下，键值对里的值必须消失
    let hit = mask::apply(b"{\"password\":\"hunter2hunter2\",\"note\":\"hello world\"}");
    let text = String::from_utf8_lossy(&hit).to_string();
    let keep = text.contains("hello world");
    let gone = !text.contains("hunter2");
    //已知前缀形态也必须消失
    let pre = mask::apply(b"key ghp_0123456789abcdefghijklmnop");
    let pre_gone = !String::from_utf8_lossy(&pre).contains("0123456789abcdef");
    keep && gone && pre_gone
} //脱敏

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn everything_green() {
        assert_eq!(run(), 4);
    }
}