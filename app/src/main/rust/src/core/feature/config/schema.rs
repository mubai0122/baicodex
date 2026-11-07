//! 配置结构。
//!
//! 分成若干组，而不是一百多个平铺字段。理由：
//!
//! - **改一组不影响另一组**。加一个 UI 开关不需要动模型配置的代码。
//! - **序列化出来能读**。嵌套 JSON 比一百个平铺键好排查。
//! - **前端可以按组取**。设置页一屏就是一个组，FFI 只传需要的部分。
//!
//! 兼容旧配置：`config::load` 会把平铺的老字段映射进对应组，见 `config::load`。
//! 未识别的字段统一进 [Config::extra]，原样保留——降级运行时不至于把用户设置吃掉。

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::core::feature::model::mode::{AgentMode, WorkMode};
use crate::core::feature::model::provider::{AuthStyle, Provider};

/// 配置版本。结构变化时 +1，`load` 里按版本迁移。
pub const VERSION: u32 = 3;

/// 一套模型配置（供应商 + 端点 + key + 模型 + 能力）。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub provider_id: String,
    pub base_url: String,
    /// 只存"引用"，真正的 key 在 vault 里，键名是 `profile:<id>`。
    /// 这里保留字段是为了兼容旧配置（旧版把 key 明文写在这）。
    #[serde(skip_serializing_if = "String::is_empty")]
    pub api_key: String,
    pub model: String,
    pub vision: bool,
    pub tool_call: bool,
    pub reasoning: bool,
}

impl Default for Profile {
    fn default() -> Self {
        Self {
            id: String::new(),
            name: String::new(),
            provider_id: String::new(),
            base_url: String::new(),
            api_key: String::new(),
            model: String::new(),
            vision: false,
            tool_call: true,
            reasoning: false,
        }
    }
}

/// 供应商相关。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct ProviderCfg {
    /// 当前供应商 id。
    pub id: String,
    pub base_url: String,
    pub model: String,
    pub auth: AuthStyle,
    /// 多 key 轮换。vault 里存 `provider:<id>:<n>`；这里只留"哪些位置有 key"。
    pub key_slots: usize,
    /// 当前用到第几个 key。
    pub key_index: usize,
    pub no_stream: bool,
    /// 额外请求头。
    pub headers: Vec<(String, String)>,
    /// 全部供应商（内置 + 自定义）。
    pub list: Vec<Provider>,
    /// 多套配置档。
    pub profiles: Vec<Profile>,
    pub profile_id: String,
}

impl Default for ProviderCfg {
    fn default() -> Self {
        Self {
            id: "deepseek".into(),
            base_url: "https://api.deepseek.com/v1".into(),
            model: "deepseek-chat".into(),
            auth: AuthStyle::Bearer,
            key_slots: 0,
            key_index: 0,
            no_stream: false,
            headers: Vec::new(),
            list: crate::core::feature::model::provider::builtin(),
            profiles: Vec::new(),
            profile_id: String::new(),
        }
    }
}

/// 模型能力与采样参数。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct ModelCfg {
    pub vision: bool,
    pub tools: bool,
    pub reasoning: bool,
    pub context: u32,
    /// 负数 = 请求里不带 temperature（部分推理模型不接受）。
    pub temperature: f32,
    pub top_p: f32,
    /// 0 = 不带 max_tokens，交给服务端。
    pub max_tokens: i64,
    /// 上不封顶模式：不传 max_tokens。
    pub max_mode: bool,
    /// 是否请求思考过程。
    pub think: bool,
    /// `low` / `medium` / `high`。
    pub effort: String,
    /// 厂商私有字段，浅合并进请求体。
    pub extra_params: Map<String, Value>,
}

impl Default for ModelCfg {
    fn default() -> Self {
        Self {
            vision: false,
            tools: true,
            reasoning: false,
            context: 128_000,
            temperature: -1.0,
            top_p: -1.0,
            max_tokens: 0,
            max_mode: false,
            think: false,
            effort: "medium".into(),
            extra_params: Map::new(),
        }
    }
}

/// Agent 行为。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct AgentCfg {
    pub work_mode: WorkMode,
    pub mode: AgentMode,
    /// 自定义系统提示。空 = 用默认。
    pub system_prompt: String,
    /// 绑定的人设 id。
    pub persona: String,
    /// 提示语言：`zh` / `en` / `auto`。
    pub language: String,
    /// 首条消息后自动生成标题。
    pub auto_title: bool,
    /// 最大轮次。0 = 不限（默认，符合原实现）。
    pub max_rounds: usize,
    /// 是否流式。
    pub stream: bool,
    /// 计划模式：先出计划再执行。
    pub plan_first: bool,
    /// 一轮里最多并行几个工具调用。
    pub parallel_tools: usize,
}

impl Default for AgentCfg {
    fn default() -> Self {
        Self {
            work_mode: WorkMode::Normal,
            mode: AgentMode::Standard,
            system_prompt: String::new(),
            persona: String::new(),
            language: "zh".into(),
            auto_title: true,
            max_rounds: 0,
            stream: true,
            plan_first: false,
            parallel_tools: 4,
        }
    }
}

/// 上下文与压缩。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct ContextCfg {
    pub auto_compact: bool,
    /// 字符预算（≈ token × 1.8）。超过就压。
    pub budget_chars: i64,
    /// 压缩时保留最近多少条消息。
    pub keep_msgs: usize,
    /// 条数阈值：只在"占用超过 1/4 预算"时才允许单凭条数触发压缩。
    pub msg_count: usize,
    /// 摘要提示词。空 = 用内置。
    pub summary_prompt: String,
    /// 摘要目标字数。
    pub summary_words: i64,
    /// 用哪个配置档做摘要（省钱的小模型）。空 = 用主配置。
    pub summary_profile: String,
}

impl Default for ContextCfg {
    fn default() -> Self {
        Self {
            auto_compact: true,
            budget_chars: 240_000,
            keep_msgs: 12,
            msg_count: 80,
            summary_prompt: String::new(),
            summary_words: 600,
            summary_profile: String::new(),
        }
    }
}

/// 工具执行。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct ToolCfg {
    /// 单个工具结果最多多少字符（超过截断）。
    pub budget_chars: usize,
    /// shell 默认超时（秒）。
    pub shell_timeout_s: u64,
    /// shell 输出最多多少字符。
    pub max_output_chars: usize,
    /// 高风险工具是否每次确认。
    pub confirm_high_risk: bool,
    /// 是否允许联网工具。
    pub allow_network: bool,
    /// 工具并发上限。
    pub max_parallel: usize,
    /// 读文件的最大字节数。
    pub max_read_bytes: usize,
}

impl Default for ToolCfg {
    fn default() -> Self {
        Self {
            budget_chars: 32_000,
            shell_timeout_s: 120,
            max_output_chars: 200_000,
            confirm_high_risk: true,
            allow_network: true,
            max_parallel: 4,
            max_read_bytes: 2 * 1024 * 1024,
        }
    }
}

/// 记忆。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct MemoryCfg {
    pub enabled: bool,
    /// 最多存多少条。
    pub max_items: usize,
    /// 每次召回几条。
    pub recall_top: usize,
    /// 召回最低分。低于它的不塞进提示词。
    pub min_score: f64,
    /// 提示词里记忆段落的字符上限。
    pub prompt_chars: usize,
}

impl Default for MemoryCfg {
    fn default() -> Self {
        Self { enabled: true, max_items: 200, recall_top: 8, min_score: 0.12, prompt_chars: 2000 }
    }
}

/// 搜索。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct SearchCfg {
    /// `tavily` / `bing` / `google`。
    pub provider: String,
    /// Tavily 的接口地址（可换中转）。
    pub tavily_host: String,
    /// Google CSE 的 cx。
    pub google_cx: String,
    /// Bing 的接口地址。
    pub bing_host: String,
    pub max_results: usize,
}

impl Default for SearchCfg {
    fn default() -> Self {
        Self {
            provider: "tavily".into(),
            tavily_host: "https://api.tavily.com".into(),
            google_cx: String::new(),
            bing_host: "https://www.bing.com".into(),
            max_results: 6,
        }
    }
}

/// 工作区与环境注入。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct WorkspaceCfg {
    /// 默认工作区根。空 = `<base>/workspace`。
    pub root: String,
    /// 上次绑定的目录。
    pub current: String,
    /// 新会话自动绑定默认工作区。
    pub auto_bind: bool,
    /// 提示词里注入时间。
    pub inject_time: bool,
    pub inject_battery: bool,
    pub inject_place: bool,
    /// 位置精度：`city` / `precise`。
    pub place_precise: bool,
    /// 工具清单里列出的工作区文件数上限。
    pub tree_files: usize,
}

impl Default for WorkspaceCfg {
    fn default() -> Self {
        Self {
            root: String::new(),
            current: String::new(),
            auto_bind: true,
            inject_time: true,
            inject_battery: true,
            inject_place: true,
            place_precise: false,
            tree_files: 40,
        }
    }
}

/// 安全。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct SecurityCfg {
    /// 开零宽通道。
    pub zero_width: bool,
    /// 免打扰：Ask 降级为 Allow（Deny 不受影响）。
    pub auto_allow: bool,
    /// 记审计。
    pub audit: bool,
    /// 额外允许根。
    pub extra_roots: Vec<String>,
    /// 显式放行的敏感路径。
    pub allow_sensitive: Vec<String>,
    /// 数据保障开关（三层密钥）。
    pub guard: bool,
}

impl Default for SecurityCfg {
    fn default() -> Self {
        Self {
            zero_width: false,
            auto_allow: false,
            audit: true,
            extra_roots: Vec::new(),
            allow_sensitive: Vec::new(),
            guard: false,
        }
    }
}

/// 用量与计价。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct UsageCfg {
    /// 货币符号。
    pub currency: String,
    /// 输入价（元 / 百万 token）。
    pub price_in: f64,
    pub price_out: f64,
    /// 缓存命中价。0 = 按输入价一半算。
    pub price_cache: f64,
    /// 是否记录用量。
    pub enabled: bool,
}

impl Default for UsageCfg {
    fn default() -> Self {
        Self { currency: "¥".into(), price_in: 1.0, price_out: 2.0, price_cache: 0.0, enabled: true }
    }
}

/// 界面。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct UiCfg {
    /// 主题 id。
    pub theme: String,
    pub dark: bool,
    pub font_scale: f32,
    /// 默认展开工具输出。
    pub expand_tools: bool,
    /// 显示思考过程。
    pub show_reasoning: bool,
    /// 侧栏默认展开。
    pub sidebar: bool,
    /// 消息间距。
    pub gap: u16,
}

impl Default for UiCfg {
    fn default() -> Self {
        Self {
            theme: "auto".into(),
            dark: true,
            font_scale: 1.0,
            expand_tools: false,
            show_reasoning: true,
            sidebar: true,
            gap: 1,
        }
    }
}

/// 全部配置。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Config {
    pub version: u32,
    pub onboarded: bool,
    pub provider: ProviderCfg,
    pub model: ModelCfg,
    pub agent: AgentCfg,
    pub context: ContextCfg,
    pub tool: ToolCfg,
    pub memory: MemoryCfg,
    pub search: SearchCfg,
    pub workspace: WorkspaceCfg,
    pub security: SecurityCfg,
    pub usage: UsageCfg,
    pub ui: UiCfg,
    /// 工具策略（工具名 → allow/ask/deny）。规则表在 `policy.json`。
    pub policies: BTreeMap<String, String>,
    /// 未识别字段，原样保留。
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            version: VERSION,
            onboarded: false,
            provider: ProviderCfg::default(),
            model: ModelCfg::default(),
            agent: AgentCfg::default(),
            context: ContextCfg::default(),
            tool: ToolCfg::default(),
            memory: MemoryCfg::default(),
            search: SearchCfg::default(),
            workspace: WorkspaceCfg::default(),
            security: SecurityCfg::default(),
            usage: UsageCfg::default(),
            ui: UiCfg::default(),
            policies: BTreeMap::new(),
            extra: Map::new(),
        }
    }
}

impl Config {
    /// 是否配好了：有 base_url 与模型名。
    pub fn ready(&self) -> bool {
        !self.provider.base_url.trim().is_empty() && !self.provider.model.trim().is_empty()
    }

    /// 当前配置档。
    pub fn profile(&self) -> Option<&Profile> {
        if self.provider.profile_id.is_empty() {
            return None;
        }
        self.provider.profiles.iter().find(|p| p.id == self.provider.profile_id)
    }

    /// 把当前配置档应用到顶层（切换配置档时调用）。
    pub fn apply_profile(&mut self, id: &str) -> bool {
        let Some(p) = self.provider.profiles.iter().find(|p| p.id == id).cloned() else {
            return false;
        };
        self.provider.profile_id = id.to_string();
        if !p.provider_id.is_empty() {
            self.provider.id = p.provider_id.clone();
        }
        if !p.base_url.is_empty() {
            self.provider.base_url = p.base_url.clone();
        }
        if !p.model.is_empty() {
            self.provider.model = p.model.clone();
        }
        self.model.vision = p.vision;
        self.model.tools = p.tool_call;
        self.model.reasoning = p.reasoning;
        true
    }

    /// 把当前顶层写回配置档（保存时调用）。
    pub fn sync_profile(&mut self) {
        let id = self.provider.profile_id.clone();
        if id.is_empty() {
            return;
        }
        let (base, model, pid, vision, tools, reasoning) = (
            self.provider.base_url.clone(),
            self.provider.model.clone(),
            self.provider.id.clone(),
            self.model.vision,
            self.model.tools,
            self.model.reasoning,
        );
        if let Some(p) = self.provider.profiles.iter_mut().find(|p| p.id == id) {
            p.base_url = base;
            p.model = model;
            p.provider_id = pid;
            p.vision = vision;
            p.tool_call = tools;
            p.reasoning = reasoning;
        }
    }

    /// 当前供应商定义。
    pub fn current_provider(&self) -> Option<&Provider> {
        self.provider.list.iter().find(|p| p.id == self.provider.id)
    }

    /// 摘要用的配置档（省钱的小模型）。没配就用主配置。
    pub fn summary_target(&self) -> (&str, &str) {
        if let Some(id) = Some(self.context.summary_profile.as_str()).filter(|s| !s.is_empty()) {
            if let Some(p) = self.provider.profiles.iter().find(|p| p.id == id) {
                return (p.base_url.as_str(), p.model.as_str());
            }
        }
        (self.provider.base_url.as_str(), self.provider.model.as_str())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_is_ready_ish() {
        let c = Config::default();
        assert!(c.ready());
        assert_eq!(c.version, VERSION);
    }

    #[test]
    fn profile_roundtrip() {
        let mut c = Config::default();
        c.provider.profiles.push(Profile {
            id: "p1".into(),
            name: "便宜".into(),
            base_url: "https://x/v1".into(),
            model: "m".into(),
            ..Default::default()
        });
        assert!(c.apply_profile("p1"));
        assert_eq!(c.provider.model, "m");
        c.provider.model = "m2".into();
        c.sync_profile();
        assert_eq!(c.provider.profiles[0].model, "m2");
    }

    #[test]
    fn unknown_fields_survive() {
        let raw = r#"{"version":3,"future_flag":true}"#;
        let c: Config = serde_json::from_str(raw).unwrap();
        assert_eq!(c.extra.get("future_flag"), Some(&Value::Bool(true)));
        let out = serde_json::to_string(&c).unwrap();
        assert!(out.contains("future_flag"));
    }
}
