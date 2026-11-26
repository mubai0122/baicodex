/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 00:52:12
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 轻提示条
 * 右键菜单里的操作（复制/置顶/删除/导出）没有专属界面，用底部浮出的小条反馈结果。
 */

package b.ai.desktop.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToastBar(text: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // 退场动画期间 text 已经是 null，缓存最后一个非空值撑住内容
    val cache = remember { mutableStateOf("") }
    if (text != null && cache.value != text) cache.value = text

    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(90)) + slideInVertically(tween(140)) { it / 3 },
        exit = fadeOut(tween(90)) + slideOutVertically(tween(120)) { it / 3 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = colors.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Text(
                cache.value,
                fontSize = 11.sp,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
