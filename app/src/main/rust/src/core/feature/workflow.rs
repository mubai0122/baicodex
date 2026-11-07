//! 工作流：一串步骤 + 可选定时。
//!
//! 一个工作流就是"把几句话排好队，让 Agent 依次做完"。步骤之间不共享变量
//! ——**共享会带来隐式耦合**：第二步读第一步的中间结果，一旦第一步换了写法，
//! 第二步就悄悄错。要传信息就让第一步把结果写进记忆或文件，第二步显式去读。
//!
//! ## 定时
//!
//! `at` 是毫秒时间戳，0 表示"不绑定定时"。`daily` 为真时按"每天的同一时刻"
//! 重新排程。过期判断带 10 分钟宽限：手机休眠醒来时可能已经过点，不该直接跳过。

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::core::base::{clock, fsx};
use crate::exec::alarms;

const FILE: &str = "flows.json";

/// 到点后允许的迟到窗口。超过这个窗口就等下一次，而不是补跑一堆历史任务。
pub const GRACE_MS: i64 = 10 * 60 * 1000;

/// 一轮里最多跑多少轮工具（防止某个步骤无限循环）。
pub const DEFAULT_ROUNDS: usize = 6;

/// 一个步骤。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Step {
    /// 展示用标题。
    pub title: String,
    /// 交给 Agent 的任务描述。
    pub task: String,
    /// 失败重试次数（不含首次）。
    #[serde(default)]
    pub retries: u32,
    /// 失败时是否跳过、继续下一步。
    #[serde(default)]
    pub skip_on_fail: bool,
}

impl Step {
    pub fn new(title: &str, task: &str) -> Self {
        Self {
            title: if title.trim().is_empty() { "步骤".into() } else { title.trim().to_string() },
            task: task.trim().to_string(),
            retries: 1,
            skip_on_fail: false,
        }
    }

    /// 校验：任务不能空。
    pub fn bad(&self) -> Option<String> {
        if self.task.trim().is_empty() {
            return Some("步骤的 task 是空的".into());
        }
        if self.task.chars().count() > 4000 {
            return Some("步骤描述超过 4000 字".into());
        }
        None
    }
}

/// 一个工作流。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Flow {
    pub id: String,
    pub name: String,
    pub steps: Vec<Step>,
    /// 每步最多几轮工具调用。
    #[serde(default = "default_rounds")]
    pub rounds: usize,
    /// 下次触发时间（毫秒），0 = 未绑定定时。
    #[serde(default)]
    pub at: i64,
    /// 每天重复。
    #[serde(default)]
    pub daily: bool,
    /// 到点是自动执行（true）还是只提醒（false）。
    #[serde(default = "yes")]
    pub auto: bool,
    /// 开关。
    #[serde(default = "yes")]
    pub on: bool,
    #[serde(default)]
    pub last_at: i64,
    #[serde(default)]
    pub last_ok: bool,
    #[serde(default)]
    pub last_note: String,
}

fn default_rounds() -> usize {
    DEFAULT_ROUNDS
}

fn yes() -> bool {
    true
}

impl Flow {
    /// 校验。
    pub fn bad(&self) -> Option<String> {
        if self.steps.is_empty() {
            return Some("至少要有一个步骤".into());
        }
        if self.steps.len() > 30 {
            return Some("步骤太多（上限 30）".into());
        }
        for (i, s) in self.steps.iter().enumerate() {
            if let Some(e) = s.bad() {
                return Some(format!("第 {} 步：{e}", i + 1));
            }
        }
        None
    }

    /// 提示词里一行。
    pub fn line(&self) -> String {
        let when = if self.at > 0 {
            format!(
                "{} {}{}",
                if self.daily { "每天" } else { "单次" },
                alarms::fmt(self.at),
                if self.on { "" } else { "（已关）" }
            )
        } else {
            "未绑定定时".into()
        };
        format!("{}（{}）：{}，{} 步", self.name, self.id, when, self.steps.len())
    }

    /// 详细展示。
    pub fn detail(&self) -> String {
        let mut s = format!("{}（id={}），{} 步\n", self.name, self.id, self.steps.len());
        s.push_str(&format!(
            "定时：{}\n",
            if self.at > 0 {
                format!(
                    "{}{}{}",
                    if self.daily { "每天 " } else { "" },
                    alarms::fmt(self.at),
                    if self.auto { " 自动执行" } else { " 只提醒" }
                )
            } else {
                "未绑定".into()
            }
        ));
        for (i, st) in self.steps.iter().enumerate() {
            s.push_str(&format!(
                "{}. {}（重试 {}，失败{}）\n   {}\n",
                i + 1,
                st.title,
                st.retries,
                if st.skip_on_fail { "跳过" } else { "中止" },
                st.task
            ));
        }
        if self.last_at > 0 {
            s.push_str(&format!(
                "\n上次 {} {}{}",
                alarms::fmt(self.last_at),
                if self.last_ok { "成功" } else { "失败" },
                if self.last_note.trim().is_empty() {
                    String::new()
                } else {
                    format!("：{}", crate::core::base::text::one_line(&self.last_note))
                }
            ));
        }
        s.trim_end().to_string()
    }
}

/// 全部工作流。
#[derive(Debug, Default)]
pub struct Flows {
    pub list: Vec<Flow>,
    base: PathBuf,
}

impl Flows {
    /// 加载。坏文件当作空表（不能让一个 json 语法错把应用卡死）。
    pub fn load(base: &Path) -> Self {
        let p = base.join(FILE);
        let list: Vec<Flow> = std::fs::read_to_string(&p)
            .ok()
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or_default();
        Self { list, base: base.to_path_buf() }
    }

    pub fn save(&self) -> bool {
        let Ok(t) = serde_json::to_string_pretty(&self.list) else { return false };
        fsx::writable_dir(&self.base) && fsx::write_text(self.base.join(FILE), &t).is_ok()
    }

    pub fn len(&self) -> usize {
        self.list.len()
    }

    pub fn is_empty(&self) -> bool {
        self.list.is_empty()
    }

    pub fn get(&self, id: &str) -> Option<&Flow> {
        let k = id.trim();
        if k.is_empty() {
            return None;
        }
        self.list.iter().find(|f| f.id == k).or_else(|| self.list.iter().find(|f| f.name == k))
    }

    pub fn get_mut(&mut self, id: &str) -> Option<&mut Flow> {
        let k = id.trim().to_string();
        if k.is_empty() {
            return None;
        }
        if self.list.iter().any(|f| f.id == k) {
            return self.list.iter_mut().find(|f| f.id == k);
        }
        self.list.iter_mut().find(|f| f.name == k)
    }

    /// 新增或覆盖。
    pub fn upsert(&mut self, mut f: Flow) -> String {
        if f.id.trim().is_empty() {
            f.id = crate::core::base::ids::IdGen::hex(8);
        }
        if f.rounds == 0 {
            f.rounds = DEFAULT_ROUNDS;
        }
        match self.list.iter_mut().find(|x| x.id == f.id) {
            Some(slot) => *slot = f.clone(),
            None => self.list.push(f.clone()),
        }
        self.save();
        f.id
    }

    pub fn remove(&mut self, id: &str) -> bool {
        let before = self.list.len();
        self.list.retain(|f| f.id != id && f.name != id);
        let removed = self.list.len() != before;
        if removed {
            self.save();
        }
        removed
    }

    pub fn toggle(&mut self, id: &str, on: bool) -> bool {
        match self.get_mut(id) {
            Some(f) => {
                f.on = on;
                self.save();
                true
            }
            None => false,
        }
    }

    /// 现在该跑的（到点 + 开着 + 自动执行）。
    pub fn due(&self, now: i64) -> Vec<&Flow> {
        self.list
            .iter()
            .filter(|f| f.on && f.auto && f.at > 0 && alarms::due(f.at, now, GRACE_MS))
            .collect()
    }

    /// 只提醒不执行的（给界面发通知用）。
    pub fn remind(&self, now: i64) -> Vec<&Flow> {
        self.list
            .iter()
            .filter(|f| f.on && !f.auto && f.at > 0 && alarms::due(f.at, now, GRACE_MS))
            .collect()
    }

    /// 记录一次执行结果，并按需排下一次。
    pub fn record(&mut self, id: &str, ok: bool, note: &str) {
        let now = clock::now_ms();
        if let Some(f) = self.get_mut(id) {
            f.last_at = now;
            f.last_ok = ok;
            f.last_note = crate::core::base::text::truncate(note, 400);
            if f.daily {
                f.at = alarms::next_daily(now);
            } else {
                // 单次任务跑完就解绑，避免醒来又跑一遍。
                f.at = 0;
            }
            self.save();
        }
    }

    /// 排下一次（用户手动改时间时用）。
    pub fn reschedule(&mut self, id: &str, at: i64) -> bool {
        match self.get_mut(id) {
            Some(f) => {
                f.at = at;
                self.save();
                true
            }
            None => false,
        }
    }

    /// 摘要，给提示词。
    pub fn prompt(&self) -> String {
        if self.list.is_empty() {
            return String::new();
        }
        let mut s = String::from("已保存的工作流：\n");
        for f in self.list.iter().take(20) {
            s.push_str(&format!("- {}\n", f.line()));
        }
        s
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn flow() -> Flow {
        Flow {
            id: "a".into(),
            name: "测试".into(),
            steps: vec![Step::new("一", "做点事")],
            rounds: 6,
            at: 0,
            daily: false,
            auto: true,
            on: true,
            ..Default::default()
        }
    }

    #[test]
    fn validate_rejects_empty_task() {
        let mut f = flow();
        f.steps[0].task.clear();
        assert!(f.bad().is_some());
    }

    #[test]
    fn due_respects_window() {
        let mut fs = Flows::default();
        let mut f = flow();
        f.at = 1000;
        fs.list.push(f);
        assert!(fs.due(1000).len() == 1);
        assert!(fs.due(1000 + GRACE_MS + 1).is_empty());
        assert!(fs.due(999).is_empty());
    }

    /// 空数据根不能写盘：`Path::new("")` 拼出来的是相对路径，会落到进程当前目录。
    /// 这条测试是为了防止有人"顺手"把 writable_dir 换成 ensure_dir。
    #[test]
    fn save_refuses_empty_base() {
        let mut fs = Flows::default();
        fs.list.push(flow());
        assert!(!fs.save());
        assert!(!std::path::Path::new("flows.json").exists());
    }

    #[test]
    fn record_clears_one_shot() {
        let mut fs = Flows::default();
        fs.list.push(flow());
        fs.record("a", true, "ok");
        assert_eq!(fs.list[0].at, 0);
        assert!(fs.list[0].last_ok);
    }
}
