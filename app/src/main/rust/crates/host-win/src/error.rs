//! 宿主层错误。
//!
//! 这里刻意不用 `anyhow`：宿主是最终进程，错误要变成**给用户看的一句话**加
//! **写进日志的一串细节**。两件事分开做，界面上不会出现 COM 的 HRESULT。

use std::fmt;

pub type Result<T> = std::result::Result<T, HostError>;

#[derive(Debug)]
pub enum HostError {
    /// Win32 / COM 调用失败。
    Win(windows::core::Error),
    /// WebView2 自己的错误（多为"没装运行时"）。
    Webview(webview2_com::Error),
    /// 内核返回的错误（已带错误码）。
    Kernel { code: String, message: String },
    /// 文件、目录。
    Io(std::io::Error),
    /// 协议解析。
    Json(serde_json::Error),
    /// 参数不对 / 环境不满足，附一句人话。
    Msg(String),
}

impl HostError {
    pub fn msg(s: impl Into<String>) -> Self {
        HostError::Msg(s.into())
    }

    /// 给用户看的一句话。不出现 HRESULT、路径、堆栈。
    pub fn hint(&self) -> String {
        match self {
            HostError::Win(e) if e.code().0 == 0x8007_0002u32 as i32 => {
                "找不到 WebView2 运行时，请先安装 Microsoft Edge WebView2 Runtime".into()
            }
            HostError::Win(_) => "窗口初始化失败，详见日志".into(),
            HostError::Webview(_) => "网页组件启动失败，请确认 WebView2 运行时已安装".into(),
            HostError::Kernel { message, .. } => message.clone(),
            HostError::Io(_) => "读写文件失败".into(),
            HostError::Json(_) => "配置格式损坏".into(),
            HostError::Msg(s) => s.clone(),
        }
    }
}

impl fmt::Display for HostError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            HostError::Win(e) => write!(f, "win32: {e}"),
            HostError::Webview(e) => write!(f, "webview2: {e}"),
            HostError::Kernel { code, message } => write!(f, "kernel[{code}]: {message}"),
            HostError::Io(e) => write!(f, "io: {e}"),
            HostError::Json(e) => write!(f, "json: {e}"),
            HostError::Msg(s) => write!(f, "{s}"),
        }
    }
}

impl std::error::Error for HostError {}

impl From<windows::core::Error> for HostError {
    fn from(e: windows::core::Error) -> Self {
        HostError::Win(e)
    }
}

impl From<webview2_com::Error> for HostError {
    fn from(e: webview2_com::Error) -> Self {
        HostError::Webview(e)
    }
}

impl From<std::io::Error> for HostError {
    fn from(e: std::io::Error) -> Self {
        HostError::Io(e)
    }
}

impl From<serde_json::Error> for HostError {
    fn from(e: serde_json::Error) -> Self {
        HostError::Json(e)
    }
}
