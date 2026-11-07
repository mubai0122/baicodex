//! 权限选路。
//!
//! 同一个路径，在不同档位下走的通道完全不同：
//!
//! | 档位 | 读 `/sdcard/Android/data/x` 走什么 |
//! |---|---|
//! | Normal | 原生直读 → 零宽变形 → 都不行就拒绝 |
//! | Shizuku | `rish` 拿 UID 2000 去读 |
//! | Root | `su -c` 直接读 |
//!
//! 上层只问 [Router::route] 要一个 [Route]，具体怎么执行由 `exec` 层分派。
//! 这样"选路"和"执行"解耦：测试时可以只测选路，不用真的起 su。

use std::sync::atomic::{AtomicBool, AtomicU8, Ordering};
use std::sync::RwLock;

use crate::core::base::path as pathx;
use crate::core::feature::model::mode::WorkMode;
use crate::core::feature::model::session::Route;

use super::perm::Perms;
use probe::Pick;
use variant::{OBB_ROOT, DATA_ROOT};

pub mod probe;
pub mod variant;

/// 受限目录前缀：这些路径在 Normal 档位下本来就不可读。
const RESTRICTED: &[&str] = &[
    "/data/", "/system/", "/vendor/", "/product/", "/root/", "/proc/", "/sys/",
];

/// 路由器。
#[derive(Debug)]
pub struct Router {
    state: RwLock<Pick>,
    /// 档位可以运行中切换（`/mode`），所以用原子量而不是普通字段。
    work_mode: AtomicU8,
    zero_width_on: AtomicBool,
    perms: RwLock<Perms>,
}

impl Router {
    pub fn new(work_mode: WorkMode, zero_width_on: bool, perms: Perms) -> Self {
        Self {
            state: RwLock::new(Pick::default()),
            work_mode: AtomicU8::new(work_mode.to_u8()),
            zero_width_on: AtomicBool::new(zero_width_on),
            perms: RwLock::new(perms),
        }
    }

    pub fn work_mode(&self) -> WorkMode {
        WorkMode::from_u8(self.work_mode.load(Ordering::Relaxed))
    }

    /// 换挡位。切到 Root / Shizuku 时把探测结果作废，下次重新走一遍。
    pub fn set_work_mode(&self, m: WorkMode) {
        if self.work_mode() != m {
            self.work_mode.store(m.to_u8(), Ordering::Relaxed);
            self.reset();
        }
    }

    pub fn zero_width_on(&self) -> bool {
        self.zero_width_on.load(Ordering::Relaxed)
    }

    pub fn set_zero_width(&self, on: bool) {
        self.zero_width_on.store(on, Ordering::Relaxed);
        self.reset();
    }

    /// 权限变化（用户刚授权完）后调用：换一份 Perms 并作废探测。
    pub fn set_perms(&self, p: Perms) {
        if let Ok(mut w) = self.perms.write() {
            *w = p;
        }
        self.reset();
    }

    pub fn perms(&self) -> Perms {
        self.perms.read().map(|p| p.clone()).unwrap_or_default()
    }

    /// 阻塞探测。只允许在工作线程调；启动时跑一次。
    ///
    /// 返回"是否启用了零宽通道"——注意这不是"零宽是否可用"，原生能读时也返回 false。
    pub fn probe(&self) -> bool {
        let pick = probe::probe(self.zero_width_on());
        let used = pick.usable() && !pick.native_ok;
        if let Ok(mut st) = self.state.write() {
            *st = pick;
        }
        used
    }

    /// 零宽通道是否可用。
    pub fn zero_width_usable(&self) -> bool {
        self.state.read().map(|s| s.usable() && !s.native_ok).unwrap_or(false)
    }

    /// 命中的变形名。
    pub fn probe_name(&self) -> &'static str {
        self.state.read().map(|s| s.name()).unwrap_or("不可用")
    }

    /// 原生能否直读受限目录。
    pub fn native_ok(&self) -> bool {
        self.state.read().map(|s| s.native_ok).unwrap_or(false)
    }

    /// 探测过一次没有。
    pub fn probed_once(&self) -> bool {
        self.state.read().map(|s| s.at > 0).unwrap_or(false)
    }

    /// 按命中的变形改写路径。没命中/不需要时原样返回。
    pub fn map(&self, path: &str) -> String {
        self.state.read().map(|s| s.map(path)).unwrap_or_else(|_| path.to_string())
    }

    /// 选路。这是整个模块的主入口。
    pub fn route(&self, path: &str) -> Route {
        self.explain(path).0
    }

    /// 选路 + 原因。界面上"为什么读不了"就靠它。
    pub fn explain(&self, path: &str) -> (Route, String) {
        let p = path.trim();
        match self.work_mode() {
            WorkMode::Root => (Route::Root, "Root 档位：su -c 直接执行".into()),
            WorkMode::Shizuku => (Route::Shizuku, "Shizuku 档位：rish 以 UID 2000 执行".into()),
            WorkMode::Normal => {
                let restricted = is_restricted(p);
                if !restricted {
                    return (Route::Sandbox, "应用沙箱内路径，直接读写".into());
                }
                let in_data = p.starts_with(DATA_ROOT) || p.starts_with(OBB_ROOT);
                if in_data && self.native_ok() {
                    return (Route::AllFiles, "系统允许直读 Android/data（原生可读）".into());
                }
                if in_data && self.zero_width_on() && self.zero_width_usable() {
                    return (
                        Route::ZeroWidth,
                        format!("Android/data 受限，走零宽通道（{}）", self.probe_name()),
                    );
                }
                if self.perms().all_files {
                    return (Route::AllFiles, "已授予所有文件访问权限".into());
                }
                (
                    Route::Sandbox,
                    "受限路径且没有可用通道；可在设置里开零宽或切 Shizuku/Root".into(),
                )
            }
        }
    }

    /// 受限目录现在实际靠什么访问，给系统提示与设置页用。
    pub fn data_zone(&self) -> String {
        if !self.probed_once() {
            return "检测中".into();
        }
        if self.native_ok() {
            return if self.perms().all_files {
                "所有文件访问（原路径直读）".into()
            } else {
                "系统原生可读".into()
            };
        }
        if self.zero_width_usable() {
            return format!("零宽通道（{}）", self.probe_name());
        }
        if self.zero_width_on() {
            "当前设备不支持零宽".into()
        } else {
            "未开启零宽".into()
        }
    }

    /// 诊断信息（设置页"权限诊断"用）。
    pub fn diagnostics(&self) -> serde_json::Value {
        let st = match self.state.read() {
            Ok(s) => s,
            Err(p) => p.into_inner(),
        };
        serde_json::json!({
            "work_mode": self.work_mode().as_str(),
            "zero_width_on": self.zero_width_on(),
            "native_ok": st.native_ok,
            "picked": st.name(),
            "usable": st.usable(),
            "tried": st.tried,
            "perms": self.perms(),
            "zone": self.data_zone(),
        })
    }

    /// 作废探测结果。
    pub fn reset(&self) {
        if let Ok(mut st) = self.state.write() {
            *st = Pick::default();
        }
    }

    /// 修正不规范路径：`~` 展开、相对路径挂到数据根、折叠重复斜杠。
    ///
    /// 只做"用户明显想表达什么"的修正，不做安全判断——安全判断在
    /// `security::sandbox`，两者不能混。
    pub fn normalize(&self, raw: &str) -> String {
        let mut p = raw.trim().trim_matches('"').trim_matches('\'').to_string();
        if p == "~" || p.starts_with("~/") {
            p = pathx::expand_tilde(&p).to_string_lossy().to_string();
        }
        if !p.starts_with('/') {
            p = pathx::home().join(p).to_string_lossy().to_string();
        }
        // 折叠重复斜杠，但别碰开头两个（某些 URI 语义）
        while p.len() > 1 && p.contains("//") && !p.starts_with("//") {
            p = p.replace("//", "/");
        }
        pathx::normalize(&p).to_string_lossy().to_string()
    }

    /// 能不能直接读受限目录（读文件前的最后一道判断）。
    pub fn readable(&self) -> bool {
        let p = self.perms();
        p.all_files || self.native_ok() || self.zero_width_usable()
    }
}

/// 是否是受限路径。
pub fn is_restricted(p: &str) -> bool {
    RESTRICTED.iter().any(|x| p.starts_with(x)) || p.starts_with(DATA_ROOT) || p.starts_with(OBB_ROOT)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn route_by_mode() {
        let r = Router::new(WorkMode::Root, false, Perms::default());
        assert_eq!(r.route("/data/local/tmp"), Route::Root);
        let r = Router::new(WorkMode::Shizuku, false, Perms::default());
        assert_eq!(r.route("/data/local/tmp"), Route::Shizuku);
        let r = Router::new(WorkMode::Normal, false, Perms::default());
        assert_eq!(r.route("/tmp/x"), Route::Sandbox);
    }

    #[test]
    fn restricted_detection() {
        assert!(is_restricted("/data/app"));
        assert!(is_restricted(DATA_ROOT));
        assert!(!is_restricted("/sdcard/Download"));
    }

    #[test]
    fn mode_switch_resets_probe() {
        let r = Router::new(WorkMode::Normal, true, Perms::default());
        r.probe();
        assert!(r.probed_once());
        r.set_work_mode(WorkMode::Root);
        assert!(!r.probed_once());
    }

    #[test]
    fn normalize_relative() {
        let r = Router::new(WorkMode::Normal, false, Perms::default());
        let p = r.normalize("a/../b");
        assert!(p.ends_with("/b"), "{p}");
    }
}
