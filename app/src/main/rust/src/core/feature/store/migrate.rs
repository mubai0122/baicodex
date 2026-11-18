//! 数据迁移：版本化、幂等、可重入。
//!
//! 迁移是「掉存储」的第二大来源：搬迁做到一半进程被杀，下次启动看到「新目录已存在」就跳过，
//! 结果一半数据在新位置、一半在旧位置。这里的两条约束：
//!
//! 1. 每个步骤必须**幂等**（重复跑不会破坏数据），搬运用「目标已存在则不覆盖」；
//! 2. 版本标记在步骤**完成之后**才写；中途被杀下次会重跑这一步。
//!
//! 步骤函数是普通 `fn`，不做动态注册，避免「加载顺序不同导致迁移顺序不同」。

use std::collections::BTreeMap;
use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::key::Key;
use crate::core::base::store::Store;
use crate::core::feature::vault::Vault;

/// 版本标记所在的 scope。
const META_SCOPE: &str = "meta";
/// 版本标记的条目名。
const META_KEY: &str = "schema";

/// 一个迁移步骤。
pub struct Step {
    /// 目标版本号，必须严格递增。
    pub version: u32,
    /// 人类可读的名字，写进日志。
    pub name: &'static str,
    /// 实际动作。必须是幂等的。
    pub run: fn(&dyn Store) -> BxResult<()>,
}

#[derive(Serialize, Deserialize, Default, Debug, Clone)]
struct Marker {
    v: u32,
    #[serde(default)]
    applied: Vec<String>,
}

/// 迁移执行器。
pub struct Migrator {
    store: Arc<dyn Store>,
}

impl Migrator {
    /// 在给定后端上执行迁移。
    pub fn new(store: Arc<dyn Store>) -> Self {
        Self { store }
    }

    /// 当前已完成的版本；读不到按 0 处理。
    pub fn current(&self) -> BxResult<u32> {
        match self.store.read(&Key::new(META_SCOPE, META_KEY)?) {
            Ok(Some(bytes)) => Ok(serde_json::from_slice::<Marker>(&bytes)
                .map(|m| m.v)
                .unwrap_or(0)),
            Ok(None) => Ok(0),
            Err(BxError::WrongKey) | Err(BxError::Corrupt(_)) => Ok(0),
            Err(e) => Err(e),
        }
    }

    fn write_marker(&self, marker: &Marker) -> BxResult<()> {
        let bytes = serde_json::to_vec(marker)
            .map_err(|_| BxError::Invalid("迁移标记序列化失败".into()))?;
        self.store.write(&Key::new(META_SCOPE, META_KEY)?, &bytes)
    }

    /// 依次执行所有 `version > current` 的步骤，返回本次实际执行的版本号。
    pub fn run(&self, steps: &[Step]) -> BxResult<Vec<u32>> {
        let mut cur = self.current()?;
        let mut marker = match self.store.read(&Key::new(META_SCOPE, META_KEY)?)? {
            Some(bytes) => serde_json::from_slice::<Marker>(&bytes).unwrap_or_default(),
            None => Marker::default(),
        };
        marker.v = cur;

        let mut sorted: Vec<&Step> = steps.iter().collect();
        sorted.sort_by_key(|s| s.version);
        sorted.dedup_by_key(|s| s.version);

        let mut done = Vec::new();
        for step in sorted {
            if step.version <= cur {
                continue;
            }
            (step.run)(self.store.as_ref())?;
            // 只有步骤成功后才推进版本；写标记失败则本次不回退（步骤幂等，下次重跑无害）
            marker.v = step.version;
            marker
                .applied
                .push(format!("{}:{}", step.version, step.name));
            self.write_marker(&marker)?;
            cur = step.version;
            done.push(step.version);
        }
        Ok(done)
    }
}

/// 把旧版明文 `secrets.json` 导进凭据库。
///
/// 只接受扁平 `{"k":"v"}`；非字符串或空值跳过。导入成功**不清除**源文件，
/// 留给调用方在确认新库可读后再删——先删后导正是旧实现丢密钥的现场。
pub fn import_legacy_secrets(vault: &Vault, bucket: &str, json: &str) -> BxResult<usize> {
    let raw: BTreeMap<String, serde_json::Value> =
        serde_json::from_str(json).map_err(|_| BxError::Corrupt("旧密钥表不是合法 JSON"))?;
    let mut legacy = BTreeMap::new();
    for (k, v) in raw {
        if let Some(s) = v.as_str() {
            if !s.is_empty() {
                legacy.insert(k, s.to_string());
            }
        }
    }
    vault.adopt(bucket, &legacy)
}
