//! 插件：把"别人写好的技能包"装进来。
//!
//! 一个插件就是一个 zip：解到 `<数据根>/skill/<id>/`，重启后由 [super::skill] 扫到。
//! 没有编译、没有动态库、没有权限提升——**插件的能力上限就是技能包的能力上限**，
//! 这是有意的：允许第三方代码在本进程里跑，等于把用户设备交出去。
//!
//! ## 解压必须防穿越
//!
//! zip 里的条目名可以是 `../../.ssh/authorized_keys`。解压前逐个检查，任何跳出
//! 目标目录的条目直接拒绝整个包——不要"跳过那一条继续解"，因为半个包比没有更危险。

use std::io::Read;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::core::base::bytes;
use crate::core::base::fsx;

/// 清单里的一个插件。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Plugin {
    pub id: String,
    pub name: String,
    /// `skill` / `persona` / `theme`。
    #[serde(default)]
    pub kind: String,
    pub desc: String,
    /// 下载地址（zip）。
    #[serde(default)]
    pub url: String,
    #[serde(default)]
    pub version: String,
    #[serde(default)]
    pub author: String,
    /// 已安装的版本；空 = 没装。
    #[serde(default)]
    pub installed: String,
}

impl Plugin {
    pub fn is_installed(&self) -> bool {
        !self.installed.is_empty()
    }

    /// 市场列表一行。
    pub fn line(&self) -> String {
        format!(
            "{}{} v{}　{}",
            self.name,
            if self.is_installed() { "（已装）" } else { "" },
            if self.version.is_empty() { "?" } else { &self.version },
            crate::core::base::text::truncate(&self.desc, 60)
        )
    }
}

/// 本地市场缓存（`plugins.json`）。真正的内容在远端，这里只是"上次看到什么"。
#[derive(Debug, Default)]
pub struct Market {
    pub list: Vec<Plugin>,
    base: PathBuf,
}

impl Market {
    pub fn load(base: &Path) -> Self {
        let list: Vec<Plugin> = std::fs::read_to_string(base.join("plugins.json"))
            .ok()
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or_default();
        Self { list, base: base.to_path_buf() }
    }

    pub fn save(&self) -> bool {
        let Ok(t) = serde_json::to_string_pretty(&self.list) else { return false };
        fsx::writable_dir(&self.base) && fsx::write_text(self.base.join("plugins.json"), &t).is_ok()
    }

    pub fn find(&self, id: &str) -> Option<&Plugin> {
        self.list.iter().find(|p| p.id == id || p.name == id)
    }

    /// 用远端清单覆盖本地（保留 `installed` 标记）。
    pub fn merge(&mut self, remote: Vec<Plugin>) {
        for mut p in remote {
            if let Some(old) = self.list.iter().find(|x| x.id == p.id) {
                p.installed = old.installed.clone();
            }
            match self.list.iter_mut().find(|x| x.id == p.id) {
                Some(slot) => *slot = p,
                None => self.list.push(p),
            }
        }
        self.save();
    }

    /// 从 URL 拉清单（JSON 数组）。
    pub async fn refresh(&mut self, url: &str) -> Result<usize, String> {
        if url.trim().is_empty() {
            return Err("没有配置市场地址".into());
        }
        let resp = crate::net::client::shared()
            .get(url.trim())
            .send()
            .await
            .map_err(|e| format!("拉清单失败：{e}"))?;
        if !resp.status().is_success() {
            return Err(format!("清单返回 HTTP {}", resp.status().as_u16()));
        }
        let text = resp.text().await.map_err(|e| format!("读清单失败：{e}"))?;
        let v: serde_json::Value = serde_json::from_str(&text).map_err(|e| format!("清单不是 JSON：{e}"))?;
        let arr = v
            .as_array()
            .cloned()
            .or_else(|| v["plugins"].as_array().cloned())
            .unwrap_or_default();
        let mut list = Vec::new();
        for item in arr {
            let id = item["id"].as_str().unwrap_or("").trim();
            if id.is_empty() {
                continue;
            }
            list.push(Plugin {
                id: id.to_string(),
                name: item["name"].as_str().unwrap_or(id).to_string(),
                kind: item["kind"].as_str().unwrap_or("skill").to_string(),
                desc: item["desc"].as_str().unwrap_or("").to_string(),
                url: item["url"].as_str().unwrap_or("").to_string(),
                version: item["version"].as_str().unwrap_or("").to_string(),
                author: item["author"].as_str().unwrap_or("").to_string(),
                installed: String::new(),
            });
        }
        let n = list.len();
        self.merge(list);
        Ok(n)
    }

    /// 标记已安装。
    pub fn mark(&mut self, id: &str, version: &str) {
        if let Some(p) = self.list.iter_mut().find(|p| p.id == id) {
            p.installed = version.to_string();
            self.save();
        }
    }
}

/// 装一个插件：下载 zip → 校验 → 解压到 `skill/<id>/`。
pub async fn install(base: &Path, p: &Plugin) -> Result<usize, String> {
    if p.url.trim().is_empty() {
        return Err("这个插件没有下载地址".into());
    }
    if !p.id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_') {
        return Err(format!("插件 id 只能用字母数字与 - _：{}", p.id));
    }
    let zip_path = crate::core::base::dirs::tmp(&format!("plugin-{}.zip", p.id));
    let (ok, msg) = crate::net::http::download(&p.url, &zip_path.to_string_lossy()).await;
    if !ok {
        return Err(format!("下载失败：{msg}"));
    }
    let dest = base.join("skill").join(&p.id);
    let out = extract(&zip_path, &dest);
    let _ = std::fs::remove_file(&zip_path);
    out
}

/// 解压到 `dest`，返回文件数。
///
/// 两步走：先全量校验（所有条目都在 dest 里），再解压。中途发现越界就整个失败，
/// 已解出的文件也删掉——不留半成品。
pub fn extract(zip_path: &Path, dest: &Path) -> Result<usize, String> {
    let f = std::fs::File::open(zip_path).map_err(|e| format!("打不开压缩包：{e}"))?;
    let mut zip = zip::ZipArchive::new(f).map_err(|e| format!("不是合法 zip：{e}"))?;
    if zip.len() > 2000 {
        return Err("压缩包条目太多（上限 2000）".into());
    }

    // 第一遍：校验。
    for i in 0..zip.len() {
        let Ok(entry) = zip.by_index(i) else { continue };
        let name = entry.name().to_string();
        if name.contains("..") || name.starts_with('/') || name.contains('\\') {
            return Err(format!("压缩包里有可疑路径：{name}"));
        }
        if entry.size() > 32 * 1024 * 1024 {
            return Err(format!("条目太大：{name}"));
        }
    }

    if fsx::ensure_dir(dest).is_err() {
        return Err("目标目录建不出来".into());
    }
    let mut n = 0;
    for i in 0..zip.len() {
        let Ok(mut entry) = zip.by_index(i) else { continue };
        let Some(rel) = entry.enclosed_name() else {
            let _ = std::fs::remove_dir_all(dest);
            return Err("压缩包条目路径不合法".into());
        };
        let out = dest.join(rel);
        if entry.is_dir() {
            let _ = fsx::ensure_dir(&out);
            continue;
        }
        if let Some(d) = out.parent() {
            let _ = fsx::ensure_dir(d);
        }
        let mut buf = Vec::new();
        if entry.read_to_end(&mut buf).is_err() {
            let _ = std::fs::remove_dir_all(dest);
            return Err(format!("解压条目失败：{}", entry.name()));
        }
        if crate::core::base::atomic::write(&out, &buf).is_err() {
            let _ = std::fs::remove_dir_all(dest);
            return Err(format!("写文件失败：{}", out.display()));
        }
        n += 1;
    }
    if n == 0 {
        let _ = std::fs::remove_dir_all(dest);
        return Err("压缩包是空的".into());
    }
    Ok(n)
}

/// 卸载：删目录。
pub fn uninstall(base: &Path, id: &str) -> bool {
    if id.trim().is_empty() || id.contains('/') || id.contains("..") {
        return false;
    }
    let d = base.join("skill").join(id);
    if !d.is_dir() {
        return false;
    }
    std::fs::remove_dir_all(d).is_ok()
}

/// 已安装的插件（扫目录，看有没有 skill.json / SKILL.md）。
pub fn installed(base: &Path) -> Vec<(String, u64)> {
    let root = base.join("skill");
    let Ok(rd) = std::fs::read_dir(&root) else { return Vec::new() };
    let mut out = Vec::new();
    for e in rd.flatten() {
        let p = e.path();
        if !p.is_dir() {
            continue;
        }
        let has = p.join("skill.json").is_file() || p.join("SKILL.md").is_file();
        if !has {
            continue;
        }
        let size = fsx::size_of(&p);
        out.push((p.file_name().map(|x| x.to_string_lossy().to_string()).unwrap_or_default(), size));
    }
    out.sort();
    out
}

/// 一句话摘要。
pub fn summary(base: &Path) -> String {
    let list = installed(base);
    if list.is_empty() {
        return "没有安装插件".into();
    }
    let total: u64 = list.iter().map(|(_, s)| s).sum();
    format!("已安装 {} 个插件，共 {}", list.len(), bytes::human_size(total))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn id_charset() {
        assert!(is_safe_id("my-plugin_1"));
        assert!(!is_safe_id("../evil"));
    }

    fn is_safe_id(s: &str) -> bool {
        !s.is_empty() && s.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
    }

    #[test]
    fn mark_installed() {
        let mut m = Market::default();
        m.list.push(Plugin { id: "a".into(), ..Default::default() });
        m.mark("a", "1.0");
        assert_eq!(m.list[0].installed, "1.0");
    }
}
