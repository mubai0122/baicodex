//! 前端 ↔ 宿主 ↔ 内核。
//!
//! ## 三条通道
//!
//! ```text
//! 前端 ──postMessage──▶ 宿主 UI 线程
//!                        ├─ __host_*  窗口操作，就地处理
//!                        └─ 其它      丢给工作线程 → ffi::call_value
//! 前端 ◀─PostWebMessageAsJson── 回复（带 id）与事件（带 type）
//! ```
//!
//! ## 为什么业务 op 一定要离开 UI 线程
//!
//! `ffi::call_value` 内部是 `block_on`。`send` 会一直阻塞到模型把整段回答吐完。
//! 如果放在 UI 线程，Windows 消息泵停了，WebView2 不重绘、事件也推不出去——
//! 表现为"点了发送，界面冻住，最后一次性蹦出一大段字"。
//!
//! 所以：**任何可能超过一帧的活儿都不许在 UI 线程做**。窗口操作是例外，它们本身
//! 就是发消息，微秒级。

use std::collections::VecDeque;
use std::sync::atomic::{AtomicIsize, Ordering};
use std::sync::{mpsc, Mutex, OnceLock};

use serde_json::{json, Value};
use windows::Win32::Foundation::{HWND, LPARAM, WPARAM};
use windows::Win32::UI::Input::KeyboardAndMouse::ReleaseCapture;
use windows::Win32::UI::WindowsAndMessaging::{
    IsZoomed, SendMessageW, ShowWindow, SW_MINIMIZE, WM_NCLBUTTONDOWN,
};

use crate::error::Result;
use crate::webview;

/// 当前窗口句柄。worker 线程要用它把自己叫醒。
static HWND_BITS: AtomicIsize = AtomicIsize::new(0);

/// 待回给前端的 JSON。UI 线程排空。
static OUTBOX: OnceLock<Mutex<VecDeque<String>>> = OnceLock::new();

/// 工作线程的任务队列。
static JOBS: OnceLock<mpsc::Sender<String>> = OnceLock::new();

/// 宿主能力快照，`host.info` 直接返回。
static INFO: OnceLock<Mutex<Value>> = OnceLock::new();

fn outbox() -> &'static Mutex<VecDeque<String>> {
    OUTBOX.get_or_init(|| Mutex::new(VecDeque::new()))
}

fn info_slot() -> &'static Mutex<Value> {
    INFO.get_or_init(|| Mutex::new(json!({})))
}

/// 启动桥：记下窗口、拉起工作线程。
pub fn start(hwnd: HWND, info: Value) {
    HWND_BITS.store(hwnd.0 as isize, Ordering::SeqCst);
    *info_slot().lock().unwrap_or_else(|e| e.into_inner()) = info;

    let (tx, rx) = mpsc::channel::<String>();
    let _ = JOBS.set(tx);

    std::thread::Builder::new()
        .name("bai-host-bridge".into())
        .spawn(move || {
            while let Ok(raw) = rx.recv() {
                let reply = run_op(&raw);
                push_out(reply);
                wake_ui();
            }
        })
        .ok();
}

fn hwnd() -> Option<HWND> {
    let v = HWND_BITS.load(Ordering::SeqCst);
    if v == 0 {
        None
    } else {
        Some(HWND(v as *mut core::ffi::c_void))
    }
}

fn wake_ui() {
    if let Some(h) = hwnd() {
        webview::wake(h);
    }
}

fn push_out(s: String) {
    let mut q = outbox().lock().unwrap_or_else(|e| e.into_inner());
    // 上限防内存爆：前端一卡，事件能堆到几万条。
    if q.len() > 4096 {
        q.pop_front();
    }
    q.push_back(s);
}

/// UI 线程调用：把攒下的回复/事件一次性推给页面。
pub fn flush(wv: &webview::WebView) {
    loop {
        let item = {
            let mut q = outbox().lock().unwrap_or_else(|e| e.into_inner());
            q.pop_front()
        };
        match item {
            Some(s) => wv.post_json(&s),
            None => break,
        }
    }
}

/// 更新宿主信息（主题切换、backdrop 变化时会调）。
pub fn set_info(patch: Value) {
    let mut cur = info_slot().lock().unwrap_or_else(|e| e.into_inner());
    if let (Some(a), Some(b)) = (cur.as_object_mut(), patch.as_object()) {
        for (k, v) in b {
            a.insert(k.clone(), v.clone());
        }
    }
}

/// 前端来一条消息。
///
/// 返回 `Some` 表示要**立刻**回（窗口类操作）；返回 `None` 表示已经转给工作线程。
pub fn on_message(raw: &str) -> Option<String> {
    let v: Value = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(e) => return Some(err_reply(None, "bad_json", &format!("消息不是 JSON：{e}"))),
    };

    let op = v.get("op").and_then(|x| x.as_str()).unwrap_or("");
    let id = v.get("id").and_then(|x| x.as_i64());

    if op.starts_with("__host_") {
        return Some(match host_op(op, &v) {
            Ok(data) => json!({ "id": id, "ok": true, "data": data }).to_string(),
            Err(e) => err_reply(id, "host", &e.to_string()),
        });
    }

    // 业务 op：交给工作线程，回复走 outbox。
    let job = json!({ "id": id, "op": op, "args": v.get("args").cloned().unwrap_or_else(|| json!({})) });
    match JOBS.get() {
        Some(tx) if tx.send(job.to_string()).is_ok() => None,
        _ => Some(err_reply(id, "not_ready", "宿主还没准备好")),
    }
}

fn err_reply(id: Option<i64>, code: &str, message: &str) -> String {
    json!({ "id": id, "ok": false, "error": { "code": code, "message": message } }).to_string()
}

/// 工作线程里真正调内核。
fn run_op(raw: &str) -> String {
    let v: Value = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(_) => return err_reply(None, "bad_json", "任务不是合法 JSON"),
    };
    let id = v.get("id").cloned().unwrap_or(Value::Null);
    let op = v.get("op").and_then(|x| x.as_str()).unwrap_or("");
    let args = v.get("args").cloned().unwrap_or_else(|| json!({}));

    // 内核要的是 {op, args}，多一个 id 不影响它
    let req = json!({ "op": op, "args": args });
    let mut out = baicodex::ffi::call_value(&req);
    if let Some(o) = out.as_object_mut() {
        o.insert("id".into(), id);
    }
    out.to_string()
}

/// 窗口操作。全部在 UI 线程，都是"发一条消息"级别的开销。
fn host_op(op: &str, v: &Value) -> Result<Value> {
    let hwnd = hwnd().ok_or_else(|| crate::error::HostError::msg("窗口还没建好"))?;
    let a = v.get("args").cloned().unwrap_or_else(|| json!({}));
    let s = |k: &str| a.get(k).and_then(|x| x.as_str()).unwrap_or("").to_string();
    let n = |k: &str| a.get(k).and_then(|x| x.as_i64()).unwrap_or(0) as i32;

    match op {
        "__host_info" => Ok(info_slot().lock().unwrap_or_else(|e| e.into_inner()).clone()),

        // 拖动：交给系统的非客户区拖动循环，这样才有 Aero Snap 和双击最大化
        "__host_drag" => {
            unsafe {
                let _ = ReleaseCapture();
                SendMessageW(hwnd, WM_NCLBUTTONDOWN, Some(WPARAM(2 /* HTCAPTION */)), Some(LPARAM(0)));
            }
            Ok(json!(null))
        }

        // 边缘缩放：同理，走原生命中测试码
        "__host_resize" => {
            let edge = s("edge");
            let code = match edge.as_str() {
                "left" => 10,        // HTLEFT
                "right" => 11,       // HTRIGHT
                "top" => 12,         // HTTOP
                "top-left" => 13,    // HTTOPLEFT
                "top-right" => 14,   // HTTOPRIGHT
                "bottom" => 15,      // HTBOTTOM
                "bottom-left" => 16, // HTBOTTOMLEFT
                "bottom-right" => 17,// HTBOTTOMRIGHT
                _ => 10,
            };
            unsafe {
                let _ = ReleaseCapture();
                SendMessageW(hwnd, WM_NCLBUTTONDOWN, Some(WPARAM(code as usize)), Some(LPARAM(0)));
            }
            Ok(json!(null))
        }

        "__host_minimize" => {
            unsafe {
                let _ = ShowWindow(hwnd, SW_MINIMIZE);
            }
            Ok(json!(null))
        }

        "__host_maximize" => {
            crate::win::frame::toggle_maximize(hwnd);
            Ok(json!({ "maximized": unsafe { IsZoomed(hwnd).as_bool() } }))
        }

        "__host_close" => {
            unsafe {
                let _ = windows::Win32::UI::WindowsAndMessaging::PostMessageW(
                    Some(hwnd),
                    windows::Win32::UI::WindowsAndMessaging::WM_CLOSE,
                    WPARAM(0),
                    LPARAM(0),
                );
            }
            Ok(json!(null))
        }

        "__host_reload" => {
            crate::win::request_reload();
            Ok(json!(null))
        }

        "__host_devtools" => {
            crate::win::request_devtools();
            Ok(json!(null))
        }

        // 打开外部链接 / 文件夹。一律走系统默认程序，绝不在内嵌浏览器里开。
        "__host_open" => {
            let target = s("url");
            if target.is_empty() {
                return Err(crate::error::HostError::msg("url 为空"));
            }
            crate::win::shell_open(&target)?;
            Ok(json!(null))
        }

        // 让窗口跟着鼠标走一小步（用于自定义的"拖到屏幕边缘"手势）
        "__host_move" => {
            let dx = n("dx");
            let dy = n("dy");
            crate::win::frame::move_by(hwnd, dx, dy)?;
            Ok(json!(null))
        }

        "__host_state" => Ok(crate::win::frame::window_state(hwnd)),

        other => Err(crate::error::HostError::msg(format!("不认识的宿主操作：{other}"))),
    }
}

/// 把内核事件接出来，推到前端。
///
/// 内核的事件源是 broadcast，谁订阅谁收。这里起一个线程常驻，收到就入队并
/// `PostMessage` 叫醒 UI 线程——不能直接 `PostWebMessageAsJson`，COM 接口不是 Send。
pub fn pump_events() {
    let Some(mut rx) = baicodex::ffi::subscribe_events() else { return };
    std::thread::Builder::new()
        .name("bai-host-events".into())
        .spawn(move || loop {
            match rx.blocking_recv() {
                Ok(ev) => {
                    let j = baicodex::core::feature::app::event::to_json(&ev);
                    push_out(j);
                    wake_ui();
                }
                Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                    let _ = n;
                    continue;
                }
                Err(_) => break,
            }
        })
        .ok();
}
