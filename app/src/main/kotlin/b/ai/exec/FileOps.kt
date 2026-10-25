package b.ai.exec

import android.util.Base64
import b.ai.App
import b.ai.core.Route
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 文件三件套：按选路执行，结果统一带 route 标注 */
object FileOps {
    private const val MAX_READ = 200_000

    /** 单次最多读这么多字节：以前 readBytes() 会把整个文件拉进内存，大日志直接卡死/OOM */
    private const val READ_BYTES = 400_000

    data class Out(val ok: Boolean, val text: String, val route: Route)

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** 按通道执行：root 走一次 su -c，Shizuku 走用户服务，其余走应用沙箱 */
    private fun exec(route: Route, cmd: String, input: String? = null): ShellResult = when (route) {
        Route.ROOT -> Root.run(cmd)
        Route.SHIZUKU -> Shiz.run(cmd, input)
        else -> Shell.run(cmd, input)
    }

    fun read(rawPath: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        return when (route) {
            Route.SANDBOX, Route.ALL_FILES -> readJava(path, File(path), route)
            Route.ZERO_WIDTH -> readJava(path, File(Router.map(path)), route)
            Route.ROOT, Route.SHIZUKU -> {
                //一条命令同时处理目录 / 普通文件 / 不存在：以前 cat 目录直接报错，读大文件还会整个拉进内存
                val cmd = "if [ -d ${q(path)} ]; then ls -la ${q(path)}; " +
                    "elif [ -f ${q(path)} ]; then head -c $READ_BYTES ${q(path)}; " +
                    "else echo '文件不存在或不可读'; exit 3; fi"
                val r = exec(route, cmd)
                if (r.ok) Out(true, "[${route.label}] $path\n${r.out}", route)
                else Out(false, "[${route.label}] 读取失败(${r.code}): ${r.out}", route)
            }
        }
    } //读取文件

    fun write(rawPath: String, content: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        return when (route) {
            Route.SANDBOX, Route.ALL_FILES -> writeJava(path, File(path), content, route)
            Route.ZERO_WIDTH -> writeJava(path, File(Router.map(path)), content, route)
            Route.ROOT -> {
                val dir = path.substringBeforeLast('/', "/")
                val mk = exec(route, "mkdir -p ${q(dir)}")
                if (!mk.ok) return Out(false, "[root] 建目录失败(${mk.code}): ${mk.out}", route)
                //libsu 是常驻 su 会话：内容灌进它的 stdin 永远等不到 EOF（base64 -d 会一直阻塞），
                //灌进去的数据还会被后续命令当成输入吃掉，整条 root 通道就此挂死。
                //所以先由应用写私有临时文件，再让 root 拷过去，完全不碰 stdin
                val tmp = File(App.self.cacheDir, "bai-w-${System.currentTimeMillis()}-${(0..999).random()}.tmp")
                val wrote = runCatching { tmp.writeText(content) }.isSuccess
                if (!wrote) return Out(false, "[root] 临时文件写入失败: ${tmp.absolutePath}", route)
                val r = exec(route, "cp -f ${q(tmp.absolutePath)} ${q(path)}; rm -f ${q(tmp.absolutePath)}")
                if (r.ok) Out(true, "[root] 已写入 $path（${content.length} 字符）", route)
                else Out(false, "[root] 写入失败(${r.code}): ${r.out}", route)
            }
            Route.SHIZUKU -> {
                val dir = path.substringBeforeLast('/', "/")
                val mk = exec(route, "mkdir -p ${q(dir)}")
                if (!mk.ok) return Out(false, "[Shizuku] 建目录失败(${mk.code}): ${mk.out}", route)
                val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
                //Shizuku 走 Binder 传内容，单次事务上限约 1MB，base64 还会膨胀 1/3
                if (b64.length > 700_000) {
                    return Out(false, "[Shizuku] 内容太大（${content.length} 字符）：Binder 单次事务放不下，请改 Root 或写小一点", route)
                }
                val r = exec(route, "base64 -d > ${q(path)}", b64)
                if (r.ok) Out(true, "[Shizuku] 已写入 $path（${content.length} 字符）", route)
                else Out(false, "[Shizuku] 写入失败(${r.code}): ${r.out}", route)
            }
        }
    } //写入文件

    fun mkdir(rawPath: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        return when (route) {
            Route.SANDBOX, Route.ALL_FILES -> mkdirJava(path, File(path), route)
            Route.ZERO_WIDTH -> mkdirJava(path, File(Router.map(path)), route)
            Route.ROOT, Route.SHIZUKU -> {
                val r = exec(route, "mkdir -p ${q(path)}")
                if (r.ok) Out(true, "[${route.label}] 已创建目录 $path", route)
                else Out(false, "[${route.label}] 创建失败(${r.code}): ${r.out}", route)
            }
        }
    } //创建目录（递归）

    fun delete(rawPath: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        return when (route) {
            Route.SANDBOX, Route.ALL_FILES -> deleteJava(path, File(path), route)
            Route.ZERO_WIDTH -> deleteJava(path, File(Router.map(path)), route)
            Route.ROOT, Route.SHIZUKU -> {
                val r = exec(route, "rm -rf -- ${q(path)}")
                if (r.ok) Out(true, "[${route.label}] 已删除 $path", route)
                else Out(false, "[${route.label}] 删除失败(${r.code}): ${r.out}", route)
            }
        }
    } //删除文件

    /** 列目录：一层或递归，带类型 / 体积 / 修改时间；条目多时截断并告知上限 */
    fun list(rawPath: String, recursive: Boolean, limit: Int): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val cap = limit.coerceIn(1, 1500)
        //root / Shizuku 只有 shell，java.io 碰不到这些目录
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            val cmd = if (recursive) {
                "find ${q(path)} -maxdepth 6 -exec ls -ld {} \\; 2>/dev/null | head -n $cap"
            } else {
                "ls -la ${q(path)} 2>/dev/null | head -n $cap"
            }
            val r = exec(route, cmd)
            return if (r.ok) Out(true, "[${route.label}] $path\n${r.out}", route)
            else Out(false, "[${route.label}] 列目录失败(${r.code}): ${r.out}", route)
        }
        val f = real(route, path)
        if (!f.exists()) return Out(false, "[${route.label}] 不存在: $path", route)
        if (!f.isDirectory) return info(rawPath)
        val sb = StringBuilder()
        var n = 0
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        fun walk(dir: File, depth: Int) {
            val kids = dir.listFiles()?.sortedBy { it.name } ?: return
            for (k in kids) {
                if (n >= cap) return
                val rel = k.absolutePath.removePrefix(f.absolutePath).trimStart('/')
                sb.append(if (k.isDirectory) "[D] " else "[F] ").append(rel)
                    .append("  ").append(if (k.isDirectory) "-" else human(k.length()))
                    .append("  ").append(fmt.format(Date(k.lastModified()))).append('\n')
                n++
                if (recursive && k.isDirectory && depth < 6) walk(k, depth + 1)
            }
        }
        walk(f, 0)
        if (n == 0) return Out(true, "[${route.label}] 目录为空: $path", route)
        val more = if (n >= cap) "\n…已达上限 $cap 项，缩小路径或加大 limit" else ""
        return Out(true, "[${route.label}] 目录 $path（$n 项）\n$sb$more", route)
    } //列目录

    /** 文件 / 目录信息：类型、体积、修改时间、图片尺寸、20MB 内附 md5 与 sha256 */
    fun info(rawPath: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            val cmd = "ls -ld ${q(path)} 2>/dev/null; stat -c 'size=%s mtime=%y' ${q(path)} 2>/dev/null; " +
                "md5sum ${q(path)} 2>/dev/null"
            val r = exec(route, cmd)
            return if (r.ok) Out(true, "[${route.label}] $path\n${r.out}", route)
            else Out(false, "[${route.label}] 取信息失败: ${r.out}", route)
        }
        val f = real(route, path)
        if (!f.exists()) return Out(false, "[${route.label}] 不存在: $path", route)
        val sb = StringBuilder()
        sb.append("[${route.label}] ").append(path).append('\n')
        if (f.isDirectory) {
            sb.append("类型：目录，子项 ").append(f.list()?.size ?: 0).append(" 个")
            return Out(true, sb.toString(), route)
        }
        val size = f.length()
        sb.append("体积：").append(size).append(" 字节（").append(human(size)).append("）\n")
        sb.append("修改：")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(f.lastModified()))).append('\n')
        val name = f.name.lowercase()
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            val opt = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath, opt) }
            if (opt.outWidth > 0) sb.append("图片：").append(opt.outWidth).append(" × ").append(opt.outHeight).append(" px\n")
        }
        //大文件不做哈希：手机上读 100MB 会卡住整轮对话
        if (size <= 20L * 1024 * 1024) {
            sb.append("md5：").append(hash(f, "MD5")).append('\n')
            sb.append("sha256：").append(hash(f, "SHA-256"))
        } else {
            sb.append("哈希：文件超过 20MB 已跳过")
        }
        return Out(true, sb.toString().trim(), route)
    } //取文件信息

    /** 移动或复制：同通道用 java 直接做，root / Shizuku 交给 shell */
    fun move(rawFrom: String, rawTo: String, copy: Boolean): Out {
        val from = Workspace.resolve(rawFrom)
        val to = Workspace.resolve(rawTo)
        val rf = Router.route(from)
        val rt = Router.route(to)
        val route = when {
            rf == Route.ROOT || rt == Route.ROOT -> Route.ROOT
            rf == Route.SHIZUKU || rt == Route.SHIZUKU -> Route.SHIZUKU
            rf == Route.ZERO_WIDTH || rt == Route.ZERO_WIDTH -> Route.ZERO_WIDTH
            else -> rt
        }
        val word = if (copy) "复制" else "移动"
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            val cmd = if (copy) "cp -a ${q(from)} ${q(to)}" else "mv -f ${q(from)} ${q(to)}"
            val r = exec(route, cmd)
            return if (r.ok) Out(true, "[${route.label}] 已$word $from → $to", route)
            else Out(false, "[${route.label}] ${word}失败(${r.code}): ${r.out}", route)
        }
        val a = real(route, from)
        val b = real(route, to)
        if (!a.exists()) return Out(false, "[${route.label}] 源不存在: $from", route)
        val target = if (b.isDirectory) File(b, a.name) else b
        val ok = runCatching {
            target.parentFile?.mkdirs()
            if (copy) a.copyRecursively(target, overwrite = true)
            else a.renameTo(target) || (a.copyRecursively(target, overwrite = true) && a.deleteRecursively())
        }.getOrDefault(false)
        return if (ok) Out(true, "[${route.label}] 已$word $from → ${target.absolutePath}", route)
        else Out(false, "[${route.label}] ${word}失败（权限不足或目标被占用）: $to", route)
    } //移动或复制

    /** 打包成 zip：paths 用英文逗号分隔多个路径，压缩包内保留各自名字 */
    fun zip(rawPaths: String, rawOut: String): Out {
        val list = rawPaths.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) return Out(false, "没有要打包的路径", Route.SANDBOX)
        val out = Workspace.resolve(rawOut)
        val route = Router.route(out)
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            return Out(false, "[${route.label}] 这个位置不支持直接打包，改用 exec_shell 的 tar 或换到 /sdcard 下", route)
        }
        val target = real(route, out)
        var count = 0
        val ok = runCatching {
            target.parentFile?.mkdirs()
            ZipOutputStream(target.outputStream().buffered()).use { zos ->
                for (p in list) {
                    val src = real(route, Workspace.resolve(p))
                    if (!src.exists()) continue
                    val base = src.parentFile?.absolutePath.orEmpty()
                    val files = if (src.isDirectory) src.walkTopDown().filter { it.isFile } else sequenceOf(src)
                    for (f in files) {
                        val name = if (src.isDirectory) f.absolutePath.removePrefix(base).trimStart('/') else src.name
                        zos.putNextEntry(ZipEntry(name))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        count++
                    }
                }
            }
            true
        }.getOrElse { false }
        return if (ok) Out(true, "[${route.label}] 已打包 $count 个文件到 $out（${human(target.length())}）", route)
        else Out(false, "[${route.label}] 打包失败（权限不足或路径非法）: $rawOut", route)
    } //打包 zip

    /** 解压 zip：挡住 ../ 目录穿越，回一份条目清单 */
    fun unzip(rawZip: String, rawTo: String): Out {
        val src = Workspace.resolve(rawZip)
        val dstPath = Workspace.resolve(rawTo)
        val route = Router.route(dstPath)
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            return Out(false, "[${route.label}] 这个位置不支持直接解压，改用 exec_shell 或换到 /sdcard 下", route)
        }
        val zf = real(route, src)
        if (!zf.exists()) return Out(false, "[${route.label}] 压缩包不存在: $src", route)
        val dst = real(route, dstPath)
        dst.mkdirs()
        val root = dst.canonicalFile
        val names = ArrayList<String>()
        val ok = runCatching {
            ZipInputStream(zf.inputStream().buffered()).use { zis ->
                var e: ZipEntry? = zis.nextEntry
                while (e != null) {
                    val name = e.name
                    val targetFile = File(root, name).canonicalFile
                    if (!targetFile.path.startsWith(root.path + File.separator) && targetFile != root) {
                        throw IllegalStateException("压缩包里有越界路径：$name")
                    }
                    if (e.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { zis.copyTo(it) }
                        if (names.size < 50) names.add(name)
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
            true
        }.getOrElse { e ->
            return Out(false, "[${route.label}] 解压失败：${e.message}", route)
        }
        return Out(true, "[${route.label}] 已解压到 ${dst.absolutePath}\n" + names.joinToString("\n"), route)
    } //解压 zip

    /** 按内容找文件：跳过二进制与超大文件，输出 路径:行号: 内容 */
    fun grep(rawPath: String, pattern: String, ext: String, maxHits: Int, ignoreCase: Boolean): Out {
        if (pattern.isEmpty()) return Out(false, "pattern 不能为空", Route.SANDBOX)
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val cap = maxHits.coerceIn(1, 400)
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            val cmd = "grep -rn ${if (ignoreCase) "-i" else ""} --binary-files=without-match " +
                "-e ${q(pattern)} ${q(path)} 2>/dev/null | head -n $cap"
            val r = exec(route, cmd)
            return if (r.ok) Out(true, "[${route.label}] $path\n${r.out}", route)
            else Out(false, "[${route.label}] 没找到匹配（或目录不可读）: $pattern", route)
        }
        val f = real(route, path)
        if (!f.exists()) return Out(false, "[${route.label}] 不存在: $path", route)
        val exts = ext.split(',').map { it.trim().lowercase().removePrefix(".") }.filter { it.isNotEmpty() }
        val regex = runCatching {
            if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        }.getOrNull() ?: return Out(false, "正则不合法：$pattern", route)
        val sb = StringBuilder()
        var hits = 0
        var scanned = 0
        val files = if (f.isDirectory) f.walkTopDown().filter { it.isFile } else sequenceOf(f)
        for (f2 in files) {
            if (hits >= cap || scanned >= 3000) break
            if (f2.length() > 2L * 1024 * 1024) continue
            if (exts.isNotEmpty() && exts.none { f2.name.lowercase().endsWith(".$it") }) continue
            val text = runCatching { f2.readText() }.getOrNull() ?: continue
            if (text.indexOf('\u0000') >= 0) continue
            scanned++
            val lines = text.lineSequence().toList()
            for (i in lines.indices) {
                if (hits >= cap) break
                if (regex.containsMatchIn(lines[i])) {
                    sb.append(f2.absolutePath).append(':').append(i + 1).append(": ")
                        .append(lines[i].trim().take(300)).append('\n')
                    hits++
                }
            }
        }
        if (hits == 0) return Out(true, "[${route.label}] 扫描 $scanned 个文件，没有匹配: $pattern", route)
        return Out(true, "[${route.label}] $path 命中 $hits 条（扫了 $scanned 个文件）\n$sb", route)
    } //按内容搜索

    private fun real(route: Route, path: String): File =
        File(if (route == Route.ZERO_WIDTH) Router.map(path) else path)

    private fun hash(f: File, alg: String): String = runCatching {
        val md = MessageDigest.getInstance(alg)
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("读取失败")

    private fun human(n: Long): String = when {
        n >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", n / 1073741824.0)
        n >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", n / 1048576.0)
        n >= 1024 -> String.format(Locale.US, "%.1f KB", n / 1024.0)
        else -> "$n B"
    }

    private fun readJava(showPath: String, f: File, route: Route): Out {
        if (!f.exists()) return Out(false, "[${route.label}] 文件不存在: $showPath", route)
        if (f.isDirectory) {
            val names = f.list() ?: return Out(false, "[${route.label}] 目录不可读: $showPath", route)
            val head = names.take(300).joinToString("\n") { n ->
                if (File(f, n).isDirectory) "$n/" else n
            }
            val more = if (names.size > 300) "\n…共 ${names.size} 项，已截断" else ""
            return Out(true, "[${route.label}] 目录 $showPath\n$head$more", route)
        }
        val total = f.length()
        //流式只读前 READ_BYTES 字节，剩下的靠 total 告诉调用方
        val bytes = runCatching {
            f.inputStream().use { ins ->
                val buf = ByteArray(READ_BYTES)
                var n = 0
                while (n < buf.size) {
                    val r = ins.read(buf, n, buf.size - n)
                    if (r <= 0) break
                    n += r
                }
                java.util.Arrays.copyOf(buf, n)
            }
        }.getOrElse { return Out(false, "[${route.label}] 读取异常: ${it.message}", route) }
        if (bytes.isEmpty()) return Out(true, "[${route.label}] $showPath 为空文件", route)
        val isBinary = bytes.take(4096).any { it == 0.toByte() }
        if (isBinary) {
            val hex = bytes.take(64).joinToString(" ") { "%02x".format(it) }
            return Out(true, "[${route.label}] $showPath 是二进制文件，共 $total 字节\n前 64 字节: $hex", route)
        }
        var text = String(bytes)
        var tail = ""
        if (text.length > MAX_READ) {
            text = text.substring(0, MAX_READ)
            tail = "\n…已截断，仅返回前 $MAX_READ 字符（共 $total 字节）"
        } else if (total > READ_BYTES) {
            tail = "\n…只读了前 ${bytes.size} 字节（共 $total 字节），要更多请分段读"
        }
        return Out(true, "[${route.label}] $showPath\n$text$tail", route)
    } //沙箱/零宽读取

    private fun writeJava(showPath: String, f: File, content: String, route: Route): Out {
        val ok = runCatching {
            f.parentFile?.mkdirs()
            f.writeText(content)
        }.isSuccess
        return if (ok) Out(true, "[${route.label}] 已写入 $showPath（${content.length} 字符）", route)
        else Out(false, "[${route.label}] 写入失败（权限不足）: $showPath", route)
    } //沙箱/零宽写入

    private fun deleteJava(showPath: String, f: File, route: Route): Out {
        if (!f.exists()) return Out(false, "[${route.label}] 不存在: $showPath", route)
        return if (f.delete()) Out(true, "[${route.label}] 已删除 $showPath", route)
        else Out(false, "[${route.label}] 删除失败（权限不足）: $showPath", route)
    } //沙箱/零宽删除

    private fun mkdirJava(showPath: String, f: File, route: Route): Out {
        if (f.isDirectory) return Out(true, "[${route.label}] 目录已存在 $showPath", route)
        val ok = runCatching { f.mkdirs() }.getOrDefault(false)
        return if (ok) Out(true, "[${route.label}] 已创建目录 $showPath", route)
        else Out(false, "[${route.label}] 创建失败（权限不足或路径被占用）: $showPath", route)
    } //沙箱/零宽建目录
}