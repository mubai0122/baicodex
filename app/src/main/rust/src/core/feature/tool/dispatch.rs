//! 工具分发。
//!
//! 一个大 `match`，每个分支只做三件事：取参数、调实现、包结果。
//! 实现不写在这里——那是 `fs` / `shell` / `web` 等文件的事。
//!
//! 为什么用 `match` 而不是注册表 `HashMap<String, Box<dyn Fn>>`：
//! - 编译器能检查穷举，漏了分支直接报错；
//! - 没有动态分派的间接层，调试时调用栈干净；
//! - 参数解析是静态的，不用为每个工具写一遍闭包。
//!
//! 代价是加工具要改这里——但那本来就要改，不值得为"少改一行"引入一层间接。

use serde_json::Value;

use super::registry as R;
use super::{a2a, flow, fs, govern, mcp, screen, shell, skill, ssh, web, ToolOutcome};
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;

/// 执行一个工具。**不做权限判断**——那在 App 的确认闸门里，先于这里发生。
pub async fn execute(app: &App, name: &str, args: &Value) -> ToolOutcome {
    let cfg = app.cfg();

    let out = match name {
        // —— 文件 ——
        R::READ => fs::read(app, args),
        R::WRITE => fs::write(app, args),
        R::EDIT => fs::edit(app, args),
        R::MAKE_DIR => fs::mkdir(app, args),
        R::DELETE => fs::delete(app, args),
        R::LIST => fs::list(app, args),
        R::INFO => fs::info(app, args),
        R::MOVE => fs::mv(app, args),
        R::ZIP => fs::zip(app, args),
        R::GREP => fs::grep(app, args),

        // —— 命令 ——
        R::SHELL => shell::exec(app, args, &cfg).await,
        R::LINUX => shell::linux(app, args, &cfg).await,

        // —— 联网 ——
        R::WEB_SEARCH => web::search(app, args).await,
        R::HTTP => web::http(app, args).await,
        R::BROWSE => web::browse(app, args).await,
        R::RAIL => web::rail(app, args).await,

        // —— 记忆 ——
        R::MEMORY_SAVE => skill::memory_save(app, args),
        R::MEMORY_SEARCH => skill::memory_search(app, args),

        // —— 交互 ——
        R::ASK => ToolOutcome::err("ask_user 需要界面交互，只能由主循环调度", Route::Sandbox),
        R::SUB => ToolOutcome::err("subagent_run 需要主循环调度，不能单独执行", Route::Sandbox),

        // —— 设备与屏幕 ——
        R::SCREEN => screen::screen(app, args).await,
        R::CLIP => screen::clipboard(args).await,
        R::OPEN => screen::open(args).await,
        R::NOTI => screen::notify(args).await,
        R::APK => screen::apk(args),

        // —— 技能 ——
        R::SKILL_READ => skill::read(app, args),
        R::JS => skill::js(args).await,

        // —— 工作流 ——
        R::FLOW_RUN => flow::run_tool(app, args).await,
        R::FLOW_SAVE => flow::save_tool(app, args),
        R::FLOW_LIST => flow::list_tool(app),

        // —— A2A ——
        R::A2A => a2a::call(app, args).await,

        n if R::SSH_ALL.contains(&n) => ssh::call(app, n, args).await,

        n if n.starts_with(R::MCP_PREFIX) => mcp::call(app, n, args).await,

        // 剩下的都是技能包函数（动态注册），由 skill 模块负责
        n => skill::call_pack(app, n, args).await,
    };

    // 统一治理：脱敏 → 折断超长行 → 保头保尾截断。
    govern::apply(out, govern::Budget::from_cfg(&cfg.tool))
}

/// 这个工具是否需要主循环亲自处理（而不是 dispatch）。
///
/// `ask_user` / `subagent_run` / `flow_run` 需要访问事件与确认闸门，dispatch 里做不了。
pub fn needs_loop(name: &str) -> bool {
    matches!(name, R::ASK | R::SUB | R::FLOW_RUN)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loop_only_tools() {
        assert!(needs_loop(R::ASK));
        assert!(needs_loop(R::SUB));
        assert!(!needs_loop(R::READ));
    }
}
