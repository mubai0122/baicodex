/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:30
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! 平台层：只做「能力探测」与「数据根选址」，不含任何平台专属业务逻辑。
//!
//! 核心层（base / security / feature）在编译期不需要知道自己在 Android 还是 Windows 上；
//! 需要差异的地方只暴露成数据（[`Caps`]）和函数（[`resolve_root`]），由宿主注入。
//!
//! 液态玻璃是这里最典型的一条：Windows 的 DWM 没有 macOS 那种把下层内容真实折射的合成器，
//! 所以 `liquid_glass = false`，前端据此降级到毛玻璃（背景模糊）而不是报错或留白。

use std::path::{Path, PathBuf};

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::fsx;

/// 运行平台。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Os {
    /// Android。
    Android,
    /// Windows。
    Windows,
    /// Linux。
    Linux,
    /// macOS。
    Macos,
    /// 其它 / 未知。
    Other,
}

impl Os {
    /// 按编译目标判断。
    pub const fn current() -> Self {
        #[cfg(target_os = "android")]
        {
            Os::Android
        }
        #[cfg(target_os = "windows")]
        {
            Os::Windows
        }
        #[cfg(target_os = "linux")]
        {
            Os::Linux
        }
        #[cfg(target_os = "macos")]
        {
            Os::Macos
        }
        #[cfg(not(any(
            target_os = "android",
            target_os = "windows",
            target_os = "linux",
            target_os = "macos"
        )))]
        {
            Os::Other
        }
    }

    /// 稳定的小写标识，跨 FFI 与前端。
    pub const fn id(self) -> &'static str {
        match self {
            Os::Android => "android",
            Os::Windows => "windows",
            Os::Linux => "linux",
            Os::Macos => "macos",
            Os::Other => "other",
        }
    }
}

/// 平台能力。前端据此决定材质与降级路径，不靠 UA 猜。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Caps {
    /// 是否支持液态玻璃（真实折射）。
    pub liquid_glass: bool,
    /// 是否支持背景模糊（毛玻璃）。
    pub backdrop_blur: bool,
    /// 是否有系统级密钥库（Android Keystore / Windows DPAPI / 桌面 keyring）。
    pub os_keyring: bool,
    /// 窗口是否支持自绘标题栏。
    pub custom_chrome: bool,
}

impl Caps {
    /// 当前平台能力。
    pub const fn current() -> Self {
        match Os::current() {
            // Android：RenderEffect 可做实时背景模糊；液态折射在低端机上不可控，默认关闭
            Os::Android => Caps {
                liquid_glass: false,
                backdrop_blur: true,
                os_keyring: true,
                custom_chrome: true,
            },
            // Windows：DWM 有 acrylic/mica，但没有真实折射；液态玻璃一律降级
            Os::Windows => Caps {
                liquid_glass: false,
                backdrop_blur: true,
                os_keyring: true,
                custom_chrome: true,
            },
            Os::Linux => Caps {
                liquid_glass: false,
                backdrop_blur: true,
                os_keyring: false,
                custom_chrome: true,
            },
            // macOS：唯一允许开液态玻璃的平台
            Os::Macos => Caps {
                liquid_glass: true,
                backdrop_blur: true,
                os_keyring: true,
                custom_chrome: true,
            },
            Os::Other => Caps {
                liquid_glass: false,
                backdrop_blur: false,
                os_keyring: false,
                custom_chrome: false,
            },
        }
    }
}

impl Caps {
    /// 序列化为 JSON（字段名即前端契约，改字段等于改协议）。
    pub fn to_json(self) -> String {
        format!(
            "{{\"os\":\"{}\",\"liquid_glass\":{},\"backdrop_blur\":{},\"os_keyring\":{},\"custom_chrome\":{},\"schema\":{}}}",
            Os::current().id(),
            self.liquid_glass,
            self.backdrop_blur,
            self.os_keyring,
            self.custom_chrome,
            crate::SCHEMA
        )
    }
}

/// 数据根选址结果。
#[derive(Debug, Clone)]
pub struct RootChoice {
    /// 选中的根。
    pub path: PathBuf,
    /// 是否用了首选路径（false 表示退到了备选）。
    pub preferred: bool,
}

/// 从候选里挑一个数据根并**钉死**。
///
/// - `require_exists = true`：候选必须是已存在目录（Android 外置存储场景），不新建；
/// - `require_exists = false`：候选必须可写（桌面场景），不存在会创建。
///
/// 全部候选都不可用时返回 [`BxError::RootUnavailable`]。调用方拿到错误应当提示用户，
/// **不要**再自己换个目录重试——那正是旧实现「两份数据互相覆盖」的起点。
pub fn resolve_root(candidates: &[PathBuf], require_exists: bool) -> BxResult<RootChoice> {
    let mut first_err: Option<BxError> = None;
    for (i, c) in candidates.iter().enumerate() {
        let ok = if require_exists {
            c.is_dir()
        } else {
            fsx::dir_writable(c)
        };
        if ok {
            return Ok(RootChoice {
                path: c.clone(),
                preferred: i == 0,
            });
        }
        if first_err.is_none() {
            first_err = Some(BxError::RootUnavailable(format!(
                "候选根不可用: {}",
                c.display()
            )));
        }
    }
    Err(first_err.unwrap_or_else(|| BxError::RootUnavailable("没有候选数据根".into())))
}

/// 桌面平台的默认数据根（Android 由宿主传入私有目录）。
pub fn default_desktop_root(app: &str) -> Option<PathBuf> {
    match Os::current() {
        Os::Windows => std::env::var_os("LOCALAPPDATA").map(|p| Path::new(&p).join(app)),
        Os::Linux | Os::Macos => std::env::var_os("XDG_DATA_HOME")
            .map(|p| Path::new(&p).join(app))
            .or_else(|| {
                std::env::var_os("HOME").map(|p| Path::new(&p).join(".local/share").join(app))
            }),
        _ => None,
    }
}
