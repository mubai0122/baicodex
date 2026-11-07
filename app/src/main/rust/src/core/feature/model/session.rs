//! 会话行与访问通道。
//!
//! `SessionRow` 是"会话列表"用的轻量结构，不带消息体：一个用户可能有上千个会话，
//! 列表页不可能每条都去读消息。预览文本、条数、置顶状态都在建表时冗余存一份。

use serde::{Deserialize, Serialize};

/// 会话列表行。
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SessionRow {
    pub id: String,
    pub title: String,
    /// 分类（用户自定义文件夹）。空表示未分类。
    #[serde(default)]
    pub category: String,
    /// 备注/描述。
    #[serde(default)]
    pub descr: String,
    pub updated_at: i64,
    /// 最后一条消息的预览。
    #[serde(default)]
    pub preview: String,
    /// 消息条数。
    #[serde(default)]
    pub count: i64,
    #[serde(default)]
    pub pinned: bool,
    /// 绑定的工作区目录。
    #[serde(default)]
    pub workspace: String,
    /// 人设 id。
    #[serde(default)]
    pub persona: String,
}

impl SessionRow {
    /// 显示用标题：空标题退回"新会话"。
    pub fn display_title(&self) -> &str {
        if self.title.trim().is_empty() {
            "新会话"
        } else {
            &self.title
        }
    }

    /// 列表第二行：预览 + 时间。
    pub fn subtitle(&self) -> String {
        let mut s = String::new();
        if !self.category.is_empty() {
            s.push_str(&format!("[{}] ", self.category));
        }
        s.push_str(&self.preview);
        if !s.is_empty() {
            s.push_str(" · ");
        }
        s.push_str(&crate::core::base::clock::humanize(self.updated_at));
        s
    }

    /// 是否有工作区。
    pub fn has_workspace(&self) -> bool {
        !self.workspace.trim().is_empty()
    }
}

/// 一次文件操作实际走的通道。工具结果里会带上它，用户能看到"为什么能读"。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[derive(Default)]
pub enum Route {
    /// 应用沙箱内。
    #[default]
    Sandbox,
    /// 有"所有文件访问"权限。
    AllFiles,
    /// 零宽路径。
    ZeroWidth,
    /// Shizuku（UID 2000）。
    Shizuku,
    /// root。
    Root,
}


impl Route {
    pub fn label(self) -> &'static str {
        match self {
            Route::Sandbox => "沙箱",
            Route::AllFiles => "全文件",
            Route::ZeroWidth => "零宽",
            Route::Shizuku => "Shizuku",
            Route::Root => "root",
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Route::Sandbox => "sandbox",
            Route::AllFiles => "all_files",
            Route::ZeroWidth => "zero_width",
            Route::Shizuku => "shizuku",
            Route::Root => "root",
        }
    }

    pub fn parse(s: &str) -> Route {
        match s {
            "all_files" => Route::AllFiles,
            "zero_width" => Route::ZeroWidth,
            "shizuku" => Route::Shizuku,
            "root" => Route::Root,
            _ => Route::Sandbox,
        }
    }

    /// 是否需要提权通道（用于在结果里提示"这条命令以 root 执行"）。
    pub fn privileged(self) -> bool {
        matches!(self, Route::Shizuku | Route::Root)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn route_roundtrip() {
        for r in [Route::Sandbox, Route::AllFiles, Route::ZeroWidth, Route::Shizuku, Route::Root] {
            assert_eq!(Route::parse(r.as_str()), r);
        }
    }

    #[test]
    fn empty_title_falls_back() {
        let s = SessionRow::default();
        assert_eq!(s.display_title(), "新会话");
    }
}
