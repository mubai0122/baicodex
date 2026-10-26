package b.ai.ui
import android.content.Context

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Agent
import b.ai.agent.PendingTool
import b.ai.agent.Tools
import b.ai.core.Perms
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.theme.WarnAmber
import b.ai.ui.theme.WarnAmberSoft
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 弹窗状态：active 驱动背景模糊，content 交给根弹窗层渲染（Onboarding 这类内嵌弹窗也走这里） */
object Overlay {
    var active by mutableStateOf(false)
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

/** 把内嵌弹窗挂到根弹窗层（否则卡片本身会被录进背景层一起模糊） */
@Composable
fun HostInOverlay(active: Boolean, content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(content)
    DisposableEffect(active) {
        Overlay.active = active
        Overlay.content = if (active) ({ latest() }) else null
        onDispose {
            Overlay.active = false
            Overlay.content = null
        }
    }
}

//玻璃卡片底：深色下是半透明深灰，不然弹窗在深色里会白成一块
private val GlassWhite: Color get() = if (DarkColors) Color(0xF21B1F26) else Color(0xF2FFFFFF)
private val CardShape = RoundedCornerShape(20.dp)

/** 统一弹窗外壳：玻璃卡片，入场 scale+fade；传了 onDismiss 就支持点卡片外部关闭（强制弹窗不传） */
@Composable
fun ScrimCard(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val slot = LocalCardSlot.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(180),
        label = "cardIn"
    )
    //缩放单独走阻尼 0.75 的弹簧，落地带一点回弹，比线性 tween 更像系统弹窗
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "cardScale"
    )
    Box(
        Modifier
            .fillMaxSize()
            .tap(enabled = onDismiss != null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp)
                //卡片自己吞掉点击，否则点卡片里任何空白都会被外层当成「点外部」
                .tap { }
                .onGloballyPositioned { slot?.rect = it.boundsInWindow() }
                .graphicsLayer {
                    alpha = t
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(26.dp, CardShape)
                .background(GlassWhite, CardShape)
                .border(1.dp, BorderGray, CardShape)
                .padding(18.dp),
            content = content
        )
    }
} //统一弹窗外壳：玻璃卡片，入场 scale+fade，位置回传给背景模糊层

private val SheetShape = RoundedCornerShape(24.dp)

/** 底部吸附卡片：同 ScrimCard 的玻璃底，但从下往上滑入、最大占屏 62% */
@Composable
fun BottomSheetCard(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val slot = LocalCardSlot.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220),
        label = "sheetIn"
    )
    val sh = LocalConfiguration.current.screenHeightDp
    Box(
        Modifier
            .fillMaxSize()
            .tap(enabled = onDismiss != null) { onDismiss?.invoke() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (sh * 0.62f).dp)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                //卡片自己吞掉点击，否则点卡片里任何空白都会关掉
                .tap { }
                .onGloballyPositioned { slot?.rect = it.boundsInWindow() }
                .graphicsLayer {
                    alpha = t
                    translationY = (1f - t) * 80.dp.toPx()
                }
                .shadow(26.dp, SheetShape)
                .background(GlassWhite, SheetShape)
                .border(1.dp, BorderGray, SheetShape)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            content = content
        )
    }
} //底部吸附卡片

private fun argPreview(ctx: Context, name: String, args: JSONObject): String = when (name) {
    Tools.SHELL -> args.optString("command")
    Tools.WEB_SEARCH -> "query: ${args.optString("query")}"
    Tools.WRITE -> {
        val body = args.optString("content")
        val cut = if (body.length > 1200) body.take(1200) + ctx.getString(R.string.dh_trunc, body.length) else body
        "path: ${args.optString("path")}\n\n$cut"
    }
    else -> "path: ${args.optString("path")}"
} //弹窗内参数全文

@Composable
fun ToolConfirmDialog(p: PendingTool) {
    val ctx = LocalContext.current
    val name = p.call.name
    ScrimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(name, AccentBlue, side = 17.dp)
            Spacer(Modifier.width(6.dp))
            Text(Tools.label(name), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(8.dp))
            Tag(stringResource(p.route.labelRes))
            if (p.highRisk) {
                Spacer(Modifier.width(6.dp))
                Tag(stringResource(R.string.dh_high), WarnAmber, WarnAmberSoft)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                argPreview(ctx, name, p.args),
                fontSize = 12.sp,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
        if (p.highRisk) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dh_high_msg),
                fontSize = 11.sp,
                color = DangerRed
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dh_deny_forever),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { Agent.resolve(false, true) }
                    .padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                stringResource(R.string.dh_deny),
                fontSize = 12.sp,
                color = DangerRed,
                modifier = Modifier
                    .weight(1f)
                    .background(DangerRedSoft, RoundedCornerShape(10.dp))
                    .tap { Agent.resolve(false, false) }
                    .padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                stringResource(R.string.dh_allow_forever),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { Agent.resolve(true, true) }
                    .padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                stringResource(R.string.dh_allow),
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlue, RoundedCornerShape(10.dp))
                    .tap { Agent.resolve(true, false) }
                    .padding(vertical = 11.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.dh_policy_tip), fontSize = 10.sp, color = TextSecondary)
    } //工具确认弹窗
}

@Composable
fun PermNoticeDialog(
    missing: List<String>,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    ScrimCard {
        Text(stringResource(R.string.dh_perm_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                missing.forEach { p ->
                    Text("· " + Perms.label(ctx, p), fontSize = 12.sp, color = TextPrimary)
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.dh_perm_body),
            fontSize = 11.sp,
            color = DangerRed
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dh_perm_later),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onClose() }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.dh_perm_again),
                fontSize = 12.sp,
                color = AccentBlue,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlueSoft, RoundedCornerShape(10.dp))
                    .tap { onRetry() }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.dh_perm_settings),
                fontSize = 12.sp,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlue, RoundedCornerShape(10.dp))
                    .tap { onSettings() }
                    .padding(vertical = 11.dp)
            )
        }
    }
} //权限被拒后的提示：可直接跳系统设置重开

@Composable
fun SimpleDialog(title: String, body: String, onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) { Text(body, fontSize = 12.sp, color = TextPrimary) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                stringResource(R.string.dh_close),
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onClose() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
}