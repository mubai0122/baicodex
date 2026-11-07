//! 窗口本体：注册类、建窗口、跑消息循环。
//!
//! ## 一个窗口，一个 `Host`
//!
//! 全部状态放在 [`Host`] 里，指针存在 `GWLP_USERDATA`。用裸指针而不是 `Rc`：
//! 窗口过程是 `extern "system"`，跨不过 Rust 的借用检查，任何"安全"包装最后都要
//! `unsafe` 一次，不如把这一次写清楚。
//!
//! ## 创建顺序不能乱
//!
//! ```text
//! RegisterClassExW → CreateWindowExW(隐藏) → 铺 DWM 底板 → 建 WebView2
//!   → ShowWindow → 消息循环
//! ```
//!
//! WebView2 在隐藏窗口上创建时如果客户区是 0×0，控制器会以 0 尺寸初始化，之后
//! 再不跟随 `WM_SIZE`——表现是一直白屏。所以必须先 `SetWindowPos` 到真实尺寸。

use std::sync::atomic::{AtomicBool, AtomicIsize, Ordering};

use windows::core::{w, PCWSTR};
use windows::Win32::Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, WPARAM};
use windows::Win32::Graphics::Gdi::UpdateWindow;
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DestroyWindow, DispatchMessageW, GetMessageW,
    GetWindowLongPtrW, LoadCursorW, PostQuitMessage, RegisterClassExW, SetWindowLongPtrW,
    SetWindowPos, ShowWindow, TranslateMessage, CS_HREDRAW, CS_VREDRAW, CW_USEDEFAULT, GWLP_USERDATA,
    IDC_ARROW, MSG, SWP_NOACTIVATE, SWP_NOZORDER, SW_SHOW, WM_APP, WM_CLOSE, WM_DPICHANGED,
    WM_ERASEBKGND, WM_GETMINMAXINFO, WM_NCCALCSIZE, WM_NCHITTEST, WM_SETTINGCHANGE, WM_SIZE,
    WNDCLASSEXW, WS_EX_APPWINDOW, WS_OVERLAPPEDWINDOW,
};

use crate::backdrop;
use crate::bridge;
use crate::cli::Cli;
use crate::error::{HostError, Result};
use crate::webview::WebView;
use crate::win::{dpi, frame};

/// 宿主命令（重载、开发者工具）走这条消息，避免在任意线程碰 COM。
pub const WM_APP_CMD: u32 = WM_APP + 0x22;

static RELOAD: AtomicBool = AtomicBool::new(false);
static DEVTOOLS: AtomicBool = AtomicBool::new(false);

pub fn request_reload() {
    RELOAD.store(true, Ordering::SeqCst);
    if let Some(h) = host_hwnd() {
        crate::webview::wake(h);
    }
}

pub fn request_devtools() {
    DEVTOOLS.store(true, Ordering::SeqCst);
    if let Some(h) = host_hwnd() {
        crate::webview::wake(h);
    }
}

static HWND_BITS: AtomicIsize = AtomicIsize::new(0);

fn host_hwnd() -> Option<HWND> {
    let v = HWND_BITS.load(Ordering::SeqCst);
    if v == 0 {
        None
    } else {
        Some(HWND(v as *mut core::ffi::c_void))
    }
}

/// 窗口的全部状态。
pub struct Host {
    pub hwnd: HWND,
    pub webview: Option<WebView>,
    pub backdrop: Option<backdrop::State>,
    pub cli: Cli,
    /// 上一次的客户区尺寸，`WM_SIZE` 里用来判断是不是真的变了。
    pub last_size: (i32, i32),
    pub dark: bool,
    /// 关闭时是否退到托盘（有托盘才生效）。
    pub close_to_tray: bool,
    pub tray: Option<crate::win::tray::Tray>,
}

impl std::fmt::Debug for Host {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Host")
            .field("hwnd", &self.hwnd)
            .field("backdrop", &self.backdrop.as_ref().map(|b| b.kind))
            .field("tray", &self.tray.is_some())
            .finish_non_exhaustive()
    }
}

impl Host {
    fn new(cli: Cli) -> Self {
        let dark = backdrop::version::system_dark();
        Self {
            hwnd: HWND::default(),
            webview: None,
            backdrop: None,
            cli,
            last_size: (0, 0),
            dark,
            close_to_tray: true,
            tray: None,
        }
    }

    /// 消息处理。返回 `Some` 表示已处理。
    fn on_message(&mut self, msg: u32, wp: WPARAM, lp: LPARAM) -> Option<LRESULT> {
        match msg {
            WM_NCCALCSIZE => frame::nc_calc_size(self.hwnd, wp, lp).map(LRESULT),

            WM_GETMINMAXINFO => {
                frame::min_max_info(self.hwnd, lp);
                Some(LRESULT(0))
            }

            WM_NCHITTEST => frame::nc_hit_test(self.hwnd, lp).map(LRESULT),

            WM_ERASEBKGND => Some(LRESULT(1)),

            WM_SIZE => {
                if let Some(wv) = &self.webview {
                    wv.resize(self.hwnd);
                }
                let r = crate::webview::client_rect(self.hwnd);
                let s = (r.right - r.left, r.bottom - r.top);
                if s != self.last_size {
                    self.last_size = s;
                    // 让前端知道尺寸变了（CSS 的 resize 事件在 WebView 里不触发）
                    bridge::set_info(serde_json::json!({
                        "width": s.0, "height": s.1, "dpi": dpi::scale(self.hwnd)
                    }));
                }
                Some(LRESULT(0))
            }

            WM_DPICHANGED => {
                // lParam 是系统建议的新矩形
                let r = unsafe { &*(lp.0 as *const windows::Win32::Foundation::RECT) };
                unsafe {
                    let _ = SetWindowPos(
                        self.hwnd,
                        None,
                        r.left,
                        r.top,
                        r.right - r.left,
                        r.bottom - r.top,
                        SWP_NOZORDER | SWP_NOACTIVATE,
                    );
                }
                if let Some(wv) = &self.webview {
                    wv.resize(self.hwnd);
                }
                bridge::set_info(serde_json::json!({ "dpi": dpi::scale(self.hwnd) }));
                Some(LRESULT(0))
            }

            WM_SETTINGCHANGE => {
                let section = unsafe { PCWSTR(lp.0 as *const u16).to_string() }.unwrap_or_default();
                if section.contains("ImmersiveColorSet") {
                    let dark = backdrop::version::system_dark();
                    if dark != self.dark {
                        self.dark = dark;
                        backdrop::set_dark(self.hwnd, dark);
                        bridge::set_info(serde_json::json!({ "dark": dark }));
                        if let Some(wv) = &self.webview {
                            wv.post_json(&serde_json::json!({
                                "type": "host_theme", "dark": dark
                            }).to_string());
                        }
                    }
                }
                Some(LRESULT(0))
            }

            // 页面/宿主攒下的事件，一次性推给 JS
            m if m == crate::webview::WM_APP_FLUSH || m == WM_APP_CMD => {
                if RELOAD.swap(false, Ordering::SeqCst) {
                    if let Some(wv) = &self.webview {
                        wv.reload();
                    }
                }
                if DEVTOOLS.swap(false, Ordering::SeqCst) {
                    if let Some(wv) = &self.webview {
                        wv.open_devtools();
                    }
                }
                if let Some(wv) = &self.webview {
                    bridge::flush(wv);
                }
                Some(LRESULT(0))
            }

            // 托盘：左键切显示/隐藏，右键弹菜单
            m if m == crate::win::tray::WM_APP_TRAY || m == 0x0111 => {
                if let Some(t) = &self.tray {
                    if t.handle(m, wp, lp) {
                        return Some(LRESULT(0));
                    }
                }
                None
            }

            WM_CLOSE => {                if self.close_to_tray && self.tray.is_some() {
                    unsafe {
                        let _ = ShowWindow(self.hwnd, windows::Win32::UI::WindowsAndMessaging::SW_HIDE);
                    }
                    return Some(LRESULT(0));
                }
                unsafe {
                    let _ = DestroyWindow(self.hwnd);
                }
                Some(LRESULT(0))
            }

            windows::Win32::UI::WindowsAndMessaging::WM_DESTROY => {
                unsafe { PostQuitMessage(0) };
                Some(LRESULT(0))
            }

            _ => None,
        }
    }
}

unsafe extern "system" fn wndproc(hwnd: HWND, msg: u32, wp: WPARAM, lp: LPARAM) -> LRESULT {
    // 窗口创建期间 GWLP_USERDATA 还是空的，NCCALCSIZE 靠纯函数处理。
    let ptr = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *mut Host;
    if !ptr.is_null() {
        if let Some(r) = (*ptr).on_message(msg, wp, lp) {
            return r;
        }
    } else if msg == WM_NCCALCSIZE {
        if let Some(r) = frame::nc_calc_size(hwnd, wp, lp) {
            return LRESULT(r);
        }
    }
    DefWindowProcW(hwnd, msg, wp, lp)
}

fn class_name() -> PCWSTR {
    w!("BaiCodexDesktop")
}

fn register(hinstance: HINSTANCE) -> Result<()> {
    let wc = WNDCLASSEXW {
        cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
        style: CS_HREDRAW | CS_VREDRAW,
        lpfnWndProc: Some(wndproc),
        cbClsExtra: 0,
        cbWndExtra: 0,
        hInstance: hinstance,
        hIcon: Default::default(),
        hCursor: unsafe { LoadCursorW(None, IDC_ARROW)? },
        // 关键：不刷背景。填了画刷，Mica 就透不上来。
        hbrBackground: Default::default(),
        lpszMenuName: PCWSTR::null(),
        lpszClassName: class_name(),
        hIconSm: Default::default(),
    };
    let atom = unsafe { RegisterClassExW(&wc) };
    if atom == 0 {
        // 已经注册过（同进程重复跑）不算错
        let err = windows::core::Error::from_thread();
        if err.code().0 != 0x8007_0581u32 as i32 {
            return Err(HostError::Win(err));
        }
    }
    Ok(())
}

/// 建窗口、铺底板、挂网页、跑循环。返回值就是进程退出码。
pub fn run(cli: Cli) -> Result<i32> {
    dpi::init_process();

    // COM：WebView2 要 apartment threading
    unsafe {
        use windows::Win32::System::Com::{CoInitializeEx, COINIT_APARTMENTTHREADED};
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED);
    }

    let hinstance: HINSTANCE = unsafe { GetModuleHandleW(None)?.into() };
    register(hinstance)?;

    let scale = 1.0f32; // 建窗口时还没有 hwnd，用系统主屏 DPI 估值
    let w = ((1180.0 * scale) as i32).max(760);
    let h = ((780.0 * scale) as i32).max(480);

    let mut host = Box::new(Host::new(cli));
    let host_ptr: *mut Host = &mut *host;

    let hwnd = unsafe {
        CreateWindowExW(
            WS_EX_APPWINDOW,
            class_name(),
            &windows::core::HSTRING::from(host.cli.title.clone()),
            WS_OVERLAPPEDWINDOW,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            w,
            h,
            None,
            None,
            Some(hinstance),
            Some(host_ptr as *const core::ffi::c_void),
        )?
    };

    host.hwnd = hwnd;
    HWND_BITS.store(hwnd.0 as isize, Ordering::SeqCst);
    unsafe {
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, host_ptr as isize);
    }

    // —— 底板 ——
    let state = backdrop::apply(hwnd, host.dark);
    host.backdrop = Some(state.clone());

    // —— 网页 ——
    let url = host.cli.url()?;
    let data_dir = host.cli.base.clone().unwrap_or_else(|| {
        dirs_data_root()
    });
    let web_dir = data_dir.join("webview2");
    let debug = host.cli.debug;

    let wv = WebView::create(hwnd, &url, &web_dir, debug, |raw| bridge::on_message(raw))?;
    host.webview = Some(wv);

    // —— 首帧尺寸 ——
    frame::center_on_screen(hwnd, w, h);
    unsafe {
        let _ = ShowWindow(hwnd, SW_SHOW);
        let _ = UpdateWindow(hwnd);
    }
    if let Some(wv) = &host.webview {
        wv.resize(hwnd);
        if debug {
            wv.open_devtools();
        }
    }

    // —— 托盘 ——
    if !host.cli.no_tray {
        match crate::win::tray::Tray::create(hwnd, "baicodex") {
            Ok(t) => host.tray = Some(t),
            Err(e) => crate::log_line(&format!("托盘创建失败：{e}")),
        }
    }

    // —— 桥 ——
    let info = serde_json::json!({
        "kind": "host_info",
        "version": baicodex::VERSION,
        "os": "windows",
        "platform": "win32",
        "arch": std::env::consts::ARCH,
        "backdrop": state.kind.name(),
        "tintAlpha": state.kind.tint_alpha(),
        "dark": state.dark,
        "rounded": state.rounded,
        "windows": format!("{}.{}.{}", state.version.major, state.version.minor, state.version.build),
        "windowsName": state.version.name(),
        "dpi": dpi::scale(hwnd),
        "dev": debug,
        "devUrl": host.cli.dev_url.clone(),
        "title": host.cli.title.clone(),
    });
    bridge::start(hwnd, info);
    bridge::pump_events();

    // —— 循环 ——
    let code = message_loop();

    // 退出清理
    if let Some(t) = &host.tray {
        t.remove();
    }
    drop(host);
    Ok(code)
}

fn message_loop() -> i32 {
    let mut msg = MSG::default();
    loop {
        let r = unsafe { GetMessageW(&mut msg, None, 0, 0) };
        if r.0 == 0 {
            // WM_QUIT
            return msg.wParam.0 as i32;
        }
        if r.0 == -1 {
            return 1;
        }
        unsafe {
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
}

/// WebView2 的用户数据目录。跟内核数据根分开放——一个是浏览器的缓存，
/// 一个是用户的会话库，混在一起备份时会把几百兆缓存也打包进去。
fn dirs_data_root() -> std::path::PathBuf {
    if let Ok(p) = std::env::var("LOCALAPPDATA") {
        return std::path::PathBuf::from(p).join("baicodex");
    }
    std::env::temp_dir().join("baicodex")
}

/// 用系统默认程序打开链接或文件夹。
pub fn shell_open(target: &str) -> Result<()> {
    use windows::Win32::UI::Shell::ShellExecuteW;
    use windows::Win32::UI::WindowsAndMessaging::SW_SHOWNORMAL;

    let op = windows::core::HSTRING::from("open");
    let file = windows::core::HSTRING::from(target);
    let r = unsafe { ShellExecuteW(None, &op, &file, PCWSTR::null(), PCWSTR::null(), SW_SHOWNORMAL) };
    // ShellExecuteW 的返回值 > 32 才算成功
    if r.0 as isize <= 32 {
        return Err(HostError::msg(format!("打不开 {target}")));
    }
    Ok(())
}
