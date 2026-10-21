package b.ai.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 一条永久记忆 */
data class MemRow(val id: Long, val text: String, val at: Long)

/** 永久记忆：模型觉得重要的事实 / 偏好写进来，用户不删就每轮都随系统提示下发 */
object Memory {
    /** 界面直接读这份表，增删都先落库再刷新 */
    var rows by mutableStateOf<List<MemRow>>(emptyList())
        private set

    fun init() {
        rows = runCatching { Repo.memoryList() }.getOrDefault(emptyList())
    } //开机载入

    fun reload() {
        runCatching { Repo.memoryList() }.getOrNull()?.let { rows = it }
    } //重新读

    /** 同一条不重复记；返回 true 才是新写入 */
    fun add(text: String, sessionId: String): Boolean {
        val t = text.trim().replace("\n", " ")
        if (t.isEmpty()) return false
        if (rows.any { it.text == t }) return false
        val ok = runCatching { Repo.memoryAdd(t.take(500), sessionId) }.getOrDefault(false)
        if (ok) reload()
        return ok
    } //记一条

    fun delete(id: Long) {
        runCatching { Repo.memoryDelete(id) }
        reload()
    } //删一条

    fun clear() {
        runCatching { Repo.memoryClear() }
        rows = emptyList()
    } //全清

    /** 注入系统提示：一条一行；空表就什么都不加 */
    fun prompt(): String {
        if (rows.isEmpty()) return ""
        val sb = StringBuilder("长期记忆（用户没删就一直算数，与本轮对话同等可信）：\n")
        rows.forEach { sb.append("- ").append(it.text).append('\n') }
        return sb.toString()
    } //拼提示
} //永久记忆
