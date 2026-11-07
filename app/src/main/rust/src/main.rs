//! 可执行入口：只是把命令行参数翻译成"起哪个前端"。
//!
//! 这里刻意不写任何业务逻辑。`--help` 的每一行都对应 [`crate::app::Facade`] 的一个
//! 能力，Android 与本程序共用同一套内核，所以这里能做的所有事，Kotlin 侧也都能做。

use std::process::ExitCode;

use baicodex::core::base::dirs;

/// 解析结果。参数少到不值得引入 clap。
#[derive(Debug, Default)]
struct Args {
    base: Option<String>,
    repl: bool,
    once: Option<String>,
    session: Option<String>,
    serve: bool,
    port: u16,
    help: bool,
    version: bool,
}

fn main() -> ExitCode {
    let args = match parse(std::env::args().skip(1)) {
        Ok(a) => a,
        Err(msg) => {
            eprintln!("参数错误：{msg}");
            eprintln!("试试 `baicodex --help`");
            return ExitCode::from(2);
        }
    };

    if args.help {
        print!("{}", help_text());
        return ExitCode::SUCCESS;
    }
    if args.version {
        let info = baicodex::build_info();
        println!("{} {} ({}/{})", info.name, info.version, info.os, info.arch);
        return ExitCode::SUCCESS;
    }

    let base = args
        .base
        .clone()
        .unwrap_or_else(|| dirs::default_base().to_string_lossy().to_string());

    let rt = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
        Ok(rt) => rt,
        Err(e) => {
            eprintln!("无法启动运行时：{e}");
            return ExitCode::FAILURE;
        }
    };

    let code = rt.block_on(async move { dispatch(args, base).await });

    // 显式关闭运行时：否则 detached 的事件线程可能让进程挂住不退。
    rt.shutdown_timeout(std::time::Duration::from_millis(300));
    code
}

async fn dispatch(args: Args, base: String) -> ExitCode {
    // `--serve` 是给 Web 前端用的调试口子：HTTP + SSE，见 `serve` 模块
    if args.serve {
        let port = if args.port == 0 { 8123 } else { args.port };
        return match baicodex::serve::run(&base, port).await {
            Ok(()) => ExitCode::SUCCESS,
            Err(e) => {
                eprintln!("{e}");
                ExitCode::FAILURE
            }
        };
    }
    // `--once` 是一次性提问，跑完就退：脚本、CI、冒烟测试用得上。
    if let Some(prompt) = args.once {
        return match once(&base, args.session.as_deref(), &prompt).await {
            Ok(text) => {
                println!("{text}");
                ExitCode::SUCCESS
            }
            Err(e) => {
                eprintln!("{e}");
                ExitCode::FAILURE
            }
        };
    }

    if args.repl {
        return match baicodex::ui::repl::run(&base).await {
            Ok(()) => ExitCode::SUCCESS,
            Err(e) => {
                eprintln!("{e}");
                ExitCode::FAILURE
            }
        };
    }

    #[cfg(feature = "tui")]
    {
        if std::io::IsTerminal::is_terminal(&std::io::stdout()) {
            return match baicodex::ui::tui::run(&base).await {
                Ok(()) => ExitCode::SUCCESS,
                Err(e) => {
                    eprintln!("{e}");
                    ExitCode::FAILURE
                }
            };
        }
        // 管道输入没有 TTY，TUI 起不来，自动降级到行模式。
        eprintln!("没有检测到终端，改用行模式（:q 退出）");
    }
    #[cfg(not(feature = "tui"))]
    eprintln!("本次构建不含 TUI，改用行模式");

    match baicodex::ui::repl::run(&base).await {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("{e}");
            ExitCode::FAILURE
        }
    }
}

async fn once(base: &str, session: Option<&str>, prompt: &str) -> Result<String, String> {
    use baicodex::core::base::error::BError;

    let facade = baicodex::app::Facade::boot(base).map_err(|e: BError| e.user_text())?;
    if let Some(id) = session {
        facade.open_session(id).map_err(|e: BError| e.user_text())?;
    }
    let out = facade
        .send(prompt, Vec::new(), Vec::new())
        .await
        .map_err(|e: BError| e.user_text())?;
    facade.shutdown();
    // `send` 返回的是给前端用的结构化结果，文本在 `text` 字段。
    Ok(out["text"].as_str().unwrap_or_default().to_string())
}

fn parse<I: Iterator<Item = String>>(mut it: I) -> Result<Args, String> {
    let mut a = Args::default();
    while let Some(arg) = it.next() {
        match arg.as_str() {
            "-h" | "--help" => a.help = true,
            "-V" | "--version" => a.version = true,
            "--repl" | "--no-tui" => a.repl = true,
            "--serve" => a.serve = true,
            "--port" => {
                let v = it.next().ok_or_else(|| "--port 需要一个端口号".to_string())?;
                a.port = v.parse().map_err(|_| format!("端口不是数字：{v}"))?;
            }
            "-b" | "--base" => {
                a.base = Some(it.next().ok_or_else(|| "--base 需要一个路径".to_string())?)
            }
            "-s" | "--session" => {
                a.session = Some(it.next().ok_or_else(|| "--session 需要一个会话 id".to_string())?)
            }
            "-p" | "--print" | "--once" => {
                a.once = Some(it.next().ok_or_else(|| "--print 需要一段话".to_string())?)
            }
            other if other.starts_with("--base=") => {
                a.base = Some(other["--base=".len()..].to_string())
            }
            other if other.starts_with("--session=") => {
                a.session = Some(other["--session=".len()..].to_string())
            }
            other if other.starts_with("--print=") => {
                a.once = Some(other["--print=".len()..].to_string())
            }
            other => return Err(format!("看不懂的参数：{other}")),
        }
    }
    Ok(a)
}

fn help_text() -> String {
    format!(
        "\
baicodex {ver}　本地 AI Agent 内核

用法：
  baicodex                    打开终端界面
  baicodex --repl             行模式（无终端库依赖）
  baicodex --serve [--port N] 起 HTTP+SSE 调试服务，给 Web 前端连真内核
  baicodex -p \"问题\"          问一次，打印回答后退出
  baicodex -s <会话id> -p …   在指定会话里问一次

选项：
  -b, --base <路径>           数据目录，默认 {base}
  -s, --session <id>          指定会话
  -p, --print <文本>          一次性提问
      --repl, --no-tui        强制行模式
  -h, --help                  显示本页
  -V, --version               版本

数据目录里的东西：
  config.json                 设置（含 provider、模式、外观）
  db.sqlite3                  会话与消息（树形，支持重新生成）
  vault.bin                   凭据（AES-256-GCM，三层密钥）
  skill/ skills/ jniskill/    技能（正文型 / 包型）
  workspace.json              工作区绑定
",
        ver = baicodex::VERSION,
        base = dirs::default_base().display()
    )
}