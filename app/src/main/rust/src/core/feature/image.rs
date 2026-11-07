//! 图片：MIME 判断、数据 URL、压缩、截图落盘。
//!
//! 模型看图片有两条路：给 URL（服务端去取）或给 base64 数据 URL（请求体里带上）。
//! 后者对本地截图是唯一可行的，所以这里负责把本地文件变成合法的 data URL。
//!
//! ## 为什么要限制大小
//!
//! 一张 4MB 的手机截图 base64 之后是 5.4MB，塞进请求体里既慢又容易触发网关限制。
//! 超过阈值一律先压缩——**先压再传**，不要指望服务端帮你压。

use std::path::Path;

use crate::core::base::{bytes, fsx, path};

/// 单张图上限（压缩前）。超过就压。
pub const RAW_LIMIT: u64 = 3 * 1024 * 1024;
/// 压缩目标边长。
pub const MAX_EDGE: u32 = 1600;
/// 压缩质量（JPEG）。
pub const QUALITY: u8 = 82;

/// 支持的图片类型。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Png,
    Jpeg,
    Webp,
    Gif,
    Bmp,
    Heic,
    Unknown,
}

impl Kind {
    pub fn mime(self) -> &'static str {
        match self {
            Kind::Png => "image/png",
            Kind::Jpeg => "image/jpeg",
            Kind::Webp => "image/webp",
            Kind::Gif => "image/gif",
            Kind::Bmp => "image/bmp",
            Kind::Heic => "image/heic",
            Kind::Unknown => "application/octet-stream",
        }
    }

    pub fn ext(self) -> &'static str {
        match self {
            Kind::Png => "png",
            Kind::Jpeg => "jpg",
            Kind::Webp => "webp",
            Kind::Gif => "gif",
            Kind::Bmp => "bmp",
            Kind::Heic => "heic",
            Kind::Unknown => "bin",
        }
    }

    /// 模型能不能看。
    pub fn model_ok(self) -> bool {
        matches!(self, Kind::Png | Kind::Jpeg | Kind::Webp | Kind::Gif)
    }
}

/// 按文件头判断（不看扩展名：用户改过名的文件太多了）。
pub fn sniff(head: &[u8]) -> Kind {
    if head.len() < 12 {
        return Kind::Unknown;
    }
    if head.starts_with(&[0x89, b'P', b'N', b'G']) {
        return Kind::Png;
    }
    if head.starts_with(&[0xFF, 0xD8, 0xFF]) {
        return Kind::Jpeg;
    }
    if head.starts_with(b"GIF8") {
        return Kind::Gif;
    }
    if head.starts_with(b"RIFF") && &head[8..12] == b"WEBP" {
        return Kind::Webp;
    }
    if head.starts_with(b"BM") {
        return Kind::Bmp;
    }
    if &head[4..8] == b"ftyp" {
        let brand = &head[8..12.min(head.len())];
        if brand == b"heic" || brand == b"heix" || brand == b"mif1" {
            return Kind::Heic;
        }
    }
    Kind::Unknown
}

/// 判断一个文件是不是图片。
pub fn kind_of(p: impl AsRef<Path>) -> Kind {
    match fsx::peek(p, 12) {
        Ok(h) => sniff(&h),
        Err(_) => Kind::Unknown,
    }
}

/// 本地文件 → data URL。
///
/// 超过 [RAW_LIMIT] 时**不压缩**（压缩需要 image crate，会引入几 MB 的依赖），
/// 直接报错让上层决定：要么用户自己压，要么改走"上传后给 URL"的路子。
pub fn data_url(p: impl AsRef<Path>) -> Result<String, String> {
    let p = p.as_ref();
    let md = std::fs::metadata(p).map_err(|e| format!("读不到文件：{e}"))?;
    if !md.is_file() {
        return Err("不是文件".into());
    }
    if md.len() > RAW_LIMIT {
        return Err(format!(
            "图片太大（{}，上限 {}）：先压缩，或改用 URL 方式",
            bytes::human_size(md.len()),
            bytes::human_size(RAW_LIMIT)
        ));
    }
    let k = kind_of(p);
    if !k.model_ok() {
        return Err(format!("模型看不了这种格式（{}），转成 png / jpg 再试", k.mime()));
    }
    let data = std::fs::read(p).map_err(|e| format!("读文件失败：{e}"))?;
    Ok(format!("data:{};base64,{}", k.mime(), bytes::b64_encode(&data)))
}

/// 一批文件 → data URL 列表（跳过非图片，返回 `(路径, 结果)`）。
pub fn data_urls(paths: &[String]) -> Vec<(String, Result<String, String>)> {
    paths.iter().map(|p| (p.clone(), data_url(p))).collect()
}

/// 截图目录。
pub fn shot_dir() -> std::path::PathBuf {
    crate::core::base::dirs::dir_or_tmp("shots")
}

/// 新建一个截图路径。
pub fn new_shot() -> std::path::PathBuf {
    crate::core::base::dirs::new_file(shot_dir(), "shot", "png")
}

/// 列出最近的截图（给界面"图片"页）。
pub fn recent(limit: usize) -> Vec<(String, u64, i64)> {
    let d = shot_dir();
    let Ok(rd) = std::fs::read_dir(&d) else { return Vec::new() };
    let mut v: Vec<(String, u64, i64)> = rd
        .flatten()
        .filter_map(|e| {
            let p = e.path();
            if !p.is_file() {
                return None;
            }
            let md = e.metadata().ok()?;
            let at = md
                .modified()
                .ok()
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|x| x.as_secs() as i64)
                .unwrap_or(0);
            Some((p.to_string_lossy().to_string(), md.len(), at))
        })
        .collect();
    v.sort_by(|a, b| b.2.cmp(&a.2));
    v.truncate(limit.clamp(1, 200));
    v
}

/// 清理旧截图，保留最近 `keep` 张。
pub fn cleanup(keep: usize) -> usize {
    let list = recent(10_000);
    let mut n = 0;
    for (p, _, _) in list.into_iter().skip(keep.max(1)) {
        if std::fs::remove_file(&p).is_ok() {
            n += 1;
        }
    }
    n
}

/// 从 Markdown 里挑出图片引用（`![](path)` 与 `![](data:...)`）。
pub fn markdown_images(text: &str) -> Vec<String> {
    let Ok(re) = regex::Regex::new(r"!\[[^\]]*\]\(([^)\s]+)") else { return Vec::new() };
    re.captures_iter(text)
        .map(|c| c[1].to_string())
        .filter(|s| !s.starts_with("data:") || s.len() < 8_000_000)
        .collect()
}

/// 一段可读描述，给日志与界面。
pub fn describe(p: impl AsRef<Path>) -> String {
    let p = p.as_ref();
    let k = kind_of(p);
    let size = std::fs::metadata(p).map(|m| m.len()).unwrap_or(0);
    format!("{}（{}，{}）", path::file_name(p), k.mime(), bytes::human_size(size))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sniff_formats() {
        assert_eq!(sniff(&[0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0]), Kind::Png);
        assert_eq!(sniff(&[0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0]), Kind::Jpeg);
        assert_eq!(sniff(b"GIF89a......"), Kind::Gif);
        assert_eq!(sniff(b"RIFF\0\0\0\0WEBP"), Kind::Webp);
        assert_eq!(sniff(b"????"), Kind::Unknown);
    }

    #[test]
    fn model_support() {
        assert!(Kind::Png.model_ok());
        assert!(!Kind::Heic.model_ok());
    }
}
