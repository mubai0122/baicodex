package b.ai.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Perms
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.theme.WarnAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 屏幕操作演示动画：手指移到按钮、点下去、涟漪扩散、按钮亮一下，纯 Canvas 画，不带任何素材成本 */
@Composable
fun A11yDemo(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "a11yDemo")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "a11yP"
    )
    val move = (p / 0.5f).coerceIn(0f, 1f)
    val ripple = if (p < 0.5f) 0f else ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val hot = p >= 0.5f
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val pad = 12.dp.toPx()
        val card = Size(size.width - pad * 2, size.height - pad * 2)
        //底下那层「手机界面」：白卡 + 三根灰条 + 一个按钮占位
        drawRoundRect(Color(0xFFF7F8FA), Offset(pad, pad), card, CornerRadius(16.dp.toPx()))
        val barW = card.width * 0.46f
        val barH = 9.dp.toPx()
        drawRoundRect(
            Color(0xFFE2E5EA), Offset(pad + 16.dp.toPx(), pad + 18.dp.toPx()), Size(barW, barH),
            CornerRadius(barH / 2)
        )
        drawRoundRect(
            Color(0xFFE9ECF1), Offset(pad + 16.dp.toPx(), pad + 38.dp.toPx()), Size(barW * 0.7f, barH),
            CornerRadius(barH / 2)
        )
        //目标按钮：被点中时换成主题蓝
        val bw = card.width * 0.52f
        val bh = 30.dp.toPx()
        val bx = pad + card.width * 0.22f
        val by = pad + card.height * 0.56f
        drawRoundRect(
            if (hot) AccentBlue else AccentBlueSoft, Offset(bx, by), Size(bw, bh),
            CornerRadius(bh / 2)
        )
        //涟漪：从按钮中心往外扩两圈
        val cx = bx + bw * 0.5f
        val cy = by + bh * 0.5f
        if (ripple > 0f) {
            val r = bh * 0.6f + ripple * bh * 2.2f
            drawCircle(AccentBlue.copy(alpha = (1f - ripple) * 0.55f), r, Offset(cx, cy), style = Stroke(2.5f))
            drawCircle(AccentBlue.copy(alpha = (1f - ripple) * 0.3f), r * 0.6f, Offset(cx, cy), style = Stroke(2f))
        }
        //手指：从右下角滑到按钮上
        val fx = size.width * 0.76f + (cx - size.width * 0.76f) * move
        val fy = size.height * 0.9f + (cy - size.height * 0.9f) * move
        drawCircle(Color(0xFF2B2F36).copy(alpha = 0.18f), 17.dp.toPx(), Offset(fx + 2f, fy + 3f))
        drawCircle(Color.White, 16.dp.toPx(), Offset(fx, fy))
        drawCircle(AccentBlue, 11.dp.toPx(), Offset(fx, fy))
        drawCircle(Color.White.copy(alpha = 0.9f), 4.dp.toPx(), Offset(fx, fy))
    }
} //屏幕操作动画

/** 权限状态卡片：引导页与设置页共用，自己刷新状态；有 Shizuku / Root 就换成一键激活 */
@Composable
fun A11yPermCard(desc: String) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val on = remember(tick) { Perms.a11yOn(ctx) }
    //高权限探测放后台：root 模式要跑一次 su，不能在组合里干等
    var ch by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick, on) {
        ch = if (on) null else withContext(Dispatchers.IO) { b.ai.exec.AutoA11y.quickChannel(ctx) }
    }
    val canAuto = ch != null
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("屏幕操作", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    on -> "已开启"
                    canAuto -> "有高权限，可一键激活"
                    else -> "未开启"
                },
                fontSize = 11.sp,
                color = if (on) OkGreen else WarnAmber
            )
            Spacer(Modifier.weight(1f))
            //刷新状态与重走流程都收成小圆圈图标，不占整行
            CircleIconBox(Icons.Filled.Refresh, desc = "刷新状态") { tick++ }
            Spacer(Modifier.width(6.dp))
            CircleIconBox(Icons.Filled.PlayArrow, desc = "重走激活流程") { A11ySetup.open.value = true }
        }
        Spacer(Modifier.height(6.dp))
        Text(desc, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        A11yDemo()
        //没开才给路径：有高权限就先给一键激活，没有才走手动四步
        if (!on) {
            Spacer(Modifier.height(10.dp))
            if (canAuto) {
                A11yAutoHint()
            } else {
                Text("开启步骤", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                StepLine(1, "点下面「去开启无障碍」，跳到系统的无障碍页面")
                StepLine(2, "点「已下载的服务」→「BaiAgent 屏幕操作」")
                StepLine(3, "打开开关，弹窗里选「允许」")
                StepLine(4, "开关是灰的：设置 → 应用 → BaiAgent → 右上角 ⋮ → 允许受限设置，再回来开")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when {
                on -> PrimaryButton("已开启", enabled = false) {}
                canAuto -> PrimaryButton(stringResource(R.string.se_348), enabled = true) { A11yAuto.open.value = true }
                else -> PrimaryButton("去开启无障碍", enabled = true) { Perms.openA11y(ctx) }
            }
        }
    }
} //屏幕操作权限卡

/** 一步一条：小圆圈里写序号，后面接一句话，路径类教程按它排队 */
@Composable
internal fun StepLine(n: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(16.dp).clip(CircleShape).background(AccentBlueSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
    }
} //教程步骤行

/** 工具跑不通时的弹窗：说清楚为什么、直接给跳转，别让用户自己找设置 */
@Composable
fun A11yPermDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    ScrimCard(onDismiss = onClose) {
        Text("屏幕操作需要无障碍权限", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "刚才那一步没跑通，是因为无障碍服务没开：AI 拿不到控件坐标，也点不了屏幕。",
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        A11yDemo()
        Spacer(Modifier.height(10.dp))
        Text("开启步骤", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        StepLine(1, "点下面「去开启」，跳到系统的无障碍页面")
        StepLine(2, "点「已下载的服务」→「BaiAgent 屏幕操作」")
        StepLine(3, "打开开关，弹窗里选「允许」")
        StepLine(4, "开关是灰的：设置 → 应用 → BaiAgent → 右上角 ⋮ → 允许受限设置，再回来开")
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton("知道了") { onClose() }
            Spacer(Modifier.width(10.dp))
            PrimaryButton("去开启") {
                Perms.openA11y(ctx)
                onClose()
            }
        }
    }
} //无障碍引导弹窗