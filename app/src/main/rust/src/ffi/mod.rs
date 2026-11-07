//! 跨语言边界：所有前端（Kotlin / C++ / 其它进程）只经过这里。
//!
//! ## 契约
//!
//! 输入一个 JSON 字符串，输出一个 JSON 字符串。**永远返回合法 JSON**，包括失败：
//!
//! ```json
//! {"ok":true,"data":{...}}
//! {"ok":false,"error":{"code":"invalid","message":"..."}}
//! ```
//!
//! 三条理由：
//!
//! 1. Kotlin 侧用 `JSONObject` 解析，比拆字符串可靠；
//! 2. 错误码是稳定的枚举字符串，前端可以分支处理（比如 `auth` 跳设置页）；
//! 3. 不抛异常、不裸传指针，C++ 侧只需要 `strlen` + `free`。
//!
//! ## 线程模型
//!
//! 内核自带一个 tokio 运行时（多线程）。`call` 是**阻塞**的：它在运行时上
//! `block_on`。前端要在自己的线程里调，别在主线程上调——Android 主线程阻塞
//! 会 ANR。Kotlin 侧的 `NativeBridge` 已经把调用放到 IO 线程池。
//!
//! 事件（流式增量、工具开始结束）不经过 `call` 返回，而是走回调：前端注册一个
//! `extern "C" fn(*const c_char)`，内核把每个事件作为一行 JSON 推过去。

pub mod jni;
pub mod spec;

use std::ffi::{c_char, c_int, CStr, CString};
use std::sync::{Mutex, OnceLock};

use serde_json::{json, Value};

use crate::core::base::error::{BError, BResult, ErrorCode};
use crate::core::feature::app::event::AppEvent;

/// 全局内核。
///
/// 这里刻意用 `OnceLock<Kernel>` 而不是 `Mutex<Option<Kernel>>`：
///
/// - 前端一次只需一个内核（数据根是进程级的，两个内核会抢同一个 sqlite 文件）；
/// - 用锁的话，`block_on` 期间锁一直被持有，事件回调线程要是再进 `call` 就死锁；
/// - `OnceLock` 拿到的是 `&'static Kernel`，await/block_on 全程无锁。
///
/// 代价是"关掉再启动"做不到——`shutdown` 只关会话与后台任务，进程级资源留到退出。
/// 这对 App 没影响（本来就只有一次启动），测试里换个数据目录请新建进程。
static KERNEL: OnceLock<Kernel> = OnceLock::new();

/// 测试期用的进程级串行锁。
///
/// `KERNEL` 是 `OnceLock`，一旦启动到进程结束都不会清空。任何会碰内核的测试
/// （boot / shutdown / 遍历 op 表）都必须先拿这把锁：否则并发时一个用例可能
/// 用空 base 抢先占住单例，另一个用例跑到一半被 `shutdown` 停掉——表现为
/// 单独跑全过、一起跑随机挂。guard 模块里也是同一套做法。
#[cfg(test)]
pub fn test_lock() -> std::sync::MutexGuard<'static, ()> {
    static M: std::sync::Mutex<()> = std::sync::Mutex::new(());
    M.lock().unwrap_or_else(|e| e.into_inner())
}

/// 内核 + 它自己的运行时。
pub struct Kernel {
    pub facade: crate::app::Facade,
    pub rt: tokio::runtime::Runtime,
}

impl std::fmt::Debug for Kernel {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Kernel").finish_non_exhaustive()
    }
}

/// 启动。重复启动是幂等的（直接复用已启动的那个）。
pub fn boot(base: &str) -> BResult<()> {
    if KERNEL.get().is_some() {
        return Ok(());
    }
    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .thread_name("baicodex")
        .build()
        .map_err(|e| BError::internal(format!("建运行时失败：{e}")))?;
    let facade = crate::app::Facade::boot(base)?;
    // set 失败说明另一个线程抢先启动了：它已经装好，我们把自己这份丢掉即可。
    let _ = KERNEL.set(Kernel { facade, rt });
    Ok(())
}

/// 停止当前生成、取消订阅、落盘。进程级资源（运行时、sqlite 连接）不动——
/// 动它们要保证"没有别的线程正在用"，而前端随时可能再调一次 `call`。
pub fn shutdown() {
    if let Some(k) = KERNEL.get() {
        k.facade.stop();
        k.facade.shutdown();
    }
}

/// 取内核。没启动就报错，而不是隐式启动——隐式启动会让"数据目录传错了"
/// 变成一个很难查的问题。
fn kernel() -> BResult<&'static Kernel> {
    KERNEL.get().ok_or_else(|| BError::new(ErrorCode::Ffi, "内核还没启动（先调 boot）"))
}

/// 在内核上做一件事，结果序列化成 JSON。
///
/// 闭包返回 `BResult<T>`，`T` 只要可序列化；`()` 会变成 `null`，省掉一堆
/// `json!(null)` 样板。
fn with<T: serde::Serialize>(f: impl FnOnce(&'static Kernel) -> BResult<T>) -> BResult<Value> {
    let k = kernel()?;
    let v = f(k)?;
    Ok(serde_json::to_value(v).unwrap_or(Value::Null))
}

/// 主入口：JSON 进，JSON 出。
pub fn call_json(req: &str) -> String {
    let v = match serde_json::from_str::<Value>(req) {
        Ok(v) => v,
        Err(e) => return err_json(BError::new(ErrorCode::Ffi, format!("请求不是合法 JSON：{e}"))),
    };
    serde_json::to_string(&call_value(&v)).unwrap_or_else(|_| err_json(BError::internal("序列化失败")))
}

/// 结构化的版本（JNI 直接复用）。
pub fn call_value(req: &Value) -> Value {
    let op = req.get("op").and_then(|x| x.as_str()).unwrap_or("").trim();
    let args = req.get("args").cloned().unwrap_or_else(|| json!({}));
    match dispatch(op, &args) {
        Ok(data) => json!({ "ok": true, "data": data }),
        Err(e) => json!({ "ok": false, "error": e.to_json() }),
    }
}

fn err_json(e: BError) -> String {
    json!({ "ok": false, "error": e.to_json() }).to_string()
}

/// 操作表。
///
/// 每个 op 都是"前端需要的一件事"，不是"内核的一个方法"。所以有 `send` 这种
/// 组合操作（存消息 + 跑循环 + 收尾），而没有 `set_busy` 这种实现细节。
///
/// 写法约定：能用 `Ok(with(|k| ...)?)` 的就不写 `match`，减少缩进层级。
fn dispatch(op: &str, args: &Value) -> BResult<Value> {
    let s = |k: &str| args.get(k).and_then(|x| x.as_str()).unwrap_or("").to_string();
    match op {
        // —— 生命周期 ——
        "boot" => {
            boot(&s("base"))?;
            Ok(json!({ "version": crate::VERSION, "base": crate::core::base::dirs::base().to_string_lossy() }))
        }
        "shutdown" => {
            shutdown();
            Ok(json!(null))
        }
        "version" => Ok(json!({
            "version": crate::VERSION,
            "arch": std::env::consts::ARCH,
            "os": std::env::consts::OS,
            "js": crate::exec::js::engine_name(),
        })),

        // —— 会话 ——
        "send" => with(|k| {
            let images = str_list(args, "images");
            let files = str_list(args, "files");
            let text = s("text");
            k.rt.block_on(k.facade.send(&text, images, files))
        }),
        "regenerate" => with(|k| k.rt.block_on(k.facade.regenerate())),
        "stop" => with(|k| {
            k.facade.stop();
            Ok(())
        }),
        "snapshot" => with(|k| Ok(k.facade.snapshot())),
        "messages" => with(|k| Ok(k.facade.messages())),
        "sessions" => with(|k| Ok(k.facade.sessions())),
        "new_session" => with(|k| k.facade.new_session()),
        "open_session" => with(|k| k.facade.open_session(&s("id"))),
        "delete_session" => with(|k| k.facade.delete_session(&s("id"))),
        "rename_session" => with(|k| k.facade.rename_session(&s("id"), &s("title"))),
        "search_sessions" => {
            with(|k| k.facade.app.db.search_session_ids(&s("q")))
        }

        // —— 配置 ——
        "config_get" => with(|k| Ok(k.facade.app.cfg())),
        "config_patch" => with(|k| {
            let patch = args.get("patch").cloned().unwrap_or_else(|| json!({}));
            let cfg = k.facade.app.update_cfg(|c| merge(c, &patch));
            let _ = k.facade.app.cfg.save();
            Ok(cfg)
        }),
        "config_save" => with(|k| k.facade.app.cfg.save()),
        "system_prompt" => with(|k| Ok(k.facade.app.cfg().agent.system_prompt)),
        "set_system_prompt" => with(|k| k.facade.set_system_prompt(&s("text"))),

        // —— 工具 ——
        "tools" => with(|k| Ok(k.facade.tools())),
        "tool_call" => with(|k| {
            let name = s("name");
            let a = args.get("args").cloned().unwrap_or_else(|| json!({}));
            let out = k
                .rt
                .block_on(crate::core::feature::tool::dispatch::execute(&k.facade.app, &name, &a));
            Ok(json!({ "ok": out.ok, "text": out.text, "route": out.route.as_str(), "meta": out.meta }))
        }),
        "confirm_tool" => with(|k| {
            let allow = args.get("allow").and_then(|x| x.as_bool()).unwrap_or(false);
            let permanent = args.get("permanent").and_then(|x| x.as_bool()).unwrap_or(false);
            k.facade.app.resolve_tool(allow, permanent);
            Ok(())
        }),
        "answer_ask" => with(|k| {
            k.facade.app.answer_ask(str_list(args, "answers"));
            Ok(())
        }),
        "answer_plan" => with(|k| {
            let v = match s("verdict").as_str() {
                "confirm" => crate::core::feature::app::PlanVerdict::Confirm,
                "revise" => crate::core::feature::app::PlanVerdict::Revise { note: s("note") },
                _ => crate::core::feature::app::PlanVerdict::Dismiss,
            };
            k.facade.app.answer_plan(v);
            Ok(())
        }),

        // —— 记忆 ——
        "memory_list" => with(|k| {
            let m = k.facade.app.memory.read().unwrap_or_else(|e| e.into_inner());
            Ok(json!(m.rows()))
        }),
        "memory_save" => with(|k| {
            let cfg = k.facade.app.cfg();
            let mut m = k.facade.app.memory.write().unwrap_or_else(|e| e.into_inner());
            m.add(&k.facade.app.db, &s("text"), None, cfg.memory.max_items)
        }),
        "memory_delete" => with(|k| {
            let id = num(args, "id");
            let mut m = k.facade.app.memory.write().unwrap_or_else(|e| e.into_inner());
            m.remove(&k.facade.app.db, id)
        }),
        "memory_search" => with(|k| {
            let q = s("q");
            let cfg = k.facade.app.cfg();
            let m = k.facade.app.memory.read().unwrap_or_else(|e| e.into_inner());
            let rows: Vec<Value> = m
                .recall(&q, cfg.memory.recall_top.max(1), 0.0)
                .into_iter()
                .map(|r| json!({ "id": r.id, "text": r.text, "at": r.at }))
                .collect();
            Ok(rows)
        }),

        // —— 技能 / MCP / 工作流 ——
        "skills" => with(|k| {
            let docs = k.facade.app.skills.read().map(|x| x.names()).unwrap_or_default();
            let packs: Vec<String> = k
                .facade
                .app
                .packs
                .read()
                .map(|x| x.packs.iter().map(|p| p.id.clone()).collect())
                .unwrap_or_default();
            Ok(json!({ "docs": docs, "packs": packs }))
        }),
        "mcp_refresh" => with(|k| {
            let id = s("id");
            k.rt.block_on(crate::core::feature::tool::mcp::refresh(&k.facade.app, &id))
                .map_err(BError::invalid)
        }),
        "flows" => with(|k| {
            let list: Vec<Value> = k
                .facade
                .app
                .flows
                .read()
                .map(|f| f.list.iter().map(|x| json!(x)).collect())
                .unwrap_or_default();
            Ok(list)
        }),

        // —— 用量 ——
        "usage" => with(|k| {
            let cfg = k.facade.app.cfg();
            Ok(json!(crate::core::feature::usage::report(&k.facade.app.db, &cfg)))
        }),

        // —— 环境 ——
        "workspace" => with(|k| {
            let cfg = k.facade.app.cfg();
            Ok(json!({
                "path": crate::exec::workspace::path(&cfg),
                "bound": crate::exec::workspace::bound(&cfg),
                "summary": crate::exec::workspace::summary(&cfg),
            }))
        }),
        "workspace_bind" => with(|k| {
            let mut cfg = k.facade.app.cfg();
            let p = crate::exec::workspace::bind(&mut cfg, &s("path")).map_err(BError::invalid)?;
            let path = p.clone();
            k.facade.app.update_cfg(|c| c.workspace.current = path);
            let _ = k.facade.app.cfg.save();
            Ok(p)
        }),
        "device" => Ok(json!({
            "os": crate::core::base::sys::os(),
            "android": crate::core::base::sys::is_android(),
            "cpus": crate::core::base::sys::cpus(),
            "line": crate::core::base::sys::device_line(),
            "battery": crate::core::base::sys::battery(),
        })),

        // —— 互联：adb 通道 ——
        // status 不自动下载，否则界面一打开就静默拉 12MB；装不装由用户点
        "adb_status" => with(|k| {
            k.rt.block_on(async {
                match crate::exec::adb::locate() {
                    None => Ok(json!({
                        "installed": false,
                        "platform": crate::exec::adb::platform(),
                    })),
                    Some(p) => {
                        let adb = crate::exec::adb::Adb::at(p);
                        let st = adb.status().await;
                        Ok(json!({ "installed": true, "status": st }))
                    }
                }
            })
        }),
        "adb_install" => with(|k| {
            k.rt.block_on(async {
                let p = crate::exec::adb::install().await.map_err(BError::invalid)?;
                let adb = crate::exec::adb::Adb::at(p);
                let st = adb.status().await;
                Ok(json!({ "installed": true, "status": st }))
            })
        }),
        "adb_connect" => with(|k| {
            let addr = s("addr");
            k.rt.block_on(async {
                let adb = crate::exec::adb::ensure().await.map_err(BError::invalid)?;
                let out = adb.connect(&addr).await.map_err(BError::invalid)?;
                Ok(json!({ "out": out, "devices": adb.devices().await.unwrap_or_default() }))
            })
        }),
        "adb_pair" => with(|k| {
            let addr = s("addr");
            let code = s("code");
            k.rt.block_on(async {
                let adb = crate::exec::adb::ensure().await.map_err(BError::invalid)?;
                let out = adb.pair(&addr, &code).await.map_err(BError::invalid)?;
                Ok(json!({ "out": out }))
            })
        }),
        "adb_forward" => with(|k| {
            let local = num(args, "local") as u16;
            let remote = num(args, "remote") as u16;
            k.rt.block_on(async {
                let adb = crate::exec::adb::ensure().await.map_err(BError::invalid)?;
                let out = adb.forward(local, remote).await.map_err(BError::invalid)?;
                Ok(json!({ "out": out, "local": local, "remote": remote }))
            })
        }),
        "adb_shell" => with(|k| {
            let cmd = s("cmd");
            k.rt.block_on(async {
                let adb = crate::exec::adb::ensure().await.map_err(BError::invalid)?;
                let out = adb.shell(&cmd).await.map_err(BError::invalid)?;
                Ok(json!({ "out": out }))
            })
        }),

        // —— 互联：内网直连通道 ——
        // 默认只放行 loopback；要让另一台机器连进来必须显式给 allow（IP 或 CIDR）。
        // token 每次启动重新生成，界面负责把它带过去；关掉只用于本机调试。
        "relay_start" => with(|k| {
            let port = num(args, "port");
            let require_token = args
                .get("require_token")
                .map_or(true, |v| !matches!(v, Value::Bool(false)) && v.as_str() != Some("false") && v.as_i64() != Some(0));
            let cfg = crate::net::relay::Cfg {
                port: if port > 0 { port as u16 } else { crate::net::relay::DEFAULT_PORT },
                require_token,
                allow: str_list(args, "allow"),
            };
            k.rt.block_on(async {
                let st = crate::net::relay::start(cfg).await.map_err(BError::invalid)?;
                Ok(crate::net::relay::as_json(&st))
            })
        }),
        "relay_stop" => {
            crate::net::relay::stop();
            Ok(crate::net::relay::as_json(&crate::net::relay::query()))
        }
        "relay_status" => Ok(crate::net::relay::as_json(&crate::net::relay::query())),
        "relay_allow" => Ok(crate::net::relay::as_json(&crate::net::relay::set_allow(str_list(args, "allow")))),

        "ensure_session" => with(|k| k.facade.app.ensure_session()),
        "pending" => with(|k| {
            let st = k.facade.app.lock_state();
            let kind = st.waiting_kind();
            let tool = st.pending.as_ref().map(|p| {
                json!({
                    "name": p.call.name,
                    "args": p.call.arguments,
                    "route": p.route.as_str(),
                    "high_risk": p.high_risk,
                })
            });
            let questions: Vec<Value> = st
                .ask
                .as_ref()
                .map(|a| {
                    a.questions
                        .iter()
                        .map(|q| {
                            json!({
                                "id": q.id,
                                "question": q.question,
                                "multi": q.multi,
                                "options": q.options.iter().map(|o| json!({"label": o.label, "desc": o.desc})).collect::<Vec<_>>(),
                            })
                        })
                        .collect()
                })
                .unwrap_or_default();
            let plan = st.plan.as_ref().map(|p| p.text.clone());
            Ok(json!({ "kind": kind, "tool": tool, "questions": questions, "plan": plan }))
        }),

        // —— 消息树与分支 ——
        "pin_session" => with(|k| {
            k.facade.app.db.set_pinned(&s("id"), flag(args, "on"))
        }),
        "archive_session" => with(|k| {
            k.facade.app.db.set_archived(&s("id"), flag(args, "on"))
        }),
        "star_message" => with(|k| {
            k.facade.app.db.set_starred(num(args, "id"), flag(args, "on"))
        }),
        "delete_message" => with(|k| {
            let sid = k.facade.app.session_id().unwrap_or_default();
            let n = k.facade.app.db.delete_message(&sid, num(args, "id"))?;
            k.facade.app.open_session(&sid)?;
            Ok(n)
        }),
        "edit_message" => with(|k| {
            k.facade.app.db.update_content(num(args, "id"), &s("text"))
        }),
        "session_stats" => with(|k| {
            let (msgs, tokens) = k.facade.app.db.stats(&s("id"))?;
            Ok(json!({ "messages": msgs, "tokens": tokens }))
        }),
        "set_session_persona" => with(|k| {
            k.facade.app.db.set_persona(&s("id"), &s("persona"))
        }),
        "set_session_workspace" => with(|k| {
            k.facade.app.db.set_workspace(&s("id"), &s("path"))
        }),
        "siblings" => with(|k| {
            let sid = k.facade.app.session_id().unwrap_or_default();
            k.facade.app.db.siblings(&sid, num(args, "id"))
        }),
        "switch_branch" => with(|k| {
            let sid = k.facade.app.session_id().unwrap_or_default();
            k.facade.app.db.switch_to(&sid, num(args, "id"))?;
            k.facade.app.open_session(&sid)
        }),

        // —— 收藏 / 导出 / 压缩 ——
        "starred" => with(|k| {
            let limit = num(args, "limit").max(1);
            let rows = k.facade.app.db.starred(limit)?;
            let list: Vec<Value> = rows
                .into_iter()
                .map(|(m, sid, title)| json!({ "message": m, "session": sid, "title": title }))
                .collect();
            Ok(list)
        }),
        "usage_session" => with(|k| {
            let (p, c) = k.facade.app.db.usage_session(&s("id"))?;
            Ok(json!({ "prompt": p, "completion": c, "total": p + c }))
        }),
        "compact" => with(|k| {
            let cfg = k.facade.app.cfg();
            k.rt.block_on(crate::core::feature::agent::turn::compact_now(&k.facade.app, &cfg.context))
        }),
        "export_md" => with(|k| Ok(crate::core::feature::agent::export_markdown(&k.facade.app))),
        "clear_current" => with(|k| {
            let sid = k.facade.app.session_id().unwrap_or_default();
            k.facade.app.db.clear_messages(&sid)?;
            k.facade.app.open_session(&sid)
        }),
        "edit_resend" => with(|k| {
            let sid = k.facade.app.session_id().unwrap_or_default();
            k.facade.app.db.delete_message(&sid, num(args, "id"))?;
            k.facade.app.open_session(&sid)?;
            let text = s("text");
            let out = k.rt.block_on(crate::core::feature::agent::send(
                &k.facade.app,
                &text,
                str_list(args, "images"),
                str_list(args, "files"),
            ))?;
            Ok(json!({ "text": out.text, "rounds": out.rounds, "stopped": out.stopped }))
        }),

        // —— 人设 ——
        "personas" => with(|k| {
            let p = k.facade.app.personas.read().unwrap_or_else(|e| e.into_inner());
            Ok(json!({
                "list": p.list,
                "current": k.facade.app.cfg().agent.persona,
                "opener": p.opener(&k.facade.app.cfg().agent.persona),
            }))
        }),
        "persona_save" => with(|k| {
            let mut patch: crate::core::feature::persona::Persona =
                serde_json::from_value(patch_of(args)).map_err(|e| BError::invalid(e.to_string()))?;
            let mut p = k.facade.app.personas.write().unwrap_or_else(|e| e.into_inner());
            let id = p.upsert(patch.clone());
            patch.id = id.clone();
            let _ = p.save(k.facade.app.base());
            Ok(id)
        }),
        "persona_delete" => with(|k| {
            let mut p = k.facade.app.personas.write().unwrap_or_else(|e| e.into_inner());
            let ok = p.remove(&s("id"));
            let _ = p.save(k.facade.app.base());
            Ok(ok)
        }),

        // —— 配置档与供应商 ——
        "profiles" => with(|k| {
            let c = k.facade.app.cfg();
            Ok(json!({ "list": c.provider.profiles, "current": c.provider.profile_id }))
        }),
        "profile_save" => with(|k| {
            let pf: crate::core::feature::config::schema::Profile =
                serde_json::from_value(patch_of(args)).map_err(|e| BError::invalid(e.to_string()))?;
            let mut c = k.facade.app.cfg();
            let id = crate::core::feature::config::profile::upsert(&mut c.provider.profiles, pf);
            k.facade.app.update_cfg(|x| x.provider.profiles = c.provider.profiles.clone());
            k.facade.app.cfg.save()?;
            Ok(id)
        }),
        "profile_use" => with(|k| {
            let id = s("id");
            let cfg = k.facade.app.cfg();
            let pf = cfg
                .provider
                .profiles
                .iter()
                .find(|p| p.id == id)
                .cloned()
                .ok_or_else(|| BError::not_found(format!("没有配置档 {id}")))?;
            k.facade.app.save_cfg(|c| {
                c.provider.profile_id = pf.id.clone();
                c.provider.id = pf.provider_id.clone();
                c.provider.base_url = pf.base_url.clone();
                c.provider.model = pf.model.clone();
                c.model.vision = pf.vision;
                c.model.tools = pf.tool_call;
                c.model.reasoning = pf.reasoning;
            })?;
            Ok(pf.id)
        }),
        "profile_delete" => with(|k| {
            let mut c = k.facade.app.cfg();
            let ok = crate::core::feature::config::profile::remove(&mut c.provider.profiles, &s("id"));
            k.facade.app.update_cfg(|x| x.provider.profiles = c.provider.profiles.clone());
            k.facade.app.cfg.save()?;
            Ok(ok)
        }),
        "providers" => with(|k| Ok(k.facade.app.cfg().provider.list)),
        "provider_save" => with(|k| {
            let p: crate::core::feature::model::provider::Provider =
                serde_json::from_value(patch_of(args)).map_err(|e| BError::invalid(e.to_string()))?;
            let mut c = k.facade.app.cfg();
            match c.provider.list.iter().position(|x| x.id == p.id) {
                Some(i) => c.provider.list[i] = p.clone(),
                None => c.provider.list.push(p.clone()),
            }
            k.facade.app.update_cfg(|x| x.provider.list = c.provider.list.clone());
            k.facade.app.cfg.save()?;
            Ok(p.id)
        }),
        "provider_delete" => with(|k| {
            let id = s("id");
            let mut c = k.facade.app.cfg();
            let before = c.provider.list.len();
            c.provider.list.retain(|x| x.id != id);
            let ok = c.provider.list.len() != before;
            k.facade.app.update_cfg(|x| x.provider.list = c.provider.list.clone());
            k.facade.app.cfg.save()?;
            Ok(ok)
        }),

        // —— 技能详情 ——
        "skill_get" => with(|k| {
            let name = s("name");
            let skills = k.facade.app.skills.read().unwrap_or_else(|e| e.into_inner());
            let s = skills
                .get(&name)
                .ok_or_else(|| BError::not_found(format!("没有技能 {name}")))?;
            Ok(json!({
                "name": s.name, "title": s.title, "desc": s.desc,
                "body": s.body, "origin": s.origin, "bytes": s.bytes,
            }))
        }),
        "skill_delete" => with(|k| {
            let name = s("name");
            let mut skills = k.facade.app.skills.write().unwrap_or_else(|e| e.into_inner());
            Ok(skills.remove(&name))
        }),
        "pack_list" => with(|k| {
            let packs = k.facade.app.packs.read().unwrap_or_else(|e| e.into_inner());
            let list: Vec<Value> = packs
                .packs
                .iter()
                .map(|p| {
                    json!({
                        "id": p.id, "name": p.name, "desc": p.desc,
                        "on": p.on, "funcs": p.funcs.len(), "usable": p.usable(),
                    })
                })
                .collect();
            Ok(list)
        }),
        "pack_toggle" => with(|k| {
            let mut packs = k.facade.app.packs.write().unwrap_or_else(|e| e.into_inner());
            Ok(packs.toggle(&s("id"), flag(args, "on")))
        }),

        // —— MCP 管理 ——
        "mcp_list" => with(|k| {
            let m = k.facade.app.mcp.read().unwrap_or_else(|e| e.into_inner());
            Ok(json!({
                "servers": m.servers,
                "tools": m.tools,
                "error": m.last_error,
            }))
        }),
        "mcp_add" => with(|k| {
            let mut m = k.facade.app.mcp.write().unwrap_or_else(|e| e.into_inner());
            let cfg = m.add(&s("name"), &s("url"), &s("token"), &s("kind"));
            m.save();
            Ok(cfg.id)
        }),
        "mcp_remove" => with(|k| {
            let mut m = k.facade.app.mcp.write().unwrap_or_else(|e| e.into_inner());
            let ok = m.remove(&s("id"));
            m.save();
            Ok(ok)
        }),
        "mcp_toggle" => with(|k| {
            let mut m = k.facade.app.mcp.write().unwrap_or_else(|e| e.into_inner());
            let ok = m.toggle(&s("id"), flag(args, "on"));
            m.save();
            Ok(ok)
        }),

        // —— 工作流写入 ——
        "flow_save" => with(|k| {
            let f: crate::core::feature::workflow::Flow =
                serde_json::from_value(patch_of(args)).map_err(|e| BError::invalid(e.to_string()))?;
            let mut flows = k.facade.app.flows.write().unwrap_or_else(|e| e.into_inner());
            let id = flows.upsert(f);
            flows.save();
            Ok(id)
        }),
        "flow_delete" => with(|k| {
            let mut flows = k.facade.app.flows.write().unwrap_or_else(|e| e.into_inner());
            let ok = flows.remove(&s("id"));
            flows.save();
            Ok(ok)
        }),

        // —— 用量明细 ——
        "usage_days" => with(|k| {
            let cfg = k.facade.app.cfg();
            let rows = k.facade.app.db.usage_recent_days(14)?;
            let list: Vec<Value> = rows
                .into_iter()
                .map(|(day, tokens, calls)| {
                    json!({ "day": day, "tokens": tokens, "calls": calls })
                })
                .collect();
            Ok(json!({ "list": list, "currency": cfg.usage.currency }))
        }),

        // —— 备份 ——
        "backup" => with(|k| {
            let app = &k.facade.app;
            let stamp = crate::core::base::clock::now_s();
            let out = app
                .base()
                .join("backup")
                .join(format!("bai-{stamp}.zip"));
            // 目录得先建出来，zip 不会替我们建父级。
            let _ = std::fs::create_dir_all(out.parent().unwrap_or(app.base()));
            // 只备"数据"，不备缓存与截图：那些东西大且可以重建。
            let mut list: Vec<String> = Vec::new();
            for name in ["bai.db", "config.json", "policy.json", "persona.json", "mcp.json"] {
                let p = app.base().join(name);
                if p.is_file() {
                    list.push(p.to_string_lossy().to_string());
                }
            }
            if list.is_empty() {
                return Err(BError::not_found("没有可备份的数据"));
            }
            let r = file_ops(k).zip(&list.join(","), &out.to_string_lossy());
            if !r.ok {
                return Err(BError::internal(r.text));
            }
            Ok(out.to_string_lossy().to_string())
        }),

        // —— 凭据（只出掩码，不出明文）——
        "vault_keys" => with(|k| {
            let bucket = s("bucket");
            let keys = k.facade.app.vault.keys(&bucket);
            let list: Vec<Value> = keys
                .into_iter()
                .map(|key| {
                    let v = k.facade.app.vault.get(&bucket, &key);
                    json!({ "key": key, "masked": crate::core::feature::config::profile::mask(&v) })
                })
                .collect();
            Ok(list)
        }),
        "vault_put" => with(|k| k.facade.app.vault.put(&s("bucket"), &s("key"), &s("value"))),
        "vault_delete" => with(|k| k.facade.app.vault.remove(&s("bucket"), &s("key"))),

        // —— 文件（界面直接浏览，不经过模型）——
        "fs_list" => with(|k| {
            let ops = file_ops(k);
            Ok(ops.list(&s("path"), flag(args, "recurse"), num(args, "limit").max(1) as usize))
        }),
        "fs_read" => with(|k| Ok(file_ops(k).read(&s("path")))),
        "fs_write" => with(|k| Ok(file_ops(k).write(&s("path"), &s("content")))),
        "fs_delete" => with(|k| Ok(file_ops(k).delete(&s("path")))),
        "fs_info" => with(|k| Ok(file_ops(k).info(&s("path")))),
        "fs_search" => with(|k| {
            Ok(file_ops(k).grep(
                &s("path"),
                &s("pattern"),
                &s("ext"),
                num(args, "max").max(1) as usize,
                flag(args, "ignore_case"),
            ))
        }),

        // —— 剪贴板 / 通知 / APK / 插件 ——
        "clipboard" => with(|k| {
            let action = s("action");
            let r = match action.as_str() {
                "set" | "write" => k.rt.block_on(crate::exec::device::clip_set(&s("text"))),
                _ => k.rt.block_on(crate::exec::device::clip_get()),
            };
            Ok(r)
        }),
        "notify" => with(|k| {
            Ok(k.rt.block_on(crate::exec::device::notify(&s("title"), &s("body"))))
        }),
        "apk_info" => with(|_k| {
            let r = crate::exec::apk::run(&s("action"), &s("path"), &s("keyword"), num(args, "limit").max(1) as usize);
            Ok(json!({ "ok": r.ok, "text": r.text }))
        }),
        "plugin_list" => with(|k| {
            let list = crate::core::feature::plugin::installed(k.facade.app.base());
            let items: Vec<Value> = list
                .into_iter()
                .map(|(id, size)| json!({ "id": id, "size": size }))
                .collect();
            Ok(items)
        }),
        "plugin_install" => with(|k| {
            let zip = std::path::PathBuf::from(s("path"));
            let id = s("id");
            let dest = k.facade.app.base().join("skill").join(&id);
            let n = crate::core::feature::plugin::extract(&zip, &dest).map_err(BError::invalid)?;
            Ok(n)
        }),
        "plugin_remove" => with(|k| {
            Ok(crate::core::feature::plugin::uninstall(k.facade.app.base(), &s("id")))
        }),

        _ => Err(BError::new(ErrorCode::Ffi, format!("不认识的 op：{op}"))),
    }
}

/// 布尔参数。`true`/`1`/`"1"`/`"true"` 都认，前端传什么的都有。
fn flag(args: &Value, key: &str) -> bool {
    match args.get(key) {
        Some(Value::Bool(b)) => *b,
        Some(Value::Number(n)) => n.as_i64().unwrap_or(0) != 0,
        Some(Value::String(s)) => matches!(s.trim(), "1" | "true" | "yes" | "on"),
        _ => false,
    }
}

/// 整数参数。数字与数字字符串都认。
fn num(args: &Value, key: &str) -> i64 {
    match args.get(key) {
        Some(Value::Number(n)) => n.as_i64().unwrap_or(0),
        Some(Value::String(s)) => s.trim().parse().unwrap_or(0),
        _ => 0,
    }
}

/// 取 `args.patch`（没有就整个 args）。前端习惯把要保存的对象放 patch 里。
fn patch_of(args: &Value) -> Value {
    args.get("patch").cloned().unwrap_or_else(|| args.clone())
}

/// 临时构造一个 FileOps（借用 router，sandbox 复制一份）。
fn file_ops<'a>(k: &'a Kernel) -> crate::exec::file_ops::FileOps<'a> {
    let sb = k
        .facade
        .app
        .sandbox
        .read()
        .map(|x| x.clone())
        .unwrap_or_else(|_| crate::core::security::sandbox::Sandbox::new(k.facade.app.base()));
    let max = k.facade.app.cfg().tool.max_read_bytes;
    crate::exec::file_ops::FileOps::new(&k.facade.app.router, sb, max)
}

fn str_list(args: &Value, key: &str) -> Vec<String> {
    args.get(key)
        .and_then(|v| v.as_array())
        .map(|a| a.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect())
        .unwrap_or_default()
}

/// 浅合并配置：只处理传进来的键，未知键进 `extra`。
fn merge(c: &mut crate::core::feature::config::schema::Config, patch: &Value) {
    let Ok(mut base) = serde_json::to_value(&*c) else { return };
    crate::core::base::jsonx::merge(&mut base, patch);
    if let Ok(next) = serde_json::from_value::<crate::core::feature::config::schema::Config>(base) {
        *c = next;
    }
}

// —— C ABI ——
//
// 这三个函数是给 C++ / 其它语言用的最小面：调一次、给个指针、用完释放。

/// 注册事件回调。`cb` 收到的是以 `\0` 结尾的 JSON，**回调里不要做重活**。
static EVENT_CB: Mutex<Option<extern "C" fn(*const c_char)>> = Mutex::new(None);
static EVENT_SPAWNED: OnceLock<()> = OnceLock::new();

/// 调用内核。返回的字符串必须用 [baicodex_free] 释放。
///
/// # Safety
/// `req` 必须是合法的以 `\0` 结尾的 UTF-8 字符串。
#[no_mangle]
pub unsafe extern "C" fn baicodex_call(req: *const c_char) -> *mut c_char {
    if req.is_null() {
        return to_c(json!({ "ok": false, "error": { "code": "ffi", "message": "空指针" } }).to_string());
    }
    let text = match CStr::from_ptr(req).to_str() {
        Ok(s) => s,
        Err(_) => return to_c(err_json(BError::new(ErrorCode::Ffi, "请求不是 UTF-8"))),
    };
    to_c(call_json(text))
}

/// 释放 [baicodex_call] 返回的字符串。
///
/// # Safety
/// 指针必须来自 [baicodex_call]，且只能释放一次。
#[no_mangle]
pub unsafe extern "C" fn baicodex_free(p: *mut c_char) {
    if p.is_null() {
        return;
    }
    drop(CString::from_raw(p));
}

/// 注册事件回调。
///
/// # Safety
/// `cb` 必须是一个不会在调用期间被卸载的函数指针。
#[no_mangle]
pub unsafe extern "C" fn baicodex_set_event_cb(cb: Option<extern "C" fn(*const c_char)>) -> c_int {
    if let Ok(mut g) = EVENT_CB.lock() {
        *g = cb;
    }
    // 只挂一次监听：重复注册会推两遍事件。
    if EVENT_SPAWNED.get().is_none() && cb.is_some() {
        let _ = EVENT_SPAWNED.set(());
        spawn_forwarder();
    }
    0
}

/// 给 JNI / 其它前端订阅事件（拿到的是同一个广播源）。
pub fn subscribe_events() -> Option<tokio::sync::broadcast::Receiver<AppEvent>> {
    kernel().ok().map(|k| k.facade.subscribe())
}

fn spawn_forwarder() {
    let Some(k) = kernel().ok().map(|k| k.facade.subscribe()) else { return };
    // 用自己的线程收广播：不占运行时 worker，也不受调用方线程影响。
    std::thread::Builder::new()
        .name("bai-events".into())
        .spawn(move || {
            let mut rx = k;
            loop {
                match rx.blocking_recv() {
                    Ok(e) => {
                        let cb = EVENT_CB.lock().ok().and_then(|g| *g);
                        if let Some(f) = cb {
                            let line = crate::core::feature::app::event::to_json(&e);
                            if let Ok(c) = CString::new(line) {
                                f(c.as_ptr());
                            }
                        }
                        if matches!(e, AppEvent::Quit) {
                            break;
                        }
                    }
                    Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                        crate::log_warn!("ffi", "事件积压，丢了 {n} 条");
                    }
                    Err(_) => break,
                }
            }
        })
        .ok();
}

fn to_c(s: String) -> *mut c_char {
    match CString::new(s) {
        Ok(c) => c.into_raw(),
        Err(_) => CString::new("{\"ok\":false,\"error\":{\"code\":\"ffi\",\"message\":\"内部有 NUL\"}}")
            .map(|c| c.into_raw())
            .unwrap_or(std::ptr::null_mut()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unknown_op_reports_code() {
        let v = call_value(&json!({"op": "nope"}));
        assert_eq!(v["ok"], false);
        assert_eq!(v["error"]["code"], "ffi");
    }

    #[test]
    fn bad_json_is_still_json() {
        let s = call_json("{not json");
        let v: Value = serde_json::from_str(&s).unwrap();
        assert_eq!(v["ok"], false);
    }

    /// 端到端：启动内核 → 发若干 op → 检查 JSON 契约。
    ///
    /// 这个测试**必须**只有一份（内核是进程级单例），所以把要验证的 op 都写在
    /// 一个用例里，按顺序跑。
    #[test]
    fn kernel_roundtrip() {
        // 内核是进程单例，spec 的 op 表校验也会动它 —— 两边必须串行。
        let _guard = test_lock();
        let base = std::env::temp_dir().join(format!("bai-ffi-{}", crate::core::base::ids::IdGen::hex(6)));
        std::fs::create_dir_all(&base).unwrap();
        let b = base.to_string_lossy().to_string();

        let v = call_value(&json!({"op": "boot", "args": {"base": b}}));
        assert_eq!(v["ok"], true, "{v}");

        // 版本
        let v = call_value(&json!({"op": "version"}));
        assert_eq!(v["ok"], true);
        assert_eq!(v["data"]["version"], crate::VERSION);

        // 启动时会自动建一个会话
        let v = call_value(&json!({"op": "sessions"}));
        assert_eq!(v["ok"], true);
        assert!(v["data"].as_array().map(|a| !a.is_empty()).unwrap_or(false), "{v}");

        // 配置读写
        let v = call_value(&json!({"op": "config_get"}));
        assert_eq!(v["ok"], true);
        assert!(v["data"]["agent"].is_object());
        let v = call_value(&json!({"op": "config_patch", "args": {"patch": {"tool": {"shell_timeout_s": 33}}}}));
        assert_eq!(v["ok"], true);
        assert_eq!(v["data"]["tool"]["shell_timeout_s"], 33);

        // 快照
        let v = call_value(&json!({"op": "snapshot"}));
        assert_eq!(v["ok"], true);
        assert!(v["data"]["session"].is_string());

        // 直接调一个工具（不经过模型）：写文件再读回来
        let f = base.join("hello.txt");
        let v = call_value(&json!({"op": "fs_write", "args": {"path": f.to_string_lossy(), "content": "你好"}}));
        assert_eq!(v["data"]["ok"], true, "{v}");
        let v = call_value(&json!({"op": "fs_read", "args": {"path": f.to_string_lossy()}}));
        assert!(v["data"]["text"].as_str().unwrap_or("").contains("你好"), "{v}");

        // 人设
        let v = call_value(&json!({"op": "personas"}));
        assert_eq!(v["ok"], true);
        assert!(v["data"]["list"].as_array().map(|a| !a.is_empty()).unwrap_or(false));

        // 用量
        let v = call_value(&json!({"op": "usage"}));
        assert_eq!(v["ok"], true);
        assert!(v["data"].is_object());

        // 不认识的 op 仍然返回结构化错误
        let v = call_value(&json!({"op": "no_such_op"}));
        assert_eq!(v["ok"], false);
        assert_eq!(v["error"]["code"], "ffi");

        std::fs::remove_dir_all(&base).ok();
    }
}