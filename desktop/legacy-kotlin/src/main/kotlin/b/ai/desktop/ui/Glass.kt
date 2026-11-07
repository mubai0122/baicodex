package b.ai.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

object GlassTheme {
    val bg0 = Color(0xFF0A0C11)
    val bg1 = Color(0xFF151A26)
    val text = Color(0xFFE9EDF5)
    val sub = Color(0xFF98A3B8)
    val accent = Color(0xFF5B8CFF)
    val line = Color(0x1FFFFFFF)
    val radius = 16.dp
}

/**玻璃采样源：只登记最底那层壁纸，玻璃自己不在里面，避免采样到自身 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**玻璃宿主：底层壁纸登记成采样纹理，上层内容里的玻璃才采得到东西 */
@Composable
fun GlassRoot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bd = rememberLayerBackdrop()
    Box(modifier) {
        Box(Modifier.fillMaxSize().layerBackdrop(bd)) { Wallpaper() }
        CompositionLocalProvider(LocalGlassBackdrop provides bd) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
} //玻璃宿主

/**壁纸：底色 + 三团光斑 + 细网格。纯渐变背后模糊了也看不出变化，网格与光斑才是液态感的来源 */
@Composable
private fun Wallpaper() {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(listOf(GlassTheme.bg0, GlassTheme.bg1)))
            spot(Color(0xFF3F6DFF), 0.22f, 0.16f, 0.62f, 0.42f)
            spot(Color(0xFF8B5CF6), 0.78f, 0.10f, 0.52f, 0.30f)
            spot(Color(0xFF14B8A6), 0.62f, 0.92f, 0.68f, 0.26f)
            grid()
        }
    )
} //壁纸

//细网格：给模糊提供高频细节，没有它玻璃后面是一块平滑色，糊不糊看着一样
private fun DrawScope.grid(step: Float = 26f) {
    val ink = Color.White.copy(alpha = 0.035f)
    var x = 0f
    while (x < size.width) {
        drawLine(ink, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(ink, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
} //细网格

//一团径向光斑：cx/cy 是相对尺寸，r 相对短边
private fun DrawScope.spot(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.minDimension * r
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
} //光斑

/**真液态玻璃：采样壁纸 -> 提饱和 -> 模糊 -> 高光边 -> 叠色。没有采样源时退回轻档 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(GlassTheme.radius),
    tint: Float = 0.06f,
    blurRadius: Dp = 20.dp
): Modifier {
    val bd = LocalGlassBackdrop.current
    if (bd == null) return glassLite(shape, tint)
    return drawBackdrop(
        backdrop = bd,
        shape = { shape },
        effects = {
            //采样边距必须大于模糊半径，否则边缘会采样不足发黑
            padding = maxOf(padding, 24.dp.toPx())
            vibrancy()
            if (blurRadius > 0.dp) blur(blurRadius.toPx())
        },
        highlight = { Highlight.Default },
        onDrawSurface = { drawRect(Color.White.copy(alpha = tint)) }
    )
} //液态玻璃

//轻档：没有 backdrop 时只画半透明渐变 + 上亮下暗描边，不建 RenderEffect
private fun Modifier.glassLite(shape: Shape, tint: Float): Modifier =
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
        ) //轻档玻璃