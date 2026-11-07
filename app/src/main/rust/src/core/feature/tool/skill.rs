//! 技能与记忆类工具：`memory_save` / `memory_search` / `skill_read` / `js`，以及技能包函数。
//!
//! ## 技能包函数为什么走一个"兜底分支"
//!
//! 技能包是运行时装进去的，它们的工具名不可能写进 `registry` 的常量表。
//! `dispatch` 里所以有一个 `n => call_pack(n)`：名字对不上任何内置工具时，
//! 到这里查一次技能包。查不到才真正算"未知工具"。
//!
//! ## 命令类函数的执行纪律
//!
//! 逐行执行、逐行判错。第一行失败就停——继续跑后面的命令只会在错误的前提下
//! 做更多错事（比如 `mkdir` 失败还继续 `cp`）。

use std::collections::BTreeMap;

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::{jsonx, text};
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::exec::shell;
use crate::net::http;

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

fn i(v: &Value, k: &str) -> i64 {
    jsonx::get_i64(v, &[k], 0)
}

/// `memory_save`。
pub fn memory_save(app: &App, args: &Value) -> ToolOutcome {
    let t = jsonx::get_str(args, &["text", "content", "memory"], "");
    if t.trim().is_empty() {
        return ToolOutcome::err("memory_save 需要 text", Route::Sandbox);
    }
    let max = app.cfg().memory.max_items;
    let sid = app.session_id();
    let added = {
        let mut m = app.memory.write().unwrap_or_else(|e| e.into_inner());
        m.add(&app.db, &t, sid.as_deref(), max)
    };
    match added {
        Ok(true) => ToolOutcome::ok(format!("已记住（{} 字）", t.chars().count()), Route::Sandbox),
        Ok(false) => ToolOutcome::ok("这条已经在记忆里了，没有重复添加", Route::Sandbox),
        Err(e) => ToolOutcome::err(format!("写入记忆失败：{e}"), Route::Sandbox),
    }
}

/// `memory_search`。
pub fn memory_search(app: &App, args: &Value) -> ToolOutcome {
    let q = jsonx::get_str(args, &["query", "text", "q"], "");
    if q.trim().is_empty() {
        return ToolOutcome::err("memory_search 需要 query", Route::Sandbox);
    }
    let cfg = app.cfg();
    let top = {
        let n = i(args, "top");
        if n > 0 {
            n as usize
        } else {
            cfg.memory.recall_top
        }
    };
    // 搜索时不设分数门槛：用户明确要找，宁多勿少。
    let rows = {
        let m = app.memory.read().unwrap_or_else(|e| e.into_inner());
        m.recall(&q, top.clamp(1, 50), 0.0).into_iter().cloned().collect::<Vec<_>>()
    };
    if rows.is_empty() {
        return ToolOutcome::ok("记忆里没有相关条目", Route::Sandbox);
    }
    let mut out = format!("命中 {} 条：\n", rows.len());
    for r in &rows {
        out.push_str(&format!(
            "- [{}] {}\n",
            crate::core::base::clock::fmt_minute(r.at),
            text::one_line(&r.text)
        ));
    }
    ToolOutcome::ok(out.trim_end().to_string(), Route::Sandbox)
}

/// `skill_read`：拿技能全文。
pub fn read(app: &App, args: &Value) -> ToolOutcome {
    let key = jsonx::get_str(args, &["name", "skill", "id"], "");
    if key.trim().is_empty() {
        return ToolOutcome::err("skill_read 需要 name", Route::Sandbox);
    }

    // 先找正文型技能。
    {
        let sk = app.skills.read().unwrap_or_else(|e| e.into_inner());
        if let Some(s) = sk.get(&key) {
            return ToolOutcome::ok(s.full(), Route::Sandbox);
        }
        let hits = sk.find(&key);
        if hits.len() == 1 {
            return ToolOutcome::ok(hits[0].full(), Route::Sandbox);
        }
        if hits.len() > 1 {
            let names: Vec<String> = hits.iter().map(|x| x.name.clone()).collect();
            return ToolOutcome::ok(
                format!("「{key}」匹配到多个技能，请用完整名字再调一次：\n- {}", names.join("\n- ")),
                Route::Sandbox,
            );
        }
    }

    // 再找技能包的说明。
    {
        let packs = app.packs.read().unwrap_or_else(|e| e.into_inner());
        if let Some(p) = packs.pack(&key) {
            let mut out = format!("# {}（{}）\n\n", p.name, p.id);
            if !p.desc.trim().is_empty() {
                out.push_str(p.desc.trim());
                out.push_str("\n\n");
            }
            if !p.readme.trim().is_empty() {
                out.push_str(&p.readme);
                out.push('\n');
            }
            if !p.funcs.is_empty() {
                out.push_str("\n## 函数\n");
                for f in &p.funcs {
                    out.push_str(&format!(
                        "- {}({})：{}{}\n",
                        f.name,
                        f.param_names().join(", "),
                        f.desc,
                        if f.bad.is_empty() { String::new() } else { format!(" ←不可用：{}", f.bad) }
                    ));
                }
            }
            return ToolOutcome::ok(out.trim_end().to_string(), Route::Sandbox);
        }
    }

    // 最后：可能给的是函数名。
    {
        let packs = app.packs.read().unwrap_or_else(|e| e.into_inner());
        if let Some(f) = packs.find(&key) {
            return ToolOutcome::ok(
                format!("{} 属于技能包 {}：{}\n参数：{}", f.name, f.skill, f.desc, f.param_names().join(", ")),
                Route::Sandbox,
            );
        }
    }

    ToolOutcome::err(
        format!("没有叫「{key}」的技能。可以先让模型看系统提示里的技能清单，或检查技能目录。"),
        Route::Sandbox,
    )
}

/// `js`：跑一段脚本。
pub async fn js(args: &Value) -> ToolOutcome {
    let code = jsonx::get_str(args, &["script", "code", "js"], "");
    if code.trim().is_empty() {
        return ToolOutcome::err("js 需要 script", Route::Sandbox);
    }
    let ms = {
        let t = i(args, "timeout");
        if t > 0 { t as u64 } else { 15_000 }
    };
    let r = crate::exec::js::run(&code, ms).await;
    let out = ToolOutcome::ok(r.text, Route::Sandbox)
        .with_meta(serde_json::json!({ "engine": r.engine }));
    if r.ok {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}

/// 技能包函数（`dispatch` 的兜底分支）。
pub async fn call_pack(app: &App, name: &str, args: &Value) -> ToolOutcome {
    let f = {
        let packs = app.packs.read().unwrap_or_else(|e| e.into_inner());
        packs.find(name).cloned()
    };
    let Some(f) = f else {
        return ToolOutcome::err(
            format!("没有这个工具：{name}（可能是技能包被删了，或名字打错）"),
            Route::Sandbox,
        );
    };

    // 参数：只认声明过的，多余的不传（多传说明模型理解错了，不该默默忽略）。
    let mut values = BTreeMap::new();
    for k in f.params.keys() {
        let v = s(args, k);
        if v.is_empty() {
            return ToolOutcome::err(format!("缺少参数 {k}（{}）", f.name), Route::Sandbox);
        }
        values.insert(k.clone(), v);
    }

    let rendered = {
        let vault = &app.vault;
        match crate::core::feature::skill::render(&f.body, &f.params, &values, vault) {
            Ok(t) => t,
            Err(e) => return ToolOutcome::err(format!("技能模板渲染失败：{}", e.user_text()), Route::Sandbox),
        }
    };

    if f.kind == "http" {
        let (ok, msg) = http::raw_message(&rendered).await;
        let out = ToolOutcome::ok(msg, Route::Sandbox);
        return if ok { out } else { ToolOutcome { ok: false, ..out } };
    }

    // cmd：逐行执行，第一行失败就停。
    let mut out = String::new();
    let mut failed = None;
    for line in rendered.lines().map(|l| l.trim()).filter(|l| !l.is_empty()) {
        let r = shell::run(line, None, crate::core::feature::skill::pack::timeout_of(&f.kind, &app.cfg()), None).await;
        if !r.out.trim().is_empty() {
            out.push_str(r.out.trim_end());
            out.push('\n');
        }
        if !r.ok() {
            failed = Some(format!("{}（{}）", line, r.status_line()));
            break;
        }
    }
    let body = if out.trim().is_empty() { "（没有输出）".to_string() } else { out.trim_end().to_string() };
    match failed {
        Some(cmd) => ToolOutcome::err(
            format!("{body}\n\n失败在：{cmd}"),
            Route::Sandbox,
        ),
        None => ToolOutcome::ok(body, Route::Sandbox).with_meta(serde_json::json!({ "skill": f.skill })),
    }
}
