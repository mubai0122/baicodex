package b.ai.core.cfg.emoji

import b.ai.core.cfg.io.Paths
import b.ai.core.cfg.io.Store
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自定义表情：内置一套 Unicode 表情打底，用户可以按分类增删、给任意条目换成自己的图片。
 * AI 回复里写 [e:名字] 就会被渲染成对应表情（有图用图，没图用内置字符）。
 * 图片落在 baicode/emoji 下，索引存数据根目录的 emoji_index.json。
 */
object Emojis {
    /** emoji 为空且 file 为空 = 条目被用户删掉的内置项，渲染时按不存在处理 */
    data class Item(val group: String, val name: String, val emoji: String = "", val file: String = "")

    sealed class Seg {
        data class Txt(val text: String) : Seg()
        data class Pic(val item: Item) : Seg()
    }

    private const val INDEX = "emoji_index.json"
    private const val DIR = "emoji"
    private val MARK = Regex("\\[e:([^\\]]{1,24})\\]")

    /** 内置：分组名 → 名字 to 字符。用户改过图的存在 custom 里覆盖 */
    private val BUILTIN: List<Item> = listOf(
        Item("情绪", "开心", "😄"), Item("情绪", "难过", "😢"), Item("情绪", "生气", "😠"),
        Item("情绪", "惊讶", "😮"), Item("情绪", "无语", "😑"), Item("情绪", "害羞", "😳"),
        Item("情绪", "偷笑", "🤭"), Item("情绪", "困", "😪"),
        Item("动作", "点赞", "👍"), Item("动作", "鼓掌", "👏"), Item("动作", "加油", "💪"),
        Item("动作", "思考", "🤔"), Item("动作", "ok", "👌"), Item("动作", "握手", "🤝"),
        Item("动作", "敬礼", "🫡"), Item("动作", "吃瓜", "🍉"),
        Item("形象", "狗头", "🐶"), Item("形象", "柴犬", "🐕"), Item("形象", "猫", "🐱"),
        Item("形象", "熊猫", "🐼"), Item("形象", "玫瑰", "🌹"), Item("形象", "爱心", "❤️"),
        Item("形象", "烟花", "🎆"), Item("形象", "火", "🔥")
    )

    private var custom: List<Item>? = null

    fun dir(): File = Paths.dir(DIR)

    private fun customList(): List<Item> {
        val c = custom
        if (c != null) return c
        val l = load()
        custom = l
        return l
    } //用户改过的条目

    private fun merged(): List<Item> {
        val cust = customList()
        val over = cust.associateBy { it.name }
        val out = ArrayList<Item>()
        BUILTIN.forEach { b -> out.add(over[b.name] ?: b) }
        //内置里没有的都是用户新建的
        cust.forEach { if (BUILTIN.none { b -> b.name == it.name }) out.add(it) }
        return out
    } //内置 +自定义合并

    fun all(): List<Item> = merged()

    /** 按分类分组：保持内置顺序，用户新分类排后面 */
    fun groups(): List<Pair<String, List<Item>>> {
        val order = ArrayList<String>()
        BUILTIN.forEach { if (it.group !in order) order.add(it.group) }
        merged().forEach { if (it.group !in order) order.add(it.group) }
        return order.map { g -> g to merged().filter { it.group == g } }.filter { it.second.isNotEmpty() }
    } //分组列表

    fun find(name: String): Item? = merged().firstOrNull { it.name == name && (it.emoji.isNotBlank() || it.file.isNotBlank()) }

    /** 新建或改一条：同名覆盖，图片路径传空就是纯字符表情 */
    fun upsert(group: String, name: String, emoji: String, file: String) {
        val g = group.trim().ifBlank { "自定义" }
        val n = name.trim().ifBlank { return }
        val cur = customList().filterNot { it.name == n }
        custom = cur + Item(g, n, emoji.trim(), file)
        save()
    } //新增或改

    /** 只给某条换图片，其余字段不动；内置条目换图后可以重置回来 */
    fun setImage(name: String, file: String) {
        val it0 = merged().firstOrNull { it.name == name } ?: return
        upsert(it0.group, it0.name, if (file.isBlank()) it0.emoji else "", file)
    } //换图

    /** 删一条：内置的标记成空（等于隐藏），自定义的直接丢 */
    fun remove(name: String) {
        val hit = merged().firstOrNull { it.name == name } ?: return
        val isBuiltin = BUILTIN.any { it.name == name }
        val rest = customList().filterNot { it.name == name }
        custom = if (isBuiltin) rest + Item(hit.group, name, "", "") else rest
        fileOf(hit).takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        save()
    } //删除

    /** 重置：清掉所有自定义与图片，回到出厂那套 */
    fun reset() {
        customList().forEach { i -> i.file.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } } }
        custom = emptyList()
        runCatching { dir().listFiles()?.forEach { it.delete() } }
        save()
    } //恢复内置

    /** 把回复文本切成文字段与表情段；认不出的标记原样留在文字里 */
    fun split(text: String): List<Seg> {
        if (!text.contains("[e:")) return listOf(Seg.Txt(text))
        val out = ArrayList<Seg>()
        var last = 0
        MARK.findAll(text).forEach { m ->
            val item = find(m.groupValues[1].trim())
            if (item == null) return@forEach
            if (m.range.first > last) out.add(Seg.Txt(text.substring(last, m.range.first)))
            out.add(Seg.Pic(item))
            last = m.range.last + 1
        }
        if (last < text.length) out.add(Seg.Txt(text.substring(last)))
        if (out.isEmpty()) return listOf(Seg.Txt(text))
        return out
    } //切分标记

    fun fileOf(i: Item): String = i.file

    /** 给系统提示用：列出可用标记，并限定使用频率，避免满屏表情 */
    fun prompt(): String {
        val names = all().filter { it.emoji.isNotBlank() || it.file.isNotBlank() }.joinToString(" / ") { it.name }
        if (names.isBlank()) return ""
        return "表情：需要表达情绪时可写 [e:名字]，名字只能用这些：$names。" +
            "一条回复最多一个、只在合适的位置放，不要每句都加，也不要自己编名字。\n"
    } //系统提示段

    private fun load(): List<Item> {
        val raw = Store.read(INDEX) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                if (name.isBlank()) return@mapNotNull null
                Item(o.optString("group").ifBlank { "自定义" }, name, o.optString("emoji"), o.optString("file"))
            }
        }.getOrDefault(emptyList())
    } //读索引

    private fun save() {
        val arr = JSONArray()
        customList().forEach { i ->
            arr.put(JSONObject().put("group", i.group).put("name", i.name).put("emoji", i.emoji).put("file", i.file))
        }
        Store.why(INDEX, arr.toString(2))
    } //写索引

    /** 导入一张图：拷进 emoji 目录，返回落地路径（失败返回空串） */
    fun importImage(bytes: ByteArray, name: String): String {
        val d = dir()
        if (!d.isDirectory && !d.mkdirs()) return ""
        val f = File(d, "e_" + Integer.toHexString(name.hashCode()) + ".png")
        return if (runCatching { f.writeBytes(bytes) }.isSuccess) f.absolutePath else ""
    } //存图

    /** 分组名候选：给「新建」时的下拉用 */
    fun groupNames(): List<String> = groups().map { it.first }
} //自定义表情