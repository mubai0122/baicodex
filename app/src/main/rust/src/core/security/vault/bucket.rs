//! 凭据分桶的数据结构与磁盘格式。
//!
//! 一个桶就是一个文件，装同一类生命周期的东西：
//!
//! | 桶 | 内容 | 换设备时 |
//! |---|---|---|
//! | `api` | 模型 key、搜索 key | 可重新填 |
//! | `user` | 账号 token、反馈 token | 可重新登录 |
//! | `ssh` | 私钥口令、主机密码 | 可重新配置 |
//!
//! 分桶不是为了好看：用户可能只想清掉 API key 换一个，不想把 SSH 密码一起干掉。
//!
//! 磁盘格式有两种，靠文件头自动识别：
//! - 加密：`secret::seal` 的二进制（magic `baicodexencryption`）
//! - 明文：`{"version":2,"items":{...}}`，权限 0600，用于还没开加密的场景

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

/// 明文格式版本。加字段不改它；改结构才加。
pub const VERSION: u32 = 2;

/// 一条凭据。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Entry {
    /// 值。加密落盘时整份文件都加密，这里不做二次处理。
    #[serde(rename = "v", default)]
    pub value: String,
    /// 最后修改时间（毫秒）。审计与"这条是不是过期了"用。
    #[serde(rename = "at", default)]
    pub at: i64,
    /// 备注：这条是干嘛的。给用户自己看。
    #[serde(rename = "note", default, skip_serializing_if = "String::is_empty")]
    pub note: String,
}

/// 一个桶的内容。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct BucketData {
    #[serde(default)]
    pub version: u32,
    #[serde(default)]
    pub items: BTreeMap<String, Entry>,
}

impl BucketData {
    pub fn new() -> Self {
        Self { version: VERSION, items: BTreeMap::new() }
    }

    /// 编码成明文 JSON 字节。
    pub fn encode(&self) -> Vec<u8> {
        serde_json::to_vec_pretty(self).unwrap_or_else(|_| b"{}".to_vec())
    }

    /// 从明文 JSON 解码。兼容最老的格式（`{"items":{"k":"v"}}`，值直接是字符串）。
    pub fn decode(bytes: &[u8]) -> Option<Self> {
        let v: serde_json::Value = serde_json::from_slice(bytes).ok()?;
        let mut out = BucketData::new();
        let items = v.get("items")?.as_object()?;
        for (k, val) in items {
            let entry = match val {
                serde_json::Value::String(s) => Entry { value: s.clone(), at: 0, note: String::new() },
                serde_json::Value::Object(o) => Entry {
                    value: o.get("v").and_then(|x| x.as_str()).unwrap_or("").to_string(),
                    at: o.get("at").and_then(|x| x.as_i64()).unwrap_or(0),
                    note: o.get("note").and_then(|x| x.as_str()).unwrap_or("").to_string(),
                },
                _ => continue,
            };
            out.items.insert(k.clone(), entry);
        }
        Some(out)
    }

    pub fn get(&self, key: &str) -> Option<&Entry> {
        self.items.get(key)
    }

    pub fn set(&mut self, key: &str, value: &str, note: &str) {
        if value.is_empty() {
            self.items.remove(key);
            return;
        }
        self.items.insert(
            key.to_string(),
            Entry { value: value.to_string(), at: crate::core::base::clock::now_ms(), note: note.to_string() },
        );
    }

    pub fn remove(&mut self, key: &str) -> bool {
        self.items.remove(key).is_some()
    }
}

/// 桶名是否合法：只允许小写字母、数字、`-`、`_`，长度 1..=32。
///
/// 桶名会拼进文件名，不校验就等于把路径穿越的口子开在凭据库上。
pub fn valid_bucket(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 32
        && name
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-' || c == '_')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encode_decode() {
        let mut b = BucketData::new();
        b.set("k", "secret-value", "备注");
        let bytes = b.encode();
        let back = BucketData::decode(&bytes).unwrap();
        assert_eq!(back.get("k").unwrap().value, "secret-value");
        assert_eq!(back.get("k").unwrap().note, "备注");
    }

    #[test]
    fn legacy_plain_string_items() {
        let raw = br#"{"items":{"a":"1","b":"2"}}"#;
        let b = BucketData::decode(raw).unwrap();
        assert_eq!(b.get("a").unwrap().value, "1");
    }

    #[test]
    fn empty_value_deletes() {
        let mut b = BucketData::new();
        b.set("k", "v", "");
        b.set("k", "", "");
        assert!(b.get("k").is_none());
    }

    #[test]
    fn bucket_name_guard() {
        assert!(valid_bucket("api"));
        assert!(valid_bucket("ssh-2"));
        assert!(!valid_bucket("../etc"));
        assert!(!valid_bucket("API"));
        assert!(!valid_bucket(""));
    }
}
