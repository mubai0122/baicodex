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
 * Baicodex Windows - 内联 Sheet 外壳
 * 手机端 SheetShell 的桌面版：白色实色卡 + 圆角 16 + 描边 + 阴影，标题行带关闭，
 * 内容区限高可滚动。从输入区上方滑入，不居中、不盖遮罩。
 */

package b.ai.desktop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SheetCard(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 260.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = colors.surface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(10.dp, shape)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Spacer(Modifier.weight(1f))
                SheetCloseButton(onClose)
            }
            content()
        }
    }
}

@Composable
fun SheetCloseButton(onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceVariant,
        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClose),
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("✕", fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
    }
}

/** Sheet 内的可点行：左标题/副标题，右箭头 */
@Composable
fun SheetActionRow(
    title: String,
    desc: String? = null,
    trailing: String = "›",
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = colors.onSurface)
            if (desc != null) {
                Text(desc, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
        Text(trailing, fontSize = 14.sp, color = colors.primary)
    }
}

/** Sheet 内的强调入口（淡底强调色） */
@Composable
fun SheetHighlightRow(
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(colors.primary.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(title, fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(desc, fontSize = 10.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("›", fontSize = 14.sp, color = colors.primary)
    }
}
