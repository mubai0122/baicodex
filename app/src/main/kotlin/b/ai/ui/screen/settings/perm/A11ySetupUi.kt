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
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import b.ai.device.sys.a11y.AutoA11y
import b.ai.net.api.llm.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.perm.Perms
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 独立无障碍激活流程的开关：系统里开着也照样跑一遍，只认「流程走没走过」 */
object A11ySetup {
    val open = mutableStateOf(false)
}

/** 两步流程：讲清用途 + 演示动画 → 开权限并复查，随时可跳过 */
@Composable
fun A11ySetupDialog(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val on = remember(tick) { Perms.a11yOn(ctx) }
    //有 Shizuku / Root 时把手动四步换成一键激活：探测放后台，别在组合里跑 su
    var canAuto by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        canAuto = if (on) false else withContext(Dispatchers.IO) { b.ai.device.sys.a11y.AutoA11y.quickChannel(ctx) != null }
    }
    //轮询：从系统设置页回来不用手动刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            tick++
        }
    }
    ScrimCard(onDismiss = onDone) {
        if (step == 0) {
            Text(stringResource(R.string.tx_bf4a564e), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.tx_cf7ceff5),
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            A11yDemo()
            Spacer(Modifier.height(10.dp))
            if (canAuto && !on) {
                A11yAutoHint()
            } else {
                Text(stringResource(R.string.tx_bad2f54e), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                StepLine(1, "点下面「去开启」，跳到系统的无障碍页面")
                StepLine(2, "点「已下载的服务」→「Baicodex 屏幕操作」")
                StepLine(3, "打开开关，弹窗里选「允许」")
                StepLine(4, "开关是灰的：设置 → 应用 → Baicodex → 右上角 ⋮ → 允许受限设置，再回来开")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (on) stringResource(R.string.tx_d1849aef) else stringResource(R.string.tx_0fa57e4a),
                fontSize = 12.sp,
                color = if (on) OkGreen else TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(stringResource(R.string.tx_87e4d9ef)) { onDone() }
                Spacer(Modifier.width(10.dp))
                when {
                    on -> PrimaryButton(stringResource(R.string.tx_38ce27d8), enabled = true) { step = 1 }
                    canAuto -> PrimaryButton(stringResource(R.string.se_348), enabled = true) { A11yAuto.open.value = true }
                    else -> PrimaryButton(stringResource(R.string.tx_5e213ddb), enabled = true) { Perms.openA11y(ctx) }
                }
            }
        } else {
            Text(stringResource(R.string.tx_1ed34876), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (on) stringResource(R.string.tx_3a7b8b88) else stringResource(R.string.tx_be58e065),
                fontSize = 12.sp,
                color = if (on) OkGreen else DangerRed
            )
            Spacer(Modifier.height(10.dp))
            if (!on) {
                StepLine(1, "点下面「去开启」，跳到系统的无障碍页面")
                StepLine(2, "点「已下载的服务」→「Baicodex 屏幕操作」")
                StepLine(3, "打开开关，弹窗里选「允许」")
                StepLine(4, "开关是灰的：设置 → 应用 → Baicodex → 右上角 ⋮ → 允许受限设置，再回来开")
                Spacer(Modifier.height(10.dp))
            }
            Text(stringResource(R.string.tx_8b920852), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!on) {
                    GhostButton(stringResource(R.string.tx_5e213ddb)) { Perms.openA11y(ctx) }
                    Spacer(Modifier.width(10.dp))
                }
                PrimaryButton("完成", enabled = true) { onDone() }
            }
        }
    }
} //独立激活流程弹窗