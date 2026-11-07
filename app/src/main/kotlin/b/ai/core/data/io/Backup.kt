package b.ai.core.data.io
import b.ai.core.data.db.Repo
import b.ai.core.cfg.io.Paths
import b.ai.core.cfg.io.Prefs
import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 整库备份：bai.db + bai_stat.db + 配置库分片 + baicode 根下的小文件 + 头像，打成一个 zip。
 * 导入不直接覆盖（运行中 SQLite 正持有库），先落到 data/compress/restore，下次进程启动时替换。
 * zip 里的条目名保持 avatars/ 不变：备份格式不跟内部目录布局一起改，老备份还能导入。
 * 配置条目三版都认：最老 bai_cfg.xml、上一版单文件 bai_cfg.json、现在 prefs/0..7.json 分片。
 */
object Backup {
    private const val RESTORE = "restore"
    private const val CFG_JSON = "bai_cfg.json"
    private const val CFG_XML = "bai_cfg.xml"
    private fun dbFile(ctx: Context) = ctx.getDatabasePath("bai.db")
    private fun statFile(ctx: Context) = ctx.getDatabasePath("bai_stat.db")
    /** 配置库目录：键散在 8 个分片里，整个目录一起备份 */
    private fun prefsDir(ctx: Context) = File(Layout.dir(ctx, Layout.PREFS), Prefs.MAIN)
    private fun restoreDir(ctx: Context) = Layout.dir(ctx, Layout.COMPRESS + "/" + RESTORE)

    /** 有没有等着换上去的备份：有就别预热配置库，否则会挡住老 XML 备份的导入 */
    fun pending(ctx: Context): Boolean = runCatching {
        restoreDir(ctx).listFiles()?.isNotEmpty() == true
    }.getOrDefault(false) //待恢复

    /** 导出：先压一次 WAL，保证拷走的库是完整状态；打出来的 zip 再整份加密，落成 .baicodex */
    fun export(ctx: Context, out: OutputStream, onLog: (String) -> Unit = {}): Boolean = runCatching {
        Repo.checkpoint()
        //配置库写盘是合并延迟的，不刷一遍拷走的可能还是半秒前的
        Prefs.flushAll()
        //先打明文 zip 到中间文件再整份加密：加密是流式写，不能回头再压
        val tmp = File(Layout.dir(ctx, Layout.COMPRESS), "export.tmp")
        ZipOutputStream(BufferedOutputStream(tmp.outputStream())).use { z ->
            fun put(name: String, f: File) {
                if (!f.isFile) return
                f.inputStream().use { ins ->
                    z.putNextEntry(ZipEntry(name))
                    ins.copyTo(z)
                }
                z.closeEntry()
                onLog("+ $name ${f.length() / 1024}K")
            }
            put("bai.db", dbFile(ctx))
            put("bai_stat.db", statFile(ctx))
            //配置库：8 个分片全收，条目名带 prefs/ 前缀，跟老备份的裸 bai_cfg.json 区分开
            prefsDir(ctx).listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedBy { it.name }?.forEach { put("prefs/" + it.name, it) }
            //baicode 根下只收文件：哈希目录是工作区，可能很大，不备份
            Paths.base().listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach {
                put("baicode/" + it.name, it)
            }
            Layout.dir(ctx, Layout.AVATAR).listFiles()?.filter { it.isFile }?.forEach {
                put("avatars/" + it.name, it)
            }
        }
        onLog("打包完成 ${tmp.length() / 1024}K")
        try {
            BackupCrypt.seal(tmp, out)
            onLog("已加密")
        } finally {
            tmp.delete()
        }
        true
    }.getOrDefault(false) //导出

    /** 导入第一步：先落本地副本，是密文就解开，再当 zip 解压；db 与配置等重启换，baicode 与头像直接覆盖 */
    fun stageImport(ctx: Context, ins: InputStream, onLog: (String) -> Unit = {}): Boolean = runCatching {
        val dir = restoreDir(ctx)
        dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        //ContentResolver 给的是单向流：先存下来，才能看文件头判断要不要解密
        val raw = File(Layout.dir(ctx, Layout.COMPRESS), "import.tmp")
        raw.outputStream().use { ins.copyTo(it) }
        val zip = if (BackupCrypt.isSealed(raw)) {
            val plain = File(Layout.dir(ctx, Layout.COMPRESS), "import.zip")
            val ok = BackupCrypt.open(raw, plain)
            raw.delete()
            if (!ok) {
                onLog("备份解不开：密钥对不上或文件已损坏")
                return false
            }
            onLog("已解密 ${plain.length() / 1024}K")
            plain
        } else raw
        var hasDb = false
        try {
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val name = e.name
                    if (e.isDirectory) continue
                    when {
                        name == "bai.db" -> {
                            File(dir, "bai.db").outputStream().use { z.copyTo(it) }
                            hasDb = true
                            onLog("+ bai.db")
                        }
                        name == "bai_stat.db" -> {
                            File(dir, "bai_stat.db").outputStream().use { z.copyTo(it) }
                            onLog("+ bai_stat.db")
                        }
                        name == CFG_JSON || name == CFG_XML -> {
                            File(dir, name).outputStream().use { z.copyTo(it) }
                            onLog("+ $name")
                        }
                        name.startsWith("prefs/") -> {
                            //分片原样落回 restore/prefs/，启动时整目录换上去
                            val f = File(dir, name)
                            f.parentFile?.mkdirs()
                            f.outputStream().use { z.copyTo(it) }
                            onLog("+ $name")
                        }
                        name.startsWith("baicode/") -> {
                            val f = File(Paths.ensure(), name.removePrefix("baicode/"))
                            f.parentFile?.mkdirs()
                            f.outputStream().use { z.copyTo(it) }
                            onLog("+ $name")
                        }
                        name.startsWith("avatars/") -> {
                            //zip 里还是 avatars/xxx，落盘落到新布局 img/avatars/
                            val f = File(Layout.dir(ctx, Layout.AVATAR), name.removePrefix("avatars/"))
                            f.parentFile?.mkdirs()
                            f.outputStream().use { z.copyTo(it) }
                            onLog("+ $name")
                        }
                    }
                    z.closeEntry()
                }
            }
        } finally {
            zip.delete()
        }
        //没有 bai.db 就不是本 App 的备份，直接判定失败
        hasDb
    }.getOrDefault(false) //导入解压

    /**
     * 进程启动时调用：把上一轮导进 restore 的库与配置换上去。
     * 必须排在 Cfg.init / Repo.init 之前，否则读到的还是旧配置。
     */
    fun applyPending(ctx: Context): Boolean {
        var changed = false
        val pdb = File(restoreDir(ctx), "bai.db")
        if (pdb.isFile) {
            val target = dbFile(ctx)
            target.parentFile?.mkdirs()
            runCatching { target.delete() }
            runCatching { pdb.copyTo(target, overwrite = true) }
            //旧库的 wal / shm 必须一起清掉，否则 SQLite 会用日志把新库覆盖回去
            runCatching { File(target.path + "-wal").delete() }
            runCatching { File(target.path + "-shm").delete() }
            runCatching { File(target.path + "-journal").delete() }
            pdb.delete()
            changed = true
        }
        val ps = File(restoreDir(ctx), "bai_stat.db")
        if (ps.isFile) {
            val target = statFile(ctx)
            target.parentFile?.mkdirs()
            runCatching { target.delete() }
            runCatching { ps.copyTo(target, overwrite = true) }
            runCatching { File(target.path + "-wal").delete() }
            runCatching { File(target.path + "-shm").delete() }
            runCatching { File(target.path + "-journal").delete() }
            ps.delete()
            changed = true
        }
        //配置：新备份是 8 个分片、整目录换；上一版是单文件 json，落到老位置让库自己拆片；更老是 xml，落回系统目录再重导
        val pd = File(restoreDir(ctx), "prefs")
        val pj = File(restoreDir(ctx), CFG_JSON)
        val px = File(restoreDir(ctx), CFG_XML)
        val pdir = prefsDir(ctx)
        if (pd.isDirectory) {
            Prefs.reset()
            runCatching { pdir.deleteRecursively() }
            runCatching { pd.copyRecursively(pdir, overwrite = true) }
            pd.deleteRecursively()
            changed = true
        } else if (pj.isFile) {
            //库只在「分片一个都没有」时才看老单文件，所以先清干净分片目录
            Prefs.reset()
            runCatching { pdir.deleteRecursively() }
            val legacy = File(Layout.dir(ctx, Layout.PREFS), Prefs.MAIN + ".json")
            legacy.parentFile?.mkdirs()
            runCatching { pj.copyTo(legacy, overwrite = true) }
            pj.delete()
            changed = true
        } else if (px.isFile) {
            val target = File(ctx.applicationInfo.dataDir + "/shared_prefs/" + CFG_XML)
            target.parentFile?.mkdirs()
            runCatching { px.copyTo(target, overwrite = true) }
            px.delete()
            //分片清掉，下次读库时从这份 XML 重新导一遍
            Prefs.reset()
            runCatching { pdir.deleteRecursively() }
            changed = true
        }
        return changed
    } //启动时替换
} //整库备份与恢复