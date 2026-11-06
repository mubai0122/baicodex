package b.ai.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import b.ai.desktop.core.ShellCore
import b.ai.desktop.ui.GlassTheme
import b.ai.desktop.ui.MainPane
import b.ai.desktop.ui.Sidebar
import b.ai.desktop.ui.TitleBar
import java.awt.Dimension

fun main() = application {
    val core = remember { ShellCore() }
    val state = rememberWindowState(size = DpSize(1280.dp, 820.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        undecorated = true,
        title = "BaiCodex",
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(960, 600) }
        val st = core.state
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(GlassTheme.bg0, GlassTheme.bg1)))
        ) {
            TitleBar(
                state = state,
                title = st.tabs.firstOrNull { it.id == st.active }?.title ?: "桌面版",
                onClose = { exitApplication() }
            )
            Row(Modifier.fillMaxSize()) {
                Sidebar(core)
                Box(Modifier.width(1.dp).fillMaxHeight().background(GlassTheme.line))
                MainPane(core, Modifier.weight(1f))
            }
        }
    }
} //桌面入口：无边框窗口 + 自绘标题栏 + 侧栏/主区两栏