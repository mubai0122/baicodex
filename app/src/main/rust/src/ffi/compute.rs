//! 纯计算的 C ABI：滑杆几何 + 请求重试策略。
//!
//! 这两组函数都是无状态纯函数，宿主每帧调用也不会出错，因此不做缓存。

use std::ffi::c_char;

use super::as_str;
use crate::core::feature::compute::{retry, slider};

//MARK: - 滑杆几何
/// 值 → 比例（0..1）
#[no_mangle]
pub extern "C" fn bx_slider_frac(value: f32, min: f32, max: f32) -> f32 {
    slider::frac(value, min, max)
} //值→比例

/// 比例 → 滑块中心 x（像素）
#[no_mangle]
pub extern "C" fn bx_slider_x(frac: f32, track_w: f32, thumb_w: f32) -> f32 {
    slider::center_x(frac, track_w, thumb_w)
} //比例→中心 x

/// 手指 x → 值（含 step 吸附与边界夹取）
#[no_mangle]
pub extern "C" fn bx_slider_value(
    x: f32,
    track_w: f32,
    thumb_w: f32,
    min: f32,
    max: f32,
    step: f32,
) -> f32 {
    slider::value_at(x, track_w, thumb_w, min, max, step)
} //手指 x→值

//MARK: - 重试策略
/// 失败分类：0 不重试 / 1 网络 / 2 超时 / 3 限流 / 4 服务端
///
/// # Safety
/// `msg` 必须是合法 C 字符串或 NULL。
#[no_mangle]
pub unsafe extern "C" fn bx_retry_classify(http_code: i32, msg: *const c_char) -> i32 {
    retry::classify(http_code, as_str(msg).unwrap_or("")) as i32
} //失败分类

/// 是否还允许重试：返回 1 / 0
#[no_mangle]
pub extern "C" fn bx_retry_allow(attempt: i32, kind: i32) -> i32 {
    if attempt <= 0 || kind <= 0 {
        return 0;
    }
    retry::allow(attempt as u32, kind as u8) as i32
} //是否允许重试

/// 第 attempt 次失败后的等待毫秒
#[no_mangle]
pub extern "C" fn bx_retry_delay(kind: i32, attempt: i32) -> i64 {
    retry::delay_ms(kind.max(0) as u8, attempt.max(1) as u32) as i64
} //退避时长

/// 最多重试次数（不含首次请求）
#[no_mangle]
pub extern "C" fn bx_retry_max() -> i32 {
    retry::MAX_RETRY as i32
} //重试上限