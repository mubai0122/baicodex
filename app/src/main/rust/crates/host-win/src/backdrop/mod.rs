//! 窗口背景：从系统毛玻璃到纯色，一条降级链。
//!
//! 名字叫 backdrop 而不是 glass，是因为这一层只负责**窗口底板**——桌面壁纸透过
//! 窗口的那层。控件自己的液态玻璃（折射、高光、形变）在 `@baicodex/liquid` 里，
//! 由浏览器合成。两层叠起来才是完整的视觉效果：
//!
//! ```text
//! ┌─ 桌面壁纸 ────────────────────────────────┐
//! │  DWM：Mica / Acrylic（本模块）            │
//! │  ┌─ WebView2（透明）──────────────────┐   │
//! │  │  CSS：背景渐变 / 极光                │   │
//! │  │  ┌─ lg-surface ────────────────┐    │   │
//! │  │  │  backdrop-filter 模糊 + 折射 │    │   │
//! │  │  └─────────────────────────────┘    │   │
//! │  └────────────────────────────────────┘   │
//! └───────────────────────────────────────────┘
//! ```
//!
//! 降级链：`Mica → Acrylic(DWM) → Mica(旧属性) → Acrylic(accent) → Blur → Solid`。

pub mod accent;
pub mod dwm;
pub mod version;

use windows::Win32::Foundation::HWND;

/// 实际生效的背景类型。会原样告诉前端，前端据此调玻璃的底色浓度。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    /// Win11 22H2+ 的 Mica。
    Mica,
    /// Mica 的标签页变体。
    MicaAlt,
    /// DWM 或 accent 的亚克力。
    Acrylic,
    /// 只有模糊，没有染色。
    Blur,
    /// 什么都不支持，纯色。
    Solid,
}

impl Kind {
    pub fn name(self) -> &'static str {
        match self {
            Kind::Mica => "mica",
            Kind::MicaAlt => "mica-alt",
            Kind::Acrylic => "acrylic",
            Kind::Blur => "blur",
            Kind::Solid => "solid",
        }
    }

    /// 前端该用的玻璃底色不透明度（0-1）。
    ///
    /// 系统底板越通透，界面自己的底色就要越实，否则文字压不住壁纸。
    pub fn tint_alpha(self) -> f32 {
        match self {
            Kind::Mica => 0.30,
            Kind::MicaAlt => 0.38,
            Kind::Acrylic => 0.55,
            Kind::Blur => 0.68,
            Kind::Solid => 0.92,
        }
    }
}

/// 一次应用之后的现场。
#[derive(Debug, Clone)]
pub struct State {
    pub kind: Kind,
    pub dark: bool,
    pub version: version::Version,
    /// 系统是否接受了大圆角。
    pub rounded: bool,
}

/// 把窗口底板铺好。启动时调一次。
pub fn apply(hwnd: HWND, dark: bool) -> State {
    let v = version::current();

    // DWM 的扩展边框给阴影和系统圆角。老系统上失败也无所谓。
    dwm::extend_frame(hwnd);
    dwm::set_dark(hwnd, dark);
    let rounded = dwm::set_corner(hwnd, dwm::Corner::Round);
    // 系统那圈 1px 亮线跟液态玻璃的镜面边冲突，去掉。
    dwm::set_border_color(hwnd, dwm::COLOR_NONE);

    let kind = if v.has_system_backdrop() {
        // 先试 Mica，不行再试 Acrylic（某些虚拟机里 Mica 会被关掉）
        if dwm::set_material(hwnd, dwm::Material::Mica) {
            Kind::Mica
        } else if dwm::set_material(hwnd, dwm::Material::Acrylic) {
            Kind::Acrylic
        } else {
            fallback(hwnd, dark)
        }
    } else if v.is_win11() {
        // 21H2：未文档化的 DWMWA_MICA_EFFECT = 1029
        if mica_legacy(hwnd) {
            Kind::Mica
        } else {
            fallback(hwnd, dark)
        }
    } else {
        fallback(hwnd, dark)
    };

    State { kind, dark, version: v, rounded }
}

/// Win11 21H2 的旧 Mica 开关。
fn mica_legacy(hwnd: HWND) -> bool {
    const DWMWA_MICA_EFFECT: i32 = 1029;
    let v: i32 = 1;
    unsafe {
        windows::Win32::Graphics::Dwm::DwmSetWindowAttribute(
            hwnd,
            windows::Win32::Graphics::Dwm::DWMWINDOWATTRIBUTE(DWMWA_MICA_EFFECT),
            &v as *const i32 as *const core::ffi::c_void,
            4,
        )
        .is_ok()
    }
}

/// Win10 及更早。
fn fallback(hwnd: HWND, dark: bool) -> Kind {
    // accent 的颜色是 ABGR，这里给的是 RRGGBB
    let tint = if dark { 0x1A1A1C } else { 0xF3F3F5 };
    let alpha = if dark { 0xB4 } else { 0xCC };
    if accent::available() && accent::set(hwnd, accent::Accent::Acrylic, tint, alpha) {
        return Kind::Acrylic;
    }
    if accent::available() && accent::set(hwnd, accent::Accent::Blur, tint, alpha) {
        return Kind::Blur;
    }
    Kind::Solid
}

/// 跟随系统深浅色。`WM_SETTINGCHANGE` 里调。
pub fn set_dark(hwnd: HWND, dark: bool) {
    dwm::set_dark(hwnd, dark);
    // accent 的颜色要重设，它不会自己跟着变
    let tint = if dark { 0x1A1A1C } else { 0xF3F3F5 };
    let alpha = if dark { 0xB4 } else { 0xCC };
    let _ = accent::set(hwnd, accent::Accent::Acrylic, tint, alpha);
    // 让 DWM 重画一帧
    unsafe {
        use windows::Win32::Foundation::RECT;
        use windows::Win32::Graphics::Gdi::InvalidateRect;
        let mut r = RECT::default();
        let _ = windows::Win32::UI::WindowsAndMessaging::GetClientRect(hwnd, &mut r);
        let _ = InvalidateRect(Some(hwnd), Some(&r), true);
    }
}

/// 最大化 / 还原时圆角会被系统改掉，切回来。
pub fn reassert_corner(hwnd: HWND, maximized: bool) {
    if maximized {
        dwm::set_corner(hwnd, dwm::Corner::DoNotRound);
    } else {
        dwm::set_corner(hwnd, dwm::Corner::Round);
    }
}
