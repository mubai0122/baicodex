//! 技能包：一个目录一个包，包内若干函数。
//!
//! 两种清单都认：
//!
//! - `skill.json`：显式声明 `funcs`，每个函数指向 `function/<文件>`；
//! - 只有 `SKILL.md`：纯说明，不注册函数（正文型技能，见 [super::Skills]）。
//!
//! ## 函数体是模板，不是代码
//!
//! `${参数}` 会被替换成调用时给的值，`${secret:名字}` 从凭据库取。命令类按行执行、
//! 逐行判错；`http` 类把渲染结果当原始报文发出去（首行 `METHOD URL`）。
//!
//! ## 为什么要在加载时静态校验
//!
//! 一个占位符写错的函数，模型会在**真正需要它的时候**才发现调不通，然后反复重试。
//! 加载时就把问题标出来，`bad` 非空就不下发给模型——宁可这个函数暂时消失，
//! 也不要让模型在坏工具上打转。

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::core::base::error::{BError, BResult};
use crate::core::feature::config::schema::Config;
use crate::core::security::vault::{Vault, BUCKET_API};

/// 支持的 DSL 版本。
pub const DSL_VERSION: i64 = 1;

/// 校验问题。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LintIssue(pub String);

impl std::fmt::Display for LintIssue {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

impl LintIssue {
    fn new(s: impl Into<String>) -> Self {
        Self(s.into())
    }
}

/// 一个函数。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SkillFn {
    /// 所属包 id。
    pub skill: String,
    /// 工具名（模型看到的名字，必须全局唯一）。
    pub name: String,
    /// `cmd` 或 `http`。
    pub kind: String,
    pub desc: String,
    /// 参数名 → 校验正则（空串 = 不校验）。
    pub params: BTreeMap<String, String>,
    pub file: String,
    pub body: String,
    /// 校验没过的原因；非空就不下发。
    pub bad: String,
}

impl SkillFn {
    /// 参数名列表，用于报错与提示词。
    pub fn param_names(&self) -> Vec<String> {
        self.params.keys().cloned().collect()
    }

    /// 工具定义。
    pub fn definition(&self) -> serde_json::Value {
        let mut props = serde_json::Map::new();
        let mut required = Vec::new();
        for (k, re) in &self.params {
            let mut p = serde_json::json!({ "type": "string" });
            if !re.trim().is_empty() {
                p["description"] = serde_json::json!(format!("格式：{re}"));
            }
            props.insert(k.clone(), p);
            required.push(serde_json::json!(k));
        }
        serde_json::json!({
            "type": "function",
            "function": {
                "name": self.name,
                "description": format!("[技能:{}] {}", self.skill, self.desc),
                "parameters": {
                    "type": "object",
                    "properties": props,
                    "required": required,
                }
            }
        })
    }
}

/// 一个包。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SkillPack {
    pub id: String,
    pub name: String,
    pub desc: String,
    /// `SKILL.md` 正文（没有就是空）。
    pub readme: String,
    pub funcs: Vec<SkillFn>,
    pub dir: String,
    pub on: bool,
}

impl SkillPack {
    /// 有几个能用的函数。
    pub fn usable(&self) -> usize {
        self.funcs.iter().filter(|f| f.bad.is_empty()).count()
    }
}

/// 全部包。
#[derive(Debug, Default)]
pub struct SkillPacks {
    pub packs: Vec<SkillPack>,
}

impl SkillPacks {
    /// 扫一个目录下的所有子目录。
    pub fn load_dir(root: &Path) -> Self {
        let mut packs = Vec::new();
        let Ok(rd) = std::fs::read_dir(root) else { return Self { packs } };
        let mut dirs: Vec<PathBuf> = rd.flatten().map(|e| e.path()).filter(|p| p.is_dir()).collect();
        dirs.sort();
        for d in dirs {
            if let Some(p) = load_one(&d) {
                packs.push(p);
            }
        }
        Self { packs }
    }

    /// 能下发的函数（包启用 + 校验通过）。
    pub fn funcs(&self) -> Vec<SkillFn> {
        self.packs
            .iter()
            .filter(|p| p.on)
            .flat_map(|p| p.funcs.iter().cloned())
            .filter(|f| f.bad.is_empty())
            .collect()
    }

    /// 重名检测：返回重复的名字（加载后调用一次，重复的会被标记为不可用）。
    pub fn dedup(&mut self) -> Vec<String> {
        let mut seen: BTreeMap<String, usize> = BTreeMap::new();
        let mut dup = Vec::new();
        for p in &mut self.packs {
            for f in &mut p.funcs {
                let n = seen.entry(f.name.clone()).or_insert(0);
                *n += 1;
                if *n > 1 {
                    f.bad = format!("工具名 {} 与别的技能包重名", f.name);
                    dup.push(f.name.clone());
                }
            }
        }
        dup
    }

    pub fn find(&self, name: &str) -> Option<&SkillFn> {
        self.packs
            .iter()
            .flat_map(|p| p.funcs.iter())
            .find(|f| f.name == name && f.bad.is_empty())
    }

    /// 找包（id 或名字，忽略大小写）。
    pub fn pack(&self, key: &str) -> Option<&SkillPack> {
        let k = key.trim();
        if k.is_empty() {
            return None;
        }
        let lower = k.to_lowercase();
        self.packs
            .iter()
            .find(|p| p.id == k)
            .or_else(|| self.packs.iter().find(|p| p.name == k))
            .or_else(|| {
                self.packs
                    .iter()
                    .find(|p| p.id.to_lowercase() == lower || p.name.to_lowercase() == lower)
            })
    }

    pub fn toggle(&mut self, key: &str, on: bool) -> bool {
        match self.packs.iter_mut().find(|p| p.id == key || p.name == key) {
            Some(p) => {
                p.on = on;
                true
            }
            None => false,
        }
    }

    /// 提示词片段。
    pub fn prompt(&self) -> String {
        let list: Vec<&SkillPack> = self.packs.iter().filter(|p| p.on).collect();
        if list.is_empty() {
            return String::new();
        }
        let mut s = String::from("技能包（先 skill_read 看用法，再直接调函数）：\n");
        for p in list {
            s.push_str(&format!("- {}（{}）：{}\n", p.name, p.id, p.desc));
            for f in &p.funcs {
                let args = f.param_names().join(", ");
                if f.bad.is_empty() {
                    s.push_str(&format!("  · {}({args})\n", f.name));
                } else {
                    s.push_str(&format!("  · {}({args}) ← 不可用：{}\n", f.name, f.bad));
                }
            }
        }
        s
    }

    /// 一句话摘要，设置页用。
    pub fn summary(&self) -> String {
        let on = self.packs.iter().filter(|p| p.on).count();
        format!("{} 个包（启用 {on}），{} 个可用函数", self.packs.len(), self.funcs().len())
    }
}

fn load_one(d: &Path) -> Option<SkillPack> {
    let dir_name = d.file_name()?.to_string_lossy().to_string();
    let jf = d.join("skill.json");
    if jf.is_file() {
        let raw = std::fs::read_to_string(&jf).ok()?;
        let o: serde_json::Value = serde_json::from_str(&raw).ok()?;
        let id = {
            let x = o["id"].as_str().unwrap_or("").trim().to_string();
            if x.is_empty() { dir_name.clone() } else { x }
        };
        let ver = o["dsl"].as_i64().unwrap_or(1);
        let readme = std::fs::read_to_string(d.join("SKILL.md"))
            .or_else(|_| std::fs::read_to_string(d.join("README.md")))
            .unwrap_or_default();
        let mut funcs = Vec::new();

        for f in o["funcs"].as_array().cloned().unwrap_or_default() {
            let name = f["name"].as_str().unwrap_or("").trim().to_string();
            if name.is_empty() {
                continue;
            }
            // 工具名只能是小写字母/数字/下划线：模型按名字调，大小写混着来必错。
            if !name.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_') {
                funcs.push(SkillFn {
                    skill: id.clone(),
                    name: name.clone(),
                    bad: format!("函数名 {name} 只能用小写字母、数字、下划线"),
                    ..Default::default()
                });
                continue;
            }
            let kind = {
                let k = f["kind"].as_str().unwrap_or("cmd").trim().to_lowercase();
                if k.is_empty() { "cmd".into() } else { k }
            };
            let file = {
                let x = f["file"].as_str().unwrap_or("").trim();
                if x.is_empty() { format!("{name}.sh") } else { x.to_string() }
            };
            let mut params = BTreeMap::new();
            if let Some(m) = f["params"].as_object() {
                for (k, v) in m {
                    params.insert(k.clone(), v.as_str().unwrap_or("").to_string());
                }
            }
            let body = std::fs::read_to_string(d.join("function").join(&file)).ok();
            let bad = if ver != DSL_VERSION {
                format!("不支持的 dsl 版本 {ver}（当前 {DSL_VERSION}）")
            } else if body.is_none() {
                format!("缺 function/{file}")
            } else {
                lint(body.as_deref().unwrap_or(""), &kind, &params)
                    .map(|i| i.0)
                    .unwrap_or_default()
            };
            funcs.push(SkillFn {
                skill: id.clone(),
                name,
                kind,
                desc: f["desc"].as_str().unwrap_or("").to_string(),
                params,
                file,
                body: body.unwrap_or_default(),
                bad,
            });
        }

        return Some(SkillPack {
            id: id.clone(),
            name: {
                let n = o["name"].as_str().unwrap_or("").trim();
                if n.is_empty() {
                    id
                } else {
                    n.to_string()
                }
            },
            desc: o["desc"].as_str().unwrap_or("").to_string(),
            readme: readme.trim().to_string(),
            funcs,
            dir: d.to_string_lossy().to_string(),
            on: o["on"].as_bool().unwrap_or(true),
        });
    }

    // 只有 SKILL.md 的包：纯说明。
    let mf = d.join("SKILL.md");
    if !mf.is_file() {
        return None;
    }
    let raw = std::fs::read_to_string(&mf).ok()?;
    let (meta, body) = frontmatter(&raw);
    let name = meta
        .get("name")
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| dir_name.clone());
    Some(SkillPack {
        id: dir_name.clone(),
        name,
        desc: meta.get("description").cloned().unwrap_or_default(),
        readme: body.trim().to_string(),
        funcs: Vec::new(),
        dir: d.to_string_lossy().to_string(),
        on: true,
    })
}

/// 拆 YAML frontmatter：只认顶层 `key: value` 与 `>` / `|` 折行写法。
///
/// 不引 YAML 库：技能文件的 frontmatter 就这几个键，引一个几百 K 的解析器不值。
pub fn frontmatter(raw: &str) -> (BTreeMap<String, String>, String) {
    let text = raw.trim_start_matches('\u{feff}');
    let lines: Vec<&str> = text.split('\n').collect();
    if lines.first().map(|l| l.trim()) != Some("---") {
        return (BTreeMap::new(), text.to_string());
    }
    let mut map = BTreeMap::new();
    let mut key = String::new();
    let mut folded = String::new();
    let mut i = 1;

    fn flush(map: &mut BTreeMap<String, String>, key: &mut String, folded: &mut String) {
        if !key.is_empty() && !folded.is_empty() {
            map.insert(key.clone(), folded.trim().to_string());
        }
        key.clear();
        folded.clear();
    }

    while i < lines.len() {
        let l = lines[i];
        if l.trim() == "---" {
            break;
        }
        if l.starts_with(' ') || l.starts_with('\t') {
            if !key.is_empty() {
                if !folded.is_empty() {
                    folded.push(' ');
                }
                folded.push_str(l.trim());
            }
            i += 1;
            continue;
        }
        flush(&mut map, &mut key, &mut folded);
        if let Some(c) = l.find(':') {
            if c > 0 {
                let k = l[..c].trim().to_lowercase();
                let v = l[c + 1..].trim();
                if matches!(v, ">" | "|" | ">-" | "|-") {
                    key = k;
                } else if v.len() >= 2
                    && ((v.starts_with('"') && v.ends_with('"')) || (v.starts_with('\'') && v.ends_with('\'')))
                {
                    map.insert(k, v[1..v.len() - 1].to_string());
                } else {
                    map.insert(k, v.to_string());
                }
            }
        }
        i += 1;
    }
    flush(&mut map, &mut key, &mut folded);
    let body = if i < lines.len() { lines[i + 1..].join("\n") } else { String::new() };
    (map, body)
}

/// 静态校验：占位符是否都在参数表里、引号是否成对、http 首行是否合法。
pub fn lint(body: &str, kind: &str, params: &BTreeMap<String, String>) -> Option<LintIssue> {
    if body.trim().is_empty() {
        return Some(LintIssue::new("函数体为空"));
    }
    if kind != "cmd" && kind != "http" {
        return Some(LintIssue::new(format!("不认识的 kind：{kind}（只支持 cmd / http）")));
    }
    for (name, _) in placeholders(body) {
        if name.is_empty() {
            return Some(LintIssue::new("有未闭合的 ${ 占位符"));
        }
        if let Some(rest) = name.strip_prefix("secret:") {
            if rest.trim().is_empty() {
                return Some(LintIssue::new("secret 占位符缺少名字"));
            }
            continue;
        }
        if !params.contains_key(&name) {
            return Some(LintIssue::new(format!("占位符 ${{{name}}} 不在 params 里")));
        }
    }
    if body.matches('\'').count() % 2 != 0 {
        return Some(LintIssue::new("单引号不成对（拼进 shell 会把命令截断）"));
    }
    if kind == "http" {
        let first = body.lines().next().unwrap_or("").trim();
        let seg: Vec<&str> = first.splitn(2, ' ').collect();
        if seg.len() < 2 || !seg[1].trim().starts_with("http") {
            return Some(LintIssue::new("http 报文首行应是「METHOD URL」"));
        }
    }
    None
}

/// 取出所有 `${...}` 占位符及其位置。
pub fn placeholders(body: &str) -> Vec<(String, usize)> {
    let mut out = Vec::new();
    let mut rest = body;
    let mut base = 0;
    while let Some(i) = rest.find("${") {
        let start = base + i;
        match rest[i + 2..].find('}') {
            Some(j) => {
                let name = rest[i + 2..i + 2 + j].trim().to_string();
                out.push((name, start));
                let adv = i + 2 + j + 1;
                base += adv;
                rest = &rest[adv..];
            }
            None => {
                out.push((String::new(), start));
                break;
            }
        }
    }
    out
}

/// 渲染模板。
///
/// - 普通参数从 `values` 取，缺了直接报错（不要用空串顶替）；
/// - `secret:名字` 从凭据库取，取不到也报错——静默给空串会让远端认证失败，
///   而报错信息通常只有一句"permission denied"，排查起来很痛苦。
pub fn render(
    body: &str,
    params: &BTreeMap<String, String>,
    values: &BTreeMap<String, String>,
    vault: &Vault,
) -> BResult<String> {
    // 先校验格式，再替换：顺序反了的话，非法字符已经进了命令。
    for (k, re) in params {
        if re.trim().is_empty() {
            continue;
        }
        let Some(v) = values.get(k) else { continue };
        let ok = regex::Regex::new(re).map(|r| r.is_match(v)).unwrap_or(true);
        if !ok {
            return Err(BError::invalid(format!("参数 {k} 不符合格式 {re}")));
        }
    }

    let mut out = body.to_string();
    for (name, _) in placeholders(body) {
        if let Some(k) = name.strip_prefix("secret:") {
            let v = vault.get(BUCKET_API, k.trim());
            if v.is_empty() {
                return Err(BError::not_found(format!("凭据库缺少 {k}")));
            }
            out = out.replace(&format!("${{{name}}}"), &v);
        } else {
            let Some(v) = values.get(&name) else {
                return Err(BError::invalid(format!("缺少参数 {name}")));
            };
            if v.is_empty() {
                return Err(BError::invalid(format!("参数 {name} 是空的")));
            }
            out = out.replace(&format!("${{{name}}}"), v);
        }
    }
    Ok(out)
}

/// 技能函数该用什么超时。`http` 给长一点。
pub fn timeout_of(kind: &str, cfg: &Config) -> std::time::Duration {
    let s = if kind == "http" {
        120
    } else {
        cfg.tool.shell_timeout_s.clamp(1, 1800)
    };
    std::time::Duration::from_secs(s)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lint_catches_unknown_placeholder() {
        let mut params = BTreeMap::new();
        params.insert("city".to_string(), String::new());
        assert!(lint("curl wttr.in/${city}", "cmd", &params).is_none());
        assert!(lint("curl wttr.in/${town}", "cmd", &params).is_some());
    }

    #[test]
    fn lint_http_first_line() {
        let p = BTreeMap::new();
        assert!(lint("GET https://a.com\nAccept: */*", "http", &p).is_none());
        assert!(lint("https://a.com", "http", &p).is_some());
    }

    #[test]
    fn placeholder_positions() {
        let v = placeholders("a${x}b${y}");
        assert_eq!(v.len(), 2);
        assert_eq!(v[0].0, "x");
    }
}
