package b.ai.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState

//自绘标题栏：玻璃底 + 拖动区 + 三键。无边框窗口的边缘缩放要自己实现，见文件末 TODO
@Composable
fun WindowScope.TitleBar(state: WindowState, title: String, onClose: () -> Unit) {
    val maxed = state.placement == WindowPlacement.Maximized
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)))
    ) {
        WindowDraggableArea(Modifier.fillMaxWidth().height(40.dp)) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BaiCodex", color = GlassTheme.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(10.dp))
                Text(title, color = GlassTheme.sub, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).height(40.dp)) {
            WinButton("—") { state.isMinimized = true }
            WinButton(if (maxed) "❐" else "□") {
                state.placement = if (maxed) WindowPlacement.Floating else WindowPlacement.Maximized
            }
            WinButton("✕", danger = true, onClick = onClose)
        }
    }
} //标题栏

@Composable
private fun WinButton(glyph: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp, 40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            color = if (danger) Color(0xFFFF6B6B) else GlassTheme.sub,
            fontSize = 12.sp
        )
    }
} //窗口按钮