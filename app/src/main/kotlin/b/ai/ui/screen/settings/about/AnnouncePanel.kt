/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:22
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.about
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.widget.tap
import b.ai.net.api.llm.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.net.api.site.Announce
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
/** 公告弹窗的全局状态：启动拉取后写这里，由根弹窗层统一渲染 */
object AnnounceUi {
    var cur by mutableStateOf<Announce.Item?>(null)
    fun close() {
        cur = null
    }
} //关掉公告

@Composable
fun AnnounceDialog(item: Announce.Item, onClose: () -> Unit) {
    val ctx = LocalContext.current
    ScrimCard {
        //标题行：标题占满，关闭按钮固定在右上角
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .liquidBg(CircleShape, tint = SurfaceMuted)
                    .tap { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 13.sp, color = TextSecondary)
            }
        }
        //第二行：日期在前、副标题在后，两者都可能为空
        if (item.date.isNotEmpty() || item.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.date.isNotEmpty()) Text(item.date, fontSize = 11.sp, color = TextSecondary)
                if (item.date.isNotEmpty() && item.subtitle.isNotEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    Text("·", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.width(7.dp))
                }
                if (item.subtitle.isNotEmpty()) {
                    Text(
                        item.subtitle,
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }

        //正文：按 \n 分段，段数多就滚动，不做富文本解析
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(glassScrollState())
        ) {
            Column {
                item.body.split("\n").forEachIndexed { i, ln ->
                    if (i > 0) Spacer(Modifier.height(if (ln.isBlank()) 6.dp else 4.dp))
                    if (ln.isNotBlank()) Text(ln, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp)
                }
            }
        }

        //按钮：第一个用主色，其余描边；点了只跳浏览器
        if (item.buttons.isNotEmpty()) Spacer(Modifier.height(14.dp))
        item.buttons.forEachIndexed { i, b ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(12.dp), tint = if (i == 0) AccentBlue else SurfaceMuted)
                    .tap { openUrl(ctx, b.url) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    b.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (i == 0) Color.White else TextPrimary
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
} //公告卡片：关闭按钮在右上角，背景模糊由 ScrimCard 统一处理