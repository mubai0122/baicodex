/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:11
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    private const val TREE_TTL = 180_000L
    /** 工作区清单缓存：进设置页就会扫一次，2 分钟内复用；点「重新扫一遍」走 force 绕过 */
    private const val SCAN_TTL = 120_000L
    /** 并发宽度：/sdcard 是 FUSE，listFiles 串行最慢，4 路同时铺开能把大目录扫盘时间砍到 1/3 左右 */
    private const val SCAN_PAR = 4
    /** 工作区列表缓存：每个工作区都要递归统计文件数与体积，60 秒内复用 */
    private const val ALL_TTL = 60_000L
    /** 单个工作区统计上限：超过就截断（大目录如内置存储根不该把设置页拖住） */
    private const val STAT_MAX_FILES = 20_000

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
        cachedScan = null
        cachedAll = null
    } //挂当前会话

    /** 本会话记着的目录，Cfg.sessionWorkspace 就是它的缓存 */
    private fun stored(): String = Cfg.sessionWorkspace.trim()

    /** 关掉「新会话自动绑定」时全局共用的内置默认工作区：没有就建一次并记住路径 */
    fun defaultWs(): String {
        val cur = Cfg.defaultWorkspace.trim().trimEnd('/')
        if (cur.isNotEmpty() && File(cur).isDirectory) return cur
        val dir = Paths.defaultWorkspace()
        Cfg.defaultWorkspace = dir.absolutePath
        return dir.absolutePath
    } //内置默认工作区

    /** 当前模式：true = 每会话各自一个目录；false = 所有会话共用一个默认工作区 */
    fun perSession(): Boolean = Cfg.autoBindWorkspace

    /** 目录已在就归一化返回；只给认领旧绑定用，这里绝不造目录 */
    private fun usable(raw: String): String? {
        val p = raw.trim().trimEnd('/')
        if (p.isEmpty()) return null
        return if (File(p).isDirectory) p else null
    } //已存在的目录

    /** 给当前会话开一个新目录：没挂会话就不建，免得建完没人落库、下次启动再建一个 */
    private fun assign(): String {
        val id = sid ?: return ""
        val dir = Paths.newDir()
        Cfg.sessionWorkspace = dir.absolutePath
        Cfg.sessionWsManual = false
        runCatching { Repo.setWorkspace(id, dir.absolutePath) }
        cachedTree = null
        cachedScan = null
        cachedAll = null
        return dir.absolutePath
    } //给当前会话新开目录

    /**
     * 当前工作区：本会话记着的目录优先，其次认领老全局绑定，最后按模式兜底。
     * 兜底只为「所有会话共用」模式服务：那边本来就没有会话级目录，读一次就该拿到默认工作区。
     * 每会话模式下这里绝不建目录——以前末尾兜底 assign()，任何 UI / 提示词顺手读一下就会多建一个空目录。
     */
    fun path(): String {
        val cur = stored()
        if (cur.isNotEmpty()) return cur
        val claimed = claimOld()
        if (claimed.isNotEmpty()) return claimed
        return if (Cfg.autoBindWorkspace) "" else defaultWs()
    } //当前工作区路径：空 = 没绑

    /** 认领老版本那个全局绑定，只做一次；返回认领到的目录，没有就是空 */
    private fun claimOld(): String {
        if (Cfg.wsMigrated) return ""
        val old = Cfg.workspace.trim().trimEnd('/')
        if (old.isEmpty()) {
            Cfg.wsMigrated = true
            return ""
        }
        //还没挂会话就先别消化掉，等会话挂上来再认领
        val id = sid ?: return ""
        Cfg.wsMigrated = true
        Cfg.workspace = ""
        val hit = usable(old) ?: return ""
        Cfg.sessionWorkspace = hit
        Cfg.sessionWsManual = true
        runCatching { Repo.setWorkspace(id, hit) }
        cachedTree = null
        cachedScan = null
        cachedAll = null
        return hit
    } //认领旧全局绑定

    /** 新会话分配工作区：每会话模式各开一个新目录；共用模式直接给内置默认工作区 */
    fun newSessionWorkspace(): String {
        //上一个会话的缓存必须清掉，否则会把它的目录沿用给新会话
        Cfg.sessionWorkspace = ""
        Cfg.sessionWsManual = false
        cachedTree = null
        cachedScan = null
        cachedAll = null
        //关掉「新会话自动绑定」= 所有会话共用一个内置默认工作区，不是空工作区
        if (!Cfg.autoBindWorkspace) return defaultWs()
        return assign()
    } //新会话分配工作区

    /** 设置项切换后清缓存，避免还拿旧目录的树 */
    fun invalidate() {
        cachedTree = null
        cachedScan = null
        cachedAll = null
    } //清目录树缓存

    /** 目录是不是自动给的（哈希工作区或内置默认工作区，false = 用户自己挑的） */
    fun isAutoDir(p: String): Boolean {
        val n = File(p.trimEnd('/')).name
        return HASH.matches(n) || n == Paths.WSDEF
    } //自动目录

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

    /** 扫一遍：文件与目录清单，跳过构建产物目录；force = 无视缓存重扫 */
    fun scan(force: Boolean = false): List<Entry> {
        if (!ensure()) return emptyList()
        val key = root().absolutePath
        val now = System.currentTimeMillis()
        //短缓存：设置页进一次扫一次，切分类/返回时不必再遍历整棵目录
        if (!force) cachedScan?.let { if (it.first == key && now - it.second < SCAN_TTL) return it.third }
        val out = ArrayList<Entry>()
        val budget = AtomicInteger(MAX_FILES)
        //每个目录的 listFiles 单独吃许可，父子之间不占着等，避免自锁
        runBlocking(Dispatchers.IO) { walkPar(root(), "", 1, out, budget, Semaphore(SCAN_PAR)) }
        //并发回来的顺序是乱的，按相对路径排一次，索引与 UI 的展示顺序才稳定
        val list = out.sortedBy { it.rel }
        cachedScan = Triple(key, now, list)
        return list
    } //扫描清单

    /** 并发遍历：父子目录各起协程，只把 listFiles 这一步卡在许可里 */
    private suspend fun walkPar(
        dir: File,
        prefix: String,
        depth: Int,
        out: MutableList<Entry>,
        budget: AtomicInteger,
        sem: Semaphore
    ) {
        if (depth > MAX_DEPTH || budget.get() <= 0) return
        //只锁住取子项这一步：递归时不持许可，父子同时等着也不会自锁
        val kids = sem.withPermit {
            runCatching { dir.listFiles()?.sortedBy { it.name.lowercase() } }.getOrNull()
        } ?: return
        val subs = ArrayList<Triple<File, String, Int>>()
        for (f in kids) {
            if (budget.get() <= 0) break
            val rel = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
            if (f.isDirectory) {
                if (f.name in SKIP) continue
                budget.decrementAndGet()
                synchronized(out) { out.add(Entry(rel, 0L, true, f.lastModified())) }
                subs.add(Triple(f, rel, depth + 1))
            } else {
                budget.decrementAndGet()
                synchronized(out) { out.add(Entry(rel, f.length(), false, f.lastModified())) }
            }
        }
        if (subs.isEmpty()) return
        //子目录并发铺开：out 是共享 List，写入处已加锁
        coroutineScope {
            subs.forEach { (d, p, dp) -> launch { walkPar(d, p, dp, out, budget, sem) } }
        }
    } //并发深度受限遍历

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
        cachedScan = null
        cachedAll = null
        return list.size
    } //写索引文件

    /** 启动时补索引：只认本会话已记下的目录，没记就不动（不能走 path() 那套） */
    fun warmIndex() {
        val p = stored()
        if (p.isEmpty()) return
        if (File(p, INDEX).isFile) return
        Thread {
            runCatching { index() }
        }.start()
    } //后台补索引

    @Volatile
    private var cachedTree: Pair<Long, String>? = null

    /** 工作区清单缓存：(目录绝对路径, 时间戳, 清单) */
    @Volatile
    private var cachedScan: Triple<String, Long, List<Entry>>? = null

    /** 工作区列表缓存：(时间戳, 列表)：统计每个工作区的文件数是重活，60 秒内复用 */
    @Volatile
    private var cachedAll: Pair<Long, List<WsInfo>>? = null

    /** 单目录统计缓存：路径 → (目录 mtime, 文件数, 体积)；目录 mtime 变了才重算 */
    private val statCache = ConcurrentHashMap<String, Triple<Long, Int, Long>>()

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
        cachedScan = null
        cachedAll = null
        index()
        return true
    } //绑定并建目录到当前会话

    /** 解绑：这个会话回到模式默认的工作区（共用模式 = 内置默认工作区，每会话模式 = 等下次分配） */
    fun unbind() {
        Cfg.workspace = ""
        Cfg.sessionWorkspace = ""
        Cfg.sessionWsManual = false
        sid?.let { s -> runCatching { Repo.setWorkspace(s, "") } }
        cachedTree = null
        cachedScan = null
        cachedAll = null
    } //解除本会话绑定，不动内置默认工作区

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

        /** 是否是内置默认工作区：不允许删除，列表里永远置顶 */
        val isDefault: Boolean
            get() = path == runCatching { Paths.defaultWorkspace().absolutePath }.getOrDefault("")
    }

    /** 老版遗留的哈希工作区：挂在数据根下、还没搬进 workspace/ 的那些 */
    fun legacyDirs(): List<File> = runCatching {
        Paths.ensure().listFiles()
            ?.filter { it.isDirectory && HASH.matches(it.name) }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }.getOrDefault(emptyList()) //老目录清单

    /** 目录里普通文件的个数与总体积：先吃缓存，再看工作区索引头，最后才真扫 */
    private fun stat(f: File): Pair<Int, Long> {
        val key = f.absolutePath
        val m = runCatching { f.lastModified() }.getOrDefault(0L)
        //目录 mtime 一变就重算：增删子项都会动它，够用来判失效
        statCache[key]?.let { c -> if (c.first == m) return c.second to c.third }
        indexStat(f)?.let { (n, b) ->
            statCache[key] = Triple(m, n, b)
            return n to b
        }
        var files = 0
        var bytes = 0L
        runCatching {
            val stack = ArrayDeque<File>()
            stack.add(f)
            var guard = 0
            //guard 限目录数、STAT_MAX_FILES 限文件数：绑了内置存储根之类的大目录也不会把设置页拖住
            while (stack.isNotEmpty() && guard++ < 4000 && files < STAT_MAX_FILES) {
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
        statCache[key] = Triple(m, files, bytes)
        return files to bytes
    } //统计目录内容

    /** 从工作区索引头部抠出文件数与体积：不解析整张 file_list，大目录能省掉一次递归 */
    private fun indexStat(f: File): Pair<Int, Long>? = runCatching {
        val idx = File(f, INDEX)
        if (!idx.isFile) {
            null
        } else {
            //索引是 toString(2) 写出来的，root/name/scanned_at/files/dirs/bytes 都在头部 1KB 内
            val head = idx.inputStream().use { ins ->
                val buf = ByteArray(1024)
                val n = ins.read(buf)
                if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
            }
            val files = Regex("\"files\"\\s*:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull()
            val bytes = Regex("\"bytes\"\\s*:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toLongOrNull()
            if (files == null || bytes == null) null else files to bytes
        }
    }.getOrNull() //索引里的统计

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
        cachedScan = null
        cachedAll = null
        return null
    } //迁移旧工作区

    /** 枚举所有工作区：workspace/ 下的新目录 + 老版本哈希目录 + 会话绑定的目录 + 全局显式绑定 */
    fun listAll(force: Boolean = false): List<WsInfo> {
        val now = System.currentTimeMillis()
        //单目录统计按 mtime 缓存在 statCache，这里只是整表缓存；force = 强制重算
        if (!force) cachedAll?.let { if (now - it.first < ALL_TTL) return it.second }
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
        val list = paths.map { p ->
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
        }.sortedWith(
            //默认工作区永远置顶，其次当前使用中，最后按文件数排
            compareByDescending<WsInfo> { it.isDefault }
                .thenByDescending { it.current }
                .thenByDescending { it.files }
        )
        cachedAll = now to list
        return list
    } //列出所有工作区

    /** 删除一个工作区目录：当前使用的、被会话绑定的、数据根本身、内置默认工作区一律拒删 */
    fun delete(c: Context, path: String): String? {
        val abs = Router.normalize(path.trim()).trimEnd('/')
        if (abs.isBlank() || abs == Paths.base().absolutePath) return c.getString(R.string.ws_del_root)
        //共用模式的内置默认工作区不被任何会话绑定，会被当成「未使用」清掉，必须显式拦下
        if (abs == Paths.defaultWorkspace().absolutePath) return c.getString(R.string.ws_del_default)
        val f = File(abs)
        if (!f.isDirectory) return c.getString(R.string.ws_del_gone)
        if (abs == this.path()) return c.getString(R.string.ws_del_inuse)
        val bind = runCatching { Repo.workspaceBindings() }.getOrDefault(emptyMap())
        if (bind.containsKey(abs)) return c.getString(R.string.ws_del_bound)
        val ok = runCatching { f.deleteRecursively() }.getOrDefault(false)
        if (!ok) return c.getString(R.string.ws_del_fail)
        statCache.remove(abs)
        cachedTree = null
        cachedScan = null
        cachedAll = null
        return null
    } //安全删除工作区

    /** 一键清理未使用：只扫没被任何会话绑定的，当前使用中的、数据根、内置默认工作区一律跳过；回调给清理进度 */
    fun purgeUnused(c: Context, onStep: (Int, Int, String) -> Unit): Triple<Int, Long, Int> {
        val def = Paths.defaultWorkspace().absolutePath
        val base = Paths.base().absolutePath
        val cur = runCatching { path() }.getOrDefault("")
        //绑定表一次取全：以前每个目录删之前都回表查一遍，几十个目录就是几十次 SQLite
        val bindAll = runCatching { Repo.workspaceBindings() }.getOrDefault(emptyMap())
        val batch = listAll(true).filter { it.unbound && !it.current && it.exists && it.path != def }
        var ok = 0
        var bytes = 0L
        var bad = 0
        batch.forEachIndexed { i, w ->
            onStep(i + 1, batch.size, w.name)
            val abs = Router.normalize(w.path.trim()).trimEnd('/')
            //就地判定，不再调 delete()：省掉它每次都重查绑定表与清全局缓存
            val err = when {
                abs.isBlank() || abs == base -> c.getString(R.string.ws_del_root)
                abs == def -> c.getString(R.string.ws_del_default)
                abs == cur -> c.getString(R.string.ws_del_inuse)
                bindAll.containsKey(abs) -> c.getString(R.string.ws_del_bound)
                !File(abs).isDirectory -> c.getString(R.string.ws_del_gone)
                !runCatching { File(abs).deleteRecursively() }.getOrDefault(false) -> c.getString(R.string.ws_del_fail)
                else -> null
            }
            if (err == null) {
                ok++
                bytes += w.bytes
                statCache.remove(abs)
            } else {
                bad++
            }
        }
        cachedTree = null
        cachedScan = null
        cachedAll = null
        return Triple(ok, bytes, bad)
    } //批量清理未使用工作区
} //工作区绑定