//! MCP：把外部能力接成工具，工具名统一 `mcp_服务_能力`。
//!
//! 这一层管**持久化与开关**（哪些服务、开没开、能力表缓存），协议在
//! [crate::net::mcp]。分开的理由：协议要能被单测覆盖（`net` 里没有文件系统），
//! 持久化要能被 UI 直接改（`feature` 里没有 HTTP）。
//!
//! ## 能力表为什么要缓存
//!
//! 每次启动都去握手拉一遍，五个服务就是五轮往返，冷启动会明显变慢。缓存写在
//! `<数据根>/mcp_tools.json`，只有用户点"刷新"或服务开关变化时才重拉。
//!
//! ## 关于 `last_error`
//!
//! 调用失败时把原因留在这里而不是返回 `Err`：工具层的 `ToolOutcome` 需要一段
//! 文本，而 `Mcp` 是被 `RwLock` 包着的，不能跨 await 持有写锁。所以失败信息
//! 由调用方在 await 之后回写。

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::core::base::error::{BError, BResult};
use crate::core::base::fsx;
use crate::net::mcp::{self as proto, Kind, Server, Tool};

const SERVERS_FILE: &str = "mcp.json";
const TOOLS_FILE: &str = "mcp_tools.json";

/// 持久化的服务条目。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerCfg {
    pub id: String,
    pub name: String,
    pub url: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub token: String,
    /// `streamable` / `rest`。
    #[serde(default = "default_kind")]
    pub kind: String,
    #[serde(default = "default_on")]
    pub on: bool,
}

fn default_kind() -> String {
    "streamable".into()
}

fn default_on() -> bool {
    true
}

impl ServerCfg {
    /// 转成协议层结构。
    pub fn to_server(&self) -> Server {
        Server {
            id: self.id.clone(),
            name: self.name.clone(),
            url: self.url.clone(),
            token: self.token.clone(),
            kind: Kind::parse(&self.kind),
            on: self.on,
        }
    }

    /// 设置页一行。
    pub fn line(&self) -> String {
        format!(
            "{}{}  {}（{}）",
            self.name,
            if self.on { "" } else { "（已停用）" },
            self.url,
            self.kind
        )
    }
}

/// MCP 集合。
#[derive(Debug, Default)]
pub struct Mcp {
    pub servers: Vec<ServerCfg>,
    pub tools: Vec<Tool>,
    /// 最近一次调用的错误（读一次不清，由下一次成功调用覆盖）。
    pub last_error: String,
    base: PathBuf,
}

impl Mcp {
    /// 加载服务与能力缓存。缓存损坏就当作空表，不阻塞启动。
    pub fn load(base: &Path) -> Self {
        let servers: Vec<ServerCfg> = read_json(&base.join(SERVERS_FILE)).unwrap_or_default();
        let all_tools: Vec<Tool> = read_json(&base.join(TOOLS_FILE)).unwrap_or_default();
        // 只保留当前还存在的服务的能力，避免删了服务工具还挂在那。
        let tools = all_tools
            .into_iter()
            .filter(|t| servers.iter().any(|s| s.id == t.server_id && s.on))
            .collect();
        Self { servers, tools, last_error: String::new(), base: base.to_path_buf() }
    }

    /// 落盘（服务表 + 能力表）。
    pub fn save(&self) {
        // 数据根没配好就别写：空路径会落到进程当前目录（见 fsx::writable_dir）。
        if !crate::core::base::fsx::writable_dir(&self.base) {
            return;
        }
        let _ = write_json(&self.base.join(SERVERS_FILE), &self.servers);
        let _ = write_json(&self.base.join(TOOLS_FILE), &self.tools);
    }

    pub fn add(&mut self, name: &str, url: &str, token: &str, kind: &str) -> ServerCfg {
        let u = url.trim().trim_end_matches('/').to_string();
        let n = {
            let x = name.trim();
            if x.is_empty() {
                u.rsplit('/').next().unwrap_or("mcp").to_string()
            } else {
                x.to_string()
            }
        };
        // 同名覆盖：用户改地址时不该留下两条一样的。
        if let Some(s) = self.servers.iter_mut().find(|s| s.name == n) {
            s.url = u;
            if !token.is_empty() {
                s.token = token.to_string();
            }
            s.kind = if kind.trim().is_empty() { "streamable".into() } else { kind.trim().to_string() };
            let out = s.clone();
            self.save();
            return out;
        }
        let s = ServerCfg {
            id: uuid::Uuid::new_v4().to_string(),
            name: n,
            url: u,
            token: token.to_string(),
            kind: if kind.trim().is_empty() { "streamable".into() } else { kind.trim().to_string() },
            on: true,
        };
        self.servers.push(s.clone());
        self.save();
        s
    }

    pub fn remove(&mut self, id: &str) -> bool {
        let before = self.servers.len();
        self.servers.retain(|s| s.id != id && s.name != id);
        if self.servers.len() == before {
            return false;
        }
        let alive: Vec<String> = self.servers.iter().map(|s| s.id.clone()).collect();
        self.tools.retain(|t| alive.contains(&t.server_id));
        self.save();
        true
    }

    pub fn toggle(&mut self, id: &str, on: bool) -> bool {
        match self.servers.iter_mut().find(|s| s.id == id || s.name == id) {
            Some(s) => {
                s.on = on;
                if !on {
                    let sid = s.id.clone();
                    self.tools.retain(|t| t.server_id != sid);
                }
                self.save();
                true
            }
            None => false,
        }
    }

    pub fn find(&self, full: &str) -> Option<&Tool> {
        self.tools.iter().find(|t| t.full == full)
    }

    /// 解析一个工具名 → (服务, 服务端工具名)。**不持锁、不 await**，供工具层先取快照。
    pub fn resolve(&self, full: &str) -> BResult<(Server, String)> {
        let Some(t) = self.find(full) else {
            return Err(BError::not_found(format!("能力 {full} 不在本地缓存里，先刷新一次能力表")));
        };
        let Some(s) = self.servers.iter().find(|s| s.id == t.server_id) else {
            return Err(BError::not_found(format!("MCP 服务已被删除：{full}")));
        };
        if !s.on {
            return Err(BError::denied(format!("MCP 服务「{}」已停用", s.name)));
        }
        Ok((s.to_server(), t.name.clone()))
    }

    pub fn set_error(&mut self, msg: impl Into<String>) {
        self.last_error = msg.into();
    }

    pub fn clear_error(&mut self) {
        self.last_error.clear();
    }

    /// 工具定义（喂给模型）。
    pub fn definitions(&self) -> Vec<Value> {
        self.tools
            .iter()
            .filter(|t| self.servers.iter().any(|s| s.id == t.server_id && s.on))
            .map(|t| t.definition())
            .collect()
    }

    /// 拉一个服务的能力表，返回条数。
    pub async fn refresh(&mut self, id: &str) -> BResult<usize> {
        let Some(cfg) = self.servers.iter().find(|s| s.id == id || s.name == id).cloned() else {
            return Err(BError::not_found("服务不存在"));
        };
        let list = proto::list_tools(&cfg.to_server()).await?;
        let n = list.len();
        self.tools.retain(|t| t.server_id != cfg.id);
        self.tools.extend(list);
        self.tools.sort_by(|a, b| a.full.cmp(&b.full));
        self.save();
        Ok(n)
    }

    /// 拉全部启用服务的能力表。单个失败不影响其他。
    pub async fn refresh_all(&mut self) -> usize {
        let ids: Vec<String> = self.servers.iter().filter(|s| s.on).map(|s| s.id.clone()).collect();
        let mut n = 0;
        let mut errs = Vec::new();
        for id in ids {
            match self.refresh(&id).await {
                Ok(k) => n += k,
                Err(e) => errs.push(e.user_text()),
            }
        }
        self.last_error = errs.join("；");
        n
    }

    /// 设置页展示：服务 → 能力。
    pub fn grouped(&self) -> Vec<(String, Vec<String>)> {
        let mut out = Vec::new();
        for s in &self.servers {
            let names: Vec<String> = self
                .tools
                .iter()
                .filter(|t| t.server_id == s.id)
                .map(|t| t.name.clone())
                .collect();
            out.push((s.line(), names));
        }
        out
    }

    /// 提示词片段：只列服务与能力名，说明靠工具定义。
    pub fn prompt(&self) -> String {
        if self.servers.is_empty() {
            return String::new();
        }
        let on: Vec<&ServerCfg> = self.servers.iter().filter(|s| s.on).collect();
        if on.is_empty() {
            return "MCP：全部服务已停用。\n".into();
        }
        let mut s = String::from("MCP 服务：\n");
        for x in on {
            let n = self.tools.iter().filter(|t| t.server_id == x.id).count();
            s.push_str(&format!("- {}（{} 个能力）\n", x.name, n));
        }
        s
    }
}

fn read_json<T: for<'de> Deserialize<'de>>(p: &Path) -> Option<T> {
    let t = std::fs::read_to_string(p).ok()?;
    serde_json::from_str(&t).ok()
}

fn write_json<T: Serialize>(p: &Path, v: &T) -> bool {
    let Ok(t) = serde_json::to_string_pretty(v) else { return false };
    if let Some(d) = p.parent() {
        let _ = fsx::ensure_dir(d);
    }
    fsx::write_text(p, &t).is_ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolve_reports_missing() {
        let m = Mcp::default();
        assert!(m.resolve("mcp_x_y").is_err());
    }

    #[test]
    fn disabled_server_blocks_tools() {
        let mut m = Mcp::default();
        m.servers.push(ServerCfg {
            id: "1".into(),
            name: "s".into(),
            url: "http://x".into(),
            token: String::new(),
            kind: default_kind(),
            on: false,
        });
        m.tools.push(Tool { server_id: "1".into(), full: "mcp_s_a".into(), name: "a".into(), ..Default::default() });
        assert!(m.resolve("mcp_s_a").is_err());
        assert!(m.definitions().is_empty());
    }
}
