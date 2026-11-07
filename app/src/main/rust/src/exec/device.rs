//! 系统交互：剪贴板 / 打开链接 / 分享 / 本地通知 / 通知记录。
//!
//! 桌面端按 X11 / Wayland / macOS 分别找工具；Android 端的剪贴板与通知由平台层
//! （Kotlin）接管——Rust 这里只给出"桌面能做到的那部分"，做不到就说清楚为什么。
//!
//! ## 为什么不用 `xdotool` 发按键
//!
//! 键盘布局不同，`type` 出来的字符会错位。剪贴板是最可靠的通道，所有平台都有。

use std::time::Duration;

use super::shell;
use crate::core::base::{dirs, fsx};

/// 读剪贴板。
pub async fn clip_get() -> String {
    if crate::core::base::sys::is_android() {
        return "Android 端剪贴板由平台层提供（Kotlin ClipboardManager），这里拿不到".into();
    }
    for (bin, args) in [
        ("wl-paste", vec!["-n"]),
        ("xclip", vec!["-selection", "clipboard", "-o"]),
        ("xsel", vec!["-b", "-o"]),
        ("pbpaste", vec![]),
    ] {
        if !shell::has(bin) {
            continue;
        }
        let mut argv = vec![bin];
        argv.extend(args);
        let r = shell::run_argv(&argv, None, Duration::from_secs(5), None).await;
        if r.ok() {
            return r.out;
        }
    }
    "失败：拿不到剪贴板（装 wl-clipboard / xclip / xsel 之一）".into()
}

/// 写剪贴板。
pub async fn clip_set(text: &str) -> String {
    if crate::core::base::sys::is_android() {
        return "Android 端剪贴板由平台层提供（Kotlin ClipboardManager），这里写不进去".into();
    }
    for (bin, args) in [
        ("wl-copy", vec![]),
        ("xclip", vec!["-selection", "clipboard"]),
        ("xsel", vec!["-b", "-i"]),
        ("pbcopy", vec![]),
    ] {
        if !shell::has(bin) {
            continue;
        }
        let mut argv = vec![bin];
        argv.extend(args);
        // 走 stdin 而不是命令行参数：内容里有换行 / 引号时不会破坏命令。
        let r = shell::run_full(&argv, None, Duration::from_secs(8), Some(text), 4096, &[]).await;
        if r.ok() {
            return format!("已复制 {} 字", text.chars().count());
        }
    }
    "失败：写不进剪贴板（装 wl-clipboard / xclip / xsel 之一）".into()
}

/// 打开链接。
pub async fn open_url(url: &str) -> String {
    let u = url.trim();
    if u.is_empty() {
        return "链接为空".into();
    }
    if !(u.starts_with("http://") || u.starts_with("https://") || u.starts_with("file://") || u.starts_with("mailto:")) {
        return "只接受 http(s):// 、file:// 或 mailto: 链接".into();
    }
    // 先挡掉带 shell 元字符的 URL：xdg-open 会被 shell 解释。
    if u.chars().any(|c| matches!(c, '`' | '$' | ';' | '|' | '&' | '\n' | '\r')) {
        return "链接里有可疑字符，拒绝执行".into();
    }
    if crate::core::base::sys::is_android() {
        let r = shell::run(
            &format!(
                "am start -a android.intent.action.VIEW -d {} >/dev/null 2>&1",
                shell::quote(u)
            ),
            None,
            Duration::from_secs(10),
            None,
        )
        .await;
        return if r.ok() { format!("已交给系统打开：{u}") } else { format!("失败：{}", r.out) };
    }
    for bin in ["xdg-open", "open", "sensible-browser", "gio"] {
        if !shell::has(bin) {
            continue;
        }
        let argv: Vec<&str> = if bin == "gio" { vec!["gio", "open", u] } else { vec![bin, u] };
        let r = shell::run_full(&argv, None, Duration::from_secs(10), None, 4096, &[]).await;
        if r.ok() {
            return format!("已交给系统打开：{u}");
        }
    }
    format!("失败：没有可用的打开方式（{u}）")
}

/// 分享文本。
///
/// 桌面端没有统一的分享面板：落到剪贴板并说明。Android 端由平台层弹 Intent。
pub async fn share_text(text: &str) -> String {
    if crate::core::base::sys::is_android() {
        return "Android 端分享由平台层弹 Intent（Kotlin），这里只做剪贴板兜底".into();
    }
    let r = clip_set(text).await;
    format!("{r}（桌面端没有系统分享面板，已放进剪贴板）")
}

/// 发一条本地通知。
pub async fn notify(title: &str, body: &str) -> String {
    let t = if title.trim().is_empty() { "baicodex" } else { title.trim() };
    if crate::core::base::sys::is_android() {
        let r = shell::run(
            &format!(
                "cmd notification post -S bigtext -t {} baicode {} >/dev/null 2>&1",
                shell::quote(t),
                shell::quote(body)
            ),
            None,
            Duration::from_secs(10),
            None,
        )
        .await;
        return if r.ok() { "已发送通知".into() } else { format!("失败：{}（部分 ROM 限制 cmd notification）", r.out) };
    }
    if shell::has("notify-send") {
        let r = shell::run_argv(&["notify-send", t, body], None, Duration::from_secs(10), None).await;
        if r.ok() {
            return "已发送通知".into();
        }
    }
    if shell::has("osascript") {
        let script = format!(
            "display notification {} with title {}",
            apple_quote(body),
            apple_quote(t)
        );
        let r = shell::run_argv(&["osascript", "-e", &script], None, Duration::from_secs(10), None).await;
        if r.ok() {
            return "已发送通知".into();
        }
    }
    // 最后兜底：写进本地通知记录，界面上仍能看到。
    append_record(t, body);
    "已写入本地通知记录（这台机器没有 notify-send / osascript）".into()
}

/// 通知记录文件。
fn record_path() -> std::path::PathBuf {
    dirs::file("notifications.log")
}

fn append_record(title: &str, body: &str) {
    let line = format!(
        "[{}] {}: {}\n",
        crate::core::base::clock::now_ms(),
        title.replace('\n', " "),
        body.replace('\n', " ")
    );
    let _ = fsx::ensure_dir(dirs::base());
    let _ = crate::core::base::atomic::append(record_path(), line.as_bytes());
}

/// 读通知记录（最近的在前）。
///
/// Android 端真正的通知监听在平台层，这里读的是本应用自己落盘的那一份
/// ——够用来回答"刚才那条通知说了什么"。
pub fn notifications(limit: usize, filter: &str) -> String {
    let Ok(text) = std::fs::read_to_string(record_path()) else {
        return "没有通知记录（需要通知监听权限；当前平台不支持时这里恒为空）".into();
    };
    let f = filter.trim().to_lowercase();
    let rows: Vec<&str> = text
        .lines()
        .rev()
        .filter(|l| f.is_empty() || l.to_lowercase().contains(&f))
        .take(limit.clamp(1, 200))
        .collect();
    if rows.is_empty() {
        return "没有匹配的通知".into();
    }
    let mut s = format!("共 {} 条（最近的在前）：\n", rows.len());
    for r in rows {
        // 时间戳转成人能看的格式，原始毫秒留给日志。
        match r.strip_prefix('[').and_then(|x| x.split_once(']')) {
            Some((ms, rest)) => {
                let at = ms.parse::<i64>().unwrap_or(0);
                s.push_str(&format!("{} {}\n", crate::core::base::clock::fmt_minute(at), rest.trim()));
            }
            None => {
                s.push_str(r);
                s.push('\n');
            }
        }
    }
    s.trim_end().to_string()
}

/// 清空通知记录。
pub fn clear_notifications() -> bool {
    std::fs::remove_file(record_path()).is_ok()
}

fn apple_quote(s: &str) -> String {
    format!("\"{}\"", s.replace('\\', "\\\\").replace('"', "\\\""))
}

/// 设备信息一行（给提示词用）。
pub fn device_line() -> String {
    crate::core::base::sys::device_line()
}
