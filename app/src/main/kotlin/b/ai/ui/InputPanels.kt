package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.core.Profiles
import b.ai.core.Providers
import b.ai.core.Skills
import b.ai.core.Mcp
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import b.ai.R
import kotlinx.coroutines.isActive
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import kotlin.math.roundToInt

/** 输入区上方的内联面板外壳：白卡 + 阴影，高度封顶可滚动，不再是全屏弹窗 */
@Composable
private fun SheetShell(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .background(SurfaceWhite, RoundedCornerShape(16.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
    ) {
        Column(
            Modifier
                .heightIn(max = 210.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            content = content
        )
    }
} //内联面板外壳

@Composable
private fun SheetHead(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.Box(
            Modifier.size(22.dp).background(SurfaceMuted, CircleShape).tap { onClose() },
            contentAlignment = Alignment.Center
        ) { IcClose(TextSecondary, side = 10.dp) }
    }
} //面板标题行（带关闭）

/** 加号面板：分类附件 + 技能包；插入纯文本，不写 @，不会和 @ 面板抢输入 */
@Composable
internal fun AttachPanel(
    onClose: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: (kind: String, mime: String) -> Unit,
    onSkill: () -> Unit
) {
    val ctx = LocalContext.current
    SheetShell {
        SheetHead(stringResource(R.string.ip_title), onClose)
        AttachRow(stringResource(R.string.ip_img), stringResource(R.string.ip_img_d)) { onPickImage() }
        AttachRow(stringResource(R.string.ip_file), stringResource(R.string.ip_file_d)) { onPickFile(ctx.getString(R.string.ip_file), "*/*") }
        AttachRow(stringResource(R.string.ip_audio), stringResource(R.string.ip_audio_d)) { onPickFile(ctx.getString(R.string.ip_audio), "audio/*") }
        AttachRow(stringResource(R.string.ip_video), stringResource(R.string.ip_video_d)) { onPickFile(ctx.getString(R.string.ip_video), "video/*") }
        AttachRow(stringResource(R.string.ip_music), stringResource(R.string.ip_music_d)) { onPickFile(ctx.getString(R.string.ip_music), "audio/*") }
        Row(
            Modifier
                .fillMaxWidth()
                .background(AccentBlueSoft, RoundedCornerShape(10.dp))
                .tap { onSkill() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ip_skill), fontSize = 12.sp, color = AccentBlue)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ip_skill_d), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 14.sp, color = AccentBlue)
        }
    }
} //附件分类面板

@Composable
private fun AttachRow(label: String, desc: String, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(SurfaceMuted, RoundedCornerShape(10.dp))
            .tap { onTap() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        Text(desc, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
} //一行附件分类

/** 技能包：点一条把技能正文带进输入框 */
@Composable
internal fun SkillPanel(onClose: () -> Unit, onInsert: (String) -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.ip_skill), onClose)
        val ctx = LocalContext.current
        val pfx = ctx.getString(R.string.ip_skill_pre)
        Skills.list.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onInsert(pfx + s.name + "\n" + s.body.take(1200) + "\n\n任务：") }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Skills.displayName(ctx, s) + if (s.on) "" else stringResource(R.string.ip_off),
                        fontSize = 12.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        Skills.displayDesc(ctx, s).ifBlank { s.body.take(40) },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                Text("＋", fontSize = 13.sp, color = AccentBlue)
            }
        }
        //MCP 能力当技能列：点了插「用某能力做某事」，具体参数由模型自己拼
        val mcpTools = Mcp.tools
        if (mcpTools.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mcp_group), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            mcpTools.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .background(SurfaceMuted, RoundedCornerShape(10.dp))
                        .tap { onInsert(ctx.getString(R.string.mcp_use_pre, t.name, t.server) + "\n") }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "MCP·" + t.server + "·" + t.name,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(t.desc, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    }
                    Text("＋", fontSize = 13.sp, color = AccentBlue)
                }
            }
        }
    }
} //技能包面板

/** 三条横线面板：本轮模型 / 是否思考 / 最大模式 */
@Composable
internal fun CfgPanel(onClose: () -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.ip_cfg), onClose)
        Text(stringResource(R.string.ip_model), fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Profiles.list.forEach { p ->
            val sel = p.id == Cfg.profileId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(if (sel) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap {
                        //切走前先把当前表单值写回旧配置，改了一半的输入不丢
                        Profiles.capture()
                        Profiles.select(p.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p.label,
                    fontSize = 12.sp,
                    color = if (sel) AccentBlue else TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (sel) Text("✓", fontSize = 12.sp, color = AccentBlue)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ip_think), fontSize = 12.sp, color = TextPrimary)
                Text(
                    if (Cfg.think) "reasoning_effort=${Cfg.thinkEffort}" else stringResource(R.string.ip_think_off),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = Cfg.think,
                onCheckedChange = { Cfg.think = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        //思考强度：整行铺满的三档滑杆，松手才落库，拖动过程只有视觉跟手
        val levels = listOf("low", "medium", "high")
        val li = levels.indexOf(Cfg.thinkEffort).coerceAtLeast(0)
        SoftSlider(index = li, enabled = Cfg.think) { v -> Cfg.thinkEffort = levels[v.coerceIn(0, 2)] }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ip_max), fontSize = 12.sp, color = TextPrimary)
                Text(
                    if (Cfg.maxMode) stringResource(R.string.ip_max_d) else stringResource(R.string.ip_max_off),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = Cfg.maxMode,
                onCheckedChange = { Cfg.maxMode = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
    }
} //本轮配置面板

/** 模型面板：输入区左下角 chip 打开，选本轮模型，或直接进设置页改配置 */
@Composable
internal fun ModelPanel(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.ip_model), onClose)
        if (Profiles.list.isEmpty()) {
            Text(stringResource(R.string.ip_no_profile), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        Profiles.list.forEach { p ->
            val sel = p.id == Cfg.profileId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(if (sel) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap {
                        //切走前先把当前表单值写回旧配置，改了一半的输入不丢
                        Profiles.capture()
                        Profiles.select(p.id)
                        onClose()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //厂商图标：按 base_url 域名自动抓官网 favicon，抓不到退首字母徽章
                BrandIcon(
                    id = p.providerId,
                    baseUrl = p.baseUrl,
                    name = Providers.byId(p.providerId).name
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.label,
                        fontSize = 12.sp,
                        color = if (sel) AccentBlue else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        p.model.ifBlank { stringResource(R.string.ip_no_model) },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                if (sel) Text("✓", fontSize = 12.sp, color = AccentBlue)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(AccentBlueSoft, RoundedCornerShape(10.dp))
                .tap { onOpenSettings() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ip_profile), fontSize = 12.sp, color = AccentBlue)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ip_profile_sub), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 14.sp, color = AccentBlue)
        }
    }
} //模型面板：本轮模型 + 配置入口

//滑杆两端色：淡青 → 淡粉，轨道渐变与拇指共用这套色
private val SoftTeal = Color(0xFF8FE3DC)
private val SoftPink = Color(0xFFFFB0CE)

//三档文案：低 / 中 / 高
private val SoftLevels = intArrayOf(R.string.ip_lv_low, R.string.ip_lv_mid, R.string.ip_lv_high)

/**
 * Operit 同款思考强度滑杆：整行铺满，轨道外侧套一圈描边外框，按档位落点。
 * 动效三件：拇指跟手连续滑动；按住时一道高光沿轨道循环扫过；松手吸附到最近档位。
 */
@Composable
private fun SoftSlider(
    index: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onIndex: (Int) -> Unit
) {
    var pos by remember { mutableFloatStateOf(index.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val pick = pos.roundToInt().coerceIn(0, 2)
    //外部改档位（切角色、恢复默认）就跟着走；正在拖动时别抢手指
    LaunchedEffect(index) { if (!dragging) pos = index.toFloat() }
    //高光扫描：只有按住时跑，900ms 一趟线性循环，松手立刻归位
    val shimmer = remember { Animatable(-0.24f) }
    LaunchedEffect(dragging) {
        if (!dragging) {
            shimmer.snapTo(-0.24f)
            return@LaunchedEffect
        }
        while (isActive) {
            shimmer.snapTo(-0.24f)
            shimmer.animateTo(1.24f, tween(durationMillis = 900, easing = LinearEasing))
        }
    }
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ip_strength), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(SoftLevels[pick]),
                fontSize = 10.sp,
                color = AccentBlue,
                fontWeight = FontWeight.Bold
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            dragging = true
                            pos = softValueAt(off.x, size.width.toFloat(), 12.dp.toPx())
                        },
                        onDragEnd = {
                            dragging = false
                            val v = pos.roundToInt().coerceIn(0, 2)
                            pos = v.toFloat()
                            onIndex(v)
                        },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        pos = softValueAt(change.position.x, size.width.toFloat(), 12.dp.toPx())
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { off ->
                        val v = softValueAt(off.x, size.width.toFloat(), 12.dp.toPx())
                            .roundToInt().coerceIn(0, 2)
                        pos = v.toFloat()
                        onIndex(v)
                    }
                }
        ) {
            drawSoftTrack(pos / 2f, pick, shimmer.value, dragging)
        }
        SoftLevelLabels(pick)
    }
} //思考强度滑杆

//像素 x 折算成 0..2 的连续档位；两端各让出一个拇指半径，跟绘制口径一致
private fun softValueAt(x: Float, widthPx: Float, inset: Float): Float {
    val span = (widthPx - inset * 2f).coerceAtLeast(1f)
    return ((x - inset) / span).coerceIn(0f, 1f) * 2f
} //折算档位

//轨道：底轨渐变只铺到拇指，外面再套一圈描边；拖动时叠一道白色柔光扫过
private fun DrawScope.drawSoftTrack(progress: Float, pick: Int, shimmerAt: Float, showShimmer: Boolean) {
    val trackH = 16.dp.toPx()
    val frameH = 20.dp.toPx()
    val stroke = 1.dp.toPx()
    val tr = trackH / 2f
    val fr = frameH / 2f
    val cy = size.height / 2f
    val p = progress.coerceIn(0f, 1f)
    //左右各留 12dp，滑杆不贴着面板边
    val edge = 12.dp.toPx()
    val valueStart = edge
    val valueEnd = size.width - edge
    val span = (valueEnd - valueStart).coerceAtLeast(1f)
    val center = valueStart + span * p
    val start = valueStart - tr
    val end = center + tr
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(lerp(SurfaceMuted, SoftTeal, 0.45f), SoftTeal, SoftPink),
            startX = start,
            endX = end
        ),
        topLeft = Offset(start, cy - trackH / 2f),
        size = Size(end - start, trackH),
        cornerRadius = CornerRadius(tr, tr)
    )
    if (showShimmer) {
        val w = 26.dp.toPx()
        val c = valueStart + span * shimmerAt
        val a = (c - w).coerceIn(start, end)
        val b = (c + w).coerceIn(start, end)
        if (b - a > 1f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.42f), Color.Transparent),
                    startX = c - w,
                    endX = c + w
                ),
                topLeft = Offset(a, cy - trackH / 2f),
                size = Size(b - a, trackH),
                cornerRadius = CornerRadius(tr, tr)
            )
        }
    }
    //外框：比轨道高 4dp，两端各探出一个圆角
    drawRoundRect(
        color = BorderGray.copy(alpha = 0.62f),
        topLeft = Offset(valueStart - fr + stroke / 2f, cy - frameH / 2f + stroke / 2f),
        size = Size(span + fr * 2f - stroke, frameH - stroke),
        cornerRadius = CornerRadius(fr, fr),
        style = Stroke(stroke)
    )
    //三档刻度：已达档位用渐变后段，未达用灰
    for (i in 0..2) {
        drawCircle(
            color = if (i <= pick) lerp(SoftTeal, SoftPink, i / 2f).copy(alpha = 0.7f)
            else BorderGray.copy(alpha = 0.78f),
            radius = 2.dp.toPx(),
            center = Offset(valueStart + span * (i / 2f), cy)
        )
    }
    //拇指：12dp 实心圆，颜色随档位在淡青淡粉之间走，外边一圈白描边压住轨道
    drawCircle(color = lerp(SoftTeal, SoftPink, p), radius = 6.dp.toPx(), center = Offset(center, cy))
    drawCircle(
        color = Color.White,
        radius = 6.dp.toPx(),
        center = Offset(center, cy),
        style = Stroke(2.dp.toPx())
    )
} //画轨道

//档位文字：贴在各档刻度正下方，第一档左端、最后一档右端
@Composable
private fun SoftLevelLabels(pick: Int) {
    Layout(
        content = {
            SoftLevels.forEachIndexed { i, res ->
                Text(
                    stringResource(res),
                    color = if (i == pick) AccentBlue else TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = if (i == pick) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val edge = 12.dp.roundToPx()
        val usable = (constraints.maxWidth - edge * 2).coerceAtLeast(0)
        val anchors = IntArray(3) { edge + usable * it / 2 }
        val starts = IntArray(3) { if (it == 0) 0 else (anchors[it - 1] + anchors[it]) / 2 }
        val ends = IntArray(3) { if (it == 2) constraints.maxWidth else (anchors[it] + anchors[it + 1]) / 2 }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = (ends[i] - starts[i]).coerceAtLeast(1),
                    minHeight = 0
                )
            )
        }
        val h = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, h) {
            placeables.forEachIndexed { i, pl ->
                val maxX = (ends[i] - pl.width).coerceAtLeast(starts[i])
                pl.placeRelative((anchors[i] - pl.width / 2).coerceIn(starts[i], maxX), 0)
            }
        }
    }
} //档位文字