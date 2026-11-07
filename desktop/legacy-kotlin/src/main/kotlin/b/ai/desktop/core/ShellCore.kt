package b.ai.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 会话元信息：桌面侧只放侧栏要用的字段，正文放 Rust 领域态 */
data class Session(val id: String, val title: String)

/** 一次界面快照：Rust 推什么 UI 就画什么，UI 只读 */
data class UiState(
    val tabs: List<Session> = emptyList(),
    val active: String? = null,
    val recent: List<Session> = emptyList(),
)

/** UI 唯一能做的事：发意图。不直接改状态 */
sealed interface Intent {
    data object NewTab : Intent
    data class SelectTab(val id: String) : Intent
    data class CloseTab(val id: String) : Intent
    data object NewSession : Intent
}

/**
 * 现在只是内存桩：dispatch 直接本地算，目的是先把界面形状和意图枚举定下来。
 * 接 Rust 时这一层整体换成 uniffi 生成的绑定：dispatch 走 FFI 单向调用，
 * state 由 Rust 回调推过来，UI 侧代码一行不用改
 */
class ShellCore {
    var state by mutableStateOf(UiState())
        private set

    private var seq = 0

    fun dispatch(intent: Intent) {
        when (intent) {
            Intent.NewTab -> open("新会话")
            Intent.NewSession -> open("新会话")
            is Intent.SelectTab -> state = state.copy(active = intent.id)
            is Intent.CloseTab -> {
                val tabs = state.tabs.filterNot { it.id == intent.id }
                val active = if (state.active == intent.id) tabs.lastOrNull()?.id else state.active
                state = state.copy(tabs = tabs, active = active)
            }
        }
    } //意图分发：Rust 接入后这里只做序列化

    private fun open(title: String) {
        val s = Session("t${++seq}", title)
        state = state.copy(tabs = state.tabs + s, active = s.id, recent = state.recent + s)
    } //开一个标签
}