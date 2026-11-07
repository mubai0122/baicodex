//! SSH 工具组：8 个工具共用一个实现。
//!
//! 为什么不是 8 个函数：它们的参数解析、超时、错误格式完全一样，只有"调哪个
//! 远端操作"不同。拆成 8 份的后果是改一处漏七处。
//!
//! ## 远端路径不在这里拼
//!
//! 转义在 [crate::exec::ssh] 里做。这一层只管取参数、判空、把结果转成
//! `ToolOutcome`。转义逻辑只能有一份，否则 `ssh_read` 安全、`ssh_write` 有洞
//! 这种裂缝迟早出现。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::jsonx;
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::core::feature::tool::registry as R;
use crate::exec::ssh;

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

fn i(v: &Value, k: &str) -> i64 {
    jsonx::get_i64(v, &[k], 0)
}

/// 执行一个 SSH 工具。`name` 一定是 `SSH_ALL` 里的一个。
pub async fn call(app: &App, name: &str, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    let local = s(args, "local");
    let cmd = jsonx::get_str(args, &["command", "cmd"], "");
    let timeout = std::time::Duration::from_secs(
        {
            let t = i(args, "timeout");
            if t > 0 { t } else { 180 }
        }
        .clamp(10, 1800) as u64,
    );

    // 参数缺失在本地就挡掉，别把请求发到远端再报错——那样错误信息更难懂。
    let missing = |msg: &str| ToolOutcome::err(msg.to_string(), Route::Sandbox);

    let r = match name {
        R::SSH_CONNECT => {
            let host = s(args, "host");
            let user = s(args, "user");
            if host.trim().is_empty() {
                return missing("ssh_connect 需要 host");
            }
            if user.trim().is_empty() {
                return missing("ssh_connect 需要 user");
            }
            ssh::connect(&app.vault, &host, i(args, "port").max(0) as u16, &user, &s(args, "pass")).await
        }
        R::SSH_LS => {
            if path.trim().is_empty() {
                return missing("ssh_ls 需要 path");
            }
            ssh::ls(&app.vault, &path, { let d = i(args, "depth"); if d > 0 { d as u16 } else { 2 } }).await
        }
        R::SSH_READ => {
            if path.trim().is_empty() {
                return missing("ssh_read 需要 path");
            }
            ssh::read(&app.vault, &path, i(args, "max_lines").max(0) as u32).await
        }
        R::SSH_WRITE => {
            // content 允许是空串（写空文件），但字段必须存在。
            if args.get("content").is_none() {
                return missing("ssh_write 需要 content（写空文件就传空字符串）");
            }
            let content = jsonx::get_str(args, &["content"], "");
            ssh::write(&app.vault, &path, &content).await
        }
        R::SSH_SUDO => {
            if cmd.trim().is_empty() {
                return missing("ssh_sudo 需要 command");
            }
            ssh::sudo(&app.vault, &cmd, timeout).await
        }
        R::SSH_EXEC => {
            if cmd.trim().is_empty() {
                return missing("ssh_exec 需要 command");
            }
            ssh::exec(&app.vault, &cmd, timeout).await
        }
        R::SSH_GET => {
            if path.trim().is_empty() || local.trim().is_empty() {
                return missing("ssh_get 需要 path（远端）与 local（本地）");
            }
            ssh::pull(&app.vault, &path, &local).await
        }
        _ => {
            if path.trim().is_empty() || local.trim().is_empty() {
                return missing("ssh_put 需要 local（本地）与 path（远端）");
            }
            ssh::push(&app.vault, &local, &path).await
        }
    };

    // 空输出时给一句人话，别让模型以为工具坏了。
    let text = if r.out.trim().is_empty() {
        if r.ok() {
            "完成".to_string()
        } else {
            format!("失败（{}）{}", r.status_line(), extra_hint(app))
        }
    } else {
        let mut t = r.out.clone();
        if !r.ok() {
            let h = extra_hint(app);
            if !h.is_empty() {
                t.push_str(&h);
            }
        }
        t
    };

    let out = ToolOutcome::ok(text, Route::Sandbox).with_meta(serde_json::json!({
        "exit": r.code,
        "dur_ms": r.dur_ms,
    }));
    if r.ok() {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}

/// 环境缺什么就补一句。
fn extra_hint(app: &App) -> String {
    let has_pass = !app.vault.get(crate::core::security::vault::BUCKET_SSH, "ssh_pass").is_empty();
    let h = ssh::hint(has_pass);
    if h.is_empty() {
        String::new()
    } else {
        format!("\n（{h}）")
    }
}
