package b.ai.ui
import b.ai.net.text

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.native.Guard
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/**
 * 三层密钥演示：外圈设备密钥、中圈组合密钥、内圈内容加密。
 * 三颗粒子从各自的环上沿半径往中心走，走到中心时闪光，对应那层的文字跟着点亮。
 * 全部手写（Canvas + 无限动画），不引第三方动效库。
 */
@Composable
fun GuardDemo(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "guardDemo")
    //粒子进度：0 在外环，1 到中心
    val flow by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart),
        label = "flow"
    )
    //三圈点阵的慢速自转，三圈方向不同，看起来才不是三张同心静态图
    val spin by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    //中心锁的光晕呼吸
    val breath by t.animateFloat(
        0.82f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(148.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val unit = min(size.width, size.height) / 2f
            val r1 = unit * 0.92f //设备密钥
            val r2 = unit * 0.63f //组合密钥
            val r3 = unit * 0.34f //内容加密
            val dot = 1.5.dp.toPx()

            //一圈点阵环：dir 决定正反转，phase 让三圈错开
            fun ring(r: Float, col: Color, dir: Float, phase: Float, n: Int) {
                for (i in 0 until n) {
                    val a = ((i.toFloat() / n) + dir * spin + phase) * 2f * PI.toFloat()
                    drawCircle(
                        color = col.copy(alpha = 0.5f),
                        radius = dot,
                        center = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                    )
                }
            }
            ring(r1, AccentBlue, 0.30f, 0f, 54)
            ring(r2, WarnAmber, -0.46f, 0.18f, 42)
            ring(r3, AccentBlue, 0.72f, 0.36f, 30)

            //三根辐条把三层串起来，亮度随粒子经过递增，暗示「一层套一层」
            for (i in 0 until 3) {
                val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 3f
                fun spoke(from: Float, to: Float, alpha: Float) = drawLine(
                    color = TextSecondary.copy(alpha = alpha),
                    start = Offset(c.x + cos(a) * from, c.y + sin(a) * from),
                    end = Offset(c.x + cos(a) * to, c.y + sin(a) * to),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                spoke(r2, r1, 0.18f)
                spoke(r3, r2, 0.18f)
            }

            //粒子：同一条半径上三颗，越靠前越淡，形成拖尾
            for (i in 0 until 3) {
                val a = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 3f
                val head = (flow + i / 3f) % 1f
                for (k in 0 until 3) {
                    val f = head - k * 0.06f
                    if (f <= 0f) continue
                    val r = mix(r1, 0f, f)
                    drawCircle(
                        color = AccentBlue.copy(alpha = (0.85f - k * 0.26f) * (1f - f * 0.35f)),
                        radius = (3.2f - k * 0.9f).dp.toPx(),
                        center = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                    )
                }
                //到中心那一下的闪光
                if (head > 0.86f) {
                    val g = (head - 0.86f) / 0.14f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * (1f - g)),
                        radius = (6f + 12f * g).dp.toPx(),
                        center = c
                    )
                }
            }

            //中心：外光晕 + 锁环 + 锁体
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AccentBlue.copy(alpha = 0.42f * breath), Color.Transparent),
                    center = c,
                    radius = r3 * 1.5f
                ),
                radius = r3 * 1.5f,
                center = c
            )
            val lw = 2.dp.toPx()
            val shackle = Size(r3 * 0.9f, r3 * 0.9f)
            drawArc(
                color = AccentBlue,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(c.x - shackle.width / 2f, c.y - shackle.height * 0.78f),
                size = shackle,
                style = Stroke(width = lw, cap = StrokeCap.Round)
            )
            val bw = r3 * 0.92f
            val bh = r3 * 0.72f
            drawRoundRect(
                color = AccentBlue,
                topLeft = Offset(c.x - bw / 2f, c.y - r3 * 0.1f),
                size = Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawCircle(Color.White.copy(alpha = 0.9f), radius = r3 * 0.09f, center = Offset(c.x, c.y + r3 * 0.22f))
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            //每层的亮度跟着粒子位置走：进入这一圈时点亮
            val l1 = lit(flow, 0f, 0.34f)
            val l2 = lit(flow, 0.33f, 0.67f)
            val l3 = lit(flow, 0.66f, 1f)
            LayerLine("1", stringResource(R.string.se_gd_l1_t), stringResource(R.string.se_gd_l1_b), l1, AccentBlue)
            Spacer(Modifier.height(8.dp))
            LayerLine("2", stringResource(R.string.se_gd_l2_t), stringResource(R.string.se_gd_l2_b), l2, WarnAmber)
            Spacer(Modifier.height(8.dp))
            LayerLine("3", stringResource(R.string.se_gd_l3_t), stringResource(R.string.se_gd_l3_b), l3, AccentBlue)
        }
    }
} //三层密钥演示

/** 粒子落到哪一层，就把那一行点亮：0.35 是暗态，1 是亮态 */
private fun lit(flow: Float, from: Float, to: Float): Float {
    val f = ((flow - from) / (to - from)).coerceIn(0f, 1f)
    //先冲上去再缓下来，避免一行一行生硬地闪
    val up = if (f < 0.5f) f * 2f else (1f - f) * 2f
    return 0.45f + 0.55f * (0.35f + 0.65f * up)
}

@Composable
private fun LayerLine(no: String, title: String, body: String, alpha: Float, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(16.dp)
                .liquidBg(RoundedCornerShape(5.dp), tint = accent.copy(alpha = alpha))
        ) {
            Text(
                no,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary.copy(alpha = alpha))
            Text(body, fontSize = 10.sp, color = TextSecondary.copy(alpha = alpha))
        }
    }
} //一层密钥的说明

/** 15 秒强制冷静期：读完文案之前按钮不可点，避免手快就开了 */
@Composable
private fun RowScope.HoldButton(
    label: String,
    waitLabel: @Composable (Int) -> String,
    danger: Boolean,
    onDone: () -> Unit
) {
    var left by remember { mutableIntStateOf(15) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }
    val ready = left <= 0
    Text(
        if (ready) label else waitLabel(left),
        fontSize = 12.sp,
        color = if (danger) Color.White else Color.White.copy(alpha = 0.55f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .liquidBg(
                RoundedCornerShape(10.dp),
                tint = if (ready) (if (danger) DangerRed else AccentBlue) else SurfaceMuted
            )
            .tap(enabled = ready) { onDone() }
            .padding(vertical = 11.dp)
    )
} //倒计时按��

/** 开启前的强制警告：关掉外部点击、关掉返回键，只能读完点确认或取消 */
@Composable
fun GuardWarnDialog(
    cost: String,
    device: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    ScrimCard {
        Text(stringResource(R.string.se_gd_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.se_gd_warn_title, device, cost),
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                //演示区吃剩余高度，按钮永远保持固有高度，小屏也不会被压扁
                .weight(1f, fill = false)
                .heightIn(max = 330.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                WarnLine(stringResource(R.string.se_gd_w1_t), stringResource(R.string.se_gd_w1_b), DangerRed)
                Spacer(Modifier.height(8.dp))
                WarnLine(stringResource(R.string.se_gd_w2_t), stringResource(R.string.se_gd_w2_b), WarnAmber)
                Spacer(Modifier.height(8.dp))
                WarnLine(stringResource(R.string.se_gd_w3_t), stringResource(R.string.se_gd_w3_b), TextSecondary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.se_gd_layers), fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                GuardDemo()
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.se_gd_cancel),
                fontSize = 12.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { onCancel() }
                    .padding(vertical = 11.dp)
            )
            HoldButton(stringResource(R.string.se_gd_go), { stringResource(R.string.se_gd_go_wait, it) }, danger = false) { onConfirm() }
        }
    }
} //开启警告

/** 强制关闭前的警告：同样 15 秒冷静期，确认后就是删数据 */
@Composable
fun GuardOffDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    ScrimCard {
        Text(stringResource(R.string.se_gd_off_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DangerRed)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.se_gd_off_body), fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 240.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = DangerRedSoft)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                listOf(
                    R.string.se_gd_off_i1,
                    R.string.se_gd_off_i2,
                    R.string.se_gd_off_i3,
                    R.string.se_gd_off_i4
                ).forEach {
                    Text("· " + stringResource(it), fontSize = 12.sp, color = DangerRed)
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.se_gd_off_note),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.se_gd_keep),
                fontSize = 12.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { onCancel() }
                    .padding(vertical = 11.dp)
            )
            HoldButton(stringResource(R.string.se_gd_wipe), { stringResource(R.string.se_gd_wipe_wait, it) }, danger = true) { onConfirm() }
        }
    }
} //关闭警告

@Composable
private fun WarnLine(title: String, body: String, color: Color) {
    Column {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        Text(body, fontSize = 11.sp, color = TextSecondary)
    }
} //一条后果

/** 开启进度：四个阶段逐条点亮；不传 onDismiss，点外面和返回键都关不掉 */
@Composable
fun GuardProgressDialog(step: Int) {
    ScrimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuardRing(step)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.se_gd_pg_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.se_gd_pg_keep), fontSize = 10.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        GuardStep(1, R.string.se_gd_pg1_t, R.string.se_gd_pg1_b, step)
        Spacer(Modifier.height(9.dp))
        GuardStep(2, R.string.se_gd_pg2_t, R.string.se_gd_pg2_b, step)
        Spacer(Modifier.height(9.dp))
        GuardStep(3, R.string.se_gd_pg3_t, R.string.se_gd_pg3_b, step)
        Spacer(Modifier.height(9.dp))
        GuardStep(4, R.string.se_gd_pg4_t, R.string.se_gd_pg4_b, step)
    }
} //开启进度

/** 一个阶段：走完打勾、正在做的高亮、还没到的灰着 */
@Composable
private fun GuardStep(no: Int, titleRes: Int, bodyRes: Int, step: Int) {
    val done = step > no
    val now = step == no
    val acc = if (done) OkGreen else if (now) AccentBlue else TextSecondary
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(16.dp)
                .liquidBg(RoundedCornerShape(5.dp), tint = if (done || now) acc else SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else no.toString(),
                fontSize = 9.sp,
                color = if (done || now) Color.White else TextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(titleRes),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (done || now) TextPrimary else TextSecondary
            )
            Text(
                stringResource(bodyRes),
                fontSize = 10.sp,
                color = TextSecondary.copy(alpha = if (now) 1f else 0.72f)
            )
        }
    }
} //一个阶段

/** 环形进度：外圈按 step/4 填，内圈一段亮弧自转表示还在干活 */
@Composable
private fun GuardRing(step: Int) {
    val t = rememberInfiniteTransition(label = "guardRing")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "guardRingSpin"
    )
    Canvas(Modifier.size(42.dp)) {
        val sw = 3.dp.toPx()
        val r = size.minDimension / 2f - sw
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(TextSecondary.copy(alpha = 0.22f), radius = r, center = c, style = Stroke(width = sw))
        drawArc(
            color = AccentBlue,
            startAngle = -90f,
            sweepAngle = 360f * (step.coerceIn(0, 4) / 4f),
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        rotate(spin, c) {
            drawArc(
                color = Color.White.copy(alpha = 0.75f),
                startAngle = -90f,
                sweepAngle = 36f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f),
                size = Size(r * 1.24f, r * 1.24f),
                style = Stroke(width = sw * 0.7f, cap = StrokeCap.Round)
            )
        }
    }
} //环形进度

/** 计算中的遮罩卡片：迁移期间不给关闭 */
@Composable
fun GuardBusyDialog(text: String) {
    ScrimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuardDot()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.se_gd_busy_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(text, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
} //执行中

/** 三颗点轮流呼吸，纯粹表示「在工作」 */
@Composable
private fun GuardDot() {
    val t = rememberInfiniteTransition(label = "guardDot")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "guardDotP"
    )
    Canvas(Modifier.size(30.dp)) {
        val r = 3.dp.toPx()
        val gap = 8.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 3) {
            val phase = ((p + i / 3f) % 1f)
            val a = 0.25f + 0.75f * (1f - kotlin.math.abs(phase * 2f - 1f))
            drawCircle(
                color = AccentBlue.copy(alpha = a),
                radius = r,
                center = Offset(c.x + (i - 1) * gap, c.y)
            )
        }
    }
} //三点呼吸

/** 开关现在的状态文案 */
@Composable
fun guardStateText(): String = when {
    !Guard.on -> stringResource(R.string.se_gd_state_off)
    !Guard.ready -> stringResource(R.string.se_gd_state_bad)
    else -> stringResource(R.string.se_gd_state_on)
} //状态文案

/** 数据输出脱敏的开启警告：15 秒冷静期，读完才能点确认；明确写出「并不完全安全」 */
@Composable
fun MaskWarnDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    ScrimCard {
        Text(stringResource(R.string.se_pt_warn_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.se_pt_warn_head), fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                //正文区吃剩余高度，按钮永远保持固有高度
                .weight(1f, fill = false)
                .heightIn(max = 280.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                WarnLine(stringResource(R.string.se_pt_w1_t), stringResource(R.string.se_pt_w1_b), AccentBlue)
                Spacer(Modifier.height(8.dp))
                WarnLine(stringResource(R.string.se_pt_w2_t), stringResource(R.string.se_pt_w2_b), WarnAmber)
                Spacer(Modifier.height(8.dp))
                WarnLine(stringResource(R.string.se_pt_w3_t), stringResource(R.string.se_pt_w3_b), DangerRed)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.se_pt_mask_note), fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.se_pt_warn_back),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DangerRed
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.se_gd_cancel),
                fontSize = 12.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { onCancel() }
                    .padding(vertical = 11.dp)
            )
            HoldButton(stringResource(R.string.se_pt_go), { stringResource(R.string.se_pt_go_wait, it) }, danger = false) { onConfirm() }
        }
    }
} //脱敏开启警告