package b.ai.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import b.ai.ui.theme.DarkColors
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** 弹窗卡片在窗口里的矩形，由 ScrimCard 上报（留给需要按卡片裁剪的效果用） */
class CardSlot {
    var rect by mutableStateOf(Rect.Zero)
}

val LocalCardSlot = compositionLocalOf<CardSlot?> { null }

/**
 * 弹窗背景层：内容层交给 backdrop 采样，弹窗打开时整屏按同一份 backdrop 再画一遍并加模糊
 * （backdrop 的 blur 走 RenderEffect，低版本库内部退化），上面压遮罩，最后才是弹窗卡片。
 */
@Composable
fun BackdropHost(
    active: Boolean,
    scrim: Boolean = true,
    content: @Composable () -> Unit,
    overlay: @Composable () -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val slot = remember { CardSlot() }
    val radius by animateDpAsState(
        targetValue = if (active) 24.dp else 0.dp,
        animationSpec = tween(220),
        label = "backdropBlur"
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (active && scrim) 1f else 0f,
        animationSpec = tween(200),
        label = "backdropScrim"
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }

        if (radius > 0.dp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(radius.toPx()) }
                    )
            )
        }

        if (scrimAlpha > 0f) {
            //深色下遮罩压得更重，否则模糊层看着还是发亮
            val base = if (DarkColors) 0.45f else 0.26f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = base * scrimAlpha)))
        }

        CompositionLocalProvider(LocalCardSlot provides slot) {
            if (active) overlay()
        }
    }
} //内容层采样 + 整屏真模糊 + 遮罩 + 全局弹窗层