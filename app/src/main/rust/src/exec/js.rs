//! JS 脚本：技能包里的 `js` 函数、以及 `js` 工具。
//!
//! ## 两条后端
//!
//! 1. **内嵌**（`feature = "js"`）：rquickjs 把 QuickJS 编进二进制，不依赖系统装
//!    任何东西。用 interrupt handler 做超时，脚本里的死循环能被掐断。
//! 2. **外部**：没有内嵌能力时找 `qjs` / `node` 起进程跑。启动慢、依赖环境，
//!    但至少能用。
//!
//! ## 返回值约定
//!
//! 脚本的执行结果 = `console.log` 的输出（每行一条）+ 脚本的返回值（JSON 序列化后
//! 附在最后）。这样"打印调试"和"算个值回去"两种写法都能用。
//!
//! ## 沙箱
//!
//! 内嵌后端**不给**任何 IO、网络、require。脚本只能做纯计算与字符串处理。
//! 这是有意的：技能包是用户从别处拿来的，不该有文件系统权限。

use std::time::Duration;

use crate::core::base::text;

/// 一次执行的结果。
#[derive(Debug, Clone)]
pub struct JsOut {
    pub ok: bool,
    pub text: String,
    /// 用的哪个引擎（`quickjs` / `qjs` / `node`）。
    pub engine: &'static str,
}

impl JsOut {
    fn ok(text: impl Into<String>, engine: &'static str) -> Self {
        Self { ok: true, text: text.into(), engine }
    }
    fn err(text: impl Into<String>, engine: &'static str) -> Self {
        Self { ok: false, text: text.into(), engine }
    }
}

/// 输出上限，防止 `while(1) console.log(1)` 把内存打满。
const MAX_OUT: usize = 200_000;

/// 跑一段脚本。
pub async fn run(code: &str, timeout_ms: u64) -> JsOut {
    let c = code.trim();
    if c.is_empty() {
        return JsOut::err("脚本是空的", "none");
    }
    let ms = timeout_ms.clamp(1_000, 300_000);

    #[cfg(feature = "js")]
    {
        let code = c.to_string();
        // 阻塞式 eval 必须放到 blocking 线程池，否则会卡住 tokio worker。
        let r = tokio::task::spawn_blocking(move || embedded::run(&code, Duration::from_millis(ms)))
            .await;
        match r {
            Ok(Ok(out)) => JsOut::ok(out, "quickjs"),
            Ok(Err(e)) => {
                // 内嵌引擎报错时不再降级到外部引擎：语法错就该直接报，换来换去只会更迷惑。
                JsOut::err(e, "quickjs")
            }
            Err(e) => JsOut::err(format!("执行线程崩了：{e}"), "quickjs"),
        }
    }

    #[cfg(not(feature = "js"))]
    {
        external(c, ms).await
    }
}

/// 有没有可用的引擎。
pub fn available() -> bool {
    #[cfg(feature = "js")]
    {
        true
    }
    #[cfg(not(feature = "js"))]
    {
        super::shell::has("qjs") || super::shell::has("node")
    }
}

/// 引擎名字，给界面显示。
pub fn engine_name() -> &'static str {
    #[cfg(feature = "js")]
    {
        "quickjs"
    }
    #[cfg(not(feature = "js"))]
    {
        if super::shell::has("qjs") {
            "qjs"
        } else if super::shell::has("node") {
            "node"
        } else {
            "none"
        }
    }
}

/// 外部引擎：写临时文件 → 起进程 → 删文件。
#[cfg(not(feature = "js"))]
async fn external(code: &str, ms: u64) -> JsOut {
    let engine = if super::shell::has("qjs") {
        "qjs"
    } else if super::shell::has("node") {
        "node"
    } else {
        return JsOut::err("本机没有 JS 引擎（装 qjs 或 node，或用带 js feature 的构建）", "none");
    };
    let tmp = crate::core::base::dirs::tmp("script.js");
    if std::fs::write(&tmp, code).is_err() {
        return JsOut::err("写临时脚本失败（数据根不可写）", engine);
    }
    let path = tmp.to_string_lossy().to_string();
    let r = super::shell::run_full(
        &[engine, &path],
        None,
        Duration::from_millis(ms),
        None,
        MAX_OUT,
        &[],
    )
    .await;
    let _ = std::fs::remove_file(&tmp);
    if r.out.trim().is_empty() && r.ok() {
        return JsOut::ok("（没有输出）", if engine == "qjs" { "qjs" } else { "node" });
    }
    let e: &'static str = if engine == "qjs" { "qjs" } else { "node" };
    JsOut { ok: r.ok(), text: text::truncate(&r.out, MAX_OUT), engine: e }
}

/// 内嵌 QuickJS。
#[cfg(feature = "js")]
mod embedded {
    use std::cell::RefCell;
    use std::rc::Rc;
    use std::time::{Duration, Instant};

    use rquickjs::{Context, Function, Runtime};

    /// 跑脚本，返回"输出 + 返回值"。
    pub fn run(code: &str, timeout: Duration) -> Result<String, String> {
        let rt = Runtime::new().map_err(|e| format!("创建运行时失败：{e}"))?;

        // 超时：interrupt handler 每执行若干条字节码被调一次，返回 true 就中断。
        let start = Instant::now();
        rt.set_interrupt_handler(Some(Box::new(move || start.elapsed() > timeout)));

        let ctx = Context::full(&rt).map_err(|e| format!("创建上下文失败：{e}"))?;
        let logs: Rc<RefCell<Vec<String>>> = Rc::new(RefCell::new(Vec::new()));
        let sink = logs.clone();

        let out = ctx.with(|ctx| -> Result<String, String> {
            // console.log 写进 Rust 侧缓冲区，避免 stdout 交叉。
            let print = Function::new(ctx.clone(), move |s: String| {
                if let Ok(mut v) = sink.try_borrow_mut() {
                    if v.len() < 2000 {
                        v.push(s);
                    }
                }
            })
            .map_err(|e| format!("注册输出回调失败：{e}"))?;
            print.set("__bai_print", ctx.globals()).map_err(|e| e.to_string())?;

            // 一行 prelude：console 三件套 + 结果读取。
            let prelude = r#"
                globalThis.console = {
                    log: (...a) => __bai_print(a.map(fmt).join(' ')),
                    info: (...a) => __bai_print(a.map(fmt).join(' ')),
                    warn: (...a) => __bai_print(a.map(fmt).join(' ')),
                    error: (...a) => __bai_print(a.map(fmt).join(' ')),
                };
                function fmt(x) {
                    if (typeof x === 'string') return x;
                    try { return JSON.stringify(x); } catch (e) { return String(x); }
                }
            "#;
            ctx.eval::<(), _>(prelude).map_err(|e| format!("初始化失败：{e}"))?;

            // 包一层函数：脚本里写 return 也能把值带回来。
            let wrapped = format!("globalThis.__bai_result = (function(){{\n{code}\n}})();");
            ctx.eval::<(), _>(wrapped).map_err(|e| clean_err(&e.to_string()))?;

            let tail: String = ctx
                .eval("(typeof __bai_result === 'undefined' || __bai_result === null) ? '' : (typeof __bai_result === 'string' ? __bai_result : fmt(__bai_result))")
                .map_err(|e| clean_err(&e.to_string()))?;
            Ok(tail)
        })?;

        let lines = logs.borrow().clone();
        let mut s = lines.join("\n");
        let t = out.trim();
        if !t.is_empty() {
            if !s.is_empty() {
                s.push('\n');
            }
            s.push_str("=> ");
            s.push_str(t);
        }
        if s.trim().is_empty() {
            s = "（没有输出）".into();
        }
        Ok(super::text::truncate(&s, super::MAX_OUT))
    }

    /// QuickJS 的报错带一长串栈，去掉内部帧只留第一行与位置。
    fn clean_err(e: &str) -> String {
        let first = e.lines().next().unwrap_or("脚本出错").trim();
        format!("脚本出错：{first}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_script_rejected() {
        let r = futures_lite_block_on(run("   ", 1000));
        assert!(!r.ok);
    }

    /// 不引额外依赖的极简 block_on：只用于同步测试。
    fn futures_lite_block_on<F: std::future::Future>(f: F) -> F::Output {
        tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("runtime")
            .block_on(f)
    }
}
