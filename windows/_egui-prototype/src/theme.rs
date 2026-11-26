/*
 * Baicodex Windows - 主题系统
 * 亮色：白色 + 淡灰；暗色：深灰 + 更深的灰。跟随系统，可手动覆盖。
 */

use eframe::egui::{self, Color32, CornerRadius, Stroke, Visuals};

/// 用户选择的主题模式
#[derive(Clone, Copy, PartialEq, Eq, Debug, Default)]
pub enum ThemeMode {
    /// 跟随系统
    #[default]
    System,
    /// 强制亮色
    Light,
    /// 强制暗色
    Dark,
}

impl ThemeMode {
    pub fn label(self) -> &'static str {
        match self {
            ThemeMode::System => "跟随系统",
            ThemeMode::Light => "浅色",
            ThemeMode::Dark => "深色",
        }
    }

    /// 解析当前模式下的实际亮/暗
    pub fn is_dark(self, system_dark: bool) -> bool {
        match self {
            ThemeMode::System => system_dark,
            ThemeMode::Light => false,
            ThemeMode::Dark => true,
        }
    }
}

// ---- 调色板 ----

// 亮色：白色 + 淡灰
const L_BG: Color32 = Color32::from_rgb(0xF7, 0xF8, 0xFA); // 页面底色（极淡灰）
const L_PANEL: Color32 = Color32::from_rgb(0xFF, 0xFF, 0xFF); // 卡片/侧栏（纯白）
const L_PANEL_ALT: Color32 = Color32::from_rgb(0xF1, 0xF3, 0xF6); // 次级面板（淡灰）
const L_BUBBLE_USER: Color32 = Color32::from_rgb(0xE8, 0xEC, 0xF2); // 用户气泡
const L_BUBBLE_AI: Color32 = Color32::from_rgb(0xFF, 0xFF, 0xFF); // AI 气泡
const L_TEXT: Color32 = Color32::from_rgb(0x1F, 0x24, 0x2C); // 主文字
const L_TEXT_DIM: Color32 = Color32::from_rgb(0x8A, 0x91, 0x9C); // 次要文字
const L_BORDER: Color32 = Color32::from_rgb(0xE2, 0xE6, 0xEC); // 描边
const L_ACCENT: Color32 = Color32::from_rgb(0x4C, 0x6B, 0x9E); // 强调色（柔和蓝）

// 暗色：深灰
const D_BG: Color32 = Color32::from_rgb(0x14, 0x16, 0x1A);
const D_PANEL: Color32 = Color32::from_rgb(0x1E, 0x21, 0x27);
const D_PANEL_ALT: Color32 = Color32::from_rgb(0x27, 0x2B, 0x33);
const D_BUBBLE_USER: Color32 = Color32::from_rgb(0x2C, 0x33, 0x40);
const D_BUBBLE_AI: Color32 = Color32::from_rgb(0x22, 0x26, 0x2E);
const D_TEXT: Color32 = Color32::from_rgb(0xE6, 0xE9, 0xEF);
const D_TEXT_DIM: Color32 = Color32::from_rgb(0x8B, 0x93, 0xA0);
const D_BORDER: Color32 = Color32::from_rgb(0x33, 0x38, 0x42);
const D_ACCENT: Color32 = Color32::from_rgb(0x7B, 0x9C, 0xD0);

/// 当前生效的配色
#[derive(Clone, Copy)]
pub struct Palette {
    pub bg: Color32,
    pub panel: Color32,
    pub panel_alt: Color32,
    pub bubble_user: Color32,
    pub bubble_ai: Color32,
    pub text: Color32,
    pub text_dim: Color32,
    pub border: Color32,
    pub accent: Color32,
    pub dark: bool,
}

impl Palette {
    pub fn get(dark: bool) -> Self {
        if dark {
            Self {
                bg: D_BG,
                panel: D_PANEL,
                panel_alt: D_PANEL_ALT,
                bubble_user: D_BUBBLE_USER,
                bubble_ai: D_BUBBLE_AI,
                text: D_TEXT,
                text_dim: D_TEXT_DIM,
                border: D_BORDER,
                accent: D_ACCENT,
                dark: true,
            }
        } else {
            Self {
                bg: L_BG,
                panel: L_PANEL,
                panel_alt: L_PANEL_ALT,
                bubble_user: L_BUBBLE_USER,
                bubble_ai: L_BUBBLE_AI,
                text: L_TEXT,
                text_dim: L_TEXT_DIM,
                border: L_BORDER,
                accent: L_ACCENT,
                dark: false,
            }
        }
    }
}

/// 把配色应用到 egui 全局样式
pub fn apply(ctx: &egui::Context, dark: bool) {
    let p = Palette::get(dark);
    let mut visuals = if dark {
        Visuals::dark()
    } else {
        Visuals::light()
    };
    visuals.window_fill = p.panel;
    visuals.panel_fill = p.bg;
    visuals.extreme_bg_color = p.panel_alt;
    visuals.faint_bg_color = p.panel_alt;

    visuals.override_text_color = Some(p.text);

    visuals.widgets.noninteractive.bg_fill = p.panel;
    visuals.widgets.noninteractive.fg_stroke = Stroke::new(1.0, p.text_dim);
    visuals.widgets.noninteractive.bg_stroke = Stroke::new(1.0, p.border);
    visuals.widgets.noninteractive.corner_radius = CornerRadius::same(6);

    visuals.widgets.inactive.bg_fill = p.panel_alt;
    visuals.widgets.inactive.fg_stroke = Stroke::new(1.0, p.text);
    visuals.widgets.inactive.bg_stroke = Stroke::new(1.0, p.border);
    visuals.widgets.inactive.corner_radius = CornerRadius::same(6);

    visuals.widgets.hovered.bg_fill = p.border;
    visuals.widgets.hovered.fg_stroke = Stroke::new(1.0, p.text);
    visuals.widgets.hovered.bg_stroke = Stroke::new(1.0, p.accent);
    visuals.widgets.hovered.corner_radius = CornerRadius::same(6);

    visuals.widgets.active.bg_fill = p.accent;
    visuals.widgets.active.fg_stroke = Stroke::new(1.0, Color32::WHITE);
    visuals.widgets.active.bg_stroke = Stroke::new(1.0, p.accent);
    visuals.widgets.active.corner_radius = CornerRadius::same(6);

    visuals.selection.bg_fill = p.accent.gamma_multiply(0.35);
    visuals.selection.stroke = Stroke::new(1.0, p.accent);

    ctx.set_visuals(visuals);
}
