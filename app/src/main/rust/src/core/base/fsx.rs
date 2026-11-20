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

//! 原子文件读写：全内核**唯一**的落盘原语。
//!
//! 为什么不让各模块自己 `fs::write`：
//! 旧实现里 63 处直接写文件，临时名、覆盖策略、是否 fsync 各不相同，进程被杀或外置存储
//! 掉线时就出现「写了一半」「写到了另一个根」「旧档被空档覆盖」。这里把三件事一次做对：
//!
//! 1. **同目录临时文件 + rename**：rename 在同一文件系统内是原子的，读者只会看到旧档或新档。
//! 2. **fsync 文件与目录**：rename 只是元数据操作，不 fsync 时掉电仍可能丢内容或丢目录项。
//! 3. **保留上一代 `.prev` 硬链接**：rename 覆盖前先 `link` 一份，旧档坏了还能退回去。
//!
//! `.prev` 用硬链接而不是复制：零拷贝、原子、不额外占空间；文件系统不支持时静默降级，
//! 此时只剩「原子替换」这一层保护，仍然不会写出半截文件。

use std::cell::Cell;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::ids;

thread_local! {
    /// 当前线程的故障注入点；命中后返回 [`BxError::Fault`]，用于崩溃一致性测试。
    static FAULT_AT: Cell<Option<&'static str>> = const { Cell::new(None) };
}

/// 在**当前线程**的下一个 `point` 处注入一次失败（测试专用）。
pub fn arm_fault(point: &'static str) {
    FAULT_AT.with(|c| c.set(Some(point)));
}

/// 解除当前线程的故障注入。
pub fn disarm_fault() {
    FAULT_AT.with(|c| c.set(None));
}

fn trip(point: &'static str) -> BxResult<()> {
    let hit = FAULT_AT.with(|c| {
        if c.get() == Some(point) {
            c.set(None);
            true
        } else {
            false
        }
    });
    if hit {
        Err(BxError::Fault(point))
    } else {
        Ok(())
    }
}

/// `.prev` 路径：同目录、同名前缀。
pub fn prev_path(path: &Path) -> PathBuf {
    let mut name = path.file_name().unwrap_or_default().to_os_string();
    name.push(".prev");
    path.with_file_name(name)
}

fn tmp_path(path: &Path) -> PathBuf {
    let name = path.file_name().unwrap_or_default().to_string_lossy();
    path.with_file_name(format!(".{name}.tmp-{:016x}", ids::rand_u64()))
}

/// 原子写入 `path`。父目录会自动创建。
///
/// 失败时 `path` 保持调用前的状态（旧档不被破坏），临时文件会被清理。
pub fn write_atomic(path: &Path, data: &[u8]) -> BxResult<()> {
    write_atomic_inner(path, data, None)
}

/// 原子写入，并让文件从**创建那一刻**就是 `0600`。
///
/// 用于主密钥这类材料：先按默认权限建出来再 chmod，中间那一下是可读窗口。
pub fn write_atomic_secret(path: &Path, data: &[u8]) -> BxResult<()> {
    write_atomic_inner(path, data, Some(0o600))
}

fn write_atomic_inner(path: &Path, data: &[u8], mode: Option<u32>) -> BxResult<()> {
    let dir = path
        .parent()
        .ok_or_else(|| BxError::Invalid(format!("路径没有父目录: {}", path.display())))?;
    fs::create_dir_all(dir)?;

    let tmp = tmp_path(path);
    // 任何提前返回（含故障注入）都要清掉临时文件，否则留下无主垃圾
    let mut guard = TmpGuard {
        path: tmp.clone(),
        armed: true,
    };
    {
        let mut opts = OpenOptions::new();
        opts.write(true).create_new(true);
        #[cfg(unix)]
        if let Some(m) = mode {
            use std::os::unix::fs::OpenOptionsExt;
            opts.mode(m);
        }
        #[cfg(not(unix))]
        let _ = mode;
        let mut f = opts.open(&tmp)?;
        f.write_all(data)?;
        // 先落内容，再改名：顺序反了会在掉电后留下长度对、内容是空洞的文件
        f.sync_all()?;
    }
    trip("after_tmp_sync")?;

    // 保留上一代。先删旧 .prev 再 link，避免 link 因目标已存在而失败
    let prev = prev_path(path);
    let _ = fs::remove_file(&prev);
    let _ = fs::hard_link(path, &prev);
    trip("before_rename")?;

    fs::rename(&tmp, path)?;
    guard.armed = false;
    // 注意：rename 之后就是提交点，这里不再注入失败。
    // 提交点之后的「崩溃」只能靠进程真的死掉来模拟——那时调用方没有机会回滚，
    // 语义是「已提交」，而不是「失败」。

    // 目录项本身也要 fsync，否则掉电后 rename 可能没生效
    #[cfg(unix)]
    {
        if let Ok(d) = File::open(dir) {
            let _ = d.sync_all();
        }
    }
    Ok(())
}

/// 临时文件守卫：Drop 时若仍持有则删除，避免失败路径留下垃圾。
struct TmpGuard {
    path: PathBuf,
    armed: bool,
}

impl Drop for TmpGuard {
    fn drop(&mut self) {
        if self.armed {
            let _ = fs::remove_file(&self.path);
        }
    }
}

/// 读文件；主文件缺失时退回 `.prev`。
///
/// 注意：这里**不做完整性校验**，校验属于上层（[`crate::core::base::sealed`]）。
/// 本函数只负责「主文件读不到时还有没有上一代」。
pub fn read_recover(path: &Path) -> BxResult<Option<Vec<u8>>> {
    match fs::read(path) {
        Ok(v) => Ok(Some(v)),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => match fs::read(prev_path(path)) {
            Ok(v) => Ok(Some(v)),
            Err(e2) if e2.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(e2) => Err(e2.into()),
        },
        Err(e) => Err(e.into()),
    }
}

/// 只读上一代快照。
pub fn read_prev(path: &Path) -> BxResult<Option<Vec<u8>>> {
    match fs::read(prev_path(path)) {
        Ok(v) => Ok(Some(v)),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e.into()),
    }
}

/// 删除文件（含 `.prev`），删除失败不视为错误：文件本来就可能不存在。
pub fn remove(path: &Path) -> BxResult<()> {
    for p in [path.to_path_buf(), prev_path(path)] {
        match fs::remove_file(&p) {
            Ok(()) => {}
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => return Err(e.into()),
        }
    }
    Ok(())
}

/// 目录是否可写：真写一个探针文件，比 `metadata().permissions()` 可靠。
pub fn dir_writable(dir: &Path) -> bool {
    if fs::create_dir_all(dir).is_err() {
        return false;
    }
    let probe = dir.join(format!(".bx-probe-{:016x}", ids::rand_u64()));
    match OpenOptions::new().write(true).create_new(true).open(&probe) {
        Ok(mut f) => {
            let ok = f.write_all(b"1").is_ok();
            drop(f);
            let _ = fs::remove_file(&probe);
            ok
        }
        Err(_) => false,
    }
}
