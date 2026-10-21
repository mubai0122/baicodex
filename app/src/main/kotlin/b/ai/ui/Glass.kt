package b.ai.ui

import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceWhite

/** 玻璃卡片：半透白底 + 光随指动的柔光 + 顶部高光边；开关关掉就走原来那张白卡 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    strong: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = Cfg.glassCard
    val shape = RoundedCornerShape(corner)
    val dark = DarkColors
    var touch by remember { mutableStateOf(Offset(-1f, -1f)) }
    var pressed by remember { mutableStateOf(false) }
    Column(
        modifier
            .then(
                if (glass) {
                    Modifier.shadow(
                        elevation = if (strong) 16.dp else 8.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.05f),
                        spotColor = Color.Black.copy(alpha = 0.10f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            //只观察指针位置，不消费事件，滚动和点击都照常
            .then(
                if (glass) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull() ?: continue
                                touch = c.position
                                pressed = c.pressed
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .background(if (glass) GlassBrush(touch.x, touch.y, pressed, dark) else SolidColor(SurfaceWhite))
            .border(
                1.dp,
                if (glass) Color.White.copy(alpha = if (dark) 0.12f else 0.60f) else BorderGray,
                shape
            )
            .then(if (onClick != null) Modifier.tap { onClick() } else Modifier)
            .padding(contentPadding),
        content = content
    )
} //玻璃卡片

/** 玻璃底：竖向半透白/黑压住内容，再叠一层跟着手指走的柔光斑（浅色 SCREEN、深色 SRC_OVER） */
private class GlassBrush(
    private val touchX: Float,
    private val touchY: Float,
    private val pressed: Boolean,
    private val dark: Boolean
) : androidx.compose.ui.graphics.ShaderBrush() {

    override fun createShader(size: Size): Shader {
        val base = if (dark) intArrayOf(
            android.graphics.Color.argb(225, 25, 28, 33),
            android.graphics.Color.argb(190, 25, 28, 33)
        ) else intArrayOf(
            android.graphics.Color.argb(238, 255, 255, 255),
            android.graphics.Color.argb(205, 247, 250, 255)
        )
        val vertical = android.graphics.LinearGradient(
            0f, 0f, 0f, size.height,
            base, floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        if (touchX < 0f || touchY < 0f) return vertical
        //深浅色下光斑强度不同：深色压住（点一下别整块发白），浅色亮一点
        val a = when {
            dark && pressed -> 44
            dark -> 24
            pressed -> 128
            else -> 68
        }
        val r = if (pressed) size.width * 0.55f else size.width * 0.32f
        val spot = android.graphics.RadialGradient(
            touchX, touchY, r.coerceAtLeast(1f),
            intArrayOf(
                android.graphics.Color.argb(a, 255, 255, 255),
                android.graphics.Color.argb(a / 3, 255, 255, 255),
                android.graphics.Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        return android.graphics.ComposeShader(vertical, spot, if (dark) PorterDuff.Mode.SRC_OVER else PorterDuff.Mode.SCREEN)
    }
} //玻璃底 + 指位柔光

/** 3D 质感胶囊：下面垫一层同色暗面当厚度，按下时面板沉下去贴住厚度面，松手弹回来 */
@Composable
fun TactileChip(
    active: Boolean,
    accent: Color = AccentBlue,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val lift by animateDpAsState(
        targetValue = if (pressed) 0.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "chipLift"
    )
    //面用主色的浅调，文字照旧用主色，所以开关状态下都不影响可读性
    val face = if (active) lerp(SurfaceWhite, accent, 0.14f) else SurfaceWhite
    //厚度层的颜色比面暗一档，立体感靠它撑
    val edge = if (active) lerp(BorderGray, accent, 0.45f) else BorderGray
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(edge, shape)
        )
        Row(
            Modifier
                .offset(y = lift)
                .clip(shape)
                .background(
                    Brush.verticalGradient(listOf(lerp(face, Color.White, 0.30f), face)),
                    shape
                )
                .border(1.dp, if (active) accent.copy(alpha = 0.80f) else BorderGray, shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        }
                    )
                }
                .tap { onClick() }
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
} //3D 质感胶囊

/** 页面背景修饰符：先铺 BgGray，再按 bg_style 叠柔光渐变 / 点阵，挂在根容器上即可 */
fun Modifier.pageBg(): Modifier = drawBehind {
    val style = Cfg.bgStyle
    drawRect(BgGray)
    when (style) {
        1 -> {
            val a = if (DarkColors) Color(0xFF18223A) else Color(0xFFE9F1FF)
            val b = if (DarkColors) Color(0xFF2B1E33) else Color(0xFFFDEEF4)
            drawRect(
                Brush.linearGradient(
                    listOf(a, BgGray, b),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
        }
        2 -> {
            val dot = if (DarkColors) Color(0x22FFFFFF) else Color(0x16000000)
            val step = 22.dp.toPx()
            val r = 1.dp.toPx()
            var y = step / 2f
            while (y < size.height) {
                var x = step / 2f
                while (x < size.width) {
                    drawCircle(dot, r, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
} //页面背景

/** 3D 质感圆钮：发送键用它，按下去整块下沉贴住底面 */
@Composable
fun TactileCircle(
    active: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 34.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val lift by animateDpAsState(
        targetValue = if (pressed) 0.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "circleLift"
    )
    val face = if (active) AccentBlue else SurfaceWhite
    val edge = if (active) AccentBlue.copy(alpha = 0.45f) else BorderGray
    Box(modifier.size(diameter)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 2.dp)
                .background(edge, CircleShape)
        )
        Box(
            Modifier
                .offset(y = lift)
                .size(diameter)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(face, Color.White, 0.20f), face)), CircleShape)
                .border(1.dp, if (active) AccentBlue.copy(alpha = 0.75f) else BorderGray, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        }
                    )
                }
                .tap(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) { content() }
    }
} //3D 质感圆钮