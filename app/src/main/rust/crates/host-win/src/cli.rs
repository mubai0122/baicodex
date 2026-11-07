//! 启动参数。
//!
//! 桌面端只有两种跑法，对应两套资源来源：
//!
//! - **生产**：前端已经构建进 `web/`，用 `file://` 加载。窗口一起就能用。
//! - **开发**：前端跑在 vite dev server 上，用 `http://` 加载，改一行刷一下。
//!
//! 判断依据是 `--dev` 有没有给。环境变量也认，方便在 IDE 里配一次就不用每次敲。

use std::path::PathBuf;

use crate::error::{HostError, Result};

#[derive(Debug, Clone)]
pub struct Cli {
    /// 开发服务器地址。有值就走 http 加载。
    pub dev_url: Option<String>,
    /// 数据根。空则用内核默认（`%APPDATA%/baicodex`）。
    pub base: Option<PathBuf>,
    /// 前端构建产物目录。默认找 exe 同级的 `web/`，再找仓库里的 `web/dist`。
    pub web_dir: Option<PathBuf>,
    /// 窗口标题。
    pub title: String,
    /// 关掉托盘图标（调试用）。
    pub no_tray: bool,
    /// 打开开发者工具。
    pub debug: bool,
    /// 只打印解析结果就退出。
    pub dry_run: bool,
}

impl Default for Cli {
    fn default() -> Self {
        Self {
            dev_url: None,
            base: None,
            web_dir: None,
            title: "baicodex".into(),
            no_tray: false,
            debug: cfg!(debug_assertions),
            dry_run: false,
        }
    }
}

impl Cli {
    /// 解析命令行。不认识的参数一律报错——静默忽略会让"我明明传了 `--dev`"变成
    /// 半小时的排查。
    pub fn parse<I: IntoIterator<Item = String>>(argv: I) -> Result<Self> {
        let mut c = Cli {
            dev_url: std::env::var("BAICODEX_DEV_URL").ok().filter(|s| !s.trim().is_empty()),
            base: std::env::var("BAICODEX_BASE").ok().filter(|s| !s.trim().is_empty()).map(PathBuf::from),
            ..Default::default()
        };

        let mut it = argv.into_iter();
        while let Some(a) = it.next() {
            match a.as_str() {
                "--dev" => c.dev_url = Some(take(&mut it, "--dev")?),
                "--base" => c.base = Some(PathBuf::from(take(&mut it, "--base")?)),
                "--web" => c.web_dir = Some(PathBuf::from(take(&mut it, "--web")?)),
                "--title" => c.title = take(&mut it, "--title")?,
                "--no-tray" => c.no_tray = true,
                "--debug" => c.debug = true,
                "--no-debug" => c.debug = false,
                "--dry-run" => c.dry_run = true,
                "-h" | "--help" => return Err(HostError::msg(HELP)),
                "-V" | "--version" => {
                    return Err(HostError::msg(format!("baicodex-desktop {}", env!("CARGO_PKG_VERSION"))))
                }
                other => return Err(HostError::msg(format!("不认识的参数：{other}\n\n{HELP}"))),
            }
        }
        Ok(c)
    }

    /// 加载地址。开发用 dev server，生产用本地文件。
    pub fn url(&self) -> Result<String> {
        if let Some(d) = &self.dev_url {
            return Ok(d.clone());
        }
        let dir = self.resolve_web_dir()?;
        Ok(format!("file:///{}", dir.join("index.html").to_string_lossy().replace('\\', "/")))
    }

    /// 找前端产物。
    ///
    /// 顺序：显式 `--web` → exe 同级 `web` → 当前目录 `web/dist` → 仓库 `web/apps/desktop/dist`。
    /// 全都没有就报错，让用户知道是"前端没构建"，而不是白屏。
    pub fn resolve_web_dir(&self) -> Result<PathBuf> {
        if let Some(d) = &self.web_dir {
            return ensure_index(d);
        }
        let mut tried = Vec::new();

        if let Ok(exe) = std::env::current_exe() {
            if let Some(dir) = exe.parent() {
                let p = dir.join("web");
                tried.push(p.clone());
                if p.join("index.html").is_file() {
                    return Ok(p);
                }
            }
        }
        if let Ok(cwd) = std::env::current_dir() {
            for rel in ["web/dist", "web/apps/desktop/dist", "desktop/web/apps/desktop/dist"] {
                let p = cwd.join(rel);
                tried.push(p.clone());
                if p.join("index.html").is_file() {
                    return Ok(p);
                }
            }
        }
        // 开发期直接 cargo run 时，cwd 是 crate 目录；前端在仓库根的 desktop/web 下。
        // host-win 在 app/src/main/rust/crates/host-win，往上 6 级才是 BaiAgent 根。
        let p = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../../../../desktop/web/apps/desktop/dist");
        if p.join("index.html").is_file() {
            return Ok(p);
        }
        tried.push(p);

        Err(HostError::msg(format!(
            "找不到前端产物（index.html）。先 `pnpm -C desktop/web build`，或用 `--dev http://localhost:5173` 跑开发模式。\n找过：{}",
            tried.iter().map(|p| p.display().to_string()).collect::<Vec<_>>().join("、")
        )))
    }
}

fn take(it: &mut impl Iterator<Item = String>, flag: &str) -> Result<String> {
    it.next().ok_or_else(|| HostError::msg(format!("{flag} 后面要跟一个值")))
}

fn ensure_index(d: &PathBuf) -> Result<PathBuf> {
    if d.join("index.html").is_file() {
        Ok(d.clone())
    } else {
        Err(HostError::msg(format!("{} 里没有 index.html", d.display())))
    }
}

pub const HELP: &str = "\
baicodex-desktop — 桌面端

用法：
  baicodex-desktop                  加载内置前端
  baicodex-desktop --dev <url>      连 vite 开发服务器
  baicodex-desktop --base <dir>     指定数据目录

选项：
  --dev <url>      开发模式，例如 http://localhost:5173
  --base <dir>     数据根（默认 %APPDATA%\\baicodex）
  --web <dir>      前端产物目录
  --title <s>      窗口标题
  --no-tray        不显示托盘图标
  --debug          打开开发者工具（调试构建默认开）
  --no-debug       关掉开发者工具
  --dry-run        只打印解析结果，不建窗口
  -h, --help       这份说明
  -V, --version    版本

环境变量：BAICODEX_DEV_URL、BAICODEX_BASE
";