package b.ai.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import b.ai.ui.theme.TintGradient
import kotlin.math.min

/** 默认光效色：淡青 → 淡粉；有主题渐变时直接走主题色 */
private val GlowColors = listOf(Color(0xFF9AE6E0), Color(0xFFF7C9DD))

/**
 * 语音边框光效：progress 0→1 时从顶部中点（屏幕左右 1/2 处）出发，
 * 沿左右两侧同时向外延伸、再一路向下，直到整圈边框点亮。
 * 主光 6dp 一次画完整段（不分段，不会有格子感），外面用 BlurMaskFilter 糊两层真散光；
 * 系统低于 28 时硬件画布不支持模糊，退化成整段更宽的淡描边。
 */
@Composable
internal fun EdgeGlow(progress: Float, modifier: Modifier = Modifier, width: Dp = 6.dp) {
    if (progress <= 0.001f) return
    val colors = TintGradient ?: GlowColors
    Canvas(modifier.fillMaxSize()) {
        val stroke = width.toPx()
        val inset = stroke / 2f
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        //圆角跟着机身走，太小的屏自动收窄，别把轮廓吃成椭圆
        val r = min(min(w, h) / 8f, inset + 42.dp.toPx())
        val left = inset
        val top = inset
        val right = w - inset
        val bottom = h - inset
        //一条从顶部中点出发、顺时针绕满一圈的圆角矩形轮廓：前半圈是右半边，后半圈回到中点
        val outline = Path().apply {
            moveTo(w / 2f, top)
            lineTo(right - r, top)
            arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)
            lineTo(right, bottom - r)
            arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
            lineTo(left + r, bottom)
            arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
            lineTo(left, top + r)
            arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
            lineTo(w / 2f, top)
            close()
        }
        val pm = PathMeasure().apply { setPath(outline, false) }
        val total = pm.length
        if (total <= 0f) return@Canvas
        //左右两侧同步生长：各走半圈的一半
        val reach = total / 2f * progress.coerceIn(0f, 1f)
        if (reach <= 0f) return@Canvas
        //整段成形：同宽同色，中途不切段，所以没有一格格跳变的感觉
        val rightSide = Path().also { pm.getSegment(0f, reach, it, true) }.asAndroidPath()
        val leftSide = Path().also { pm.getSegment(total - reach, total, it, true) }.asAndroidPath()
        val lg = android.graphics.LinearGradient(
            left, top, right, bottom,
            colors.map { it.toArgb() }.toIntArray(), null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val nc = drawContext.canvas.nativeCanvas
        val core = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = android.graphics.Paint.Cap.ROUND
            shader = lg
        }
        val canBlur = android.os.Build.VERSION.SDK_INT >= 28
        //散光：两层不同半径的真模糊，外面那圈是晕开的余晖
        listOf(3.2f to 0.32f, 7f to 0.15f).forEach { (k, a) ->
            val p = android.graphics.Paint(core).apply {
                alpha = (a * 255).toInt()
                if (canBlur) maskFilter = android.graphics.BlurMaskFilter(stroke * k, android.graphics.BlurMaskFilter.Blur.NORMAL)
                else strokeWidth = stroke * (1f + k)
            }
            nc.drawPath(rightSide, p)
            nc.drawPath(leftSide, p)
        }
        //主光：几乎不透明，一次画完
        core.alpha = 242
        nc.drawPath(rightSide, core)
        nc.drawPath(leftSide, core)
    }
} //语音边框光效