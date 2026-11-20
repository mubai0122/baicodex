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

//! 加解密一致性：
//! - 换密钥后必须报 `WrongKey` 并**保留**原密文（不许覆盖）；
//! - 密文挪到别的桶/条目必须解不开（AAD 绑定）；
//! - 盘上不能出现明文；
//! - 密钥轮换后新旧副本各自可读。

use std::sync::Arc;

use baicodex::core::base::sealed::SealedStore;
use baicodex::core::base::store::{FsStore, Store};
use baicodex::core::feature::vault::Vault;
use baicodex::core::security::{MasterKey, Sealer};

fn vault_with(dir: &std::path::Path, key_byte: u8) -> Vault {
    let key = MasterKey::from_bytes(&[key_byte; 32]).unwrap();
    let fs = FsStore::open(dir, false).unwrap();
    let sealed: Arc<dyn Store> = Arc::new(SealedStore::new(
        Arc::new(fs) as Arc<dyn Store>,
        Arc::new(Sealer::new(key)),
    ));
    Vault::new(sealed)
}

#[test]
fn wrong_key_is_reported_and_ciphertext_preserved() {
    let tmp = tempfile::tempdir().unwrap();
    vault_with(tmp.path(), 1)
        .put("apikeys", "token", "sk-secret")
        .unwrap();
    let path = tmp.path().join("vault").join("apikeys.bx");
    let before = std::fs::read(&path).unwrap();

    // 换了一把钥匙（相当于用户改了 local.properties 里的种子）
    let v2 = vault_with(tmp.path(), 2);
    let e = v2.get("apikeys", "token").unwrap_err();
    assert!(matches!(e, baicodex::BxError::WrongKey), "实际 {e:?}");

    // 关键：写入必须被拒绝，旧密文原样保留
    let e = v2.put("apikeys", "token", "新值").unwrap_err();
    assert!(matches!(e, baicodex::BxError::WrongKey), "实际 {e:?}");
    assert_eq!(std::fs::read(&path).unwrap(), before, "旧密文被改写了");
}

#[test]
fn ciphertext_moved_to_another_bucket_is_rejected() {
    let tmp = tempfile::tempdir().unwrap();
    let v = vault_with(tmp.path(), 3);
    v.put("apikeys", "token", "sk-secret").unwrap();
    let src = tmp.path().join("vault").join("apikeys.bx");
    let dst = tmp.path().join("vault").join("secrets.bx");
    std::fs::copy(&src, &dst).unwrap();

    // 同一把钥匙、同一个文件内容，只是换了个桶名
    let v2 = vault_with(tmp.path(), 3);
    let e = v2.get("secrets", "token").unwrap_err();
    assert!(
        matches!(e, baicodex::BxError::Corrupt(_)),
        "桶名参与 AAD，必须解不开，实际 {e:?}"
    );
}

#[test]
fn tampered_header_is_rejected() {
    let tmp = tempfile::tempdir().unwrap();
    let v = vault_with(tmp.path(), 4);
    v.put("apikeys", "alpha", "sk-a").unwrap();
    let path = tmp.path().join("vault").join("apikeys.bx");

    let mut bytes = std::fs::read(&path).unwrap();
    bytes[6] = 9; // 算法字段改成未知值
    std::fs::write(&path, &bytes).unwrap();

    let v2 = vault_with(tmp.path(), 4);
    let e = v2.get("apikeys", "alpha").unwrap_err();
    assert!(
        matches!(
            e,
            baicodex::BxError::Corrupt(_) | baicodex::BxError::Version(_)
        ),
        "头部被改必须拒绝，实际 {e:?}"
    );
}

#[test]
fn entry_names_are_validated() {
    let tmp = tempfile::tempdir().unwrap();
    let v = vault_with(tmp.path(), 4);
    v.put("apikeys", "ok_name-1", "v").unwrap();
    for bad in ["", "../x", "a/b"] {
        let e = v.put("apikeys", bad, "v").unwrap_err();
        assert!(
            matches!(e, baicodex::BxError::BadKey(_)),
            "{bad:?} 应被拒绝，实际 {e:?}"
        );
    }
}

#[test]
fn no_plaintext_on_disk() {
    let tmp = tempfile::tempdir().unwrap();
    let v = vault_with(tmp.path(), 5);
    let secret = "sk-plaintext-must-not-appear-0123456789";
    v.put("apikeys", "openai", secret).unwrap();
    v.put("apikeys", "github", "ghp_also_secret").unwrap();

    let mut hits = Vec::new();
    for ent in walk(tmp.path()) {
        let bytes = std::fs::read(&ent).unwrap_or_default();
        if contains(&bytes, secret.as_bytes()) {
            hits.push(ent.display().to_string());
        }
    }
    assert!(hits.is_empty(), "明文出现在: {hits:?}");
}

#[test]
fn key_rotation_keeps_data_readable() {
    let tmp = tempfile::tempdir().unwrap();
    let old = vault_with(tmp.path(), 6);
    old.put("apikeys", "token", "sk-old").unwrap();

    // 新钥匙 + 新目录，逐桶搬运（等价于 rotate_to）
    let new_dir = tmp.path().join("new");
    let new_key = MasterKey::from_bytes(&[7u8; 32]).unwrap();
    let fs = FsStore::open(&new_dir, false).unwrap();
    let new_store: Arc<dyn Store> = Arc::new(SealedStore::new(
        Arc::new(fs) as Arc<dyn Store>,
        Arc::new(Sealer::new(new_key)),
    ));
    let n = old.rotate_to(Arc::clone(&new_store)).unwrap();
    assert_eq!(n, 1);

    let v = Vault::new(new_store);
    assert_eq!(
        v.get("apikeys", "token").unwrap().as_deref(),
        Some("sk-old")
    );

    // 旧库仍然可读（回滚路径）
    assert_eq!(
        old.get("apikeys", "token").unwrap().as_deref(),
        Some("sk-old")
    );
}

#[test]
fn fingerprint_follows_key() {
    let a = Sealer::new(MasterKey::from_bytes(&[1u8; 32]).unwrap());
    let b = Sealer::new(MasterKey::from_bytes(&[2u8; 32]).unwrap());
    assert_eq!(a.fingerprint().len(), 16);
    assert_ne!(a.fingerprint(), b.fingerprint());
}

fn walk(dir: &std::path::Path) -> Vec<std::path::PathBuf> {
    let mut out = Vec::new();
    let Ok(rd) = std::fs::read_dir(dir) else {
        return out;
    };
    for e in rd.flatten() {
        let p = e.path();
        if p.is_dir() {
            out.extend(walk(&p));
        } else {
            out.push(p);
        }
    }
    out
}

fn contains(hay: &[u8], needle: &[u8]) -> bool {
    hay.windows(needle.len()).any(|w| w == needle)
}
