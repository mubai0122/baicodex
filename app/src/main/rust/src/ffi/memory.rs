/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:30
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 记忆的 C ABI：规范化、去重指纹、关键词召回、注入拼装、统计。
//!
//! 约定与 `store.rs` 一致：返回 `*mut c_char` 的字符串由调用方用 `bx_free` 释放；
//! JSON 解析失败返回空数组/空串，绝不 panic（panic 跨 FFI 传播是 UB）。

use std::ffi::c_char;
use std::ptr;

use super::{as_str, to_c};
use crate::core::feature::memory as mem;

/// 记忆规范化：去空白、压换行、按字符截断
///
/// # Safety
/// `raw` 必须是合法 C 字符串。
#[no_mangle]
pub unsafe extern "C" fn bx_mem_normalize(raw: *const c_char) -> *mut c_char {
    match as_str(raw) {
        Some(s) => to_c(mem::normalize(s)),
        None => ptr::null_mut(),
    }
} //规范化

/// 去重指纹：忽略大小写、标点与空白差异
///
/// # Safety
/// `raw` 必须是合法 C 字符串。
#[no_mangle]
pub unsafe extern "C" fn bx_mem_dedup(raw: *const c_char) -> *mut c_char {
    match as_str(raw) {
        Some(s) => to_c(mem::dedup_key(s)),
        None => ptr::null_mut(),
    }
} //去重指纹

/// 关键词召回：`items_json` = `[{"id":1,"text":"..","kind":0,"at":123}]`
///
/// 返回 `[{"id":1,"score":0.83}]`；解析失败返回 `[]`。
///
/// # Safety
/// 两个入参必须是合法 C 字符串。
#[no_mangle]
pub unsafe extern "C" fn bx_mem_rank(
    items_json: *const c_char,
    query: *const c_char,
    now_ms: i64,
    limit: i32,
) -> *mut c_char {
    let items = match as_str(items_json).and_then(parse_items) {
        Some(v) => v,
        None => return to_c("[]".into()),
    };
    let q = as_str(query).unwrap_or("");
    let hits = mem::rank(&items, q, now_ms, limit.max(1) as usize);
    to_c(serde_json::to_string(&hits).unwrap_or_else(|_| "[]".into()))
} //关键词召回

/// 拼装注入文本：`mode` 取 `full` / `smart`，`limit` 为 0 表示不限条数
///
/// # Safety
/// `items_json` / `mode` / `query` 必须是合法 C 字符串。
#[no_mangle]
pub unsafe extern "C" fn bx_mem_compose(
    items_json: *const c_char,
    mode: *const c_char,
    limit: i32,
    now_ms: i64,
    query: *const c_char,
) -> *mut c_char {
    let items = match as_str(items_json).and_then(parse_items) {
        Some(v) => v,
        None => return to_c(String::new()),
    };
    let m = as_str(mode).unwrap_or("full");
    let q = as_str(query).unwrap_or("");
    to_c(mem::compose(&items, m, limit.max(0) as usize, now_ms, q))
} //拼装注入文本

/// 统计：返回 `{"long":3,"normal":12,"chars":880,"oldestDays":40,"newestDays":0}`
///
/// # Safety
/// `items_json` 必须是合法 C 字符串。
#[no_mangle]
pub unsafe extern "C" fn bx_mem_stats(items_json: *const c_char, now_ms: i64) -> *mut c_char {
    let items = as_str(items_json).and_then(parse_items).unwrap_or_default();
    to_c(mem::stats(&items, now_ms))
} //统计

/// 长期记忆的类型值（宿主建行时用，避免两端写死数字）
#[no_mangle]
pub extern "C" fn bx_mem_kind_long() -> i32 {
    mem::KIND_LONG as i32
} //长期类型

/// 普通记忆的类型值
#[no_mangle]
pub extern "C" fn bx_mem_kind_normal() -> i32 {
    mem::KIND_NORMAL as i32
} //普通类型

/// 解析记忆数组 JSON
fn parse_items(s: &str) -> Option<Vec<mem::Item>> {
    serde_json::from_str::<Vec<mem::Item>>(s.trim()).ok()
} //解析记忆数组