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
 * Baicodex Windows - 顶栏
 * 只放品牌与版本：模型切换在输入区胶囊，设置入口在侧边栏底部。
 */

package b.ai.desktop.feature.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.ui.component.AppIcon

@Composable
fun TopBar(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text("Baicodex", color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
            Text(
                "Windows MVP",
                color = colors.onSurfaceVariant,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "AGPL-3.0 · 本地优先",
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 9.sp,
        )
    }
}
