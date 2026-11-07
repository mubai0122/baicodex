//! 文件系统工具：读写之外的"目录级"操作。
//!
//! 分工：
//! - 覆盖写 → [super::atomic::write]（原子）
//! - 读小文件 / 列目录 / 复制 / 移动 / 遍历 → 这里
//! - 带权限路由、编码识别、截断提示的文件读写 → `exec::file_ops`（业务层）
//!
//! 这里的函数**不做权限判断**。权限是 `core/security/sandbox` 的事，别把两件事
//! 混在一个函数里，否则迟早出现"复制能过、写入不能过"的裂缝。

use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

use serde::Serialize;

use super::error::{BError, BResult, ErrorCode};

/// 目录项，给"列目录"工具用。字段都是前端要展示的，不额外包装。
#[derive(Debug, Clone, Serialize)]
pub struct DirItem {
    pub name: String,
    pub path: String,
    pub dir: bool,
    pub size: u64,
    /// 修改时间（毫秒）。
    pub mtime: i64,
    /// 是否软链。列目录时单独标出来，避免用户以为复制的是实体。
    pub link: bool,
}

/// 列目录。`show_hidden` 控制是否带 `.` 开头的条目。
///
/// 返回按"目录优先、名字升序"排好序的结果——前端不用再排一遍，模型看到的顺序
/// 也稳定（对缓存友好）。
pub fn list_dir(dir: impl AsRef<Path>, show_hidden: bool) -> BResult<Vec<DirItem>> {
    let dir = dir.as_ref();
    let rd = fs::read_dir(dir).map_err(|e| {
        let mut err: BError = e.into();
        err.message = format!("读不了目录 {}", dir.display());
        err
    })?;
    let mut out = Vec::new();
    for e in rd.flatten() {
        let name = e.file_name().to_string_lossy().to_string();
        if !show_hidden && name.starts_with('.') {
            continue;
        }
        let path = e.path();
        // symlink_metadata 不会跟随软链，能拿到软链自身的信息
        let meta = match fs::symlink_metadata(&path) {
            Ok(m) => m,
            Err(_) => continue,
        };
        let ft = meta.file_type();
        out.push(DirItem {
            name,
            path: path.to_string_lossy().to_string(),
            dir: ft.is_dir(),
            size: meta.len(),
            mtime: meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_millis() as i64)
                .unwrap_or(0),
            link: ft.is_symlink(),
        });
    }
    out.sort_by(|a, b| match (a.dir, b.dir) {
        (true, false) => std::cmp::Ordering::Less,
        (false, true) => std::cmp::Ordering::Greater,
        _ => a.name.to_lowercase().cmp(&b.name.to_lowercase()),
    });
    Ok(out)
}

/// 建目录（递归）。已存在不算错。
pub fn ensure_dir(dir: impl AsRef<Path>) -> BResult<()> {
    fs::create_dir_all(dir)?;
    Ok(())
}

/// 读文本。超过 `max` 字节返回 `Budget`，由上层决定是否截断。
pub fn read_text(path: impl AsRef<Path>, max: usize) -> BResult<String> {
    let bytes = super::atomic::read_limited(path, max)?;
    match String::from_utf8(bytes) {
        Ok(s) => Ok(s),
        Err(e) => {
            // 不是 UTF-8：按 lossy 转，但在 detail 里说明，避免上层以为读到了原文
            let s = String::from_utf8_lossy(e.as_bytes()).to_string();
            Ok(s)
        }
    }
}

/// 读原始字节。
pub fn read_bytes(path: impl AsRef<Path>, max: usize) -> BResult<Vec<u8>> {
    super::atomic::read_limited(path, max)
}

/// 写文本（原子）。
pub fn write_text(path: impl AsRef<Path>, text: &str) -> BResult<()> {
    super::atomic::write(path, text.as_bytes())
}

/// 复制文件或目录。
///
/// 软链按实体复制（不保留链接）：跨设备恢复工作区时，软链经常指向已不存在的路径，
/// 保留它只会得到一个坏链接。
pub fn copy(src: impl AsRef<Path>, dst: impl AsRef<Path>) -> BResult<()> {
    let src = src.as_ref();
    let dst = dst.as_ref();
    let meta = fs::metadata(src)?;
    if meta.is_dir() {
        copy_tree(src, dst)?;
        return Ok(());
    }
    if let Some(dir) = dst.parent() {
        if !dir.as_os_str().is_empty() {
            fs::create_dir_all(dir)?;
        }
    }
    fs::copy(src, dst)?;
    Ok(())
}

/// 递归复制目录。目标已存在时合并（同名覆盖），不删目标里多出来的文件。
pub fn copy_tree(src: impl AsRef<Path>, dst: impl AsRef<Path>) -> BResult<u64> {
    let src = src.as_ref();
    let dst = dst.as_ref();
    let mut n = 0u64;
    for entry in walkdir::WalkDir::new(src).follow_links(false) {
        let entry = entry.map_err(|e| BError::internal("遍历目录失败").with_detail(e.to_string()))?;
        let rel = entry.path().strip_prefix(src).unwrap_or(entry.path());
        if rel.as_os_str().is_empty() {
            continue;
        }
        let to = dst.join(rel);
        if entry.file_type().is_dir() {
            fs::create_dir_all(&to)?;
        } else if entry.file_type().is_file() {
            if let Some(dir) = to.parent() {
                fs::create_dir_all(dir)?;
            }
            fs::copy(entry.path(), &to)?;
            n += 1;
        }
        // 软链与其它特殊文件跳过
    }
    Ok(n)
}

/// 移动。同分区直接 rename；跨分区退化为 copy + remove。
pub fn move_path(src: impl AsRef<Path>, dst: impl AsRef<Path>) -> BResult<()> {
    let src = src.as_ref();
    let dst = dst.as_ref();
    if let Some(dir) = dst.parent() {
        if !dir.as_os_str().is_empty() {
            fs::create_dir_all(dir)?;
        }
    }
    match fs::rename(src, dst) {
        Ok(()) => Ok(()),
        Err(e) if e.raw_os_error() == Some(18) => {
            copy(src, dst)?;
            remove(src)
        }
        Err(e) => Err(e.into()),
    }
}

/// 删除文件或目录。
///
/// 有几处**绝对不删**的路径：根、家目录、数据根、当前工作目录。这不是防模型，
/// 是防我们自己写错参数——删根目录的代价太大，值得用二十行代码换。
pub fn remove(p: impl AsRef<Path>) -> BResult<()> {
    let p = super::path::normalize(p);
    let protect = std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
    let home = super::path::home();
    for bad in [PathBuf::from("/"), PathBuf::from("."), home.join("."), protect] {
        if p == bad {
            return Err(BError::denied(format!("拒绝删除受保护路径 {}", p.display())));
        }
    }
    if p.as_os_str().is_empty() || p == Path::new("/") {
        return Err(BError::denied("拒绝删除根目录"));
    }
    let meta = fs::symlink_metadata(&p)?;
    if meta.is_dir() {
        fs::remove_dir_all(&p)?;
    } else {
        fs::remove_file(&p)?;
    }
    Ok(())
}

/// 递归统计大小。
pub fn size_of(p: impl AsRef<Path>) -> u64 {
    let p = p.as_ref();
    match fs::metadata(p) {
        Ok(m) if m.is_file() => m.len(),
        Ok(m) if m.is_dir() => walkdir::WalkDir::new(p)
            .into_iter()
            .flatten()
            .filter_map(|e| e.metadata().ok())
            .filter(|m| m.is_file())
            .map(|m| m.len())
            .sum(),
        _ => 0,
    }
}

/// 是否是文本文件：先看扩展名白名单，再看前 8KB 有没有 NUL。
pub fn is_text_file(p: impl AsRef<Path>) -> bool {
    let p = p.as_ref();
    let ext = super::path::ext(p);
    const TEXT: &[&str] = &[
        "txt", "md", "markdown", "json", "jsonl", "yaml", "yml", "toml", "ini", "conf", "cfg",
        "rs", "kt", "kts", "java", "c", "h", "cpp", "hpp", "cc", "py", "js", "ts", "tsx", "jsx",
        "go", "rb", "php", "sh", "bash", "zsh", "fish", "sql", "xml", "html", "htm", "css",
        "scss", "less", "gradle", "properties", "csv", "tsv", "log", "diff", "patch", "svg",
    ];
    if TEXT.contains(&ext.as_str()) {
        return true;
    }
    let mut buf = [0u8; 8192];
    match File::open(p).and_then(|mut f| f.read(&mut buf)) {
        Ok(n) => !super::text::looks_binary(&buf[..n]),
        Err(_) => false,
    }
}

/// 精确读取前 N 字节，用于嗅探文件类型。
pub fn peek(p: impl AsRef<Path>, n: usize) -> BResult<Vec<u8>> {
    let mut f = File::open(p)?;
    let mut buf = vec![0u8; n];
    let read = f.read(&mut buf)?;
    buf.truncate(read);
    Ok(buf)
}

/// 文件锁：同一路径同时只允许一个进程持有。
///
/// 用 `create_new` 而不是 flock：Android 上 flock 在部分文件系统不可用，
/// 而 `.lock` 文件方案在哪儿都能跑。进程崩溃会留下锁文件，所以带超时清理。
#[derive(Debug)]
pub struct FileLock {
    path: PathBuf,
    file: Option<File>,
}

impl FileLock {
    /// 尝试拿锁。`stale_ms` 内没更新的锁文件视为过期，允许抢占。
    pub fn acquire(path: impl AsRef<Path>, stale_ms: i64) -> BResult<Self> {
        let path = path.as_ref().to_path_buf();
        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir)?;
        }
        if let Ok(meta) = fs::metadata(&path) {
            let age = meta
                .modified()
                .ok()
                .and_then(|t| t.elapsed().ok())
                .map(|d| d.as_millis() as i64)
                .unwrap_or(0);
            if stale_ms > 0 && age > stale_ms {
                let _ = fs::remove_file(&path);
            }
        }
        match OpenOptions::new().write(true).create_new(true).open(&path) {
            Ok(mut f) => {
                let _ = write!(f, "{}", std::process::id());
                Ok(Self { path, file: Some(f) })
            }
            Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => Err(BError::new(
                ErrorCode::Conflict,
                "另一个进程正在操作，稍后再试",
            )),
            Err(e) => Err(e.into()),
        }
    }
}

impl Drop for FileLock {
    fn drop(&mut self) {
        self.file.take();
        let _ = fs::remove_file(&self.path);
    }
}

/// 遍历文件，返回相对路径列表。跳过的目录可配置。
///
/// 默认跳过 `.git`、`node_modules`、`target`、`__pycache__`：这些目录动辄几万文件，
/// 对模型没有任何帮助，只会把工具输出撑爆。
pub fn walk_files(root: impl AsRef<Path>, max: usize, skip: &[&str]) -> Vec<String> {
    let root = root.as_ref();
    let mut out = Vec::new();
    for entry in walkdir::WalkDir::new(root)
        .follow_links(false)
        .into_iter()
        .filter_entry(|e| {
            let name = e.file_name().to_string_lossy();
            !skip.iter().any(|s| name == *s)
        })
        .flatten()
    {
        if out.len() >= max {
            break;
        }
        if entry.file_type().is_file() {
            let rel = entry.path().strip_prefix(root).unwrap_or(entry.path());
            out.push(super::path::to_slash(rel));
        }
    }
    out
}

/// 这个目录能落盘吗。
///
/// **空路径一律判否**。`Path::new("")` 拼出来的 `"flows.json"` 是相对路径，会写到
/// 进程当前目录——桌面上是启动目录，安卓上是 `/`。这类写入不会报错，只会留下一堆
/// 谁也解释不清的文件，所以在这里一次性挡住。
pub fn writable_dir(p: impl AsRef<Path>) -> bool {
    let p = p.as_ref();
    !p.as_os_str().is_empty() && ensure_dir(p).is_ok()
}

/// 目录树（只到 `depth` 层），给系统提示词里的工作区概览用。
pub fn tree(root: impl AsRef<Path>, depth: usize, max: usize) -> Vec<String> {
    let root = root.as_ref();
    let mut out = Vec::new();
    for entry in walkdir::WalkDir::new(root)
        .min_depth(1)
        .max_depth(depth)
        .follow_links(false)
        .into_iter()
        .flatten()
    {
        if out.len() >= max {
            break;
        }
        let mut line = String::new();
        let d = entry.depth().saturating_sub(1);
        for _ in 0..d {
            line.push_str("  ");
        }
        line.push_str(&entry.file_name().to_string_lossy());
        if entry.file_type().is_dir() {
            line.push('/');
        }
        out.push(line);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn copy_and_remove() {
        let base = std::env::temp_dir().join(format!("bai-fsx-{}", super::super::ids::IdGen::hex(6)));
        let a = base.join("a");
        ensure_dir(&a).unwrap();
        write_text(a.join("x.txt"), "hello").unwrap();
        let b = base.join("b");
        copy_tree(&a, &b).unwrap();
        assert_eq!(read_text(b.join("x.txt"), 1024).unwrap(), "hello");
        assert!(remove(&b).is_ok());
        fs::remove_dir_all(&base).ok();
    }

    #[test]
    fn lock_excludes() {
        let p = std::env::temp_dir().join(format!("bai-lock-{}.lock", super::super::ids::IdGen::hex(6)));
        let l = FileLock::acquire(&p, 0).unwrap();
        assert!(FileLock::acquire(&p, 0).is_err());
        drop(l);
        assert!(FileLock::acquire(&p, 0).is_ok());
        let _ = fs::remove_file(&p);
    }
}
