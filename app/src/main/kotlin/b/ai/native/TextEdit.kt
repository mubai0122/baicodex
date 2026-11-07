package b.ai.native
import b.ai.net.api.text

/** 按行改文件：定位在 C++（engine/edit.cpp），拼文本在 Kotlin，避免整篇文本过 JNI 把 4 字节字符踩坏 */
object TextEdit {
    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    private external fun nativeFind(src: String, old: String): String

    /** 命中的位置：起始行（0 基）、被替换行数、原文缩进、old 首行缩进 */
    data class Hit(val start: Int, val count: Int, val indent: String, val oldIndent: String)

    sealed class Loc {
        data class One(val hit: Hit) : Loc()
        data class Bad(val msg: String) : Loc()
    } //定位结果

    fun locate(src: String, old: String): Loc {
        if (!ok) return Loc.Bad("本地引擎没加载起来")
        val raw = runCatching { nativeFind(src, old) }.getOrElse {
            return Loc.Bad("定位失败：" + (it.message ?: it.javaClass.simpleName))
        }
        if (!raw.startsWith("OK\u0001")) return Loc.Bad(raw.substringAfter('\t', "定位失败"))
        val f = raw.split('\u0001')
        if (f.size < 5) return Loc.Bad("定位结果异常")
        val start = f[1].toIntOrNull() ?: return Loc.Bad("行号解析失败")
        val count = f[2].toIntOrNull() ?: return Loc.Bad("行数解析失败")
        if (count <= 0) return Loc.Bad("行数解析失败")
        return Loc.One(Hit(start, count, f[3], f[4]))
    } //定位这段老代码

    /** 拼改好的全文：新代码每行按 old 首行的缩进差平移，换行符风格跟原文件保持一致 */
    fun splice(src: String, hit: Hit, neu: String): String {
        val cr = if (src.contains("\r\n")) "\r" else ""
        val lines = src.split("\n")
        val a = hit.start.coerceIn(0, lines.size)
        val b = (hit.start + hit.count).coerceIn(a, lines.size)
        val head = lines.subList(0, a)
        val tail = lines.subList(b, lines.size)
        //新代码前后多带的空行自动去掉：这段替换的是老代码的位置，不含边界空行
        val raw = neu.split("\n")
        var s = 0
        var e = raw.size
        while (s < e && raw[s].isBlank()) s++
        while (e > s && raw[e - 1].isBlank()) e--
        val block = raw.subList(s, e)
        val out = ArrayList<String>(head.size + block.size + tail.size)
        out.addAll(head)
        block.forEachIndexed { i, line ->
            val body = line.trim()
            val lead = line.substring(0, line.length - line.trimStart().length)
            val pre = if (lead.length >= hit.oldIndent.length) {
                hit.indent + lead.substring(hit.oldIndent.length)
            } else {
                hit.indent
            }
            val more = i < block.size - 1 || tail.isNotEmpty()
            out.add(if (body.isEmpty()) "" else pre + body)
            if (cr.isNotEmpty() && more) out[out.size - 1] = out[out.size - 1] + cr
        }
        out.addAll(tail)
        return out.joinToString("\n")
    } //拼装替换结果

    /** 在 anchor 行之后插入一段（anchor=0 表示插到文件最前面）：缩进以锚点行为基准，换行符跟原文件一致 */
    fun insert(src: String, anchor: Int, text: String): String {
        val cr = if (src.contains("\r\n")) "\r" else ""
        val lines = src.split("\n").toMutableList()
        val a = anchor.coerceIn(0, lines.size)
        //锚点行的缩进即目标缩进；插在最前面时借下面第一行的缩进
        val refRaw = when {
            a > 0 -> lines[a - 1]
            lines.isNotEmpty() -> lines[0]
            else -> ""
        }
        val ref = refRaw.substring(0, refRaw.length - refRaw.trimStart().length)
        val raw = text.split("\n")
        var s = 0
        var e = raw.size
        while (s < e && raw[s].isBlank()) s++
        while (e > s && raw[e - 1].isBlank()) e--
        val block = raw.subList(s, e)
        if (block.isEmpty()) return src
        //新代码自己的首行缩进当作基准，后面的行按与首行的缩进差平移，用户给的相对层次不丢
        val oldIndent = block.first().let { it.substring(0, it.length - it.trimStart().length) }
        val out = ArrayList<String>(block.size)
        for (line in block) {
            val body = line.trim()
            val lead = line.substring(0, line.length - line.trimStart().length)
            val pre = if (lead.length >= oldIndent.length) ref + lead.substring(oldIndent.length) else ref
            out.add(if (body.isEmpty()) "" else pre + body)
            if (cr.isNotEmpty()) out[out.size - 1] = out[out.size - 1] + cr
        }
        lines.addAll(a, out)
        return lines.joinToString("\n")
    } //按行插入

    /** 删掉从 start 行（0 基）起的 n 行：剩下的行原样拼回去，末尾换行风格不变 */
    fun cut(src: String, start: Int, n: Int): String {
        val lines = src.split("\n")
        val a = start.coerceIn(0, lines.size)
        val b = (a + n).coerceIn(a, lines.size)
        return (lines.subList(0, a) + lines.subList(b, lines.size)).joinToString("\n")
    } //按行删除

    /** 行数：文件以换行结尾时 split 会多出一个空串，那个不算一行 */
    fun lineCount(src: String): Int {
        if (src.isEmpty()) return 0
        val lines = src.split("\n")
        return if (lines.last().isEmpty()) lines.size - 1 else lines.size
    } //统计行数
} //按行改文件