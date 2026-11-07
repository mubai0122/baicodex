//! 内置浏览器：直接抓页面，不弹外置 App。
//!
//! 真正的渲染引擎（WebView / CDP）由平台层提供；这一层只做**静态抓取**：
//! 取正文、列链接、找文字。控件级操作（点击 / 输入 / 执行 JS）在没有渲染引擎时
//! 明确说明不可用，而不是假装点了——假装成功比报错更糟，模型会以为页面状态变了。
//!
//! ## 会话状态
//!
//! 记住当前 URL、页面 HTML、前进 / 后退栈。多个会话共享一份（浏览是全局的），
//! 锁只在读写字段时短暂持有，**不跨 await**：抓取过程中界面不能卡住。

use std::sync::{Mutex, OnceLock};

use regex::Regex;

use super::client;
use crate::core::base::text;

/// 一次抓取的 HTML 上限，超过就截（再大也没法提取完整正文）。
const MAX_HTML: usize = 4 * 1024 * 1024;

#[derive(Debug, Default)]
struct State {
    url: String,
    html: String,
    back: Vec<String>,
    forward: Vec<String>,
}

fn slot() -> &'static Mutex<State> {
    static S: OnceLock<Mutex<State>> = OnceLock::new();
    S.get_or_init(|| Mutex::new(State::default()))
}

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut g = slot().lock().unwrap_or_else(|e| e.into_inner());
    f(&mut g)
}

/// 打开一个页面，返回正文（已截断）。
///
/// 这是给"搜完顺手看一眼"用的：成功返回正文，失败返回原因。
pub async fn open(url: &str) -> Result<String, String> {
    let u = normalize_url(url)?;
    let (final_url, html) = fetch(&u).await?;
    let body = html_to_text(&html);
    with(|s| {
        if !s.url.is_empty() && s.url != final_url {
            s.back.push(s.url.clone());
            s.forward.clear();
        }
        s.url = final_url.clone();
        s.html = html;
    });
    Ok(format!("已打开 {final_url}\n\n{}", text::truncate(&body, 6000)))
}

/// 工具入口。
pub async fn run(action: &str, url: &str, text_in: &str, target: &str, script: &str, limit: usize) -> String {
    match action.trim().to_lowercase().as_str() {
        "" | "open" | "goto" => match open(url).await {
            Ok(t) => t,
            Err(e) => e,
        },
        "text" | "read" => page_text(limit),
        "links" | "list" => links(),
        "find" | "search" => find(text_in),
        "url" | "current" => {
            let u = with(|s| s.url.clone());
            if u.is_empty() {
                "还没有打开过页面".into()
            } else {
                u
            }
        }
        "reload" | "refresh" => reload().await,
        "back" => history(true).await,
        "forward" => history(false).await,
        "close" | "clear" => {
            with(|s| *s = State::default());
            "已清空浏览状态".into()
        }
        "scroll" => "滚动需要渲染引擎：当前后端只能取整页正文，没有视口概念".into(),
        "click" => {
            if text_in.trim().is_empty() {
                "click 需要 text（要点的链接文字）".into()
            } else {
                format!("点链接需要渲染引擎。先用 links 找到目标 URL，再 action=open 直接打开。要找的是「{text_in}」")
            }
        }
        "input" | "type" => format!("填表单需要渲染引擎（当前后端只做静态抓取），target={target}"),
        "eval" | "js" => {
            if script.trim().is_empty() {
                "eval 需要 script".into()
            } else {
                "执行 JS 需要渲染引擎（当前后端只做静态抓取）".into()
            }
        }
        other => format!("不支持的 action：{other}（可用 open / text / links / find / url / reload / back / forward / close）"),
    }
}

fn normalize_url(url: &str) -> Result<String, String> {
    let u = url.trim();
    if u.is_empty() {
        return Err("url 是空的".into());
    }
    if u.starts_with("http://") || u.starts_with("https://") {
        return Ok(u.to_string());
    }
    // 像域名的补 https；其余明确报错，不要瞎猜。
    if u.contains('.') && !u.contains(' ') && !u.contains('/') {
        return Ok(format!("https://{u}"));
    }
    if u.contains('.') && !u.contains(' ') {
        return Ok(format!("https://{u}"));
    }
    Err(format!("URL 不合法：{u}"))
}

async fn fetch(url: &str) -> Result<(String, String), String> {
    let resp = client::shared()
        .get(url)
        .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .send()
        .await
        .map_err(|e| format!("请求失败：{e}"))?;
    let final_url = resp.url().to_string();
    let status = resp.status();
    if !status.is_success() {
        return Err(format!("HTTP {}（{final_url}）", status.as_u16()));
    }
    let ctype = resp
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_lowercase();
    if ctype.starts_with("image/") || ctype.contains("octet-stream") {
        return Err(format!("这不是网页（{ctype}）：要用图片处理请走 read_file / image 工具"));
    }
    let html = resp.text().await.map_err(|e| format!("读正文失败：{e}"))?;
    Ok((final_url, text::truncate(&html, MAX_HTML)))
}

async fn reload() -> String {
    let u = with(|s| s.url.clone());
    if u.is_empty() {
        return "还没有打开过页面".into();
    }
    match open(&u).await {
        Ok(t) => t,
        Err(e) => e,
    }
}

async fn history(back: bool) -> String {
    let target = with(|s| {
        if back {
            let prev = s.back.pop()?;
            if !s.url.is_empty() {
                s.forward.push(s.url.clone());
            }
            Some(prev)
        } else {
            let nxt = s.forward.pop()?;
            if !s.url.is_empty() {
                s.back.push(s.url.clone());
            }
            Some(nxt)
        }
    });
    match target {
        Some(u) => match open(&u).await {
            Ok(t) => t,
            Err(e) => e,
        },
        None => "没有更多历史".into(),
    }
}

fn page_text(limit: usize) -> String {
    let html = with(|s| s.html.clone());
    if html.is_empty() {
        return "还没有打开过页面".into();
    }
    text::truncate(&html_to_text(&html), limit.clamp(200, 40_000))
}

fn links() -> String {
    let (html, base) = with(|s| (s.html.clone(), s.url.clone()));
    if html.is_empty() {
        return "还没有打开过页面".into();
    }
    let Ok(re) = Regex::new(r#"(?is)<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>"#) else {
        return "内部正则编译失败".into();
    };
    let Ok(tag) = Regex::new(r"(?s)<[^>]+>") else { return "内部正则编译失败".into() };
    let mut seen = std::collections::BTreeSet::new();
    let mut out = Vec::new();
    for c in re.captures_iter(&html) {
        let href = c[1].trim();
        if href.is_empty()
            || href.starts_with('#')
            || href.starts_with("javascript:")
            || href.starts_with("mailto:")
        {
            continue;
        }
        let label = text::one_line(&tag.replace_all(&c[2], ""));
        if label.is_empty() {
            continue;
        }
        let abs = absolute(&base, href);
        if !seen.insert(abs.clone()) {
            continue;
        }
        out.push(format!("- {label} → {abs}"));
        if out.len() >= 60 {
            break;
        }
    }
    if out.is_empty() {
        "页面里没有可点的链接".into()
    } else {
        out.join("\n")
    }
}

fn find(needle: &str) -> String {
    let html = with(|s| s.html.clone());
    if html.is_empty() {
        return "还没有打开过页面".into();
    }
    let n = needle.trim();
    if n.is_empty() {
        return "find 需要 text".into();
    }
    let body = html_to_text(&html);
    let lower = body.to_lowercase();
    let key = n.to_lowercase();
    let mut hits = Vec::new();
    let mut from = 0usize;
    while let Some(i) = lower[from..].find(&key) {
        let at = from + i;
        // 按字符边界取上下文，避免切在 UTF-8 中间导致 panic。
        let start = byte_floor(&body, at.saturating_sub(60));
        let end = byte_ceil(&body, (at + n.len() + 60).min(body.len()));
        if start < end {
            hits.push(format!("…{}…", body[start..end].replace('\n', " ")));
        }
        from = at + key.len().max(1);
        if hits.len() >= 10 || from >= lower.len() {
            break;
        }
    }
    if hits.is_empty() {
        format!("页面里没有「{n}」")
    } else {
        hits.join("\n")
    }
}

fn byte_floor(s: &str, mut i: usize) -> usize {
    while i > 0 && !s.is_char_boundary(i) {
        i -= 1;
    }
    i
}

fn byte_ceil(s: &str, mut i: usize) -> usize {
    while i < s.len() && !s.is_char_boundary(i) {
        i += 1;
    }
    i.min(s.len())
}

/// 粗暴但够用的正文提取：去脚本 / 样式 / 注释，块级标签换行，折叠空白。
pub fn html_to_text(html: &str) -> String {
    let mut s = html.to_string();
    for pat in [
        r"(?is)<script\b.*?</script>",
        r"(?is)<style\b.*?</style>",
        r"(?is)<noscript\b.*?</noscript>",
        r"(?is)<svg\b.*?</svg>",
        r"(?is)<!--.*?-->",
    ] {
        if let Ok(re) = Regex::new(pat) {
            s = re.replace_all(&s, " ").to_string();
        }
    }
    if let Ok(re) = Regex::new(r"(?is)</?(p|div|br|li|tr|h[1-6]|section|article|header|footer|table|ul|ol)\b[^>]*>") {
        s = re.replace_all(&s, "\n").to_string();
    }
    if let Ok(re) = Regex::new(r"(?s)<[^>]+>") {
        s = re.replace_all(&s, " ").to_string();
    }
    s = decode_entities(&s);
    if let Ok(re) = Regex::new(r"[ \t\u{00a0}]+") {
        s = re.replace_all(&s, " ").to_string();
    }
    if let Ok(re) = Regex::new(r"\n[ \t]*\n[ \t\n]*") {
        s = re.replace_all(&s, "\n\n").to_string();
    }
    s.trim().to_string()
}

fn decode_entities(s: &str) -> String {
    s.replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&amp;", "&")
}

/// 相对 → 绝对。手写而不是引 url crate，因为规则就这几条。
fn absolute(base: &str, href: &str) -> String {
    if href.starts_with("http://") || href.starts_with("https://") {
        return href.to_string();
    }
    if href.starts_with("//") {
        return format!("https:{href}");
    }
    let Some(scheme_end) = base.find("://") else { return href.to_string() };
    let after = &base[scheme_end + 3..];
    let host_end = after.find('/').unwrap_or(after.len());
    let origin = &base[..scheme_end + 3 + host_end];
    if href.starts_with('/') {
        return format!("{origin}{href}");
    }
    let dir = base.rfind('/').map(|i| &base[..i]).unwrap_or(base);
    format!("{dir}/{href}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn text_extract() {
        let html = "<html><head><style>a{}</style></head><body><h1>标题</h1><p>正文 &amp; 更多</p><script>x()</script></body></html>";
        let t = html_to_text(html);
        assert!(t.contains("标题"));
        assert!(t.contains("正文 & 更多"));
        assert!(!t.contains("x()"));
    }

    #[test]
    fn abs_links() {
        assert_eq!(absolute("https://a.com/x/y", "/z"), "https://a.com/z");
        assert_eq!(absolute("https://a.com/x/y", "q"), "https://a.com/x/q");
        assert_eq!(absolute("https://a.com/x/y", "//b.com/k"), "https://b.com/k");
    }

    #[test]
    fn url_normalize() {
        assert!(normalize_url("example.com").unwrap().starts_with("https://"));
        assert!(normalize_url("随便写点什么").is_err());
    }
}
