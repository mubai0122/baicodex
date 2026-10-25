package b.ai.ui

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Cfg
import b.ai.core.PetBalance
import b.ai.core.PetLines
import b.ai.core.PetSound
import b.ai.exec.Pet
import b.ai.exec.PetGrant
import b.ai.exec.PetSvc
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.File

private val PetPeakRed = Color(0xFFD0342C)
private val PetOffGreen = Color(0xFF2E7D4F)

/** 桌宠自己的底栏分类：外观 / 动效 / 台词 / 行为 / 权限，图标用 emoji 免图标依赖 */
private val PetTabs = listOf(
    FloatTab(R.string.pet_tab_look) { c -> Text("🎨", fontSize = 13.sp, color = c) },
    FloatTab(R.string.pet_tab_fx) { c -> Text("🌊", fontSize = 13.sp, color = c) },
    FloatTab(R.string.pet_tab_line) { c -> Text("💬", fontSize = 13.sp, color = c) },
    FloatTab(R.string.pet_tab_act) { c -> Text("⚙", fontSize = 13.sp, color = c) },
    FloatTab(R.string.pet_tab_perm) { c -> Text("🔒", fontSize = 13.sp, color = c) }
)

/** 桌宠页索引：搜索只查这张表，命中后跳到对应分类 */
private data class PetEntry(val labelRes: Int, val tab: Int)

private val PetIndex = listOf(
    PetEntry(R.string.pet_size, 0),
    PetEntry(R.string.pet_alpha, 0),
    PetEntry(R.string.pet_flip, 0),
    PetEntry(R.string.pet_shadow, 0),
    PetEntry(R.string.pet_card, 0),
    PetEntry(R.string.pet_font, 0),
    PetEntry(R.string.pet_font_bold, 0),
    PetEntry(R.string.pet_bubble_w, 0),
    PetEntry(R.string.pet_bubble_r, 0),
    PetEntry(R.string.pet_bubble_dark, 0),
    PetEntry(R.string.pet_float, 1),
    PetEntry(R.string.pet_float_amp, 1),
    PetEntry(R.string.pet_float_ms, 1),
    PetEntry(R.string.pet_swim, 1),
    PetEntry(R.string.pet_swim_speed, 1),
    PetEntry(R.string.pet_swim_bounce, 1),
    PetEntry(R.string.pet_bounce, 1),
    PetEntry(R.string.pet_bounce_pct, 1),
    PetEntry(R.string.pet_talk, 2),
    PetEntry(R.string.pet_bubble_ms, 2),
    PetEntry(R.string.pet_timed, 2),
    PetEntry(R.string.pet_lines, 2),
    PetEntry(R.string.pet_global, 3),
    PetEntry(R.string.pet_in_app, 3),
    PetEntry(R.string.pet_dbl_bal, 3),
    PetEntry(R.string.pet_sound, 3),
    PetEntry(R.string.pet_sound_vol, 3),
    PetEntry(R.string.pet_bal, 3),
    PetEntry(R.string.pet_perm_state, 4),
    PetEntry(R.string.pet_perm_auto, 4),
    PetEntry(R.string.pet_perm_go, 4),
    PetEntry(R.string.pet_perm_fgs, 4)
) //搜索索引

/** 桌宠设置：和设置页同款的悬浮底栏 + 搜索，各分类独立滚动 */
@Composable
fun PetScreen(backProgress: Float = 0f, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var find by remember { mutableStateOf(false) }
    var fq by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(PetLines.all()) }
    var draft by remember { mutableStateOf("") }
    var route by remember { mutableStateOf(PetGrant.route(ctx)) }
    var bal by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var peakAt by remember { mutableStateOf(System.currentTimeMillis()) }

    //峰谷状态半分钟刷一次，跨过 00:30 / 08:30 时界面自己会变
    LaunchedEffect(Unit) {
        while (true) {
            peakAt = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    fun remark() {
        route = PetGrant.route(ctx)
    }

    fun applyGlobal(global: Boolean) {
        Cfg.petGlobal = global
        Cfg.petOn = true
        if (global) {
            if (Pet.canShow(ctx)) {
                PetSvc.start(ctx)
                tip = ""
            } else {
                tip = ctx.getString(R.string.pet_need_perm)
            }
        } else {
            PetSvc.stop(ctx)
            Pet.hide()
        }
        remark()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * 0.22f * backProgress
                scaleX = 1f - 0.05f * backProgress
                scaleY = 1f - 0.05f * backProgress
                alpha = (1f - 0.55f * backProgress).coerceIn(0f, 1f)
            }
            .pageBg()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.pet_pg_t), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (find) stringResource(R.string.pet_pg_t) else stringResource(PetTabs[tab].labelRes),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .background(if (find) AccentBlueSoft else SurfaceWhite, RoundedCornerShape(9.dp))
                        .tap { find = !find; fq = "" }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.pet_pg_search),
                        tint = if (find) AccentBlue else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .background(AccentBlueSoft, RoundedCornerShape(9.dp))
                        .tap { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.pet_close),
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pet_close), fontSize = 11.sp, color = AccentBlue)
                }
            }

            if (find) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (fq.isEmpty()) {
                            Text(stringResource(R.string.pet_pg_search_sub), fontSize = 12.sp, color = TextSecondary)
                        }
                        BasicTextField(
                            value = fq,
                            onValueChange = { fq = it },
                            textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                            singleLine = true,
                            cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)
            ) {
                when {
                    find && fq.isNotBlank() -> {
                        val key = fq.trim()
                        val hits = PetIndex.filter { ctx.getString(it.labelRes).contains(key, true) }
                        if (hits.isEmpty()) {
                            Text(
                                stringResource(R.string.pet_pg_none),
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        } else {
                            hits.forEach { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                                        .tap { tab = e.tab; find = false; fq = "" }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        ctx.getString(e.labelRes),
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        stringResource(PetTabs[e.tab].labelRes),
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    find -> Text(
                        stringResource(R.string.pet_pg_search_sub),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    else -> when (tab) {
                        0 -> PetLookTab(
                            onResetPos = {
                                Cfg.petX = -1
                                Cfg.petY = -1
                                Pet.hide()
                                if (Cfg.petOn && Cfg.petGlobal && Pet.canShow(ctx)) PetSvc.start(ctx)
                                tip = ""
                            },
                            tip = tip
                        )
                        1 -> PetFxTab()
                        2 -> PetLineTab(
                            lines = lines,
                            draft = draft,
                            onDraft = { draft = it },
                            onReload = { lines = PetLines.all() },
                            onTip = { tip = it },
                            tip = tip
                        )
                        3 -> PetActTab(
                            route = route,
                            peakAt = peakAt,
                            bal = bal,
                            tip = tip,
                            onBal = { bal = it },
                            onTip = { tip = it; },
                            onApply = { applyGlobal(it) },
                            onRemark = { remark() }
                        )
                        else -> PetPermTab(
                            route = route,
                            tip = tip,
                            onTip = { tip = it },
                            onRemark = { remark() }
                        )
                    }
                }
            }
        }
        FloatTabBar(
            tabs = PetTabs,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        )
    }
} //桌宠设置页

/** 数值滑块：标题里带当前值，拖动即时落盘 */
@Composable
private fun PetSlider(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            valueRange = min.toFloat()..max.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(min, max)) }
        )
    }
} //数值滑块

/** 外观：尺寸、透明度、镜像、阴影、卡片底、气泡排版 */
@Composable
private fun PetLookTab(onResetPos: () -> Unit, tip: String) {
    SectionTitle(stringResource(R.string.pet_tab_look), stringResource(R.string.pet_look_sub))
    CardBox {
        PetSlider(stringResource(R.string.pet_size, Cfg.petSize), 60, 260, Cfg.petSize) { Cfg.petSize = it }
        PetSlider(stringResource(R.string.pet_alpha, Cfg.petAlpha), 30, 100, Cfg.petAlpha) { Cfg.petAlpha = it }
        CapSwitch(stringResource(R.string.pet_flip), "", Cfg.petFlip) { Cfg.petFlip = it }
        CapSwitch(stringResource(R.string.pet_shadow), "", Cfg.petShadow) { Cfg.petShadow = it }
        CapSwitch(stringResource(R.string.pet_card), "", Cfg.petCard) { Cfg.petCard = it }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        GhostButton(stringResource(R.string.pet_pos_reset)) { onResetPos() }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.pet_bubble_w), null)
    CardBox {
        PetSlider(stringResource(R.string.pet_font, Cfg.petFont), 9, 20, Cfg.petFont) { Cfg.petFont = it }
        CapSwitch(stringResource(R.string.pet_font_bold), "", Cfg.petFontBold) { Cfg.petFontBold = it }
        PetSlider(stringResource(R.string.pet_bubble_w, Cfg.petBubbleW), 100, 320, Cfg.petBubbleW) { Cfg.petBubbleW = it }
        PetSlider(stringResource(R.string.pet_bubble_r, Cfg.petBubbleR), 0, 28, Cfg.petBubbleR) { Cfg.petBubbleR = it }
        CapSwitch(stringResource(R.string.pet_bubble_dark), "", Cfg.petBubbleDark) { Cfg.petBubbleDark = it }
    }
} //外观分类

/** 动效：原地缓动、游泳、点击弹跳，外加一个「试一下」 */
@Composable
private fun PetFxTab() {
    val ctx = LocalContext.current
    SectionTitle(stringResource(R.string.pet_tab_fx), stringResource(R.string.pet_fx_sub))
    CardBox {
        CapSwitch(stringResource(R.string.pet_float), "", Cfg.petFloat) { Cfg.petFloat = it }
        PetSlider(stringResource(R.string.pet_float_amp, Cfg.petFloatAmp), 0, 24, Cfg.petFloatAmp) { Cfg.petFloatAmp = it }
        PetSlider(stringResource(R.string.pet_float_ms, Cfg.petFloatMs), 400, 4000, Cfg.petFloatMs) { Cfg.petFloatMs = it }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        CapSwitch(stringResource(R.string.pet_swim), "", Cfg.petSwim) {
            Cfg.petSwim = it
            Pet.rebuildSoon()
        }
        PetSlider(stringResource(R.string.pet_swim_speed, Cfg.petSwimSpeed), 10, 200, Cfg.petSwimSpeed) { Cfg.petSwimSpeed = it }
        CapSwitch(stringResource(R.string.pet_swim_bounce), "", Cfg.petSwimBounce) { Cfg.petSwimBounce = it }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        CapSwitch(stringResource(R.string.pet_bounce), "", Cfg.petBounce) { Cfg.petBounce = it }
        PetSlider(stringResource(R.string.pet_bounce_pct, Cfg.petBouncePct), 0, 30, Cfg.petBouncePct) { Cfg.petBouncePct = it }
        GhostButton(stringResource(R.string.pet_test)) {
            if (Cfg.petSound) PetSound.boing(Cfg.petSoundVol / 100f)
            Pet.sayNow(ctx)
        }
    }
} //动效分类

/** 台词：说不说、说多久、时段提示概率，以及台词池本身 */
@Composable
private fun PetLineTab(
    lines: List<String>,
    draft: String,
    onDraft: (String) -> Unit,
    onReload: () -> Unit,
    onTip: (String) -> Unit,
    tip: String
) {
    val ctx = LocalContext.current
    SectionTitle(stringResource(R.string.pet_tab_line), stringResource(R.string.pet_line_sub))
    CardBox {
        CapSwitch(stringResource(R.string.pet_talk), "", Cfg.petTalk) { Cfg.petTalk = it }
        PetSlider(stringResource(R.string.pet_bubble_ms, Cfg.petBubbleMs), 800, 8000, Cfg.petBubbleMs) { Cfg.petBubbleMs = it }
        PetSlider(stringResource(R.string.pet_timed, Cfg.petTimedPct), 0, 100, Cfg.petTimedPct) { Cfg.petTimedPct = it }
    }
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.pet_lines, lines.size), null)
    lines.forEach { one ->
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceWhite, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(one, fontSize = Cfg.petFont.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.pet_line_del),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier
                    .background(AccentBlueSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .tap {
                        PetLines.save(lines.filterNot { it == one })
                        onReload()
                    }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Field(
        label = "",
        value = draft,
        onValueChange = onDraft,
        placeholder = stringResource(R.string.pet_line_new)
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        GhostButton(stringResource(R.string.pet_line_add)) {
            val t = draft.trim()
            if (t.isNotEmpty()) {
                PetLines.save(PetLines.all() + t)
                onDraft("")
                onTip("")
                onReload()
            }
        }
        Spacer(Modifier.width(8.dp))
        GhostButton(stringResource(R.string.pet_import)) {
            val src = File(Environment.getExternalStorageDirectory(), "Download/台词.txt")
            val n = PetLines.import(src)
            onTip(if (n > 0) ctx.getString(R.string.pet_import_ok, n) else ctx.getString(R.string.pet_import_fail))
            onReload()
        }
    }
    if (tip.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(tip, fontSize = 10.sp, color = TextSecondary)
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.pet_lines_path, PetLines.path()), fontSize = 9.sp, color = TextSecondary)
} //台词分类

/** 行为：展示方式、双击查余额、音效、余额与峰谷 */
@Composable
private fun PetActTab(
    route: PetGrant.Route,
    peakAt: Long,
    bal: String,
    tip: String,
    onBal: (String) -> Unit,
    onTip: (String) -> Unit,
    onApply: (Boolean) -> Unit,
    onRemark: () -> Unit
) {
    val ctx = LocalContext.current
    SectionTitle(stringResource(R.string.pet_tab_act), stringResource(R.string.pet_act_sub))
    CardBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PolicyChip(stringResource(R.string.pet_in_app), !Cfg.petGlobal) { onApply(false) }
            Spacer(Modifier.width(8.dp))
            PolicyChip(stringResource(R.string.pet_global), Cfg.petGlobal) { onApply(true) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (route) {
                PetGrant.Route.A11Y -> stringResource(R.string.pet_route_a11y)
                PetGrant.Route.MANUAL -> stringResource(R.string.pet_route_manual)
                PetGrant.Route.NONE -> stringResource(R.string.pet_route_none)
            },
            fontSize = 11.sp,
            color = if (route == PetGrant.Route.NONE) TextSecondary else PetOffGreen
        )
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        CapSwitch(stringResource(R.string.pet_dbl_bal), stringResource(R.string.pet_dbl_bal_sub), Cfg.petDblBal) { Cfg.petDblBal = it }
        CapSwitch(stringResource(R.string.pet_sound), "", Cfg.petSound) { Cfg.petSound = it }
        PetSlider(stringResource(R.string.pet_sound_vol, Cfg.petSoundVol), 20, 100, Cfg.petSoundVol) { Cfg.petSoundVol = it }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        val off = PetBalance.offPeak(peakAt)
        Text(
            stringResource(if (off) R.string.pet_offpeak else R.string.pet_peak),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (off) PetOffGreen else PetPeakRed
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.pet_bal)) {
                onBal("")
                if (Cfg.apiKey.isBlank()) {
                    onTip(ctx.getString(R.string.pet_no_key))
                } else {
                    onBal(ctx.getString(R.string.pet_bal_wait))
                    PetBalance.fetchAsync(Cfg.apiKey) { t ->
                        onBal(if (t.isBlank()) ctx.getString(R.string.pet_bal_fail) else ctx.getString(R.string.pet_bal_say, t))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(bal.ifBlank { "—" }, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
    }
} //行为分类

/** 权限：当前走哪条路、一键授权、去系统设置、前台服务说明 */
@Composable
private fun PetPermTab(route: PetGrant.Route, tip: String, onTip: (String) -> Unit, onRemark: () -> Unit) {
    val ctx = LocalContext.current
    SectionTitle(stringResource(R.string.pet_tab_perm), stringResource(R.string.pet_perm_sub))
    CardBox {
        Text(
            when (route) {
                PetGrant.Route.A11Y -> stringResource(R.string.pet_route_a11y)
                PetGrant.Route.MANUAL -> stringResource(R.string.pet_route_manual)
                PetGrant.Route.NONE -> stringResource(R.string.pet_route_none)
            },
            fontSize = 12.sp,
            color = if (route == PetGrant.Route.NONE) TextSecondary else PetOffGreen
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(stringResource(R.string.pet_perm_auto)) {
                PetGrant.grant(ctx) { r ->
                    onTip(
                        if (r.ok) ctx.getString(R.string.pet_grant_ok, r.route)
                        else ctx.getString(R.string.pet_grant_fail)
                    )
                    onRemark()
                    if (r.ok && Cfg.petGlobal) PetSvc.start(ctx)
                }
            }
            Spacer(Modifier.width(8.dp))
            GhostButton(stringResource(R.string.pet_perm_go)) { PetGrant.openOverlaySetting(ctx) }
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        Text(stringResource(R.string.pet_perm_fgs), fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.pet_perm_fgs_sub), fontSize = 11.sp, color = TextSecondary)
    }
} //权限分类