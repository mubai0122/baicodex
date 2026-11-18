//! 时间：只暴露毫秒时间戳，避免把时区/格式化逻辑混进内核。

/// 当前 Unix 毫秒。
pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}
