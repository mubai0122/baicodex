//! 起进程。
//!
//! 三条通道（沙箱 / root / Shizuku）只是启动方式不同，收尾逻辑完全一样，
//! 所以全部落在这里的 [run_argv]：写 stdin → 读 stdout+stderr → 等退出 → 判超时。
//!
//! ## 为什么要自己读管道，而不用 `wait_with_output`
//!
//! `wait_with_output` 会把输出全读进内存。`cat /dev/urandom`、`yes`、编译日志刷屏
//! 这类命令能在两秒内吃掉几个 G，进程被 OOM 杀掉时用户只看到"闪退"。
//! 这里改成带上限地读：读满上限后**继续排空但不保存**，子进程不会因为管道满而卡死，
//! 内存也有天花板。
//!
//! ## 超时
//!
//! 用 `kill_on_drop(true)` + `tokio::time::timeout`：超时后 future 被丢弃，
//! Child 随之 drop，内核收到 SIGKILL。不需要额外记 pid 去杀。

use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::time::Duration;

use tokio::io::{AsyncRead, AsyncReadExt, AsyncWriteExt};
use tokio::process::Command;

/// 默认输出上限。单条命令吐超过 1MiB 基本等于失控，保留头部足够定位问题。
pub const DEFAULT_CAP: usize = 1024 * 1024;

/// 一条命令的执行结果。
#[derive(Debug, Clone, Default)]
pub struct ShellResult {
    /// 退出码。-1 表示没跑起来 / 超时 / 被信号杀。
    pub code: i32,
    /// stdout 与 stderr 合并（保持原始先后顺序做不到，先 stdout 再 stderr）。
    pub out: String,
    /// 输出被截断过。
    pub truncated: bool,
    /// 实际耗时。
    pub dur_ms: i64,
}

impl ShellResult {
    pub fn ok(&self) -> bool {
        self.code == 0
    }

    pub fn fail(msg: impl Into<String>) -> Self {
        Self { code: -1, out: msg.into(), truncated: false, dur_ms: 0 }
    }

    /// 给模型看的一行状态：`exit=0` 或 `exit=127`。
    pub fn status_line(&self) -> String {
        if self.code < 0 {
            "exit=?".to_string()
        } else {
            format!("exit={}", self.code)
        }
    }

    /// 失败但没有任何输出时，补一句人能看懂的原因。
    pub fn reason(&self) -> String {
        if self.code == 0 {
            return String::new();
        }
        match self.code {
            126 => "没有执行权限".into(),
            127 => "命令不存在（PATH 里找不到）".into(),
            137 => "被 SIGKILL 杀掉（多半是内存不足）".into(),
            143 => "被 SIGTERM 结束".into(),
            -1 if self.out.trim().is_empty() => "没能启动".into(),
            _ => String::new(),
        }
    }
}

/// 沙箱通道：当前身份起 `sh -c`。
pub async fn run(cmd: &str, cwd: Option<&Path>, timeout: Duration, input: Option<&str>) -> ShellResult {
    run_full(&["sh", "-c", cmd], cwd, timeout, input, DEFAULT_CAP, &[]).await
}

/// 带输出上限的 `sh -c`。
pub async fn run_capped(cmd: &str, timeout: Duration, cap: usize) -> ShellResult {
    run_full(&["sh", "-c", cmd], None, timeout, None, cap, &[]).await
}

/// 按 argv 起进程，**不经过 shell**，参数里有引号也不会被解释。
pub async fn run_argv(argv: &[&str], cwd: Option<&Path>, timeout: Duration, input: Option<&str>) -> ShellResult {
    run_full(argv, cwd, timeout, input, DEFAULT_CAP, &[]).await
}

/// 全参数版本。[run] / [run_argv] / [run_capped] 都是它的薄封装。
///
/// - `cap`：stdout 各留多少字节（stderr 用同一上限）；
/// - `env`：额外环境变量，会覆盖继承来的同名项。
pub async fn run_full(
    argv: &[&str],
    cwd: Option<&Path>,
    timeout: Duration,
    input: Option<&str>,
    cap: usize,
    env: &[(&str, &str)],
) -> ShellResult {
    if argv.is_empty() || argv[0].trim().is_empty() {
        return ShellResult::fail("命令为空");
    }
    let started = std::time::Instant::now();

    let mut c = Command::new(argv[0]);
    c.args(&argv[1..])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    if let Some(d) = cwd {
        if d.is_dir() {
            c.current_dir(d);
        }
    }
    for (k, v) in env {
        c.env(k, v);
    }

    let mut child = match c.spawn() {
        Ok(ch) => ch,
        Err(e) => {
            // 这里区分"命令不存在"和"权限不够"，模型据此决定是换命令还是提权。
            let hint = match e.kind() {
                std::io::ErrorKind::NotFound => "命令不存在",
                std::io::ErrorKind::PermissionDenied => "没有执行权限",
                _ => "启动失败",
            };
            return ShellResult::fail(format!("[{hint}] {}: {e}", argv[0]));
        }
    };

    // stdin 一定要处理：不写内容也要关掉，否则 `cat`、`python -` 会一直等。
    if let Some(mut si) = child.stdin.take() {
        if let Some(text) = input {
            let _ = si.write_all(text.as_bytes()).await;
        }
        let _ = si.shutdown().await;
    }

    let cap = cap.clamp(4096, 64 * 1024 * 1024);
    let out_pipe = child.stdout.take();
    let err_pipe = child.stderr.take();

    let work = async {
        let (o, ot) = match out_pipe {
            Some(p) => pump(p, cap).await,
            None => (Vec::new(), false),
        };
        let (e, et) = match err_pipe {
            Some(p) => pump(p, cap).await,
            None => (Vec::new(), false),
        };
        let status = child.wait().await;
        (o, ot, e, et, status)
    };

    match tokio::time::timeout(timeout, work).await {
        Ok((o, ot, e, et, status)) => {
            let mut text = String::from_utf8_lossy(&o).to_string();
            let err_text = String::from_utf8_lossy(&e);
            if !err_text.is_empty() {
                if !text.is_empty() && !text.ends_with('\n') {
                    text.push('\n');
                }
                text.push_str(&err_text);
            }
            let code = match status {
                Ok(s) => s.code().unwrap_or(-1),
                Err(e) => {
                    return ShellResult::fail(format!("[等待失败] {e}"));
                }
            };
            ShellResult {
                code,
                out: text.trim_end().to_string(),
                truncated: ot || et,
                dur_ms: started.elapsed().as_millis() as i64,
            }
        }
        Err(_) => ShellResult {
            code: -1,
            out: format!("[超时 {}s，已强制结束]", fmt_secs(timeout)),
            truncated: false,
            dur_ms: started.elapsed().as_millis() as i64,
        },
    }
}

/// 读一个管道直到 EOF，最多保存 `cap` 字节。
///
/// 超过上限后仍然继续读——只是不再保存。**不能停读**：管道缓冲区满了之后
/// 子进程会阻塞在 write 上，永远不退出，看起来就像"命令卡死"。
async fn pump<R: AsyncRead + Unpin>(mut r: R, cap: usize) -> (Vec<u8>, bool) {
    let mut out: Vec<u8> = Vec::new();
    let mut buf = vec![0u8; 16 * 1024];
    let mut truncated = false;
    loop {
        match r.read(&mut buf).await {
            Ok(0) => break,
            Ok(n) => {
                if out.len() < cap {
                    let room = cap - out.len();
                    let take = n.min(room);
                    out.extend_from_slice(&buf[..take]);
                    if take < n {
                        truncated = true;
                    }
                } else {
                    truncated = true;
                }
            }
            Err(_) => break,
        }
    }
    (out, truncated)
}

fn fmt_secs(d: Duration) -> String {
    let s = d.as_secs();
    if s < 60 {
        format!("{s}s")
    } else if s < 3600 {
        format!("{}m{}s", s / 60, s % 60)
    } else {
        format!("{}h{}m", s / 3600, (s % 3600) / 60)
    }
}

/// 当前身份（`id -un`）。
pub async fn whoami() -> String {
    let r = run("id -un 2>/dev/null || whoami", None, Duration::from_secs(5), None).await;
    let s = r.out.trim().to_string();
    if s.is_empty() {
        crate::core::base::sys::user()
    } else {
        s
    }
}

/// PATH 里有没有这个命令。
///
/// 同步查 PATH 比起一个 `command -v` 快两个数量级，这里不再起进程。
pub fn has(bin: &str) -> bool {
    let b = bin.trim();
    if b.is_empty() {
        return false;
    }
    if b.contains('/') {
        return crate::core::base::sys::is_executable(b);
    }
    crate::core::base::sys::which(b).is_some()
}

/// 找到命令的绝对路径。
pub fn locate(bin: &str) -> Option<PathBuf> {
    crate::core::base::sys::which(bin)
}

/// 给 shell 用的单引号转义：`a'b` → `'a'\''b'`。
///
/// 所有把外部字符串拼进命令的地方都必须先过这里。宁可多一层引号，
/// 也不要让文件名里的 `;` 变成命令分隔符。
pub fn quote(s: &str) -> String {
    if s.is_empty() {
        return "''".into();
    }
    // 只含安全字符时不加引号，命令更好读、日志更短。
    if s.chars().all(|c| c.is_ascii_alphanumeric() || "-_./:@%+=,".contains(c)) {
        return s.to_string();
    }
    format!("'{}'", s.replace('\'', "'\\''"))
}

/// 把 argv 拼成一条可读命令（仅用于展示与日志，**不要**拿去执行）。
pub fn display(argv: &[&str]) -> String {
    argv.iter().map(|a| quote(a)).collect::<Vec<_>>().join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn quote_escapes_single_quote() {
        assert_eq!(quote("a'b"), "'a'\\''b'");
        assert_eq!(quote("plain/path.txt"), "plain/path.txt");
        assert_eq!(quote("a b"), "'a b'");
        assert_eq!(quote(""), "''");
    }

    #[test]
    fn reason_codes() {
        assert_eq!(ShellResult { code: 127, ..Default::default() }.reason(), "命令不存在（PATH 里找不到）");
        assert!(ShellResult { code: 0, ..Default::default() }.reason().is_empty());
    }
}
