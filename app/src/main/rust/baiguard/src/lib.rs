//! 数据保障的密钥组合层：先用 Rust 接管最小一块，验证交叉编译链路是否通。

/// 逐字节异或：dst = a ^ b，长度相同
///
/// # Safety
/// 三个指针必须指向至少 `len` 字节的可读写区，且互不重叠。
#[no_mangle]
pub unsafe extern "C" fn bai_xor(dst: *mut u8, a: *const u8, b: *const u8, len: usize) {
    if dst.is_null() || a.is_null() || b.is_null() || len == 0 {
        return;
    }
    for i in 0..len {
        *dst.add(i) = *a.add(i) ^ *b.add(i);
    }
} //逐字节异或

/// 常量时间比较：相等返回 1，否则 0，不因为提前返回泄露内容
///
/// # Safety
/// 两个指针必须指向至少 `len` 字节的可读区。
#[no_mangle]
pub unsafe extern "C" fn bai_ct_eq(a: *const u8, b: *const u8, len: usize) -> i32 {
    if a.is_null() || b.is_null() {
        return 0;
    }
    let mut diff: u8 = 0;
    for i in 0..len {
        diff |= *a.add(i) ^ *b.add(i);
    }
    if diff == 0 {
        1
    } else {
        0
    }
} //常量时间比较

/// 自检：确认这层 Rust 真的被链接进来了，返回值是固定口令长度
#[no_mangle]
pub extern "C" fn bai_selftest() -> i32 {
    let a = [1u8, 2, 3, 4];
    let b = [9u8, 8, 7, 6];
    let mut out = [0u8; 4];
    unsafe {
        bai_xor(out.as_mut_ptr(), a.as_ptr(), b.as_ptr(), 4);
    }
    if out != [8u8, 10, 4, 2] {
        return 0;
    }
    let eq = unsafe { bai_ct_eq(a.as_ptr(), a.as_ptr(), 4) };
    let ne = unsafe { bai_ct_eq(a.as_ptr(), b.as_ptr(), 4) };
    if eq == 1 && ne == 0 {
        4
    } else {
        0
    }
} //自检