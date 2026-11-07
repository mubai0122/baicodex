//! 桌面端入口。
//!
//! 真活儿都在 [`baicodex_host_win::app::run`] 里；这里只是个不 panic 的外壳：
//! 桌面程序崩了，用户连个控制台都看不到，只能对着"窗口闪一下没了"发呆。
//! 所以任何 `Result` 错误都要变成一句人话再退出。

// 发布版不要控制台窗口；调试版留着，方便看 panic 和日志。
#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

fn main() {
    #[cfg(windows)]
    {
        // panic 也要留个痕：装一个钩子把栈打到临时文件里，
        // 用户反馈"打不开"时至少有东西可查。
        install_panic_hook();
        std::process::exit(baicodex_host_win::app::run());
    }

    #[cfg(not(windows))]
    {
        eprintln!("{}", baicodex_host_win::unsupported());
        std::process::exit(1);
    }
}

#[cfg(windows)]
fn install_panic_hook() {
    std::panic::set_hook(Box::new(|info| {
        use std::io::Write;
        let text = format!(
            "time: {}\npanic: {info}\n\nbacktrace:\n{}\n",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0),
            std::backtrace::Backtrace::force_capture()
        );
        if let Ok(mut f) = std::fs::OpenOptions::new().create(true).append(true).open(crash_log_path()) {
            let _ = f.write_all(text.as_bytes());
        }
        // 控制台版还能看见
        eprintln!("{text}");
    }));
}

#[cfg(windows)]
fn crash_log_path() -> std::path::PathBuf {
    let base = std::env::var("LOCALAPPDATA")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|_| std::env::temp_dir());
    let dir = base.join("baicodex");
    let _ = std::fs::create_dir_all(&dir);
    dir.join("crash.log")
}
