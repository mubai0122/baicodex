//! 配置加载与迁移。
//!
//! 三种输入都要能吃：
//! 1. **新格式**（带 `version` 且 `provider` 是对象）——直接反序列化。
//! 2. **旧格式**（一百多个平铺字段）——逐字段映射进新结构，凭据单独交出去。
//! 3. **半坏的文件**（少字段、类型错）——能读多少读多少，读不出的用默认值。
//!
//! 第 3 种最重要：用户手改配置改坏一个逗号，不能让 App 起不来。所以这里**从不返回错误**，
//! 最差的情况是返回默认配置，并在日志里留一条。

use std::path::{Path, PathBuf};

use serde_json::Value;

use crate::core::base::atomic;
use crate::core::base::error::{BError, BResult};
use crate::core::base::jsonx;
use crate::core::feature::model::mode::{AgentMode, WorkMode};
use crate::core::feature::model::provider::{AuthStyle, Provider};

use super::schema::*;

/// 配置文件路径。
pub fn path(base: impl AsRef<Path>) -> PathBuf {
    base.as_ref().join("config.json")
}

/// 系统提示词文件（用户可编辑的覆盖）。
pub fn prompt_path(base: impl AsRef<Path>) -> PathBuf {
    base.as_ref().join("system_prompt.md")
}

/// 加载结果。凭据单独拎出来，由调用方写进 vault。
#[derive(Debug, Clone)]
pub struct Loaded {
    pub config: Config,
    /// 旧配置里的明文 key 列表（迁移后应清空）。
    pub legacy_keys: Vec<String>,
    /// 旧配置里的单个 api_key。
    pub legacy_api_key: String,
    /// 是否发生了结构迁移（需要回写）。
    pub migrated: bool,
}

/// 加载。永远成功。
pub fn load(base: impl AsRef<Path>) -> Loaded {
    let p = path(&base);
    let raw = std::fs::read_to_string(&p).unwrap_or_default();
    if raw.trim().is_empty() {
        return Loaded { config: Config::default(), legacy_keys: Vec::new(), legacy_api_key: String::new(), migrated: false };
    }
    let v: Value = match serde_json::from_str(&raw) {
        Ok(v) => v,
        Err(e) => {
            crate::log_warn!("config", "配置文件解析失败，用默认配置：{e}");
            return Loaded {
                config: Config::default(),
                legacy_keys: Vec::new(),
                legacy_api_key: String::new(),
                migrated: false,
            };
        }
    };
    let (config, keys, api_key, migrated) = from_value(v);
    Loaded { config, legacy_keys: keys, legacy_api_key: api_key, migrated }
}

/// 从 JSON 值构造配置。返回 `(配置, 旧 key 列表, 旧单 key, 是否迁移)`。
pub fn from_value(v: Value) -> (Config, Vec<String>, String, bool) {
    let is_new = v.get("version").is_some() && v.get("provider").map(|x| x.is_object()).unwrap_or(false);
    if is_new {
        let mut cfg: Config = serde_json::from_value(v).unwrap_or_default();
        if cfg.provider.list.is_empty() {
            cfg.provider.list = crate::core::feature::model::provider::builtin();
        }
        let need = cfg.version < VERSION;
        if need {
            cfg.version = VERSION;
        }
        return (cfg, Vec::new(), String::new(), need);
    }
    // —— 旧格式：平铺字段 ——
    let mut cfg = Config::default();
    let api_key = jsonx::get_str(&v, &["api_key", "key"], "");
    let keys = jsonx::get_str_array(&v, &["keys"]);

    cfg.onboarded = jsonx::get_bool(&v, &["onboarded"], false);
    cfg.agent.language = jsonx::get_str(&v, &["language"], "zh");
    cfg.ui.font_scale = jsonx::get_f64(&v, &["font_scale"], 1.0) as f32;
    cfg.ui.dark = jsonx::get_i64(&v, &["dark_theme"], 1) != 0;

    if let Some(m) = jsonx::get_nonempty(&v, &["work_mode"]).and_then(|s| WorkMode::parse(&s)) {
        cfg.agent.work_mode = m;
    }
    if let Some(m) = jsonx::get_nonempty(&v, &["agent_mode"]).and_then(|s| AgentMode::parse(&s)) {
        cfg.agent.mode = m;
    }
    cfg.security.zero_width = jsonx::get_bool(&v, &["zero_width"], false);

    cfg.provider.id = jsonx::get_str(&v, &["provider_id"], "deepseek");
    cfg.provider.base_url = jsonx::get_str(&v, &["base_url"], &cfg.provider.base_url);
    cfg.provider.model = jsonx::get_str(&v, &["model"], &cfg.provider.model);
    cfg.provider.auth = AuthStyle::parse(&jsonx::get_str(&v, &["auth"], "bearer"));
    cfg.provider.no_stream = jsonx::get_bool(&v, &["no_stream"], false);
    cfg.provider.profile_id = jsonx::get_str(&v, &["profile_id"], "");
    cfg.provider.profiles = v
        .get("profiles")
        .and_then(|x| serde_json::from_value::<Vec<Profile>>(x.clone()).ok())
        .unwrap_or_default();
    if let Some(list) = v.get("providers") {
        if let Ok(mut ps) = serde_json::from_value::<Vec<Provider>>(list.clone()) {
            if !ps.is_empty() {
                ps.extend(crate::core::feature::model::provider::builtin());
                ps.dedup_by(|a, b| a.id == b.id);
                cfg.provider.list = ps;
            }
        }
    }

    cfg.model.vision = jsonx::get_bool(&v, &["model_vision", "vision"], false);
    cfg.model.tools = jsonx::get_bool(&v, &["model_tool_call", "tool_call"], true);
    cfg.model.reasoning = jsonx::get_bool(&v, &["model_reasoning", "reasoning"], false);
    cfg.model.temperature = jsonx::get_f64(&v, &["temperature"], -1.0) as f32;
    cfg.model.max_tokens = jsonx::get_i64(&v, &["max_tokens"], 0);
    cfg.model.max_mode = jsonx::get_bool(&v, &["max_mode"], false);
    cfg.model.think = jsonx::get_bool(&v, &["think"], false);
    cfg.model.effort = jsonx::get_str(&v, &["think_effort", "effort"], "medium");
    if let Some(raw) = jsonx::get_nonempty(&v, &["extra_params"]) {
        if let Ok(Value::Object(m)) = serde_json::from_str::<Value>(&raw) {
            cfg.model.extra_params = m;
        }
    }

    cfg.context.auto_compact = jsonx::get_bool(&v, &["ctx_auto"], true);
    cfg.context.budget_chars = jsonx::get_i64(&v, &["ctx_budget"], 240_000);
    cfg.context.keep_msgs = jsonx::get_i64(&v, &["ctx_keep"], 12).max(2) as usize;
    cfg.context.msg_count = jsonx::get_i64(&v, &["ctx_msg_count"], 80).max(8) as usize;
    cfg.context.summary_prompt = jsonx::get_str(&v, &["summary_prompt"], "");
    cfg.context.summary_words = jsonx::get_i64(&v, &["summary_words"], 600);
    cfg.context.summary_profile = jsonx::get_str(&v, &["summary_profile_id"], "");

    cfg.tool.budget_chars = jsonx::get_i64(&v, &["tool_budget_tokens"], 16_000).max(1000) as usize;
    for (k, val) in jsonx::get_obj(&v, &["policies"]) {
        if let Some(s) = val.as_str() {
            cfg.policies.insert(k, s.to_string());
        }
    }

    cfg.agent.system_prompt = jsonx::get_str(&v, &["system_prompt"], "");
    cfg.agent.persona = jsonx::get_str(&v, &["persona_id"], "");
    cfg.workspace.root = jsonx::get_str(&v, &["default_workspace"], "");
    cfg.workspace.current = jsonx::get_str(&v, &["workspace", "session_workspace"], "");
    cfg.workspace.auto_bind = jsonx::get_bool(&v, &["auto_bind_workspace"], true);
    cfg.workspace.inject_time = jsonx::get_bool(&v, &["inject_time"], true);
    cfg.workspace.inject_battery = jsonx::get_bool(&v, &["inject_battery"], true);
    cfg.workspace.inject_place = jsonx::get_bool(&v, &["inject_place"], true);
    cfg.workspace.place_precise = jsonx::get_bool(&v, &["inject_precise"], false);

    cfg.search.provider = jsonx::get_str(&v, &["search_provider"], "tavily");
    if let Some(table) = v.get("search").and_then(|x| x.as_object()) {
        if let Some(t) = table.get("tavily").and_then(|x| x.as_object()) {
            if let Some(h) = t.get("host").and_then(|x| x.as_str()) {
                cfg.search.tavily_host = h.to_string();
            }
        }
        if let Some(g) = table.get("google").and_then(|x| x.as_object()) {
            if let Some(cx) = g.get("cx").and_then(|x| x.as_str()) {
                cfg.search.google_cx = cx.to_string();
            }
        }
    }

    cfg.usage.price_in = jsonx::get_f64(&v, &["price_in"], 1.0);
    cfg.usage.price_out = jsonx::get_f64(&v, &["price_out"], 2.0);
    cfg.usage.price_cache = jsonx::get_f64(&v, &["price_cache"], 0.0);

    // a2a 的旧字段原样留在 extra 里，由 net::a2a 自己读
    cfg.version = VERSION;
    (cfg, keys, api_key, true)
}

/// 保存。原子写；保存前把当前顶层同步回配置档。
pub fn save(base: impl AsRef<Path>, cfg: &Config) -> BResult<()> {
    let mut c = cfg.clone();
    c.sync_profile();
    c.version = VERSION;
    let text = serde_json::to_string_pretty(&c)
        .map_err(|e| BError::internal("配置序列化失败").with_detail(e.to_string()))?;
    atomic::write(path(base), text.as_bytes())
}

/// 读系统提示词：文件 > 配置字段 > 空串（由 prompt 模块补默认）。
pub fn system_prompt(base: impl AsRef<Path>, cfg: &Config) -> String {
    match std::fs::read_to_string(prompt_path(base)) {
        Ok(s) if !s.trim().is_empty() => s,
        _ => cfg.agent.system_prompt.clone(),
    }
}

/// 写系统提示词到文件。
pub fn set_system_prompt(base: impl AsRef<Path>, text: &str) -> BResult<()> {
    atomic::write(prompt_path(base), text.as_bytes())
}

/// 当前配置里"有效"的 key 数量。
pub fn key_count(cfg: &Config) -> usize {
    cfg.provider.key_slots
}

/// 把一份旧配置转成新结构并写回（用户点"升级配置"时）。
pub fn upgrade_in_place(base: impl AsRef<Path>) -> BResult<bool> {
    let p = path(&base);
    let raw = std::fs::read_to_string(&p).unwrap_or_default();
    if raw.trim().is_empty() {
        return Ok(false);
    }
    let v: Value = serde_json::from_str(&raw)?;
    let (cfg, _, _, migrated) = from_value(v);
    if migrated {
        save(base, &cfg)?;
    }
    Ok(migrated)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn old_flat_config_migrates() {
        let v = serde_json::json!({
            "base_url": "https://api.deepseek.com/v1",
            "model": "deepseek-chat",
            "api_key": "sk-old",
            "keys": ["sk-1", "sk-2"],
            "temperature": 0.7,
            "model_vision": true,
            "ctx_budget": 100000,
            "policies": {"shell": "allow"},
            "zero_width": true
        });
        let (cfg, keys, key, migrated) = from_value(v);
        assert!(migrated);
        assert_eq!(cfg.provider.model, "deepseek-chat");
        assert_eq!(cfg.model.temperature, 0.7);
        assert!(cfg.model.vision);
        assert_eq!(cfg.context.budget_chars, 100000);
        assert_eq!(cfg.policies.get("shell").map(|s| s.as_str()), Some("allow"));
        assert!(cfg.security.zero_width);
        assert_eq!(keys.len(), 2);
        assert_eq!(key, "sk-old");
    }

    #[test]
    fn broken_json_returns_default() {
        let dir = std::env::temp_dir().join(format!("bai-cfg-{}", crate::core::base::ids::IdGen::hex(6)));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(path(&dir), "{ broken").unwrap();
        let l = load(&dir);
        assert!(l.config.ready());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn save_and_reload() {
        let dir = std::env::temp_dir().join(format!("bai-cfg-{}", crate::core::base::ids::IdGen::hex(6)));
        let mut c = Config::default();
        c.provider.model = "custom-model".into();
        save(&dir, &c).unwrap();
        let l = load(&dir);
        assert_eq!(l.config.provider.model, "custom-model");
        assert!(!l.migrated);
        std::fs::remove_dir_all(&dir).ok();
    }
}
