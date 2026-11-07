//! 终端渲染用的小工具：折行、单行化、事件标签。
//!
//! 为什么不用 markdown 渲染库：终端里能表达的只有颜色与粗体，把 `**粗**` 变成真粗体
//! 需要一套 span 切分，收益远小于复杂度。这里只做三件事：去掉标记、折行、截断。

/// 把一段可能带 markdown 标记的文本变成纯文本（保留代码块缩进）。
pub fn strip_markdown(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut in_code = false;
    for line in s.lines() {
        if line.trim_start().starts_with("```") {
            in_code = !in_code;
            continue;
        }
        if in_code {
            out.push_str("    ");
            out.push_str(line);
            out.push('\n');
            continue;
        }
        let mut l = line.to_string();
        // 标题
        let t = l.trim_start();
        if t.starts_with('#') {
            let rest = t.trim_start_matches('#').trim_start();
            l = rest.to_string();
        }
        // 行内代码与强调
        l = l.replace("**", "").replace("__", "").replace('`', "");
        // 链接 [文字](url) → 文字 (url)
        while let Some(i) = l.find("](") {
            let Some(lb) = l[..i].rfind('[') else { break };
            let Some(rp) = l[i + 2..].find(')') else { break };
            let text = l[lb + 1..i].to_string();
            let url = l[i + 2..i + 2 + rp].to_string();
            l = format!("{}{} ({}){}", &l[..lb], text, url, &l[i + 2 + rp + 1..]);
        }
        out.push_str(&l);
        out.push('\n');
    }
    out.trim_end().to_string()
}

/// 单行化：把换行压成空格，多个空格压成一个。
pub fn one_line(s: &str) -> String {
    crate::core::base::text::one_line(s)
}

/// 按宽度折行（中日韩字符按 2 宽算）。
pub fn wrap_block(s: &str, width: usize) -> Vec<String> {
    let w = width.max(8);
    let mut out = Vec::new();
    for line in s.lines() {
        if line.trim().is_empty() {
            out.push(String::new());
            continue;
        }
        let mut cur = String::new();
        let mut cur_w = 0usize;
        for ch in line.chars() {
            let cw = crate::core::base::text::Text::width(&ch.to_string());
            if cur_w + cw > w && !cur.is_empty() {
                out.push(std::mem::take(&mut cur));
                cur_w = 0;
            }
            cur.push(ch);
            cur_w += cw;
        }
        if !cur.is_empty() {
            out.push(cur);
        }
    }
    out
}

/// 事件类型 → 界面上的短标签。
pub fn kind_tag(kind: &str) -> &'static str {
    match kind {
        "status" => "状态",
        "delta" => "流式",
        "message" => "消息",
        "tool_start" => "工具",
        "tool_end" => "完成",
        "busy" => "忙闲",
        "sessions" => "会话",
        "notice" => "提示",
        "confirm" => "确认",
        "ask" => "提问",
        "plan" => "计划",
        "usage" => "用量",
        "quit" => "退出",
        _ => "其它",
    }
}

/// 角色 → 前缀。
pub fn role_prefix(role: &str) -> &'static str {
    match role {
        "user" => "你",
        "assistant" => "助手",
        "tool" => "工具",
        "summary" => "摘要",
        "system" => "系统",
        _ => "·",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_inline_marks() {
        assert_eq!(strip_markdown("**粗** 与 `code`"), "粗 与 code");
    }

    #[test]
    fn wraps_cjk_by_width() {
        let v = wrap_block("中文中文中文", 4);
        assert!(v.len() >= 2);
    }

    #[test]
    fn link_becomes_text_and_url() {
        assert!(strip_markdown("见 [文档](https://a.b)").contains("文档 (https://a.b)"));
    }
}
