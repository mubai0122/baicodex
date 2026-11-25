/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:23
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.perm
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*
import androidx.compose.ui.res.stringResource
import b.ai.R
import b.ai.ui.widget.GhostButton
import b.ai.net.api.llm.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.perm.Perms
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.theme.WarnAmber
import kotlinx.coroutines.delay

/** 特殊权限档位列表：一条一档，状态自己轮询刷新，从系统设置页返回就能看到变化 */
@Composable
fun SpecialPermCard(keys: List<String>, desc: String? = null) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    //轮询而不是监听：这三个开关都没有可靠的变更回调
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (desc != null) {
            Text(desc, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
        }
        keys.forEach { k ->
            val on = remember(tick, k) { Perms.specialOn(ctx, k) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Perms.specialName(k), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (on) stringResource(R.string.tx_9db7a84f) else stringResource(R.string.tx_ea4a363d),
                            fontSize = 11.sp,
                            color = if (on) OkGreen else WarnAmber
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(Perms.specialDesc(k), fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.width(10.dp))
                if (on) {
                    Text("✓", fontSize = 14.sp, color = OkGreen)
                } else {
                    GhostButton(stringResource(R.string.tx_5e213ddb)) {
                        Perms.openSpecial(ctx, k)
                        tick++
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.tx_4f8d48ef)) { tick++ }
        }
    }
} //特殊权限档位卡片