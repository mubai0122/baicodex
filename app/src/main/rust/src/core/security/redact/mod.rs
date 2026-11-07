//! 日志与回显脱敏。
//!
//! 两类处理：
//! 1. **密钥**：`sk-`、`ghp_`、PEM、Bearer —— 见 [pattern]。
//! 2. **路径**：把 `/home/alice/...` 换成 `~/...`，避免把用户名带进日志和错误信息。
//!
//! 什么时候调：写日志前、把工具输出回灌给模型前、导出诊断包前、显示错误前。
//! 不要在每个函数里手写 `redact(...)`，容易漏；统一在"出口"处理。

pub mod pattern;

use serde_json::Value;

pub use pattern::{detect, has_secret, redact};

/// 递归脱敏一个 JSON。
///
/// 两条规则一起用：
/// 1. **按键名**：`password` / `token` / `api_key` 这类键，值整段换成 `***`。
///    只靠正则抓 `sk-` 是不够的——用户设的密码、自建网关的 token 长什么样都有。
/// 2. **按内容**：其余字符串过一遍正则，抓掉形如密钥的片段。
pub fn redact_value(v: &Value) -> Value {
    match v {
        Value::String(s) => Value::String(redact(s)),
        Value::Array(a) => Value::Array(a.iter().map(redact_value).collect()),
        Value::Object(o) => {
            let mut m = serde_json::Map::with_capacity(o.len());
            for (k, val) in o {
                let masked = is_secret_key(k) && !is_blank(val);
                m.insert(
                    k.clone(),
                    if masked { Value::String("***".into()) } else { redact_value(val) },
                );
            }
            Value::Object(m)
        }
        other => other.clone(),
    }
}

/// 空值不动：`{"token": null}` 打码成 `***` 只会让人误以为配过。
fn is_blank(v: &Value) -> bool {
    match v {
        Value::Null => true,
        Value::String(s) => s.trim().is_empty(),
        _ => false,
    }
}

/// 把家目录替换成 `~`。
///
/// 只处理"像路径"的片段：整段文本做字符串替换会把正文里的用户名也换掉，
/// 那样读起来很怪。这里按空白切词，只有以 home 开头的词才替换。
pub fn scrub_home(text: &str) -> String {
    let home = crate::core::base::path::home();
    let h = home.to_string_lossy().to_string();
    if h.is_empty() || !text.contains(&h) {
        return text.to_string();
    }
    let mut out = String::with_capacity(text.len());
    for (i, part) in text.split_inclusive(char::is_whitespace).enumerate() {
        let _ = i;
        if part.starts_with(&h) {
            out.push('~');
            out.push_str(&part[h.len()..]);
        } else {
            out.push_str(part);
        }
    }
    out
}

/// 出口统一处理：先脱敏密钥，再收掉家目录。
pub fn clean(text: &str) -> String {
    let s = redact(text);
    scrub_home(&s)
}

/// 工具输出专用。有些工具天生会带出敏感内容（读环境变量、读配置文件），
/// 需要在回灌前额外拦一道。
///
/// `tool` 是工具名，`out` 是原始输出。
pub fn clean_tool_output(tool: &str, out: &str) -> String {
    let s = match tool {
        // env / printenv 的输出整段都是 KEY=VALUE，逐行按密码字段处理
        "shell" | "ssh_exec" if looks_like_env(out) => redact_env_dump(out),
        _ => out.to_string(),
    };
    clean(&s)
}

fn looks_like_env(s: &str) -> bool {
    let mut lines = 0;
    let mut kv = 0;
    for line in s.lines().take(40) {
        if line.trim().is_empty() {
            continue;
        }
        lines += 1;
        if let Some((k, _)) = line.split_once('=') {
            if !k.contains(' ') && k.chars().all(|c| c.is_ascii_uppercase() || c == '_' || c.is_ascii_digit()) {
                kv += 1;
            }
        }
    }
    lines >= 3 && kv * 10 >= lines * 7
}

/// 处理 `env` 输出：键名里带 KEY/TOKEN/SECRET/PASSWORD 的值一律打码。
fn redact_env_dump(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for line in s.lines() {
        match line.split_once('=') {
            Some((k, v)) if is_secret_key(k) && !v.is_empty() => {
                out.push_str(k);
                out.push_str("=***\n");
            }
            _ => {
                out.push_str(line);
                out.push('\n');
            }
        }
    }
    out
}

fn is_secret_key(k: &str) -> bool {
    let u = k.to_ascii_uppercase();
    [
        "KEY",
        "TOKEN",
        "SECRET",
        "PASSWORD",
        "PASSWD",
        "CREDENTIAL",
        "COOKIE",
        "SESSION",
        "AUTHORIZATION",
        "PRIVATE",
    ]
        .iter()
        .any(|x| u.contains(x))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn json_recursive() {
        let v = json!({"a": ["sk-abcdefghijklmnopqrstuvwxyz", "ok"], "b": {"password": "hunter2hunter2"}});
        let r = redact_value(&v);
        let s = r.to_string();
        assert!(!s.contains("abcdefghijklmnop"));
        assert!(!s.contains("hunter2hunter2"));
        assert!(s.contains("ok"));
    }

    #[test]
    fn env_dump_is_masked() {
        let s = "PATH=/usr/bin\nOPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwxyz\nHOME=/root\n";
        let r = clean_tool_output("shell", s);
        assert!(!r.contains("abcdefghijklmnop"));
        assert!(r.contains("PATH=/usr/bin"));
    }

    #[test]
    fn normal_shell_output_untouched() {
        let s = "total 4\ndrwxr-xr-x 2 root root 4096 a\n";
        assert_eq!(clean_tool_output("shell", s), s);
    }

    #[test]
    fn home_scrubbed() {
        let h = crate::core::base::path::home().to_string_lossy().to_string();
        let s = format!("打开 {h}/a.txt 失败");
        assert!(clean(&s).contains("~/a.txt"));
    }
}
