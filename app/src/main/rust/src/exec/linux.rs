//! 内置 Linux：在自带的根文件系统里跑命令。
//!
//! 优先 proot（不需要 root，Android 上常用），其次 chroot（有 root 时），
//! 最后退到 bwrap（桌面端沙箱）。根文件系统在 `<数据根>/ubuntu`，没装就明确说没装
//! ——"命令不存在"这种模糊错误会让模型反复重试同一件事。
//!
//! ## 为什么要固定一批 bind
//!
//! 根文件系统里的 `/sdcard` 是空的，模型写的路径在宿主上却真实存在。把这几个目录
//! 挂进去之后，两边看到的是同一套路径，提示词里就不需要教模型"容器内路径要换算"。

use std::path::PathBuf;
use std::time::Duration;

use super::shell::{run, run_full, ShellResult, DEFAULT_CAP};

/// 根文件系统目录名。
pub const DIR: &str = "ubuntu";

/// 根文件系统路径。
pub fn rootfs() -> PathBuf {
    crate::core::base::dirs::dir(DIR)
}

/// 装没装。
pub fn installed() -> bool {
    let r = rootfs();
    r.join("bin/sh").exists() || r.join("usr/bin/env").exists() || r.join("bin/busybox").exists()
}

/// 版本信息（装了就顺手读一下 `/etc/os-release`，给设置页显示）。
pub fn version() -> String {
    if !installed() {
        return String::new();
    }
    let p = rootfs().join("etc/os-release");
    let Ok(t) = std::fs::read_to_string(p) else { return String::new() };
    for line in t.lines() {
        if let Some(v) = line.strip_prefix("PRETTY_NAME=") {
            return v.trim_matches('"').to_string();
        }
    }
    String::new()
}

/// 挂载进容器的目录：宿主路径 → 容器路径。
///
/// 只挂数据类目录，`/system`、`/proc` 这类由 proot 自己处理，重复挂会打架。
fn binds() -> Vec<(String, String)> {
    let mut v: Vec<(String, String)> = Vec::new();
    for p in ["/sdcard", "/storage/emulated/0"] {
        if std::path::Path::new(p).is_dir() {
            v.push((p.to_string(), p.to_string()));
        }
    }
    let home = crate::core::base::path::home();
    if home.is_dir() {
        v.push((home.to_string_lossy().to_string(), "/hosthome".into()));
    }
    // 数据根一定要挂：模型读写配置、技能包都在那。
    let base = crate::core::base::dirs::base();
    v.push((base.to_string_lossy().to_string(), "/baicode".into()));
    v
}

/// 用当前可用的后端跑一条命令。
pub async fn run_in(command: &str, timeout: Duration) -> ShellResult {
    if command.trim().is_empty() {
        return ShellResult::fail("命令为空");
    }
    if !installed() {
        return ShellResult::fail(format!(
            "内置 Linux 没装：设置 → 扩展 → Linux 环境里安装一次（或把根文件系统解到 {}）",
            rootfs().to_string_lossy()
        ));
    }
    let root = rootfs().to_string_lossy().to_string();

    // 1) proot：不需要 root，Android 首选。
    if super::shell::has("proot") {
        let mut b = String::new();
        for (host, guest) in binds() {
            b.push_str(&format!(" -b {}:{}", super::shell::quote(&host), super::shell::quote(&guest)));
        }
        let script = format!(
            "proot -0 -r {r}{b} -w /root /bin/sh -c {c}",
            r = super::shell::quote(&root),
            b = b,
            c = super::shell::quote(command)
        );
        return run(&script, None, timeout, None).await;
    }

    // 2) chroot：要有 root。
    if super::root::available().await {
        let script = format!(
            "chroot {r} /bin/sh -c {c}",
            r = super::shell::quote(&root),
            c = super::shell::quote(command)
        );
        return super::root::run(&script, timeout).await;
    }

    // 3) bwrap：桌面端无 root 时用。
    if super::shell::has("bwrap") {
        let root_ref = root.as_str();
        let mut argv: Vec<&str> = vec!["bwrap", "--dev-bind", "/", "/", "--bind", root_ref, "/", "--chdir", "/root"];
        // bwrap 的 -b 参数是可变长列表，这里手工拼成 "host guest" 两组。
        let mut bind_args: Vec<String> = Vec::new();
        for (host, guest) in binds() {
            bind_args.push("--bind".into());
            bind_args.push(host);
            bind_args.push(guest);
        }
        let bind_refs: Vec<&str> = bind_args.iter().map(|s| s.as_str()).collect();
        argv.extend(bind_refs);
        argv.extend(["/bin/sh", "-c", command]);
        return run_full(&argv, None, timeout, None, DEFAULT_CAP, &[]).await;
    }

    ShellResult::fail("这台机器既没有 proot，也没有 root / bwrap，跑不了内置 Linux")
}

/// 只探测能不能跑（不真的执行用户命令）。
pub async fn available() -> bool {
    installed()
        && (super::shell::has("proot") || super::shell::has("bwrap") || super::root::available().await)
}

/// 后端名字，给界面显示"当前走 proot"。
pub async fn backend() -> &'static str {
    if !installed() {
        return "未安装";
    }
    if super::shell::has("proot") {
        "proot"
    } else if super::root::available().await {
        "chroot"
    } else if super::shell::has("bwrap") {
        "bwrap"
    } else {
        "不可用"
    }
}

/// 在容器里跑 argv（不经 shell），参数里的空格不会被解释。
pub async fn exec_argv(argv: &[&str], timeout: Duration) -> ShellResult {
    if argv.is_empty() {
        return ShellResult::fail("命令为空");
    }
    let quoted: Vec<String> = argv.iter().map(|a| super::shell::quote(a)).collect();
    run_in(&quoted.join(" "), timeout).await
}

/// 确保目录存在（容器内路径），返回是否成功。
pub async fn mkdir(path: &str) -> bool {
    run_in(&format!("mkdir -p {}", super::shell::quote(path)), Duration::from_secs(30))
        .await
        .ok()
}

/// 打包成一条 argv（给上层复用，避免每处都拼字符串）。
pub fn build_argv(program: &str, args: &[&str]) -> Vec<String> {
    let mut v = vec![program.to_string()];
    v.extend(args.iter().map(|s| s.to_string()));
    v
}
