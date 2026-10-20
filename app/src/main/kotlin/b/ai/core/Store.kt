package b.ai.core

/** 数据根下的小文件读写：技能、角色、自定义提示都落在 baicode 里，用户和模型都能直接改 */
object Store {
    fun file(name: String) = Paths.file(name)

    fun read(name: String): String? {
        val f = Paths.file(name)
        if (!f.isFile) return null
        return runCatching { f.readText() }.getOrNull()
    } //读，不存在返回 null

    fun write(name: String, text: String): Boolean =
        runCatching { Paths.file(name).writeText(text) }.isSuccess //写，失败返回 false

    fun exists(name: String): Boolean = Paths.file(name).isFile
} //根目录小文件