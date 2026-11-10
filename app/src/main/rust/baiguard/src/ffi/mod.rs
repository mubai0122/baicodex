//! 纯 C ABI 薄层：只做指针与切片互转，业务逻辑一律在 core/ 下
//!
//! 导出符号名与 C++ 兜底实现完全一致，JNI 层不需要区分后端是不是 Rust。

use crate::core::feature::{miot, selfcheck};
use crate::core::security::{crypto, mask};

/// 逐字节异或：dst = a ^ b
///
/// # Safety
/// 三个指针必须指向至少 `len` 字节的可读写区，且互不重叠。
#[no_mangle]
pub unsafe extern "C" fn bai_xor(dst: *mut u8, a: *const u8, b: *const u8, len: usize) {
    if dst.is_null() || a.is_null() || b.is_null() || len == 0 {
        return;
    }
    let out = std::slice::from_raw_parts_mut(dst, len);
    let x = std::slice::from_raw_parts(a, len);
    let y = std::slice::from_raw_parts(b, len);
    crypto::xor(out, x, y);
} //逐字节异或

/// 常量时间比较：相等返回 1，否则 0
///
/// # Safety
/// 两个指针必须指向至少 `len` 字节的可读区。
#[no_mangle]
pub unsafe extern "C" fn bai_ct_eq(a: *const u8, b: *const u8, len: usize) -> i32 {
    if a.is_null() || b.is_null() || len == 0 {
        return 0;
    }
    let x = std::slice::from_raw_parts(a, len);
    let y = std::slice::from_raw_parts(b, len);
    if crypto::ct_eq(x, y) {
        1
    } else {
        0
    }
} //常量时间比较

/// 自检：返回 4 表示这一层 Rust 真的被链接进来了
#[no_mangle]
pub extern "C" fn bai_selftest() -> i32 {
    selfcheck::run()
} //自检

/// 载入脱敏规则：成功返回 1，空文本返回 0（保留上一版规则）
///
/// # Safety
/// `p` 必须指向至少 `len` 字节的可读区。
#[no_mangle]
pub unsafe extern "C" fn bai_mask_rules(p: *const u8, len: usize) -> i32 {
    if p.is_null() || len == 0 {
        return 0;
    }
    let text = std::slice::from_raw_parts(p, len);
    if mask::load(text) {
        1
    } else {
        0
    }
} //载入脱敏规则

/// 文本脱敏：返回写入 dst 的字节数，缓冲不够返回 -1
///
/// # Safety
/// `src` 可读 `slen` 字节，`dst` 可写 `dlen` 字节，两者不重叠。
#[no_mangle]
pub unsafe extern "C" fn bai_mask(src: *const u8, slen: usize, dst: *mut u8, dlen: usize) -> isize {
    if src.is_null() || dst.is_null() {
        return -1;
    }
    let s = std::slice::from_raw_parts(src, slen);
    let out = mask::apply(s);
    if out.len() > dlen {
        return -1;
    }
    std::ptr::copy_nonoverlapping(out.as_ptr(), dst, out.len());
    out.len() as isize
} //文本脱敏

/// 当前规则规模：高 8 位是键名数，低 8 位是前缀数，日志与自检用
#[no_mangle]
pub extern "C" fn bai_mask_shape() -> i32 {
    let (keys, prefixes) = mask::shape();
    ((keys.min(255) as i32) << 8) | prefixes.min(255) as i32
} //规则规模

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