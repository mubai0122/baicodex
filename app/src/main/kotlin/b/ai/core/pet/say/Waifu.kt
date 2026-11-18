package b.ai.core.pet.say
import b.ai.net.api.llm.text

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

    /**
     * 两条之间的停顿：按字数 × 打字速度（ms/字）给，短句快、长句慢。
     * 下限 150ms 保证上一口气泡的弹出动画看得见，上限 5s 防止长句把整轮拖死。
     */
    fun gapMs(s: String, msPerChar: Int = 45): Long =
        (s.length.toLong() * msPerChar.toLong()).coerceIn(150L, 5000L) //两条之间的停顿

    //真实引擎用的两档上限：单条字数上限更小，逗号也当断点
    private const val REAL_MAX = 26

    /**
     * 更真实的分句引擎：真人连发短消息不会一段话分两块，而是逗号处也会断开。
     * 与 split() 的差别：
     *   · 逗号 / 顿号 / 分号也当断点（但只在单句超过 REAL_MAX 时才断，短句不硬切）；
     *   · 断点处的标点留在前一句，不做句末补齐；
     *   · 切出来的每条互不合并，宁可多几条也不给一整块。
     */
    fun splitReal(text: String): List<String> {
        val out = ArrayList<String>()
        cut(text, SENT).forEach { raw ->
            val s = raw.trim()
            if (s.isEmpty()) return@forEach
            if (s.length <= REAL_MAX) {
                out.add(s)
                return@forEach
            }
            //长句：逗号处直接断，每段不再合并，保证每条都短
            cut(s, COMMA).forEach { piece ->
                val t = piece.trim()
                if (t.isEmpty()) return@forEach
                //单段仍然过长（没有逗号的长串）：按 REAL_MAX 硬切
                if (t.length <= REAL_MAX) {
                    out.add(t)
                } else {
                    t.chunked(REAL_MAX).forEach { c -> if (c.isNotBlank()) out.add(c.trim()) }
                }
            }
        }
        val clean = out.filter { it.isNotBlank() }
        return clean.ifEmpty { listOf(text.trim()) }
    } //真实分句
} //Waifu 分句