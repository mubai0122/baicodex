//! 两个"模式"。
//!
//! - **WorkMode**：权限档位。决定"能碰到什么"，与工具集无关。
//! - **AgentMode**：工作方式。决定"用什么工具、怎么写提示词"，与权限无关。
//!
//! 两者正交：可以"Root + 极简"（有权限但只用最小工具集），也可以
//! "Normal + 代码"（沙箱里写代码）。把它们合成一个枚举是常见的设计错误，
//! 会导致"切到代码模式就要求 root"这种荒唐行为。

use serde::{Deserialize, Serialize};

/// 权限档位。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum WorkMode {
    /// 应用沙箱 + 可选零宽扩展。
    #[default]
    Normal,
    /// Shizuku（UID 2000）。
    Shizuku,
    /// root。
    Root,
}

impl WorkMode {
    pub fn label(self) -> &'static str {
        match self {
            WorkMode::Normal => "普通用户",
            WorkMode::Shizuku => "Shizuku",
            WorkMode::Root => "Root",
        }
    }

    pub fn desc(self) -> &'static str {
        match self {
            WorkMode::Normal => "应用沙箱权限，可选零宽路径拓展",
            WorkMode::Shizuku => "Binder 桥接，得到 shell(UID 2000) 能力",
            WorkMode::Root => "su -c 直接执行，无限制",
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            WorkMode::Normal => "normal",
            WorkMode::Shizuku => "shizuku",
            WorkMode::Root => "root",
        }
    }

    pub fn to_u8(self) -> u8 {
        match self {
            WorkMode::Normal => 0,
            WorkMode::Shizuku => 1,
            WorkMode::Root => 2,
        }
    }

    pub fn from_u8(v: u8) -> Self {
        match v {
            1 => WorkMode::Shizuku,
            2 => WorkMode::Root,
            _ => WorkMode::Normal,
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s.trim().to_ascii_lowercase().as_str() {
            "normal" | "n" | "普通" | "沙箱" => Some(WorkMode::Normal),
            "shizuku" | "s" | "shell" => Some(WorkMode::Shizuku),
            "root" | "r" | "su" | "超级用户" => Some(WorkMode::Root),
            _ => None,
        }
    }

    pub fn all() -> [WorkMode; 3] {
        [WorkMode::Normal, WorkMode::Shizuku, WorkMode::Root]
    }

    /// 这个档位是否需要额外的前置条件（su / rish）。
    pub fn needs_setup(self) -> bool {
        !matches!(self, WorkMode::Normal)
    }
}

/// 工作方式。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum AgentMode {
    /// 计划：先出方案，用户确认后再动手。
    Plan,
    /// 标准：全部工具。
    #[default]
    Standard,
    /// 代码：偏向读写文件与执行命令。
    Code,
    /// 极简：只给最小工具集，减少模型乱调。
    Minimal,
    /// 创造：偏向写作、画图、生成。
    Creator,
}

impl AgentMode {
    pub fn label(self) -> &'static str {
        match self {
            AgentMode::Plan => "计划",
            AgentMode::Standard => "标准",
            AgentMode::Code => "代码",
            AgentMode::Minimal => "极简",
            AgentMode::Creator => "创造",
        }
    }

    pub fn desc(self) -> &'static str {
        match self {
            AgentMode::Plan => "先给方案，确认后再执行",
            AgentMode::Standard => "全部工具，按需使用",
            AgentMode::Code => "专注读写文件、跑命令、改项目",
            AgentMode::Minimal => "只给最小工具集，少即是多",
            AgentMode::Creator => "写作、改写、生成内容为主",
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            AgentMode::Plan => "plan",
            AgentMode::Standard => "standard",
            AgentMode::Code => "code",
            AgentMode::Minimal => "minimal",
            AgentMode::Creator => "creator",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s.trim().to_ascii_lowercase().as_str() {
            "plan" | "p" | "计划" => Some(AgentMode::Plan),
            "standard" | "std" | "标准" => Some(AgentMode::Standard),
            "code" | "c" | "代码" => Some(AgentMode::Code),
            "minimal" | "min" | "极简" => Some(AgentMode::Minimal),
            "creator" | "write" | "创作" | "创造" => Some(AgentMode::Creator),
            _ => None,
        }
    }

    pub fn all() -> [AgentMode; 5] {
        [AgentMode::Plan, AgentMode::Standard, AgentMode::Code, AgentMode::Minimal, AgentMode::Creator]
    }

    /// 是否需要"先出计划再执行"的闸门。
    pub fn needs_plan(self) -> bool {
        self == AgentMode::Plan
    }

    /// 是否只给最小工具集。
    pub fn minimal_tools(self) -> bool {
        self == AgentMode::Minimal
    }

    /// 图标（终端里用文字符号，不依赖 Nerd Font）。
    pub fn glyph(self) -> &'static str {
        match self {
            AgentMode::Plan => "◇",
            AgentMode::Standard => "●",
            AgentMode::Code => "⌘",
            AgentMode::Minimal => "·",
            AgentMode::Creator => "✎",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_aliases() {
        assert_eq!(WorkMode::parse("SU"), Some(WorkMode::Root));
        assert_eq!(WorkMode::parse("沙箱"), Some(WorkMode::Normal));
        assert_eq!(AgentMode::parse("计划"), Some(AgentMode::Plan));
        assert_eq!(AgentMode::parse("nope"), None);
    }

    #[test]
    fn u8_roundtrip() {
        for m in WorkMode::all() {
            assert_eq!(WorkMode::from_u8(m.to_u8()), m);
        }
    }
}
