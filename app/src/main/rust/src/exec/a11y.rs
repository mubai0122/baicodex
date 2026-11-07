//! 屏幕：读控件树、截图、点击 / 滑动 / 输入、系统键、拉起 App。
//!
//! 两条后端：
//!
//! - **无障碍**（`BAICODE_A11Y=1` 或检测到已启用的服务）：能直接读节点树，最稳；
//! - **uiautomator**：没有无障碍时退化到 `uiautomator dump` + `input`，需要 root / Shizuku。
//!
//! ## 为什么先 dump 到文件再 cat
//!
//! `uiautomator dump` 把 XML 写到 stdout 时会夹带 "UI hierchary dumped to: ..." 之类
//! 的噪音，且在某些 ROM 上直接管道会截断。固定写文件再读，行为稳定得多。
//!
//! ## 为什么节点要过滤
//!
//! 一个列表页动辄几千个节点，全塞给模型既烧 token 又没用。默认只保留"有文字、
//! 有描述、或可点击"的节点，上限 400 条。

use std::time::Duration;

use regex::Regex;

use super::shell::{self, ShellResult};
use crate::core::base::{dirs, text};

/// 一次 dump 最多渲染多少节点。
const MAX_NODES: usize = 400;

/// 一个控件节点。
#[derive(Debug, Clone, Default)]
pub struct Node {
    pub text: String,
    pub desc: String,
    pub class: String,
    pub id: String,
    pub clickable: bool,
    pub scrollable: bool,
    pub enabled: bool,
    pub bounds: (i32, i32, i32, i32),
}

impl Node {
    /// 中心点，直接可以拿去 tap。
    pub fn center(&self) -> (i32, i32) {
        let (x1, y1, x2, y2) = self.bounds;
        ((x1 + x2) / 2, (y1 + y2) / 2)
    }

    /// 有没有可用来匹配的文本。
    pub fn label(&self) -> String {
        if !self.text.trim().is_empty() {
            self.text.trim().to_string()
        } else {
            self.desc.trim().to_string()
        }
    }

    /// 一行展示。
    pub fn line(&self, i: usize) -> String {
        let short = self.class.rsplit('.').next().unwrap_or(&self.class);
        let (x1, y1, x2, y2) = self.bounds;
        let (cx, cy) = self.center();
        format!(
            "[{i}] {short} text=\"{}\" desc=\"{}\" id=\"{}\" clickable={} bounds=[{x1},{y1}][{x2},{y2}] center=({cx}, {cy})",
            shorten(&self.text, 60),
            shorten(&self.desc, 40),
            shorten(&self.id, 60),
            self.clickable
        )
    }
}

fn shorten(s: &str, n: usize) -> String {
    text::one_line(s).chars().take(n).collect()
}

/// 屏幕后端。
#[derive(Debug, Clone)]
pub struct A11y {
    pub enabled: bool,
    /// 报告里显示用的是哪条通道。
    pub backend: &'static str,
}

impl A11y {
    /// 探测可用性。`enabled` 表示能否读到节点树。
    pub async fn detect() -> Self {
        if std::env::var("BAICODE_A11Y").map(|v| v == "1").unwrap_or(false) {
            return Self { enabled: true, backend: "无障碍" };
        }
        let r = shell::run(
            "dumpsys accessibility 2>/dev/null | grep -c 'enabled services'",
            None,
            Duration::from_secs(5),
            None,
        )
        .await;
        let has_service = r.ok() && r.out.trim().parse::<i32>().unwrap_or(0) > 0;
        if has_service {
            return Self { enabled: true, backend: "无障碍" };
        }
        // 没有无障碍也可能能用 uiautomator（root / Shizuku 下）。
        if shell::has("uiautomator") {
            return Self { enabled: false, backend: "uiautomator" };
        }
        Self { enabled: false, backend: "不可用" }
    }

    /// 读控件树并渲染成文本。`all` 为 true 时连空壳容器一起给。
    pub async fn dump(&self, all: bool) -> String {
        match self.nodes().await {
            Ok(nodes) => render(&nodes, all),
            Err(e) => e,
        }
    }

    /// 只取节点，不渲染。
    pub async fn nodes(&self) -> Result<Vec<Node>, String> {
        let out = dirs::tmp("ui.xml");
        let out_s = out.to_string_lossy().to_string();
        let cmd = format!(
            "uiautomator dump --compressed {} >/dev/null 2>&1 && cat {}",
            shell::quote(&out_s),
            shell::quote(&out_s)
        );
        let r = shell::run(&cmd, None, Duration::from_secs(25), None).await;
        let _ = std::fs::remove_file(&out);
        if !r.ok() || r.out.trim().is_empty() {
            return Err(format!(
                "拿不到控件树（当前后端：{}）。无障碍服务没开时，uiautomator 需要 Shizuku 或 Root。",
                self.backend
            ));
        }
        Ok(parse(&r.out))
    }

    /// 截图，返回路径。
    pub async fn screenshot(&self) -> String {
        let p = dirs::new_shot();
        let ps = p.to_string_lossy().to_string();
        let r = shell::run(&format!("screencap -p {}", shell::quote(&ps)), None, Duration::from_secs(25), None).await;
        if r.ok() && p.exists() {
            let size = std::fs::metadata(&p).map(|m| m.len()).unwrap_or(0);
            if size > 0 {
                return format!("截图: {ps}");
            }
        }
        format!("截图失败：{}", if r.out.trim().is_empty() { "screencap 没有输出" } else { r.out.trim() })
    }

    /// 点击。
    pub async fn tap(&self, x: i32, y: i32) -> String {
        if !in_screen(x, y) {
            return format!("坐标 ({x}, {y}) 不合法");
        }
        input(&format!("input tap {x} {y}")).await
    }

    /// 长按 = 原地滑动 + 时长。
    pub async fn long_press(&self, x: i32, y: i32, dur_ms: u64) -> String {
        input(&format!("input swipe {x} {y} {x} {y} {}", dur_ms.clamp(400, 10_000))).await
    }

    /// 滑动。
    pub async fn swipe(&self, x1: i32, y1: i32, x2: i32, y2: i32, dur_ms: u64) -> String {
        input(&format!("input swipe {x1} {y1} {x2} {y2} {}", dur_ms.clamp(50, 10_000))).await
    }

    /// 输入文字。
    pub async fn input_text(&self, s: &str) -> String {
        if s.is_empty() {
            return "要输入的文字是空的".into();
        }
        // `input text` 只认 ASCII 的一部分：空格要 `%s`，中文得走 adb 之外的路。
        // 中文用 `am broadcast` 的剪贴板方案不可靠，这里明确告诉模型换路。
        if !s.is_ascii() {
            return "input text 不支持非 ASCII：中文请先 clipboard 写入剪贴板，再用 key 长按粘贴；或改用无障碍节点 setText".into();
        }
        let escaped = s.replace('\'', "'\\''").replace(' ', "%s");
        // 先绑成变量：input 的 future 借这个字符串，直接传临时量会被提前释放。
        let cmd = format!("input text '{escaped}'");
        input(&cmd).await
    }

    /// 按键。
    pub async fn key(&self, key: &str) -> String {
        // 先落成变量：`other` 分支会把 &str 借出去，临时 String 活不到那一步。
        let lower = key.trim().to_lowercase();
        let code = match lower.as_str() {
            "back" => "4",
            "home" => "3",
            "recents" | "recent" => "187",
            "notifications" | "notification" => "83",
            "quick" | "quick_settings" => "42",
            "power" => "26",
            "volume_up" => "24",
            "volume_down" => "25",
            "enter" => "66",
            "delete" | "backspace" => "67",
            "menu" => "82",
            "search" => "84",
            other => {
                if !other.is_empty() && other.chars().all(|c| c.is_ascii_digit()) {
                    other
                } else {
                    return format!(
                        "不认识的按键：{key}（可用 back / home / recents / notifications / quick / power / volume_up / volume_down / enter / delete / menu / search，或直接给数字 keycode）"
                    );
                }
            }
        };
        input(&format!("input keyevent {code}")).await
    }

    /// 按文字点：先找节点，再点中心。找不到就报清楚。
    pub async fn tap_text(&self, needle: &str) -> String {
        let n = needle.trim();
        if n.is_empty() {
            return "要点的文字是空的".into();
        }
        match self.nodes().await {
            Ok(nodes) => match find(&nodes, n) {
                Some(node) => {
                    let (x, y) = node.center();
                    let r = self.tap(x, y).await;
                    format!("点中「{}」({x}, {y})：{r}", node.label())
                }
                None => format!("界面里没找到「{n}」：先用 action=tree 看一眼有哪些控件"),
            },
            Err(e) => e,
        }
    }

    /// 拉起 App。
    pub async fn launch(&self, pkg: &str) -> String {
        let pkg = pkg.trim();
        if pkg.is_empty() {
            return "缺少包名：不确定就先 action=apps".into();
        }
        if !pkg.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_') {
            return format!("包名不合法：{pkg}");
        }
        let r = shell::run(
            &format!("monkey -p {pkg} -c android.intent.category.LAUNCHER 1 2>&1 | tail -3"),
            None,
            Duration::from_secs(20),
            None,
        )
        .await;
        if r.out.contains("No activities found") || r.out.contains("monkey aborted") {
            format!("没找到这个应用：{pkg}")
        } else {
            format!("已启动 {pkg}\n{}", r.out.trim())
        }
    }

    /// 列已安装的第三方应用。
    pub async fn apps(&self, filter: &str) -> String {
        let r = shell::run("pm list packages -3", None, Duration::from_secs(20), None).await;
        if !r.ok() {
            return format!("列应用失败：{}", r.out);
        }
        let f = filter.trim().to_lowercase();
        let rows: Vec<&str> = r
            .out
            .lines()
            .filter_map(|l| l.strip_prefix("package:"))
            .filter(|p| f.is_empty() || p.to_lowercase().contains(&f))
            .collect();
        if rows.is_empty() {
            "没有匹配的应用".into()
        } else {
            let mut s = format!("共 {} 个：\n", rows.len());
            s.push_str(&rows.join("\n"));
            s
        }
    }

    /// 等界面稳定：连续两次 dump 的节点数相同就算稳定，最多等 `max_ms`。
    ///
    /// 动画、加载骨架屏是"点了没反应"的头号原因，让模型多等一会儿比让它瞎点有效。
    pub async fn wait_idle(&self, max_ms: u64) -> String {
        let deadline = std::time::Instant::now() + Duration::from_millis(max_ms.clamp(200, 15_000));
        let mut last = usize::MAX;
        let mut stable = 0;
        while std::time::Instant::now() < deadline {
            tokio::time::sleep(Duration::from_millis(350)).await;
            let n = match self.nodes().await {
                Ok(v) => v.len(),
                Err(e) => return e,
            };
            if n == last {
                stable += 1;
                if stable >= 2 {
                    return format!("界面已稳定（{n} 个节点）");
                }
            } else {
                stable = 0;
                last = n;
            }
        }
        format!("等了一会儿界面还在变（最后 {last} 个节点）")
    }
}

fn in_screen(x: i32, y: i32) -> bool {
    x >= 0 && y >= 0 && x < 20_000 && y < 20_000
}

async fn input(cmd: &str) -> String {
    let r: ShellResult = shell::run(cmd, None, Duration::from_secs(15), None).await;
    if r.ok() {
        "完成".into()
    } else {
        format!("失败：{}", r.out.trim())
    }
}

/// 渲染节点列表。
fn render(nodes: &[Node], all: bool) -> String {
    let mut out = String::new();
    let mut n = 0;
    for node in nodes {
        if !all && node.label().is_empty() && !node.clickable {
            continue;
        }
        out.push_str(&node.line(n));
        out.push('\n');
        n += 1;
        if n >= MAX_NODES {
            out.push_str(&format!("…（只列前 {MAX_NODES} 个节点）\n"));
            break;
        }
    }
    if out.is_empty() {
        return "控件树是空的：可能还在加载，或这个界面拿不到节点（试试 action=shot 看一眼）".into();
    }
    format!("控件树（{n} 个节点）：\n{out}")
}

/// 找一个匹配 `needle` 的可点节点：先精确，再包含，最后忽略大小写。
pub fn find(nodes: &[Node], needle: &str) -> Option<Node> {
    let key = needle.trim();
    if key.is_empty() {
        return None;
    }
    let lower = key.to_lowercase();
    for n in nodes {
        if n.label() == key && n.clickable {
            return Some(n.clone());
        }
    }
    for n in nodes {
        if n.label() == key {
            return Some(n.clone());
        }
    }
    for n in nodes {
        if n.label().to_lowercase().contains(&lower) {
            return Some(n.clone());
        }
    }
    for n in nodes {
        if n.id.to_lowercase().contains(&lower) {
            return Some(n.clone());
        }
    }
    None
}

/// 解析 uiautomator 的 XML。
fn parse(xml: &str) -> Vec<Node> {
    let Ok(node_re) = Regex::new(r"<node\b([^>]*?)/?>") else { return Vec::new() };
    let Ok(kv_re) = Regex::new(r#"([\w:-]+)="([^"]*)""#) else { return Vec::new() };
    let mut out = Vec::new();
    for cap in node_re.captures_iter(xml) {
        let body = &cap[1];
        let mut n = Node { enabled: true, ..Default::default() };
        for kv in kv_re.captures_iter(body) {
            let v = kv[2].to_string();
            match &kv[1] {
                "text" => n.text = v,
                "content-desc" => n.desc = v,
                "class" => n.class = v,
                "resource-id" => n.id = v,
                "clickable" => n.clickable = v == "true",
                "scrollable" => n.scrollable = v == "true",
                "enabled" => n.enabled = v == "true",
                "bounds" => n.bounds = parse_bounds(&v),
                _ => {}
            }
        }
        out.push(n);
    }
    out
}

fn parse_bounds(s: &str) -> (i32, i32, i32, i32) {
    let Ok(re) = Regex::new(r"-?\d+") else { return (0, 0, 0, 0) };
    let nums: Vec<i32> = re.find_iter(s).filter_map(|m| m.as_str().parse().ok()).collect();
    if nums.len() >= 4 {
        (nums[0], nums[1], nums[2], nums[3])
    } else {
        (0, 0, 0, 0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_basic_tree() {
        let xml = r#"<hierarchy><node class="android.widget.Button" text="确定" clickable="true" bounds="[0,0][100,50]" /></hierarchy>"#;
        let nodes = parse(xml);
        assert_eq!(nodes.len(), 1);
        assert_eq!(nodes[0].label(), "确定");
        assert_eq!(nodes[0].center(), (50, 25));
    }

    #[test]
    fn find_prefers_clickable_exact() {
        let a = Node { text: "确定".into(), clickable: false, ..Default::default() };
        let b = Node { text: "确定".into(), clickable: true, ..Default::default() };
        assert!(find(&[a, b], "确定").unwrap().clickable);
    }
}
