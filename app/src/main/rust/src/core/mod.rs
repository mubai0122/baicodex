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

//! 核心分类：新增的 Rust 能力按 base / security / feature / platform 归位，不要在 lib.rs 里堆业务
//!
//! 依赖只能向下：platform → feature → security → base，任何模块都不允许反向依赖。

pub mod base;
pub mod feature;
pub mod platform;
pub mod security;
