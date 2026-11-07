//! 命令类工具：`exec_shell` 与 `linux_shell`。
//!
//! 两条命令工具的区别只有"在哪跑"：`exec_shell` 在设备上跑，`linux_shell` 在内置
//! Linux 根文件系统里跑。权限分派、计划模式拦截、超时、工作区 `cd` 前缀都要一致，
//! 所以都走这里。
//!
//! ## 计划模式为什么在工具层拦
//!
//! 策略层只知道"要调 exec_shell"，不知道这条命令是 `ls` 还是 `rm -rf`。要判断
//! "会不会改动磁盘"只能看命令文本，那属于工具的知识。**硬红线仍在策略层**，
//! 这里是第二道闸，两道都要有：策略管"这个工具能不能用"，这里管"这条命令能不能跑"。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::jsonx;
use crate::core::feature::app::App;
use crate::core::feature::config::schema::Config;
use crate::core::feature::model::mode::{AgentMode, WorkMode};
use crate::core::feature::model::session::Route;
use crate::exec::{file_ops, linux, root, shell, shizuku, workspace};

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

fn i(v: &Value, k: &str) -> i64 {
    jsonx::get_i64(v, &[k], 0)
}

/// `exec_shell`：在设备上执行命令。
pub async fn exec(app: &App, args: &Value, cfg: &Config) -> ToolOutcome {
    let command = s(args, "command");
    let cmd = command.trim();
    if cmd.is_empty() {
        return ToolOutcome::err("命令为空", Route::Sandbox);
    }

    // 计划模式：只读命令放行，写盘的一律拒绝。
    if cfg.agent.mode == AgentMode::Plan {
        if let Some(hit) = file_ops::plan_write_hit(cmd) {
            return ToolOutcome::err(
                format!(
                    "计划模式下不允许改动文件（命中「{}」），本次命令没有执行。先出计划，用户确认后再动手。",
                    hit.trim()
                ),
                Route::Sandbox,
            );
        }
    }

    let timeout_s = if i(args, "timeout") > 0 {
        i(args, "timeout").clamp(1, 1800) as u64
    } else {
        cfg.tool.shell_timeout_s.clamp(1, 1800)
    };
    let timeout = std::time::Duration::from_secs(timeout_s);
    let cap = cfg.tool.max_output_chars.clamp(4096, 4 * 1024 * 1024);
    let wrapped = workspace::wrap(cfg, cmd);

    let (res, route, tag) = match cfg.agent.work_mode {
        WorkMode::Root => (root::run_capped(&wrapped, timeout, cap).await, Route::Root, "root"),
        WorkMode::Shizuku => (shizuku::run_capped(&wrapped, timeout, cap).await, Route::Shizuku, "Shizuku"),
        WorkMode::Normal => (shell::run_full(&["sh", "-c", &wrapped], None, timeout, None, cap, &[]).await, Route::Sandbox, "沙箱"),
    };

    let mut text = format!("[{tag}] {}\n{}", res.status_line(), res.out);
    let reason = res.reason();
    if !reason.is_empty() {
        text.push_str(&format!("\n（{reason}）"));
    }
    if res.truncated {
        text.push_str(&format!("\n（输出超过 {cap} 字节，已截断）"));
    }

    // 给一条有针对性的提示：受限目录在普通身份下就是读不到，换工具没用，要换通道。
    if !res.ok()
        && cfg.agent.work_mode == WorkMode::Normal
        && !app.router.readable()
        && (cmd.contains("/Android/data") || cmd.contains("/Android/obb"))
    {
        text.push_str(
            "\n（exec_shell 走原路径，零宽变换只对 read_file / write_file / make_dir / delete_file 生效；这个目录请改用那几个工具）",
        );
    }

    let out = ToolOutcome::ok(text, route).with_meta(serde_json::json!({
        "exit": res.code,
        "dur_ms": res.dur_ms,
        "truncated": res.truncated,
        "mode": cfg.agent.work_mode.as_str(),
    }));
    // 失败时 ok=false，界面才会标红。
    if res.ok() {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}

/// `linux_shell`：在内置 Linux 里执行命令。
pub async fn linux(app: &App, args: &Value, cfg: &Config) -> ToolOutcome {
    let command = s(args, "command");
    let cmd = command.trim();
    if cmd.is_empty() {
        return ToolOutcome::err("命令为空", Route::Sandbox);
    }
    if cfg.agent.mode == AgentMode::Plan {
        if let Some(hit) = file_ops::plan_write_hit(cmd) {
            return ToolOutcome::err(
                format!("计划模式下不允许改动文件（命中「{}」），本次命令没有执行", hit.trim()),
                Route::Sandbox,
            );
        }
    }
    let _ = app;
    let timeout_s = if i(args, "timeout") > 0 {
        i(args, "timeout").clamp(1, 1800) as u64
    } else {
        cfg.tool.shell_timeout_s.clamp(1, 1800)
    };
    let res = crate::exec::linux::run_in(cmd, std::time::Duration::from_secs(timeout_s)).await;
    let mut text = format!("[内置 Linux / {}] {}\n{}", linux::backend().await, res.status_line(), res.out);
    if !res.ok() {
        let hint = linux::version();
        if hint.is_empty() && !linux::installed() {
            text.push_str("\n（没装根文件系统：设置 → 扩展 → Linux 环境里装一次）");
        }
    }
    let out = ToolOutcome::ok(text, Route::Sandbox);
    if res.ok() {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}

/// 命令是否看起来想写盘（给上层做二次确认用）。
pub fn looks_write(cmd: &str) -> bool {
    file_ops::plan_write_hit(cmd).is_some()
}
