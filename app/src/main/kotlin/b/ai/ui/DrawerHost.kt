package b.ai.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import b.ai.ui.theme.BgGray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 抽屉宽度：屏宽的 80%，展开后正文还能露出一点，一眼读得出一前一后两层 */
private const val WIDTH_RATIO = 0.8f

/** 量出来的面板宽度经它下发给 DrawerPanel：宿主按屏宽算，面板自己只管取 */
val LocalDrawerWidth = compositionLocalOf { 261.dp }

/** 抽屉动效：打开 220ms、关闭 180ms，ease-out-quint 三次贝塞尔——比 Material3 默认的 256ms + 标准曲线更快，收尾不生硬 */
private const val OPEN_MS = 220
private const val CLOSE_MS = 180
private val DrawerEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
/** 松手判方向的速度阈值（px/s）：超过就顺着甩的方向收，否则看进度过半没有 */
private const val FLING = 900f

/** 抽屉推开正文：右移距离（正文按同比例缩小，右边缘正好贴住屏幕不裁切） */
private val PushShift = 42.dp
/** 正文被推开后的圆角，配合倾斜读成一张退到后面的卡片 */
private val PushCorner = 22.dp
/** 绕左边缘内倾的角度：正角度把右半边压进屏幕里，左边缘离观者最近 */
private const val PUSH_TILT = 9f
/** 正文压暗上限：抽屉在前、正文退后，靠这个明暗差立住层次 */
private const val PUSH_DIM = 0.14f
/** 透视强度：数值越大透视越弱，22 相当于手机上一臂远的自然感 */
private const val PUSH_CAMERA = 22f
/** 面板右缘投射到正文上的阴影宽度与强度 */
private val EdgeShadow = 14.dp
private const val EDGE_ALPHA = 0.18f

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

    /** 预测性返回手势跟手：系统给的进度直接落到抽屉上，手势自己带节奏，这里不再插动画 */
    internal fun follow(p: Float) {
        scope.launch { anim.snapTo(p.coerceIn(0f, 1f)) }
    }

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
    width: Dp? = null,
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    //面板宽度按屏宽算：不传宽度就是 80%，跑平板也不会被固定的 261dp 顶成一条缝
    BoxWithConstraints(Modifier.fillMaxSize().background(BgGray)) {
    val panelWidth = width ?: (maxWidth * WIDTH_RATIO)
    val widthPx = with(LocalDensity.current) { panelWidth.toPx() }
    val shiftPx = with(LocalDensity.current) { PushShift.toPx() }
    val density = LocalDensity.current.density
    //卡片圆角只建一次：graphicsLayer 里每帧都要用，不能每次 new
    val cardShape = remember { RoundedCornerShape(PushCorner) }
    //正文虚化半径固定：跟着进度每帧重建 RenderEffect 太贵，只建一次；31 以下没有 RenderEffect，只走压暗
    val blurFx = remember { BlurEffect(18f, 18f, TileMode.Clamp) }
    //只订阅"开/关"这个布尔，不订阅进度值：以前在组合期读 ctl.progress，
    //抽屉每移一帧就重组整个 DrawerHost，连 content() 里的整页都跟着重组一遍，必然掉帧
    val open by produceState(initialValue = ctl.active, ctl) {
        snapshotFlow { ctl.active }.distinctUntilChanged().collect { value = it }
    }
    //面板等抽屉真开过一次再组合：冷启动首帧不该为一屏看不见的会话列表买单（它还要读会话库）
    var live by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) live = true }
        //正文层：抽屉一动就右移 + 等比缩小 + 绕左边缘内倾 + 压暗，
        //四个量全在 graphicsLayer / drawWithContent 的 lambda 里读进度，只走绘制阶段更新，不触发重组
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = ctl.anim.value
                    if (p > 0f) {
                        val w = if (size.width > 0f) size.width else 1f
                        //右移多少就缩多少：左边缘跟着推开，右边缘正好停在屏幕边上，正文不会被裁掉一角
                        val s = 1f - shiftPx / w * p
                        translationX = shiftPx * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = PUSH_TILT * p
                        cameraDistance = PUSH_CAMERA * density
                        shape = cardShape
                        clip = true
                        //抽屉一开正文整块虚化，只留轮廓：被面板盖住的部分和露出来的那条边一起糊
                        if (Build.VERSION.SDK_INT >= 31) renderEffect = blurFx
                    }
                }
                .drawWithContent {
                    drawContent()
                    val a = PUSH_DIM * ctl.anim.value
                    if (a > 0.002f) drawRect(Color.Black.copy(alpha = a))
                }
                //整页右滑召唤：起手在屏幕左三分之一才认，纵向滚动和左滑都不抢
                .then(
                    if (gestures) Modifier.pointerInput(widthPx) { dragToOpen(ctl, widthPx) } else Modifier
                )
        ) { content() }
        if (open) {
            //动画走到一半也会在场，于是点一下就立刻反向收（这就是打断）
            Box(Modifier.fillMaxSize().tap { ctl.close() })
            //面板右缘的投影：只有抽屉在场才铺，初始闭合态左边不许留一道阴影
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(EdgeShadow)
                    .offset { IntOffset((ctl.anim.value * widthPx).roundToInt(), 0) }
                    .drawWithContent {
                        val a = EDGE_ALPHA * ctl.anim.value
                        if (a > 0.002f) drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = a),
                                1f to Color.Transparent
                            )
                        )
                    }
            )
        }
        if (live) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(panelWidth)
                    .fillMaxHeight()
                    //进度在布局阶段读：只让这一层重新摆位置，不触发任何重组
                    .offset { IntOffset(((ctl.anim.value - 1f) * widthPx).roundToInt(), 0) }
                    .then(
                        if (gestures) Modifier.pointerInput(widthPx) { dragToClose(ctl, widthPx) } else Modifier
                    )
            ) { CompositionLocalProvider(LocalDrawerWidth provides panelWidth) { panel() } }
        }
        //关闭态才铺左边缘热区：往外拖直接跟手拉开
        if (gestures && !open) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(32.dp)
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

/** 整页右滑召唤：屏幕任何位置起手都认，只认「向右」这一下——左滑和纵向滚动仍然交给列表和输入框 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragToOpen(ctl: DrawerCtl, widthPx: Float) {
    val vt = VelocityTracker()
    detectHorizontalDragGestures(
        onDragStart = { vt.resetTracking() },
        onDragCancel = { ctl.settle(0f) },
        onDragEnd = { ctl.settle(vt.calculateVelocity().x) },
        onHorizontalDrag = { change, dx ->
            //关着的时候只认右滑，往左拖一律还给列表
            if (ctl.progress <= 0.001f && dx < 0f) return@detectHorizontalDragGestures
            change.consume()
            vt.addPosition(change.uptimeMillis, change.position)
            ctl.drag(dx, widthPx)
        }
    )
} //右滑召唤