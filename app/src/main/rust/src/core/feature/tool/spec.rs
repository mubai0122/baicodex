//! 工具定义（OpenAI `tools` 数组）。
//!
//! 每个定义就是一段 JSON Schema：名字、一句话说明、参数表。模型靠它决定调哪个工具、
//! 传什么参数。写法上有几条硬规矩：
//!
//! 1. **说明里写"什么时候用"，不只写"是什么"**。模型不缺常识，缺的是边界。
//! 2. **参数说明里写格式与例子**。`path` 要写"绝对路径"，否则模型会给相对路径。
//! 3. **能一句话说清就别写三段**。工具描述会占提示词预算，几十个工具加起来很可观。
//! 4. **名字一旦发布不能改**。技能包、用户习惯、历史记录都按名字引用。
//!
//! 定义顺序按"常用程度"排：文件、命令、联网在前面，SSH、工作流在后面。
//! 模型对前面的工具注意力更高，这个顺序是有意的。

use serde_json::{json, Value};

use super::registry::*;
use crate::core::feature::app::App;
use crate::exec::linux;
use crate::net::search;

pub fn definitions(app: &App) -> Value {
    let cfg = app.cfg();
    let mut arr: Vec<Value> = vec![
        fjson(READ, "读取文件内容；路径是目录时返回目录列表。", req(&[("path", "绝对路径")])),
        fjson(
            WRITE,
            "写入文本文件（覆盖写，父目录自动创建）。",
            req(&[("path", "绝对路径"), ("content", "完整文本内容")]),
        ),
    ];
    arr.push(fjson(
        EDIT,
        "局部修改文件：把 old 这段原文换成 new。old 按行匹配、缩进和行尾空白自动忽略，\
         但其余字符必须与文件里完全一致；命中多处会报错，需要带更多上下文。\
         new 每行的缩进会自动对齐到原位置的缩进。改几行就用它，别整篇 write_file。",
        req(&[
            ("path", "绝对路径"),
            ("old", "文件里现在这几行的原文，原样抄，可以只抄正文不管缩进"),
            ("new", "替换后的新代码，缩进不用自己补"),
        ]),
    ));
    arr.push(fjson(DELETE, "删除文件或目录（目录递归删除）。", req(&[("path", "绝对路径")])));
    arr.push(fjson(MAKE_DIR, "创建目录（递归建父级），已存在不算失败。", req(&[("path", "绝对路径")])));
    arr.push(fjson(
        SHELL,
        "执行 shell 命令，返回 stdout 与 stderr 合并后的输出。解压、安装、逆向等都用它拼命令完成。",
        req(&[("command", "要执行的完整 shell 命令")]),
    ));

    if linux::installed() {
        arr.push(fjson(
            LINUX,
            "在自带的 Linux 环境里执行命令，返回输出。apt 装依赖、编译、跑脚本都用它；宿主机的命令仍用 exec_shell。",
            req(&[("command", "要执行的 shell 命令")]),
        ));
    }

    arr.push(fjson(
        RAIL,
        "查 12306 余票、中转组合与车次经停站。车次、时刻、余票一律用它，不要凭记忆编。\
         用户说「明天、后天」先换算成 yyyy-MM-dd。",
        obj(
            &[
                ("action", "tickets 查余票（默认）；transfer 查中转；route 查某车次经停站"),
                ("from", "出发站或城市中文名，如 杭州、上海虹桥"),
                ("to", "到达站或城市中文名"),
                ("date", "出发日期 yyyy-MM-dd，只能查今天起 15 天内"),
                ("middle", "仅 transfer：中转城市中文名"),
                ("type", "车次类型筛选，可组合：G D Z T K"),
                ("sort", "排序：start 出发时间（默认）、arrive 到达时间、duration 历时"),
                ("limit", "显示条数，默认 10，最多 40"),
                ("train_no", "仅 route：12306 内部车次号，从 tickets 结果的 no= 取"),
            ],
            &["action", "from", "to", "date"],
        ),
    ));

    if let Some(p) = search::selected(&cfg) {
        arr.push(fjson(
            WEB_SEARCH,
            &format!("联网搜索（{}）。需要最新信息、查资料、确认事实时用它，返回标题、链接与摘要。", p.label()),
            obj(
                &[
                    ("query", "搜索词，越具体越好"),
                    ("max_results", "返回条数，1-10，默认 5"),
                    ("topic", "general 或 news，默认 general"),
                    ("days", "news 时限定最近多少天"),
                    ("search_depth", "basic 或 advanced，默认 basic"),
                ],
                &["query"],
            ),
        ));
    }

    arr.push(fjson(
        MEMORY_SAVE,
        "把值得长期记住的事实、结论或用户偏好存进永久记忆。用户删掉之前，它每轮都会随系统提示生效；\
         普通寒暄、临时信息不要存。",
        req(&[("text", "要记住的一句话，写成能独立看懂的陈述句")]),
    ));
    arr.push(fjson(
        MEMORY_SEARCH,
        "在永久记忆里按关键词检索，返回命中的记忆条目（带 id）与总条数。\
         遇到像是以前告诉过你的事但当前上下文没有，先查一次，别直接问用户、也别猜。",
        obj(&[("query", "关键词，空格分隔多个"), ("limit", "最多返回几条，默认 20，上限 50")], &["query"]),
    ));
    arr.push(fjson(
        ASK,
        "向用户提问以便确认细节。一次可以问多个问题，界面会按页展示，每题列出可选项，用户还能自己填写。\
         信息不足时先问，不要靠猜。",
        obj(&[("questions", "要问的问题，1-6 个，每项含 q / options / multi / other")], &["questions"]),
    ));
    arr.push(fjson(
        SCREEN,
        "操作当前屏幕（无障碍 / uiautomator）。先用 tree 看清界面再动手：tree 给控件树与坐标，see 额外存一张截图。\
         动作：tap 点坐标、long 长按、swipe 滑动、text 往焦点输入框打字、key 系统键、launch 按包名启动 App、apps 列出可启动的 App。",
        obj(
            &[
                ("action", "see / tree / shot / tap / long / swipe / text / key / launch / apps / start / quiet"),
                ("x", "tap/long/swipe 起点 x"),
                ("y", "tap/long/swipe 起点 y"),
                ("x2", "swipe 终点 x"),
                ("y2", "swipe 终点 y"),
                ("dur", "长按 / 滑动时长毫秒"),
                ("text", "text 动作要输入的文字；start 时可给一句说明"),
                ("key", "back / home / recents / notifications / quick"),
                ("app", "launch 用包名"),
                ("all", "tree / see 时是否连空壳容器一起输出"),
            ],
            &["action"],
        ),
    ));

    arr.push(fjson(
        LIST,
        "列目录（比 read_file 读目录更清楚）：带类型、体积、修改时间。recurse=true 递归到 6 层。",
        obj(&[("path", "目录绝对路径"), ("recurse", "是否递归，默认 false"), ("limit", "最多列多少项，默认 200")], &["path"]),
    ));
    arr.push(fjson(
        INFO,
        "看文件或目录的信息：体积、修改时间、md5 与 sha256（20MB 内）。",
        req(&[("path", "绝对路径")]),
    ));
    arr.push(fjson(
        MOVE,
        "移动文件或目录；copy=true 改成复制。跨通道会自动选路。",
        obj(&[("from", "源路径"), ("to", "目标路径"), ("copy", "true=复制，默认 false=移动")], &["from", "to"]),
    ));
    arr.push(fjson(
        ZIP,
        "压缩或解压 zip：action=zip 把 paths 打包（英文逗号分隔多个路径）；action=unzip 解压到 to。",
        obj(
            &[
                ("action", "zip（默认）或 unzip"),
                ("paths", "仅 zip：要打包的路径，英文逗号分隔"),
                ("path", "仅 unzip：zip 文件路径"),
                ("out", "仅 zip：压缩包输出路径"),
                ("to", "仅 unzip：解压目标目录"),
            ],
            &["action"],
        ),
    ));
    arr.push(fjson(
        GREP,
        "在文件或整个目录里按内容搜（正则），输出 路径:行号: 内容；跳过二进制与超 2MB 的文件。",
        obj(
            &[
                ("path", "文件或目录的绝对路径"),
                ("pattern", "正则表达式"),
                ("ext", "只搜这些扩展名，英文逗号分隔"),
                ("max", "最多命中多少条，默认 60"),
                ("ignore_case", "是否忽略大小写，默认 true"),
            ],
            &["path", "pattern"],
        ),
    ));
    arr.push(fjson(
        CLIP,
        "读写剪贴板：action=get 读当前剪贴板文本；action=set 把 text 写进剪贴板。",
        obj(&[("action", "get（默认）或 set"), ("text", "仅 set：要复制的文字")], &["action"]),
    ));
    arr.push(fjson(
        OPEN,
        "打开链接或分享文本：action=url 用浏览器打开网页；action=share 拉起系统分享面板。",
        obj(&[("action", "url（默认）或 share"), ("url", "仅 url：要打开的网址"), ("text", "仅 share：要分享的文字")], &["action"]),
    ));
    arr.push(fjson(
        NOTI,
        "通知：action=list 看今天收到的通知（app 可按应用名筛）；action=send 给用户发一条本地通知。",
        obj(
            &[
                ("action", "list（默认）或 send"),
                ("app", "仅 list：只看这个应用的通知"),
                ("limit", "仅 list：最多几条，默认 30"),
                ("title", "仅 send：通知标题"),
                ("text", "仅 send：通知正文"),
            ],
            &["action"],
        ),
    ));
    arr.push(fjson(
        HTTP,
        "直接发 HTTP 请求（不用拼 curl）：返回状态码与正文前 8000 字。headers 每行一个「名: 值」。",
        obj(
            &[
                ("method", "GET（默认）/ POST / PUT / DELETE / HEAD"),
                ("url", "完整 URL"),
                ("headers", "可选：每行一个「名: 值」"),
                ("body", "可选：请求体"),
            ],
            &["url"],
        ),
    ));
    arr.push(fjson(
        APK,
        "看 APK 安装包（纯本地解析）：action=info 概览；sign 签名；permissions 权限；components 组件；\
         dex 搜 dex 字符串；files 包内文件。",
        obj(
            &[
                ("action", "info（默认）/ sign / permissions / components / dex / files"),
                ("path", "apk 文件的绝对路径"),
                ("keyword", "仅 dex：字符串过滤，支持正则"),
                ("limit", "最多多少条，默认 60"),
            ],
            &["path"],
        ),
    ));
    arr.push(fjson(
        BROWSE,
        "内置浏览器：action=open 打开网址并返回正文；text 重读正文；links 列链接；find 找文字。\
         要点开链接、要登录后的页面，用它而不是 web_search。",
        obj(
            &[
                ("action", "open（默认）/ text / links / find / click / input / scroll / back / forward / reload / url / eval"),
                ("url", "仅 open：完整网址"),
                ("text", "find 要匹配的文字；input 要填的内容"),
                ("target", "仅 input：输入框的提示文字"),
                ("script", "仅 eval：要执行的 JS"),
                ("limit", "text 最多返回多少字，默认 4000"),
            ],
            &["action"],
        ),
    ));
    arr.push(fjson(
        SUB,
        "派分智能体并行干活：一次给 2-4 条互相独立的子任务，它们同时跑，各自返回结论，最后一次给你合并后的结果。\
         适合「同时推进几件互不依赖的事」；只有一件事时自己做更快。",
        obj(
            &[
                ("tasks", "子任务列表，每项含 title / task / priority（high / normal / low）"),
                ("rounds", "每个分智能体最多几轮工具调用，默认 6，上限 12"),
            ],
            &["tasks"],
        ),
    ));
    arr.push(fjson(
        FLOW_RUN,
        "跑一条工作流：多个步骤按顺序执行，每一步交给一个分智能体做完再进下一步。\
         适合「先查、再改、再验证」这种有先后的活；互相独立的活用 subagent_run 并行更快。",
        obj(
            &[
                ("flow_id", "已保存工作流的 id（先用 flow_list 查）"),
                ("name", "临时工作流的名字"),
                ("steps", "步骤列表，每项含 title / task / retry / on_fail"),
                ("rounds", "每一步最多几轮工具调用，默认 6，上限 8"),
            ],
            &[],
        ),
    ));
    arr.push(fjson(
        FLOW_SAVE,
        "把一组步骤保存成工作流，以后能一键跑或定时跑。带上 at 就同时登记定时。传 id 就是覆盖已有的那条。",
        obj(
            &[
                ("id", "要覆盖的工作流 id，留空表示新建"),
                ("name", "工作流名字，2-12 字"),
                ("steps", "步骤列表"),
                ("at", "定时时间，格式 2026-01-01 08:30 或 08:30"),
                ("daily", "true 表示每天同一时间跑"),
                ("auto", "到点是否自动执行，默认 true"),
                ("on", "开关，默认 true"),
            ],
            &["name", "steps"],
        ),
    ));
    arr.push(fjson(FLOW_LIST, "列出已保存的工作流：id、名字、步骤数、定时、上次跑的结果。", json!({"type":"object","properties":{}})));
    arr.push(fjson(
        A2A,
        "和别的 A2A 智能体对话。action：nodes 列对端 / card 看名片 / send 派任务 / get 查进度 / cancel 取消 / tasks 列任务。",
        obj(
            &[
                ("action", "nodes / card / send / get / cancel / tasks"),
                ("node", "对端：保存过的名字或 id，或直接写 http(s)://地址"),
                ("text", "send 时派过去的任务内容"),
                ("task", "get / cancel 用的任务 id"),
                ("context", "tasks 时按上下文筛选"),
            ],
            &["action"],
        ),
    ));
    arr.push(fjson(
        SKILL_READ,
        "取某个技能的完整用法（README）。第一次用一个技能前先调它，看清参数与示例再调具体函数。",
        req(&[("name", "技能 id 或名字")]),
    ));
    arr.push(fjson(
        JS,
        "执行一段 JS（需要本机有 qjs / node）。脚本里可用 console.log 输出、bai.call(工具名, 参数) 调别的工具。",
        obj(&[("script", "要执行的 JS 源码"), ("timeout", "超时毫秒，默认 15000")], &["script"]),
    ));

    for n in SSH_ALL {
        let (desc, props) = ssh_def(n);
        arr.push(fjson(n, desc, props));
    }

    for f in app.packs.read().unwrap_or_else(|e| e.into_inner()).funcs() {
        let mut props = serde_json::Map::new();
        for (k, re) in &f.params {
            props.insert(
                k.clone(),
                json!({ "type": "string", "description": if re.is_empty() { "字符串".to_string() } else { format!("需匹配：{re}") } }),
            );
        }
        arr.push(fjson(
            &f.name,
            &format!("[{}] {}", f.skill, f.desc),
            json!({ "type": "object", "properties": props, "required": f.params.keys().cloned().collect::<Vec<_>>() }),
        ));
    }

    for t in app.mcp.read().unwrap_or_else(|e| e.into_inner()).tools.iter() {
        // 服务端给的 parameters 已经是 JSON 了，校验一下形状就行。
        let params = if t.schema.is_object() { t.schema.clone() } else { json!({"type":"object"}) };
        arr.push(fjson(&t.full, &format!("[MCP·{}] {}", t.server, t.desc), params));
    }

    // 按当前模式裁掉不可用的，再按名字去重（OpenAI 侧重名直接 400）。
    let mode = cfg.agent.mode;
    let mut seen = std::collections::HashSet::new();
    let out: Vec<Value> = arr
        .into_iter()
        .filter(|v| {
            let n = v["function"]["name"].as_str().unwrap_or("");
            !n.is_empty() && seen.insert(n.to_string()) && allowed(n, mode)
        })
        .collect();
    Value::Array(out)
}

fn ssh_def(name: &str) -> (&'static str, Value) {
    match name {
        SSH_CONNECT => (
            "连服务器（只需配一次）：保存 host / user / pass / port 并当场自检。换服务器或改密码时再调一次。",
            obj(&[("host", "服务器地址"), ("port", "端口，默认 22"), ("user", "登录用户名"), ("pass", "登录密码")], &["host", "user", "pass"]),
        ),
        SSH_LS => ("列出服务器上的目录：默认长格式。", obj(&[("path", "远端目录"), ("depth", "递归层数，默认 1")], &["path"])),
        SSH_READ => ("读服务器上的文本文件。", obj(&[("path", "远端文件路径"), ("max_lines", "只返回前 N 行，0=全文")], &["path"])),
        SSH_WRITE => ("把整段文本写到服务器上的文件。", obj(&[("path", "远端文件路径"), ("content", "要写入的文本")], &["path", "content"])),
        SSH_SUDO => ("以 root（sudo）身份在服务器上执行一条命令。", obj(&[("command", "要执行的命令"), ("timeout", "超时秒数，默认 180")], &["command"])),
        SSH_EXEC => ("以登录用户在服务器上执行一条命令。", obj(&[("command", "远端命令"), ("timeout", "超时秒数，默认 180")], &["command"])),
        SSH_GET => ("从服务器下载文件到本机。", obj(&[("path", "服务器上的路径"), ("local", "本机绝对路径")], &["path", "local"])),
        _ => ("把本机文件上传到服务器。", obj(&[("local", "本机绝对路径"), ("path", "服务器上的目标路径")], &["local", "path"])),
    }
}

fn fjson(name: &str, desc: &str, parameters: Value) -> Value {
    json!({ "type": "function", "function": { "name": name, "description": desc, "parameters": parameters } })
}

fn obj(props: &[(&str, &str)], required: &[&str]) -> Value {
    let mut p = serde_json::Map::new();
    for (k, d) in props {
        p.insert(k.to_string(), json!({ "type": "string", "description": d }));
    }
    json!({ "type": "object", "properties": p, "required": required })
}

fn req(props: &[(&str, &str)]) -> Value {
    obj(props, &props.iter().map(|(k, _)| *k).collect::<Vec<_>>())
}

