package b.ai.agent

import b.ai.core.Apk
import b.ai.core.AgentMode
import b.ai.core.Cfg
import b.ai.core.Dsl
import b.ai.core.Mcp
import b.ai.core.Memory
import b.ai.core.Route
import b.ai.core.Search
import b.ai.core.Secret
import b.ai.core.SkillFn
import b.ai.core.SkillPacks
import b.ai.core.WorkMode
import b.ai.exec.FileOps
import b.ai.exec.Linux
import b.ai.exec.Root
import b.ai.exec.Router
import b.ai.exec.Shell
import b.ai.exec.Shiz
import b.ai.exec.Workspace
import b.ai.net.Bing
import b.ai.net.GoogleCse
import b.ai.net.Rail
import b.ai.net.Tavily
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ToolOutcome(val ok: Boolean, val text: String, val route: Route)

/** 工具定义 + 执行分发 */
object Tools {
    const val READ = "read_file"
    const val WRITE = "write_file"
    const val MAKE_DIR = "make_dir"
    const val DELETE = "delete_file"
    const val SHELL = "exec_shell"
    const val LINUX = "linux_shell"
    const val WEB_SEARCH = "web_search"
    const val RAIL = "rail_query"
    const val MEMORY = "memory_save"
    const val ASK = "ask_user"
    /** 取某个技能的完整用法（README），技能函数由技能包动态注册 */
    const val SKILL_READ = "skill_read"

    /** 1.6.0 补齐的文件与系统工具：列目录 / 取信息 / 移动复制 / 压缩解压 / 内容搜索 */
    const val LIST = "list_dir"
    const val INFO = "file_info"
    const val MOVE = "move_path"
    const val ZIP = "zip_op"
    const val GREP = "grep_files"

    /** 剪贴板 / 打开链接 / 分享 / 本地通知与今日通知 / 直发 HTTP */
    const val CLIP = "clipboard"
    const val OPEN = "open_link"
    const val NOTI = "notification"
    const val HTTP = "http_fetch"

    /** APK 逆向：签名证书 / 二进制清单 / 权限与组件 / dex 字符串 / 包内文件 */
    const val APKTOOL = "apk"

    /** 浏览器：内置 WebView 读正文、点链接、填表单，不弹外置浏览器 */
    const val BROWSE = "browser"

    /** 屏幕操作：无障碍侧读控件树 / 截图 / 点击滑动长按输入，走 A11y */
    const val SCREEN = "screen"
    /** MCP 能力的工具名前缀：mcp_服务_能力 */
    const val MCP = "mcp_"

    /**工具清单：搜索是可选的技能，只有设置里选了平台才下发；Linux工具只在装了环境后出现；MCP能力按缓存表下发 */
    val all: List<String>
        get() {
            val l = ArrayList<String>()
            l.addAll(listOf(READ, WRITE, MAKE_DIR, DELETE, SHELL, RAIL, MEMORY, ASK, SCREEN))
            l.addAll(listOf(LIST, INFO, MOVE, ZIP, GREP, CLIP, OPEN, NOTI, HTTP, APKTOOL, BROWSE))
            if (Linux.installed()) l.add(LINUX)
            if (Search.selected != null) l.add(WEB_SEARCH)
            l.add(SKILL_READ)
            SkillPacks.funcs.forEach { if (it.name !in l) l.add(it.name) }
            l.addAll(Mcp.tools.map { it.full })
            return l
        }

    /** 计划模式禁掉的工具：一切会落地改动或有长期副作用的能力 */
    private val PLAN_OFF = setOf(
        WRITE, MAKE_DIR, DELETE, MEMORY, LINUX, SCREEN, MOVE, ZIP, CLIP, OPEN, NOTI
    )

    /** 极简模式只留这五个：文件四件套 + exec_shell */
    private val MIN_SET = setOf(READ, WRITE, MAKE_DIR, DELETE, SHELL)

    /** 计划模式下 exec_shell 里一律判死的写操作片段：只读命令才放过 */
    private val PLAN_WRITE = listOf(
        "rm ", "mv ", "cp ", "touch ", "mkdir ", "rmdir ", "tee ", "chmod ", "chown ", "ln ",
        "sed -i", "truncate ", "dd ", "git commit", "git checkout", "git reset", "git add"
    )

    /** 重定向目标：> /dev/null 与 2>&1 这类只是丢弃输出，不算写文件 */
    private val REDIRECT = Regex("""\d?>>?\s*([^\s;|&]+)""")

    /** 当前模式下这个工具能不能用：下发定义和执行前都过一遍 */
    fun allowed(name: String): Boolean = when (Cfg.agentMode) {
        AgentMode.PLAN -> name !in PLAN_OFF
        AgentMode.MINIMAL -> name in MIN_SET
        else -> true
    }

    fun label(name: String): String = when (name) {
        READ -> "读取文件"
        WRITE -> "写入文件"
        MAKE_DIR -> "创建目录"
        DELETE -> "删除文件"
        SHELL -> "执行命令"
        LINUX -> "Linux 命令"
        WEB_SEARCH -> "联网搜索"
        RAIL -> "火车票查询"
        MEMORY -> "记住这件事"
        ASK -> "向用户提问"
        SKILL_READ -> "查技能用法"
        SCREEN -> "操作屏幕"
        LIST -> "列目录"
        INFO -> "看文件信息"
        MOVE -> "移动/复制"
        ZIP -> "压缩/解压"
        GREP -> "搜文件内容"
        CLIP -> "剪贴板"
        OPEN -> "打开/分享"
        NOTI -> "通知"
        HTTP -> "发 HTTP 请求"
        APKTOOL -> "看安装包"
        BROWSE -> "浏览网页"
        else -> SkillPacks.find(name)?.let { "${it.skill}·${it.name}" } ?: mcpLabel(name)
    }

    /** MCP 能力的显示名：MCP·服务名·能力名，找不到缓存就原样返回 */
    private fun mcpLabel(name: String): String {
        if (!name.startsWith(MCP)) return name
        val t = Mcp.findTool(name) ?: return name
        return "MCP·" + t.server + "·" + t.name
    } //MCP 显示名

    /** 高危标记：删除与任意命令执行；MCP 里凡是能跑命令的也按高危算 */
    fun isHighRisk(name: String): Boolean {
        if (name == DELETE || name == SHELL || name == LINUX) return true
        if (!name.startsWith(MCP)) return false
        val t = Mcp.findTool(name) ?: return true
        val n = t.name.lowercase()
        return HIGH_WORDS.any { n.contains(it) }
    } //高危判定

    private val HIGH_WORDS = listOf("run", "shell", "exec", "cmd", "command", "terminal", "install", "kill")

    internal fun ToolOutcome.governed(): ToolOutcome {
        if (!Dsl.ready) return this
        //0 = max：不限，原样放行；否则按 token 预算折算字符（1 token ≈ 1.8 字符）
        val tok = Cfg.toolBudgetTokens
        if (tok <= 0) return this
        val cap = if (tok >= Int.MAX_VALUE / 18) Int.MAX_VALUE else tok * 18 / 10
        val r = Dsl.nativeGovern(text, cap)
        if (!r.startsWith("OK\n")) return this
        return ToolOutcome(ok, r.substring(3), route)
    } //工具结果治理：超预算直接截止，只保留头部

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)

    private fun params(vararg props: Pair<String, JSONObject>): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()
        props.forEach { (k, v) ->
            properties.put(k, v)
            required.put(k)
        }
        return JSONObject().put("type", "object").put("properties", properties).put("required", required)
    }

    private fun fn(name: String, desc: String, parameters: JSONObject): JSONObject =
        JSONObject().put("type", "function").put(
            "function",
            JSONObject().put("name", name).put("description", desc).put("parameters", parameters)
        )

    fun definitions(): JSONArray {
        val arr = JSONArray()
        arr.put(fn(READ, "读取文件内容；路径是目录时返回目录列表。", params("path" to str("绝对路径"))))
        arr.put(
            fn(
                WRITE, "写入文本文件（覆盖写，父目录自动创建）。",
                params("path" to str("绝对路径"), "content" to str("完整文本内容"))
            )
        )
        arr.put(fn(DELETE, "删除文件或目录（目录递归删除）。", params("path" to str("绝对路径"))))
        arr.put(fn(MAKE_DIR, "创建目录（递归建父级），已存在不算失败。", params("path" to str("绝对路径"))))
        arr.put(
            fn(
                SHELL, "执行 shell 命令，返回 stdout 与 stderr 合并后的输出。解压、安装、逆向等都用它拼命令完成。",
                params("command" to str("要执行的完整 shell 命令"))
            )
        )
        //12306：余票 / 中转 / 经停三个动作合成一个工具，参数多但少一个工具就少一轮误选
        val railProps = JSONObject()
            .put("action", str("tickets 查余票（默认）；transfer 查中转；route 查某车次经停站"))
            .put("from", str("出发站或城市中文名，如 杭州、上海虹桥、北京"))
            .put("to", str("到达站或城市中文名"))
            .put("date", str("出发日期 yyyy-MM-dd，只能查今天起 15 天内"))
            .put("middle", str("仅 transfer：中转城市中文名，如 南京、武汉"))
            .put("type", str("车次类型筛选，可组合：G D Z T K，不填=全部"))
            .put("sort", str("排序：start 出发时间（默认）、arrive 到达时间、duration 历时"))
            .put("limit", JSONObject().put("type", "integer").put("description", "显示条数，默认 10，最多 40"))
            .put("train_no", str("仅 route：12306 内部车次号，形如 650000K52806，从 tickets 结果的 no= 取"))
        arr.put(
            fn(
                RAIL,
                "查 12306 余票、中转组合与车次经停站。车次、时刻、余票一律用它，不要凭记忆编。" +
                    "用户说「明天、后天」先换算成 yyyy-MM-dd。",
                JSONObject().put("type", "object").put("properties", railProps)
                    .put("required", JSONArray().put("action").put("from").put("to").put("date"))
            )
        )
        //内置 Linux：装好环境才下发，命令在 proot 里的 Ubuntu 跑
        if (Linux.installed()) {
            arr.put(
                fn(
                    LINUX,
                    "在应用自带的 Ubuntu 24.04（proot）里执行命令，返回输出。" +
                        "apt 装依赖、编译、跑脚本都用它；宿主 Android 的命令仍用 exec_shell。",
                    params("command" to str("要执行的 Ubuntu shell 命令"))
                )
            )
        }
        //搜索技能：只有用户选了平台才把 web_search 交给模型
        val provider = Search.selected
        if (provider != null) {
            //参数做成可选：query 必填，其余走默认值，省得模型每次都堆一堆字段
            val webProps = JSONObject()
                .put("query", str("搜索词，越具体越好"))
                .put("max_results", JSONObject().put("type", "integer").put("description", "返回条数，1-10，默认 5"))
                .put("topic", str("general 或 news，默认 general"))
                .put("days", JSONObject().put("type", "integer").put("description", "news 时限定最近多少天"))
                .put("search_depth", str("basic 或 advanced，默认 basic"))
            arr.put(
                fn(
                    WEB_SEARCH,
                    "联网搜索（${provider.label}）。需要最新信息、查资料、确认事实时用它，返回标题、链接与摘要。",
                    JSONObject().put("type", "object").put("properties", webProps)
                        .put("required", JSONArray().put("query"))
                )
            )
        }
        //永久记忆：模型自己判断哪些值得长期记住
        arr.put(
            fn(
                MEMORY,
                "把值得长期记住的事实、结论或用户偏好存进永久记忆。用户删掉之前，它每轮都会随系统提示生效；普通寒暄、临时信息不要存。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject().put(
                        "text",
                        str("要记住的一句话，写成能独立看懂的陈述句")
                    )
                ).put("required", JSONArray().put("text"))
            )
        )
        //向用户提问：模型自己决定问什么、给哪些选项，UI 分页展示，可多选 + 自定义填写
        val qItem = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("q", str("问题正文，一句话说清要选什么"))
                    .put(
                        "options",
                        JSONObject().put("type", "array").put("description", "可选项，2-4 个，短句，不要带 A/B 前缀")
                            .put("items", JSONObject().put("type", "string"))
                    )
                    .put("multi", JSONObject().put("type", "boolean").put("description", "是否可多选，默认 false"))
                    .put("other", JSONObject().put("type", "boolean").put("description", "末尾是否给「自己填写」，默认 true"))
            )
            .put("required", JSONArray().put("q").put("options"))
        arr.put(
            fn(
                ASK,
                "向用户提问以便确认细节。一次可以问多个问题，界面会按页展示（‹ 1/3 ›），每题列出可选项，" +
                    "用户还能自己填写。信息不足时先问，不要靠猜。",
                JSONObject().put("type", "object")
                    .put("properties", JSONObject().put("questions", JSONObject().put("type", "array")
                        .put("description", "要问的问题，1-6 个").put("items", qItem)))
                    .put("required", JSONArray().put("questions"))
            )
        )
        //屏幕操作：无障碍侧控制，动作合进一个工具，按 action 取参数
        arr.put(
            fn(
                SCREEN,
                "操作当前手机屏幕（无障碍）。先用 see/tree 看清界面再动手：tree 给控件树与坐标，see 额外存一张截图。" +
                    "动作：tap 点坐标、long 长按、swipe 滑动、text 往焦点输入框打字、key 系统键(back/home/recents/notifications/quick)、" +
                    "launch 按包名启动 App、apps 列出可启动的 App。没开无障碍会失败并提示用户去开。" +
                    "多步操作（连着几下点击/输入/滑动）第一步先 action=start 让顶部胶囊常驻，全部做完最后一步 action=quiet 收起，" +
                    "中间每步不用再管胶囊。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put(
                            "action",
                            str(
                                "see 控件树+截图(默认) / tree / shot / tap / long / swipe / text / key / launch / apps / " +
                                    "start 胶囊常驻 / quiet 收起胶囊"
                            )
                        )
                        .put("x", JSONObject().put("type", "integer").put("description", "tap/long/swipe 起点 x"))
                        .put("y", JSONObject().put("type", "integer").put("description", "tap/long/swipe 起点 y"))
                        .put("x2", JSONObject().put("type", "integer").put("description", "swipe 终点 x"))
                        .put("y2", JSONObject().put("type", "integer").put("description", "swipe 终点 y"))
                        .put("dur", JSONObject().put("type", "integer").put("description", "长按 / 滑动时长毫秒"))
                        .put("text", str("text 动作要输入的文字；start 时可给一句「正在做什么」，会显示在胶囊上"))
                        .put("key", str("key 动作：back / home / recents / notifications / quick"))
                        .put("app", str("launch 用包名；不确定就先 action=apps"))
                        .put(
                            "all",
                            JSONObject().put("type", "boolean").put(
                                "description",
                                "tree / see 时是否连没有文字的空壳容器一起输出，默认 false；默认读不到控件时也会自动附一份诊断输出"
                            )
                        )
                ).put("required", JSONArray().put("action"))
            )
        )
        //1.6.0 补齐的文件与系统工具：参数化动作合并到一个工具里，少一个工具就少一次误选
        arr.put(
            fn(
                LIST,
                "列目录（比 read_file 读目录更清楚）：带类型、体积、修改时间。recurse=true 递归到 6 层。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("path", str("目录绝对路径"))
                        .put("recurse", JSONObject().put("type", "boolean").put("description", "是否递归，默认 false"))
                        .put("limit", JSONObject().put("type", "integer").put("description", "最多列多少项，默认 200"))
                ).put("required", JSONArray().put("path"))
            )
        )
        arr.put(
            fn(
                INFO, "看文件或目录的信息：体积、修改时间、图片尺寸、md5 与 sha256（20MB 内）。",
                params("path" to str("绝对路径"))
            )
        )
        arr.put(
            fn(
                MOVE, "移动文件或目录；copy=true 改成复制。跨通道（root / Shizuku / 零宽）会自动选路。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("from", str("源路径"))
                        .put("to", str("目标路径"))
                        .put("copy", JSONObject().put("type", "boolean").put("description", "true=复制，默认 false=移动"))
                ).put("required", JSONArray().put("from").put("to"))
            )
        )
        arr.put(
            fn(
                ZIP, "压缩或解压 zip：action=zip 把 paths 打包（英文逗号分隔多个路径）；action=unzip 解压到 to。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("action", str("zip（默认）或 unzip"))
                        .put("paths", str("仅 zip：要打包的路径，英文逗号分隔"))
                        .put("path", str("仅 unzip：zip 文件路径"))
                        .put("out", str("仅 zip：压缩包输出路径"))
                        .put("to", str("仅 unzip：解压目标目录，留空则解到压缩包同名的文件夹"))
                ).put("required", JSONArray().put("action"))
            )
        )
        arr.put(
            fn(
                GREP, "在文件或整个目录里按内容搜（正则），输出 路径:行号: 内容；跳过二进制与超 2MB 的文件。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("path", str("文件或目录的绝对路径"))
                        .put("pattern", str("正则表达式"))
                        .put("ext", str("只搜这些扩展名，英文逗号分隔，留空=全部"))
                        .put("max", JSONObject().put("type", "integer").put("description", "最多命中多少条，默认 60"))
                        .put("ignore_case", JSONObject().put("type", "boolean").put("description", "是否忽略大小写，默认 true"))
                ).put("required", JSONArray().put("path").put("pattern"))
            )
        )
        arr.put(
            fn(
                CLIP, "读写剪贴板：action=get 读当前剪贴板文本；action=set 把 text 写进剪贴板。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("action", str("get（默认）或 set"))
                        .put("text", str("仅 set：要复制进剪贴板的文字"))
                ).put("required", JSONArray().put("action"))
            )
        )
        arr.put(
            fn(
                OPEN, "打开链接或分享文本：action=url 用浏览器打开网页；action=share 拉起系统分享面板把 text 发出去。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("action", str("url（默认）或 share"))
                        .put("url", str("仅 url：要打开的网址"))
                        .put("text", str("仅 share：要分享的文字"))
                ).put("required", JSONArray().put("action"))
            )
        )
        arr.put(
            fn(
                NOTI, "通知：action=list 看今天收到的通知（app 可按应用名/包名筛）；action=send 给用户发一条本地通知。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("action", str("list（默认）或 send"))
                        .put("app", str("仅 list：只看这个应用的通知，留空=全部"))
                        .put("limit", JSONObject().put("type", "integer").put("description", "仅 list：最多几条，默认 30"))
                        .put("title", str("仅 send：通知标题"))
                        .put("text", str("仅 send：通知正文"))
                ).put("required", JSONArray().put("action"))
            )
        )
        arr.put(
            fn(
                HTTP, "直接发 HTTP 请求（不用拼 curl）：返回状态码与正文前 8000 字。headers 每行一个「名: 值」。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("method", str("GET（默认）/ POST / PUT / DELETE / HEAD"))
                        .put("url", str("完整 URL，带 https://"))
                        .put("headers", str("可选：每行一个「名: 值」"))
                        .put("body", str("可选：请求体，JSON 或纯文本"))
                ).put("required", JSONArray().put("url"))
            )
        )
        arr.put(
            fn(
                APKTOOL,
                "看 APK 安装包（纯本地解析，不用装 apktool）：action=info 概览（包名/版本/SDK/签名指纹/权限/组件）；" +
                    "sign 签名证书详情；permissions 权限清单；components 组件清单；" +
                    "dex 搜 dex 里的字符串（keyword 可给正则）；files 包内文件。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("action", str("info（默认）/ sign / permissions / components / dex / files"))
                        .put("path", str("apk 文件的绝对路径"))
                        .put("keyword", str("仅 dex：字符串过滤，支持正则，留空=按体积列前几条"))
                        .put("limit", JSONObject().put("type", "integer").put("description", "最多多少条/项，默认 60"))
                ).put("required", JSONArray().put("path"))
            )
        )
        arr.put(
            fn(
                BROWSE,
                "内置浏览器（不弹外置 App，直接在应用里抓页面）：action=open 打开网址并返回正文；" +
                    "text 重读正文；links 列链接；find 找页面里的文字；click 按文字点链接或按钮；" +
                    "input 往输入框填内容（target 指定框的提示文字）；scroll 往下翻；back / forward / reload / url；" +
                    "eval 跑一段 JS 取结果。要查网页里才有的内容、要点开链接、要登录后的页面，用它而不是 web_search。",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject()
                        .put("action", str("open（默认）/ text / links / find / click / input / scroll / back / forward / reload / url / eval"))
                        .put("url", str("仅 open：完整网址，可省略 https://"))
                        .put("text", str("find / click 要匹配的文字；input 要填的内容；scroll 翻多少像素"))
                        .put("target", str("仅 input：输入框的提示文字或 name，留空=第一个能填的框"))
                        .put("script", str("仅 eval：要执行的 JS，返回值转成字符串"))
                        .put("limit", JSONObject().put("type", "integer").put("description", "text 最多返回多少字，默认 4000"))
                ).put("required", JSONArray().put("action"))
            )
        )
        //技能包：清单在系统提示里，每个函数直接注册成独立工具，不搞两级跳
        arr.put(
            fn(
                SKILL_READ,
                "取某个技能的完整用法（README）。第一次用一个技能前先调它，看清参数与示例再调具体函数。",
                params("name" to str("技能 id 或名字，见系统提示里的技能清单"))
            )
        )
        SkillPacks.funcs.forEach { f ->
            val props = JSONObject()
            f.params.forEach { (k, re) -> props.put(k, str(if (re.isBlank()) "字符串" else "需匹配：$re")) }
            arr.put(
                fn(
                    f.name,
                    "[" + f.skill + "] " + f.desc,
                    JSONObject().put("type", "object").put("properties", props)
                        .put("required", JSONArray(f.params.keys.toList()))
                )
            )
        }
        //MCP 能力：schema 是服务端给的 parameters 原文，原样透传给模型
        Mcp.tools.forEach { t ->
            val p = runCatching { JSONObject(t.schema) }.getOrElse { JSONObject() }
            arr.put(fn(t.full, "[MCP·" + t.server + "] " + t.desc, p))
        }
        //按当前模式裁掉不可用的工具：模型看不到定义就不会去试
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optJSONObject("function")?.optString("name").orEmpty()
            if (allowed(n)) out.put(o)
        }
        return out
    } //OpenAI tools 定义

    fun execute(name: String, args: JSONObject): ToolOutcome = when (name) {
        READ -> FileOps.read(args.optString("path")).toOutcome().governed()
        WRITE -> FileOps.write(args.optString("path"), args.optString("content")).toOutcome().governed()
        MAKE_DIR -> FileOps.mkdir(args.optString("path")).toOutcome().governed()
        DELETE -> FileOps.delete(args.optString("path")).toOutcome().governed()
        SHELL -> execShell(args.optString("command")).governed()
        LINUX -> execLinux(args.optString("command")).governed()
        WEB_SEARCH -> search(args).governed()
        RAIL -> rail(args).governed()
        MEMORY -> memorySave(args).governed()
        //ask_user 由 Agent 直接拦下弹面板，走到这里说明调用链不对
        ASK -> ToolOutcome(false, "ask_user 需要界面交互，无法在后台执行", Route.SANDBOX)
        SCREEN -> screen(args).governed()
        LIST -> FileOps.list(
            args.optString("path"), args.optBoolean("recurse", false), args.optInt("limit", 200)
        ).toOutcome().governed()
        INFO -> FileOps.info(args.optString("path")).toOutcome().governed()
        MOVE -> FileOps.move(
            args.optString("from"), args.optString("to"), args.optBoolean("copy", false)
        ).toOutcome().governed()
        ZIP -> zipOp(args).governed()
        GREP -> FileOps.grep(
            args.optString("path"), args.optString("pattern"), args.optString("ext"),
            args.optInt("max", 60), args.optBoolean("ignore_case", true)
        ).toOutcome().governed()
        CLIP -> clip(args).governed()
        OPEN -> openLink(args).governed()
        NOTI -> noti(args).governed()
        HTTP -> httpFetch(args).governed()
        APKTOOL -> apk(args).governed()
        BROWSE -> browse(args).governed()
        SKILL_READ -> skillRead(args.optString("name")).governed()
        else -> SkillPacks.find(name)?.let { skillExec(it, args) }?.governed()
            ?: mcpCall(name, args).governed()
    } //执行单个工具

    /** skill_read：返回技能用法，正文按需给，不进系统提示 */
    private fun skillRead(key: String): ToolOutcome {
        val p = SkillPacks.pack(key) ?: return ToolOutcome(false, "没有这个技能：$key", Route.SANDBOX)
        val fns = p.funcs.joinToString("\n") { f ->
            " - ${f.name}(${f.params.keys.joinToString(", ")})" +
                if (f.bad.isEmpty()) "" else " ←包有问题：${f.bad}"
        }
        val sb = StringBuilder()
        sb.append("# ").append(p.name.ifBlank { p.id }).append("（").append(p.id).append("）\n")
        if (p.desc.isNotBlank()) sb.append(p.desc).append('\n')
        if (fns.isNotBlank()) sb.append("函数：\n").append(fns).append('\n')
        if (p.dir.isNotBlank()) sb.append("目录：").append(p.dir).append('\n')
        sb.append('\n').append(p.readme.take(6000))
        return ToolOutcome(true, sb.toString().trim(), Route.SANDBOX)
    } //返回技能用法

    /** 技能函数：先过 DSL 引擎校验+插值，cmd 逐行执行，http 直接发请求 */
    private fun skillExec(f: SkillFn, args: JSONObject): ToolOutcome {
        if (f.bad.isNotEmpty()) return ToolOutcome(false, "技能包校验没过：${f.bad}", Route.SANDBOX)
        val vals = LinkedHashMap<String, String>()
        f.params.forEach { (k, _) ->
            val v = args.optString(k, "")
            if (v.isEmpty()) return ToolOutcome(false, "缺少参数 $k（${f.name}）", Route.SANDBOX)
            vals[k] = v
        }
        val (ok, out) = Dsl.render(f.body, f.kind, f.params, Secret.all(), vals)
        if (!ok) return ToolOutcome(false, "技能模板校验失败：$out", Route.SANDBOX)
        if (f.kind == "http") return httpRaw(out)
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ToolOutcome(false, "插值后没有可执行命令", Route.SANDBOX)
        val sb = StringBuilder()
        var route = Route.SANDBOX
        for (l in lines) {
            val o = execShell(l)
            route = o.route
            sb.append(o.text).append('\n')
            if (!o.ok) return ToolOutcome(false, sb.toString().trim(), route)
        }
        return ToolOutcome(true, sb.toString().trim(), route)
    } //执行技能函数

    /** http 技能：报文照约定拆成 方法 / URL / header / body，不额外引 HTTP 库 */
    private fun httpRaw(text: String): ToolOutcome {
        val nl = text.indexOf('\n')
        val first = (if (nl < 0) text else text.substring(0, nl)).trim()
        val seg = first.split(" ", limit = 2)
        if (seg.size < 2) return ToolOutcome(false, "http 报文首行不合法", Route.SANDBOX)
        val method = seg[0].trim().uppercase()
        val url = seg[1].trim()
        val headers = ArrayList<Pair<String, String>>()
        var i = if (nl < 0) text.length else nl + 1
        var bodyStart = text.length
        while (i < text.length) {
            val e = text.indexOf('\n', i)
            val row = text.substring(i, if (e < 0) text.length else e).trim()
            if (row.isEmpty()) {
                bodyStart = if (e < 0) text.length else e + 1
                break
            }
            val c = row.indexOf(':')
            if (c > 0) headers.add(row.substring(0, c).trim() to row.substring(c + 1).trim())
            i = if (e < 0) text.length else e + 1
        }
        val body = if (bodyStart < text.length) text.substring(bodyStart) else ""
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 40_000
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body.isNotEmpty() && method != "GET" && method != "HEAD") {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val txt = runCatching { stream?.bufferedReader()?.use { it.readText() }.orEmpty() }
                .getOrDefault("")
            conn.disconnect()
            ToolOutcome(code in 200..299, "HTTP $code\n" + txt.take(6000), Route.SANDBOX)
        }.getOrElse { ToolOutcome(false, "请求失败：" + (it.message ?: it.javaClass.simpleName), Route.SANDBOX) }
    } //发 http 技能请求

    /** 存一条永久记忆：重复的直接告诉模型别啰嗦 */
    private fun memorySave(args: JSONObject): ToolOutcome {
        val t = args.optString("text").trim()
        if (t.isEmpty()) return ToolOutcome(false, "memory_save 需要 text 参数", Route.SANDBOX)
        val ok = Memory.add(t, "")
        return if (ok) ToolOutcome(true, "已记住：$t", Route.SANDBOX)
        else ToolOutcome(true, "这条已经记过了：$t", Route.SANDBOX)
    } //存记忆

    /** MCP 能力调用：能力不在缓存 / 服务被删或停用都如实报错，不能静默当没这工具 */
    private fun mcpCall(full: String, args: JSONObject): ToolOutcome {
        if (!full.startsWith(MCP)) return ToolOutcome(false, "未知工具: $full", Route.SANDBOX)
        val t = Mcp.findTool(full)
            ?: return ToolOutcome(
                false,
                "MCP 能力 $full 不在本地缓存里：请到 设置 → 扩展 → MCP 服务 里点一次「刷新能力」。",
                Route.SANDBOX
            )
        val s = Mcp.serverOf(t) ?: return ToolOutcome(false, "MCP 服务已被删除：$full", Route.SANDBOX)
        if (!s.on) return ToolOutcome(false, "MCP 服务「${s.name}」已停用，本次调用没有发出去。", Route.SANDBOX)
        val text = Mcp.callTool(s, t.name, args)
        val err = Mcp.lastError
        if (err.isNotEmpty()) return ToolOutcome(false, "MCP 调用失败：$err", Route.SANDBOX)
        if (text.isBlank()) return ToolOutcome(false, "MCP 服务「${s.name}」没有返回内容", Route.SANDBOX)
        return ToolOutcome(true, text, Route.SANDBOX)
    } //调用 MCP 能力

    /** 搜索技能：按用户选的平台分发；平台表以后往里加分支即可 */
    private fun search(args: JSONObject): ToolOutcome {
        val provider = Search.selected
            ?: return ToolOutcome(
                false,
                "未启用联网搜索：请到 设置 → 扩展 → 搜索平台 里选一个平台并填好 Key。",
                Route.SANDBOX
            )
        val query = args.optString("query").trim()
        if (query.isEmpty()) return ToolOutcome(false, "搜索词为空", Route.SANDBOX)
        val n = args.optInt("max_results", 5).coerceIn(1, 10)
        val r = when (provider.id) {
            "tavily" -> Tavily.search(
                key = Cfg.searchCfg("tavily", "key"),
                query = query,
                maxResults = n,
                depth = args.optString("search_depth", "basic"),
                topic = args.optString("topic", "general"),
                days = args.optInt("days", 0).takeIf { it > 0 }
            )
            "bing" -> Bing.search(query, n)
            "google" -> GoogleCse.search(
                key = Cfg.searchCfg("google", "key"),
                cx = Cfg.searchCfg("google", "cx"),
                query = query,
                maxResults = n
            )
            //新增平台在这里加分支，同时在 core/Search.kt 的 providers 里登记
            else -> "搜索失败：平台 ${provider.id} 还没接入"
        }
        return ToolOutcome(!r.startsWith("未配置") && !r.startsWith("搜索失败"), r, Route.SANDBOX)
    } //联网搜索

    /** 12306 查询：只读网络请求，走应用身份，不需要 root / Shizuku */
    private fun rail(a: JSONObject): ToolOutcome {
        val text = runCatching {
            Rail.run(
                action = a.optString("action", "tickets"),
                from = a.optString("from"),
                to = a.optString("to"),
                date = a.optString("date"),
                middle = a.optString("middle").takeIf { it.isNotBlank() },
                trainType = a.optString("type").takeIf { it.isNotBlank() },
                sort = a.optString("sort").takeIf { it.isNotBlank() },
                limit = a.optInt("limit", 10),
                trainNo = a.optString("train_no").takeIf { it.isNotBlank() }
            )
        }.getOrElse { return ToolOutcome(false, "查询失败：${it.message}", Route.SANDBOX) }
        val ok = !FAIL_HEADS.any { text.startsWith(it) }
        return ToolOutcome(ok, text, Route.SANDBOX)
    } //火车票查询

    private val FAIL_HEADS = listOf(
        "查询失败", "找不到", "日期格式不对", "中转要指定", "没查到经停站", "第一段", "第二段"
    )

    /** 屏幕操作：全部动作走 A11y；没权限就置位让界面弹引导，而不是只回一句失败 */
    private fun screen(a: JSONObject): ToolOutcome {
        val act = a.optString("action", "see").lowercase()
        //胶囊常驻开关：不吃无障碍检查，不然 quiet 在服务挂掉时收不回来
        if (act == "start" || act == "begin") {
            val given = a.optString("text").trim()
            val t = given.ifEmpty { b.ai.App.self.getString(b.ai.R.string.ag_capsule_fmt, label(SCREEN)) }
            b.ai.exec.Capsule.begin(t)
            return ToolOutcome(
                true,
                "胶囊已常驻：「$t」。接着一步步操作，全部做完再调 screen action=quiet 收起。",
                Route.SANDBOX
            )
        }
        if (act == "quiet" || act == "stop" || act == "end") {
            b.ai.exec.Capsule.end()
            return ToolOutcome(true, "胶囊已收起。", Route.SANDBOX)
        }
        if (!b.ai.exec.A11y.ready()) {
            b.ai.exec.A11y.askPerm()
            return ToolOutcome(
                false,
                "无障碍服务没开，屏幕操作跑不通。已经给用户弹了引导弹窗，等用户开启后再试。" +
                    "现在也可以改用 exec_shell（uiautomator dump / input tap / screencap），但那需要 Shizuku 或 Root。",
                Route.SANDBOX
            )
        }
        val t = when (act) {
            "see", "shot2" -> b.ai.exec.A11y.dump(400, a.optBoolean("all", false)) + "\n截图: " + b.ai.exec.A11y.shot(shotFile())
            "tree", "dump" -> b.ai.exec.A11y.dump(400, a.optBoolean("all", false))
            "shot", "screenshot" -> "截图: " + b.ai.exec.A11y.shot(shotFile())
            "tap", "click" -> b.ai.exec.A11y.tap(a.optInt("x", -1), a.optInt("y", -1))
            "long", "longpress" -> b.ai.exec.A11y.longPress(
                a.optInt("x", -1), a.optInt("y", -1), a.optInt("dur", 800).toLong()
            )
            "swipe" -> b.ai.exec.A11y.swipe(
                a.optInt("x", -1), a.optInt("y", -1), a.optInt("x2", -1), a.optInt("y2", -1),
                a.optInt("dur", 300).toLong()
            )
            "text", "input" -> b.ai.exec.A11y.input(a.optString("text"))
            "key" -> b.ai.exec.A11y.global(a.optString("key", "back"))
            "launch", "open" -> launchApp(a.optString("app"))
            "apps", "list" -> apps(a.optString("app"))
            else -> "不支持的 action：$act（可用 see / tree / shot / tap / long / swipe / text / key / launch / apps / start / quiet）"
        }
        val bad = t.contains("未开启") || t.contains("失败") || t.contains("不支持") || t.contains("拿不到")
        return ToolOutcome(!bad, t, Route.SANDBOX)
    } //屏幕操作分发

    /** 截图落盘到私有目录 shots/，路径回给模型；勾了图片输入的模型可以据此看图 */
    private fun shotFile(): java.io.File =
        java.io.File(b.ai.App.self.filesDir, "shots/shot-" + System.currentTimeMillis() + ".jpg")

    /** 按包名或名字拉起应用 */
    private fun launchApp(key: String): String {
        val c = b.ai.App.self
        val k = key.trim()
        if (k.isEmpty()) return "缺少 app 参数：给包名（如 com.tencent.mm），不确定就先 action=apps"
        val pm = c.packageManager
        var i = if (k.contains('.')) runCatching { pm.getLaunchIntentForPackage(k) }.getOrNull() else null
        if (i == null) {
            val hit = runCatching {
                pm.getInstalledApplications(0).firstOrNull { app ->
                    val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault("")
                    label.contains(k, true) || app.packageName.contains(k, true)
                }
            }.getOrNull()
            i = hit?.let { runCatching { pm.getLaunchIntentForPackage(it.packageName) }.getOrNull() }
        }
        val intent = i ?: return "没找到这个应用：$k（用 action=apps 看能启动哪些）"
        val err = runCatching {
            c.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.exceptionOrNull()
        if (err != null) return "启动失败：${err.message}"
        return "已启动 " + (intent.component?.packageName ?: k)
    } //启动应用

    /** 列出可启动的应用：名字 → 包名 */
    private fun apps(filter: String): String {
        val pm = b.ai.App.self.packageManager
        val kw = filter.trim()
        val rows = runCatching {
            pm.getInstalledApplications(0).mapNotNull { app ->
                runCatching { pm.getLaunchIntentForPackage(app.packageName) }.getOrNull()
                    ?: return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(app).toString() }
                    .getOrDefault(app.packageName)
                if (kw.isNotBlank() && !label.contains(kw, true) && !app.packageName.contains(kw, true)) {
                    return@mapNotNull null
                }
                "$label → ${app.packageName}"
            }.sorted()
        }.getOrDefault(emptyList())
        if (rows.isEmpty()) return "没有匹配的可启动应用"
        val head = rows.take(120).joinToString("\n")
        return if (rows.size > 120) head + "\n（共 ${rows.size} 个，只列前 120）" else head
    } //列出可启动应用

    private fun FileOps.Out.toOutcome() = ToolOutcome(ok, text, route)

    /** 压缩 / 解压：action 分流，未给输出路径就按源路径推导 */
    private fun zipOp(a: JSONObject): ToolOutcome {
        val act = a.optString("action", "zip").lowercase()
        if (act == "unzip") {
            val z = a.optString("path").trim()
            if (z.isEmpty()) return ToolOutcome(false, "unzip 需要 path（zip 文件路径）", Route.SANDBOX)
            val to = a.optString("to").trim()
                .ifEmpty { z.removeSuffix(".zip").removeSuffix(".ZIP") + "_unzip" }
            return FileOps.unzip(z, to).toOutcome()
        }
        val paths = a.optString("paths").trim()
        if (paths.isEmpty()) return ToolOutcome(false, "zip 需要 paths（要打包的路径，逗号分隔）", Route.SANDBOX)
        val out = a.optString("out").trim().ifEmpty {
            paths.split(',').first().trim().trimEnd('/') + ".zip"
        }
        return FileOps.zip(paths, out).toOutcome()
    } //压缩解压

    /** 剪贴板：get 读、set 写，失败统一以「失败」开头由调用方判 ok */
    private fun clip(a: JSONObject): ToolOutcome {
        val act = a.optString("action", "get").lowercase()
        val t = if (act == "set") b.ai.exec.Device.clipSet(a.optString("text"))
        else b.ai.exec.Device.clipGet()
        return ToolOutcome(!t.startsWith("失败"), t, Route.SANDBOX)
    } //剪贴板

    /** 打开链接 / 分享：url 走浏览器，share 走系统分享面板 */
    private fun openLink(a: JSONObject): ToolOutcome {
        val act = a.optString("action", "url").lowercase()
        val t = if (act == "share") b.ai.exec.Device.shareText(a.optString("text"))
        else b.ai.exec.Device.openUrl(a.optString("url"))
        return ToolOutcome(!t.startsWith("失败"), t, Route.SANDBOX)
    } //打开或分享

    /** 通知：list 读今日通知（可按应用筛），send 发一条本地通知给用户 */
    private fun noti(a: JSONObject): ToolOutcome {
        val act = a.optString("action", "list").lowercase()
        if (act == "send") {
            val t = b.ai.exec.Device.sendNoti(a.optString("title"), a.optString("text"))
            return ToolOutcome(!t.startsWith("失败"), t, Route.SANDBOX)
        }
        return ToolOutcome(true, b.ai.exec.Device.notiList(a.optInt("limit", 30), a.optString("app")), Route.SANDBOX)
    } //通知

    /** 直发 HTTP：不依赖 curl，headers 每行一个「名: 值」，返回状态码与正文前 8000 字 */
    private fun httpFetch(a: JSONObject): ToolOutcome {
        val url = a.optString("url").trim()
        if (url.isEmpty()) return ToolOutcome(false, "缺少 url", Route.SANDBOX)
        val method = a.optString("method", "GET").trim().uppercase().ifEmpty { "GET" }
        val body = a.optString("body")
        val heads = ArrayList<Pair<String, String>>()
        a.optString("headers").lines().forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) heads.add(line.substring(0, i).trim() to line.substring(i + 1).trim())
        }
        return runCatching {
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 45_000
            conn.instanceFollowRedirects = true
            heads.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body.isNotEmpty() && method != "GET" && method != "HEAD") {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = runCatching { stream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
            val ctype = conn.contentType.orEmpty()
            conn.disconnect()
            val head = "HTTP $code" + if (ctype.isNotEmpty()) "（$ctype）" else ""
            val cut = if (text.length > 8000) text.take(8000) + "\n…已截断（共 ${text.length} 字符）" else text
            ToolOutcome(code in 200..299, "$head\n$cut", Route.SANDBOX)
        }.getOrElse {
            ToolOutcome(false, "请求失败：" + (it.message ?: it.javaClass.simpleName), Route.SANDBOX)
        }
    } //直发 HTTP

    private fun apk(a: JSONObject): ToolOutcome {
        val r = Apk.run(
            a.optString("action", "info"), a.optString("path"),
            a.optString("keyword"), a.optInt("limit", 60)
        )
        return ToolOutcome(r.ok, r.text, Route.SANDBOX)
    } //看安装包

    private fun browse(a: JSONObject): ToolOutcome {
        val t = b.ai.exec.Browser.run(
            a.optString("action", "open"), a.optString("url"), a.optString("text"),
            a.optString("target"), a.optString("script"), a.optInt("limit", 4000)
        )
        return ToolOutcome(!t.startsWith("失败"), t, Route.SANDBOX)
    } //浏览网页

    private fun execShell(command: String): ToolOutcome {
        if (command.isBlank()) return ToolOutcome(false, "命令为空", Route.SANDBOX)
        //计划模式一律不许落盘：带写操作的命令直接拒，只放过只读命令
        if (Cfg.agentMode == AgentMode.PLAN) {
            val hit = PLAN_WRITE.find { command.contains(it) }
            //重定向只拦真正落盘的目标，/dev/null 与 2>&1 放过
            val sink = REDIRECT.findAll(command)
                .map { it.groupValues[1] }
                .firstOrNull { it != "/dev/null" && it != "&1" && it != "&2" }
            val bad = hit ?: sink
            if (bad != null) {
                return ToolOutcome(
                    false,
                    "计划模式下不允许改动文件（命中「${bad.trim()}」），本次命令没有执行；先出计划，用户确认后再动手。",
                    Route.SANDBOX
                )
            }
        }
        //绑了工作区就先把 cwd 切进去：模型只写相对路径也能跑对
        val cmd = Workspace.wrap(command)
        return when (Cfg.workMode) {
            WorkMode.ROOT -> {
                val r = Root.run(cmd, timeoutMs = 120_000L)
                ToolOutcome(r.ok, "[root] exit=${r.code}\n${r.out}", Route.ROOT)
            } //root 身份：一次 su -c
            WorkMode.SHIZUKU -> {
                val r = Shiz.run(cmd, timeoutMs = 120_000L)
                ToolOutcome(r.ok, "[Shizuku] exit=${r.code}\n${r.out}", Route.SHIZUKU)
            } //shell 身份：Shizuku 用户服务
            WorkMode.NORMAL -> {
                val r = Shell.run(cmd, timeoutMs = 120_000L)
                //exec_shell 是应用身份的 sh，命令里的路径原样交给内核：零宽只作用于文件四件套，
                //命令直连 Android/data 或 obb 时失败必须说清原因，别让模型以为是零宽没开
                val lack = !r.ok && !Router.allFiles() &&
                    (command.contains("/Android/data") || command.contains("/Android/obb"))
                val extra = if (lack) {
                    "\n（exec_shell 走原路径，零宽变换只对 read_file / write_file / make_dir / delete_file 生效；" +
                        "这个目录请改用那几个工具，或在设置 → 权限 里打开「所有文件访问」）"
                } else ""
                ToolOutcome(r.ok, "[沙箱] exit=${r.code}\n${r.out}$extra", Route.SANDBOX)
            } //应用身份：sh
        }
    } //exec_shell：不自动降级，权限不足直接把错误回给模型

    private fun execLinux(command: String): ToolOutcome {
        if (command.isBlank()) return ToolOutcome(false, "命令为空", Route.SANDBOX)
        val r = Linux.run(Workspace.wrap(command))
        return ToolOutcome(r.ok, "[Ubuntu] exit=${r.code}\n${r.out}", Route.SANDBOX)
    } //linux_shell：在自带 Ubuntu 里跑，/sdcard 是同一条路径
}