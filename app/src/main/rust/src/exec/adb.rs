//! adb 互联：探测 / 自动安装 platform-tools、设备枚举、无线调试、localhost 转发。
//!
//! ## 为什么不引 adb crate
//!
//! 那类 crate 要么自己拉一份二进制、要么只包一层 shell。Google 的
//! platform-tools 已经覆盖三平台，够用；我们只负责**把它放进数据目录、记住路径**，
//! 之后所有操作都起一次 `adb` 子进程。省掉依赖，也省掉版本漂移。
//!
//! ## 两种通道
//!
//! ```text
//! 数据线   USB 上的 adb，稳定、免配对，带宽 30~40MB/s（USB2）/ 200MB+（USB3）
//! 内网     手机端 HTTP 服务，PC 走局域网直连，20~70MB/s，但要同一网段
//! ```
//!
//! 两者都能跑时上层会分块并行（见 `net::relay`），不是二选一。
//!
//! ## 无线调试的现实
//!
//! Android 11+ 的「无线调试」要先用 `adb pair HOST:PORT CODE` 配对，端口随机；
//! Android 14 起睡眠/重启后端口还会变。所以配对信息必须记下来，连接时按
//! 「上次的端口 → mDNS → 常见端口」顺序试，不能写死 5555。
use std::path::PathBuf;
use std::time::Duration;

use serde_json::{json, Value};

use crate::core::base::dirs;
use crate::exec::shell;

/// 普通 adb 子命令的超时。
const T: Duration = Duration::from_secs(20);
/// 下载 platform-tools 的超时。
const T_DL: Duration = Duration::from_secs(240);

/// 一个可用的 adb。
#[derive(Debug, Clone)]
pub struct Adb {
    exe: PathBuf,
}

/// 当前平台名，用来拼官方下载地址。
pub fn platform() -> &'static str {
    if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "darwin"
    } else {
        "linux"
    }
}

/// adb 可执行文件名。
pub fn exe_name() -> &'static str {
    if cfg!(windows) {
        "adb.exe"
    } else {
        "adb"
    }
}

/// 找 adb：数据目录 → PATH → 各家 Android SDK 的常见位置。
pub fn locate() -> Option<PathBuf> {
    let name = exe_name();
    let own = dirs::dir("tools").join("platform-tools").join(name);
    if own.is_file() {
        return Some(own);
    }
    if let Some(p) = shell::locate(name) {
        return Some(p);
    }
    let mut cands: Vec<PathBuf> = Vec::new();
    for key in ["ANDROID_HOME", "ANDROID_SDK_ROOT"] {
        if let Ok(v) = std::env::var(key) {
            cands.push(PathBuf::from(v).join("platform-tools").join(name));
        }
    }
    if let Ok(v) = std::env::var("LOCALAPPDATA") {
        cands.push(PathBuf::from(v).join("Android/Sdk/platform-tools").join(name));
    }
    for key in ["HOME", "USERPROFILE"] {
        if let Ok(v) = std::env::var(key) {
            let home = PathBuf::from(v);
            cands.push(home.join("Android/Sdk/platform-tools").join(name));
            cands.push(home.join("Library/Android/sdk/platform-tools").join(name));
        }
    }
    cands.into_iter().find(|p| p.is_file())
}

/// 拿一个可用的 adb：没有就现装。
pub async fn ensure() -> Result<Adb, String> {
    if let Some(p) = locate() {
        return Ok(Adb { exe: p });
    }
    let exe = install().await?;
    Ok(Adb { exe })
}

/// 下载并解压官方 platform-tools，返回 adb 路径。
///
/// 解压走系统自带工具：Windows 用 PowerShell 的 Expand-Archive，其它平台用 unzip。
/// 不引 zip crate 的原因同上——为一次安装引一个解压库不值当。
pub async fn install() -> Result<PathBuf, String> {
    let url = format!(
        "https://dl.google.com/android/repository/platform-tools-latest-{}.zip",
        platform()
    );
    let dir = dirs::dir("tools");
    std::fs::create_dir_all(&dir).map_err(|e| format!("建 tools 目录失败：{e}"))?;
    let zip = dir.join("platform-tools.zip");
    let zip_s = zip.to_string_lossy().to_string();

    let dl = shell::run_argv(
        &["curl", "-L", "--fail", "--connect-timeout", "20", "-o", &zip_s, &url],
        None,
        T_DL,
        None,
    )
    .await;
    if !dl.ok() {
        return Err(format!("下载 platform-tools 失败：{}", one_line(&dl)));
    }

    let dir_s = dir.to_string_lossy().to_string();
    let un = if cfg!(windows) {
        let ps = format!("Expand-Archive -LiteralPath '{zip_s}' -DestinationPath '{dir_s}' -Force");
        shell::run_argv(&["powershell", "-NoProfile", "-NonInteractive", "-Command", &ps], None, T_DL, None).await
    } else {
        shell::run_argv(&["unzip", "-oq", &zip_s, "-d", &dir_s], None, T_DL, None).await
    };
    if !un.ok() {
        return Err(format!("解压 platform-tools 失败：{}", one_line(&un)));
    }

    let exe = dir.join("platform-tools").join(exe_name());
    if !exe.is_file() {
        return Err(format!("解压完没找到 {}", exe.display()));
    }
    Ok(exe)
}

/// 一行化失败原因：adb 有些报错压成一行才看得懂。
fn one_line(r: &shell::ShellResult) -> String {
    let s = r.out.trim();
    if s.is_empty() {
        r.reason()
    } else {
        s.lines().next().unwrap_or("").trim().to_string()
    }
}

impl Adb {
    /// 指定路径的 adb（已知位置时跳过探测）。
    pub fn at(exe: PathBuf) -> Self {
        Self { exe }
    }

    /// adb 可执行文件路径。
    pub fn exe(&self) -> &PathBuf {
        &self.exe
    }

    /// 底层：按 argv 跑一次 adb，把 stdout 当字符串返回。
    async fn run(&self, args: &[&str]) -> Result<String, String> {
        let exe = self.exe.to_string_lossy().to_string();
        let mut argv: Vec<&str> = Vec::with_capacity(args.len() + 1);
        argv.push(&exe);
        argv.extend_from_slice(args);
        let r = shell::run_argv(&argv, None, T, None).await;
        if r.ok() {
            Ok(r.out)
        } else {
            Err(one_line(&r))
        }
    }

    /// 版本行，用来证明它真的能跑。
    pub async fn version(&self) -> Result<String, String> {
        let out = self.run(&["version"]).await?;
        Ok(out.lines().next().unwrap_or("").trim().to_string())
    }

    /// 已连接设备。解析 `adb devices -l` 的固定列格式。
    pub async fn devices(&self) -> Result<Vec<Value>, String> {
        let out = self.run(&["devices", "-l"]).await?;
        let mut list: Vec<Value> = Vec::new();
        for line in out.lines().skip(1) {
            let line = line.trim();
            if line.is_empty() || line.starts_with('*') {
                continue;
            }
            let mut it = line.split_whitespace();
            let Some(serial) = it.next() else { continue };
            let Some(state) = it.next() else { continue };
            let mut model = String::new();
            for kv in it {
                if let Some(v) = kv.strip_prefix("model:") {
                    model = v.to_string();
                }
            }
            let usb = !serial.contains(':');
            list.push(json!({
                "serial": serial,
                "state": state,
                "model": model.replace('_', " "),
                "transport": if usb { "usb" } else { "tcp" },
            }));
        }
        Ok(list)
    }

    /// 走 USB 时把 adbd 切成 TCP 模式（之后可以不插线）。端口默认 5555。
    pub async fn tcpip(&self, port: u16) -> Result<String, String> {
        self.run(&["tcpip", &port.to_string()]).await
    }

    /// 连接一个已经开了 TCP 模式的设备。
    pub async fn connect(&self, addr: &str) -> Result<String, String> {
        self.run(&["connect", addr]).await
    }

    /// 断开。
    pub async fn disconnect(&self, addr: &str) -> Result<String, String> {
        self.run(&["disconnect", addr]).await
    }

    /// 无线调试配对（Android 11+）。配对码由用户从「无线调试」界面抄过来。
    pub async fn pair(&self, addr: &str, code: &str) -> Result<String, String> {
        self.run(&["pair", addr, code]).await
    }

    /// 把手机的某个端口映射到本机 localhost（PC 访问 127.0.0.1:local 等于访问手机的 remote）。
    pub async fn forward(&self, local: u16, remote: u16) -> Result<String, String> {
        self.run(&["forward", &format!("tcp:{local}"), &format!("tcp:{remote}")]).await
    }

    /// 反过来：手机访问自己的 localhost:remote 走 PC 的 local。
    pub async fn reverse(&self, remote: u16, local: u16) -> Result<String, String> {
        self.run(&["reverse", &format!("tcp:{remote}"), &format!("tcp:{local}")]).await
    }

    /// 清掉所有转发。
    pub async fn forward_clear(&self) -> Result<String, String> {
        self.run(&["forward", "--remove-all"]).await
    }

    /// 在手机上跑一条 shell 命令（免安装、免传输的「直接操作」入口）。
    pub async fn shell(&self, cmd: &str) -> Result<String, String> {
        self.run(&["shell", cmd]).await
    }

    /// 拉一个文件到本机。
    pub async fn pull(&self, remote: &str, local: &str) -> Result<String, String> {
        self.run(&["pull", remote, local]).await
    }

    /// 推一个文件到手机。
    pub async fn push(&self, local: &str, remote: &str) -> Result<String, String> {
        self.run(&["push", local, remote]).await
    }

    /// 给界面用的一份自报家底。
    ///
    /// `available` 单独给：文件在但跑不起来（架构不符 / 缺动态库 / 没有执行权限）
    /// 是最常见的坑，界面必须能区分「没装」和「装了用不了」。
    pub async fn status(&self) -> Value {
        let ver = self.version().await;
        let devs = match ver {
            Ok(_) => self.devices().await.unwrap_or_default(),
            Err(_) => Vec::new(),
        };
        json!({
            "exe": self.exe.to_string_lossy(),
            "available": ver.is_ok(),
            "version": ver.clone().unwrap_or_default(),
            "error": ver.err().unwrap_or_default(),
            "devices": devs,
            "platform": platform(),
        })
    }
}