package b.ai.core

/** Waifu 模式：把整段回复按标点切成小句逐条发出去，像真人连发消息 */
object Waifu {
    /** 单条上限：句子太长就再按逗号断，避免一口气刷满屏 */
    private const val MAX = 72

    /** 句末标点：切在标点之后，标点留在前一句 */
    private val SENT = Regex("[。！？!?…~\n]")

    /** 句内停顿：长句按这些再断 */
    private val COMMA = Regex("[，,、；;：:]")

    /** 按分隔符切开，分隔符归前一段；零宽切分不用 split，避免末尾空串差异 */
    private fun cut(s: String, sep: Regex): List<String> {
        val out = ArrayList<String>()
        var last = 0
        sep.findAll(s).forEach { m ->
            val end = m.range.last + 1
            if (end > last) {
                out.add(s.substring(last, end))
                last = end
            }
        }
        if (last < s.length) out.add(s.substring(last))
        return out
    } //按标点切成小段

    /** 整段回复切成待逐条发送的小句；切不出来就原样返回一条 */
    fun split(text: String): List<String> {
        val out = ArrayList<String>()
        cut(text, SENT).forEach { raw ->
            val s = raw.trim()
            if (s.isEmpty()) return@forEach
            if (s.length <= MAX) {
                out.add(s)
                return@forEach
            }
            //太长：按逗号贪心合并，每段不超过 MAX
            val buf = StringBuilder()
            cut(s, COMMA).forEach { piece ->
                if (buf.isNotEmpty() && buf.length + piece.length > MAX) {
                    out.add(buf.toString().trim())
                    buf.setLength(0)
                }
                buf.append(piece)
            }
            if (buf.isNotBlank()) out.add(buf.toString().trim())
        }
        val clean = out.filter { it.isNotBlank() }
        return clean.ifEmpty { listOf(text.trim()) }
    } //分句

    /** 两条之间的停顿：按字数给，短句快、长句慢，夹在 200–900ms */
    fun gapMs(s: String): Long = (s.length * 45L).coerceIn(200L, 900L)
} //Waifu 分句
