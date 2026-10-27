package b.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 抽屉宽度：DrawerPanel 与抽屉宿主共用这一个值 */
val DrawerWidth = 261.dp

/** 抽屉动效：打开 220ms、关闭 180ms，ease-out-quint 三次贝塞尔——比 Material3 默认的 256ms + 标准曲线更快，收尾不生硬 */
private const val OPEN_MS = 220
private const val CLOSE_MS = 180
private val DrawerEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
/** 松手判方向的速度阈值（px/s）：超过就顺着甩的方向收，否则看进度过半没有 */
private const val FLING = 900f

/** 抽屉控制器：进度 0=关 1=开。任何时刻换目标都从当前进度接着走，这本身就是打断，不需要额外的状态机 */
class DrawerCtl(
    internal val anim: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope
) {
    val progress: Float get() = anim.value
    /** 关着且没有动画在排队才为 false：驱动模糊层与返回键 */
    val active: Boolean get() = anim.value > 0.001f || anim.targetValue > 0.001f

    fun open() {
        scope.launch { anim.animateTo(1f, tween(OPEN_MS, easing = DrawerEase)) }
    }

    fun close() {
        scope.launch { anim.animateTo(0f, tween(CLOSE_MS, easing = DrawerEase)) }
    }

    /** 拖拽跟手：直接改当前进度，正在跑的动画会被取消（动画中途按下拖动也能接手） */
    internal fun drag(dx: Float, widthPx: Float) {
        scope.launch { anim.snapTo((anim.value + dx / widthPx).coerceIn(0f, 1f)) }
    }

    /** 松手收尾：甩得够快就按方向走，不然看进度过半没有 */
    internal fun settle(velocity: Float) {
        val goOpen = when {
            velocity > FLING -> true
            velocity < -FLING -> false
            else -> anim.value > 0.5f
        }
        if (goOpen) open() else close()
    }
} //抽屉控制器

/** 抽屉宿主：内容层 + 透明点击遮罩 + 可拖拽面板。遮罩不带颜色，模糊交给 BackdropHost，避免两层遮罩叠黑 */
@Composable
fun DrawerHost(
    ctl: DrawerCtl,
    gestures: Boolean,
    width: Dp = DrawerWidth,
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    Box(Modifier.fillMaxSize()) {
        content()
        val p = ctl.progress
        if (p > 0.001f) {
            //动画走到一半也会在场，于是点一下就立刻反向收（这就是打断）
            Box(Modifier.fillMaxSize().tap { ctl.close() })
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(width)
                .fillMaxHeight()
                .offset { IntOffset(((p - 1f) * widthPx).roundToInt(), 0) }
                .then(
                    if (gestures) Modifier.pointerInput(widthPx) { dragToClose(ctl, widthPx) } else Modifier
                )
        ) { panel() }
        //关闭态才铺左边缘热区：往外拖直接跟手拉开
        if (gestures && p < 0.001f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(16.dp)
                    .pointerInput(widthPx) { dragToClose(ctl, widthPx) }
            )
        }
    }
} //抽屉宿主

/** 横向拖拽手势：拖的时候跟手，松手按速度收尾 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragToClose(ctl: DrawerCtl, widthPx: Float) {
    val vt = VelocityTracker()
    detectHorizontalDragGestures(
        onDragStart = { vt.resetTracking() },
        onDragCancel = { ctl.settle(0f) },
        onDragEnd = { ctl.settle(vt.calculateVelocity().x) },
        onHorizontalDrag = { change, dx ->
            vt.addPosition(change.uptimeMillis, change.position)
            ctl.drag(dx, widthPx)
        }
    )
} //拖拽手势
