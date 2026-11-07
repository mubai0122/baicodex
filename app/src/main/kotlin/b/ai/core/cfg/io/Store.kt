package b.ai.core.cfg.io
import b.ai.net.api.text

import java.io.File

/** 数据根下的小文件读写：技能、角色、自定义提示都落在 baicode 里，用户和模型都能直接改 */
object Store {
    fun file(name: String) = Paths.file(name)

    fun read(name: String): String? {
        val f = Paths.file(name)
        if (!f.isFile) return null
        return runCatching { f.readText() }.getOrNull()
    } //读，不存在返回 null

    fun write(name: String, text: String): Boolean = runCatching {
        //外部存储没就绪时 ensure() 会退回私有目录：这时写下去就有两份数据源，
        //等外部恢复后两边互相覆盖，配置就这么"少了一条"。宁可不写，也不写错地方
        if (Paths.ensure() != Paths.base()) return@runCatching false
        val f = Paths.file(name)
        val t = File(f.parentFile ?: return@runCatching false, "$name.tmp")
        t.writeText(text)
        //先写临时文件再改名：进程被杀只会留下完整的旧档或完整的新档，不会留半截 JSON
        if (!t.renameTo(f)) {
            f.delete()
            if (!t.renameTo(f)) {
                t.copyTo(f, overwrite = true)
                t.delete()
            }
        }
        true
    }.getOrDefault(false) //写，失败返回 false

    fun exists(name: String): Boolean = Paths.file(name).isFile
} //根目录小文件