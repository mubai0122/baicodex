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

//! 配置库：按「页」分文件的 KV，全部走统一 [`Store`]。
//!
//! 旧实现把配置拆在 `SharedPreferences`、`baicode/*.json`、`files/data/prefs/*.json`
//! 三处，同一项设置在不同入口读写不同副本，于是「设置改了但没生效」。
//! 这里一页一个文件、一把锁、一条写入路径，读写永远是同一份数据。

use std::collections::BTreeMap;
use std::sync::{Arc, RwLock};

use serde_json::Value;

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::key::Key;
use crate::core::base::store::Store;

/// 配置在存储里的 scope。
pub const SCOPE: &str = "prefs";

/// 配置库。
pub struct Prefs {
    store: Arc<dyn Store>,
    pages: RwLock<BTreeMap<String, BTreeMap<String, Value>>>,
}

impl Prefs {
    /// 建库。
    pub fn new(store: Arc<dyn Store>) -> Self {
        Self {
            store,
            pages: RwLock::new(BTreeMap::new()),
        }
    }

    fn load(&self, page: &str) -> BxResult<()> {
        {
            let p = self
                .pages
                .read()
                .map_err(|_| BxError::Invalid("配置锁中毒".into()))?;
            if p.contains_key(page) {
                return Ok(());
            }
        }
        let mut p = self
            .pages
            .write()
            .map_err(|_| BxError::Invalid("配置锁中毒".into()))?;
        if p.contains_key(page) {
            return Ok(());
        }
        let map = match self.store.read(&Key::new(SCOPE, page)?) {
            Ok(Some(bytes)) => serde_json::from_slice::<BTreeMap<String, Value>>(&bytes)
                // 配置坏掉不该让 App 起不来：退成空页，由上层提示「已重置」
                .unwrap_or_default(),
            Ok(None) => BTreeMap::new(),
            Err(BxError::WrongKey) | Err(BxError::Corrupt(_)) => BTreeMap::new(),
            Err(e) => return Err(e),
        };
        p.insert(page.to_string(), map);
        Ok(())
    }

    fn persist(&self, page: &str, map: &BTreeMap<String, Value>) -> BxResult<()> {
        let bytes =
            serde_json::to_vec(map).map_err(|_| BxError::Invalid("配置序列化失败".into()))?;
        self.store.write(&Key::new(SCOPE, page)?, &bytes)
    }

    /// 读一页的全量副本。
    pub fn page(&self, page: &str) -> BxResult<BTreeMap<String, Value>> {
        self.load(page)?;
        let p = self
            .pages
            .read()
            .map_err(|_| BxError::Invalid("配置锁中毒".into()))?;
        Ok(p.get(page).cloned().unwrap_or_default())
    }

    /// 读一个值。
    pub fn get(&self, page: &str, key: &str) -> BxResult<Option<Value>> {
        self.load(page)?;
        let p = self
            .pages
            .read()
            .map_err(|_| BxError::Invalid("配置锁中毒".into()))?;
        Ok(p.get(page).and_then(|m| m.get(key)).cloned())
    }

    /// 读字符串，缺省给 `None`。
    pub fn string(&self, page: &str, key: &str) -> Option<String> {
        self.get(page, key)
            .ok()
            .flatten()
            .and_then(|v| v.as_str().map(str::to_string))
    }

    /// 读布尔，缺省给 `default`。
    pub fn boolean(&self, page: &str, key: &str, default: bool) -> bool {
        self.get(page, key)
            .ok()
            .flatten()
            .and_then(|v| v.as_bool())
            .unwrap_or(default)
    }

    /// 读整数，缺省给 `default`。
    pub fn integer(&self, page: &str, key: &str, default: i64) -> i64 {
        self.get(page, key)
            .ok()
            .flatten()
            .and_then(|v| v.as_i64())
            .unwrap_or(default)
    }

    /// 写一个值；`Value::Null` 等于删除。
    pub fn set(&self, page: &str, key: &str, value: Value) -> BxResult<()> {
        self.set_many(page, [(key, value)])
    }

    /// 批量写，只落盘一次。
    pub fn set_many<'a, I>(&self, page: &str, pairs: I) -> BxResult<()>
    where
        I: IntoIterator<Item = (&'a str, Value)>,
    {
        self.load(page)?;
        let mut p = self
            .pages
            .write()
            .map_err(|_| BxError::Invalid("配置锁中毒".into()))?;
        let map = p.entry(page.to_string()).or_default();
        let backup = map.clone();
        for (k, v) in pairs {
            if v.is_null() {
                map.remove(k);
            } else {
                map.insert(k.to_string(), v);
            }
        }
        if let Err(e) = self.persist(page, map) {
            *map = backup;
            return Err(e);
        }
        Ok(())
    }

    /// 写字符串。
    pub fn set_string(&self, page: &str, key: &str, value: &str) -> BxResult<()> {
        self.set(page, key, Value::String(value.to_string()))
    }

    /// 写布尔。
    pub fn set_bool(&self, page: &str, key: &str, value: bool) -> BxResult<()> {
        self.set(page, key, Value::Bool(value))
    }

    /// 写整数。
    pub fn set_int(&self, page: &str, key: &str, value: i64) -> BxResult<()> {
        self.set(page, key, Value::Number(value.into()))
    }

    /// 删一项。
    pub fn remove(&self, page: &str, key: &str) -> BxResult<()> {
        self.set(page, key, Value::Null)
    }
}
