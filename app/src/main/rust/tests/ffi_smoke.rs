//! FFI 冒烟测试：Android 与 Windows 共用同一套 C ABI，这里在 Linux 上先把它跑通。

use std::ffi::{c_char, CStr, CString};
use std::ptr;

use baicodex::ffi::*;

unsafe fn cstr(p: *const c_char) -> String {
    if p.is_null() {
        return String::new();
    }
    CStr::from_ptr(p).to_string_lossy().to_string()
}

#[test]
fn caps_json_is_well_formed() {
    let p = bx_caps_json();
    assert!(!p.is_null());
    let s = unsafe { cstr(p) };
    unsafe { bx_free(p) };
    let v: serde_json::Value = serde_json::from_str(&s).unwrap();
    assert!(v.get("os").is_some());
    assert!(v.get("liquid_glass").is_some());
    // Linux 上不支持液态玻璃，必须明确给 false 而不是缺字段
    assert_eq!(v["liquid_glass"], serde_json::json!(false));
}

#[test]
fn open_put_get_roundtrip() {
    let tmp = tempfile::tempdir().unwrap();
    let root = CString::new(tmp.path().to_string_lossy().as_bytes()).unwrap();
    let key = [42u8; 32];
    let h = unsafe { bx_open(root.as_ptr(), key.as_ptr(), 32) };
    assert!(!h.is_null(), "open 失败: {}", unsafe {
        cstr(bx_last_error())
    });

    let bucket = CString::new("apikeys").unwrap();
    let name = CString::new("openai").unwrap();
    let val = CString::new("sk-test").unwrap();
    let rc = unsafe { bx_put(h, bucket.as_ptr(), name.as_ptr(), val.as_ptr()) };
    assert_eq!(rc, 0, "put 失败: {}", unsafe { cstr(bx_last_error()) });

    let got = unsafe { bx_get(h, bucket.as_ptr(), name.as_ptr()) };
    assert_eq!(unsafe { cstr(got) }, "sk-test");
    unsafe { bx_free(got) };

    let snap = unsafe { bx_snapshot(h, bucket.as_ptr()) };
    let s = unsafe { cstr(snap) };
    unsafe { bx_free(snap) };
    assert_eq!(s, "{\"openai\":\"sk-test\"}");

    let buckets = unsafe { bx_buckets(h) };
    let b = unsafe { cstr(buckets) };
    unsafe { bx_free(buckets) };
    assert!(b.contains("apikeys"));

    assert_eq!(unsafe { bx_del(h, bucket.as_ptr(), name.as_ptr()) }, 0);
    let gone = unsafe { bx_get(h, bucket.as_ptr(), name.as_ptr()) };
    assert!(gone.is_null(), "删除后应读不到");

    unsafe { bx_close(h) };
}

#[test]
fn open_rejects_bad_key_length() {
    let tmp = tempfile::tempdir().unwrap();
    let root = CString::new(tmp.path().to_string_lossy().as_bytes()).unwrap();
    let key = [0u8; 16];
    let h = unsafe { bx_open(root.as_ptr(), key.as_ptr(), 16) };
    assert!(h.is_null());
    assert!(!unsafe { cstr(bx_last_error()) }.is_empty());
}

#[test]
fn null_handle_is_reported_not_crashing() {
    let bucket = CString::new("apikeys").unwrap();
    let name = CString::new("x").unwrap();
    let p = unsafe { bx_get(ptr::null_mut(), bucket.as_ptr(), name.as_ptr()) };
    assert!(p.is_null());
    assert!(!unsafe { cstr(bx_last_error()) }.is_empty());
}
