package b.ai.ui.screen.settings

import androidx.compose.runtime.mutableStateListOf
import b.ai.R

/**
 * 设置二级页面：底栏分类（不变）→ 分类页列出子页 → 点子页进去。
 * 栈只存页面 id，页面内容仍由 SettingsScreen 按 id 渲染；栈放在单例里，
 * 因为返回手势在 MainActivity 处理，它要知道「设置页里还能不能再退一层」。
 */
data class SePage(val id: String, val titleRes: Int, val subRes: Int = 0, val cat: Int = 0)

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

/** 分类页上每一行对应的子页；S1 阶段一个分类先只挂一页「全部」，后面按主题继续拆 */
val SePages = listOf(
    SePage("model", R.string.se_007, R.string.se_008, 0),
    SePage("chat", R.string.se_090, R.string.se_091, 1),
    SePage("ui", R.string.se_030, R.string.se_185, 2),
    SePage("ext", R.string.se_044, R.string.se2_task_sub, 3),
    SePage("hub", R.string.se_hub, R.string.se_hub_sub, 3),
    SePage("stat", R.string.se_005, 0, 4),
    SePage("about", R.string.se_006, 0, 5)
) //二级页定义

fun sePagesOf(cat: Int): List<SePage> = SePages.filter { it.cat == cat }

fun sePage(id: String): SePage? = SePages.firstOrNull { it.id == id }

/** 某个底栏分类的默认子页：搜索命中但没指明具体页时，退到它 */
fun seDefaultPage(cat: Int): SePage? = sePagesOf(cat).firstOrNull()

/** 页面 id → 底栏分类下标，跨页跳转要先把底栏也切过去 */
fun seCatOf(id: String): Int = sePage(id)?.cat ?: 0