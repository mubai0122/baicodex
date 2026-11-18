//! 记忆的排序与注入：关键词召回打分、注入文本拼装、统计。
//!
//! 打分公式：IDF × 覆盖率 × 长度惩罚 × 时间加成。
//! 记忆条数很少（百级），直接在内存里全文扫一遍，不建索引。

use std::collections::{HashMap, HashSet};

use super::model::{tokens, Hit, Item, KIND_LONG, KIND_NORMAL};

/// 关键词召回：IDF × 覆盖率 × 长度惩罚 × 时间加成
///
/// - IDF：只在少数记忆里出现的词命中，信号更强；
/// - 覆盖率：命中的查询词元越多越相关；
/// - 长度惩罚：长记忆天然更容易命中，要压一压；
/// - 时间加成：最近的记忆最多 +25%（90 天半衰）。
pub fn rank(items: &[Item], query: &str, now_ms: i64, limit: usize) -> Vec<Hit> {
    let toks = tokens(query);
    if toks.is_empty() || items.is_empty() {
        return Vec::new();
    }
    let docs: Vec<(usize, HashSet<String>)> = items
        .iter()
        .enumerate()
        .map(|(i, it)| (i, tokens(&it.text)))
        .collect();
    //文档频率
    let mut df: HashMap<&str, usize> = HashMap::new();
    for (_, set) in &docs {
        for t in set {
            *df.entry(t.as_str()).or_insert(0) += 1;
        }
    }
    let n = docs.len() as f64;
    let n_tok = toks.len() as f64;
    let mut hits: Vec<Hit> = Vec::new();
    for (idx, set) in &docs {
        let mut score = 0.0f64;
        let mut cover = 0usize;
        for t in &toks {
            if set.contains(t) {
                let d = *df.get(t.as_str()).unwrap_or(&0) as f64;
                score += (1.0f64 + n / (1.0f64 + d)).ln();
                cover += 1;
            }
        }
        if score <= 0.0 {
            continue;
        }
        score *= 1.0 + cover as f64 / n_tok;
        let chars = items[*idx].text.chars().count() as f64;
        score /= 1.0 + chars / 200.0;
        let days = ((now_ms - items[*idx].at).max(0) as f64) / 86_400_000.0;
        score *= 1.0 + 0.25 * (-days / 90.0).exp();
        hits.push(Hit { id: items[*idx].id, score });
    }
    hits.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));
    hits.truncate(limit.clamp(1, 50));
    hits
} //关键词召回

/// 拼装注入文本
///
/// `mode`：
/// - `"full"`  只注入长期记忆（整段，稳定可预期）；
/// - `"smart"` 长期记忆整段 + 与 `query` 相关的普通记忆前 `limit` 条。
///
/// `limit` 是**注入条数上限**，对两类记忆分别生效；0 表示不限制。
pub fn compose(items: &[Item], mode: &str, limit: usize, now_ms: i64, query: &str) -> String {
    //长期记忆按时间倒序（新的更贴近现状）
    let mut long: Vec<&Item> = items.iter().filter(|i| i.kind == KIND_LONG).collect();
    long.sort_by(|a, b| b.at.cmp(&a.at));
    if limit > 0 {
        long.truncate(limit);
    }
    let mut out = String::new();
    if !long.is_empty() {
        out.push_str("长期记忆（用户没删就一直算数，与本轮对话同等可信）：\n");
        for it in &long {
            out.push_str("- ");
            out.push_str(&it.text);
            out.push('\n');
        }
    }
    if mode == "smart" {
        let normal: Vec<Item> = items.iter().filter(|i| i.kind == KIND_NORMAL).cloned().collect();
        let n_limit = if limit > 0 { limit } else { 8 };
        let hits = rank(&normal, query, now_ms, n_limit);
        if !hits.is_empty() {
            out.push_str("相关记忆（可能与当前问题有关，若与用户当轮说法冲突，以当轮为准）：\n");
            for h in hits {
                if let Some(it) = normal.iter().find(|i| i.id == h.id) {
                    out.push_str("- ");
                    out.push_str(&it.text);
                    out.push('\n');
                }
            }
        }
    }
    out
} //拼装注入文本

/// 统计：给设置页展示「长期多少条 / 普通多少条 / 最老与最新」，返回 JSON
pub fn stats(items: &[Item], now_ms: i64) -> String {
    let long_n = items.iter().filter(|i| i.kind == KIND_LONG).count();
    let normal_n = items.len() - long_n;
    let oldest = items.iter().map(|i| i.at).filter(|t| *t > 0).min().unwrap_or(0);
    let newest = items.iter().map(|i| i.at).max().unwrap_or(0);
    let chars: usize = items.iter().map(|i| i.text.chars().count()).sum();
    let days = |t: i64| -> i64 {
        if t <= 0 {
            0
        } else {
            ((now_ms - t).max(0)) / 86_400_000
        }
    };
    let json = serde_json::json!({
        "long": long_n,
        "normal": normal_n,
        "chars": chars,
        "oldestDays": days(oldest),
        "newestDays": days(newest),
    });
    json.to_string()
} //统计
