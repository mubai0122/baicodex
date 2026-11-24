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
import b.ai.device.shell.root.Root
import b.ai.device.shell.local.Shell
import b.ai.device.shell.local.ShellResult
import b.ai.device.shell.root.Shiz
import b.ai.device.sys.probe.Router
import b.ai.net.api.llm.text

import android.util.Base64
import b.ai.App
import b.ai.core.model.chat.Route
import b.ai.core.model.chat.Sum
import b.ai.native.text.TextEdit
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

    /** 分页读时还要数总行数：超过这个体积就只回本页，行数标成未知（逐行数 50MB 是白烧电） */
    private const val COUNT_BYTES = 8L * 1024 * 1024

    /** 搜索结果交给总结模型时的要求：只要结论与下一步，不要复述原文 */
    private const val GREP_SUM_SYS = """你在帮独立开发者在一份代码搜索结果里定位问题。根据给出的命中列表，用中文归纳三点：
1) 命中集中在哪些文件或模块；2) 这些代码大概在做什么；3) 下一步该看哪个文件的哪几行（写成 路径:行号）。
不要复制代码原文，300 字以内，不确定就说不确定。"""

    /** 目录列表交给总结模型时的要求：讲这是什么、怎么分组、先看哪里 */
    private const val LIST_SUM_SYS = """你在帮独立开发者看一个目录。根据给出的列表说明：
1) 这是什么项目或什么东西；2) 主要文件怎么分组；3) 值得先看的是哪几个。
300 字以内，只给结论，不要罗列已有的文件名。"""

    data class Out(val ok: Boolean, val text: String, val route: Route)

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** 按通道执行：root 走一次 su -c，Shizuku 走用户服务，其余走应用沙箱 */
    private fun exec(route: Route, cmd: String, input: String? = null): ShellResult = when (route) {
        Route.ROOT -> Root.run(cmd)
        Route.SHIZUKU -> Shiz.run(cmd, input)
        else -> Shell.run(cmd, input)
    }

    /**
     * 读文件：按行分页返回（带行号与总行数），路径是目录时照样返回目录列表。
     * 三种取法：①page+lines 分页；②rangeFrom/rangeTo 绝对行区间（给了就忽略 page）；
     * ③width 限制单行返回多少字符（0=不限），超长单行会被截断并注明原长度。
     */
    fun read(
        rawPath: String, page: Int = 1, lines: Int = 400,
        rangeFrom: Int = 0, rangeTo: Int = 0, width: Int = 0
    ): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val pg = page.coerceIn(1, 100_000)
        val cap = lines.coerceIn(10, 2000)
        //行区间优先：rangeFrom>0 就按绝对行号取，rangeTo 不填则取 rangeFrom+cap-1
        val from: Int
        val to: Int
        if (rangeFrom > 0) {
            from = rangeFrom.coerceAtLeast(1)
            to = if (rangeTo >= from) rangeTo else from + cap - 1
        } else {
            from = (pg - 1) * cap + 1
            to = from + cap - 1
        }
        val w = width.coerceIn(0, 20_000) //单行最多返回多少字符，0=不限
        return when (route) {
            Route.SANDBOX, Route.ALL_FILES -> readJava(path, File(path), route, from, to, w)
            Route.ZERO_WIDTH -> readJava(path, File(Router.map(path)), route, from, to, w)
            Route.ROOT, Route.SHIZUKU -> {
                //目录 / 普通文件 / 不存在一条命令处理；文件只取这一页，不再整篇拉回来
                val cmd = "if [ -d ${q(path)} ]; then ls -la ${q(path)} | head -n 200; " +
                    "elif [ -f ${q(path)} ]; then echo \"@N@\$(awk 'END{print NR}' ${q(path)})\"; " +
                    "sed -n '${from},${to}p' ${q(path)}; " +
                    "else echo '文件不存在或不可读'; exit 3; fi"
                val r = exec(route, cmd)
                if (!r.ok) return Out(false, "[${route.label}] 读取失败(${r.code}): ${r.out}", route)
                val raw = r.out
                if (!raw.startsWith("@N@")) return Out(true, "[${route.label}] $path\n$raw", route)
                val nl = raw.indexOf('\n')
                val total = raw.substring(3, if (nl < 0) raw.length else nl).trim().toLongOrNull() ?: 0L
                val body = if (nl < 0) "" else raw.substring(nl + 1)
                Out(true, pageText(route.label, path, body, from, to, total, cap, w), route)
            }
        }
    } //读取文件（分页 / 行区间 / 单行截断）

    /** 分页结果的抬头 + 带行号的正文：总行数、页数、越界提示都写在一行里，模型一眼能决定下一页取哪 */
    private fun pageText(
        label: String, path: String, body: String, from: Int, to: Int,
        total: Long, cap: Int, width: Int = 0
    ): String {
        val cnt = if (total < 0) "行数未知（文件较大）" else "共 $total 行"
        val head = "[$label] $path（第 $from-$to 行，$cnt）"
        if (total > 0 && from > total) return "$head\n（起始行超出文件末尾，共 $total 行）"
        val rows = body.split("\n").let { if (it.isNotEmpty() && it.last().isEmpty()) it.dropLast(1) else it }
        if (rows.isEmpty()) return "$head\n（这一页没有内容）"
        val sb = StringBuilder(head).append('\n')
        rows.forEachIndexed { i, line ->
            //width>0 时单行超长就截断，但把原长度写出来，模型才知道该不该再来一次要全文
            val shown = if (width > 0 && line.length > width) {
                line.take(width) + " …（本行共 ${line.length} 字符）"
            } else line
            sb.append((from + i).toString().padStart(6)).append("| ").append(shown).append('\n')
        }
        val tail = if (total > 0 && to >= total) "\n（已到文件末尾）"
        else "\n（还有后续，下一页 page=${from / cap + 2}，或 from=${to + 1} 继续）"
        return sb.toString().trimEnd('\n') + tail
    } //分页抬头与行号正文


    /**
     * 写文件：默认只用于**新建**（目标已存在直接拒绝，避免模型悄悄毁掉旧内容）。
     * append=true 时改成「追加」：目标存在就把内容接到末尾，不存在等于新建。
     */
    fun write(rawPath: String, content: String, append: Boolean = false): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val exists = existsOn(path, route)
        if (!append) {
            //覆盖写会让模型悄悄毁掉旧内容：目标已存在就拒绝，逼它先 delete_file 或改用 edit_file
            if (exists) {
                return Out(
                    false,
                    "[${route.label}] 目标文件已存在：$path\n" +
                        "write_file 只用于新建文件。改几行请用 edit_file（保留原内容）；确实要整篇覆盖，先 delete_file 再 write_file。",
                    route
                )
            }
        } else if (exists) {
            //追加模式：目录不能追加，文件可以
            val isDir = when (route) {
                Route.SANDBOX, Route.ALL_FILES -> File(path).isDirectory
                Route.ZERO_WIDTH -> File(Router.map(path)).isDirectory
                else -> exec(route, "if [ -d ${q(path)} ]; then echo Y; else echo N; fi").out.contains("Y")
            }
            if (isDir) return Out(false, "[${route.label}] 目标是目录，不能追加：$path", route)
        }
        //写之前先留一份旧内容给界面算 diff；读不到（没权限/太大）就返回 null，跳过 diff
        val before = readTextForDiff(path, route)
        val redir = if (append) ">>" else ">"
        val out = when (route) {
            Route.SANDBOX, Route.ALL_FILES -> writeJava(path, File(path), content, route, append)
            Route.ZERO_WIDTH -> writeJava(path, File(Router.map(path)), content, route, append)
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
                //追加时用 cat >> 接在旧内容后面；覆盖仍走 cp -f
                val mv = if (append) "cat ${q(tmp.absolutePath)} >> ${q(path)}" else "cp -f ${q(tmp.absolutePath)} ${q(path)}"
                val r = exec(route, "$mv; rm -f ${q(tmp.absolutePath)}")
                if (r.ok) Out(true, "[root] 已${if (append) "追加" else "写入"} $path（${content.length} 字符）", route)
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
                val r = exec(route, "base64 -d $redir ${q(path)}", b64)
                if (r.ok) Out(true, "[Shizuku] 已${if (append) "追加" else "写入"} $path（${content.length} 字符）", route)
                else Out(false, "[Shizuku] 写入失败(${r.code}): ${r.out}", route)
            }
        } //四通道各自失败点都会提前 return
        //写成功了才谈 diff：拼在结果尾部，UI 按标记拆开上色
        if (!out.ok || before == null) return out
        //追加时的新全文 = 旧内容 + 本次内容，diff 才是真实的追加行
        val after = if (append) before + content else content
        val d = Diff.unified(before, after)
        if (d.isBlank()) return out
        return out.copy(text = out.text + "\n" + Diff.MARK + "\n" + d + "\n" + Diff.MARK_END)
    } //写入文件（带 diff，可追加）

    /** 声明行：各语言的函数 / 类 / 结构体签名，用于 mode=outline 只看骨架 */
    private val OUTLINE = Regex(
        """^\s*(public |private |internal |protected |static |export |final |abstract |open |data |sealed |inline |external |suspend |override |async |pub )*""" +
            """(class|object|interface|enum|struct|trait|record|fun|function|func|fn|def|sub|module|impl|type)\b"""
    )

    /**
     * 文件骨架：只回函数与类的声明行（带行号），压缩后的实现细节不进上下文。
     * 读不到整文件（超大 / 无权限）时提示改用 read_file 分页或 grep_files。
     */
    fun outline(rawPath: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val text = readTextForDiff(path, route)
            ?: return Out(false, "[${route.label}] 读不到整个文件（体积过大或无权限）：$path\n改用 read_file 分页，或 grep_files 按名字搜", route)
        if (text.isEmpty()) return Out(false, "[${route.label}] 文件为空：$path", route)
        val sb = StringBuilder()
        var n = 0
        text.lineSequence().forEachIndexed { i, line ->
            val t = line.trimStart()
            //注释行与注解不算声明，避免把 @Composable 当成函数
            if (t.isEmpty() || t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("@")) return@forEachIndexed
            if (OUTLINE.containsMatchIn(line)) {
                if (n < 1200) {
                    sb.append((i + 1).toString().padStart(6)).append("| ").append(line.trimEnd().take(220)).append('\n')
                    n++
                }
            }
        }
        if (n == 0) return Out(true, "[${route.label}] $path（没有识别到函数或类声明，可能不是源码文件）", route)
        val more = if (n >= 1200) "\n…声明过多，已截断到 1200 条" else ""
        return Out(true, "[${route.label}] $path 骨架（$n 条声明）\n$sb$more", route)
    } //文件骨架

    /** 目标是否存在（跨通道）：沙箱与零宽看 File.exists()，root / Shizuku 走 test -e */
    private fun existsOn(path: String, route: Route): Boolean = when (route) {
        Route.SANDBOX, Route.ALL_FILES -> File(path).exists()
        Route.ZERO_WIDTH -> File(Router.map(path)).exists()
        else -> {
            val r = exec(route, "if [ -e ${q(path)} ]; then echo Y; else echo N; fi")
            r.ok && r.out.contains("Y")
        }
    } //判断文件是否已存在

    /** 给 diff 用的旧内容：文件不存在返回空串（=整个文件都是新增），读不到返回 null（不加 diff） */
    private fun readTextForDiff(path: String, route: Route): String? = runCatching {
        when (route) {
            Route.SANDBOX, Route.ALL_FILES, Route.ZERO_WIDTH -> {
                val f = File(if (route == Route.ZERO_WIDTH) Router.map(path) else path)
                when {
                    !f.exists() -> ""
                    !f.isFile || f.length() > READ_BYTES -> null
                    else -> f.readText()
                }
            }
            else -> {
                val r = exec(route, "if [ -e ${q(path)} ]; then head -c $READ_BYTES ${q(path)}; else printf '[new]'; fi")
                when {
                    !r.ok -> null
                    r.out.trim() == "[new]" -> ""
                    else -> r.out
                }
            }
        }
    }.getOrNull()

    /** 改文件时能读多大：整篇要进内存做行匹配，超过就不再硬上 */
    private const val EDIT_BYTES = 1_500_000

    /** 读整篇文本（改文件用）：不存在 / 没权限 / 太大都返回 null */
    private fun readAll(path: String, route: Route): String? = runCatching {
        when (route) {
            Route.SANDBOX, Route.ALL_FILES, Route.ZERO_WIDTH -> {
                val f = File(if (route == Route.ZERO_WIDTH) Router.map(path) else path)
                if (!f.isFile || f.length() > EDIT_BYTES) null else f.readText()
            }
            else -> {
                val sz = exec(route, "wc -c < ${q(path)} 2>/dev/null || echo -1")
                val n = sz.out.trim().toLongOrNull() ?: -1L
                if (n < 0 || n > EDIT_BYTES) return@runCatching null
                val r = exec(route, "head -c $EDIT_BYTES ${q(path)} 2>/dev/null")
                if (r.ok) r.out else null
            }
        }
    }.getOrNull()

    /** 改文件：old 按行定位（缩进与行尾空白自动忽略），唯一命中才替换，落盘复用 write 带 diff */
    fun edit(rawPath: String, old: String, neu: String): Out {
        if (old.isBlank()) return Out(false, "old 不能为空：至少给一行原文，新增整段用 write_file", Route.SANDBOX)
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val src = readAll(path, route)
            ?: return Out(false, "[${route.label}] 读不到 $path（不存在、没权限或超过 1.5MB）", route)
        val hit = when (val loc = TextEdit.locate(src, old)) {
            is TextEdit.Loc.Bad -> return Out(false, loc.msg, route)
            is TextEdit.Loc.One -> loc.hit
        }
        val next = TextEdit.splice(src, hit, neu)
        if (next == src) return Out(true, "内容和原来一样，没有改动：$path", route)
        val out = write(path, next)
        if (!out.ok) return out
        return out.copy(text = "已替换第 ${hit.start + 1} 行起共 ${hit.count} 行\n" + out.text)
    } //局部改文件

    /** 按行号插 / 删：insert 插到第 line 行（pos=after 默认插在该行后面，before 插在前面）；delete 从 line 起删 count 行 */
    fun editAt(rawPath: String, mode: String, lineNo: Int, count: Int, after: Boolean, text: String): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val src = readAll(path, route)
            ?: return Out(false, "[${route.label}] 读不到 $path（不存在、没权限或超过 1.5MB）", route)
        val total = TextEdit.lineCount(src)
        //空文件只允许插入：删除没有行可删
        if (total <= 0 && mode != "insert") return Out(false, "文件是空的，没行可删：$path", route)
        when (mode) {
            "insert" -> {
                if (text.isBlank()) return Out(false, "insert 需要 text：要插入的内容不能为空", route)
                if (lineNo < 0 || lineNo > total) return Out(false, "line 超出范围（0-$total，0 表示插到文件最前面）", route)
                //插入锚点：after=插在该行后面，before=插在该行前面（line=0 就是文件开头）
                val anchor = if (after) lineNo else (lineNo - 1).coerceAtLeast(0)
                val next = TextEdit.insert(src, anchor, text)
                if (next == src) return Out(true, "内容和原来一样，没有改动：$path", route)
                val out = write(path, next)
                if (!out.ok) return out
                val n = TextEdit.lineCount(text.trim('\n'))
                val at = if (after) lineNo else anchor
                return out.copy(text = "已在第 $at 行处插入 $n 行（$total → ${total + n}）\n" + out.text)
            }
            "delete" -> {
                val n = count.coerceAtLeast(1)
                if (lineNo < 1 || lineNo > total) return Out(false, "line 超出范围（1-$total）", route)
                val real = minOf(n, total - lineNo + 1)
                val out = write(path, TextEdit.cut(src, lineNo - 1, real))
                if (!out.ok) return out
                return out.copy(text = "已删除第 $lineNo 行起共 $real 行（$total → ${total - real}）\n" + out.text)
            }
            else -> return Out(false, "mode 只能是 insert 或 delete（replace 用 old/new 那套）", route)
        }
    } //按行号插删

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

    /** 列目录：recurse 递归、match 按文件名过滤（支持 *.kt 通配或关键字）、summarize 让总结模型归纳 */
    /**
     * 列目录：depth 控制往下几层（0 或 1 = 只看当前层，>1 = 指定层数，上限 8；
     * 不填时沿用旧行为：recurse=true 就钻 6 层，否则只看一层）。
     */
    fun list(
        rawPath: String, recursive: Boolean, limit: Int, match: String = "",
        summarize: Boolean = false, depth: Int = -1
    ): Out {
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val cap = limit.coerceIn(1, 1500)
        val maxDepth = if (depth >= 0) depth.coerceIn(1, 8) else if (recursive) 6 else 1
        val out = listRaw(path, route, recursive, cap, match, maxDepth)
        if (!out.ok || !summarize) return out
        val summed = Sum.run(LIST_SUM_SYS, "目录：$path\n过滤条件：${match.ifBlank { "无" }}，递归=${recursive}，层数=$maxDepth\n${out.text}")
        if (summed == null) return out.copy(text = out.text + "\n\n（总结失败：总结模型未配置或调用出错）")
        return out.copy(text = out.text + "\n\n—— 总结（${Sum.label()}）——\n" + summed)
    } //列目录（可总结，可按层数）

    /** 真正的列目录：root / Shizuku 只有 shell，其它通道用 java.io 直接走 */
    private fun listRaw(path: String, route: Route, recursive: Boolean, cap: Int, match: String, maxDepth: Int): Out {
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            val glob = match.trim().let { it.contains('*') || it.contains('?') }
            val nameArg = if (glob) " -name ${q(match.trim())}" else ""
            val filter = if (match.isBlank() || glob) "" else " | grep -i -- ${q(match.trim())}"
            val cmd = if (recursive) {
                "find ${q(path)} -maxdepth $maxDepth$nameArg -exec ls -ld {} \\; 2>/dev/null$filter | head -n $cap"
            } else {
                "find ${q(path)} -maxdepth 1 -mindepth 1$nameArg -exec ls -ld {} \\; 2>/dev/null$filter | head -n $cap"
            }
            val r = exec(route, cmd)
            if (!r.ok) return Out(false, "[${route.label}] 列目录失败(${r.code}): ${r.out}", route)
            if (r.out.isBlank()) return Out(true, "[${route.label}] 没有匹配项: $path${if (match.isBlank()) "" else "（过滤 $match）"}", route)
            return Out(true, "[${route.label}] $path\n${r.out}", route)
        }
        val f = real(route, path)
        if (!f.exists()) return Out(false, "[${route.label}] 不存在: $path", route)
        if (!f.isDirectory) return info(path)
        val sb = StringBuilder()
        var n = 0
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        fun walk(dir: File, depth: Int) {
            val kids = dir.listFiles()?.sortedBy { it.name } ?: return
            for (k in kids) {
                if (n >= cap) return
                //过滤只落在文件名上：命中就列，没命中的目录仍然要往下走，否则整棵子树被漏掉
                val hit = matchBlob(k.name, match)
                if (!hit) {
                    if (recursive && k.isDirectory && depth + 1 < maxDepth) walk(k, depth + 1)
                    continue
                }
                val rel = k.absolutePath.removePrefix(f.absolutePath).trimStart('/')
                sb.append(if (k.isDirectory) "[D] " else "[F] ").append(rel)
                    .append("  ").append(if (k.isDirectory) "-" else human(k.length()))
                    .append("  ").append(fmt.format(Date(k.lastModified()))).append('\n')
                n++
                if (recursive && k.isDirectory && depth + 1 < maxDepth) walk(k, depth + 1)
            }
        }
        walk(f, 0)
        if (n == 0) return Out(true, "[${route.label}] 没有匹配项: $path${if (match.isBlank()) "" else "（过滤 $match）"}", route)
        val more = if (n >= cap) "\n…已达上限 $cap 项，缩小路径或加大 limit" else ""
        return Out(true, "[${route.label}] 目录 $path（$n 项）\n$sb$more", route)
    } //列目录本体

    /** 文件名过滤：带 * 或 ? 按通配匹配，否则按关键字包含（都忽略大小写） */
    private fun matchBlob(name: String, match: String): Boolean {
        val m = match.trim()
        if (m.isEmpty()) return true
        if (!m.contains('*') && !m.contains('?')) return name.contains(m, true)
        val pat = buildString {
            append('^')
            for (ch in m) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
            append('$')
        }
        return runCatching { Regex(pat, RegexOption.IGNORE_CASE).matches(name) }.getOrDefault(false)
    } //文件名过滤

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

    /**
     * 按内容搜索：context=命中行前后各带几行（0-10），offset=跳过前多少条命中继续往后看。
     * 两个参数都是为「命中太多、一次看不过来」准备的：先看前 60 条，再 offset=60 接着往下翻。
     */
    fun grep(
        rawPath: String, pattern: String, ext: String, maxHits: Int, ignoreCase: Boolean,
        recurse: Boolean = true, summarize: Boolean = false, context: Int = 0, offset: Int = 0
    ): Out {
        if (pattern.isEmpty()) return Out(false, "pattern 不能为空", Route.SANDBOX)
        val path = Workspace.resolve(rawPath)
        val route = Router.route(path)
        val cap = maxHits.coerceIn(1, 400)
        val ctx = context.coerceIn(0, 10)
        val skip = offset.coerceAtLeast(0)
        val out = grepRaw(path, route, pattern, ext, cap, ignoreCase, recurse, ctx, skip)
        if (!out.ok || !summarize) return out
        val summed = Sum.run(
            GREP_SUM_SYS,
            "搜索路径：$path\n正则：$pattern\n扩展名过滤：${ext.ifBlank { "无" }}，递归=$recurse\n\n${out.text}"
        )
        if (summed == null) return out.copy(text = out.text + "\n\n（总结失败：总结模型未配置或调用出错）")
        return out.copy(text = out.text + "\n\n—— 总结（${Sum.label()}）——\n" + summed)
    } //按内容搜索（可总结）

    /** 搜索本体：shell 通道交给 grep / find，其它通道在 Kotlin 里逐文件逐行匹配 */
    private fun grepRaw(
        path: String, route: Route, pattern: String, ext: String, cap: Int,
        ignoreCase: Boolean, recurse: Boolean, ctx: Int = 0, skip: Int = 0
    ): Out {
        if (route == Route.ROOT || route == Route.SHIZUKU) {
            val flag = if (ignoreCase) "-i" else ""
            val ctxArg = if (ctx > 0) "-C $ctx" else ""
            //offset 要跳过的命中条数：用 tail 从第 skip+1 行开始截，之后再 head 限 cap
            val pipe = " | tail -n +${skip + 1} | head -n $cap"
            val cmd = if (recurse) {
                "grep -rn $flag $ctxArg --binary-files=without-match -e ${q(pattern)} ${q(path)} 2>/dev/null$pipe"
            } else {
                "find ${q(path)} -maxdepth 1 -type f -exec grep -n $flag $ctxArg --binary-files=without-match -e ${q(pattern)} {} + " +
                    "2>/dev/null$pipe"
            }
            val r = exec(route, cmd)
            return if (r.out.isNotBlank()) Out(true, "[${route.label}] $path\n${r.out}", route)
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
        var skipped = 0
        var scanned = 0
        val files = when {
            !f.isDirectory -> sequenceOf(f)
            recurse -> f.walkTopDown().filter { it.isFile }
            else -> f.listFiles()?.filter { it.isFile }?.asSequence() ?: emptySequence()
        }
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
                if (!regex.containsMatchIn(lines[i])) continue
                //offset：前面的命中直接跳过，只计数不输出
                if (skipped < skip) {
                    skipped++
                    continue
                }
                if (ctx > 0) {
                    //带上下文：命中行用 > 标，前后行用行号标，模型能直接照行号去 read_file
                    val a = (i - ctx).coerceAtLeast(0)
                    val b = (i + ctx).coerceAtMost(lines.size - 1)
                    sb.append(f2.absolutePath).append(':').append(i + 1).append(":\n")
                    for (k in a..b) {
                        sb.append(if (k == i) "> " else "  ").append(k + 1).append("| ")
                            .append(lines[k].trim().take(300)).append('\n')
                    }
                } else {
                    sb.append(f2.absolutePath).append(':').append(i + 1).append(": ")
                        .append(lines[i].trim().take(300)).append('\n')
                }
                hits++
            }
        }
        if (hits == 0) {
            val tail = if (skipped >= skip && skip > 0) "（已跳过前 $skip 条命中，后面没有了）" else ""
            return Out(true, "[${route.label}] 扫描 $scanned 个文件，没有匹配: $pattern$tail", route)
        }
        val more = if (hits >= cap) "\n（还有后续，offset=${skip + hits} 继续）" else ""
        return Out(true, "[${route.label}] $path 命中 $hits 条（扫了 $scanned 个文件）\n$sb$more", route)
    } //按内容搜索本体

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

    /** 沙箱 / 零宽读取：按行分页，只留这一页进内存；文件不太大时顺手数出总行数 */
    private fun readJava(showPath: String, f: File, route: Route, from: Int, to: Int, width: Int = 0): Out {
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
        //先看文件头：空文件与二进制不需要分页，也避免后面按行读把二进制当文本吞掉
        val headBytes = runCatching {
            f.inputStream().use { ins ->
                val buf = ByteArray(4096)
                val n = ins.read(buf)
                java.util.Arrays.copyOf(buf, n.coerceAtLeast(0))
            }
        }.getOrElse { return Out(false, "[${route.label}] 读取异常: ${it.message}", route) }
        if (headBytes.isEmpty()) return Out(true, "[${route.label}] $showPath 为空文件", route)
        if (headBytes.any { it == 0.toByte() }) {
            val hex = headBytes.take(64).joinToString(" ") { "%02x".format(it) }
            return Out(true, "[${route.label}] $showPath 是二进制文件，共 $total 字节\n前 64 字节: $hex", route)
        }
        val countAll = total <= COUNT_BYTES
        val rows = ArrayList<String>()
        var n = 0L
        runCatching {
            f.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    n++
                    if (n in from.toLong()..to.toLong()) rows.add(line)
                    else if (!countAll && n > to) break
                }
            }
        }.getOrElse { return Out(false, "[${route.label}] 读取异常: ${it.message}", route) }
        var body = rows.joinToString("\n")
        var cut = ""
        //压缩过的单行文件能一页顶出几 MB：按字符数再兜一刀
        if (body.length > MAX_READ) {
            body = body.substring(0, MAX_READ)
            cut = "\n…这一页内容过长，只返回前 $MAX_READ 字符，请减小 lines 再读"
        }
        val pt = pageText(route.label, showPath, body, from, to, if (countAll) n else -1L, to - from + 1, width)
        return Out(true, pt + cut, route)
    } //沙箱/零宽读取（分页）

    private fun writeJava(showPath: String, f: File, content: String, route: Route, append: Boolean = false): Out {
        val ok = runCatching {
            f.parentFile?.mkdirs()
            //追加走 appendText，否则整篇覆盖
            if (append) f.appendText(content) else f.writeText(content)
        }.isSuccess
        val verb = if (append) "追加" else "写入"
        return if (ok) Out(true, "[${route.label}] 已$verb $showPath（${content.length} 字符）", route)
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