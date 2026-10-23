package b.ai.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Perms
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay

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
    //轮询：从系统设置页回来不用手动刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            tick++
        }
    }
    ScrimCard(onDismiss = onDone) {
        if (step == 0) {
            Text("把屏幕操作开起来", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "普通用户模式下，AI 靠无障碍服务读屏、点屏：能看清当前页的控件和坐标，也能代你点、滑、长按、输入。",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            A11yDemo()
            Spacer(Modifier.height(10.dp))
            Text(
                if (on) "当前状态：已开启" else "当前状态：未开启",
                fontSize = 12.sp,
                color = if (on) OkGreen else TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton("稍后再说") { onDone() }
                Spacer(Modifier.width(10.dp))
                if (on) {
                    PrimaryButton("下一步", enabled = true) { step = 1 }
                } else {
                    PrimaryButton("去开启", enabled = true) { Perms.openA11y(ctx) }
                }
            }
        } else {
            Text("确认一下", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (on) "已开启，AI 现在可以读屏和代你操作。" else "还没开：开关没打开之前，屏幕类工具会跑不通。",
                fontSize = 12.sp,
                color = if (on) OkGreen else DangerRed
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "开启路径：设置 → 无障碍 → 已下载的服务 → BaiAgent 屏幕操作。\n" +
                    "开关是灰的：设置 → 应用 → BaiAgent → 右上角 ⋮ → 允许受限设置，再回来开。",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Text("以后想重开或重走这个流程：设置 → 权限 → 特殊权限。", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!on) {
                    GhostButton("去开启") { Perms.openA11y(ctx) }
                    Spacer(Modifier.width(10.dp))
                }
                PrimaryButton("完成", enabled = true) { onDone() }
            }
        }
    }
} //独立激活流程弹窗