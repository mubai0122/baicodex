/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:06
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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

    /**
     * 导出：先压一次 WAL，保证拷走的库是完整状态；打出来的 zip 再整份加密，落成 .baicodex。
     * @param includeVault 是否把 Vault 的加密配置桶（apikeys / ssh / secrets …）一起打包。
     *   这些文件本身已是密文，包里再套一层备份加密；只能由同构建密钥的 App 解开。
     */
    fun export(
        ctx: Context,
        out: OutputStream,
        includeVault: Boolean = false,
        onLog: (String) -> Unit = {}
    ): Boolean = runCatching {
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
            //加密配置：Vault 的凭据桶原样打包（apikeys / usertokens / secrets / ssh / places / maskrules / sums）
            //它们本身就是 AES 密文，包里再套一层备份加密；只能在同构建密钥的 App 上解开
            if (includeVault) {
                var n = 0
                Layout.dir(ctx, Layout.CFG).listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".baicodex") }
                    ?.sortedBy { it.name }
                    ?.forEach { f ->
                        put("vault/" + f.name, f)
                        n++
                    }
                onLog("加密配置 $n 个")
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

    /** 明文 zip 的文件头：PK\x03\x04。老备份与外部改名成 .bin 的备份都靠它认出来 */
    private fun isZip(f: File): Boolean = runCatching {
        if (f.length() < 4) return false
        val h = ByteArray(4)
        f.inputStream().use { it.read(h) }
        h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() && h[2] == 0x03.toByte() && h[3] == 0x04.toByte()
    }.getOrDefault(false)

    /** 导入第一步：先落本地副本，是密文就解开，再当 zip 解压；db 与配置等重启换，baicode 与头像直接覆盖 */
    fun stageImport(ctx: Context, ins: InputStream, onLog: (String) -> Unit = {}): Boolean = runCatching {
        val dir = restoreDir(ctx)
        dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        //ContentResolver 给的是单向流：先存下来，才能看文件头判断要不要解密
        val raw = File(Layout.dir(ctx, Layout.COMPRESS), "import.tmp")
        raw.outputStream().use { ins.copyTo(it) }
        val zip = when {
            //加密备份：先看 bom 头，与扩展名无关（存成 .bin / .bak 也一样认）
            BackupCrypt.isSealed(raw) -> {
                val plain = File(Layout.dir(ctx, Layout.COMPRESS), "import.zip")
                val ok = BackupCrypt.open(raw, plain)
                raw.delete()
                if (!ok) {
                    onLog("备份解不开：密钥对不上或文件已损坏")
                    return false
                }
                onLog("已解密 ${plain.length() / 1024}K")
                plain
            }
            //老的明文 zip 备份
            isZip(raw) -> raw
            else -> {
                raw.delete()
                onLog("这个文件不是备份：既不是加密备份，也不是 zip 压缩包")
                return false
            }
        }
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
                        name.startsWith("vault/") -> {
                            //加密配置：先落到 restore/vault/，下次启动整批换回 Vault 目录
                            val f = File(dir, name)
                            f.parentFile?.mkdirs()
                            f.outputStream().use { z.copyTo(it) }
                            onLog("+ $name（加密配置）")
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
        //加密配置：备份里带了 vault/ 才动，没带就保留本机现有凭据（不能因为导了个老备份就把凭据清空）
        val pv = File(restoreDir(ctx), "vault")
        if (pv.isDirectory) {
            val vdir = Layout.dir(ctx, Layout.CFG)
            //先把本机现有的密文改名留档，导入出错时还能手工回退
            vdir.listFiles()?.filter { it.isFile && it.name.endsWith(".baicodex") }?.forEach { f ->
                runCatching {
                    val bak = File(f.parentFile, f.name + ".preimport")
                    if (bak.isFile) bak.delete()
                    if (!f.renameTo(bak)) f.copyTo(bak, overwrite = true)
                }
            }
            pv.listFiles()?.filter { it.isFile }?.forEach { f ->
                runCatching {
                    if (!f.renameTo(File(vdir, f.name))) f.copyTo(File(vdir, f.name), overwrite = true)
                }
            }
            pv.deleteRecursively()
            //内存里的桶若已经读过就作废，下次访问从新密文重新解密
            runCatching { b.ai.native.security.Vault.reload() }
            changed = true
        }
        return changed
    } //启动时替换
} //整库备份与恢复