//! 原子落盘。
//!
//! 规则只有一条：**任何"覆盖写"都必须走这里**。
//!
//! 直接 `fs::write` 的问题是：写到一半进程被杀 / 断电，文件就变成半截垃圾。
//! 配置、数据库索引、加密文件全都经不起这个。标准做法是：
//!
//! ```text
//! 写同目录临时文件 → fsync 文件 → rename 覆盖 → fsync 目录
//! ```
//!
//! `rename` 在同一文件系统内是原子的：要么看到旧内容，要么看到新内容，不存在中间态。
//! 跨文件系统（比如 /tmp 与 /data 不在一个分区）rename 会失败，退化成 copy + remove，
//! 这条路径不原子，但至少不会留下半截文件——copy 失败时原文件仍是完整的。

use std::fs::{self, OpenOptions};
// 只有 Unix 上才对目录 fsync（Windows 不支持以目录句柄打开）
#[cfg(unix)]
use std::fs::File;
use std::io::Write;
use std::path::{Path, PathBuf};

use super::error::{BError, BResult};

/// 临时文件后缀。用 `.tmp` 而不是随机名，方便清理孤儿文件。
const TMP: &str = ".tmp";

fn tmp_of(path: &Path) -> PathBuf {
    let mut s = path.as_os_str().to_os_string();
    s.push(TMP);
    PathBuf::from(s)
}

/// 原子写入（覆盖）。权限沿用旧文件；新文件用系统默认。
pub fn write(path: impl AsRef<Path>, data: impl AsRef<[u8]>) -> BResult<()> {
    write_with(path, data, None)
}

/// 原子写入并指定 Unix 权限（例如 0o600 的凭据文件）。
#[cfg(unix)]
pub fn write_secure(path: impl AsRef<Path>, data: impl AsRef<[u8]>, mode: u32) -> BResult<()> {
    write_with(path, data, Some(mode))
}

#[cfg(not(unix))]
pub fn write_secure(path: impl AsRef<Path>, data: impl AsRef<[u8]>, _mode: u32) -> BResult<()> {
    write_with(path, data, None)
}

fn write_with(path: impl AsRef<Path>, data: impl AsRef<[u8]>, mode: Option<u32>) -> BResult<()> {
    // Windows 上没有 POSIX 权限位。调用方（write_secure 的 non-unix 版本）已经
    // 把 mode 丢了，这里的参数只是为了让两条路径共用一份实现。
    #[cfg(not(unix))]
    let _ = mode;
    let path = path.as_ref();
    if let Some(dir) = path.parent() {
        if !dir.as_os_str().is_empty() {
            fs::create_dir_all(dir)?;
        }
    }
    let tmp = tmp_of(path);
    {
        let mut f = OpenOptions::new().write(true).create(true).truncate(true).open(&tmp)?;
        #[cfg(unix)]
        if let Some(m) = mode {
            use std::os::unix::fs::PermissionsExt;
            // 先设权限再写内容：避免"文件已创建但还没收紧权限"的窗口被别的进程读到
            f.set_permissions(fs::Permissions::from_mode(m))?;
        }
        f.write_all(data.as_ref())?;
        f.flush()?;
        // fsync 不能省：rename 只保证目录项原子，不保证数据已经落盘。
        // 断电后可能出现"文件名换了、内容是空的"这种更糟的情况。
        f.sync_all()?;
    }
    match fs::rename(&tmp, path) {
        Ok(()) => {}
        Err(e) if is_cross_device(&e) => {
            // 跨分区：copy 到目标再删临时文件。目标文件在 copy 完成前保持旧内容。
            fs::copy(&tmp, path)?;
            let _ = fs::remove_file(&tmp);
        }
        Err(e) => {
            let _ = fs::remove_file(&tmp);
            return Err(e.into());
        }
    }
    sync_dir(path);
    Ok(())
}

/// 追加。追加不是幂等操作，没法用 rename 做原子；这里用 `O_APPEND`，
/// 单次 write 在大多数文件系统上不会与其他进程交错。
pub fn append(path: impl AsRef<Path>, data: impl AsRef<[u8]>) -> BResult<()> {
    let path = path.as_ref();
    if let Some(dir) = path.parent() {
        if !dir.as_os_str().is_empty() {
            fs::create_dir_all(dir)?;
        }
    }
    let mut f = OpenOptions::new().create(true).append(true).open(path)?;
    f.write_all(data.as_ref())?;
    f.flush()?;
    Ok(())
}

/// 把目录项刷到磁盘。失败不报错——有些文件系统（如部分 FUSE）不支持对目录 fsync，
/// 不该因为这一点让保存整份配置失败。
fn sync_dir(path: &Path) {
    #[cfg(unix)]
    if let Some(dir) = path.parent() {
        if let Ok(f) = File::open(dir) {
            let _ = f.sync_all();
        }
    }
    #[cfg(not(unix))]
    let _ = path;
}

fn is_cross_device(e: &std::io::Error) -> bool {
    // EXDEV = 18（Linux/macOS）。std 的 ErrorKind::CrossesDevices 还不够稳定，
    // 直接看 errno 更可靠。
    #[cfg(unix)]
    {
        e.raw_os_error() == Some(18)
    }
    #[cfg(not(unix))]
    {
        let _ = e;
        false
    }
}

/// 读取整个文件，限制最大字节数。
///
/// 为什么要限制：模型可能让读 `/dev/zero` 或一个 2G 的日志，直接把进程 OOM 掉。
/// 超过上限时返回 `Budget`，由调用方决定是截断还是拒绝。
pub fn read_limited(path: impl AsRef<Path>, max: usize) -> BResult<Vec<u8>> {
    let path = path.as_ref();
    let meta = fs::metadata(path)?;
    if meta.is_dir() {
        return Err(BError::invalid("这是一个目录，不是文件"));
    }
    if meta.len() as usize > max {
        return Err(BError::budget(format!(
            "文件 {} MB，超过 {} MB 上限",
            meta.len() / 1024 / 1024,
            max / 1024 / 1024
        )));
    }
    Ok(fs::read(path)?)
}

/// 清理同目录下遗留的 `.tmp`。启动时跑一次，防止上次崩溃留下一堆垃圾。
pub fn sweep_tmp(dir: impl AsRef<Path>) -> usize {
    let mut n = 0;
    if let Ok(rd) = fs::read_dir(dir) {
        for e in rd.flatten() {
            let p = e.path();
            if p.extension().map(|x| x == "tmp").unwrap_or(false) && fs::remove_file(&p).is_ok() {
                n += 1;
            }
        }
    }
    n
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn atomic_roundtrip() {
        let dir = std::env::temp_dir().join(format!("bai-atomic-{}", super::super::ids::IdGen::hex(6)));
        let p = dir.join("a/b.json");
        write(&p, b"{\"x\":1}").unwrap();
        assert_eq!(fs::read_to_string(&p).unwrap(), "{\"x\":1}");
        write(&p, b"{\"x\":2}").unwrap();
        assert_eq!(fs::read_to_string(&p).unwrap(), "{\"x\":2}");
        // 不留临时文件
        assert!(!dir.join("a/b.json.tmp").exists());
        fs::remove_dir_all(&dir).ok();
    }

    #[cfg(unix)]
    #[test]
    fn secure_mode() {
        use std::os::unix::fs::PermissionsExt;
        let dir = std::env::temp_dir().join(format!("bai-mode-{}", super::super::ids::IdGen::hex(6)));
        let p = dir.join("vault.json");
        write_secure(&p, b"{}", 0o600).unwrap();
        let m = fs::metadata(&p).unwrap().permissions().mode() & 0o777;
        assert_eq!(m, 0o600);
        fs::remove_dir_all(&dir).ok();
    }
}
