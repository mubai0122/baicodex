/*
 * Baicodex Windows (MVP)
 * License: AGPL-3.0
 * 纯 Rust GUI 骨架：会话列表 + 聊天区 + 输入区 + 模型选择 + 设置面板
 */

mod theme;

use eframe::egui::{
    self, Align, CentralPanel, Context, Frame, Layout, Margin, RichText, ScrollArea,
    SidePanel, Stroke, TextEdit, TextStyle, TopBottomPanel, Ui, Vec2,
};
use std::collections::HashMap;
use theme::{Palette, ThemeMode};

fn main() -> eframe::Result<()> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_title("Baicodex")
            .with_inner_size([1180.0, 760.0])
            .with_min_inner_size([860.0, 560.0]),
        ..Default::default()
    };
    eframe::run_native(
        "Baicodex",
        options,
        Box::new(|cc| {
            // 字体：让中文正常显示（增大基础字号）
            let mut style = (*cc.egui_ctx.style()).clone();
            style.text_styles.insert(
                TextStyle::Body,
                egui::FontId::proportional(14.0),
            );
            cc.egui_ctx.set_style(style);
            Ok(Box::new(App::new(cc)))
        }),
    )
}

// ---------- 数据模型（MVP 假数据） ----------

#[derive(Clone)]
struct Conversation {
    title: String,
    preview: String,
    time: String,
}

#[derive(Clone, PartialEq)]
enum Role {
    User,
    Assistant,
}

#[derive(Clone)]
struct Message {
    role: Role,
    text: String,
}

struct App {
    // 主题
    theme_mode: ThemeMode,
    system_dark: bool,
    // 会话
    conversations: Vec<Conversation>,
    selected: usize,
    // 消息：按会话 id 存
    messages: HashMap<usize, Vec<Message>>,
    // 输入
    draft: String,
    // 模型
    models: Vec<String>,
    model_idx: usize,
    // 面板
    show_settings: bool,
    show_model_menu: bool,
}

impl App {
    fn new(cc: &eframe::CreationContext<'_>) -> Self {
        let system_dark = cc
            .egui_ctx
            .system_theme()
            .map(|t| matches!(t, egui::Theme::Dark))
            .unwrap_or(false);

        let conversations = vec![
            Conversation {
                title: "帮我写个 Rust 爬虫".into(),
                preview: "好，先看一下目标页面的结构…".into(),
                time: "刚刚".into(),
            },
            Conversation {
                title: "解释一下所有权和借用".into(),
                preview: "所有权是 Rust 内存安全的核心…".into(),
                time: "10:32".into(),
            },
            Conversation {
                title: "周末去哪玩".into(),
                preview: "深圳周边的话，推荐这几个地方…".into(),
                time: "昨天".into(),
            },
            Conversation {
                title: "SQL 注入怎么防".into(),
                preview: "用参数化查询，永远不要拼接…".into(),
                time: "周二".into(),
            },
        ];

        let mut messages = HashMap::new();
        messages.insert(
            0,
            vec![
                Message {
                    role: Role::User,
                    text: "帮我写个 Rust 爬虫，抓取网页标题".into(),
                },
                Message {
                    role: Role::Assistant,
                    text: "可以。最简单的方案是用 reqwest 拉取页面，再用 scraper 解析 HTML 里的 <title> 标签。\n\n我先给你一个最小可运行的例子，然后你可以按需要扩展并发和重试。".into(),
                },
                Message {
                    role: Role::User,
                    text: "要能处理 gzip 压缩".into(),
                },
                Message {
                    role: Role::Assistant,
                    text: "reqwest 默认就带 gzip 特性，只要打开 `gzip` feature 即可，不需要手动解压。在 Cargo.toml 里加上：\n\nreqwest = { version = \"0.12\", features = [\"gzip\"] }".into(),
                },
            ],
        );

        Self {
            theme_mode: ThemeMode::System,
            system_dark,
            conversations,
            selected: 0,
            messages,
            draft: String::new(),
            models: vec![
                "deepseek-chat".into(),
                "gpt-4o".into(),
                "claude-sonnet".into(),
                "本地模型".into(),
            ],
            model_idx: 0,
            show_settings: false,
            show_model_menu: false,
        }
    }

    fn dark(&self) -> bool {
        self.theme_mode.is_dark(self.system_dark)
    }

    fn send(&mut self) {
        let text = self.draft.trim().to_string();
        if text.is_empty() {
            return;
        }
        let list = self.messages.entry(self.selected).or_default();
        list.push(Message {
            role: Role::User,
            text: text.clone(),
        });
        // MVP：模拟一个回复
        list.push(Message {
            role: Role::Assistant,
            text: "（MVP 占位回复）这是 Windows 版本的界面骨架，模型调用还没接入。".into(),
        });
        self.draft.clear();
        if let Some(c) = self.conversations.get_mut(self.selected) {
            c.preview = text;
            c.time = "刚刚".into();
        }
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &Context, _frame: &mut eframe::Frame) {
        // 检测系统主题变化
        if self.theme_mode == ThemeMode::System {
            if let Some(t) = ctx.system_theme() {
                self.system_dark = matches!(t, egui::Theme::Dark);
            }
        }
        theme::apply(ctx, self.dark());
        let p = Palette::get(self.dark());

        // 顶栏
        TopBottomPanel::top("top").show(ctx, |ui| self.top_bar(ui, &p));

        // 主体：左侧会话 + 右侧聊天
        let mut settings_open = self.show_settings;
        CentralPanel::default()
            .frame(Frame::new().fill(p.bg))
            .show(ctx, |ui| {
                SidePanel::left("sessions")
                    .resizable(true)
                    .default_width(260.0)
                    .min_width(200.0)
                    .frame(Frame::new().fill(p.panel).inner_margin(Margin::same(8)))
                    .show_inside(ui, |ui| self.session_list(ui, &p));

                CentralPanel::default()
                    .frame(Frame::new().fill(p.bg))
                    .show_inside(ui, |ui| self.chat_area(ui, &p));
            });

        // 设置面板（模态）
        if settings_open {
            self.settings_window(ctx, &p);
        }
    }
}

impl App {
    fn top_bar(&mut self, ui: &mut Ui, p: &Palette) {
        ui.horizontal(|ui| {
            ui.add_space(12.0);
            ui.label(
                RichText::new("◈ Baicodex")
                    .size(17.0)
                    .strong()
                    .color(p.text),
            );
            ui.label(RichText::new("Windows MVP").size(11.0).color(p.text_dim));

            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                // 设置按钮
                if ui
                    .button(RichText::new("⚙").size(15.0))
                    .on_hover_text("设置")
                    .clicked()
                {
                    self.show_settings = true;
                }

                // 主题快捷切换（三个小按钮）
                let mode = self.theme_mode;
                for (m, icon) in [
                    (ThemeMode::System, "◐"),
                    (ThemeMode::Light, "☀"),
                    (ThemeMode::Dark, "☾"),
                ] {
                    let selected = m == mode;
                    let btn = ui.button(RichText::new(icon).size(13.0));
                    if selected {
                        // 选中态用强调色描边提示
                        let r = btn.rect;
                        ui.painter().rect_stroke(
                            r.expand(1.0),
                            egui::CornerRadius::same(5),
                            Stroke::new(1.5, p.accent),
                            egui::StrokeKind::Outside,
                        );
                    }
                    if btn.clicked() {
                        self.theme_mode = m;
                    }
                }

                ui.add_space(8.0);

                // 模型选择
                let model_label = format!("{} ▾", self.models[self.model_idx]);
                if ui.button(model_label).clicked() {
                    self.show_model_menu = !self.show_model_menu;
                }
                if self.show_model_menu {
                    let mut close = false;
                    egui::Window::new("model_menu")
                        .collapsible(false)
                        .resizable(false)
                        .title_bar(false)
                        .anchor(egui::Align2::RIGHT_TOP, Vec2::new(0.0, 4.0))
                        .show(ui.ctx(), |ui| {
                            for (i, m) in self.models.iter().enumerate() {
                                let text = if i == self.model_idx {
                                    RichText::new(format!("✓  {}", m)).color(p.accent)
                                } else {
                                    RichText::new(format!("    {}", m)).color(p.text)
                                };
                                if ui.button(text).clicked() {
                                    self.model_idx = i;
                                    close = true;
                                }
                            }
                        });
                    if close {
                        self.show_model_menu = false;
                    }
                }
            });
        });
    }

    fn session_list(&mut self, ui: &mut Ui, p: &Palette) {
        ui.horizontal(|ui| {
            ui.label(RichText::new("会话").size(13.0).strong().color(p.text_dim));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if ui.button(RichText::new("＋").size(16.0)).on_hover_text("新会话").clicked() {
                    self.conversations.insert(
                        0,
                        Conversation {
                            title: "新会话".into(),
                            preview: "开始对话".into(),
                            time: "刚刚".into(),
                        },
                    );
                    self.selected = 0;
                }
            });
        });
        ui.separator();

        ScrollArea::vertical().show(ui, |ui| {
            for (i, c) in self.conversations.iter().enumerate() {
                let selected = i == self.selected;
                let fill = if selected { p.panel_alt } else { p.panel };
                let (rect, resp) = ui.allocate_exact_size(
                    Vec2::new(ui.available_width(), 58.0),
                    egui::Sense::click(),
                );
                let painter = ui.painter_at(rect);
                painter.rect_filled(rect, egui::CornerRadius::same(8), fill);
                if selected {
                    painter.rect_stroke(
                        rect,
                        egui::CornerRadius::same(8),
                        Stroke::new(1.0_f32, p.accent),
                        egui::StrokeKind::Inside,
                    );
                }
                ui.allocate_ui_at_rect(rect, |ui| {
                    ui.set_min_width(rect.width());
                    ui.vertical(|ui| {
                        ui.add_space(6.0);
                        ui.horizontal(|ui| {
                            ui.add_space(10.0);
                            ui.label(RichText::new(&c.title).size(13.0).strong().color(p.text));
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                ui.label(RichText::new(&c.time).size(10.0).color(p.text_dim));
                            });
                        });
                        ui.horizontal(|ui| {
                            ui.add_space(10.0);
                            let preview: String = c.preview.chars().take(16).collect();
                            ui.label(RichText::new(preview).size(11.0).color(p.text_dim));
                        });
                    });
                });
                if resp.clicked() {
                    self.selected = i;
                }
                ui.add_space(3.0);
            }
        });
    }

    fn chat_area(&mut self, ui: &mut Ui, p: &Palette) {
        let title = self
            .conversations
            .get(self.selected)
            .map(|c| c.title.clone())
            .unwrap_or_default();

        // 聊天标题
        ui.horizontal(|ui| {
            ui.add_space(16.0);
            ui.label(RichText::new(&title).size(15.0).strong().color(p.text));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                ui.label(
                    RichText::new(format!("模型：{}", self.models[self.model_idx]))
                        .size(11.0)
                        .color(p.text_dim),
                );
            });
        });
        ui.separator();

        // 消息列表
        ScrollArea::vertical()
            .stick_to_bottom(true)
            .show(ui, |ui| {
                ui.add_space(8.0);
                if let Some(msgs) = self.messages.get(&self.selected) {
                    for m in msgs {
                        self.message_bubble(ui, m, p);
                    }
                }
                ui.add_space(8.0);
            });

        ui.separator();

        // 输入区
        ui.horizontal(|ui| {
            ui.add_space(12.0);
            let response = ui.add(
                TextEdit::multiline(&mut self.draft)
                    .desired_rows(2)
                    .desired_width(ui.available_width() - 100.0)
                    .hint_text("输入消息，Enter 发送，Shift+Enter 换行")
                    .frame(true),
            );
            if response.has_focus()
                && ui.input(|i| i.key_pressed(egui::Key::Enter) && !i.modifiers.shift)
            {
                self.send();
            }
            ui.add_space(8.0);
            if ui
                .button(RichText::new("发送").strong())
                .on_hover_text("发送 (Enter)")
                .clicked()
            {
                self.send();
            }
            ui.add_space(12.0);
        });
        ui.add_space(8.0);
    }

    fn message_bubble(&self, ui: &mut Ui, m: &Message, p: &Palette) {
        let is_user = m.role == Role::User;
        let max_w = ui.available_width() * 0.72;
        ui.horizontal(|ui| {
            if !is_user {
                ui.add_space(6.0);
                ui.label(RichText::new("◈").size(14.0).color(p.accent));
            }
            if is_user {
                ui.with_layout(Layout::right_to_left(Align::TOP), |ui| {
                    ui.add_space(12.0);
                    egui::Frame::new()
                        .fill(p.bubble_user)
                        .corner_radius(egui::CornerRadius::same(10))
                        .inner_margin(Margin {
                            left: 12,
                            right: 12,
                            top: 8,
                            bottom: 8,
                        })
                        .show(ui, |ui| {
                            ui.set_max_width(max_w);
                            ui.add(egui::Label::new(RichText::new(&m.text).color(p.text)).wrap());
                        });
                });
            } else {
                egui::Frame::new()
                    .fill(p.bubble_ai)
                    .stroke(Stroke::new(1.0, p.border))
                    .corner_radius(egui::CornerRadius::same(10))
                    .inner_margin(Margin {
                        left: 12,
                        right: 12,
                        top: 8,
                        bottom: 8,
                    })
                    .show(ui, |ui| {
                        ui.set_max_width(max_w);
                        ui.add(egui::Label::new(RichText::new(&m.text).color(p.text)).wrap());
                    });
            }
        });
        ui.add_space(10.0);
    }

    fn settings_window(&mut self, ctx: &Context, p: &Palette) {
        let mut open = self.show_settings;
        egui::Window::new("设置")
            .default_width(440.0)
            .default_height(360.0)
            .collapsible(false)
            .resizable(false)
            .open(&mut open)
            .show(ctx, |ui| {
                ui.add_space(8.0);
                ui.label(RichText::new("外观").size(14.0).strong().color(p.text));
                ui.separator();
                ui.horizontal(|ui| {
                    ui.label(RichText::new("主题").color(p.text));
                    ui.add_space(20.0);
                    for m in [ThemeMode::System, ThemeMode::Light, ThemeMode::Dark] {
                        let selected = m == self.theme_mode;
                        let txt = if selected {
                            RichText::new(format!("● {}", m.label())).color(p.accent)
                        } else {
                            RichText::new(m.label()).color(p.text_dim)
                        };
                        if ui.button(txt).clicked() {
                            self.theme_mode = m;
                        }
                    }
                });
                ui.add_space(6.0);
                ui.label(
                    RichText::new("浅色为白色 + 淡灰；选择「跟随系统」会随系统亮暗自动切换。")
                        .size(11.0)
                        .color(p.text_dim),
                );

                ui.add_space(20.0);
                ui.label(RichText::new("关于").size(14.0).strong().color(p.text));
                ui.separator();
                ui.label(RichText::new("Baicodex Windows MVP v0.1.0").color(p.text));
                ui.label(
                    RichText::new("纯 Rust 界面骨架 · AGPL-3.0")
                        .size(11.0)
                        .color(p.text_dim),
                );
            });
        self.show_settings = open;
    }
}
