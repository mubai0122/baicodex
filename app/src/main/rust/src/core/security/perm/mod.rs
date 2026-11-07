//! 运行期能力。
//!
//! 一句话：**问"我现在能做什么"，而不是"用户点了哪个开关"**。
//!
//! Android 上权限会被用户在设置里随时撤销，桌面端的 `sudo` 也可能过期（`timestamp_timeout`）。
//! 所以 `Perms` 不是一次性配置，而是带缓存、可刷新的探测结果。工具执行前如果发现
//! 能力不满足，直接返回明确原因，不要让模型反复重试同一件事。

use std::sync::{Mutex, OnceLock};

use serde::Serialize;

use crate::core::feature::model::mode::WorkMode;

pub mod probe;

/// 一项具体能力。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Capability {
    /// 读普通文件（应用沙箱内）。
    ReadFiles,
    /// 写普通文件。
    WriteFiles,
    /// 读共享存储（/sdcard）。
    ReadStorage,
    /// 无限制文件访问（Android 11+ 的 MANAGE_EXTERNAL_STORAGE）。
    AllFiles,
    /// root shell。
    Root,
    /// shell（UID 2000）通道。
    Shell,
    /// 屏幕读取与点击。
    Screen,
    /// 发通知。
    Notify,
    /// 录音。
    Mic,
}

impl Capability {
    pub fn key(self) -> &'static str {
        match self {
            Capability::ReadFiles => "read_files",
            Capability::WriteFiles => "write_files",
            Capability::ReadStorage => "storage",
            Capability::AllFiles => "all_files",
            Capability::Root => "root",
            Capability::Shell => "shizuku",
            Capability::Screen => "a11y",
            Capability::Notify => "notifications",
            Capability::Mic => "mic",
        }
    }
}

/// 当前具备的能力。
#[derive(Debug, Clone, Default, Serialize)]
pub struct Perms {
    pub all_files: bool,
    pub root: bool,
    pub shizuku: bool,
    pub a11y: bool,
    pub notifications: bool,
    pub mic: bool,
    pub storage: bool,
    /// 探测时间。超过 60 秒的缓存应重探（用户可能刚授权完回来）。
    pub at: i64,
}

impl Perms {
    /// 缓存探测。首次调用会起子进程，之后 60 秒内直接返回缓存。
    pub fn cached() -> Self {
        static CACHE: OnceLock<Mutex<Option<Perms>>> = OnceLock::new();
        let slot = CACHE.get_or_init(|| Mutex::new(None));
        let mut g = match slot.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        let stale = g
            .as_ref()
            .map(|p| crate::core::base::clock::now_ms() - p.at > 60_000)
            .unwrap_or(true);
        if stale {
            *g = Some(probe::detect());
        }
        g.clone().unwrap_or_default()
    }

    /// 强制重探。
    pub fn refresh() -> Self {
        probe::detect()
    }

    /// 是否具备某项能力。
    pub fn has(&self, cap: Capability) -> bool {
        match cap {
            Capability::ReadFiles | Capability::WriteFiles => true, // 沙箱内永远可读写
            Capability::ReadStorage => self.storage || self.all_files,
            Capability::AllFiles => self.all_files,
            Capability::Root => self.root,
            Capability::Shell => self.shizuku,
            Capability::Screen => self.a11y,
            Capability::Notify => self.notifications,
            Capability::Mic => self.mic,
        }
    }

    /// 缺哪些（只列"用户可能在意"的项，不列 ReadFiles 这种永远有的）。
    pub fn missing(&self) -> Vec<&'static str> {
        let mut v = Vec::new();
        if !self.storage && !self.all_files {
            v.push("storage");
        }
        if !self.notifications {
            v.push("notifications");
        }
        if !self.a11y {
            v.push("a11y");
        }
        v
    }

    /// 全部齐了。
    pub fn ok(&self) -> bool {
        self.missing().is_empty()
    }

    /// 当前档位的通道能不能用。
    pub fn for_mode(&self, mode: WorkMode) -> bool {
        match mode {
            WorkMode::Normal => self.storage || self.all_files || !crate::core::base::sys::is_android(),
            WorkMode::Shizuku => self.shizuku,
            WorkMode::Root => self.root,
        }
    }

    /// 给界面用的一句话。
    pub fn summary(&self) -> String {
        let mut on = Vec::new();
        if self.root {
            on.push("Root");
        }
        if self.shizuku {
            on.push("Shizuku");
        }
        if self.all_files {
            on.push("全文件");
        }
        if self.a11y {
            on.push("无障碍");
        }
        if self.storage {
            on.push("存储");
        }
        if on.is_empty() {
            "仅沙箱".into()
        } else {
            on.join(" · ")
        }
    }

    /// 权限项的中文名。
    pub fn label(key: &str) -> &'static str {
        match key {
            "storage" => "存储",
            "all_files" => "所有文件访问",
            "root" => "Root",
            "shizuku" => "Shizuku",
            "a11y" => "无障碍服务",
            "notifications" => "通知",
            "mic" => "麦克风",
            _ => "未知权限",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normal_mode_on_desktop_works() {
        let p = Perms { storage: false, all_files: false, ..Default::default() };
        // 桌面端没有 Android 的存储限制
        if !crate::core::base::sys::is_android() {
            assert!(p.for_mode(WorkMode::Normal));
        }
    }

    #[test]
    fn missing_lists_gaps() {
        let p = Perms { notifications: true, storage: true, a11y: false, ..Default::default() };
        assert_eq!(p.missing(), vec!["a11y"]);
    }

    #[test]
    fn capability_mapping() {
        let p = Perms { all_files: true, ..Default::default() };
        assert!(p.has(Capability::AllFiles));
        assert!(p.has(Capability::ReadStorage));
        assert!(!p.has(Capability::Root));
    }
}
