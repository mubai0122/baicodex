package b.ai.exec

import android.util.Base64
import b.ai.App
import b.ai.core.Route
import java.io.File

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