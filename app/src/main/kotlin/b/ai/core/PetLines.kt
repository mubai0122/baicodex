package b.ai.core

import java.io.File

/** 桌宠台词池：一行一条，落在数据根 pet/lines.txt，用户拿文本编辑器就能改 */
object PetLines {
    /** 首次运行用的种子台词 */
    private val SEED = listOf(
        "哦鲸鲸...",
        "你目录里的 dsh 是什么..大烧货吗？",
        "你再点！😡",
        "白饭...",
        "叫我大肥鱼？你号没了！😡",
        "饿饿，饭饭，Token!"
    )

    private fun file(): File = File(Paths.dir("pet"), "lines.txt")

    /** 全部台词；文件不存在时先落种子，读空也回种子 */
    fun all(): List<String> {
        val f = file()
        if (!f.isFile) {
            runCatching { f.writeText(SEED.joinToString("\n") + "\n") }
            return SEED
        }
        val list = runCatching {
            f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
        return list.ifEmpty { SEED }
    } //读全部

    /** 整表写回，空行自动丢掉 */
    fun save(lines: List<String>) {
        val body = lines.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        runCatching { file().writeText(if (body.isEmpty()) "" else body + "\n") }
    } //写回

    /** 从外部文件导入覆盖：返回导入条数，读不到或全空返回 -1 */
    fun import(src: File): Int {
        if (!src.isFile) return -1
        val lines = runCatching {
            src.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
        if (lines.isEmpty()) return -1
        save(lines)
        return lines.size
    } //导入覆盖

    /** 最近说过的两句：同一条至少隔两句才会再出现 */
    private val recent = ArrayDeque<String>()

    /** 挑一句：池子太小时就不管重复了，否则尽量避开最近两句 */
    fun pick(): String {
        val pool = all()
        if (pool.isEmpty()) return ""
        var t = pool[0]
        for (i in 0 until 12) {
            t = pool[(Math.random() * pool.size).toInt().coerceIn(0, pool.size - 1)]
            if (t !in recent) break
        }
        recent.addLast(t)
        while (recent.size > 2) recent.removeFirst()
        return t
    } //挑一句

    fun path(): String = file().absolutePath
} //桌宠台词
