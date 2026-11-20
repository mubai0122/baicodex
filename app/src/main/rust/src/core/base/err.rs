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

//! 统一错误模型。
//!
//! 设计要点：
//! - 每一类失败都有稳定的数字码，FFI 侧不需要字符串匹配。
//! - [`BxError::WrongKey`] 与 [`BxError::Corrupt`] 必须分开：前者要「拒绝写入」保护旧密文，
//!   后者要「尝试上一代快照」。把两者混成一个错误正是旧实现掉存储的根因之一。

use std::fmt;

/// 错误码，跨 FFI 稳定。
#[repr(i32)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ErrorCode {
    /// 成功。
    Ok = 0,
    /// 普通 IO 失败。
    Io = 1,
    /// 目标不存在。
    NotFound = 2,
    /// 文件损坏（校验和不符、长度不对、格式非法）。
    Corrupt = 3,
    /// 密钥不匹配：文件是别的密钥写的，**不允许覆盖**。
    WrongKey = 4,
    /// 桶被锁定（通常是 WrongKey 之后的保护态）。
    Locked = 5,
    /// Key 名字非法。
    BadKey = 6,
    /// 存储格式版本不受支持。
    Version = 7,
    /// 数据根不可用（外置存储掉线等），**不允许静默切换**。
    RootUnavailable = 8,
    /// 命中故障注入点（仅测试构建）。
    Fault = 9,
    /// 参数非法。
    Invalid = 10,
}

impl ErrorCode {
    /// 取数值，供 C ABI 返回。
    pub fn as_i32(self) -> i32 {
        self as i32
    }
}

/// 内核统一错误。
#[derive(Debug)]
pub enum BxError {
    /// IO 失败。
    Io(std::io::Error),
    /// 目标不存在。
    NotFound,
    /// 数据损坏，附带原因。
    Corrupt(&'static str),
    /// 密钥不匹配。
    WrongKey,
    /// 桶已锁定。
    Locked,
    /// Key 名字非法。
    BadKey(String),
    /// 格式版本不支持。
    Version(u16),
    /// 数据根不可用。
    RootUnavailable(String),
    /// 故障注入。
    Fault(&'static str),
    /// 参数非法。
    Invalid(String),
}

impl BxError {
    /// 稳定错误码。
    pub fn code(&self) -> ErrorCode {
        match self {
            BxError::Io(_) => ErrorCode::Io,
            BxError::NotFound => ErrorCode::NotFound,
            BxError::Corrupt(_) => ErrorCode::Corrupt,
            BxError::WrongKey => ErrorCode::WrongKey,
            BxError::Locked => ErrorCode::Locked,
            BxError::BadKey(_) => ErrorCode::BadKey,
            BxError::Version(_) => ErrorCode::Version,
            BxError::RootUnavailable(_) => ErrorCode::RootUnavailable,
            BxError::Fault(_) => ErrorCode::Fault,
            BxError::Invalid(_) => ErrorCode::Invalid,
        }
    }
}

impl fmt::Display for BxError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BxError::Io(e) => write!(f, "io: {e}"),
            BxError::NotFound => f.write_str("not found"),
            BxError::Corrupt(why) => write!(f, "corrupt: {why}"),
            BxError::WrongKey => f.write_str("wrong key"),
            BxError::Locked => f.write_str("locked"),
            BxError::BadKey(k) => write!(f, "bad key: {k}"),
            BxError::Version(v) => write!(f, "unsupported version: {v}"),
            BxError::RootUnavailable(why) => write!(f, "root unavailable: {why}"),
            BxError::Fault(p) => write!(f, "fault injected at {p}"),
            BxError::Invalid(why) => write!(f, "invalid: {why}"),
        }
    }
}

impl std::error::Error for BxError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            BxError::Io(e) => Some(e),
            _ => None,
        }
    }
}

impl From<std::io::Error> for BxError {
    fn from(e: std::io::Error) -> Self {
        if e.kind() == std::io::ErrorKind::NotFound {
            BxError::NotFound
        } else {
            BxError::Io(e)
        }
    }
}

/// 内核 Result 别名。
pub type BxResult<T> = Result<T, BxError>;
