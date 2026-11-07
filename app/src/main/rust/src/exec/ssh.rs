//! 远端主机：一整组专用工具（连接 / 列目录 / 读 / 写 / 提权 / 执行 / 下载 / 上传）。
//!
//! ## 三条安全约束
//!
//! 1. **远端路径一律由这一层转义**，模型不用自己拼引号，也不会因为文件名里有 `;`
//!    把后半句话变成第二条命令；
//! 2. **密码只走环境变量**（`sshpass -e`），不出现在命令行里——否则同机器上
//!    `ps aux` 就能看到；
//! 3. **连接信息落盘、密码进凭据库**，两者分开：丢了 json 也不会泄密码，
//!    密码换了也不用改 json。
//!
//! ## 为什么需要 sshpass
//!
//! 纯 `ssh` 在非交互环境里没法喂密码（没有 tty）。三种选择：key 认证、sshpass、
//! 自建 SSH 客户端。第三种要引一堆依赖；第二种最省事，缺了就明确报"装 sshpass"。

use std::path::Path;
use std::time::Duration;

use serde::{Deserialize, Serialize};

use super::shell::{self, ShellResult};
use crate::core::base::{dirs, fsx};
use crate::core::security::vault::{Vault, BUCKET_SSH};

/// 连接档案文件名。
const FILE: &str = "ssh.json";
/// 凭据库里密码的键名。
const PASS_KEY: &str = "ssh_pass";

/// 保存的连接信息（不含密码）。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Profile {
    pub host: String,
    pub port: u16,
    pub user: String,
}

impl Profile {
    fn target(&self) -> String {
        format!("{}@{}", self.user, self.host)
    }

    fn port(&self) -> u16 {
        if self.port == 0 {
            22
        } else {
            self.port
        }
    }
}

fn path() -> std::path::PathBuf {
    dirs::file(FILE)
}

/// 读档案。
pub fn load() -> Option<Profile> {
    let t = std::fs::read_to_string(path()).ok()?;
    let p: Profile = serde_json::from_str(&t).ok()?;
    if p.host.trim().is_empty() || p.user.trim().is_empty() {
        return None;
    }
    Some(p)
}

/// 存档案。
pub fn save(p: &Profile) -> bool {
    let Ok(t) = serde_json::to_string_pretty(p) else { return false };
    fsx::ensure_dir(dirs::base()).is_ok() && fsx::write_text(path(), &t).is_ok()
}

/// 清掉档案。
pub fn forget(vault: &Vault) -> bool {
    let _ = vault.remove(BUCKET_SSH, PASS_KEY);
    let _ = vault.flush(BUCKET_SSH);
    std::fs::remove_file(path()).is_ok()
}

/// 有没有配置过。
pub fn configured() -> bool {
    load().is_some()
}

fn pass(vault: &Vault) -> String {
    vault.get(BUCKET_SSH, PASS_KEY)
}

/// 拼一条远端命令。
///
/// 密码走 `SSHPASS=` 环境变量 + `sshpass -e`；`-o` 那几项是为了在无人值守时
/// 不去问 host key（第一次连接也没有 tty 可以回答 yes）。
fn remote_cmd(vault: &Vault, p: &Profile, remote: &str) -> String {
    let pw = pass(vault);
    if pw.is_empty() {
        // 没密码就走 key 认证，别给 sshpass 传空串（会直接认证失败）。
        format!(
            "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
             -o ConnectTimeout=15 -o BatchMode=yes -p {port} {target} {cmd}",
            port = p.port(),
            target = p.target(),
            cmd = shell::quote(remote)
        )
    } else {
        format!(
            "SSHPASS={pw} sshpass -e ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
             -o ConnectTimeout=15 -p {port} {target} {cmd}",
            pw = shell::quote(&pw),
            port = p.port(),
            target = p.target(),
            cmd = shell::quote(remote)
        )
    }
}

/// 拼一条 scp 命令。
fn scp_cmd(vault: &Vault, p: &Profile, from: &str, to: &str) -> String {
    let pw = pass(vault);
    let base = format!(
        "scp -O -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -P {port} {from} {to}",
        port = p.port(),
        from = from,
        to = to
    );
    if pw.is_empty() {
        base
    } else {
        format!("SSHPASS={} sshpass -e {base}", shell::quote(&pw))
    }
}

/// 保存连接信息并验证。
pub async fn connect(vault: &Vault, host: &str, port: u16, user: &str, password: &str) -> ShellResult {
    let h = host.trim();
    let u = user.trim();
    if h.is_empty() || u.is_empty() {
        return ShellResult::fail("host / user 不能为空");
    }
    // 主机名只允许常规字符：防止 `-oProxyCommand=...` 这种参数注入。
    if !h.chars().all(|c| c.is_ascii_alphanumeric() || "-._:[]".contains(c)) {
        return ShellResult::fail(format!("主机名里有不合法字符：{h}"));
    }
    if !u.chars().all(|c| c.is_ascii_alphanumeric() || "-._@".contains(c)) {
        return ShellResult::fail(format!("用户名里有不合法字符：{u}"));
    }

    let p = Profile { host: h.to_string(), port: p2(port), user: u.to_string() };
    if !save(&p) {
        return ShellResult::fail("保存连接信息失败（数据根不可写）");
    }
    if !password.is_empty() {
        if let Err(e) = vault.put(BUCKET_SSH, PASS_KEY, password) {
            return ShellResult::fail(format!("保存密码失败：{e}"));
        }
        let _ = vault.flush(BUCKET_SSH);
    }

    // 真连一次：光存不算，密码错了要当场知道。
    let r = shell::run(&remote_cmd(vault, &p, "echo ok"), None, Duration::from_secs(30), None).await;
    if r.ok() && r.out.contains("ok") {
        ShellResult { code: 0, out: format!("连接已保存并验证通过：{}@{}:{}", p.user, p.host, p.port()), ..Default::default() }
    } else {
        ShellResult::fail(format!("连接失败：{}", if r.out.trim().is_empty() { "认证没通过" } else { r.out.trim() }))
    }
}

fn p2(port: u16) -> u16 {
    if port == 0 {
        22
    } else {
        port
    }
}

/// 列目录。
pub async fn ls(vault: &Vault, path: &str, depth: u16) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    let d = depth.clamp(1, 4);
    // `find -printf` 在 busybox 上没有，退到 ls -l 也能看。
    let remote = format!(
        "if find --version >/dev/null 2>&1; then find {q} -maxdepth {d} -printf '%y %10s %TY-%Tm-%Td %TH:%TM %p\\n' | sort -k5; else ls -laR {q}; fi",
        q = shell::quote(path)
    );
    shell::run(&remote_cmd(vault, &p, &remote), None, Duration::from_secs(90), None).await
}

/// 读文件。
pub async fn read(vault: &Vault, path: &str, max_lines: u32) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    let remote = if max_lines > 0 {
        format!("head -n {max_lines} {}", shell::quote(path))
    } else {
        format!("cat {}", shell::quote(path))
    };
    shell::run(&remote_cmd(vault, &p, &remote), None, Duration::from_secs(90), None).await
}

/// 写文件。
pub async fn write(vault: &Vault, path: &str, content: &str) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    // base64 走管道：内容里有什么引号、换行、控制字符都不会破坏命令。
    let b64 = crate::core::base::bytes::b64_encode(content.as_bytes());
    let remote = format!(
        "mkdir -p $(dirname {q}) && printf %s {b} | base64 -d > {q}",
        q = shell::quote(path),
        b = shell::quote(&b64)
    );
    shell::run(&remote_cmd(vault, &p, &remote), None, Duration::from_secs(120), None).await
}

/// 以 sudo 执行。
pub async fn sudo(vault: &Vault, command: &str, timeout: Duration) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    let pw = pass(vault);
    let remote = if pw.is_empty() {
        format!("sudo -n sh -c {}", shell::quote(command))
    } else {
        // 密码喂给 sudo -S，不写进命令里（远端 ps 看不到）。
        format!("echo {} | sudo -S -p '' sh -c {}", shell::quote(&pw), shell::quote(command))
    };
    shell::run(&remote_cmd(vault, &p, &remote), None, timeout, None).await
}

/// 执行命令。
pub async fn exec(vault: &Vault, command: &str, timeout: Duration) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    shell::run(&remote_cmd(vault, &p, command), None, timeout, None).await
}

/// 下载：远端 → 本地。
pub async fn pull(vault: &Vault, remote: &str, local: &str) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    if let Some(d) = Path::new(local).parent() {
        let _ = fsx::ensure_dir(d);
    }
    let spec = format!("{}:{}", p.target(), remote);
    let cmd = scp_cmd(vault, &p, &shell::quote(&spec), &shell::quote(local));
    shell::run(&cmd, None, Duration::from_secs(900), None).await
}

/// 上传：本地 → 远端。
pub async fn push(vault: &Vault, local: &str, remote: &str) -> ShellResult {
    let Some(p) = load() else { return ShellResult::fail("还没配置服务器：先调 ssh_connect") };
    if !Path::new(local).exists() {
        return ShellResult::fail(format!("本地文件不存在：{local}"));
    }
    // 先确保远端目录在，否则 scp 会失败且报错含糊。
    let mk = format!("mkdir -p $(dirname {})", shell::quote(remote));
    let _ = shell::run(&remote_cmd(vault, &p, &mk), None, Duration::from_secs(30), None).await;
    let spec = format!("{}:{}", p.target(), remote);
    let cmd = scp_cmd(vault, &p, &shell::quote(local), &shell::quote(&spec));
    shell::run(&cmd, None, Duration::from_secs(900), None).await
}

/// 环境是否齐全（ssh + sshpass/scp）。
pub fn has_tools() -> (bool, bool) {
    (shell::has("ssh"), shell::has("sshpass"))
}

/// 一段可读的状态说明，工具执行失败时附在末尾，省得模型瞎猜。
///
/// `has_password`：这次是不是要用密码登录。空密码走密钥认证，不需要 sshpass。
pub fn hint(has_password: bool) -> String {
    let (ssh, sshpass) = has_tools();
    if !ssh {
        return "本机没有 ssh 客户端".into();
    }
    if has_password && !sshpass {
        return "没装 sshpass，密码登录用不了；可以改用密钥认证，或装 sshpass".into();
    }
    String::new()
}
