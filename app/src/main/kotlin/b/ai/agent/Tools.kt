package b.ai.agent

import b.ai.core.Cfg
import b.ai.core.Mcp
import b.ai.core.Route
import b.ai.core.Search
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
    /** MCP 能力的工具名前缀：mcp_服务_能力 */
    const val MCP = "mcp_"

    /**工具清单：搜索是可选的技能，只有设置里选了平台才下发；Linux工具只在装了环境后出现；MCP能力按缓存表下发 */
    val all: List<String>
        get() {
            val l = ArrayList<String>()
            l.addAll(listOf(READ, WRITE, MAKE_DIR, DELETE, SHELL, RAIL))
            if (Linux.installed()) l.add(LINUX)
            if (Search.selected != null) l.add(WEB_SEARCH)
            l.addAll(Mcp.tools.map { it.full })
            return l
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
        else -> mcpLabel(name)
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
        //MCP 能力：schema 是服务端给的 parameters 原文，原样透传给模型
        Mcp.tools.forEach { t ->
            val p = runCatching { JSONObject(t.schema) }.getOrElse { JSONObject() }
            arr.put(fn(t.full, "[MCP·" + t.server + "] " + t.desc, p))
        }
        return arr
    } //OpenAI tools 定义

    fun execute(name: String, args: JSONObject): ToolOutcome = when (name) {
        READ -> FileOps.read(args.optString("path")).toOutcome()
        WRITE -> FileOps.write(args.optString("path"), args.optString("content")).toOutcome()
        MAKE_DIR -> FileOps.mkdir(args.optString("path")).toOutcome()
        DELETE -> FileOps.delete(args.optString("path")).toOutcome()
        SHELL -> execShell(args.optString("command"))
        LINUX -> execLinux(args.optString("command"))
        WEB_SEARCH -> search(args)
        RAIL -> rail(args)
        else -> mcpCall(name, args)
    } //执行单个工具

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

    private fun FileOps.Out.toOutcome() = ToolOutcome(ok, text, route)

    private fun execShell(command: String): ToolOutcome {
        if (command.isBlank()) return ToolOutcome(false, "命令为空", Route.SANDBOX)
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