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

//! 存储抽象：**唯一**的写入入口。
//!
//! 上层的 vault / prefs / 迁移全部只认 [`Store`]，因此换后端（文件、内存、Android 私有目录、
//! Windows 的 `%LOCALAPPDATA%`）不影响任何业务代码，也不会出现「某模块自己写了另一个地方」。
//!
//! 根目录一旦确定就**钉死**：外置存储掉线时返回
//! [`BxError::RootUnavailable`]，绝不静默切到私有目录——旧版正是靠「掉线就换根」把配置写进了
//! 另一个位置，等外置恢复后两边互相覆盖，表现为「配置少了一条」。

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::fsx;
use crate::core::base::key::Key;

/// 后端扩展名：文件后端统一加 `.bx`，避免和用户的同名文件混淆。
const EXT: &str = "bx";

/// 统一存储接口。
pub trait Store: Send + Sync {
    /// 读一条；不存在返回 `Ok(None)`。
    fn read(&self, key: &Key) -> BxResult<Option<Vec<u8>>>;

    /// 写一条：实现必须保证「要么旧值、要么新值」，不允许半截。
    fn write(&self, key: &Key, value: &[u8]) -> BxResult<()>;

    /// 删一条；不存在视为成功。
    fn remove(&self, key: &Key) -> BxResult<()>;

    /// 列出一个桶里的全部条目名。
    fn list(&self, scope: &str) -> BxResult<Vec<String>>;

    /// 读上一代快照（若后端保留）。默认不保留。
    fn read_prev(&self, _key: &Key) -> BxResult<Option<Vec<u8>>> {
        Ok(None)
    }

    /// 数据根的可读标识，用于诊断页展示。
    fn root_label(&self) -> String {
        String::from("<memory>")
    }
}

/// 文件后端：`<root>/<scope>/<name>.bx`。
pub struct FsStore {
    root: PathBuf,
    /// 根目录是否已经确认存在；一旦为 true，后续掉线即报错而不是换根。
    pinned: bool,
}

impl FsStore {
    /// 打开（必要时创建）一个数据根。
    ///
    /// `must_exist` 为 true 时不会创建目录：用于「外置存储尚未挂载」的判定，
    /// 让调用方拿到明确错误而不是被悄悄建到别处。
    pub fn open(root: impl Into<PathBuf>, must_exist: bool) -> BxResult<Self> {
        let root = root.into();
        if must_exist {
            if !root.is_dir() {
                return Err(BxError::RootUnavailable(format!(
                    "数据根不存在: {}",
                    root.display()
                )));
            }
        } else if !fsx::dir_writable(&root) {
            return Err(BxError::RootUnavailable(format!(
                "数据根不可写: {}",
                root.display()
            )));
        }
        Ok(Self { root, pinned: true })
    }

    /// 数据根路径。
    pub fn root(&self) -> &Path {
        &self.root
    }

    fn ensure_root(&self) -> BxResult<()> {
        if !self.pinned {
            return Err(BxError::RootUnavailable("数据根未初始化".into()));
        }
        // 只检查不重建：掉线后再 mkdir 会把数据根建到临时挂载点上
        if !self.root.is_dir() {
            return Err(BxError::RootUnavailable(format!(
                "数据根已不可用: {}（不会切换到其它目录）",
                self.root.display()
            )));
        }
        Ok(())
    }

    fn path_of(&self, key: &Key) -> BxResult<PathBuf> {
        Ok(self
            .root
            .join(key.scope())
            .join(format!("{}.{EXT}", key.name())))
    }
}

impl Store for FsStore {
    fn read(&self, key: &Key) -> BxResult<Option<Vec<u8>>> {
        self.ensure_root()?;
        fsx::read_recover(&self.path_of(key)?)
    }

    fn write(&self, key: &Key, value: &[u8]) -> BxResult<()> {
        self.ensure_root()?;
        fsx::write_atomic(&self.path_of(key)?, value)
    }

    fn remove(&self, key: &Key) -> BxResult<()> {
        self.ensure_root()?;
        fsx::remove(&self.path_of(key)?)
    }

    fn list(&self, scope: &str) -> BxResult<Vec<String>> {
        self.ensure_root()?;
        let dir = self.root.join(scope);
        let rd = match std::fs::read_dir(&dir) {
            Ok(rd) => rd,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
            Err(e) => return Err(e.into()),
        };
        let mut out = Vec::new();
        for ent in rd {
            let ent = ent?;
            let name = ent.file_name().to_string_lossy().to_string();
            if let Some(stem) = name.strip_suffix(&format!(".{EXT}")) {
                out.push(stem.to_string());
            }
        }
        out.sort();
        Ok(out)
    }

    fn read_prev(&self, key: &Key) -> BxResult<Option<Vec<u8>>> {
        self.ensure_root()?;
        fsx::read_prev(&self.path_of(key)?)
    }

    fn root_label(&self) -> String {
        self.root.display().to_string()
    }
}

/// 内存后端：测试与「未解锁时的只读占位」用。
#[derive(Default)]
pub struct MemStore {
    map: Mutex<BTreeMap<(String, String), Vec<u8>>>,
}

impl MemStore {
    /// 建一个空内存后端。
    pub fn new() -> Self {
        Self::default()
    }

    /// 当前条目数，测试断言用。
    pub fn len(&self) -> usize {
        self.map.lock().map(|m| m.len()).unwrap_or(0)
    }

    /// 是否为空。
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// 读出全部内容，只给测试做「盘上是否出现明文」的检查。
    pub fn dump(&self) -> Vec<(String, Vec<u8>)> {
        self.map
            .lock()
            .map(|m| {
                m.iter()
                    .map(|((s, n), v)| (format!("{s}/{n}"), v.clone()))
                    .collect()
            })
            .unwrap_or_default()
    }
}

impl Store for MemStore {
    fn read(&self, key: &Key) -> BxResult<Option<Vec<u8>>> {
        let m = self
            .map
            .lock()
            .map_err(|_| BxError::Invalid("内存后端锁中毒".into()))?;
        Ok(m.get(&(key.scope().to_string(), key.name().to_string()))
            .cloned())
    }

    fn write(&self, key: &Key, value: &[u8]) -> BxResult<()> {
        let mut m = self
            .map
            .lock()
            .map_err(|_| BxError::Invalid("内存后端锁中毒".into()))?;
        m.insert(
            (key.scope().to_string(), key.name().to_string()),
            value.to_vec(),
        );
        Ok(())
    }

    fn remove(&self, key: &Key) -> BxResult<()> {
        let mut m = self
            .map
            .lock()
            .map_err(|_| BxError::Invalid("内存后端锁中毒".into()))?;
        m.remove(&(key.scope().to_string(), key.name().to_string()));
        Ok(())
    }

    fn list(&self, scope: &str) -> BxResult<Vec<String>> {
        let m = self
            .map
            .lock()
            .map_err(|_| BxError::Invalid("内存后端锁中毒".into()))?;
        Ok(m.keys()
            .filter(|(s, _)| s == scope)
            .map(|(_, n)| n.clone())
            .collect())
    }
}
