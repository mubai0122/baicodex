package b.ai.core

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
} //按行改文件