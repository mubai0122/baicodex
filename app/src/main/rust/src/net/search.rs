//! 联网搜索：Tavily / Bing / Google CSE，按设置里选的那个分发。
//!
//! 统一返回"标题 + 链接 + 摘要"的纯文本，模型直接读，不用二次解析 JSON。
//!
//! ## 为什么要三个后端
//!
//! - Tavily 质量最好但要 key、按次计费；
//! - Google CSE 要 key + cx，配额小但中文结果好；
//! - Bing 不用 key（抓 HTML），稳定性和结构都靠不住——**降级用它，别当主力**。
//!
//! ## 失败也要有内容
//!
//! 搜不到时返回一句话说明原因（没 key / 被风控 / 没结果），而不是空串。
//! 空串会让模型以为"搜到了但内容为空"，然后反复重试。

use std::time::Duration;

use serde_json::{json, Value};

use super::client;
use crate::core::base::text;
use crate::core::feature::config::schema::Config;

/// 后端。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Backend {
    Tavily,
    Bing,
    Google,
}

impl Backend {
    pub fn id(self) -> &'static str {
        match self {
            Backend::Tavily => "tavily",
            Backend::Bing => "bing",
            Backend::Google => "google",
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            Backend::Tavily => "Tavily",
            Backend::Bing => "Bing",
            Backend::Google => "Google",
        }
    }

    pub fn parse(s: &str) -> Option<Backend> {
        match s.trim().to_lowercase().as_str() {
            "tavily" => Some(Backend::Tavily),
            "bing" => Some(Backend::Bing),
            "google" | "google_cse" | "cse" => Some(Backend::Google),
            _ => None,
        }
    }

    /// 要不要 key。
    pub fn needs_key(self) -> bool {
        !matches!(self, Backend::Bing)
    }
}

/// 密钥（由上层从凭据库取好传进来，本层不碰存储）。
#[derive(Debug, Clone, Default)]
pub struct Keys {
    pub tavily: String,
    pub google: String,
}

/// 搜索参数。
#[derive(Debug, Clone)]
pub struct Opts {
    pub max: usize,
    /// `general` / `news`。
    pub topic: String,
    /// news 往回几天。0 = 不限。
    pub days: i64,
    /// `basic` / `advanced`。
    pub depth: String,
}

impl Default for Opts {
    fn default() -> Self {
        Self { max: 6, topic: "general".into(), days: 0, depth: "basic".into() }
    }
}

/// 结果。
#[derive(Debug, Clone)]
pub struct Outcome {
    pub ok: bool,
    pub text: String,
    pub backend: &'static str,
}

impl Outcome {
    fn ok(text: impl Into<String>, b: Backend) -> Self {
        Self { ok: true, text: text.into(), backend: b.id() }
    }
    fn err(text: impl Into<String>, b: Backend) -> Self {
        Self { ok: false, text: text.into(), backend: b.id() }
    }
}

/// 设置里选的后端。
pub fn selected(cfg: &Config) -> Option<Backend> {
    Backend::parse(&cfg.search.provider)
}

/// 搜索。
pub async fn search(cfg: &Config, keys: &Keys, query: &str, opts: &Opts) -> Outcome {
    let Some(b) = selected(cfg) else {
        return Outcome {
            ok: false,
            text: "没有选搜索平台：设置 → 扩展 → 搜索里选一个（tavily / bing / google）".into(),
            backend: "none",
        };
    };
    let q = query.trim();
    if q.is_empty() {
        return Outcome::err("搜索词是空的", b);
    }
    let n = opts.max.clamp(1, 12);

    match b {
        Backend::Tavily => {
            if keys.tavily.trim().is_empty() {
                return Outcome::err("Tavily 需要 API Key（设置 → 扩展 → 搜索）", b);
            }
            let host = if cfg.search.tavily_host.trim().is_empty() {
                "https://api.tavily.com"
            } else {
                cfg.search.tavily_host.trim()
            };
            tavily(host, &keys.tavily, q, n, opts).await
        }
        Backend::Google => {
            let key = keys.google.trim();
            let cx = cfg.search.google_cx.trim();
            if key.is_empty() || cx.is_empty() {
                return Outcome::err("Google CSE 需要 API Key 与 cx 两个值", b);
            }
            google(key, cx, q, n).await
        }
        Backend::Bing => {
            let host = if cfg.search.bing_host.trim().is_empty() {
                "https://www.bing.com"
            } else {
                cfg.search.bing_host.trim().trim_end_matches('/')
            };
            bing(host, q, n).await
        }
    }
}

async fn tavily(host: &str, key: &str, query: &str, n: usize, opts: &Opts) -> Outcome {
    let mut body = json!({
        "api_key": key,
        "query": query,
        "max_results": n,
        "search_depth": if opts.depth == "advanced" { "advanced" } else { "basic" },
        "topic": if opts.topic == "news" { "news" } else { "general" },
        "include_answer": true,
    });
    if opts.topic == "news" && opts.days > 0 {
        body["days"] = json!(opts.days);
    }

    let url = format!("{}/search", host.trim_end_matches('/'));
    let resp = client::shared()
        .post(&url)
        .json(&body)
        .timeout(Duration::from_secs(45))
        .send()
        .await;
    let v: Value = match resp {
        Ok(r) if r.status().is_success() => match r.json().await {
            Ok(v) => v,
            Err(e) => return Outcome::err(format!("Tavily 返回的不是 JSON：{e}"), Backend::Tavily),
        },
        Ok(r) => {
            let code = r.status().as_u16();
            let hint = match code {
                401 | 403 => "（key 无效）",
                429 => "（额度用完了）",
                432 | 433 => "（套餐不支持这个参数）",
                _ => "",
            };
            return Outcome::err(format!("Tavily 请求失败：HTTP {code}{hint}"), Backend::Tavily);
        }
        Err(e) => return Outcome::err(format!("Tavily 请求失败：{e}"), Backend::Tavily),
    };

    let mut out = String::new();
    if let Some(a) = v["answer"].as_str() {
        if !a.trim().is_empty() {
            out.push_str(&format!("摘要：{}\n\n", a.trim()));
        }
    }
    let results = v["results"].as_array().cloned().unwrap_or_default();
    for (i, r) in results.iter().enumerate() {
        let title = r["title"].as_str().unwrap_or("（无标题）");
        let link = r["url"].as_str().unwrap_or("");
        let content = text::truncate(r["content"].as_str().unwrap_or("").trim(), 320);
        out.push_str(&format!("{}. {title}\n   {link}\n   {content}\n\n", i + 1));
    }
    if out.trim().is_empty() {
        return Outcome::ok("没有搜到结果（换个说法，或把 topic 换成 news 试试）", Backend::Tavily);
    }
    Outcome::ok(out.trim().to_string(), Backend::Tavily)
}

async fn google(key: &str, cx: &str, query: &str, n: usize) -> Outcome {
    let url = format!(
        "https://www.googleapis.com/customsearch/v1?key={}&cx={}&q={}&num={}",
        text::url_encode(key),
        text::url_encode(cx),
        text::url_encode(query),
        n.min(10)
    );
    let resp = client::shared().get(&url).send().await;
    let v: Value = match resp {
        Ok(r) if r.status().is_success() => match r.json().await {
            Ok(v) => v,
            Err(e) => return Outcome::err(format!("Google 返回的不是 JSON：{e}"), Backend::Google),
        },
        Ok(r) => {
            let code = r.status().as_u16();
            let body = r.text().await.unwrap_or_default();
            return Outcome::err(
                format!("Google 请求失败：HTTP {code} {}", text::truncate(body.trim(), 200)),
                Backend::Google,
            );
        }
        Err(e) => return Outcome::err(format!("Google 请求失败：{e}"), Backend::Google),
    };
    let mut out = String::new();
    for (i, item) in v["items"].as_array().cloned().unwrap_or_default().iter().enumerate() {
        out.push_str(&format!(
            "{}. {}\n   {}\n   {}\n\n",
            i + 1,
            item["title"].as_str().unwrap_or("（无标题）"),
            item["link"].as_str().unwrap_or(""),
            text::truncate(item["snippet"].as_str().unwrap_or("").trim(), 320)
        ));
    }
    if out.trim().is_empty() {
        return Outcome::ok("没有搜到结果", Backend::Google);
    }
    Outcome::ok(out.trim().to_string(), Backend::Google)
}

/// Bing 网页版抓取。结构随时会变，解析失败就明说，不假装有结果。
async fn bing(host: &str, query: &str, n: usize) -> Outcome {
    let url = format!("{host}/search?q={}&count={n}&setlang=zh-CN", text::url_encode(query));
    let resp = client::shared()
        .get(&url)
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .send()
        .await;
    let html = match resp {
        Ok(r) if r.status().is_success() => r.text().await.unwrap_or_default(),
        Ok(r) => return Outcome::err(format!("Bing 请求失败：HTTP {}", r.status().as_u16()), Backend::Bing),
        Err(e) => return Outcome::err(format!("Bing 请求失败：{e}"), Backend::Bing),
    };

    let block = match regex::Regex::new(r#"(?is)<li class="b_algo".*?</li>"#) {
        Ok(r) => r,
        Err(_) => return Outcome::err("内部正则编译失败", Backend::Bing),
    };
    let head = regex::Regex::new(r#"(?is)<h2[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>"#).ok();
    let para = regex::Regex::new(r"(?is)<p[^>]*>(.*?)</p>").ok();
    let tag = regex::Regex::new(r"(?s)<[^>]+>").ok();

    let mut out = String::new();
    for (i, b) in block.find_iter(&html).enumerate() {
        if i >= n {
            break;
        }
        let body = b.as_str();
        let (Some(head), Some(tag)) = (head.as_ref(), tag.as_ref()) else { continue };
        let Some(c) = head.captures(body) else { continue };
        let href = c[1].to_string();
        let title = tag.replace_all(&c[2], "").trim().to_string();
        let desc = para
            .as_ref()
            .and_then(|p| p.captures(body))
            .map(|c| tag.replace_all(&c[1], "").trim().to_string())
            .unwrap_or_default();
        if title.is_empty() {
            continue;
        }
        out.push_str(&format!("{}. {title}\n   {href}\n   {}\n\n", i + 1, text::truncate(&desc, 320)));
    }
    if out.trim().is_empty() {
        return Outcome::ok(
            "Bing 没有解析出结果（页面结构可能变了，或被风控拦了）。建议换 tavily / google。",
            Backend::Bing,
        );
    }
    Outcome::ok(out.trim().to_string(), Backend::Bing)
}

/// 直接给 URL 抓正文（搜索之外也常用，比如"看看这个页面"）。
pub async fn fetch_text(url: &str, max_chars: usize) -> (bool, String) {
    match super::browser::open(url).await {
        Ok(text) => (true, text::truncate(&text, max_chars.clamp(200, 60_000))),
        Err(e) => (false, e),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn backend_parse() {
        assert_eq!(Backend::parse("Tavily"), Some(Backend::Tavily));
        assert_eq!(Backend::parse("google_cse"), Some(Backend::Google));
        assert_eq!(Backend::parse("duck"), None);
        assert!(!Backend::Bing.needs_key());
    }
}
