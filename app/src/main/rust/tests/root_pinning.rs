/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:31
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 数据根钉死：外置存储掉线时**不许**静默换根。
//!
//! 旧实现 `Paths.ensure()` 在外部存储不可用时返回私有目录，于是
//! `Store.read()` 从私有目录读（读不到）、`Store.why()` 又拒绝写，界面显示「空」，
//! 用户重填一次就写进了私有目录；等外置恢复，两边各有一份，互相覆盖。

use std::path::PathBuf;

use baicodex::core::base::store::FsStore;
use baicodex::core::base::Key;
use baicodex::core::base::Store;
use baicodex::core::platform::resolve_root;
use baicodex::BxError;

#[test]
fn resolve_refuses_when_no_candidate_exists() {
    let tmp = tempfile::tempdir().unwrap();
    let a = tmp.path().join("ext-not-mounted");
    let b = tmp.path().join("private");
    let e = resolve_root(&[a, b], true).unwrap_err();
    assert!(matches!(e, BxError::RootUnavailable(_)), "实际 {e:?}");
}

#[test]
fn resolve_picks_first_available_and_reports_fallback() {
    let tmp = tempfile::tempdir().unwrap();
    let a = tmp.path().join("ext");
    let b = tmp.path().join("private");
    std::fs::create_dir_all(&b).unwrap();
    let c = resolve_root(&[a, b.clone()], true).unwrap();
    assert_eq!(c.path, b);
    assert!(!c.preferred);
}

#[test]
fn root_disappearing_mid_session_errors_instead_of_switching() {
    let tmp = tempfile::tempdir().unwrap();
    let root = tmp.path().join("root");
    std::fs::create_dir_all(&root).unwrap();
    let store = FsStore::open(&root, true).unwrap();
    let key = Key::new("prefs", "ui").unwrap();
    store.write(&key, b"{}").unwrap();

    // 模拟外置存储被拔掉
    std::fs::remove_dir_all(&root).unwrap();

    let e = store.write(&key, b"{\"x\":1}").unwrap_err();
    assert!(matches!(e, BxError::RootUnavailable(_)), "实际 {e:?}");
    // 不许在别处偷偷建一个 root
    assert!(!root.exists(), "掉线后不应重建数据根");
}

#[test]
fn key_rejects_path_escape() {
    let tmp = tempfile::tempdir().unwrap();
    let store = FsStore::open(tmp.path(), false).unwrap();
    for bad in ["../etc/passwd", "a/b", "..", "", "x\\y"] {
        let k = Key::new("prefs", bad);
        assert!(k.is_err(), "非法名字应被拒绝: {bad:?}");
    }
    // 合法名字仍可用
    let ok = Key::new("prefs", "ui.theme_v2-dark@1").unwrap();
    assert_eq!(ok.rel(), "prefs/ui.theme_v2-dark@1");

    // 即使有人绕过构造函数，也不会真的写到根外：这里只验证公开 API 的防线
    let _ = store.list("prefs").unwrap();
}

#[test]
fn list_returns_written_keys_only() {
    let tmp = tempfile::tempdir().unwrap();
    let store = FsStore::open(tmp.path(), false).unwrap();
    for n in ["b", "a", "c"] {
        store.write(&Key::new("prefs", n).unwrap(), b"1").unwrap();
    }
    assert_eq!(store.list("prefs").unwrap(), vec!["a", "b", "c"]);
    assert_eq!(store.list("nope").unwrap(), Vec::<String>::new());
    let _ = PathBuf::new();
}
