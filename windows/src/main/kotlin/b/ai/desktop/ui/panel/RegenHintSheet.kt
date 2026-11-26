/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:08:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 带提示重新生成
 * 手机端 PromptDialog 的桌面版：贴输入区上方的一张小卡，写一句要求再重跑这一轮。
 */

package b.ai.desktop.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.ui.component.SheetCard

@Composable
fun RegenHintSheet(
    value: String,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SheetCard(title = "带提示重新生成", onClose = onClose, maxHeight = 220.dp) {
        Text(
            "补充一句要求，这一轮回复会按新方向重写；上一轮的回复会被替换。",
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.heightIn(min = 8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(colors.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (value.isEmpty()) {
                Text("例如：更简短一些，直接给结论", fontSize = 12.sp, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.heightIn(min = 10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "取消",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "重新生成",
                fontSize = 12.sp,
                color = if (value.isBlank()) colors.onSurfaceVariant else colors.primary,
                modifier = Modifier
                    .clickable(enabled = value.isNotBlank(), onClick = onConfirm)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
