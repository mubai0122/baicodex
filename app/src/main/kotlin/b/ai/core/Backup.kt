package b.ai.core

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 整库备份：bai.db + bai_stat.db + bai_cfg.xml + baicode 根下的小文件 + 头像，打成一个 zip。
 * 导入不直接覆盖（运行中 SQLite 正持有库），先落到 files/restore，下次进程启动时替换。
 */
object Backup {
    private const val RESTORE = "restore"

    private fun dbFile(ctx: Context) = ctx.getDatabasePath("bai.db")

    private fun statFile(ctx: Context) = ctx.getDatabasePath("bai_stat.db")

    private fun prefsFile(ctx: Context) = File(ctx.applicationInfo.dataDir + "/shared_prefs/bai_cfg.xml")

    private fun restoreDir(ctx: Context) = File(ctx.filesDir, RESTORE).also { runCatching { it.mkdirs() } }

    /** 导出：先压一次 WAL，保证拷走的库是完整状态 */
    fun export(ctx: Context, out: OutputStream, onLog: (String) -> Unit = {}): Boolean = runCatching {
        Repo.checkpoint()
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
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
            put("bai_cfg.xml", prefsFile(ctx))
            //baicode 根下只收文件：哈希目录是工作区，可能很大，不备份
            Paths.base().listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach {
                put("baicode/" + it.name, it)
            }
            File(ctx.filesDir, "avatars").listFiles()?.filter { it.isFile }?.forEach {
                put("avatars/" + it.name, it)
            }
        }
        onLog("打包完成")
        true
    }.getOrDefault(false) //导出

    /** 导入第一步：解压。db 与配置落到 restore 目录等重启，baicode 与头像直接覆盖 */
    fun stageImport(ctx: Context, ins: InputStream, onLog: (String) -> Unit = {}): Boolean = runCatching {
        val dir = restoreDir(ctx)
        dir.listFiles()?.forEach { it.delete() }
        var hasDb = false
        ZipInputStream(ins.buffered()).use { z ->
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
                    name == "bai_cfg.xml" -> {
                        File(dir, "bai_cfg.xml").outputStream().use { z.copyTo(it) }
                        onLog("+ bai_cfg.xml")
                    }
                    name.startsWith("baicode/") -> {
                        val f = File(Paths.ensure(), name.removePrefix("baicode/"))
                        f.parentFile?.mkdirs()
                        f.outputStream().use { z.copyTo(it) }
                        onLog("+ $name")
                    }
                    name.startsWith("avatars/") -> {
                        val f = File(ctx.filesDir, name)
                        f.parentFile?.mkdirs()
                        f.outputStream().use { z.copyTo(it) }
                        onLog("+ $name")
                    }
                }
                z.closeEntry()
            }
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
        val pp = File(restoreDir(ctx), "bai_cfg.xml")
        if (pp.isFile) {
            val target = prefsFile(ctx)
            target.parentFile?.mkdirs()
            runCatching { pp.copyTo(target, overwrite = true) }
            pp.delete()
        }
        return changed
    } //启动时替换
} //整库备份与恢复