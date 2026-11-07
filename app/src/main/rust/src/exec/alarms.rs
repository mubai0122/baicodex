//! 定时表达式：解析、格式化、算下一次。
//!
//! 认这几种写法（都比"Unix 时间戳"更适合人写）：
//!
//! ```text
//! 2025-03-08 09:30     绝对时间
//! 2025/03/08 09:30     同上，斜杠也认
//! 09:30                今天这个点；已过则顺延（每天型）
//! +30m / +2h / +90s    相对时间
//! ```
//!
//! ## 为什么"已过"要区分每天型与单次型
//!
//! 单次任务填了个过去的时间，正确行为是"不排程"而不是"立刻跑"——立刻跑会让
//! 用户重启应用时被一堆历史任务淹没。每天型则应该顺延到明天。

use chrono::{Datelike, Duration, Local, NaiveDate, NaiveDateTime, NaiveTime, TimeZone, Timelike};

/// 解析结果。0 表示不合法或已过期（单次型）。
pub fn parse_at(raw: &str, daily: bool) -> i64 {
    let s = raw.trim();
    if s.is_empty() {
        return 0;
    }
    let now = Local::now();
    let now_ms = now.timestamp_millis();

    // 相对时间：+30m / -2h / 90s
    if let Some(ms) = parse_relative(s) {
        let at = now_ms + ms;
        return if at > now_ms { at } else { 0 };
    }

    let dt: Option<NaiveDateTime> = NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M")
        .ok()
        .or_else(|| NaiveDateTime::parse_from_str(s, "%Y/%m/%d %H:%M").ok())
        .or_else(|| NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S").ok())
        .or_else(|| NaiveTime::parse_from_str(s, "%H:%M").ok().map(|t| now.date_naive().and_time(t)));

    let Some(dt) = dt else { return 0 };
    let Some(at) = Local.from_local_datetime(&dt).single().map(|v| v.timestamp_millis()) else {
        // 本地时间跳过（夏令时缺口）。
        return 0;
    };
    if at > now_ms {
        return at;
    }
    if !daily {
        return 0;
    }
    // 每天型：顺延到下一个同一时刻。
    let mut next = dt;
    for _ in 0..400 {
        next += Duration::days(1);
        if let Some(ms) = Local.from_local_datetime(&next).single().map(|v| v.timestamp_millis()) {
            if ms > now_ms {
                return ms;
            }
        }
    }
    0
}

/// `+30m` / `+2h` / `+90s` / `+1d`，返回毫秒增量。没有前缀时返回 None。
fn parse_relative(s: &str) -> Option<i64> {
    let rest = s.strip_prefix('+')?;
    let rest = rest.trim();
    let (num, unit) = rest.split_at(rest.find(|c: char| c.is_alphabetic())?);
    let n: i64 = num.trim().parse().ok()?;
    let ms = match unit.trim().to_lowercase().as_str() {
        "s" | "秒" => n * 1000,
        "m" | "分" => n * 60_000,
        "h" | "时" => n * 3_600_000,
        "d" | "天" => n * 86_400_000,
        _ => return None,
    };
    Some(ms)
}

/// `MM-dd HH:mm`，跨年时带上年份。
pub fn fmt(ms: i64) -> String {
    if ms <= 0 {
        return "—".into();
    }
    let Some(dt) = Local.timestamp_millis_opt(ms).single() else { return "—".into() };
    if dt.year() == Local::now().year() {
        dt.format("%m-%d %H:%M").to_string()
    } else {
        dt.format("%Y-%m-%d %H:%M").to_string()
    }
}

/// 完整格式（导出 / 日志用）。
pub fn fmt_full(ms: i64) -> String {
    match Local.timestamp_millis_opt(ms).single() {
        Some(dt) => dt.format("%Y-%m-%d %H:%M:%S").to_string(),
        None => "—".into(),
    }
}

/// 还有多久触发："3 小时后"。
pub fn humanize(ms: i64) -> String {
    let d = ms - crate::core::base::clock::now_ms();
    if d <= 0 {
        return "已到点".into();
    }
    let mins = d / 60_000;
    if mins < 1 {
        "不到 1 分钟".into()
    } else if mins < 60 {
        format!("{mins} 分钟后")
    } else if mins < 60 * 24 {
        format!("{} 小时后", mins / 60)
    } else {
        format!("{} 天后", mins / 60 / 24)
    }
}

/// 今天（或明天）的 `HH:MM` 对应的时间戳。
pub fn next_at(hour: u32, minute: u32) -> i64 {
    let now = Local::now();
    let mut d = NaiveDate::from_ymd_opt(now.year(), now.month(), now.day()).unwrap_or(now.date_naive());
    let t = NaiveTime::from_hms_opt(hour.min(23), minute.min(59), 0).unwrap_or_default();
    for _ in 0..3 {
        if let Some(ms) = Local.from_local_datetime(&d.and_time(t)).single().map(|v| v.timestamp_millis()) {
            if ms > now.timestamp_millis() {
                return ms;
            }
        }
        d += Duration::days(1);
    }
    0
}

/// 毫秒时间戳 → (时, 分)。
pub fn hm(ms: i64) -> (u32, u32) {
    match Local.timestamp_millis_opt(ms).single() {
        Some(dt) => (dt.hour(), dt.minute()),
        None => (0, 0),
    }
}

/// 是否到点了。`grace_ms` 是宽限：进程休眠醒来时可能已经过点几分钟，
/// 只要没过太久就该跑一次，而不是等下一天。
pub fn due(at: i64, now: i64, grace_ms: i64) -> bool {
    at > 0 && now >= at && now - at <= grace_ms.max(0)
}

/// 每天型的下一次：给定上次触发时间与"时:分"，算出明天的同一时刻。
pub fn next_daily(last: i64) -> i64 {
    let (h, m) = hm(last);
    let base = if last > 0 { last } else { crate::core::base::clock::now_ms() };
    let d = Local.timestamp_millis_opt(base).single().map(|v| v.date_naive()).unwrap_or_else(|| Local::now().date_naive());
    let t = NaiveTime::from_hms_opt(h, m, 0).unwrap_or_default();
    let mut day = d + Duration::days(1);
    for _ in 0..3 {
        if let Some(ms) = Local.from_local_datetime(&day.and_time(t)).single().map(|v| v.timestamp_millis()) {
            return ms;
        }
        day += Duration::days(1);
    }
    0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn relative() {
        assert_eq!(parse_relative("+30m"), Some(1_800_000));
        assert_eq!(parse_relative("+2h"), Some(7_200_000));
        assert_eq!(parse_relative("30m"), None);
    }

    #[test]
    fn due_window() {
        assert!(due(1000, 1000, 60_000));
        assert!(due(1000, 50_000, 60_000));
        assert!(!due(1000, 100_000, 60_000));
        assert!(!due(0, 100_000, 60_000));
    }

    #[test]
    fn hm_roundtrip() {
        let ms = next_at(9, 30);
        if ms > 0 {
            let (h, m) = hm(ms);
            assert_eq!((h, m), (9, 30));
        }
    }
}
