//! 时间与日期。
//!
//! 全库只用两种时间表示：
//! - **毫秒时间戳** `i64`：存库、算耗时、排序。
//! - **本地日期字符串** `YYYY-MM-DD`：给用户看、按天聚合。
//!
//! 不存 `NaiveDateTime`、不存带时区的字符串——跨设备同步时这些都会变成坑。

use chrono::{Datelike, Duration, Local, NaiveDate, TimeZone, Timelike};

/// 当前毫秒时间戳。
pub fn now_ms() -> i64 {
    Local::now().timestamp_millis()
}

/// 当前秒时间戳。
pub fn now_s() -> i64 {
    Local::now().timestamp()
}

/// 今天，`YYYY-MM-DD`。
pub fn today() -> String {
    Local::now().format("%Y-%m-%d").to_string()
}

/// 把毫秒时间戳格式化成 `YYYY-MM-DD HH:MM`。
pub fn fmt_minute(ms: i64) -> String {
    match Local.timestamp_millis_opt(ms).single() {
        Some(t) => t.format("%Y-%m-%d %H:%M").to_string(),
        None => String::new(),
    }
}

/// `HH:MM`。时间戳非法时返回空串，让调用方自己决定显示什么。
pub fn fmt_hm(ms: i64) -> String {
    match Local.timestamp_millis_opt(ms).single() {
        Some(t) => t.format("%H:%M").to_string(),
        None => String::new(),
    }
}

/// 只要日期部分。
pub fn fmt_date(ms: i64) -> String {
    match Local.timestamp_millis_opt(ms).single() {
        Some(t) => t.format("%Y-%m-%d").to_string(),
        None => String::new(),
    }
}

/// 人性化相对时间。超过 30 天直接给日期，别写"97 天前"这种没用的。
pub fn humanize(ms: i64) -> String {
    if ms <= 0 {
        return String::new();
    }
    let diff = now_ms() - ms;
    if diff < 0 {
        // 未来时间：定时任务会用到
        let d = -diff;
        return if d < 60_000 {
            "即将".into()
        } else if d < 3_600_000 {
            format!("{} 分钟后", d / 60_000)
        } else if d < 86_400_000 {
            format!("{} 小时后", d / 3_600_000)
        } else {
            format!("{} 天后", d / 86_400_000)
        };
    }
    const MIN: i64 = 60_000;
    const HOUR: i64 = 3_600_000;
    const DAY: i64 = 86_400_000;
    if diff < MIN {
        "刚刚".into()
    } else if diff < HOUR {
        format!("{} 分钟前", diff / MIN)
    } else if diff < DAY {
        format!("{} 小时前", diff / HOUR)
    } else if diff < 30 * DAY {
        format!("{} 天前", diff / DAY)
    } else {
        fmt_date(ms)
    }
}

/// 耗时：`820ms` / `3.4s` / `1m12s`。
pub fn human_ms(ms: i64) -> String {
    if ms < 0 {
        return "-".into();
    }
    if ms < 1000 {
        format!("{ms}ms")
    } else if ms < 60_000 {
        format!("{:.1}s", ms as f64 / 1000.0)
    } else if ms < 3_600_000 {
        let m = ms / 60_000;
        let s = (ms % 60_000) / 1000;
        format!("{m}m{s}s")
    } else {
        let h = ms / 3_600_000;
        let m = (ms % 3_600_000) / 60_000;
        format!("{h}h{m}m")
    }
}

/// 一天里的时段，给提示词用（"现在是深夜，用户可能想快点结束"）。
pub fn part_of_day() -> &'static str {
    match Local::now().hour() {
        0..=4 => "深夜",
        5..=7 => "清晨",
        8..=10 => "上午",
        11..=12 => "中午",
        13..=17 => "下午",
        18..=21 => "晚上",
        _ => "夜里",
    }
}

/// 星期几（中文单字）。
pub fn weekday_cn() -> &'static str {
    match Local::now().weekday().num_days_from_monday() {
        0 => "一",
        1 => "二",
        2 => "三",
        3 => "四",
        4 => "五",
        5 => "六",
        _ => "日",
    }
}

/// 校验 `YYYY-MM-DD` 是否是真实存在的日期（2 月 30 号要被判错）。
pub fn valid_date(s: &str) -> bool {
    if s.len() != 10 {
        return false;
    }
    NaiveDate::parse_from_str(s, "%Y-%m-%d").is_ok()
}

/// 日期加减天数，返回 `YYYY-MM-DD`。日期非法时返回 None。
pub fn shift_days(date: &str, days: i64) -> Option<String> {
    let d = NaiveDate::parse_from_str(date, "%Y-%m-%d").ok()?;
    Some((d + Duration::days(days)).format("%Y-%m-%d").to_string())
}

/// 两个日期相差多少天（b - a）。任一非法返回 None。
pub fn days_between(a: &str, b: &str) -> Option<i64> {
    let a = NaiveDate::parse_from_str(a, "%Y-%m-%d").ok()?;
    let b = NaiveDate::parse_from_str(b, "%Y-%m-%d").ok()?;
    Some((b - a).num_days())
}

/// 解析 `HH:MM`，返回当天的分钟数。给定时任务用。
pub fn parse_hm(s: &str) -> Option<u32> {
    let (h, m) = s.split_once(':')?;
    let h: u32 = h.trim().parse().ok()?;
    let m: u32 = m.trim().parse().ok()?;
    if h > 23 || m > 59 {
        return None;
    }
    Some(h * 60 + m)
}

/// 把当天的 `HH:MM` 换算成毫秒时间戳；已经过了就顺延到明天。
///
/// 定时任务每天跑一次，这个"顺延"决定了它不会在 00:00 补跑昨天的那次。
pub fn next_at_hm(hm: &str) -> Option<i64> {
    let mins = parse_hm(hm)?;
    let now = Local::now();
    let today = now.date_naive();
    let naive = today.and_hms_opt(mins / 60, mins % 60, 0)?;
    let dt = Local.from_local_datetime(&naive).single()?;
    let mut ts = dt.timestamp_millis();
    if ts <= now.timestamp_millis() {
        let tomorrow = Local
            .from_local_datetime(&(today + Duration::days(1)).and_hms_opt(mins / 60, mins % 60, 0)?)
            .single()?;
        ts = tomorrow.timestamp_millis();
    }
    Some(ts)
}

/// 距离下一个整点还有多少毫秒。调度器用它决定睡多久。
pub fn ms_to_next_minute() -> i64 {
    let now = Local::now();
    let sec = now.second() as i64;
    let nano = now.nanosecond() as i64;
    60_000 - (sec * 1000 + nano / 1_000_000)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn date_math() {
        assert!(valid_date("2024-02-29"));
        assert!(!valid_date("2023-02-29"));
        assert_eq!(shift_days("2024-02-28", 1).as_deref(), Some("2024-02-29"));
        assert_eq!(days_between("2024-01-01", "2024-01-31"), Some(30));
    }

    #[test]
    fn hm() {
        assert_eq!(parse_hm("08:05"), Some(485));
        assert_eq!(parse_hm("24:00"), None);
        assert_eq!(parse_hm("8:5"), Some(485));
    }
}
