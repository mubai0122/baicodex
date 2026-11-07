//! 工具层。
//!
//! 一次工具调用经过四步，每一步一个文件：
//!
//! ```text
//! registry  名字/风险/模式   → 决定"能不能出现"
//! spec      JSON Schema      → 决定"模型怎么调"
//! dispatch  分发             → 决定"调哪个实现"
//! govern    输出治理         → 决定"回灌多少、要不要脱敏"
//! ```
//!
//! 具体实现按领域分文件：`fs`（文件）、`shell`（命令）、`web`（联网）、
//! `screen`（屏幕）、`ssh`（远程）、`skill`（技能包）、`mcp`、`flow`、`a2a`。
//!
//! ## 加一个新工具要改几处
//!
//! 1. `registry.rs` 加常量与一行元信息；
//! 2. `spec.rs` 加定义；
//! 3. `dispatch.rs` 加分支，实现放到对应领域文件；
//! 4. 如果是新领域，才新建文件。
//!
//! 四处缺一不可，这是有意的：名字、描述、分发、实现分开，谁都不会忘记其中一步
//! ——编译器会提醒（穷举 match 少了分支直接报错）。

pub mod a2a;
pub mod dispatch;
pub mod flow;
pub mod fs;
pub mod govern;
pub mod mcp;
pub mod registry;
pub mod screen;
pub mod shell;
pub mod skill;
pub mod spec;
pub mod ssh;
pub mod web;

use serde::Serialize;

use crate::core::feature::model::session::Route;

/// 工具执行结果。
#[derive(Debug, Clone, Serialize)]
pub struct ToolOutcome {
    pub ok: bool,
    pub text: String,
    /// 实际走的通道。
    pub route: Route,
    /// 结构化附加信息（界面可折叠展示，模型看不到）。
    #[serde(skip_serializing_if = "Option::is_none")]
    pub meta: Option<serde_json::Value>,
}

impl ToolOutcome {
    pub fn ok(text: impl Into<String>, route: Route) -> Self {
        Self { ok: true, text: text.into(), route, meta: None }
    }

    pub fn err(text: impl Into<String>, route: Route) -> Self {
        Self { ok: false, text: text.into(), route, meta: None }
    }

    pub fn with_meta(mut self, v: serde_json::Value) -> Self {
        self.meta = Some(v);
        self
    }

    /// 给模型看的文本：失败时加前缀，让它一眼看出这不是正常结果。
    pub fn model_text(&self) -> String {
        if self.ok {
            self.text.clone()
        } else {
            format!("[失败] {}", self.text)
        }
    }
}

/// 兼容：把 `file_ops::Out` 转成工具结果。
pub fn from_file_out(o: crate::exec::file_ops::Out, route: Route) -> ToolOutcome {
    ToolOutcome { ok: o.ok, text: o.text, route, meta: None }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn error_prefix() {
        let o = ToolOutcome::err("没权限", Route::Sandbox);
        assert!(o.model_text().starts_with("[失败]"));
        assert!(ToolOutcome::ok("好了", Route::Sandbox).model_text() == "好了");
    }
}
