//! 无边框窗口的几何：去掉标题栏、最大化时不盖任务栏、限制最小尺寸。
//!
//! ## 核心手法
//!
//! `WM_NCCALCSIZE` 返回 0，等于告诉系统"客户区就是整个窗口矩形"。标题栏因此
//! 消失，但窗口样式里仍然保留 `WS_THICKFRAME | WS_CAPTION | WS_SYSMENU`——
//! 这是必须的：去掉这些样式，DWM 就不再给窗口阴影、圆角、Aero Snap 和动画，
//! 窗口会变成一块贴纸。
//!
//! 保留样式的代价是最大化时窗口会撑到屏幕边缘、盖住任务栏。所以要在
//! `WM_NCCALCSIZE` 里把矩形往里收一个边框厚度。

use windows::Win32::Foundation::{HWND, LPARAM, RECT, WPARAM};
use windows::Win32::UI::WindowsAndMessaging::{
    GetWindowPlacement, GetWindowRect, IsIconic, IsWindowVisible, IsZoomed, MoveWindow,
    SetWindowPos, ShowWindow, NCCALCSIZE_PARAMS, SWP_NOACTIVATE, SWP_NOZORDER, SW_MAXIMIZE,
    SW_RESTORE, WINDOWPLACEMENT,
};

use crate::error::Result;
use crate::win::dpi;

/// 处理 `WM_NCCALCSIZE`。
///
/// 返回 `Some(lresult)` 表示已处理；`None` 交回默认流程。
pub fn nc_calc_size(hwnd: HWND, wparam: WPARAM, lparam: LPARAM) -> Option<isize> {
    // wParam == FALSE 时 lParam 是 RECT，系统只问"客户区多大"，这时不能改。
    if wparam.0 == 0 {
        return None;
    }

    let maximized = unsafe { IsZoomed(hwnd).as_bool() };
    if !maximized {
        // 非最大化：客户区 = 窗口矩形，标题栏消失。
        return Some(0);
    }

    // 最大化：收掉一圈边框，别盖住任务栏。
    let params = unsafe { &mut *(lparam.0 as *mut NCCALCSIZE_PARAMS) };
    let bx = dpi::resize_border(hwnd);
    let by = dpi::resize_border_y(hwnd);
    let r = &mut params.rgrc[0];
    r.left += bx;
    r.top += by;
    r.right -= bx;
    r.bottom -= by;
    Some(0)
}

/// `WM_GETMINMAXINFO`：最小尺寸按 DPI 换算。
pub fn min_max_info(hwnd: HWND, lparam: LPARAM) {
    use windows::Win32::UI::WindowsAndMessaging::MINMAXINFO;
    let mmi = unsafe { &mut *(lparam.0 as *mut MINMAXINFO) };
    // 再小的话侧栏和输入框会打架。数字是逻辑像素。
    mmi.ptMinTrackSize.x = dpi::px(hwnd, 760);
    mmi.ptMinTrackSize.y = dpi::px(hwnd, 480);
}

/// `WM_NCHITTEST`：非客户区保留给系统（边框、缩放角）。
///
/// WebView2 铺满客户区，正常鼠标事件到不了这里；只有最大化收边后露出的那几像素
/// 会进来。
pub fn nc_hit_test(hwnd: HWND, lparam: LPARAM) -> Option<isize> {
    const HTCLIENT: isize = 1;
    const HTLEFT: isize = 10;
    const HTRIGHT: isize = 11;
    const HTTOP: isize = 12;
    const HTTOPLEFT: isize = 13;
    const HTTOPRIGHT: isize = 14;
    const HTBOTTOM: isize = 15;
    const HTBOTTOMLEFT: isize = 16;
    const HTBOTTOMRIGHT: isize = 17;

    let x = (lparam.0 & 0xFFFF) as i16 as i32;
    let y = ((lparam.0 >> 16) & 0xFFFF) as i16 as i32;

    let mut wr = RECT::default();
    if unsafe { GetWindowRect(hwnd, &mut wr) }.is_err() {
        return None;
    }
    let b = dpi::resize_border(hwnd);
    let maximized = unsafe { IsZoomed(hwnd).as_bool() };
    // 最大化时上边缘不参与缩放，否则会跟"向下拖还原"打架
    let top_zone = if maximized { 0 } else { b };

    let left = x < wr.left + b;
    let right = x >= wr.right - b;
    let top = top_zone > 0 && y < wr.top + top_zone;
    let bottom = y >= wr.bottom - b;

    let code = match (left, right, top, bottom) {
        (true, _, true, _) => HTTOPLEFT,
        (_, true, true, _) => HTTOPRIGHT,
        (true, _, _, true) => HTBOTTOMLEFT,
        (_, true, _, true) => HTBOTTOMRIGHT,
        (true, _, _, _) => HTLEFT,
        (_, true, _, _) => HTRIGHT,
        (_, _, true, _) => HTTOP,
        (_, _, _, true) => HTBOTTOM,
        _ => return Some(HTCLIENT),
    };
    Some(code)
}

/// 最大化 / 还原。
pub fn toggle_maximize(hwnd: HWND) {
    unsafe {
        if IsZoomed(hwnd).as_bool() {
            let _ = ShowWindow(hwnd, SW_RESTORE);
        } else {
            let _ = ShowWindow(hwnd, SW_MAXIMIZE);
        }
    }
    crate::backdrop::reassert_corner(hwnd, unsafe { IsZoomed(hwnd).as_bool() });
}

/// 相对移动窗口。给自定义拖拽手势用。
pub fn move_by(hwnd: HWND, dx: i32, dy: i32) -> Result<()> {
    let mut r = RECT::default();
    unsafe {
        GetWindowRect(hwnd, &mut r)?;
        MoveWindow(hwnd, r.left + dx, r.top + dy, r.right - r.left, r.bottom - r.top, true)?;
    }
    Ok(())
}

/// 窗口几何与状态，前端用来画最大化图标、判断贴边。
pub fn window_state(hwnd: HWND) -> serde_json::Value {
    use serde_json::json;

    let mut r = RECT::default();
    let _ = unsafe { GetWindowRect(hwnd, &mut r) };
    let mut wp = WINDOWPLACEMENT { length: std::mem::size_of::<WINDOWPLACEMENT>() as u32, ..Default::default() };
    let _ = unsafe { GetWindowPlacement(hwnd, &mut wp) };

    json!({
        "maximized": unsafe { IsZoomed(hwnd).as_bool() },
        "minimized": unsafe { IsIconic(hwnd).as_bool() },
        "visible": unsafe { IsWindowVisible(hwnd).as_bool() },
        "x": r.left,
        "y": r.top,
        "width": r.right - r.left,
        "height": r.bottom - r.top,
        "dpi": dpi::scale(hwnd),
        "dpiRaw": dpi::of(hwnd),
        "shownCmd": wp.showCmd,
    })
}

/// 把窗口居中到主显示器工作区。首次启动用。
pub fn center_on_screen(hwnd: HWND, w: i32, h: i32) {
    use windows::Win32::Graphics::Gdi::{GetMonitorInfoW, MonitorFromWindow, MONITORINFO, MONITOR_DEFAULTTONEAREST};
    unsafe {
        let mon = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
        if mon.is_invalid() {
            return;
        }
        let mut mi = MONITORINFO { cbSize: std::mem::size_of::<MONITORINFO>() as u32, ..Default::default() };
        if !GetMonitorInfoW(mon, &mut mi).as_bool() {
            return;
        }
        let work = mi.rcWork;
        let x = work.left + ((work.right - work.left) - w) / 2;
        let y = work.top + ((work.bottom - work.top) - h) / 2;
        let _ = SetWindowPos(hwnd, None, x, y, w, h, SWP_NOZORDER | SWP_NOACTIVATE);
    }
}

/// 客户区矩形指针的便捷包装（给别处判断鼠标是否在窗口里用）。
pub fn rect_of(hwnd: HWND) -> RECT {
    let mut r = RECT::default();
    let _ = unsafe { GetWindowRect(hwnd, &mut r) };
    r
}
