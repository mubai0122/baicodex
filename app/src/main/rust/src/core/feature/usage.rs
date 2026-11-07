//! 用量与花费。
//!
//! 三件事：把一次调用的 token 折算成钱、按天汇总、给界面一个"这个月花了多少"。
//!
//! ## 为什么价格要能配
//!
//! 同一家的不同模型差十倍，中转站又自己定价。写死一张价目表只会让数字越来越不准。
//! 配置里给"输入价 / 输出价 / 缓存价"三个数（元/百万 token），算不出来就显示 token 数
//! 而不是乱猜钱。
//!
//! ## 账本只存 token，不存钱
//!
//! 钱是"算出来的"，不是"记下来的"。改了单价之后历史记录应该按新单价重算
//! ——否则用户改了价格，历史数字对不上，会以为统计坏了。

use serde::Serialize;

use crate::core::base::clock;
use crate::core::feature::config::schema::Config;
use crate::core::feature::storage::Db;

/// 一次调用的用量。
#[derive(Debug, Clone, Default, Serialize)]
pub struct Turn {
    pub prompt: i64,
    pub cached: i64,
    pub completion: i64,
    /// 毫秒。
    pub dur_ms: i64,
}

impl Turn {
    pub fn total(&self) -> i64 {
        self.prompt.max(0) + self.completion.max(0)
    }

    pub fn empty(&self) -> bool {
        self.total() == 0
    }

    /// 缓存命中率（0~1）。没有 prompt 时返回 0。
    pub fn cache_rate(&self) -> f64 {
        if self.prompt <= 0 {
            return 0.0;
        }
        (self.cached.max(0) as f64 / self.prompt as f64).clamp(0.0, 1.0)
    }
}

/// 折算成钱。
///
/// 缓存命中按 `price_cache` 算；没配就按输入价的一半（大多数厂商的行为）。
pub fn cost(prompt: i64, cached: i64, completion: i64, cfg: &Config) -> f64 {
    let c = &cfg.usage;
    if !c.enabled {
        return 0.0;
    }
    let pin = c.price_in.max(0.0) / 1_000_000.0;
    let pout = c.price_out.max(0.0) / 1_000_000.0;
    let pcache = if c.price_cache > 0.0 {
        c.price_cache.max(0.0) / 1_000_000.0
    } else {
        pin * 0.5
    };
    let p = prompt.max(0);
    let hit = cached.clamp(0, p);
    let fresh = p - hit;
    fresh as f64 * pin + hit as f64 * pcache + completion.max(0) as f64 * pout
}

/// 金额展示：小于一分钱也要显示，否则用户以为免费。
pub fn money(v: f64, cfg: &Config) -> String {
    let cur = if cfg.usage.currency.trim().is_empty() { "¥" } else { cfg.usage.currency.trim() };
    if v <= 0.0 {
        format!("{cur}0")
    } else if v < 0.01 {
        format!("{cur}{v:.4}")
    } else if v < 1.0 {
        format!("{cur}{v:.3}")
    } else {
        format!("{cur}{v:.2}")
    }
}

/// 记一次调用。
pub fn record(db: &Db, cfg: &Config, provider: &str, model: &str, session: &str, t: &Turn) {
    if !cfg.usage.enabled || t.empty() {
        return;
    }
    let _ = db.record_usage(
        clock::now_ms(),
        provider,
        model,
        session,
        t.prompt.max(0),
        t.cached.max(0),
        t.completion.max(0),
    );
}

/// 汇总报告（界面"用量"页）。
#[derive(Debug, Clone, Default, Serialize)]
pub struct Report {
    pub calls: i64,
    pub prompt: i64,
    pub cached: i64,
    pub completion: i64,
    pub cost: f64,
    pub money: String,
    /// 最近 7 天：(日期, token, 花费)。
    pub daily: Vec<(String, i64, f64)>,
    /// 今天按模型：(模型, token, 花费)。
    pub models: Vec<(String, i64, f64)>,
}

impl Report {
    pub fn total_tokens(&self) -> i64 {
        self.prompt.max(0) + self.completion.max(0)
    }

    pub fn cache_rate(&self) -> f64 {
        if self.prompt <= 0 {
            0.0
        } else {
            (self.cached.max(0) as f64 / self.prompt as f64).clamp(0.0, 1.0)
        }
    }
}

/// 出报告。数据库出错时返回空报告而不是报错——用量页崩掉没有意义。
pub fn report(db: &Db, cfg: &Config) -> Report {
    let mut r = Report::default();
    if let Ok((calls, prompt, cached, completion)) = db.usage_totals() {
        r.calls = calls;
        r.prompt = prompt;
        r.cached = cached;
        r.completion = completion;
        r.cost = cost(prompt, cached, completion, cfg);
    }
    r.money = money(r.cost, cfg);

    // 最近 7 天：账本按天聚合给的是 (日期, prompt, cached, completion)。
    let days = db.usage_recent_days(7).unwrap_or_default();
    let daily = db.usage_daily().unwrap_or_default();
    let mut rows: Vec<(String, i64, f64)> = Vec::new();
    for (day, p, c, comp) in &daily {
        if !days.iter().any(|(d, _, _)| d == day) {
            continue;
        }
        rows.push((day.clone(), p + comp, cost(*p, *c, *comp, cfg)));
    }
    rows.sort_by(|a, b| b.0.cmp(&a.0));
    r.daily = rows;

    // 今天按模型拆分（改单价时能看出是哪个模型花的）。
    let today = clock::today();
    let per_model = db.usage_day_model().unwrap_or_default();
    let mut by_model: Vec<(String, i64, f64)> = Vec::new();
    for (day, model, p, c, comp) in per_model {
        if day != today {
            continue;
        }
        by_model.push((
            if model.is_empty() { "（未知）".into() } else { model },
            p + comp,
            cost(p, c, comp, cfg),
        ));
    }
    by_model.sort_by(|a, b| b.1.cmp(&a.1));
    r.models = by_model;

    r
}

/// 一行摘要，状态栏用。
pub fn status_line(db: &Db, cfg: &Config) -> String {
    let r = report(db, cfg);
    if r.calls == 0 {
        return "还没有用量记录".into();
    }
    format!(
        "{} 次调用，{} tokens，{}",
        r.calls,
        crate::core::base::bytes::human_size(r.total_tokens().max(0) as u64),
        r.money
    )
}

/// 本次会话的用量与花费。
pub fn session_line(db: &Db, cfg: &Config, sid: &str) -> String {
    let Ok((p, comp)) = db.usage_session(sid) else { return String::new() };
    if p + comp <= 0 {
        return String::new();
    }
    let c = cost(p, 0, comp, cfg);
    format!("本会话 {p} 入 / {comp} 出，{}", money(c, cfg))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cost_with_cache() {
        let mut cfg = Config::default();
        cfg.usage.price_in = 1.0;
        cfg.usage.price_out = 2.0;
        // 100万入（其中50万缓存）：50万×1.0 + 50万×0.5 + 100万×2.0 = 0.5+0.25+2 = 2.75
        let c = cost(1_000_000, 500_000, 1_000_000, &cfg);
        assert!((c - 2.75).abs() < 1e-6, "got {c}");
    }

    #[test]
    fn money_small() {
        let cfg = Config::default();
        assert_eq!(money(0.0, &cfg), "¥0");
        assert!(money(0.001, &cfg).starts_with("¥0.00"));
    }

    #[test]
    fn cache_rate_bounds() {
        let t = Turn { prompt: 100, cached: 150, completion: 0, dur_ms: 0 };
        assert!((t.cache_rate() - 1.0).abs() < 1e-9);
    }
}
