package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Cfg
import b.ai.core.Perms
import b.ai.exec.A11y
import b.ai.exec.AutoA11y
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一键激活弹窗的开关：挂在根弹窗层，任意页面都能唤起 */
object A11yAuto {
    val open = mutableStateOf(false)
}

/**
 * 高权限一键激活无障碍：
 * 确认（含「以后每次进入自动激活」）→ 进度条两步 → 成功页给出关闭入口的提示。
 */
@Composable
fun A11yAutoDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    //0 = 确认，1 = 进行中，2 = 成功，3 = 失败
    var phase by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    var prog by remember { mutableFloatStateOf(0f) }
    var err by remember { mutableStateOf("") }
    var autoRun by remember { mutableStateOf(true) }
    var ch by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ch = withContext(Dispatchers.IO) { AutoA11y.channels(ctx).firstOrNull() }
    }
    LaunchedEffect(phase) {
        if (phase != 1) return@LaunchedEffect
        prog = 0.1f
        step = 0
        val ok = withContext(Dispatchers.IO) {
            AutoA11y.run(ctx) { s, msg ->
                when (s) {
                    0 -> { step = 0; prog = 0.35f }
                    1 -> { step = 1; prog = 0.8f }
                    2 -> { step = 1; prog = 1f }
                    else -> err = msg
                }
            }
        }
        Cfg.autoA11y = autoRun
        phase = if (ok) 2 else 3
    }

    ScrimCard(onDismiss = onClose) {
        when (phase) {
            0 -> {
                Text(
                    stringResource(R.string.se_350),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.se_351,
                        when (ch) {
                            "shizuku" -> "Shizuku"
                            "root" -> "Root"
                            else -> stringResource(R.string.se_349)
                        }
                    ),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { autoRun = !autoRun },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (autoRun) AccentBlue else AccentBlueSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        if (autoRun) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = Color.White, modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.se_352), fontSize = 12.sp, color = TextPrimary)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton(stringResource(R.string.se_142)) { onClose() }
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(stringResource(R.string.se_353), enabled = true) { phase = 1 }
                }
            }
            1 -> {
                Text(
                    stringResource(R.string.se_354),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { prog },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AccentBlue,
                    trackColor = AccentBlueSoft
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(if (step == 0) R.string.se_355 else R.string.se_356),
                    fontSize = 12.sp,
                    color = if (step == 0) TextSecondary else OkGreen
                )
            }
            2 -> {
                Text(
                    stringResource(R.string.se_357),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OkGreen
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.se_358), fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (Cfg.autoA11y) R.string.se_359 else R.string.se_360),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PrimaryButton(stringResource(R.string.se_361), enabled = true) { onClose() }
                }
            }
            else -> {
                Text(
                    stringResource(R.string.se_362),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DangerRed
                )
                Spacer(Modifier.height(6.dp))
                Text(err.ifBlank { stringResource(R.string.se_363) }, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton(stringResource(R.string.se_364)) { onClose() }
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(stringResource(R.string.se_365), enabled = true) {
                        Perms.openA11y(ctx)
                        onClose()
                    }
                }
            }
        }
    }
} //高权限一键激活无障碍

/** 卡片里那句提示：有高权限时替代四步教程，按钮在卡片底部统一摆 */
@Composable
internal fun A11yAutoHint() {
    Text(stringResource(R.string.se_346), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.se_347), fontSize = 12.sp, color = TextSecondary)
} //高权限提示块