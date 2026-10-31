package b.ai.exec

/**
 * 行级 unified diff，只给界面展示用，不做补丁回放。
 *
 * 输出约定：' ' 上下文 / '-' 删除 / '+' 新增 / '@@' 段头 / '…' 折叠提示，
 * UI 靠首字符上色，所以不要改这套前缀。
 */
object Diff {
    /** 正文与 diff 之间的分隔标记：UI 靠它把工具结果拆成「摘要 + 修改行」 */
    const val MARK = "[[diff]]"
    const val MARK_END = "[[/diff]]"

    private const val MAX_OUT = 400   // 最多输出这么多行，再多面板也看不过来
    private const val MAX_WORK = 800  // 中间段超过这么多行就不做 LCS，直接整段删加
    private const val CTX = 2         // 每个改动点前后保留的上下文行数

    fun unified(oldText: String, newText: String): String {
        if (oldText == newText) return ""
        val a = oldText.split('\n')
        val b = newText.split('\n')

        // 先裁掉公共前后缀，剩下的中间段才需要算 LCS
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre &&
            a[a.size - 1 - suf] == b[b.size - 1 - suf]
        ) suf++

        val aMid = a.subList(pre, a.size - suf)
        val bMid = b.subList(pre, b.size - suf)
        val rows = ArrayList<String>()
        rows += "@@ -${pre + 1},${aMid.size} +${pre + 1},${bMid.size} @@"

        if (aMid.size + bMid.size > MAX_WORK) {
            // 大改直接整段删、整段加，省掉 O(n*m) 的表格
            aMid.forEach { rows += "-$it" }
            bMid.forEach { rows += "+$it" }
        } else {
            rows += compact(lcs(aMid, bMid))
        }

        if (rows.size > MAX_OUT) {
            return rows.subList(0, MAX_OUT).joinToString("\n") + "\n…（修改行太多，已截断）"
        }
        return rows.joinToString("\n")
    } //生成 unified 文本

    /** 经典 LCS 回溯，返回 ' ' / '-' / '+' 的行序列 */
    private fun lcs(a: List<String>, b: List<String>): List<Pair<Char, String>> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val ops = ArrayList<Pair<Char, String>>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    ops += ' ' to a[i]
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    ops += '-' to a[i]
                    i++
                }
                else -> {
                    ops += '+' to b[j]
                    j++
                }
            }
        }
        while (i < n) {
            ops += '-' to a[i]
            i++
        }
        while (j < m) {
            ops += '+' to b[j]
            j++
        }
        return ops
    } //最长公共子序列

    /** 离改动点太远的上下文折成一行「…省略 N 行」 */
    private fun compact(ops: List<Pair<Char, String>>): List<String> {
        val keep = BooleanArray(ops.size)
        for (k in ops.indices) {
            if (ops[k].first == ' ') continue
            for (d in -CTX..CTX) {
                val x = k + d
                if (x in ops.indices) keep[x] = true
            }
        }
        val out = ArrayList<String>()
        var skipped = 0
        for (k in ops.indices) {
            if (keep[k]) {
                if (skipped > 0) {
                    out += " …省略 $skipped 行"
                    skipped = 0
                }
                out += ops[k].first + ops[k].second
            } else {
                skipped++
            }
        }
        if (skipped > 0) out += " …省略 $skipped 行"
        return out
    } //折叠长上下文
}