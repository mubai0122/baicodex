/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:19
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.dialog
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import b.ai.net.api.llm.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.task.hook.Noti
import b.ai.core.data.usage.Usage
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/** 注入类权限的提示开关：值是 noti / usage，挂载点在设置页根弹窗层 */
object PermAsk {
    val kind = mutableStateOf<String?>(null)
}

/** 开关一打开就弹这个：讲清要什么权限、为什么，然后直接把用户送到系统页 */
@Composable
fun PermAskDialog(kind: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val noti = kind == "noti"
    ScrimCard(onDismiss = onClose) {
        Text(
            stringResource(if (noti) R.string.se_332 else R.string.se_334),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (noti) R.string.se_333 else R.string.se_335),
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.up_later)) { onClose() }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(stringResource(R.string.se_336), enabled = true) {
                if (noti) Noti.openPerm(ctx) else Usage.openPerm(ctx)
                onClose()
            }
        }
    }
} //注入权限提示