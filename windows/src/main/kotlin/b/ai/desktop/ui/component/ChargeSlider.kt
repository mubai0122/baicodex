/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 思考强度充能条（与手机端 InputPanels.kt 的 ChargeSlider 同款）
 * 26dp 胶囊里排 24 格，拖动跟手、松手弹到最近档位、最高档燃烧。
 * 关键：拖动中格子颜色一律不做动画，否则前沿热核会拖影；只有落档与燃烧态才跑动画。
 */

package b.ai.desktop.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

// 充能条 24 格：一格约 4% 强度
private const val ChargeCells = 24

// 落档弹簧：dampingRatio < 1 才有过冲，才有松手回弹
private val ChargeSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

@Composable
fun ChargeSlider(
    levels: List<String>,
    index: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onIndex: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val deep = colors.primary
    val core = colors.primaryContainer
    val empty = colors.surfaceVariant

    // 档位数随模型变：少于一档就没得滑，按两档兜底防除零
    val count = levels.size.coerceAtLeast(2)
    val span = (count - 1).toFloat()
    fun ratioOf(i: Int): Float = i.coerceIn(0, count - 1) / span

    val scope = rememberCoroutineScope()
    val settle = remember(count) { Animatable(ratioOf(index)) }
    var dragRatio by remember(count) { mutableFloatStateOf(ratioOf(index)) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }

    // 拖动中读原始值，其余时候读落档动画值
    val ratio = if (dragging) dragRatio else settle.value

    val knobDp = 14.dp
    val knobPx = with(LocalDensity.current) { knobDp.toPx() }
    var barPx by remember { mutableIntStateOf(0) }

    // 外部改档位（换模型、恢复默认）才跟过去，拖动与落档途中不抢
    LaunchedEffect(index, count) {
        if (!dragging && !settling) settle.animateTo(ratioOf(index), ChargeSpring)
    }

    // 按压：拖动起落手动转成 PressInteraction，只喂旋钮放大
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 1.1f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "charge-press",
    )

    var held by remember { mutableStateOf<PressInteraction.Press?>(null) }
    fun hold(position: Offset) {
        val press = PressInteraction.Press(position)
        held = press
        scope.launch { interaction.emit(press) }
    }

    fun drop() {
        val press = held ?: return
        held = null
        scope.launch { interaction.emit(PressInteraction.Release(press)) }
    }

    // 松手：先接管手指位置再弹到最近档位，中间不跳帧
    fun settleTo() {
        val v = (dragRatio * span).roundToInt().coerceIn(0, count - 1)
        if (v != index) onIndex(v)
        scope.launch {
            settling = true
            settle.snapTo(dragRatio)
            dragging = false
            settle.animateTo(ratioOf(v), ChargeSpring)
            settling = false
        }
    }

    // 燃烧：最高档起两组错拍往复
    val burn = rememberInfiniteTransition(label = "charge-burn")
    val deepT by burn.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "charge-burn-deep",
    )
    val coreT by burn.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "charge-burn-core",
    )

    // 燃烧只在最高档：最后一格到手就点火，跟着手指走，不等松手
    val burning = (ratio * span).roundToInt() >= count - 1
    val deepColor = if (burning) lerp(deep, core, deepT) else deep
    val coreColor = if (burning) lerp(core, Color.White, coreT) else core

    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("思考强度", fontSize = 10.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                levels[(ratio * span).roundToInt().coerceIn(0, count - 1)].uppercase(),
                fontSize = 10.sp,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .onSizeChanged { barPx = it.width }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragRatio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragging = true
                            hold(offset)
                        },
                        onDragEnd = {
                            drop()
                            settleTo()
                        },
                        onDragCancel = {
                            drop()
                            settleTo()
                        },
                    ) { change, _ ->
                        // 事件直更，不套任何动画，前沿热核才不会拖影
                        dragRatio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        dragRatio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragging = true
                        hold(offset)
                        drop()
                        settleTo()
                    }
                },
        ) {
            // 底：胶囊背景单独裁切
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(13.dp)).background(empty))
            Canvas(Modifier.fillMaxSize()) {
                drawCharge(ratio, deepColor, coreColor, empty)
            }
            // 旋钮：跟着前沿走，按下放大 1.1 倍
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(((barPx - knobPx).coerceAtLeast(0f) * ratio).roundToInt(), 0) }
                    .size(knobDp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .background(Color.White, CircleShape)
                    .border(2.dp, if (burning) deepColor else colors.primary, CircleShape),
            )
        }
    }
}

/** 画 24 格：已充能格用深色，前沿热核用浅色，未充能留空 */
private fun DrawScope.drawCharge(
    ratio: Float,
    deep: Color,
    core: Color,
    empty: Color,
) {
    val gap = 2.dp.toPx()
    val cellW = (size.width - gap * (ChargeCells - 1)) / ChargeCells
    if (cellW <= 0f) return
    val filled = ratio * ChargeCells
    val leading = floor(filled).toInt().coerceIn(0, ChargeCells - 1)

    for (i in 0 until ChargeCells) {
        val frac = (filled - i).coerceIn(0f, 1f)
        if (frac <= 0f) continue
        val base = if (i == leading) core else deep
        drawRoundRect(
            color = lerp(empty, base, frac),
            topLeft = Offset(i * (cellW + gap), 0f),
            size = Size(cellW, size.height),
            cornerRadius = CornerRadius(cellW / 2f),
        )
    }
}
