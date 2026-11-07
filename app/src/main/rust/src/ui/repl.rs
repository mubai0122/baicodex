//! 行模式：不依赖终端库，管道/远程/调试都用得着。
//!
//! ```
//! baicodex --repl --base ~/.baicode
//! > 读一下 src 目录，告诉我入口在哪
//! ```
//!
//! 事件由另一个任务打印，所以输入时也能看到工具在跑。

use crate::app::Facade;
use crate::core::base::error::BResult;
use crate::core::feature::app::AppEvent;

/// 启动行模式。
pub async fn run(base: &str) -> BResult<()> {
    let facade = Facade::boot(base)?;
    let mut rx = facade.subscribe();

    // 事件打印：流式增量紧跟上一行输出，不换行。
    tokio::spawn(async move {
        while let Ok(e) = rx.recv().await {
            match e {
                AppEvent::Delta { content, reason } => {
                    if !content.is_empty() {
                        print!("{content}");
                        use std::io::Write;
                        let _ = std::io::stdout().flush();
                    }
                    if !reason.is_empty() {
                        // 思考过程用暗色，但要先结束当前行。
                        println!("\n\x1b[2m…{reason}\x1b[0m");
                    }
                }
                AppEvent::Message { message } => {
                    if message.role == crate::core::feature::model::message::Role::Tool {
                        let tag = if message.is_error { "失败" } else { "完成" };
                        let name = message.tool_name.clone().unwrap_or_else(|| "工具".into());
                        println!("\n\x1b[2m[{}] {}：{}\x1b[0m", tag, name, first_line(&message.content, 120));
                    }
                }
                AppEvent::ToolStart { name, args, .. } => {
                    println!("\n\x1b[36m→ {}({})\x1b[0m", name, first_line(&args, 80));
                }
                AppEvent::Notice { text } => println!("\x1b[33m{}\x1b[0m", text),
                AppEvent::Status { text } => println!("\x1b[2m{}\x1b[0m", text),
                AppEvent::Error { code, message } => println!("\x1b[31m错误（{code}）：{message}\x1b[0m"),
                AppEvent::ConfirmRequest => println!("\x1b[33m需要确认：输入 y 允许，n 拒绝\x1b[0m"),
                AppEvent::AskRequest => println!("\x1b[33m需要回答：直接输入内容\x1b[0m"),
                AppEvent::PlanRequest => println!("\x1b[33m计划待确认：输入 y 执行，其它内容当作修改意见\x1b[0m"),
                AppEvent::Usage { prompt, completion } => {
                    println!("\x1b[2m[用量] {prompt} 入 / {completion} 出\x1b[0m");
                }
                AppEvent::Quit => break,
                _ => {}
            }
        }
    });

    println!("\x1b[1mbaicodex {}\x1b[0m　输入开始聊，:q 退出，:stop 停止，:new 新会话", crate::VERSION);

    let mut lines = read_lines();
    loop {
        print!("\x1b[1m> \x1b[0m");
        use std::io::Write;
        let _ = std::io::stdout().flush();
        let Some(line) = lines.recv().await else { break };
        let t = line.trim();
        if t.is_empty() {
            continue;
        }
        if t == ":q" || t == ":quit" || t == "exit" {
            break;
        }
        if t == ":stop" {
            facade.stop();
            continue;
        }
        if t == ":new" {
            let _ = facade.new_session();
            println!("已开新会话");
            continue;
        }
        if t == ":help" {
            println!(":q 退出　:stop 停止　:new 新会话　:tools 列工具　:usage 用量");
            continue;
        }
        if t == ":tools" {
            println!("{}", serde_json::to_string_pretty(&facade.tools()).unwrap_or_default());
            continue;
        }
        if t == ":usage" {
            println!("{}", serde_json::to_string_pretty(&facade.snapshot()).unwrap_or_default());
            continue;
        }
        // 确认 / 回答：上一轮留了挂起请求就直接回填。
        let st = facade.snapshot();
        if st["waiting"] == true {
            if st["status"] == "计划待确认" || st.get("plan").is_some() {
                if t == "y" {
                    facade.app.answer_plan(crate::core::feature::app::PlanVerdict::Confirm);
                } else {
                    facade
                        .app
                        .answer_plan(crate::core::feature::app::PlanVerdict::Revise { note: t.to_string() });
                }
                continue;
            }
            if t == "y" || t == "n" {
                facade.app.resolve_tool(t == "y", false);
                continue;
            }
            facade.app.answer_ask(vec![t.to_string()]);
            continue;
        }

        println!();
        match facade.send(t, Vec::new(), Vec::new()).await {
            Ok(v) => {
                let text = v["text"].as_str().unwrap_or("");
                if !text.trim().is_empty() {
                    println!();
                    println!("{}", crate::ui::format::strip_markdown(text));
                }
            }
            Err(e) => println!("\x1b[31m失败：{}\x1b[0m", e.user_text()),
        }
        println!();
    }

    facade.shutdown();
    Ok(())
}

fn first_line(s: &str, n: usize) -> String {
    crate::core::base::text::truncate(&crate::core::base::text::one_line(s), n)
}

/// 用线程读 stdin，转成通道：tokio 的 stdin 在管道关闭时行为不一致，自己读更稳。
fn read_lines() -> tokio::sync::mpsc::UnboundedReceiver<String> {
    let (tx, rx) = tokio::sync::mpsc::unbounded_channel();
    std::thread::Builder::new()
        .name("bai-stdin".into())
        .spawn(move || {
            use std::io::BufRead;
            let stdin = std::io::stdin();
            for line in stdin.lock().lines() {
                match line {
                    Ok(l) => {
                        if tx.send(l).is_err() {
                            break;
                        }
                    }
                    Err(_) => break,
                }
            }
        })
        .ok();
    rx
}
