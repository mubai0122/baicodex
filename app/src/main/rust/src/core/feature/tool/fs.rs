//! 文件类工具。
//!
//! 一层薄包装：从参数里取路径，交给 [FileOps]（它负责路由、沙箱、编码、截断）。
//! 这里**不写文件逻辑**——那样的逻辑只能有一份，否则"write_file 能过、shell 的 echo 不能过"
//! 这类裂缝迟早出现。

use serde_json::Value;

use super::{ToolOutcome};
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::exec::file_ops::{FileOps, Out};

/// 构造一个 FileOps：拿当前沙箱快照 + 配置里的读取上限。
pub fn ops(app: &App) -> FileOps<'_> {
    let sandbox = app
        .sandbox
        .read()
        .map(|s| s.clone())
        .unwrap_or_else(|e| e.into_inner().clone());
    let max = app.cfg().tool.max_read_bytes;
    FileOps::new(&app.router, sandbox, max)
}

fn out(o: Out) -> ToolOutcome {
    ToolOutcome { ok: o.ok, text: o.text, route: o.route, meta: None }
}

fn s(v: &Value, k: &str) -> String {
    crate::core::base::jsonx::get_str(v, &[k], "")
}

fn i(v: &Value, k: &str) -> i64 {
    crate::core::base::jsonx::get_i64(v, &[k], 0)
}

fn b(v: &Value, k: &str) -> bool {
    crate::core::base::jsonx::get_bool(v, &[k], false)
}

pub fn read(app: &App, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    if path.trim().is_empty() {
        return ToolOutcome::err("read_file 需要 path 参数", Route::Sandbox);
    }
    out(ops(app).read(&path))
}

pub fn write(app: &App, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    if path.trim().is_empty() {
        return ToolOutcome::err("write_file 需要 path 参数", Route::Sandbox);
    }
    let content = crate::core::base::jsonx::get_str(args, &["content", "text", "data"], "");
    out(ops(app).write(&path, &content))
}

pub fn edit(app: &App, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    if path.trim().is_empty() {
        return ToolOutcome::err("edit_file 需要 path 参数", Route::Sandbox);
    }
    let old = crate::core::base::jsonx::get_str(args, &["old", "old_string", "from"], "");
    let new = crate::core::base::jsonx::get_str(args, &["new", "new_string", "to"], "");
    if old.trim().is_empty() {
        return ToolOutcome::err("edit_file 需要 old（要替换的原文）", Route::Sandbox);
    }
    out(ops(app).edit(&path, &old, &new))
}

pub fn mkdir(app: &App, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    out(ops(app).mkdir(&path))
}

pub fn delete(app: &App, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    if path.trim().is_empty() {
        return ToolOutcome::err("delete_file 需要 path 参数", Route::Sandbox);
    }
    out(ops(app).delete(&path))
}

pub fn list(app: &App, args: &Value) -> ToolOutcome {
    let path = {
        let p = s(args, "path");
        if p.trim().is_empty() { ".".to_string() } else { p }
    };
    let recurse = b(args, "recurse");
    let limit = i(args, "limit").clamp(1, 2000) as usize;
    out(ops(app).list(&path, recurse, limit))
}

pub fn info(app: &App, args: &Value) -> ToolOutcome {
    out(ops(app).info(&s(args, "path")))
}

pub fn mv(app: &App, args: &Value) -> ToolOutcome {
    let from = s(args, "from");
    let to = s(args, "to");
    if from.trim().is_empty() || to.trim().is_empty() {
        return ToolOutcome::err("move_path 需要 from 与 to", Route::Sandbox);
    }
    out(ops(app).mv(&from, &to, b(args, "copy")))
}

pub fn zip(app: &App, args: &Value) -> ToolOutcome {
    let act = {
        let a = s(args, "action").to_lowercase();
        if a.is_empty() { "zip".to_string() } else { a }
    };
    let fo = ops(app);
    if act == "unzip" {
        let z = s(args, "path");
        if z.trim().is_empty() {
            return ToolOutcome::err("unzip 需要 path（zip 文件路径）", Route::Sandbox);
        }
        let to = {
            let t = s(args, "to");
            if t.trim().is_empty() {
                format!("{}_unzip", z.trim_end_matches(".zip").trim_end_matches(".ZIP"))
            } else {
                t
            }
        };
        return out(fo.unzip(&z, &to));
    }
    let paths = s(args, "paths");
    if paths.trim().is_empty() {
        return ToolOutcome::err("zip 需要 paths（要打包的路径，逗号分隔）", Route::Sandbox);
    }
    let target = {
        let o = s(args, "out");
        if o.trim().is_empty() {
            format!("{}.zip", paths.split(',').next().unwrap_or("").trim().trim_end_matches('/'))
        } else {
            o
        }
    };
    out(fo.zip(&paths, &target))
}

pub fn grep(app: &App, args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    let pattern = s(args, "pattern");
    if pattern.trim().is_empty() {
        return ToolOutcome::err("grep_files 需要 pattern", Route::Sandbox);
    }
    let ext = s(args, "ext");
    let max = i(args, "max").max(60) as usize;
    let ignore_case = crate::core::base::jsonx::get_bool(args, &["ignore_case"], true);
    out(ops(app).grep(&path, &pattern, &ext, max, ignore_case))
}
