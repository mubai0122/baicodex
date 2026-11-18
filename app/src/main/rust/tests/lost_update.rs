//! 复现「掉存储」（丢失写入），并证明新实现不再丢。
//!
//! 旧实现（`app/src/main/kotlin/b/ai/native/security/Vault.kt`）：
//!
//! ```kotlin
//! fun put(key: String, value: String, file: String = API) {
//!     ensure(file)                    // 仅首次访问时 @Synchronized
//!     val m = cache.getOrPut(file) { HashMap() }
//!     if (value.isEmpty()) m.remove(key) else m[key] = value   // ← 锁外改内存
//!     flush(file)                     // ← flush 自己 @Synchronized，但基于上面这份内存
//! }
//! ```
//!
//! 破坏点在「读—改—写」不是原子的：两个 `put` 各自读到同一份旧快照，
//! 各自插入自己的键，后写的那次用**自己的**快照整体覆盖 → 先写的那条消失。
//! Kotlin 侧还有第二层问题：`HashMap` 在锁外被并发修改，`flush` 遍历时抛
//! `ConcurrentModificationException`，被 `runCatching` 吞掉，这一次写彻底无效。
//!
//! 下面的 `LegacyVault` 忠实复刻了「读快照 → 改副本 → 整体写回」这条路径，
//! 唯一的还原手段是把两次读之间的调度点显式化（`Barrier`），否则测试会靠运气。

use std::collections::BTreeMap;
use std::sync::{Arc, Barrier, RwLock};
use std::thread;

use baicodex::core::base::sealed::SealedStore;
use baicodex::core::base::store::{FsStore, Store};
use baicodex::core::feature::vault::Vault;
use baicodex::core::security::{MasterKey, Sealer};

/// 旧实现的等价模型：内存表 + 每次写整体覆盖。
struct LegacyVault {
    map: RwLock<BTreeMap<String, String>>,
}

impl LegacyVault {
    fn new() -> Self {
        Self {
            map: RwLock::new(BTreeMap::new()),
        }
    }

    /// 对应 `cache.getOrPut(file)`：取当前内存表的**副本**。
    fn load(&self) -> BTreeMap<String, String> {
        self.map.read().unwrap().clone()
    }

    /// 对应 `flush(file)`：把副本整体写回（旧实现会在这里做加密落盘）。
    fn store(&self, next: BTreeMap<String, String>) {
        *self.map.write().unwrap() = next;
    }

    /// 旧版 `put` 的两段式：先读，再改，再整体写回。
    fn put(&self, k: &str, v: &str, barrier: Option<&Barrier>) {
        let mut next = self.load();
        if let Some(b) = barrier {
            // 这里就是真实的调度点：两个线程都已完成 load，才会继续往下
            b.wait();
        }
        next.insert(k.to_string(), v.to_string());
        self.store(next);
    }

    fn len(&self) -> usize {
        self.map.read().unwrap().len()
    }
}

#[test]
fn legacy_vault_loses_update_deterministically() {
    let v = Arc::new(LegacyVault::new());
    let barrier = Arc::new(Barrier::new(2));
    let mut hs = Vec::new();
    for (k, val) in [("api_key", "sk-a"), ("ssh_token", "ghp-b")] {
        let v = Arc::clone(&v);
        let b = Arc::clone(&barrier);
        hs.push(thread::spawn(move || v.put(k, val, Some(&b))));
    }
    for h in hs {
        h.join().unwrap();
    }
    // 两条都写成功、都没有报错，但只有一条留在库里 —— 这就是用户看到的「配置少了一条」
    assert_eq!(
        v.len(),
        1,
        "旧模型应当复现丢失（若这里等于 2，说明模型没有还原竞态）"
    );
}

#[test]
fn legacy_vault_loses_under_load() {
    // 不加栅栏也能丢：32 线程各写 50 条，几乎必然少于 1600 条
    let v = Arc::new(LegacyVault::new());
    let mut hs = Vec::new();
    for t in 0..32 {
        let v = Arc::clone(&v);
        hs.push(thread::spawn(move || {
            for i in 0..50 {
                v.put(&format!("k{t}-{i}"), "v", None);
            }
        }));
    }
    for h in hs {
        h.join().unwrap();
    }
    assert!(v.len() < 1600, "旧模型在高并发下应当丢写，实际 {}", v.len());
}

fn new_vault(dir: &std::path::Path) -> Vault {
    let key = MasterKey::from_bytes(&[7u8; 32]).unwrap();
    let fs = FsStore::open(dir, false).unwrap();
    let sealed: Arc<dyn Store> = Arc::new(SealedStore::new(
        Arc::new(fs) as Arc<dyn Store>,
        Arc::new(Sealer::new(key)),
    ));
    Vault::new(sealed)
}

#[test]
fn new_vault_never_loses_update() {
    let tmp = tempfile::tempdir().unwrap();
    let v = Arc::new(new_vault(tmp.path()));
    let mut hs = Vec::new();
    for t in 0..16 {
        let v = Arc::clone(&v);
        hs.push(thread::spawn(move || {
            for i in 0..100 {
                v.put("apikeys", &format!("k{t}-{i}"), &format!("v{t}-{i}"))
                    .expect("并发写不应失败");
            }
        }));
    }
    for h in hs {
        h.join().unwrap();
    }
    let snap = v.snapshot("apikeys").unwrap();
    assert_eq!(snap.len(), 1600, "新实现不允许丢写");
    for t in 0..16 {
        for i in 0..100 {
            let k = format!("k{t}-{i}");
            assert_eq!(
                snap.get(&k).map(String::as_str),
                Some(format!("v{t}-{i}").as_str())
            );
        }
    }
}

#[test]
fn new_vault_reports_write_failure_instead_of_swallowing() {
    // 旧实现的 runCatching 会把失败吞掉，调用方以为写成功了
    let tmp = tempfile::tempdir().unwrap();
    let v = new_vault(tmp.path());
    v.put("apikeys", "k", "v1").unwrap();
    v.put("apikeys", "", "x").unwrap_err(); // 空 key 必须报错，而不是静默忽略
    assert_eq!(v.get("apikeys", "k").unwrap().as_deref(), Some("v1"));
}
