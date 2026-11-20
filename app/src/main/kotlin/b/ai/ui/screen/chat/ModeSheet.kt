/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:21
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.chat
import b.ai.ui.input.SheetHead
import b.ai.ui.input.SheetShell
import b.ai.ui.glass.liquidBg
import b.ai.ui.widget.tap
import b.ai.net.api.llm.text
import b.ai.ui.theme.AccentBlueSoft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.cfg.app.AgentMode
import b.ai.core.cfg.app.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

/** 运行模式卡片：一档一行，带一句说明，点中即存盘并关面板；贴输入区上沿，跟其他面板同一套外壳 */
@Composable
internal fun ModeCard(onClose: () -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.mode_title), onClose)
        //注意：SheetShell 内部已经是「限高 + verticalScroll」的 Column，这里不能再套滚动或 weight，
        //否则子项高度约束无界，weight 分配不到高度会把内容压成 0（弹窗空白）
        Column(Modifier.fillMaxWidth()) {
            AgentMode.entries.forEach { m ->
                val on = Cfg.agentMode == m
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .liquidBg(RoundedCornerShape(12.dp)
                        , tint = if (on) b.ai.ui.theme.AccentBlueSoft else SurfaceMuted)
                        .tap {
                            Cfg.agentMode = m
                            onClose()
                        }
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(m.labelRes),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (on) AccentBlue else TextPrimary
                        )
                        Text(
                            stringResource(m.descRes),
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (on) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
} //运行模式卡片