//! 工具策略：Allow / Ask / Deny。
//!
//! 判定顺序是**固定的**，不能因为调用方是谁而变：
//!
//! ```text
//! 1. 硬红线（hard rule）     —— 命中就 Deny，用户设置改不了
//! 2. 参数级规则（rule）      —— 按顺序，第一条命中生效
//! 3. 工具默认（defaults）    —— 用户在设置页里逐项配的
//! 4. 风险兜底（risk）        —— 高风险 Ask，其余 Allow
//! ```
//!
//! 为什么硬红线要单独一层：`shell` 工具本身是 Allow 的（用户天天用），但
//! `rm -rf /` 必须拦。如果只靠"用户在 shell 上设了 Allow"，这条命令就放出去了。
//! 安全红线不能依赖用户配置正确。

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

pub mod rule;
pub mod store;

use rule::Rule;

/// 三档策略。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Policy {
    Allow,
    #[default]
    Ask,
    Deny,
}

impl Policy {
    pub fn as_str(self) -> &'static str {
        match self {
            Policy::Allow => "allow",
            Policy::Ask => "ask",
            Policy::Deny => "deny",
        }
    }

    pub fn parse(s: &str) -> Option<Self> {
        match s.trim().to_ascii_lowercase().as_str() {
            "allow" | "ok" | "yes" | "允许" => Some(Policy::Allow),
            "ask" | "confirm" | "问" | "询问" => Some(Policy::Ask),
            "deny" | "no" | "block" | "拒绝" => Some(Policy::Deny),
            _ => None,
        }
    }

    /// 中文标签，给设置页用。
    pub fn label(self) -> &'static str {
        match self {
            Policy::Allow => "允许",
            Policy::Ask => "每次询问",
            Policy::Deny => "禁止",
        }
    }
}

/// 工具风险等级。由工具的静态定义给出，不由用户配置。
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Risk {
    /// 只读、无副作用：读文件、列目录、查天气。
    Low,
    /// 有副作用但可恢复：写文件、改配置、装技能。
    Medium,
    /// 不可恢复或影响系统：执行命令、删文件、root、SSH、发消息。
    High,
}

impl Risk {
    /// 稳定字符串，给 FFI 与界面用。
    pub fn as_str(self) -> &'static str {
        match self {
            Risk::Low => "low",
            Risk::Medium => "medium",
            Risk::High => "high",
        }
    }

    /// 中文标签。
    pub fn label(self) -> &'static str {
        match self {
            Risk::Low => "低风险",
            Risk::Medium => "中风险",
            Risk::High => "高风险",
        }
    }

    /// 没配策略时，这个风险等级默认怎么办。
    pub fn fallback(self) -> Policy {
        match self {
            Risk::Low => Policy::Allow,
            Risk::Medium => Policy::Allow,
            // 高风险默认每次问。用户嫌烦可以显式改成 Allow——那是他的选择。
            Risk::High => Policy::Ask,
        }
    }
}

/// 判定结果。带原因，方便界面显示"为什么被拦了"。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Decision {
    pub policy: Policy,
    pub reason: String,
    /// 命中的规则 id（若有）。前端据此高亮"是哪条规则"。
    pub rule: Option<String>,
}

impl Decision {
    pub fn allow(reason: impl Into<String>) -> Self {
        Self { policy: Policy::Allow, reason: reason.into(), rule: None }
    }

    pub fn ask(reason: impl Into<String>) -> Self {
        Self { policy: Policy::Ask, reason: reason.into(), rule: None }
    }

    pub fn deny(reason: impl Into<String>) -> Self {
        Self { policy: Policy::Deny, reason: reason.into(), rule: None }
    }

    pub fn blocked(&self) -> bool {
        self.policy == Policy::Deny
    }

    pub fn needs_confirm(&self) -> bool {
        self.policy == Policy::Ask
    }
}

/// 一整套策略。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[derive(Default)]
pub struct PolicySet {
    /// 工具名 → 策略。
    #[serde(default)]
    pub defaults: BTreeMap<String, Policy>,
    /// 参数级规则，按顺序匹配。
    #[serde(default)]
    pub rules: Vec<Rule>,
    /// 全局开关：关掉后所有 Ask 降级为 Allow（"免打扰"模式）。
    /// 注意 **Deny 不受影响**——免打扰不等于免安全。
    #[serde(default)]
    pub auto_allow: bool,
}


impl PolicySet {
    /// 判定一次工具调用。
    ///
    /// `args_json` 是模型给的原始参数串：规则里的正则直接匹配它，而不是匹配解析后的
    /// 结构。这样规则可以写成 `"command":\s*"rm\s+-rf` 这种形式，也能匹配到
    /// 嵌套字段，不需要理解结构。
    pub fn decide(&self, tool: &str, args_json: &str, risk: Risk) -> Decision {
        // 只整理一次：规则可能有几十条，每条都去解析一遍 JSON 就太浪费了。
        let probe = rule::probe_text(args_json);
        // 1. 硬红线
        for r in self.rules.iter().filter(|r| r.hard) {
            if r.matches(tool, &probe) {
                return Decision {
                    policy: Policy::Deny,
                    reason: if r.note.is_empty() {
                        format!("命中安全红线：{}", r.describe())
                    } else {
                        r.note.clone()
                    },
                    rule: Some(r.id.clone()),
                };
            }
        }

        // 2. 参数级规则
        for r in self.rules.iter().filter(|r| !r.hard) {
            if r.matches(tool, &probe) {
                let policy = if self.auto_allow && r.policy == Policy::Ask {
                    Policy::Allow
                } else {
                    r.policy
                };
                return Decision {
                    policy,
                    reason: if r.note.is_empty() {
                        format!("规则：{}", r.describe())
                    } else {
                        r.note.clone()
                    },
                    rule: Some(r.id.clone()),
                };
            }
        }

        // 3. 工具默认
        if let Some(p) = self.defaults.get(tool) {
            let policy = if self.auto_allow && *p == Policy::Ask { Policy::Allow } else { *p };
            return Decision {
                policy,
                reason: format!("{} 的策略是「{}」", tool, p.label()),
                rule: None,
            };
        }

        // 4. 风险兜底
        let p = risk.fallback();
        let policy = if self.auto_allow && p == Policy::Ask { Policy::Allow } else { p };
        Decision {
            policy,
            reason: match risk {
                Risk::High => "高风险操作，默认每次确认",
                Risk::Medium => "有副作用的操作，默认放行",
                Risk::Low => "只读操作，默认放行",
            }
            .into(),
            rule: None,
        }
    }

    /// 设置某个工具的策略。`Policy::Ask` 等同于"删掉默认值，走风险兜底"。
    pub fn set(&mut self, tool: impl Into<String>, policy: Policy) {
        let tool = tool.into();
        if policy == Policy::Ask {
            self.defaults.remove(&tool);
        } else {
            self.defaults.insert(tool, policy);
        }
    }

    pub fn get(&self, tool: &str) -> Policy {
        self.defaults.get(tool).copied().unwrap_or(Policy::Ask)
    }

    /// 加一条规则。id 为空时自动生成。
    pub fn add_rule(&mut self, mut rule: Rule) -> String {
        if rule.id.is_empty() {
            rule.id = format!("r{}", self.rules.len() + 1);
        }
        let id = rule.id.clone();
        // 同 id 视为更新，避免重复添加把列表撑大
        match self.rules.iter().position(|r| r.id == id) {
            Some(i) => self.rules[i] = rule,
            None => self.rules.push(rule),
        }
        id
    }

    pub fn remove_rule(&mut self, id: &str) -> bool {
        let before = self.rules.len();
        self.rules.retain(|r| r.id != id);
        self.rules.len() != before
    }

    /// 自检：硬红线是否齐全。前端设置页可以提示"安全规则被删了"。
    pub fn hard_rule_count(&self) -> usize {
        self.rules.iter().filter(|r| r.hard).count()
    }

    /// 补齐内置硬红线（只在缺失时加，不覆盖用户自定义规则）。
    pub fn ensure_builtin(&mut self) {
        for r in rule::builtin_hard_rules() {
            if !self.rules.iter().any(|x| x.id == r.id) {
                self.rules.push(r);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deny_beats_user_allow() {
        let mut s = PolicySet::default();
        s.set("shell", Policy::Allow);
        s.ensure_builtin();
        let d = s.decide("shell", r#"{"command":"rm -rf /"}"#, Risk::High);
        assert_eq!(d.policy, Policy::Deny);
    }

    #[test]
    fn high_risk_asks_by_default() {
        let s = PolicySet::default();
        assert_eq!(s.decide("shell", "{}", Risk::High).policy, Policy::Ask);
        assert_eq!(s.decide("read_file", "{}", Risk::Low).policy, Policy::Allow);
    }

    #[test]
    fn auto_allow_never_bypasses_deny() {
        let mut s = PolicySet { auto_allow: true, ..Default::default() };
        s.ensure_builtin();
        assert_eq!(s.decide("shell", "{}", Risk::High).policy, Policy::Allow);
        assert_eq!(s.decide("shell", "dd if=/dev/zero of=/dev/block/sda", Risk::High).policy, Policy::Deny);
    }
}
