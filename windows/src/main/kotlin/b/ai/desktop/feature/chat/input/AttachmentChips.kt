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
 * Baicodex Windows - 已挂载附件展示
 * 输入区上方的附件条：横向滚动，每项可单独移除。
 */

package b.ai.desktop.feature.chat.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.data.Attachment

@Composable
fun AttachmentChips(
    attachments: List<Attachment>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEachIndexed { index, attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.primary.copy(alpha = 0.10f),
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        attachment.badge,
                        fontSize = 10.sp,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "✕",
                        fontSize = 9.sp,
                        color = colors.primary,
                        modifier = Modifier
                            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .clickable { onRemove(index) }
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
