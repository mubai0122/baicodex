//! 写入路径收敛的静态守卫。
//!
//! 「所有写入统一收敛到基类」如果只写在文档里，下一个人加模块时照样能绕过。
//! 这里直接扫源码：除 `base/fsx.rs` 之外，内核里不允许出现直接写文件的 API。

use std::path::{Path, PathBuf};

/// 允许直接落盘的唯一文件（相对 `kernel/src`）。
const ALLOWED: &[&str] = &["core/base/fsx.rs"];

/// 禁止在业务模块出现的落盘 API。
const FORBIDDEN: &[&str] = &[
    "fs::write(",
    "std::fs::write(",
    "File::create(",
    "OpenOptions::new()",
    "fs::copy(",
    "std::fs::copy(",
];

fn src_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("src")
}

fn walk(dir: &Path, out: &mut Vec<PathBuf>) {
    for e in std::fs::read_dir(dir).unwrap().flatten() {
        let p = e.path();
        if p.is_dir() {
            walk(&p, out);
        } else if p.extension().and_then(|s| s.to_str()) == Some("rs") {
            out.push(p);
        }
    }
}

#[test]
fn only_fsx_may_touch_the_filesystem_for_writes() {
    let root = src_root();
    let mut files = Vec::new();
    walk(&root, &mut files);
    assert!(!files.is_empty());

    let mut bad = Vec::new();
    for f in files {
        let rel = f
            .strip_prefix(&root)
            .unwrap()
            .to_string_lossy()
            .replace('\\', "/");
        if ALLOWED.contains(&rel.as_str()) {
            continue;
        }
        let text = std::fs::read_to_string(&f).unwrap();
        for pat in FORBIDDEN {
            if text.contains(pat) {
                bad.push(format!("{rel}: {pat}"));
            }
        }
    }
    assert!(
        bad.is_empty(),
        "以下位置绕过统一写入层，请改用 Store::write / fsx::write_atomic：\n{}",
        bad.join("\n")
    );
}

#[test]
fn reads_are_allowed_but_listed() {
    // 读操作不受限，但要确认我们清楚哪些模块直接读盘（迁移、密钥来源）
    let root = src_root();
    let mut files = Vec::new();
    walk(&root, &mut files);
    let mut readers = Vec::new();
    for f in files {
        let rel = f
            .strip_prefix(&root)
            .unwrap()
            .to_string_lossy()
            .replace('\\', "/");
        let text = std::fs::read_to_string(&f).unwrap();
        if text.contains("fs::read(") {
            readers.push(rel);
        }
    }
    // 只允许 fsx（原语）、keyring（读主密钥文件）、vault 测试用不到
    for r in &readers {
        assert!(
            r == "core/base/fsx.rs" || r == "core/security/keyring.rs",
            "新增了直接读盘的位置：{r}，请确认是否该走 Store::read"
        );
    }
}
