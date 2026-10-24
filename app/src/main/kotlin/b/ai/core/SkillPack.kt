package b.ai.core

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 技能包里的一个可调用函数：模板体在加载时就 lint 好，坏包不删但不可调用 */
data class SkillFn(
    val skill: String, //所属技能 id
    val name: String, //全局工具名，直接注册成工具
    val kind: String, //cmd / http
    val desc: String,
    val params: Map<String, String>, //参数名 → 正则
    val file: String, //function/ 下的文件名
    val body: String, //模板体原文
    val bad: String = "" //非空即校验没过，不可调用
)

/** 一个技能包：baicode/skill 下的文件夹，或旧 skills.json 里的 body 型技能 */
data class SkillPack(
    val id: String,
    val name: String,
    val desc: String,
    val readme: String, //skill_read 返回的全文
    val funcs: List<SkillFn>,
    val dir: String, //包目录，旧数据为空串
    val on: Boolean
)

/**
 * 技能包注册表：外部目录 + 旧 skills.json 合成一份清单。
 * 系统提示只放清单（id — 名字：说明），正文由 skill_read 现场取，省 token。
 */
object SkillPacks {
    private val cache = mutableStateOf<List<SkillPack>>(emptyList())

    val packs: List<SkillPack> get() = cache.value

    /** 可暴露给模型的函数：所属技能启用、且 lint 通过 */
    val funcs: List<SkillFn>
        get() = cache.value.filter { it.on }.flatMap { it.funcs }.filter { it.bad.isEmpty() }

    /**
     * 重新扫描：外部包目录优先，旧 skills.json 的 body 型技能作为「只有 README 没有函数」的包补进来。
     * 同名 id 外部包覆盖旧数据。
     */
    fun reload(c: Context) {
        seed(c)
        val out = ArrayList<SkillPack>()
        runCatching {
            Paths.skillRoot().listFiles()?.sortedBy { it.name }?.forEach { d ->
                if (d.isDirectory) loadDir(d)?.let { out.add(it) }
            }
        }
        runCatching {
            Skills.list.forEach { s ->
                if (out.any { it.id == s.id }) return@forEach
                out.add(SkillPack(s.id, s.name, s.desc, s.body, emptyList(), "", s.on))
            }
        }
        cache.value = out
    } //重扫技能包

    /** 加载一个包目录：skill.json 是唯一权威定义，缺字段一律按约定兜底 */
    private fun loadDir(d: File): SkillPack? {
        val jf = File(d, "skill.json")
        if (!jf.isFile) return loadMd(d)
        val o = runCatching { JSONObject(jf.readText()) }.getOrNull() ?: return null
        val id = o.optString("id").trim().ifBlank { d.name }
        val ver = o.optInt("dsl", 1)
        val readme = runCatching { File(d, "README.md").readText() }.getOrDefault("")
        val fnDir = File(d, "function")
        val list = ArrayList<SkillFn>()
        val arr = o.optJSONArray("funcs") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val name = f.optString("name").trim()
            if (name.isEmpty()) continue
            val kind = f.optString("kind").trim().ifEmpty { "cmd" }
            val file = f.optString("file").trim().ifEmpty { "$name.js" }
            val params = LinkedHashMap<String, String>()
            f.optJSONObject("params")?.let { p -> p.keys().forEach { k -> params[k] = p.optString(k) } }
            val body = runCatching { File(fnDir, file).readText() }.getOrNull()
            val bad = when {
                ver != 1 -> "不支持的 dsl 版本 $ver"
                body == null -> "缺 function/$file"
                else -> Dsl.lint(body, kind, params).orEmpty()
            }
            list.add(SkillFn(id, name, kind, f.optString("desc"), params, file, body.orEmpty(), bad))
        }
        return SkillPack(id, o.optString("name").trim().ifBlank { id }, o.optString("desc"), readme, list, d.absolutePath, true)
    } //加载单个包

    /** 通用 Agent Skills 格式：目录名就是技能名，SKILL.md 的 frontmatter 给名字与说明，正文当 README */
    private fun loadMd(d: File): SkillPack? {
        val mf = File(d, "SKILL.md")
        if (!mf.isFile) return null
        val raw = runCatching { mf.readText() }.getOrNull() ?: return null
        val (meta, body) = front(raw)
        val name = meta["name"]?.trim().orEmpty().ifBlank { d.name }
        return SkillPack(d.name, name, meta["description"]?.trim().orEmpty(), body.trim(), emptyList(), d.absolutePath, true)
    } //加载通用格式技能

    /** 拆 YAML frontmatter：只认顶层 key: value 与 > | 折行写法，够通用格式用；没有 frontmatter 就把整份当正文 */
    private fun front(raw: String): Pair<Map<String, String>, String> {
        val text = raw.removePrefix("\uFEFF")
        val lines = text.split("\n")
        if (lines.firstOrNull()?.trim() != "---") return emptyMap<String, String>() to text
        val map = LinkedHashMap<String, String>()
        var i = 1
        var key = ""
        val folded = StringBuilder()
        fun flush() {
            if (key.isNotEmpty() && folded.isNotEmpty()) map[key] = folded.toString().trim()
            key = ""
            folded.setLength(0)
        }
        while (i < lines.size) {
            val l = lines[i]
            if (l.trim() == "---") break
            if (l.startsWith(" ") || l.startsWith("\t")) {
                if (key.isNotEmpty()) {
                    if (folded.isNotEmpty()) folded.append(' ')
                    folded.append(l.trim())
                }
                i++
                continue
            }
            flush()
            val c = l.indexOf(':')
            if (c > 0) {
                val k = l.substring(0, c).trim().lowercase()
                val v = l.substring(c + 1).trim()
                when {
                    v == ">" || v == "|" || v == ">-" || v == "|-" -> key = k
                    v.length >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) ->
                        map[k] = v.substring(1, v.length - 1)
                    else -> map[k] = v
                }
            }
            i++
        }
        flush()
        val body = if (i < lines.size) lines.drop(i + 1).joinToString("\n") else ""
        return map to body
    } //解析 frontmatter

    /** 按 id 或名字找包：skill_read 用 */
    fun pack(key: String): SkillPack? {
        val k = key.trim()
        if (k.isEmpty()) return null
        return cache.value.firstOrNull { it.id == k }
            ?: cache.value.firstOrNull { it.name == k }
            ?: cache.value.firstOrNull { it.id.equals(k, true) || it.name.equals(k, true) }
    } //查包

    /** 按工具名找函数：同名只认第一个，避免两个包抢一个名字 */
    fun find(tool: String): SkillFn? = funcs.firstOrNull { it.name == tool }

    /** 系统提示里的清单：每条一行，不带正文 */
    fun prompt(): String {
        val on = cache.value.filter { it.on && (it.funcs.isNotEmpty() || it.readme.isNotBlank()) }
        if (on.isEmpty()) return ""
        return buildString {
            append("可用技能（要用法先 skill_read，再直接调它下面的函数；参数不要凭空编）：\n")
            on.forEach { p ->
                append("- ").append(p.id).append(" — ").append(p.name.ifBlank { "未命名技能" })
                if (p.desc.isNotBlank()) append("：").append(p.desc)
                val fns = p.funcs.joinToString("、") { if (it.bad.isEmpty()) it.name else "${it.name}（不可用）" }
                if (fns.isNotEmpty()) append("（函数：").append(fns).append("）")
                append("\n")
            }
        }.trim()
    } //技能清单

    /** 已经声明但校验没过的函数：设置页标红用 */
    fun broken(): List<SkillFn> = cache.value.flatMap { it.funcs }.filter { it.bad.isNotEmpty() }

    /** 模板里引用到的密钥名（DollarSecret.xxx 那种写法）：设置页据此生成可配置项 */
    fun secretsOf(body: String): List<String> {
        val head = '$' + "secret."
        val out = LinkedHashSet<String>()
        var i = body.indexOf(head)
        while (i >= 0) {
            var j = i + head.length
            val sb = StringBuilder()
            while (j < body.length && (body[j].isLetterOrDigit() || body[j] == '_')) {
                sb.append(body[j])
                j++
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
            i = body.indexOf(head, j)
        }
        return out.toList()
    } //模板里用到的密钥名

    /** 内置包：assets/skills 下一包一目录，外部没有同名目录时才铺过去，用户改过的不覆盖 */
    private fun seed(c: Context) {
        val am = c.assets
        val names = runCatching { am.list("skills")?.toList() }.getOrNull() ?: return
        val root = Paths.skillRoot()
        names.forEach { n ->
            val dst = File(root, n)
            if (!dst.exists()) {
                runCatching { copyRes(am, "skills/$n", dst) }
                return@forEach
            }
            //已存在的包只补 SKILL.md 这种新增说明文件，其余一律不动用户改过的内容
            val md = File(dst, "SKILL.md")
            if (!md.exists()) runCatching { copyRes(am, "skills/$n/SKILL.md", md) }
        }
    } //铺内置技能包

    /** 递归复制 assets 目录：list 为空说明当前是文件 */
    private fun copyRes(am: android.content.res.AssetManager, from: String, to: File) {
        val kids = runCatching { am.list(from) }.getOrNull().orEmpty()
        if (kids.isEmpty()) {
            to.parentFile?.mkdirs()
            am.open(from).use { i -> to.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        to.mkdirs()
        kids.forEach { k -> copyRes(am, "$from/$k", File(to, k)) }
    } //递归复制资源
} //技能包注册表