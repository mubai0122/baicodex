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

//! 存储键：把「桶名 + 条目名」变成一个**不可能逃出根目录**的路径片段。
//!
//! 旧实现把用户/模型可控的字符串直接拼进路径，靠调用方自觉；这里把校验放进构造函数，
//! 非法名字根本构造不出 [`Key`]，后续所有 API 也就无从写错位置。

use std::fmt;

use crate::core::base::err::{BxError, BxResult};

/// 一条存储键：`scope` 是桶（目录），`name` 是条目。
#[derive(Clone, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct Key {
    scope: String,
    name: String,
}

impl Key {
    /// 构造并校验。名字允许 `A-Z a-z 0-9 . _ -`，长度 1..=120。
    pub fn new(scope: impl Into<String>, name: impl Into<String>) -> BxResult<Self> {
        let scope = scope.into();
        let name = name.into();
        check_part(&scope, "scope")?;
        check_part(&name, "name")?;
        Ok(Self { scope, name })
    }

    /// 桶名。
    pub fn scope(&self) -> &str {
        &self.scope
    }

    /// 条目名。
    pub fn name(&self) -> &str {
        &self.name
    }

    /// 相对路径片段：`scope/name`。两侧都已校验，不含分隔符以外的特殊字符。
    pub fn rel(&self) -> String {
        format!("{}/{}", self.scope, self.name)
    }

    /// AEAD 的附加数据：把键绑进密文，文件被挪到别的桶就解不开。
    pub fn aad(&self) -> Vec<u8> {
        format!("bx2:{}/{}", self.scope, self.name).into_bytes()
    }
}

impl fmt::Debug for Key {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        // 条目名可能含凭据语义（如 ssh.github_token），日志里不打印
        write!(f, "Key({}/*)", self.scope)
    }
}

/// 校验一个路径片段是否合法（供凭据条目名等复用）。
pub fn valid_name(s: &str) -> BxResult<()> {
    check_part(s, "name")
}

fn check_part(s: &str, what: &str) -> BxResult<()> {
    if s.is_empty() {
        return Err(BxError::BadKey(format!("{what} 为空")));
    }
    if s.len() > 120 {
        return Err(BxError::BadKey(format!("{what} 过长: {} 字节", s.len())));
    }
    if s == "." || s == ".." || s.contains("..") {
        return Err(BxError::BadKey(format!("{what} 含路径回溯: {s}")));
    }
    for c in s.chars() {
        let ok = c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-' | '@');
        if !ok {
            return Err(BxError::BadKey(format!("{what} 含非法字符 {c:?}")));
        }
    }
    Ok(())
}
