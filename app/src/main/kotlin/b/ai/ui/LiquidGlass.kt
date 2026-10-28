package b.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 卡片采样源：只登记页面背景那一层（不含卡片自己），没登记的页面卡片照旧是白卡 */
val LocalCardBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 最近一次登记的背景层：抽屉这类挂在 Activity 层、不在页面树里的浮层靠它取采样源 */
object CardBackdropHolder {
    var current: Backdrop? by mutableStateOf(null)
} //全局背景层

/** 页面把自己那层纯背景登记给全局，同时下发 CompositionLocal；离开页面自动清掉 */
@Composable
fun ProvideCardBackdrop(bd: Backdrop, content: @Composable () -> Unit) {
    DisposableEffect(bd) {
        CardBackdropHolder.current = bd
        onDispose { if (CardBackdropHolder.current === bd) CardBackdropHolder.current = null }
    }
    CompositionLocalProvider(LocalCardBackdrop provides bd, content = content)
} //背景层登记

/** 按下去的进度：0 松手 / 1 按住，用来放大、加高光；只有可点的卡片才挂，不抢里面的按钮与滑杆 */
private class PressHighlight(private val scope: CoroutineScope, private val onClick: (() -> Unit)?) {
    val progress = Animatable(0f)
    val modifier: Modifier = Modifier.drawWithContent {
        //按压时整块叠一层白色 Plus 提亮，跟参考实现一致
        val p = progress.value
        if (p > 0f) drawRect(Color.White.copy(alpha = 0.25f * p), blendMode = BlendMode.Plus)
        drawContent()
    } //按压提亮
    val gesture: Modifier = Modifier.pointerInput(onClick) {
        detectTapGestures(
            onPress = {
                scope.launch { progress.animateTo(1f, spring(0.5f, 300f)) }
                tryAwaitRelease()
                scope.launch { progress.animateTo(0f, spring(0.5f, 300f)) }
            },
            onTap = if (onClick != null) ({ onClick() }) else null
        )
    } //按压探测
} //按压进度

/** 真液态玻璃卡片：采样页面 -> 提饱和 -> 模糊 -> 边缘折射 -> 高光边 -> 叠加色 */
@Composable
fun LiquidCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val scope = rememberCoroutineScope()
    val press = remember(scope, onClick) { PressHighlight(scope, onClick) }
    Column(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    padding = maxOf(padding, 24.dp.toPx())
                    vibrancy()
                    if (Cfg.glassBlur > 0) blur(Cfg.glassBlur.dp.toPx())
                    lens(16.dp.toPx(), 20.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(alpha = 0.25f + 0.5f * press.progress.value)
                },
                layerBlock = {
                    //按住时整块微微长大，像一层水膜被压下去
                    val s = 1f + (3.dp.toPx() / size.height.coerceAtLeast(1f)) * press.progress.value
                    scaleX = s
                    scaleY = s
                },
                onDrawSurface = {
                    //浅色下什么都不叠：玻璃直接透出页面背景（有背景图就是背景图，没有就是背景的模糊）
                    drawRect(if (DarkColors) Color.Black.copy(alpha = 0.10f) else Color.Transparent)
                }
            )
            //只有可点的卡片才挂按压：里面的开关、按钮、滑杆照常拿事件
            .then(if (onClick != null) press.modifier.then(press.gesture) else Modifier)
            .padding(contentPadding),
        content = content
    )
} //液态玻璃卡片

/** 任意容器的玻璃底：开了液态玻璃且这一页有背景层就真玻璃，否则退回原来的白底 */
@Composable
fun Modifier.liquidBg(
    shape: Shape,
    tint: Color = SurfaceWhite,
    highlightAlpha: Float = 0.5f,
    refract: Boolean = false,
    veil: Float = 0.12f
): Modifier {
    val bd = LocalCardBackdrop.current ?: CardBackdropHolder.current
    if (!Cfg.glassCard || bd == null) return background(tint, shape)
    //白/灰底不叠色，直接透背景；主色底（选中态、发送键）在玻璃上叠一层同色，保住"这个是开着的"
    val overlay = if (tint == AccentBlue || tint == AccentBlueSoft) tint.copy(alpha = 0.55f)
    else Color.Transparent
    //veil 只在弹窗这类需要压住底下的地方用：玻璃照做，再盖一层半透底保住文字可读
    val sheet = if (veil <= 0f) Color.Transparent
    else if (DarkColors) Color.Black.copy(alpha = veil) else Color.White.copy(alpha = veil)
    return drawBackdrop(
        backdrop = bd,
        shape = { shape },
        effects = {
            padding = maxOf(padding, 40.dp.toPx())
            vibrancy()
            if (Cfg.glassBlur > 0) blur(Cfg.glassBlur.dp.toPx())
            //气泡、输入框这类小元素默认不折射，边缘变形会显得脏
            if (refract) lens(6.dp.toPx(), 10.dp.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
        onDrawSurface = {
            drawRect(if (DarkColors) Color.Black.copy(alpha = 0.10f) else Color.Transparent)
            if (overlay.alpha > 0f) drawRect(overlay)
            if (sheet.alpha > 0f) drawRect(sheet)
        }
    )
} //容器玻璃底