//! JNI 导出（Android）。
//!
//! 契约与 C ABI 完全一致：一个 JSON 字符串进、一个 JSON 字符串出。Kotlin 侧只认
//! 这一个类：`b.ai.native.NativeBridge`。
//!
//! ## 为什么返回 Rust `String` 而不是手工 `NewStringUTF`
//!
//! `jni` crate 会在 `Env` 作用域内安全地转换；手工管理 `jstring` 的局部引用是
//! 内存泄漏与崩溃的常见来源，尤其是异常路径上。
//!
//! ## 事件通道
//!
//! Kotlin 注册一个 `NativeBridge.EventSink`（只有一个 `void onEvent(String)`），
//! 这里把它存成全局引用，起一个**常驻线程**订阅内核事件并回调。
//! 线程用 `attach_current_thread_as_daemon` —— 进程退出时不需要 join，
//! 也不会阻止 JVM 卸载。
//!
//! ## 没有开启 feature 时
//!
//! 整个模块是空的，库照样能编。Android 构建开 `--features jni-bridge` 才有导出。

#[cfg(feature = "jni-bridge")]
mod imp {
    use std::sync::Arc;

    use jni::objects::{GlobalRef, JClass, JObject, JString};
    use jni::sys::jstring;
    use jni::{JNIEnv, JavaVM};

    /// `JavaVM` 本身不可 Clone（0.21 里的 `get_java_vm` 返回所有权），
    /// 而线程之间要共享同一个 VM，所以套一层 `Arc`。
    static SINK: std::sync::Mutex<Option<(Arc<JavaVM>, GlobalRef)>> = std::sync::Mutex::new(None);

    /// 调用内核：入参 JSON，出参 JSON。
    #[no_mangle]
    pub extern "system" fn Java_b_ai_native_NativeBridge_nativeCall<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        req: JString<'l>,
    ) -> jstring {
        let text: String = match env.get_string(&req) {
            Ok(s) => s.into(),
            Err(_) => return new_string(&mut env, r#"{"ok":false,"error":{"code":"ffi","message":"参数不是字符串"}}"#),
        };
        let out = crate::ffi::call_json(&text);
        new_string(&mut env, &out)
    }

    fn new_string(env: &mut JNIEnv<'_>, s: &str) -> jstring {
        env.new_string(s).map(|x| x.into_raw()).unwrap_or(std::ptr::null_mut())
    }

    /// 版本号。
    #[no_mangle]
    pub extern "system" fn Java_b_ai_native_NativeBridge_nativeVersion<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
    ) -> jstring {
        new_string(&mut env, crate::VERSION)
    }

    /// 注册事件回调。
    #[no_mangle]
    pub extern "system" fn Java_b_ai_native_NativeBridge_nativeSetEventSink<'l>(
        env: JNIEnv<'l>,
        _class: JClass<'l>,
        sink: JObject<'l>,
    ) {
        if sink.is_null() {
            if let Ok(mut g) = SINK.lock() {
                *g = None;
            }
            return;
        }
        let (Ok(vm), Ok(global)) = (env.get_java_vm(), env.new_global_ref(sink)) else { return };
        let first = {
            let mut g = SINK.lock().unwrap_or_else(|e| e.into_inner());
            let first = g.is_none();
            *g = Some((Arc::new(vm), global));
            first
        };
        if first {
            spawn_pump();
        }
    }

    /// 常驻事件线程。
    fn spawn_pump() {
        let Some(mut rx) = crate::ffi::subscribe_events() else { return };
        let Some(vm) = ({
            let g = SINK.lock().unwrap_or_else(|e| e.into_inner());
            g.as_ref().map(|(vm, _)| Arc::clone(vm))
        }) else {
            return;
        };

        std::thread::Builder::new()
            .name("bai-jni-events".into())
            .spawn(move || {
                let Ok(mut env) = vm.attach_current_thread_as_daemon() else { return };
                loop {
                    let ev = match rx.blocking_recv() {
                        Ok(e) => e,
                        Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                            crate::log_warn!("jni", "事件积压，丢了 {n} 条");
                            continue;
                        }
                        Err(_) => break,
                    };
                    let quit = matches!(ev, crate::core::feature::app::AppEvent::Quit);
                    let line = crate::core::feature::app::event::to_json(&ev);
                    let g = SINK.lock().unwrap_or_else(|e| e.into_inner());
                    if let Some((_, sink)) = g.as_ref() {
                        if let Ok(s) = env.new_string(line) {
                            // 方法签名固定：void onEvent(String)
                            let _ = env.call_method(sink.as_obj(), "onEvent", "(Ljava/lang/String;)V", &[(&s).into()]);
                        }
                    }
                    drop(g);
                    if quit {
                        break;
                    }
                }
            })
            .ok();
    }
}

#[cfg(feature = "jni-bridge")]
pub use imp::*;
