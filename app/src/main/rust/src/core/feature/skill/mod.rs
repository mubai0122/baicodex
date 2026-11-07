//! 技能：正文型（`SKILL.md`）与函数型（`skill.json` + `function/*`）。
//!
//! ## 为什么分成两个结构
//!
//! 正文型技能只是"一段说明书"，模型读完照着做，**不需要注册工具**；函数型技能
//! 会把自己的函数注册成独立工具，模型可以直接调。两者的加载、校验、提示词注入
//! 都不同，硬塞进一个结构会到处 `if kind == ...`。
//!
//! ## 目录约定
//!
//! ```text
//! <数据根>/skill/<包名>/SKILL.md          正文型（通用 Agent Skills 格式）
//! <数据根>/skill/<包名>/skill.json        函数型清单
//! <数据根>/skill/<包名>/function/x.sh     函数体（模板）
//! <数据根>/skills/<名字>.md               单文件正文型
//! ```
//!
//! ## 提示词只放"索引"
//!
//! 技能正文可能有几千字。全塞进系统提示等于每轮都付一遍 token。所以提示词里
//! 只列名字 + 一句说明，正文靠 `skill_read` 现场取。

pub mod pack;

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

pub use pack::{lint, placeholders, render, LintIssue, SkillFn, SkillPack, SkillPacks};

/// 正文型技能。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Skill {
    /// 调用名（目录名或文件名，小写）。
    pub name: String,
    /// 展示名（frontmatter 里的 `name`）。
    pub title: String,
    pub desc: String,
    /// 正文（frontmatter 之后的全部）。
    pub body: String,
    /// 来源：`dir` / `file`。
    pub origin: String,
    pub dir: String,
    pub bytes: u64,
}

impl Skill {
    /// 给提示词的一行。
    pub fn line(&self) -> String {
        let d = if self.desc.trim().is_empty() { "（无说明）" } else { self.desc.trim() };
        format!("- {}：{}", self.name, crate::core::base::text::truncate(d, 140))
    }

    /// 给 `skill_read` 的完整内容。
    pub fn full(&self) -> String {
        let mut s = format!("# {}\n\n", self.title);
        if !self.desc.trim().is_empty() {
            s.push_str(self.desc.trim());
            s.push_str("\n\n");
        }
        s.push_str(&self.body);
        s
    }
}

/// 全部正文型技能。
#[derive(Debug, Default)]
pub struct Skills {
    pub list: Vec<Skill>,
}

impl Skills {
    /// 扫两个目录。`max` 为 0 表示不限。
    ///
    /// 加载失败不报错：一个坏掉的 SKILL.md 不该让整个应用起不来。
    pub fn load(base: &Path, max: usize) -> Self {
        let mut list: Vec<Skill> = Vec::new();
        let mut roots: Vec<PathBuf> = vec![base.join("skill"), base.join("skills")];
        // 兼容旧版把技能放在 jniskill 里的情况。
        roots.push(base.join("jniskill"));

        for root in roots {
            let Ok(rd) = std::fs::read_dir(&root) else { continue };
            let mut items: Vec<PathBuf> = rd.flatten().map(|e| e.path()).collect();
            items.sort();
            for p in items {
                if p.is_dir() {
                    let md = p.join("SKILL.md");
                    if md.is_file() {
                        if let Some(s) = load_md(&md, &p) {
                            list.push(s);
                        }
                    }
                } else if p.extension().and_then(|x| x.to_str()).map(|x| x.eq_ignore_ascii_case("md")).unwrap_or(false) {
                    if let Some(s) = load_md(&p, Path::new(&root_of(&p))) {
                        list.push(s);
                    }
                }
            }
        }

        // 同名去重：先加载的优先（`skill/` 优先于 `skills/`）。
        let mut seen = std::collections::BTreeSet::new();
        list.retain(|s| seen.insert(s.name.clone()));
        list.sort_by(|a, b| a.name.cmp(&b.name));
        if max > 0 {
            list.truncate(max);
        }
        Self { list }
    }

    pub fn len(&self) -> usize {
        self.list.len()
    }

    pub fn is_empty(&self) -> bool {
        self.list.is_empty()
    }

    pub fn get(&self, name: &str) -> Option<&Skill> {
        let k = name.trim().to_lowercase();
        self.list
            .iter()
            .find(|s| s.name.to_lowercase() == k || s.title.to_lowercase() == k)
    }

    /// 模糊找：名字包含关键词。
    pub fn find(&self, key: &str) -> Vec<&Skill> {
        let k = key.trim().to_lowercase();
        if k.is_empty() {
            return Vec::new();
        }
        self.list
            .iter()
            .filter(|s| s.name.to_lowercase().contains(&k) || s.desc.to_lowercase().contains(&k))
            .collect()
    }

    pub fn names(&self) -> Vec<String> {
        self.list.iter().map(|s| s.name.clone()).collect()
    }

    /// 提示词片段。没技能就返回空串，别在提示里留个空标题。
    pub fn prompt(&self) -> String {
        if self.list.is_empty() {
            return String::new();
        }
        let mut s = String::from("可用技能（需要细节时调 skill_read 取全文）：\n");
        for x in self.list.iter().take(60) {
            s.push_str(&x.line());
            s.push('\n');
        }
        if self.list.len() > 60 {
            s.push_str(&format!("…（共 {} 个）\n", self.list.len()));
        }
        s
    }

    /// 重新加载（用户装了新技能后调）。
    pub fn reload(&mut self, base: &Path) {
        *self = Self::load(base, 0);
    }

    /// 删除一个技能（只删正文型；函数型走 [SkillPacks]）。
    pub fn remove(&mut self, name: &str) -> bool {
        let Some(s) = self.get(name).cloned() else { return false };
        let target = if s.origin == "file" {
            PathBuf::from(&s.dir).join(format!("{}.md", s.name))
        } else {
            PathBuf::from(&s.dir)
        };
        let ok = if target.is_dir() {
            std::fs::remove_dir_all(&target).is_ok()
        } else {
            std::fs::remove_file(&target).is_ok()
        };
        if ok {
            self.list.retain(|x| x.name != s.name);
        }
        ok
    }
}

fn root_of(p: &Path) -> String {
    p.parent().map(|x| x.to_string_lossy().to_string()).unwrap_or_default()
}

/// 读一个 md，拆 frontmatter。
fn load_md(path: &Path, dir: &Path) -> Option<Skill> {
    let raw = std::fs::read_to_string(path).ok()?;
    let bytes = raw.len() as u64;
    let (meta, body) = pack::frontmatter(&raw);
    let stem = path
        .file_stem()
        .map(|x| x.to_string_lossy().to_string())
        .unwrap_or_default();
    let folder = dir.file_name().map(|x| x.to_string_lossy().to_string()).unwrap_or_default();
    // SKILL.md 用目录名当调用名；单文件用文件名。
    let name = if stem.eq_ignore_ascii_case("skill") || stem.eq_ignore_ascii_case("readme") {
        folder.to_lowercase()
    } else {
        stem.to_lowercase()
    };
    if name.is_empty() {
        return None;
    }
    let title = meta
        .get("name")
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| name.clone());
    let desc = meta
        .get("description")
        .or_else(|| meta.get("desc"))
        .cloned()
        .unwrap_or_default();
    Some(Skill {
        name,
        title,
        desc,
        body: body.trim().to_string(),
        origin: if path.file_name().map(|x| x.to_string_lossy().to_lowercase()) == Some("skill.md".into()) {
            "dir".into()
        } else {
            "file".into()
        },
        dir: dir.to_string_lossy().to_string(),
        bytes,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn frontmatter_split() {
        let raw = "---\nname: 测试\ndescription: 一句话\n---\n\n正文第一行\n正文第二行\n";
        let (m, body) = pack::frontmatter(raw);
        assert_eq!(m.get("name").unwrap(), "测试");
        assert!(body.contains("正文第一行"));
    }

    #[test]
    fn placeholders_found() {
        let p = placeholders("echo ${a} ${secret:token}");
        assert_eq!(p.len(), 2);
        assert_eq!(p[0].0, "a");
    }
}
