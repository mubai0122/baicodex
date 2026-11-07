//! APK 解析：纯本地读 zip，不用装 apktool。
//!
//! 能做：概览（包名 / 版本 / 体积 / 文件数）、包内文件、dex 字符串搜索、
//! 权限与组件清单、签名文件列表。
//!
//! ## 二进制 manifest 能拿到什么
//!
//! `AndroidManifest.xml` 在 APK 里是 AXML（二进制 XML）。不做完整解析的话，
//! 能可靠取到的是**字符串池**——里面含包名、权限、组件名。这里按字符串池推断：
//! 以 `android.permission.` 开头的是权限，含 `Activity`/`Service`/… 的是组件，
//! 形如 `a.b.c` 的当作候选包名。
//!
//! 准确率不保证 100%，但足够回答"这是什么应用、要什么权限"。真要做完整解析，
//! 得实现 AXML 的 chunk 结构，那是另一个量级的代码——本模块明确不做。

use std::io::Read;
use std::path::Path;

use regex::Regex;

use crate::core::base::bytes;

/// 结果。
#[derive(Debug, Clone)]
pub struct Report {
    pub ok: bool,
    pub text: String,
}

impl Report {
    fn ok(text: impl Into<String>) -> Self {
        Self { ok: true, text: text.into() }
    }
    fn err(text: impl Into<String>) -> Self {
        Self { ok: false, text: text.into() }
    }
}

/// 解析入口。
///
/// `action`：`info` / `files` / `dex` / `permissions` / `components` / `sign`。
pub fn run(action: &str, path: &str, keyword: &str, limit: usize) -> Report {
    let p = path.trim();
    if p.is_empty() {
        return Report::err("apk 需要 path（.apk 文件路径）");
    }
    let path = Path::new(p);
    if !path.exists() {
        return Report::err(format!("文件不存在：{p}"));
    }
    if !path.is_file() {
        return Report::err(format!("不是文件：{p}"));
    }
    let file = match std::fs::File::open(path) {
        Ok(f) => f,
        Err(e) => return Report::err(format!("打不开：{e}")),
    };
    let mut zip = match zip::ZipArchive::new(file) {
        Ok(z) => z,
        Err(e) => return Report::err(format!("不是合法的 APK / zip：{e}")),
    };

    match action.trim().to_lowercase().as_str() {
        "files" | "list" => files(&mut zip, limit),
        "dex" | "search" => dex(&mut zip, keyword, limit),
        "permissions" | "perm" => manifest_filter(&mut zip, Filter::Permission, limit),
        "components" | "comp" => manifest_filter(&mut zip, Filter::Component, limit),
        "sign" | "signature" => sign(&mut zip),
        _ => info(&mut zip, path),
    }
}

#[derive(Clone, Copy)]
enum Filter {
    Permission,
    Component,
}

fn info(zip: &mut zip::ZipArchive<std::fs::File>, p: &Path) -> Report {
    let size = std::fs::metadata(p).map(|m| m.len()).unwrap_or(0);
    let mut dex = 0usize;
    let mut libs: Vec<String> = Vec::new();
    let mut manifest: Vec<u8> = Vec::new();
    let mut entries = 0usize;
    let mut assets = 0usize;
    let mut has_v2 = false;

    for i in 0..zip.len() {
        let Ok(mut f) = zip.by_index(i) else { continue };
        let name = f.name().to_string();
        if name.ends_with('/') {
            continue;
        }
        entries += 1;
        if name.starts_with("classes") && name.ends_with(".dex") {
            dex += 1;
        }
        if name.starts_with("lib/") {
            if let Some(arch) = name.split('/').nth(1) {
                if !libs.contains(&arch.to_string()) {
                    libs.push(arch.to_string());
                }
            }
        }
        if name.starts_with("assets/") {
            assets += 1;
        }
        if name.starts_with("META-INF/") && name.contains("APK Sig Block") {
            has_v2 = true;
        }
        if name == "AndroidManifest.xml" && manifest.is_empty() {
            let _ = f.read_to_end(&mut manifest);
        }
    }

    let strings = extract_strings(&manifest);
    let pkg = strings
        .iter()
        .find(|s| looks_like_package(s) && !s.starts_with("android.") && !s.starts_with("androidx."))
        .cloned()
        .unwrap_or_else(|| "（没解析出包名）".into());
    let perms = strings.iter().filter(|s| s.starts_with("android.permission.")).count();

    let mut s = String::new();
    s.push_str(&format!("文件：{}\n", p.display()));
    s.push_str(&format!("体积：{}（{size} 字节）\n", bytes::human_size(size)));
    s.push_str(&format!("包名（推断）：{pkg}\n"));
    s.push_str(&format!("条目：{entries} 个文件（assets {assets} 个）\n"));
    s.push_str(&format!("dex 数量：{dex}\n"));
    s.push_str(&format!("声明权限：{perms} 条\n"));
    if !libs.is_empty() {
        s.push_str(&format!("原生库架构：{}\n", libs.join(", ")));
    }
    if has_v2 {
        s.push_str("签名：检测到 APK Signing Block（v2+）\n");
    }
    s.push_str(&format!("manifest 字符串：{} 条\n", strings.len()));
    s.push_str("\n（细节：action=permissions / components / dex / files / sign）");
    Report::ok(s)
}

fn files(zip: &mut zip::ZipArchive<std::fs::File>, limit: usize) -> Report {
    let limit = limit.clamp(1, 800);
    let mut rows: Vec<(u64, String)> = Vec::new();
    for i in 0..zip.len() {
        if rows.len() >= limit {
            break;
        }
        let Ok(f) = zip.by_index(i) else { continue };
        if f.is_dir() {
            continue;
        }
        rows.push((f.size(), f.name().to_string()));
    }
    if rows.is_empty() {
        return Report::err("包里没有文件");
    }
    // 大的在前：判断这是什么应用时，体积分布比字母序有用。
    rows.sort_by(|a, b| b.0.cmp(&a.0));
    let mut s = format!("包内文件（按体积，前 {} 个）：\n", rows.len());
    for (n, name) in &rows {
        s.push_str(&format!("{:>10}  {name}\n", bytes::human_size(*n)));
    }
    Report::ok(s.trim_end().to_string())
}

fn dex(zip: &mut zip::ZipArchive<std::fs::File>, keyword: &str, limit: usize) -> Report {
    let limit = limit.clamp(1, 300);
    let key = keyword.trim();
    let re = if key.is_empty() {
        None
    } else {
        // 先按正则试；不是合法正则就当字面量。模型经常传 `http://a.b` 这种带元字符的串。
        Regex::new(key).ok().or_else(|| Regex::new(&regex::escape(key)).ok())
    };
    let mut hits: Vec<String> = Vec::new();
    let mut scanned = 0usize;
    for i in 0..zip.len() {
        let Ok(mut f) = zip.by_index(i) else { continue };
        let name = f.name().to_string();
        if !(name.starts_with("classes") && name.ends_with(".dex")) {
            continue;
        }
        let mut buf = Vec::new();
        if f.read_to_end(&mut buf).is_err() {
            continue;
        }
        scanned += 1;
        for s in extract_strings(&buf) {
            let n = s.chars().count();
            if !(4..=200).contains(&n) {
                continue;
            }
            let hit = match &re {
                Some(re) => re.is_match(&s),
                None => true,
            };
            if hit {
                if !hits.contains(&s) {
                    hits.push(s);
                }
                if hits.len() >= limit {
                    break;
                }
            }
        }
        if hits.len() >= limit {
            break;
        }
    }
    if hits.is_empty() {
        return Report::ok(format!("dex 里没有匹配的字符串（扫了 {scanned} 个 dex）"));
    }
    Report::ok(format!("命中 {} 条：\n{}", hits.len(), hits.join("\n")))
}

fn manifest_filter(zip: &mut zip::ZipArchive<std::fs::File>, which: Filter, limit: usize) -> Report {
    let mut manifest = Vec::new();
    for i in 0..zip.len() {
        let Ok(mut f) = zip.by_index(i) else { continue };
        if f.name() == "AndroidManifest.xml" {
            let _ = f.read_to_end(&mut manifest);
            break;
        }
    }
    if manifest.is_empty() {
        return Report::err("包里没有 AndroidManifest.xml");
    }
    let limit = limit.clamp(1, 400);
    let strings = extract_strings(&manifest);
    let rows: Vec<String> = match which {
        Filter::Permission => strings
            .into_iter()
            .filter(|s| s.starts_with("android.permission.") || s.contains(".permission."))
            .take(limit)
            .collect(),
        Filter::Component => strings
            .into_iter()
            .filter(|s| {
                s.contains("Activity")
                    || s.contains("Service")
                    || s.contains("Receiver")
                    || s.contains("Provider")
                    || looks_like_package(s)
            })
            .take(limit)
            .collect(),
    };
    if rows.is_empty() {
        return Report::ok("manifest 里没有识别到条目（可能是加固 / 混淆过的包）");
    }
    Report::ok(rows.join("\n"))
}

fn sign(zip: &mut zip::ZipArchive<std::fs::File>) -> Report {
    let mut certs = Vec::new();
    for i in 0..zip.len() {
        let Ok(f) = zip.by_index(i) else { continue };
        let n = f.name().to_string();
        if n.starts_with("META-INF/")
            && (n.ends_with(".RSA") || n.ends_with(".DSA") || n.ends_with(".EC"))
        {
            certs.push(n);
        }
    }
    if certs.is_empty() {
        return Report::err("没有 v1 签名文件（可能只用 v2+ 签名，或根本没签名）");
    }
    Report::ok(format!(
        "签名证书文件：\n{}\n\n（证书主体 / 指纹要用 keytool / openssl 解，本工具不做深度解析）",
        certs.join("\n")
    ))
}

/// 从二进制里抠可打印字符串（长度 ≥ 4）。dex 与 AXML 通用。
fn extract_strings(buf: &[u8]) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur: Vec<u8> = Vec::new();
    for &b in buf {
        if (0x20..0x7f).contains(&b) {
            cur.push(b);
        } else {
            if cur.len() >= 4 {
                out.push(String::from_utf8_lossy(&cur).to_string());
            }
            cur.clear();
        }
        if out.len() > 200_000 {
            break;
        }
    }
    if cur.len() >= 4 {
        out.push(String::from_utf8_lossy(&cur).to_string());
    }
    out
}

fn looks_like_package(s: &str) -> bool {
    let parts: Vec<&str> = s.split('.').collect();
    parts.len() >= 2
        && parts.iter().all(|p| {
            !p.is_empty()
                && p.len() <= 24
                && p.chars().next().map(|c| c.is_ascii_alphabetic()).unwrap_or(false)
                && p.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn package_shape() {
        assert!(looks_like_package("com.example.app"));
        assert!(!looks_like_package("com"));
        assert!(!looks_like_package("com..app"));
        assert!(!looks_like_package("1com.example"));
    }

    #[test]
    fn strings_extract() {
        let v = extract_strings(b"\x00\x01hello\x00abc\x00world!");
        assert!(v.contains(&"hello".to_string()));
        assert!(v.contains(&"world!".to_string()));
    }
}
