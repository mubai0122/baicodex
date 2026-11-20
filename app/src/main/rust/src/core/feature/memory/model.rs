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

//! 记忆的数据结构与文本处理：一条记忆长什么样、怎么规范化、怎么切词。
//!
//! 这里只有「纯文本变换」，不涉及排序与注入。

use serde::{Deserialize, Serialize};
use std::collections::HashSet;

/// 长期记忆
pub const KIND_LONG: u8 = 0;
/// 普通记忆
pub const KIND_NORMAL: u8 = 1;
/// 单条记忆的最大字符数（超出按字符截断，不切断 UTF-8）
pub const MAX_CHARS: usize = 500;

/// 一条记忆（宿主与 Rust 之间的传输结构，字段名即 JSON 键）
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct Item {
    /// 数据库主键
    pub id: i64,
    /// 原文
    pub text: String,
    /// 0 = 长期，1 = 普通
    #[serde(default)]
    pub kind: u8,
    /// 创建时间（毫秒）
    #[serde(default)]
    pub at: i64,
} //一条记忆

/// 单条召回结果
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
pub struct Hit {
    /// 记忆主键
    pub id: i64,
    /// 相关度分数（越大越相关）
    pub score: f64,
} //召回结果

/// 规范化：去首尾空白、换行与制表符压成一个空格、连续空格合并、按字符截断
pub fn normalize(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    let mut prev_space = false;
    for c in raw.chars() {
        if c.is_whitespace() {
            //行内空白一律归一，避免一条记忆在界面上占三行
            if !prev_space && !out.is_empty() {
                out.push(' ');
            }
            prev_space = true;
        } else {
            out.push(c);
            prev_space = false;
        }
    }
    let trimmed = out.trim();
    if trimmed.chars().count() <= MAX_CHARS {
        trimmed.to_string()
    } else {
        trimmed.chars().take(MAX_CHARS).collect::<String>()
    }
} //规范化

/// 去重指纹：小写 + 只留字母数字汉字，忽略标点与空格差异
pub fn dedup_key(text: &str) -> String {
    text.chars()
        .filter(|c| c.is_alphanumeric())
        .flat_map(|c| c.to_lowercase())
        .collect()
} //去重指纹

/// 切词元：拉丁数字按词（长度 ≥2），中文按字 bigram
///
/// 不停在分词上是有意的：记忆条数很少（百级），bigram 召回够用且零依赖。
pub fn tokens(s: &str) -> HashSet<String> {
    let lower = s.to_lowercase();
    let mut out = HashSet::new();
    let mut word = String::new();
    for c in lower.chars() {
        if c.is_ascii_alphanumeric() || c == '_' {
            word.push(c);
        } else {
            if word.chars().count() >= 2 {
                out.insert(std::mem::take(&mut word));
            } else {
                word.clear();
            }
        }
    }
    if word.chars().count() >= 2 {
        out.insert(word);
    }
    //汉字独立处理：一个汉字也算词元，连字再补 bigram
    let chars: Vec<char> = lower.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        if is_cjk(chars[i]) {
            let start = i;
            while i < chars.len() && is_cjk(chars[i]) {
                i += 1;
            }
            let run = &chars[start..i];
            if run.len() == 1 {
                out.insert(run[0].to_string());
            } else {
                for k in 0..run.len() - 1 {
                    out.insert(run[k..k + 2].iter().collect());
                }
            }
        } else {
            i += 1;
        }
    }
    out
} //切词元

fn is_cjk(c: char) -> bool {
    matches!(c as u32, 0x3400..=0x4DBF | 0x4E00..=0x9FFF)
} //汉字区间
