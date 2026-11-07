//! 零宽通道的实测。
//!
//! 判据只有一个：`read_dir` 能不能成功。
//!
//! 为什么不用 `File::canRead` / `isExternalStorageManager` 这类 API：它们在 FUSE
//! 层之上，经常返回"理论上有权限"，而真正 `opendir` 时才失败。零宽漏洞恰恰发生在
//! 更底层——所以必须实际打开目录，看内核给不给。
//!
//! 一次探测最多 6 次 `read_dir`，都在毫秒级，启动时跑一次没有负担。

use std::path::Path;

use super::variant::{Variant, DATA_ROOT, OBB_ROOT, VARIANTS};

/// 一次探测的结果。
#[derive(Debug, Clone, Default)]
pub struct Pick {
    /// 原生能不能读（不需要任何变形）。
    pub native_ok: bool,
    /// 命中的候选下标。
    pub index: Option<usize>,
    /// 每个候选的实测结果，给诊断页展示。
    pub tried: Vec<(&'static str, bool)>,
    /// 探测时间。
    pub at: i64,
}

impl Pick {
    /// 有没有可用的零宽通道。
    pub fn usable(&self) -> bool {
        self.index.is_some()
    }

    /// 命中的候选名。
    pub fn name(&self) -> &'static str {
        match self.index {
            Some(i) => VARIANTS[i].name,
            None => "不可用",
        }
    }

    /// 当前应该用哪个变形。
    pub fn variant(&self) -> Option<&'static Variant> {
        self.index.map(|i| &VARIANTS[i])
    }

    /// 把路径按命中变形改写；没命中就原样返回。
    pub fn map(&self, path: &str) -> String {
        match self.variant() {
            Some(v) => v.apply(path),
            None => path.to_string(),
        }
    }
}

/// 实测。`enabled=false` 时只测原生，不试零宽（省时间、也避免无谓的路径变形）。
pub fn probe(enabled: bool) -> Pick {
    let mut p = Pick {
        native_ok: can_list(DATA_ROOT) || can_list(OBB_ROOT),
        at: crate::core::base::clock::now_ms(),
        ..Default::default()
    };
    if p.native_ok {
        // 原生能读，不需要任何变形。仍然把"原路径"记成可用，
        // 这样设置页能显示"原路径直读"而不是"不可用"。
        p.index = Some(0);
        return p;
    }
    if !enabled {
        return p;
    }
    for (i, v) in VARIANTS.iter().enumerate() {
        // 原路径已经在 native_ok 里测过，跳过，省一次系统调用
        if i == 0 {
            p.tried.push((v.name, false));
            continue;
        }
        let ok = can_list(&v.apply(DATA_ROOT)) || can_list(&v.apply(OBB_ROOT));
        p.tried.push((v.name, ok));
        if ok {
            p.index = Some(i);
            break;
        }
    }
    p
}

/// 目录能不能列。
pub fn can_list(p: &str) -> bool {
    Path::new(p).read_dir().is_ok()
}

/// 目录能不能读（能列出一个条目）。
pub fn can_read(p: &str) -> bool {
    Path::new(p)
        .read_dir()
        .map(|mut d| d.next().is_some())
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn probe_runs_without_panic() {
        let p = probe(true);
        // 桌面端 /storage 不存在，大概率 native_ok=false、index=None
        assert!(p.at > 0);
    }

    #[test]
    fn can_list_tmp() {
        assert!(can_list("/tmp") || can_list("/"));
        assert!(!can_list("/definitely/not/a/dir/xyz"));
    }

    #[test]
    fn map_without_pick_is_identity() {
        let p = Pick::default();
        assert_eq!(p.map("/a/b"), "/a/b");
        assert!(!p.usable());
    }
}
