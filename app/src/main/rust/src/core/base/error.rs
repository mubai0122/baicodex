//! 统一错误类型。
//!
//! 为什么不用 `anyhow`：这个库要把错误跨过 JNI / C ABI 传给 Kotlin 与 C++，
//! 对方需要的是**稳定的错误码**，不是一段格式化后的字符串。所以错误被收敛成
//! 有限枚举 + 人话消息 + 可选细节，序列化后就是 `{"code":"denied","message":"..."}`。
//!
//! 内部实现可以 `?` 任意底层错误（下面有一堆 `From`），但离开库边界前必须变成
//! [BError]。这样前端永远不需要解析字符串来判断失败原因。

use std::fmt;

use serde::{Deserialize, Serialize};

/// 错误码。新增只能往后加，不能改已有含义——Kotlin 侧会按它分支。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    /// 没分类的内部错误。出现它说明某处该补一个具体错误码。
    Internal,
    /// 参数不合法：缺字段、格式错、越界。
    Invalid,
    /// 找不到：文件、会话、工具、模型。
    NotFound,
    /// 被拒绝：权限、策略、用户取消确认。
    Denied,
    /// 状态冲突：正在运行又发一条、版本不匹配。
    Conflict,
    /// 超时。
    Timeout,
    /// 网络层失败（DNS、连接、TLS、断流）。
    Network,
    /// 鉴权失败（401/403、key 无效）。
    Auth,
    /// 预算超限（token、字符数、轮次）。
    Budget,
    /// 用户或上层主动取消。
    Cancelled,
    /// 能力不支持（模型不支持图片、平台没有 root）。
    Unsupported,
    /// 文件系统错误。
    Io,
    /// 数据库错误。
    Db,
    /// 加解密错误。
    Crypto,
    /// 跨语言边界错误（JSON 编解码、句柄失效）。
    Ffi,
}

impl ErrorCode {
    /// 给前端做分支用的稳定字符串。
    pub fn as_str(self) -> &'static str {
        match self {
            ErrorCode::Internal => "internal",
            ErrorCode::Invalid => "invalid",
            ErrorCode::NotFound => "not_found",
            ErrorCode::Denied => "denied",
            ErrorCode::Conflict => "conflict",
            ErrorCode::Timeout => "timeout",
            ErrorCode::Network => "network",
            ErrorCode::Auth => "auth",
            ErrorCode::Budget => "budget",
            ErrorCode::Cancelled => "cancelled",
            ErrorCode::Unsupported => "unsupported",
            ErrorCode::Io => "io",
            ErrorCode::Db => "db",
            ErrorCode::Crypto => "crypto",
            ErrorCode::Ffi => "ffi",
        }
    }

    /// 默认是否值得重试。
    ///
    /// 只有"换个时间可能成功"的才重试：网络、超时、冲突。参数错、鉴权错、被拒绝的，
    /// 重试一万次也是同一个结果，只会浪费用户的钱。
    pub fn retryable(self) -> bool {
        matches!(self, ErrorCode::Network | ErrorCode::Timeout | ErrorCode::Conflict)
    }
}

/// 库内统一错误。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BError {
    pub code: ErrorCode,
    pub message: String,
    /// 给日志看的细节，不展示给用户（可能含路径、SQL、响应体）。
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    /// 覆盖 `code.retryable()`：少数场景（比如 429）需要单独标记。
    #[serde(default, skip_serializing_if = "is_false")]
    pub retryable: bool,
}

fn is_false(b: &bool) -> bool {
    !*b
}

impl BError {
    pub fn new(code: ErrorCode, message: impl Into<String>) -> Self {
        Self { code, message: message.into(), detail: None, retryable: code.retryable() }
    }

    pub fn internal(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::Internal, message)
    }

    pub fn invalid(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::Invalid, message)
    }

    pub fn not_found(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::NotFound, message)
    }

    pub fn denied(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::Denied, message)
    }

    pub fn conflict(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::Conflict, message)
    }

    pub fn unsupported(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::Unsupported, message)
    }

    pub fn cancelled() -> Self {
        Self::new(ErrorCode::Cancelled, "已取消")
    }

    pub fn budget(message: impl Into<String>) -> Self {
        Self::new(ErrorCode::Budget, message)
    }

    /// 附上细节。可以链式调用。
    pub fn with_detail(mut self, detail: impl Into<String>) -> Self {
        let d = detail.into();
        if !d.is_empty() {
            self.detail = Some(d);
        }
        self
    }

    /// 改写是否可重试。
    pub fn retry_hint(mut self, yes: bool) -> Self {
        self.retryable = yes;
        self
    }

    pub fn is(&self, code: ErrorCode) -> bool {
        self.code == code
    }

    pub fn retryable_now(&self) -> bool {
        self.retryable
    }

    /// 给用户看的一句话。不含 detail（detail 可能带密钥、路径）。
    pub fn user_text(&self) -> String {
        if self.message.is_empty() {
            self.code.as_str().to_string()
        } else {
            self.message.clone()
        }
    }

    /// 转成 FFI 响应体里的 `error` 字段。
    pub fn to_json(&self) -> serde_json::Value {
        serde_json::json!({
            "code": self.code.as_str(),
            "message": self.message,
            "retryable": self.retryable,
        })
    }
}

impl fmt::Display for BError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.message)?;
        if let Some(d) = &self.detail {
            write!(f, "（{d}）")?;
        }
        Ok(())
    }
}

impl std::error::Error for BError {}

impl From<std::io::Error> for BError {
    fn from(e: std::io::Error) -> Self {
        use std::io::ErrorKind as K;
        let code = match e.kind() {
            K::NotFound => ErrorCode::NotFound,
            K::PermissionDenied => ErrorCode::Denied,
            K::TimedOut => ErrorCode::Timeout,
            K::AlreadyExists => ErrorCode::Conflict,
            _ => ErrorCode::Io,
        };
        BError::new(code, io_text(&e)).with_detail(e.to_string())
    }
}

fn io_text(e: &std::io::Error) -> String {
    use std::io::ErrorKind as K;
    match e.kind() {
        K::NotFound => "文件不存在".into(),
        K::PermissionDenied => "没有权限".into(),
        K::TimedOut => "操作超时".into(),
        K::AlreadyExists => "已经存在".into(),
        // StorageFull / ReadOnlyFilesystem 需要较新的 std，这里按错误码兜底，
        // 不为了两句话把 MSRV 抬上去。
        _ => "文件操作失败".into(),
    }
}

impl From<serde_json::Error> for BError {
    fn from(e: serde_json::Error) -> Self {
        BError::new(ErrorCode::Invalid, "JSON 解析失败").with_detail(e.to_string())
    }
}

impl From<rusqlite::Error> for BError {
    fn from(e: rusqlite::Error) -> Self {
        let code = match &e {
            rusqlite::Error::QueryReturnedNoRows => ErrorCode::NotFound,
            rusqlite::Error::SqliteFailure(f, _) => match f.code {
                rusqlite::ErrorCode::ConstraintViolation => ErrorCode::Conflict,
                rusqlite::ErrorCode::DatabaseBusy | rusqlite::ErrorCode::DatabaseLocked => {
                    ErrorCode::Conflict
                }
                _ => ErrorCode::Db,
            },
            _ => ErrorCode::Db,
        };
        BError::new(code, "数据库操作失败").with_detail(e.to_string())
    }
}

impl From<reqwest::Error> for BError {
    fn from(e: reqwest::Error) -> Self {
        let code = if e.is_timeout() {
            ErrorCode::Timeout
        } else if e.is_connect() {
            ErrorCode::Network
        } else if let Some(s) = e.status() {
            match s.as_u16() {
                401 | 403 => ErrorCode::Auth,
                404 => ErrorCode::NotFound,
                409 => ErrorCode::Conflict,
                429 => ErrorCode::Conflict,
                _ => ErrorCode::Network,
            }
        } else {
            ErrorCode::Network
        };
        let msg = if e.is_timeout() {
            "请求超时"
        } else if e.is_connect() {
            "连不上服务器"
        } else {
            "网络请求失败"
        };
        BError::new(code, msg).with_detail(e.to_string())
    }
}

impl From<zip::result::ZipError> for BError {
    fn from(e: zip::result::ZipError) -> Self {
        BError::new(ErrorCode::Io, "压缩包处理失败").with_detail(e.to_string())
    }
}

impl From<std::str::Utf8Error> for BError {
    fn from(e: std::str::Utf8Error) -> Self {
        BError::new(ErrorCode::Invalid, "文本编码不是 UTF-8").with_detail(e.to_string())
    }
}

impl From<std::string::FromUtf8Error> for BError {
    fn from(e: std::string::FromUtf8Error) -> Self {
        BError::new(ErrorCode::Invalid, "文本编码不是 UTF-8").with_detail(e.to_string())
    }
}

impl From<regex::Error> for BError {
    fn from(e: regex::Error) -> Self {
        BError::new(ErrorCode::Invalid, "正则表达式不合法").with_detail(e.to_string())
    }
}

/// 库内通用返回值。
pub type BResult<T> = Result<T, BError>;

/// 给 `Result` 补几个常用的语境化方法。
///
/// `x.context("读配置")?` 比 `map_err(|e| BError::new(..., e.to_string()))?` 短得多，
/// 而且 detail 里会自动带上原始错误。
pub trait ResultExt<T> {
    fn context(self, what: impl Into<String>) -> BResult<T>;
    fn code(self, code: ErrorCode) -> BResult<T>;
}

impl<T, E> ResultExt<T> for Result<T, E>
where
    E: std::error::Error + Send + Sync + 'static,
{
    fn context(self, what: impl Into<String>) -> BResult<T> {
        self.map_err(|e| BError::internal(what.into()).with_detail(e.to_string()))
    }

    fn code(self, code: ErrorCode) -> BResult<T> {
        self.map_err(|e| BError::new(code, e.to_string()))
    }
}

impl<T> ResultExt<T> for Option<T> {
    fn context(self, what: impl Into<String>) -> BResult<T> {
        self.ok_or_else(|| BError::not_found(what.into()))
    }

    fn code(self, code: ErrorCode) -> BResult<T> {
        self.ok_or_else(|| BError::new(code, "缺少必要的值"))
    }
}
