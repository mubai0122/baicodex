//! 路径处理。
//!
//! 这一层的存在意义只有一个：**任何来自模型或用户的路径，在碰系统之前先过这里**。
//! 模型会写出 `../../etc/passwd`，也会写出带软链的目录；直接 `Path::join` 是把
//! 沙箱的门开着。
//!
//! 两类检查要分清：
//! - [normalize] + [is_within]：纯字符串层面的规整，**不触盘**，快，防 `..`。
//! - [real_path] + [real_within]：解析软链后的真实路径，慢一点，防"软链指向外面"。
//!
//! 涉及写入、删除时两个都要做。

use std::path::{Component, Path, PathBuf};

/// 纯字符串规整：消掉 `.`、折叠 `..`、去掉末尾斜杠，不碰文件系统。
///
/// `..` 在根部时保留（`/../x` → `/x` 的语义交给系统），但相对路径上的 `..` 会被
/// 真正回退——这是与 `PathBuf::push` 最大的不同。
pub fn normalize(p: impl AsRef<Path>) -> PathBuf {
    let p = p.as_ref();
    let mut out = PathBuf::new();
    for c in p.components() {
        match c {
            Component::CurDir => {}
            Component::ParentDir => {
                let popped = matches!(
                    out.components().next_back(),
                    Some(Component::Normal(_))
                );
                if popped {
                    out.pop();
                } else if !p.is_absolute() {
                    out.push("..");
                }
                // 绝对路径的根部 `..` 直接丢弃：/.. == /
            }
            other => out.push(other.as_os_str()),
        }
    }
    if out.as_os_str().is_empty() {
        out.push(".");
    }
    out
}

/// `~` / `~/x` 展开成 home。其它路径原样返回。
pub fn expand_tilde(p: &str) -> PathBuf {
    if p == "~" {
        return home();
    }
    if let Some(rest) = p.strip_prefix("~/") {
        return home().join(rest);
    }
    PathBuf::from(p)
}

/// 当前用户 home。拿不到就退回当前目录（宁可路径怪一点，也别 panic）。
pub fn home() -> PathBuf {
    dirs::home_dir().unwrap_or_else(|| PathBuf::from("."))
}

/// 字符串层面判断 `child` 是否在 `parent` 里面。
///
/// 两边都先 [normalize]；不触盘，所以对"还不存在的文件"同样有效。
/// 注意：`/a/bc` 不算在 `/a/b` 里（按组件比较，不是按字符串前缀）。
pub fn is_within(parent: impl AsRef<Path>, child: impl AsRef<Path>) -> bool {
    let parent = normalize(parent);
    let child = normalize(child);
    child.starts_with(&parent)
}

/// 解析出"最接近真实的路径"：从最深的已存在祖先开始 canonicalize，再把不存在的
/// 尾巴拼回去。
///
/// 为什么不用 `fs::canonicalize` 一把梭：写入新文件时目标还不存在，canonicalize
/// 会失败；而只检查父目录又会被 `a/../b` 绕过。
pub fn real_path(p: impl AsRef<Path>) -> PathBuf {
    let p = normalize(p);
    if let Ok(c) = p.canonicalize() {
        return c;
    }
    let mut tail: Vec<std::ffi::OsString> = Vec::new();
    let mut cur = p.clone();
    loop {
        match cur.parent() {
            Some(parent) if !parent.as_os_str().is_empty() => {
                if let Some(name) = cur.file_name() {
                    tail.push(name.to_os_string());
                }
                if let Ok(c) = parent.canonicalize() {
                    let mut out = c;
                    for seg in tail.iter().rev() {
                        out.push(seg);
                    }
                    return out;
                }
                cur = parent.to_path_buf();
            }
            _ => return p,
        }
    }
}

/// 解析软链后判断是否越界。写入/删除前的最后一道闸。
pub fn real_within(parent: impl AsRef<Path>, child: impl AsRef<Path>) -> bool {
    let parent = real_path(parent);
    let child = real_path(child);
    child.starts_with(&parent)
}

/// 把用户给的相对路径安全地拼到根上。
///
/// 拒绝：绝对路径、Windows 盘符、`..` 逃逸。允许：`a/b/../c`（规整后在根内）。
pub fn safe_join(root: impl AsRef<Path>, rel: &str) -> Option<PathBuf> {
    if rel.is_empty() {
        return Some(normalize(root));
    }
    let p = Path::new(rel);
    if p.is_absolute() {
        return None;
    }
    if p.components().any(|c| matches!(c, Component::Prefix(_))) {
        return None;
    }
    let joined = normalize(root.as_ref().join(p));
    if is_within(root, &joined) {
        Some(joined)
    } else {
        None
    }
}

/// 相对化。算不出来就返回原路径。
pub fn relative_to(base: impl AsRef<Path>, p: impl AsRef<Path>) -> PathBuf {
    let base = normalize(base);
    let p = normalize(p);
    match p.strip_prefix(&base) {
        Ok(rest) => {
            if rest.as_os_str().is_empty() {
                PathBuf::from(".")
            } else {
                rest.to_path_buf()
            }
        }
        Err(_) => p,
    }
}

/// 文件名（含扩展名）。末尾是 `..` 之类的怪路径返回空串。
pub fn file_name(p: impl AsRef<Path>) -> String {
    p.as_ref()
        .file_name()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default()
}

/// 扩展名，小写，不带点。
pub fn ext(p: impl AsRef<Path>) -> String {
    p.as_ref()
        .extension()
        .map(|s| s.to_string_lossy().to_ascii_lowercase())
        .unwrap_or_default()
}

/// 主名（不含扩展名）。
pub fn stem(p: impl AsRef<Path>) -> String {
    p.as_ref()
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default()
}

/// 同名文件已存在时找一个不冲突的名字：`a.txt` → `a (1).txt`。
///
/// 只处理到 999，超过就直接加随机后缀——说明目录里已经堆了上千个同名文件，
/// 再线性探测纯属浪费。
pub fn unique_path(dir: impl AsRef<Path>, name: &str) -> PathBuf {
    let dir = dir.as_ref();
    let candidate = dir.join(name);
    if !candidate.exists() {
        return candidate;
    }
    let stem = stem(name);
    let e = ext(name);
    for i in 1..1000 {
        let n = if e.is_empty() {
            format!("{stem} ({i})")
        } else {
            format!("{stem} ({i}).{e}")
        };
        let p = dir.join(n);
        if !p.exists() {
            return p;
        }
    }
    let n = if e.is_empty() {
        format!("{stem}-{}", crate::core::base::ids::IdGen::hex(6))
    } else {
        format!("{stem}-{}.{e}", crate::core::base::ids::IdGen::hex(6))
    };
    dir.join(n)
}

/// 统一成正斜杠，便于展示与比较（Windows 上也能读）。
pub fn to_slash(p: impl AsRef<Path>) -> String {
    p.as_ref().to_string_lossy().replace('\\', "/")
}

/// 是否隐藏文件（`.` 开头，但排除 `.` 与 `..`）。
pub fn is_hidden(p: impl AsRef<Path>) -> bool {
    let n = file_name(p);
    n.starts_with('.') && n != "." && n != ".."
}

/// 把路径折成"家目录用 ~ 表示"的短形式，界面上更省地方。
pub fn tildify(p: impl AsRef<Path>) -> String {
    let p = normalize(p);
    let h = home();
    if let Ok(rest) = p.strip_prefix(&h) {
        if rest.as_os_str().is_empty() {
            return "~".into();
        }
        return format!("~/{}", to_slash(rest));
    }
    to_slash(p)
}

/// 从一段文本里挑出像路径的片段。技能参数、报错信息里经常夹着路径。
pub fn looks_like_path(s: &str) -> bool {
    s.starts_with('/')
        || s.starts_with("~/")
        || s.starts_with("./")
        || s.starts_with("../")
        || (s.len() > 2 && s.as_bytes()[1] == b':')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_dotdot() {
        assert_eq!(normalize("/a/b/../c"), PathBuf::from("/a/c"));
        assert_eq!(normalize("/../etc"), PathBuf::from("/etc"));
        assert_eq!(normalize("a/./b//c"), PathBuf::from("a/b/c"));
    }

    #[test]
    fn safe_join_blocks_escape() {
        assert!(safe_join("/srv/ws", "../etc/passwd").is_none());
        assert!(safe_join("/srv/ws", "/etc/passwd").is_none());
        assert_eq!(safe_join("/srv/ws", "a/../b").unwrap(), PathBuf::from("/srv/ws/b"));
    }

    #[test]
    fn within_by_component() {
        assert!(is_within("/a/b", "/a/b/c"));
        assert!(!is_within("/a/b", "/a/bc"));
    }

    #[test]
    fn unique_names() {
        let d = std::env::temp_dir().join(format!("bai-test-{}", crate::core::base::ids::IdGen::hex(6)));
        std::fs::create_dir_all(&d).unwrap();
        std::fs::write(d.join("a.txt"), b"x").unwrap();
        let p = unique_path(&d, "a.txt");
        assert_eq!(p.file_name().unwrap(), "a (1).txt");
        std::fs::remove_dir_all(&d).ok();
    }
}
