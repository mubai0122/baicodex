package b.ai.core

import b.ai.exec.Workspace
import java.io.File

/** 重构引擎：扫工作区建索引（.docs/function.txt 等）、查 imports 与被引用关系；只读源码，只往 .docs 写 */
object Refactor {
    data class Res(val ok: Boolean, val text: String)

    private const val MAX_FILES = 20_000
    private const val MAX_BYTES = 800_000
    private const val MAX_LIST = 120

    /** 扩展名 → 语言：认不出的文件不进索引（图片、锁文件、二进制都跳过） */
    private val LANGS = linkedMapOf(
        "kt" to "Kotlin", "kts" to "Kotlin", "java" to "Java",
        "php" to "PHP",
        "ts" to "TypeScript", "tsx" to "TSX", "js" to "JS", "jsx" to "JSX", "vue" to "Vue",
        "c" to "C", "h" to "C header", "hpp" to "C++", "cc" to "C++", "cpp" to "C++",
        "rs" to "Rust", "py" to "Python",
        "gradle" to "Gradle", "sh" to "Shell", "sql" to "SQL", "xml" to "XML"
    )

    /** 扫盘时跳过的目录：构建产物与依赖缓存 */
    private val SKIP = setOf(
        ".git", ".gradle", "build", ".idea", "node_modules", "__pycache__",
        ".cxx", "dist", ".kotlin", ".tmp", "target", "venv", ".venv", "vendor"
    )

    /** 用于「文件作用」兜底：没注释时取第一行像声明的东西 */
    private val DECL = Regex(
        """^(public |private |internal |protected |static |export |final |abstract |open |data |sealed |inline |external |suspend |override )*""" +
            """(class|object|interface|enum|struct|fun|function|func|fn|def|sub|module|impl|trait|record|type)\b"""
    )

    private val RE_KT_IMP = Regex("""^import\s+(?:static\s+)?([\w.*]+)""")
    private val RE_FROM = Regex("""from\s+['"]([^'"]+)['"]""")
    private val RE_REQUIRE = Regex("""(?:require|import)\s*\(\s*['"]([^'"]+)['"]""")
    private val RE_IMPORT_ONLY = Regex("""^import\s+['"]([^'"]+)['"]""")
    private val RE_PHP = Regex("""(?:require|include)(?:_once)?\s*\(?\s*['"]([^'"]+)['"]""")
    private val RE_PHP_USE = Regex("""^use\s+([\w\\]+)""")
    private val RE_C_INC = Regex("""^#include\s+[<"]([^>"]+)[>"]""")
    private val RE_RS_USE = Regex("""^use\s+([\w:]+)""")
    private val RE_RS_MOD = Regex("""^mod\s+(\w+)""")
    private val RE_PY = Regex("""^(?:from\s+([\w.]+)\s+import|import\s+([\w.]+))""")

    private data class Info(
        val rel: String,
        val lang: String,
        val lines: Int,
        val bytes: Long,
        val summary: String,
        val imports: List<Pair<Int, String>>
    )

    /** 按扩展名认语言，认不出返回空串 */
    fun langOf(name: String): String = LANGS[name.substringAfterLast('.', "").lowercase()] ?: ""

    /** 工作区根：参数为空就用当前工作区；路径不存在直接报错 */
    private fun rootOf(wsArg: String): File? {
        val raw = wsArg.trim().ifBlank { Workspace.path() }
        if (raw.isBlank()) return null
        val f = File(raw)
        return if (f.isDirectory) f else null
    } //取工作区根目录

    private fun walk(root: File, out: MutableList<File>) {
        val kids = root.listFiles() ?: return
        for (k in kids) {
            if (out.size >= MAX_FILES) return
            if (k.isDirectory) {
                if (k.name in SKIP) continue
                walk(k, out)
            } else {
                out.add(k)
            }
        }
    } //递归收集文件

    private fun scanOne(root: File, f: File): Info? {
        val lang = langOf(f.name)
        if (lang.isEmpty()) return null
        if (f.length() > MAX_BYTES) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        if (text.indexOf('\u0000') >= 0) return null
        val rel = f.absolutePath.removePrefix(root.absolutePath).trimStart('/')
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + (if (text.endsWith("\n")) 0 else 1)
        return Info(rel, lang, lines, f.length(), summaryOf(text), importsOf(text, lang))
    } //扫一个文件

    private fun scanAll(root: File): List<Info> {
        val files = ArrayList<File>()
        walk(root, files)
        return files.mapNotNull { scanOne(root, it) }.sortedBy { it.rel }
    } //扫整个工作区

    /** 文件作用：优先取文件头第一段注释，没有就取第一条像声明的行 */
    private fun summaryOf(text: String): String {
        val head = text.lineSequence().take(60).toList()
        for (raw in head) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            if (s.startsWith("package ") || s.startsWith("#!") || s.startsWith("@file")) continue
            if (s.startsWith("//")) {
                val t = s.removePrefix("//").trim()
                if (t.length >= 3) return t.take(90)
                continue
            }
            if (s.startsWith("/*") || s.startsWith("*")) {
                val t = s.removePrefix("/*").removePrefix("*").removeSuffix("*/").trim()
                if (t.length >= 4 && !t.startsWith("@")) return t.take(90)
                continue
            }
            if (s.startsWith("#") && !s.startsWith("#include")) {
                val t = s.removePrefix("#").trim()
                if (t.length >= 3) return t.take(90)
                continue
            }
            if (s.startsWith("\"\"\"") || s.startsWith("--")) continue
            if (DECL.containsMatchIn(s)) return s.take(90)
        }
        return head.firstOrNull { it.isNotBlank() }?.trim()?.take(90) ?: "（空文件）"
    } //提取文件作用

    /** 一个文件里所有的 import / require / include / use，带行号 */
    private fun importsOf(text: String, lang: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var n = 0
        for (line in text.lineSequence()) {
            n++
            val s = line.trim()
            if (s.isEmpty()) continue
            val imp = when {
                lang == "Kotlin" || lang == "Java" -> RE_KT_IMP.find(s)?.groupValues?.get(1)
                lang == "PHP" -> RE_PHP.find(s)?.groupValues?.get(1)
                    ?: RE_PHP_USE.find(s)?.groupValues?.get(1)
                lang == "TypeScript" || lang == "TSX" || lang == "JS" || lang == "JSX" || lang == "Vue" ->
                    RE_FROM.find(s)?.groupValues?.get(1)
                        ?: RE_REQUIRE.find(s)?.groupValues?.get(1)
                        ?: RE_IMPORT_ONLY.find(s)?.groupValues?.get(1)
                lang == "C" || lang == "C header" || lang == "C++" -> RE_C_INC.find(s)?.groupValues?.get(1)
                lang == "Rust" -> RE_RS_USE.find(s)?.groupValues?.get(1)
                    ?: RE_RS_MOD.find(s)?.groupValues?.get(1)
                lang == "Python" -> RE_PY.find(s)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                else -> null
            }
            if (!imp.isNullOrBlank()) out.add(n to imp)
        }
        return out
    } //提取 imports

    /** 反查用：import 原文是否指向目标文件 */
    private fun hitImport(imp: String, name: String): Boolean {
        val t = imp.trim()
        if (t.isEmpty() || name.isEmpty()) return false
        if (t.contains(name, true)) return true
        //类名与文件名对不上时（Kotlin 一个文件多个类）用去掉结尾的短名再试一次
        val tail = name.substringAfterLast('/').substringAfterLast('.')
        return tail.length >= 3 && t.contains(tail, true)
    } //import 是否指向目标

    /** import 能否在工作区里对上某个文件（给 deps 标「内部文件」用） */
    private fun matchRel(imp: String, rels: List<String>): String? {
        val t = imp.trim().trimStart('.', '/').replace('\\', '/')
        if (t.isEmpty()) return null
        val tail = t.substringAfterLast('/')
        val cand = if (tail.contains('.')) tail else tail.replace('.', '/')
        return rels.firstOrNull { r ->
            val n = r.substringAfterLast('/')
            n == tail || n.substringBeforeLast('.') == cand.substringAfterLast('/')
        }
    } //把 import 对到工作区里的文件

    private fun writeDoc(root: File, name: String, text: String): File {
        val docs = File(root, ".docs")
        if (!docs.exists()) docs.mkdirs()
        val f = File(docs, name)
        f.writeText(text)
        return f
    } //写 .docs 下的文件

    /** 建索引：function.txt（文件作用）/ imports.txt（依赖关系）/ index.txt（规模与入口） */
    fun scan(wsArg: String): Res {
        val root = rootOf(wsArg) ?: return Res(false, "工作区不存在：${wsArg.ifBlank { "（未绑定工作区）" }}")
        val all = scanAll(root)
        if (all.isEmpty()) return Res(false, "没扫到可识别的代码文件：${root.absolutePath}")
        val totalLines = all.sumOf { it.lines }
        val byLang = all.groupBy { it.lang }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
        val docs = File(root, ".docs")
        if (!docs.exists()) docs.mkdirs()
        //文件作用表：模型后续改哪块先查这张表，不用反复读源码
        writeDoc(
            root, "function.txt",
            buildString {
                append("#文件作用（refactor_scan 生成，改过代码要重跑一次）\n")
                all.forEach { i ->
                    append(i.rel).append(" | ").append(i.lang).append(" | ").append(i.lines).append(" 行 | ")
                    append(i.summary).append('\n')
                }
            }
        )
        //依赖表：谁 import 了谁，一行一个文件
        writeDoc(
            root, "imports.txt",
            buildString {
                append("#imports（refactor_scan 生成；箭头右边是它依赖的东西，没写 import 的标「无」）\n")
                all.forEach { i ->
                    append(i.rel).append(" → ")
                    if (i.imports.isEmpty()) append("（无）")
                    else append(i.imports.joinToString(", ") { it.second })
                    append('\n')
                }
            }
        )
        //规模表：按目录分组，顺带挑出疑似入口
        val entryWords = listOf("Main", "App", "index", "main", "router", "entry")
        val entries = all.filter { i -> entryWords.any { i.rel.substringAfterLast('/').contains(it, true) } }
        val byDir = all.groupBy { i -> i.rel.substringBefore('/', "(根)") }
        writeDoc(
            root, "index.txt",
            buildString {
                append("#工作区索引（refactor_scan 生成）\n")
                append("工作区：").append(root.absolutePath).append('\n')
                append("文件 ").append(all.size).append(" 个，共 ").append(totalLines).append(" 行\n")
                append("语言分布：").append(byLang.joinToString("、") { "${it.first} ${it.second}" }).append('\n')
                append("疑似入口：")
                if (entries.isEmpty()) append("（没猜到，按需自己看 function.txt）")
                else append(entries.take(8).joinToString("、") { it.rel })
                append("\n\n## 按目录\n")
                byDir.toSortedMap().forEach { (dir, list) ->
                    append("- ").append(dir).append("：").append(list.size).append(" 个文件，")
                        .append(list.sumOf { it.lines }).append(" 行\n")
                }
                append("\n## 最大的 10 个文件\n")
                all.sortedByDescending { it.lines }.take(10).forEach {
                    append("- ").append(it.rel).append("（").append(it.lines).append(" 行）\n")
                }
            }
        )
        val head = all.take(40).joinToString("\n") { "${it.rel} | ${it.lang} | ${it.lines} 行 | ${it.summary}" }
        return Res(
            true,
            "已建索引：${root.absolutePath}/.docs/\n" +
                "function.txt（${all.size} 个文件的作用）｜imports.txt（依赖关系）｜index.txt（规模与入口）\n" +
                "文件 ${all.size} 个 / ${totalLines} 行；语言：" + byLang.take(5).joinToString("、") { "${it.first} ${it.second}" } +
                "\n\n前 40 条：\n$head"
        )
    } //建索引

    /** 查谁引用了目标文件：正向用 imports，命中只按名字包含匹配，可能有同名 */
    fun refs(wsArg: String, target: String): Res {
        val root = rootOf(wsArg) ?: return Res(false, "工作区不存在：${wsArg.ifBlank { "（未绑定工作区）" }}")
        val t = target.trim()
        if (t.isEmpty()) return Res(false, "target 不能为空：给文件相对路径或文件名")
        val name = t.trimStart('/').substringAfterLast('/')
        val all = scanAll(root)
        if (all.isEmpty()) return Res(false, "工作区里没扫到代码文件：${root.absolutePath}")
        val hit = ArrayList<String>()
        var files = 0
        for (info in all) {
            if (info.rel == t.trimStart('/') || info.rel.substringAfterLast('/') == name) continue
            var any = false
            for ((line, imp) in info.imports) {
                if (!hitImport(imp, name)) continue
                any = true
                if (hit.size < MAX_LIST) hit.add("${info.rel}:$line: import $imp")
            }
            if (any) files++
        }
        if (hit.isEmpty()) {
            return Res(
                true,
                "没有文件在 import 里指向 $name。\n" +
                    "可能原因：它是入口/被动态加载、被同包直接调用（Kotlin / Java 同包不用 import）、" +
                    "或者是别处 include 进来的。可以改用 grep_files 搜类名/函数名精确确认。"
            )
        }
        return Res(true, "$name 被 $files 个文件引用（显示前 ${minOf(hit.size, MAX_LIST)} 条）：\n" + hit.joinToString("\n"))
    } //查被引用

    /** 查目标文件自己依赖了谁；能对上工作区文件的标出来，方便顺着摸依赖链 */
    fun deps(wsArg: String, file: String): Res {
        val root = rootOf(wsArg) ?: return Res(false, "工作区不存在：${wsArg.ifBlank { "（未绑定工作区）" }}")
        val t = file.trim()
        if (t.isEmpty()) return Res(false, "file 不能为空：给文件相对路径或文件名")
        val all = scanAll(root)
        val name = t.trimStart('/').substringAfterLast('/')
        val info = all.firstOrNull { it.rel == t.trimStart('/') }
            ?: all.firstOrNull { it.rel.substringAfterLast('/') == name }
            ?: return Res(false, "工作区里没找到 $t；先 refactor_scan 的 function.txt 里确认路径")
        if (info.imports.isEmpty()) return Res(true, "${info.rel}（${info.lang}，${info.lines} 行）没有任何 import：要么是叶子文件，要么是入口。")
        val rels = all.map { it.rel }
        val inside = ArrayList<String>()
        val outside = ArrayList<String>()
        for ((line, imp) in info.imports) {
            val m = matchRel(imp, rels)
            if (m == null) outside.add("$line: $imp")
            else inside.add("$line: $imp  →  $m")
        }
        return Res(
            true,
            "${info.rel}（${info.lang}，${info.lines} 行，作用：${info.summary}）\n" +
                "依赖工作区内文件 ${inside.size} 个：\n" + (inside.joinToString("\n").ifBlank { "（无）" }) +
                "\n\n外部依赖 ${outside.size} 个：\n" + (outside.joinToString("\n").ifBlank { "（无）" })
        )
    } //查依赖

    /** 生成重构计划骨架：换语言 / 换架构两套节奏，落到 .docs/refactor.txt 供一步步勾掉 */
    fun plan(wsArg: String, mode: String, note: String): Res {
        val root = rootOf(wsArg) ?: return Res(false, "工作区不存在：${wsArg.ifBlank { "（未绑定工作区）" }}")
        val m = mode.trim().lowercase()
        if (m != "lang" && m != "arch") return Res(false, "mode 只能是 lang（换语言）或 arch（换架构）")
        val all = scanAll(root)
        val steps = if (m == "lang") listOf(
            "先 refactor_scan，读 .docs/function.txt 与 imports.txt，确认要翻哪些文件、谁依赖谁",
            "定目标语言与映射表：文件后缀、构建脚本、依赖库、命名与包结构怎么对应，写进 .docs/refactor.txt 顶部",
            "从依赖链末端（没人引用它的文件）开始翻，一个一个文件来，翻完立刻编译；不要一次翻一批",
            "每翻完一个文件：更新 .docs/function.txt 那一行、用 refactor_refs 确认还有谁在引用旧文件",
            "接口层（对外暴露的类/函数/CLI/HTTP 入口）最后翻，翻的时候保持签名不变，减少上层返工",
            "全部翻完再换构建脚本与目录结构，然后删旧文件、跑一次完整构建与入口回归"
        ) else listOf(
            "先 refactor_scan，用 imports.txt 画出依赖关系，标出循环依赖与上帝文件",
            "定目标架构：分层还是分模块、每个层的职责边界、哪些是公共契约，写进 .docs/refactor.txt",
            "先抽接口/适配层（不动业务），让老代码通过接口被调用；这一步只加不删，编译必须一直是绿的",
            "按模块一个个搬：一次搬一个模块，搬完编译 + 跑通一个真实入口，再搬下一个",
            "每搬完一个模块：refactor_refs 查还有谁引用旧路径，把引用改干净再进下一块",
            "最后删残留、统一目录结构，跑完整构建与冒烟；把 .docs 里的索引重新生成一遍收尾"
        )
        val body = buildString {
            append("# 重构计划（").append(if (m == "lang") "换语言" else "换架构").append("）\n\n")
            if (note.isNotBlank()) append("用户要求：").append(note.trim()).append("\n\n")
            append("工作区：").append(root.absolutePath).append('\n')
            append("现状：").append(all.size).append(" 个代码文件，").append(all.sumOf { it.lines }).append(" 行\n\n")
            append("## 原则\n")
            append("- 一次只做一件事：一个文件 / 一个模块，做完立刻编译，绿了再下一步\n")
            append("- 每一步都能单独回滚：不要攒一大坨改动再验证\n")
            append("- 先查索引再动手：.docs/function.txt 看作用、imports.txt 看谁依赖谁、refactor_refs 查引用\n\n")
            append("## 步骤（做完一条删一条）\n")
            steps.forEachIndexed { i, s -> append("- [ ] ${i + 1}. ").append(s).append('\n') }
        }
        writeDoc(root, "refactor.txt", body)
        return Res(
            true,
            "已写 ${root.absolutePath}/.docs/refactor.txt（" + (if (m == "lang") "换语言" else "换架构") + "，${steps.size} 步）\n" +
                "下一步：先 refactor_scan 建索引，再按文件里的步骤一条条做，一次只改一个文件/模块。"
        )
    } //生成重构计划
} //重构引擎