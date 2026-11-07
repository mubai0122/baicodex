//! 设备与屏幕工具：`screen` / `clipboard` / `open_link` / `notification` / `apk`。
//!
//! `screen` 是唯一"有状态"的工具：`start` 开胶囊、`quiet` 收胶囊，中间的动作
//! 都作用在当前界面。状态放在 [crate::exec::capsule]，这里只做参数翻译。
//!
//! ## 为什么动作名要兜底
//!
//! 模型会写 `click`、`tap`、`长按`、`screenshot`、`shot`……全都认，映射到
//! 同一个实现。教模型背一个精确的词表，不如让工具宽容一点。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::jsonx;
use crate::core::feature::app::App;
use crate::core::feature::model::session::Route;
use crate::exec::{a11y, apk, capsule, device};

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

fn i(v: &Value, k: &str) -> i64 {
    jsonx::get_i64(v, &[k], 0)
}

fn b(v: &Value, k: &str) -> bool {
    jsonx::get_bool(v, &[k], false)
}

/// 失败前缀，用来判断是否该把 `ok` 置 false。
const FAIL_HINTS: &[&str] = &["失败", "不支持", "拿不到", "没有匹配", "不认识的", "需要", "不合法", "没找到"];

fn verdict(text: String, route: Route) -> ToolOutcome {
    let bad = FAIL_HINTS.iter().any(|h| text.starts_with(h));
    let out = ToolOutcome::ok(text, route);
    if bad {
        ToolOutcome { ok: false, ..out }
    } else {
        out
    }
}

/// `screen`。
pub async fn screen(app: &App, args: &Value) -> ToolOutcome {
    let act = {
        let a = s(args, "action").to_lowercase();
        if a.is_empty() { "see".into() } else { a }
    };

    // 胶囊：不需要无障碍，先处理。
    if matches!(act.as_str(), "start" | "begin" | "pin") {
        let given = jsonx::get_str(args, &["text", "note"], "");
        let t = if given.trim().is_empty() { "正在操作屏幕".to_string() } else { given };
        capsule::begin(&t);
        return ToolOutcome::ok(
            format!("胶囊已常驻：「{t}」。全部做完再调 screen action=quiet 收起。"),
            Route::Sandbox,
        );
    }
    if matches!(act.as_str(), "quiet" | "stop" | "end" | "unpin") {
        capsule::end();
        return ToolOutcome::ok("胶囊已收起。", Route::Sandbox);
    }

    let a = a11y::A11y::detect().await;
    if !a.enabled && !app.router.perms().a11y && !crate::exec::shell::has("uiautomator") {
        return ToolOutcome::err(
            "无障碍服务没开，屏幕操作跑不通。可以改用 exec_shell（uiautomator dump / input tap / screencap），但那需要 Shizuku 或 Root。",
            Route::Sandbox,
        );
    }

    let text = match act.as_str() {
        "see" => format!("{}\n{}", a.dump(b(args, "all")).await, a.screenshot().await),
        "tree" | "dump" | "nodes" => a.dump(b(args, "all")).await,
        "shot" | "screenshot" | "cap" => a.screenshot().await,
        "tap" | "click" => {
            // 给了 text 就按文字点，否则按坐标。
            let t = jsonx::get_str(args, &["text", "label", "target"], "");
            if !t.trim().is_empty() {
                a.tap_text(&t).await
            } else {
                a.tap(i(args, "x") as i32, i(args, "y") as i32).await
            }
        }
        "long" | "longpress" | "press" => {
            a.long_press(i(args, "x") as i32, i(args, "y") as i32, i(args, "dur").max(800) as u64)
                .await
        }
        "swipe" | "scroll" | "drag" => {
            a.swipe(
                i(args, "x") as i32,
                i(args, "y") as i32,
                i(args, "x2") as i32,
                i(args, "y2") as i32,
                i(args, "dur").max(300) as u64,
            )
            .await
        }
        "text" | "input" | "type" => a.input_text(&jsonx::get_str(args, &["text", "content"], "")).await,
        "key" | "back" | "home" => {
            let k = {
                let v = s(args, "key");
                if v.is_empty() { act.clone() } else { v }
            };
            a.key(&k).await
        }
        "launch" | "open" | "start_app" => a.launch(&jsonx::get_str(args, &["app", "package"], "")).await,
        "apps" | "list" | "packages" => a.apps(&jsonx::get_str(args, &["app", "filter"], "")).await,
        "wait" | "idle" => a.wait_idle(i(args, "dur").max(2000) as u64).await,
        other => format!(
            "不支持的 action：{other}（可用 see / tree / shot / tap / long / swipe / text / key / launch / apps / wait / start / quiet）"
        ),
    };
    verdict(text, Route::Sandbox)
}

/// `clipboard`。
pub async fn clipboard(args: &Value) -> ToolOutcome {
    let act = {
        let a = s(args, "action").to_lowercase();
        if a.is_empty() {
            // 没写 action：给了 text 就是写，否则是读。
            if jsonx::get_str(args, &["text", "content"], "").is_empty() {
                "get".into()
            } else {
                "set".into()
            }
        } else {
            a
        }
    };
    match act.as_str() {
        "get" | "read" => verdict(device::clip_get().await, Route::Sandbox),
        "set" | "write" | "copy" => {
            let t = jsonx::get_str(args, &["text", "content"], "");
            if t.is_empty() {
                return ToolOutcome::err("clipboard set 需要 text", Route::Sandbox);
            }
            verdict(device::clip_set(&t).await, Route::Sandbox)
        }
        "share" => {
            let t = jsonx::get_str(args, &["text", "content"], "");
            if t.is_empty() {
                return ToolOutcome::err("clipboard share 需要 text", Route::Sandbox);
            }
            verdict(device::share_text(&t).await, Route::Sandbox)
        }
        other => ToolOutcome::err(format!("不支持的 action：{other}（get / set / share）"), Route::Sandbox),
    }
}

/// `open_link`。
pub async fn open(args: &Value) -> ToolOutcome {
    let url = jsonx::get_str(args, &["url", "link"], "");
    if url.trim().is_empty() {
        return ToolOutcome::err("open_link 需要 url", Route::Sandbox);
    }
    verdict(device::open_url(&url).await, Route::Sandbox)
}

/// `notification`。
pub async fn notify(args: &Value) -> ToolOutcome {
    let act = {
        let a = s(args, "action").to_lowercase();
        if a.is_empty() { "send".into() } else { a }
    };
    match act.as_str() {
        "send" | "post" => {
            let title = s(args, "title");
            let body = jsonx::get_str(args, &["text", "body", "content"], "");
            if body.trim().is_empty() {
                return ToolOutcome::err("notification 需要 text", Route::Sandbox);
            }
            verdict(device::notify(&title, &body).await, Route::Sandbox)
        }
        "list" | "read" => {
            let n = i(args, "limit");
            let limit = if n > 0 { n as usize } else { 30 };
            verdict(device::notifications(limit, &s(args, "app")), Route::Sandbox)
        }
        "clear" => {
            if device::clear_notifications() {
                ToolOutcome::ok("已清空通知记录", Route::Sandbox)
            } else {
                ToolOutcome::ok("没有通知记录可清", Route::Sandbox)
            }
        }
        other => ToolOutcome::err(format!("不支持的 action：{other}（send / list / clear）"), Route::Sandbox),
    }
}

/// `apk`。
pub fn apk(args: &Value) -> ToolOutcome {
    let path = s(args, "path");
    if path.trim().is_empty() {
        return ToolOutcome::err("apk 需要 path", Route::Sandbox);
    }
    let action = {
        let a = s(args, "action");
        if a.is_empty() { "info".into() } else { a }
    };
    let limit = {
        let n = i(args, "limit");
        if n > 0 { n as usize } else { 100 }
    };
    let r = apk::run(&action, &path, &jsonx::get_str(args, &["keyword", "text", "q"], ""), limit);
    let out = ToolOutcome::ok(r.text, Route::Sandbox);
    if r.ok {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}
