//! 托盘图标。
//!
//! 关窗口时退到托盘，是桌面应用的默认预期——用户按 × 往往只是想腾出屏幕，不是
//! 想杀掉正在跑的对话。
//!
//! 图标用系统的 `IDI_APPLICATION`。真要做品牌图标，把 `.ico` 编进资源再
//! `LoadImageW` 取——这里的接口不用改，换 [`load_icon`] 就行。

use windows::core::{w, PCWSTR};
use windows::Win32::Foundation::{HWND, LPARAM, POINT, WPARAM};
use windows::Win32::UI::Shell::{
    Shell_NotifyIconW, NIF_ICON, NIF_MESSAGE, NIF_TIP, NIM_ADD, NIM_DELETE, NOTIFYICONDATAW,
};
use windows::Win32::UI::WindowsAndMessaging::{
    AppendMenuW, CreatePopupMenu, DestroyMenu, GetCursorPos, LoadIconW, SetForegroundWindow,
    TrackPopupMenu, IDI_APPLICATION, MF_SEPARATOR, MF_STRING, SW_HIDE, SW_SHOW, TPM_BOTTOMALIGN,
    TPM_LEFTALIGN, WM_APP, WM_COMMAND, WM_LBUTTONUP, WM_RBUTTONUP,
};

use crate::error::{HostError, Result};

/// 托盘回调消息号。
pub const WM_APP_TRAY: u32 = WM_APP + 0x31;

const CMD_SHOW: usize = 0x9101;
const CMD_QUIT: usize = 0x9102;

pub struct Tray {
    hwnd: HWND,
    icon_id: u32,
    added: bool,
}

impl std::fmt::Debug for Tray {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Tray").field("added", &self.added).finish()
    }
}

impl Tray {
    pub fn create(hwnd: HWND, tip: &str) -> Result<Self> {
        let mut t = Tray { hwnd, icon_id: 1, added: false };
        let mut nid = NOTIFYICONDATAW {
            cbSize: std::mem::size_of::<NOTIFYICONDATAW>() as u32,
            hWnd: hwnd,
            uID: t.icon_id,
            uFlags: NIF_MESSAGE | NIF_ICON | NIF_TIP,
            uCallbackMessage: WM_APP_TRAY,
            hIcon: load_icon(),
            ..Default::default()
        };
        write_tip(&mut nid.szTip, tip);
        let ok = unsafe { Shell_NotifyIconW(NIM_ADD, &nid) };
        if !ok.as_bool() {
            return Err(HostError::msg("往托盘加图标失败"));
        }
        t.added = true;
        Ok(t)
    }

    /// 托盘事件。返回 true 表示已处理。
    pub fn handle(&self, msg: u32, wp: WPARAM, lp: LPARAM) -> bool {
        if msg == WM_APP_TRAY {
            // 低字是鼠标事件
            match (lp.0 as u32) & 0xFFFF {
                x if x == WM_LBUTTONUP => {
                    toggle(self.hwnd);
                    true
                }
                x if x == WM_RBUTTONUP => {
                    let _ = popup(self.hwnd);
                    true
                }
                _ => false,
            }
        } else if msg == WM_COMMAND && wp.0 & 0xFFFF == CMD_SHOW {
            toggle(self.hwnd);
            true
        } else if msg == WM_COMMAND && wp.0 & 0xFFFF == CMD_QUIT {
            unsafe {
                let _ = windows::Win32::UI::WindowsAndMessaging::DestroyWindow(self.hwnd);
            }
            true
        } else {
            false
        }
    }

    pub fn remove(&self) {
        if !self.added {
            return;
        }
        let nid = NOTIFYICONDATAW {
            cbSize: std::mem::size_of::<NOTIFYICONDATAW>() as u32,
            hWnd: self.hwnd,
            uID: self.icon_id,
            ..Default::default()
        };
        unsafe {
            let _ = Shell_NotifyIconW(NIM_DELETE, &nid);
        }
    }
}

impl Drop for Tray {
    fn drop(&mut self) {
        self.remove();
    }
}


fn load_icon() -> windows::Win32::UI::WindowsAndMessaging::HICON {
    unsafe { LoadIconW(None, IDI_APPLICATION).unwrap_or_default() }
}

fn toggle(hwnd: HWND) {
    use windows::Win32::UI::WindowsAndMessaging::IsWindowVisible;
    unsafe {
        if IsWindowVisible(hwnd).as_bool() {
            let _ = windows::Win32::UI::WindowsAndMessaging::ShowWindow(hwnd, SW_HIDE);
        } else {
            let _ = windows::Win32::UI::WindowsAndMessaging::ShowWindow(hwnd, SW_SHOW);
            let _ = SetForegroundWindow(hwnd);
        }
    }
}

fn popup(hwnd: HWND) -> Result<()> {
    unsafe {
        let menu = CreatePopupMenu()?;
        let _ = AppendMenuW(menu, MF_STRING, CMD_SHOW, w!("显示 / 隐藏"));
        let _ = AppendMenuW(menu, MF_SEPARATOR, 0, PCWSTR::null());
        let _ = AppendMenuW(menu, MF_STRING, CMD_QUIT, w!("退出"));

        let mut pt = POINT::default();
        let _ = GetCursorPos(&mut pt);
        // 不先抢前台，菜单点外面不会消失
        let _ = SetForegroundWindow(hwnd);
        let _ = TrackPopupMenu(menu, TPM_LEFTALIGN | TPM_BOTTOMALIGN, pt.x, pt.y, Some(0), hwnd, None);
        let _ = DestroyMenu(menu);
    }
    Ok(())
}

/// `szTip` 是 `[u16; 128]`，要自己按 UTF-16 抄进去并以 0 结尾。
fn write_tip(dst: &mut [u16], s: &str) {
    let n = dst.len().saturating_sub(1).min(s.len());
    let mut i = 0;
    for c in s.chars() {
        if i >= n {
            break;
        }
        let mut buf = [0u16; 2];
        for u in c.encode_utf16(&mut buf) {
            if i >= n {
                break;
            }
            dst[i] = *u;
            i += 1;
        }
    }
    dst[i] = 0;
}
