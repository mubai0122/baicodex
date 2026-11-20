/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:27
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 加密装饰器：把任意 [`Store`] 变成「透明加密、写坏可退」的后端。
//!
//! 这一层是「加解密一致性」的关键：业务代码只看到 `Vec<u8>`，密钥、随机数、头部格式
//! 全部在这里统一处理，不存在「某个模块用 ECB 拼了个 IV 就写盘」的可能。

use std::sync::Arc;

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::key::Key;
use crate::core::base::store::Store;
use crate::core::security::sealer::Sealer;

/// 读到的是主文件还是上一代快照。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Source {
    /// 主文件。
    Head,
    /// `.prev` 上一代。
    Prev,
}

/// 加密存储：写入时密封，读取时校验并解封。
pub struct SealedStore {
    inner: Arc<dyn Store>,
    sealer: Arc<Sealer>,
    schema: u32,
}

impl SealedStore {
    /// 用给定后端与加解密器构造。
    pub fn new(inner: Arc<dyn Store>, sealer: Arc<Sealer>) -> Self {
        Self {
            inner,
            sealer,
            schema: crate::SCHEMA,
        }
    }

    /// 当前主密钥指纹。
    pub fn fingerprint(&self) -> String {
        self.sealer.fingerprint()
    }

    /// 读一条，并告知来源（主文件 / 上一代），用于诊断与自愈统计。
    ///
    /// 语义：
    /// - 主文件损坏 → 尝试 `.prev`，成功则返回 `Prev`（自愈）；
    /// - 主文件是**别的密钥**写的（[`BxError::WrongKey`]）→ 直接报错，**不**退 `.prev`：
    ///   上一代同样是那把钥匙写的，退过去只会掩盖「用户换了种子」这件事；
    /// - 不存在 → `Ok(None)`。
    pub fn read_with_source(&self, key: &Key) -> BxResult<Option<(Vec<u8>, Source)>> {
        let Some(blob) = self.inner.read(key)? else {
            return Ok(None);
        };
        match self.sealer.open(key, &blob) {
            Ok(plain) => Ok(Some((plain, Source::Head))),
            Err(BxError::WrongKey) => Err(BxError::WrongKey),
            Err(BxError::Corrupt(why)) => match self.inner.read_prev(key)? {
                Some(prev) => match self.sealer.open(key, &prev) {
                    Ok(plain) => Ok(Some((plain, Source::Prev))),
                    Err(_) => Err(BxError::Corrupt(why)),
                },
                None => Err(BxError::Corrupt(why)),
            },
            Err(e) => Err(e),
        }
    }

    /// 底层后端。
    pub fn inner(&self) -> &Arc<dyn Store> {
        &self.inner
    }
}

impl Store for SealedStore {
    fn read(&self, key: &Key) -> BxResult<Option<Vec<u8>>> {
        Ok(self.read_with_source(key)?.map(|(v, _)| v))
    }

    fn write(&self, key: &Key, value: &[u8]) -> BxResult<()> {
        let blob = self.sealer.seal(key, self.schema, value)?;
        self.inner.write(key, &blob)
    }

    fn remove(&self, key: &Key) -> BxResult<()> {
        self.inner.remove(key)
    }

    fn list(&self, scope: &str) -> BxResult<Vec<String>> {
        self.inner.list(scope)
    }

    fn read_prev(&self, key: &Key) -> BxResult<Option<Vec<u8>>> {
        let Some(blob) = self.inner.read_prev(key)? else {
            return Ok(None);
        };
        self.sealer.open(key, &blob).map(Some)
    }

    fn root_label(&self) -> String {
        format!("{} (sealed)", self.inner.root_label())
    }
}
