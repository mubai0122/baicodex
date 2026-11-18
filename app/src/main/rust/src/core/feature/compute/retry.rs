//! 请求重试策略：判断「这次失败该不该再发一次」以及「隔多久再发」。
//!
//! 三端共用同一套判定，避免 Android 自动重试、Windows 不重试这种分裂行为。
//!
//! 边界（重要，别在这里加业务）：
//! - 只管**同一次 HTTP 请求**的失败；工具调用失败、参数校验失败、用户取消一律不进来。
//! - 重试发出的是**语义完全相同的请求**：调用方必须复用同一份请求体，不得重建消息列表。
//! - 上限 5 次重试（首次请求 + 最多 5 次重发 = 最多 6 次尝试）。

/// 最多重试次数（不含首次请求）
pub const MAX_RETRY: u32 = 5;

/// 失败分类
pub const KIND_NONE: u8 = 0;
/// 连接/读写错误：断网、DNS、连接重置、broken pipe
pub const KIND_NETWORK: u8 = 1;
/// 请求超时（408 / SocketTimeout）
pub const KIND_TIMEOUT: u8 = 2;
/// 限流（429）：退避要更长，别把服务端惹毛
pub const KIND_RATE: u8 = 3;
/// 服务端错误（5xx）
pub const KIND_SERVER: u8 = 4;

/// 单次退避上限：网络/服务端 8 秒，限流 30 秒；
/// 再长用户会以为卡死，不如直接报错让他手动重发。
const CAP_NORMAL_MS: u64 = 8_000;
const CAP_RATE_MS: u64 = 30_000;

/// 分类：先看 HTTP 码，码为 0（没拿到响应）再按错误文本猜
pub fn classify(http_code: i32, msg: &str) -> u8 {
    let m = msg.to_lowercase();
    //用户主动停止不算失败，绝不能自动重发
    if m.contains("已停止") || m.contains("cancelled") || m.contains("canceled") {
        return KIND_NONE;
    }
    //配置类错误重发一万次也没用
    for bad in ["base_url 未配置", "401", "403", "404", "响应不是 json", "响应缺失"] {
        if m.contains(bad) {
            return KIND_NONE;
        }
    }
    match http_code {
        408 => return KIND_TIMEOUT,
        429 => return KIND_RATE,
        500..=599 => return KIND_SERVER,
        200..=499 => return KIND_NONE,
        _ => {}
    }
    if m.contains("timeout") || m.contains("timed out") || m.contains("超时") {
        return KIND_TIMEOUT;
    }
    for hit in [
        "网络错误",
        "unknownhost",
        "connect",
        "connection reset",
        "broken pipe",
        "unreachable",
        "eof",
        "socket",
        "ssl",
    ] {
        if m.contains(hit) {
            return KIND_NETWORK;
        }
    }
    KIND_NONE
} //失败分类

/// 这次是否还允许重试：attempt 是「已发生的失败次数」，从 1 开始
pub fn allow(attempt: u32, kind: u8) -> bool {
    kind != KIND_NONE && attempt >= 1 && attempt <= MAX_RETRY
} //是否允许重试

/// 第 attempt 次失败后的等待毫秒：指数退避 500 → 1000 → 2000 → 4000 → 8000
/// 限流单独一档 2000 起步（2000 → 4000 → 8000 → 16000 → 30000 封顶）
pub fn delay_ms(kind: u8, attempt: u32) -> u64 {
    let n = attempt.max(1).min(MAX_RETRY);
    let base: u64 = if kind == KIND_RATE { 2_000 } else { 500 };
    let cap = if kind == KIND_RATE { CAP_RATE_MS } else { CAP_NORMAL_MS };
    base.saturating_mul(1u64 << (n - 1)).min(cap)
} //退避时长

#[cfg(test)]
mod tests {
    use super::*;

    /// 5xx / 429 / 超时 / 断网都要重试，4xx 与用户取消不重试
    #[test]
    fn classify_cases() {
        assert_eq!(classify(500, ""), KIND_SERVER);
        assert_eq!(classify(503, ""), KIND_SERVER);
        assert_eq!(classify(429, ""), KIND_RATE);
        assert_eq!(classify(408, ""), KIND_TIMEOUT);
        assert_eq!(classify(400, ""), KIND_NONE);
        assert_eq!(classify(0, "网络错误: Connection reset"), KIND_NETWORK);
        assert_eq!(classify(0, "已停止"), KIND_NONE);
        assert_eq!(classify(0, "base_url 未配置"), KIND_NONE);
    } //分类

    /// 最多 5 次，第 6 次失败不再重试
    #[test]
    fn attempts_capped_at_five() {
        for a in 1..=MAX_RETRY {
            assert!(allow(a, KIND_NETWORK), "attempt {a} 应该允许");
        }
        assert!(!allow(MAX_RETRY + 1, KIND_NETWORK));
        assert!(!allow(1, KIND_NONE));
    } //上限

    /// 退避单调递增且封顶
    #[test]
    fn delay_grows_and_caps() {
        let seq: Vec<u64> = (1..=MAX_RETRY).map(|a| delay_ms(KIND_NETWORK, a)).collect();
        assert_eq!(seq, vec![500, 1000, 2000, 4000, 8000]);
        assert_eq!(delay_ms(KIND_RATE, 5), CAP_RATE_MS);
        assert_eq!(delay_ms(KIND_SERVER, 99), CAP_NORMAL_MS);
    } //退避
} //测试
