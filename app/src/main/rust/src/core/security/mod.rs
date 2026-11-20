/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:29
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 安全相关：数据输出脱敏、常量时间比较、异或、信封加密与密钥来源

pub mod crypto;
pub mod keyring;
pub mod mask;
pub mod sealer;

pub use keyring::{FileKeyProvider, KeyProvider, StaticKeyProvider};
pub use sealer::{MasterKey, Sealer};
