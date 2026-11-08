package b.ai.device.fs
import b.ai.device.sys.probe.Router

import android.content.Context

import b.ai.R
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.db.Repo
import b.ai.core.cfg.io.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 工作区绑定：模型给的相对路径一律落到绑定目录，命令默认在该目录里执行。
 * 索引写在工作区根目录的 workspace.json（相对路径 / 大小 / 时间 / 忽略规则），模型与 UI 共用。
 */
object Workspace {
    /** 索引文件名，放在工作区根目录 */
    const val INDEX = "workspace.json"

    private const val MAX_FILES = 2000
    private const val MAX_DEPTH = 6
    private const val TREE_CHARS = 4000
    private const val TREE_TTL = 30_000L

    /** 扫目录时直接跳过的重目录：构建产物与缓存，列出来只会撑爆提示词 */
    private val SKIP = setOf(
        ".git", ".gradle", "build", ".idea", "node_modules",
        "__pycache__", ".cxx", "dist", ".kotlin"
    )

    private fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** 当前会话 id：由 Agent 切会话时同步，工作区判定一律按它走 */
    @Volatile
    var sid: String? = null
        private set

    /** 切会话时挂上 id：缓存一起清掉，免得还拿上一份目录树 */
    fun attachSession(id: String?) {
        sid = id
        cachedTree = null
    } //挂当前会话

    /** 本会话记着的目录，Cfg.sessionWorkspace 就是它的缓存 */
    private fun stored(): String = Cfg.sessionWorkspace.trim()

    /** 目录能用就归一化返回，不能用返回 null */
    private fun usable(raw: String): String? {
        val p = raw.trim().trimEnd('/')
        if (p.isEmpty()) return null
        val f = File(p)
        if (f.isDirectory || runCatching { f.mkdirs() }.getOrDefault(false)) return p
        return null
    } //可用目录

    /** 给当前会话开一个新目录：记住并写回会话行，绝不复用上一个会话的目录 */
    private fun assign(): String {
        val dir = Paths.newDir()
        Cfg.sessionWorkspace = dir.absolutePath
        Cfg.sessionWsManual = false
        sid?.let { runCatching { Repo.setWorkspace(it, dir.absolutePath) } }
        cachedTree = null
        return dir.absolutePath
    } //给当前会话新开目录

    /** 当前工作区：本会话记的目录 → 老全局绑定认领一次 → 关掉自动绑定就空 → 兜底新开一个 */
    fun path(): String {
        usable(stored())?.let { return it }
        //老版本那个全局绑定只认领一次，认领完清掉，免得又变成所有会话共用一个目录
        if (!Cfg.wsMigrated) {
            Cfg.wsMigrated = true
            val old = Cfg.workspace.trim()
            Cfg.workspace = ""
            usable(old)?.let {
                Cfg.sessionWorkspace = it
                Cfg.sessionWsManual = true
                sid?.let { s -> runCatching { Repo.setWorkspace(s, it) } }
                return it
            }
        }
        //关掉「新会话自动绑定」= 空工作区：不给默认目录兜底，模型只能用绝对路径
        if (!Cfg.autoBindWorkspace) return ""
        return assign()
    } //当前工作区路径

    /** 新会话分配工作区：一律各开一个新目录，用户手动绑的目录只作用于原来那个会话 */
    fun newSessionWorkspace(): String {
        //上一个会话的缓存必须清掉，否则会把它的目录沿用给新会话
        Cfg.sessionWorkspace = ""
        Cfg.sessionWsManual = false
        cachedTree = null
        //关掉「新会话自动绑定」= 空工作区，不给默认目录兜底
        if (!Cfg.autoBindWorkspace) return ""
        return assign()
    } //新会话分配工作区

    /** 设置项切换后清缓存，避免还拿旧目录的树 */
    fun invalidate() {
        cachedTree = null
    } //清目录树缓存

    /** 目录是不是自动分配的哈希工作区（false = 用户自己挑的） */
    fun isAutoDir(p: String): Boolean = HASH.matches(File(p.trimEnd('/')).name)

    /** 是不是用户自己绑的目录（false = 自动哈希工作区） */
    fun custom(): Boolean = Cfg.sessionWsManual && stored().isNotEmpty()

    fun bound(): Boolean = path().isNotEmpty()

    fun name(): String = path().substringAfterLast('/').ifBlank { path() }

    private fun root(): File = File(path())

    fun exists(): Boolean = bound() && root().isDirectory

    fun ensure(): Boolean = bound() && runCatching {
        val f = root()
        f.isDirectory || f.mkdirs()
    }.getOrDefault(false) //保证目录在

    /** 相对路径 → 工作区内绝对路径；绝对路径原样归一化；空工作区时原样返回交给工具 */
    fun resolve(raw: String): String {
        val p = raw.trim().trim('"').trim('\'')
        if (p.startsWith("/")) return Router.normalize(p)
        val rel = p.removePrefix("./")
        val base = path()
        if (base.isEmpty()) return Router.normalize(rel)
        return Router.normalize(if (rel.isEmpty()) base else "$base/$rel")
    } //路径解析：相对路径挂到工作区

    /** 给 shell 命令加 cd 前缀：命令默认就在工作区里跑 */
    fun wrap(cmd: String): String {
        if (cmd.isBlank()) return cmd
        if (!exists()) return cmd
        return "cd " + sq(path()) + " && " + cmd
    } //命令落到工作区目录执行

    data class Entry(val rel: String, val size: Long, val dir: Boolean, val mtime: Long)

    /** 扫一遍：文件与目录清单，跳过构建产物目录 */
    fun scan(): List<Entry> {
        if (!ensure()) return emptyList()
        val out = ArrayList<Entry>()
        walk(root(), 1, "", out)
        return out
    } //扫描清单

    private fun walk(dir: File, depth: Int, prefix: String, out: MutableList<Entry>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val kids = runCatching { dir.listFiles()?.sortedBy { it.name.lowercase() } }.getOrNull() ?: return
        for (f in kids) {
            if (out.size >= MAX_FILES) return
            val rel = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
            if (f.isDirectory) {
                if (f.name in SKIP) continue
                out.add(Entry(rel, 0L, true, f.lastModified()))
                walk(f, depth + 1, rel, out)
            } else {
                out.add(Entry(rel, f.length(), false, f.lastModified()))
            }
        }
    } //深度受限遍历

    /** 重写 workspace.json，返回写入的条目数 */
    fun index(): Int {
        val list = scan()
        if (list.isEmpty()) return 0
        val files = JSONArray()
        val dirs = JSONArray()
        var bytes = 0L
        list.forEach { e ->
            if (e.dir) {
                dirs.put(e.rel)
            } else {
                files.put(JSONObject().put("p", e.rel).put("s", e.size).put("t", e.mtime / 1000))
                bytes += e.size
            }
        }
        val o = JSONObject()
            .put("root", path())
            .put("name", name())
            .put("scanned_at", System.currentTimeMillis() / 1000)
            .put("files", files.length())
            .put("dirs", dirs.length())
            .put("bytes", bytes)
            .put("skip_dirs", JSONArray(SKIP.toList()))
            .put("file_list", files)
            .put("dir_list", dirs)
        runCatching { File(path(), INDEX).writeText(o.toString(2)) }
        cachedTree = null
        return list.size
    } //写索引文件

    /** 启动时补索引：绑了工作区但没有 workspace.json 就后台写一份 */
    fun warmIndex() {
        if (!bound()) return
        if (File(path(), INDEX).isFile) return
        Thread {
            runCatching { index() }
        }.start()
    } //后台补索引

    @Volatile
    private var cachedTree: Pair<Long, String>? = null

    /** 目录树摘录，给系统提示用；30 秒内复用，避免每轮都扫盘 */
    fun tree(): String {
        if (!bound()) return ""
        val now = System.currentTimeMillis()
        cachedTree?.let { if (now - it.first < TREE_TTL) return it.second }
        val r = root()
        if (!r.isDirectory) return ""
        val sb = StringBuilder()
        walkTree(r, "", 1, sb)
        val text = if (sb.length > TREE_CHARS) sb.substring(0, TREE_CHARS) else sb.toString()
        cachedTree = now to text
        return text
    } //提示词用的目录树

    private fun walkTree(dir: File, prefix: String, depth: Int, sb: StringBuilder) {
        if (depth > 3 || sb.length > TREE_CHARS) return
        val kids = runCatching { dir.listFiles()?.sortedBy { it.name.lowercase() } }.getOrNull() ?: return
        //目录太多时先摊到磁盘上更好读，但提示词里只留前 40 项，剩下的靠 read_file 自己列
        kids.take(40).forEachIndexed { i, f ->
            if (sb.length > TREE_CHARS) return
            val last = i == kids.lastIndex || i == 39
            sb.append(prefix).append(if (last) "└─ " else "├─ ").append(f.name)
            if (f.isDirectory) {
                sb.append('/')
            } else {
                sb.append(" (").append(human(f.length())).append(')')
            }
            sb.append('\n')
            if (f.isDirectory && f.name !in SKIP) {
                walkTree(f, prefix + if (last) "   " else "│  ", depth + 1, sb)
            }
        }
    } //递归拼树

    fun human(n: Long): String = when {
        n >= 1L shl 20 -> String.format("%.1fM", n / 1048576.0)
        n >= 1L shl 10 -> "${n / 1024}K"
        else -> "${n}B"
    } //体积可读化

    /** 绑定目录到当前会话：不存在就建，成功后写回会话行并重写索引；空 / 根目录 / 外部存储根一律拒绝 */
    fun bind(raw: String): Boolean {
        val abs = Router.normalize(raw.trim()).trimEnd('/')
        if (abs.isBlank() || abs == "/sdcard" || abs == Paths.base().absolutePath) return false
        val f = File(abs)
        if (!f.isDirectory && !f.mkdirs()) return false
        Cfg.workspace = ""
        Cfg.sessionWorkspace = abs
        Cfg.sessionWsManual = true
        sid?.let { s -> runCatching { Repo.setWorkspace(s, abs) } }
        cachedTree = null
        index()
        return true
    } //绑定并建目录到当前会话

    /** 解绑：这个会话回到自动分配的工作区，目录本身保留不动 */
    fun unbind() {
        Cfg.workspace = ""
        Cfg.sessionWorkspace = ""
        Cfg.sessionWsManual = false
        sid?.let { s -> runCatching { Repo.setWorkspace(s, "") } }
        cachedTree = null
    } //解除本会话绑定

    /** 常用目录候选：设置页与绑定页的快捷胶囊；当前工作区排第一个 */
    fun quickPicks(): List<Pair<String, String>> = listOf(
        "当前工作区" to path().ifBlank { Paths.base().absolutePath },
        "数据根" to Paths.base().absolutePath,
        "下载" to Router.normalize("/sdcard/Download"),
        "文档" to Router.normalize("/sdcard/Documents")
    )

    /** 工作区里能直接用 @ 引用的文件（相对路径，最多 300 条） */
    fun filesForMention(): List<String> =
        scan().filter { !it.dir }.map { it.rel }.take(300) //@ 面板候选

    /** 数据根下的哈希目录名格式：六段四位小写 hex */
    private val HASH = Regex("^[0-9a-f]{4}(-[0-9a-f]{4}){5}$")

    /** 一条工作区信息：路径 / 文件数 / 体积 / 绑了哪些会话 / 是否当前使用中 */
    data class WsInfo(
        val path: String,
        val name: String,
        val files: Int,
        val bytes: Long,
        val sessions: List<String>,
        val current: Boolean,
        val exists: Boolean
    ) {
        val unbound: Boolean get() = sessions.isEmpty()
    }

    /** 老版遗留的哈希工作区：挂在数据根下、还没搬进 workspace/ 的那些 */
    fun legacyDirs(): List<File> = runCatching {
        Paths.ensure().listFiles()
            ?.filter { it.isDirectory && HASH.matches(it.name) }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }.getOrDefault(emptyList()) //老目录清单

    /** 目录里普通文件的个数与总体积，跳过 SKIP 重目录 */
    private fun stat(f: File): Pair<Int, Long> {
        var files = 0
        var bytes = 0L
        runCatching {
            val stack = ArrayDeque<File>()
            stack.add(f)
            var guard = 0
            while (stack.isNotEmpty() && guard++ < 4000) {
                val d = stack.removeFirst()
                d.listFiles()?.forEach { k ->
                    if (k.isDirectory) {
                        if (k.name !in SKIP) stack.add(k)
                    } else {
                        files++
                        bytes += k.length()
                    }
                }
            }
        }
        return files to bytes
    } //统计目录内容

    /**
     * 一键迁移：把老版挂在数据根下的哈希工作区搬进 workspace/，同步 Cfg 里记的路径与所有会话语录。
     * onStep 在每个工作区开始时回调（序号 / 总数 / 名字 / 文件数 / 体积），失败即返回错误文案。
     */
    fun migrate(onStep: (Int, Int, String, Int, Long) -> Unit): String? {
        val olds = legacyDirs()
        if (olds.isEmpty()) return "none"
        val root = Paths.workspaceRoot()
        for ((i, src) in olds.withIndex()) {
            val (files, bytes) = stat(src)
            onStep(i + 1, olds.size, src.name, files, bytes)
            val dst = File(root, src.name)
            if (dst.exists()) continue
            //优先原子改名；跨分区才会失败，失败再退化成搬运
            val moved = runCatching { src.renameTo(dst) }.getOrDefault(false)
            if (!moved) {
                val ok = runCatching {
                    src.copyRecursively(dst, overwrite = true)
                    src.deleteRecursively()
                }.getOrDefault(false)
                if (!ok) return "copy:${src.name}"
            }
            val nb = dst.absolutePath
            val ob = src.absolutePath
            runCatching { Repo.remapWorkspace(ob, nb) }
            if (Cfg.workspace.trim().trimEnd('/') == ob) Cfg.workspace = nb
            if (Cfg.defaultWorkspace.trim().trimEnd('/') == ob) Cfg.defaultWorkspace = nb
            if (Cfg.sessionWorkspace.trim().trimEnd('/') == ob) Cfg.sessionWorkspace = nb
        }
        cachedTree = null
        return null
    } //迁移旧工作区

    /** 枚举所有工作区：workspace/ 下的新目录 + 老版本哈希目录 + 会话绑定的目录 + 全局显式绑定 */
    fun listAll(): List<WsInfo> {
        val bind = runCatching { Repo.workspaceBindings() }.getOrDefault(emptyMap())
        val paths = LinkedHashSet<String>()
        //新老两处都扫：迁移没跑过的老目录也要能看见、能删
        runCatching {
            Paths.workspaceRoot().listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { paths.add(it.absolutePath) }
        }
        legacyDirs().forEach { paths.add(it.absolutePath) }
        bind.keys.forEach { paths.add(it) }
        val fixed = Cfg.workspace.trim().trimEnd('/')
        if (fixed.isNotEmpty()) paths.add(fixed)
        val cur = path()
        return paths.map { p ->
            val f = File(p)
            val (files, bytes) = if (f.isDirectory) stat(f) else 0 to 0L
            WsInfo(
                path = p,
                name = f.name,
                files = files,
                bytes = bytes,
                sessions = bind[p].orEmpty(),
                current = p == cur,
                exists = f.isDirectory
            )
        }.sortedWith(compareByDescending<WsInfo> { it.current }.thenByDescending { it.files })
    } //列出所有工作区

    /** 删除一个工作区目录：当前使用的、被会话绑定的、数据根本身一律拒删 */
    fun delete(c: Context, path: String): String? {
        val abs = Router.normalize(path.trim()).trimEnd('/')
        if (abs.isBlank() || abs == Paths.base().absolutePath) return c.getString(R.string.ws_del_root)
        val f = File(abs)
        if (!f.isDirectory) return c.getString(R.string.ws_del_gone)
        if (abs == this.path()) return c.getString(R.string.ws_del_inuse)
        val bind = runCatching { Repo.workspaceBindings() }.getOrDefault(emptyMap())
        if (bind.containsKey(abs)) return c.getString(R.string.ws_del_bound)
        val ok = runCatching { f.deleteRecursively() }.getOrDefault(false)
        if (!ok) return c.getString(R.string.ws_del_fail)
        cachedTree = null
        return null
    } //安全删除工作区

    /** 一键清理未使用：只扫没被任何会话绑定的，当前使用中的与数据根自动跳过；回调给清理进度 */
    fun purgeUnused(c: Context, onStep: (Int, Int, String) -> Unit): Triple<Int, Long, Int> {
        val batch = listAll().filter { it.unbound && !it.current && it.exists }
        var ok = 0
        var bytes = 0L
        var bad = 0
        batch.forEachIndexed { i, w ->
            onStep(i + 1, batch.size, w.name)
            if (delete(c, w.path) == null) {
                ok++
                bytes += w.bytes
            } else {
                bad++
            }
        }
        cachedTree = null
        return Triple(ok, bytes, bad)
    } //批量清理未使用工作区
} //工作区绑定