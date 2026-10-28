package b.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 底栏的一格：自绘图标 + 文案 */
data class FloatTab(val labelRes: Int, val icon: @Composable (Color) -> Unit)

/** 悬浮胶囊底栏：真液态玻璃（backdrop 采样页面 + 模糊 + 折射 + 饱和度），没传 backdrop 就退化成白底 */
@Composable
fun FloatTabBar(
    tabs: List<FloatTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null
) {
    if (tabs.isEmpty()) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(selected.toFloat()) }
    //按住时的形变量：0 静止 / 1 完全按下，胶囊跟着放大、折射与色散变强
    val press = remember { Animatable(0f) }
    var totalPx by remember { mutableFloatStateOf(0f) }
    var dragFrom by remember { mutableFloatStateOf(-1f) }
    var dragSum by remember { mutableFloatStateOf(0f) }
    val padPx = with(density) { 4.dp.toPx() }
    val tabPx = if (totalPx <= 0f) 0f else (totalPx - padPx * 2f) / tabs.size
    val tabDp = with(density) { tabPx.toDp() }
    //选中胶囊要同时采样页面和这一条 tab 栏，所以再录一层自己的内容
    val tabsBackdrop = rememberLayerBackdrop()
    val bd = backdrop
    val combined = if (bd == null) null else rememberCombinedBackdrop(bd, tabsBackdrop)
    //玻璃容器底色：白底越透越像玻璃，0.50 在浅色页面上会看着发白，压到 0.40
    val containerColor = if (DarkColors) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.40f)
    val glassOn = Cfg.glassBar && bd != null
    //降档用的极淡白：只提亮一点，不发白
    val thin = if (DarkColors) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.16f)
    //底栏只看"有没有整屏弹窗"：滑动中也必须是真玻璃，一降档整条栏就白一下再变回来
    val soft = glassOn && !GlassPerf.dialog
    //选中变化时胶囊弹到目标位置，阻尼 0.75 收尾不抖
    LaunchedEffect(selected) { offset.animateTo(selected.toFloat(), spring(0.75f, 400f)) }
    //按下就鼓、松手就落：响应要快，别等拖动阈值
    var pressedIdx by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pressedIdx) {
        if (pressedIdx >= 0) press.animateTo(1f, spring(0.65f, 1200f))
        else press.animateTo(0f, spring(0.75f, 500f))
    }

    fun indexAt(x: Float): Int =
        if (tabPx <= 0f) 0 else ((x - padPx) / tabPx).toInt().coerceIn(0, tabs.lastIndex)

    //整条底栏离屏幕底再抬 18dp：别贴着导航条，视觉上才是悬浮的
    val gap = 18.dp
    //根容器固定 104dp：栏本体只占底部 64dp，上方空出来的高度就是水滴按压时长大的余量
    Box(modifier.fillMaxWidth().height(104.dp)) {
        //这里不垫任何底色：底栏区域要透出页面背景，垫一块 BgGray 就是「没内容也填白」的来源
        //真玻璃：照参考项目的顺序——投影 -> 采样页面 -> 提饱和 -> 模糊 -> 边缘折射 -> 高光边 -> 底色
        //折射参数必须是常量：跟着 press.value 每帧变，等于每帧重建一个 RuntimeShader，点了就卡
        val glassMod = if (soft) {
            Modifier.drawBackdrop(
                backdrop = bd,
                shape = { CircleShape },
                effects = {
                    padding = maxOf(padding, 40.dp.toPx())
                    vibrancy()
                    if (Cfg.glassBlur > 0) blur(Cfg.glassBlur.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.75f) },
                shadow = {
                    Shadow.Default.copy(radius = 10.dp, alpha = if (DarkColors) 0.20f else 0.10f)
                },
                layerBlock = {
                    //按住时整条 bar 微微长大：scale 是图层变换，不会重建 effect
                    val w = size.width.coerceAtLeast(1f)
                    val s = 1f + (16.dp.toPx() / w) * press.value
                    scaleX = s
                    scaleY = s
                },
                onDrawSurface = { drawRect(containerColor) }
            )
        } else {
            Modifier.background(SolidColor(if (glassOn) thin else SurfaceWhite), CircleShape)
        }
        //栏本体只画底：投影与描边，图标行稍后压在水滴上面，z 顺序才对
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = gap)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp)
                .onGloballyPositioned { totalPx = it.size.width.toFloat() }
                //玻璃模式的投影与描边由 drawBackdrop 自己画，再叠 Compose 那套会多一圈脏边
                .then(
                    if (soft) Modifier
                    else Modifier
                        .shadow(
                            16.dp, CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.14f)
                        )
                        .border(1.dp, BorderGray, CircleShape)
                )
                .then(glassMod)
        ) {}
        //选中胶囊：宽高跟着 press 真实长大（不靠缩放，才不会被图层边界裁掉），
        //底边始终落在栏本体中线上左右对称，长出来上下各一半，跟参考项目的水滴一致
        if (tabPx > 0f) {
            val p = press.value
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    //x：20dp 栏体留白 + 4dp 内边距，再往左让 7dp；y：静止时底边距栏底 1dp，按下向下均分
                    .offset {
                        val dy = with(density) { (12.dp * p - 19.dp).toPx() }
                        IntOffset((17.dp.toPx() + offset.value * tabPx).roundToInt(), dy.roundToInt())
                    }
                    .requiredWidth(tabDp + 14.dp + 8.dp * p)
                    .requiredHeight(62.dp + 24.dp * p)
                    .then(
                        if (Cfg.glassBar && combined != null && soft) {
                            Modifier.drawBackdrop(
                                backdrop = combined,
                                shape = { CircleShape },
                                effects = {
                                    //折射参数固定：跟着 press.value 每帧变会每帧重建 RuntimeShader
                                    lens(
                                        refractionHeight = 10.dp.toPx(),
                                        refractionAmount = 14.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = 0.5f
                                    )
                                },
                                highlight = { Highlight.Default.copy(alpha = 0.35f + 0.45f * p) },
                                //按下时的厚度感用固定内阴影，不再每帧改半径
                                innerShadow = {
                                    InnerShadow.Default.copy(radius = 8.dp, alpha = 0.55f)
                                },
                                onDrawSurface = {
                                    drawRect(
                                        color = if (DarkColors) Color.White.copy(alpha = 0.10f)
                                        else Color.Black.copy(alpha = 0.10f),
                                        alpha = 1f - p
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * p))
                                }
                            )
                        } else {
                            //降档：跟玻璃同色系的极淡底，不发白
                            Modifier.clip(CircleShape).background(if (glassOn) thin else AccentBlueSoft)
                        }
                    )
            )
        }
        //图标行压在水滴上面：点击与横向拖动都在这一层收
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = gap)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp)
                .pointerInput(tabs.size, tabPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { pt ->
                            dragFrom = pt.x
                            dragSum = 0f
                            //按下就鼓起来，像按住一颗水袋
                            scope.launch { press.animateTo(1f, spring(0.7f, 900f)) }
                        },
                        onDragCancel = {
                            dragFrom = -1f
                            scope.launch { press.animateTo(0f, spring(0.7f, 900f)) }
                        },
                        onDragEnd = {
                            //松手先落到最近的格子上，再交给选中态去弹；点在同格上也要归位
                            val t = if (dragFrom >= 0f) indexAt(dragFrom + dragSum) else selected
                            onSelect(t)
                            scope.launch { offset.animateTo(t.toFloat(), spring(0.75f, 400f)) }
                            dragFrom = -1f
                            scope.launch { press.animateTo(0f, spring(0.7f, 900f)) }
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            if (dragFrom < 0f) dragFrom = change.position.x
                            dragSum += dx
                            if (tabPx > 0f) {
                                val f = ((dragFrom + dragSum - padPx) / tabPx)
                                    .coerceIn(0f, tabs.lastIndex.toFloat())
                                //胶囊不是硬贴着手指，而是带阻尼追过去，像水被拖着走
                                scope.launch { offset.animateTo(f, spring(0.75f, 180f)) }
                            }
                        }
                    )
                }
        ) {
            TabRow(tabs, selected, Modifier.fillMaxWidth().padding(4.dp), onSelect) { i, down ->
                pressedIdx = if (down) i else -1
            }
            //不可见的一份内容，专门录给胶囊玻璃当采样源（否则胶囊里看不到图标被折射）
            if (Cfg.glassBar && combined != null && soft) {
                TabRow(
                    tabs,
                    selected,
                    Modifier.fillMaxWidth().padding(4.dp).alpha(0f).layerBackdrop(tabsBackdrop),
                    onSelect
                )
            }
        }
    }
} //悬浮胶囊底栏
/** 底栏的一排图标 + 文案；玻璃模式会多画一份透明的用来录制采样层 */
@Composable
private fun TabRow(
    tabs: List<FloatTab>,
    selected: Int,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
    onPress: (Int, Boolean) -> Unit = { _, _ -> }
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { i, tab ->
            val on = i == selected
            val c = if (on) AccentBlue else TextSecondary
            //按下/抬起用 InteractionSource 读，不额外握手势检测器，不会和父层横向拖动抢事件
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            LaunchedEffect(pressed) { onPress(i, pressed) }
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable(interactionSource = src, indication = null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                tab.icon(c)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(tab.labelRes), fontSize = 10.sp, color = c, maxLines = 1)
            }
        }
    }
} //一排 tab