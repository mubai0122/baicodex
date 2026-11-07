//! 配置档（多套模型配置）。
//!
//! 一个用户常常有"日常用的便宜模型"和"关键时刻的强模型"，也可能有公司网关与个人
//! 账号两套 key。配置档就是把这些打包成可切换的条目。
//!
//! 两条规则：
//! - **档里不存明文 key**。档只存 `id`，真正的 key 在 vault 的 `profile:<id>` 下。
//! - **切换档 = 覆盖顶层字段**。切换后上层只读顶层，不需要到处判断"当前用哪档"。

use crate::core::base::ids::IdGen;
use crate::core::feature::model::provider::Provider;

use super::schema::Profile;

/// 新建一个配置档。
pub fn create(name: &str, p: &Provider, model: &str) -> Profile {
    Profile {
        id: IdGen::short("pf"),
        name: name.to_string(),
        provider_id: p.id.clone(),
        base_url: p.base_url.clone(),
        api_key: String::new(),
        model: if model.is_empty() { p.model.clone() } else { model.to_string() },
        vision: p.caps.vision,
        tool_call: p.caps.tools,
        reasoning: p.caps.reasoning,
    }
}

/// 插入或更新（按 id）。返回 id。
pub fn upsert(list: &mut Vec<Profile>, mut p: Profile) -> String {
    if p.id.is_empty() {
        p.id = IdGen::short("pf");
    }
    if p.name.trim().is_empty() {
        p.name = format!("{} / {}", p.provider_id, p.model);
    }
    match list.iter().position(|x| x.id == p.id) {
        Some(i) => {
            // 空字段不覆盖旧值：前端只提交改动的字段时不会把别的清掉
            let old = &list[i];
            if p.base_url.is_empty() {
                p.base_url = old.base_url.clone();
            }
            if p.model.is_empty() {
                p.model = old.model.clone();
            }
            let id = p.id.clone();
            list[i] = p;
            id
        }
        None => {
            let id = p.id.clone();
            list.push(p);
            id
        }
    }
}

/// 删除。返回是否删掉了。
pub fn remove(list: &mut Vec<Profile>, id: &str) -> bool {
    let before = list.len();
    list.retain(|p| p.id != id);
    list.len() != before
}

/// 复制一份（"另存为"）。
pub fn duplicate(list: &mut Vec<Profile>, id: &str) -> Option<String> {
    let src = list.iter().find(|p| p.id == id)?.clone();
    let mut copy = src;
    copy.id = IdGen::short("pf");
    copy.name = format!("{} 副本", copy.name);
    let new_id = copy.id.clone();
    list.push(copy);
    Some(new_id)
}

/// 打码后的 key，界面上显示 `sk-…abcd`。
pub fn mask(key: &str) -> String {
    let k = key.trim();
    if k.is_empty() {
        return String::new();
    }
    let n = k.chars().count();
    if n <= 8 {
        return "***".into();
    }
    let head: String = k.chars().take(4).collect();
    let tail: String = k.chars().skip(n - 4).collect();
    format!("{head}…{tail}")
}

/// 从供应商列表里挑一个，生成默认配置档名。
pub fn suggest_name(providers: &[Provider], provider_id: &str, model: &str) -> String {
    let pname = providers
        .iter()
        .find(|p| p.id == provider_id)
        .map(|p| p.name.clone())
        .unwrap_or_else(|| provider_id.to_string());
    if model.is_empty() {
        pname
    } else {
        format!("{pname} · {model}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn upsert_generates_id_and_keeps_fields() {
        let mut list = Vec::new();
        let p = Provider::new("x", "X", "https://x/v1", "m");
        let pf = create("默认", &p, "");
        // create 会从供应商补全 base_url / model
        assert_eq!(pf.base_url, "https://x/v1");
        let id = upsert(&mut list, pf);
        assert!(!id.is_empty());
        assert_eq!(list[0].base_url, "https://x/v1");
        // 再 upsert 同一个 id：空字段不覆盖旧值（前端只提交改动字段的场景）
        let mut again = list[0].clone();
        again.model = String::new();
        again.base_url = String::new();
        upsert(&mut list, again);
        assert_eq!(list[0].model, "m");
        assert_eq!(list[0].base_url, "https://x/v1");
    }

    #[test]
    fn mask_hides_middle() {
        assert_eq!(mask("sk-1234567890abcd"), "sk-1…abcd");
        assert_eq!(mask("short"), "***");
        assert_eq!(mask(""), "");
    }
}
