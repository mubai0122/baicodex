//! 策略规则。
//!
//! 一条规则 = 「工具名（可通配）」+「参数正则（可空）」→ 一个策略。
//!
//! 例子：
//!
//! ```text
//! tool = "shell"          arg = `rm\s+-[a-zA-Z]*[rf]`        policy = Ask
//! tool = "fs_*"           arg = ""                            policy = Allow
//! tool = "shell"          arg = `dd\s+.*of=/dev/block`        policy = Deny（硬）
//! ```
//!
//! 参数正则匹配的是模型给的**原始 JSON 串**。这看起来糙，但很实用：不用理解
//! 每种工具的参数结构，一条正则就能同时覆盖 `command`、`cmd`、`script` 几个别名。

use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};

use regex::Regex;
use serde::{Deserialize, Serialize};

use super::Policy;

/// 一条策略规则。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    #[serde(default)]
    pub id: String,
    /// 工具名，支持 `*` 与 `?` 通配。空串表示"所有工具"。
    #[serde(default)]
    pub tool: String,
    /// 参数正则。空串表示"不看参数，只看工具"。
    #[serde(default)]
    pub arg: String,
    pub policy: Policy,
    /// 给用户看的说明。
    #[serde(default)]
    pub note: String,
    /// 硬红线：命中即 Deny，且不能被 `auto_allow` 或用户设置绕过。
    #[serde(default)]
    pub hard: bool,
    /// 临时停用某条规则（硬红线停用后仍然可以通过删除规则来恢复，见 `ensure_builtin`）。
    #[serde(default = "yes")]
    pub enabled: bool,
}

fn yes() -> bool {
    true
}

impl Rule {
    pub fn new(tool: impl Into<String>, arg: impl Into<String>, policy: Policy) -> Self {
        Self {
            id: String::new(),
            tool: tool.into(),
            arg: arg.into(),
            policy,
            note: String::new(),
            hard: false,
            enabled: true,
        }
    }

    pub fn hard(mut self) -> Self {
        self.hard = true;
        self
    }

    pub fn note(mut self, note: impl Into<String>) -> Self {
        self.note = note.into();
        self
    }

    pub fn id(mut self, id: impl Into<String>) -> Self {
        self.id = id.into();
        self
    }

    pub fn matches(&self, tool: &str, args: &str) -> bool {
        if !self.enabled {
            return false;
        }
        if !glob_match(&self.tool, tool) {
            return false;
        }
        if self.arg.trim().is_empty() {
            return true;
        }
        match cached_regex(&self.arg) {
            Some(re) => re.is_match(args),
            // 正则写错时**不匹配**，而不是 panic 或全放行。
            // 硬红线写错会失效，所以 `builtin_hard_rules` 里有单元测试兜着。
            None => false,
        }
    }

    pub fn describe(&self) -> String {
        if self.arg.is_empty() {
            format!("{} → {}", if self.tool.is_empty() { "*" } else { &self.tool }, self.policy.as_str())
        } else {
            format!("{} ~ /{}/ → {}", self.tool, self.arg, self.policy.as_str())
        }
    }
}

/// 把参数串整理成"规则友好的文本"。
///
/// 参数是 JSON：`{"command":"rm -rf /"}`。规则按自然写法写 `rm\s+-rf\s+/$` 时，
/// 原文里 `/` 后面是引号和右括号，永远匹配不上——这是"安全规则看起来写了、
/// 实际没生效"的典型来源。
///
/// 所以额外把 JSON 里所有字符串值摊平拼一份，和原文合在一起交给正则：
/// 既能写 `"command":\s*"rm` 这种针对结构的规则，也能写 `rm -rf /$` 这种针对
/// 内容的规则。规则本身不用知道参数是 JSON。
pub fn probe_text(args: &str) -> String {
    let Ok(v) = serde_json::from_str::<serde_json::Value>(args) else {
        return args.to_string();
    };
    let mut leaves = Vec::new();
    collect_strings(&v, &mut leaves);
    if leaves.is_empty() {
        return args.to_string();
    }
    let mut out = String::with_capacity(args.len() + leaves.iter().map(|s| s.len() + 1).sum::<usize>());
    out.push_str(args);
    out.push('\n');
    out.push_str(&leaves.join(" "));
    out
}

fn collect_strings(v: &serde_json::Value, out: &mut Vec<String>) {
    match v {
        serde_json::Value::String(s) => out.push(s.clone()),
        serde_json::Value::Array(a) => a.iter().for_each(|x| collect_strings(x, out)),
        serde_json::Value::Object(m) => m.values().for_each(|x| collect_strings(x, out)),
        _ => {}
    }
}

/// 正则缓存。规则每轮对话都要匹配，反复编译正则会明显拖慢工具调用前的判定。
fn cached_regex(pattern: &str) -> Option<Regex> {
    static CACHE: OnceLock<Mutex<HashMap<String, Option<Regex>>>> = OnceLock::new();
    let cache = CACHE.get_or_init(|| Mutex::new(HashMap::new()));
    let mut guard = match cache.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    };
    if let Some(v) = guard.get(pattern) {
        return v.clone();
    }
    let compiled = Regex::new(&format!("(?is){pattern}")).ok();
    // 缓存上限：规则数量有限，但用户可能反复编辑出几百条，超过就清空重来。
    if guard.len() > 512 {
        guard.clear();
    }
    guard.insert(pattern.to_string(), compiled.clone());
    compiled
}

/// 通配符匹配：`*` 任意长度，`?` 单个字符。
///
/// 手写而不是引入 globset：只有两个元字符，二十行够了，还省一个依赖。
/// 双指针 + 回溯点，O(n) 均摊。
pub fn glob_match(pattern: &str, text: &str) -> bool {
    if pattern.is_empty() {
        return true; // 空规则 = 匹配所有工具
    }
    if pattern == "*" {
        return true;
    }
    let p: Vec<char> = pattern.chars().collect();
    let t: Vec<char> = text.chars().collect();
    let (mut i, mut j) = (0usize, 0usize);
    let mut star: Option<usize> = None;
    let mut mark = 0usize;
    while j < t.len() {
        if i < p.len() && (p[i] == '?' || p[i] == t[j]) {
            i += 1;
            j += 1;
        } else if i < p.len() && p[i] == '*' {
            star = Some(i);
            mark = j;
            i += 1;
        } else if let Some(s) = star {
            i = s + 1;
            mark += 1;
            j = mark;
        } else {
            return false;
        }
    }
    while i < p.len() && p[i] == '*' {
        i += 1;
    }
    i == p.len()
}

/// 内置硬红线。
///
/// 这些规则**不是**为了让产品显得安全，而是因为误触代价不可逆：
/// 抹掉分区、删根目录、fork 炸弹——一条命令就能让用户的设备变砖。
/// 模型有概率抽风，用户也可能手滑，这条底线不能交给配置。
pub fn builtin_hard_rules() -> Vec<Rule> {
    vec![
        Rule::new("shell", r"rm\s+(-[a-zA-Z]*\s+)*-[a-zA-Z]*[rf][a-zA-Z]*\s+(-[a-zA-Z]+\s+)*/(\s|$|\*)", Policy::Deny)
            .hard()
            .id("hard.rm_root")
            .note("拒绝执行：递归删除根目录"),
        Rule::new("shell", r"rm\s+(-[a-zA-Z]*\s+)*-[a-zA-Z]*[rf][a-zA-Z]*\s+(-[a-zA-Z]+\s+)*(/system|/data|/vendor|/boot|/sdcard)(\s|$|/)", Policy::Deny)
            .hard()
            .id("hard.rm_system")
            .note("拒绝执行：删除系统分区"),
        Rule::new("shell", r"dd\s+.*of\s*=\s*/dev/(block|sd|nvme|mmcblk|disk)", Policy::Deny)
            .hard()
            .id("hard.dd_disk")
            .note("拒绝执行：直接写块设备"),
        Rule::new("shell", r"mkfs(\.\w+)?\s+.*(/dev/|/system|/data)", Policy::Deny)
            .hard()
            .id("hard.mkfs")
            .note("拒绝执行：格式化文件系统"),
        Rule::new("shell", r":\s*\(\s*\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:", Policy::Deny)
            .hard()
            .id("hard.forkbomb")
            .note("拒绝执行：fork 炸弹"),
        Rule::new("shell", r">\s*/dev/(block|sd|nvme|mmcblk)", Policy::Deny)
            .hard()
            .id("hard.overwrite_dev")
            .note("拒绝执行：重定向覆盖块设备"),
        Rule::new("shell", r"chmod\s+(-[a-zA-Z]+\s+)*777\s+(-[a-zA-Z]+\s+)*/(\s|$)", Policy::Deny)
            .hard()
            .id("hard.chmod_root")
            .note("拒绝执行：把根目录改成 777"),
        Rule::new("shell", r"mv\s+.*\s+/dev/null\s*$", Policy::Deny)
            .hard()
            .id("hard.mv_devnull")
            .note("拒绝执行：把文件移进 /dev/null"),
        // 非硬红线：远程脚本直接执行。很多安装脚本就是这么写的，所以不禁止，
        // 但要让用户看一眼。
        Rule::new("shell", r"(curl|wget)\s+[^|;]*\|\s*(sudo\s+)?(sh|bash|zsh)", Policy::Ask)
            .id("warn.pipe_shell")
            .note("这条命令会把网络上的脚本直接执行，确认来源可信"),
        Rule::new("shell", r"\bsu\b|\bsudo\b", Policy::Ask)
            .id("warn.privilege")
            .note("需要提权，确认你知情"),
        Rule::new("ssh_exec", "", Policy::Ask)
            .id("warn.ssh")
            .note("在远程主机上执行命令"),
        Rule::new("send_message", "", Policy::Ask)
            .id("warn.send")
            .note("会以你的身份对外发消息"),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn glob() {
        assert!(glob_match("*", "anything"));
        assert!(glob_match("fs_*", "fs_write"));
        assert!(!glob_match("fs_*", "shell"));
        assert!(glob_match("read_?ile", "read_file"));
        assert!(!glob_match("read_?ile", "read__file"));
    }

    #[test]
    fn hard_rules_catch_the_bad_ones() {
        let rules = builtin_hard_rules();
        let cases = [
            r#"{"command":"rm -rf /"}"#,
            r#"{"command":"rm -rf /*"}"#,
            r#"{"command":"sudo rm -fr /"}"#,
            r#"{"command":"dd if=/dev/zero of=/dev/block/sda"}"#,
            r#"{"command":"mkfs.ext4 /dev/block/by-name/userdata"}"#,
            r#"{"command":":(){ :|:& };:"}"#,
        ];
        for c in cases {
            let probe = probe_text(c);
            let hit = rules.iter().filter(|r| r.hard).any(|r| r.matches("shell", &probe));
            assert!(hit, "没拦住 {c}");
        }
    }

    #[test]
    fn normal_commands_pass() {
        let rules = builtin_hard_rules();
        let cases = [
            r#"{"command":"rm -rf ./build"}"#,
            r#"{"command":"ls -la /sdcard/Download"}"#,
            r#"{"command":"dd if=a.img of=b.img"}"#,
            r#"{"command":"cat /proc/meminfo"}"#,
        ];
        for c in cases {
            let probe = probe_text(c);
            let hit = rules.iter().filter(|r| r.hard).any(|r| r.matches("shell", &probe));
            assert!(!hit, "误伤 {c}");
        }
    }

    #[test]
    fn bad_regex_does_not_match() {
        let r = Rule::new("shell", "([", Policy::Deny);
        assert!(!r.matches("shell", "anything"));
    }
}
