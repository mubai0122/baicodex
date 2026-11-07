//! 数据根与临时文件。
//!
//! 整个进程只有一个数据根（`~/.baicode` 或 `$BAICODE_HOME`）。为什么放在 `base`
//! 而不是 `feature`：`exec` 层要写截图、要落临时脚本、要放 ui.xml，但它不能反过来
//! 依赖 `feature`。把"根目录在哪"变成一个进程级事实，谁都能问，谁都不用认识配置结构。
//!
//! 约定：
//!
//! - [base] 返回数据根，目录不存在时**保证创建**（`ensure` 显式调用时才建）；
//! - [file] 落在根下，文件名里的 `/` 会被压平成 `_`，防止 `../` 逃逸；
//! - [tmp] 落在 `<base>/tmp`，进程启动后第一次用会顺手清掉 24 小时前的残留；
//! - [new_dir] / [new_file] 生成不冲突的唯一名，用于"新会话工作区""截图"这类场景。

use std::path::{Path, PathBuf};
use std::sync::{OnceLock, RwLock};
use std::time::Duration;

use super::path;

/// 临时文件保留时长。超过的在下一次 [sweep] 时删除。
const TMP_TTL: Duration = Duration::from_secs(24 * 3600);

fn slot() -> &'static RwLock<Option<PathBuf>> {
    static S: OnceLock<RwLock<Option<PathBuf>>> = OnceLock::new();
    S.get_or_init(|| RwLock::new(None))
}

/// 默认数据根：`$BAICODE_HOME` 优先，其次 `~/.baicode`。
///
/// 不做存在性判断——路径算出来就该稳定，建不建是调用方的事。
pub fn default_base() -> PathBuf {
    if let Ok(v) = std::env::var("BAICODE_HOME") {
        let v = v.trim();
        if !v.is_empty() {
            return PathBuf::from(v);
        }
    }
    path::home().join(".baicode")
}

/// 设定数据根（前端启动时调一次，之后全进程统一）。
///
/// 传空串等于恢复默认。返回最终生效的路径。
pub fn set_base(p: impl AsRef<Path>) -> PathBuf {
    let p = p.as_ref();
    let v = if p.as_os_str().is_empty() { default_base() } else { path::normalize(p) };
    if let Ok(mut w) = slot().write() {
        *w = Some(v.clone());
    }
    v
}

/// 当前数据根。没设过就用 [default_base]。
pub fn base() -> PathBuf {
    if let Ok(r) = slot().read() {
        if let Some(p) = r.as_ref() {
            return p.clone();
        }
    }
    default_base()
}

/// 数据根下的文件路径（不创建）。
///
/// `name` 里的分隔符会被替换成 `_`：模型给的字符串不能直接当路径拼。
pub fn file(name: &str) -> PathBuf {
    base().join(flat(name))
}

/// 数据根下的子目录路径（不创建）。
pub fn dir(name: &str) -> PathBuf {
    base().join(flat(name))
}

/// 数据根下的子目录，确保存在。建不出来时退回 `tmp_dir()`，保证返回值一定可写。
pub fn dir_or_tmp(name: &str) -> PathBuf {
    let d = dir(name);
    if super::fsx::ensure_dir(&d).is_ok() {
        d
    } else {
        super::sys::tmp_dir()
    }
}

/// 临时目录 `<base>/tmp`，确保存在。
pub fn tmp_dir() -> PathBuf {
    let d = base().join("tmp");
    if super::fsx::ensure_dir(&d).is_ok() {
        d
    } else {
        // 数据根都写不了（只读挂载 / 权限被收走），退到系统临时目录。
        std::env::temp_dir()
    }
}

/// 临时文件路径（不创建），带随机后缀。
pub fn tmp(name: &str) -> PathBuf {
    tmp_dir().join(format!("{}-{}", flat(name), super::ids::IdGen::hex(4)))
}

/// 新建一个唯一目录：`<base>/<prefix>/<时间>-<随机>`。
///
/// 用于"新会话工作区"这类需要独占目录的场景。建不出来就退回临时目录下的随机名。
pub fn new_dir(prefix: &str) -> PathBuf {
    let stamp = format!("{}-{}", super::clock::now_s(), super::ids::IdGen::hex(3));
    let p = dir(&flat(prefix)).join(stamp);
    if super::fsx::ensure_dir(&p).is_ok() {
        return p;
    }
    let fallback = std::env::temp_dir().join(format!("baicode-{}", super::ids::IdGen::hex(6)));
    let _ = super::fsx::ensure_dir(&fallback);
    fallback
}

/// 新建一个唯一文件路径：`<dir>/<prefix>-<随机>.<ext>`，不创建文件本身。
pub fn new_file(dir: impl AsRef<Path>, prefix: &str, ext: &str) -> PathBuf {
    let name = if ext.is_empty() {
        format!("{}-{}", flat(prefix), super::ids::IdGen::hex(6))
    } else {
        format!("{}-{}.{}", flat(prefix), super::ids::IdGen::hex(6), ext.trim_start_matches('.'))
    };
    dir.as_ref().join(name)
}

/// 截图落盘路径：`<base>/shots/shot-<随机>.png`，目录确保存在。
pub fn new_shot() -> PathBuf {
    new_file(dir_or_tmp("shots"), "shot", "png")
}

/// 删掉 `tmp` 目录里过期的残留。启动时调一次就够。
///
/// 只删普通文件；目录不动（可能是正在用的解压目标）。
pub fn sweep() -> usize {
    let d = tmp_dir();
    let Ok(rd) = std::fs::read_dir(&d) else { return 0 };
    let now = std::time::SystemTime::now();
    let mut n = 0;
    for e in rd.flatten() {
        let Ok(md) = e.metadata() else { continue };
        if !md.is_file() {
            continue;
        }
        let old = md
            .modified()
            .ok()
            .and_then(|t| now.duration_since(t).ok())
            .map(|d| d > TMP_TTL)
            .unwrap_or(false);
        if old && std::fs::remove_file(e.path()).is_ok() {
            n += 1;
        }
    }
    n
}

/// 把 name 压平成一个安全的文件名片段：分隔符换 `_`，`..` 换 `_`，去掉控制字符。
fn flat(name: &str) -> String {
    let mut s = String::with_capacity(name.len());
    for c in name.trim().chars() {
        match c {
            '/' | '\\' | '\0' => s.push('_'),
            c if c.is_control() => s.push('_'),
            c => s.push(c),
        }
    }
    while s.contains("..") {
        s = s.replace("..", "_");
    }
    if s.is_empty() {
        "unnamed".into()
    } else {
        s
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn flatten_blocks_traversal() {
        assert!(!flat("../../etc/passwd").contains('/'));
        assert!(!flat("a/../b").contains(".."));
        assert_eq!(flat(""), "unnamed");
    }

    #[test]
    fn base_is_stable() {
        let a = base();
        let b = base();
        assert_eq!(a, b);
    }
}
