//! 配置层。
//!
//! 对外只有 [ConfigStore] 一个东西：线程安全的配置镜像 + 原子保存。
//! 业务层通过 `app.cfg()` 拿到只读快照，改配置走 `app.update_config(|c| ...)`，
//! 不要各自 `load()` 一遍——那样会出现"两个模块各持一份配置，互相覆盖"的经典 bug。

pub mod load;
pub mod profile;
pub mod schema;

use std::path::PathBuf;
use std::sync::RwLock;

use crate::core::base::error::BResult;

pub use schema::*;

/// 配置存储。
///
/// 读多写少，用 `RwLock`：读配置发生在每一轮对话里，写只在用户点保存时。
#[derive(Debug)]
pub struct ConfigStore {
    base: PathBuf,
    cfg: RwLock<Config>,
}

impl ConfigStore {
    /// 打开：读盘 + 迁移。迁移产生的旧 key 由调用方另行处理（见 [load::Loaded]）。
    pub fn open(base: impl Into<PathBuf>) -> (Self, load::Loaded) {
        let base = base.into();
        let loaded = load::load(&base);
        let store = Self { base, cfg: RwLock::new(loaded.config.clone()) };
        (store, loaded)
    }

    /// 只用内存配置（测试、临时会话）。
    pub fn memory(cfg: Config) -> Self {
        Self { base: PathBuf::from("."), cfg: RwLock::new(cfg) }
    }

    pub fn base(&self) -> &std::path::Path {
        &self.base
    }

    /// 只读快照。克隆是有意的：配置很小，克隆比持锁穿越 await 安全得多。
    pub fn get(&self) -> Config {
        match self.cfg.read() {
            Ok(c) => c.clone(),
            Err(p) => p.into_inner().clone(),
        }
    }

    /// 改配置。`f` 里不要做 IO。
    pub fn update(&self, f: impl FnOnce(&mut Config)) -> Config {
        let mut g = match self.cfg.write() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        f(&mut g);
        g.clone()
    }

    /// 保存到磁盘。
    pub fn save(&self) -> BResult<()> {
        let cfg = self.get();
        load::save(&self.base, &cfg)
    }

    /// 重新读盘（外部改了配置时）。
    pub fn reload(&self) -> load::Loaded {
        let loaded = load::load(&self.base);
        if let Ok(mut g) = self.cfg.write() {
            *g = loaded.config.clone();
        }
        loaded
    }

    /// 系统提示词（文件优先）。
    pub fn system_prompt(&self) -> String {
        load::system_prompt(&self.base, &self.get())
    }

    /// 设置系统提示词（写文件）。
    pub fn set_system_prompt(&self, text: &str) -> BResult<()> {
        load::set_system_prompt(&self.base, text)?;
        self.update(|c| c.agent.system_prompt = text.to_string());
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn store_reads_and_writes() {
        let dir = std::env::temp_dir().join(format!("bai-store-{}", crate::core::base::ids::IdGen::hex(6)));
        let (s, _) = ConfigStore::open(&dir);
        s.update(|c| c.provider.model = "m2".into());
        s.save().unwrap();
        let (s2, _) = ConfigStore::open(&dir);
        assert_eq!(s2.get().provider.model, "m2");
        std::fs::remove_dir_all(&dir).ok();
    }
}
