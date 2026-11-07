//! Win11 的 DWM 合成属性。
//!
//! 这些是**文档化**的接口（`DWMWA_SYSTEMBACKDROP_TYPE` 从 22621 起公开），失败
//! 只会返回 HRESULT，不会崩。所以每个调用都要单独判，不能一个失败就整体放弃：
//! 老系统上"圆角"可能支持而"backdrop"不支持，能拿一个是一个。
//!
//! 属性编号是 ABI 的一部分，写死在这里而不是引 crate 常量——crate 换版本时这些
//! 编号不会变，但类型名会。

use windows::Win32::Foundation::HWND;
use windows::Win32::Graphics::Dwm::{
    DwmExtendFrameIntoClientArea, DwmSetWindowAttribute, DWMWINDOWATTRIBUTE,
};
// MARGINS 在 Controls 里，不在 Dwm 里——这是 windows-rs 的模块划分，别问为什么。
use windows::Win32::UI::Controls::MARGINS;

/// 深色标题栏 / 深色系统背景。
pub const ATTR_DARK: i32 = 20;
/// 窗口圆角偏好。
pub const ATTR_CORNER: i32 = 33;
/// 窗口边框颜色。
pub const ATTR_BORDER: i32 = 34;
/// 系统背景材质。
pub const ATTR_BACKDROP: i32 = 38;

/// 不画边框。`DWMWA_COLOR_NONE`
pub const COLOR_NONE: u32 = 0xFFFF_FFFE;

/// 系统背景材质。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Material {
    /// 不画。窗口自己负责背景。
    None,
    /// 桌面壁纸的模糊采样，最省电，静态。
    Mica,
    /// 亚克力，采样窗口后面的内容，有轻微动态。
    Acrylic,
    /// Mica 的标签页变体，对比度更低。
    MicaAlt,
}

impl Material {
    fn code(self) -> i32 {
        match self {
            Material::None => 1,
            Material::Mica => 2,
            Material::Acrylic => 3,
            Material::MicaAlt => 4,
        }
    }

    /// 给前端的名字。前端据此决定玻璃底色要多透明。
    pub fn name(self) -> &'static str {
        match self {
            Material::None => "none",
            Material::Mica => "mica",
            Material::Acrylic => "acrylic",
            Material::MicaAlt => "mica-alt",
        }
    }
}

/// 圆角档位。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Corner {
    Default,
    DoNotRound,
    /// 8px，系统标准。
    Round,
    /// 4px。
    RoundSmall,
}

impl Corner {
    fn code(self) -> i32 {
        match self {
            Corner::Default => 0,
            Corner::DoNotRound => 1,
            Corner::Round => 2,
            Corner::RoundSmall => 3,
        }
    }
}

/// 让 DWM 接管整个客户区，从而拿到系统阴影和圆角。
///
/// `-1` 表示"整个窗口都是玻璃"。对无边框窗口来说这是必需的：不给的话，
/// 窗口没有阴影，看起来像贴在桌面上的一张纸。
pub fn extend_frame(hwnd: HWND) {
    let m = MARGINS { cxLeftWidth: -1, cyTopHeight: -1, cxRightWidth: -1, cyBottomHeight: -1 };
    unsafe {
        let _ = DwmExtendFrameIntoClientArea(hwnd, &m);
    }
}

/// 设置系统背景材质。返回 false 表示这个系统不支持（调用方降级）。
pub fn set_material(hwnd: HWND, m: Material) -> bool {
    let v = m.code();
    unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWINDOWATTRIBUTE(ATTR_BACKDROP),
            &v as *const i32 as *const core::ffi::c_void,
            std::mem::size_of::<i32>() as u32,
        )
        .is_ok()
    }
}

/// 深色 / 浅色。失败不算错——老系统没这个属性，界面自己会画深色。
pub fn set_dark(hwnd: HWND, dark: bool) -> bool {
    let v: i32 = if dark { 1 } else { 0 };
    unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWINDOWATTRIBUTE(ATTR_DARK),
            &v as *const i32 as *const core::ffi::c_void,
            std::mem::size_of::<i32>() as u32,
        )
        .is_ok()
    }
}

/// 圆角。
pub fn set_corner(hwnd: HWND, c: Corner) -> bool {
    let v = c.code();
    unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWINDOWATTRIBUTE(ATTR_CORNER),
            &v as *const i32 as *const core::ffi::c_void,
            std::mem::size_of::<i32>() as u32,
        )
        .is_ok()
    }
}

/// 边框颜色。传 [`COLOR_NONE`] 去掉系统画的那一圈 1px 亮线。
pub fn set_border_color(hwnd: HWND, color: u32) -> bool {
    unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWINDOWATTRIBUTE(ATTR_BORDER),
            &color as *const u32 as *const core::ffi::c_void,
            std::mem::size_of::<u32>() as u32,
        )
        .is_ok()
    }
}
