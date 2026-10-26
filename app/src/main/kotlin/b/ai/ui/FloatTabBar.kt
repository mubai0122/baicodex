package b.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

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
    //玻璃容器底色：参考项目取 surfaceContainer 的 40% 半透，太实会压掉模糊，太透又压不住字
    val containerColor = if (DarkColors) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.50f)

    //选中变化时胶囊弹到目标位置，阻尼 0.75 收尾不抖
    LaunchedEffect(selected) { offset.animateTo(selected.toFloat(), spring(0.75f, 400f)) }

    fun indexAt(x: Float): Int =
        if (tabPx <= 0f) 0 else ((x - padPx) / tabPx).toInt().coerceIn(0, tabs.lastIndex)

    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        //真玻璃：照参考项目的顺序——投影 -> 采样页面 -> 提饱和 -> 模糊 -> 边缘折射 -> 高光边 -> 底色
        val glassMod = if (Cfg.glassBar && bd != null) {
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
                    //按住时整条 bar 微微长大，跟参考项目的 pressProgress 一个效果
                    val w = size.width.coerceAtLeast(1f)
                    val s = 1f + (16.dp.toPx() / w) * press.value
                    scaleX = s
                    scaleY = s
                },
                onDrawSurface = { drawRect(containerColor) }
            )
        } else {
            Modifier.background(SolidColor(SurfaceWhite), CircleShape)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .onGloballyPositioned { totalPx = it.size.width.toFloat() }
                //玻璃模式的投影与描边由 drawBackdrop 自己画，再叠 Compose 那套会多一圈脏边
                .then(
                    if (Cfg.glassBar && bd != null) Modifier
                    else Modifier
                        .shadow(
                            16.dp, CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.14f)
                        )
                        .border(1.dp, BorderGray, CircleShape)
                )
                .then(glassMod)
                .pointerInput(tabs.size, tabPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { p ->
                            dragFrom = p.x
                            dragSum = 0f
                            //按下就鼓起来，像按住一颗水袋
                            scope.launch { press.animateTo(1f, spring(0.7f, 900f)) }
                        },
                        onDragCancel = {
                            dragFrom = -1f
                            scope.launch { press.animateTo(0f, spring(0.7f, 900f)) }
                        },
                        onDragEnd = {
                            if (dragFrom >= 0f) onSelect(indexAt(dragFrom + dragSum))
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
                                scope.launch { offset.snapTo(f) }
                            }
                        }
                    )
                }
        ) {
            //选中胶囊：也是真玻璃（页面层 + tab 层合并采样），按住时放大并加色散
            if (tabPx > 0f) {
                Box(Modifier.padding(4.dp)) {
                    val pill = if (Cfg.glassBar && combined != null) {
                        Modifier
                            .graphicsLayer { translationX = offset.value * tabPx }
                            .width(tabDp)
                            .height(54.dp)
                            .drawBackdrop(
                                backdrop = combined,
                                shape = { CircleShape },
                                effects = {
                                    val p = press.value
                                    lens(
                                        refractionHeight = 10.dp.toPx() * p,
                                        refractionAmount = 14.dp.toPx() * p,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f
                                    )
                                },
                                highlight = { Highlight.Default.copy(alpha = press.value) },
                                //按下去时内缩一圈阴影，玻璃才有厚度感
                                innerShadow = {
                                    InnerShadow.Default.copy(radius = 8.dp * press.value, alpha = press.value)
                                },
                                layerBlock = {
                                    //竖直方向鼓得比水平多，跟参考项目 pressedScale 78/56 的观感一致
                                    scaleX = 1f + 0.06f * press.value
                                    scaleY = 1f + 0.35f * press.value
                                },
                                onDrawSurface = {
                                    val p = press.value
                                    drawRect(
                                        color = if (DarkColors) Color.White.copy(alpha = 0.10f)
                                        else Color.Black.copy(alpha = 0.10f),
                                        alpha = 1f - p
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * p))
                                }
                            )
                    } else {
                        Modifier
                            .graphicsLayer { translationX = offset.value * tabPx }
                            .width(tabDp)
                            .height(54.dp)
                            .clip(CircleShape)
                            .background(AccentBlueSoft)
                    }
                    Box(pill)
                }
            }
            TabRow(tabs, selected, Modifier.fillMaxWidth().padding(4.dp), onSelect)
            //不可见的一份内容，专门录给胶囊玻璃当采样源（否则胶囊里看不到图标被折射）
            if (Cfg.glassBar && combined != null) {
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
private fun TabRow(tabs: List<FloatTab>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        tabs.forEachIndexed { i, tab ->
            val on = i == selected
            val c = if (on) AccentBlue else TextSecondary
            Column(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .tap { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                tab.icon(c)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(tab.labelRes), fontSize = 10.sp, color = c, maxLines = 1)
            }
        }
    }
} //图标行