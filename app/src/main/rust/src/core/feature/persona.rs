//! 角色卡：一个角色 = 一段人设提示 + 一个开场白，可选绑定一套模型配置。
//!
//! 存在 `<数据根>/personas.json`，用户可以直接改文件；模型在"创造模式"下也能改。
//!
//! ## 为什么人设不做成"多轮对话"
//!
//! 人设是**每轮都要在场**的东西。做成一条历史消息，压缩上下文时会被摘要掉，
//! 角色就慢慢跑了。拼在系统提示最前面才是稳的。

use serde::{Deserialize, Serialize};

use crate::core::base::{dirs, fsx};

const FILE: &str = "personas.json";

/// 一个角色。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Persona {
    pub id: String,
    pub name: String,
    /// 人设提示，拼在系统提示最前面。
    #[serde(default)]
    pub prompt: String,
    /// 新会话的开场白，空就不发。
    #[serde(default)]
    pub opener: String,
    /// 绑定的模型配置 id，空 = 跟随全局。
    #[serde(default)]
    pub profile_id: String,
    /// 内置角色不能删。
    #[serde(default)]
    pub builtin: bool,
}

impl Persona {
    pub fn is_empty(&self) -> bool {
        self.id.trim().is_empty()
    }

    /// 设置页一行。
    pub fn line(&self) -> String {
        format!(
            "{}{}：{}",
            self.name,
            if self.builtin { "（内置）" } else { "" },
            crate::core::base::text::truncate(self.prompt.trim(), 80)
        )
    }
}

/// 全部角色。
#[derive(Debug, Default)]
pub struct Personas {
    pub list: Vec<Persona>,
}

impl Personas {
    /// 加载。文件不存在时写入内置三个，方便用户照着改。
    pub fn load(base: &std::path::Path) -> Self {
        let p = base.join(FILE);
        let mut list: Vec<Persona> = std::fs::read_to_string(&p)
            .ok()
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or_default();
        if list.is_empty() {
            list = presets();
            let _ = save_to(base, &list);
        }
        Self { list }
    }

    pub fn save(&self, base: &std::path::Path) -> bool {
        save_to(base, &self.list)
    }

    pub fn current(&self, id: &str) -> Option<&Persona> {
        let k = id.trim();
        if k.is_empty() {
            return None;
        }
        self.list.iter().find(|p| p.id == k).or_else(|| self.list.iter().find(|p| p.name == k))
    }

    /// 系统提示里那一段。
    pub fn prompt(&self, id: &str) -> String {
        match self.current(id) {
            Some(p) if !p.prompt.trim().is_empty() => format!(
                "{}\n\n（以上是你的角色设定，全程保持；不要跳出角色自称 AI。）",
                p.prompt.trim()
            ),
            _ => String::new(),
        }
    }

    pub fn opener(&self, id: &str) -> String {
        self.current(id).map(|p| p.opener.clone()).unwrap_or_default()
    }

    /// 绑定到某个角色的模型配置（空表示跟随全局）。
    pub fn profile_of(&self, id: &str) -> String {
        self.current(id).map(|p| p.profile_id.clone()).unwrap_or_default()
    }

    /// 新增或更新。
    pub fn upsert(&mut self, mut p: Persona) -> String {
        if p.id.trim().is_empty() {
            p.id = crate::core::base::ids::IdGen::hex(8);
        }
        if p.name.trim().is_empty() {
            p.name = "未命名角色".into();
        }
        match self.list.iter_mut().find(|x| x.id == p.id) {
            Some(slot) => {
                // 内置标记不允许被外部改掉，否则内置角色就变成可删的了。
                let builtin = slot.builtin;
                *slot = p.clone();
                slot.builtin = builtin;
            }
            None => self.list.push(p.clone()),
        }
        p.id
    }

    /// 删除。内置角色删不掉，返回 `false`。
    pub fn remove(&mut self, id: &str) -> bool {
        let before = self.list.len();
        self.list.retain(|p| p.id != id || p.builtin);
        self.list.len() != before
    }

    pub fn names(&self) -> Vec<String> {
        self.list.iter().map(|p| p.name.clone()).collect()
    }

    /// 给界面的紧凑列表：`(id, 名字, 是否内置)`。
    pub fn brief(&self) -> Vec<(String, String, bool)> {
        self.list.iter().map(|p| (p.id.clone(), p.name.clone(), p.builtin)).collect()
    }
}

fn save_to(base: &std::path::Path, list: &[Persona]) -> bool {
    let Ok(t) = serde_json::to_string_pretty(list) else { return false };
    fsx::ensure_dir(base).is_ok() && fsx::write_text(base.join(FILE), &t).is_ok()
}

/// 内置角色。
pub fn presets() -> Vec<Persona> {
    vec![
        Persona {
            id: "default".into(),
            name: "默认助手".into(),
            prompt: "你是一个跑在用户设备上的 AI Agent。说话直接、不客套；能动手就不要只给建议；不确定就先查再答。"
                .into(),
            opener: String::new(),
            profile_id: String::new(),
            builtin: true,
        },
        Persona {
            id: "engineer".into(),
            name: "工程师".into(),
            prompt: "你是资深工程师。给方案前先看代码与现状，改动要能回退；结论在前，依据在后，不解释常识。"
                .into(),
            opener: "把要改的东西发我，我先看现状再动手。".into(),
            profile_id: String::new(),
            builtin: true,
        },
        Persona {
            id: "secretary".into(),
            name: "助理".into(),
            prompt: "你是用户的私人助理。办事细致，时间、地点、金额这类信息一定核对；办完用一两句话汇报结果。"
                .into(),
            opener: String::new(),
            profile_id: String::new(),
            builtin: true,
        },
    ]
}

/// 数据根下的默认文件名，给外部（备份/导出）用。
pub fn file_name() -> std::path::PathBuf {
    dirs::file(FILE)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builtin_cannot_be_removed() {
        let mut p = Personas { list: presets() };
        assert!(!p.remove("default"));
        assert!(!p.remove("nope"));
    }

    #[test]
    fn upsert_assigns_id() {
        let mut p = Personas::default();
        let id = p.upsert(Persona { name: "x".into(), ..Default::default() });
        assert!(!id.is_empty());
        assert_eq!(p.list.len(), 1);
    }
}
