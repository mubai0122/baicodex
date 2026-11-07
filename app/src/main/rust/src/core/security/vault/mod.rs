//! 凭据库。
//!
//! 设计目标：**模型永远读不到凭据原文**。
//!
//! 工具执行时如果需要 key（比如技能包声明了 `${secret:openai}`），由执行层按需取出、
//! 用完即弃，不写进日志、不写进消息、不进提示词。凭据本身只在这一层进内存。
//!
//! 落盘策略：
//! - guard 已解锁 → 整份文件 AES-256-GCM（v2），文件里连 key 名字都看不到；
//! - 未解锁 → 明文 JSON + 0600。这是**明确接受的降级**：没开加密时还要能给模型填 key。
//!   界面上必须提示"未加密"，不能假装安全。

pub mod bucket;

use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use crate::core::base::error::{BError, BResult};
use crate::core::security::secret;

pub use bucket::{valid_bucket, BucketData, Entry};

/// 常量的三个桶名。自定义桶也允许，但这三个有约定含义。
pub const BUCKET_API: &str = "api";
pub const BUCKET_USER: &str = "user";
pub const BUCKET_SSH: &str = "ssh";

/// 凭据库。
#[derive(Debug)]
pub struct Vault {
    base: PathBuf,
    cache: Mutex<BTreeMap<String, BucketData>>,
    dirty: Mutex<BTreeSet<String>>,
}

impl Vault {
    /// `base` 是数据根（`~/.baicode`），桶文件直接落在下面。
    pub fn new(base: impl AsRef<Path>) -> Self {
        Self {
            base: base.as_ref().to_path_buf(),
            cache: Mutex::new(BTreeMap::new()),
            dirty: Mutex::new(BTreeSet::new()),
        }
    }

    /// 桶文件路径。
    pub fn path_of(&self, bucket: &str) -> BResult<PathBuf> {
        if !valid_bucket(bucket) {
            return Err(BError::invalid(format!("桶名不合法：{bucket}")));
        }
        Ok(self.base.join(format!("vault-{bucket}.json")))
    }

    /// 读一个桶（带缓存）。文件不存在 = 空桶。
    fn bucket_data(&self, bucket: &str) -> BucketData {
        if let Ok(c) = self.cache.lock() {
            if let Some(d) = c.get(bucket) {
                return d.clone();
            }
        }
        let d = self.read_from_disk(bucket);
        if let Ok(mut c) = self.cache.lock() {
            c.insert(bucket.to_string(), d.clone());
        }
        d
    }

    fn read_from_disk(&self, bucket: &str) -> BucketData {
        let Ok(p) = self.path_of(bucket) else { return BucketData::new() };
        let Ok(bytes) = std::fs::read(&p) else { return BucketData::new() };
        if secret::is_encrypted(&bytes) {
            // 加密文件：解不开就当空桶（而不是报错覆盖），否则未解锁时一次误写就毁库
            return match secret::open(&bytes) {
                Ok(plain) => BucketData::decode(&plain).unwrap_or_default(),
                Err(_) => BucketData::new(),
            };
        }
        BucketData::decode(&bytes).unwrap_or_default()
    }

    /// 取一条。不存在返回空串（调用方的"没配 key"分支靠空串判断）。
    pub fn get(&self, bucket: &str, key: &str) -> String {
        self.bucket_data(bucket)
            .get(key)
            .map(|e| e.value.clone())
            .unwrap_or_default()
    }

    /// 取一条；要求非空，否则报"没配置"。
    pub fn require(&self, bucket: &str, key: &str) -> BResult<String> {
        let v = self.get(bucket, key);
        if v.trim().is_empty() {
            Err(BError::not_found(format!("凭据 {bucket}/{key} 还没配置")))
        } else {
            Ok(v)
        }
    }

    /// 写一条。值为空等于删除。
    pub fn put(&self, bucket: &str, key: &str, value: &str) -> BResult<()> {
        self.put_note(bucket, key, value, "")
    }

    /// 写一条并带备注。
    pub fn put_note(&self, bucket: &str, key: &str, value: &str, note: &str) -> BResult<()> {
        // 桶名会拼进文件名，必须先校验：`../evil` 这种名字不能落到磁盘上。
        self.path_of(bucket)?;
        let mut d = self.bucket_data(bucket);
        d.set(key, value, note);
        if let Ok(mut c) = self.cache.lock() {
            c.insert(bucket.to_string(), d);
        }
        if let Ok(mut s) = self.dirty.lock() {
            s.insert(bucket.to_string());
        }
        Ok(())
    }

    /// 删一条。
    pub fn remove(&self, bucket: &str, key: &str) -> BResult<()> {
        self.path_of(bucket)?;
        let mut d = self.bucket_data(bucket);
        if !d.remove(key) {
            return Ok(());
        }
        if let Ok(mut c) = self.cache.lock() {
            c.insert(bucket.to_string(), d);
        }
        if let Ok(mut s) = self.dirty.lock() {
            s.insert(bucket.to_string());
        }
        Ok(())
    }

    /// 批量并入（老版本明文迁移用）。已存在的键**不覆盖**——用户的现有配置优先。
    pub fn adopt(&self, bucket: &str, items: impl IntoIterator<Item = (String, String)>) -> BResult<usize> {
        self.path_of(bucket)?;
        let mut d = self.bucket_data(bucket);
        let mut n = 0;
        for (k, v) in items {
            if v.is_empty() || d.items.contains_key(&k) {
                continue;
            }
            d.set(&k, &v, "");
            n += 1;
        }
        if n > 0 {
            if let Ok(mut c) = self.cache.lock() {
                c.insert(bucket.to_string(), d);
            }
            if let Ok(mut s) = self.dirty.lock() {
                s.insert(bucket.to_string());
            }
        }
        Ok(n)
    }

    /// 列出一个桶里所有键（不返回值）。
    pub fn keys(&self, bucket: &str) -> Vec<String> {
        self.bucket_data(bucket).items.keys().cloned().collect()
    }

    /// 列出磁盘上所有桶名。
    pub fn buckets(&self) -> Vec<String> {
        let mut out = Vec::new();
        if let Ok(rd) = std::fs::read_dir(&self.base) {
            for e in rd.flatten() {
                let name = e.file_name().to_string_lossy().to_string();
                if let Some(rest) = name.strip_prefix("vault-") {
                    if let Some(b) = rest.strip_suffix(".json") {
                        if valid_bucket(b) {
                            out.push(b.to_string());
                        }
                    }
                }
            }
        }
        out.sort();
        out
    }

    /// 这个桶当前是不是加密的（读文件头，不看内存状态）。
    pub fn is_encrypted(&self, bucket: &str) -> bool {
        match self.path_of(bucket) {
            Ok(p) => std::fs::read(&p).map(|b| secret::is_encrypted(&b)).unwrap_or(false),
            Err(_) => false,
        }
    }

    /// 落盘一个桶。
    pub fn flush(&self, bucket: &str) -> BResult<()> {
        let data = match self.cache.lock() {
            Ok(c) => c.get(bucket).cloned(),
            Err(p) => p.into_inner().get(bucket).cloned(),
        };
        let Some(data) = data else { return Ok(()) };
        let path = self.path_of(bucket)?;
        if crate::core::security::guard::ready() {
            secret::save(&path, &data.encode())?;
        } else {
            // 明文：权限收紧到 0600。内容里只有 key 与值，没有别的。
            #[cfg(unix)]
            crate::core::base::atomic::write_secure(&path, data.encode(), 0o600)?;
            #[cfg(not(unix))]
            crate::core::base::atomic::write(&path, &data.encode())?;
        }
        if let Ok(mut s) = self.dirty.lock() {
            s.remove(bucket);
        }
        Ok(())
    }

    /// 落盘所有脏桶。进程退出、切后台、用户点"保存"时调用。
    pub fn flush_all(&self) -> BResult<()> {
        let list: Vec<String> = match self.dirty.lock() {
            Ok(s) => s.iter().cloned().collect(),
            Err(p) => p.into_inner().iter().cloned().collect(),
        };
        for b in list {
            self.flush(&b)?;
        }
        Ok(())
    }

    /// 把磁盘上所有能解开的桶重写成当前格式（开加密后调用一次）。
    pub fn rewrap_all(&self) -> BResult<usize> {
        let mut n = 0;
        for b in self.buckets() {
            let d = self.bucket_data(&b);
            if let Ok(mut c) = self.cache.lock() {
                c.insert(b.clone(), d);
            }
            if let Ok(mut s) = self.dirty.lock() {
                s.insert(b.clone());
            }
            self.flush(&b)?;
            n += 1;
        }
        Ok(n)
    }

    /// 清空全部凭据：既清内存也删文件。关掉加密、退出登录时用。
    pub fn wipe_all(&self) -> BResult<()> {
        if let Ok(mut c) = self.cache.lock() {
            c.clear();
        }
        if let Ok(mut s) = self.dirty.lock() {
            s.clear();
        }
        for b in self.buckets() {
            if let Ok(p) = self.path_of(&b) {
                let _ = std::fs::remove_file(p);
            }
        }
        Ok(())
    }

    /// 丢弃内存缓存，下次读重新从磁盘来（外部改了文件时用）。
    pub fn reload(&self) {
        if let Ok(mut c) = self.cache.lock() {
            c.clear();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmp() -> PathBuf {
        std::env::temp_dir().join(format!("bai-vault-{}", crate::core::base::ids::IdGen::hex(6)))
    }

    #[test]
    fn put_get_roundtrip() {
        let _g = crate::core::security::guard::test_lock();
        crate::core::security::guard::lock();
        let dir = tmp();
        let v = Vault::new(&dir);
        v.put(BUCKET_API, "openai", "sk-1").unwrap();
        v.flush_all().unwrap();
        let v2 = Vault::new(&dir);
        assert_eq!(v2.get(BUCKET_API, "openai"), "sk-1");
        assert!(!v2.is_encrypted(BUCKET_API));
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn encrypted_when_guard_ready() {
        let _g = crate::core::security::guard::test_lock();
        let dir = tmp();
        let g = crate::core::security::guard::ring();
        g.start("dev");
        g.unlock(&crate::core::security::guard::random_hex());
        let v = Vault::new(&dir);
        v.put(BUCKET_SSH, "host", "pw").unwrap();
        v.flush_all().unwrap();
        assert!(v.is_encrypted(BUCKET_SSH));
        let v2 = Vault::new(&dir);
        assert_eq!(v2.get(BUCKET_SSH, "host"), "pw");
        g.lock();
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn bad_bucket_name_rejected() {
        let v = Vault::new(tmp());
        assert!(v.put("../evil", "k", "v").is_err());
    }

    #[test]
    fn adopt_does_not_overwrite() {
        let v = Vault::new(tmp());
        v.put(BUCKET_API, "a", "new").unwrap();
        v.adopt(BUCKET_API, vec![("a".into(), "old".into()), ("b".into(), "2".into())]).unwrap();
        assert_eq!(v.get(BUCKET_API, "a"), "new");
        assert_eq!(v.get(BUCKET_API, "b"), "2");
    }
}
