/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:26
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.widget

/**
 * 文件作用：聊天页「回到最新一条」圆钮。
 * · 配色三档（Cfg.scrollBtnStyle）：0 默认白 / 1 继承液态玻璃 / 2 继承主题色
 * · AI 正在进行（规划 / 生成 / 执行）时，圆钮外圈套一道主题色光弧，顺时针无限旋转
 * 显隐由调用方决定（列表没滚到底才显示），本组件只负责外观与点击。
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import b.ai.core.cfg.app.Cfg
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary

/** 圆钮直径：跟改造前的尺寸保持一致，环挂在它外面不占正文空间 */
private val BTN = 38.dp

/** 环到圆钮之间的留白：太贴会显得糊，太远像两个控件 */
private val RING_GAP = 3.dp

/** 环的线宽 */
private val RING_W = 2.dp

@Composable
fun ScrollDownButton(
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val style = Cfg.scrollBtnStyle
    //外框比圆钮大一圈，把环的位置预留出来：否则 drawArc 会画到裁切边界外看不见
    val outer = BTN + (RING_GAP + RING_W) * 2
    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        //进行中才有环：空闲时连 infiniteTransition 都不建，滚动列表里不做无谓的动画帧
        if (busy) {
            val tr = rememberInfiniteTransition(label = "scrollRing")
            //顺时针：角度 0 → 360 匀速，1.1s 一圈
            val deg by tr.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                label = "scrollRingDeg"
            )
            Canvas(Modifier.matchParentSize().rotate(deg)) {
                val sw = RING_W.toPx()
                val inset = sw / 2f
                //sweep 渐变：头部实主题色、尾巴全透明，转起来像一道光在绕圈
                drawArc(
                    brush = Brush.sweepGradient(
                        0.00f to AccentBlue,
                        0.16f to AccentBlue.copy(alpha = 0.45f),
                        0.42f to Color.Transparent,
                        1.00f to Color.Transparent
                    ),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        //底与图标色按三档取：2 档整体跟随主题色；0/1 档保留「有新内容时图标转主题色」的提示
        val bg = if (style == 2) AccentBlueSoft else SurfaceWhite
        val fg = if (style == 2) AccentBlue else if (busy) AccentBlue else TextPrimary
        Box(
            Modifier
                .size(BTN)
                //0 档强制平涂白（不跟玻璃）；1、2 档走 liquidBg，开玻璃时自动继承材质
                .then(
                    if (style == 0) Modifier.background(bg, CircleShape)
                    else Modifier.liquidBg(CircleShape, tint = bg, gate = 0.dp)
                )
                .tap { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
        }
    }
} //回到底部圆钮