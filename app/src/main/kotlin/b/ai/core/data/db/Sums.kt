package b.ai.core.data.db

import b.ai.native.security.Vault

/** 摘要正文的加密落点：库里只留 [[upto:id]] 标记，正文按「会话 id + 覆盖上界」存进加密桶，DB 里看不到明文 */
object Sums {
    private const val BF = Vault.SUM
    private val KEY = Regex("""upto:(\d+)""")
    /** 一条摘要一个键：同一会话会压出多条摘要，只按会话 id 存会互相覆盖 */
    private fun key(sid: String, marker: String): String? {
        if (sid.isBlank()) return null
        val n = KEY.find(marker)?.groupValues?.get(1) ?: return null
        return "sum:$sid:$n"
    } //摘要正文键
    /** 存正文并返回该写进库的字符串：加密桶不可用时降级成「标记+正文」直接入库，宁可明文也不丢摘要 */
    fun put(sid: String, marker: String, body: String): String {
        val k = key(sid, marker) ?: return marker + body
        if (body.isBlank() || !Vault.writable(BF)) return marker + body
        Vault.put(k, body, BF)
        return if (Vault.get(k, BF) == body) marker else marker + body
    } //写正文
    /** 把库里的标记补成完整正文：没有密文（老版本明文摘要 / 桶被清）就原样返回 */
    fun open(sid: String, content: String): String {
        val i = content.indexOf("]]")
        if (i < 0) return content
        val k = key(sid, content) ?: return content
        if (!Vault.writable(BF)) return content
        val body = Vault.get(k, BF)
        if (body.isBlank()) return content
        return content.substring(0, i + 2) + body
    } //补正文
    /** 会话删了或清空：把这个会话名下所有摘要正文一起清掉 */
    fun drop(sid: String) {
        if (sid.isBlank() || !Vault.writable(BF)) return
        val head = "sum:$sid:"
        runCatching {
            Vault.snapshot(BF).keys.filter { it.startsWith(head) }.forEach { Vault.put(it, "", BF) }
        }
    } //按会话前缀清
} //摘要加密落点