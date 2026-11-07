//! 敏感信息识别规则。
//!
//! 什么时候会用到：日志、工具输出回显、错误信息、导出的诊断包。这些东西可能被
//! 贴进 issue、发给模型厂商、存进聊天记录。密钥一旦进去就收不回来了。
//!
//! 规则设计原则：
//! - **宁可多脱敏**。把一个普通词打成 `***` 只是难看，漏掉一个 key 是事故。
//! - **认得常见格式**。`sk-`、`ghp_`、`AKIA`、`xoxb-`、PEM 私钥、`Bearer`。
//! - **不追求完备**。真正防止密钥外泄的是"不把密钥放进这些通道"，脱敏是兜底。
//!
//! 正则只在首次使用时编译，之后走缓存。

use std::sync::OnceLock;

use regex::Regex;

/// 一条规则。
#[derive(Debug)]
pub struct Pattern {
    /// 展示用的类型名。
    pub kind: &'static str,
    /// 正则。
    pub re: &'static str,
    /// 替换文本。
    pub replace: &'static str,
}

/// 全部规则。顺序重要：先匹配"整块私钥"，再匹配里面的片段。
pub const PATTERNS: &[Pattern] = &[
    Pattern {
        kind: "私钥",
        re: r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
        replace: "[私钥已脱敏]",
    },
    Pattern {
        kind: "OpenAI/Anthropic key",
        re: r"\bsk-[A-Za-z0-9_\-]{16,}\b",
        replace: "sk-***",
    },
    Pattern {
        kind: "GitHub token",
        re: r"\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}\b",
        replace: "${1}_***",
    },
    Pattern {
        kind: "AWS key",
        re: r"\bAKIA[0-9A-Z]{16}\b",
        replace: "AKIA***",
    },
    Pattern {
        kind: "Slack token",
        re: r"\bxox[baprs]-[A-Za-z0-9\-]{10,}\b",
        replace: "xox*-***",
    },
    Pattern {
        kind: "Google key",
        re: r"\bAIza[0-9A-Za-z_\-]{35}\b",
        replace: "AIza***",
    },
    Pattern {
        kind: "Bearer token",
        re: r"(?i)\bBearer\s+[A-Za-z0-9\-\._~\+/]{12,}=*",
        replace: "Bearer ***",
    },
    Pattern {
        kind: "密码字段",
        re: r#"(?i)("?(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key)"?\s*[:=]\s*")([^"]{4,})(")"#,
        replace: "${1}***${3}",
    },
    Pattern {
        kind: "密码字段",
        re: r"(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key)\s*=\s*([^\s&;]{4,})",
        replace: "${1}=***",
    },
    Pattern {
        kind: "URL 里的凭据",
        re: r"(?i)(https?://[^/\s:@]+):([^/\s@]+)@",
        replace: "${1}:***@",
    },
    Pattern {
        kind: "手机号",
        re: r"\b1[3-9]\d{9}\b",
        replace: "1**********",
    },
    Pattern {
        kind: "身份证",
        re: r"\b\d{17}[\dXx]\b",
        replace: "[身份证已脱敏]",
    },
];

/// 编译缓存。
fn compiled() -> &'static Vec<(&'static str, Regex, &'static str)> {
    static CACHE: OnceLock<Vec<(&'static str, Regex, &'static str)>> = OnceLock::new();
    CACHE.get_or_init(|| {
        PATTERNS
            .iter()
            .filter_map(|p| Regex::new(p.re).ok().map(|re| (p.kind, re, p.replace)))
            .collect()
    })
}

/// 检测文本里有没有敏感信息，返回命中的类型（第一个）。
///
/// 用于"这段内容要不要弹窗提醒用户"。
pub fn detect(text: &str) -> Option<&'static str> {
    for (kind, re, _) in compiled() {
        if re.is_match(text) {
            return Some(kind);
        }
    }
    None
}

/// 全量脱敏。
pub fn redact(text: &str) -> String {
    let mut out = text.to_string();
    for (_, re, replace) in compiled() {
        if re.is_match(&out) {
            out = re.replace_all(&out, *replace).to_string();
        }
    }
    out
}

/// 只要"有没有"且不想分配新字符串时用它。比 `detect` 便宜（不构造结果）。
pub fn has_secret(text: &str) -> bool {
    compiled().iter().any(|(_, re, _)| re.is_match(text))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn openai_key() {
        let s = "key=sk-abcdefghijklmnopqrstuvwxyz123456";
        assert!(has_secret(s));
        assert!(!redact(s).contains("abcdefghijklmnop"));
    }

    #[test]
    fn pem_block() {
        let s = "-----BEGIN RSA PRIVATE KEY-----\nMIIEow...\n-----END RSA PRIVATE KEY-----";
        assert_eq!(redact(s), "[私钥已脱敏]");
    }

    #[test]
    fn bearer() {
        let s = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig";
        assert!(!redact(s).contains("eyJhbGci"));
    }

    #[test]
    fn json_password() {
        let s = r#"{"password":"hunter2hunter2","user":"a"}"#;
        let r = redact(s);
        assert!(!r.contains("hunter2hunter2"));
        assert!(r.contains("\"user\":\"a\""));
    }

    #[test]
    fn url_credentials() {
        let s = "https://user:supersecret@example.com/x";
        assert!(!redact(s).contains("supersecret"));
    }

    #[test]
    fn normal_text_untouched() {
        let s = "今天天气不错，去 /tmp 看下文件";
        assert_eq!(redact(s), s);
    }
}
