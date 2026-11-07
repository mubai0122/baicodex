//! 工作区绑定。
//!
//! 模型给的相对路径一律落到绑定目录，命令默认在该目录里执行。这样提示词里可以
//! 只说 `src/main.rs`，不需要每轮重复绝对路径。
//!
//! ## 三个来源，优先级从高到低
//!
//! 1. `cfg.workspace.current` —— 用户显式绑定 / 会话记住的；
//! 2. `cfg.workspace.root` —— 默认根（新会话第一次分配时写回）；
//! 3. 空 —— 没绑定，相对路径按数据根解释。
//!
//! ## 索引与缓存
//!
//! `workspace.json` 写给 UI 与模型共用；目录树摘录 30 秒内复用。**不缓存扫描结果**
//! ——文件随时会变，缓存清单只会让模型拿到过期的文件列表。

use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};

use crate::core::base::{bytes, clock, dirs, fsx};
use crate::core::feature::config::schema::Config;

/// 索引文件名。
pub const INDEX: &str = "workspace.json";

const MAX_FILES: usize = 4000;
const MAX_DEPTH: usize = 8;
const TREE_CHARS: usize = 4000;
const TREE_TTL: Duration = Duration::from_secs(30);

/// 扫目录时直接跳过的重目录：构建产物与缓存，列出来只会撑爆提示词。
pub const SKIP: &[&str] = &[
    ".git",
    ".gradle",
    "build",
    ".idea",
    "node_modules",
    "__pycache__",
    ".cxx",
    "dist",
    ".kotlin",
    ".dart_tool",
    "target",
    ".venv",
    "venv",
    ".next",
    ".cache",
];

/// 一个条目。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Entry {
    /// 相对工作区根的路径，正斜杠分隔。
    pub rel: String,
    pub size: u64,
    pub dir: bool,
    /// 修改时间（秒）。0 表示取不到。
    pub mtime: i64,
}

impl Entry {
    /// 给模型看的一行。
    pub fn line(&self) -> String {
        if self.dir {
            format!("{}/", self.rel)
        } else {
            format!("{} ({})", self.rel, bytes::human_size(self.size))
        }
    }
}

fn cache() -> &'static Mutex<Option<(Instant, String)>> {
    static C: OnceLock<Mutex<Option<(Instant, String)>>> = OnceLock::new();
    C.get_or_init(|| Mutex::new(None))
}

/// 当前工作区绝对路径。空串表示没绑定。
pub fn path(cfg: &Config) -> String {
    let cur = cfg.workspace.current.trim();
    if !cur.is_empty() {
        return cur.trim_end_matches('/').to_string();
    }
    if !cfg.workspace.auto_bind {
        return String::new();
    }
    let root = cfg.workspace.root.trim();
    if !root.is_empty() && Path::new(root).is_dir() {
        return root.trim_end_matches('/').to_string();
    }
    String::new()
}

/// 默认根：配置里没写就用 `<数据根>/workspace`。
pub fn default_root(cfg: &Config) -> PathBuf {
    let r = cfg.workspace.root.trim();
    if r.is_empty() {
        dirs::dir("workspace")
    } else {
        crate::core::base::path::expand_tilde(r)
    }
}

/// 新会话的工作区：沿用默认根，没有就分配一个。
///
/// 返回 `(会话工作区, 需要写回的默认根)`。第二个值只在"第一次分配"时非空，
/// 调用方据此决定要不要落盘——这样不会每次开新会话都改配置。
pub fn new_session_workspace(cfg: &Config) -> (String, Option<String>) {
    if !cfg.workspace.current.trim().is_empty() {
        return (cfg.workspace.current.clone(), None);
    }
    if !cfg.workspace.auto_bind {
        return (String::new(), None);
    }
    let root = default_root(cfg);
    if fsx::ensure_dir(&root).is_ok() {
        let s = root.to_string_lossy().to_string();
        let need_write = cfg.workspace.root.trim().is_empty();
        return (s.clone(), if need_write { Some(s) } else { None });
    }
    // 默认根建不出来（只读 / 权限），退到一个新建的唯一目录。
    let d = dirs::new_dir("workspace");
    (d.to_string_lossy().to_string(), Some(d.to_string_lossy().to_string()))
}

/// 作废目录树缓存。
pub fn invalidate() {
    if let Ok(mut c) = cache().lock() {
        *c = None;
    }
}

pub fn bound(cfg: &Config) -> bool {
    !path(cfg).is_empty()
}

/// 用户显式绑定过（区别于自动兜底）。
pub fn custom(cfg: &Config) -> bool {
    !cfg.workspace.current.trim().is_empty()
}

/// 工作区名字（最后一段）。
pub fn name(cfg: &Config) -> String {
    let p = path(cfg);
    crate::core::base::path::file_name(&p)
}

pub fn exists(cfg: &Config) -> bool {
    let p = path(cfg);
    !p.is_empty() && Path::new(&p).is_dir()
}

pub fn ensure(cfg: &Config) -> bool {
    let p = path(cfg);
    if p.is_empty() {
        return false;
    }
    Path::new(&p).is_dir() || fsx::ensure_dir(&p).is_ok()
}

/// 把模型给的路径解析成绝对路径。
///
/// - 绝对路径：原样归一化；
/// - `~/x`：展开成主目录；
/// - 相对路径：拼到工作区；工作区没绑定时拼到数据根。
pub fn resolve(cfg: &Config, raw: &str) -> String {
    let p = raw.trim().trim_matches('"').trim_matches('\'');
    if p.is_empty() {
        return path(cfg);
    }
    if p == "~" || p.starts_with("~/") {
        return crate::core::base::path::to_slash(crate::core::base::path::expand_tilde(p));
    }
    if p.starts_with('/') {
        return crate::core::base::path::to_slash(crate::core::base::path::normalize(p));
    }
    let rel = p.strip_prefix("./").unwrap_or(p);
    let base = path(cfg);
    if base.is_empty() {
        return crate::core::base::path::to_slash(crate::core::base::path::normalize(dirs::base().join(rel)));
    }
    crate::core::base::path::to_slash(crate::core::base::path::normalize(Path::new(&base).join(rel)))
}

/// 绝对路径 → 相对工作区（在区内时）。给界面显示用。
pub fn relativize(cfg: &Config, abs: &str) -> String {
    let base = path(cfg);
    if base.is_empty() {
        return abs.to_string();
    }
    match Path::new(abs).strip_prefix(&base) {
        Ok(r) => crate::core::base::path::to_slash(r),
        Err(_) => abs.to_string(),
    }
}

/// 给 shell 命令加 `cd` 前缀：命令默认就在工作区里跑。
///
/// 不绑定时原样返回。用 `&&` 而不是 `;`：cd 失败时命令不该在错误的目录里执行。
pub fn wrap(cfg: &Config, cmd: &str) -> String {
    if cmd.trim().is_empty() || !exists(cfg) {
        return cmd.to_string();
    }
    format!("cd {} && {}", super::shell::quote(&path(cfg)), cmd)
}

/// 扫一遍工作区。
pub fn scan(cfg: &Config) -> Vec<Entry> {
    if !ensure(cfg) {
        return Vec::new();
    }
    let mut out = Vec::new();
    walk(Path::new(&path(cfg)), 1, "", &mut out);
    out
}

fn walk(dir: &Path, depth: usize, prefix: &str, out: &mut Vec<Entry>) {
    if depth > MAX_DEPTH || out.len() >= MAX_FILES {
        return;
    }
    let Ok(rd) = std::fs::read_dir(dir) else { return };
    let mut kids: Vec<PathBuf> = rd.flatten().map(|e| e.path()).collect();
    kids.sort_by_key(|p| {
        (
            !p.is_dir(),
            p.file_name().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default(),
        )
    });

    for f in kids {
        if out.len() >= MAX_FILES {
            return;
        }
        let n = f.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
        // 隐藏文件除了 .gitignore / .env 这类有意义的，其余不列（.DS_Store 之类是噪音）。
        if n.starts_with('.') && !matches!(n.as_str(), ".gitignore" | ".env" | ".editorconfig") {
            continue;
        }
        let rel = if prefix.is_empty() { n.clone() } else { format!("{prefix}/{n}") };
        let Ok(md) = std::fs::metadata(&f) else { continue };
        let mtime = md
            .modified()
            .ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0);
        if md.is_dir() {
            if SKIP.contains(&n.as_str()) {
                continue;
            }
            out.push(Entry { rel: rel.clone(), size: 0, dir: true, mtime });
            walk(&f, depth + 1, &rel, out);
        } else {
            // 符号链接按文件算，但大小取链接本身（避免 follow 到 /dev/zero 卡死）。
            let size = if md.file_type().is_symlink() { 0 } else { md.len() };
            out.push(Entry { rel, size, dir: false, mtime });
        }
    }
}

#[derive(Serialize)]
struct IndexFile<'a> {
    root: &'a str,
    name: &'a str,
    scanned_at: i64,
    files: usize,
    dirs: usize,
    bytes: u64,
    skip_dirs: &'static [&'static str],
    file_list: Vec<FileRow>,
    dir_list: Vec<String>,
}

#[derive(Serialize)]
struct FileRow {
    p: String,
    s: u64,
    t: i64,
}

/// 重写 `workspace.json`，返回写入的条目数。
pub fn index(cfg: &Config) -> usize {
    let list = scan(cfg);
    if list.is_empty() {
        return 0;
    }
    let mut file_list = Vec::new();
    let mut dir_list = Vec::new();
    let mut total = 0u64;
    for e in &list {
        if e.dir {
            dir_list.push(e.rel.clone());
        } else {
            total += e.size;
            file_list.push(FileRow { p: e.rel.clone(), s: e.size, t: e.mtime });
        }
    }
    let root = path(cfg);
    let doc = IndexFile {
        root: &root,
        name: "",
        scanned_at: clock::now_s(),
        files: file_list.len(),
        dirs: dir_list.len(),
        bytes: total,
        skip_dirs: SKIP,
        file_list,
        dir_list,
    };
    let mut v = match serde_json::to_value(&doc) {
        Ok(v) => v,
        Err(_) => return 0,
    };
    v["name"] = serde_json::json!(name(cfg));
    if let Ok(text) = serde_json::to_string_pretty(&v) {
        let _ = fsx::write_text(Path::new(&root).join(INDEX), &text);
    }
    invalidate();
    list.len()
}

/// 读回索引（UI 用；不重新扫盘）。
pub fn read_index(cfg: &Config) -> Option<serde_json::Value> {
    let p = Path::new(&path(cfg)).join(INDEX);
    let t = std::fs::read_to_string(p).ok()?;
    serde_json::from_str(&t).ok()
}

/// 目录树摘录，给系统提示用；30 秒内复用。
pub fn tree(cfg: &Config) -> String {
    if !bound(cfg) {
        return String::new();
    }
    if let Ok(c) = cache().lock() {
        if let Some((at, text)) = c.as_ref() {
            if at.elapsed() < TREE_TTL {
                return text.clone();
            }
        }
    }
    let root = path(cfg);
    if !Path::new(&root).is_dir() {
        return String::new();
    }
    let mut sb = String::new();
    walk_tree(Path::new(&root), "", 1, &mut sb);
    let text: String = sb.chars().take(TREE_CHARS).collect();
    if let Ok(mut c) = cache().lock() {
        *c = Some((Instant::now(), text.clone()));
    }
    text
}

fn walk_tree(dir: &Path, prefix: &str, depth: usize, sb: &mut String) {
    if depth > 3 || sb.chars().count() > TREE_CHARS {
        return;
    }
    let Ok(rd) = std::fs::read_dir(dir) else { return };
    let mut kids: Vec<PathBuf> = rd.flatten().map(|e| e.path()).collect();
    kids.sort_by_key(|p| {
        (
            !p.is_dir(),
            p.file_name().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default(),
        )
    });
    let kids: Vec<PathBuf> = kids
        .into_iter()
        .filter(|p| {
            let n = p.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
            !SKIP.contains(&n.as_str()) && !n.starts_with('.')
        })
        .take(40)
        .collect();
    let total = kids.len();
    for (i, f) in kids.iter().enumerate() {
        if sb.chars().count() > TREE_CHARS {
            return;
        }
        let n = f.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
        let last = i + 1 == total;
        sb.push_str(prefix);
        sb.push_str(if last { "└─ " } else { "├─ " });
        sb.push_str(&n);
        if f.is_dir() {
            sb.push('/');
            sb.push('\n');
            let next = format!("{prefix}{}", if last { "   " } else { "│  " });
            walk_tree(f, &next, depth + 1, sb);
        } else {
            let size = std::fs::metadata(f).map(|m| m.len()).unwrap_or(0);
            sb.push_str(&format!(" ({})\n", bytes::human_size(size)));
        }
    }
}

/// 绑定目录：不存在就建，成功后写一次索引。空 / 根 / 数据根本身一律拒绝。
pub fn bind(cfg: &mut Config, raw: &str) -> Result<String, String> {
    let abs = resolve(cfg, raw);
    let abs = abs.trim_end_matches('/').to_string();
    if abs.is_empty() {
        return Err("路径为空".into());
    }
    if abs == "/" || abs == dirs::base().to_string_lossy() {
        return Err("不能绑定这个目录".into());
    }
    let f = Path::new(&abs);
    if !f.is_dir() && fsx::ensure_dir(f).is_err() {
        return Err("目录建不出来（检查权限）".into());
    }
    cfg.workspace.current = abs.clone();
    invalidate();
    index(cfg);
    Ok(abs)
}

/// 解绑：回到默认根。
pub fn unbind(cfg: &mut Config) {
    cfg.workspace.current.clear();
    invalidate();
}

/// 常用目录候选：设置页与绑定弹窗的快捷胶囊。
pub fn quick_picks(cfg: &Config) -> Vec<(String, String)> {
    let base = dirs::base();
    let mut v = vec![
        ("数据根".to_string(), base.to_string_lossy().to_string()),
        (
            "默认工作区".to_string(),
            default_root(cfg).to_string_lossy().to_string(),
        ),
        ("主目录".to_string(), crate::core::base::path::home().to_string_lossy().to_string()),
    ];
    for p in ["/sdcard", "/storage/emulated/0", "/tmp", "/workspace"] {
        if Path::new(p).is_dir() {
            v.push((p.to_string(), p.to_string()));
        }
    }
    let cur = path(cfg);
    if !cur.is_empty() {
        v.insert(0, ("当前工作区".to_string(), cur));
    }
    v
}

/// 工作区里能直接用 `@` 引用的文件（相对路径，最多 300 条）。
pub fn files_for_mention(cfg: &Config) -> Vec<String> {
    scan(cfg).into_iter().filter(|e| !e.dir).map(|e| e.rel).take(300).collect()
}

/// 一句话摘要，给提示词与状态栏用。
pub fn summary(cfg: &Config) -> String {
    match (bound(cfg), custom(cfg)) {
        (false, _) => "未绑定工作区（相对路径按数据根解释）".to_string(),
        (true, true) => format!("工作区 {}（用户指定）", path(cfg)),
        (true, false) => format!("工作区 {}（默认）", path(cfg)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn skip_list_has_build_dirs() {
        assert!(SKIP.contains(&"target"));
        assert!(SKIP.contains(&"node_modules"));
    }

    #[test]
    fn entry_line_marks_dirs() {
        let e = Entry { rel: "src".into(), size: 0, dir: true, mtime: 0 };
        assert!(e.line().ends_with('/'));
    }
}
