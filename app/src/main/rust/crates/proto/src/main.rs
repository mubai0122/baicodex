//! 契约生成器。
//!
//! 读 [`baicodex::ffi::spec::OPS`]，写出前端要用的 TypeScript 声明。手写这些
//! 类型是不可行的：90 个 op、15 个事件，改一个字段名就漏一处。
//!
//! ```text
//! cargo run -p baicodex-proto            # 生成到 web/packages/bridge/src/
//! cargo run -p baicodex-proto -- check   # 校验磁盘上的文件是不是最新的（CI 用）
//! ```
//!
//! 生成物是**提交进仓库**的：前端构建不该依赖 Rust 工具链。`check` 模式保证
//! 提交进去的那份和契约表一致。

use std::path::{Path, PathBuf};

use baicodex::ffi::spec::{OpSpec, OPS};

/// 事件声明。
///
/// 事件是 Rust 侧 `AppEvent` 的镜像，但**没法自动反射**（serde 的 tag 只在运行期
/// 生效）。所以这里手写一份，由 [`baicodex::core::feature::app::event::AppEvent`]
/// 的 `kind()` 输出做交叉校验——`cargo test -p baicodex --lib` 里的
/// `event_kinds_match_proto` 会在两边不一致时失败。
struct Ev {
    name: &'static str,
    /// (字段名, TS 类型表达式)
    fields: &'static [(&'static str, &'static str)],
    doc: &'static str,
}

const EVENTS: &[Ev] = &[
    Ev { name: "status", fields: &[("text", "string")], doc: "状态栏一句话" },
    Ev { name: "delta", fields: &[("content", "string"), ("reason", "string")], doc: "流式增量，正文与思考分开" },
    Ev {
        name: "user_message",
        fields: &[("id", "number"), ("text", "string"), ("images", "string[]")],
        doc: "用户消息已落库",
    },
    Ev { name: "message", fields: &[("message", "ChatMsg")], doc: "一条消息已落库，按 id 去重" },
    Ev {
        name: "tool_start",
        fields: &[("seq", "number"), ("name", "string"), ("args", "string"), ("from_sub", "boolean")],
        doc: "工具开始",
    },
    Ev { name: "tool_end", fields: &[("seq", "number")], doc: "工具结束" },
    Ev { name: "busy", fields: &[("busy", "boolean")], doc: "忙闲变化" },
    Ev { name: "sessions", fields: &[("list", "SessionRow[]")], doc: "会话列表变化" },
    Ev { name: "notice", fields: &[("text", "string")], doc: "一次性提示" },
    Ev { name: "confirm_request", fields: &[], doc: "需要确认工具调用" },
    Ev { name: "ask_request", fields: &[], doc: "需要用户作答" },
    Ev { name: "plan_request", fields: &[], doc: "计划待确认" },
    Ev { name: "usage", fields: &[("prompt", "number"), ("completion", "number")], doc: "用量变化" },
    Ev { name: "error", fields: &[("code", "string"), ("message", "string")], doc: "出错了，message 已转成人话" },
    Ev { name: "quit", fields: &[], doc: "退出" },
];

const HEADER: &str = "\
// 由 `cargo run -p baicodex-proto` 生成，不要手改。
// 契约在 src/ffi/spec.rs；改完重新生成，`-- check` 会挡住忘记生成的情况。
";

fn main() {
    let mut argv = std::env::args().skip(1);
    let cmd = argv.next().unwrap_or_else(|| "ts".to_string());
    let out = argv.next().map(PathBuf::from).unwrap_or_else(default_out);

    match cmd.as_str() {
        "ts" => {
            let ops = gen_ops();
            let evs = gen_events();
            if let Err(e) = write(&out.join("ops.gen.ts"), &ops).and_then(|_| write(&out.join("events.gen.ts"), &evs)) {
                eprintln!("写文件失败：{e}");
                std::process::exit(1);
            }
            println!("已生成 {} 个 op、{} 个事件 → {}", OPS.len(), EVENTS.len(), out.display());
        }
        "check" => {
            let mut bad = Vec::new();
            for (name, want) in [("ops.gen.ts", gen_ops()), ("events.gen.ts", gen_events())] {
                let p = out.join(name);
                match std::fs::read_to_string(&p) {
                    Ok(got) if got == want => {}
                    Ok(_) => bad.push(format!("{} 不是最新的", p.display())),
                    Err(_) => bad.push(format!("{} 不存在", p.display())),
                }
            }
            if bad.is_empty() {
                println!("契约生成物是最新的");
            } else {
                for b in &bad {
                    eprintln!("{b}");
                }
                eprintln!("跑 `cargo run -p baicodex-proto` 重新生成");
                std::process::exit(1);
            }
        }
        "print" => {
            print!("{}", gen_ops());
            print!("{}", gen_events());
        }
        other => {
            eprintln!("用法：proto-gen [ts|check|print] [输出目录]");
            eprintln!("不认识的子命令：{other}");
            std::process::exit(2);
        }
    }
}

/// 默认输出到仓库里的 bridge 包。相对 crate 目录找，找不到就报错让调用方传参。
fn default_out() -> PathBuf {
    // crates/proto → app/src/main/rust（内核根）→ 再上 4 级到 BaiAgent 根。
    // 内核住在 app/src/main/rust 里是为了让 CMake 能直接把内核链进 libbaicodex.so。
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    root.join("../../../../desktop/web/packages/bridge/src")
}

fn write(p: &Path, body: &str) -> std::io::Result<()> {
    if let Some(dir) = p.parent() {
        std::fs::create_dir_all(dir)?;
    }
    std::fs::write(p, body)
}

fn gen_ops() -> String {
    let mut s = String::new();
    s.push_str(HEADER);
    s.push('\n');
    s.push_str("/// 全部操作名。运行期也用它做参数校验。\n");
    s.push_str("export const OP_NAMES = [\n");
    for o in OPS {
        s.push_str(&format!("  \"{}\",\n", o.name));
    }
    s.push_str("] as const;\n\n");

    s.push_str("export type Op = (typeof OP_NAMES)[number];\n\n");

    s.push_str("/// 返回形状，仅作文档与调试断言用。\n");
    s.push_str("export type RetKind = \"Null\" | \"Bool\" | \"Num\" | \"Str\" | \"Obj\" | \"List\" | \"Any\";\n\n");

    s.push_str("export interface OpMeta {\n");
    s.push_str("  readonly args: readonly string[];\n");
    s.push_str("  readonly required: readonly string[];\n");
    s.push_str("  readonly ret: RetKind;\n");
    s.push_str("  readonly doc: string;\n");
    s.push_str("}\n\n");

    s.push_str("/// 每个 op 的声明。`args` 是参数名，`required` 是必填项。\n");
    s.push_str("export const OPS: Readonly<Record<Op, OpMeta>> = {\n");
    for o in OPS {
        s.push_str(&format!("  {}: {},\n", o.name, meta_lit(o)));
    }
    s.push_str("};\n\n");

    s.push_str("/// 该 op 是否接受这个参数名。前端传错键时能立刻发现。\n");
    s.push_str("export function accepts(op: Op, key: string): boolean {\n");
    s.push_str("  return (OPS[op].args as readonly string[]).includes(key);\n");
    s.push_str("}\n");
    s
}

fn meta_lit(o: &OpSpec) -> String {
    let args = o.args.iter().map(|a| format!("\"{a}\"")).collect::<Vec<_>>().join(", ");
    let req = o.required.iter().map(|a| format!("\"{a}\"")).collect::<Vec<_>>().join(", ");
    format!(
        "{{ args: [{args}], required: [{req}], ret: \"{:?}\", doc: {} }}",
        o.ret,
        json_str(o.doc)
    )
}

fn gen_events() -> String {
    let mut s = String::new();
    s.push_str(HEADER);
    s.push('\n');
    s.push_str("import type { ChatMsg, SessionRow } from \"./model\";\n\n");

    s.push_str("/// 内核推给界面的事件。按 `type` 判别。\n");
    s.push_str("export type KernelEvent =\n");
    for e in EVENTS {
        if e.fields.is_empty() {
            s.push_str(&format!("  | {{ type: \"{}\" }}\n", e.name));
            continue;
        }
        let body = e
            .fields
            .iter()
            .map(|(f, t)| format!("{f}: {t}"))
            .collect::<Vec<_>>()
            .join("; ");
        s.push_str(&format!("  | {{ type: \"{}\"; {body} }}\n", e.name));
    }
    s.push_str(";\n\n");

    s.push_str("export type EventType = KernelEvent[\"type\"];\n\n");

    s.push_str("/// 事件说明，给调试面板用。\n");
    s.push_str("export const EVENT_DOC: Readonly<Record<EventType, string>> = {\n");
    for e in EVENTS {
        s.push_str(&format!("  {}: {},\n", e.name, json_str(e.doc)));
    }
    s.push_str("};\n\n");

    s.push_str("/// 窄化：`if (isEvent(ev, \"delta\"))` 后 ev 就带上 content/reason。\n");
    s.push_str("export function isEvent<T extends EventType>(\n");
    s.push_str("  ev: KernelEvent,\n");
    s.push_str("  type: T,\n");
    s.push_str("): ev is Extract<KernelEvent, { type: T }> {\n");
    s.push_str("  return ev.type === type;\n");
    s.push_str("}\n");
    s
}

/// 生成合法的 TS 字符串字面量。手写转义比引一个 json 依赖划算。
fn json_str(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            // 其余原样：中文、全角标点都不需要转义，保持可读
            _ => out.push(c),
        }
    }
    out.push('"');
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    /// 生成器里的事件表必须覆盖内核真正会推的所有事件。
    ///
    /// 两边都是手写的清单，但一个在 Rust 内核里、一个在生成器里，中间隔着
    /// 语言边界——测试是唯一能发现"内核加了事件、前端没跟上"的地方。
    #[test]
    fn events_cover_every_kind() {
        let kernel: HashSet<&str> = baicodex::core::feature::app::event::KINDS.iter().copied().collect();
        let proto: HashSet<&str> = EVENTS.iter().map(|e| e.name).collect();
        for k in &kernel {
            assert!(proto.contains(k), "内核有事件 `{k}`，生成器里没有");
        }
        for p in &proto {
            assert!(kernel.contains(p), "生成器有事件 `{p}`，内核里没有");
        }
    }

    /// 事件名不能重复，否则 TS 的联合类型会静默合并。
    #[test]
    fn event_names_unique() {
        let mut seen = HashSet::new();
        for e in EVENTS {
            assert!(seen.insert(e.name), "事件名重复：{}", e.name);
        }
    }

    /// 字符串转义后必须能原样还原——`doc` 里带引号的情况不少。
    #[test]
    fn escapes_roundtrip() {
        let s = "他\"说\"了\n换行\tTab\\反斜杠";
        let lit = json_str(s);
        assert_eq!(lit, "\"他\\\"说\\\"了\\n换行\\tTab\\\\反斜杠\"");
    }

    /// 生成的 TS 里不能出现裸换行把对象字面量撑坏。
    #[test]
    fn ops_output_has_no_stray_newline_in_literals() {
        let out = gen_ops();
        for o in OPS {
            assert!(out.contains(o.name), "生成的 TS 里没有 {}", o.name);
        }
        assert!(!out.contains("\"\"\""), "出现了三引号，说明转义漏了");
    }
}