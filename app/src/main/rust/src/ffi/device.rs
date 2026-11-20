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

//! 设备协议的 C ABI：米家报文探测与解析。
//!
//! 与 C++ 侧的 miot_ffi.cpp 同名同签名，宿主不需要关心后端是谁。

use crate::core::feature::miot;

/// 米家探测包：写满 32 字节
///
/// # Safety
/// `out` 必须可写 32 字节。
#[no_mangle]
pub unsafe extern "C" fn bai_miot_probe(virtual_did: u64, out: *mut u8) {
    if out.is_null() {
        return;
    }
    let b = miot::probe(virtual_did);
    std::ptr::copy_nonoverlapping(b.as_ptr(), out, b.len());
} //探测包

/// 报文头里的 did；长度不够返回 -1
///
/// # Safety
/// `p` 必须可读 `len` 字节。
#[no_mangle]
pub unsafe extern "C" fn bai_miot_head_did(p: *const u8, len: usize) -> i64 {
    if p.is_null() || len < 12 {
        return -1;
    }
    miot::head_did(std::slice::from_raw_parts(p, len))
} //取 did

/// 报文头里的设备时间戳；长度不够返回 0
///
/// # Safety
/// `p` 必须可读 `len` 字节。
#[no_mangle]
pub unsafe extern "C" fn bai_miot_head_stamp(p: *const u8, len: usize) -> u32 {
    if p.is_null() || len < 16 {
        return 0;
    }
    miot::head_stamp(std::slice::from_raw_parts(p, len))
} //取时间戳

/// JSON 里取整数：找不到返回 i64::MIN
///
/// # Safety
/// `p` 可读 `len` 字节，`key` 可读 `klen` 字节。
#[no_mangle]
pub unsafe extern "C" fn bai_miot_json_int(p: *const u8, len: usize, key: *const u8, klen: usize) -> i64 {
    if p.is_null() || key.is_null() || klen == 0 {
        return i64::MIN;
    }
    miot::json_int(std::slice::from_raw_parts(p, len), std::slice::from_raw_parts(key, klen))
} //取整数字段