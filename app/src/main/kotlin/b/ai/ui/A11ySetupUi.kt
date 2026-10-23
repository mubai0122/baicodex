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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Perms
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
        canAuto = if (on) false else withContext(Dispatchers.IO) { b.ai.exec.AutoA11y.quickChannel(ctx) != null }
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
            if (canAuto && !on) {
                A11yAutoHint()
            } else {
                Text("开启步骤", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                StepLine(1, "点下面「去开启」，跳到系统的无障碍页面")
                StepLine(2, "点「已下载的服务」→「BaiAgent 屏幕操作」")
                StepLine(3, "打开开关，弹窗里选「允许」")
                StepLine(4, "开关是灰的：设置 → 应用 → BaiAgent → 右上角 ⋮ → 允许受限设置，再回来开")
            }
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
                when {
                    on -> PrimaryButton("下一步", enabled = true) { step = 1 }
                    canAuto -> PrimaryButton(stringResource(R.string.se_348), enabled = true) { A11yAuto.open.value = true }
                    else -> PrimaryButton("去开启", enabled = true) { Perms.openA11y(ctx) }
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
            if (!on) {
                StepLine(1, "点下面「去开启」，跳到系统的无障碍页面")
                StepLine(2, "点「已下载的服务」→「BaiAgent 屏幕操作」")
                StepLine(3, "打开开关，弹窗里选「允许」")
                StepLine(4, "开关是灰的：设置 → 应用 → BaiAgent → 右上角 ⋮ → 允许受限设置，再回来开")
                Spacer(Modifier.height(10.dp))
            }
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