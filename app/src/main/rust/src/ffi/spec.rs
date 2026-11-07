//! 操作契约表。
//!
//! 这是**唯一的**跨语言接口声明：Kotlin / TypeScript / C++ 三侧的类型都从这里
//! 生成，而不是各自抄一份。抄一份的结局一定是漂移——某个 op 改了参数名，三端
//! 有两端还在用旧的，线上表现是"点了没反应"。
//!
//! ## 为什么表在 ffi 里，而不是单独一个 crate
//!
//! 契约必须贴着实现放。放到别处，改 `dispatch` 的人看不见它，半年后必然有两份
//! 互相矛盾的真相。放这里，改分支时抬头就能看见上面那张表。
//!
//! ## 怎么保证表没漏
//!
//! [`tests::every_op_is_dispatched`] 会拿表里每个名字真的去调一次 `dispatch`，
//! 只要有一个走到 `未知操作` 分支就失败。反过来，`dispatch` 里多出来的 op 表里
//! 没有，会在 [`tests::every_dispatch_branch_is_declared`] 里被文件扫描抓出来。
//!
//! ## 字段类型为什么是宽口径
//!
//! 参数名是契约，参数类型不是。前端拿到的值本来就来自 JSON，硬标 `string` 只会
//! 在边缘情况（数字 id 传成字符串）逼着调用方写 `as any`。所以表里只声明"有哪
//! 几个键、哪些必填"，类型交给各语言自己的封装层（Kotlin 的 `Kernel.kt`、TS 的
//! `kernel.ts`）去收紧。

/// 返回值形状。只用于给前端做运行期断言，不参与序列化。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Ret {
    /// `null`
    Null,
    /// 布尔
    Bool,
    /// 数字（含 id）
    Num,
    /// 字符串
    Str,
    /// 对象
    Obj,
    /// 数组
    List,
    /// 结构不固定，前端按 `unknown` 处理
    Any,
}

impl Ret {
    /// TS 类型表达式。
    pub fn ts(self) -> &'static str {
        match self {
            Ret::Null => "null",
            Ret::Bool => "boolean",
            Ret::Num => "number",
            Ret::Str => "string",
            Ret::Obj => "Record<string, unknown>",
            Ret::List => "unknown[]",
            Ret::Any => "unknown",
        }
    }
}

/// 一个 op 的声明。
#[derive(Debug, Clone, Copy)]
pub struct OpSpec {
    /// op 名，snake_case，与 `dispatch` 的分支字符串逐字一致。
    pub name: &'static str,
    /// 参数名。顺序即文档顺序，不表示位置参数——所有参数都走 `args` 对象。
    pub args: &'static [&'static str],
    /// 必填参数。不在这里面的都可以省略。
    pub required: &'static [&'static str],
    /// 返回形状。
    pub ret: Ret,
    /// 一句话说明。会进 IDE 的悬浮提示，所以写"给谁用、什么时候用"，不写实现。
    pub doc: &'static str,
}

/// 参数表简写：无可选/必填之分的 op 用这个。
const fn op(name: &'static str, args: &'static [&'static str], ret: Ret, doc: &'static str) -> OpSpec {
    OpSpec { name, args, required: &[], ret, doc }
}

/// 全量操作表。
///
/// 顺序按「前端会怎么用」排：先生命周期，再对话，再配置，最后是工具类。
/// 新增 op 时**加在这里，同时加 dispatch 分支**，两边任一缺失测试都会红。
pub const OPS: &[OpSpec] = &[
    // —— 生命周期 ——
    OpSpec {
        name: "boot",
        args: &["base"],
        required: &[],
        ret: Ret::Obj,
        doc: "启动内核。base 为空则用平台默认数据根；重复调用幂等。",
    },
    op("shutdown", &[], Ret::Null, "停掉当前生成、落盘。进程级资源留到退出。"),
    op("version", &[], Ret::Obj, "版本、架构、平台、JS 引擎名。"),
    // —— 对话 ——
    OpSpec {
        name: "send",
        args: &["text", "images", "files"],
        required: &["text"],
        ret: Ret::Obj,
        doc: "发一条消息并跑到结束。返回正文、轮数、工具次数、是否被中断、token 用量。",
    },
    op("regenerate", &[], Ret::Obj, "对最后一条回复重新生成（加一个兄弟分支）。"),
    op("stop", &[], Ret::Null, "中断当前这一轮。没在跑就什么也不做。"),
    op("snapshot", &[], Ret::Obj, "一次取全界面状态：配置、会话、消息、忙闲、待确认。"),
    op("messages", &[], Ret::List, "当前会话的消息树（当前分支）。"),
    op("sessions", &[], Ret::List, "会话列表，按最近活动排序。"),
    op("new_session", &[], Ret::Obj, "新建会话并切过去。"),
    op("open_session", &["id"], Ret::Obj, "切到指定会话。"),
    op("delete_session", &["id"], Ret::Bool, "删除会话及其消息。"),
    op("rename_session", &["id", "title"], Ret::Bool, "改标题。"),
    op("search_sessions", &["q"], Ret::List, "按标题与正文搜会话，返回 id 列表。"),
    op("ensure_session", &[], Ret::Obj, "保证有一个当前会话；没有就建一个。"),
    op("clear_current", &[], Ret::Null, "清空当前会话的消息。"),
    op("compact", &[], Ret::Obj, "手动压缩上下文，把早期消息折成摘要。"),
    op("export_md", &[], Ret::Str, "把当前分支导出成 Markdown 文本。"),
    // —— 消息级操作 ——
    op("pin_session", &["id", "on"], Ret::Bool, "置顶/取消置顶会话。"),
    op("archive_session", &["id", "on"], Ret::Bool, "归档/取消归档会话。"),
    op("star_message", &["id", "on"], Ret::Bool, "收藏/取消收藏一条消息。"),
    op("delete_message", &["id"], Ret::Bool, "删除一条消息（连带其子分支）。"),
    op("edit_message", &["id", "text"], Ret::Bool, "改一条消息的正文。"),
    op("edit_resend", &["id", "text", "images", "files"], Ret::Obj, "改用户消息并从该点重跑。"),
    op("session_stats", &["id"], Ret::Obj, "单个会话的轮数、token、花费。"),
    op("set_session_persona", &["id", "persona"], Ret::Bool, "给会话换人设。"),
    op("set_session_workspace", &["id", "path"], Ret::Bool, "给会话绑工作区。"),
    op("siblings", &["id"], Ret::Obj, "一条消息的所有分支及其序号。"),
    op("switch_branch", &["id"], Ret::Obj, "切到另一个分支。"),
    op("starred", &["limit"], Ret::List, "收藏的消息，按时间倒序。"),
    // —— 配置 ——
    op("config_get", &[], Ret::Obj, "读全量配置。"),
    OpSpec {
        name: "config_patch",
        args: &["patch"],
        required: &["patch"],
        ret: Ret::Obj,
        doc: "深合并一小块配置并立即落盘，返回合并后的全量。",
    },
    op("config_save", &[], Ret::Null, "把内存里的配置写盘（平时不用手动调）。"),
    op("system_prompt", &[], Ret::Str, "读系统提示词。"),
    op("set_system_prompt", &["text"], Ret::Null, "改系统提示词。"),
    // —— 工具 ——
    op("tools", &[], Ret::List, "当前可用工具及其参数 schema。"),
    OpSpec {
        name: "tool_call",
        args: &["name", "args"],
        required: &["name"],
        ret: Ret::Obj,
        doc: "直接跑一次工具，不经过模型。返回 ok/text/route/meta。",
    },
    op("confirm_tool", &["allow", "permanent"], Ret::Null, "回答「是否允许这次工具调用」。"),
    op("answer_ask", &["answers"], Ret::Null, "回答模型的追问（多选，按问题顺序）。"),
    op("answer_plan", &["verdict", "note"], Ret::Null, "确认/打回计划。"),
    op("pending", &[], Ret::Obj, "当前在等用户做什么：confirm / ask / plan / 空。"),
    // —— 记忆 ——
    op("memory_list", &[], Ret::List, "全部记忆条目。"),
    op("memory_save", &["text"], Ret::Obj, "新增一条记忆，重复的会合并。"),
    op("memory_delete", &["id"], Ret::Bool, "删一条记忆。"),
    op("memory_search", &["q"], Ret::List, "按语义相关度召回记忆。"),
    // —— 技能 / MCP / 工作流 ——
    op("skills", &[], Ret::Obj, "已装载的技能文档与技能包。"),
    op("skill_get", &["name"], Ret::Obj, "读一个技能的完整正文。"),
    op("skill_delete", &["name"], Ret::Bool, "删一个技能。"),
    op("pack_list", &[], Ret::List, "技能包列表。"),
    op("pack_toggle", &["id", "on"], Ret::Bool, "启用/停用技能包。"),
    op("mcp_list", &[], Ret::List, "MCP 服务器列表。"),
    OpSpec {
        name: "mcp_add",
        args: &["name", "url", "token", "kind"],
        required: &["name", "url"],
        ret: Ret::Obj,
        doc: "加一个 MCP 服务器。kind 为 stdio / http / sse。",
    },
    op("mcp_remove", &["id"], Ret::Bool, "移除 MCP 服务器。"),
    op("mcp_toggle", &["id", "on"], Ret::Bool, "启用/停用 MCP 服务器。"),
    op("mcp_refresh", &["id"], Ret::Obj, "重新拉取该服务器的工具清单。"),
    op("flows", &[], Ret::List, "工作流列表。"),
    op("flow_save", &[], Ret::Obj, "保存工作流（对象即 body，或放在 patch 里）。"),
    op("flow_delete", &["id"], Ret::Bool, "删工作流。"),
    // —— 用量 ——
    op("usage", &[], Ret::Obj, "总计：调用次数、token、花费。"),
    op("usage_days", &[], Ret::List, "按天聚合的用量。"),
    op("usage_session", &["id"], Ret::Obj, "单会话用量。"),
    // —— 人设 / 配置档 / 供应商 ——
    op("personas", &[], Ret::List, "人设列表。"),
    op("persona_save", &[], Ret::Obj, "保存人设（对象即 body）。返回 id。"),
    op("persona_delete", &["id"], Ret::Bool, "删人设。"),
    op("profiles", &[], Ret::Obj, "配置档列表与当前档 id。"),
    op("profile_save", &[], Ret::Obj, "保存配置档（对象即 body）。返回 id。"),
    op("profile_use", &["id"], Ret::Obj, "切到某个配置档。"),
    op("profile_delete", &["id"], Ret::Bool, "删配置档。"),
    op("providers", &[], Ret::List, "供应商列表。"),
    op("provider_save", &[], Ret::Obj, "保存供应商（对象即 body）。返回 id。"),
    op("provider_delete", &["id"], Ret::Bool, "删供应商。"),
    // —— 工作区 / 设备 ——
    op("workspace", &[], Ret::Obj, "当前工作区路径与文件树概要。"),
    op("workspace_bind", &["path"], Ret::Obj, "绑定工作区。"),
    op("device", &[], Ret::Obj, "设备信息：型号、系统、可用能力。"),
    // —— 互联（adb 通道）——
    op("adb_status", &[], Ret::Obj, "adb 是否可用、路径、版本与已连设备列表。不自动下载。"),
    op("adb_install", &[], Ret::Obj, "下载并解压官方 platform-tools，返回安装结果。"),
    op("adb_connect", &["addr"], Ret::Obj, "连接一个已开 TCP 模式的设备，形如 192.168.1.20:5555。"),
    op("adb_pair", &["addr", "code"], Ret::Obj, "无线调试配对（Android 11+），addr 是配对界面上那个随机端口。"),
    op("adb_forward", &["local", "remote"], Ret::Obj, "把手机端口映射到本机 localhost，供浏览器/调试器直连。"),
    op("adb_shell", &["cmd"], Ret::Obj, "在手机上执行一条 shell 命令，不落盘也不传输。"),
    op("relay_start", &["port", "allow", "require_token"], Ret::Obj, "起内网直连通道：让另一台机器按区间读写本机文件。默认只认 loopback，allow 填 IP/CIDR 才对外。"),
    op("relay_stop", &[], Ret::Obj, "停内网直连通道，返回停止后的状态。"),
    op("relay_status", &[], Ret::Obj, "通道状态：端口、token、白名单、本机内网地址、命中次数。"),
    op("relay_allow", &["allow"], Ret::Obj, "改白名单，不重启服务；传空数组等于只放行 loopback。"),
    // —— 备份 / 凭据 ——
    op("backup", &[], Ret::Obj, "导出加密备份包，返回路径与大小。"),
    op("vault_keys", &["bucket"], Ret::List, "某个桶里的键名（不含值）。"),
    OpSpec {
        name: "vault_put",
        args: &["bucket", "key", "value"],
        required: &["key", "value"],
        ret: Ret::Bool,
        doc: "写一条凭据。值加密落盘，日志里永远是 ***。",
    },
    op("vault_delete", &["bucket", "key"], Ret::Bool, "删一条凭据。"),
    // —— 文件 ——
    op("fs_list", &["path", "recurse", "limit"], Ret::List, "列目录。"),
    op("fs_read", &["path"], Ret::Obj, "读文本文件，带大小与编码判定。"),
    op("fs_write", &["path", "content"], Ret::Obj, "写文本文件（原子替换）。"),
    op("fs_delete", &["path"], Ret::Bool, "删文件或空目录。"),
    op("fs_info", &["path"], Ret::Obj, "路径元信息：类型、大小、时间、权限。"),
    op("fs_search", &["path", "pattern", "ext", "max", "ignore_case"], Ret::List, "按内容正则搜文件。"),
    // —— 系统 ——
    op("clipboard", &["action", "text"], Ret::Obj, "读写剪贴板。action 为 get / set。"),
    op("notify", &["title", "body"], Ret::Null, "发系统通知。"),
    op("apk_info", &["action", "path", "keyword", "limit"], Ret::Obj, "APK 解析与搜索。"),
    // —— 插件 ——
    op("plugin_list", &[], Ret::List, "已装插件。"),
    op("plugin_install", &["path", "id"], Ret::Obj, "从 zip 装插件到数据根。"),
    op("plugin_remove", &["id"], Ret::Bool, "卸载插件。"),
];

/// 按名字找声明。给前端生成器与调试用。
pub fn find(name: &str) -> Option<&'static OpSpec> {
    OPS.iter().find(|o| o.name == name)
}

/// 全部 op 名。
pub fn names() -> impl Iterator<Item = &'static str> {
    OPS.iter().map(|o| o.name)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    /// 表里每个 op 都必须真的被 dispatch 认识。
    ///
    /// 判据是错误信息里不能出现"不认识的 op"——这些调用大多会因为没启动内核或
    /// 缺参数而失败，那是对的；失败原因必须是别的东西。所以本用例**不需要**
    /// 启动内核，也因此不会去碰 `boot` / `shutdown`：前者会用空 base 占住进程级
    /// `OnceLock`，后者会把别人跑到一半的内核停掉。这两个改用源码静态校验。
    #[test]
    fn every_op_is_dispatched() {
        let _guard = crate::ffi::test_lock();
        let src = include_str!("mod.rs");
        for name in names() {
            if matches!(name, "boot" | "shutdown") {
                assert!(
                    src.contains(&format!("\"{name}\" =>")),
                    "op `{name}` 在契约表里，但 dispatch 没有对应分支"
                );
                continue;
            }
            let v = crate::ffi::call_value(&json!({ "op": name, "args": {} }));
            if let Some(msg) = v["error"]["message"].as_str() {
                assert!(
                    !msg.contains("不认识的 op"),
                    "op `{name}` 在契约表里，但 dispatch 不认识它"
                );
            }
        }
    }

    /// 反向：dispatch 里写了的 op，必须在表里声明。
    ///
    /// 用源码扫描而不是运行时反射——Rust 没有 `match` 反射，扫源码是唯一能在
    /// 测试期发现"加了分支没加表"的办法。分支缩进固定 8 空格，这里跟着这个约定。
    #[test]
    fn every_dispatch_branch_is_declared() {
        let src = include_str!("mod.rs");
        let start = src.find("fn dispatch(op: &str, args: &Value)").expect("找不到 dispatch");
        let body = &src[start..];
        let end = body.find("\n}\n\n/// 布尔参数").expect("找不到 dispatch 的结尾");
        let body = &body[..end];

        let declared: std::collections::HashSet<&str> = names().collect();
        let mut found = 0usize;
        for line in body.lines() {
            let t = line.trim();
            // 分支行形如 `"send" => with(...)`，且缩进正好 8 空格。
            if line.len() - line.trim_start().len() != 8 {
                continue;
            }
            let Some(rest) = t.strip_prefix('"') else { continue };
            let Some(q) = rest.find('"') else { continue };
            let op = &rest[..q];
            if !rest[q + 1..].trim_start().starts_with("=>") {
                continue;
            }
            found += 1;
            assert!(declared.contains(op), "dispatch 有分支 `{op}`，但契约表里没有");
        }
        assert_eq!(found, OPS.len(), "分支数与表大小不一致：{found} vs {}", OPS.len());
    }

    #[test]
    fn names_are_unique() {
        let mut seen = std::collections::HashSet::new();
        for n in names() {
            assert!(seen.insert(n), "op 名重复：{n}");
        }
    }

    /// 必填参数必须是参数列表的子集——写错了会让前端生成出矛盾的文档。
    #[test]
    fn required_is_subset_of_args() {
        for o in OPS {
            for r in o.required {
                assert!(o.args.contains(r), "`{}` 的必填参数 `{r}` 不在 args 里", o.name);
            }
        }
    }
}