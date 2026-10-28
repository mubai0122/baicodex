package b.ai.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 插件安装：把下载下来的 zip 解到 baicode/skill 下，成为一个技能包目录。
 * 只认两种结构——根下直接放 skill.json / SKILL.md，或者整个包裹在一层同名目录里。
 * 解压前逐条校验路径，专门挡 ../ 穿越与解压炸弹；装完立刻重扫技能包。
 */
object PluginInstall {
    private const val TMP = "plugin_tmp"
    private const val ENTRY_MAX = 2000
    private const val BYTES_MAX = 64L * 1024 * 1024

    /** 安装结果：ok 时 dir 是最终落地的包目录，否则 msg 是能直接显示给用户的原因 */
    data class Result(val ok: Boolean, val dir: File? = null, val msg: String = "")

    private fun tmpRoot(): File = Paths.dir(TMP)

    /** 目录名安全化：只留字母数字中划线下划线点与中文，长度压到 48 */
    private fun safe(name: String): String {
        val s = name.trim().replace(Regex("[^\\w\\u4e00-\\u9fa5.-]+"), "_").trim('_', '.', '-')
        return (s.ifBlank { "plugin-" + System.currentTimeMillis().toString(36) }).take(48)
    } //安全目录名

    /** 在 skillRoot 下挑一个不撞名的目录名 */
    private fun freeName(root: File, base: String): String {
        var n = base
        var i = 1
        while (File(root, n).exists() && i < 999) {
            i++
            n = base + "-" + i
        }
        return n
    } //不撞名的目录名

    /**
     * 解压到临时目录 → 找出真正的包根（根或唯一的一层子目录）→ 移到 skillRoot。
     * $hint 是服务端给的插件名，包里没有 id / name 时拿它当目录名。
     */
    fun install(ctx: Context, zip: File, hint: String): Result {
        val root = Paths.skillRoot()
        val tmp = File(tmpRoot(), "i" + System.currentTimeMillis().toString(36))
        var total = 0L
        var count = 0
        return try {
            tmp.mkdirs()
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val raw = e.name.replace('\\', '/')
                    if (raw.isBlank() || raw.startsWith("/") || Regex("(^|/)\\.\\.(/|$)").containsMatchIn(raw)) {
                        return Result(false, msg = "包内有非法路径：" + raw.take(40))
                    }
                    if (e.isDirectory) continue
                    if (++count > ENTRY_MAX) return Result(false, msg = "包内文件太多（上限 2000 个）")
                    val dst = File(tmp, raw)
                    //再验一次真实落地路径必须在临时目录里，符号链接与怪异名字也挡掉
                    if (!dst.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                        return Result(false, msg = "包内有非法路径：" + raw.take(40))
                    }
                    dst.parentFile?.mkdirs()
                    dst.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = z.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > BYTES_MAX) return Result(false, msg = "解压后体积过大，拒绝安装")
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            val packRoot = packRootOf(tmp) ?: return Result(false, msg = "包里没有 skill.json 或 SKILL.md，不是插件包")
            val name = freeName(root, safe(dirNameOf(packRoot, hint)))
            val dst = File(root, name)
            if (!packRoot.renameTo(dst)) {
                //跨文件系统或权限问题时退回复制再删
                packRoot.copyRecursively(dst, overwrite = true)
                packRoot.deleteRecursively()
            }
            SkillPacks.reload(ctx)
            Result(true, dst, name)
        } catch (t: Throwable) {
            Result(false, msg = t.message?.take(80) ?: "解压失败")
        } finally {
            //临时目录整个清掉，成功时内容已经搬走了
            runCatching { tmp.deleteRecursively() }
        }
    } //安装插件包

    /** 真正的包根：临时目录自己算，或者唯一的那层子目录 */
    private fun packRootOf(tmp: File): File? {
        fun hasManifest(d: File) = File(d, "skill.json").isFile || File(d, "SKILL.md").isFile
        if (hasManifest(tmp)) return tmp
        val kids = tmp.listFiles()?.filter { it.isDirectory }.orEmpty()
        val one = kids.singleOrNull() ?: return null
        return if (hasManifest(one)) one else null
    } //找包根

    /** 目录名：skill.json 的 id → 包里的 name → 服务端插件名 */
    private fun dirNameOf(packRoot: File, hint: String): String {
        val jf = File(packRoot, "skill.json")
        if (jf.isFile) {
            runCatching {
                val o = JSONObject(jf.readText())
                val id = o.optString("id").trim()
                if (id.isNotEmpty()) return id
                val n = o.optString("name").trim()
                if (n.isNotEmpty()) return n
            }
        }
        val md = File(packRoot, "SKILL.md")
        if (md.isFile) {
            runCatching {
                md.useLines { lines ->
                    var i = 0
                    for (l in lines) {
                        if (i++ > 8) break
                        val t = l.trim()
                        if (t.startsWith("name:")) {
                            val v = t.removePrefix("name:").trim().trim('"', '\'')
                            if (v.isNotEmpty()) return v
                        }
                    }
                }
            }
        }
        return hint.ifBlank { packRoot.name }
    } //包目录名
} //插件安装