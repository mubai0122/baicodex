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

//! 标识符与随机数：统一走 OS CSPRNG，不自己拿时间戳凑。

use crate::core::base::err::{BxError, BxResult};

/// 取 `N` 字节密码学随机数。
pub fn random<const N: usize>() -> BxResult<[u8; N]> {
    let mut buf = [0u8; N];
    getrandom::getrandom(&mut buf).map_err(|_| BxError::Invalid("系统随机源不可用".into()))?;
    Ok(buf)
}

/// 一个 64 位随机数，用于临时文件名后缀，避免并发写同一个 tmp。
pub fn rand_u64() -> u64 {
    let mut b = [0u8; 8];
    if getrandom::getrandom(&mut b).is_err() {
        // 随机源坏掉也不能撞名导致覆盖：退到纳秒时间戳
        let ns = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0);
        return ns;
    }
    u64::from_le_bytes(b)
}

/// 小写十六进制。
pub fn hex(bytes: &[u8]) -> String {
    const T: &[u8; 16] = b"0123456789abcdef";
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push(T[(b >> 4) as usize] as char);
        s.push(T[(b & 0x0f) as usize] as char);
    }
    s
}

/// 生成形如 `a1b2c3d4` 的短标识，用于日志关联。
pub fn short() -> String {
    match random::<6>() {
        Ok(b) => hex(&b),
        Err(_) => format!("{:012x}", rand_u64() & 0xffff_ffff_ffff),
    }
}
