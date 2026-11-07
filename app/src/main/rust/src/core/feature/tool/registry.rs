//! 工具注册表：名字、标签、风险、可用模式。
//!
//! 这是**唯一的真相来源**。定义（JSON Schema）在 [super::spec]，执行在
//! [super::dispatch]，但"这个工具叫什么、多危险、什么模式下能用"只在这里写一遍。
//!
//! 为什么把风险写死在表里而不是让工具自己报：风险等级要稳定。
//! 一个工具改了实现，风险等级不能跟着变——否则用户的"永久允许"会突然失效或放大。

use crate::core::feature::model::mode::AgentMode;
use crate::core::security::policy::Risk;

// —— 工具名。改动等于破坏技能包与用户习惯，只能加不能改 ——
pub const READ: &str = "read_file";
pub const WRITE: &str = "write_file";
pub const EDIT: &str = "edit_file";
pub const MAKE_DIR: &str = "make_dir";
pub const DELETE: &str = "delete_file";
pub const LIST: &str = "list_dir";
pub const INFO: &str = "file_info";
pub const MOVE: &str = "move_path";
pub const ZIP: &str = "zip_op";
pub const GREP: &str = "grep_files";

pub const SHELL: &str = "exec_shell";
pub const LINUX: &str = "linux_shell";

pub const WEB_SEARCH: &str = "web_search";
pub const HTTP: &str = "http_fetch";
pub const BROWSE: &str = "browser";
pub const RAIL: &str = "rail_query";

pub const MEMORY_SAVE: &str = "memory_save";
pub const MEMORY_SEARCH: &str = "memory_search";

pub const ASK: &str = "ask_user";
pub const SUB: &str = "subagent_run";
pub const SCREEN: &str = "screen";
pub const CLIP: &str = "clipboard";
pub const OPEN: &str = "open_link";
pub const NOTI: &str = "notification";
pub const APK: &str = "apk";
pub const SKILL_READ: &str = "skill_read";
pub const JS: &str = "js";
pub const MCP_PREFIX: &str = "mcp_";

pub const FLOW_RUN: &str = "flow_run";
pub const FLOW_SAVE: &str = "flow_save";
pub const FLOW_LIST: &str = "flow_list";
pub const A2A: &str = "a2a";

pub const SSH_CONNECT: &str = "ssh_connect";
pub const SSH_LS: &str = "ssh_ls";
pub const SSH_READ: &str = "ssh_read";
pub const SSH_WRITE: &str = "ssh_write";
pub const SSH_SUDO: &str = "ssh_sudo";
pub const SSH_EXEC: &str = "ssh_exec";
pub const SSH_GET: &str = "ssh_get";
pub const SSH_PUT: &str = "ssh_put";

/// 全部 SSH 工具。
pub const SSH_ALL: &[&str] = &[
    SSH_CONNECT, SSH_LS, SSH_READ, SSH_WRITE, SSH_SUDO, SSH_EXEC, SSH_GET, SSH_PUT,
];

// 模式位。用 u8 而不是 Vec<AgentMode>：查表时一次位运算，不用遍历。
const M_PLAN: u8 = 1 << 0;
const M_STD: u8 = 1 << 1;
const M_CODE: u8 = 1 << 2;
const M_MIN: u8 = 1 << 3;
const M_CREATOR: u8 = 1 << 4;
const M_ALL: u8 = M_PLAN | M_STD | M_CODE | M_MIN | M_CREATOR;

/// 工具元信息。
#[derive(Debug, Clone, Copy)]
pub struct Meta {
    pub name: &'static str,
    /// 给人看的短标签（确认弹窗、工具卡片标题）。
    pub label: &'static str,
    pub risk: Risk,
    /// 允许的模式位。
    pub modes: u8,
    /// 分类，给界面分组与"预算按类分配"用。
    pub kind: &'static str,
}

const fn m(name: &'static str, label: &'static str, risk: Risk, modes: u8, kind: &'static str) -> Meta {
    Meta { name, label, risk, modes, kind }
}

/// 内置工具表。顺序就是模型看到的大致顺序（定义里再按需排）。
pub const TABLE: &[Meta] = &[
    m(READ, "读文件", Risk::Low, M_ALL, "fs"),
    m(LIST, "列目录", Risk::Low, M_ALL, "fs"),
    m(INFO, "文件信息", Risk::Low, M_ALL, "fs"),
    m(GREP, "搜索内容", Risk::Low, M_ALL, "fs"),
    m(WRITE, "写文件", Risk::Medium, M_PLAN | M_STD | M_CODE | M_MIN | M_CREATOR, "fs"),
    m(EDIT, "改文件", Risk::Medium, M_PLAN | M_STD | M_CODE | M_MIN | M_CREATOR, "fs"),
    m(MAKE_DIR, "建目录", Risk::Medium, M_ALL, "fs"),
    m(DELETE, "删除", Risk::High, M_PLAN | M_STD | M_CODE | M_MIN, "fs"),
    m(MOVE, "移动/复制", Risk::Medium, M_STD | M_CODE | M_MIN, "fs"),
    m(ZIP, "压缩/解压", Risk::Medium, M_STD | M_CODE | M_MIN, "fs"),
    m(SHELL, "执行命令", Risk::High, M_ALL, "shell"),
    m(LINUX, "Linux 环境", Risk::High, M_STD | M_CODE, "shell"),
    m(WEB_SEARCH, "联网搜索", Risk::Low, M_ALL, "web"),
    m(HTTP, "HTTP 请求", Risk::Low, M_ALL, "web"),
    m(BROWSE, "浏览网页", Risk::Low, M_STD | M_CODE | M_CREATOR, "web"),
    m(RAIL, "查车次", Risk::Low, M_ALL, "web"),
    m(MEMORY_SAVE, "记一条", Risk::Low, M_ALL, "memory"),
    m(MEMORY_SEARCH, "查记忆", Risk::Low, M_ALL, "memory"),
    m(ASK, "问用户", Risk::Low, M_ALL, "ui"),
    m(SCREEN, "看/点屏幕", Risk::High, M_STD | M_CODE, "screen"),
    m(CLIP, "剪贴板", Risk::Medium, M_ALL, "device"),
    m(OPEN, "打开/分享", Risk::Medium, M_ALL, "device"),
    m(NOTI, "通知", Risk::Medium, M_STD | M_CODE, "device"),
    m(APK, "解析 APK", Risk::Low, M_CODE, "device"),
    m(SKILL_READ, "看技能说明", Risk::Low, M_ALL, "skill"),
    m(JS, "跑 JS", Risk::High, M_STD | M_CODE, "skill"),
    m(FLOW_RUN, "跑工作流", Risk::High, M_STD | M_CODE, "flow"),
    m(FLOW_SAVE, "存工作流", Risk::Medium, M_STD | M_CODE | M_CREATOR, "flow"),
    m(FLOW_LIST, "列工作流", Risk::Low, M_ALL, "flow"),
    m(A2A, "A2A 对话", Risk::High, M_STD | M_CODE, "a2a"),
    m(SUB, "子智能体", Risk::High, M_STD | M_CODE, "agent"),
    m(SSH_CONNECT, "连服务器", Risk::High, M_STD | M_CODE, "ssh"),
    m(SSH_LS, "列远端目录", Risk::Medium, M_STD | M_CODE, "ssh"),
    m(SSH_READ, "读远端文件", Risk::Medium, M_STD | M_CODE, "ssh"),
    m(SSH_WRITE, "写远端文件", Risk::High, M_STD | M_CODE, "ssh"),
    m(SSH_SUDO, "远端 sudo", Risk::High, M_STD | M_CODE, "ssh"),
    m(SSH_EXEC, "远端执行", Risk::High, M_STD | M_CODE, "ssh"),
    m(SSH_GET, "下载文件", Risk::Medium, M_STD | M_CODE, "ssh"),
    m(SSH_PUT, "上传文件", Risk::High, M_STD | M_CODE, "ssh"),
];

/// 计划模式下禁止的工具：任何会改动外部状态的动作。
///
/// 计划模式的语义是"只看不做"，所以不只是禁写文件，还要禁执行命令、发消息、
/// 调子智能体——它们都可能间接改东西。
pub const PLAN_OFF: &[&str] = &[
    WRITE, EDIT, DELETE, MAKE_DIR, MOVE, ZIP, SHELL, LINUX, SCREEN, CLIP, OPEN, NOTI, JS,
    FLOW_RUN, A2A, SUB, SSH_WRITE, SSH_SUDO, SSH_EXEC, SSH_PUT, SSH_CONNECT,
];

/// 极简模式的工具集。
pub const MIN_SET: &[&str] = &[READ, LIST, WRITE, EDIT, MAKE_DIR, DELETE, SHELL];

/// 查表。
pub fn meta(name: &str) -> Option<&'static Meta> {
    if name.starts_with(MCP_PREFIX) {
        // MCP 工具动态注册，风险按"能干什么"分不了，统一按高风险处理
        return None;
    }
    TABLE.iter().find(|x| x.name == name)
}

/// 风险等级。未知工具（技能包函数、MCP）按高风险——它们能干什么我们不知道。
pub fn risk_of(name: &str) -> Risk {
    meta(name).map(|m| m.risk).unwrap_or(Risk::High)
}

/// 标签。未知工具返回名字本身。
pub fn label(name: &str) -> String {
    meta(name).map(|m| m.label.to_string()).unwrap_or_else(|| name.to_string())
}

/// 分类。
pub fn kind(name: &str) -> &'static str {
    meta(name).map(|m| m.kind).unwrap_or("other")
}

/// 是否高风险。
pub fn is_high_risk(name: &str) -> bool {
    risk_of(name) == Risk::High
}

fn mode_bit(mode: AgentMode) -> u8 {
    match mode {
        AgentMode::Plan => M_PLAN,
        AgentMode::Standard => M_STD,
        AgentMode::Code => M_CODE,
        AgentMode::Minimal => M_MIN,
        AgentMode::Creator => M_CREATOR,
    }
}

/// 这个模式允许的内置工具。
pub fn allowed_in(mode: AgentMode) -> Vec<&'static str> {
    let bit = mode_bit(mode);
    let mut out: Vec<&'static str> = TABLE.iter().filter(|m| m.modes & bit != 0).map(|m| m.name).collect();
    if mode.minimal_tools() {
        out.retain(|n| MIN_SET.contains(n));
    }
    if mode.needs_plan() {
        out.retain(|n| !PLAN_OFF.contains(n));
    }
    out
}

/// 这个工具在指定模式下是否可用。
pub fn allowed(name: &str, mode: AgentMode) -> bool {
    // 技能包函数与 MCP 工具不受模式表限制：它们由用户自己安装，装了就说明要用
    if name.starts_with(MCP_PREFIX) {
        return !mode.minimal_tools();
    }
    let Some(m) = meta(name) else { return true };
    if mode.minimal_tools() {
        return MIN_SET.contains(&name);
    }
    if mode.needs_plan() && PLAN_OFF.contains(&name) {
        return false;
    }
    m.modes & mode_bit(mode) != 0
}

/// 全部内置工具名。
pub fn builtin_names() -> Vec<&'static str> {
    TABLE.iter().map(|m| m.name).collect()
}

/// 一组工具的标签串，用于确认弹窗标题：`执行命令 · exec_shell`。
pub fn describe(name: &str) -> String {
    let l = label(name);
    if l == name {
        name.to_string()
    } else {
        format!("{l} · {name}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plan_mode_blocks_writes() {
        let list = allowed_in(AgentMode::Plan);
        assert!(list.contains(&READ));
        assert!(!list.contains(&WRITE));
        assert!(!list.contains(&SHELL));
    }

    #[test]
    fn minimal_mode_is_small() {
        let list = allowed_in(AgentMode::Minimal);
        assert!(list.len() <= MIN_SET.len());
        assert!(list.contains(&READ));
        assert!(!list.contains(&WEB_SEARCH));
    }

    #[test]
    fn unknown_is_high_risk() {
        assert_eq!(risk_of("some_skill_fn"), Risk::High);
        assert_eq!(risk_of("mcp_server_tool"), Risk::High);
    }

    #[test]
    fn table_has_no_duplicates() {
        let mut names: Vec<_> = builtin_names();
        let n = names.len();
        names.sort();
        names.dedup();
        assert_eq!(names.len(), n);
    }

    #[test]
    fn plan_off_all_exist() {
        for n in PLAN_OFF {
            assert!(meta(n).is_some(), "PLAN_OFF 里有不存在的工具 {n}");
        }
        for n in MIN_SET {
            assert!(meta(n).is_some(), "MIN_SET 里有不存在的工具 {n}");
        }
    }
}
