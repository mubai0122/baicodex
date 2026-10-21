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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** 底栏的一格：自绘图标 + 文案 */
data class FloatTab(val labelRes: Int, val icon: @Composable (Color) -> Unit)

/** 悬浮胶囊底栏：白底实心 + 选中胶囊滑动，横向可拖拽切换（无液态玻璃） */
@Composable
fun FloatTabBar(
    tabs: List<FloatTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(selected.toFloat()) }
    var totalPx by remember { mutableFloatStateOf(0f) }
    var dragFrom by remember { mutableFloatStateOf(-1f) }
    var dragSum by remember { mutableFloatStateOf(0f) }
    val padPx = with(density) { 4.dp.toPx() }
    val tabPx = if (totalPx <= 0f) 0f else (totalPx - padPx * 2f) / tabs.size
    val tabDp = with(density) { tabPx.toDp() }

    //选中变化时胶囊弹到目标位置，阻尼 0.75 收尾不抖
    LaunchedEffect(selected) { offset.animateTo(selected.toFloat(), spring(0.75f, 400f)) }

    fun indexAt(x: Float): Int =
        if (tabPx <= 0f) 0 else ((x - padPx) / tabPx).toInt().coerceIn(0, tabs.lastIndex)

    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        //液态玻璃底栏：半透白渐变 + 白色高光边，底下内容能透一点出来
        val glass = Cfg.glassBar
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .onGloballyPositioned { totalPx = it.size.width.toFloat() }
                .shadow(
                    16.dp, CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.14f)
                )
                .background(
                    if (glass) {
                        Brush.verticalGradient(
                            listOf(
                                SurfaceWhite.copy(alpha = 0.94f),
                                SurfaceWhite.copy(alpha = 0.68f)
                            )
                        )
                    } else {
                        SolidColor(SurfaceWhite)
                    },
                    CircleShape
                )
                .border(
                    1.dp,
                    if (glass) Color.White.copy(alpha = if (DarkColors) 0.18f else 0.85f) else BorderGray,
                    CircleShape
                )
                .pointerInput(tabs.size, tabPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { p ->
                            dragFrom = p.x
                            dragSum = 0f
                        },
                        onDragCancel = { dragFrom = -1f },
                        onDragEnd = {
                            if (dragFrom >= 0f) onSelect(indexAt(dragFrom + dragSum))
                            dragFrom = -1f
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
            if (tabPx > 0f) {
                Box(Modifier.padding(4.dp)) {
                    Box(
                        Modifier
                            .graphicsLayer { translationX = offset.value * tabPx }
                            .width(tabDp)
                            .height(54.dp)
                            .clip(CircleShape)
                            .background(AccentBlueSoft)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        }
    }
} //悬浮胶囊底栏