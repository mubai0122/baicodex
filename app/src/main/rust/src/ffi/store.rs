//! 内核存储层的 C ABI：凭据库（vault）+ 配置库（prefs），三端共用同一套入口。
//!
//! 约定（与 Windows / Linux 宿主一致，改字段等于改协议）：
//! - 返回 `*mut c_char` 的字符串由调用方用 [`bx_free`] 释放；
//! - 返回 `0` 表示成功，其余为 [`ErrorCode`] 的数值；
//! - 失败详情用 [`bx_last_error`] 取（**按线程**保存，宿主在自己的线程里读）；
//! - 句柄由 [`bx_open`] / [`bx_open_default`] 建，只能 [`bx_close`] 一次。
//!
//! 这一层刻意做得很薄：不做业务判断、不缓存状态，避免各端出现两套语义。

use std::cell::{Cell, RefCell};
use std::ffi::{c_char, c_int, CStr, CString};
use std::path::Path;
use std::ptr;
use std::sync::Arc;

use crate::core::base::err::{BxError, BxResult, ErrorCode};
use crate::core::base::sealed::SealedStore;
use crate::core::base::store::{FsStore, Store};
use crate::core::feature::prefs::Prefs;
use crate::core::feature::vault::{BucketState, Vault};
use crate::core::platform::Caps;
use crate::core::security::{FileKeyProvider, KeyProvider, MasterKey, Sealer};

/// 自检用的桶名：与业务桶分开，用完即删不留垃圾
const SELFTEST_BUCKET: &str = "selftest";
/// 自管主密钥时的相对路径（相对数据根）
const KEY_REL: &str = "meta/master.key";

thread_local! {
    /// 最近一次错误的文本；按线程保存，宿主在自己的线程里读
    static LAST_ERR: RefCell<CString> = RefCell::new(CString::new("").unwrap_or_default());
    /// 最近一次错误码；成功时置 0
    static LAST_CODE: Cell<c_int> = const { Cell::new(0) };
}

fn set_err(msg: impl AsRef<str>) {
    let s = CString::new(msg.as_ref().replace('\0', " ")).unwrap_or_default();
    LAST_ERR.with(|c| *c.borrow_mut() = s);
} //记录错误文本

fn clear_err() {
    LAST_ERR.with(|c| *c.borrow_mut() = CString::new("").unwrap_or_default());
    LAST_CODE.with(|c| c.set(0));
} //清空错误

fn fail(e: &BxError) -> c_int {
    set_err(e.to_string());
    let code = e.code().as_i32();
    LAST_CODE.with(|c| c.set(code));
    code
} //记录错误并返回码

/// 不透明句柄：凭据库与配置库共用同一个（已加密的）后端
pub struct KernelHandle {
    /// 凭据库
    vault: Vault,
    /// 配置库
    prefs: Prefs,
}

fn to_c(s: &str) -> *mut c_char {
    match CString::new(s) {
        Ok(c) => c.into_raw(),
        Err(_) => ptr::null_mut(),
    }
} //Rust 字符串 → 由调用方释放的 C 字符串

fn read_str(p: *const c_char) -> BxResult<String> {
    if p.is_null() {
        return Err(BxError::Invalid("空指针".into()));
    }
    // SAFETY: 调用方保证 p 指向以 NUL 结尾的合法 C 字符串
    let s = unsafe { CStr::from_ptr(p) };
    s.to_str()
        .map(|v| v.to_string())
        .map_err(|_| BxError::Invalid("字符串不是 UTF-8".into()))
} //C 字符串 → Rust 字符串

/// 用给定数据根与主密钥装配句柄：加密装饰器一次套好，业务代码只看到明文
fn build(root: &str, master: MasterKey) -> BxResult<Box<KernelHandle>> {
    let fs = FsStore::open(root, true)?;
    let sealed: Arc<dyn Store> =
        Arc::new(SealedStore::new(Arc::new(fs) as Arc<dyn Store>, Arc::new(Sealer::new(master))));
    Ok(Box::new(KernelHandle {
        vault: Vault::new(sealed.clone()),
        prefs: Prefs::new(sealed),
    }))
} //装配存储后端

/// 内核自管主密钥时的打开路径：`<root>/meta/master.key`，首次自动生成
fn open_default(root: &str) -> BxResult<Box<KernelHandle>> {
    let kp = FileKeyProvider::new(Path::new(root).join(KEY_REL));
    let master = kp.load_or_create()?;
    build(root, master)
} //打开（内核自管主密钥）

/// 内核版本（静态字符串，不需要释放）
#[no_mangle]
pub extern "C" fn bx_version() -> *const c_char {
    static V: &[u8] = b"2.0.0\0";
    V.as_ptr().cast()
} //版本

/// 存储格式代数：改信封格式时必须 +1，前端据此判断要不要迁移
#[no_mangle]
pub extern "C" fn bx_schema() -> u32 {
    crate::SCHEMA
} //代际

/// 平台能力 JSON：{"os":"android","liquid_glass":false,...}，需 [`bx_free`]
#[no_mangle]
pub extern "C" fn bx_caps_json() -> *mut c_char {
    to_c(&Caps::current().to_json())
} //平台能力

/// 打开凭据库（主密钥由宿主自己保管）
///
/// - `root`：数据根（UTF-8，必须是已存在目录，调用方负责选好并保证不换根）；
/// - `key`：32 字节主密钥（Android 从 Keystore 解出，桌面从 DPAPI / 文件解出）；
/// - `key_len`：必须为 32。
///
/// 返回句柄；失败返回 null 并设置 last_error。
///
/// # Safety
/// `root` 必须是有效的 NUL 结尾字符串；`key` 必须指向至少 `key_len` 字节可读内存。
#[no_mangle]
pub unsafe extern "C" fn bx_open(root: *const c_char, key: *const u8, key_len: usize) -> *mut KernelHandle {
    let res = (|| -> BxResult<Box<KernelHandle>> {
        let root = read_str(root)?;
        if key.is_null() || key_len != 32 {
            return Err(BxError::Invalid("主密钥必须是 32 字节".into()));
        }
        // SAFETY: 由调用方保证 key 指向 key_len 字节可读内存
        let raw = unsafe { std::slice::from_raw_parts(key, key_len) };
        let master = MasterKey::from_bytes(raw)?;
        build(&root, master)
    })();
    match res {
        Ok(h) => {
            clear_err();
            Box::into_raw(h)
        }
        Err(e) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //打开（宿主给定主密钥）

/// 打开凭据库，主密钥由内核自己保管在 `<root>/meta/master.key`（0600，首次自动生成）
///
/// 给「没有系统密钥库、又不想自己管 32 字节」的场景用；接上 Keystore 后应改用 [`bx_open`]。
///
/// # Safety
/// `root` 必须是有效的 NUL 结尾字符串，且目录已存在。
#[no_mangle]
pub unsafe extern "C" fn bx_open_default(root: *const c_char) -> *mut KernelHandle {
    let res = (|| -> BxResult<Box<KernelHandle>> { open_default(&read_str(root)?) })();
    match res {
        Ok(h) => {
            clear_err();
            Box::into_raw(h)
        }
        Err(e) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //打开（内核自管主密钥）

/// 关闭句柄（可重复调用，传 null 无操作）
///
/// # Safety
/// `h` 必须来自 `bx_open` / `bx_open_default`，且只能关闭一次。
#[no_mangle]
pub unsafe extern "C" fn bx_close(h: *mut KernelHandle) {
    if h.is_null() {
        return;
    }
    // SAFETY: 指针由 Box::into_raw 产生，调用方保证不重复关闭
    drop(unsafe { Box::from_raw(h) });
} //关闭

/// 读一条凭据；不存在返回 null（不是错误）
///
/// # Safety
/// `h` 必须有效；`bucket` / `name` 必须是以 NUL 结尾的字符串。
#[no_mangle]
pub unsafe extern "C" fn bx_get(h: *mut KernelHandle, bucket: *const c_char, name: *const c_char) -> *mut c_char {
    if h.is_null() {
        fail(&BxError::Invalid("句柄为空".into()));
        return ptr::null_mut();
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    match (read_str(bucket), read_str(name)) {
        (Ok(b), Ok(n)) => match k.vault.get(&b, &n) {
            Ok(Some(v)) => {
                clear_err();
                to_c(&v)
            }
            Ok(None) => {
                clear_err();
                ptr::null_mut()
            }
            Err(e) => {
                fail(&e);
                ptr::null_mut()
            }
        },
        (Err(e), _) | (_, Err(e)) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //读凭据

/// 写一条凭据（`value` 为空等于删除）
///
/// # Safety
/// 同 [`bx_get`]。
#[no_mangle]
pub unsafe extern "C" fn bx_put(
    h: *mut KernelHandle,
    bucket: *const c_char,
    name: *const c_char,
    value: *const c_char,
) -> c_int {
    if h.is_null() {
        return fail(&BxError::Invalid("句柄为空".into()));
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    let (b, n, v) = match (read_str(bucket), read_str(name), read_str(value)) {
        (Ok(b), Ok(n), Ok(v)) => (b, n, v),
        (Err(e), _, _) | (_, Err(e), _) | (_, _, Err(e)) => return fail(&e),
    };
    match k.vault.put(&b, &n, &v) {
        Ok(()) => {
            clear_err();
            0
        }
        Err(e) => fail(&e),
    }
} //写凭据

/// 删一条凭据
///
/// # Safety
/// 同 [`bx_get`]。
#[no_mangle]
pub unsafe extern "C" fn bx_del(h: *mut KernelHandle, bucket: *const c_char, name: *const c_char) -> c_int {
    if h.is_null() {
        return fail(&BxError::Invalid("句柄为空".into()));
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    match (read_str(bucket), read_str(name)) {
        (Ok(b), Ok(n)) => match k.vault.remove(&b, &n) {
            Ok(()) => {
                clear_err();
                0
            }
            Err(e) => fail(&e),
        },
        (Err(e), _) | (_, Err(e)) => fail(&e),
    }
} //删凭据

/// 一个桶的全量快照（JSON 对象），需 [`bx_free`]
///
/// # Safety
/// 同 [`bx_get`]。
#[no_mangle]
pub unsafe extern "C" fn bx_snapshot(h: *mut KernelHandle, bucket: *const c_char) -> *mut c_char {
    if h.is_null() {
        fail(&BxError::Invalid("句柄为空".into()));
        return ptr::null_mut();
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    match read_str(bucket) {
        Ok(b) => match k.vault.snapshot(&b) {
            Ok(map) => match serde_json::to_string(&map) {
                Ok(s) => to_c(&s),
                Err(_) => {
                    fail(&BxError::Invalid("快照序列化失败".into()));
                    ptr::null_mut()
                }
            },
            Err(e) => {
                fail(&e);
                ptr::null_mut()
            }
        },
        Err(e) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //快照

/// 桶与状态（JSON 数组 [{name,state,reason,count}]），需 [`bx_free`]
///
/// # Safety
/// `h` 必须有效。
#[no_mangle]
pub unsafe extern "C" fn bx_buckets(h: *mut KernelHandle) -> *mut c_char {
    if h.is_null() {
        fail(&BxError::Invalid("句柄为空".into()));
        return ptr::null_mut();
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    match k.vault.buckets() {
        Ok(list) => {
            let arr: Vec<serde_json::Value> = list
                .into_iter()
                .map(|b| {
                    let (state, why) = match b.state {
                        BucketState::Ready => ("ready", ""),
                        BucketState::Unloaded => ("unloaded", ""),
                        BucketState::Locked(w) => ("locked", w),
                    };
                    serde_json::json!({
                        "name": b.name,
                        "state": state,
                        "reason": why,
                        "count": b.count,
                    })
                })
                .collect();
            clear_err();
            to_c(&serde_json::Value::Array(arr).to_string())
        }
        Err(e) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //桶列表

/// 自检：在临时桶里跑一遍「写 → 读 → 对账 → 删」，返回 JSON，需 [`bx_free`]
///
/// 只证一件事：内核真被链接进来，且能在给定数据根上完成一次完整的加解密落盘。
///
/// # Safety
/// `root` 必须是有效的 NUL 结尾字符串，且目录已存在。
#[no_mangle]
pub unsafe extern "C" fn bx_selfcheck(root: *const c_char) -> *mut c_char {
    let res = (|| -> BxResult<serde_json::Value> {
        let h = open_default(&read_str(root)?)?;
        let k = h.as_ref();
        let probe = crate::core::base::clock::now_ms().to_string();
        k.vault.put(SELFTEST_BUCKET, "probe", &probe)?;
        let back = k.vault.get(SELFTEST_BUCKET, "probe")?;
        let write_ok = back.as_deref() == Some(probe.as_str());
        k.vault.remove(SELFTEST_BUCKET, "probe")?;
        //删完再数一遍，确认没留垃圾
        let left = k.vault.snapshot(SELFTEST_BUCKET)?.len();
        Ok(serde_json::json!({
            "ok": write_ok && left == 0,
            "version": crate::VERSION,
            "schema": crate::SCHEMA,
            "write": write_ok,
            "left": left,
            "backend": k.vault.backend(),
        }))
    })();
    match res {
        Ok(v) => {
            clear_err();
            to_c(&v.to_string())
        }
        Err(e) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //自检

/// 读配置项，返回 JSON 文本（数字/布尔/字符串已带引号），没有就返回 null
///
/// # Safety
/// 同 [`bx_get`]。
#[no_mangle]
pub unsafe extern "C" fn bx_prefs_get(h: *mut KernelHandle, page: *const c_char, key: *const c_char) -> *mut c_char {
    if h.is_null() {
        fail(&BxError::Invalid("句柄为空".into()));
        return ptr::null_mut();
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    match (read_str(page), read_str(key)) {
        (Ok(p), Ok(n)) => match k.prefs.get(&p, &n) {
            Ok(Some(v)) => {
                clear_err();
                to_c(&v.to_string())
            }
            Ok(None) => {
                clear_err();
                ptr::null_mut()
            }
            Err(e) => {
                fail(&e);
                ptr::null_mut()
            }
        },
        (Err(e), _) | (_, Err(e)) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //读配置

/// 写配置项：`json` 必须是合法 JSON 值文本（`null` 等于删除）
///
/// # Safety
/// 同 [`bx_get`]。
#[no_mangle]
pub unsafe extern "C" fn bx_prefs_set(
    h: *mut KernelHandle,
    page: *const c_char,
    key: *const c_char,
    json: *const c_char,
) -> c_int {
    if h.is_null() {
        return fail(&BxError::Invalid("句柄为空".into()));
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    let (p, n, raw) = match (read_str(page), read_str(key), read_str(json)) {
        (Ok(p), Ok(n), Ok(raw)) => (p, n, raw),
        (Err(e), _, _) | (_, Err(e), _) | (_, _, Err(e)) => return fail(&e),
    };
    match serde_json::from_str::<serde_json::Value>(&raw) {
        Ok(v) => match k.prefs.set(&p, &n, v) {
            Ok(()) => {
                clear_err();
                0
            }
            Err(e) => fail(&e),
        },
        Err(_) => fail(&BxError::Invalid("配置值不是合法 JSON".into())),
    }
} //写配置

/// 一整页配置（JSON 对象），需 [`bx_free`]
///
/// # Safety
/// 同 [`bx_get`]。
#[no_mangle]
pub unsafe extern "C" fn bx_prefs_page(h: *mut KernelHandle, page: *const c_char) -> *mut c_char {
    if h.is_null() {
        fail(&BxError::Invalid("句柄为空".into()));
        return ptr::null_mut();
    }
    // SAFETY: h 由调用方保证有效
    let k = unsafe { &*h };
    match read_str(page) {
        Ok(p) => match k.prefs.page(&p) {
            Ok(map) => {
                clear_err();
                to_c(&serde_json::Value::Object(map.into_iter().collect()).to_string())
            }
            Err(e) => {
                fail(&e);
                ptr::null_mut()
            }
        },
        Err(e) => {
            fail(&e);
            ptr::null_mut()
        }
    }
} //整页配置

/// 释放本库返回的字符串
///
/// # Safety
/// `p` 必须来自本库（或为 null），且只能释放一次。
#[no_mangle]
pub unsafe extern "C" fn bx_free(p: *mut c_char) {
    if p.is_null() {
        return;
    }
    // SAFETY: p 由 CString::into_raw 产生
    drop(unsafe { CString::from_raw(p) });
} //释放

/// 最近一次错误的数值码（线程内）；0 表示上一次调用成功
#[no_mangle]
pub extern "C" fn bx_last_code() -> c_int {
    LAST_CODE.with(|c| c.get())
} //错误码

/// 最近一次错误的文本（线程内），不需要释放
#[no_mangle]
pub extern "C" fn bx_last_error() -> *const c_char {
    LAST_ERR.with(|c| c.borrow().as_ptr())
} //错误文本

/// 成功码，便于宿主写 if code != bx_ok()
#[no_mangle]
pub extern "C" fn bx_ok() -> c_int {
    ErrorCode::Ok.as_i32()
} //成功