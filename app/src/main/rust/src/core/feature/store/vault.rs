/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:28
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 凭据库：全内核**唯一**的凭据写入路径。
//!
//! # 旧实现为什么会掉存储（已复现，见 `tests/lost_update.rs`）
//!
//! 旧 `Vault`（Kotlin）长这样：
//!
//! ```kotlin
//! fun put(key, value, file) {
//!     ensure(file)                       // 只在首次访问时 @Synchronized
//!     val m = cache.getOrPut(file) { HashMap() }
//!     m[key] = value                     // ← 改的是普通 HashMap，没持锁
//!     flush(file)                        // ← flush 本身 @Synchronized
//! }
//! ```
//!
//! `flush` 加了锁、`m[key] = value` 没有，于是两个并发 `put` 会：
//! 1. 同时改 `HashMap`（未定义行为，Android 上偶发死循环或丢条目）；
//! 2. A 改完 → B 改完 → A 落盘（含 A+B）→ B 落盘（也含 A+B，看似没事），
//!    但若 B 的落盘发生在 A 之前、且 B 的快照早于 A 的修改，A 的条目就被**静默丢掉**；
//! 3. `flush` 里遍历 `cache[file]` 时另一个线程正在写，直接 `ConcurrentModificationException`，
//!    被 `runCatching` 吞掉 → 这一次写彻底消失。
//!
//! 更隐蔽的一条：`ensure` 用 `runCatching { nativeLoad(...) }` 吞掉**瞬时 IO 错误**，
//! 把「读失败」当成「全新安装」，随后一次 `flush` 就用空表覆盖了还能救的密文。
//!
//! # 现在的做法
//!
//! - 一把 `RwLock` 同时保护「内存表」与「落盘」：`put` 在**写锁内**完成改内存 + 落盘，
//!   失败则回滚内存，内存与磁盘永远一致；
//! - 读失败**不降级**：只有 `None`（文件确实不存在）才算全新安装；`WrongKey` / `Corrupt`
//!   进只读态，`Io` 直接上抛，绝不把空表写回去。

use std::collections::BTreeMap;
use std::sync::{Arc, RwLock, RwLockReadGuard, RwLockWriteGuard};

use serde::{Deserialize, Serialize};

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::key::Key;
use crate::core::base::store::Store;

/// 凭据桶在存储里的 scope。
pub const SCOPE: &str = "vault";

/// 桶的可用状态。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BucketState {
    /// 正常可读写。
    Ready,
    /// 未加载（首次访问时才解密）。
    Unloaded,
    /// 只读：密文是别的密钥写的，或文件损坏且无上一代。
    Locked(&'static str),
}

/// 供 UI 诊断页展示的桶信息。
#[derive(Debug, Clone)]
pub struct BucketInfo {
    /// 桶名。
    pub name: String,
    /// 状态。
    pub state: BucketState,
    /// 条目数。
    pub count: usize,
}

/// 桶被锁定的原因。带着原因走，UI 才能告诉用户「是换了种子还是文件坏了」。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum LockReason {
    WrongKey,
    Corrupt,
    NewerSchema,
    BadPayload,
}

impl LockReason {
    fn why(self) -> &'static str {
        match self {
            LockReason::WrongKey => "密文由另一把密钥写入",
            LockReason::Corrupt => "密文损坏且无可用上一代",
            LockReason::NewerSchema => "storage schema 过新",
            LockReason::BadPayload => "密文解出的不是合法凭据表",
        }
    }

    fn err(self) -> BxError {
        match self {
            LockReason::WrongKey => BxError::WrongKey,
            LockReason::Corrupt => BxError::Corrupt("密文损坏且无可用上一代"),
            LockReason::NewerSchema => BxError::Version(crate::SCHEMA as u16),
            LockReason::BadPayload => BxError::Corrupt("凭据表格式非法"),
        }
    }
}

#[derive(Default)]
struct Bucket {
    items: BTreeMap<String, String>,
    loaded: bool,
    locked: Option<LockReason>,
}

impl Bucket {
    fn state(&self) -> BucketState {
        match (self.loaded, self.locked) {
            (_, Some(why)) => BucketState::Locked(why.why()),
            (true, None) => BucketState::Ready,
            (false, None) => BucketState::Unloaded,
        }
    }
}

#[derive(Serialize)]
struct PayloadRef<'a> {
    v: u32,
    items: &'a BTreeMap<String, String>,
}

#[derive(Deserialize)]
struct Payload {
    v: u32,
    #[serde(default)]
    items: BTreeMap<String, String>,
}

/// 凭据库。
pub struct Vault {
    store: Arc<dyn Store>,
    state: RwLock<BTreeMap<String, Bucket>>,
}

impl Vault {
    /// 在给定后端上建库（通常是 [`crate::core::base::sealed::SealedStore`]）。
    pub fn new(store: Arc<dyn Store>) -> Self {
        Self {
            store,
            state: RwLock::new(BTreeMap::new()),
        }
    }

    /// 后端标识，诊断用。
    pub fn backend(&self) -> String {
        self.store.root_label()
    }

    fn rd(&self) -> BxResult<RwLockReadGuard<'_, BTreeMap<String, Bucket>>> {
        self.state
            .read()
            .map_err(|_| BxError::Invalid("凭据库锁中毒".into()))
    }

    fn wr(&self) -> BxResult<RwLockWriteGuard<'_, BTreeMap<String, Bucket>>> {
        self.state
            .write()
            .map_err(|_| BxError::Invalid("凭据库锁中毒".into()))
    }

    /// 首次访问时解密进内存。**只有确认不存在**才算全新安装。
    fn ensure_loaded(&self, bucket: &str) -> BxResult<()> {
        {
            let st = self.rd()?;
            if st.get(bucket).map(|b| b.loaded).unwrap_or(false) {
                return Ok(());
            }
        }
        let mut st = self.wr()?;
        let entry = st.entry(bucket.to_string()).or_default();
        if entry.loaded {
            return Ok(());
        }
        let key = Key::new(SCOPE, bucket)?;
        match self.store.read(&key) {
            Ok(Some(bytes)) => match serde_json::from_slice::<Payload>(&bytes) {
                Ok(p) if p.v <= crate::SCHEMA => {
                    entry.items = p.items;
                    entry.loaded = true;
                }
                Ok(_) => {
                    // 未来版本的文件：不认识就别动它，只读更安全
                    entry.loaded = true;
                    entry.locked = Some(LockReason::NewerSchema);
                }
                Err(_) => {
                    entry.loaded = true;
                    entry.locked = Some(LockReason::BadPayload);
                }
            },
            // 文件确实不存在 = 全新安装，正常可写
            Ok(None) => entry.loaded = true,
            // 钥匙不对：文件里是有效密文，只是不是这把钥匙。禁止覆盖，等用户换回正确种子
            Err(BxError::WrongKey) => {
                entry.loaded = true;
                entry.locked = Some(LockReason::WrongKey);
            }
            // 损坏且上一代也解不开：同样只读
            Err(BxError::Corrupt(_)) => {
                entry.loaded = true;
                entry.locked = Some(LockReason::Corrupt);
            }
            // 瞬时 IO 错误：不标 loaded，下次重试；绝不返回「空桶」
            Err(e) => return Err(e),
        }
        Ok(())
    }

    /// 序列化并落盘。调用方必须持有写锁。
    fn persist(&self, bucket: &str, items: &BTreeMap<String, String>) -> BxResult<()> {
        let payload = PayloadRef {
            v: crate::SCHEMA,
            items,
        };
        let bytes = serde_json::to_vec(&payload)
            .map_err(|_| BxError::Invalid("凭据表序列化失败".into()))?;
        self.store.write(&Key::new(SCOPE, bucket)?, &bytes)
    }

    /// 读一条；不存在返回 `None`。
    pub fn get(&self, bucket: &str, name: &str) -> BxResult<Option<String>> {
        self.ensure_loaded(bucket)?;
        let st = self.rd()?;
        let b = st.get(bucket).ok_or(BxError::NotFound)?;
        if let Some(why) = b.locked {
            return Err(why.err());
        }
        Ok(b.items.get(name).cloned())
    }

    /// 读一条，读不到就给空串（与旧 API 语义一致，方便直接替换）。
    pub fn value(&self, bucket: &str, name: &str) -> String {
        self.get(bucket, name).ok().flatten().unwrap_or_default()
    }

    /// 写一条；`value` 为空等于删除。
    ///
    /// 内存修改与落盘在同一把写锁内完成；落盘失败则回滚内存，调用方拿到真实错误。
    pub fn put(&self, bucket: &str, name: &str, value: &str) -> BxResult<()> {
        // 条目名与存储键同一套规则：不含分隔符、不含 `..`，从源头断掉「写歪路径」与
        // 「名字里带控制字符」两类问题
        crate::core::base::key::valid_name(name)?;
        self.ensure_loaded(bucket)?;
        let mut st = self.wr()?;
        let b = st.get_mut(bucket).ok_or(BxError::NotFound)?;
        if let Some(why) = b.locked {
            return Err(why.err());
        }
        let prev = if value.is_empty() {
            b.items.remove(name)
        } else {
            b.items.insert(name.to_string(), value.to_string())
        };
        if let Err(e) = self.persist(bucket, &b.items) {
            // 关键：盘上没写成功，内存也要退回去，否则下次读到的值与盘上不一致
            match prev {
                Some(v) => {
                    b.items.insert(name.to_string(), v);
                }
                None => {
                    b.items.remove(name);
                }
            }
            return Err(e);
        }
        Ok(())
    }

    /// 批量写：只落盘一次。
    pub fn put_many<'a, I>(&self, bucket: &str, pairs: I) -> BxResult<usize>
    where
        I: IntoIterator<Item = (&'a str, &'a str)>,
    {
        self.ensure_loaded(bucket)?;
        let mut st = self.wr()?;
        let b = st.get_mut(bucket).ok_or(BxError::NotFound)?;
        if let Some(why) = b.locked {
            return Err(why.err());
        }
        let backup = b.items.clone();
        let mut n = 0usize;
        for (k, v) in pairs {
            if k.is_empty() {
                continue;
            }
            if v.is_empty() {
                b.items.remove(k);
            } else {
                b.items.insert(k.to_string(), v.to_string());
            }
            n += 1;
        }
        if let Err(e) = self.persist(bucket, &b.items) {
            b.items = backup;
            return Err(e);
        }
        Ok(n)
    }

    /// 删一条。
    pub fn remove(&self, bucket: &str, name: &str) -> BxResult<()> {
        self.put(bucket, name, "")
    }

    /// 全量快照。
    pub fn snapshot(&self, bucket: &str) -> BxResult<BTreeMap<String, String>> {
        self.ensure_loaded(bucket)?;
        let st = self.rd()?;
        let b = st.get(bucket).ok_or(BxError::NotFound)?;
        if b.locked.is_some() {
            return Ok(BTreeMap::new());
        }
        Ok(b.items.clone())
    }

    /// 把历史明文一次性搬进来，返回真正变化的条数。
    pub fn adopt(&self, bucket: &str, legacy: &BTreeMap<String, String>) -> BxResult<usize> {
        self.ensure_loaded(bucket)?;
        let mut st = self.wr()?;
        let b = st.get_mut(bucket).ok_or(BxError::NotFound)?;
        if let Some(why) = b.locked {
            return Err(why.err());
        }
        let backup = b.items.clone();
        let mut n = 0usize;
        for (k, v) in legacy {
            if v.is_empty() {
                continue;
            }
            if b.items.get(k) != Some(v) {
                b.items.insert(k.clone(), v.clone());
                n += 1;
            }
        }
        if n == 0 {
            return Ok(0);
        }
        if let Err(e) = self.persist(bucket, &b.items) {
            b.items = backup;
            return Err(e);
        }
        Ok(n)
    }

    /// 清空一个桶（连盘一起）。
    pub fn wipe(&self, bucket: &str) -> BxResult<()> {
        let mut st = self.wr()?;
        self.store.remove(&Key::new(SCOPE, bucket)?)?;
        if let Some(b) = st.get_mut(bucket) {
            b.items.clear();
            b.locked = None;
            b.loaded = true;
        }
        Ok(())
    }

    /// 列出所有桶及其状态。
    pub fn buckets(&self) -> BxResult<Vec<BucketInfo>> {
        let mut names = self.store.list(SCOPE)?;
        {
            let st = self.rd()?;
            for k in st.keys() {
                if !names.contains(k) {
                    names.push(k.clone());
                }
            }
        }
        names.sort();
        names.dedup();
        let mut out = Vec::with_capacity(names.len());
        for name in names {
            let st = self.rd()?;
            let (state, count) = match st.get(&name) {
                Some(b) => (b.state(), b.items.len()),
                None => (BucketState::Unloaded, 0),
            };
            out.push(BucketInfo { name, state, count });
        }
        Ok(out)
    }

    /// 用新的（已换钥的）后端重写全部桶，用于密钥轮换。
    ///
    /// 先读全量、再逐桶写到新后端；任何一桶失败立即中止并保留旧后端内容。
    pub fn rotate_to(&self, new_store: Arc<dyn Store>) -> BxResult<usize> {
        let names = self.store.list(SCOPE)?;
        let mut n = 0usize;
        for name in names {
            let key = Key::new(SCOPE, &name)?;
            let Some(bytes) = self.store.read(&key)? else {
                continue;
            };
            new_store.write(&key, &bytes)?;
            n += 1;
        }
        Ok(n)
    }
}
