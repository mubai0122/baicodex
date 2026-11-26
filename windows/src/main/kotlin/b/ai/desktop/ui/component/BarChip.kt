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
 * Baicodex Windows - 扁平胶囊
 * 对齐手机端 BarChip：无 Material 默认灰底；选中态淡底 + 同色描边渐变；按压缩放反馈。
 */

package b.ai.desktop.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun BarChip(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val bg by animateColorAsState(
        targetValue = if (active) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.7f),
        label = "chipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant.copy(alpha = 0.5f),
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.7f),
        label = "chipBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.5f),
        label = "chipScale",
    )

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 按压缩放反馈的数值（供胶囊/按钮复用） */
@Composable
fun pressScale(pressed: Boolean): Float = animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1.0f,
    animationSpec = spring(stiffness = 500f, dampingRatio = 0.5f),
    label = "pressScale",
).value

/** 箭头旋转反馈：展开 180°，收起 0° */
@Composable
fun chevronRotation(expanded: Boolean): Float = animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = spring(stiffness = 420f, dampingRatio = 0.6f),
    label = "chevronRotation",
).value
