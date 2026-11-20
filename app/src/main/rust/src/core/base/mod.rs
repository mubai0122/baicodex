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

//! 基类层：与业务无关的地基
//!
//! bytes/kv/marks/text                     字节、KV 文本、掩码写入、扫描游走
//! err/fsx/key/store/sealed/clock/ids      内核存储地基：唯一落盘原语 + 唯一写入入口

pub mod bytes;
pub mod clock;
pub mod err;
pub mod fsx;
pub mod ids;
pub mod key;
pub mod kv;
pub mod marks;
pub mod sealed;
pub mod store;
pub mod text;

pub use err::{BxError, BxResult};
pub use key::Key;
pub use store::{MemStore, Store};
