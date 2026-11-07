//! 记忆打分。
//!
//! 目标：用户问"上次那个部署脚本在哪"时，把"项目部署脚本在 ~/work/deploy.sh"这条
//! 记忆排到前面，而不是把 200 条记忆全塞进提示词。
//!
//! 打分公式（四项相乘，再加短语加成）：
//!
//! ```text
//! score = idf × coverage × length_penalty × time_decay + phrase_bonus
//! ```
//!
//! - **idf**：词越常见越不值钱。所有记忆里都出现的"用户"不该有权重。
//! - **coverage**：查询里的词命中得越全越相关。只命中一个"的"不算。
//! - **length_penalty**：长记忆天然容易命中，压一压，避免"什么都沾一点"的长文霸榜。
//! - **time_decay**：半衰期 180 天。去年的偏好和上个月的偏好，权重不一样。
//! - **phrase_bonus**：整串查询原样出现，说明高度相关，直接加 0.3。
//!
//! 不引入向量检索：几百条记忆、本地设备、要快要省电。关键词打分足够好，
//! 而且**可解释**——用户问"为什么召回这条"，能指出命中了哪几个词。

use std::collections::{HashMap, HashSet};

/// 中文停用词（粗粒度）。这些词不该贡献 idf。
const STOP: &[&str] = &[
    "的", "了", "是", "在", "我", "你", "他", "她", "它", "我们", "你们", "他们", "这", "那",
    "一个", "一些", "什么", "怎么", "为什么", "可以", "需要", "然后", "但是", "因为", "所以",
    // 英文停用词只收最高频的那批。收太多会误伤 "git"、"api" 这种短但关键的词，
    // 所以按"语法词"而不是"短词"来挑。
    "the", "a", "an", "is", "are", "was", "were", "be", "been", "being", "to", "of", "and",
    "or", "in", "on", "at", "by", "for", "with", "from", "into", "about", "as", "it", "this",
    "that", "these", "those", "i", "you", "he", "she", "we", "they", "my", "your", "our",
    "do", "does", "did", "have", "has", "had", "not", "no", "if", "so", "but", "than", "then",
    "there", "here", "when", "what", "which", "who", "how", "all", "any", "can", "will",
    "would", "should", "could", "may", "might", "must", "up", "out", "over", "after", "before",
];

/// 分词：英文/数字按连续段，中文按 bigram（单字成词时保留单字）。
///
/// 为什么用 bigram 而不是词典分词：词典要几 MB，还分不干净专有名词；
/// bigram 对中文检索的召回率足够，代价是索引词变多——记忆只有几百条，无所谓。
pub fn terms(text: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut latin = String::new();
    let mut cjk: Vec<char> = Vec::new();

    let flush_latin = |buf: &mut String, out: &mut Vec<String>| {
        if buf.len() >= 2 {
            let w = buf.to_ascii_lowercase();
            if !STOP.contains(&w.as_str()) {
                out.push(w);
            }
        }
        buf.clear();
    };
    let flush_cjk = |buf: &mut Vec<char>, out: &mut Vec<String>| {
        if buf.len() == 1 {
            let c = buf[0].to_string();
            if !STOP.contains(&c.as_str()) {
                out.push(c);
            }
        } else {
            for w in buf.windows(2) {
                let t: String = w.iter().collect();
                if !STOP.contains(&t.as_str()) {
                    out.push(t);
                }
            }
        }
        buf.clear();
    };

    for ch in text.chars() {
        if is_cjk(ch) {
            flush_latin(&mut latin, &mut out);
            cjk.push(ch);
        } else if ch.is_alphanumeric() || ch == '_' || ch == '-' {
            flush_cjk(&mut cjk, &mut out);
            latin.push(ch);
        } else {
            flush_latin(&mut latin, &mut out);
            flush_cjk(&mut cjk, &mut out);
        }
    }
    flush_latin(&mut latin, &mut out);
    flush_cjk(&mut cjk, &mut out);
    out
}

fn is_cjk(ch: char) -> bool {
    let c = ch as u32;
    (0x3400..=0x4DBF).contains(&c) || (0x4E00..=0x9FFF).contains(&c) || (0x3040..=0x30FF).contains(&c)
}

/// 语料统计：每个词出现在多少条记忆里。
#[derive(Debug, Default)]
pub struct Corpus {
    pub total: usize,
    pub df: HashMap<String, usize>,
}

impl Corpus {
    pub fn build<'a>(docs: impl IntoIterator<Item = &'a str>) -> Self {
        let mut df: HashMap<String, usize> = HashMap::new();
        let mut total = 0;
        for d in docs {
            total += 1;
            let uniq: HashSet<String> = terms(d).into_iter().collect();
            for t in uniq {
                *df.entry(t).or_insert(0) += 1;
            }
        }
        Self { total, df }
    }

    /// 逆文档频率。+1 平滑，避免除零；词表里没有的词给最高权重（可能是专有名词）。
    pub fn idf(&self, term: &str) -> f64 {
        let n = self.total.max(1) as f64;
        let df = *self.df.get(term).unwrap_or(&0) as f64;
        (1.0 + n / (1.0 + df)).ln()
    }
}

/// 一条记忆的打分输入。
#[derive(Debug, Clone, Copy)]
pub struct Doc<'a> {
    pub text: &'a str,
    pub at: i64,
}

/// 打分。
pub fn score(query: &str, doc: &Doc<'_>, corpus: &Corpus, now: i64) -> f64 {
    let q_terms: Vec<String> = {
        let mut v = terms(query);
        v.sort();
        v.dedup();
        v
    };
    if q_terms.is_empty() {
        return 0.0;
    }
    let d_text = doc.text.to_lowercase();
    let d_terms: HashSet<String> = terms(doc.text).into_iter().collect();

    let mut hit = 0.0;
    let mut total_idf = 0.0;
    for t in &q_terms {
        let idf = corpus.idf(t);
        total_idf += idf;
        if d_terms.contains(t) || d_text.contains(t.as_str()) {
            hit += idf;
        }
    }
    if hit <= 0.0 {
        return 0.0;
    }
    let coverage = hit / total_idf.max(0.01);

    // 长度惩罚：以 60 字为基准，越长分越低，但不会低到 0
    let chars = doc.text.chars().count() as f64;
    let length_penalty = 1.0 / (1.0 + (chars / 60.0).ln_1p());

    // 时间衰减：半衰期 180 天
    let days = ((now - doc.at).max(0) as f64) / 86_400_000.0;
    let time_decay = 0.5f64.powf(days / 180.0);

    let mut s = hit * coverage * length_penalty * (0.35 + 0.65 * time_decay);

    // 短语加成：整串查询原样出现
    let q = query.trim().to_lowercase();
    if q.chars().count() >= 2 && d_text.contains(&q) {
        s += 0.3;
    }
    s
}

/// 从候选里挑前 `top` 条，按分数降序。
pub fn rank<'a>(query: &str, docs: Vec<Doc<'a>>, corpus: &Corpus, now: i64, top: usize, min: f64) -> Vec<(usize, f64)> {
    let mut scored: Vec<(usize, f64)> = docs
        .iter()
        .enumerate()
        .map(|(i, d)| (i, score(query, d, corpus, now)))
        .filter(|(_, s)| *s >= min)
        .collect();
    scored.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    scored.truncate(top);
    scored
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn terms_cjk_bigram() {
        let t = terms("部署脚本在哪");
        assert!(t.contains(&"部署".to_string()));
        assert!(t.contains(&"脚本".to_string()));
        // 停用词不出现
        assert!(!t.contains(&"在".to_string()));
    }

    #[test]
    fn terms_latin() {
        let t = terms("deploy script at /opt/app");
        assert!(t.contains(&"deploy".to_string()));
        assert!(t.contains(&"script".to_string()));
        assert!(!t.contains(&"at".to_string()));
    }

    #[test]
    fn relevant_scores_higher() {
        let docs = ["项目部署脚本在 ~/work/deploy.sh", "用户喜欢简洁的回答", "今天天气不错"];
        let corpus = Corpus::build(docs.iter().copied());
        let now = crate::core::base::clock::now_ms();
        let list: Vec<Doc<'_>> = docs.iter().map(|t| Doc { text: t, at: now }).collect();
        let r = rank("部署脚本在哪", list, &corpus, now, 3, 0.0);
        assert_eq!(r[0].0, 0, "第一条应该最相关：{r:?}");
    }

    #[test]
    fn common_word_has_low_idf() {
        let docs = ["用户喜欢简洁", "用户说要用中文", "用户住在杭州"];
        let corpus = Corpus::build(docs.iter().copied());
        let common = corpus.idf("用户");
        let rare = corpus.idf("杭州");
        assert!(rare > common, "{rare} vs {common}");
    }

    #[test]
    fn old_memory_decays() {
        let corpus = Corpus::build(["部署脚本在 a", "别的"]);
        let now = crate::core::base::clock::now_ms();
        let fresh = Doc { text: "部署脚本在 a", at: now };
        let old = Doc { text: "部署脚本在 a", at: now - 400 * 86_400_000 };
        assert!(score("部署脚本", &fresh, &corpus, now) > score("部署脚本", &old, &corpus, now));
    }
}
