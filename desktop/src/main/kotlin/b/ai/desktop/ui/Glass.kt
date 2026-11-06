package b.ai.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object GlassTheme {
    val bg0 = Color(0xFF0A0C11)
    val bg1 = Color(0xFF151A26)
    val text = Color(0xFFE9EDF5)
    val sub = Color(0xFF98A3B8)
    val accent = Color(0xFF5B8CFF)
    val line = Color(0x1FFFFFFF)
    val radius = 16.dp
}

//液态玻璃近似：半透明渐变填充 + 上亮下暗描边。接 backdrop-desktop 后整段换成真模糊
fun Modifier.glass(shape: Shape = RoundedCornerShape(GlassTheme.radius), tint: Float = 0.10f): Modifier =
    this.clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = tint), Color.White.copy(alpha = tint * 0.35f))
            )
        )
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))
            ),
            shape
        ) //玻璃面板底