package b.ai.ui.screen.settings

import androidx.compose.runtime.mutableStateListOf
import b.ai.R

/**
 * 设置二级页面：底栏分类（不变）→ 分类页列出子页 → 点子页进去。
 * 栈只存页面 id，页面内容仍由 SettingsScreen 按 id 渲染；栈放在单例里，
 * 因为返回手势在 MainActivity 处理，它要知道「设置页里还能不能再退一层」。
 */
data class SePage(
    val id: String,
    val titleRes: Int,
    val subRes: Int = 0,
    /** 0 模型 / 1 对话 / 2 外观 / 3 扩展 / 4 统计 / 5 关于 */
    val cat: Int = 0,
    /** true = 该分类只有这一页，点底栏直接渲染内容，不出分类页（统计 / 关于） */
    val direct: Boolean = false,
    /** 非空 = 这一页不列在分类页上，挂在指定父页下（扩展 → 生态与专区 → 各专区） */
    val parent: String? = null
)

object SeNav {
    /** 当前打开的二级页栈；空 = 停在分类页 */
    val stack = mutableStateListOf<SePage>()

    val top: SePage? get() = stack.lastOrNull()

    fun push(p: SePage) {
        //同一页不重复叠：搜索命中会先 push 再回落，避免栈里堆一串同一个 id
        if (top?.id == p.id) return
        stack.add(p)
    } //进子页

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    } //退一层，退到底返回 false

    fun clear() {
        stack.clear()
    } //回分类页（切底栏 / 退出设置时调）
}

/** 分类页上每一行对应的子页。一个分类只有一页时不给它建子页，SettingsScreen 直接渲染内容。 */
val SePages = listOf(
    //模型
    SePage("model_cfg", R.string.se_pg_model_cfg, R.string.se_pg_model_cfg_sub, 0),
    SePage("model_param", R.string.se_pg_model_param, R.string.se_pg_model_param_sub, 0),
    SePage("model_voice", R.string.se_pg_voice, R.string.se_pg_voice_sub, 0),
    SePage("model_mem", R.string.se_pg_mem, R.string.se_pg_mem_sub, 0),
    //对话
    SePage("chat_ctx", R.string.se_pg_chat_ctx, R.string.se_pg_chat_ctx_sub, 1),
    SePage("chat_inject", R.string.se_pg_chat_inject, R.string.se_pg_chat_inject_sub, 1),
    SePage("chat_session", R.string.se_pg_chat_session, R.string.se_pg_chat_session_sub, 1),
    SePage("chat_perm", R.string.se_pg_perm, R.string.se_pg_perm_sub, 1),
    //外观
    SePage("ui_theme", R.string.se_pg_ui_theme, R.string.se_pg_ui_theme_sub, 2),
    SePage("ui_person", R.string.se_pg_ui_person, R.string.se_pg_ui_person_sub, 2),
    SePage("ui_input", R.string.se_pg_ui_input, R.string.se_pg_ui_input_sub, 2),
    SePage("ui_nav", R.string.se_037, R.string.se2_back_sub, 2),
    //扩展
    SePage("ext_auto", R.string.se_pg_ext_auto, R.string.se_pg_ext_auto_sub, 3),
    SePage("ext_agent", R.string.se_pg_agent, R.string.se_pg_agent_sub, 3),
    SePage("ext_link", R.string.se_pg_ext_link, R.string.se_pg_ext_link_sub, 3),
    SePage("ext_tool", R.string.se_050, R.string.se_235, 3),
    SePage("ext_ws", R.string.se_pg_ext_ws, R.string.se_pg_ext_ws_sub, 3),
    SePage("ext_hub", R.string.se_hub, R.string.se_hub_sub, 3),
    //生态与专区下的各专区：不列在扩展分类页上，只在 ext_hub 里列
    SePage("ext_mi", R.string.se_hub_mi, R.string.se_pg_mi_sub, 3, parent = "ext_hub"),
    SePage("ext_mi_login", R.string.se_pg_mi_login, R.string.se_pg_mi_login_sub, 3, parent = "ext_mi"),
    SePage("ext_mi_dev", R.string.se_pg_mi_dev, R.string.se_pg_mi_dev_sub, 3, parent = "ext_mi"),
    SePage("ext_mi_ch", R.string.se_pg_mi_ch, R.string.se_pg_mi_ch_sub, 3, parent = "ext_mi"),
    SePage("ext_music", R.string.se_hub_music, R.string.se_pg_music_sub, 3, parent = "ext_hub"),
    SePage("ext_lan", R.string.se_hub_lan, R.string.se_pg_lan_sub, 3, parent = "ext_hub"),
    SePage("ext_box", R.string.se_hub_box, R.string.se_pg_box_sub, 3, parent = "ext_hub"),
    //统计 / 关于：分类里只有一页，点底栏直接进内容
    SePage("stat", R.string.se_004, 0, 4, direct = true),
    SePage("about", R.string.se_006, 0, 5, direct = true)
) //子页定义

/** 某个底栏分类下直接挂的子页（parent 不为空的挂在父页里，不进这一层） */
fun sePagesOf(cat: Int): List<SePage> = SePages.filter { it.cat == cat && it.parent == null }

/** 某个页下挂的下一级入口（目前只有 ext_hub 用） */
fun seChildren(id: String): List<SePage> = SePages.filter { it.parent == id }

fun sePage(id: String): SePage? = SePages.firstOrNull { it.id == id }

/** 某个底栏分类的默认子页：搜索命中但没指明具体页时，退到它 */
fun seDefaultPage(cat: Int): SePage? = sePagesOf(cat).firstOrNull()

/** 页面 id → 底栏分类下标，跨页跳转要先把底栏也切过去 */
fun seCatOf(id: String): Int = sePage(id)?.cat ?: 0

/**
 * 子页 key → 内容分区。一个分类可能拆多个子页，但内容仍由同一批
 * modelTab / chatTab / uiTab / extTab 函数渲染，只是按 key 过滤出对应分区。
 * 底栏分类 → 该分类「默认分区」的 key（点底栏直达时用）。
 */
val seDefaultKeyByCat = mapOf(0 to "model_cfg", 1 to "chat_ctx", 2 to "ui_theme", 3 to "ext_auto")