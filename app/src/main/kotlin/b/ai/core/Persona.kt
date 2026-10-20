package b.ai.core

import b.ai.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 一张角色卡：身份 + 简介 + 人设 + 场景 + 开场白 + 示例对话 + 后置指令，可绑一套模型配置。
 * 字段全给默认值，老版本只存了 name / prompt 的 personas.json 读进来不会炸。
 */
data class Persona(
    val id: String,
    val name: String,
    val avatar: String = "",
    val desc: String = "",
    val prompt: String = "",
    val scenario: String = "",
    val firstMsg: String = "",
    val examples: String = "",
    val postHistory: String = "",
    val profileId: String = ""
) {
} //角色卡

/** 角色卡库：整表 JSON 落在数据根 personas.json，当前选中的 id 记在 Cfg.personaId；默认空 */
object Personas {
    /** 数据根里的文件名，用户可以直接改 */
    const val FILE = "personas.json"

    /** 内置角色版本号：调大才会补一次新预置，用户删掉的不会复活 */
    const val PRESET_SEED = 2

    /** 内置角色卡：按 name 对齐，缺的补卡、缺的字段补值，不覆盖用户改过的正文 */
    private val PRESETS = listOf(
        Persona(
            id = "",
            name = "代码搭子",
            desc = "话少的资深工程师，只给代码和关键那一句",
            prompt = "你是一个话少的资深工程师。给代码为主，说明只写关键的一句为什么，不写客套话、不写总结。" +
                "改代码给最小 diff，不要整文件重排。遇到用户说法有技术错误，先一句话指出，再给证据。",
            scenario = "用户贴代码、甩报错、问「为什么挂了」的时候。",
            firstMsg = "要改哪个文件？贴代码或者给路径。",
            examples = "用户：这个协程为什么泄漏\n助手：因为 scope 是 activity 级的，页面销毁没 cancel。改成 lifecycleScope 或手动 cancel。",
            postHistory = "回答控制在 30 行内；不确定的 API 直接说不确定，不要编。"
        ),
        Persona(
            id = "",
            name = "严谨审阅",
            desc = "先挑问题再给结论，每条都带依据",
            prompt = "你是审稿人。先挑问题再给结论：按严重程度列出风险，每条写清原因与依据；" +
                "不确定就明说「我不确定」并给出验证方法。禁止编造 API、版本与事实。",
            scenario = "评审方案、代码、文档，或要判断某个结论站不站得住的时候。",
            firstMsg = "把要审的东西贴上来，我先按严重程度列风险，再给结论。",
            examples = "用户：这个方案能上线吗\n助手：能，但两处要先修：①…（阻塞，原因：…）②…（建议，原因：…）",
            postHistory = "结论必须可验证：给出验证命令或复现步骤，不接受「应该没问题」。"
        ),
        Persona(
            id = "",
            name = "运维值班",
            desc = "先给命令再说后果，破坏性操作先亮红牌",
            prompt = "你是运维值班工程师。所有操作先给可执行命令并说明后果，破坏性命令（删除、覆盖、重启服务）必须先提示风险。" +
                "输出以命令与结果为主，少解释。",
            scenario = "服务器、容器、数据库、线上排障的时候。",
            firstMsg = "什么现象？把报错和最近改动给我，我先给只读命令。",
            examples = "用户：磁盘满了\n助手：先只读排查 df -h / 和 du -xh -d1 / | sort -h；删文件前先确认不是日志轮转里的活文件。",
            postHistory = "不给 rm -rf、DROP、systemctl restart 这类命令而不加风险说明。"
        ),
        Persona(
            id = "",
            name = "文字打磨",
            desc = "只给成品，不加点评",
            prompt = "你是文字编辑。保留原意，删冗词，把口语顺手整理成可读的书面表达。直接给成品，不加点评、不加前言后语。",
            scenario = "润色文案、改写邮件、把口述整理成成稿的时候。",
            postHistory = "只输出改写后的正文，一句话也不点评。"
        ),
        Persona(
            id = "",
            name = "口语翻译",
            desc = "只给译文，专名首次出现带原文",
            prompt = "你是翻译。只给译文，不加解释；专有名词第一次出现时在括号里保留原文。中英互译默认按目标语言自然语序改写，不做逐字直译。",
            scenario = "中英互译、对照术语的时候。",
            postHistory = "不加「译文如下」之类的前后缀。"
        )
    )

    /**内置角色卡的界面文案：中文锚点 → 名称 / 简介 / 开场白，库里仍存中文原文 */
    private data class PresetText(val name: Int, val desc: Int, val opener: Int)

    private val TEXTS = mapOf(
        "代码搭子" to PresetText(R.string.per_n1, R.string.per_d1, R.string.per_o1),
        "严谨审阅" to PresetText(R.string.per_n2, R.string.per_d2, R.string.per_o2),
        "运维值班" to PresetText(R.string.per_n3, R.string.per_d3, R.string.per_o3),
        "文字打磨" to PresetText(R.string.per_n4, R.string.per_d4, 0),
        "口语翻译" to PresetText(R.string.per_n5, R.string.per_d5, 0)
    )

    private val cache = mutableStateOf<List<Persona>>(emptyList())
    private var sp: SharedPreferences? = null
    private var app: Context? = null

    val list: List<Persona> get() = cache.value

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        app = c.applicationContext
        //文件优先；没有文件就迁移旧配置并写出去
        val fromFile = Store.read(FILE)
        if (fromFile != null) {
            cache.value = runCatching { parse(fromFile) }.getOrDefault(emptyList())
        } else {
            cache.value = runCatching { parse(p.getString("personas", "")) }.getOrDefault(emptyList())
            if (cache.value.isNotEmpty()) save()
        }
        seedPresets()
    } //读盘（文件优先）

    /** 补一次内置角色卡：新卡按名字追加，老卡只补空字段，用户写过的正文不动 */
    private fun seedPresets() {
        if (Cfg.personaSeed >= PRESET_SEED) return
        val out = cache.value.toMutableList()
        var changed = false
        PRESETS.forEach { pre ->
            val i = out.indexOfFirst { it.name == pre.name }
            if (i < 0) {
                out.add(pre.copy(id = UUID.randomUUID().toString()))
                changed = true
            } else {
                val old = out[i]
                val merged = old.copy(
                    avatar = old.avatar.ifBlank { pre.avatar },
                    desc = old.desc.ifBlank { pre.desc },
                    scenario = old.scenario.ifBlank { pre.scenario },
                    firstMsg = old.firstMsg.ifBlank { pre.firstMsg },
                    examples = old.examples.ifBlank { pre.examples },
                    postHistory = old.postHistory.ifBlank { pre.postHistory }
                )
                if (merged != old) {
                    out[i] = merged
                    changed = true
                }
            }
        }
        if (changed) {
            cache.value = out
            save()
        }
        Cfg.personaSeed = PRESET_SEED
    } //补内置角色卡

    private fun parse(raw: String?): List<Persona> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            Persona(
                id = o.optString("id"),
                name = o.optString("name"),
                avatar = o.optString("avatar"),
                desc = o.optString("desc"),
                prompt = o.optString("prompt"),
                scenario = o.optString("scenario"),
                firstMsg = o.optString("first_msg"),
                examples = o.optString("examples"),
                postHistory = o.optString("post_history"),
                profileId = o.optString("profile_id")
            )
        }
    } //从 JSON 还原（缺字段一律空串）

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("avatar", p.avatar)
                    .put("desc", p.desc)
                    .put("prompt", p.prompt)
                    .put("scenario", p.scenario)
                    .put("first_msg", p.firstMsg)
                    .put("examples", p.examples)
                    .put("post_history", p.postHistory)
                    .put("profile_id", p.profileId)
            )
        }
        sp?.edit()?.putString("personas", arr.toString())?.apply()
        Store.write(FILE, arr.toString(2))
    } //整表落盘

    fun current(): Persona? = list.firstOrNull { it.id == Cfg.personaId }

    /** 新增一张角色卡，id 为空时自动补一个，返回落库后的卡 */
    fun add(p: Persona): Persona {
        if (p.name.isBlank() && p.prompt.isBlank()) return p
        val card = p.copy(
            id = p.id.ifBlank { UUID.randomUUID().toString() },
            name = p.name.ifBlank { res(R.string.per_unnamed) }
        )
        cache.value = cache.value + card
        save()
        Cfg.personaId = card.id
        return card
    } //新增并选中

    fun update(p: Persona) {
        cache.value = cache.value.map {
            if (it.id != p.id) it else p.copy(
                name = p.name.ifBlank { res(R.string.per_unnamed) },
                avatar = p.avatar.trim(),
                desc = p.desc.trim(),
                prompt = p.prompt.trim(),
                scenario = p.scenario.trim(),
                firstMsg = p.firstMsg.trim(),
                examples = p.examples.trim(),
                postHistory = p.postHistory.trim(),
                profileId = p.profileId
            )
        }
        save()
    } //改内容

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        if (Cfg.personaId == id) Cfg.personaId = ""
        save()
    } //删除，删的是当前角色就回默认

    fun select(id: String) {
        Cfg.personaId = id
    }

    /** 当前角色的提示正文；没选角色返回空串（默认助手什么都不加） */
    fun prompt(): String {
        val p = current() ?: return ""
        return buildString {
            if (p.name.isNotBlank()) append("你现在扮演「${p.name}」。\n")
            if (p.desc.isNotBlank()) append(p.desc).append("\n")
            if (p.prompt.isNotBlank()) append(p.prompt).append("\n")
            if (p.scenario.isNotBlank()) append("适用场景：").append(p.scenario).append("\n")
            //示例对话只当风格样本，明确告诉模型别照抄内容
            if (p.examples.isNotBlank()) {
                append("示例对话（只学语气与结构，不要照抄内容）：\n").append(p.examples).append("\n")
            }
            //后置指令放在最后，模型对末尾内容更敏感
            if (p.postHistory.isNotBlank()) append(p.postHistory).append("\n")
        }.trim()
    } //拼系统提示

    /**取一条资源文案：没有 Context（初始化前）时退回空串 */
    private fun res(id: Int): String = if (id == 0) "" else app?.getString(id).orEmpty()

    /**内置角色的中文原文；命中说明这张卡是预置的 */
    private fun presetOf(p: Persona): Persona? = PRESETS.firstOrNull { it.name == p.name.trim() }

    /**卡片标题：内置角色按当前语言取，用户自建或改过名字的原样返回 */
    fun displayName(p: Persona): String {
        val t = TEXTS[p.name.trim()] ?: return p.name
        return res(t.name).ifBlank { p.name }
    } //本地化角色名

    /**卡片简介：内置角色的简介没被改过才用译文 */
    fun displayDesc(p: Persona): String {
        val pre = presetOf(p) ?: return p.desc
        if (p.desc.trim() != pre.desc) return p.desc
        return res(TEXTS[p.name.trim()]?.desc ?: 0).ifBlank { p.desc }
    } //本地化角色简介

    /**开场白：内置角色的开场白没被改过才用译文，新会话落库的就是这个 */
    fun displayOpener(p: Persona): String {
        val pre = presetOf(p) ?: return p.firstMsg.trim()
        val raw = p.firstMsg.trim()
        if (raw.isNotEmpty() && raw != pre.firstMsg.trim()) return raw
        return res(TEXTS[p.name.trim()]?.opener ?: 0).ifBlank { raw }
    } //本地化开场白

    /** 列表副标题：优先一句话简介，没有就报人设字数 */
    fun brief(p: Persona): String {
        val d = displayDesc(p)
        return when {
            d.isNotBlank() -> d
            p.prompt.isNotBlank() -> app?.getString(R.string.per_brief_fmt, p.prompt.length).orEmpty()
            else -> res(R.string.per_empty)
        }
    } //列表副标题

    /** 当前角色的开场白：新会话建好后自动落一条 AI 消息，没有就返回空串 */
    fun opener(): String = current()?.let { displayOpener(it) }.orEmpty()
} //角色卡库