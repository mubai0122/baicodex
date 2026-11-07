//! DPI。
//!
//! 全程按"物理像素"思考：Win32 给的坐标是物理像素，CSS 是逻辑像素，两者差一个
//! 缩放比。混合着用最典型的症状是——125% 缩放下，窗口右边和下边各多出一条白边，
//! 或者鼠标拖拽区偏了 20px。
//!
//! 约定：
//!
//! - 窗口尺寸、位置、`WM_NCCALCSIZE` 里的矩形：**物理像素**；
//! - 传给 WebView2 的 `SetBounds`：**物理像素**（WebView2 自己按 DPI 缩放页面）；
//! - 前端 CSS：逻辑像素，靠 `window.devicePixelRatio` 换算。

use windows::Win32::Foundation::{HWND, RECT};
use windows::Win32::UI::HiDpi::{
    GetDpiForWindow, GetSystemMetricsForDpi, SetProcessDpiAwarenessContext,
    DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
};
use windows::Win32::UI::WindowsAndMessaging::{
    SYSTEM_METRICS_INDEX, SM_CXPADDEDBORDER, SM_CXSIZEFRAME, SM_CYSIZEFRAME,
};

use crate::error::Result;

/// 进程级：声明自己是 per-monitor v2。
///
/// 必须在任何窗口创建之前调用。失败通常意味着 manifest 里已经声明过——
/// 那也算成功，不往上抛。
pub fn init_process() {
    unsafe {
        let _ = SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    }
}

/// 这个窗口当前的 DPI（96 = 100%）。
pub fn of(hwnd: HWND) -> u32 {
    unsafe {
        let d = GetDpiForWindow(hwnd);
        if d == 0 {
            96
        } else {
            d
        }
    }
}

/// 缩放比，1.0 = 100%。
pub fn scale(hwnd: HWND) -> f32 {
    of(hwnd) as f32 / 96.0
}

/// 逻辑像素 → 物理像素。
pub fn px(hwnd: HWND, logical: i32) -> i32 {
    (logical as f32 * scale(hwnd)).round() as i32
}

/// 可缩放边框的厚度（含 padding）。无边框窗口拿它当拖拽热区。
pub fn resize_border(hwnd: HWND) -> i32 {
    let dpi = of(hwnd);
    unsafe {
        let frame = GetSystemMetricsForDpi(SM_CXSIZEFRAME, dpi);
        let pad = GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi);
        (frame + pad).max(4)
    }
}

/// 纵向边框厚度。窗口最大化时上下也要收这么多，否则会盖住任务栏。
pub fn resize_border_y(hwnd: HWND) -> i32 {
    let dpi = of(hwnd);
    unsafe {
        let frame = GetSystemMetricsForDpi(SM_CYSIZEFRAME, dpi);
        let pad = GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi);
        (frame + pad).max(4)
    }
}

/// 某个系统度量在当前 DPI 下的值。
pub fn metric(hwnd: HWND, idx: SYSTEM_METRICS_INDEX) -> i32 {
    unsafe { GetSystemMetricsForDpi(idx, of(hwnd)) }
}

/// 客户区尺寸。
pub fn client_size(hwnd: HWND) -> RECT {
    let mut r = RECT::default();
    unsafe {
        let _ = windows::Win32::UI::WindowsAndMessaging::GetClientRect(hwnd, &mut r);
    }
    r
}

/// 结果不使用时也想要的版本。
pub fn client_size_checked(hwnd: HWND) -> Result<RECT> {
    let mut r = RECT::default();
    unsafe { windows::Win32::UI::WindowsAndMessaging::GetClientRect(hwnd, &mut r)? };
    Ok(r)
}
