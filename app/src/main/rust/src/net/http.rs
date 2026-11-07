//! 直发 HTTP：给 `http_fetch` 工具与技能包里的 http 报文用。
//!
//! 只做最常见的几件事：方法、头、体、跟随跳转、超时、正文截断。
//! 返回值永远是"给人看的一段文本"，不抛异常——工具层需要把失败也回灌给模型。
//!
//! ## 为什么正文要按 content-type 分开处理
//!
//! 把一张 JPEG 当 UTF-8 转成字符串再塞给模型，既浪费几万 token 又毫无意义。
//! 非文本类型只回"这是二进制，多大，可以下载到哪"。

use std::time::Duration;

use super::client;

/// 正文最多回多少字符。
pub const MAX_CHARS: usize = 12_000;
/// 下载上限，防止一条命令把磁盘写满。
pub const MAX_DOWNLOAD: u64 = 512 * 1024 * 1024;

/// 发一个请求。
pub async fn fetch(method: &str, url: &str, headers: &str, body: &str) -> (bool, String) {
    let u = url.trim();
    if u.is_empty() {
        return (false, "缺少 url".into());
    }
    if !(u.starts_with("http://") || u.starts_with("https://")) {
        return (false, format!("只支持 http(s):// ，收到：{u}"));
    }

    let m = {
        let t = method.trim();
        if t.is_empty() {
            "GET".to_string()
        } else {
            t.to_uppercase()
        }
    };

    let c = client::shared();
    let mut rb = match m.as_str() {
        "GET" => c.get(u),
        "POST" => c.post(u),
        "PUT" => c.put(u),
        "PATCH" => c.patch(u),
        "DELETE" => c.delete(u),
        "HEAD" => c.head(u),
        other => return (false, format!("不支持的 method：{other}（GET/POST/PUT/PATCH/DELETE/HEAD）")),
    };

    let mut n_headers = 0;
    for line in headers.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        // 容忍 `K: V`、`K:V`、`K = V` 三种写法——模型经常写成最后一种。
        let Some((k, v)) = line.split_once(':').or_else(|| line.split_once('=')) else { continue };
        let k = k.trim();
        if k.is_empty() || k.contains(char::is_whitespace) {
            continue;
        }
        rb = rb.header(k, v.trim());
        n_headers += 1;
    }
    // 默认 Accept 给个宽的值，否则有些站点回 406。
    if n_headers == 0 {
        rb = rb.header("Accept", "*/*");
    }
    if !body.is_empty() && m != "GET" && m != "HEAD" {
        rb = rb.body(body.to_string());
    }

    match rb.send().await {
        Ok(resp) => {
            let status = resp.status();
            let ctype = resp
                .headers()
                .get("content-type")
                .and_then(|v| v.to_str().ok())
                .unwrap_or("")
                .to_lowercase();
            let len = resp.content_length().unwrap_or(0);
            let head = if ctype.is_empty() {
                format!("HTTP {}", status.as_u16())
            } else {
                format!("HTTP {}（{}）", status.as_u16(), ctype)
            };

            if m == "HEAD" {
                let mut s = format!("{head}\n");
                if len > 0 {
                    s.push_str(&format!("长度：{} 字节\n", len));
                }
                for (k, v) in resp.headers() {
                    if let Ok(vs) = v.to_str() {
                        s.push_str(&format!("{k}: {vs}\n"));
                    }
                }
                return (status.is_success(), s.trim_end().to_string());
            }

            let binary = is_binary(&ctype);
            if binary {
                let mut s = format!("{head}\n正文是二进制");
                if len > 0 {
                    s.push_str(&format!("，{} 字节", len));
                }
                s.push_str("：要看内容请用 http_fetch 的 download 形式（或 action=download 存到本地）");
                return (status.is_success(), s);
            }

            let text = resp.text().await.unwrap_or_default();
            let total = text.chars().count();
            let cut = if total > MAX_CHARS {
                format!("{}\n…已截断（共 {total} 字符）", text.chars().take(MAX_CHARS).collect::<String>())
            } else {
                text
            };
            (status.is_success(), format!("{head}\n{cut}"))
        }
        Err(e) => (false, format!("请求失败：{}", human_err(&e))),
    }
}

fn is_binary(ctype: &str) -> bool {
    if ctype.is_empty() {
        return false;
    }
    ctype.starts_with("image/")
        || ctype.starts_with("audio/")
        || ctype.starts_with("video/")
        || ctype.starts_with("font/")
        || ctype.contains("octet-stream")
        || ctype.contains("zip")
        || ctype.contains("pdf")
        || ctype.contains("protobuf")
}

/// reqwest 的错误信息经常只有 `error sending request for url (...)`，
/// 后面那串 `source` 才是"连接超时""证书无效"这类有用信息。
fn human_err(e: &reqwest::Error) -> String {
    let mut s = e.to_string();
    let mut src = std::error::Error::source(e);
    while let Some(inner) = src {
        s.push('：');
        s.push_str(&inner.to_string());
        src = inner.source();
    }
    s
}

/// 下载到文件。
pub async fn download(url: &str, to: &str) -> (bool, String) {
    let u = url.trim();
    if u.is_empty() {
        return (false, "缺少 url".into());
    }
    let path = {
        let t = to.trim();
        if t.is_empty() {
            // 没给目标就用 URL 最后一段当文件名。
            let name = u.split('?').next().unwrap_or(u).rsplit('/').next().unwrap_or("download");
            crate::core::base::dirs::file(if name.is_empty() { "download" } else { name })
        } else if t.starts_with('/') || t.starts_with('~') {
            crate::core::base::path::expand_tilde(t)
        } else {
            crate::core::base::dirs::base().join(t)
        }
    };

    match client::long().get(u).send().await {
        Ok(resp) if resp.status().is_success() => {
            if let Some(len) = resp.content_length() {
                if len > MAX_DOWNLOAD {
                    return (false, format!("文件太大（{}），拒绝下载", crate::core::base::bytes::human_size(len)));
                }
            }
            let bytes = match resp.bytes().await {
                Ok(b) => b,
                Err(e) => return (false, format!("读正文失败：{}", human_err(&e))),
            };
            if let Some(d) = path.parent() {
                if crate::core::base::fsx::ensure_dir(d).is_err() {
                    return (false, format!("建目录失败：{}", d.display()));
                }
            }
            match crate::core::base::atomic::write(&path, &bytes) {
                Ok(_) => (
                    true,
                    format!("已保存 {}（{} 字节）", path.display(), bytes.len()),
                ),
                Err(e) => (false, format!("落盘失败：{e}")),
            }
        }
        Ok(resp) => (false, format!("HTTP {}（{}）", resp.status().as_u16(), u)),
        Err(e) => (false, format!("请求失败：{}", human_err(&e))),
    }
}

/// 技能里的 http 报文：首行 `METHOD URL`，随后 header，空行后是 body。
///
/// 这是技能包作者最熟的写法（跟 curl -v / .http 文件一致），解析器必须宽容：
/// 多空行、行尾 \r、header 里带冒号的值，都不能把报文解错。
pub async fn raw_message(text: &str) -> (bool, String) {
    let mut lines = text.lines();
    let first = lines.next().unwrap_or("").trim().to_string();
    if first.is_empty() {
        return (false, "http 报文是空的".into());
    }
    let (method, url) = match first.split_once(char::is_whitespace) {
        Some((m, u)) => (m.trim().to_string(), u.trim().to_string()),
        None => ("GET".to_string(), first.clone()),
    };
    if url.is_empty() {
        return (false, "http 报文首行不合法（应为 METHOD URL）".into());
    }

    let mut headers: Vec<String> = Vec::new();
    let mut body = String::new();
    let mut in_body = false;
    for line in lines {
        let l = line.trim_end_matches('\r');
        if !in_body {
            if l.trim().is_empty() {
                in_body = true;
                continue;
            }
            headers.push(l.trim().to_string());
        } else {
            body.push_str(l);
            body.push('\n');
        }
    }
    fetch(&method, &url, &headers.join("\n"), body.trim_end()).await
}

/// `GET url` 的简写，给内部调用省参数。
pub async fn get(url: &str) -> (bool, String) {
    fetch("GET", url, "", "").await
}

/// 带超时的 GET（给健康检查用）。
pub async fn get_timeout(url: &str, secs: u64) -> (bool, String) {
    let fut = fetch("GET", url, "", "");
    match tokio::time::timeout(Duration::from_secs(secs.clamp(1, 120)), fut).await {
        Ok(r) => r,
        Err(_) => (false, format!("请求超时（{secs}s）")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn binary_detect() {
        assert!(is_binary("image/png"));
        assert!(is_binary("application/octet-stream"));
        assert!(!is_binary("application/json"));
        assert!(!is_binary(""));
    }
}
