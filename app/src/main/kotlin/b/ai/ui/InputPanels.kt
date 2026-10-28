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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.net.LlmClient
import b.ai.core.Effort
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import b.ai.ui.theme.DangerRed
import b.ai.R
import b.ai.ui.theme.DarkColors
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
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

/** 输入区上方的内联面板外壳：白卡 + 阴影，高度封顶可滚动；玻璃开关打开时改成真 backdrop 模糊 */
@Composable
internal fun SheetShell(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bd = LocalPageBackdrop.current
    val glass = Cfg.glassCard && bd != null
    val outer = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .padding(bottom = 6.dp)
        .then(
            if (glass) {
                Modifier.drawBackdrop(
                    backdrop = bd!!,
                    shape = { shape },
                    effects = {
                        padding = maxOf(padding, 40.dp.toPx())
                        vibrancy()
                        //模糊强度可在设置里调，0 就是只折射不糊
                        if (Cfg.glassBlur > 0) blur(Cfg.glassBlur.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.75f) },
                    shadow = {
                        Shadow.Default.copy(radius = 10.dp, alpha = if (DarkColors) 0.20f else 0.10f)
                    },
                    onDrawSurface = {
                        drawRect(SurfaceWhite.copy(alpha = if (DarkColors) 0.12f else 0.50f))
                    }
                )
            } else {
                Modifier.shadow(10.dp, shape).liquidBg(shape).border(1.dp, BorderGray, shape)
            }
        )
    Column(outer) {
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
internal fun SheetHead(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.Box(
            Modifier.size(22.dp).liquidBg(CircleShape, tint = SurfaceMuted).tap { onClose() },
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
                .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
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
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
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
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
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
                        .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
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
                    .liquidBg(RoundedCornerShape(10.dp), tint = if (sel) AccentBlueSoft else SurfaceMuted)
                    .tap {
                        //切走前先把当前表单值写回旧配置，改了一半的输入不丢
                        Profiles.capture()
                        Profiles.select(p.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.label,
                        fontSize = 12.sp,
                        color = if (sel) AccentBlue else TextPrimary,
                        maxLines = 1
                    )
                    //带上这条配置当前用的模型，点进去前就知道是什么
                    Text(
                        p.model.ifBlank { stringResource(R.string.ip_no_model) },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                if (sel) {
                    Text("✓", fontSize = 12.sp, color = AccentBlue)
                }
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
        //思考强度：档位随模型走（DeepSeek 3 档、OpenAI 4~5 档），松手才落库
        val effortLevels = Effort.levels(Cfg.providerId, Cfg.model)
        val li = Effort.indexOf(effortLevels, Cfg.thinkEffort)
        //换模型后旧档位可能不合法：直接落库成映射后的值，免得显示的和实际下发的不一致
        LaunchedEffect(Cfg.providerId, Cfg.model) {
            if (Cfg.thinkEffort.lowercase() !in effortLevels) Cfg.thinkEffort = effortLevels[li]
        }
        ChargeSlider(levels = effortLevels, index = li, enabled = Cfg.think) { v ->
            Cfg.thinkEffort = effortLevels[v.coerceIn(0, effortLevels.lastIndex)]
        }
        //工具结果预算：0 = max 不限；右侧胶囊点一下切一档，治理层按它折算字符截止
        val budgetSteps = intArrayOf(0, 2000, 4000, 8000, 16000, 32000)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ip_budget), fontSize = 12.sp, color = TextPrimary)
                Text(
                    if (Cfg.toolBudgetTokens <= 0) stringResource(R.string.ip_budget_max)
                    else Cfg.toolBudgetTokens.toString() + " tok",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Row(
                Modifier
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                    .tap {
                        val at = budgetSteps.indexOf(Cfg.toolBudgetTokens)
                        Cfg.toolBudgetTokens = budgetSteps[(if (at < 0) 0 else at + 1) % budgetSteps.size]
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (Cfg.toolBudgetTokens <= 0) "max" else Cfg.toolBudgetTokens.toString(),
                    fontSize = 11.sp,
                    color = AccentBlue
                )
                Spacer(Modifier.width(4.dp))
                Text("›", fontSize = 12.sp, color = AccentBlue)
            }
        }
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

/** 模型面板：输入区左下角 chip 打开；先列配置，点进去只列设置里勾过的模型 */
@Composable
internal fun ModelPanel(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    //非空 = 已经进了某条配置的模型列表，值是配置 id
    var sub by remember { mutableStateOf<String?>(null) }
    val one = sub?.let { id -> Profiles.list.firstOrNull { it.id == id } }
    //返回键在二级卡片里先退回配置列表
    BackHandler(enabled = sub != null) { sub = null }
    SheetShell {
        if (one == null) {
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
                        .liquidBg(RoundedCornerShape(10.dp), tint = if (sel) AccentBlueSoft else SurfaceMuted)
                        .tap {
                            //切走前先把当前表单值写回旧配置，改了一半的输入不丢
                            Profiles.capture()
                            Profiles.select(p.id)
                            //一律进二级卡片：没勾模型时那里有「去设置里选模型」的入口，不再直接关掉面板
                            sub = p.id
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
                    Text("›", fontSize = 12.sp, color = AccentBlue)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .liquidBg(RoundedCornerShape(10.dp))
                    .tap {
                        //面板里直接加一条空白配置：存回当前表单再新增，然后进设置把 Key / 模型填完
                        Profiles.capture()
                        Profiles.add()
                        onOpenSettings()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.ms_add), fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ms_add_sub), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("+", fontSize = 14.sp, color = AccentBlue)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
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
        } else {
            //二级卡片：只列这条配置在设置里勾过的模型，不联网也没有搜索
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    fontSize = 20.sp,
                    color = TextSecondary,
                    modifier = Modifier.tap { sub = null }.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Text(
                    one.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            if (one.models.isEmpty()) {
                Text(stringResource(R.string.ms_multi_empty), fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlueSoft)
                        .tap { onOpenSettings() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.ms_multi_go), fontSize = 12.sp, color = AccentBlue)
                    Spacer(Modifier.weight(1f))
                    Text("›", fontSize = 14.sp, color = AccentBlue)
                }
            } else {
                one.models.forEach { m ->
                    val sel = m == Cfg.model && one.id == Cfg.profileId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .liquidBg(RoundedCornerShape(10.dp), tint = if (sel) AccentBlueSoft else SurfaceMuted)
                            .tap {
                                Profiles.select(one.id)
                                Cfg.model = m
                                Profiles.capture()
                                onClose()
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            m,
                            fontSize = 12.sp,
                            color = if (sel) AccentBlue else TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (sel) Text("✓", fontSize = 12.sp, color = AccentBlue)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.ms_multi_hint), fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
} //模型面板：配置 → 已勾选模型

//档位文案：低 / 中 / 高
private val SoftLevels = intArrayOf(R.string.ip_lv_low, R.string.ip_lv_mid, R.string.ip_lv_high)

//充能条 24 格：一格约 4% 强度
private const val ChargeCells = 24
//已充能用主题深蓝，前沿热核用主题浅蓝；燃烧只在这两个色之间呼吸，不引入新色
private val ChargeDeep = AccentBlue
private val ChargeCore = AccentBlueSoft

//落档弹簧：dampingRatio < 1 才有过冲，才有松手回弹
private val ChargeSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

/**
 * 思考强度充能条：26dp 胶囊里排 24 格，拖动跟手、松手弹到最近档位、最高档燃烧。
 * 关键：拖动中格子颜色一律不做动画，否则前沿热核会拖影；只有落档与燃烧态才跑动画。
 */
@Composable
private fun ChargeSlider(
    levels: List<String>,
    index: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onIndex: (Int) -> Unit
) {
    //档位数随模型变：少于一档就没得滑，按两档兜底防除零
    val count = levels.size.coerceAtLeast(2)
    val span = (count - 1).toFloat()
    fun ratioOf(i: Int): Float = i.coerceIn(0, count - 1) / span
    val scope = rememberCoroutineScope()
    val settle = remember(count) { Animatable(ratioOf(index)) }
    var dragRatio by remember(count) { mutableFloatStateOf(ratioOf(index)) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    //拖动中读原始值，其余时候读落档动画值，两者无缝衔接
    val ratio = if (dragging) dragRatio else settle.value
    val knobDp = 14.dp
    val knobPx = with(LocalDensity.current) { knobDp.toPx() }
    var barPx by remember { mutableIntStateOf(0) }
    //外部改档位（切角色、恢复默认）才跟过去，拖动与落档途中不抢
    LaunchedEffect(index, count) {
        if (!dragging && !settling) settle.animateTo(ratioOf(index), ChargeSpring)
    }
    //按压：拖动起落手动转成 PressInteraction，只喂旋钮放大
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 1.1f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "charge-press"
    )
    var held by remember { mutableStateOf<PressInteraction.Press?>(null) }
    fun hold(p: Offset) {
        val e = PressInteraction.Press(p)
        held = e
        scope.launch { interaction.emit(e) }
    }
    fun drop() {
        val e = held ?: return
        held = null
        scope.launch { interaction.emit(PressInteraction.Release(e)) }
    }
    //松手：先接管手指位置再弹到最近档位，中间不跳帧
    fun settleTo() {
        val v = (dragRatio * span).roundToInt().coerceIn(0, count - 1)
        if (v != index) onIndex(v)
        scope.launch {
            settling = true
            settle.snapTo(dragRatio)
            dragging = false
            settle.animateTo(ratioOf(v), ChargeSpring)
            settling = false
        }
    }
    //燃烧：最高档起两组错拍往复（已充能格 1100ms、热核 700ms），跑 0..1 进度再 lerp 颜色
    val burn = rememberInfiniteTransition(label = "charge-burn")
    val deepT by burn.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "charge-burn-deep"
    )
    val coreT by burn.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "charge-burn-core"
    )
    //燃烧只在最高档：最后一格到手就点火，跟着手指走，不等松手
    val burning = (ratio * span).roundToInt() >= count - 1
    val deepColor = if (burning) lerp(ChargeDeep, ChargeCore, deepT) else ChargeDeep
    val coreColor = if (burning) lerp(ChargeCore, Color.White, coreT) else ChargeCore
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ip_strength), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                //档位名直接用下发的原值，避免自造译名和实际请求对不上
                levels[(ratio * span).roundToInt().coerceIn(0, count - 1)].uppercase(),
                fontSize = 10.sp,
                color = AccentBlue,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .onSizeChanged { barPx = it.width }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            dragRatio = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragging = true
                            hold(off)
                        },
                        onDragEnd = { drop(); settleTo() },
                        onDragCancel = { drop(); settleTo() }
                    ) { change, _ ->
                        //事件直更，不套任何动画，前沿热核才不会拖影
                        dragRatio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { off ->
                        dragRatio = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragging = true
                        hold(off)
                        drop()
                        settleTo()
                    }
                }
        ) {
            //底：胶囊背景单独裁切，燃烧发光画在上层才不会被裁掉
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(13.dp)).background(SurfaceMuted))
            Canvas(Modifier.fillMaxSize()) {
                drawCharge(ratio, burning, deepColor, coreColor)
            }
            //旋钮：跟着前沿走，按下放大 1.1 倍
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(((barPx - knobPx).coerceAtLeast(0f) * ratio).roundToInt(), 0) }
                    .size(knobDp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .background(Color.White, CircleShape)
                    .border(2.dp, if (burning) deepColor else AccentBlue, CircleShape)
            )
        }
    }
} //思考强度充能条

//充能条：已充能是一整条连续色块（右端跟手、不分块），前沿一段浅色热核，只有未充能处留格子刻度
private fun DrawScope.drawCharge(ratio: Float, burning: Boolean, deep: Color, core: Color) {
    val padX = 3.dp.toPx()
    val padY = 3.dp.toPx()
    val gap = 1.2.dp.toPx()
    val cellH = (size.height - padY * 2f).coerceAtLeast(1f)
    val innerW = (size.width - padX * 2f).coerceAtLeast(1f)
    val cellW = ((innerW - gap * (ChargeCells - 1)) / ChargeCells).coerceAtLeast(1f)
    val pill = CornerRadius(cellH / 2f, cellH / 2f)
    val tick = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    val p = ratio.coerceIn(0f, 1f)
    val filledW = innerW * p
    //发光用三层同色半透明外扩，兼容 API 24，不依赖 RenderEffect 的 31+
    if (burning) {
        for (i in 1..3) {
            val grow = 2.dp.toPx() * i
            drawRoundRect(
                color = deep.copy(alpha = 0.14f / i),
                topLeft = Offset(-grow, padY - grow),
                size = Size(size.width + grow * 2f, cellH + grow * 2f),
                cornerRadius = CornerRadius((cellH + grow * 2f) / 2f, (cellH + grow * 2f) / 2f)
            )
        }
    }
    //未充能：只在填充体右侧铺刻度格，已划过的区间不留缝
    for (i in 0 until ChargeCells) {
        val left = padX + i * (cellW + gap)
        if (left < padX + filledW) continue
        drawRoundRect(
            color = Color.White.copy(alpha = 0.72f),
            topLeft = Offset(left, padY),
            size = Size(cellW, cellH),
            cornerRadius = tick
        )
    }
    if (filledW <= 1f) return
    //已充能：一整条连续色块，右端跟着手指走
    drawRoundRect(
        color = deep,
        topLeft = Offset(padX, padY),
        size = Size(filledW, cellH),
        cornerRadius = pill
    )
    //热核：贴着填充体右端的一段浅色，满格时自然消失
    if (filledW >= innerW - 0.5f) return
    val coreRight = padX + filledW
    val coreLeft = (coreRight - cellW * 1.6f).coerceAtLeast(padX)
    drawRoundRect(
        color = core,
        topLeft = Offset(coreLeft, padY),
        size = Size(coreRight - coreLeft, cellH),
        cornerRadius = pill
    )
}

/** 模型吸附卡片：配置的模型列表直接从底部弹上来，点一条就切本轮模型 */
@Composable
private fun ModelPickSheet(
    label: String,
    loading: Boolean,
    models: List<String>,
    current: String,
    err: String?,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    //中转站动辄几百条模型，给个简单的本地搜索
    val kw = q.trim()
    val shown = if (kw.isEmpty()) models else models.filter { it.contains(kw, ignoreCase = true) }
    BottomSheetCard(onDismiss = onClose) {
        //顶部把手
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(BorderGray, CircleShape))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    label.ifBlank { stringResource(R.string.ip_model) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    if (loading) stringResource(R.string.ms_fetching) else stringResource(R.string.ms_pulled, models.size),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Text(
                stringResource(R.string.ms_fetch),
                fontSize = 11.sp,
                color = AccentBlue,
                modifier = Modifier.tap { onRetry() }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        //搜索只过滤本地已拉到的列表，不发请求
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) {
                    Text(stringResource(R.string.ms_search), fontSize = 12.sp, color = TextSecondary)
                }
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (q.isNotEmpty()) {
                Text(
                    "✕",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.tap { q = "" }.padding(start = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (loading) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.ms_fetching), fontSize = 13.sp, color = TextSecondary)
            }
        } else {
            if (err != null) {
                Text(err, fontSize = 11.sp, color = DangerRed, modifier = Modifier.padding(bottom = 6.dp))
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                shown.forEach { m ->
                    val sel = m == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liquidBg(RoundedCornerShape(12.dp), tint = if (sel) AccentBlueSoft else Color.Transparent)
                            .tap { onPick(m) }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            m,
                            fontSize = 15.sp,
                            color = if (sel) AccentBlue else TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (sel) Text("✓", fontSize = 13.sp, color = AccentBlue)
                    }
                }
            }
        }
    }
} //模型吸附卡片