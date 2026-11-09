package b.ai.core.cfg.io
import b.ai.net.api.llm.text
import java.io.File
/** 数据根下的小文件读写：技能、角色、自定义提示都落在 baicode 里，用户和模型都能直接改 */
object Store {
    fun file(name: String) = Paths.file(name)

    fun read(name: String): String? {
        val f = Paths.file(name)
        if (!f.isFile) return null
        return runCatching { f.readText() }.getOrNull()
    } //读，不存在返回 null

    /** 写盘：空串 = 成功，否则是给人看的失败原因（调用方必须把它显示出来） */
    fun why(name: String, text: String): String {
        //外部存储没就绪时 ensure() 会退回私有目录：这时写下去就有两份数据源，
        //等外部恢复后两边互相覆盖，配置就这么"少了一条"。宁可不写，也不写错地方
        if (Paths.ensure() != Paths.base()) return "外部存储还没挂上，没写进磁盘"
        val f = Paths.file(name)
        val dir = f.parentFile ?: return "取不到目录：$f"
        val t = File(dir, "$name.tmp")
        val w = runCatching { t.writeText(text) }
        if (w.isFailure) {
            runCatching { t.delete() }
            return "没写进磁盘：${f.absolutePath} 的临时文件建不了（目录不可写）"
        }
        //先写临时文件再改名：进程被杀只会留下完整的旧档或完整的新档，不会留半截 JSON
        if (t.renameTo(f)) return ""
        //改名不行就原地覆盖。这里不许先删原档：删了再失败就是彻底丢数据
        val direct = runCatching { f.writeText(text) }
        runCatching { t.delete() }
        if (direct.isSuccess) return ""
        return "没写进磁盘：${f.absolutePath} 覆盖不了（属主是别的应用，本应用只有读权限）"
    } //写盘并说明原因

    fun write(name: String, text: String): Boolean = why(name, text).isEmpty()
    fun exists(name: String): Boolean = Paths.file(name).isFile
} //根目录小文件