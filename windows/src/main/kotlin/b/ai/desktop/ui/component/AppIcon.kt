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
 * 应用图标：圆角主色块 + 白色终端提示符（>_），纯 Canvas 绘制，不依赖图片资源
 */

package b.ai.desktop.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppIcon(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(colors.primary),
    ) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.2f)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(
                width = size.toPx() * 0.1f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            // 提示符 >
            val chevron = Path().apply {
                moveTo(w * 0.06f, h * 0.18f)
                lineTo(w * 0.44f, h * 0.5f)
                lineTo(w * 0.06f, h * 0.82f)
            }
            drawPath(chevron, Color.White, style = stroke)
            // 光标 _
            drawLine(
                color = Color.White,
                start = Offset(w * 0.58f, h * 0.82f),
                end = Offset(w * 0.96f, h * 0.82f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}
