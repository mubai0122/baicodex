//! WebView2 宿主。
//!
//! 只做四件事：建环境、建控制器、把背景设成全透明、接消息。业务逻辑一律在
//! [`crate::bridge`] 里，这一层不认识任何 op。
//!
//! ## 透明是怎么来的
//!
//! 三件事缺一不可：
//!
//! 1. 窗口不刷背景（`hbrBackground` 为 null），否则 WM_ERASEBKGND 会涂白；
//! 2. `ICoreWebView2Controller2::SetDefaultBackgroundColor(0,0,0,0)`；
//! 3. 页面 `html/body` 背景透明。
//!
//! 少任何一个，DWM 的 Mica 都透不上来，窗口会变成一块死白。
//!
//! ## 为什么不用 `WS_EX_NOREDIRECTIONBITMAP`
//!
//! 那个样式是给 DirectComposition 自绘准备的。WebView2 自己就是个子窗口合成器，
//! 加上它会让窗口内容整个消失，只剩一片透明。这是最容易踩的坑之一。

use std::cell::RefCell;

use windows::core::{Interface, HSTRING, PCWSTR};
use windows::Win32::Foundation::{E_POINTER, HWND, LPARAM, RECT, WPARAM};
use windows::Win32::UI::WindowsAndMessaging::{
    GetClientRect, PostMessageW, SetWindowPos, SWP_NOACTIVATE, SWP_NOMOVE, SWP_NOSIZE, SWP_NOZORDER,
    WM_APP,
};
use webview2_com::Microsoft::Web::WebView2::Win32::*;
use webview2_com::{
    CreateCoreWebView2ControllerCompletedHandler, CreateCoreWebView2EnvironmentCompletedHandler,
    WebMessageReceivedEventHandler,
};

use crate::error::{HostError, Result};

/// 宿主 → 界面的自定义消息号。用来把"有事要推给 JS"从任意线程踢回 UI 线程。
pub const WM_APP_FLUSH: u32 = WM_APP + 0x21;

/// 一个已经跑起来的 WebView。
pub struct WebView {
    controller: ICoreWebView2Controller,
    core: ICoreWebView2,
    url: String,
}

impl std::fmt::Debug for WebView {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("WebView").field("url", &self.url).finish_non_exhaustive()
    }
}

impl WebView {
    /// 创建。`user_data` 必须是可写目录，WebView2 会在里面放缓存。
    ///
    /// `on_message` 在 UI 线程被调用，入参是前端 post 过来的原始 JSON。
    pub fn create<F>(hwnd: HWND, url: &str, user_data: &std::path::Path, debug: bool, on_message: F) -> Result<Self>
    where
        F: Fn(&str) -> Option<String> + 'static,
    {
        let env = create_env(hwnd, user_data)?;
        let controller = create_controller(hwnd, env)?;

        // —— 透明 ——
        unsafe {
            let c2: ICoreWebView2Controller2 = controller.cast()?;
            c2.SetDefaultBackgroundColor(COREWEBVIEW2_COLOR { A: 0, R: 0, G: 0, B: 0 })?;
        }

        let core = unsafe { controller.CoreWebView2()? };
        tune_settings(&core, debug)?;
        install_message_handler(&core, on_message)?;
        resize(&controller, hwnd);

        unsafe {
            controller.SetIsVisible(true)?;
            core.Navigate(&HSTRING::from(url))?;
            // 触发一次重排，让 WebView 立刻跟随客户区尺寸
            let _ = SetWindowPos(hwnd, None, 0, 0, 0, 0, SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOMOVE | SWP_NOSIZE);
        }

        // 回调的 token 没留：WebView2 在窗口销毁时会连带释放控制器，
        // 那时再摘回调反而可能碰到已释放的对象。
        Ok(Self { controller, core, url: url.to_string() })
    }

    /// 跟随窗口大小。`WM_SIZE` 里调。
    pub fn resize(&self, hwnd: HWND) {
        resize(&self.controller, hwnd);
    }

    /// 往页面推一条 JSON（事件）。
    pub fn post_json(&self, json: &str) {
        unsafe {
            let _ = self.core.PostWebMessageAsJson(&HSTRING::from(json));
        }
    }

    /// 导航到新地址。
    pub fn navigate(&self, url: &str) -> Result<()> {
        unsafe { self.core.Navigate(&HSTRING::from(url))? };
        Ok(())
    }

    /// 重新加载。前端崩溃或白屏时兜底。
    pub fn reload(&self) {
        unsafe {
            let _ = self.core.Reload();
        }
    }

    /// 打开开发者工具。
    pub fn open_devtools(&self) {
        unsafe {
            let _ = self.core.OpenDevToolsWindow();
        }
    }

    /// 当前地址，给日志用。
    pub fn url(&self) -> &str {
        &self.url
    }

    /// 页面文档标题变化时同步到窗口标题。
    pub fn on_title_changed<F>(&self, mut f: F) -> Result<i64>
    where
        F: FnMut(&str) + 'static,
    {
        let mut token = 0i64;
        let handler = webview2_com::DocumentTitleChangedEventHandler::create(Box::new(move |sender, _args| {
            if let Some(s) = sender {
                let mut p = windows::core::PWSTR::null();
                let ok = unsafe { s.DocumentTitle(&mut p) }.is_ok();
                if ok {
                    let h = webview2_com::CoTaskMemPWSTR::from(p);
                    f(&h.to_string());
                }
            }
            Ok(())
        }));
        unsafe { self.core.add_DocumentTitleChanged(&handler, &mut token)? };
        Ok(token)
    }
}

fn create_env(hwnd: HWND, user_data: &std::path::Path) -> Result<ICoreWebView2Environment> {
    let _ = hwnd;
    std::fs::create_dir_all(user_data)?;
    let dir = HSTRING::from(user_data.to_string_lossy().to_string());
    let (tx, rx) = std::sync::mpsc::channel();

    CreateCoreWebView2EnvironmentCompletedHandler::wait_for_async_operation(
        Box::new(move |handler| unsafe {
            CreateCoreWebView2EnvironmentWithOptions(
                PCWSTR::null(),
                PCWSTR(dir.as_ptr()),
                None,
                &handler,
            )
            .map_err(webview2_com::Error::WindowsError)
        }),
        Box::new(move |err, env| {
            err?;
            let env = env.ok_or_else(|| windows::core::Error::from(E_POINTER))?;
            let _ = tx.send(env);
            Ok(())
        }),
    )?;

    rx.recv()
        .map_err(|_| HostError::msg("WebView2 环境创建没有回调（运行时未安装？）"))
}

fn create_controller(hwnd: HWND, env: ICoreWebView2Environment) -> Result<ICoreWebView2Controller> {
    let (tx, rx) = std::sync::mpsc::channel();
    CreateCoreWebView2ControllerCompletedHandler::wait_for_async_operation(
        Box::new(move |handler| unsafe {
            env.CreateCoreWebView2Controller(hwnd, &handler)
                .map_err(webview2_com::Error::WindowsError)
        }),
        Box::new(move |err, c| {
            err?;
            let c = c.ok_or_else(|| windows::core::Error::from(E_POINTER))?;
            let _ = tx.send(c);
            Ok(())
        }),
    )?;
    rx.recv().map_err(|_| HostError::msg("WebView2 控制器创建没有回调"))
}

fn tune_settings(core: &ICoreWebView2, debug: bool) -> Result<()> {
    unsafe {
        let s: ICoreWebView2Settings = core.Settings()?;
        // 右键菜单是浏览器的，不是应用的
        s.SetAreDefaultContextMenusEnabled(false)?;
        s.SetIsStatusBarEnabled(false)?;
        // 缩放交给页面自己管，Ctrl+滚轮会跟我们的布局打架
        s.SetIsZoomControlEnabled(false)?;
        s.SetAreDevToolsEnabled(debug)?;
        // F5 刷新、Ctrl+P 打印这些在桌面应用里全是干扰
        if let Ok(s3) = s.cast::<ICoreWebView2Settings3>() {
            let _ = s3.SetAreBrowserAcceleratorKeysEnabled(debug);
        }
    }
    Ok(())
}

fn install_message_handler<F>(core: &ICoreWebView2, on_message: F) -> Result<()>
where
    F: Fn(&str) -> Option<String> + 'static,
{
    // 回调在 UI 线程，不能跨线程共享闭包；用 RefCell 是因为它只需要 Fn
    let f = RefCell::new(on_message);
    let mut token = 0i64;

    let handler = WebMessageReceivedEventHandler::create(Box::new(move |_sender, args| {
        let Some(args) = args else { return Ok(()) };
        let mut raw = windows::core::PWSTR::null();
        // 优先按字符串取；前端如果 post 的是对象，WebMessageAsJson 也能拿到
        let direct = unsafe { args.TryGetWebMessageAsString(&mut raw) }.is_ok();
        if !direct && unsafe { args.WebMessageAsJson(&mut raw) }.is_err() {
            return Ok(());
        }
        let text = webview2_com::CoTaskMemPWSTR::from(raw).to_string();
        if let Some(reply) = (f.borrow())(&text) {
            if let Some(sender) = _sender {
                unsafe {
                    let _ = sender.PostWebMessageAsJson(&HSTRING::from(reply));
                }
            }
        }
        Ok(())
    }));

    unsafe { core.add_WebMessageReceived(&handler, &mut token)? };
    Ok(())
}

fn resize(controller: &ICoreWebView2Controller, hwnd: HWND) {
    let mut r = RECT::default();
    unsafe {
        if GetClientRect(hwnd, &mut r).is_err() {
            return;
        }
        let _ = controller.SetBounds(r);
    }
}

/// 把一件事踢回 UI 线程处理。事件线程用它。
pub fn wake(hwnd: HWND) {
    unsafe {
        let _ = PostMessageW(Some(hwnd), WM_APP_FLUSH, WPARAM(0), LPARAM(0));
    }
}

/// 顶层窗口的客户区矩形（物理像素）。
pub fn client_rect(hwnd: HWND) -> RECT {
    let mut r = RECT::default();
    unsafe {
        let _ = GetClientRect(hwnd, &mut r);
    }
    r
}
