//! 终端界面（ratatui）。
//!
//! 布局三块：消息区（可滚动）、输入区、状态栏。
//!
//! ## 事件循环
//!
//! 一个 `tokio::select!` 同时等三件事：按键、内核事件、100ms 心跳。
//! 心跳是必要的：流式增量到达后要重绘，但如果只在事件到达时重绘，滚动与光标
//! 就会看起来"卡一下"。固定节奏重绘（哪怕内容没变）在终端里开销可以忽略。
//!
//! ## 为什么不用 ratatui 的 async 组件
//!
//! 那些组件要求你按它的生命周期管理状态，而这里的状态由内核事件驱动、按键只改
//! 一小部分。手写 `App` + `render` 反而更直白。

use std::collections::VecDeque;
use std::time::Duration;

use crossterm::event::{Event, KeyCode, KeyEvent, KeyModifiers};
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Borders, Paragraph, Wrap};
use ratatui::Frame;

use crate::app::Facade;
use crate::core::base::error::BResult;
use crate::core::feature::app::AppEvent;
use crate::core::feature::model::message::Role;

/// 渲染缓存。只放"要画的东西"，业务状态还在内核里。
struct Ui {
    lines: VecDeque<Line<'static>>,
    input: String,
    status: String,
    title: String,
    busy: bool,
    /// 挂起状态：`confirm` / `ask` / `plan`，空表示没有。
    waiting: String,
    scroll: u16,
    /// 流式中的正文还没进消息列表，先攒着。
    stream: String,
    quit: bool,
}

impl Ui {
    fn new() -> Self {
        Self {
            lines: VecDeque::new(),
            input: String::new(),
            status: "就绪".into(),
            title: "新会话".into(),
            busy: false,
            waiting: String::new(),
            scroll: 0,
            stream: String::new(),
            quit: false,
        }
    }

    fn push(&mut self, text: impl Into<String>) {
        for l in crate::ui::format::wrap_block(&text.into(), 200) {
            self.lines.push_back(Line::from(l));
        }
        while self.lines.len() > 4000 {
            self.lines.pop_front();
        }
        self.scroll = 0;
    }

    fn push_styled(&mut self, spans: Vec<Span<'static>>) {
        self.lines.push_back(Line::from(spans));
        while self.lines.len() > 4000 {
            self.lines.pop_front();
        }
        self.scroll = 0;
    }

    /// 把流式缓冲落成一条消息行。
    fn flush_stream(&mut self) {
        if self.stream.trim().is_empty() {
            self.stream.clear();
            return;
        }
        let body = crate::ui::format::strip_markdown(&self.stream);
        self.stream.clear();
        self.push_styled(vec![
            Span::styled("助手 ", Style::default().fg(Color::Green).add_modifier(Modifier::BOLD)),
            Span::raw(""),
        ]);
        self.push(body);
        self.push("");
    }
}

/// 启动。
pub async fn run(base: &str) -> BResult<()> {
    let facade = Facade::boot(base)?;
    let mut rx = facade.subscribe();

    let mut terminal = ratatui::init();
    let mut ui = Ui::new();
    ui.push_styled(vec![Span::styled(
        format!("baicodex {}　Enter 发送　Esc 停止　Ctrl+C 退出　F1 帮助", crate::VERSION),
        Style::default().fg(Color::DarkGray),
    )]);
    ui.push("");

    let r = loop {
        terminal.draw(|f| render(f, &mut ui)).ok();
        if ui.quit {
            break Ok(());
        }

        tokio::select! {
            // 按键（crossterm 是阻塞读，放进 spawn_blocking 里带上限地等）
            ev = read_event() => {
                if let Some(key) = ev {
                    if handle_key(&facade, &mut ui, key).await.is_err() {
                        break Ok(());
                    }
                }
            }
            // 内核事件
            e = rx.recv() => {
                match e {
                    Ok(e) => apply(&mut ui, e),
                    Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                        ui.status = format!("事件积压，丢了 {n} 条");
                    }
                    Err(_) => break Ok(()),
                }
            }
            // 心跳：保证流式内容能及时刷新
            _ = tokio::time::sleep(Duration::from_millis(100)) => {}
        }
    };

    ratatui::restore();
    facade.shutdown();
    r
}

async fn read_event() -> Option<KeyEvent> {
    let r = tokio::task::spawn_blocking(|| {
        // 200ms 超时返回 None，避免一直占着线程池。
        if crossterm::event::poll(Duration::from_millis(200)).unwrap_or(false) {
            if let Ok(Event::Key(k)) = crossterm::event::read() {
                return Some(k);
            }
        }
        None
    })
    .await;
    r.ok().flatten()
}

async fn handle_key(facade: &Facade, ui: &mut Ui, key: KeyEvent) -> BResult<()> {
    // 全局键
    match (key.code, key.modifiers) {
        (KeyCode::Char('c'), KeyModifiers::CONTROL) => {
            ui.quit = true;
            return Ok(());
        }
        (KeyCode::Esc, _) => {
            facade.stop();
            ui.status = "已请求停止".into();
            return Ok(());
        }
        (KeyCode::PageUp, _) => {
            ui.scroll = ui.scroll.saturating_add(5);
            return Ok(());
        }
        (KeyCode::PageDown, _) => {
            ui.scroll = ui.scroll.saturating_sub(5);
            return Ok(());
        }
        (KeyCode::F(1), _) => {
            ui.push("快捷键：Enter 发送 / Esc 停止 / Ctrl+C 退出 / PageUp·PageDown 滚动 / Ctrl+N 新会话 / Ctrl+L 清屏");
            return Ok(());
        }
        (KeyCode::Char('n'), KeyModifiers::CONTROL) => {
            facade.new_session()?;
            ui.lines.clear();
            ui.title = "新会话".into();
            ui.status = "已开新会话".into();
            return Ok(());
        }
        (KeyCode::Char('l'), KeyModifiers::CONTROL) => {
            ui.lines.clear();
            return Ok(());
        }
        _ => {}
    }

    // 挂起状态：先处理确认与回答
    if !ui.waiting.is_empty() {
        match key.code {
            KeyCode::Char('y') | KeyCode::Enter if ui.waiting != "ask" => {
                if ui.waiting == "plan" {
                    facade.app.answer_plan(crate::core::feature::app::PlanVerdict::Confirm);
                } else {
                    facade.app.resolve_tool(true, false);
                }
                ui.push_styled(vec![Span::styled("已允许", Style::default().fg(Color::Green))]);
                ui.waiting.clear();
                return Ok(());
            }
            KeyCode::Char('n') if ui.waiting != "ask" => {
                if ui.waiting == "plan" {
                    facade.app.answer_plan(crate::core::feature::app::PlanVerdict::Dismiss);
                } else {
                    facade.app.resolve_tool(false, false);
                }
                ui.push_styled(vec![Span::styled("已拒绝", Style::default().fg(Color::Red))]);
                ui.waiting.clear();
                return Ok(());
            }
            _ => {}
        }
    }

    match key.code {
        KeyCode::Enter => {
            let text = std::mem::take(&mut ui.input);
            let t = text.trim().to_string();
            if t.is_empty() {
                return Ok(());
            }
            // 挂起中：当回答/意见处理
            if !ui.waiting.is_empty() {
                if ui.waiting == "plan" {
                    facade.app.answer_plan(crate::core::feature::app::PlanVerdict::Revise { note: t });
                } else {
                    facade.app.answer_ask(vec![t]);
                }
                ui.waiting.clear();
                return Ok(());
            }
            ui.push_styled(vec![Span::styled("你 ", Style::default().fg(Color::Cyan).add_modifier(Modifier::BOLD))]);
            ui.push(t.clone());
            ui.busy = true;
            ui.status = "生成中…".into();
            // 不能在这里 await：一 await 界面就不响应按键了（连 Esc 都按不动）。
            let f = facade.clone();
            tokio::spawn(async move {
                if let Err(e) = f.send(&t, Vec::new(), Vec::new()).await {
                    f.app.emit(AppEvent::Error {
                        code: e.code.as_str().to_string(),
                        message: e.user_text(),
                    });
                }
            });
            Ok(())
        }
        KeyCode::Backspace => {
            ui.input.pop();
            Ok(())
        }
        KeyCode::Char(c) => {
            ui.input.push(c);
            Ok(())
        }
        _ => Ok(()),
    }
}

fn apply(ui: &mut Ui, e: AppEvent) {
    match e {
        AppEvent::Delta { content, .. } => {
            if !content.is_empty() {
                ui.stream.push_str(&content);
                ui.busy = true;
            }
        }
        AppEvent::UserMessage { text, .. } => {
            // 用户刚发的那条：内核落库后才推这条事件，界面照它画，
            // 不要自己"先画一条"，否则重开会话顺序就对不上。
            ui.flush_stream();
            let t = crate::core::base::text::one_line(&text);
            ui.push_styled(vec![Span::styled(
                format!("你 {}", crate::core::base::text::truncate(&t, 400)),
                Style::default().fg(Color::Cyan),
            )]);
            ui.push("");
        }
        AppEvent::Message { message } => {
            if message.role == Role::Assistant {
                ui.flush_stream();
                if !message.content.trim().is_empty() {
                    ui.push_styled(vec![Span::styled(
                        "助手 ",
                        Style::default().fg(Color::Green).add_modifier(Modifier::BOLD),
                    )]);
                    ui.push(crate::ui::format::strip_markdown(&message.content));
                    ui.push("");
                }
            } else if message.role == Role::Tool {
                let ok = !message.is_error;
                let name = message.tool_name.clone().unwrap_or_else(|| "工具".into());
                let head = crate::core::base::text::truncate(&crate::core::base::text::one_line(&message.content), 160);
                ui.push_styled(vec![Span::styled(
                    format!("  {} {name}：{head}", if ok { "✓" } else { "✗" }),
                    Style::default().fg(if ok { Color::DarkGray } else { Color::Red }),
                )]);
            } else if message.role == Role::Summary {
                ui.push_styled(vec![Span::styled(
                    format!("—— 这里压缩了较早的对话（{} 条）——", message.content.chars().count()),
                    Style::default().fg(Color::Yellow),
                )]);
            }
        }
        AppEvent::ToolStart { name, args, .. } => {
            ui.status = format!("调用 {name}…");
            ui.push_styled(vec![Span::styled(
                format!("→ {name}({})", crate::core::base::text::truncate(&crate::core::base::text::one_line(&args), 80)),
                Style::default().fg(Color::Blue),
            )]);
        }
        AppEvent::ToolEnd { .. } => {}
        AppEvent::Status { text } => ui.status = text,
        AppEvent::Notice { text } => ui.push_styled(vec![Span::styled(text, Style::default().fg(Color::Yellow))]),
        AppEvent::Error { code, message } => ui.push_styled(vec![Span::styled(
            format!("错误（{code}）：{message}"),
            Style::default().fg(Color::Red),
        )]),
        AppEvent::Busy { busy } => {
            ui.busy = busy;
            if !busy {
                ui.flush_stream();
                ui.status = "就绪".into();
            }
        }
        AppEvent::Sessions { .. } => {}
        AppEvent::Usage { prompt, completion } => {
            ui.status = format!("就绪　{prompt} 入 / {completion} 出");
        }
        AppEvent::ConfirmRequest => {
            ui.waiting = "confirm".into();
            ui.push_styled(vec![Span::styled(
                "需要确认：y 允许 / n 拒绝",
                Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD),
            )]);
        }
        AppEvent::AskRequest => {
            ui.waiting = "ask".into();
            ui.push_styled(vec![Span::styled(
                "需要回答：直接输入并回车",
                Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD),
            )]);
        }
        AppEvent::PlanRequest => {
            ui.waiting = "plan".into();
            ui.push_styled(vec![Span::styled(
                "计划待确认：y 执行 / 输入修改意见 / n 放弃",
                Style::default().fg(Color::Yellow).add_modifier(Modifier::BOLD),
            )]);
        }
        AppEvent::Quit => ui.quit = true,
    }
}

fn render(f: &mut Frame<'_>, ui: &mut Ui) {
    let area = f.area();
    let chunks = Layout::vertical([
        Constraint::Length(1),
        Constraint::Min(3),
        Constraint::Length(3),
        Constraint::Length(1),
    ])
    .split(area);

    // 顶栏
    let head = format!(
        " baicodex {}　{}　{}",
        crate::VERSION,
        ui.title,
        if ui.busy { "● 生成中" } else { "○ 空闲" }
    );
    f.render_widget(
        Paragraph::new(head).style(Style::default().bg(Color::DarkGray).fg(Color::White)),
        chunks[0],
    );

    // 消息区：流式内容接在最后
    let mut lines: Vec<Line<'static>> = ui.lines.iter().cloned().collect();
    if !ui.stream.trim().is_empty() {
        lines.push(Line::from(""));
        lines.push(Line::from(Span::styled("助手 ", Style::default().fg(Color::Green))));
        for l in crate::ui::format::wrap_block(&crate::ui::format::strip_markdown(&ui.stream), area.width.saturating_sub(4) as usize) {
            lines.push(Line::from(l));
        }
    }
    let h = chunks[1].height as usize;
    let total = lines.len();
    let start = total.saturating_sub(h + ui.scroll as usize);
    let view: Vec<Line<'static>> = lines.into_iter().skip(start).take(h).collect();
    f.render_widget(
        Paragraph::new(view).block(Block::default().borders(Borders::NONE)),
        chunks[1],
    );

    // 输入区
    let title = if ui.waiting.is_empty() { " 输入 " } else { " 回应 " };
    f.render_widget(
        Paragraph::new(ui.input.as_str())
            .style(Style::default().fg(Color::White))
            .block(Block::default().borders(Borders::ALL).title(title))
            .wrap(Wrap { trim: false }),
        chunks[2],
    );

    // 状态栏
    let hint = if ui.waiting.is_empty() {
        "Enter 发送　Esc 停止　Ctrl+N 新会话　Ctrl+C 退出"
    } else {
        "y 允许　n 拒绝　或直接输入内容回车"
    };
    f.render_widget(
        Paragraph::new(format!(" {}　│　{hint}", ui.status))
            .style(Style::default().fg(Color::DarkGray)),
        chunks[3],
    );

    // 光标放到输入末尾
    let inner = chunks[2];
    place_cursor(f, inner, &ui.input);
}

fn place_cursor(f: &mut Frame<'_>, area: Rect, input: &str) {
    if area.width < 2 || area.height < 2 {
        return;
    }
    let width = area.width.saturating_sub(2) as usize;
    let mut row = 0u16;
    let mut col = 0usize;
    for ch in input.chars() {
        let w = crate::core::base::text::Text::width(&ch.to_string());
        if col + w > width {
            row += 1;
            col = 0;
        }
        col += w;
    }
    let x = area.x + 1 + col as u16;
    let y = area.y + 1 + row.min(area.height.saturating_sub(2));
    f.set_cursor_position((x.min(area.x + area.width.saturating_sub(1)), y));
}
