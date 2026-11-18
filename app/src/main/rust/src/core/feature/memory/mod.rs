//! 记忆块：一类是长期记忆（整段注入），一类是普通记忆（按相关度注入）。
//!
//! | 文件        | 职责                                             |
//! |-------------|--------------------------------------------------|
//! | `model.rs`  | 数据结构、规范化、去重指纹、切词                  |
//! | `rank.rs`   | 关键词召回打分、注入文本拼装、统计 
//!
//! 纯计算层：不碰数据库、不碰文件。宿主把行读出来塞进来，这里给出
//! 「哪些该注入、按什么顺序、拼成什么文本」。

pub mod model;
pub mod rank;

pub use model::{dedup_key, normalize, tokens, Hit, Item, KIND_LONG, KIND_NORMAL, MAX_CHARS};
pub use rank::{compose, rank, stats};

#[cfg(test)]
mod tests {
    use super::model::*;
    use super::rank::*;

    fn item(id: i64, text: &str, kind: u8, at: i64) -> Item {
        Item { id, text: text.into(), kind, at }
    }

    /// 规范化：换行压空格、超长按字符截断
    #[test]
    fn normalize_collapses_and_truncates() {
        assert_eq!(normalize("  a\n\n b\t c  "), "a b c");
        let long = "字".repeat(600);
        assert_eq!(normalize(&long).chars().count(), MAX_CHARS);
    } //规范化

    /// 去重指纹忽略标点与大小写
    #[test]
    fn dedup_ignores_punct_case() {
        assert_eq!(dedup_key("I like Rust."), dedup_key("i like  rust!"));
        assert_ne!(dedup_key("abc"), dedup_key("abd"));
    } //去重

    /// 中文 bigram + 英文词
    #[test]
    fn tokens_mix() {
        let t = tokens("我叫小明 Rust 很好");
        assert!(t.contains("小明"));
        assert!(t.contains("rust"));
    } //切词

    /// 相关的那条排第一
    #[test]
    fn rank_puts_relevant_first() {
        let now = 1_700_000_000_000;
        let items = vec![
            item(1, "用户偏好深色主题", KIND_NORMAL, now),
            item(2, "周末带猫去看医生", KIND_NORMAL, now),
            item(3, "项目用 Kotlin 和 Rust", KIND_NORMAL, now - 30 * 86_400_000),
        ];
        let hits = rank(&items, "主题要深色还是浅色", now, 3);
        assert_eq!(hits.first().map(|h| h.id), Some(1));
    } //排序

    /// full 不注入普通记忆，smart 才注入
    #[test]
    fn compose_modes() {
        let now = 1_700_000_000_000;
        let items = vec![
            item(1, "用户叫小明", KIND_LONG, now),
            item(2, "小明喜欢吃辣", KIND_NORMAL, now),
        ];
        let full = compose(&items, "full", 0, now, "小明");
        assert!(full.contains("长期记忆"));
        assert!(!full.contains("相关记忆"));
        let smart = compose(&items, "smart", 5, now, "小明喜欢吃辣吗");
        assert!(smart.contains("长期记忆"));
        assert!(smart.contains("相关记忆"));
    } //注入模式

    /// limit 分别限制两类记忆的条数
    #[test]
    fn compose_respects_limit() {
        let now = 1_700_000_000_000;
        let mut items = Vec::new();
        for i in 1..=10 {
            items.push(item(i, &format!("偏好第{i}条"), KIND_LONG, now - i));
        }
        let s = compose(&items, "full", 3, now, "");
        assert_eq!(s.matches("\n- ").count(), 3);
    } //条数上限
} //测试