/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:30
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 崩溃一致性与损坏自愈。
//!
//! 覆盖三类真实故障：
//! 1. 写临时文件时失败（磁盘满 / 权限变化）→ 旧档必须完好；
//! 2. 改名之前失败（进程被杀）→ 旧档必须完好，且不留临时文件；
//! 3. 文件被截断或篡改（掉电、外部工具改坏）→ 用 `.prev` 上一代自愈；
//!    上一代也没有时必须**报损坏**，而不是当成空表再写回去。

use std::sync::Arc;

use baicodex::core::base::fsx;
use baicodex::core::base::sealed::SealedStore;
use baicodex::core::base::store::{FsStore, Store};
use baicodex::core::feature::vault::Vault;
use baicodex::core::security::{MasterKey, Sealer};

fn open(dir: &std::path::Path) -> (Vault, Arc<dyn Store>) {
    let key = MasterKey::from_bytes(&[9u8; 32]).unwrap();
    let fs = FsStore::open(dir, false).unwrap();
    let sealed: Arc<dyn Store> = Arc::new(SealedStore::new(
        Arc::new(fs) as Arc<dyn Store>,
        Arc::new(Sealer::new(key)),
    ));
    (Vault::new(Arc::clone(&sealed)), sealed)
}

fn bucket_file(dir: &std::path::Path) -> std::path::PathBuf {
    dir.join("vault").join("apikeys.bx")
}

#[test]
fn write_failure_before_commit_keeps_old_value() {
    let tmp = tempfile::tempdir().unwrap();
    let (v, _) = open(tmp.path());
    v.put("apikeys", "token", "v1").unwrap();

    fsx::arm_fault("after_tmp_sync");
    let e = v.put("apikeys", "token", "v2").unwrap_err();
    fsx::disarm_fault();
    assert!(
        matches!(e, baicodex::BxError::Fault(_)),
        "应报注入的故障，实际 {e:?}"
    );

    // 内存与磁盘都必须还是 v1
    assert_eq!(v.get("apikeys", "token").unwrap().as_deref(), Some("v1"));
    let (v2, _) = open(tmp.path());
    assert_eq!(v2.get("apikeys", "token").unwrap().as_deref(), Some("v1"));

    // 不能留下临时文件
    let leftovers: Vec<_> = std::fs::read_dir(tmp.path().join("vault"))
        .unwrap()
        .filter_map(Result::ok)
        .map(|e| e.file_name().to_string_lossy().to_string())
        .filter(|n| n.contains(".tmp-"))
        .collect();
    assert!(leftovers.is_empty(), "残留临时文件: {leftovers:?}");
}

#[test]
fn crash_before_rename_keeps_old_value() {
    let tmp = tempfile::tempdir().unwrap();
    let (v, _) = open(tmp.path());
    v.put("apikeys", "token", "v1").unwrap();

    fsx::arm_fault("before_rename");
    assert!(v.put("apikeys", "token", "v2").is_err());
    fsx::disarm_fault();

    let (v2, _) = open(tmp.path());
    assert_eq!(v2.get("apikeys", "token").unwrap().as_deref(), Some("v1"));
}

#[test]
fn truncated_head_self_heals_from_prev() {
    let tmp = tempfile::tempdir().unwrap();
    let (v, _) = open(tmp.path());
    v.put("apikeys", "token", "v1").unwrap();
    v.put("apikeys", "token", "v2").unwrap(); // 此时 .prev = v1

    // 模拟掉电写坏：主文件截断成一半
    let f = bucket_file(tmp.path());
    let bytes = std::fs::read(&f).unwrap();
    std::fs::write(&f, &bytes[..bytes.len() / 2]).unwrap();

    // 新实例强制重新从盘上读
    let (v2, _) = open(tmp.path());
    assert_eq!(
        v2.get("apikeys", "token").unwrap().as_deref(),
        Some("v1"),
        "损坏的主文件应退回上一代"
    );
}

#[test]
fn garbage_head_self_heals_from_prev() {
    let tmp = tempfile::tempdir().unwrap();
    let (v, _) = open(tmp.path());
    v.put("apikeys", "token", "v1").unwrap();
    v.put("apikeys", "token", "v2").unwrap();

    let f = bucket_file(tmp.path());
    let mut bytes = std::fs::read(&f).unwrap();
    // 改掉密文中间一个字节：整体摘要必然不符
    let mid = bytes.len() / 2;
    bytes[mid] ^= 0xff;
    std::fs::write(&f, &bytes).unwrap();

    let (v2, _) = open(tmp.path());
    assert_eq!(v2.get("apikeys", "token").unwrap().as_deref(), Some("v1"));
}

#[test]
fn corrupt_without_prev_is_locked_not_wiped() {
    let tmp = tempfile::tempdir().unwrap();
    let (v, _) = open(tmp.path());
    v.put("apikeys", "token", "v1").unwrap();
    v.put("apikeys", "token", "v1b").unwrap(); // 产生 .prev，才有「上一代」可删
    std::fs::remove_file(fsx::prev_path(&bucket_file(tmp.path()))).unwrap();

    // 直接把主文件写成垃圾
    std::fs::write(bucket_file(tmp.path()), b"not a sealed file at all").unwrap();

    let (v2, _) = open(tmp.path());
    // 读不出来可以接受；关键是**不许把空表写回去**
    let read = v2.get("apikeys", "token");
    assert!(read.is_err(), "损坏且无上一代时应报错，实际 {read:?}");

    let put = v2.put("apikeys", "token", "v2");
    assert!(put.is_err(), "桶已只读，写入必须被拒绝");

    // 盘上仍是原垃圾数据，没有被覆盖成「空库」
    let on_disk = std::fs::read(bucket_file(tmp.path())).unwrap();
    assert_eq!(on_disk, b"not a sealed file at all");
}
