//! Win10 的亚克力。
//!
//! `SetWindowCompositionAttribute` 从来没有正式文档，但从 1803 起一直在，直到
//! Win11 22H2 才被 `DWMWA_SYSTEMBACKDROP_TYPE` 取代。这里动态取地址，取不到就
//! 老实降级成纯色——比在启动时崩掉强。
//!
//! ## 颜色是 ABGR 不是 ARGB
//!
//! 这是这套 API 最容易踩的坑。传 `0xCC1E1E1E` 期望深灰，屏幕上会变成蓝色。
//! 顺序是 `AA BB GG RR`。

use windows::core::{s, PCSTR};
use windows::Win32::Foundation::HWND;
use windows::Win32::System::LibraryLoader::{GetModuleHandleW, GetProcAddress};

/// 亚克力档。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Accent {
    /// 关掉。
    Disabled,
    /// 高斯模糊，最轻。
    Blur,
    /// 亚克力：模糊 + 噪点 + 染色。
    Acrylic,
}

impl Accent {
    fn code(self) -> i32 {
        match self {
            Accent::Disabled => 0,
            Accent::Blur => 3,
            Accent::Acrylic => 4,
        }
    }
}

#[repr(C)]
struct AccentPolicy {
    state: i32,
    flags: i32,
    /// ABGR
    color: u32,
    animation_id: i32,
}

#[repr(C)]
struct CompositionAttribData {
    attr: i32,
    data: *mut core::ffi::c_void,
    size: usize,
}

const WCA_ACCENT_POLICY: i32 = 19;

type SetWindowCompositionAttributeFn = unsafe extern "system" fn(HWND, *mut CompositionAttribData) -> i32;

/// 函数地址。整个进程只查一次。
fn proc_addr() -> Option<SetWindowCompositionAttributeFn> {
    use std::sync::OnceLock;
    static ADDR: OnceLock<Option<usize>> = OnceLock::new();
    let raw = *ADDR.get_or_init(|| unsafe {
        let module = GetModuleHandleW(windows::core::w!("user32.dll")).ok()?;
        let f = GetProcAddress(module, s!("SetWindowCompositionAttribute"))?;
        Some(f as usize)
    });
    raw.map(|p| unsafe { std::mem::transmute::<usize, SetWindowCompositionAttributeFn>(p) })
}

/// 这台机器能不能用亚克力。
pub fn available() -> bool {
    proc_addr().is_some()
}

/// 设置亚克力。`tint` 是 `0xRRGGBB`，`alpha` 是 0-255 的不透明度。
///
/// 注意 alpha 越低越透明，但低于 ~0x40 时文字会看不清——背景是桌面壁纸，
/// 什么颜色都有。界面上建议 0xB0 起步。
pub fn set(hwnd: HWND, accent: Accent, tint: u32, alpha: u8) -> bool {
    let Some(f) = proc_addr() else { return false };

    let r = (tint & 0x0000_00FF) as u32;
    let g = (tint & 0x0000_FF00) as u32;
    let b = (tint & 0x00FF_0000) as u32;
    let color = ((alpha as u32) << 24) | (b << 16) | (g << 8) | r;

    let mut policy = AccentPolicy {
        state: accent.code(),
        flags: 0,
        color,
        animation_id: 0,
    };
    let mut data = CompositionAttribData {
        attr: WCA_ACCENT_POLICY,
        data: &mut policy as *mut AccentPolicy as *mut core::ffi::c_void,
        size: std::mem::size_of::<AccentPolicy>(),
    };
    unsafe { f(hwnd, &mut data) != 0 }
}

/// 关掉。切到 Mica 之前要先清，否则两层叠加会发灰。
pub fn clear(hwnd: HWND) {
    let _ = set(hwnd, Accent::Disabled, 0, 0);
}

// 让 `PCSTR` 的引入有实际用途（s! 宏返回的就是它），同时避免 unused 警告。
const _: PCSTR = PCSTR::null();
