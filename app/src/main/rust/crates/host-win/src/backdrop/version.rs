//! 系统版本探测。
//!
//! `GetVersionEx` 从 Win8.1 起会因为 manifest 里的兼容性声明撒谎，不能用来判断
//! 功能可用性。`RtlGetVersion` 是内核自己报的，永远是真的。
//!
//! 这里只回答一个问题：**这台机器支持哪一档毛玻璃**。答案决定 [`super::apply`]
//! 走哪条路，也决定前端拿到的不透明度建议。

/// 一个 Windows 版本。
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub struct Version {
    pub major: u32,
    pub minor: u32,
    pub build: u32,
}

impl Version {
    /// Win11 21H2。
    pub fn is_win11(&self) -> bool {
        self.build >= 22000
    }

    /// Win11 22H2（22621）起才有 `DWMWA_SYSTEMBACKDROP_TYPE`。
    pub fn has_system_backdrop(&self) -> bool {
        self.build >= 22621
    }

    /// Win10 1803（17134）起有 acrylic 的 accent policy。
    pub fn has_accent_acrylic(&self) -> bool {
        self.build >= 17134
    }

    /// 文字版的系统名，进日志与"关于"页。
    pub fn name(&self) -> String {
        match (self.major, self.minor, self.build) {
            (10, 0, b) if b >= 22000 => format!("Windows 11 (build {b})"),
            (10, 0, b) => format!("Windows 10 (build {b})"),
            (6, 3, _) => "Windows 8.1".into(),
            (6, 2, _) => "Windows 8".into(),
            (6, 1, _) => "Windows 7".into(),
            (a, b, c) => format!("Windows {a}.{b} (build {c})"),
        }
    }
}

#[repr(C)]
struct OsVersionInfoW {
    size: u32,
    major: u32,
    minor: u32,
    build: u32,
    platform: u32,
    csd: [u16; 128],
}

#[link(name = "ntdll")]
extern "system" {
    fn RtlGetVersion(info: *mut OsVersionInfoW) -> i32;
}

/// 当前系统版本。拿不到就按 Win10 处理——降级到 acrylic 至少还能看。
pub fn current() -> Version {
    let mut info = OsVersionInfoW {
        size: std::mem::size_of::<OsVersionInfoW>() as u32,
        major: 0,
        minor: 0,
        build: 0,
        platform: 0,
        csd: [0; 128],
    };
    // NTSTATUS 0 = STATUS_SUCCESS
    let st = unsafe { RtlGetVersion(&mut info) };
    if st != 0 || info.major == 0 {
        return Version { major: 10, minor: 0, build: 19045 };
    }
    Version { major: info.major, minor: info.minor, build: info.build }
}

/// 系统是否是深色主题。
///
/// 读的是"个性化 → 颜色 → 默认应用模式"，不是应用自己记的那个。用户改了系统
/// 主题，下一次 `WM_SETTINGCHANGE` 就会同步过来（见 wndproc）。
pub fn system_dark() -> bool {
    use windows::Win32::System::Registry::{
        RegCloseKey, RegOpenKeyExW, RegQueryValueExW, HKEY, HKEY_CURRENT_USER, KEY_READ, REG_DWORD,
    };

    unsafe {
        let mut key = HKEY::default();
        let path = windows::core::w!("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize");
        if RegOpenKeyExW(HKEY_CURRENT_USER, path, Some(0), KEY_READ, &mut key).is_err() {
            return false;
        }
        let mut ty = REG_DWORD;
        let mut data = [0u8; 4];
        let mut cb = 4u32;
        let name = windows::core::w!("AppsUseLightTheme");
        let ok = RegQueryValueExW(key, name, None, Some(&mut ty), Some(data.as_mut_ptr()), Some(&mut cb)).is_ok();
        let _ = RegCloseKey(key);
        if !ok {
            return false;
        }
        // 0 = 深色，1 = 浅色
        u32::from_le_bytes(data) == 0
    }
}
