//! 联网工具：搜索、直发 HTTP、浏览器、12306。
//!
//! 四个工具的共同点：**都会有网络失败**。所以每个都做两件事：
//! 1. 明确告诉模型失败原因（没 key / 被限流 / URL 不合法），别让它重试同一个动作；
//! 2. 输出统一截断（`govern` 还会再处理一次），避免一页 HTML 把上下文占满。
//!
//! 密钥从凭据库读，不从配置读：配置文件会被导出、截图、贴到聊天里。

use serde_json::Value;

use super::ToolOutcome;
use crate::core::base::{jsonx, text};
use crate::core::feature::app::App;
use crate::core::feature::config::schema::Config;
use crate::core::feature::model::session::Route;
use crate::core::security::vault::BUCKET_API;
use crate::net::{browser, http, rail, search};

fn s(v: &Value, k: &str) -> String {
    jsonx::get_str(v, &[k], "")
}

fn i(v: &Value, k: &str) -> i64 {
    jsonx::get_i64(v, &[k], 0)
}

fn b(v: &Value, k: &str) -> bool {
    jsonx::get_bool(v, &[k], false)
}

/// 凭据库里的键名。集中在这里，改名只改一处。
const KEY_TAVILY: &str = "search:tavily";
const KEY_GOOGLE: &str = "search:google";

/// `web_search`。
pub async fn search(app: &App, args: &Value) -> ToolOutcome {
    let cfg = app.cfg();
    if !cfg.tool.allow_network {
        return ToolOutcome::err("联网工具被关掉了（设置 → 工具 → 允许联网）", Route::Sandbox);
    }
    let q = s(args, "query");
    if q.trim().is_empty() {
        // 兼容模型把搜索词写在 q / text / keyword 里。
        let alt = jsonx::get_str(args, &["q", "text", "keyword"], "");
        if alt.trim().is_empty() {
            return ToolOutcome::err("web_search 需要 query", Route::Sandbox);
        }
        return run_search(app, &cfg, &alt, args).await;
    }
    run_search(app, &cfg, &q, args).await
}

async fn run_search(app: &App, cfg: &Config, query: &str, args: &Value) -> ToolOutcome {
    let keys = search::Keys {
        tavily: app.vault.get(BUCKET_API, KEY_TAVILY),
        google: app.vault.get(BUCKET_API, KEY_GOOGLE),
    };
    let opts = search::Opts {
        max: {
            let n = i(args, "max");
            if n > 0 {
                n as usize
            } else {
                cfg.search.max_results
            }
        },
        topic: {
            let t = s(args, "topic");
            if t.is_empty() { "general".into() } else { t }
        },
        days: i(args, "days"),
        depth: {
            let d = s(args, "depth");
            if d.is_empty() { "basic".into() } else { d }
        },
    };
    let r = search::search(cfg, &keys, query, &opts).await;
    let out = ToolOutcome::ok(r.text, Route::Sandbox).with_meta(serde_json::json!({
        "backend": r.backend,
        "query": query,
    }));
    if r.ok {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}

/// `http_fetch`。
pub async fn http(app: &App, args: &Value) -> ToolOutcome {
    let cfg = app.cfg();
    if !cfg.tool.allow_network {
        return ToolOutcome::err("联网工具被关掉了（设置 → 工具 → 允许联网）", Route::Sandbox);
    }
    let url = s(args, "url");
    if url.trim().is_empty() {
        return ToolOutcome::err("http_fetch 需要 url", Route::Sandbox);
    }
    // download 模式：存到本地。
    if b(args, "download") || s(args, "action") == "download" {
        let to = s(args, "to");
        let (ok, msg) = http::download(&url, &to).await;
        return if ok {
            ToolOutcome::ok(msg, Route::Sandbox)
        } else {
            ToolOutcome::err(msg, Route::Sandbox)
        };
    }

    let method = {
        let m = s(args, "method");
        if m.trim().is_empty() { "GET".into() } else { m }
    };
    let headers = s(args, "headers");
    let body = jsonx::get_str(args, &["body", "data"], "");
    let (ok, msg) = http::fetch(&method, &url, &headers, &body).await;
    let out = ToolOutcome::ok(msg, Route::Sandbox);
    if ok {
        out
    } else {
        ToolOutcome { ok: false, ..out }
    }
}

/// `browser`。
pub async fn browse(app: &App, args: &Value) -> ToolOutcome {
    let cfg = app.cfg();
    if !cfg.tool.allow_network {
        return ToolOutcome::err("联网工具被关掉了（设置 → 工具 → 允许联网）", Route::Sandbox);
    }
    let action = {
        let a = s(args, "action");
        if a.is_empty() { "open".into() } else { a }
    };
    let limit = {
        let n = i(args, "limit");
        if n > 0 {
            n as usize
        } else {
            6000
        }
    };
    let out = browser::run(
        &action,
        &s(args, "url"),
        &jsonx::get_str(args, &["text", "query", "find"], ""),
        &s(args, "target"),
        &s(args, "script"),
        limit,
    )
    .await;
    // 浏览器的失败也是一段可读文本，靠前缀判断。
    let bad = ["失败", "不合法", "不支持的 action", "需要渲染引擎", "请求失败", "还没有打开过页面", "HTTP "]
        .iter()
        .any(|h| out.starts_with(h) || out.contains(h));
    let r = ToolOutcome::ok(out, Route::Sandbox);
    if bad {
        ToolOutcome { ok: false, ..r }
    } else {
        r
    }
}

/// `rail_query`。
pub async fn rail(app: &App, args: &Value) -> ToolOutcome {
    let cfg = app.cfg();
    if !cfg.tool.allow_network {
        return ToolOutcome::err("联网工具被关掉了（设置 → 工具 → 允许联网）", Route::Sandbox);
    }
    let action = {
        let a = s(args, "action");
        if a.is_empty() { "tickets".into() } else { a }
    };
    let limit = {
        let n = i(args, "limit");
        if n > 0 { n as usize } else { 20 }
    };
    let out = match action.as_str() {
        "transfer" => {
            rail::transfer(&s(args, "from"), &s(args, "to"), &s(args, "date"), &s(args, "middle"), limit)
                .await
        }
        "route" => {
            rail::route(&s(args, "train_no"), &s(args, "from"), &s(args, "to"), &s(args, "date")).await
        }
        "suggest" => rail::suggest(&s(args, "from"), &s(args, "to"), &s(args, "date")).await,
        _ => {
            let ty = s(args, "type");
            let sort = s(args, "sort");
            rail::tickets(
                &s(args, "from"),
                &s(args, "to"),
                &s(args, "date"),
                if ty.is_empty() { None } else { Some(ty.as_str()) },
                if sort.is_empty() { None } else { Some(sort.as_str()) },
                limit,
            )
            .await
        }
    };
    // 12306 的失败都以这几句开头。
    let bad = [
        "查询失败",
        "没查到",
        "日期格式不对",
        "中转要指定",
        "站名是空的",
        "找不到车站",
        "出发站和到达站",
        "查经停要",
    ];
    let ok = !bad.iter().any(|h| out.starts_with(h));
    let r = ToolOutcome::ok(text::truncate(&out, 20_000), Route::Sandbox);
    if ok {
        r
    } else {
        ToolOutcome { ok: false, ..r }
    }
}
