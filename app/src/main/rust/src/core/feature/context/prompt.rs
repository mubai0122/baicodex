//! 系统提示组装。
//!
//! 顺序有讲究：**越靠前的越不可违背，越靠后的越可以按情况调整**。
//!
//! ```text
//! 角色人设 → 自定义提示 → 工作流 → 模式 → 记忆 → 环境 → 工具与规则
//!          → 工作区 → 技能 / MCP → 提醒
//! ```
//!
//! ## 三条纪律
//!
//! 1. **提示里提到的东西必须真能调**。工具清单跟工具定义走同一套模式过滤，
//!    否则模型会去调一个它根本没有的工具，然后卡在那反复重试。
//! 2. **不写"你可以做任何事"**。写清楚边界（哪个模式下不能写文件），模型才不会硬撞。
//! 3. **每段都可为空**。没有记忆、没有技能、没绑工作区时，不要留空标题。

use crate::core::feature::app::App;
use crate::core::feature::config::schema::Config;
use crate::core::feature::model::mode::{AgentMode, WorkMode};
use crate::core::feature::tool::registry as R;
use crate::exec::workspace;

/// 组装完整系统提示。
///
/// `query` 用于记忆召回：拿当前这句话去匹配，比"把所有记忆都塞进来"准得多。
pub fn build(app: &App, query: &str) -> String {
    let cfg = app.cfg();
    let mut s = String::with_capacity(4096);

    // 1. 角色
    let persona = app.personas.read().map(|p| p.prompt(&cfg.agent.persona)).unwrap_or_default();
    if !persona.is_empty() {
        s.push_str(&persona);
        s.push_str("\n\n");
    }

    // 2. 自定义
    let custom = cfg.agent.system_prompt.trim().to_string();
    if !custom.is_empty() {
        s.push_str(&custom);
        s.push_str("\n\n");
    }

    // 3. 模式
    s.push_str(mode_prompt(cfg.agent.mode));
    s.push_str("\n\n");

    // 4. 记忆
    if cfg.memory.enabled {
        let memo = app
            .memory
            .read()
            .map(|m| m.prompt(query, cfg.memory.recall_top, cfg.memory.min_score, cfg.memory.prompt_chars))
            .unwrap_or_default();
        if !memo.is_empty() {
            s.push_str(&memo);
            s.push('\n');
        }
    }

    // 5. 环境
    s.push_str(&env_block(app, &cfg));

    // 6. 工具与规则
    s.push_str(&tools_block(&cfg));
    s.push_str(&rules_block(app, &cfg));

    // 7. 工作区
    let ws = workspace_block(&cfg);
    if !ws.is_empty() {
        s.push_str(&ws);
    }

    // 8. 技能与 MCP
    let skills = app.skills.read().map(|x| x.prompt()).unwrap_or_default();
    if !skills.is_empty() {
        s.push('\n');
        s.push_str(&skills);
    }
    let packs = app.packs.read().map(|x| x.prompt()).unwrap_or_default();
    if !packs.is_empty() {
        s.push('\n');
        s.push_str(&packs);
    }
    let mcp = app.mcp.read().map(|x| x.prompt()).unwrap_or_default();
    if !mcp.is_empty() {
        s.push('\n');
        s.push_str(&mcp);
    }
    let flows = app.flows.read().map(|x| x.prompt()).unwrap_or_default();
    if !flows.is_empty() {
        s.push('\n');
        s.push_str(&flows);
    }

    // 9. 提醒
    s.push_str(&tail_block(app, &cfg));
    s.trim_end().to_string()
}

/// 模式说明。
pub fn mode_prompt(m: AgentMode) -> &'static str {
    match m {
        AgentMode::Plan => {
            "当前是【计划模式】：只调研、只出方案，不要改动任何东西。write_file / edit_file / make_dir / delete_file / memory_save / linux_shell 都已禁用，\
             exec_shell 只跑只读命令（ls / cat / grep / find / head / wc）。先给结论，再给分步计划（每步写清改哪个文件、跑什么命令、怎么验证），\
             结尾列出需要用户拍板的问题；用户同意后再切到标准或代码模式动手。"
        }
        AgentMode::Standard => "当前是【标准模式】：正常干活，该读读、该改改、该跑命令就跑。",
        AgentMode::Code => {
            "当前是【代码模式】：能力同标准模式，但把重复、有条件、多步的操作合并成一段脚本一次跑完\
             （在 exec_shell 里写 sh / python 循环，别一格一格来回调工具），只在必须看中间结果时才拆开。"
        }
        AgentMode::Minimal => {
            "当前是【极简模式】：只有 read_file / list_dir / write_file / edit_file / make_dir / delete_file / exec_shell 这几个工具，\
             搜索、记忆、火车票、Linux、MCP 全都不可用。不要提这些能力，需要时直接说明当前模式做不到。"
        }
        AgentMode::Creator => {
            "当前是【创造模式】：在标准模式能力之上，你被授权改动自己的运行环境——\
             系统提示词、技能包、工作流配置、工具权限都可以改。动手前先读现状，改完说明改了哪几处、怎么回退。"
        }
    }
}

/// 环境一段。
fn env_block(app: &App, cfg: &Config) -> String {
    let _ = app;
    let mut s = String::new();
    let now = crate::core::base::clock::now_ms();
    if cfg.workspace.inject_time {
        s.push_str(&format!(
            "现在：{} {}（{}）。\n",
            crate::core::base::clock::today(),
            crate::core::base::clock::fmt_hm(now),
            crate::core::base::clock::part_of_day()
        ));
    }
    if cfg.workspace.inject_battery {
        if let Some((pct, charging)) = crate::core::base::sys::battery() {
            s.push_str(&format!("电量：{pct}%{}。\n", if charging { "（充电中）" } else { "" }));
        }
    }
    s.push_str(&format!(
        "设备：{}；工作方式：{}；Android/data 与 obb：{}。\n",
        crate::core::base::sys::device_line(),
        cfg.agent.work_mode.label(),
        if app.router.probed_once() { app.router.data_zone() } else { "检测中".to_string() }
    ));
    if cfg.agent.work_mode == WorkMode::Normal {
        s.push_str("（exec_shell 走原路径、不受零宽影响；这类目录一律用 read_file / write_file / edit_file / make_dir / delete_file）\n");
    }
    s
}

/// 工具清单。
fn tools_block(cfg: &Config) -> String {
    let names: Vec<String> = R::builtin_names()
        .into_iter()
        .filter(|n| R::allowed(n, cfg.agent.mode))
        .map(R::label)
        .collect();
    let mut s = if names.is_empty() {
        "当前没有任何可用工具。\n".to_string()
    } else {
        format!("工具：{}。\n", names.join(" / "))
    };
    if !cfg.model.tools {
        s.push_str("注意：当前模型没勾选工具调用能力，请求里没有下发任何工具。只能用文字回答，不要假装执行过命令或读过文件。\n");
    }
    if !cfg.model.vision {
        s.push_str("注意：当前模型没勾选图片输入能力，看不到用户附的图片，需要让用户改用文字描述。\n");
    }
    s
}

/// 行为规则。
fn rules_block(app: &App, cfg: &Config) -> String {
    let mut s = String::new();
    s.push_str(
        "用法：\
         解压、安装、逆向等一律用 exec_shell 拼命令完成；\
         只改文件里几行就用 edit_file（给现有原文 old 与新内容 new，缩进自动对齐），不要整篇重写；\
         车次、时刻、余票一律用 rail_query 查，不要凭记忆编车次；\
         值得长期记住的偏好或结论用 memory_save 存下来，不要只在回答里提醒自己；\
         遇到像是以前告诉过你的事而上下文里没有，先用 memory_search 查一遍再开口，别猜也别急着问用户；\
         信息不够就先用 ask_user 问清（每题 2-4 个选项，用户还能自己填），别猜着往下做。\n",
    );
    if R::allowed(R::SCREEN, cfg.agent.mode) {
        s.push_str("操作屏幕要多步时，第一步先调 screen action=start 让顶部胶囊常驻，全部做完最后调 screen action=quiet 收起；");
    }
    if crate::net::search::selected(cfg).is_some() {
        s.push_str("需要最新信息或核实事实时用 web_search。\n");
    } else {
        s.push_str("没有配置搜索平台，不要假设自己能上网搜。\n");
    }

    let path_rule = if workspace::bound(cfg) {
        "相对路径按下面的工作区解析，工作区外才写绝对路径；"
    } else {
        "路径用绝对路径，工作目录优先 /sdcard/；"
    };
    s.push_str(&format!(
        "规则：1) {path_rule}\
         2) 高风险工具调用会让用户确认，被拒绝时不要重复同一调用，换方案或直接说明；\
         3) 工具结果里的 [沙箱]/[root]/[Shizuku]/[内置 Linux] 是实际走的通道，失败时据此判断是权限问题还是命令问题；\
         4) 大段输出先落文件再分段读，不要一次性刷屏；\
         5) 完成任务后用一两句话收尾，不要复述全部过程。\n"
    ));
    let _ = app;
    s
}

/// 工作区一段。
fn workspace_block(cfg: &Config) -> String {
    if !workspace::bound(cfg) {
        return "当前没有绑定工作区：所有路径必须写绝对路径（例如 /sdcard/Download/a.txt），相对路径不生效。\n".into();
    }
    let p = workspace::path(cfg);
    let mut s = format!("工作区：{p}（已绑定）。\n");
    s.push_str(&format!(
        "相对路径就落在工作区里，例如 write_file(\"notes/a.md\") 等于写 {p}/notes/a.md；\
         exec_shell / linux_shell 已经自动 cd 到工作区，命令里直接用相对路径；要动工作区之外的地方必须写绝对路径。\n"
    ));
    s.push_str(&format!(
        "工作区索引：{p}/{}（含全量相对路径、体积、目录清单；需要整体结构时读它，不要自己 find 全盘）。\n",
        workspace::INDEX
    ));
    let tree = workspace::tree(cfg);
    if !tree.is_empty() {
        s.push_str("工作区目录树（深度 ≤3，已跳过 build/.git 等产物目录）：\n");
        s.push_str(&tree);
    }
    s
}

/// 结尾提醒。
fn tail_block(app: &App, cfg: &Config) -> String {
    let mut s = String::new();
    if cfg.context.auto_compact {
        s.push_str("\n上下文不够时会自动压缩较早的对话；被压缩的部分在界面里仍能看到，但你看不到原文，需要细节就重新读文件或查记忆。\n");
    }
    let line = crate::core::feature::usage::session_line(&app.db, cfg, &app.session_id().unwrap_or_default());
    if !line.is_empty() {
        s.push_str(&format!("{line}\n"));
    }
    s
}

/// 给设置页预览：按段落切开。
pub fn sections(app: &App) -> Vec<(String, String)> {
    let cfg = app.cfg();
    vec![
        ("角色".into(), app.personas.read().map(|p| p.prompt(&cfg.agent.persona)).unwrap_or_default()),
        ("自定义".into(), cfg.agent.system_prompt.clone()),
        ("模式".into(), mode_prompt(cfg.agent.mode).to_string()),
        ("记忆".into(), app.memory.read().map(|m| m.prompt("", cfg.memory.recall_top, 0.0, cfg.memory.prompt_chars)).unwrap_or_default()),
        ("环境".into(), env_block(app, &cfg)),
        ("工具".into(), tools_block(&cfg)),
        ("工作区".into(), workspace_block(&cfg)),
        ("技能".into(), app.packs.read().map(|x| x.prompt()).unwrap_or_default()),
    ]
}
